package ai.neargo.fnb.pos.core.model

import kotlinx.serialization.Serializable

/** 厨房任务卡状态（厨师操作）；送达（厨房屏持久接收）单独记录，不等于接单。 */
@Serializable enum class KdsState(val label: String) {
    Pending("待接单"), Accepted("已接单"), Cooking("制作中"), Done("已完成"), Cancelled("已取消")
}

/** 任务卡菜品行：voidQty 为送厨后退菜份数（主机退菜时更新）。 */
@Serializable data class KdsLine(val uid: Long, val name: String, val spec: String, val note: String, val qty: Int, val voidQty: Int = 0) {
    val active get() = (qty - voidQty).coerceAtLeast(0)
}

/**
 * 厨房任务卡（N7）：一个送厨批次一张，主 POS 为权威。
 * rev：内容或状态每变化一次 +1（厨师操作须带所见 rev，防迟到命令覆盖取消）；
 * seq：门店内递增的变更序号，厨房屏按游标增量拉取。
 */
@Serializable data class KdsTicket(
    val id: String, val tableId: String, val orderId: String, val station: String, val lines: List<KdsLine>,
    val createdAt: Long, val rev: Int = 1, val seq: Long = 0, val state: KdsState = KdsState.Pending,
    val updatedAt: Long = 0, val by: String = "", val note: String = "",
    /** 厨房屏持久接收回执（主机侧记录）：接收时间与已接收的版本。 */
    val deliveredAt: Long = 0, val deliveredRev: Int = 0,
) {
    val activeQty get() = lines.sumOf { it.active }
}

object KdsRules {
    /** 菜品所属档口：按分类映射；未映射或档口已删除时归默认（第一个）档口。 */
    fun stationFor(s: StoreData, dishId: String): String {
        val cat = s.products.firstOrNull { it.id == dishId }?.categoryId
        return s.stationOf[cat]?.takeIf { it in s.kdsStations } ?: s.kdsStations.firstOrNull() ?: "厨房"
    }

    /**
     * 打印路由：厨房类任务先找本档口的打印机，未设置时跟随默认档口，再兼容旧的「厨房」路由；其他任务按前台等固定路由。
     * 返回打印机 id，空串表示模拟打印。
     */
    fun printRoute(routes: Map<String, String>, stations: List<String>, j: PrintJob): String = routeKey(routes, stations, j)?.let { routes[it] }.orEmpty()

    /** 任务实际使用的路由键（有主打印机的那一级）；无则 null（模拟打印）。 */
    fun routeKey(routes: Map<String, String>, stations: List<String>, j: PrintJob): String? =
        if (j.kind != PrintKind.Kitchen) j.kind.printer.takeIf { routes[it] != null }
        else listOfNotNull(j.station.ifBlank { null }, stations.firstOrNull(), "厨房").firstOrNull { routes[it] != null }

    /** 该任务所在路由的备用打印机 id；空=未设置。 */
    fun printBackup(routes: Map<String, String>, backups: Map<String, String>, stations: List<String>, j: PrintJob): String =
        routeKey(routes, stations, j)?.let { backups[it] }.orEmpty()

    /** 自动改投备用：确定未发出达到次数或持续时间。结果未知不改投。 */
    const val FAILOVER_ATTEMPTS = 2
    const val FAILOVER_MS = 30_000L

    /** 档口配置校验：1–8 个，名称 1–8 字且不重复；映射只能指向现有档口。 */
    fun validateStations(stations: List<String>, mapping: Map<String, String>): String? = when {
        stations.isEmpty() || stations.size > 8 -> "档口数量应为 1–8 个"
        stations.any { it.isBlank() || it.trim().length > 8 } -> "档口名称 1–8 字"
        stations.map { it.trim() }.toSet().size != stations.size -> "档口名称重复"
        mapping.values.any { it !in stations } -> "分类指向了不存在的档口"
        else -> null
    }

    /** 一个送厨批次中某菜品行所在的任务卡（单档口时卡号即批次号，多档口为「批次@档口」）。 */
    fun ticketOf(tickets: List<KdsTicket>, batchId: String, uid: Long): KdsTicket? =
        tickets.firstOrNull { (it.id == batchId || it.id.startsWith("$batchId@")) && it.lines.any { l -> l.uid == uid } }

    /** 厨师操作的合法流转；已取消为终态，已完成可撤回到制作中。 */
    fun transition(cur: KdsState, to: KdsState): String? = when {
        cur == KdsState.Cancelled -> "该单已取消（退菜），不能再操作"
        to == KdsState.Cancelled -> "取消只能由前台退菜产生"
        cur == to -> "状态未变化"
        to == KdsState.Accepted && cur == KdsState.Pending -> null
        to == KdsState.Cooking && cur in setOf(KdsState.Pending, KdsState.Accepted, KdsState.Done) -> null
        to == KdsState.Done && cur in setOf(KdsState.Accepted, KdsState.Cooking) -> null
        else -> "不能从「${cur.label}」改为「${to.label}」"
    }

    /** 厨房屏收件去重：只接受比本地更新的变更（seq 更大），重复或旧投递忽略。 */
    fun merge(local: List<KdsTicket>, incoming: List<KdsTicket>): Pair<List<KdsTicket>, List<KdsTicket>> {
        val map = local.associateBy { it.id }.toMutableMap()
        val accepted = mutableListOf<KdsTicket>()
        for (t in incoming.sortedBy { it.seq }) {
            val old = map[t.id]
            if (old == null || t.seq > old.seq) { map[t.id] = t; accepted += t }
        }
        return map.values.sortedBy { it.createdAt } to accepted
    }
}
