package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.data.JournalEvent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
    data class Online(val pending: Int, val waiting: Int = 0) : CloudBadge
    data class Offline(val pending: Int, val reason: String, val limit: CloudLimit = CloudLimit.None) : CloudBadge
    data class Syncing(val done: Int, val total: Int) : CloudBadge
    data class NeedsAction(val count: Int) : CloudBadge
    /** 副 POS：不直连云端，由主 POS 上传 */
    data object ViaHost : CloudBadge
}

/** 本机 id ↔ 服务端号 的映射（方案 §4.1：订单 → checkNo、桌台 → tableNo；带服务端 version） */
interface CloudRefs {
    fun get(kind: String, localId: String): CloudRef?
    /** 某类映射全部（用于价差等列表） */
    fun all(kind: String): List<CloudRef>
    fun put(ref: CloudRef)
    /** 需人工处理的上传（业务拒绝 / 冲突重试后仍失败） */
    fun issues(): List<CloudIssue>
    fun addIssue(issue: CloudIssue)
    fun resolve(eventId: String)
}
@Serializable data class CloudRef(val kind: String, val localId: String, val remoteNo: String, val version: Long = 0)
/** label：给店员看的对象（桌 H01、销售单 R0158、库存单 RK0003…）；aggregateId 是内部阻塞键 */
@Serializable data class CloudIssue(val eventId: String, val type: String, val aggregateId: String, val code: Int, val message: String, val at: Long, val label: String = "")

class MemoryCloudRefs : CloudRefs {
    private val refs = LinkedHashMap<String, CloudRef>(); private val list = mutableListOf<CloudIssue>()
    override fun get(kind: String, localId: String) = refs["$kind/$localId"]
    override fun all(kind: String) = refs.values.filter { it.kind == kind }
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

/** 这条事件按载荷判定还不能上传（服务端接口未对接）：保留在本机，挡同单后续事件，不进需处理 */
class WaitForBackend(message: String) : Exception(message)

/** 一轮回放的结果：uploaded 已上传；local 只在本机的事件（直接标为已处理）；waiting 等后端接口或被同单前序事件挡住 */
/**
 * 一轮回放的结果：uploaded 已上传；local 只在本机的事件（直接标为已处理）；waiting 被挡住没上传的（等后端接口，或被需处理的同单前序事件挡住）；
 * backendWait 其中「等服务端接口」的那部分——它们按设计一直留在本机，不算待上传（页头、离线时限都不该受它们影响）。
 */
data class SyncRound(val uploaded: Int, val waiting: Int, val issues: Int, val stoppedBy: CloudException? = null, val local: Int = 0, val backendWait: Int = 0)
/**
 * 云端同步流水线（下一批对接方案 §0–§2）：在主 POS / 单机上按命令日志顺序回放到服务端。流水线只管顺序、阻塞、需处理、确认；
 * 每类事件怎么上传由各业态的 [EventUploader] 负责（公共：日结；餐饮：开台 / 换桌 / 送厨 / 结账；零售：进销存）。
 * - 事件三类：上传（上传器的 types）、只在本机（直接 ack，不挡同单后续事件）、其余等后端接口。
 * - 幂等键 = 本机事件 ID；一条事件拆成多次调用时按「事件ID:步骤[:行]」派生，重放时规则不变。
 * - 网络错误：本轮停止，事件保留（结果未知不重建）。
 * - 业务拒绝 / 冲突重放后仍失败：记入「需处理」，这个聚合后续事件暂停，其他聚合照常。
 */
class CloudSync(
    private val api: CloudApi,
    private val refs: CloudRefs,
    private val events: EventSource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val currency: String = "CNY",
    /** 当前门店是零售（进销存单据上传）；餐饮门店的库存调整只在本机 */
    private val inventory: Boolean = false,
    /** 员工下单的买家号（真实后端要求买家号或会话号之一；散客方案待后端定） */
    private val buyerNo: String = "",
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 幂等键 = 本机事件 ID；人工在「需处理」点了重试则为「事件ID#第几次」（服务端按键固定业务结果，同键重试只会得到同样的拒绝）
     */
    private fun keyOf(e: JournalEvent) = refs.get("attempt", e.eventId)?.let { "${e.eventId}#${it.remoteNo}" } ?: e.eventId

    suspend fun run(storeId: String, storeNo: String, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): SyncRound {
        val list = events.pending(storeId, 500)
        // 挡住的键 → 是否因「等服务端接口」（false = 因需处理）；需处理记的是挡住的键（见 blockKeyOf）
        val blocked = refs.issues().associate { it.aggregateId to false }.toMutableMap()
        var uploaded = 0; var waiting = 0; var local = 0; var backendWait = 0
        val ctx = UploadContext(api, refs, storeNo, zone, currency, buyerNo, inventory)
        val uploaders = listOf(CommonUploader(ctx), DiningUploader(ctx), RetailUploader(ctx))
        val uploads = uploaders.flatMap { it.types }.toSet()
        // 各上传器有自己的事件时先做准备（餐饮拉桌台映射）
        for (u in uploaders) if (list.any { it.type in u.types }) try { u.prepare() } catch (x: CloudException) {
            return SyncRound(0, list.size, refs.issues().size, x)
        }
        list.forEachIndexed { i, e ->
            progress(i, list.size)
            val p = runCatching { json.parseToJsonElement(e.payload).jsonObject }.getOrNull()
            // 挡住同单后续事件的键：上传器按单号等细分（零售），否则按事件主聚合（订单、桌台）
            val key = uploaders.firstNotNullOfOrNull { it.blockKey(e, p) } ?: e.aggregateId
            val label = { runCatching { p?.let { pp -> uploaders.firstNotNullOfOrNull { it.label(e, pp) } } }.getOrNull().orEmpty() }
            when {
                key in blocked -> { waiting++; if (blocked[key] == true) backendWait++ }
                // 旧版事件（envelope 为空，迁移前的本机流）不上传
                e.envelope.isBlank() -> { waiting++; backendWait++; blocked[key] = true }
                uploaders.any { it.handledLocally(e) } -> { events.ack(storeId, e.eventId); local++ }
                e.type !in uploads -> { waiting++; backendWait++; blocked[key] = true }   // 等后端接口；同单后续事件要等它
                else -> try {
                    uploaders.first { e.type in it.types }.upload(e, p ?: error("事件载荷不是 JSON"), keyOf(e))
                    events.ack(storeId, e.eventId); uploaded++
                } catch (x: CancellationException) { throw x }
                catch (x: WaitForBackend) { waiting++; backendWait++; blocked[key] = true }   // 这条按载荷判定还不能上传（如扫码收款）：保留并挡同单
                catch (x: CloudException) {
                    when (x.kind) {
                        CloudErrorKind.Network, CloudErrorKind.Unauthorized -> return SyncRound(uploaded, list.size - uploaded - local, refs.issues().size, x, local)
                        else -> { refs.addIssue(CloudIssue(e.eventId, e.type, key, x.code, x.message.orEmpty(), clock(), label())); blocked.putIfAbsent(key, false) }
                    }
                } catch (x: Exception) {
                    // 本机载荷无法转换（字段缺失、格式不符）：不重试，交人工，不影响其他聚合
                    refs.addIssue(CloudIssue(e.eventId, e.type, key, 0, "本机事件无法转换：${x.message ?: x.javaClass.simpleName}", clock(), label())); blocked.putIfAbsent(key, false)
                }
            }
        }
        progress(list.size, list.size)
        return SyncRound(uploaded, waiting, refs.issues().size, local = local, backendWait = backendWait)
    }
}
