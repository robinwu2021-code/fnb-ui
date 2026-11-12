package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.data.JournalEvent
import ai.neargo.fnb.pos.core.model.DayClose
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.Instant
import java.time.ZoneId

/**
 * 云端连通判定（纯规则，方案 §2）：连续 2 次失败判离线、连续 2 次成功判在线（防抖）；
 * 离线时探测间隔 5 → 10 → 30 → 60 秒封顶，在线时 30 秒。
 */
data class CloudHealth(val online: Boolean = false, val okStreak: Int = 0, val failStreak: Int = 0, val lastError: String = "") {
    fun success() = copy(okStreak = okStreak + 1, failStreak = 0, lastError = "").let { if (!online && it.okStreak >= 2) it.copy(online = true) else it }
    fun failure(error: String) = copy(failStreak = failStreak + 1, okStreak = 0, lastError = error).let { if (online && it.failStreak >= 2) it.copy(online = false) else it }
    /** 系统报告断网或强制离线：立即离线 */
    fun forceOffline(reason: String) = copy(online = false, okStreak = 0, failStreak = maxOf(failStreak, 2), lastError = reason)
    fun nextProbeMs(): Long = if (online) 30_000 else when (failStreak) { 0, 1 -> 5_000; 2 -> 10_000; 3 -> 30_000; else -> 60_000 }
}

/** 页头云图标四态（方案 §6） */
sealed interface CloudBadge {
    data object NotConfigured : CloudBadge
    data class Online(val pending: Int) : CloudBadge
    data class Offline(val pending: Int, val reason: String) : CloudBadge
    data class Syncing(val done: Int, val total: Int) : CloudBadge
    data class NeedsAction(val count: Int) : CloudBadge
    /** 副 POS：不直连云端，由主 POS 上传 */
    data object ViaHost : CloudBadge
}

/** 本机 id ↔ 服务端号 的映射（方案 §4.1：订单 → checkNo、桌台 → tableNo；带服务端 version） */
interface CloudRefs {
    fun get(kind: String, localId: String): CloudRef?
    fun put(ref: CloudRef)
    /** 需人工处理的上传（业务拒绝 / 冲突重试后仍失败） */
    fun issues(): List<CloudIssue>
    fun addIssue(issue: CloudIssue)
    fun resolve(eventId: String)
}
@Serializable data class CloudRef(val kind: String, val localId: String, val remoteNo: String, val version: Long = 0)
@Serializable data class CloudIssue(val eventId: String, val type: String, val aggregateId: String, val code: Int, val message: String, val at: Long)

class MemoryCloudRefs : CloudRefs {
    private val refs = LinkedHashMap<String, CloudRef>(); private val list = mutableListOf<CloudIssue>()
    override fun get(kind: String, localId: String) = refs["$kind/$localId"]
    override fun put(ref: CloudRef) { refs["${ref.kind}/${ref.localId}"] = ref }
    override fun issues() = list.toList()
    override fun addIssue(issue: CloudIssue) { list.removeAll { it.eventId == issue.eventId }; list += issue }
    override fun resolve(eventId: String) { list.removeAll { it.eventId == eventId } }
}

/** 待上传事件来源：命令日志（`pending` 按序；`ack` 只在服务端确认持久接收后调用） */
interface EventSource {
    suspend fun pending(storeId: String, limit: Int): List<JournalEvent>
    suspend fun ack(storeId: String, eventId: String)
}

/** 一轮回放的结果 */
data class SyncRound(val uploaded: Int, val waiting: Int, val issues: Int, val stoppedBy: CloudException? = null)

/**
 * 云端同步适配（方案 §4.1–4.2）：在主 POS / 单机上按命令日志顺序回放到服务端 POS 合同。
 * - 幂等键 = 本机事件 ID（同一事件重放永远同键同内容；超时后重放即「按幂等键查结果」）。
 * - 网络错误：本轮停止，事件保留（结果未知不重建）。
 * - 业务拒绝 / 冲突重放后仍失败：记入「需处理」，这个聚合（订单 / 桌台）后续事件暂停，其他聚合照常。
 * - 服务端暂不支持的事件类型：保留不上传（等后端接口），不阻塞其他聚合。
 */
