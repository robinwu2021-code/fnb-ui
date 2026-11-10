package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BusinessEventFactoryTest {
    private val before = DemoCatalog.stores(1_000_000L).getValue("s-main")
    private fun encoded(type: String, table: String, after: StoreData) = Json.parseToJsonElement(
        BusinessEventFactory.encode(BusinessEventFactory.build(type, table, before, after))
    ).jsonObject

    @Test fun orderOnlyAndBothTableChangesAreIncluded() {
        val original = before.orders.getValue("A03")
        val after = before.copy(
            orders = before.orders + ("A03" to original.copy(note = "changed")) + ("A02" to TableOrder(note = "target")),
        )
        val payload = encoded("TableChanged", "A03", after)
        val affected = payload.getValue("affectedTables").jsonArray.map { it.jsonObject.getValue("table").jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(setOf("A03", "A02"), affected.toSet())
        assertEquals(original, before.orders.getValue("A03"))
        val legacyJson = Json { encodeDefaults = true }
        assertEquals(legacyJson.encodeToJsonElement(TableOrder.serializer(), after.orders.getValue("A03")), payload.getValue("order"))
        assertFalse(payload.containsKey("payment")); assertFalse(payload.containsKey("audit"))
        assertEquals(setOf("tableId", "table", "order", "affectedTables", "addedHeldTickets", "removedHeldTicketIds", "addedPrintJobs", "addedOutboxItems"), payload.keys)
    }

    @Test fun ticketAndTaskDeltasPreserveIdsAndDoNotRepeatOldTasks() {
        val ticket = HeldTicket("new-held", "A03", emptyList(), "", 1L)
        val removed = HeldTicket("old-held", "A03", emptyList(), "", 1L)
        val oldJob = PrintJob("existing", PrintKind.Kitchen, "old", emptyList(), 1L)
        val newJob = oldJob.copy(id = "new-job")
        val oldOutbox = OutboxItem("existing-outbox", OutboxKind.Kitchen, "A03", "old", 1L)
        val old = before.copy(held = listOf(removed), printJobs = listOf(oldJob), outbox = listOf(oldOutbox))
        val after = old.copy(held = listOf(ticket), printJobs = listOf(oldJob, newJob), outbox = listOf(oldOutbox, oldOutbox.copy(id = "new-outbox")))
        val result = BusinessEventFactory.build("OrderHeld", "A03", old, after)
        assertEquals(listOf("new-held"), result.addedHeldTickets.map { it.id })
        assertEquals(listOf("old-held"), result.removedHeldTicketIds)
        assertEquals(listOf("new-job"), result.addedPrintJobs.map { it.id })
        assertEquals(listOf("new-outbox"), result.addedOutboxItems.map { it.id })
    }

    @Test fun paymentAndAuditKeepLegacyOptionalFields() {
        val payment = PaymentRecord("payment", "A03", 100, "现金", 1L, synced = false)
        val audit = AdjustmentAudit("audit", "A03", AdjustmentKind.Gift, "staff", "supervisor", "reason", 100, 1, 1L)
        val after = before.copy(payments = listOf(payment), adjustments = listOf(audit))
        assertEquals("payment", encoded("PaymentRecorded", "A03", after).getValue("payment").jsonObject.getValue("id").jsonPrimitive.content)
        for (type in listOf("DishVoided", "DishGifted", "OrderDiscounted")) {
            assertEquals("audit", encoded(type, "A03", after).getValue("audit").jsonObject.getValue("id").jsonPrimitive.content)
        }
        assertFalse(encoded("DishAdded", "A03", after).containsKey("payment"))
    }

    @Test fun wrongScopeAndMissingTargetOrRequiredEvidenceAreRejected() {
        assertTrue(runCatching { BusinessEventFactory.build("DishAdded", "A03", before, before.copy(id = "other")) }.isFailure)
        assertTrue(runCatching { BusinessEventFactory.build("DishAdded", "missing", before, before) }.isFailure)
        assertTrue(runCatching { BusinessEventFactory.build("PaymentRecorded", "A03", before, before.copy(payments = emptyList())) }.isFailure)
        assertTrue(runCatching { BusinessEventFactory.build("DishGifted", "A03", before, before.copy(adjustments = emptyList())) }.isFailure)
    }
}
