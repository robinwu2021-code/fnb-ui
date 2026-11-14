package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdjustmentWorkspaceTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    @Test fun authorizationWritesAuditAndVoidSlipAndSurvivesRestore() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk(); val w = LocalWorkspace(disk, backgroundScope) { 1000000L }
        w.enterDemo(); w.selectStore("s-main")
        val table = w.current.value!!.currentTableId
        val line = w.current.value!!.orders.getValue(table).lines.first { it.status == LineStatus.Sent }
        val request = AdjustmentRequest(AdjustmentKind.Void, line.uid, 1, reason = "菜品售罄")
        assertTrue(w.authorizeAdjustment(table, request, "manager@demo.local", "wrong").isFailure)
        assertTrue(w.current.value!!.adjustments.isEmpty())
        assertTrue(w.authorizeAdjustment(table, request, "manager@demo.local", "demo12345").isSuccess)
        val store = w.current.value!!
        assertEquals(1, store.adjustments.size)
        assertTrue(store.printJobs.last().body.first().contains("退菜单"))
        assertEquals(1, ShiftRules.summary(store.payments, store.shift, store.adjustments).voidCount)
        yield()
        val restored = LocalWorkspace(disk, backgroundScope) { 1000000L }; restored.restore(); yield()
        assertEquals(store.adjustments, restored.current.value!!.adjustments)
    }
    @Test fun cashIsRevalidatedAtSettlementAndRecordsChange() = runTest(UnconfinedTestDispatcher()) {
        val w = LocalWorkspace(Disk(), backgroundScope) { 1000000L }; w.enterDemo(); w.selectStore("s-main")
        val table = w.current.value!!.currentTableId
        w.sendToKitchen(table, true)
        val due = OrderRules.totals(w.current.value!!.orders.getValue(table)).due
        assertTrue(w.settle(table, due, "现金", false, due - 1).isFailure)
        assertTrue(w.settle(table, due - 1, "现金", false, due).isFailure)
        assertTrue(w.settle(table, due, "现金", false, due + 1000).isSuccess)
        val pay = w.current.value!!.payments.last()
        assertEquals(1000, pay.change); assertEquals(due + 1000, pay.received)
        assertTrue(w.settle(table, due, "现金", false, due).isFailure)
    }
    @Test fun missingVersionSnapshotMigratesAliasesWithoutLosingOrders() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk()
        val seed = DemoCatalog.stores(1000000L)
        val old = seed.mapValues { (_, store) -> store.copy(products = store.products.map { it.copy(searchAliases = "", mealGroups = emptyList()) }) }
        disk.data = kotlinx.serialization.json.Json.encodeToString(Vault.serializer(), Vault(stores = old, user = "体验账号", storeId = "s-main"))
        val w = LocalWorkspace(disk, backgroundScope) { 1000000L }; w.restore(); yield()
        assertTrue(ProductSearch.matches(w.current.value!!.products.first { it.id == "1" }, "gbjd"))
        // 菜品不丢；活动订单补发稳定ID并记录映射，空闲桌不分配
        assertEquals(old.getValue("s-main").orders.mapValues { it.value.lines }, w.current.value!!.orders.mapValues { it.value.lines })
        val store = w.current.value!!
        val dining = store.tables.filter { it.status == ai.neargo.fnb.pos.core.model.TableStatus.Dining }
        assertTrue(dining.all { it.sessionId.isNotBlank() && store.orders.getValue(it.id).orderId.isNotBlank() })
        assertTrue(store.idMappings.filter { it.source == "legacy-v3" }.map { it.tableId }.containsAll(dining.map { it.id }))
        assertTrue(store.tables.filter { it.status == ai.neargo.fnb.pos.core.model.TableStatus.Idle }.all { it.sessionId.isEmpty() })
        assertTrue(store.payments.all { it.orderId == null }) // 历史收款关联未知
        val vault = VaultCodec.decode(disk.data!!)
        assertTrue(vault.deviceId.startsWith("dev-"))
        assertTrue(disk.data!!.contains("\"version\":3"))
    }

    @Test fun finalVoidAllowsTableToReturnToService() = runTest(UnconfinedTestDispatcher()) {
        val w = LocalWorkspace(Disk(), backgroundScope) { 1000000L }; w.enterDemo(); w.selectStore("s-main")
        val table = w.current.value!!.tables.first { it.status == TableStatus.Idle }.id
        assertTrue(w.openTable(table, 1).isSuccess)
        val product = w.current.value!!.products.first { !it.hasSpec && it.mealGroups.isEmpty() && !it.soldOut }
        w.addDish(table, product, null, 1); w.sendToKitchen(table, true)
        val line = w.current.value!!.orders.getValue(table).lines.single()
        assertTrue(w.authorizeAdjustment(table, AdjustmentRequest(AdjustmentKind.Void, line.uid, line.qty, reason = "顾客取消"), "manager@demo.local", "demo12345").isSuccess)
        assertEquals(TableStatus.Cleaning, w.tables.value.first { it.id == table }.status)
        assertTrue(w.finishCleaning(table).isSuccess)
        assertEquals(TableStatus.Idle, w.tables.value.first { it.id == table }.status)
    }

}
