package ai.neargo.fnb.pos.core.hardware

import ai.neargo.fnb.pos.core.model.ScaleRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

/** 秤的状态：没配置 / 连接中 / 已连接（带最近读数）/ 连不上 */
sealed interface ScaleState {
    data object Off : ScaleState
    data object Connecting : ScaleState
    data class Live(val reading: ScaleRules.Reading?, val at: Long) : ScaleState
    data class Error(val message: String) : ScaleState
}

/**
 * 网口电子秤（或串口服务器把秤的串口转成 TCP）：连上后持续读秤的连续输出，逐行解析成读数。
 * poll 非空时每 300ms 发一次（问答式的秤，例如发 "W\r" 才回重量）。断线 3 秒后自动重连。
 * 蓝牙 / USB 串口秤：接秤型号确定后按同样的 ScaleRules 接，只换传输层。
 */
class NetworkScale(private val scope: CoroutineScope) {
    private val s = MutableStateFlow<ScaleState>(ScaleState.Off)
    val state: StateFlow<ScaleState> = s.asStateFlow()
    private var job: Job? = null
    private var config: Triple<String, Int, String>? = null

    /** 换配置时重连；host 为空就关掉 */
    fun configure(host: String, port: Int, poll: String, unit: ScaleRules.Unit = ScaleRules.Unit.Kg) {
        val c = Triple(host.trim(), port, poll)
        if (c == config) return
        config = c; job?.cancel()
        if (c.first.isEmpty()) { s.value = ScaleState.Off; return }
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                s.value = ScaleState.Connecting
                try {
                    Socket().use { sock ->
                        sock.connect(InetSocketAddress(c.first, c.second), 3_000)
                        sock.soTimeout = 2_000
                        s.value = ScaleState.Live(null, System.currentTimeMillis())
                        val inp = sock.getInputStream(); val out = sock.getOutputStream()
                        val buf = StringBuilder(); val bytes = ByteArray(256)
                        var lastPoll = 0L
                        while (isActive) {
                            if (c.third.isNotEmpty() && System.currentTimeMillis() - lastPoll > 300) { out.write(c.third.replace("\\r", "\r").replace("\\n", "\n").toByteArray()); out.flush(); lastPoll = System.currentTimeMillis() }
                            val n = try { inp.read(bytes) } catch (_: java.net.SocketTimeoutException) { 0 }
                            if (n < 0) break
                            if (n > 0) ScaleRules.lines(buf, String(bytes, 0, n, Charsets.US_ASCII)).forEach { line ->
                                ScaleRules.parse(line, unit)?.let { r -> s.value = ScaleState.Live(r, System.currentTimeMillis()) }
                            }
                        }
                    }
                    s.value = ScaleState.Error("秤断开了，正在重连")
                } catch (e: Exception) {
                    if (!isActive) break
                    s.value = ScaleState.Error("连不上秤（${c.first}:${c.second}）：${e.message ?: "超时"}")
                }
                delay(3_000)
            }
        }
    }
}
