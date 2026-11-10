package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.OutboxItem
import ai.neargo.fnb.pos.core.model.PrintJob
import ai.neargo.fnb.pos.core.model.StoreData
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * 第一阶段事务存储：保留门店级 JSON，队列独立存行，所有行同一事务提交。
 * 尚不是领域事件日志，不能将这里的快照用作多 POS 或云端整库覆盖协议。
 */
internal class SqliteSnapshotStore(context: Context, name: String = "pos-workspace.db") : SnapshotStore, Closeable {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, name, null, 2) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE vault (id INTEGER PRIMARY KEY CHECK(id=1), json TEXT NOT NULL)")
            db.execSQL("CREATE TABLE stores (id TEXT PRIMARY KEY NOT NULL, json TEXT NOT NULL)")
            for (table in listOf("outbox", "print_jobs")) {
                db.execSQL("CREATE TABLE $table (store_id TEXT NOT NULL REFERENCES stores(id) ON DELETE CASCADE, id TEXT NOT NULL, position INTEGER NOT NULL, json TEXT NOT NULL, PRIMARY KEY(store_id,id))")
            }
            CommandJournal.createTables(db)
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            check(oldVersion == 1 && newVersion == 2) { "缺少数据库迁移：$oldVersion → $newVersion" }
            CommandJournal.createTables(db)
        }
    }
    val journal = CommandJournal { helper.writableDatabase }
    private val json get() = VaultCodec.json

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val metadata = db.rawQuery("SELECT json FROM vault WHERE id=1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (metadata == null) {
                db.rawQuery("SELECT COUNT(*) FROM stores", null).use {
                    it.moveToFirst()
                    check(it.getInt(0) == 0) { "本地数据库缺少元数据，停止恢复" }
                }
                db.setTransactionSuccessful()
                null
            } else {
                val vault = json.decodeFromString(Vault.serializer(), metadata)
                val stores = linkedMapOf<String, StoreData>()
                db.rawQuery("SELECT id,json FROM stores ORDER BY id", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val data = json.decodeFromString(StoreData.serializer(), cursor.getString(1))
                        require(data.id == id) { "门店数据标识不一致" }
                        stores[id] = data.copy(
                            outbox = readRows(db, "outbox", id).map { json.decodeFromString(OutboxItem.serializer(), it) },
                            printJobs = readRows(db, "print_jobs", id).map { json.decodeFromString(PrintJob.serializer(), it) },
                        )
                    }
                }
                val restored = vault.copy(stores = stores)
                val encoded = json.encodeToString(Vault.serializer(), restored)
                VaultCodec.decode(encoded)
                db.setTransactionSuccessful()
                encoded
            }
        } finally { db.endTransaction() }
    }

    override suspend fun save(json: String) = withContext(Dispatchers.IO) {
        val vault = VaultCodec.decode(json)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            writeSnapshot(db, vault)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override suspend fun saveCommand(source: String, command: JournalCommand) {
        val vault = VaultCodec.decode(source)
        journal.execute(command, persistBusiness = { db -> writeSnapshot(db, vault) }) {
            JournalChange("snapshot-projection", command.type, command.payload, "committed")
        }
    }

    private fun writeSnapshot(db: SQLiteDatabase, vault: Vault) {
            // 元数据中的当前门店在 load 组装 stores 后验证，不在该占位片段中校验。
            val metadata = this@SqliteSnapshotStore.json.encodeToString(Vault.serializer(), vault.copy(stores = emptyMap()))
            db.insertWithOnConflict("vault", null, ContentValues().apply { put("id", 1); put("json", metadata) }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
            db.delete("stores", null, null)
            for ((id, data) in vault.stores) {
                val body = this@SqliteSnapshotStore.json.encodeToString(StoreData.serializer(), data.copy(outbox = emptyList(), printJobs = emptyList()))
                db.insertOrThrow("stores", null, ContentValues().apply { put("id", id); put("json", body) })
                data.outbox.forEachIndexed { index, item ->
                    insertRow(db, "outbox", id, item.id, index, this@SqliteSnapshotStore.json.encodeToString(OutboxItem.serializer(), item))
                }
                data.printJobs.forEachIndexed { index, item ->
                    insertRow(db, "print_jobs", id, item.id, index, this@SqliteSnapshotStore.json.encodeToString(PrintJob.serializer(), item))
                }
            }
    }

    private fun insertRow(db: SQLiteDatabase, table: String, storeId: String, id: String, index: Int, body: String) {
        db.insertOrThrow(table, null, ContentValues().apply {
            put("store_id", storeId); put("id", id); put("position", index); put("json", body)
        })
    }
    private fun readRows(db: SQLiteDatabase, table: String, storeId: String): List<String> =
        db.rawQuery("SELECT json FROM $table WHERE store_id=? ORDER BY position", arrayOf(storeId)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    override fun close() = helper.close()
}
