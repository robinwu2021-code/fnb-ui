package ai.neargo.fnb.pos.core.model

/**
 * 扫码枪（HID 键盘）输入识别：扫码枪把条码当键盘一口气打出来，再补一个回车。
 * 判断依据是按键间隔：人手打字一般 > 80ms，扫码枪 < 30ms。阈值取 50ms，至少 4 个字符才算一个码。
 * 纯逻辑，界面把按键喂进来；enter 返回非空就是扫到的码。
 */
class ScanBuffer(private val maxGapMs: Long = 50, private val minLength: Int = 4) {
    private val buf = StringBuilder()
    private var lastAt = 0L

    /** 一个可见字符；和上一个隔得太久就从头开始（人手敲的字不算） */
    fun char(c: Char, at: Long) {
        if (buf.isNotEmpty() && at - lastAt > maxGapMs) buf.clear()
        buf.append(c); lastAt = at
    }

    /** 回车：够长且紧跟着最后一个字符 → 返回码；否则丢弃 */
    fun enter(at: Long): String? {
        val code = buf.toString().takeIf { it.length >= minLength && at - lastAt <= maxGapMs * 4 }
        buf.clear()
        return code
    }

    fun clear() = buf.clear()
}
