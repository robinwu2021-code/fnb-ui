package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 线上订单：来单 → 接单（拣货单）→ 拣货完成 → 取走完成（记零售单、扣库存、线上支付）；拒单；自动接单。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailOnlineTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private suspend fun mart(scope: kotlinx.coroutines.CoroutineScope) = LocalWorkspace(Disk(), scope) { 1_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }

    @Test fun fullFlowRecordsSaleAndStock() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        val no = w.simulateOnlineOrder(SaleChannel.Mini).getOrThrow()
        val o = w.retail.value.online.first { it.no == no }
        assertEquals("XC0001", no); assertEquals(OnlineState.New, o.state); assertTrue(o.lines.isNotEmpty())
        assertEquals(RetailCheckout.totals(w.current.value!!.products, o.lines, false).due, o.amount)
        val stock0 = o.lines.associate { l -> l.productId to w.current.value!!.products.first { it.id == l.productId }.stock!! }
        val cash0 = ShiftRules.summary(w.current.value!!.payments, w.current.value!!.shift).cash
        assertEquals(OnlineState.Picking, w.advanceOnline(no).getOrThrow())
        assertTrue(w.current.value!!.printJobs.any { it.title == "拣货单 $no" })
        assertEquals(OnlineState.Ready, w.advanceOnline(no).getOrThrow())
        assertEquals(OnlineState.Done, w.advanceOnline(no).getOrThrow())
        val done = w.retail.value.online.first { it.no == no }
        val sale = w.retail.value.sales.first { it.no == done.saleNo }
        assertEquals(SaleChannel.Mini, sale.channel); assertEquals(o.amount, sale.paid); assertEquals(listOf(SalePay(ONLINE_PAY, o.amount)), sale.pays)
        assertEquals(sale.paid, sale.lines.sumOf { it.amount } - sale.discount)
        o.lines.forEach { l -> assertEquals(stock0.getValue(l.productId) - l.qty, w.current.value!!.products.first { it.id == l.productId }.stock) }
        assertEquals(ONLINE_PAY, w.current.value!!.payments.last().method)
        assertEquals(cash0, ShiftRules.summary(w.current.value!!.payments, w.current.value!!.shift).cash) // 不进现金
        assertTrue(w.advanceOnline(no).isFailure); assertTrue(w.rejectOnline(no, "缺货").isFailure)
    }

    @Test fun rejectAndAutoAccept() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        val a = w.simulateOnlineOrder(SaleChannel.Platform).getOrThrow()
        assertEquals("PT0001", a); assertTrue(w.retail.value.online.first().delivery)
        assertTrue(w.rejectOnline(a, " ").isFailure)
        w.rejectOnline(a, "商品缺货").getOrThrow()
        assertEquals(OnlineState.Rejected, w.retail.value.online.first { it.no == a }.state)
        w.saveRetailSettings(w.retail.value.settings.copy(autoAccept = true, printPick = false)).getOrThrow()
        val b = w.simulateOnlineOrder(SaleChannel.Mini).getOrThrow()
        assertEquals(OnlineState.Picking, w.retail.value.online.first { it.no == b }.state)
        assertTrue(w.current.value!!.printJobs.none { it.title == "拣货单 $b" })
        assertTrue(w.simulateOnlineOrder(SaleChannel.Store).isFailure)
    }
}
