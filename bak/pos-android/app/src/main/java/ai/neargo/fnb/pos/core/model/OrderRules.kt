package ai.neargo.fnb.pos.core.model

/** 规则校验结果：成功给出新订单，失败给出可直接展示的原因。 */
sealed interface OrderResult {
    data class Ok(val order: TableOrder) : OrderResult
    data class Rejected(val reason: String) : OrderResult
}

/**
 * 订单纯规则（无副作用、可单测）：加菜合并、库存上限、草稿改量、状态推进不回退、金额计算。
 * Repository 负责保存，ViewModel 负责调用和展示结果。
 */
object OrderRules {
    const val DEFAULT_SPEC = "标准做法"
    private const val SERVICE_RATE = 0.05
    private const val MEMBER_RATE = 0.05

    fun qtyOf(order: TableOrder, dishId: String) = order.lines.filter { it.dishId == dishId }.sumOf { it.qty }

    /** 加菜：沽清、超出余量拒绝；同菜同规格的草稿行合并；新行排在草稿区末尾。 */
    fun add(order: TableOrder, dish: Dish, spec: String, unit: Int, qty: Int, newUid: Long): OrderResult {
        if (qty <= 0) return OrderResult.Rejected("数量必须大于 0")
        if (dish.soldOut) return OrderResult.Rejected("${dish.name}已沽清")
        if (dish.stock != null && qtyOf(order, dish.id) + qty > dish.stock) return OrderResult.Rejected("${dish.name}仅剩 ${dish.stock} 份")
        val s = spec.ifBlank { DEFAULT_SPEC }
        val lines = order.lines.toMutableList()
        val i = lines.indexOfFirst { it.status == LineStatus.New && it.dishId == dish.id && it.spec == s }
        if (i >= 0) {
            lines[i] = lines[i].copy(qty = lines[i].qty + qty)
        } else {
            val at = lines.indexOfLast { it.status == LineStatus.New } + 1
            lines.add(at, OrderLine(newUid, dish.id, dish.name, s, unit, qty, LineStatus.New))
        }
        return OrderResult.Ok(order.copy(lines = lines))
    }

    /** 只改草稿行；增加走库存校验，减到 0 移除；已送厨行拒绝。 */
    fun changeQty(order: TableOrder, uid: Long, delta: Int, dish: Dish): OrderResult {
        val line = order.lines.firstOrNull { it.uid == uid } ?: return OrderResult.Rejected("菜品已不在账单中")
        if (line.status != LineStatus.New) return OrderResult.Rejected("${line.name}已送厨，不能直接改数量")
        if (delta > 0) return add(order, dish, line.spec, line.unit, delta, uid)
        val q = line.qty + delta
        val lines = if (q <= 0) order.lines.filterNot { it.uid == uid } else order.lines.map { if (it.uid == uid) it.copy(qty = q) else it }
        return OrderResult.Ok(order.copy(lines = lines))
    }

    fun clearNew(order: TableOrder) = order.copy(lines = order.lines.filterNot { it.status == LineStatus.New })

    /** 送厨：所有草稿行转为已下单。 */
    fun sendAll(order: TableOrder) = order.copy(lines = order.lines.map { if (it.status == LineStatus.New) it.copy(status = LineStatus.Sent) else it })

    /** 推进行状态，只进不退（乱序回执不会让已上菜回到催菜中）；草稿行不能直接推进。 */
    fun advance(order: TableOrder, uid: Long, to: LineStatus) = order.copy(
        lines = order.lines.map {
            if (it.uid == uid && it.status != LineStatus.New && it.status.ordinal < to.ordinal) it.copy(status = to) else it
        },
    )

    fun totals(order: TableOrder): Totals {
        val sub = order.lines.sumOf { it.unit * it.qty }
        val service = Math.round(sub * SERVICE_RATE).toInt()
        val discount = if (order.member) Math.round(sub * MEMBER_RATE).toInt() else 0
        return Totals(
            sub = sub, service = service, discount = discount, due = sub + service - discount,
            items = order.lines.sumOf { it.qty }, newItems = order.pending.sumOf { it.qty },
        )
    }
}

/** 金额统一两位小数，负数写作 −¥。 */
fun yuan(fen: Int): String {
    val sign = if (fen < 0) "−" else ""
    val abs = kotlin.math.abs(fen)
    return "$sign¥${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
