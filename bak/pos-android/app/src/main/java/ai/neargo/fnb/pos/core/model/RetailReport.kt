package ai.neargo.fnb.pos.core.model

/**
 * 零售报表分区：按零售单算销售、退货、渠道、商品与分类排行。
 * 收款方式、时段分布仍用共用的 ReportRules（按收款记录算，退款是负数，已自然扣减）。
 */
data class RetailReport(
    val sales: Int, val gross: Long, val pieces: Int,
    val refund: Long, val refundCount: Int, val exchangeCount: Int, val looseRefund: Long,
    val byChannel: List<Pair<String, Long>>,
    val items: List<DishStat>,
    val categories: List<Pair<String, Long>>,
) {
    /** 净销售 = 售出实收 − 退款（换货抵扣不算退款，钱留在新单里） */
    val net get() = gross - refund
    val average get() = if (sales == 0) 0L else Money.roundHalfUp(gross, sales.toLong())
}

object RetailReportRules {
    fun build(s: StoreData, from: Long, to: Long): RetailReport {
        val sales = s.retail.sales.filter { it.at in from until to }
        // 退货按发生时间算进当期（可能退的是更早的单）
        val returns = s.retail.sales.flatMap { it.returns }.filter { it.at in from until to }
        val loose = s.retail.looseReturns.filter { it.at in from until to }
        val products = s.products.associateBy { it.id }
        val lines = sales.flatMap { it.lines }
        val items = lines.groupBy { it.name + (it.sku?.let { k -> " · $k" } ?: "") }
            .map { (n, ls) -> DishStat(n, ls.sumOf { if (it.grams != null) 1 else it.qty }, Money.sum(ls.map { it.amount })) }
            .sortedWith(compareByDescending<DishStat> { it.amount }.thenByDescending { it.qty })
        val cats = lines.groupBy { l -> products[l.productId]?.categoryId?.let { c -> s.categories.firstOrNull { it.id == c }?.name } ?: "其他" }
            .map { (n, ls) -> n to Money.sum(ls.map { it.amount }) }.sortedByDescending { it.second }
        val refunds = returns.filter { !it.exchange }
        // 实收不含「换货抵扣」：那部分钱在原单里收过了，再算一次就重复了
        fun cashIn(x: RetailSale) = Money.sum(x.pays.filter { it.method != EXCHANGE }.map { it.amount })
        return RetailReport(
            sales = sales.size, gross = Money.sum(sales.map { cashIn(it) }), pieces = sales.sumOf { it.pieces },
            refund = Money.sum(refunds.map { it.amount } + loose.map { it.amount }), refundCount = refunds.size + loose.size,
            exchangeCount = returns.count { it.exchange }, looseRefund = Money.sum(loose.map { it.amount }),
            byChannel = sales.groupBy { it.channel.label }.map { (k, v) -> k to Money.sum(v.map { cashIn(it) }) }.sortedByDescending { it.second },
            items = items, categories = cats,
        )
    }
}
