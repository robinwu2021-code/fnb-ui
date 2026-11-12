package ai.neargo.fnb.pos.core.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 桌台操作纯规则：换桌、并台、拆单与平面位置。失败返回可展示原因。 */
object TableRules {
    const val CANVAS_W = 900
    const val CANVAS_H = 460
    const val CARD_W = 140
    const val CARD_H = 112
    const val MAX_X = 744
    const val MAX_Y = 332

    /** 换桌：整单从用餐中的 from 移到空闲 / 预订的 to；原桌转待清台。 */
    fun canChange(from: DiningTable, to: DiningTable): String? = when {
        from.id == to.id -> "请选择另一张桌台"
        from.status != TableStatus.Dining -> "${from.id} 未在用餐"
        to.status != TableStatus.Idle && to.status != TableStatus.Reserved -> "${to.id} ${to.status.label}，不能换入"
        from.guests > to.seats -> "${to.id} 只有 ${to.seats} 座，当前 ${from.guests} 人"
        else -> null
    }

    /** 并台：from 的菜品并入同为用餐中的 into；人数相加；原桌转待清台。 */
    fun canMerge(from: DiningTable, into: DiningTable): String? = when {
        from.id == into.id -> "请选择另一张桌台"
        from.status != TableStatus.Dining || into.status != TableStatus.Dining -> "并台双方都需为用餐中"
        else -> null
    }

    fun compatible(a: TableOrder, b: TableOrder): String? =
        if (b.lines.isNotEmpty() && (a.discountPercent != b.discountPercent || a.member != b.member || a.coupon != b.coupon || a.activity != b.activity)) "双方优惠不同，请先核对优惠再并单" else null

    fun merge(a: TableOrder, b: TableOrder) = b.copy(
        lines = b.lines + a.lines, member = a.member || b.member,
        discountPercent = if (b.lines.isEmpty()) a.discountPercent else b.discountPercent,
        coupon = a.coupon || b.coupon, activity = a.activity || b.activity,
        note = listOf(b.note, a.note).filter { it.isNotBlank() }.joinToString("；"),
        mergedOrderIds = (b.mergedOrderIds + a.orderId + a.mergedOrderIds).filter { it.isNotBlank() }.distinct(),
    )

    /** 拆单：把选中行移到目标桌（空闲桌会被开台）；至少留一行、至少移一行。 */
    fun canSplit(order: TableOrder, uids: Set<Long>, to: DiningTable, guests: Int): String? = when {
        uids.isEmpty() -> "请选择要拆出的菜品"
        uids.size >= order.lines.size -> "至少保留一道菜；整单转移请用换桌"
        to.status != TableStatus.Dining && to.status != TableStatus.Idle && to.status != TableStatus.Reserved -> "${to.id} ${to.status.label}，不能拆入"
        to.status != TableStatus.Dining && guests !in 1..to.seats -> "人数应为 1–${to.seats}"
        else -> null
    }

    fun split(order: TableOrder, uids: Set<Long>): Pair<TableOrder, List<OrderLine>> =
        order.copy(lines = order.lines.filterNot { it.uid in uids }) to order.lines.filter { it.uid in uids }

    /** 平面位置：未设置时按区域内顺序 5 列排布。 */
    fun position(t: DiningTable, indexInArea: Int): Pair<Int, Int> =
        if (t.x >= 0 && t.y >= 0) t.x to t.y else (20 + (indexInArea % 5) * 172).coerceAtMost(MAX_X) to (20 + (indexInArea / 5) * 140).coerceAtMost(MAX_Y)

    fun validPosition(x: String, y: String): Result<Pair<Int, Int>> {
        val xi = x.trim().toIntOrNull(); val yi = y.trim().toIntOrNull()
        return if (xi == null || yi == null || xi !in 0..MAX_X || yi !in 0..MAX_Y) Result.failure(IllegalArgumentException("坐标范围：X 0–$MAX_X，Y 0–$MAX_Y"))
        else Result.success(xi to yi)
    }
}

/** 票据排版（等宽 32 字符左右；中文按 2 宽计算）。 */
object Receipts {
    private const val WIDTH = 32
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    private fun w(s: String) = s.sumOf { if (it.code > 0x2E80) 2 else 1 }
    private fun row(left: String, right: String): String = left + " ".repeat((WIDTH - w(left) - w(right)).coerceAtLeast(1)) + right
    private fun rule() = "-".repeat(WIDTH)

