package ai.neargo.fnb.pos.core.model

/**
 * 零售收银：把购物车行按商品资料算成价格、零售单行。收银页预览和入账用同一份，保证显示的应收就是入账的应收。
 */
object RetailCheckout {
    /** 规格行的单价：有规格取该格价格，否则取商品售价 */
    fun unitOf(p: Product, skuLabel: String?): Long = skuLabel?.let { l -> p.skus.firstOrNull { it.label == l }?.price } ?: p.price
    fun skuKeyOf(p: Product, skuLabel: String?): String? = skuLabel?.let { l -> p.skus.firstOrNull { it.label == l }?.key }

    fun info(p: Product, skuLabel: String? = null) = RetailPricing.PriceInfo(unitOf(p, skuLabel), p.retail?.memberPrice, p.retail?.promo)

    /** 应收；找不到的商品按 0 元（入账时会拦下） */
    fun totals(products: List<Product>, lines: List<CartLine>, member: Boolean, coupon: Long = 0, manual: Long = 0): RetailPricing.Totals {
        val byId = products.associateBy { it.id }
        // 同一商品不同规格价格可能不同：逐行取 info
        var sub = 0L; var promo = 0L; var mem = 0L
        lines.forEach { l ->
            val p = byId[l.productId]
            val info = if (l.custom) RetailPricing.PriceInfo(l.customPrice ?: 0) else p?.let { info(it, l.sku) } ?: RetailPricing.PriceInfo(0)
            sub += RetailPricing.lineAmount(l, info.unit); promo += RetailPricing.linePromo(l, info); if (member) mem += RetailPricing.lineMemberSave(l, info)
        }
        val cp = minOf(coupon, maxOf(0L, sub - promo - mem))
        val raw = maxOf(0L, sub - promo - mem - cp - manual)
        val round = raw % 10
        return RetailPricing.Totals(sub, promo, mem, cp, manual, round, raw - round, RetailCartRules.pieces(lines))
    }

    /** 零售单行：amount = 行原价 − 行级促销 − 会员价减额（整单抹零记在单上的 discount） */
    fun saleLines(products: List<Product>, lines: List<CartLine>, member: Boolean): List<RetailSaleLine> {
        val byId = products.associateBy { it.id }
        return lines.mapIndexed { i, l ->
            val p = byId[l.productId]
            val info = if (l.custom) RetailPricing.PriceInfo(l.customPrice ?: 0) else info(p!!, l.sku)
            val amount = RetailPricing.lineAmount(l, info.unit) - RetailPricing.linePromo(l, info) - (if (member) RetailPricing.lineMemberSave(l, info) else 0)
            RetailSaleLine("L${i + 1}", if (l.custom) "" else l.productId, l.customName ?: p!!.name, l.sku, l.qty, l.grams, info.unit, amount)
        }
    }

    /** 加购前检查：缺货（按件库存为 0 / 称重库存为 0）、所选规格缺货 */
    fun addBlock(p: Product, skuLabel: String?): String? = when {
        skuLabel != null && (p.skus.firstOrNull { it.label == skuLabel }?.stock ?: 0) <= 0 -> "${p.name} $skuLabel 缺货"
        skuLabel == null && !p.hasSpec && p.stock == 0 -> "${p.name} 缺货"
        else -> null
    }

    /** 促销品每单限购：同一商品（各规格合计）件数超过 limit 时返回原因；limit = 0 不限 */
    fun limitBlock(products: List<Product>, lines: List<CartLine>, limit: Int): String? {
        if (limit <= 0) return null
        val byId = products.associateBy { it.id }
        return lines.filter { !it.custom && !it.gift && it.grams == null && byId[it.productId]?.retail?.promo != null }.groupBy { it.productId }
            .entries.firstOrNull { (_, ls) -> ls.sumOf { it.qty } > limit }?.let { (id, _) -> "${byId.getValue(id).name} 是促销品，每单限购 $limit 件" }
    }
}
