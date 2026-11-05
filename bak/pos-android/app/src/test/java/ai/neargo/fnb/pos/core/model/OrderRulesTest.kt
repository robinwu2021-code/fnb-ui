package ai.neargo.fnb.pos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderRulesTest {
    private val rice = Dish("9", "米饭", "Rice", "主食", 300, null)
    private val fish = Dish("6", "酸菜鱼", "Fish", "招牌", 7800, 2)
    private val noodles = Dish("10", "担担面", "Noodles", "主食", 2200, 0)

    private fun ok(r: OrderResult) = (r as OrderResult.Ok).order

    @Test fun sameDishSameSpecMerges() {
        var o = ok(OrderRules.add(TableOrder(), rice, "", 300, 1, 1))
        o = ok(OrderRules.add(o, rice, "", 300, 2, 2))
        assertEquals(1, o.lines.size)
        assertEquals(3, o.lines.single().qty)
        assertEquals(OrderRules.DEFAULT_SPEC, o.lines.single().spec)
    }

    @Test fun differentSpecSplitsLines() {
        var o = ok(OrderRules.add(TableOrder(), rice, "标准", 300, 1, 1))
        o = ok(OrderRules.add(o, rice, "大份", 400, 1, 2))
        assertEquals(2, o.lines.size)
    }

    @Test fun soldOutAndStockLimitRejected() {
        assertTrue(OrderRules.add(TableOrder(), noodles, "", 2200, 1, 1) is OrderResult.Rejected)
        val o = ok(OrderRules.add(TableOrder(), fish, "", 7800, 2, 1))
        val r = OrderRules.add(o, fish, "", 7800, 1, 2)
        assertTrue(r is OrderResult.Rejected)
        assertEquals("酸菜鱼仅剩 2 份", (r as OrderResult.Rejected).reason)
    }

    @Test fun sentLineCannotChangeQty() {
        val o = OrderRules.sendAll(ok(OrderRules.add(TableOrder(), rice, "", 300, 1, 1)))
        assertTrue(OrderRules.changeQty(o, 1, -1, rice) is OrderResult.Rejected)
    }

    @Test fun decreaseToZeroRemovesDraft() {
        val o = ok(OrderRules.add(TableOrder(), rice, "", 300, 1, 1))
        assertTrue(ok(OrderRules.changeQty(o, 1, -1, rice)).lines.isEmpty())
    }

    @Test fun statusNeverGoesBack() {
        var o = OrderRules.sendAll(ok(OrderRules.add(TableOrder(), rice, "", 300, 1, 1)))
        o = OrderRules.advance(o, 1, LineStatus.Served)
        o = OrderRules.advance(o, 1, LineStatus.Rushing) // 乱序回执
        assertEquals(LineStatus.Served, o.lines.single().status)
    }

    @Test fun draftCannotBeAdvanced() {
        val o = OrderRules.advance(ok(OrderRules.add(TableOrder(), rice, "", 300, 1, 1)), 1, LineStatus.Served)
        assertEquals(LineStatus.New, o.lines.single().status)
    }

    @Test fun totalsMatchPrototypeA03() {
        // 宫保鸡丁 48 + 米饭 3×4 + 酸菜鱼 78 + 夫妻肺片 38 + 小炒黄牛肉 68 = 244；服务费 5% = 12.20；应收 256.20
        val lines = listOf(4800 to 1, 300 to 4, 7800 to 1, 3800 to 1, 6800 to 1).mapIndexed { i, (u, q) ->
            OrderLine(i.toLong(), "$i", "x", OrderRules.DEFAULT_SPEC, u, q, if (i < 2) LineStatus.New else LineStatus.Sent)
        }
        val t = OrderRules.totals(TableOrder(lines))
        assertEquals(24400, t.sub); assertEquals(1220, t.service); assertEquals(25620, t.due)
        assertEquals(8, t.items); assertEquals(5, t.newItems)
        assertEquals(1220, OrderRules.totals(TableOrder(lines, member = true)).discount)
    }

    @Test fun moneyFormat() {
        assertEquals("¥256.20", yuan(25620))
        assertEquals("−¥0.05", yuan(-5))
    }
}
