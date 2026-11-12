package ai.neargo.fnb.pos.core.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException

/** 推送帧（event=push）：只有对象号与版本，内容按现有接口重读 */
@Serializable data class PushFrame(val seq: Long = 0, val topic: String = "", val refType: String = "", val refNo: String = "",
    val refVersion: Long = 0, val targetDevice: String? = null, val occurredAt: String = "")
/** 定向消息（event=message，无 id）：按 messageNo 去重，收到回 RECEIVED，做完回 DONE / FAILED */
@Serializable data class DirectedFrame(val messageNo: String, val kind: String = "", val payload: String? = null, val expireAt: String = "")
@Serializable data class PushAck(val messageNo: String, val status: String, val resultCode: String? = null)

/** SSE 一帧 */
data class SseEvent(val id: String?, val event: String, val data: String)

/** SSE 行解析（`id:` `event:` `data:`，空行结束一帧，`:` 开头为心跳注释） */
class SseParser {
    private var id: String? = null; private var event = "message"; private val data = StringBuilder()
    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) {
            if (data.isEmpty()) { id = null; event = "message"; return null }
            val e = SseEvent(id, event, data.toString().removeSuffix("\n"))
            id = null; event = "message"; data.clear(); return e
        }
        if (line.startsWith(":")) return null
        val (field, value) = line.indexOf(':').let { if (it < 0) line to "" else line.substring(0, it) to line.substring(it + 1).removePrefix(" ") }
        when (field) { "id" -> id = value; "event" -> event = value; "data" -> data.append(value).append('\n') }
        return null
    }
}

/** 推送连接本机持久数据：安装号、每个门店的端号与最后事件序号 */
interface PushStore {
    fun installId(): String
    fun terminalNo(storeNo: String): String?
    fun saveTerminalNo(storeNo: String, terminalNo: String?)
    fun lastEventId(storeNo: String): String?
    fun saveLastEventId(storeNo: String, id: String)
}

data class PushState(
    val connected: Boolean = false, val terminalNo: String = "", val lastSeq: String = "", val received: Int = 0,
    val lastTopic: String = "", val lastAt: Long = 0, val lastError: String = "",
)

/**
 * 门店实时推送（`feat/store-push-sse` 契约，下一批方案 §5）：
 * - 只有主 POS / 单机连（与上传同一规则）；选定门店后登记终端拿端号，换门店重新登记。
 * - 订阅 `/biz/stores/{storeNo}/push/stream?terminalNo=`，带 `Last-Event-ID` 续传；服务端 30 分钟断开、网络中断都自动重连。
 * - event=reset：整页重读后从 head 继续；推送只当「该去拉了」的信号，丢一条不影响正确性。
 * - 定向消息：去重后先回 RECEIVED；本期没有要执行的消息种类，统一回 FAILED / UNSUPPORTED_KIND。
 */
