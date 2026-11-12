package ai.neargo.fnb.pos.core.model

/**
 * 电子秤读数解析：常见计价秤 / 台秤的「连续输出」ASCII 帧，一行一个读数。
 * 支持的写法（大小写、空格、正负号都容忍）：
 *  - 托利多风格：`ST,GS,+  1.234kg`（ST 稳定 / US 不稳定 / OL 超载；GS 毛重 / NT 净重）
 *  - 带单位：`1.234 kg`、`+001234 g`、`1234g`、`0.500KG`
 *  - 只有数字：按秤设置的默认单位（kg 或 g）
 * 不稳定的读数也返回（界面显示「称重中」），只有稳定读数才能加进购物车。
 */
object ScaleRules {
    enum class Unit { Kg, G }
    data class Reading(val grams: Int, val stable: Boolean, val overload: Boolean = false)

    private val num = Regex("([+-]?)\\s*(\\d+(?:\\.\\d+)?)\\s*(kg|g)?", RegexOption.IGNORE_CASE)

    /** 解析一行；看不懂返回 null。负数（去皮后托盘拿走）按 0 克 */
    fun parse(line: String, defaultUnit: Unit = Unit.Kg): Reading? {
        val s = line.trim().trim('\u0002', '\u0003').trim()
        if (s.isEmpty()) return null
        val up = s.uppercase()
        if (up.startsWith("OL") || up.contains("OVER")) return Reading(0, stable = false, overload = true)
        val unstable = up.startsWith("US") || up.contains(",US,") || up.contains("MOTION")
        val m = num.findAll(s).lastOrNull() ?: return null
        val v = m.groupValues[2].toBigDecimalOrNull() ?: return null
        val unit = when (m.groupValues[3].lowercase()) { "kg" -> Unit.Kg; "g" -> Unit.G; else -> defaultUnit }
        val grams = (if (unit == Unit.Kg) v.movePointRight(3) else v).setScale(0, java.math.RoundingMode.HALF_UP).toInt()
        return Reading(if (m.groupValues[1] == "-") 0 else grams.coerceAtLeast(0), stable = !unstable)
    }

    /** 从字节流里切行：CR、LF、STX、ETX 都算行尾 */
    fun lines(buffer: StringBuilder, chunk: String): List<String> {
        buffer.append(chunk)
        val out = mutableListOf<String>()
        var start = 0
        buffer.forEachIndexed { i, ch -> if (ch == '\r' || ch == '\n' || ch == '\u0003') { if (i > start) out += buffer.substring(start, i); start = i + 1 } }
        buffer.delete(0, start)
        if (buffer.length > 256) buffer.clear() // 一直没有行尾：丢掉，免得越攒越大
        return out
    }
}
