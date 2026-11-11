package ai.neargo.fnb.pos.core.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

/** 命令信封版本。旧版（v1：单聚合、local-page）只存在于历史事件，不再接受新命令。 */
const val COMMAND_SCHEMA_VERSION = 2
/** 当前支持的业务载荷版本。 */
val SUPPORTED_PAYLOAD_VERSIONS = setOf(1)

@Serializable data class AggregateVersion(val id: String, val expected: Long)

/**
 * 命令信封（N2-01）。身份字段目前来自本机上下文，N4 前不是可信身份证明。
 * occurredAt 为客户端时间，仅供展示；acceptedAt 与门店序号由协调方生成。
 * authorizationRef / policyVersion 属于传输凭据，不参与幂等摘要。
 */
@Serializable
data class JournalCommand(
    val storeId: String,
    val deviceId: String,
    val commandId: String,
    val type: String,
    val payload: String,
    val aggregates: List<AggregateVersion>,
    val tenantId: String = "local-tenant",
    val actorId: String = "unknown",
    val correlationId: String = commandId,
    val coordinatorId: String = deviceId,
    val authorityEpoch: Long = 1,
    val occurredAt: Long = 0,
    val schemaVersion: Int = COMMAND_SCHEMA_VERSION,
    val payloadVersion: Int = 1,
    val authorizationRef: String? = null,
    val policyVersion: String? = null,
)

@Serializable enum class ReceiptStatus { Committed, Rejected, Conflict }

/** 持久回执：Committed 含参与聚合的新版本；Rejected/Conflict 为终态，同ID重放返回同一结果。 */
@Serializable
data class JournalReceipt(
    val status: ReceiptStatus,
    val eventId: String?,
    val revisions: Map<String, Long>,
    val result: String,
    val acceptedAt: Long,
    val sequence: Long = 0,
) {
    /** 主聚合（第一个）的新版本；未提交为 0。 */
    val revision get() = revisions.values.firstOrNull() ?: 0L
}

data class JournalChange(val eventType: String, val eventPayload: String, val result: String)
data class JournalEvent(
    val sequence: Long, val eventId: String, val aggregateId: String, val revision: Long, val type: String, val payload: String,
    val envelope: String = "", val acceptedAt: Long = 0, val causationId: String = "", val correlationId: String = "",
)

/** 业务拒绝：作为 Rejected 终态回执持久保存。 */
class CommandRejected(reason: String) : Exception(reason)

