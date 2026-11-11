package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.ReservationStatus
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReservationWorkspaceTest {
    private class MemoryStore : SnapshotStore {
        var json: String? = null
        override suspend fun load() = json
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { this.json = json }
    }
    @Test fun bookingLifecyclePersistsAndRejectsRepeatedActions() = runTest(UnconfinedTestDispatcher()) {
        val disk = MemoryStore()
        val w = LocalWorkspace(disk, backgroundScope) { 1_000_000L }
        w.enterDemo(); w.selectStore("s-main")
        val t = w.current.value!!.tables.first { it.status == TableStatus.Idle }
        assertTrue(w.createReservation(t.id, "客人", "13800138000", 1, 2_000_000L, "窗边").isSuccess)
        val r = w.current.value!!.reservations.first { it.tableId == t.id }
        yield()
        val pendingRestore = LocalWorkspace(disk, backgroundScope) { 1_000_000L }; pendingRestore.restore(); yield()
        assertEquals(r, pendingRestore.current.value!!.reservations.first { it.id == r.id })
        assertEquals(TableStatus.Reserved, pendingRestore.tables.value.first { it.id == t.id }.status)
        assertEquals(TableStatus.Reserved, w.tables.value.first { it.id == t.id }.status)
        assertTrue(w.createReservation(t.id, "客人", "13800138000", 1, 2_000_000L, "").isFailure)
        assertTrue(w.cancelReservation(r.id).isSuccess)
        assertTrue(w.cancelReservation(r.id).isFailure)
        assertTrue(w.checkInReservation(r.id).isFailure)
        assertEquals(TableStatus.Idle, w.tables.value.first { it.id == t.id }.status)
        assertTrue(w.createReservation(t.id, "客人", "13800138000", 1, 2_000_000L, "").isSuccess)
        val active = w.current.value!!.reservations.last()
        assertTrue(w.checkInReservation(active.id).isSuccess)
        assertTrue(w.checkInReservation(active.id).isFailure)
        assertEquals(TableStatus.Dining, w.tables.value.first { it.id == t.id }.status)
        assertEquals(ReservationStatus.Arrived, w.current.value!!.reservations.last().status)
        yield()
        val restored = LocalWorkspace(disk, backgroundScope) { 1_000_000L }
        restored.restore()
        yield()
        restored.selectStore("s-main")
        assertEquals(w.current.value!!.reservations, restored.current.value!!.reservations)
    }
}
