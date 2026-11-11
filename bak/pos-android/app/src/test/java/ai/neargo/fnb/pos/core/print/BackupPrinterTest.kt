package ai.neargo.fnb.pos.core.print

import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.model.DeliveryRules
import ai.neargo.fnb.pos.core.model.DeliveryState
import ai.neargo.fnb.pos.core.model.PrintKind
import ai.neargo.fnb.pos.core.model.PrintState
import ai.neargo.fnb.pos.core.model.PrinterConfig
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket

/** 方案 B：备用打印机——确定未发出才改投、结果未知不改投、主机停用直接走备用、手动改投、配置校验。 */
class BackupPrinterTest {
    private class Store : SnapshotStore {
        @Volatile var json: String? = null
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { json = source; return testReceipt(command) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }
    private class Clock(@Volatile var now: Long = 1_800_000_000_000L)

    private suspend fun ws(clock: Clock): LocalWorkspace {
        val w = LocalWorkspace(Store(), scope) { clock.now }
        w.enterDemo(); w.selectStore("s-main")
        return w
    }
    private fun LocalWorkspace.s() = lanSnapshot().store!!
    private suspend fun LocalWorkspace.order(): String {
        val t = s().tables.first { it.status == TableStatus.Idle }.id
        openTable(t, 1); addDish(t, s().products.first { it.id == "12" }, null, 1); sendToKitchen(t, true)
        return t
    }
    private fun LocalWorkspace.kitchenJob() = s().printJobs.single { it.kind == PrintKind.Kitchen }
    private fun deadPort() = ServerSocket(0).use { it.localPort }

    @Test fun failsOverAfterRepeatedNotSentAndPrintsOnBackup() = runBlocking {
        FakeEscPosPrinter().use { backup ->
            val clock = Clock(); val w = ws(clock)
            w.savePrinter(PrinterConfig("k1", "热菜机", "127.0.0.1", deadPort())); w.savePrinter(PrinterConfig("b1", "备用机", "127.0.0.1", backup.port))
            w.setRoute("厨房", "k1"); assertTrue(w.setBackup("厨房", "b1").isSuccess)
            val t = w.order()
            assertEquals(1, PrintGateway(w).drain())
            assertEquals(PrintState.Failed, w.kitchenJob().state); assertEquals("k1", w.kitchenJob().printerId) // 第一次失败先重试主机
            clock.now += 5_000
            assertEquals(2, PrintGateway(w).drain()) // 第二次失败 → 改投 → 备用机发出
            val j = w.kitchenJob()
            assertEquals(PrintState.Sent, j.state); assertEquals("b1", j.printerId); assertEquals("k1", j.failoverFrom)
            backup.awaitJobs(1); assertTrue(backup.text(0).contains("备用机代打") && backup.text(0).contains("热菜机") && backup.text(0).contains("酸梅汤"))
            assertEquals(DeliveryState.Delivered, w.s().kitchenDeliveries.single().state)
            assertNull(DeliveryRules.checkoutBlock(w.s(), t))
            clock.now += 120_000; assertEquals(0, PrintGateway(w).drain()); assertEquals(1, backup.jobs.size) // 不会再发
        }
    }

    @Test fun unknownResultIsNeverFailedOver() = runBlocking {
        FakeEscPosPrinter(errorAfter = true).use { primary -> FakeEscPosPrinter().use { backup ->
            val clock = Clock(); val w = ws(clock)
            w.savePrinter(PrinterConfig("k1", "热菜机", "127.0.0.1", primary.port)); w.savePrinter(PrinterConfig("b1", "备用机", "127.0.0.1", backup.port))
            w.setRoute("厨房", "k1"); w.setBackup("厨房", "b1")
            w.order(); PrintGateway(w).drain()
            assertEquals(PrintState.Unknown, w.kitchenJob().state)
            clock.now += 120_000; assertEquals(0, PrintGateway(w).drain())
            assertEquals("k1", w.kitchenJob().printerId); assertTrue(backup.jobs.isEmpty())
            assertTrue(w.redirectPrint(w.kitchenJob().id, "b1").isFailure) // 未知须先核对
        } }
    }

    @Test fun disabledPrimaryGoesStraightToBackup() = runBlocking {
        FakeEscPosPrinter().use { backup ->
            val clock = Clock(); val w = ws(clock)
            w.savePrinter(PrinterConfig("k1", "热菜机", "127.0.0.1", deadPort(), enabled = false)); w.savePrinter(PrinterConfig("b1", "备用机", "127.0.0.1", backup.port))
            w.setRoute("厨房", "k1"); w.setBackup("厨房", "b1")
            w.order()
            assertEquals(1, PrintGateway(w).drain())
            assertEquals("b1", w.kitchenJob().printerId); assertEquals("k1", w.kitchenJob().failoverFrom)
            assertEquals(PrintState.Sent, w.kitchenJob().state)
        }
    }

    @Test fun manualRedirectOfFailedJob() = runBlocking {
        FakeEscPosPrinter().use { other ->
            val clock = Clock(); val w = ws(clock)
            w.savePrinter(PrinterConfig("k1", "热菜机", "127.0.0.1", deadPort())); w.savePrinter(PrinterConfig("o1", "吧台机", "127.0.0.1", other.port))
            w.setRoute("厨房", "k1") // 不设备用：一直重试主机
            w.order(); PrintGateway(w).drain(); clock.now += 5_000; PrintGateway(w).drain()
            assertEquals(PrintState.Failed, w.kitchenJob().state); assertEquals("k1", w.kitchenJob().printerId)
            assertTrue(w.redirectPrint(w.kitchenJob().id, "k1").isFailure)
            assertTrue(w.redirectPrint(w.kitchenJob().id, "o1").isSuccess)
            assertEquals(1, PrintGateway(w).drain())
            other.awaitJobs(1); assertTrue(other.text(0).contains("手动改投"))
            assertEquals(PrintState.Sent, w.kitchenJob().state)
        }
    }

    @Test fun backupConfigValidation() = runBlocking {
        val w = ws(Clock())
        w.savePrinter(PrinterConfig("k1", "热菜机", "127.0.0.1", 9100)); w.savePrinter(PrinterConfig("b1", "备用机", "127.0.0.1", 9101))
        w.setRoute("厨房", "k1")
        assertTrue(w.setBackup("厨房", "k1").isFailure)
        assertTrue(w.setBackup("厨房", "nope").isFailure)
        assertTrue(w.setBackup("厨房", "b1").isSuccess)
        assertEquals("b1", w.s().printBackups["厨房"])
        assertTrue(w.removePrinter("b1").isSuccess)
        assertTrue(w.s().printBackups.isEmpty())
    }
}
