package ai.neargo.fnb.pos.core.model

/**
 * 零售收银：把购物车行按商品资料算成价格、零售单行。收银页预览和入账用同一份，保证显示的应收就是入账的应收。
 */
object RetailCheckout {
    /** 规格行的单价：有规格取该格价格，否则取商品售价 */
    fun unitOf(p: Product, skuLabel: String?): Long = skuLabel?.let { l -> p.skus.firstOrNull { it.label == l }?.price } ?: p.price
    fun skuKeyOf(p: Product, skuLabel: String?): String? = skuLabel?.let { l -> p.skus.firstOrNull { it.label == l }?.key }

    fun info(p: Product, skuLabel: String? = null) = RetailPricing.PriceInfo(unitOf(p, skuLabel), p.retail?.memberPrice, p.retail?.promo)

    /** 一行的计价信息：临时商品用行上单价；手改过价的只认改后单价（不叠加促销、会员价）；否则按商品资料 */
    fun lineInfo(byId: Map<String, Product>, l: CartLine): RetailPricing.PriceInfo = when {
        l.custom -> RetailPricing.PriceInfo(l.customPrice ?: 0)
        l.price != null -> RetailPricing.PriceInfo(l.price)
        else -> byId[l.productId]?.let { info(it, l.sku) } ?: RetailPricing.PriceInfo(0)
    }

    /**
     * 整单优惠：先主管整单折扣（对促销、会员价后的金额），再按门店「优惠活动」算——整单折扣类按折后金额、满减按小计判断门槛。
     * 返回 coupon（活动减额）与 manual（主管折扣），交给 totals。
     */
    fun offs(products: List<Product>, promotions: List<Promotion>, lines: List<CartLine>, member: Boolean, x: CheckoutExtras): Pair<Long, Long> {
        val t = totals(products, lines, member)
        val base = t.sub - t.promo - t.memberSave
        val manual = Money.rate(base, (100 - x.discountPct.coerceIn(1, 100)) * 100)
        val after = base - manual
        val chosen = promotions.filter { it.id in x.promotionIds && it.enabled && t.sub >= it.threshold }
        val pct = chosen.filter { it.kind == PromotionKind.Percent }.sumOf { Money.rate(after, (100 - it.percent.coerceIn(1, 100)) * 100) }
        val cut = chosen.filter { it.kind == PromotionKind.Threshold }.sumOf { it.amount }
        return (pct + cut) to manual
    }

    /** 含整单优惠的应收（收银页显示、入账同用） */
    fun totalsWith(products: List<Product>, promotions: List<Promotion>, lines: List<CartLine>, member: Boolean, x: CheckoutExtras): RetailPricing.Totals {
        val (coupon, manual) = offs(products, promotions, lines, member, x)
        return totals(products, lines, member, coupon, manual)
    }

    /** 优惠活动能不能用：启用、满门槛、整单折扣类只能一个 */
    fun promotionBlock(products: List<Product>, promotions: List<Promotion>, lines: List<CartLine>, ids: List<String>): String? {
        val sub = totals(products, lines, false).sub
        val chosen = ids.map { id -> promotions.firstOrNull { it.id == id } ?: return "活动已删除" }
        chosen.firstOrNull { !it.enabled }?.let { return "活动「${it.name}」已停用" }
        chosen.firstOrNull { sub < it.threshold }?.let { return "商品金额未满 ${yuan(it.threshold)}，不能用「${it.name}」" }
        if (chosen.count { it.kind == PromotionKind.Percent } > 1) return "整单折扣类活动只能选一个"
        return null
    }

    /** 改价、整单折扣需要主管授权 */
    fun needsApproval(lines: List<CartLine>, x: CheckoutExtras) = x.discountPct < 100 || lines.any { it.price != null }

    /** 应收；找不到的商品按 0 元（入账时会拦下） */
    fun totals(products: List<Product>, lines: List<CartLine>, member: Boolean, coupon: Long = 0, manual: Long = 0): RetailPricing.Totals {
        val byId = products.associateBy { it.id }
        // 同一商品不同规格价格可能不同：逐行取 info
        var sub = 0L; var promo = 0L; var mem = 0L
        lines.forEach { l ->
            val info = lineInfo(byId, l)
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
            val info = lineInfo(byId, l)
            val amount = RetailPricing.lineAmount(l, info.unit) - RetailPricing.linePromo(l, info) - (if (member) RetailPricing.lineMemberSave(l, info) else 0)
            RetailSaleLine("L${i + 1}", if (l.custom) "" else l.productId, l.customName ?: p!!.name, l.sku, l.qty, l.grams, info.unit, amount, note = l.note.orEmpty())
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
