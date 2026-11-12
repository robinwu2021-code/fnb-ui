package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import ai.neargo.fnb.pos.feature.manage.Section
import ai.neargo.fnb.pos.feature.manage.visibleFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** R7：零售设置接进收银 / 退货 / 价签，菜单按业态裁剪，交班退款单独计数，零售报表。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailSettingsTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private var now = 1_000_000_000L
    private suspend fun mart(scope: kotlinx.coroutines.CoroutineScope) = LocalWorkspace(Disk(), scope) { now }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }
    private suspend fun LocalWorkspace.set(f: (RetailSettings) -> RetailSettings) = saveRetailSettings(f(retail.value.settings)).getOrThrow()

    @Test fun saveValidatesAndDiningRejects() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        assertEquals(RetailSettings(), w.retail.value.settings)
        assertTrue(w.saveRetailSettings(RetailSettings(promoLimit = 100)).isFailure)
        assertTrue(w.saveRetailSettings(RetailSettings(bagIds = listOf("nope"))).isFailure)
        w.set { it.copy(expiryDays = 1, labelTpl = LabelTpl.Shelf) }
        assertEquals(1, w.retail.value.settings.expiryDays)
        w.selectStore("s-main"); assertTrue(w.saveRetailSettings(RetailSettings()).isFailure)
    }

    @Test fun autoLabelAndPromoLimit() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        w.set { it.copy(autoLabel = false) }
        w.setRetailPrice("cola", 380).getOrThrow()
        assertTrue(w.retail.value.labelQueue.none { it.productId == "cola" })
        w.set { it.copy(promoLimit = 2) }
        val ps = w.current.value!!.products
        assertEquals("农夫山泉 550ml 是促销品，每单限购 2 件", RetailCheckout.limitBlock(ps, listOf(CartLine(1, "water", 3)), 2))
        assertNull(RetailCheckout.limitBlock(ps, listOf(CartLine(1, "water", 2), CartLine(2, "cola", 9)), 2)) // 可乐没促销
        assertNull(RetailCheckout.limitBlock(ps, listOf(CartLine(1, "water", 3)), 0))
        assertEquals("农夫山泉 550ml 是促销品，每单限购 2 件", w.checkoutRetail(listOf(CartLine(1, "water", 3)), false, "现金", 10000, true).exceptionOrNull()?.message)
        assertTrue(w.checkoutRetail(listOf(CartLine(1, "water", 2)), false, "现金", 10000, true).isSuccess)
    }

    @Test fun returnWindowAuthAndMethod() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        // 原路退关掉：扫码付的单可以退现金
        w.set { it.copy(originalMethod = false) }
        assertTrue(w.returnRetail(ReturnRequest("#R0157", mapOf("L1" to 1), ReturnRules.Reason.Unwanted, true, "现金"), true).isSuccess)
        // 无单退货不要授权
        w.set { it.copy(noReceiptAuth = false) }
        assertTrue(w.returnRetail(ReturnRequest(null, mapOf("cola" to 1), ReturnRules.Reason.Wrong, true, "现金"), true).isSuccess)
        assertEquals("体验账号", w.retail.value.looseReturns.first().approver)
        // 过了退货期限
        now += 8 * 86_400_000L
        assertEquals("已超过 7 天退货期限", w.returnRetail(ReturnRequest("#R0157", mapOf("L4" to 1), ReturnRules.Reason.Unwanted, true, "扫码"), true).exceptionOrNull()?.message)
        w.set { it.copy(returnDays = 0) }
        assertTrue(w.returnRetail(ReturnRequest("#R0157", mapOf("L4" to 1), ReturnRules.Reason.Unwanted, true, "扫码"), true).isSuccess)
    }

    @Test fun menuShiftAndReport() = runTest(UnconfinedTestDispatcher()) {
        assertFalse(Section.Tables.visibleFor(BizType.Retail)); assertFalse(Section.Menus.visibleFor(BizType.Retail)); assertTrue(Section.Retail.visibleFor(BizType.Retail))
        assertTrue(Section.Tables.visibleFor(BizType.Dining)); assertFalse(Section.Retail.visibleFor(BizType.Dining))
        val sum = ShiftRules.summary(listOf(PaymentRecord("a", "零售", 1000, "现金", 10), PaymentRecord("b", "零售", -300, "现金", 20)), ShiftState())
        assertEquals(1, sum.count); assertEquals(1, sum.refundCount); assertEquals(300, sum.refund); assertEquals(700, sum.total); assertEquals(700, sum.cash)
        val w = mart(backgroundScope)
        val r = RetailReportRules.build(w.current.value!!, 0, Long.MAX_VALUE)
        val sales = w.retail.value.sales
        assertEquals(6, r.sales); assertEquals(sales.sumOf { it.paid }, r.gross); assertEquals(790 + 7580L, r.refund); assertEquals(2, r.refundCount)
        assertEquals(r.gross - r.refund, r.net)
        assertEquals("门店", r.byChannel.first().first)
    }
}

/** 报表实收不重复计算换货抵扣 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailReportExchangeTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    @Test fun exchangeCreditNotCountedTwice() = runTest(UnconfinedTestDispatcher()) {
        val w = LocalWorkspace(Disk(), backgroundScope) { 1_000_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }
        val before = RetailReportRules.build(w.current.value!!, 0, Long.MAX_VALUE).gross
        w.returnRetail(ReturnRequest("#R0155", mapOf("L1" to 1), ReturnRules.Reason.Wrong, true, "现金", exchange = true), true).getOrThrow()
        val credit = w.retail.value.credit!!.amount
        w.checkoutRetail(listOf(CartLine(1, "tee", 1, sku = "黑 · M")), false, "现金", 10000, true).getOrThrow()
        val r = RetailReportRules.build(w.current.value!!, 0, Long.MAX_VALUE)
        assertEquals(before + (5900 - credit), r.gross) // 只多了补收的差价
        assertEquals(1, r.exchangeCount)
    }
}

/** 挂单号单独编号，取走后也不复用 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailHeldNumberTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    @Test fun heldNumbersIncrease() = runTest(UnconfinedTestDispatcher()) {
        val w = LocalWorkspace(Disk(), backgroundScope) { 1_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }
        val a = w.holdCart(listOf(CartLine(1, "cola", 1)), null, "").getOrThrow()
        val b = w.holdCart(listOf(CartLine(1, "cola", 1)), null, "").getOrThrow()
        assertEquals(listOf("H0157", "H0158"), listOf(a, b)) // 演示挂单最大 H0156
        w.recallHeld(b).getOrThrow()
        assertEquals("H0159", w.holdCart(listOf(CartLine(1, "cola", 1)), null, "").getOrThrow())
        // 收款不影响挂单号
        w.checkoutRetail(listOf(CartLine(1, "cola", 1)), false, "现金", 1000, true).getOrThrow()
        assertEquals("H0160", w.holdCart(listOf(CartLine(1, "cola", 1)), null, "").getOrThrow())
    }
}
