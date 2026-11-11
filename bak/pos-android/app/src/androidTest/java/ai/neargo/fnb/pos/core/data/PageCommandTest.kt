package ai.neargo.fnb.pos.core.data

import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PageCommandTest {
    @Test fun tableFlowKitchenAndHeldTicketsAreAtomic() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "flow-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            SqliteSnapshotStore(context, name).use { db ->
                val w = LocalWorkspace(db, scope)
                w.enterDemo(); w.selectStore("s-main")
                val ids = w.current.value!!.tables.filter { it.status == ai.neargo.fnb.pos.core.model.TableStatus.Idle }.take(3).map { it.id }
                val a = ids[0]; val b = ids[1]; val c = ids[2]
                w.openTable(a, 1)
                w.addDish(a, w.current.value!!.products.first { it.id == "12" }, null, 2)
                w.addDish(a, w.current.value!!.products.first { it.id == "9" }, null, 1)
                assertTrue(w.hold(a).isSuccess)
                val ticket = w.current.value!!.held.last().id
                assertTrue(w.retrieve(ticket, a).isSuccess)
                val countBeforeDuplicate = db.journal.pending("s-main").size
                assertTrue(w.retrieve(ticket, a).isFailure)
                assertEquals(countBeforeDuplicate, db.journal.pending("s-main").size)
                val heldEvent = VaultCodec.json.parseToJsonElement(db.journal.pending("s-main").first { it.type == "OrderHeld" }.payload).jsonObject
                assertEquals(ticket, heldEvent.getValue("addedHeldTickets").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
                val retrievedEvent = VaultCodec.json.parseToJsonElement(db.journal.pending("s-main").last().payload).jsonObject
                assertEquals(ticket, retrievedEvent.getValue("removedHeldTicketIds").jsonArray.single().jsonPrimitive.content)
                assertTrue(db.journal.pending("s-main").any { it.type == "OrderHeld" })
                val before = db.load()
                val memory = w.current.value
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                    it.execSQL("CREATE TRIGGER fail_move BEFORE INSERT ON command_events BEGIN SELECT RAISE(ABORT, 'failure'); END")
                }
                assertTrue(w.changeTable(a, b).isFailure)
                assertEquals(before, db.load()); assertEquals(memory, w.current.value)
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("DROP TRIGGER fail_move") }
                assertTrue(w.changeTable(a, b).isSuccess)
                val move = VaultCodec.json.parseToJsonElement(db.journal.pending("s-main").last().payload).jsonObject
                assertEquals(2, move.getValue("affectedTables").jsonArray.size)
                val uid = w.current.value!!.orders.getValue(b).lines.first().uid
                assertTrue(w.splitLines(b, setOf(uid), c, 1).isSuccess)
                assertTrue(w.mergeTable(c, b).isSuccess)
                assertEquals(3, w.sendToKitchen(b, false).qty)
                val countBeforeResend = db.journal.pending("s-main").size
                assertEquals(0, w.sendToKitchen(b, false).qty)
                assertEquals(countBeforeResend, db.journal.pending("s-main").size)
                val events = db.journal.pending("s-main")
                assertTrue(events.map { it.type }.containsAll(listOf("OrderHeld", "OrderRetrieved", "TableChanged", "OrderSplit", "TableMerged", "KitchenSubmitted")))
                assertTrue(events.last().payload.contains(w.current.value!!.outbox.last().id))
            }
        } finally { scope.cancel(); context.deleteDatabase(name) }
    }

    @Test fun giftEventContainsAuditAndRejectedRepeatDoesNotWrite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "gift-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            SqliteSnapshotStore(context, name).use { db ->
                val w = LocalWorkspace(db, scope)
                w.enterDemo(); w.selectStore("s-main")
                val table = w.current.value!!.currentTableId
                val line = w.current.value!!.orders.getValue(table).lines.first { it.status == ai.neargo.fnb.pos.core.model.LineStatus.Sent }
                val request = ai.neargo.fnb.pos.core.model.AdjustmentRequest(ai.neargo.fnb.pos.core.model.AdjustmentKind.Gift, line.uid, reason = "顾客赠送")
                assertTrue(w.authorizeAdjustment(table, request, "manager@demo.local", "demo12345").isSuccess)
                assertTrue(w.authorizeAdjustment(table, request, "manager@demo.local", "demo12345").isFailure)
                val event = db.journal.pending("s-main").single()
                assertEquals("DishGifted", event.type)
                assertEquals(w.current.value!!.adjustments.single().id, VaultCodec.json.parseToJsonElement(event.payload).jsonObject.getValue("audit").jsonObject.getValue("id").jsonPrimitive.content)
            }
        } finally { scope.cancel(); context.deleteDatabase(name) }
    }

    @Test fun quantityAdjustmentsAndPaymentCommitWithEvents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "payment-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            SqliteSnapshotStore(context, name).use { db ->
                val w = LocalWorkspace(db, scope)
                w.enterDemo(); w.selectStore("s-main")
                val table = w.current.value!!.tables.first { it.status == ai.neargo.fnb.pos.core.model.TableStatus.Idle }.id
                w.openTable(table, 1)
                val product = w.current.value!!.products.first { it.id == "12" }
                w.addDish(table, product, null, 3)
                val uid = w.current.value!!.orders.getValue(table).lines.single().uid
                w.changeQty(table, uid, 1)
                w.sendToKitchen(table, true)
                val request = ai.neargo.fnb.pos.core.model.AdjustmentRequest(ai.neargo.fnb.pos.core.model.AdjustmentKind.Void, uid, 1, reason = "顾客取消")
                assertTrue(w.authorizeAdjustment(table, request, "manager@demo.local", "wrong").isFailure)
                assertTrue(w.authorizeAdjustment(table, request, "manager@demo.local", "demo12345").isSuccess)
                val discount = ai.neargo.fnb.pos.core.model.AdjustmentRequest(ai.neargo.fnb.pos.core.model.AdjustmentKind.Discount, percent = 90, reason = "门店活动")
                w.authorizeAdjustment(table, discount, "manager@demo.local", "demo12345")
                assertEquals(listOf("TableOpened", "DishAdded", "DishQuantityChanged", "KitchenSubmitted", "DishVoided", "OrderDiscounted"), db.journal.pending("s-main").map { it.type })
                val kitchenEvent = VaultCodec.json.parseToJsonElement(db.journal.pending("s-main").first { it.type == "KitchenSubmitted" }.payload).jsonObject
                assertEquals(1, kitchenEvent.getValue("addedPrintJobs").jsonArray.size)
                val due = ai.neargo.fnb.pos.core.model.OrderRules.totals(w.current.value!!.orders.getValue(table)).due
                val before = db.load()
                val memoryBefore = w.current.value
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                    it.execSQL("CREATE TRIGGER fail_payment BEFORE INSERT ON command_events BEGIN SELECT RAISE(ABORT, 'failure'); END")
                }
                assertTrue(w.settle(table, due, "现金", false, due + 100).isFailure)
                assertEquals(before, db.load())
                assertEquals(memoryBefore, w.current.value)
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("DROP TRIGGER fail_payment") }
                assertTrue(w.settle(table, due, "现金", false, due + 100).isSuccess)
                val event = db.journal.pending("s-main").last()
                assertEquals("PaymentRecorded", event.type)
                assertTrue(event.payload.contains("payment"))
                assertEquals(w.current.value!!.payments.last().id, VaultCodec.json.parseToJsonElement(event.payload).jsonObject.getValue("payment").jsonObject.getValue("id").jsonPrimitive.content)
                assertTrue(w.settle(table, due, "现金", false, due + 100).isFailure)
                assertEquals(7, db.journal.pending("s-main").size)
            }
        } finally { scope.cancel(); context.deleteDatabase(name) }
    }

    @Test fun pageCommandsPersistEventsAndRollbackTogether() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "page-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            SqliteSnapshotStore(context, name).use { db ->
                val w = LocalWorkspace(db, scope)
                w.enterDemo(); w.selectStore("s-main")
                val idle = w.current.value!!.tables.first { it.status == ai.neargo.fnb.pos.core.model.TableStatus.Idle }
                assertTrue(w.openTable(idle.id, 1).isSuccess)
                val product = w.current.value!!.products.first { it.id == "12" }
                w.addDish(idle.id, product, null, 1)
                assertEquals(listOf("TableOpened", "DishAdded"), db.journal.pending("s-main").map { it.type })
                val before = db.load()
                val memoryBefore = w.current.value
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                    it.execSQL("CREATE TRIGGER fail_page BEFORE INSERT ON command_events BEGIN SELECT RAISE(ABORT, 'failure'); END")
                }
                assertTrue(w.addDish(idle.id, product, null, 1) is ai.neargo.fnb.pos.core.model.OrderResult.Rejected)
                assertEquals(before, db.load())
                assertEquals(memoryBefore, w.current.value)
                assertEquals(2, db.journal.pending("s-main").size)
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                    it.execSQL("DROP TRIGGER fail_page")
                    it.execSQL("CREATE TRIGGER fail_projection BEFORE INSERT ON stores BEGIN SELECT RAISE(ABORT, 'failure'); END")
                }
                assertTrue(w.addDish(idle.id, product, null, 1) is ai.neargo.fnb.pos.core.model.OrderResult.Rejected)
                assertEquals(before, db.load())
                assertEquals(memoryBefore, w.current.value)
                assertEquals(2, db.journal.pending("s-main").size)
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("DROP TRIGGER fail_projection") }
                w.addDish(idle.id, product, null, 1)
                assertEquals(3L, db.journal.pending("s-main").last().revision)
                val restored = LocalWorkspace(db, scope)
                restored.restore()
                assertEquals(w.current.value, restored.current.value)
            }
        } finally { scope.cancel(); context.deleteDatabase(name) }
    }

    @Test fun reservationCheckInAndTableEventsRollbackTogether() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "booking-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            SqliteSnapshotStore(context, name).use { db ->
                val w = LocalWorkspace(db, scope)
                w.enterDemo(); w.selectStore("s-main")
                val t = w.current.value!!.tables.first { it.status == ai.neargo.fnb.pos.core.model.TableStatus.Idle }.id
                assertTrue(w.createReservation(t, "客人", "13800138000", 1, System.currentTimeMillis() + 3_600_000L, "").isSuccess)
                val r = w.current.value!!.reservations.last()
                val before = db.load()
                val memory = w.current.value
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                    it.execSQL("CREATE TRIGGER fail_booking BEFORE INSERT ON command_events BEGIN SELECT RAISE(ABORT, 'failure'); END")
                }
                assertTrue(w.checkInReservation(r.id).isFailure)
                assertEquals(before, db.load()); assertEquals(memory, w.current.value)
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("DROP TRIGGER fail_booking") }
                assertTrue(w.checkInReservation(r.id).isSuccess)
                w.setOrderNote(t, "靠窗")
                w.clearNew(t)
                assertEquals(listOf("ReservationCreated", "ReservationCheckedIn", "OrderNoteChanged"), db.journal.pending("s-main").map { it.type })
                val event = VaultCodec.json.parseToJsonElement(db.journal.pending("s-main")[1].payload).jsonObject
                assertEquals(r.id, event.getValue("reservation").jsonObject.getValue("id").jsonPrimitive.content)
            }
        } finally { scope.cancel(); context.deleteDatabase(name) }
    }
}