/** 本地协议内核。调用方先校验身份及权限；reducer只能纯计算。 */
internal class CommandJournal(private val database: () -> SQLiteDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val json = Json { encodeDefaults = true }

    /** 幂等摘要：业务内容及作用域，不含传输凭据。 */
    private fun digest(c: JournalCommand): String {
        val body = json.encodeToString(JournalCommand.serializer(), c.copy(authorizationRef = null, policyVersion = null))
        return MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** 协议/作用域校验失败直接拒绝（不写Inbox）：不能确认来源时不产生任何回执。 */
    private fun validate(c: JournalCommand) {
        require(listOf(c.tenantId, c.storeId, c.deviceId, c.actorId, c.commandId, c.correlationId, c.coordinatorId, c.type).all { it.isNotBlank() }) { "命令缺少作用域字段" }
        require(c.schemaVersion == COMMAND_SCHEMA_VERSION) { "不支持的命令版本：${c.schemaVersion}" }
        require(c.payloadVersion in SUPPORTED_PAYLOAD_VERSIONS) { "不支持的载荷版本：${c.payloadVersion}" }
        require(c.aggregates.isNotEmpty() && c.aggregates.map { it.id }.distinct().size == c.aggregates.size) { "参与聚合为空或重复" }
        require(c.aggregates.all { it.id.isNotBlank() && it.expected in 0 until Long.MAX_VALUE }) { "聚合版本无效" }
        require(c.authorityEpoch > 0) { "epoch无效" }
    }

    suspend fun execute(command: JournalCommand, persistBusiness: (SQLiteDatabase) -> Unit = {}, reducer: (Map<String, String?>) -> JournalChange): JournalReceipt = withContext(Dispatchers.IO) {
        validate(command)
        val digest = digest(command)
        val db = database()
        db.beginTransaction()
        try {
            // 协调方及 epoch：门店首个命令登记，之后不一致即作用域错误。
            db.rawQuery("SELECT tenant_id,coordinator_id,epoch FROM command_scope WHERE store_id=?", arrayOf(command.storeId)).use {
                if (it.moveToFirst()) {
                    require(it.getString(0) == command.tenantId) { "租户不一致" }
                    require(it.getString(1) == command.coordinatorId && it.getLong(2) == command.authorityEpoch) { "协调方或epoch不一致，拒绝写入" }
                } else db.insertOrThrow("command_scope", null, ContentValues().apply {
                    put("store_id", command.storeId); put("tenant_id", command.tenantId); put("coordinator_id", command.coordinatorId); put("epoch", command.authorityEpoch)
                })
            }
            val args = arrayOf(command.storeId, command.deviceId, command.commandId)
            val prior = db.rawQuery("SELECT digest,receipt FROM command_inbox WHERE store_id=? AND device_id=? AND command_id=?", args).use {
                if (it.moveToFirst()) {
                    require(it.getString(0) == digest) { "同一命令编号的内容不一致" }
                    json.decodeFromString(JournalReceipt.serializer(), it.getString(1))
                } else null
            }
            val receipt = prior ?: run {
                val now = clock()
                val current = command.aggregates.associate { a ->
                    a.id to db.rawQuery("SELECT revision,state FROM command_aggregates WHERE store_id=? AND id=?", arrayOf(command.storeId, a.id)).use {
                        if (it.moveToFirst()) it.getLong(0) to it.getString(1) else 0L to null
                    }
                }
                val stale = command.aggregates.filter { current.getValue(it.id).first != it.expected }
                val result = if (stale.isNotEmpty()) {
                    JournalReceipt(ReceiptStatus.Conflict, null, current.mapValues { it.value.first },
                        "业务版本冲突：" + stale.joinToString("，") { "${it.id} 期望${it.expected} 当前${current.getValue(it.id).first}" }, now)
                } else {
                    var reason = "业务拒绝"
                    val change = try { reducer(current.mapValues { it.value.second }) } catch (e: CommandRejected) { reason = e.message ?: reason; null }
                    if (change == null) JournalReceipt(ReceiptStatus.Rejected, null, current.mapValues { it.value.first }, reason, now)
                    else {
                        require(change.eventType.isNotBlank())
                        val eventId = UUID.randomUUID().toString()
                        val revisions = command.aggregates.associate { it.id to it.expected + 1 }
                        for (a in command.aggregates) {
                            val state = json.encodeToString(AggregateState.serializer(), AggregateState(a.id.substringBefore(':'), eventId, change.eventType, command.commandId))
                            db.insertWithOnConflict("command_aggregates", null, ContentValues().apply {
                                put("store_id", command.storeId); put("id", a.id); put("revision", revisions.getValue(a.id)); put("state", state)
                            }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
                        }
                        val primary = command.aggregates.first().id
                        val sequence = db.insertOrThrow("command_events", null, ContentValues().apply {
                            put("event_id", eventId); put("store_id", command.storeId); put("aggregate_id", primary)
                            put("revision", revisions.getValue(primary)); put("type", change.eventType); put("payload", change.eventPayload); put("acked", 0)
                            put("envelope", json.encodeToString(JournalCommand.serializer(), command.copy(payload = "", authorizationRef = null)))
                            put("accepted_at", now); put("causation_id", command.commandId); put("correlation_id", command.correlationId)
                        })
                        persistBusiness(db)
                        JournalReceipt(ReceiptStatus.Committed, eventId, revisions, change.result, now, sequence)
                    }
                }
                saveInbox(db, command, digest, result)
                result
            }
            db.setTransactionSuccessful()
            receipt
        } finally { db.endTransaction() }
    }

    private fun saveInbox(db: SQLiteDatabase, command: JournalCommand, digest: String, receipt: JournalReceipt) {
        db.insertOrThrow("command_inbox", null, ContentValues().apply {
            put("store_id", command.storeId); put("device_id", command.deviceId); put("command_id", command.commandId)
            put("digest", digest); put("receipt", json.encodeToString(JournalReceipt.serializer(), receipt))
        })
    }

    /** 只查回执，不执行（发送端重启或超时后查询）。 */
    suspend fun receipt(storeId: String, deviceId: String, commandId: String): JournalReceipt? = withContext(Dispatchers.IO) {
        database().rawQuery("SELECT receipt FROM command_inbox WHERE store_id=? AND device_id=? AND command_id=?", arrayOf(storeId, deviceId, commandId)).use {
            if (it.moveToFirst()) json.decodeFromString(JournalReceipt.serializer(), it.getString(0)) else null
        }
    }

    suspend fun pending(storeId: String, limit: Int = 100): List<JournalEvent> = withContext(Dispatchers.IO) {
        require(limit in 1..500)
        database().rawQuery("SELECT sequence,event_id,aggregate_id,revision,type,payload,envelope,accepted_at,causation_id,correlation_id FROM command_events WHERE store_id=? AND acked=0 ORDER BY sequence LIMIT ?", arrayOf(storeId, limit.toString())).use {
            buildList { while (it.moveToNext()) add(JournalEvent(it.getLong(0), it.getString(1), it.getString(2), it.getLong(3), it.getString(4), it.getString(5),
                it.getString(6).orEmpty(), it.getLong(7), it.getString(8).orEmpty(), it.getString(9).orEmpty())) }
        }
    }

    /** 仅在远端确认持久接收后调用；不删除事件或Inbox，重放仍可查询原回执。 */
    suspend fun ack(storeId: String, eventId: String): Int = withContext(Dispatchers.IO) {
        database().update("command_events", ContentValues().apply { put("acked", 1) }, "store_id=? AND event_id=? AND acked=0", arrayOf(storeId, eventId))
    }

    @Serializable private data class AggregateState(val kind: String, val lastEventId: String, val lastEventType: String, val lastCommandId: String)

    companion object {
        fun createTables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE command_aggregates (store_id TEXT NOT NULL,id TEXT NOT NULL,revision INTEGER NOT NULL,state TEXT NOT NULL,PRIMARY KEY(store_id,id))")
            db.execSQL("CREATE TABLE command_inbox (store_id TEXT NOT NULL,device_id TEXT NOT NULL,command_id TEXT NOT NULL,digest TEXT NOT NULL,receipt TEXT NOT NULL,PRIMARY KEY(store_id,device_id,command_id))")
            db.execSQL("CREATE TABLE command_events (sequence INTEGER PRIMARY KEY AUTOINCREMENT,event_id TEXT NOT NULL UNIQUE,store_id TEXT NOT NULL,aggregate_id TEXT NOT NULL,revision INTEGER NOT NULL,type TEXT NOT NULL,payload TEXT NOT NULL,acked INTEGER NOT NULL DEFAULT 0,UNIQUE(store_id,aggregate_id,revision))")
            db.execSQL("CREATE INDEX command_pending ON command_events(store_id,acked,sequence)")
        }

        /** v3：信封列、作用域登记表。旧事件 envelope 为空即 legacy（schema v1、local-page）。 */
        fun upgradeToV3(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE command_events ADD COLUMN envelope TEXT")
            db.execSQL("ALTER TABLE command_events ADD COLUMN accepted_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE command_events ADD COLUMN causation_id TEXT")
            db.execSQL("ALTER TABLE command_events ADD COLUMN correlation_id TEXT")
            db.execSQL("CREATE TABLE command_scope (store_id TEXT PRIMARY KEY NOT NULL,tenant_id TEXT NOT NULL,coordinator_id TEXT NOT NULL,epoch INTEGER NOT NULL)")
        }
    }
}
