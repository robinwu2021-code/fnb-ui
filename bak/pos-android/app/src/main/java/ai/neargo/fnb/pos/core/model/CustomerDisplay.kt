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
}

data class CustomerLine(val name: String, val spec: String, val qty: Int, val amount: Long, val gifted: Boolean)
data class CustomerSlide(val name: String, val price: Long, val image: String?)
data class CustomerCheckout(val method: String, val received: Long?, val change: Long?)

sealed interface CustomerView {
    val store: String
    data class Idle(override val store: String, val slides: List<CustomerSlide>) : CustomerView
    data class Order(
        override val store: String, val table: String, val lines: List<CustomerLine>, val showLines: Boolean,
        val totals: Totals, val checkout: CustomerCheckout?,
    ) : CustomerView
    data class Paid(override val store: String, val amount: Long, val change: Long, val method: String) : CustomerView
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

    /** 轮播菜品：有图优先、排除沽清，最多 8 道。 */
    fun slidesOf(s: StoreData): List<CustomerSlide> = s.products
        .filter { it.stock != 0 }
        .sortedByDescending { it.image != null }
        .take(MAX_SLIDES)
        .map { CustomerSlide(it.name, it.price, it.image) }
}
