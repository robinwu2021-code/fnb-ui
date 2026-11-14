package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.OutboxItem
import ai.neargo.fnb.pos.core.model.PrintJob
import ai.neargo.fnb.pos.core.model.StoreData
import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * 第一阶段事务存储：保留门店级 JSON，队列独立存行，所有行同一事务提交。
 * 尚不是领域事件日志，不能将这里的快照用作多 POS 或云端整库覆盖协议。
 */
internal class SqliteSnapshotStore(private val context: Context, private val name: String = "pos-workspace.db") : SnapshotStore, Closeable, ai.neargo.fnb.pos.core.lan.CommandReceipts {
    /** 主机去重：副机命令是否已与业务同事务提交。 */
    override suspend fun committed(storeId: String, deviceId: String, commandId: String) =
        journal.receipt(storeId, deviceId, commandId)?.status == ReceiptStatus.Committed

    /** 损坏时不删除库文件（系统默认处理会删库），保留原件供诊断导出。 */
    private val keepCorrupt = DatabaseErrorHandler { db -> android.util.Log.e("SqliteSnapshotStore", "数据库损坏，保留原件：${db.path}") }
    private val helper = object : SQLiteOpenHelper(context.applicationContext, name, null, DB_VERSION, keepCorrupt) {
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
            CommandJournal.upgradeToV3(db)
            CommandOutbox.createTables(db)
            createMigrations(db)
            recordMigration(db, DB_VERSION, "新建数据库")
        }
        /** 逐级迁移，均在 SQLiteOpenHelper 的同一事务中；失败整体回滚，不清库。 */
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            check(oldVersion in 1..2 && newVersion == DB_VERSION) { "缺少数据库迁移：$oldVersion → $newVersion" }
            if (oldVersion < 2) CommandJournal.createTables(db)
            CommandJournal.upgradeToV3(db)
            CommandOutbox.createTables(db)
            createMigrations(db)
            // 旧流水位：local-page / page-order-stream 事件保留原ID、顺序、载荷；新事件按聚合另起版本，不混入旧流。
            val (events, revision) = db.rawQuery("SELECT COUNT(*),COALESCE(MAX(revision),0) FROM command_events", null).use { it.moveToFirst(); it.getLong(0) to it.getLong(1) }
            recordMigration(db, DB_VERSION, "v$oldVersion→v$DB_VERSION；旧事件 $events 条为 legacy（envelope 为空），旧流最高版本 $revision")
        }
    }
    val journal = CommandJournal({ helper.writableDatabase })
    /** 云端映射表等附属表用同一个库文件 */
    fun writable(): SQLiteDatabase = helper.writableDatabase
    val commandOutbox = CommandOutbox { helper.writableDatabase }

    /** 迁移记录（版本、时间、说明），用于恢复页显示。 */
    fun migrations(): List<Triple<Int, Long, String>> = helper.readableDatabase.rawQuery("SELECT version,at,note FROM schema_migrations ORDER BY at", null).use {
        buildList { while (it.moveToNext()) add(Triple(it.getInt(0), it.getLong(1), it.getString(2))) }
    }

    private fun createMigrations(db: SQLiteDatabase) =
        db.execSQL("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER NOT NULL,at INTEGER NOT NULL,note TEXT NOT NULL)")
    private fun recordMigration(db: SQLiteDatabase, version: Int, note: String) =
        db.insertOrThrow("schema_migrations", null, ContentValues().apply { put("version", version); put("at", System.currentTimeMillis()); put("note", note) })
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

    override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt {
        val vault = VaultCodec.decode(source)
        return journal.execute(command, persistBusiness = { db -> writeSnapshot(db, vault) }) { JournalChange(command.type, command.payload, "committed") }
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

    val file: File get() = context.getDatabasePath(name)

    /** 当前恢复点：数据库版本、最新事件序号、未确认事件、未决发送命令、旧版事件数。 */
    fun recoveryPoint(): RecoveryPoint {
        val db = helper.readableDatabase
        fun long(sql: String) = db.rawQuery(sql, null).use { it.moveToFirst(); it.getLong(0) }
        return RecoveryPoint(
            at = System.currentTimeMillis(), dbVersion = db.version,
            lastSequence = long("SELECT COALESCE(MAX(sequence),0) FROM command_events"),
            unackedEvents = long("SELECT COUNT(*) FROM command_events WHERE acked=0").toInt(),
            openCommands = long("SELECT COUNT(*) FROM command_outbox WHERE state IN ('Saved','Waiting','Sending')").toInt(),
            legacyEvents = long("SELECT COUNT(*) FROM command_events WHERE envelope IS NULL").toInt(),
            pendingQueue = long("SELECT COUNT(*) FROM outbox").toInt(),
        )
    }

    /** 同步中心统计：回执按状态计数，事件按确认状态计数。 */
    fun commandStats(storeId: String): CommandStats {
        val db = helper.readableDatabase
        fun long(sql: String, vararg args: String) = db.rawQuery(sql, args).use { it.moveToFirst(); it.getLong(0) }
        fun receipts(status: String) = long("SELECT COUNT(*) FROM command_inbox WHERE store_id=? AND receipt LIKE ?", storeId, "%\"status\":\"$status\"%").toInt()
        return CommandStats(
            committed = receipts("Committed"), conflicts = receipts("Conflict"), rejected = receipts("Rejected"),
            lastAcceptedAt = long("SELECT COALESCE(MAX(accepted_at),0) FROM command_events WHERE store_id=?", storeId),
            unackedEvents = long("SELECT COUNT(*) FROM command_events WHERE store_id=? AND acked=0", storeId).toInt(),
            legacyUnacked = long("SELECT COUNT(*) FROM command_events WHERE store_id=? AND acked=0 AND envelope IS NULL", storeId).toInt(),
            oldestUnackedAt = long("SELECT COALESCE(MIN(accepted_at),0) FROM command_events WHERE store_id=? AND acked=0 AND accepted_at>0", storeId),
            openCommands = long("SELECT COUNT(*) FROM command_outbox WHERE store_id=? AND state IN ('Saved','Waiting','Sending')", storeId).toInt(),
            available = true,
        )
    }

    /** 一致性备份：VACUUM INTO 在单一读事务中生成完整副本（业务、Inbox/Outbox、事件、版本同一恢复点）。 */
    suspend fun backupTo(target: File) = withContext(Dispatchers.IO) {
        require(!target.exists()) { "备份文件已存在" }
        target.parentFile?.mkdirs()
        helper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(target.absolutePath))
    }

    /** 原样复制当前库文件（含损坏库），只用于诊断导出，不做任何解析。 */
    fun exportRaw(target: File) {
        target.parentFile?.mkdirs()
        file.copyTo(target, overwrite = false)
    }

    /** 用已校验的备份替换当前库：先关闭连接，复制后由下次访问重新打开（必要时升级）。 */
    fun replaceWith(validated: File) {
        helper.close()
        for (suffix in listOf("-journal", "-wal", "-shm")) File(file.path + suffix).delete()
        validated.copyTo(file, overwrite = true)
    }

    companion object { const val DB_VERSION = 3 }
}
