package ai.neargo.fnb.pos.core.print

import ai.neargo.fnb.pos.core.data.LocalWorkspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 打印网关（N6）：主 POS/单机唯一拥有打印机。逐张领取→锁外发送→回写结果；
 * 同一时间只发一张，未知结果不自动重打、不改投备机。副 POS 不领取任务。
 */
class PrintGateway(private val ws: LocalWorkspace, private val client: EscPosClient = EscPosClient()) {
    /** 处理当前所有可发任务，返回处理张数。 */
    suspend fun drain(max: Int = 20): Int {
        var n = 0
        while (n < max) {
            val (job, printer) = ws.claimNextPrint() ?: break
            // 价签任务按 TSPL 发，不查 ESC/POS 状态；其余按 ESC/POS
            val label = job.kind == ai.neargo.fnb.pos.core.model.PrintKind.Label
            val data = if (label) Tspl.encode(job.body) else EscPos.encode(job.body)
            val outcome = try { withContext(Dispatchers.IO) { client.send(printer.host, printer.port, data, printer.statusQuery && !label) } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { SendOutcome.Unknown("发送异常：${e.message}") }
            ws.finishPrint(job.id, outcome)
            n++
        }
        return n
    }

    fun start(scope: CoroutineScope, intervalMs: Long = 1_000) = scope.launch {
        while (isActive) { runCatching { drain() }; delay(intervalMs) }
    }
}
