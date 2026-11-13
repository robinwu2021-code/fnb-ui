package ai.neargo.fnb.pos.core.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** 本机保存的云端连接设置（每台设备一份；门店号 = 服务端 storeNo） */
@Serializable data class CloudSettings(val baseUrl: String = "", val storeNo: String = "", val token: String = "",
    /** 员工下单要买家号（散客方案待后端定）：联调先用一个默认买家号 */
    val buyerNo: String = "") {
    val configured get() = baseUrl.isNotBlank() && storeNo.isNotBlank()
}

interface CloudSettingsStore {
    fun load(): CloudSettings
    fun save(settings: CloudSettings)
    /** 最近一次连上云端的时间（重启后仍记得，离线时限按它算） */
    fun lastOnlineAt(): Long = 0
    fun saveLastOnlineAt(at: Long) {}
}

/** 日结已在云端预览、等另一个账号签核（二人复核）：营业日、日结单号 */
data class CloudCloseReview(val businessDate: String, val closeNo: String)

/** 送厨下单时服务端金额与本机不一致（本机批次 id、本机金额、服务端金额，分） */
data class CloudPriceDiff(val batchId: String, val localMinor: Long, val remoteMinor: Long)

/** 页面展示用的云端状态 */
data class CloudState(
    val settings: CloudSettings = CloudSettings(),
    val health: CloudHealth = CloudHealth(),
    val badge: CloudBadge = CloudBadge.NotConfigured,
    val uploader: Boolean = true,
    val pending: Int = 0,
    val issues: List<CloudIssue> = emptyList(),
    val lastRound: SyncRound? = null,
    val lastSyncAt: Long = 0,
    /** 服务端门店桌台数（探测时带回，用于确认门店号填对了） */
    val tables: Int = 0,
    val lastOnlineAt: Long = 0,
    val limit: CloudLimit = CloudLimit.None,
    val priceDiffs: List<CloudPriceDiff> = emptyList(),
    val closeReviews: List<CloudCloseReview> = emptyList(),
    /** 等服务端接口、留在本机的事件数（上一轮回放得出）：不算待上传 */
    val backendWait: Int = 0,
) {
    /** 真正等着上传的事件数 */
    val uploadable get() = (pending - backendWait).coerceAtLeast(0)
}

/**
 * 云端连通与同步循环（方案 §2–§6）：
 * - 只有主 POS / 单机连云（`uploader`）；副 POS 显示「经主 POS」。
 * - 系统无网络或强制离线：立即离线，不探测。
 * - 有网：按 [CloudHealth.nextProbeMs] 探测（取 1 张桌台）；判为在线且有待上传时回放一轮。
 * - 网络恢复、新事件、手动「立即同步」会提前唤醒。
 */
