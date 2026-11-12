package ai.neargo.fnb.pos.core.print

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.Charset

/**
 * 通用 ESC/POS 编码（N6）：初始化、标题加粗倍高、GB18030 中文、走纸切纸。
 * 不依赖厂商 SDK；不同型号对中文模式与切刀指令的支持需按实机核对（见实施记录）。
 */
object EscPos {
    val CHARSET: Charset = Charset.forName("GB18030")
    private val INIT = byteArrayOf(0x1B, 0x40)                 // ESC @
    private val CN_ON = byteArrayOf(0x1C, 0x26)                // FS & 汉字模式
    private val BOLD_BIG = byteArrayOf(0x1D, 0x21, 0x11)       // GS ! 倍宽倍高
    private val NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    private val CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val FEED_CUT = byteArrayOf(0x1B, 0x64, 0x04, 0x1D, 0x56, 0x42, 0x00) // ESC d 4 + GS V B 0
    /** 实时状态查询 DLE EOT n：1 打印机、2 脱机原因、4 纸张传感器。 */
    fun status(n: Int) = byteArrayOf(0x10, 0x04, n.toByte())

    fun encode(lines: List<String>, cut: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(INIT); out.write(CN_ON)
        lines.forEachIndexed { i, l ->
            if (i == 0) { out.write(CENTER); out.write(BOLD_BIG); out.write(l.toByteArray(CHARSET)); out.write('\n'.code); out.write(NORMAL); out.write(LEFT) }
            else { out.write(l.toByteArray(CHARSET)); out.write('\n'.code) }
        }
        if (cut) out.write(FEED_CUT) else out.write(byteArrayOf(0x1B, 0x64, 0x04))
        return out.toByteArray()
    }
}

/**
 * 标签机指令（TSPL，203 dpi）：价签任务的正文就是 TSPL 指令行，按行加 CRLF，中文用 GB18030（TSS24.BF2 字体要求）。
 * 标签机一般不回 ESC/POS 状态，发送时不查状态。
 */
object Tspl {
    fun encode(lines: List<String>): ByteArray = lines.joinToString("\r\n", postfix = "\r\n").toByteArray(EscPos.CHARSET)
}

/** 一次发送尝试的结果（分级）：没发出 / 已发出且状态正常 / 结果未知。 */
sealed interface SendOutcome {
    /** 确定未发出（连接失败、发送前缺纸或脱机）：可安全自动重试。 */
    data class NotSent(val reason: String) : SendOutcome
    /** 已写入打印机且随后状态正常（statusChecked=false 表示该型号不回状态）。仍不等于已确认出纸。 */
    data class Sent(val statusChecked: Boolean) : SendOutcome
    /** 写入中断开、或发送后打印机报错：可能部分或全部已打印，须人工核对，不得自动重打。 */
    data class Unknown(val reason: String) : SendOutcome
}

/** 打印机状态字节解析（ESC/POS 标准位）。 */
object EscPosStatus {
    /** DLE EOT 1：bit3=脱机。 */
    fun offline(b: Int) = b and 0x08 != 0
    /** DLE EOT 4：bit5/6=纸尽。 */
    fun paperEnd(b: Int) = b and 0x60 != 0
    /** DLE EOT 2：bit2=开盖、bit5=缺纸停止、bit6=错误。 */
    fun error(b: Int) = b and 0x64 != 0
}

/** 网口直连（RAW 9100）。每个任务一个连接，调用方保证同一打印机串行。 */
class EscPosClient(private val connectTimeoutMs: Int = 3_000, private val statusTimeoutMs: Int = 1_500) {
    /** 状态查询结果：状态字节；NO_REPLY=型号不回状态（超时）；连接关闭抛 IOException。 */
    private fun query(sock: Socket, n: Int): Int? = try {
        sock.getOutputStream().apply { write(EscPos.status(n)); flush() }
        sock.soTimeout = statusTimeoutMs
        val b = sock.getInputStream().read()
        if (b < 0) throw IOException("打印机已关闭连接")
        b
    } catch (_: SocketTimeoutException) { null }

    fun send(host: String, port: Int, data: ByteArray, checkStatus: Boolean = true): SendOutcome {
        val sock = Socket()
        try {
            try { sock.connect(InetSocketAddress(host, port), connectTimeoutMs) } catch (e: IOException) { return SendOutcome.NotSent("无法连接打印机：${e.message ?: "超时"}") }
            if (checkStatus) {
                val paper = try { query(sock, 4) } catch (e: IOException) { return SendOutcome.NotSent("打印机无响应：${e.message}") }
                if (paper != null && EscPosStatus.paperEnd(paper)) return SendOutcome.NotSent("打印机缺纸")
                val st = try { query(sock, 1) } catch (e: IOException) { return SendOutcome.NotSent("打印机无响应：${e.message}") }
                if (st != null && EscPosStatus.offline(st)) return SendOutcome.NotSent("打印机脱机（可能开盖或缺纸）")
            }
            try {
                sock.getOutputStream().apply { write(data); flush() }
            } catch (e: IOException) { return SendOutcome.Unknown("发送中断：${e.message ?: "连接断开"}") }
            if (!checkStatus) return SendOutcome.Sent(false)
            val after = try { query(sock, 2) } catch (e: IOException) { return SendOutcome.Unknown("发送后连接断开，无法确认") }
            return when {
                after == null -> SendOutcome.Sent(false)
                EscPosStatus.error(after) -> SendOutcome.Unknown("发送后打印机报错（开盖/缺纸/故障），可能未完整出纸")
                else -> SendOutcome.Sent(true)
            }
        } finally { runCatching { sock.close() } }
    }
}
