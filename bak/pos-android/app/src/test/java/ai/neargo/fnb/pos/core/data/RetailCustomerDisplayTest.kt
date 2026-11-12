package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** R8（客显部分）：零售购物车快照、空闲轮播特惠、收款与换货退差价。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailCustomerDisplayTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private suspend fun mart(scope: kotlinx.coroutines.CoroutineScope) = LocalWorkspace(Disk(), scope) { 1_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }
    private val lines = listOf(CartLine(1, "water", 2), CartLine(2, "milk", 1), CartLine(3, "banana", 1, grams = 618))

    @Test fun cartSnapshotMatchesCheckoutMath() = runTest(UnconfinedTestDispatcher()) {
        val s = mart(backgroundScope).current.value!!
        val k = CustomerDisplayRules.retailCart(s.products, lines, member = true, last = 3, method = "现金", received = 10000)
        val t = RetailCheckout.totals(s.products, lines, true)
        assertEquals(t.due, k.due); assertEquals(t.save, k.save); assertEquals(t.sub, k.sub); assertEquals(4, k.pieces)
        assertEquals("第二件半价 −¥1.00", k.lines[0].note); assertEquals(300, k.lines[0].amount)
        assertEquals("会员价 −¥3.00", k.lines[1].note)
        assertEquals("0.618 kg", k.lines[2].qty); assertEquals("香蕉", k.last)
        assertEquals(CustomerCheckout("现金", 10000, 10000 - t.due), k.pay)
        // 换货抵扣大于新商品：退差价
        val x = CustomerDisplayRules.retailCart(s.products, listOf(CartLine(1, "water", 1)), false, 1, credit = 5310)
        assertEquals(0, x.due); assertEquals(5310 - 200, x.refund)
    }

    @Test fun viewForRetailStore() = runTest(UnconfinedTestDispatcher()) {
        val s = mart(backgroundScope).current.value!!
        val cart = CustomerDisplayRules.retailCart(s.products, lines, false, 1)
        assertTrue(CustomerDisplayRules.view(s, "", CustomerStage.Retail(cart), true, true, 0) is CustomerView.Retail)
        val idle = CustomerDisplayRules.view(s, "", CustomerStage.Browse, true, true, 0) as CustomerView.Idle
        assertTrue(idle.slides.first().note.isNotEmpty()) // 特惠排前面
        assertTrue(idle.slides.none { it.name == "法棍" }) // 缺货不轮播
        val paid = CustomerDisplayRules.view(s, "", CustomerStage.Paid("零售", 10000, 2670, "现金", 100), true, true, 200)
        assertEquals(CustomerView.Paid(s.settings.shortName, 10000, 2670, "现金"), paid)
        // 致谢页过了停留时间，回到空闲
        assertTrue(CustomerDisplayRules.view(s, "", CustomerStage.Paid("零售", 1, 0, "现金", 0), true, true, CustomerDisplayRules.PAID_HOLD_MS + 1) is CustomerView.Idle)
    }
}
