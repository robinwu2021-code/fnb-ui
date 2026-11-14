package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** R5：库存作业界面规则（账面、成本、预警、加行合并）与「预警一键建单 → 过账」联动。 */
@OptIn(ExperimentalCoroutinesApi::class)
class StockWorkTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private val now = 1_000_000L
    private val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    private suspend fun mart(scope: kotlinx.coroutines.CoroutineScope) = LocalWorkspace(Disk(), scope) { now }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }

    @Test fun alertsCostAndQuantities() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope); val ps = w.current.value!!.products; fun p(id: String) = ps.first { it.id == id }
        assertEquals(setOf("milk", "toast", "baguette"), ps.filter { StockWork.low(it) }.map { it.id }.toSet())
        assertEquals(setOf("milk", "croissant", "yogurt", "toast", "eggtart"), ps.filter { StockWork.near(it, today) }.map { it.id }.toSet())
        assertFalse(StockWork.near(p("milk"), today.minusDays(5)))
        assertEquals(949, StockWork.lineCost(p("apple"), 1236)) // 进价 7.68/kg × 1.236kg
        assertEquals(2394 * 2, StockWork.lineCost(p("milk"), 2))
        assertEquals("1.236 kg", StockWork.qtyText(p("apple"), 1236)); assertEquals("12 件", StockWork.qtyText(p("cola"), 12))
        assertEquals(500, StockWork.step(p("apple"))); assertEquals(12, StockWork.refill(p("milk"))); assertEquals(12, StockWork.refill(p("baguette")))
        assertEquals(47, StockWork.book(p("tee"))) // 规格合计
    }

    @Test fun addMergesAndCountKeepsZero() {
        var ls = StockWork.add(emptyList(), "cola", null, 12)
        ls = StockWork.add(ls, "cola", null, 12); ls = StockWork.add(ls, "tee", "r-color:white|r-size:m", 1)
        assertEquals(listOf(StockDocLine("cola", null, 24), StockDocLine("tee", "r-color:white|r-size:m", 1)), ls)
        assertEquals(1, StockWork.setQty(ls, "cola", null, 0, keepZero = false).size)
        assertEquals(0L, StockWork.setQty(ls, "cola", null, 0, keepZero = true).first().qty)
    }

    @Test fun refillLowThenPostRaisesBookAndCountUsesSnapshot() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        val low = w.current.value!!.products.filter { StockWork.low(it) }
        val no = w.newStockDoc(StockDocKind.Receive, supplier = "补货").getOrThrow()
        w.setStockDocLines(no, low.map { StockDocLine(it.id, null, StockWork.refill(it)) }).getOrThrow()
        assertEquals(StockDocState.Posted, w.advanceStockDoc(no).getOrThrow())
        assertTrue(w.current.value!!.products.none { StockWork.low(it) })
        // 盘点：提交后账面变化不影响这张单的差异
        val pd = w.newStockDoc(StockDocKind.Count).getOrThrow()
        w.setStockDocLines(pd, listOf(StockDocLine("cola", null, 30))).getOrThrow()
        assertEquals(StockDocRules.CountDiff(listOf(StockDocRules.DiffRow("cola", 36, 30)), 0, 6), StockWork.countDiff(w.current.value!!.products, w.retail.value.stockDocs.first { it.no == pd }))
        w.advanceStockDoc(pd).getOrThrow()
        w.checkoutRetail(listOf(CartLine(1, "cola", 2)), false, "现金", 1000, true).getOrThrow()
        assertEquals(6L, StockWork.countDiff(w.current.value!!.products, w.retail.value.stockDocs.first { it.no == pd }).loss)
        // 审批：按差异调整当前账面（36 → 卖 2 → 34，再盘亏 6 → 28），不是直接改成 30
        assertEquals(StockDocState.Variance, w.advanceStockDoc(pd).getOrThrow())
        assertEquals(28, w.current.value!!.products.first { it.id == "cola" }.stock)
    }
}