class CloudMonitor(
    private val settingsStore: CloudSettingsStore,
    private val api: CloudApi,
    /** 当前本机门店 id；未进店为 null */
    private val storeId: () -> String?,
    /** 按「本机门店@服务端门店」取映射表（换门店或换服务端门店不串号） */
    private val refsFor: (scope: String) -> CloudRefs,
    private val events: EventSource,
    private val network: Flow<Boolean>,
    private val uploader: Flow<Boolean>,
    private val pending: Flow<Int>,
    private val clock: () -> Long = System::currentTimeMillis,
    /** 一轮上传后（ack 改了待上传数）刷新统计 */
    private val afterRound: suspend () -> Unit = {},
    /** 当前门店是零售（上传进销存单据） */
    private val retailStore: () -> Boolean = { false },
) {
    private val s = MutableStateFlow(CloudState(settings = settingsStore.load(), lastOnlineAt = settingsStore.lastOnlineAt()))
    val state: StateFlow<CloudState> = s.asStateFlow()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val roundLock = Mutex()
    @Volatile private var netUp = false

    fun start(scope: CoroutineScope) {
        observe(scope)
        scope.launch { while (true) { tick(); withTimeoutOrNull(delayMs()) { wake.receive() } } }
    }

    /** 只跟踪网络、角色、待上传数（测试里手动 tick） */
    internal fun observe(scope: CoroutineScope) {
        scope.launch {
            combine(network, uploader, pending) { n, u, p -> Triple(n, u, p) }.distinctUntilChanged().collect { (n, u, p) ->
                val wasUp = netUp; netUp = n
                s.update { it.copy(uploader = u, pending = p).withBadge() }
                if (n && !wasUp) s.update { it.copy(health = it.health.copy(failStreak = 0)) }
                wake.trySend(Unit)
            }
        }
    }

    private fun delayMs(): Long = s.value.let { st ->
        when {
            !st.settings.configured || !st.uploader -> 60_000
            !netUp -> 60_000                      // 等系统网络恢复事件唤醒
            else -> st.health.nextProbeMs()
        }
    }

    /** 一次探测；在线且有待上传则回放。供循环和测试调用。 */
    suspend fun tick() {
        val st = s.value
        if (!st.settings.configured || !st.uploader) { s.update { it.withBadge() }; return }
        if (!netUp) { s.update { it.copy(health = it.health.forceOffline("本机无网络或已强制离线")).withBadge() }; return }
        val probe = runCatching { api.probe(st.settings.storeNo) }
        probe.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        s.update { cur ->
            val h = probe.fold({ cur.health.success() }, { e ->
                // 门店号或令牌错误：属于配置问题，直接判离线并显示原因
                if (e is CloudException && e.kind != CloudErrorKind.Network) cur.health.forceOffline(e.message.orEmpty()) else cur.health.failure(e.message ?: "连接失败")
            })
            cur.copy(health = h, tables = probe.getOrNull()?.tables ?: cur.tables, lastOnlineAt = if (probe.isSuccess) markOnline(cur.lastOnlineAt) else cur.lastOnlineAt).withBadge()
        }
        if (s.value.health.online && s.value.uploadable > 0) runRound()
    }

    /** 手动「立即同步」：不等防抖，直接探测并回放 */
    suspend fun syncNow(): Result<SyncRound> {
        val st = s.value
        if (!st.settings.configured) return Result.failure(IllegalStateException("尚未设置云端地址和门店号"))
        if (!st.uploader) return Result.failure(IllegalStateException("副 POS 由主 POS 上传云端"))
        if (!netUp) return Result.failure(IllegalStateException("当前离线，联网后自动上传"))
        return runCatching { runRound() }.onFailure { if (it is CancellationException) throw it }
    }

    /** 测试连接：用当前填写（未保存）的设置探测一次 */
    suspend fun test(settings: CloudSettings, probe: suspend (CloudSettings) -> CloudProbe): Result<CloudProbe> =
        runCatching { probe(settings) }.onFailure { if (it is CancellationException) throw it }

    fun configure(settings: CloudSettings) {
        val clean = settings.copy(baseUrl = settings.baseUrl.trim().trimEnd('/'), storeNo = settings.storeNo.trim(), token = settings.token.trim(), buyerNo = settings.buyerNo.trim())
        settingsStore.save(clean)
        // 新设置从现在起算离线时长（不把设置前的时间算进去）
        val at = clock().also(settingsStore::saveLastOnlineAt)
        s.update { it.copy(settings = clean, health = CloudHealth(), issues = issuesOf(clean), priceDiffs = diffsOf(clean), closeReviews = reviewsOf(clean), lastOnlineAt = at).withBadge() }
        wake.trySend(Unit)
    }

    /** 需处理 · 重试：人工确认原因已排除（如后台已空出桌台），换新的尝试键重传；该聚合解除阻塞 */
    fun retry(eventId: String) {
        val r = refs() ?: return
        val n = (r.get("attempt", eventId)?.remoteNo?.toIntOrNull() ?: 0) + 1
        r.put(CloudRef("attempt", eventId, n.toString())); r.resolve(eventId)
        s.update { it.copy(issues = issuesOf(it.settings)).withBadge() }
        wake.trySend(Unit)
    }

    /** 需处理 · 跳过：人工已在后台处理，这条不再上传（本机标记为已处理）；该聚合后续事件继续 */
    suspend fun skip(eventId: String) {
        val id = storeId() ?: return
        events.ack(id, eventId); refs()?.resolve(eventId)
        s.update { it.copy(issues = issuesOf(it.settings)).withBadge() }
        runCatching { afterRound() }
        wake.trySend(Unit)
    }

    fun nudge() { wake.trySend(Unit) }

    private fun scopeKey(st: CloudSettings) = storeId()?.let { "$it@${st.storeNo}" }
    private fun refs() = scopeKey(s.value.settings)?.let(refsFor)
    private fun issuesOf(st: CloudSettings) = scopeKey(st)?.let { refsFor(it).issues() }.orEmpty()
    private fun reviewsOf(st: CloudSettings) = scopeKey(st)?.let { refsFor(it).all("dayclose") }.orEmpty().map { CloudCloseReview(it.localId, it.remoteNo.substringBefore('|')) }
    private fun diffsOf(st: CloudSettings) = scopeKey(st)?.let { refsFor(it).all("pricediff") }.orEmpty().mapNotNull { r ->
        r.remoteNo.split('/').takeIf { it.size == 2 }?.let { (l, rm) -> CloudPriceDiff(r.localId, l.toLongOrNull() ?: return@let null, rm.toLongOrNull() ?: return@let null) }
    }

    /** 记录连上云端的时间；1 分钟内不重复写盘 */
    private fun markOnline(prev: Long): Long {
        val now = clock()
        if (now - prev >= 60_000) settingsStore.saveLastOnlineAt(now)
        return if (now - prev >= 60_000) now else prev
    }

    /** 当前门店的价差、需处理（进店 / 换店后刷新） */
    fun refreshLocal() { s.update { it.copy(issues = issuesOf(it.settings), priceDiffs = diffsOf(it.settings), closeReviews = reviewsOf(it.settings)).withBadge() } }

    private suspend fun runRound(): SyncRound = roundLock.withLock {
        val st = s.value.settings
        val id = storeId() ?: throw IllegalStateException("未进入门店")
        val refs = refsFor("$id@${st.storeNo}")
        val r = CloudSync(api, refs, events, clock, inventory = retailStore(), buyerNo = s.value.settings.buyerNo).run(id, st.storeNo) { done, total ->
            if (total > 0) s.update { it.copy(badge = CloudBadge.Syncing(done, total)) }
        }
        s.update { cur ->
            val h = when (r.stoppedBy?.kind) {
                CloudErrorKind.Network -> cur.health.failure(r.stoppedBy.message.orEmpty())
                CloudErrorKind.Unauthorized -> cur.health.forceOffline(r.stoppedBy.message.orEmpty())
                else -> cur.health
            }
            val reached = r.stoppedBy?.kind != CloudErrorKind.Network
            cur.copy(health = h, lastRound = r, lastSyncAt = clock(), issues = refs.issues(), priceDiffs = diffsOf(cur.settings), closeReviews = reviewsOf(cur.settings),
                backendWait = if (r.stoppedBy == null) r.backendWait else cur.backendWait,
                lastOnlineAt = if (reached) markOnline(cur.lastOnlineAt) else cur.lastOnlineAt).withBadge()
        }
        if (r.uploaded + r.local > 0) runCatching { afterRound() }
        r
    }

    private fun CloudState.withBadge(): CloudState {
        val lim = CloudLimits.evaluate(settings.configured && uploader, health.online, uploadable, lastOnlineAt, clock())
        return copy(limit = lim, badge = when {
            !uploader -> CloudBadge.ViaHost
            !settings.configured -> CloudBadge.NotConfigured
            // 离线时限优先于需处理显示：它会挡日结
            lim is CloudLimit.Block -> CloudBadge.Offline(pending, health.lastError, lim)
            issues.isNotEmpty() -> CloudBadge.NeedsAction(issues.size)
            health.online -> CloudBadge.Online(uploadable, backendWait)
            else -> CloudBadge.Offline(uploadable, health.lastError, lim)
        })
    }

    /** 日结前检查：超过 72 小时连不上云端时返回原因 */
    fun dayCloseBlock(): String? = (s.value.limit as? CloudLimit.Block)?.message
}
