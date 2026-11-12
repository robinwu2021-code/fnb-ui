package ai.neargo.fnb.pos.core.model

/**
 * 客显屏（顾客面向的副屏）：收银员所在桌台的点单明细与金额、结账时应收与找零、收款成功致谢、空闲时轮播。
 * 只读展示，不接受顾客输入；内容全部由本机状态推导。
 */
sealed interface CustomerStage {
    /** 普通浏览：跟随收银员当前桌台。 */
    data object Browse : CustomerStage
    /** 正在结账：显示应收、收款方式与现金找零。 */
    data class Checkout(val tableId: String, val method: String, val received: Long?) : CustomerStage
    /** 收款成功：致谢页停留片刻后回到空闲。 */
    data class Paid(val tableId: String, val amount: Long, val change: Long, val method: String, val at: Long) : CustomerStage
    /** 零售收银：购物车在收银页内存里，由收银页推一份快照过来（空购物车时收银页改推 Browse） */
    data class Retail(val cart: RetailCustomerCart) : CustomerStage
}

data class CustomerLine(val name: String, val spec: String, val qty: Int, val amount: Long, val gifted: Boolean)
/** note：零售轮播的促销说明（如「第二件半价」「会员价 ¥36.90」）；price 是否按 kg 由 perKg 标明 */
data class CustomerSlide(val name: String, val price: Long, val image: String?, val note: String = "", val perKg: Boolean = false)
data class CustomerCheckout(val method: String, val received: Long?, val change: Long?)

/** 零售客显的一行：数量写法（×2 / 0.618 kg）、金额（促销后）、优惠说明 */
data class RetailCustomerLine(val name: String, val qty: String, val amount: Long, val note: String, val gift: Boolean)
/** 零售客显内容：明细、刚扫的一行、件数、原价合计、已省、应付（有换货抵扣时是补收 / 退差价）、收款 */
data class RetailCustomerCart(
    val lines: List<RetailCustomerLine>, val last: String?, val pieces: Int, val sub: Long, val save: Long, val due: Long,
    val member: Boolean, val credit: Long = 0, val refund: Long = 0, val pay: CustomerCheckout? = null,
)

sealed interface CustomerView {
    val store: String
    data class Idle(override val store: String, val slides: List<CustomerSlide>) : CustomerView
    data class Order(
        override val store: String, val table: String, val lines: List<CustomerLine>, val showLines: Boolean,
        val totals: Totals, val checkout: CustomerCheckout?,
    ) : CustomerView
    data class Paid(override val store: String, val amount: Long, val change: Long, val method: String) : CustomerView
    data class Retail(override val store: String, val cart: RetailCustomerCart, val showLines: Boolean) : CustomerView
}

object CustomerDisplayRules {
    /** 致谢页停留时长。 */
    const val PAID_HOLD_MS = 8_000L
    /** 空闲轮播每页时长与最多菜品数。 */
    const val SLIDE_MS = 5_000L
    const val MAX_SLIDES = 8

    fun view(s: StoreData?, currentTable: String, stage: CustomerStage, showLines: Boolean, slides: Boolean, now: Long): CustomerView {
        val store = s?.settings?.shortName.orEmpty()
        if (s == null) return CustomerView.Idle(store, emptyList())
        if (stage is CustomerStage.Paid && now - stage.at in 0 until PAID_HOLD_MS) return CustomerView.Paid(store, stage.amount, stage.change, stage.method)
        if (s.biz == BizType.Retail) {
            if (stage is CustomerStage.Retail && (stage.cart.lines.isNotEmpty() || stage.cart.credit > 0)) return CustomerView.Retail(store, stage.cart, showLines)
            return CustomerView.Idle(store, if (!slides) emptyList() else slidesOf(s))
        }
        val tableId = (stage as? CustomerStage.Checkout)?.tableId ?: currentTable
        val table = s.tables.firstOrNull { it.id == tableId }
        val order = s.orders[tableId]
        if (table != null && table.status == TableStatus.Dining && order != null && order.lines.isNotEmpty()) {
            val lines = order.lines.map { l ->
                CustomerLine(l.name, if (l.spec == OrderRules.DEFAULT_SPEC) "" else l.spec, l.qty, if (l.gifted) 0 else Money.line(l.unit, l.qty), l.gifted)
            }
            val totals = OrderRules.totals(order)
            val checkout = (stage as? CustomerStage.Checkout)?.takeIf { it.tableId == tableId }?.let { c ->
                CustomerCheckout(c.method, c.received, c.received?.takeIf { it >= totals.due }?.minus(totals.due))
            }
            return CustomerView.Order(store, tableId, lines, showLines, totals, checkout)
        }
        return CustomerView.Idle(store, if (!slides) emptyList() else slidesOf(s))
    }

