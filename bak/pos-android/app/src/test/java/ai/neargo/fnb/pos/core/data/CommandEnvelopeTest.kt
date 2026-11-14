package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.OrderRules
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** N2-01/02：信封字段、稳定订单/会话ID、按聚合的版本与跨桌多聚合。 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommandEnvelopeTest {
    private class Store : SnapshotStore {
        var json: String? = null
        val commands = mutableListOf<JournalCommand>()
        var answer: ((JournalCommand) -> JournalReceipt)? = null
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt {
            val r = answer?.invoke(command) ?: testReceipt(command)
            if (r.status == ReceiptStatus.Committed) { json = source; commands += command }
            return r
        }
    }

    private fun runWs(block: suspend TestScope.(LocalWorkspace, Store) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val disk = Store()
        val first = LocalWorkspace(disk, backgroundScope) { 1_000_000L }
        first.enterDemo(); first.selectStore("s-main")
        val w = LocalWorkspace(disk, backgroundScope) { 1_000_000L }; w.restore() // 生成并持久化设备ID
        disk.commands.clear()
        block(w, disk)
    }
    private fun LocalWorkspace.idle(n: Int = 0) = current.value!!.tables.filter { it.status == TableStatus.Idle }[n].id
    private fun LocalWorkspace.product(id: String) = current.value!!.products.first { it.id == id }
    private fun LocalWorkspace.orderId(t: String) = orders.value.getValue(t).orderId

    @Test fun envelopeCarriesScopeActorAndStableDevice() = runWs { w, d ->
        val t = w.idle()
        w.openTable(t, 1); w.addDish(t, w.product("12"), null, 1)
        val (open, add) = d.commands
        assertEquals(COMMAND_SCHEMA_VERSION, open.schemaVersion)
        assertTrue(open.deviceId.startsWith("dev-")); assertEquals(open.deviceId, add.deviceId)
        assertEquals(open.deviceId, open.coordinatorId)
        assertEquals("体验账号", open.actorId); assertEquals("local-tenant", open.tenantId); assertEquals(1L, open.authorityEpoch)
        assertEquals(1_000_000L, open.occurredAt); assertEquals(open.commandId, open.correlationId)
        assertNotEquals(open.commandId, add.commandId)
        // 重启后设备ID不变
        val again = LocalWorkspace(d, backgroundScope) { 1_000_000L }; again.restore()
        again.addDish(t, again.current.value!!.products.first { it.id == "12" }, null, 1)
        assertEquals(open.deviceId, d.commands.last().deviceId)
    }

    @Test fun openTableCreatesOrderAndSessionAggregatesWithPerAggregateVersions() = runWs { w, d ->
        val t = w.idle()
        w.openTable(t, 1)
        val oid = w.orderId(t)
        val session = w.tables.value.first { it.id == t }.sessionId
        assertTrue(oid.startsWith("O-") && session.startsWith("TS-"))
        assertEquals(listOf(AggregateVersion("order:$oid", 0), AggregateVersion("table:$t", 0)), d.commands[0].aggregates)
        w.addDish(t, w.product("12"), null, 1)
        w.addDish(t, w.product("9"), null, 1)
        assertEquals(listOf(1L, 2L), d.commands.drop(1).map { c -> c.aggregates.first { it.id == "order:$oid" }.expected })
        // 另一张桌的订单版本独立
        val u = w.idle()
        w.openTable(u, 1)
        assertEquals(0L, d.commands.last().aggregates.first { it.id.startsWith("order:") }.expected)
    }

    @Test fun changeMergeSplitKeepOrderIdentityAndSpanAggregates() = runWs { w, d ->
        val a = w.idle(0); val b = w.idle(1); val c = w.idle(2)
        w.openTable(a, 1); w.addDish(a, w.product("12"), null, 1); w.addDish(a, w.product("9"), null, 1)
        val oid = w.orderId(a)
        assertTrue(w.changeTable(a, b).isSuccess)
        assertEquals(oid, w.orderId(b)) // 换桌保留订单ID
        assertNotEquals(w.tables.value.first { it.id == a }.sessionId, w.tables.value.first { it.id == b }.sessionId)
        assertEquals(setOf("order:$oid", "table:$a", "table:$b"), d.commands.last().aggregates.map { it.id }.toSet())
        val uid = w.orders.value.getValue(b).lines.first().uid
        assertTrue(w.splitLines(b, setOf(uid), c, 1).isSuccess)
        val splitId = w.orderId(c)
        assertTrue(splitId.isNotBlank() && splitId != oid)
        assertEquals(listOf(oid), w.orders.value.getValue(c).splitFromOrderIds)
        assertTrue(d.commands.last().aggregates.map { it.id }.containsAll(listOf("order:$oid", "order:$splitId")))
        assertTrue(w.mergeTable(c, b).isSuccess)
        assertEquals(oid, w.orderId(b)); assertEquals(listOf(splitId), w.orders.value.getValue(b).mergedOrderIds)
        val due = OrderRules.totals(w.orders.value.getValue(b)).due
        w.sendToKitchen(b, true)
        assertTrue(w.settle(b, due, "银行卡", true, due).isSuccess)
        assertEquals(oid, w.current.value!!.payments.last().orderId)
    }

    @Test fun managementAggregatesAreNotTables() = runWs { w, d ->
        w.addCategory("夜宵", "")
        val p = w.current.value!!.products.first { it.skus.isEmpty() && it.stock != null }
        w.adjustStock(p.id, null, 3, "盘点")
        w.closeShift(50_000, "林晓")
        assertEquals(listOf(listOf("catalog"), listOf("stock:${p.id}"), listOf("shift")), d.commands.map { c -> c.aggregates.map { it.id } })
        val t = w.idle()
        w.createReservation(t, "客人", "13800138000", 1, 2_000_000L, "")
        val r = w.current.value!!.reservations.last()
        w.checkInReservation(r.id)
        val ids = d.commands.last().aggregates.map { it.id }
        assertTrue(ids.contains("reservation:${r.id}") && ids.contains("order:${w.orderId(t)}") && ids.contains("table:$t"))
    }

    @Test fun conflictReceiptLeavesStateUnchanged() = runWs { w, d ->
        val t = w.idle()
        val before = w.current.value
        d.answer = { c -> JournalReceipt(ReceiptStatus.Conflict, null, c.aggregates.associate { it.id to it.expected + 5 }, "业务版本冲突：table:$t", 0) }
        val r = w.openTable(t, 1)
        assertTrue(r.isFailure)
        assertEquals(before, w.current.value)
        d.answer = null
        assertTrue(w.openTable(t, 1).isSuccess)
        assertEquals(0L, d.commands.single().aggregates.first().expected) // 冲突未推进本地版本
    }

    @Test fun freshInstallPersistsDeviceIdBeforeFirstRestart() = runTest(UnconfinedTestDispatcher()) {
        val disk = Store()
        val first = LocalWorkspace(disk, backgroundScope) { 1_000_000L }
        first.restore() // 无快照：保持初始数据
        first.enterDemo(); first.selectStore("s-main")
        first.addCategory("夜宵", "")
        val id = disk.commands.single().deviceId
        assertTrue(id.startsWith("dev-"))
        val restarted = LocalWorkspace(disk, backgroundScope) { 1_000_000L }; restarted.restore()
        restarted.addCategory("早茶", "")
        assertEquals(id, disk.commands.last().deviceId)
        assertEquals(id, disk.commands.last().coordinatorId)
    }
}
