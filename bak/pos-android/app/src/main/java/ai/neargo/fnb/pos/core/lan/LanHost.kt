package ai.neargo.fnb.pos.core.lan

import ai.neargo.fnb.pos.core.data.LanRole
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.PairedDevice
import ai.neargo.fnb.pos.core.data.VaultCodec
import ai.neargo.fnb.pos.core.identity.AuthState
import ai.neargo.fnb.pos.core.identity.DeviceKeyStore
import ai.neargo.fnb.pos.core.model.StoreData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

@Serializable data class LanRequest(val id: Long, val kind: String, val storeId: String = "", val commandId: String = "", val actorId: String = "", val payload: String = "", val token: String = "")
@Serializable data class LanResponse(val id: Long, val ok: Boolean, val message: String = "", val result: IntentResult? = null, val token: String = "", val store: String? = null)

internal val lanJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** 配对码：8 位数字，5 分钟有效，最多 3 次尝试（每次配对握手计一次）；配对成功即作废。 */
class PairingCode(val code: String, val expiresAt: Long) {
    val attempts = java.util.concurrent.atomic.AtomicInteger(0)
}

data class HostStatus(val running: Boolean = false, val port: Int = 0, val connected: Set<String> = emptySet(), val error: String = "", val code: PairingCode? = null)

/**
 * 主 POS 局域网服务（N5）：唯一协调方。每个连接独立线程，业务意图经 HostExecutor 串行执行。
 * 会话期限不超过主机自身离线授权；被撤销的副机立即断开且无法再握手。
 */
