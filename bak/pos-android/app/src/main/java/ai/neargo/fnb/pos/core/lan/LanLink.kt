package ai.neargo.fnb.pos.core.lan

import ai.neargo.fnb.pos.core.data.LanRole
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.PendingIntent
import ai.neargo.fnb.pos.core.data.VaultCodec
import ai.neargo.fnb.pos.core.identity.DeviceKeyStore
import ai.neargo.fnb.pos.core.model.StoreData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

data class LinkStatus(val connected: Boolean = false, val error: String = "", val lastSyncAt: Long = 0, val sessionExpiresAt: Long = 0)

/**
 * 副 POS 与主机的连接（N5）：
 * - 业务意图先保存为待确认（PendingIntent），再发送；网络失败保留并以同一命令ID重发，主机按回执去重。
 * - 失联时副机只读：不排队新的业务写入，也不绕过主机直接写本地或云端。
 * - 定时拉取主机门店快照（令牌未变则不传正文）作为本机只读副本。
 */
class LanLink(
    private val ws: LocalWorkspace, private val keys: DeviceKeyStore, private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis, private val deviceName: String = "副 POS",
    private val requestTimeoutMs: Int = 8_000,
) {
    private val s = MutableStateFlow(LinkStatus())
    val status: StateFlow<LinkStatus> = s
    private val io = Mutex()
    private var channel: SecureChannel? = null
    private var reqId = 0L

    val isSecondary get() = ws.lanSnapshot().lan.role == LanRole.Secondary

    private fun parse(address: String): Pair<String, Int> {
        val a = address.trim()
        val host = a.substringBeforeLast(':', a); val port = a.substringAfterLast(':', "").toIntOrNull() ?: ws.lanState.value.port
        return host to port
    }

    /** 首次配对：验证配对码与主机签名，保存主机公钥（之后只认这把公钥）。 */
    suspend fun pair(address: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(Regex("^\\d{8}$").matches(code.trim())) { "配对码为 8 位数字" }
            val (h, p) = parse(address)
            val sock = Socket().apply { connect(InetSocketAddress(h, p), 5_000) }
            val session = LanHandshake.client(sock, ws.deviceId(), deviceName, keys, null, code.trim())
            ws.updateLan("已与主机 ${session.server.hostName}（${session.server.hostId}）配对") { l ->
                l.copy(role = LanRole.Secondary, hostAddress = "$h:$p", hostId = session.server.hostId, hostName = session.server.hostName,
                    hostPublicKey = session.server.hostPub, sessionExpiresAt = session.welcome.sessionExpiresAt, replicaToken = "")
            }
            io.withLock { channel?.close(); channel = session.channel; sock.soTimeout = requestTimeoutMs }
            s.update { it.copy(connected = true, error = "", sessionExpiresAt = session.welcome.sessionExpiresAt) }
            pull()
        }
    }

    /** 解除配对：回到单机；待确认意图保留在记录中供核对，不再自动发送。 */
    suspend fun unpair() {
        io.withLock { channel?.close(); channel = null }
        ws.updateLan("解除与主机的配对") { it.copy(role = LanRole.Standalone, hostAddress = "", hostId = "", hostPublicKey = "", sessionExpiresAt = 0, replicaToken = "") }
        s.value = LinkStatus()
    }

    private fun connectLocked(): SecureChannel {
        channel?.let { return it }
        val l = ws.lanSnapshot().lan
        require(l.role == LanRole.Secondary && l.hostPublicKey.isNotBlank()) { "未与主机配对" }
        val (h, p) = parse(l.hostAddress)
        val sock = Socket().apply { connect(InetSocketAddress(h, p), 3_000) }
        val session = LanHandshake.client(sock, ws.deviceId(), deviceName, keys, LanCrypto.dec(l.hostPublicKey), null)
        sock.soTimeout = requestTimeoutMs
        channel = session.channel
        s.update { it.copy(connected = true, error = "", sessionExpiresAt = session.welcome.sessionExpiresAt) }
        return session.channel
    }

    /** 连接或握手阶段失败：请求肯定没有发出。 */
    class NotDelivered(cause: Exception) : Exception(cause.message, cause)

    private suspend fun request(req: (Long) -> LanRequest): LanResponse = withContext(Dispatchers.IO) {
        io.withLock {
            val ch = try { connectLocked() } catch (e: Exception) {
                channel?.close(); channel = null
                s.update { it.copy(connected = false, error = e.message ?: e.javaClass.simpleName) }
                throw NotDelivered(e)
            }
            try {
                val r = req(++reqId)
                ch.send(lanJson.encodeToString(LanRequest.serializer(), r).toByteArray())
                val resp = lanJson.decodeFromString(LanResponse.serializer(), String(ch.receive()))
                require(resp.id == r.id) { "响应与请求不对应" }
                resp
            } catch (e: Exception) {
                channel?.close(); channel = null
                s.update { it.copy(connected = false, error = e.message ?: e.javaClass.simpleName) }
                throw e
            }
        }
    }

    /**
     * 提交业务意图。未连接主机时直接拒绝（副机只读）；已发送但未收到回执时保留，后台以同一ID重发。
     */
    suspend fun submit(intent: RemoteIntent, summary: String): IntentResult {
        val v = ws.lanSnapshot()
        val storeId = v.storeId ?: return IntentResult(false, "请先选择门店")
        if (!s.value.connected) runCatching { request { LanRequest(it, "ping") } }
        if (!s.value.connected) return IntentResult(false, "主机未连接：副 POS 只读，恢复连接后再操作")
        val p = PendingIntent(UUID.randomUUID().toString(), storeId, Intents.encode(intent), summary, clock())
        ws.updateLan { l -> l.copy(pending = l.pending + p) } // 发送前持久保存
        return send(p)
    }

    private suspend fun send(p: PendingIntent): IntentResult {
        val actor = ws.actorId()
        val resp = try {
            request { LanRequest(it, "command", p.storeId, p.commandId, actor, p.payload) }
        } catch (e: NotDelivered) {
            // 未发出：首次提交则撤回待确认记录并按只读处理；重发中的保留等待下次
            if (p.attempts == 0) {
                ws.updateLan { l -> l.copy(pending = l.pending.filterNot { it.commandId == p.commandId }) }
                return IntentResult(false, "主机未连接：副 POS 只读，恢复连接后再操作")
            }
            ws.updateLan { l -> l.copy(pending = l.pending.map { if (it.commandId == p.commandId) it.copy(lastError = e.message ?: "主机未连接", nextAt = clock() + 2_000) else it }) }
            return IntentResult(false, "主机未连接，待确认的操作将在恢复后以同一编号重发")
        } catch (e: Exception) {
            ws.updateLan { l -> l.copy(pending = l.pending.map { if (it.commandId == p.commandId) it.copy(attempts = it.attempts + 1, lastError = e.message ?: "网络中断", nextAt = clock() + 2_000) else it }) }
            return IntentResult(false, "已发送但未收到主机确认，将以同一编号自动重试（不会重复执行）")
        }
        ws.updateLan { l -> l.copy(pending = l.pending.filterNot { it.commandId == p.commandId }) }
        val r = resp.result ?: IntentResult(false, resp.message)
        if (r.ok) runCatching { pull() }
        return r
    }

    /** 重发未确认意图（同一命令ID）。 */
    suspend fun flushPending(): Int {
        var n = 0
        for (p in ws.lanSnapshot().lan.pending.sortedBy { it.createdAt }) {
            if (p.nextAt > clock()) continue
            val r = send(p)
            if (r.message.startsWith("已发送但未收到") || r.message.startsWith("主机未连接")) break else n++
        }
        return n
    }

    /** 拉取主机快照作为只读副本。 */
    suspend fun pull() {
        val v = ws.lanSnapshot()
        val storeId = v.storeId ?: return
        val resp = request { LanRequest(it, "snapshot", storeId, token = v.lan.replicaToken) }
        if (!resp.ok) { s.update { it.copy(error = resp.message) }; return }
        resp.store?.let { ws.applyReplica(VaultCodec.json.decodeFromString(StoreData.serializer(), it), resp.token) }
        s.update { it.copy(lastSyncAt = clock()) }
    }

    /** 厨房屏：增量拉取任务卡 → 去重落盘 → 回执已持久接收的版本。返回新收到的张数。 */
    suspend fun kdsSync(): Int {
        val v = ws.lanSnapshot()
        val storeId = v.storeId ?: return 0
        if (v.kdsStation.isBlank()) return 0
        var total = 0
        while (true) {
            val cur = ws.lanSnapshot().kdsCursor
            val resp = request { LanRequest(it, "kds-pull", storeId, payload = v.kdsStation, token = cur.toString()) }
            if (!resp.ok) { s.update { it.copy(error = resp.message) }; return total }
            val list = lanJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ai.neargo.fnb.pos.core.model.KdsTicket.serializer()), resp.store ?: "[]")
            val acks = ws.kdsReceive(list, resp.token.toLongOrNull() ?: cur)
            if (acks.isNotEmpty()) request { LanRequest(it, "kds-ack", storeId, payload = acks.joinToString(",") { (id, r) -> "$id:$r" }) }
            total += acks.size
            if (list.size < 200) break
        }
        s.update { it.copy(lastSyncAt = clock()) }
        return total
    }

    /** 厨师操作：经主机仲裁（带所见版本），成功后立即刷新。 */
    suspend fun kdsAdvance(ticketId: String, rev: Int, to: ai.neargo.fnb.pos.core.model.KdsState): IntentResult {
        val r = submit(RemoteIntent.KdsAdvance(ticketId, rev, to), "厨房 ${to.label}")
        runCatching { kdsSync() }
        return r
    }

    /** 后台循环：连接、重发、同步。 */
    fun start(intervalMs: Long = 2_000) = scope.launch(Dispatchers.IO) {
        while (isActive) {
            if (isSecondary) runCatching { flushPending(); if (ws.lanSnapshot().kdsStation.isNotBlank()) kdsSync() else pull() }
            delay(intervalMs)
        }
    }
}
