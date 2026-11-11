package ai.neargo.fnb.pos.core.model

import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** 客显屏：跟随当前桌台显示明细与合计；结账显示应付/实收/找零；收款致谢限时；空闲轮播。 */
class CustomerDisplayTest {
    private class Store : SnapshotStore {
        @Volatile var json: String? = null
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { json = source; return testReceipt(command) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }

    private suspend fun ws() = LocalWorkspace(Store(), scope).also { it.enterDemo(); it.selectStore("s-main") }
    private fun LocalWorkspace.s() = lanSnapshot().store!!

    @Test fun followsCurrentTableAndCheckout() = runBlocking {
        val w = ws()
        val t = w.s().tables.first { it.status == TableStatus.Idle }.id
        assertTrue(CustomerDisplayRules.view(w.s(), t, CustomerStage.Browse, true, true, 0) is CustomerView.Idle) // 空桌：空闲
        w.openTable(t, 2)
        w.addDish(t, w.s().products.first { it.id == "12" }, null, 2)
        val v = CustomerDisplayRules.view(w.s(), t, CustomerStage.Browse, true, true, 0) as CustomerView.Order
        assertEquals(t, v.table); assertEquals("酸梅汤", v.lines.single().name); assertEquals(2, v.lines.single().qty)
        assertNull(v.checkout)
        // 结账：现金实收 100 → 找零
        val due = v.totals.due
        val k = CustomerDisplayRules.view(w.s(), "另一桌", CustomerStage.Checkout(t, "现金", 10_000), true, true, 0) as CustomerView.Order
        assertEquals(t, k.table) // 结账阶段以结账桌台为准
        assertEquals(10_000 - due, k.checkout!!.change)
        assertNull((CustomerDisplayRules.view(w.s(), t, CustomerStage.Checkout(t, "现金", 1), true, true, 0) as CustomerView.Order).checkout!!.change) // 实收不足无找零
        // 不显示明细时仍有合计
        assertFalse((CustomerDisplayRules.view(w.s(), t, CustomerStage.Browse, false, true, 0) as CustomerView.Order).showLines)
    }

    @Test fun paidHoldsThenIdle() = runBlocking {
        val w = ws()
        val paid = CustomerStage.Paid("A01", 4_800, 200, "现金", at = 1_000)
        assertEquals(CustomerView.Paid(w.s().settings.shortName, 4_800, 200, "现金"), CustomerDisplayRules.view(w.s(), "A01", paid, true, true, 1_000 + 7_999))
        assertTrue(CustomerDisplayRules.view(w.s(), "A01", paid, true, true, 1_000 + CustomerDisplayRules.PAID_HOLD_MS) !is CustomerView.Paid)
    }

    @Test fun idleSlidesSkipSoldOutAndCanBeOff() = runBlocking {
        val w = ws()
        val s = w.s()
        val sold = s.products.first()
        val s2 = s.copy(products = s.products.map { if (it.id == sold.id) it.copy(stock = 0) else it })
        val idle = CustomerDisplayRules.view(s2, "无", CustomerStage.Browse, true, true, 0) as CustomerView.Idle
        assertTrue(idle.slides.isNotEmpty() && idle.slides.size <= CustomerDisplayRules.MAX_SLIDES)
        assertTrue(idle.slides.none { it.name == sold.name && it.price == sold.price && s2.products.count { p -> p.name == sold.name } == 1 })
        assertTrue((CustomerDisplayRules.view(s2, "无", CustomerStage.Browse, true, false, 0) as CustomerView.Idle).slides.isEmpty())
        assertTrue(CustomerDisplayRules.view(null, "无", CustomerStage.Browse, true, true, 0) is CustomerView.Idle)
    }
}
