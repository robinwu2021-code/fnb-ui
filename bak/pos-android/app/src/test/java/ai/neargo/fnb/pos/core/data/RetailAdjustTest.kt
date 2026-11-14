package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 购物车改价、备注、整单折扣（主管授权）、优惠活动；入账后整单优惠记在单上，退货照样分摊。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailAdjustTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private suspend fun mart(scope: kotlinx.coroutines.CoroutineScope) = LocalWorkspace(Disk(), scope) { 1_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }

    @Test fun repriceAndNoteNeedApprovalAndShowOnReceipt() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        val lines = listOf(CartLine(1, "milk", 2, price = 3500, note = "礼品包装"), CartLine(2, "water", 2))
        assertEquals("改价、整单折扣需要主管授权", w.checkoutRetail(lines, true, "现金", 99999, true).exceptionOrNull()?.message)
        assertTrue(w.authorize("manager@demo.local", "bad").isFailure)
        val by = w.authorize("manager@demo.local", "demo12345").getOrThrow()
        val sale = w.checkoutRetail(lines, true, "现金", 99999, true, CheckoutExtras(approver = by)).getOrThrow()
        // 改过价的行不叠加会员价：7000；矿泉水第二件半价 300
        assertEquals(7000, sale.lines.first { it.productId == "milk" }.amount)
        assertEquals("礼品包装", sale.lines.first { it.productId == "milk" }.note)
        assertEquals("改价 1 行（授权：manager@demo.local）", sale.adjust)
        val receipt = w.current.value!!.printJobs.last { it.title == "零售小票 ${sale.no}" }.body
        assertTrue(receipt.contains("  备注：礼品包装")); assertTrue(receipt.contains(sale.adjust))
        // 改价的行不会被新扫的同款合并
        assertEquals(2, RetailCartRules.scanAdd(listOf(CartLine(1, "milk", 1, price = 3500)), CartLine(0, "milk", 1)) { 9 }.lines.size)
    }

    @Test fun orderDiscountAndPromotionsGoIntoSaleDiscount() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        val s = w.current.value!!
        val lines = listOf(CartLine(1, "tee", 2, sku = "黑 · M")) // 118.00
        assertEquals(listOf("promo-200-10", "promo-100-5"), s.promotions.map { it.id })
        assertNotNull(RetailCheckout.promotionBlock(s.products, s.promotions, lines, listOf("promo-200-10"))) // 未满 200
        val x = CheckoutExtras(90, listOf("promo-100-5"), "店长")
        val t = RetailCheckout.totalsWith(s.products, s.promotions, lines, false, x)
        assertEquals(1180, t.manual); assertEquals(500, t.coupon) // 118 × 10% ；满100减5
        assertEquals(11800 - 1180 - 500 - t.round, t.due)
        val sale = w.checkoutRetail(lines, false, "现金", 20000, true, x).getOrThrow()
        assertEquals(t.due, sale.paid); assertEquals(t.round + 1180 + 500, sale.discount)
        assertEquals(sale.paid, sale.lines.sumOf { it.amount } - sale.discount)
        assertTrue(sale.adjust.startsWith("整单 9 折 · 满100减5"))
        // 退一件：整单优惠按比例分摊
        val r = w.returnRetail(ReturnRequest(sale.no, mapOf("L1" to 1), ReturnRules.Reason.Unwanted, true, "现金"), true).getOrThrow()
        assertEquals(ReturnRules.refundAmount(sale.soldLines, sale.discount, mapOf("L1" to 1)), r.amount)
        assertTrue(r.amount < 5900)
        assertTrue(w.checkoutRetail(lines, false, "现金", 20000, true, CheckoutExtras(40, approver = "店长")).isFailure) // 低于 5 折
    }
}
