package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 餐饮服务只靠公共内核就能跑完一桌：开台 → 点菜 → 送厨（厨房单）→ 现金结账（收款、开钱箱、结账单）→ 清台。 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiningServiceTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }

    @Test fun fullTableThroughKernelOnly() = runTest(UnconfinedTestDispatcher()) {
        val w = LocalWorkspace(Disk(), backgroundScope) { 1_800_000_000_000L }.also { it.enterDemo(); it.selectStore("s-coast") }
        val d = DiningService(w.kernel)
        d.openTable("H02", 2).getOrThrow()
        val p = w.current.value!!.products.first { !it.hasSpec && (it.stock ?: 1) > 0 }
        assertTrue(d.addDish("H02", p, null, 2) is OrderResult.Ok)
        assertEquals(SendResult(2, false), d.sendToKitchen("H02", true))
        val s = w.current.value!!
        assertTrue(s.printJobs.any { it.kind == PrintKind.Kitchen && it.title.startsWith("H02") })
        val due = OrderRules.totals(s.order("H02")).due
        d.settle("H02", due, "现金", true, due).getOrThrow()
        val after = w.current.value!!
        assertEquals(TableStatus.Paid, after.table("H02")!!.status)
        assertEquals(due, after.payments.last().amount)
        assertTrue(after.printJobs.any { it.kind == PrintKind.Drawer } && after.printJobs.any { it.kind == PrintKind.Receipt && it.title == "H02 结账单" })
        d.startCleaning("H02").getOrThrow(); d.finishCleaning("H02").getOrThrow()
        assertEquals(TableStatus.Idle, w.current.value!!.table("H02")!!.status)
        // 流经同一个内核：LocalWorkspace 的订单流看到同样的结果
        assertTrue(w.orders.value["H02"]?.lines.isNullOrEmpty())
    }
}
