package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

/** R3：零售收款入账——零售单、收款记录、小票、扣库存、离线进同步队列；各种拒绝。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailCheckoutTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private suspend fun mart(disk: Disk, scope: kotlinx.coroutines.CoroutineScope) = LocalWorkspace(disk, scope) { 1_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }

    // 可乐 ×2、矿泉水 ×2（第二件半价）、牛奶（会员价）、苹果 1.236kg、T 恤 白 · M、临时商品 10 元
    private val cart = listOf(
        CartLine(1, "cola", 2), CartLine(2, "water", 2), CartLine(3, "milk", 1), CartLine(4, "apple", 1, grams = 1236),
        CartLine(5, "tee", 1, sku = "白 · M"), CartLine(6, "custom", 1, customName = "临时商品", customPrice = 1000),
    )

    @Test fun totalsMatchRules() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(Disk(), backgroundScope)
        val t = RetailCheckout.totals(w.current.value!!.products, cart, member = true)
        assertEquals(700 + 400 + 3990 + 1582 + 5900 + 1000L, t.sub)
        assertEquals(100, t.promo); assertEquals(300, t.memberSave); assertEquals(2, t.round); assertEquals(13170, t.due); assertEquals(8, t.pcs)
        assertEquals(13472, RetailCheckout.totals(w.current.value!!.products, cart, member = false).sub - 100) // 非会员不减会员价
    }

    @Test fun cashCheckoutRecordsSalePaymentReceiptAndStock() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk(); val w = mart(disk, backgroundScope)
        val s0 = w.current.value!!
        val sale = w.checkoutRetail(cart, member = true, method = "现金", received = 20000, online = true).getOrThrow()
        assertEquals("#R0158", sale.no); assertEquals(13170, sale.paid); assertEquals(2, sale.discount); assertEquals("会员", sale.member)
        assertEquals(sale.paid, sale.lines.sumOf { it.amount } - sale.discount)
        assertEquals(1236, sale.lines.first { it.productId == "apple" }.grams)
        assertEquals("", sale.lines.first { it.name == "临时商品" }.productId)
        val s = w.current.value!!
        assertEquals(159, s.retail.saleSeq); assertEquals(sale, s.retail.sales.first())
        val pay = s.payments.last(); assertEquals("#R0158", pay.orderId); assertEquals(13170, pay.amount); assertEquals(20000, pay.received); assertEquals(6830, pay.change)
        assertTrue(s.printJobs.any { it.title == "零售小票 #R0158" && it.body.any { l -> "找零" in l } })
        assertEquals(s0.outbox, s.outbox)
        fun stock(id: String) = s.products.first { it.id == id }.stock
        assertEquals(34, stock("cola")); assertEquals(3, stock("milk")); assertEquals(18_500 - 1236, stock("apple"))
        assertEquals(1, s.products.first { it.id == "tee" }.skus.first { it.label == "白 · M" }.stock)
        assertTrue(s.stockMovements.any { it.reason == "销售 #R0158" })
        yield()
        val r = LocalWorkspace(disk, backgroundScope) { 1_000_000L }; r.restore(); yield(); r.selectStore(DemoCatalog.RETAIL_STORE_ID)
        assertEquals(sale, r.retail.value.sales.first())
    }

    @Test fun offlineCardGoesToOutboxAndScanIsBlocked() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(Disk(), backgroundScope)
        assertEquals("离线时扫码收款不可用", w.checkoutRetail(cart, false, "扫码", 0, online = false).exceptionOrNull()?.message)
        val sale = w.checkoutRetail(listOf(CartLine(1, "chips", 1)), false, "银行卡", 0, online = false).getOrThrow()
        val s = w.current.value!!
        assertTrue(s.outbox.any { sale.no in it.summary }); assertFalse(s.payments.last().synced)
    }

    @Test fun rejectsBadCheckouts() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(Disk(), backgroundScope)
        assertTrue(w.checkoutRetail(emptyList(), false, "现金", 0, true).isFailure)
        assertEquals("实收不足，不能确认收款", w.checkoutRetail(cart, false, "现金", 100, true).exceptionOrNull()?.message)
        assertEquals("请先识别会员", w.checkoutRetail(cart, false, "储值卡", 0, true).exceptionOrNull()?.message)
        assertTrue(w.checkoutRetail(listOf(CartLine(1, "nope", 1)), false, "现金", 9999, true).isFailure)
        assertEquals(158, w.retail.value.saleSeq)
        // 餐饮门店不能零售收款
        w.selectStore("s-main")
        assertTrue(w.checkoutRetail(listOf(CartLine(1, "1", 1)), false, "现金", 99999, true).isFailure)
    }

    @Test fun scanBufferTellsScannerFromTyping() {
        val b = ScanBuffer()
        "6954767423579".forEachIndexed { i, c -> b.char(c, 1000L + i * 8) }
        assertEquals("6954767423579", b.enter(1000L + 13 * 8))
        "123456".forEachIndexed { i, c -> b.char(c, 5000L + i * 150) } // 人手敲
        assertNull(b.enter(5000L + 6 * 150))
        "12".forEach { b.char(it, 9000) }; assertNull(b.enter(9001)) // 太短
    }
}
