package ai.neargo.fnb.pos.core.print

import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.model.AdjustmentKind
import ai.neargo.fnb.pos.core.model.AdjustmentRequest
import ai.neargo.fnb.pos.core.model.DeliveryRules
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

/** 方案 B：多打印机副本——主单与副本各自发送，副本不计入送达、不改投备用，配置校验。 */
class PrintCopiesTest {
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
    private fun deadPort() = ServerSocket(0).use { it.localPort }

    @Test fun primaryAndCopiesPrintSeparately() = runBlocking {
        FakeEscPosPrinter().use { main -> FakeEscPosPrinter().use { pass ->
            val clock = Clock(); val w = ws(clock)
            w.savePrinter(PrinterConfig("k1", "热菜机", "127.0.0.1", main.port)); w.savePrinter(PrinterConfig("p1", "出菜口", "127.0.0.1", pass.port))
            w.setRoute("厨房", "k1"); assertTrue(w.setCopies("厨房", listOf("p1")).isSuccess)
            val t = w.order()
            val jobs = w.s().printJobs.filter { it.kind == PrintKind.Kitchen }
            assertEquals(2, jobs.size)
            val copy = jobs.single { it.copyOf.isNotBlank() }
            assertEquals("p1", copy.printerId); assertTrue(copy.title.contains("副本·出菜口"))
            assertEquals(1, w.s().kitchenDeliveries.size) // 只有主单计入送达
            assertEquals(2, PrintGateway(w).drain())
            main.awaitJobs(1); pass.awaitJobs(1)
            assertFalse(main.text(0).contains("副本")); assertTrue(pass.text(0).contains("** 副本 **") && pass.text(0).contains("酸梅汤"))
            assertNull(DeliveryRules.checkoutBlock(w.s(), t))
            // 退菜单同样出副本
            val line = w.s().orders.getValue(t).lines.single()
            assertTrue(w.authorizeAdjustment(t, AdjustmentRequest(AdjustmentKind.Void, line.uid, 1, reason = "不要了"), "manager@demo.local", "demo12345").isSuccess)
            assertEquals(2, PrintGateway(w).drain())
            pass.awaitJobs(2); assertTrue(pass.text(1).contains("退菜单"))
        } }
    }

    @Test fun copyFailureNeitherBlocksNorFailsOver() = runBlocking {
        FakeEscPosPrinter().use { main -> FakeEscPosPrinter().use { backup ->
            val clock = Clock(); val w = ws(clock)
            w.savePrinter(PrinterConfig("k1", "热菜机", "127.0.0.1", main.port)); w.savePrinter(PrinterConfig("p1", "出菜口", "127.0.0.1", deadPort()))
            w.savePrinter(PrinterConfig("b1", "备用机", "127.0.0.1", backup.port))
            w.setRoute("厨房", "k1"); w.setCopies("厨房", listOf("p1")); w.setBackup("厨房", "b1")
            val t = w.order()
            PrintGateway(w).drain(); clock.now += 5_000; PrintGateway(w).drain(); clock.now += 10_000; PrintGateway(w).drain()
            val copy = w.s().printJobs.single { it.copyOf.isNotBlank() }
            assertEquals(PrintState.Failed, copy.state); assertEquals("p1", copy.printerId) // 副本不改投备用
            assertTrue(backup.jobs.isEmpty())
            assertNull(DeliveryRules.checkoutBlock(w.s(), t)) // 副本失败不拦结账
            assertTrue(w.redirectPrint(copy.id, "b1").isSuccess) // 可手动改投
            assertEquals(1, PrintGateway(w).drain()); backup.awaitJobs(1); Unit
        } }
    }

    @Test fun frontReceiptsGetCopiesToo() = runBlocking {
        FakeEscPosPrinter().use { front -> FakeEscPosPrinter().use { office ->
            val w = ws(Clock())
            w.savePrinter(PrinterConfig("f1", "前台机", "127.0.0.1", front.port)); w.savePrinter(PrinterConfig("o1", "财务机", "127.0.0.1", office.port))
            w.setRoute("前台", "f1"); w.setCopies("前台", listOf("o1"))
            val t = w.order()
            assertTrue(w.preBill(t).isSuccess)
            val pre = w.s().printJobs.filter { it.kind == PrintKind.PreBill }
            assertEquals(2, pre.size); assertEquals("o1", pre.single { it.copyOf.isNotBlank() }.printerId)
            assertEquals(2, PrintGateway(w).drain())
            front.awaitJobs(1); office.awaitJobs(1); assertTrue(office.text(0).contains("预结单"))
        } }
    }

    @Test fun copiesConfigValidation() = runBlocking {
        val w = ws(Clock())
        listOf("k1", "a", "b", "c", "d").forEach { w.savePrinter(PrinterConfig(it, "机$it", "127.0.0.1", 9100)) }
        w.setRoute("厨房", "k1")
        assertTrue(w.setCopies("厨房", listOf("k1")).isFailure)
        assertTrue(w.setCopies("厨房", listOf("nope")).isFailure)
        assertTrue(w.setCopies("厨房", listOf("a", "b", "c", "d")).isFailure)
        assertTrue(w.setCopies("厨房", listOf("a", "b")).isSuccess)
        assertTrue(w.removePrinter("a").isSuccess)
        assertEquals(listOf("b"), w.s().printCopies["厨房"])
        assertTrue(w.setCopies("厨房", emptyList()).isSuccess)
        assertTrue(w.s().printCopies.isEmpty())
    }
}