class CloudPush(
    private val http: CloudHttp,
    private val store: PushStore,
    private val appVersion: String,
    private val onFrame: suspend (PushFrame) -> Unit = {},
    private val onReset: suspend () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryMs: (attempt: Int) -> Long = { a -> listOf(1_000L, 5_000L, 15_000L, 30_000L, 60_000L)[minOf(a, 4)] },
) {
    private val s = MutableStateFlow(PushState())
    val state: StateFlow<PushState> = s.asStateFlow()
    private val seen = LinkedHashSet<String>()

    /** target = 连接目标（地址@门店）；null 表示不连（未设置云端、副 POS、离线）。目标变化（换门店、改地址）即重连 */
    fun start(scope: CoroutineScope, target: Flow<String?>) {
        scope.launch {
            target.distinctUntilChanged().collectLatest { on ->
                if (on == null) { s.update { it.copy(connected = false) }; return@collectLatest }
                var attempt = 0
                while (true) {
                    val ok = runCatching { connectOnce() }.onFailure { if (it is CancellationException) throw it }
                    val err = ok.exceptionOrNull()
                    s.update { it.copy(connected = false, lastError = err?.message ?: it.lastError) }
                    // 正常结束（服务端 30 分钟断开）马上重连；出错按退避
                    if (err == null) attempt = 0 else delay(retryMs(attempt++))
                }
            }
        }
    }

    /** 登记（有缓存端号则复用）→ 订阅一次，直到连接结束 */
    suspend fun connectOnce() = withContext(Dispatchers.IO) {
        val storeNo = http.config.storeNo
        val terminal = store.terminalNo(storeNo) ?: register(storeNo)
        try { stream(storeNo, terminal) } catch (x: CloudException) {
            // 端号登记的门店与订阅门店不一致（切店）或端号失效：重新登记后再订阅
            if (x.code == 400 || x.kind == CloudErrorKind.NotFound) { store.saveTerminalNo(storeNo, null); stream(storeNo, register(storeNo)) } else throw x
        }
    }

    suspend fun register(storeNo: String): String {
        val r = http.send("POST", "/biz/push/terminals", null, RegisterBody(store.installId(), "POS", "android", appVersion), RegisterBody.serializer(), TerminalVO.serializer())
        store.saveTerminalNo(storeNo, r.terminalNo); s.update { it.copy(terminalNo = r.terminalNo) }
        return r.terminalNo
    }

    private suspend fun stream(storeNo: String, terminal: String) {
        val url = http.url("/biz/stores/${HttpCloudApi.enc(storeNo)}/push/stream", mapOf("terminalNo" to terminal))
        val conn = http.open(url, "GET", readTimeoutMs = 90_000)   // 心跳为注释行；90 秒没有任何数据视为断线
        conn.setRequestProperty("Accept", "text/event-stream")
        store.lastEventId(storeNo)?.let { conn.setRequestProperty("Last-Event-ID", it) }
        try {
            val status = conn.responseCode
            if (status == 401 || status == 403) throw CloudException(CloudErrorKind.Unauthorized, status, "推送订阅被拒（HTTP $status）")
            if (status == 400) throw CloudException(CloudErrorKind.Rejected, 400, "推送订阅参数不合法或端号门店不一致")
            if (status != 200) throw CloudException(CloudErrorKind.Network, status, "推送服务不可用（HTTP $status）")
            s.update { it.copy(connected = true, terminalNo = terminal, lastError = "") }
            val parser = SseParser()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    parser.feed(line)?.let { handle(storeNo, terminal, it) }
                }
            }
        } catch (e: IOException) {
            throw CloudException(CloudErrorKind.Network, 0, "推送连接中断：${e.message ?: e.javaClass.simpleName}")
        } finally { conn.disconnect() }
    }

    private suspend fun handle(storeNo: String, terminal: String, e: SseEvent) {
        when (e.event) {
            "push" -> {
                val f = runCatching { http.json.decodeFromString(PushFrame.serializer(), e.data) }.getOrNull() ?: return
                e.id?.let { store.saveLastEventId(storeNo, it) }
                s.update { it.copy(lastSeq = e.id ?: it.lastSeq, received = it.received + 1, lastTopic = f.topic, lastAt = clock()) }
                runCatching { onFrame(f) }
            }
            "reset" -> {
                // data = {head}：整页重读，然后从 head 继续
                val head = runCatching { http.json.parseToJsonElement(e.data).let { (it as kotlinx.serialization.json.JsonObject)["head"]?.toString()?.trim('"') } }.getOrNull()
                head?.let { store.saveLastEventId(storeNo, it) }
                runCatching { onReset() }
            }
            "message" -> {
                val m = runCatching { http.json.decodeFromString(DirectedFrame.serializer(), e.data) }.getOrNull() ?: return
                if (!seen.add(m.messageNo)) return
                if (seen.size > 500) seen.remove(seen.first())
                ack(terminal, listOf(PushAck(m.messageNo, "RECEIVED"), PushAck(m.messageNo, "FAILED", "UNSUPPORTED_KIND")))
            }
        }
    }

    suspend fun ack(terminal: String, acks: List<PushAck>) {
        runCatching { http.send("POST", "/biz/push/acks", null, AckBody(terminal, acks), AckBody.serializer(), AckResult.serializer()) }
            .onFailure { if (it is CancellationException) throw it }   // 回执失败不影响连接；未确认的消息重连时服务端会重发
    }
}

@Serializable internal data class RegisterBody(val installId: String, val clientType: String, val platform: String, val appVersion: String)
@Serializable internal data class TerminalVO(val terminalNo: String)
@Serializable internal data class AckBody(val terminalNo: String, val acks: List<PushAck>)
@Serializable internal data class AckResult(val applied: Int = 0)
