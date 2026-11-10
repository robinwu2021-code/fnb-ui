package ai.neargo.fnb.pos.core.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class JournalCommand(val storeId: String, val deviceId: String, val commandId: String, val aggregateId: String, val expectedRevision: Long, val type: String, val payload: String)
data class JournalChange(val state: String, val eventType: String, val eventPayload: String, val result: String)
@Serializable
data class JournalReceipt(val eventId: String, val revision: Long, val result: String)
data class JournalEvent(val sequence: Long, val eventId: String, val aggregateId: String, val revision: Long, val type: String, val payload: String)

/** 本地协议内核。调用方必须先校验身份、epoch及权限；reducer只能纯计算。 */
internal class CommandJournal(private val database: () -> SQLiteDatabase) {
    private val json = Json { encodeDefaults = true }

    suspend fun execute(command: JournalCommand, persistBusiness: (SQLiteDatabase) -> Unit = {}, reducer: (String?) -> JournalChange): JournalReceipt = withContext(Dispatchers.IO) {
        require(listOf(command.storeId, command.deviceId, command.commandId, command.aggregateId, command.type).all { it.isNotBlank() })
        require(command.expectedRevision in 0 until Long.MAX_VALUE)
        val digest = MessageDigest.getInstance("SHA-256").digest(json.encodeToString(JournalCommand.serializer(), command).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val db = database()
        db.beginTransaction()
        try {
            val args = arrayOf(command.storeId, command.deviceId, command.commandId)
            val prior = db.rawQuery("SELECT digest,receipt FROM command_inbox WHERE store_id=? AND device_id=? AND command_id=?", args).use {
                if (it.moveToFirst()) {
                    require(it.getString(0) == digest) { "同一命令编号的内容不一致" }
                    json.decodeFromString(JournalReceipt.serializer(), it.getString(1))
                } else null
            }
            val receipt = if (prior != null) prior else {
                val current = db.rawQuery("SELECT revision,state FROM command_aggregates WHERE store_id=? AND id=?", arrayOf(command.storeId, command.aggregateId)).use {
                    if (it.moveToFirst()) it.getLong(0) to it.getString(1) else 0L to null
                }
                require(current.first == command.expectedRevision) { "业务版本冲突：当前${current.first}" }
                val change = reducer(current.second)
                require(change.eventType.isNotBlank())
                val result = JournalReceipt(UUID.randomUUID().toString(), current.first + 1, change.result)
                db.insertWithOnConflict("command_aggregates", null, ContentValues().apply {
                    put("store_id", command.storeId); put("id", command.aggregateId); put("revision", result.revision); put("state", change.state)
                }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
                db.insertOrThrow("command_events", null, ContentValues().apply {
                    put("event_id", result.eventId); put("store_id", command.storeId); put("aggregate_id", command.aggregateId)
                    put("revision", result.revision); put("type", change.eventType); put("payload", change.eventPayload); put("acked", 0)
                })
                db.insertOrThrow("command_inbox", null, ContentValues().apply {
                    put("store_id", command.storeId); put("device_id", command.deviceId); put("command_id", command.commandId)
                    put("digest", digest); put("receipt", json.encodeToString(JournalReceipt.serializer(), result))
                })
                persistBusiness(db)
                result
            }
            db.setTransactionSuccessful()
            receipt
        } finally { db.endTransaction() }
    }

    suspend fun pending(storeId: String, limit: Int = 100): List<JournalEvent> = withContext(Dispatchers.IO) {
        require(limit in 1..500)
        database().rawQuery("SELECT sequence,event_id,aggregate_id,revision,type,payload FROM command_events WHERE store_id=? AND acked=0 ORDER BY sequence LIMIT ?", arrayOf(storeId, limit.toString())).use {
            buildList { while (it.moveToNext()) add(JournalEvent(it.getLong(0), it.getString(1), it.getString(2), it.getLong(3), it.getString(4), it.getString(5))) }
        }
    }

    /** 仅在远端确认持久接收后调用；不删除事件或Inbox，重放仍可查询原回执。 */
    suspend fun ack(storeId: String, eventId: String): Int = withContext(Dispatchers.IO) {
        database().update("command_events", ContentValues().apply { put("acked", 1) }, "store_id=? AND event_id=? AND acked=0", arrayOf(storeId, eventId))
    }

    companion object {
        fun createTables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE command_aggregates (store_id TEXT NOT NULL,id TEXT NOT NULL,revision INTEGER NOT NULL,state TEXT NOT NULL,PRIMARY KEY(store_id,id))")
            db.execSQL("CREATE TABLE command_inbox (store_id TEXT NOT NULL,device_id TEXT NOT NULL,command_id TEXT NOT NULL,digest TEXT NOT NULL,receipt TEXT NOT NULL,PRIMARY KEY(store_id,device_id,command_id))")
            db.execSQL("CREATE TABLE command_events (sequence INTEGER PRIMARY KEY AUTOINCREMENT,event_id TEXT NOT NULL UNIQUE,store_id TEXT NOT NULL,aggregate_id TEXT NOT NULL,revision INTEGER NOT NULL,type TEXT NOT NULL,payload TEXT NOT NULL,acked INTEGER NOT NULL DEFAULT 0,UNIQUE(store_id,aggregate_id,revision))")
            db.execSQL("CREATE INDEX command_pending ON command_events(store_id,acked,sequence)")
        }
    }
}
