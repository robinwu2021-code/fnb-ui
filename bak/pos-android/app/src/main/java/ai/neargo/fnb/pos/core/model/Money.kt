package ai.neargo.fnb.pos.core.model

/**
 * 金额规则（N2 金额专项）：
 * - 所有金额为 Long 整数分，币种见 [CURRENCY]；不使用浮点参与计算。
 * - 上限：单价/加价 [MAX_UNIT]，单行份数 [MAX_QTY]，单据/收款/备用金 [MAX_BILL]。
 * - 舍入：比例（服务费、会员价、折扣）按万分比整数运算，四舍五入（0.5 分进位）。
 * - 乘法与累加用 exact 运算，溢出抛 ArithmeticException，业务入口先用上限拒绝。
 */
object Money {
    const val CURRENCY = "CNY"
    val SUPPORTED = setOf(CURRENCY)
    /** 单价或加价上限：¥99,999.00。 */
    const val MAX_UNIT = 9_999_900L
    /** 单行份数上限。 */
    const val MAX_QTY = 999
    /** 单据、收款、备用金上限：¥99,999,999.99。 */
    const val MAX_BILL = 9_999_999_999L

    fun line(unit: Long, qty: Int): Long = Math.multiplyExact(unit, qty.toLong())
    fun sum(values: Iterable<Long>): Long = values.fold(0L) { a, b -> Math.addExact(a, b) }

    /** amount × basisPoints / 10000，四舍五入（非负金额）。 */
    fun rate(amount: Long, basisPoints: Int): Long {
        require(amount >= 0 && basisPoints in 0..10_000) { "比例参数无效" }
        return roundHalfUp(Math.multiplyExact(amount, basisPoints.toLong()), 10_000)
    }

    /** 非负整数除法四舍五入。 */
    fun roundHalfUp(numerator: Long, denominator: Long): Long {
        require(numerator >= 0 && denominator > 0)
        return numerator / denominator + if (numerator % denominator * 2 >= denominator) 1 else 0
    }

    /** 元文本 → 分：非负、最多两位小数、不超过 max。 */
    fun parse(text: String, max: Long = MAX_BILL): Long? {
        val s = text.trim()
        if (!Regex("^\\d{1,11}(\\.\\d{1,2})?$").matches(s)) return null
        val fen = runCatching { s.toBigDecimal().movePointRight(2).longValueExact() }.getOrNull() ?: return null
        return fen.takeIf { it <= max }
    }

    fun withinBill(amount: Long) = amount in 0..MAX_BILL

    /** 表单用：返回带原因的结果。 */
    fun input(text: String, label: String, max: Long = MAX_BILL): Result<Long> = parse(text, max)?.let { Result.success(it) }
        ?: Result.failure(IllegalArgumentException("$label 应为非负金额，最多两位小数，不超过 ${yuan(max)}"))
}
