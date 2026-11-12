package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

/** R6：改价进待打价签、手动加入、打价签（TSPL 指令）、价签条码扫回来能认出商品。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailGoodsTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private suspend fun mart(disk: Disk, scope: kotlinx.coroutines.CoroutineScope) = LocalWorkspace(disk, scope) { 1_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }

    @Test fun labelFaceAndTspl() = runTest(UnconfinedTestDispatcher()) {
        val ps = mart(Disk(), backgroundScope).current.value!!.products; fun p(id: String) = ps.first { it.id == id }
        val apple = LabelRules.face(p("apple"))
        assertEquals("¥12.80/kg", apple.price); assertEquals("每 500g ¥6.40", apple.unit); assertNull(apple.member)
        assertEquals(BarcodeRules.toEan13("PLU 1001"), apple.ean13); assertEquals(13, apple.ean13.length)
        assertEquals("会员价 ¥36.90", LabelRules.face(p("milk")).member)
        assertEquals("6954767423579", LabelRules.face(p("cola")).ean13)
        val prog = LabelRules.tspl(p("cola"), LabelTpl.L60x40)
        assertEquals("SIZE 60 mm,40 mm", prog.first()); assertEquals("PRINT 1,1", prog.last())
        assertTrue(prog.any { it.startsWith("BARCODE") && it.endsWith("\"695476742357\"") })
        assertTrue(LabelRules.tspl(p("cola"), LabelTpl.Shelf).first() == "SIZE 85 mm,24 mm")
        // 价签上印的 13 位码扫回来能认出商品（店内码、PLU 打价签时补成了 13 位）
        assertEquals("bag", BarcodeRules.findByCode(ps, BarcodeRules.toEan13("2000001")) { it.retail?.barcodes.orEmpty() }?.id)
        assertEquals("apple", BarcodeRules.findByCode(ps, apple.ean13) { it.retail?.barcodes.orEmpty() }?.id)
    }

    @Test fun priceChangeQueuesLabelAndPrintClearsQueue() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk(); val w = mart(disk, backgroundScope)
        assertEquals("售价没有变化", w.setRetailPrice("cola", 350).exceptionOrNull()?.message)
        w.setRetailPrice("cola", 380).getOrThrow()
        assertEquals(380, w.current.value!!.products.first { it.id == "cola" }.price)
        assertEquals(LabelJob("cola", LabelWhy.Price, 350), w.retail.value.labelQueue.first())
        // 已在队列里：保留最早的原价
        w.setRetailPrice("milk", 3890).getOrThrow()
        assertEquals(LabelJob("milk", LabelWhy.Price, 4290), w.retail.value.labelQueue.first())
        // 规格商品：规格价一起改
        w.setRetailPrice("tee", 6900).getOrThrow()
        assertTrue(w.current.value!!.products.first { it.id == "tee" }.skus.all { it.price == 6900L })
        // 收银按新价算
        assertEquals(760, RetailCheckout.totals(w.current.value!!.products, listOf(CartLine(1, "cola", 2)), false).sub)
        w.queueLabel("water").getOrThrow(); w.queueLabel("water").getOrThrow()
        assertEquals(1, w.retail.value.labelQueue.count { it.productId == "water" })
        val n = w.printLabels(listOf("cola", "water"), LabelTpl.L40x30).getOrThrow()
        assertEquals(2, n)
        assertTrue(w.retail.value.labelQueue.none { it.productId in setOf("cola", "water") })
        val rec = w.retail.value.labelPrints.first(); assertEquals(listOf("cola", "water"), rec.productIds); assertEquals(2, rec.program.count { it.startsWith("PRINT") })
        assertTrue(w.printLabels(emptyList(), LabelTpl.L60x40).isFailure)
        yield()
        val r = LocalWorkspace(disk, backgroundScope) { 1_000_000L }; r.restore(); yield(); r.selectStore(DemoCatalog.RETAIL_STORE_ID)
        assertEquals(w.retail.value.labelPrints, r.retail.value.labelPrints)
        // 餐饮门店不能用
        w.selectStore("s-main"); assertTrue(w.queueLabel("1").isFailure)
    }
}
