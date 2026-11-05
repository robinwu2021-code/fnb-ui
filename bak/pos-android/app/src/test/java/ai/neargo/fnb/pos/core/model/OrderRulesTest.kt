package ai.neargo.fnb.pos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderRulesTest {
    private val rice = Product("9", "米饭", "Rice", "c-staple", 300, null)
    private val fish = Product("6", "酸菜鱼", "Fish", "c-sig", 7800, 2)
    private val noodles = Product("10", "担担面", "Noodles", "c-staple", 2200, 0)
    private val size = SpecGroup("g1", "份量", options = listOf(SpecOption("s", "标准"), SpecOption("l", "大份", 1000)))
    private val chicken = Product("1", "宫保鸡丁", "Chicken", "c-hot", 4800, null, listOf("g1"),
        ManageRules.skus(listOf(size), 4800, 1, emptyList()).getOrThrow())

    private fun ok(r: OrderResult) = (r as OrderResult.Ok).order

    @Test fun sameDishMerges() {
        var o = ok(OrderRules.add(TableOrder(), rice, null, 1, 1))
        o = ok(OrderRules.add(o, rice, null, 2, 2))
        assertEquals(3, o.lines.single().qty)
        assertEquals(OrderRules.DEFAULT_SPEC, o.lines.single().spec)
    }

    @Test fun skuPriceAndSeparateLines() {
        val small = chicken.skus.first { it.label == "标准" }
        val large = chicken.skus.first { it.label == "大份" }
        var o = ok(OrderRules.add(TableOrder(), chicken, small, 1, 1))
        o = ok(OrderRules.add(o, chicken, large, 1, 2))
        assertEquals(listOf(4800, 5800), o.lines.map { it.unit })
        assertTrue(OrderRules.add(TableOrder(), chicken, null, 1, 3) is OrderResult.Rejected) // 规格菜必须选 SKU
    }

    /** SKU 只剩 1 份：加满后再加被拒，其他 SKU 不受影响（必测场景 3）。 */
    @Test fun skuStockIsolated() {
        val small = chicken.skus.first { it.label == "标准" }
        val large = chicken.skus.first { it.label == "大份" }
        val o = ok(OrderRules.add(TableOrder(), chicken, small, 1, 1))
        assertTrue(OrderRules.add(o, chicken, small, 1, 2) is OrderResult.Rejected)
        assertTrue(OrderRules.add(o, chicken, large, 1, 2) is OrderResult.Ok)
    }

    @Test fun soldOutAndStockLimitRejected() {
        assertTrue(OrderRules.add(TableOrder(), noodles, null, 1, 1) is OrderResult.Rejected)
        val o = ok(OrderRules.add(TableOrder(), fish, null, 2, 1))
        assertEquals("酸菜鱼仅剩 2 份", (OrderRules.add(o, fish, null, 1, 2) as OrderResult.Rejected).reason)
    }

    @Test fun offlineSendQueuesThenBatchSentIsIdempotent() {
        var o = ok(OrderRules.add(TableOrder(), rice, null, 2, 1))
        o = OrderRules.send(o, "K1", online = false)
        assertEquals(LineStatus.Queued, o.lines.single().status)
        assertTrue(OrderRules.changeQty(o, 1, 1, rice) is OrderResult.Rejected) // 待同步不能改数量
        o = OrderRules.markBatchSent(o, "K1")
        o = OrderRules.markBatchSent(o, "K1")
        assertEquals(LineStatus.Sent, o.lines.single().status)
        assertEquals(1, o.lines.size)
    }

    @Test fun statusNeverGoesBackAndQueuedCannotAdvance() {
        var o = OrderRules.send(ok(OrderRules.add(TableOrder(), rice, null, 1, 1)), "K1", online = false)
        o = OrderRules.advance(o, 1, LineStatus.Served)
        assertEquals(LineStatus.Queued, o.lines.single().status)
        o = OrderRules.markBatchSent(o, "K1")
        o = OrderRules.advance(o, 1, LineStatus.Served)
        o = OrderRules.advance(o, 1, LineStatus.Rushing)
        assertEquals(LineStatus.Served, o.lines.single().status)
    }

    @Test fun checkoutBlockedByNewOrQueued() {
        val o = ok(OrderRules.add(TableOrder(), rice, null, 1, 1))
        assertEquals("先送厨再结账", OrderRules.checkoutBlock(OrderRules.totals(o)))
        val q = OrderRules.send(o, "K1", online = false)
        assertTrue(OrderRules.checkoutBlock(OrderRules.totals(q))!!.contains("待同步"))
        assertNull(OrderRules.checkoutBlock(OrderRules.totals(OrderRules.markBatchSent(q, "K1"))))
    }

    @Test fun totalsMatchPrototypeA03() {
        val lines = listOf(4800 to 1, 300 to 4, 7800 to 1, 3800 to 1, 6800 to 1).mapIndexed { i, (u, q) ->
            OrderLine(i.toLong(), "$i", "x", OrderRules.DEFAULT_SPEC, u, q, if (i < 2) LineStatus.New else LineStatus.Sent)
        }
        val t = OrderRules.totals(TableOrder(lines))
        assertEquals(24400, t.sub); assertEquals(1220, t.service); assertEquals(25620, t.due)
        assertEquals(8, t.items); assertEquals(5, t.newItems)
    }

    @Test fun moneyFormat() {
        assertEquals("¥256.20", yuan(25620))
        assertEquals("−¥0.05", yuan(-5))
        assertEquals("48.5", fenToInput(4850))
    }
}