    fun kitchen(store: String, table: String, lines: List<OrderLine>, note: String, at: Long, station: String = "") = buildList {
        add("【厨房单${if (station.isNotBlank()) "·$station" else ""}】$table"); add(time.format(Date(at))); add(rule())
        lines.forEach { l -> add(row(l.name, "×${l.qty}")); if (l.spec != OrderRules.DEFAULT_SPEC) add("  ${l.spec}"); if (l.note.isNotBlank()) add("  备注：${l.note}") }
        if (note.isNotBlank()) { add(rule()); add("整单备注：$note") }
        add(rule()); add(store)
    }

    fun bill(title: String, s: StoreData, table: String, order: TableOrder, at: Long, payment: PaymentRecord? = null) = buildList {
        val t = OrderRules.totals(order)
        add(s.settings.shortName); add("【$title】桌台 $table"); add(time.format(Date(at))); add(rule())
        order.lines.forEach { l ->
            add(row(l.name + if (l.spec != OrderRules.DEFAULT_SPEC) "（${l.spec}）" else "", "${l.qty} × ${yuan(l.unit).drop(1)}"))
            if (l.gifted) add("  赠送 ${yuan(l.unit * l.qty)}")
            if (l.note.isNotBlank()) add("  备注：${l.note}")
        }
        add(rule())
        add(row("小计", yuan(t.sub))); add(row("服务费 5%", yuan(t.service)))
        if (t.discount > 0) add(row("优惠合计", yuan(-t.discount)))
        add(row("应收", yuan(t.due)))
        if (payment != null) {
            add(row("实收（${payment.method}）", yuan(payment.received)))
            if (payment.method == "现金") add(row("找零", yuan(payment.change)))
        }
        if (order.note.isNotBlank()) add("备注：${order.note}")
        add(rule()); add(s.settings.receiptFooter); add("${s.code} · ${s.address}")
    }

    fun shift(r: ShiftRecord) = buildList {
        add("【交班单】${r.staff}"); add("${time.format(Date(r.start))} — ${time.format(Date(r.end))}"); add(rule())
        add(row("退菜 ${r.voidCount} 笔", yuan(r.voidAmount))); add(row("赠送 ${r.giftCount} 笔", yuan(r.giftAmount)))
        add(row("收款笔数", "${r.count}")); if (r.refundCount > 0) add(row("退款 ${r.refundCount} 笔", yuan(-r.refund))); add(row("营业额", yuan(r.total)))
        r.byMethod.forEach { (m, v) -> add(row("  $m", yuan(v))) }
        add(rule()); add(row("备用金", yuan(r.float))); add(row("应有现金", yuan(r.cashExpected))); add(row("实盘", yuan(r.cashCounted)))
        add(row("差额", yuan(r.cashCounted - r.cashExpected)))
    }
}

/** 交班汇总：只统计本班开始后的收款。退款（零售退货、退差价）记为负数收款：金额照常抵扣，笔数单独算。 */
object ShiftRules {
    data class Summary(val count: Int, val total: Long, val byMethod: Map<String, Long>, val cash: Long, val cashExpected: Long, val tables: Int, val voidAmount: Long = 0, val voidCount: Int = 0, val giftAmount: Long = 0, val giftCount: Int = 0,
        val refundCount: Int = 0, val refund: Long = 0)

    fun summary(payments: List<PaymentRecord>, shift: ShiftState, adjustments: List<AdjustmentAudit> = emptyList()): Summary {
        val ps = payments.filter { it.at >= shift.start }
        val refunds = ps.filter { it.amount < 0 }
        val by = ps.groupBy { it.method }.mapValues { (_, v) -> Money.sum(v.map { it.amount }) }
        val cash = by["现金"] ?: 0L
        val voids = adjustments.filter { it.at >= shift.start && it.kind == AdjustmentKind.Void }
        val gifts = adjustments.filter { it.at >= shift.start && it.kind == AdjustmentKind.Gift }
        return Summary(ps.size - refunds.size, Money.sum(ps.map { it.amount }), by, cash, Math.addExact(shift.float, cash), ps.filter { it.amount >= 0 }.map { it.tableId }.distinct().size,
            Money.sum(voids.map { it.amount }), voids.size, Money.sum(gifts.map { it.amount }), gifts.size, refunds.size, -Money.sum(refunds.map { it.amount }))
    }
}


/** 结账与厨房送达（N3-02）：任一厨房票据失败或结果未知时须先处理。待出单不阻止。 */
/**
 * 一张厨房任务（同一 ticketId）的送达汇总：纸单、厨房屏、屏未收到时改打的纸单。
 * needsAction：按档口出单方式，必需渠道失败/未知/超时且未满足，拦结账。
 */
