package ai.neargo.fnb.pos.core.print

import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.model.AdjustmentKind
import ai.neargo.fnb.pos.core.model.AdjustmentRequest
import ai.neargo.fnb.pos.core.model.DeliveryMode
import ai.neargo.fnb.pos.core.model.DeliveryRules
import ai.neargo.fnb.pos.core.model.DeliveryState
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

/** 方案 A：档口出单方式决定必需送达渠道；厨房屏回执/超时、改打纸单、人工确认、按卡拦结账。 */
class DeliveryPolicyTest {
    private class Store : SnapshotStore {
        @Volatile var json: String? = null
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { json = source; return testReceipt(command) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }
    private class Clock(@Volatile var now: Long = 1_800_000_000_000L)

    private suspend fun ws(clock: Clock, mode: DeliveryMode?): LocalWorkspace {
        val w = LocalWorkspace(Store(), scope) { clock.now }
        w.enterDemo(); w.selectStore("s-main")
        if (mode != null) assertTrue(w.saveDeliveryModes(mapOf("厨房" to mode)).isSuccess)
        return w
    }
    private fun LocalWorkspace.s() = lanSnapshot().store!!
    private suspend fun LocalWorkspace.order(qty: Int = 1): String {
        val t = s().tables.first { it.status == TableStatus.Idle }.id
        openTable(t, 1); addDish(t, s().products.first { it.id == "12" }, null, qty); sendToKitchen(t, true)
        return t
    }
    private fun LocalWorkspace.group(t: String) = DeliveryRules.groups(s()).single { it.tableId == t }
    private fun LocalWorkspace.ticket(t: String) = s().kdsTickets.single { it.tableId == t }.id

    @Test fun paperModeKeepsLegacyBehaviour() = runBlocking {
        val clock = Clock(); val w = ws(clock, null)
        val t = w.order()
        assertTrue(w.s().kitchenDeliveries.none { it.channel == "screen" }) // 仅纸单不生成厨房屏记录
        w.process(true)
        clock.now += 120_000; assertEquals(0, w.expireScreenDeliveries())
        assertNull(DeliveryRules.checkoutBlock(w.s(), t))
        assertTrue(w.saveDeliveryModes(mapOf("不存在" to DeliveryMode.Screen)).isFailure)
    }

    @Test fun screenOnlyTimesOutThenLateAckClearsIt() = runBlocking {
        val clock = Clock(); val w = ws(clock, DeliveryMode.Screen)
        val t = w.order(); w.process(true)
        assertEquals("screen", w.group(t).screen?.channel)
        assertNull(DeliveryRules.checkoutBlock(w.s(), t)) // 等待中不拦
        clock.now += 30_000; assertEquals(0, w.expireScreenDeliveries())
        clock.now += 31_000; assertEquals(1, w.expireScreenDeliveries())
        assertTrue(w.group(t).needsAction)
        assertTrue(DeliveryRules.checkoutBlock(w.s(), t)!!.contains("厨房屏未收到"))
        w.kdsAck(listOf(w.ticket(t) to 1)) // 屏重新上线补拉
        assertEquals(DeliveryState.Delivered, w.group(t).screen?.state)
        assertNull(DeliveryRules.checkoutBlock(w.s(), t))
    }

    @Test fun fallbackPaperSatisfiesScreenAndPrintsOnce() = runBlocking {
        FakeEscPosPrinter().use { printer ->
            val clock = Clock(); val w = ws(clock, DeliveryMode.Screen)
            w.savePrinter(PrinterConfig("k1", "厨房", "127.0.0.1", printer.port)); w.setRoute("厨房", "k1")
            val t = w.order(2)
            assertEquals(1, PrintGateway(w).drain())
            clock.now += 61_000; w.expireScreenDeliveries()
            assertTrue(w.group(t).canFallback)
            assertTrue(w.printForScreen(w.ticket(t)).isSuccess)
            assertFalse(w.group(t).canFallback); assertTrue(w.printForScreen(w.ticket(t)).isFailure) // 不重复改打
            assertNotNull(DeliveryRules.checkoutBlock(w.s(), t)) // 改打未送达前仍拦
            assertEquals(1, PrintGateway(w).drain())
            printer.awaitJobs(2); assertTrue(printer.text(1).contains("厨房屏未收到") && printer.text(1).contains("酸梅汤"))
            assertEquals("screen", w.group(t).fallback?.covers)
            assertNull(DeliveryRules.checkoutBlock(w.s(), t))
        }
    }

