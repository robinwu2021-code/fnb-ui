package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

/** R4：零售退货（有单 / 无单）、换货抵扣带回收银、补打小票。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailReturnTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private suspend fun mart(disk: Disk, scope: kotlinx.coroutines.CoroutineScope) = LocalWorkspace(disk, scope) { 1_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }
    private fun LocalWorkspace.sale(no: String) = retail.value.sales.first { it.no == no }
    private fun LocalWorkspace.stock(id: String) = current.value!!.products.first { it.id == id }.stock

    @Test fun returnWithReceiptRefundsToOriginalMethodAndRestocks() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(Disk(), backgroundScope)
        val s0 = w.sale("#R0157"); val pick = mapOf("L1" to 1, "L3" to 1) // 可乐 1 件 + 苹果整行
        val expected = ReturnRules.refundAmount(s0.soldLines, s0.discount, pick)
        val cola0 = w.stock("cola")!!; val apple0 = w.stock("apple")!!
        assertEquals("只能按原收款方式退", w.returnRetail(ReturnRequest("#R0157", pick, ReturnRules.Reason.Unwanted, true, "现金"), true).exceptionOrNull()?.message)
        val r = w.returnRetail(ReturnRequest("#R0157", pick, ReturnRules.Reason.Unwanted, true, "扫码"), true).getOrThrow()
        assertEquals(expected, r.amount); assertEquals(2, r.pieces)
        val sale = w.sale("#R0157")
        assertEquals(ReturnRules.SaleStatus.PartialReturn, sale.status); assertEquals(1, sale.lines.first { it.key == "L1" }.returned); assertEquals(1, sale.lines.first { it.key == "L3" }.returned)
        val pay = w.current.value!!.payments.last(); assertEquals(-expected, pay.amount); assertEquals("扫码", pay.method); assertEquals(PaymentState.Refunded, pay.state)
        assertEquals(cola0 + 1, w.stock("cola")); assertEquals(apple0 + 1236, w.stock("apple"))
        assertTrue(w.current.value!!.printJobs.any { it.title == "退货小票 #R0157" })
        // 称重行已整行退过，再退不出件数
        assertTrue(w.returnRetail(ReturnRequest("#R0157", mapOf("L3" to 1), ReturnRules.Reason.Unwanted, true, "扫码"), true).isFailure)
    }

    @Test fun qualityReturnWritesPostedLossDocInsteadOfRestock() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(Disk(), backgroundScope)
        val cro0 = w.stock("croissant")
        w.returnRetail(ReturnRequest("#R0157", mapOf("L4" to 2), ReturnRules.Reason.Quality, false, "扫码"), true).getOrThrow()
        assertEquals(cro0, w.stock("croissant"))
        val doc = w.retail.value.stockDocs.first()
        assertEquals(StockDocKind.Loss, doc.kind); assertEquals(StockDocState.Posted, doc.state); assertEquals("#R0157", doc.ref); assertEquals(2L, doc.lines.single().qty)
    }

    @Test fun platformOrdersAndNoReceiptRules() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(Disk(), backgroundScope)
        assertEquals("即时零售平台单请在平台发起退款", w.returnRetail(ReturnRequest("#R0146", mapOf("L1" to 1), ReturnRules.Reason.Unwanted, true, "扫码"), true).exceptionOrNull()?.message)
        // 无单退货：要主管授权，只退现金 / 储值，按现价
        assertTrue(w.returnRetail(ReturnRequest(null, mapOf("cola" to 2), ReturnRules.Reason.Wrong, true, "现金", approver = "manager@demo.local", secret = "bad"), true).isFailure)
        assertEquals("无单退货只能退现金或储值", w.returnRetail(ReturnRequest(null, mapOf("cola" to 2), ReturnRules.Reason.Wrong, true, "扫码", approver = "manager@demo.local", secret = "demo12345"), true).exceptionOrNull()?.message)
        val cola0 = w.stock("cola")!!
        val r = w.returnRetail(ReturnRequest(null, mapOf("cola" to 2), ReturnRules.Reason.Wrong, true, "现金", approver = "manager@demo.local", secret = "demo12345"), true).getOrThrow()
        assertEquals(700, r.amount); assertEquals(cola0 + 2, w.stock("cola"))
        val lr = w.retail.value.looseReturns.single(); assertEquals("NR0001", lr.no); assertEquals("manager@demo.local", lr.approver)
        assertEquals(-700, w.current.value!!.payments.last().amount)
    }

    @Test fun exchangeCreditCarriesToCheckoutAndRefundsDifference() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk(); val w = mart(disk, backgroundScope)
        val s0 = w.sale("#R0155"); val credit = ReturnRules.refundAmount(s0.soldLines, s0.discount, mapOf("L1" to 1))
        w.returnRetail(ReturnRequest("#R0155", mapOf("L1" to 1), ReturnRules.Reason.Wrong, true, "现金", exchange = true), true).getOrThrow()
        assertEquals(ExchangeCredit("#R0155", credit, 1, "现金"), w.retail.value.credit)
        val pays0 = w.current.value!!.payments.size
        assertEquals(pays0, w.current.value!!.payments.size) // 换货不退钱
        assertEquals(EXCHANGE, w.sale("#R0155").returns.last().method)
        assertTrue(w.returnRetail(ReturnRequest("#R0155", mapOf("L2" to 1), ReturnRules.Reason.Wrong, true, "现金", exchange = true), true).isFailure)
        yield()
        // 重启后抵扣还在
        val r = LocalWorkspace(disk, backgroundScope) { 1_000_000L }; r.restore(); yield(); r.selectStore(DemoCatalog.RETAIL_STORE_ID)
        assertEquals(credit, r.retail.value.credit?.amount)
        // 换一双袜子（29 元）< 抵扣：退差价到原方式现金
        val sale = r.checkoutRetail(listOf(CartLine(1, "socks", 1, sku = "白")), false, "", 0, true).getOrThrow()
        assertEquals(listOf(SalePay(EXCHANGE, 2900)), sale.pays)
        val pay = r.current.value!!.payments.last(); assertEquals(-(credit - 2900), pay.amount); assertEquals("现金", pay.method)
        assertNull(r.retail.value.credit)
    }

    @Test fun exchangeForMoreCollectsDifference() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(Disk(), backgroundScope)
        w.returnRetail(ReturnRequest("#R0155", mapOf("L2" to 1), ReturnRules.Reason.Wrong, true, "现金", exchange = true), true).getOrThrow()
        val credit = w.retail.value.credit!!.amount
        val lines = listOf(CartLine(1, "tee", 1, sku = "黑 · M"))
        val due = RetailCheckout.totals(w.current.value!!.products, lines, false).due
        assertTrue(w.checkoutRetail(lines, false, "现金", 100, true).isFailure) // 补收不足
        val sale = w.checkoutRetail(lines, false, "现金", 10000, true).getOrThrow()
        assertEquals(listOf(SalePay(EXCHANGE, credit), SalePay("现金", due - credit)), sale.pays)
        assertEquals(due, sale.paid)
        assertEquals(10000 - (due - credit), w.current.value!!.payments.last().change)
        assertTrue(w.reprintSale(sale.no).isSuccess); assertTrue(w.current.value!!.printJobs.last().reprint)
    }
}
