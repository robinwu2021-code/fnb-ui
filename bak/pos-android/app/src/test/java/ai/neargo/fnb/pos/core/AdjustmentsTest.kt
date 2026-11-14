package ai.neargo.fnb.pos.core

import ai.neargo.fnb.pos.core.model.*
import org.junit.Assert.*
import org.junit.Test

class AdjustmentsTest {
    private val line = OrderLine(1, "p", "菜品", "标准", 1000, 2, LineStatus.Sent)
    @Test fun giftAndDiscountUseSnapshotsAndDoNotMergeWithNewPaidLines() {
        val order = TableOrder(listOf(line))
        val gift = (AdjustmentRules.apply(order, AdjustmentRequest(AdjustmentKind.Gift, 1, reason = "顾客生日")) as OrderResult.Ok).order
        assertEquals(0, OrderRules.totals(gift).due)
        assertEquals(1000, gift.lines[0].unit)
        assertTrue(AdjustmentRules.apply(gift, AdjustmentRequest(AdjustmentKind.Gift, 1, reason = "顾客生日")) is OrderResult.Rejected)
        val discounted = (AdjustmentRules.apply(order, AdjustmentRequest(AdjustmentKind.Discount, percent = 80, reason = "主管优惠")) as OrderResult.Ok).order
        assertEquals(1700, OrderRules.totals(discounted).due) // 2000 + 100 service - 400
    }
    @Test fun voidRequiresValidQuantityAndSyncedLine() {
        val order = TableOrder(listOf(line))
        val one = (AdjustmentRules.apply(order, AdjustmentRequest(AdjustmentKind.Void, 1, 1, reason = "菜品售罄")) as OrderResult.Ok).order
        assertEquals(1, one.lines.single().qty)
        assertTrue(AdjustmentRules.apply(order, AdjustmentRequest(AdjustmentKind.Void, 1, 3, reason = "菜品售罄")) is OrderResult.Rejected)
        assertTrue(AdjustmentRules.apply(order.copy(lines = listOf(line.copy(status = LineStatus.Queued))), AdjustmentRequest(AdjustmentKind.Void, 1, reason = "菜品售罄")) is OrderResult.Rejected)
    }
    @Test fun cashRejectsInsufficientAndFractionalPrecision() {
        assertNull(CashRules.change("19.99", 2000))
        assertEquals(3000L, CashRules.change("50", 2000))
        assertNull(CashRules.amount("1.234"))
        assertEquals("1.23", CashRules.key("1.23", "4"))
        assertEquals("0.", CashRules.key("", "."))
    }
}