data class TicketDelivery(
    val ticketId: String, val tableId: String, val orderId: String, val station: String, val mode: DeliveryMode, val label: String,
    val paper: KitchenDelivery?, val screen: KitchenDelivery?, val fallback: KitchenDelivery?,
    val cancelled: Boolean, val needsAction: Boolean, val issue: String, val updatedAt: Long, val createdAt: Long,
) {
    /** 厨房屏未收到、可改打纸单（尚未改打或改打已失败）。 */
    val canFallback get() = needsAction && screen?.state == DeliveryState.Failed && (fallback == null || fallback.state == DeliveryState.Failed)
    val records get() = listOfNotNull(paper, screen, fallback)
}

object DeliveryRules {
    /** 厨房屏未收到的等待时限。 */
    const val SCREEN_TIMEOUT_MS = 60_000L

    private fun ok(d: KitchenDelivery?) = d != null && (d.state == DeliveryState.Delivered || d.state == DeliveryState.ManualConfirmed)
    private fun bad(d: KitchenDelivery?) = d != null && (d.state == DeliveryState.Failed || d.state == DeliveryState.Unknown)

    fun modeOf(s: StoreData, station: String) = s.stationDelivery[station.ifBlank { s.kdsStations.firstOrNull().orEmpty() }] ?: DeliveryMode.Paper

    fun groups(s: StoreData): List<TicketDelivery> {
        val cancelled = s.kdsTickets.filter { it.state == KdsState.Cancelled }.map { it.id }.toSet()
        return s.kitchenDeliveries.groupBy { it.ticketId }.map { (id, rs) ->
            val live = rs.filter { it.supersededBy.isBlank() }.sortedBy { it.createdAt }
            // 已被补打接替的纸单不再参与判定（旧版补打记录另成一组，由补打自身追踪）
            val paper = live.lastOrNull { it.channel == "paper" && it.covers.isBlank() }
            val screen = rs.lastOrNull { it.channel == "screen" }
            val fallback = live.lastOrNull { it.covers == "screen" }
            val first = rs.minBy { it.createdAt }
            val station = (screen ?: paper ?: first).station
            if (live.isEmpty()) return@map TicketDelivery(id, first.tableId, first.orderId, station, if (screen == null) DeliveryMode.Paper else modeOf(s, station), first.label, null, null, null,
                id in cancelled, false, "", rs.maxOf { it.updatedAt }, first.createdAt)
            // 送厨时没有生成厨房屏记录 = 当时为「仅纸单」（或旧数据），按发出时的方式判定
            val mode = if (screen == null) DeliveryMode.Paper else modeOf(s, station)
            // 非「仅纸单」时，模拟打印出的纸单不算真实送达（没有真打印机）
            val realPaper = paper?.takeUnless { mode != DeliveryMode.Paper && it.source == "simulation" && it.state == DeliveryState.Delivered }
            val paperOk = ok(realPaper); val paperBad = bad(realPaper)
            val screenOk = ok(screen) || ok(fallback)
            val screenBad = screen != null && !screenOk && (screen.state == DeliveryState.Failed)
            val isCancelled = id in cancelled
            val needs = !isCancelled && when (mode) {
                DeliveryMode.Paper -> paperBad
                DeliveryMode.Screen -> screenBad
                DeliveryMode.Both -> paperBad || screenBad
                DeliveryMode.Either -> (realPaper != null || screen != null) && !paperOk && !screenOk &&
                    (realPaper == null || paperBad) && (screen == null || screenBad)
            }
            val issue = buildList {
                if (paperBad) add("纸单${realPaper!!.state.label}")
                if (screenBad) add(if (fallback != null && !ok(fallback)) "厨房屏未收到，改打纸单${fallback.state.label}" else "厨房屏未收到")
            }.joinToString("，").let { if (it.isNotBlank() && !needs) "仅提示：$it" else it }
            TicketDelivery(id, first.tableId, first.orderId, station, mode, (paper ?: screen ?: first).label, paper, screen, fallback, isCancelled, needs, issue,
                rs.maxOf { it.updatedAt }, first.createdAt)
        }.sortedByDescending { it.createdAt }
    }

    fun pendingAction(s: StoreData, tableId: String): List<TicketDelivery> {
        val orderId = s.orders[tableId]?.orderId.orEmpty()
        return groups(s).filter { it.needsAction && (if (orderId.isNotBlank()) it.orderId == orderId else it.tableId == tableId) }
    }
    fun checkoutBlock(s: StoreData, tableId: String): String? =
        pendingAction(s, tableId).takeIf { it.isNotEmpty() }?.let { g ->
            val first = g.first()
            "有 ${g.size} 张厨房单未确认送达（${listOf(first.tableId, first.station).filter { it.isNotBlank() }.joinToString(" ")}：${first.issue}），请在同步中心处理后再结账"
        }
}
