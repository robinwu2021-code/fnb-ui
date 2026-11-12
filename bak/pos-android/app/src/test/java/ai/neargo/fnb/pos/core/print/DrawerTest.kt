package ai.neargo.fnb.pos.core.print

import ai.neargo.fnb.pos.core.data.DemoCatalog
import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.model.CartLine
import ai.neargo.fnb.pos.core.model.PrintKind
import ai.neargo.fnb.pos.core.model.PrintState
import ai.neargo.fnb.pos.core.model.PrinterConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** 钱箱：现金收款 / 退款自动开、手动开；发到前台小票机的是 ESC p 脉冲（不出纸）；刷卡不开。 */
class DrawerTest {
    private class Store : SnapshotStore {
        @Volatile var json: String? = null
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { json = source; return testReceipt(command) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }

    private fun LocalWorkspace.drawers() = lanSnapshot().store!!.printJobs.filter { it.kind == PrintKind.Drawer }

    @Test fun cashOpensDrawerCardDoesNot() = runBlocking {
        FakeEscPosPrinter().use { printer ->
            val w = LocalWorkspace(Store(), scope) { 1_800_000_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }
            w.savePrinter(PrinterConfig("p1", "前台", "127.0.0.1", printer.port)).getOrThrow()
            w.setRoute("前台", "p1").getOrThrow()
            w.checkoutRetail(listOf(CartLine(1, "cola", 1)), false, "银行卡", 0, true).getOrThrow()
            assertTrue(w.drawers().isEmpty())
            w.checkoutRetail(listOf(CartLine(1, "cola", 1)), false, "现金", 10_00, true).getOrThrow()
            assertEquals(1, w.drawers().size)
            w.openDrawer().getOrThrow()
            assertEquals(2, w.drawers().size)
            PrintGateway(w).drain()
            assertTrue(w.drawers().all { it.state == PrintState.Sent })
            // 两张小票 + 两次开钱箱；开钱箱只有脉冲，不带切纸
            printer.awaitJobs(4)
            val pulses = printer.jobs.filter { it.size == EscPos.DRAWER.size && it.contentEquals(EscPos.DRAWER) }
            assertEquals(2, pulses.size)
        }
    }

    @Test fun diningCashSettleOpensDrawer() = runBlocking {
        val w = LocalWorkspace(Store(), scope) { 1_800_000_000_000L }.also { it.enterDemo(); it.selectStore("s-coast") }
        w.openTable("H01", 2).getOrThrow()
        val p = w.current.value!!.products.first { !it.hasSpec && (it.stock ?: 1) > 0 }
        w.addDish("H01", p, null, 1)
        w.sendToKitchen("H01", true)
        val due = ai.neargo.fnb.pos.core.model.OrderRules.totals(w.current.value!!.orders.getValue("H01")).due
        w.settle("H01", due, "现金", true, due).getOrThrow()
        assertEquals(1, w.drawers().size)
    }
}
