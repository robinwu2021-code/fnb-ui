package ai.neargo.fnb.pos.core.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import ai.neargo.fnb.pos.core.model.OutboxItem
import ai.neargo.fnb.pos.core.model.OutboxKind
import ai.neargo.fnb.pos.core.model.PrintJob
import ai.neargo.fnb.pos.core.model.PrintKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SqliteSnapshotStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun source(): Vault {
        val stores = DemoCatalog.stores(1_000_000L).toMutableMap()
        val main = stores.getValue("s-main")
        stores[main.id] = main.copy(
            outbox = listOf(OutboxItem("same-command-id", OutboxKind.Kitchen, "A03", "送厨", 1L)),
            printJobs = listOf(PrintJob("ticket-1", PrintKind.Kitchen, "送厨单", listOf("菜品"), 1L)),
        )
        return Vault(stores = stores, storeId = main.id)
    }
    private fun encode(vault: Vault) = VaultCodec.json.encodeToString(Vault.serializer(), vault)

    @Test fun queueAndBusinessSurviveReopenWithStableIds() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            val vault = source()
            SqliteSnapshotStore(context, name).use { it.save(encode(vault)) }
            SqliteSnapshotStore(context, name).use { store ->
                assertEquals(vault, VaultCodec.decode(requireNotNull(store.load())))
                store.save(encode(vault))
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM outbox", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
                db.rawQuery("SELECT COUNT(*) FROM print_jobs", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun failureDuringQueueInsertRollsBackWholeSnapshot() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            val vault = source()
            SqliteSnapshotStore(context, name).use { store ->
                store.save(encode(vault))
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    db.execSQL("CREATE TRIGGER fail_queue BEFORE INSERT ON outbox BEGIN SELECT RAISE(ABORT, 'test failure'); END")
                }
                val changed = vault.copy(user = "changed")
                assertTrue(runCatching { store.save(encode(changed)) }.isFailure)
                assertEquals(vault, VaultCodec.decode(requireNotNull(store.load())))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun legacyImportIsOneTimeAndPreservesSource() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        val old = object : SnapshotStore {
            var source = encode(source())
            override suspend fun load() = source
            override suspend fun save(json: String) { source = json }
            override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt = throw UnsupportedOperationException()
        }
        try {
            val original = old.source
            SqliteSnapshotStore(context, name).use { db ->
                val migrating = MigratingSnapshotStore(db, old)
                assertEquals(original, migrating.load())
                val changed = encode(source().copy(user = "new-user"))
                migrating.save(changed)
                assertEquals("new-user", VaultCodec.decode(requireNotNull(migrating.load())).user)
                assertEquals(original, old.source)
            }
        } finally { context.deleteDatabase(name) }
    }
}
