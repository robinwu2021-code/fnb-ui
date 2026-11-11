package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

/** R2：零售数据（商品零售字段、零售单、挂单、库存单据、价签队列）的读写与持久化；餐饮门店不受影响。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailDataTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private fun ws(disk: Disk, scope: kotlinx.coroutines.CoroutineScope) = LocalWorkspace(disk, scope) { 1_000_000L }

    @Test fun demoCatalogIsComplete() = runTest(UnconfinedTestDispatcher()) {
        val w = ws(Disk(), backgroundScope); w.enterDemo(); w.selectStore(DemoCatalog.RETAIL_STORE_ID)
        val s = w.current.value!!
        assertEquals(22, s.products.size)
        val apple = s.products.first { it.id == "apple" }
        assertTrue(apple.retail!!.weighable); assertEquals("kg", apple.retail!!.unit); assertEquals(18_500, apple.stock)
        assertEquals(LinePromo.Half2, s.products.first { it.id == "water" }.retail!!.promo)
        val tee = s.products.first { it.id == "tee" }
        assertEquals(12, tee.skus.size); assertEquals(0, tee.skus.first { it.label == "白 · XL" }.stock); assertTrue(tee.skus.all { it.barcode != null })
        // 条码能找到商品
        assertEquals("cola", BarcodeRules.findByCode(s.products, "6954 7674 23579") { it.retail?.barcodes.orEmpty() }?.id)
        assertEquals(6, s.retail.sales.size); assertEquals(2, s.retail.held.size); assertEquals(6, s.retail.stockDocs.size); assertEquals(3, s.retail.labelQueue.size)
        assertEquals(ReturnRules.SaleStatus.PartialReturn, s.retail.sales.first { it.no == "#R0152" }.status)
        assertEquals(ReturnRules.SaleStatus.Returned, s.retail.sales.first { it.no == "#R0149" }.status)
        // 演示单金额自洽：行实付 − 整单优惠 = 实收
        s.retail.sales.forEach { sale -> assertEquals(sale.no, sale.lines.sumOf { it.amount } - sale.discount, sale.paid) }
    }

    @Test fun holdRecallAndStockDocsPersistAcrossRestart() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk(); val w = ws(disk, backgroundScope); w.enterDemo(); w.selectStore(DemoCatalog.RETAIL_STORE_ID)
        val held = w.holdCart(listOf(CartLine(1, "cola", 3), CartLine(2, "apple", 1, grams = 520)), null, "顾客去拿钱包").getOrThrow()
        assertEquals(3, w.retail.value.held.size)
        val back = w.recallHeld("H0156").getOrThrow(); assertEquals(3, back.lines.size); assertEquals(2, w.retail.value.held.size)
        // 入库：草稿 → 过账，账面增加并记流水
        val milk0 = w.current.value!!.products.first { it.id == "milk" }.stock!!
        assertEquals(StockDocState.Posted, w.advanceStockDoc("RK0003").getOrThrow())
        assertEquals(milk0 + 12, w.current.value!!.products.first { it.id == "milk" }.stock)
        assertTrue(w.current.value!!.stockMovements.any { it.reason == "入库 RK0003" })
        // 盘点：提交冻结快照 → 审批过账（有差异）
        val no = w.newStockDoc(StockDocKind.Count, scope = "零食").getOrThrow()
        w.setStockDocLines(no, listOf(StockDocLine("chips", qty = 30), StockDocLine("tee", "r-color:white|r-size:m", qty = 3))).getOrThrow()
        assertEquals(StockDocState.Pending, w.advanceStockDoc(no).getOrThrow())
        val doc = w.retail.value.stockDocs.first { it.no == no }
        assertEquals(mapOf("chips" to 36L, "tee#r-color:white|r-size:m" to 2L), doc.snapshot); assertEquals(1, doc.gain); assertEquals(6, doc.loss)
        assertTrue(w.setStockDocLines(no, emptyList()).isFailure) // 待审批不能再改
        assertEquals(StockDocState.Variance, w.advanceStockDoc(no).getOrThrow())
        assertEquals(30, w.current.value!!.products.first { it.id == "chips" }.stock)
        assertEquals(3, w.current.value!!.products.first { it.id == "tee" }.skus.first { it.key == "r-color:white|r-size:m" }.stock)
        // 报损：过账后扣减
        assertEquals(StockDocState.Posted, w.advanceStockDoc("BS0002").getOrThrow())
        assertEquals(32, w.current.value!!.products.first { it.id == "eggtart" }.stock)
        // 作废：只有草稿 / 盘点中能作废
        assertTrue(w.voidStockDoc("RK0003").isFailure)
        yield()
        val r = ws(disk, backgroundScope); r.restore(); yield()
        val s = r.stores.value.first { it.id == DemoCatalog.RETAIL_STORE_ID }; assertEquals(BizType.Retail, s.biz)
        r.selectStore(DemoCatalog.RETAIL_STORE_ID)
        assertEquals(w.retail.value, r.retail.value)
        assertEquals(30, r.current.value!!.products.first { it.id == "chips" }.stock)
        assertTrue(r.retail.value.held.any { it.no == held })
    }

    @Test fun diningStoreRejectsRetailOpsAndIsUnchanged() = runTest(UnconfinedTestDispatcher()) {
        val w = ws(Disk(), backgroundScope); w.enterDemo(); w.selectStore("s-main")
        val before = w.current.value!!
        assertTrue(w.holdCart(listOf(CartLine(1, "1", 1)), null, "").isFailure)
        assertTrue(w.newStockDoc(StockDocKind.Receive).isFailure)
        assertEquals(RetailData(), w.retail.value)
        assertEquals(before, w.current.value)
        assertTrue(before.products.all { it.retail == null })
    }

    @Test fun emptyRetailStoreFromR0IsUpgradedToFullCatalog() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk(); val w = ws(disk, backgroundScope); w.enterDemo(); w.selectStore("s-main"); yield()
        // 模拟 R0 时补进来的空零售门店：去掉商品与零售数据
        val json = kotlinx.serialization.json.Json.parseToJsonElement(disk.data!!).let { root ->
            fun strip(e: kotlinx.serialization.json.JsonElement, inMart: Boolean): kotlinx.serialization.json.JsonElement = when (e) {
                is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(e.mapValues { (k, v) ->
                    when {
                        inMart && k == "products" -> kotlinx.serialization.json.JsonArray(emptyList())
                        inMart && k == "retail" -> kotlinx.serialization.json.JsonObject(emptyMap())
                        else -> strip(v, inMart || k == DemoCatalog.RETAIL_STORE_ID)
                    }
                })
                is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map { strip(it, inMart) })
                else -> e
            }
            strip(root, false)
        }
        disk.data = json.toString()
        val r = ws(disk, backgroundScope); r.restore(); yield(); r.selectStore(DemoCatalog.RETAIL_STORE_ID)
        assertEquals(22, r.current.value!!.products.size)
        assertEquals(6, r.retail.value.sales.size)
    }
}
