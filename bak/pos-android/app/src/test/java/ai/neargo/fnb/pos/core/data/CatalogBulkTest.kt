package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 建店后整理资料：批量调价（按角取整、规格跟随、零售进价签）、批量删商品（在用不能删、移出餐牌）、删桌台（只删空闲无预订）；设店长后按权限。 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogBulkTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private suspend fun ws(scope: kotlinx.coroutines.CoroutineScope, biz: BizType) = LocalWorkspace(Disk(), scope) { 1_800_000_000_000L }.also {
        it.register("甲", "a@b.cn", "secret123", "secret123").getOrThrow(); it.createStore("甲店", "JA01", "", biz).getOrThrow()
    }

    @Test fun priceRounding() {
        assertEquals(390L, CatalogBulk.newPrice(350, 10))  // 3.85 → 3.9
        assertEquals(330L, CatalogBulk.newPrice(350, -5))  // 3.325 → 3.3
        assertEquals(10L, CatalogBulk.newPrice(30, -90))   // 最低 1 角
    }

    @Test fun retailRepriceQueuesLabels() = runTest(UnconfinedTestDispatcher()) {
        val w = ws(backgroundScope, BizType.Retail)
        w.repriceProducts(setOf("cola", "milk"), 10).getOrThrow()
        val s = w.lanSnapshot().store!!
        assertEquals(390L, s.products.first { it.id == "cola" }.price)
        assertTrue(s.retail.labelQueue.any { it.productId == "cola" && it.why == LabelWhy.Price && it.oldPrice == 350L })
        assertTrue(w.repriceProducts(setOf("cola"), 0).isFailure)
        assertTrue(w.repriceProducts(setOf("cola"), 500).isFailure)
    }

    @Test fun deleteProductsSkipsInUse() = runTest(UnconfinedTestDispatcher()) {
        val w = ws(backgroundScope, BizType.Retail)
        w.holdCart(listOf(CartLine(1, "cola", 1)), null, "").getOrThrow()
        assertTrue(w.deleteProducts(setOf("cola")).exceptionOrNull()!!.message!!.contains("未结账"))
        w.queueLabel("milk").getOrThrow()
        assertEquals(1, w.deleteProducts(setOf("milk")).getOrThrow())
        val s = w.lanSnapshot().store!!
        assertTrue(s.products.none { it.id == "milk" } && s.retail.labelQueue.none { it.productId == "milk" })
    }

    @Test fun diningDeleteTablesAndProducts() = runTest(UnconfinedTestDispatcher()) {
        val w = ws(backgroundScope, BizType.Dining)
        val s0 = w.lanSnapshot().store!!
        val (a, b) = s0.tables.take(2).map { it.id }
        w.openTable(a, 2).getOrThrow()
        val dish = s0.products.first { !it.hasSpec }
        w.addDish(a, dish, null, 1)
        assertTrue(w.deleteTables(setOf(a)).isFailure)
        assertTrue(w.deleteProducts(setOf(dish.id)).isFailure)
        assertEquals(1, w.deleteTables(setOf(b)).getOrThrow())
        assertTrue(w.lanSnapshot().store!!.tables.none { it.id == b })
        val other = s0.products.first { it.id != dish.id }
        w.deleteProducts(setOf(other.id)).getOrThrow()
        assertTrue(w.lanSnapshot().store!!.menus.none { other.id in it.productIds })
    }

    @Test fun ownerSetupThenPermissionsApply() = runTest(UnconfinedTestDispatcher()) {
        val w = ws(backgroundScope, BizType.Retail)
        assertTrue(w.lanSnapshot().store!!.employees.isEmpty())
        w.saveEmployee(null, "王店长", Role.Owner, "1234", true).getOrThrow()
        assertEquals("王店长 · ${Role.Owner.label}", w.session.value.staff)
        w.saveEmployee(null, "小李", Role.Cashier, "5678", true).getOrThrow()
        val cashier = w.lanSnapshot().store!!.employees.first { it.name == "小李" }
        w.signOutStaff(); w.signInStaff(cashier.id, "5678").getOrThrow()
        // 收银员没有「设置」权限：不能批量调价
        assertTrue(w.repriceProducts(setOf("cola"), 10).isFailure)
    }
}
