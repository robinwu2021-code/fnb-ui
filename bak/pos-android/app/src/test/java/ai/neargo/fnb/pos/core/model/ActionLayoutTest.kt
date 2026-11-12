package ai.neargo.fnb.pos.core.model

import org.junit.Assert.*
import org.junit.Test

/** 操作布局规则：默认、整理、拖动校验、宽度、落点换算、存取。 */
class ActionLayoutTest {
    private val L = ActionLayout

    @Test fun defaultsPutOnlyLineActionsInRail() {
        val d = L.defaults(BizType.Dining)
        assertEquals(listOf("line.delete", "line.note", "line.gift", "line.void", "line.rush", "line.serve"), d.rail)
        assertTrue(d.more.all { L.spec(BizType.Dining, it)!!.scope == ActionScope.Table })
        val r = L.defaults(BizType.Retail)
        assertTrue("add.custom" in r.rail)            // 临时商品新增一行，和行操作放一起
        assertEquals(listOf("order.clear", "device.drawer"), r.more)
    }

    @Test fun normalizeFixesBadSavedConfig() {
        val saved = RailConfig(listOf("table.change", "line.note", "line.note", "nope"), listOf("line.delete"))
        val n = L.normalize(saved, BizType.Dining)
        assertEquals("line.note", n.rail.first())
        assertFalse("table.change" in n.rail)          // 桌台级不能进竖条
        assertTrue("table.change" in n.more)
        assertTrue("line.delete" in n.more)            // 用户放进「更多」的保留在「更多」
        assertFalse("nope" in n.rail + n.more)
        assertEquals(L.catalog(BizType.Dining).size, (n.rail + n.more).toSet().size) // 新增的操作补上，没有重复
        assertEquals(L.defaults(BizType.Retail), L.normalize(null, BizType.Retail))
    }

    @Test fun dragAcrossDividerAndValidate() {
        val d = L.defaults(BizType.Retail)
        val list = d.rail + L.DIVIDER + d.more
        // 把「开钱箱」拖进竖条：拒绝
        val bad = L.drag(list, list.indexOf("device.drawer"), 0)
        assertTrue(L.fromList(bad.filter { it != L.DIVIDER }, bad.indexOf(L.DIVIDER), BizType.Retail).isFailure)
        // 把「删除」拖到「更多」：可以
        val ok = L.drag(list, 0, list.size - 1)
        val c = L.fromList(ok.filter { it != L.DIVIDER }, ok.indexOf(L.DIVIDER), BizType.Retail).getOrThrow()
        assertFalse("line.delete" in c.rail); assertEquals("line.delete", c.more.last())
    }

    @Test fun widthsClamp() {
        assertEquals(400, L.billWidth(0, 400)); assertEquals(320, L.billWidth(100, 400)); assertEquals(480, L.billWidth(900, 400))
        assertEquals(80, L.railWidth(0)); assertEquals(64, L.railWidth(10)); assertEquals(96, L.railWidth(200))
    }

    @Test fun dropToLayout() {
        assertEquals(BillSide.Left, L.billDrop(100f, 1000f)); assertEquals(BillSide.Right, L.billDrop(900f, 1000f))
        assertEquals(CatSide.Top, L.catDrop(500f, 20f, 1000f, 600f))
        assertEquals(CatSide.Right, L.catDrop(980f, 300f, 1000f, 600f))
        assertEquals(OrderLayout.L1, L.layoutOf(CatSide.Left, BillSide.Right))
        assertEquals(OrderLayout.L3, L.layoutOf(CatSide.Top, BillSide.Left))
        assertEquals(OrderLayout.L4, L.layoutOf(CatSide.Left, BillSide.Left))   // 同一侧：分类换到另一侧
        assertEquals(OrderLayout.L1, L.layoutOf(CatSide.Right, BillSide.Right))
        OrderLayout.entries.forEach { assertEquals(it, L.layoutOf(it.cat, it.bill)) }
    }

    @Test fun encodeRoundTrip() {
        val c = RailConfig(listOf("line.note", "line.delete"), listOf("table.change"))
        assertEquals(c, L.decode(L.encode(c)))
        assertEquals(RailConfig(emptyList(), listOf("table.change")), L.decode(L.encode(RailConfig(emptyList(), listOf("table.change")))))
        assertNull(L.decode("")); assertNull(L.decode(null))
    }
}
