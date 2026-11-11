package ai.neargo.fnb.pos.core.model

import java.util.Calendar

data class DishStat(val name: String, val qty: Int, val amount: Long)

/** 营业报表（按时间范围，纯计算）：收款为营业额口径；菜品/分类来自结账订单快照（旧收款无快照只计营业额）。 */
data class SalesReport(
    val from: Long, val to: Long,
    val revenue: Long, val orders: Int, val guests: Int, val discount: Long, val service: Long, val sub: Long,
    val byMethod: Map<String, Long>, val dishes: List<DishStat>, val categories: List<Pair<String, Long>>,
    val hours: Map<Int, Long>, val voidAmount: Long, val voidCount: Int, val giftAmount: Long, val giftCount: Int,
    val recharge: Long, val storedConsume: Long, val newMembers: Int, val untracked: Int,
) {
    val average get() = if (orders == 0) 0L else Money.roundHalfUp(revenue, orders.toLong())
}

object ReportRules {
    fun build(s: StoreData, from: Long, to: Long): SalesReport {
        val pays = s.payments.filter { it.at in from until to }
        val settled = s.settledOrders.filter { it.at in from until to }
        val products = s.products.associateBy { it.id }
        val categories = s.categories.associateBy { it.id }
        val lines = settled.flatMap { it.lines }.filter { !it.gifted }
        val dishes = lines.groupBy { it.name }.map { (n, ls) -> DishStat(n, ls.sumOf { it.qty }, Money.sum(ls.map { Money.line(it.unit, it.qty) })) }
            .sortedWith(compareByDescending<DishStat> { it.amount }.thenByDescending { it.qty })
        val cats = lines.groupBy { products[it.dishId]?.categoryId?.let { c -> categories[c]?.name } ?: "其他" }
            .map { (n, ls) -> n to Money.sum(ls.map { Money.line(it.unit, it.qty) }) }.sortedByDescending { it.second }
        val cal = Calendar.getInstance()
        val hours = pays.groupBy { cal.timeInMillis = it.at; cal.get(Calendar.HOUR_OF_DAY) }.mapValues { (_, v) -> Money.sum(v.map { it.amount }) }.toSortedMap()
        val adj = s.adjustments.filter { it.at in from until to }
        val voids = adj.filter { it.kind == AdjustmentKind.Void }; val gifts = adj.filter { it.kind == AdjustmentKind.Gift }
        val txns = s.memberTxns.filter { it.at in from until to }
        return SalesReport(
            from, to,
            revenue = Money.sum(pays.map { it.amount }), orders = pays.size, guests = settled.sumOf { it.guests },
            discount = Money.sum(settled.map { it.discount }), service = Money.sum(settled.map { it.service }), sub = Money.sum(settled.map { it.sub }),
            byMethod = pays.groupBy { it.method }.mapValues { (_, v) -> Money.sum(v.map { it.amount }) },
            dishes = dishes, categories = cats, hours = hours,
            voidAmount = Money.sum(voids.map { it.amount }), voidCount = voids.size, giftAmount = Money.sum(gifts.map { it.amount }), giftCount = gifts.size,
            recharge = Money.sum(txns.filter { it.kind == MemberTxnKind.Recharge }.map { it.amount }),
            storedConsume = Money.sum(txns.filter { it.kind == MemberTxnKind.Consume }.map { -it.amount }),
            newMembers = s.members.count { it.createdAt in from until to },
            untracked = pays.count { p -> settled.none { it.paymentId == p.id } },
        )
    }

    /** 当前营业日开始：日结后的时间点；首日取最早收款时间。 */
    fun dayStart(s: StoreData): Long = s.dayStart.takeIf { it > 0 } ?: (s.payments.minOfOrNull { it.at } ?: 0L)

    /** 日结前置条件。 */
    fun closeBlock(s: StoreData): String? {
        val open = s.tables.filter { t -> t.status == TableStatus.Dining && s.orders[t.id]?.lines?.isNotEmpty() == true }
        if (open.isNotEmpty()) return "还有 ${open.size} 桌未结账（${open.take(5).joinToString("、") { it.id }}），请先结账或退菜"
        val unknown = s.printJobs.count { it.state == PrintState.Unknown }
        if (unknown > 0) return "有 $unknown 张打印结果未知，请先在打印队列核对"
        return null
    }

    fun dayCloseReceipt(store: String, d: DayClose): List<String> {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
        fun row(l: String, r: String) = l + " ".repeat((32 - l.sumOf { if (it.code > 0x2E80) 2 else 1 } - r.length).coerceAtLeast(1)) + r
        return buildList {
            add("【日结单】$store"); add("${fmt.format(java.util.Date(d.start))} — ${fmt.format(java.util.Date(d.end))}"); add("-".repeat(32))
            add(row("营业额", yuan(d.revenue))); add(row("单数", "${d.orders}")); add(row("客数", "${d.guests}")); add(row("优惠", yuan(d.discount)))
            add(row("退菜", yuan(d.voidAmount))); add(row("赠送", yuan(d.giftAmount)))
            d.byMethod.forEach { (m, v) -> add(row("  $m", yuan(v))) }
            add("-".repeat(32)); add(row("会员充值", yuan(d.recharge))); add(row("新增会员", "${d.newMembers}"))
            add("-".repeat(32)); add("日结人：${d.staff}")
        }
    }
}