    @Test fun eitherNeedsOneRealChannel() = runBlocking {
        // 有真打印机：纸单送达即满足，厨房屏未收到只提示
        FakeEscPosPrinter().use { printer ->
            val clock = Clock(); val w = ws(clock, DeliveryMode.Either)
            w.savePrinter(PrinterConfig("k1", "厨房", "127.0.0.1", printer.port)); w.setRoute("厨房", "k1")
            val t = w.order(); PrintGateway(w).drain()
            clock.now += 61_000; w.expireScreenDeliveries()
            assertEquals(DeliveryState.Failed, w.group(t).screen?.state)
            assertNull(DeliveryRules.checkoutBlock(w.s(), t))
        }
        // 没有打印机：模拟纸单不算真实送达，厨房屏超时则拦
        val clock = Clock(); val w = ws(clock, DeliveryMode.Either)
        val t = w.order(); w.process(true)
        assertEquals(DeliveryState.Delivered, w.group(t).paper?.state)
        clock.now += 61_000; w.expireScreenDeliveries()
        assertTrue(w.group(t).needsAction)
    }

    @Test fun bothNeedsEveryChannelAndManualConfirmClearsTicket() = runBlocking {
        FakeEscPosPrinter(errorAfter = true).use { printer ->
            val clock = Clock(); val w = ws(clock, DeliveryMode.Both)
            w.savePrinter(PrinterConfig("k1", "厨房", "127.0.0.1", printer.port)); w.setRoute("厨房", "k1")
            val t = w.order(); PrintGateway(w).drain()
            assertEquals(PrintState.Unknown, w.s().printJobs.single { it.kind.name == "Kitchen" }.state)
            w.kdsAck(listOf(w.ticket(t) to 1))
            assertTrue(DeliveryRules.checkoutBlock(w.s(), t)!!.contains("纸单结果未知")) // 屏已收，纸单未知仍拦
            assertTrue(w.confirmDelivery(w.ticket(t), "已口头通知厨房").isSuccess)
            assertNull(DeliveryRules.checkoutBlock(w.s(), t))
            assertTrue(w.confirmDelivery(w.ticket(t), "再次").isFailure)
        }
    }

    @Test fun cancelledTicketDoesNotBlock() = runBlocking {
        val clock = Clock(); val w = ws(clock, DeliveryMode.Screen)
        val t = w.order(); w.process(true)
        val line = w.s().orders.getValue(t).lines.single()
        assertTrue(w.authorizeAdjustment(t, AdjustmentRequest(AdjustmentKind.Void, line.uid, 1, reason = "不要了"), "manager@demo.local", "demo12345").isSuccess)
        clock.now += 61_000; w.expireScreenDeliveries()
        assertTrue(DeliveryRules.groups(w.s()).none { it.needsAction })
    }

    @Test fun legacySupersededPaperWithSeparateReprintDoesNotBlock() = runBlocking {
        val clock = Clock(); val w = ws(clock, null)
        val t = w.order(); w.process(true)
        // 模拟升级前的数据：原纸单失败并被补打接替，补打记录的 ticketId 为补打单号（另成一组）
        val s0 = w.s()
        val d = s0.kitchenDeliveries.single()
        val legacy = s0.copy(kitchenDeliveries = listOf(d.copy(state = DeliveryState.Failed, supersededBy = "RP-1"),
            d.copy(ticketId = "RP-1", jobId = "RP-1", state = DeliveryState.Delivered)))
        assertTrue(DeliveryRules.groups(legacy).none { it.needsAction })
        assertNull(DeliveryRules.checkoutBlock(legacy, t))
    }
}
