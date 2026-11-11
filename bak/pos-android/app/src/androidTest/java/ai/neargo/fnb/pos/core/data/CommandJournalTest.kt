package ai.neargo.fnb.pos.core.data

import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CommandJournalTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun command(id: String = "c1", revision: Long = 0, payload: String = "add", aggregates: List<AggregateVersion> = listOf(AggregateVersion("order", revision)),
                        epoch: Long = 1, coordinator: String = "device") =
        JournalCommand("store", "device", id, "Add", payload, aggregates, actorId = "staff", coordinatorId = coordinator, authorityEpoch = epoch, occurredAt = 1)
    private val ok: (Map<String, String?>) -> JournalChange = { JournalChange("Added", "item", "ok") }
    private fun run(block: suspend (SqliteSnapshotStore, String) -> Unit) = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        try { SqliteSnapshotStore(context, name).use { block(it, name) } }
        finally { context.deleteDatabase(name) }
    }

    @Test fun duplicateReturnsOriginalAndMismatchIsRejected() = run { db, _ ->
        var calls = 0
        val original = db.journal.execute(command()) { calls++; JournalChange("Added", "item", "ok") }
        assertEquals(ReceiptStatus.Committed, original.status)
        assertEquals(original, db.journal.execute(command()) { error("must not execute") })
        assertEquals(1, calls)
        assertTrue(runCatching { db.journal.execute(command(payload = "different")) { error("must not execute") } }.isFailure)
        // 授权引用是传输凭据，不改变摘要
        assertEquals(original, db.journal.execute(command().copy(authorizationRef = "token-2")) { error("must not execute") })
        assertTrue(db.journal.execute(command("auth").copy(authorizationRef = "grant-1", aggregates = listOf(AggregateVersion("order", 1))), reducer = ok).status == ReceiptStatus.Committed)
        assertTrue(db.journal.pending("store").last().envelope.contains("\"authorizationRef\":\"grant-1\""))
        val event = db.journal.pending("store").first()
        assertEquals("c1", event.causationId); assertTrue(event.envelope.contains("\"actorId\":\"staff\"")); assertTrue(event.acceptedAt > 0)
    }

    @Test fun conflictAndRejectionAreDurableTerminalReceipts() = run { db, _ ->
        db.journal.execute(command()) { JournalChange("Added", "item", "ok") }
        val conflict = db.journal.execute(command("c2", 0)) { error("stale") }
        assertEquals(ReceiptStatus.Conflict, conflict.status)
        assertTrue(conflict.result.contains("order 期望0 当前1"))
        assertEquals(conflict, db.journal.execute(command("c2", 0)) { error("replay") })
        val rejected = db.journal.execute(command("c3", 1)) { throw CommandRejected("库存不足") }
        assertEquals(ReceiptStatus.Rejected, rejected.status); assertEquals("库存不足", rejected.result)
        assertEquals(rejected, db.journal.receipt("store", "device", "c3"))
        assertEquals(1, db.journal.pending("store").size) // 冲突/拒绝不产生事件
        val r = db.journal.execute(command("c4", 1), reducer = ok)
        assertEquals(2L, r.revision)
    }

    @Test fun multiAggregateCommandChecksAllVersionsAtomically() = run { db, _ ->
        db.journal.execute(command("a", aggregates = listOf(AggregateVersion("order:A", 0))), reducer = ok)
        val both = listOf(AggregateVersion("order:A", 1), AggregateVersion("order:B", 1))
        val conflict = db.journal.execute(command("move", aggregates = both), reducer = ok)
        assertEquals(ReceiptStatus.Conflict, conflict.status)
        assertTrue(conflict.result.contains("order:B"))
        val fixed = db.journal.execute(command("move2", aggregates = listOf(AggregateVersion("order:A", 1), AggregateVersion("order:B", 0))), reducer = ok)
        assertEquals(mapOf("order:A" to 2L, "order:B" to 1L), fixed.revisions)
    }

    @Test fun scopeVersionAndEpochErrorsAreRejectedWithoutReceipt() = run { db, _ ->
        db.journal.execute(command(), reducer = ok)
        assertTrue(runCatching { db.journal.execute(command("x").copy(schemaVersion = 1), reducer = ok) }.isFailure)
        assertTrue(runCatching { db.journal.execute(command("y").copy(payloadVersion = 9), reducer = ok) }.isFailure)
        assertTrue(runCatching { db.journal.execute(command("z", 1, epoch = 2), reducer = ok) }.isFailure)
        assertTrue(runCatching { db.journal.execute(command("w", 1, coordinator = "other"), reducer = ok) }.isFailure)
        assertTrue(runCatching { db.journal.execute(command("v", aggregates = emptyList()), reducer = ok) }.isFailure)
        listOf("x", "y", "z", "w", "v").forEach { assertNull(db.journal.receipt("store", "device", it)) }
    }

    @Test fun failedEventInsertRollsBackInboxAndAggregate() = run { db, name ->
        db.journal.pending("store")
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON command_events BEGIN SELECT RAISE(ABORT, 'failure'); END")
        }
        assertTrue(runCatching { db.journal.execute(command(), reducer = ok) }.isFailure)
        assertNull(db.journal.receipt("store", "device", "c1"))
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("DROP TRIGGER fail_event") }
        assertEquals(1L, db.journal.execute(command(), reducer = ok).revision)
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
                db.journal.execute(command(), reducer = ok)
                assertEquals(SqliteSnapshotStore.DB_VERSION, db.migrations().single().first)
            }
        } finally { context.deleteDatabase(name) }
    }

    /** v2 库（旧单流 local-page 事件）升级：旧事件原样保留为 legacy，新命令按聚合另起版本。 */
    @Test fun upgradeFromV2KeepsLegacyEventsAndWatermark() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
                db.execSQL("CREATE TABLE vault (id INTEGER PRIMARY KEY CHECK(id=1), json TEXT NOT NULL)")
                db.execSQL("CREATE TABLE stores (id TEXT PRIMARY KEY NOT NULL, json TEXT NOT NULL)")
                for (t in listOf("outbox", "print_jobs")) db.execSQL("CREATE TABLE $t (store_id TEXT NOT NULL REFERENCES stores(id) ON DELETE CASCADE, id TEXT NOT NULL, position INTEGER NOT NULL, json TEXT NOT NULL, PRIMARY KEY(store_id,id))")
                CommandJournal.createTables(db)
                db.execSQL("INSERT INTO vault VALUES(1,?)", arrayOf("{\"stores\":{},\"version\":3}"))
                db.execSQL("INSERT INTO command_aggregates VALUES('store','page-order-stream',2,'snapshot-projection')")
                db.execSQL("INSERT INTO command_events(event_id,store_id,aggregate_id,revision,type,payload,acked) VALUES('e1','store','page-order-stream',1,'TableOpened','{}',0),('e2','store','page-order-stream',2,'DishAdded','{}',0)")
                db.version = 2
            }
            SqliteSnapshotStore(context, name).use { db ->
                val legacy = db.journal.pending("store")
                assertEquals(listOf("e1", "e2"), legacy.map { it.eventId })
                assertTrue(legacy.all { it.envelope.isEmpty() })
                assertTrue(db.migrations().single().third.contains("旧事件 2 条"))
                db.journal.execute(command(), reducer = ok)
                val events = db.journal.pending("store")
                assertEquals(3, events.size); assertEquals("order", events.last().aggregateId); assertEquals(1L, events.last().revision)
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun replaySurvivesReopen() = run { db, name ->
        val first = db.journal.execute(command(), reducer = ok)
        SqliteSnapshotStore(context, name).use { reopened ->
            assertEquals(first, reopened.journal.execute(command()) { error("duplicate") })
            assertEquals(first.eventId, reopened.journal.pending("store").single().eventId)
        }
    }

    /** N2-03：超时后用同一ID查回执/重投，业务只执行一次；重启恢复 Sending；同ID异内容拒绝。 */
    @Test fun outboxRetriesWithSameIdAndSurvivesRestart() = run { db, name ->
        var executed = 0
        var failSubmits = 1
        var dropReply = true
        val loop = LoopbackTransport(db.journal) { executed++; JournalChange("Added", "item", "ok") }
        val flaky = object : CommandTransport {
            override suspend fun query(command: JournalCommand) = loop.query(command)
            override suspend fun submit(command: JournalCommand): JournalReceipt {
                if (failSubmits-- > 0) error("主机不可达")
                val r = loop.submit(command)
                if (dropReply) { dropReply = false; error("回执超时") } // 已提交但回执丢失
                return r
            }
        }
        var now = 1_000L
        val c = command("send-1")
        db.commandOutbox.save(c, "host", now)
        assertTrue(runCatching { db.commandOutbox.save(c.copy(payload = "other"), "host", now) }.isFailure)
        val dispatcher = CommandDispatcher(db.commandOutbox, flaky) { now }
        assertEquals(0, dispatcher.flush()) // 不可达
        assertEquals(OutboxCommandState.Waiting, db.commandOutbox.get("store", "device", "send-1")!!.state)
        assertEquals(0, dispatcher.flush()) // 未到退避时间
        now += 2_000
        assertEquals(0, dispatcher.flush()) // 已提交但回执丢失
        assertEquals(1, executed)
        now += 5_000
        assertEquals(1, dispatcher.flush()) // 查回执得到原结果，不重复执行
        assertEquals(1, executed)
        val done = db.commandOutbox.get("store", "device", "send-1")!!
        assertEquals(OutboxCommandState.Committed, done.state); assertEquals(3, done.attempts)
        assertEquals(1, db.journal.pending("store").size)
        // 重启：Sending 回到 Waiting
        val c2 = command("send-2", 1)
        db.commandOutbox.save(c2, "host", now)
        db.commandOutbox.markSending(db.commandOutbox.get("store", "device", "send-2")!!)
        SqliteSnapshotStore(context, name).use { reopened ->
            assertEquals(1, reopened.commandOutbox.recoverAfterRestart())
            assertEquals(1, CommandDispatcher(reopened.commandOutbox, LoopbackTransport(reopened.journal) { JournalChange("Added", "item", "ok") }) { now }.flush())
            assertEquals(OutboxCommandState.Committed, reopened.commandOutbox.get("store", "device", "send-2")!!.state)
            // 超窗未决：标过期，不删除
            reopened.commandOutbox.save(command("old", 2), "host", 1)
            assertEquals(1, reopened.commandOutbox.expire(olderThan = 500))
            assertEquals(OutboxCommandState.Expired, reopened.commandOutbox.get("store", "device", "old")!!.state)
            assertTrue(reopened.commandOutbox.due(Long.MAX_VALUE).isEmpty())
        }
    }
}
