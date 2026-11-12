package ai.neargo.fnb.pos.core.model

import kotlinx.serialization.Serializable

/**
 * 零售购物车规则（移植自原型 domain/cart.ts）：扫码加购合并、加减、撤销、赠送、件数、行级促销。
 * 和餐饮订单行分开：零售没有送厨、上菜状态。重量用整数克，金额用整数分。
 */
@Serializable
data class CartLine(
    val key: Long,
    val productId: String,
    val qty: Int,
    /** 称重商品的重量（克）；null = 按件 */
    val grams: Int? = null,
    /** 规格文字（如「白 / M」）；同商品不同规格分行 */
    val sku: String? = null,
    val gift: Boolean = false,
    /** 临时商品（未知条码手输）：名称与单价（分） */
    val customName: String? = null,
    val customPrice: Long? = null,
    /** 手改单价（称重商品是每 kg）；改过价的行不再叠加促销和会员价 */
    val price: Long? = null,
    /** 行备注（打到小票上） */
    val note: String? = null,
) {
    val weighed get() = grams != null
    val custom get() = customName != null
}

/** 行级促销 */
@Serializable enum class LinePromo(val label: String) { Half2("第二件半价") }

object RetailCartRules {
    data class AddResult(val lines: List<CartLine>, val last: Long)

    /** 扫一件：同商品同规格、非称重、非赠送、非临时、没改价没备注的行合并数量并移到最上；其余每次新起一行 */
    fun scanAdd(lines: List<CartLine>, add: CartLine, newKey: () -> Long): AddResult {
        val hit = if (!add.weighed && !add.custom) lines.firstOrNull { it.productId == add.productId && !it.weighed && !it.gift && !it.custom && it.price == null && it.note == null && (it.sku ?: "") == (add.sku ?: "") } else null
        if (hit != null) return AddResult(listOf(hit.copy(qty = hit.qty + add.qty)) + lines.filter { it !== hit }, hit.key)
        val key = newKey()
        return AddResult(listOf(add.copy(key = key)) + lines, key)
    }

    /** 加减数量：减到 0 删行；称重行不能加减 */
    fun bump(lines: List<CartLine>, key: Long, d: Int): List<CartLine> {
        val l = lines.firstOrNull { it.key == key } ?: return lines
        if (l.weighed) return lines
        return if (l.qty + d <= 0) lines.filter { it.key != key } else lines.map { if (it.key == key) it.copy(qty = it.qty + d) else it }
    }

    /** 撤销刚扫的：普通行减一件，称重行整行删 */
    fun undoLast(lines: List<CartLine>, key: Long): List<CartLine> {
        val l = lines.firstOrNull { it.key == key } ?: return lines
        return if (l.weighed) lines.filter { it.key != key } else bump(lines, key, -1)
    }

    fun toggleGift(lines: List<CartLine>, key: Long) = lines.map { if (it.key == key) it.copy(gift = !it.gift) else it }
    fun remove(lines: List<CartLine>, key: Long) = lines.filter { it.key != key }

    /** 件数：称重行算 1 件 */
    fun pieces(lines: List<CartLine>) = lines.sumOf { if (it.weighed) 1 else it.qty }

    /** 第二件半价：每满 2 件减半件（半分四舍五入） */
    fun promoOff(kind: LinePromo?, qty: Int, unit: Long): Long = when (kind) {
        LinePromo.Half2 -> Money.roundHalfUp(Math.multiplyExact((qty / 2).toLong(), unit), 2)
        null -> 0
    }
}
