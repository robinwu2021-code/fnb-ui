package ai.neargo.fnb.pos.core.model

import org.junit.Assert.*
import org.junit.Test

class ReservationRulesTest {
    private val table = DiningTable("A01", "a", 4, TableStatus.Idle)
    private fun error(t: DiningTable? = table, bookings: List<Reservation> = emptyList(), guests: Int = 2, time: Long = 2000) =
        ReservationRules.validate(t, bookings, "客人", "13800138000", guests, time, 1000)

    @Test fun validatesSeatsTableAndFutureTime() {
        assertNull(error())
        assertNotNull(error(t = null))
        assertNotNull(error(t = table.copy(status = TableStatus.Dining)))
        assertNotNull(error(guests = 0)); assertNotNull(error(guests = 5))
        assertNotNull(error(time = 1000)); assertNotNull(error(time = Long.MAX_VALUE))
    }
    @Test fun rejectsDuplicateActiveButAllowsHistoricalBookings() {
        val r = Reservation("R1", "A01", "客人", "13800138000", 2, 2000)
        assertNotNull(error(bookings = listOf(r)))
        assertNull(error(bookings = listOf(r.copy(status = ReservationStatus.Cancelled))))
        assertNull(error(bookings = listOf(r.copy(status = ReservationStatus.Arrived))))
    }
    @Test fun parsesOnlyCompleteRealCalendarMinutes() {
        assertNotNull(ReservationRules.parse("2028-02-29 18:30"))
        listOf("2027-02-29 18:30", "2028-02-29 24:30", "2028-02-29 18:30xyz", "2028-2-29 18:30").forEach { assertNull(it, ReservationRules.parse(it)) }
        val time = ReservationRules.parse("2028-02-29 18:30")!!
        assertEquals("2028-02-29 18:30", ReservationRules.format(time))
    }
}
