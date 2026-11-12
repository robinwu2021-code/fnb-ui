package ai.neargo.fnb.pos.core.model

/**
 * 零售计价（移植自原型 domain/pricing.ts 的 RETAIL_RULES 与 retail/data.ts 的适配器）：
 * 小计 → 行级促销（第二件半价）→ 会员价（低于售价才生效）→ 券（在促销后）→ 人工折扣 → 抹零到角 → 应收。
 * 无服务费、无整单活动。金额整数分。
 */
object RetailPricing {
    /** 计价需要的商品信息（由商品资料提供） */
    data class PriceInfo(val unit: Long, val memberPrice: Long? = null, val promo: LinePromo? = null)

    data class Totals(val sub: Long, val promo: Long, val memberSave: Long, val coupon: Long, val manual: Long, val round: Long, val due: Long, val pcs: Int) {
        /** 本单优惠合计（促销 + 会员价 + 券 + 人工 + 抹零） */
        val save get() = promo + memberSave + coupon + manual + round
    }

    /** 行原价小计：赠送为 0；称重 = 单价（分 / kg）× 克 / 1000，四舍五入到分 */
    fun lineAmount(l: CartLine, unit: Long): Long = when {
        l.gift -> 0
        l.grams != null -> Money.roundHalfUp(Math.multiplyExact(unit, l.grams.toLong()), 1000)
        else -> Money.line(unit, l.qty)
    }

    fun linePromo(l: CartLine, info: PriceInfo): Long = if (l.custom || l.gift) 0 else RetailCartRules.promoOff(info.promo, l.qty, info.unit)

    /** 会员价减额：只在会员价低于售价时生效 */
    fun lineMemberSave(l: CartLine, info: PriceInfo): Long {
        val mp = info.memberPrice ?: return 0
        if (l.custom || l.gift) return 0
        return Math.multiplyExact(maxOf(0L, info.unit - mp), l.qty.toLong())
    }

    /**
     * @param infoOf 按商品取单价、会员价、促销；临时商品用行上的单价
     * @param coupon 已选且可用的券减额（券在促销后算，零售允许抹零）
     */
    fun totals(lines: List<CartLine>, member: Boolean, coupon: Long = 0, manual: Long = 0, infoOf: (String) -> PriceInfo): Totals {
        var sub = 0L; var promo = 0L; var mem = 0L
        lines.forEach { l ->
            val info = if (l.custom) PriceInfo(l.customPrice ?: 0) else infoOf(l.productId)
            sub = Math.addExact(sub, lineAmount(l, info.unit))
            promo += linePromo(l, info)
            if (member) mem += lineMemberSave(l, info)
        }
        val cp = minOf(coupon, maxOf(0L, sub - promo - mem))
        val raw = maxOf(0L, sub - promo - mem - cp - manual)
        val round = raw % 10 // 抹零到角
        return Totals(sub, promo, mem, cp, manual, round, raw - round, RetailCartRules.pieces(lines))
    }

    /** 换货：新商品应收减抵扣，补收 pay 或退差价 refund（至多一个非 0） */
    data class Settle(val pay: Long, val refund: Long) { val even get() = pay == 0L && refund == 0L }
    fun exchangeSettle(newDue: Long, credit: Long): Settle { val d = newDue - credit; return Settle(maxOf(0L, d), maxOf(0L, -d)) }
}
