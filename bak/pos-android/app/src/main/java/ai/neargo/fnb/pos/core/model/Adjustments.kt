package ai.neargo.fnb.pos.core.model

import kotlinx.serialization.Serializable

@Serializable enum class AdjustmentKind(val label: String) { Void("退菜"), Gift("赠送"), Discount("整单折扣") }
@Serializable data class AdjustmentAudit(
    val id: String, val tableId: String, val kind: AdjustmentKind, val operator: String,
    val supervisor: String, val reason: String, val amount: Int, val qty: Int, val at: Long,
    val lineName: String = "", val detail: String = "",
)
data class AdjustmentRequest(val kind: AdjustmentKind, val uid: Long? = null, val quantity: Int = 1, val percent: Int = 100, val reason: String = "")

object AdjustmentRules {
    fun apply(order: TableOrder, request: AdjustmentRequest): OrderResult {
        if (request.reason.trim().length !in 2..100) return OrderResult.Rejected("请填写 2–100 字操作原因")
        if (request.kind == AdjustmentKind.Discount) {
            if (order.lines.isEmpty()) return OrderResult.Rejected("本单没有菜品")
            if (request.percent !in 1..100) return OrderResult.Rejected("折扣应为 1–100，100 表示原价")
            return OrderResult.Ok(order.copy(discountPercent = request.percent))
        }
        val line = order.lines.firstOrNull { it.uid == request.uid } ?: return OrderResult.Rejected("菜品已不在账单中")
        if (line.status == LineStatus.Queued) return OrderResult.Rejected("待同步菜品请先完成同步")
        if (request.kind == AdjustmentKind.Gift) {
            if (line.gifted) return OrderResult.Rejected("该菜品已赠送")
            return OrderResult.Ok(order.copy(lines = order.lines.map { if (it.uid == line.uid) it.copy(gifted = true) else it }))
        }
        if (line.status == LineStatus.New) return OrderResult.Rejected("新加菜品请直接调整数量")
        if (request.quantity !in 1..line.qty) return OrderResult.Rejected("退菜数量超出当前份数")
        return OrderResult.Ok(order.copy(lines = order.lines.mapNotNull {
            if (it.uid != line.uid) it else if (it.qty == request.quantity) null else it.copy(qty = it.qty - request.quantity)
        }))
    }
}

/** 现金输入只保留有效金额，金额一律整数分。 */
object CashRules {
    fun amount(text: String): Int? = if (Regex("^\\d{1,5}(\\.\\d{1,2})?$").matches(text)) {
        val p = text.split('.')
        p[0].toInt() * 100 + p.getOrNull(1).orEmpty().padEnd(2, '0').toInt()
    } else null
    fun key(value: String, key: String): String = when (key) {
        "清空" -> ""
        "⌫" -> value.dropLast(1)
        "." -> if ('.' in value) value else if (value.isEmpty()) "0." else "$value."
        else -> (value + key).let { if (Regex("^\\d{0,5}(\\.\\d{0,2})?$").matches(it)) it else value }
    }
    fun change(received: String, due: Int): Int? = amount(received)?.takeIf { it >= due }?.minus(due)
}
