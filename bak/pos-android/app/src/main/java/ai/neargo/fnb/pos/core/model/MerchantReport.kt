package ai.neargo.fnb.pos.core.model

/**
 * 商户汇总（跨门店、跨业态）：同一时间段内，按门店列营业额、单数、退款，再合计；收款方式合并。
 * 营业额口径和单店报表一致（实收，退款已扣）；单数餐饮按结账单、零售按销售单；退款只有零售有（餐饮是退菜，不算退款）。
 */
data class MerchantReport(val rows: List<Row>, val byMethod: Map<String, Long>) {
    data class Row(val storeId: String, val name: String, val biz: BizType, val revenue: Long, val orders: Int, val refund: Long)
    val revenue get() = rows.sumOf { it.revenue }
    val orders get() = rows.sumOf { it.orders }
    val refund get() = rows.sumOf { it.refund }
    /** 按业态小计（餐饮、零售） */
    val byBiz: Map<BizType, Long> get() = rows.groupBy { it.biz }.mapValues { (_, l) -> l.sumOf { it.revenue } }

    companion object {
        fun build(stores: List<StoreData>, from: Long, to: Long): MerchantReport {
            val rows = stores.map { s ->
                val r = ReportRules.build(s, from, to)
                if (s.profile.retail) { val x = RetailReportRules.build(s, from, to); Row(s.id, s.settings.shortName, s.biz, r.revenue, x.sales, x.refund) }
                else Row(s.id, s.settings.shortName, s.biz, r.revenue, r.orders, 0)
            }.sortedByDescending { it.revenue }
            val methods = stores.map { ReportRules.build(it, from, to).byMethod }
                .fold(emptyMap<String, Long>()) { acc, m -> (acc.keys + m.keys).associateWith { (acc[it] ?: 0) + (m[it] ?: 0) } }
            return MerchantReport(rows, methods.filterValues { it != 0L })
        }

        /** 本地日历日 0 点（商户汇总用自然日，各店营业日可能不同） */
        fun dayStart(now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
            java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