class CloudSync(
    private val api: CloudApi,
    private val refs: CloudRefs,
    private val events: EventSource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /** 第一批已对接的事件类型（草案已有接口） */
        val SUPPORTED = setOf("TableOpened", "TableChanged", "DayClosed")
    }

    suspend fun run(storeId: String, storeNo: String, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): SyncRound {
        val list = events.pending(storeId, 500)
        val blocked = refs.issues().map { it.aggregateId }.toMutableSet()
        var uploaded = 0; var waiting = 0
        if (list.any { it.type in SUPPORTED }) try { refreshTables(storeNo) } catch (x: CloudException) {
            return SyncRound(0, list.size, refs.issues().size, x)
        }
        list.forEachIndexed { i, e ->
            progress(i, list.size)
            when {
                e.aggregateId in blocked -> waiting++
                // 旧版事件（envelope 为空，迁移前的本机流）不上传；暂不支持的类型等后端接口。同聚合后续事件要等它
                e.envelope.isBlank() || e.type !in SUPPORTED -> { waiting++; blocked += e.aggregateId }
                else -> try {
                    upload(e, storeNo)
                    events.ack(storeId, e.eventId); uploaded++
                } catch (x: CancellationException) { throw x }
                catch (x: CloudException) {
                    when (x.kind) {
                        CloudErrorKind.Network, CloudErrorKind.Unauthorized -> return SyncRound(uploaded, list.size - uploaded, refs.issues().size, x)
                        else -> { refs.addIssue(CloudIssue(e.eventId, e.type, e.aggregateId, x.code, x.message.orEmpty(), clock())); blocked += e.aggregateId }
                    }
                } catch (x: Exception) {
                    // 本机载荷无法转换（字段缺失、格式不符）：不重试，交人工，不影响其他聚合
                    refs.addIssue(CloudIssue(e.eventId, e.type, e.aggregateId, 0, "本机事件无法转换：${x.message ?: x.javaClass.simpleName}", clock())); blocked += e.aggregateId
                }
            }
        }
        progress(list.size, list.size)
        return SyncRound(uploaded, waiting, refs.issues().size)
    }

    /** 桌台：本机桌号 = 服务端桌台 name（门店里写在桌上的号）；拉一次服务端桌台表建立映射 */
    private suspend fun refreshTables(storeNo: String) {
        api.tableList(storeNo).forEach { t -> refs.put(CloudRef("table", t.name, t.tableNo, t.version)) }
    }

    private fun tableNo(local: String) = refs.get("table", local)?.remoteNo
        ?: throw CloudException(CloudErrorKind.NotFound, 10404, "服务端没有桌台 $local，请先在后台建桌或同步桌台")

    /**
     * 幂等键 = 本机事件 ID；人工在「需处理」点了重试则为「事件ID#第几次」（服务端按键固定业务结果，同键重试只会得到同样的拒绝）
     */
    private fun keyOf(e: JournalEvent) = refs.get("attempt", e.eventId)?.let { "${e.eventId}#${it.remoteNo}" } ?: e.eventId

    private suspend fun upload(e: JournalEvent, storeNo: String) {
        val p = json.parseToJsonElement(e.payload).jsonObject
        val key = keyOf(e)
        when (e.type) {
            "TableOpened" -> {
                val tableId = p.str("tableId"); val order = p.obj("order")
                val guests = p.obj("table")["guests"]!!.jsonPrimitive.int
                val r = api.openTable(storeNo, key, tableNo(tableId), guests)
                refs.put(CloudRef("order", order.str("orderId"), r.check.checkNo, r.check.version))
                refs.put(CloudRef("table", tableId, r.table.tableNo, r.table.version))
            }
            "TableChanged" -> {
                // 载荷：tableId = 原桌；受影响桌台里带着同一订单的那张是目标桌
                val moved = p["affectedTables"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it.obj("order").str("orderId").isNotBlank() }
                    ?: throw CloudException(CloudErrorKind.Rejected, 10400, "换桌事件缺少目标桌")
                val orderId = moved.obj("order").str("orderId"); val target = moved.obj("table").str("id")
                val check = refs.get("order", orderId) ?: throw CloudException(CloudErrorKind.NotFound, 10404, "订单还没有上云（开台未同步）")
                val r = withConflictRetry(storeNo, check) { v -> api.transferTable(storeNo, key, check.remoteNo, tableNo(target), v) }
                refs.put(check.copy(version = r.version))
            }
            "DayClosed" -> {
                val d = json.decodeFromJsonElement(DayClose.serializer(), p)
                val date = Instant.ofEpochMilli(d.end).atZone(zone).toLocalDate().toString()
                val preview = api.closePreview(storeNo, "$key:preview", date)
                if (preview.blockingItems.isNotEmpty())
                    throw CloudException(CloudErrorKind.Rejected, 10400, "日结被阻断：" + preview.blockingItems.joinToString("；") { it.reason })
                api.closeSign(storeNo, "$key:sign", preview.closeNo, preview.version)
            }
        }
    }

    /** 10409 版本冲突：重读账单拿最新 version，按本机意图重放一次；仍冲突交人工 */
    private suspend fun <T> withConflictRetry(storeNo: String, check: CloudRef, op: suspend (Long) -> T): T =
        try { op(check.version) } catch (x: CloudException) {
            if (x.kind != CloudErrorKind.Conflict) throw x
            val fresh = api.checkDetail(storeNo, check.remoteNo).check.version
            refs.put(check.copy(version = fresh)); op(fresh)
        }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content.orEmpty()
    private fun JsonObject.obj(k: String) = this[k]!!.jsonObject
    @Suppress("unused") private fun JsonObject.num(k: String) = this[k]!!.jsonPrimitive.long
}
