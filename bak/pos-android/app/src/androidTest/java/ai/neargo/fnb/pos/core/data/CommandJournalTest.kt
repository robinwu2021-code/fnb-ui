package ai.neargo.fnb.pos.core.data

import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CommandJournalTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun command(id: String = "c1", revision: Long = 0, payload: String = "add") = JournalCommand("store", "device", id, "order", revision, "Add", payload)
    private fun run(block: suspend (SqliteSnapshotStore, String) -> Unit) = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        try { SqliteSnapshotStore(context, name).use { block(it, name) } }
        finally { context.deleteDatabase(name) }
    }
    @Test fun duplicateReturnsOriginalAndMismatchIsRejected() = run { db, _ ->
        var calls = 0
        val original = db.journal.execute(command()) { calls++; JournalChange("one", "Added", "item", "ok") }
        assertEquals(original, db.journal.execute(command()) { error("must not execute") })
        assertEquals(1, calls)
        assertTrue(runCatching { db.journal.execute(command(payload = "different")) { error("must not execute") } }.isFailure)
        assertEquals(1, db.journal.pending("store").size)
    }
    @Test fun revisionConflictAndAckAreScopedAndIdempotent() = run { db, _ ->
        val result = db.journal.execute(command()) { old -> assertNull(old); JournalChange("one", "Added", "item", "ok") }
        assertTrue(runCatching { db.journal.execute(command("c2")) { error("stale") } }.isFailure)
        assertEquals(0, db.journal.ack("other-store", result.eventId))
        assertEquals(1, db.journal.ack("store", result.eventId))
        assertEquals(0, db.journal.ack("store", result.eventId))
        assertTrue(db.journal.pending("store").isEmpty())
        db.journal.execute(command("c2", 1)) { old -> assertEquals("one", old); JournalChange("two", "Added", "item", "ok2") }
        assertEquals(2L, db.journal.pending("store").single().revision)
    }
    @Test fun failedEventInsertRollsBackInboxAndAggregate() = run { db, name ->
        // 初始化表后注入错误，验证业务、事件、Inbox同进退。
        db.journal.pending("store")
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON command_events BEGIN SELECT RAISE(ABORT, 'failure'); END")
        }
        assertTrue(runCatching { db.journal.execute(command()) { JournalChange("bad", "Added", "", "ok") } }.isFailure)
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("DROP TRIGGER fail_event") }
        val r = db.journal.execute(command()) { old -> assertNull(old); JournalChange("good", "Added", "", "ok") }
        assertEquals(1L, r.revision)
    }
    @Test fun upgradeFromV1PreservesLegacySnapshot() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
                db.execSQL("CREATE TABLE vault(id INTEGER PRIMARY KEY,json TEXT NOT NULL)")
                db.execSQL("CREATE TABLE stores(id TEXT PRIMARY KEY,json TEXT NOT NULL)")
                db.execSQL("CREATE TABLE outbox(store_id TEXT,id TEXT,position INTEGER,json TEXT)")
                db.execSQL("CREATE TABLE print_jobs(store_id TEXT,id TEXT,position INTEGER,json TEXT)")
                db.execSQL("INSERT INTO vault VALUES(1,?)", arrayOf("{\"stores\":{},\"version\":3,\"user\":\"legacy-user\"}"))
                db.version = 1
            }
            SqliteSnapshotStore(context, name).use { db ->
                assertEquals("legacy-user", VaultCodec.decode(requireNotNull(db.load())).user)
                db.journal.execute(command()) { JournalChange("one", "Added", "item", "ok") }
                assertEquals("legacy-user", VaultCodec.decode(requireNotNull(db.load())).user)
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun replaySurvivesReopen() = run { db, name ->
        val first = db.journal.execute(command()) { JournalChange("one", "Added", "item", "ok") }
        SqliteSnapshotStore(context, name).use { reopened ->
            assertEquals(first, reopened.journal.execute(command()) { error("duplicate") })
            assertEquals(first.eventId, reopened.journal.pending("store").single().eventId)
        }
    }
}
