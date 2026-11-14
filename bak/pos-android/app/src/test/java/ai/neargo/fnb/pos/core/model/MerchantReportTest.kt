package ai.neargo.fnb.pos.core.model

import ai.neargo.fnb.pos.core.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 商户汇总：一个账号下餐饮店 + 零售店同一时段合计；零售退款扣在营业额里并单列；收款方式合并。 */
@OptIn(ExperimentalCoroutinesApi::class)
class MerchantReportTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }

    @Test fun diningPlusRetail() = runTest(UnconfinedTestDispatcher()) {
        val now = 1_800_000_000_000L
        val w = LocalWorkspace(Disk(), backgroundScope) { now }
        w.register("东门", "boss@dm.cn", "secret123", "secret123").getOrThrow()
        val retail = w.createStore("东门便利", "DM01", "", BizType.Retail).getOrThrow()
        w.checkoutRetail(listOf(CartLine(1, "cola", 2)), false, "现金", 100_00, true).getOrThrow()
        val sale = w.lanSnapshot().store!!.retail.sales.first()
        w.returnRetail(ReturnRequest(sale.no, mapOf(sale.lines.first().key to 1), ReturnRules.Reason.Unwanted, true, "现金"), true).getOrThrow()
        val dining = w.createStore("东门小馆", "DM02", "", BizType.Dining).getOrThrow()
        val t = w.current.value!!.tables.first().id
        w.openTable(t, 2).getOrThrow()
        w.addDish(t, w.current.value!!.products.first { !it.hasSpec && (it.stock ?: 1) > 0 }, null, 1)
        w.sendToKitchen(t, true)
        val due = OrderRules.totals(w.current.value!!.orders.getValue(t)).due
        w.settle(t, due, "银行卡", true, due).getOrThrow()

        val stores = w.visibleStores.value
        assertEquals(setOf(retail, dining), stores.map { it.id }.toSet())
        val m = MerchantReport.build(stores, now - 1, now + 1)
        val r = m.rows.first { it.storeId == retail }; val d = m.rows.first { it.storeId == dining }
        assertEquals(sale.paid - r.refund, r.revenue); assertEquals(1, r.orders); assertTrue(r.refund > 0)
        assertEquals(due, d.revenue); assertEquals(1, d.orders); assertEquals(0L, d.refund)
        assertEquals(r.revenue + d.revenue, m.revenue); assertEquals(2, m.orders)
        assertEquals(due, m.byMethod["银行卡"]); assertEquals(r.revenue, m.byMethod["现金"])
        assertEquals(mapOf(BizType.Retail to r.revenue, BizType.Dining to d.revenue), m.byBiz)
    }
}
