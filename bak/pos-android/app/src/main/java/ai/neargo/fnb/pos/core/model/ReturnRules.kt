package ai.neargo.fnb.pos.core.model

import ai.neargo.fnb.pos.core.common.PayMethods
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 零售退货规则（移植自原型 domain/aftersale.ts）：可退数量（称重整行）· 整单优惠按行分摊（尾差落金额最大行）·
 * 退款金额 · 默认回库 · 原单状态 · 渠道限制 · 无单退货。金额整数分。
 */
object ReturnRules {
    data class SoldLine(val key: String, val qty: Int, val amount: Long, val returned: Int = 0, val weighed: Boolean = false)
    @Serializable enum class Reason(val label: String) { Quality("质量问题"), Unwanted("不想要了"), Wrong("拿错了"), Other("其他") }
    enum class SaleStatus(val label: String) { Paid("已结账"), PartialReturn("部分退货"), Returned("已退货") }

    /** 还能退几件；称重行只能整行退（1 或 0） */
    fun refundable(l: SoldLine) = if (l.weighed) (if (l.returned > 0) 0 else 1) else maxOf(0, l.qty - l.returned)

    /** 非负整数除法，0.5 舍去（与原型浮点 round2(amt - x) 的进位方向一致） */
    private fun roundHalfDown(n: Long, d: Long): Long = n / d + if (n % d * 2 > d) 1 else 0

    /** 整单优惠按行实付比例分摊：每行分摊后的实付；尾差记在金额最大的一行（第一个），合计 = 整单实付 */
    fun allocate(lines: List<SoldLine>, discount: Long): Map<String, Long> {
        val gross = lines.sumOf { it.amount }
        if (gross == 0L) return lines.associate { it.key to 0L }
        val out = LinkedHashMap<String, Long>()
        lines.forEach { out[it.key] = it.amount - roundHalfDown(Math.multiplyExact(discount, it.amount), gross) }
        val big = lines.reduce { m, l -> if (l.amount > m.amount) l else m }
        out[big.key] = out.getValue(big.key) + (gross - discount) - out.values.sum()
        return out
    }

    /** 退这些（行 → 件数）应退多少：按分摊后的单件实付，合计后四舍五入到分 */
    fun refundAmount(lines: List<SoldLine>, discount: Long, pick: Map<String, Int>): Long {
        val net = allocate(lines, discount)
        var total = BigDecimal.ZERO
        lines.forEach { l ->
            val n = minOf(pick[l.key] ?: 0, refundable(l))
            if (l.weighed) { if (n > 0) total += BigDecimal(net.getValue(l.key)) }
            else if (n > 0) total += BigDecimal(net.getValue(l.key)).multiply(BigDecimal(n)).divide(BigDecimal(l.qty), MathContext.DECIMAL64)
        }
        return total.setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    /** 退货后把件数记到原单行（不超过可退数量） */
    fun applyReturn(lines: List<SoldLine>, pick: Map<String, Int>): List<SoldLine> = lines.map { l ->
        val n = minOf(pick[l.key] ?: 0, refundable(l))
        if (n > 0) l.copy(returned = l.returned + if (l.weighed) 1 else n) else l
    }

    fun status(lines: List<SoldLine>): SaleStatus = when {
        lines.all { refundable(it) == 0 } -> SaleStatus.Returned
        lines.any { it.returned > 0 } -> SaleStatus.PartialReturn
        else -> SaleStatus.Paid
    }

    /** 默认是否回库存：质量问题不回（记报损），其余回 */
    fun restockByDefault(r: Reason) = r != Reason.Quality

    /** 即时零售平台单要在平台发起退款，门店不能直接退 */
    fun channelBlocked(channel: String) = channel == "platform"

    /** 无单退货：需主管授权，只能退现金或储值 */
    val NO_RECEIPT_METHODS = listOf(PayMethods.CASH, PayMethods.STORED)
    fun pickedPieces(lines: List<SoldLine>, pick: Map<String, Int>) = lines.sumOf { minOf(pick[it.key] ?: 0, refundable(it)) }
}