class LanHost(
    private val ws: LocalWorkspace, private val executor: HostExecutor, private val keys: DeviceKeyStore,
    private val scope: CoroutineScope, private val clock: () -> Long = System::currentTimeMillis,
    private val hostName: String = "主 POS",
) {
    private val s = MutableStateFlow(HostStatus())
    val status: StateFlow<HostStatus> = s
    private var server: ServerSocket? = null
    private var job: Job? = null
    private val sockets = java.util.concurrent.ConcurrentHashMap<String, Socket>()

    fun newPairingCode(): PairingCode {
        val code = "%08d".format(SecureRandom().nextInt(100_000_000))
        return PairingCode(code, clock() + 5 * 60_000L).also { c -> s.update { it.copy(code = c) } }
    }

    private fun currentCode(): String? = s.value.code?.takeIf { it.expiresAt > clock() && it.attempts.get() <= 3 }?.code

    fun start(port: Int = ws.lanState.value.port, bind: String = "0.0.0.0") {
        if (s.value.running) return
        val ss = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bind, port)) }
        server = ss
        s.update { it.copy(running = true, port = ss.localPort, error = "") }
        job = scope.launch(Dispatchers.IO) {
            while (isActive && !ss.isClosed) {
                val sock = runCatching { ss.accept() }.getOrNull() ?: break
                launch(Dispatchers.IO) { serve(sock) }
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }; job?.cancel()
        sockets.values.forEach { runCatching { it.close() } }; sockets.clear()
        s.update { it.copy(running = false, connected = emptySet()) }
    }

    /** 撤销副机：立即断开其连接。 */
    fun disconnect(deviceId: String) { sockets.remove(deviceId)?.let { runCatching { it.close() } }; s.update { it.copy(connected = it.connected - deviceId) } }

    private fun authorize(hello: ClientHello, pairing: Boolean): Welcome = runBlocking {
        val v = ws.lanSnapshot()
        if (v.lan.role != LanRole.Host) return@runBlocking Welcome(false, "对方不是主 POS")
        val storeId = v.storeId ?: return@runBlocking Welcome(false, "主机未选择门店")
        val auth = ws.auth.value
        val grantExp = auth.grant?.takeIf { auth.state == AuthState.Active }?.exp
        val expires = minOf(clock() + 8 * 3_600_000L, grantExp ?: Long.MAX_VALUE)
        val tp = ai.neargo.fnb.pos.core.identity.thumbprint(LanCrypto.dec(hello.devicePub))
        if (pairing) {
            if (v.lan.paired.any { it.deviceId == hello.deviceId && it.revokedAt == 0L && it.thumbprint != tp }) return@runBlocking Welcome(false, "设备ID已被另一把密钥登记")
            ws.updateLan("配对副机 ${hello.deviceName}（${hello.deviceId}）") { l ->
                l.copy(paired = l.paired.filterNot { it.deviceId == hello.deviceId } + PairedDevice(hello.deviceId, hello.deviceName, hello.devicePub, tp, clock(), lastSeen = clock()))
            }
            s.update { it.copy(code = null) } // 一次性
        } else {
            val d = v.lan.paired.firstOrNull { it.deviceId == hello.deviceId } ?: return@runBlocking Welcome(false, "设备未与本主机配对")
            if (d.revokedAt > 0) return@runBlocking Welcome(false, "设备已被主机撤销")
            if (d.thumbprint != tp) return@runBlocking Welcome(false, "设备密钥与配对记录不符")
        }
        Welcome(true, sessionExpiresAt = expires, storeId = storeId, tenantId = ws.tenantId())
    }

    private suspend fun serve(sock: Socket) {
        var deviceId = ""
        try {
            var mode = ""
            val res = try {
                // 每次配对握手在使用配对码之前计一次尝试（原子计数，避免并发/竞态绕过上限）
                LanHandshake.server(sock, ws.deviceId(), hostName, keys, ::currentCode, onHello = { h -> mode = h.mode; if (h.mode == "pair") s.value.code?.attempts?.incrementAndGet() }) { hello, pairing -> authorize(hello, pairing) }
            } catch (e: Exception) { null }
            if (res == null) { runCatching { sock.close() }; return }
            val (ch, hello) = res
            deviceId = hello.deviceId
            sock.soTimeout = 0
            sockets.put(deviceId, sock)?.let { runCatching { it.close() } }
            s.update { it.copy(connected = it.connected + deviceId) }
            while (true) {
                val req = lanJson.decodeFromString(LanRequest.serializer(), String(ch.receive()))
                val dev = ws.lanSnapshot().lan.paired.firstOrNull { it.deviceId == deviceId }
                if (dev == null || dev.revokedAt > 0) { ch.send(lanJson.encodeToString(LanResponse.serializer(), LanResponse(req.id, false, "设备已被主机撤销")).toByteArray()); break }
                val resp = handle(req, deviceId)
                ch.send(lanJson.encodeToString(LanResponse.serializer(), resp).toByteArray())
                if (clock() - dev.lastSeen > 60_000) ws.updateLan { l -> l.copy(paired = l.paired.map { if (it.deviceId == deviceId) it.copy(lastSeen = clock()) else it }) }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { sock.close() }
            if (deviceId.isNotEmpty() && sockets[deviceId] === sock) { sockets.remove(deviceId); s.update { it.copy(connected = it.connected - deviceId) } }
        }
    }

    private suspend fun handle(req: LanRequest, deviceId: String): LanResponse = when (req.kind) {
        "ping" -> LanResponse(req.id, true)
        "command" -> {
            val intent = runCatching { Intents.decode(req.payload) }.getOrNull()
            if (intent == null || req.commandId.isBlank()) LanResponse(req.id, false, "无法识别的请求")
            else LanResponse(req.id, true, result = executor.execute(req.storeId, deviceId, req.commandId, req.actorId, intent))
        }
        "snapshot" -> {
            val v = ws.lanSnapshot()
            val st = v.stores[req.storeId]
            if (st == null) LanResponse(req.id, false, "门店不存在")
            else {
                val body = VaultCodec.json.encodeToString(StoreData.serializer(), st)
                val token = LanCrypto.enc(LanCrypto.sha256(body.toByteArray()))
                if (token == req.token) LanResponse(req.id, true, token = token) else LanResponse(req.id, true, token = token, store = body)
            }
        }
        else -> LanResponse(req.id, false, "未知请求")
    }
}
