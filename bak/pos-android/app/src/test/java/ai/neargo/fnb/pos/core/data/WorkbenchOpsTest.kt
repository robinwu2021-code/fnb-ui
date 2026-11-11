package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.PrintKind
import ai.neargo.fnb.pos.core.model.PrintState
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 阶段二：换桌 / 并台 / 拆单、备注、挂单取单、打印队列、交班。 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkbenchOpsTest {
    private class MemoryStore : SnapshotStore {
        var json: String? = null
        override suspend fun load() = json
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { this.json = json }
    }

    private fun runWs(block: suspend TestScope.(LocalWorkspace) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val w = LocalWorkspace(MemoryStore(), backgroundScope) { 1_000_000L }
        w.enterDemo(); w.selectStore("s-main")
        block(w)
    }
    private fun LocalWorkspace.product(id: String) = current.value!!.products.first { it.id == id }
    private fun LocalWorkspace.table(id: String) = current.value!!.tables.first { it.id == id }

    @Test fun changeTableMovesWholeOrderAndCleansOrigin() = runWs { w ->
        val lines = w.orders.value.getValue("A03").lines.size
        assertTrue(w.changeTable("A03", "A02").isFailure) // 目标在用餐
        assertTrue(w.changeTable("A03", "B05").isFailure) // B05 只有 2 座，A03 4 人
        assertTrue(w.changeTable("A03", "C01").isSuccess)
        assertEquals(lines, w.orders.value.getValue("C01").lines.size)
        assertEquals(TableStatus.Cleaning, w.table("A03").status)
        assertEquals(4, w.table("C01").guests)
        assertEquals("C01", w.session.value.currentTableId)
    }

    @Test fun mergeAddsGuestsAndLines() = runWs { w ->
        val a = w.orders.value.getValue("A02").lines.size
        val b = w.orders.value.getValue("A03").lines.size
        assertTrue(w.mergeTable("A02", "A03").isSuccess)
        assertEquals(a + b, w.orders.value.getValue("A03").lines.size)
        assertEquals(8, w.table("A03").guests)
        assertEquals(TableStatus.Cleaning, w.table("A02").status)
    }

    @Test fun splitMovesSelectedLinesAndOpensIdleTable() = runWs { w ->
        val first = w.orders.value.getValue("A03").lines.first().uid
        assertTrue(w.splitLines("A03", emptySet(), "A01", 2).isFailure)
        assertTrue(w.splitLines("A03", w.orders.value.getValue("A03").lines.map { it.uid }.toSet(), "A01", 2).isFailure) // 不能拆空
        assertTrue(w.splitLines("A03", setOf(first), "A01", 2).isSuccess)
        assertEquals(TableStatus.Dining, w.table("A01").status)
        assertTrue(w.orders.value.getValue("A01").lines.any { it.uid == first })
        assertTrue(w.orders.value.getValue("A03").lines.none { it.uid == first })
    }

    @Test fun notesSeparateLinesAndOnlyDraftEditable() = runWs { w ->
        w.addDish("A02", w.product("9"), null, 1, "打包")
        w.addDish("A02", w.product("9"), null, 1, "")
        val rice = w.orders.value.getValue("A02").lines.filter { it.dishId == "9" && it.status == LineStatus.New }
        assertEquals(2, rice.size) // 备注不同不合并
        val sent = w.orders.value.getValue("A02").lines.first { it.status == LineStatus.Sent }
        assertTrue(w.setLineNote("A02", sent.uid, "少辣") is OrderResult.Rejected)
        assertTrue(w.setOrderNote("A02", "有小孩").isSuccess)
        assertEquals("有小孩", w.orders.value.getValue("A02").note)
    }

    @Test fun holdAndRetrieveDraft() = runWs { w ->
        assertEquals(5, w.hold("A03").getOrThrow())
        assertTrue(w.orders.value.getValue("A03").pending.isEmpty())
        assertTrue(w.hold("A03").isFailure)
        val ticket = w.current.value!!.held.single()
        assertTrue(w.retrieve(ticket.id, "A01").isFailure) // 未开台
        assertTrue(w.retrieve(ticket.id, "A02").isSuccess)
        assertEquals(5, w.orders.value.getValue("A02").pending.sumOf { it.qty })
        assertTrue(w.current.value!!.held.isEmpty())
    }

    @Test fun printQueueFailsOnPaperOutAndRetries() = runWs { w ->
        w.sendToKitchen("A03", online = true)
        assertEquals(PrintKind.Kitchen, w.jobs.value.first().kind)
        assertEquals(1, w.process(kitchenOk = false))
        assertEquals(PrintState.Failed, w.jobs.value.first().state)
        w.preBill("A03")
        w.process(kitchenOk = false) // 前台单照常打印
        assertEquals(PrintState.Printed, w.jobs.value.first { it.kind == PrintKind.PreBill }.state)
        w.process(kitchenOk = true)
        assertTrue(w.jobs.value.all { it.state == PrintState.Printed })
        val id = w.jobs.value.first { it.kind == PrintKind.PreBill }.id
        assertTrue(w.reprint(id).isSuccess)
        assertTrue(w.jobs.value.any { it.reprint && it.body.first().contains("补打") })
    }

    /** N3：厨房单走本机，离线立即出单；云端补发只改同步状态，不重复出单。 */
    @Test fun offlineKitchenTicketPrintsImmediatelyAndSyncDoesNotReprint() = runWs { w ->
        w.sendToKitchen("A03", online = false)
        val kitchen = w.jobs.value.filter { it.kind == PrintKind.Kitchen }
        assertEquals(1, kitchen.size); assertTrue(kitchen.single().title.startsWith("A03"))
        assertEquals(1, w.current.value!!.kitchenDeliveries.size)
        w.changeTable("A03", "C01")
        w.syncNow()
        assertTrue(w.orders.value.getValue("C01").lines.none { it.status == LineStatus.Queued })
        assertEquals(1, w.jobs.value.count { it.kind == PrintKind.Kitchen })
        assertEquals("simulation", w.current.value!!.outbox.single().ackSource)
    }

    @Test fun closeShiftSummarisesPaymentsAndStartsNewShift() = runWs { w ->
        w.sendToKitchen("A03", online = true)
        w.settle("A03", 25620, "现金", online = true)
        val r = w.closeShift(counted = 75_620, staff = "林晓").getOrThrow()
        assertEquals(1, r.count); assertEquals(25620, r.total)
        assertEquals(50_000 + 25_620, r.cashExpected); assertEquals(0, r.cashCounted - r.cashExpected)
        assertEquals(1, w.current.value!!.shift.history.size)
        assertTrue(w.jobs.value.any { it.kind == PrintKind.Shift })
        assertTrue(w.jobs.value.any { it.kind == PrintKind.Receipt })
    }

    @Test fun positionValidated() = runWs { w ->
        assertTrue(w.setPosition("A01", 800, 10).isFailure)
        assertTrue(w.setPosition("A01", 100, 200).isSuccess)
        assertEquals(100, w.table("A01").x)
    }
}
