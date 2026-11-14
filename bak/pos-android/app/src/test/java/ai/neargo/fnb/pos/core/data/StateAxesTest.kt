package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.DeliveryState
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderRules
import ai.neargo.fnb.pos.core.model.OutboxState
import ai.neargo.fnb.pos.core.model.PaymentState
import ai.neargo.fnb.pos.core.model.PrintKind
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/** N3：厨房送达、云同步、支付各自独立；模拟/旧状态不升级为真实回执；结账受厨房失败阻止。 */
@OptIn(ExperimentalCoroutinesApi::class)
class StateAxesTest {
    private class Store : SnapshotStore {
        var json: String? = null
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { json = source; return testReceipt(command) }
    }
    private fun runWs(block: suspend TestScope.(LocalWorkspace, Store) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val disk = Store()
        val w = LocalWorkspace(disk, backgroundScope) { 1_000_000L }
        w.enterDemo(); w.selectStore("s-main")
        block(w, disk)
    }
    private fun LocalWorkspace.idle() = current.value!!.tables.first { it.status == TableStatus.Idle }.id
    private fun LocalWorkspace.product(id: String) = current.value!!.products.first { it.id == id }

    @Test fun offlineKitchenDeliveryIsIndependentOfCloud() = runWs { w, _ ->
        val t = w.idle()
        w.openTable(t, 1); w.addDish(t, w.product("12"), null, 1)
        w.sendToKitchen(t, online = false)
        val d = w.deliveries.value.single()
        assertEquals(DeliveryState.Pending, d.state); assertEquals(w.orders.value.getValue(t).orderId, d.orderId)
        w.process(kitchenOk = true)
        assertEquals(DeliveryState.Delivered, w.deliveries.value.single().state) // 云端仍未同步，厨房已送达
        assertEquals("simulation", w.deliveries.value.single().source)
        assertEquals(OutboxState.Pending, w.current.value!!.outbox.single().state)
        assertTrue(w.orders.value.getValue(t).lines.all { it.status == LineStatus.Queued })
    }

    @Test fun failedKitchenBlocksCheckoutUntilRetriedOrConfirmed() = runWs { w, _ ->
        val t = w.idle()
        w.openTable(t, 1); w.addDish(t, w.product("12"), null, 1); w.sendToKitchen(t, true)
        w.process(kitchenOk = false)
        val d = w.deliveries.value.single()
        assertEquals(DeliveryState.Failed, d.state); assertTrue(d.lastError.contains("缺纸"))
        val due = OrderRules.totals(w.orders.value.getValue(t)).due
        assertTrue(w.settle(t, due, "现金", true, due).exceptionOrNull()!!.message!!.contains("厨房单未确认送达"))
        assertTrue(w.confirmDelivery(d.ticketId, " ").isFailure)
        assertTrue(w.confirmDelivery(d.ticketId, "已手写单交厨房").isSuccess)
        val c = w.deliveries.value.single()
        assertEquals(DeliveryState.ManualConfirmed, c.state); assertEquals("体验账号", c.confirmedBy); assertEquals("manual", c.source)
        w.process(kitchenOk = false) // 人工确认后不被模拟结果覆盖
        assertEquals(DeliveryState.ManualConfirmed, w.deliveries.value.single().state)
        assertTrue(w.confirmDelivery(d.ticketId, "重复").isFailure)
        assertTrue(w.settle(t, due, "现金", true, due).isSuccess)
        val p = w.current.value!!.payments.last()
        assertEquals(PaymentState.LocalRecorded, p.state); assertEquals("local", p.source)
    }

    @Test fun retryAfterPaperRestoresDelivery() = runWs { w, _ ->
        val t = w.idle()
        w.openTable(t, 1); w.addDish(t, w.product("12"), null, 1); w.sendToKitchen(t, true)
        w.process(false); w.process(true)
        assertEquals(DeliveryState.Delivered, w.deliveries.value.single().state)
        assertEquals(2, w.deliveries.value.single().attempts)
    }

    @Test fun voidSlipAndReprintAlsoTracked() = runWs { w, _ ->
        val t = w.current.value!!.currentTableId
        val line = w.orders.value.getValue(t).lines.first { it.status == LineStatus.Sent }
        w.authorizeAdjustment(t, ai.neargo.fnb.pos.core.model.AdjustmentRequest(ai.neargo.fnb.pos.core.model.AdjustmentKind.Void, line.uid, 1, reason = "售罄"), "manager@demo.local", "demo12345")
        assertTrue(w.deliveries.value.any { it.label.contains("退菜单") })
        val job = w.jobs.value.first { it.kind == PrintKind.Kitchen }
        w.reprint(job.id)
        assertTrue(w.deliveries.value.any { it.label.contains("补打") })
    }

    /** 旧数据：已同步标 legacy；已有厨房任务记为历史待核实；未出单的离线批次补出单。 */
    @Test fun legacyStatesAreNotUpgradedToRealReceipts() = runTest(UnconfinedTestDispatcher()) {
        val disk = Store()
        val w = LocalWorkspace(disk, backgroundScope) { 1_000_000L }
        w.enterDemo(); w.selectStore("s-main")
        val t = w.idle()
        w.openTable(t, 1); w.addDish(t, w.product("12"), null, 1); w.sendToKitchen(t, false)
        val t2 = w.current.value!!.tables.first { it.status == TableStatus.Idle }.id
        w.openTable(t2, 1); w.addDish(t2, w.product("9"), null, 1); w.sendToKitchen(t2, false)
        w.process(true)
        // 构造旧版快照：移除送达记录、t2 批次的厨房单和完成来源；把 t 的队列项标为旧版「已同步」
        val root = Json.parseToJsonElement(disk.json!!).jsonObject
        val store = root.getValue("stores").jsonObject.getValue("s-main").jsonObject
        val batch2 = w.orders.value.getValue(t2).lines.first().batchId!!
        val legacyStore = JsonObject(store + mapOf(
            "kitchenDeliveries" to JsonArray(emptyList()),
            "printJobs" to JsonArray(store.getValue("printJobs").jsonArray.filter { it.jsonObject.getValue("id").jsonPrimitive.content != "P-$batch2" }),
            "outbox" to JsonArray(store.getValue("outbox").jsonArray.map { o ->
                val m = o.jsonObject
                if (m.getValue("tableId").jsonPrimitive.content == t) JsonObject(m + mapOf("state" to JsonPrimitive("Done")) - "ackSource") else o
            }),
        ))
        disk.json = JsonObject(root + ("stores" to JsonObject(root.getValue("stores").jsonObject + ("s-main" to legacyStore)))).toString()
        val r = LocalWorkspace(disk, backgroundScope) { 1_000_000L }; r.restore()
        val s = r.current.value!!
        assertEquals("legacy", s.outbox.first { it.tableId == t }.ackSource)
        assertEquals("legacy", s.kitchenDeliveries.first { it.tableId == t }.source)
        assertTrue(s.printJobs.any { it.id == "P-$batch2" }) // 补出单
        assertEquals("simulation", s.kitchenDeliveries.first { it.jobId == "P-$batch2" }.source)
        assertEquals(DeliveryState.Pending, s.kitchenDeliveries.first { it.jobId == "P-$batch2" }.state)
    }
}