    /** 轮播菜品：有图优先、排除沽清，最多 8 道。零售门店：有促销 / 会员价的商品排前面，标出优惠。 */
    fun slidesOf(s: StoreData): List<CustomerSlide> {
        val retail = s.biz == BizType.Retail
        fun note(p: Product) = if (!retail) "" else p.retail?.promo?.label ?: p.retail?.memberPrice?.takeIf { it < p.price }?.let { "会员价 ${yuan(it)}" } ?: ""
        return s.products
            // 餐饮沿用原来的沽清判断；零售有规格的看各规格合计
            .filter { if (retail) it.totalStock != 0 else it.stock != 0 }
            .sortedWith(compareByDescending<Product> { note(it).isNotEmpty() }.thenByDescending { it.image != null })
            .take(MAX_SLIDES)
            .map { CustomerSlide(it.name, it.price, it.image, note(it), retail && it.retail?.weighable == true) }
    }

    /**
     * 零售购物车 → 客显内容（收银页调用）。金额用 RetailCheckout，和收银页、入账同一份算法。
     * credit：换货抵扣；method / received：收款态时的方式与实收。
     */
    fun retailCart(products: List<Product>, lines: List<CartLine>, member: Boolean, last: Long?, credit: Long = 0, method: String? = null, received: Long? = null): RetailCustomerCart {
        val byId = products.associateBy { it.id }
        val t = RetailCheckout.totals(products, lines, member)
        val st = RetailPricing.exchangeSettle(t.due, credit)
        val due = if (credit > 0) st.pay else t.due
        val rows = lines.map { l ->
            val p = byId[l.productId]
            val info = if (l.custom) RetailPricing.PriceInfo(l.customPrice ?: 0) else p?.let { RetailCheckout.info(it, l.sku) } ?: RetailPricing.PriceInfo(0)
            val promo = RetailPricing.linePromo(l, info)
            val mem = if (member) RetailPricing.lineMemberSave(l, info) else 0
            RetailCustomerLine(
                (l.customName ?: p?.name ?: l.productId) + (l.sku?.let { " · $it" } ?: ""),
                l.grams?.let { "%d.%03d kg".format(it / 1000, it % 1000) } ?: "×${l.qty}",
                RetailPricing.lineAmount(l, info.unit) - promo - mem,
                listOfNotNull(if (l.gift) "赠送" else null, if (promo > 0) "${info.promo?.label} −${yuan(promo)}" else null, if (mem > 0) "会员价 −${yuan(mem)}" else null).joinToString(" · "),
                l.gift,
            )
        }
        val lastName = lines.firstOrNull { it.key == last }?.let { l -> (l.customName ?: byId[l.productId]?.name ?: "") + (l.sku?.let { " · $it" } ?: "") }
        val pay = method?.let { m -> CustomerCheckout(m, received, received?.takeIf { m == "现金" && it >= due }?.minus(due)) }
        return RetailCustomerCart(rows, lastName, t.pcs, t.sub, t.save, due, member, credit, if (credit > 0) st.refund else 0, pay)
    }
}
