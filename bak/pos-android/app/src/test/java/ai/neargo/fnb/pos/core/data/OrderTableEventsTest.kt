package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.ReservationStatus
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/** N1-02：订单与桌台剩余入口的事件；拒绝或无变化不写事件，成功时快照与事件一起提交。 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderTableEventsTest {
    private class JournalStore : SnapshotStore {
        var json: String? = null
        val commands = mutableListOf<JournalCommand>()
        var failNext = false
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand) {
            if (failNext) { failNext = false; error("disk") }
            json = source; commands += command
        }
    }

    private fun runWs(block: suspend TestScope.(LocalWorkspace, JournalStore) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val disk = JournalStore()
        val w = LocalWorkspace(disk, backgroundScope) { 1_000_000L }
        w.enterDemo(); w.selectStore("s-main")
        disk.commands.clear()
        block(w, disk)
    }
    private fun JournalStore.types() = commands.map { it.type }
    private fun JournalStore.last(): JsonObject = Json.parseToJsonElement(commands.last().payload).jsonObject
    private fun LocalWorkspace.idle() = current.value!!.tables.first { it.status == TableStatus.Idle }.id
    private fun LocalWorkspace.product(id: String) = current.value!!.products.first { it.id == id }

    @Test fun cleaningAndPositionEmitTableEvents() = runWs { w, d ->
        val t = w.idle()
        assertTrue(w.startCleaning(t).isFailure) // 未结账
        assertTrue(d.commands.isEmpty())
        w.openTable(t, 1); w.addDish(t, w.product("12"), null, 1); w.sendToKitchen(t, true)
        val due = ai.neargo.fnb.pos.core.model.OrderRules.totals(w.orders.value.getValue(t)).due
        assertTrue(w.settle(t, due, "银行卡", true, due).isSuccess)
        d.commands.clear()
        assertTrue(w.startCleaning(t).isSuccess)
        assertTrue(w.finishCleaning(t).isSuccess)
        assertTrue(w.finishCleaning(t).isFailure)
        assertTrue(w.setPosition(t, 999, 0).isFailure)
        assertTrue(w.setPosition(t, 100, 100).isSuccess)
        assertEquals(listOf("TableCleaningStarted", "TableCleaningFinished", "TablePositionChanged"), d.types())
        assertEquals("100", d.last().getValue("table").jsonObject.getValue("x").jsonPrimitive.content)
        assertEquals(TableStatus.Idle.name, Json.parseToJsonElement(d.commands[1].payload).jsonObject.getValue("table").jsonObject.getValue("status").jsonPrimitive.content)
    }

    @Test fun notesDraftsAndBenefitsEmitOrderEvents() = runWs { w, d ->
        val t = w.idle()
        w.openTable(t, 2)
        w.addDish(t, w.product("12"), null, 1)
        val uid = w.orders.value.getValue(t).lines.single().uid
        d.commands.clear()
        assertTrue(w.setLineNote(t, uid, "少辣") is OrderResult.Ok)
        assertTrue(w.setLineNote(t, uid, "少辣") is OrderResult.Ok) // 无变化
        assertTrue(w.setOrderNote(t, "x".repeat(61)).isFailure)
        assertTrue(w.setOrderNote(t, "有小孩").isSuccess)
        w.setMember(t, true)
        assertTrue(w.setBenefits(t, coupon = true, activity = false).isFailure) // 未满 200
        w.clearNew(t)
        assertEquals(listOf("DishNoteChanged", "OrderNoteChanged", "OrderMemberChanged", "OrderDraftCleared"), d.types())
        val note = Json.parseToJsonElement(d.commands[0].payload).jsonObject
        assertEquals("少辣", note.getValue("changedLines").jsonArray.single().jsonObject.getValue("note").jsonPrimitive.content)
        assertFalse(Json.parseToJsonElement(d.commands[1].payload).jsonObject.containsKey("changedLines"))
        assertTrue(d.last().getValue("order").jsonObject.getValue("lines").jsonArray.isEmpty())
    }

    @Test fun rushAndServeEmitLineEventsAndRejectionIsNotSilent() = runWs { w, d ->
        val t = w.idle()
        w.openTable(t, 1); w.addDish(t, w.product("12"), null, 1); w.sendToKitchen(t, true)
        val uid = w.orders.value.getValue(t).lines.single().uid
        d.commands.clear()
        w.advance(t, uid, LineStatus.Rushing)
        w.advance(t, uid, LineStatus.Served)
        w.advance(t, uid, LineStatus.Served) // 已上菜，无变化
        assertEquals(listOf("DishRushed", "DishServed"), d.types())
        assertEquals(LineStatus.Served.name, d.last().getValue("changedLines").jsonArray.single().jsonObject.getValue("status").jsonPrimitive.content)
        val other = w.idle()
        val e = runCatching { w.setMember(other, true) }.exceptionOrNull()
        assertTrue(e is WorkspaceRejectedException)
        assertEquals(2, d.commands.size)
    }

    @Test fun reservationLifecycleEmitsDedicatedEvents() = runWs { w, d ->
        val t = w.idle()
        assertTrue(w.createReservation(t, "客人", "13800138000", 1, 2_000_000L, "").isSuccess)
        val r1 = w.current.value!!.reservations.last()
        assertTrue(w.cancelReservation(r1.id).isSuccess)
        assertTrue(w.cancelReservation(r1.id).isFailure)
        assertTrue(w.createReservation(t, "客人", "13800138000", 1, 2_000_000L, "").isSuccess)
        val r2 = w.current.value!!.reservations.last()
        assertTrue(w.checkInReservation(r2.id).isSuccess)
        assertTrue(w.checkInReservation(r2.id).isFailure)
        assertEquals(listOf("ReservationCreated", "ReservationCancelled", "ReservationCreated", "ReservationCheckedIn"), d.types())
        val checkIn = d.last()
        assertEquals(r2.id, checkIn.getValue("reservation").jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals(ReservationStatus.Arrived.name, checkIn.getValue("reservation").jsonObject.getValue("status").jsonPrimitive.content)
        assertEquals(TableStatus.Dining.name, checkIn.getValue("table").jsonObject.getValue("status").jsonPrimitive.content)
        assertEquals(ReservationStatus.Cancelled, w.current.value!!.reservations.first { it.id == r1.id }.status)
    }

    @Test fun failedEventWriteLeavesStateUnchanged() = runWs { w, d ->
        val t = w.idle()
        w.openTable(t, 1)
        val before = w.current.value
        d.failNext = true
        assertTrue(w.setOrderNote(t, "有小孩").isFailure)
        assertEquals(before, w.current.value)
        d.failNext = true
        assertTrue(runCatching { w.setMember(t, true) }.exceptionOrNull() is WorkspaceWriteException)
        assertEquals(before, w.current.value)
        assertEquals(1, d.commands.size) // 只有开台
    }
}
