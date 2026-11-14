package ai.neargo.fnb.pos.core.model

/**
 * 零售条码规则（移植自原型 prototypes/pos-react/src/domain/catalog.ts）：
 * 扫到的码是什么 · 18 位称重码解析 · 按码找商品 · EAN-13 校验与条码模块 · 价签单位价。
 * 金额一律整数分；重量用整数克。
 */
object BarcodeRules {
    enum class CodeKind { Weigh, Plu, Instore, Std, Member, Pay, Unknown }

    private val member = Regex("^M\\d+$", RegexOption.IGNORE_CASE)
    private val pay = Regex("^1[0-5]\\d{16}$")
    private val weigh = Regex("^2\\d{17}$")
    private val plu = Regex("^PLU\\d+$", RegexOption.IGNORE_CASE)
    private val instore = Regex("^2\\d{6}$")
    private val std = Regex("^\\d{12,13}$")

    private fun clean(raw: String) = raw.filterNot { it.isWhitespace() }

    /** 码分类：会员码 M 开头；付款码 18 位 10–15 开头；称重码 18 位 2 开头；PLU；店内码 7 位 2 开头；标准码 12–13 位 */
    fun classify(raw: String): CodeKind {
        val c = clean(raw)
        return when {
            member.matches(c) -> CodeKind.Member
            pay.matches(c) -> CodeKind.Pay
            weigh.matches(c) -> CodeKind.Weigh
            plu.matches(c) -> CodeKind.Plu
            instore.matches(c) -> CodeKind.Instore
            std.matches(c) -> CodeKind.Std
            else -> CodeKind.Unknown
        }
    }

    /** 称重码：2 + 商品码 6 + 金额 5（分）+ 重量 5（克）+ 校验 1；格式以门店条码规则为准 */
    data class WeighCode(val plu: String, val amount: Long, val grams: Int)

    fun parseWeigh(raw: String): WeighCode? {
        val c = clean(raw)
        if (classify(c) != CodeKind.Weigh) return null
        return WeighCode(c.substring(1, 7), c.substring(7, 12).toLong(), c.substring(12, 17).toInt())
    }

    /** 按码找商品（忽略空格）；扫到的是 13 位码时，也认价签上印的 EAN-13（店内码、PLU 打价签时转成了 13 位） */
    fun <T> findByCode(items: List<T>, raw: String, codesOf: (T) -> List<String>): T? {
        val c = clean(raw)
        return items.firstOrNull { item -> codesOf(item).any { clean(it) == c } }
            ?: if (c.length == 13 && c.all(Char::isDigit)) items.firstOrNull { item -> codesOf(item).any { it.any(Char::isDigit) && toEan13(it) == c } } else null
    }

    /** 一码多品：同一个码对应的所有商品（按码找时用第一个，收银时多于一个就弹出让收银员选） */
    fun <T> findAllByCode(items: List<T>, raw: String, codesOf: (T) -> List<String>): List<T> {
        val c = clean(raw)
        return items.filter { item -> codesOf(item).any { clean(it) == c } }
    }

    /** EAN-13 校验位（前 12 位） */
    fun ean13Check(d12: String): String {
        val s = d12.mapIndexed { i, ch -> (ch - '0') * (if (i % 2 == 1) 3 else 1) }.sum()
        return ((10 - s % 10) % 10).toString()
    }

    /** 任意商品码转成可打印的 EAN-13：13 位原样；12 位补校验；店内码 / PLU 按「2 + 补零到 11 位」再补校验 */
    fun toEan13(raw: String): String {
        val d = raw.filter { it.isDigit() }
        if (d.length == 13) return d
        if (d.length == 12) return d + ean13Check(d)
        val body = ("2" + d.padStart(11, '0')).take(12)
        return body + ean13Check(body)
    }

    private val L = listOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
    private val G = listOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
    private val R = listOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")
    private val PARITY = listOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")

    /** EAN-13 条模块：95 位 0/1（1 = 黑条），价签按位画矩形 */
    fun ean13Bars(code13: String): String {
        val d = code13.map { it - '0' }
        val par = PARITY[d[0]]
        val sb = StringBuilder("101")
        for (i in 1..6) sb.append((if (par[i - 1] == 'L') L else G)[d[i]])
        sb.append("01010")
        for (i in 7..12) sb.append(R[d[i]])
        return sb.append("101").toString()
    }

    /** 价签单位价：称重商品（单价按 kg）标每 500g，返回分；按件商品返回 null */
    fun labelPer500g(pricePerKg: Long, weighable: Boolean): Long? = if (weighable) Money.roundHalfUp(pricePerKg, 2) else null
}
