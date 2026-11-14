package ai.neargo.fnb.pos.core.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * 持久命令发送端（N2-03）。区别于厨房/支付演示 Outbox 和事件待发队列：
 * 发送前先保存原请求及ID；超时/重启后用同一ID查回执或重投，不新建UUID。
 * 状态：Saved → Sending → Committed / Rejected / Conflict；失败回到 Waiting；超过保留窗口标 Expired（不删除、不当新请求重发）。
 */
@Serializable enum class OutboxCommandState { Saved, Waiting, Sending, Committed, Rejected, Conflict, Expired }

data class OutboxCommand(
    val command: JournalCommand, val target: String, val state: OutboxCommandState, val attempts: Int,
    val nextAt: Long, val lastError: String, val receipt: JournalReceipt?, val createdAt: Long,
) {
    val terminal get() = state in setOf(OutboxCommandState.Committed, OutboxCommandState.Rejected, OutboxCommandState.Conflict, OutboxCommandState.Expired)
}

/** 目标协调方（主POS / 云端）。submit 超时或不可达时抛异常；query 查已持久回执，未知返回 null。 */
interface CommandTransport {
    suspend fun query(command: JournalCommand): JournalReceipt?
    suspend fun submit(command: JournalCommand): JournalReceipt
}

internal class CommandOutbox(private val database: () -> SQLiteDatabase) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun digest(c: JournalCommand) = MessageDigest.getInstance("SHA-256")
        .digest(json.encodeToString(JournalCommand.serializer(), c.copy(authorizationRef = null, policyVersion = null)).toByteArray()).joinToString("") { "%02x".format(it) }

    /** 保存原请求；同ID同内容幂等，同ID异内容拒绝。切店不改已保存请求的 storeId。 */
    suspend fun save(command: JournalCommand, target: String, now: Long): OutboxCommand = withContext(Dispatchers.IO) {
        val db = database()
        db.beginTransaction()
        try {
            val existing = db.rawQuery("SELECT digest FROM command_outbox WHERE store_id=? AND device_id=? AND command_id=?", arrayOf(command.storeId, command.deviceId, command.commandId)).use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            if (existing == null) db.insertOrThrow("command_outbox", null, ContentValues().apply {
                put("store_id", command.storeId); put("device_id", command.deviceId); put("command_id", command.commandId)
                put("digest", digest(command)); put("command", json.encodeToString(JournalCommand.serializer(), command)); put("target", target)
                put("state", OutboxCommandState.Saved.name); put("attempts", 0); put("next_at", now); put("last_error", ""); put("created_at", now)
            }) else require(existing == digest(command)) { "同一命令编号的内容不一致" }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        requireNotNull(get(command.storeId, command.deviceId, command.commandId))
    }

    suspend fun get(storeId: String, deviceId: String, commandId: String): OutboxCommand? = withContext(Dispatchers.IO) {
        query("store_id=? AND device_id=? AND command_id=?", arrayOf(storeId, deviceId, commandId)).firstOrNull()
    }

    /** 可发送：Saved/Waiting 且到期。 */
    suspend fun due(now: Long, limit: Int = 50): List<OutboxCommand> = withContext(Dispatchers.IO) {
        query("state IN ('Saved','Waiting') AND next_at<=? ORDER BY created_at LIMIT $limit", arrayOf(now.toString()))
    }

    suspend fun all(): List<OutboxCommand> = withContext(Dispatchers.IO) { query("1=1 ORDER BY created_at", emptyArray()) }

    /** 重启：Sending 结果未知，回到 Waiting，下次先查回执再按同ID重投。 */
    suspend fun recoverAfterRestart(): Int = withContext(Dispatchers.IO) {
        database().update("command_outbox", ContentValues().apply { put("state", OutboxCommandState.Waiting.name); put("last_error", "进程重启，结果待确认") }, "state=?", arrayOf(OutboxCommandState.Sending.name))
    }

    suspend fun markSending(c: OutboxCommand) = update(c, ContentValues().apply { put("state", OutboxCommandState.Sending.name); put("attempts", c.attempts + 1) })

    suspend fun onReceipt(c: OutboxCommand, receipt: JournalReceipt) = update(c, ContentValues().apply {
        put("state", when (receipt.status) { ReceiptStatus.Committed -> OutboxCommandState.Committed; ReceiptStatus.Rejected -> OutboxCommandState.Rejected; ReceiptStatus.Conflict -> OutboxCommandState.Conflict }.name)
        put("receipt", json.encodeToString(JournalReceipt.serializer(), receipt)); put("last_error", if (receipt.status == ReceiptStatus.Committed) "" else receipt.result)
    })

    /** 发送失败/超时：保持结果待确认，指数退避（1s 起，最长 60s）。 */
    suspend fun onFailure(c: OutboxCommand, error: String, now: Long) = update(c, ContentValues().apply {
        put("state", OutboxCommandState.Waiting.name); put("last_error", error.take(200))
        put("next_at", now + minOf(60_000L, 1_000L shl minOf(c.attempts, 6)))
    })

    /** 超过保留窗口仍未终态：标 Expired 交人工查询，不删除也不作为新请求重发。 */
    suspend fun expire(olderThan: Long): Int = withContext(Dispatchers.IO) {
        database().update("command_outbox", ContentValues().apply { put("state", OutboxCommandState.Expired.name); put("last_error", "超过保留窗口，需人工查询") },
            "state IN ('Saved','Waiting','Sending') AND created_at<?", arrayOf(olderThan.toString()))
    }

    private suspend fun update(c: OutboxCommand, values: ContentValues) = withContext(Dispatchers.IO) {
        database().update("command_outbox", values, "store_id=? AND device_id=? AND command_id=?", arrayOf(c.command.storeId, c.command.deviceId, c.command.commandId))
    }

    private fun query(where: String, args: Array<String>): List<OutboxCommand> =
        database().rawQuery("SELECT command,target,state,attempts,next_at,last_error,receipt,created_at FROM command_outbox WHERE $where", args).use {
            buildList { while (it.moveToNext()) add(OutboxCommand(
                json.decodeFromString(JournalCommand.serializer(), it.getString(0)), it.getString(1), OutboxCommandState.valueOf(it.getString(2)), it.getInt(3),
                it.getLong(4), it.getString(5), it.getString(6)?.let { r -> json.decodeFromString(JournalReceipt.serializer(), r) }, it.getLong(7))) }
        }

    companion object {
        fun createTables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS command_outbox (store_id TEXT NOT NULL,device_id TEXT NOT NULL,command_id TEXT NOT NULL,digest TEXT NOT NULL,command TEXT NOT NULL,target TEXT NOT NULL,state TEXT NOT NULL,attempts INTEGER NOT NULL,next_at INTEGER NOT NULL,last_error TEXT NOT NULL,receipt TEXT,created_at INTEGER NOT NULL,PRIMARY KEY(store_id,device_id,command_id))")
        }
    }
}

/** 发送循环：每条先查回执，未知再用同一命令重投；取消向上传播。 */
internal class CommandDispatcher(private val outbox: CommandOutbox, private val transport: CommandTransport, private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun flush(): Int {
        var settled = 0
        for (c in outbox.due(clock())) {
            outbox.markSending(c)
            try {
                val receipt = transport.query(c.command) ?: transport.submit(c.command)
                outbox.onReceipt(c.copy(attempts = c.attempts + 1), receipt); settled++
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { outbox.onFailure(c.copy(attempts = c.attempts + 1), e.message ?: e.javaClass.simpleName, clock()) }
        }
        return settled
    }
}

/** 本机回环：协调方即本机日志。真实主机/云端传输在 N5/N8 实现同一接口。 */
internal class LoopbackTransport(private val journal: CommandJournal, private val reducer: (JournalCommand) -> JournalChange) : CommandTransport {
    override suspend fun query(command: JournalCommand) = journal.receipt(command.storeId, command.deviceId, command.commandId)
    override suspend fun submit(command: JournalCommand) = journal.execute(command) { reducer(command) }
}
