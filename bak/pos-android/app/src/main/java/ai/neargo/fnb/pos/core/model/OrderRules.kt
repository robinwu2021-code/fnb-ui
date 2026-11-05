package ai.neargo.fnb.pos.core.model

/** 规则校验结果：成功给出新订单，失败给出可直接展示的原因。 */
sealed interface OrderResult {
    data class Ok(val order: TableOrder) : OrderResult
    data class Rejected(val reason: String) : OrderResult
}

/**
 * 订单纯规则（无副作用、可单测）：加菜合并、SKU / 商品库存上限、草稿改量、送厨（在线 / 离线）、
 * 批次补发幂等、状态只进不退、金额计算。
 */
object OrderRules {
    const val DEFAULT_SPEC = "标准做法"
    private const val SERVICE_RATE = 0.05
    private const val MEMBER_RATE = 0.05

    fun qtyOf(order: TableOrder, dishId: String) = order.lines.filter { it.dishId == dishId }.sumOf { it.qty }
    private fun qtyOfSku(order: TableOrder, key: String) = order.lines.filter { it.skuKey == key }.sumOf { it.qty }

    /** 加菜：有 SKU 时按所选 SKU 校验余量与单价；同菜同 SKU 的草稿行合并。 */
    fun add(order: TableOrder, product: Product, sku: Sku?, qty: Int, newUid: Long, note: String = ""): OrderResult {
        if (qty <= 0) return OrderResult.Rejected("数量必须大于 0")
        if (product.hasSpec && sku == null) return OrderResult.Rejected("请先选择${product.name}的规格")
        if (sku != null) {
            if (sku.stock == 0) return OrderResult.Rejected("${product.name}（${sku.label}）已沽清")
            if (qtyOfSku(order, sku.key) + qty > sku.stock) return OrderResult.Rejected("${product.name}（${sku.label}）仅剩 ${sku.stock} 份")
        } else {
            if (product.soldOut) return OrderResult.Rejected("${product.name}已沽清")
            val stock = product.stock
            if (stock != null && qtyOf(order, product.id) + qty > stock) return OrderResult.Rejected("${product.name}仅剩 $stock 份")
        }
        val spec = sku?.label ?: DEFAULT_SPEC
        val unit = sku?.price ?: product.price
        val lines = order.lines.toMutableList()
        val i = lines.indexOfFirst { it.status == LineStatus.New && it.dishId == product.id && it.skuKey == sku?.key && it.spec == spec && it.note == note.trim() }
        if (i >= 0) {
            lines[i] = lines[i].copy(qty = lines[i].qty + qty)
        } else {
            val at = lines.indexOfLast { it.status == LineStatus.New } + 1
            lines.add(at, OrderLine(newUid, product.id, product.name, spec, unit, qty, LineStatus.New, skuKey = sku?.key, note = note.trim()))
        }
        return OrderResult.Ok(order.copy(lines = lines))
    }

    /** 只改草稿行；增加走库存校验，减到 0 移除；已送厨（含待同步）行拒绝。 */
    fun changeQty(order: TableOrder, uid: Long, delta: Int, product: Product): OrderResult {
        val line = order.lines.firstOrNull { it.uid == uid } ?: return OrderResult.Rejected("菜品已不在账单中")
        if (line.status != LineStatus.New) return OrderResult.Rejected("${line.name}已送厨，不能直接改数量")
        if (delta > 0) {
            val sku = line.skuKey?.let { k -> product.skus.firstOrNull { it.key == k } }
            if (line.skuKey != null && sku == null) return OrderResult.Rejected("${line.name}的规格已下架")
            return add(order, product, sku, delta, uid, line.note)
        }
        val q = line.qty + delta
        val lines = if (q <= 0) order.lines.filterNot { it.uid == uid } else order.lines.map { if (it.uid == uid) it.copy(qty = q) else it }
        return OrderResult.Ok(order.copy(lines = lines))
    }

    /** 菜品备注只改草稿行；已送厨的备注需通过厨房沟通，不在此修改。 */
    fun setNote(order: TableOrder, uid: Long, note: String): OrderResult {
        val line = order.lines.firstOrNull { it.uid == uid } ?: return OrderResult.Rejected("菜品已不在账单中")
        if (line.status != LineStatus.New) return OrderResult.Rejected("${line.name}已送厨，备注请通知厨房")
        if (note.length > 40) return OrderResult.Rejected("备注最多 40 字")
        return OrderResult.Ok(order.copy(lines = order.lines.map { if (it.uid == uid) it.copy(note = note.trim()) else it }))
    }

    fun clearNew(order: TableOrder) = order.copy(lines = order.lines.filterNot { it.status == LineStatus.New })

    /** 送厨：草稿行打上批次号；在线直接已下单，离线为待同步。 */
    fun send(order: TableOrder, batchId: String, online: Boolean) = order.copy(
        lines = order.lines.map { if (it.status == LineStatus.New) it.copy(status = if (online) LineStatus.Sent else LineStatus.Queued, batchId = batchId) else it },
    )

    /** 批次补发成功：只把该批次的待同步行改为已下单；重复调用无副作用（幂等）。 */
    fun markBatchSent(order: TableOrder, batchId: String) = order.copy(
        lines = order.lines.map { if (it.batchId == batchId && it.status == LineStatus.Queued) it.copy(status = LineStatus.Sent) else it },
    )

    /** 推进行状态，只进不退；草稿与待同步行不能直接推进。 */
    fun advance(order: TableOrder, uid: Long, to: LineStatus) = order.copy(
        lines = order.lines.map {
            if (it.uid == uid && it.status != LineStatus.New && it.status != LineStatus.Queued && it.status.ordinal < to.ordinal) it.copy(status = to) else it
        },
    )

    fun totals(order: TableOrder): Totals {
        val sub = order.lines.sumOf { it.unit * it.qty }
        val service = Math.round(sub * SERVICE_RATE).toInt()
        val discount = if (order.member) Math.round(sub * MEMBER_RATE).toInt() else 0
        return Totals(
            sub = sub, service = service, discount = discount, due = sub + service - discount,
            items = order.lines.sumOf { it.qty }, newItems = order.pending.sumOf { it.qty }, queuedItems = order.queued.sumOf { it.qty },
        )
    }

    /** 结账核对条件：有菜品，且没有新加或待同步行（数据契约 §7）。 */
    fun checkoutBlock(t: Totals): String? = when {
        t.items == 0 -> "本单没有菜品"
        t.newItems > 0 -> "先送厨再结账"
        t.queuedItems > 0 -> "有 ${t.queuedItems} 份待同步，联网后再结账"
        else -> null
    }
}

/** 金额统一两位小数，负数写作 −¥。 */
fun yuan(fen: Int): String {
    val sign = if (fen < 0) "−" else ""
    val abs = kotlin.math.abs(fen)
    return "$sign¥${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

/** 分转「元」输入文本：4800 → "48"，4850 → "48.5"。 */
fun fenToInput(fen: Int): String = if (fen % 100 == 0) "${fen / 100}" else yuan(fen).removePrefix("¥").trimEnd('0')
