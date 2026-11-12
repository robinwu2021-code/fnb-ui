package ai.neargo.fnb.pos.feature.ops

import ai.neargo.fnb.pos.app.appContainer
import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.GroupTitle
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderLine
import ai.neargo.fnb.pos.core.model.ReportRules
import ai.neargo.fnb.pos.core.model.SalesReport
import ai.neargo.fnb.pos.core.model.TableStatus
import ai.neargo.fnb.pos.core.model.yuan
import ai.neargo.fnb.pos.feature.manage.PageHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val OVERDUE_MIN = 15

/** 出菜看板：全店已下单未上菜，按等待时长排序；一键上菜 / 催菜。 */
@Composable
fun ServeRoute() {
    val c = appContainer()
    val store by c.config.current.collectAsStateWithLifecycle()
    val s = store ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    var filter by remember { mutableStateOf("全部") }
    val scope = rememberCoroutineScope()
    data class Item(val table: String, val line: OrderLine) { fun wait(now: Long) = if (line.sentAt > 0) ((now - line.sentAt) / 60_000).toInt() else -1 }
    val all = s.tables.filter { it.status == TableStatus.Dining }.flatMap { t -> s.orders[t.id]?.lines.orEmpty().filter { it.status == LineStatus.Sent || it.status == LineStatus.Rushing }.map { Item(t.id, it) } }
        .sortedByDescending { it.wait(now) }
    val shown = when (filter) { "超时" -> all.filter { it.wait(now) >= OVERDUE_MIN }; "催菜中" -> all.filter { it.line.status == LineStatus.Rushing }; else -> all }
    val host by c.lanHost.status.collectAsStateWithLifecycle()
    fun reclaim(k: ai.neargo.fnb.pos.core.model.KdsTicket) = scope.launchStorage(c.messages) {
        c.kds.kdsReassign(k.id, null).onSuccess { c.messages.post("已收回 ${k.tableId} ${k.station} 的任务卡，其他屏可以接单") }.onFailure { c.messages.post(it.message ?: "收回失败") }
    }
    fun advance(i: Item, to: LineStatus) = scope.launchStorage(c.messages) { c.orders.advance(i.table, i.line.uid, to); c.messages.post(if (to == LineStatus.Served) "${i.table} ${i.line.name} 已上菜" else "已催菜") }
    Panel(Modifier.fillMaxSize().padding(Pos.metrics.gap)) {
        Column(Modifier.padding(Pos.metrics.pad)) {
            PageHeader("出菜", "待上菜 ${all.sumOf { it.line.qty }} 份 · 超时（≥$OVERDUE_MIN 分钟）${all.count { it.wait(now) >= OVERDUE_MIN }} 项 · 催菜中 ${all.count { it.line.status == LineStatus.Rushing }} 项") {
                listOf("全部", "超时", "催菜中").forEach { f -> PosButton(f, { filter = f }, size = BtnSize.Small, selected = filter == f) }
            }
            if (shown.isEmpty()) Text("没有待上菜的菜品", style = Pos.type.body, color = Pos.colors.sub, modifier = Modifier.padding(16.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach { i ->
                    val w = i.wait(now); val late = w >= OVERDUE_MIN
                    Row(Modifier.fillMaxWidth().background(if (late) Pos.colors.dangerBg else Pos.colors.tile).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(i.table, style = Pos.type.title, color = Pos.colors.ink, modifier = Modifier.width(72.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${i.line.name} ×${i.line.qty}", style = Pos.type.strong, color = Pos.colors.ink)
                            val extra = listOfNotNull(i.line.spec.takeIf { it != "标准做法" }, i.line.note.takeIf { it.isNotBlank() }?.let { "备注：$it" }).joinToString(" · ")
                            if (extra.isNotBlank()) Text(extra, style = Pos.type.label, color = Pos.colors.sub)
                        }
                        if (i.line.status == LineStatus.Rushing) Tag("催菜中", bg = Pos.colors.dangerBg, fg = Pos.colors.danger)
                        i.line.batchId?.let { b -> ai.neargo.fnb.pos.core.model.KdsRules.ticketOf(s.kdsTickets, b, i.line.uid) }?.let { k ->
                            val active = k.state != ai.neargo.fnb.pos.core.model.KdsState.Done && k.state != ai.neargo.fnb.pos.core.model.KdsState.Cancelled
                            // 分派模式：负责屏离线（主机未连接该设备）时提示收回
                            val isHost = !c.lanLink.isSecondary
                            val ownerOff = isHost && active && k.owner.isNotBlank() && k.owner != c.deviceId && k.owner !in host.connected
                            Tag(when {
                                k.deliveredAt == 0L -> "${k.station}屏未接收"
                                ownerOff -> "${k.station} · ${k.ownerName}离线"
                                k.owner.isNotBlank() -> "${k.station} · ${k.state.label} · ${k.ownerName}"
                                else -> "${k.station} · ${k.state.label}"
                            }, bg = if (ownerOff) Pos.colors.dangerBg else if (k.state == ai.neargo.fnb.pos.core.model.KdsState.Done) Pos.colors.tint else Pos.colors.tile,
                                fg = if (ownerOff) Pos.colors.danger else if (k.deliveredAt == 0L) Pos.colors.warning else Pos.colors.primary)
                            if (isHost && active && k.owner.isNotBlank()) PosButton("收回", { reclaim(k) }, size = BtnSize.Small, variant = BtnVariant.Text)
                        }
                        Text(if (w < 0) "—" else "$w 分钟", style = Pos.type.strong, color = if (late) Pos.colors.danger else Pos.colors.sub, modifier = Modifier.width(80.dp))
                        if (i.line.status == LineStatus.Sent) PosButton("催菜", { advance(i, LineStatus.Rushing) }, size = BtnSize.Small)
                        PosButton("上菜", { advance(i, LineStatus.Served) }, size = BtnSize.Small, variant = BtnVariant.Primary)
                    }
                }
            }
        }
    }
}

private fun time(ms: Long) = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ms))

/** 报表与日结：当前营业日 / 近 7 天 / 历史日结。 */
@Composable
fun ReportsRoute() {
    val c = appContainer()
    val store by c.config.current.collectAsStateWithLifecycle()
    val session by c.session.session.collectAsStateWithLifecycle()
    val s = store ?: return
    val scope = rememberCoroutineScope()
    var range by remember { mutableStateOf("today") }
    var closing by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val dayStart = ReportRules.dayStart(s).takeIf { it > 0 } ?: now
    val (from, to, title) = when {
        range == "today" -> Triple(dayStart, now + 1, "当前营业日（${time(dayStart)} 起）")
        range == "week" -> Triple(now - 7 * 86_400_000L, now + 1, "近 7 天")
        else -> s.dayCloses.firstOrNull { it.id == range }?.let { Triple(it.start, it.end + 1, "日结 ${time(it.start)} — ${time(it.end)}") } ?: Triple(dayStart, now + 1, "当前营业日")
    }
    val r = ReportRules.build(s, from, to)
    Panel(Modifier.fillMaxSize().padding(Pos.metrics.gap)) {
        Column(Modifier.padding(Pos.metrics.pad)) {
            PageHeader("报表与日结", title) {
                PosButton("当前营业日", { range = "today" }, size = BtnSize.Small, selected = range == "today")
                PosButton("近 7 天", { range = "week" }, size = BtnSize.Small, selected = range == "week")
                PosButton("日结", { closing = true }, size = BtnSize.Small, variant = BtnVariant.Primary)
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 零售门店：零售分区（零售单、退货、渠道、商品）+ 共用的收款方式与时段；餐饮照旧
                    if (s.biz == ai.neargo.fnb.pos.core.model.BizType.Retail) RetailReportBody(ai.neargo.fnb.pos.core.model.RetailReportRules.build(s, from, to), r) else ReportBody(r)
                }
                Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GroupTitle("历史日结（${s.dayCloses.size}）")
                    if (s.dayCloses.isEmpty()) Text("暂无日结记录", style = Pos.type.label, color = Pos.colors.sub)
                    s.dayCloses.sortedByDescending { it.end }.forEach { d ->
                        KeyValue(time(d.end), yuan(d.revenue)) { PosButton("查看", { range = d.id }, size = BtnSize.Small, selected = range == d.id) }
                    }
                }
            }
        }
    }
    if (closing) {
        val block = ReportRules.closeBlock(s)
        val today = ReportRules.build(s, dayStart, now + 1)
        PosDialog("日结", { closing = false }, subtitle = "营业日 ${time(dayStart)} 起", footer = {
            PosButton("取消", { closing = false }, size = BtnSize.Large)
            PosButton("确认日结", {
                scope.launchStorage(c.messages) {
                    c.ops.closeDay(session.staff).onSuccess { closing = false; c.messages.post("日结完成，日结单已加入打印队列，开启新营业日") }.onFailure { c.messages.post(it.message ?: "日结失败") }
                }
            }, variant = BtnVariant.Primary, size = BtnSize.Large, enabled = block == null)
        }) {
            if (block != null) Text(block, style = Pos.type.body, color = Pos.colors.danger, modifier = Modifier.padding(bottom = 8.dp))
            KeyValue("营业额", yuan(today.revenue)); KeyValue("单数 / 客数", "${today.orders} / ${today.guests}"); KeyValue("优惠", yuan(today.discount))
            today.byMethod.forEach { (m, v) -> KeyValue("  $m", yuan(v)) }
            KeyValue("会员充值", yuan(today.recharge))
            Text("日结后生成日结单，营业日重新开始；已结账数据保留，可在历史日结中查看。", style = Pos.type.label, color = Pos.colors.sub, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun RetailReportBody(x: ai.neargo.fnb.pos.core.model.RetailReport, r: SalesReport) {
    GroupTitle("零售概览")
    KeyValue("净销售（售出 − 退款）", yuan(x.net)); KeyValue("售出实收", yuan(x.gross)); KeyValue("销售单数", "${x.sales}"); KeyValue("件数", "${x.pieces} 件"); KeyValue("客单价", yuan(x.average))
    KeyValue("退款", "${x.refundCount} 笔 · ${yuan(x.refund)}" + if (x.looseRefund > 0) "（含无单 ${yuan(x.looseRefund)}）" else "")
    KeyValue("换货", "${x.exchangeCount} 笔（抵扣进了新单，不算退款）")
    GroupTitle("销售渠道")
    if (x.byChannel.isEmpty()) Text("无销售", style = Pos.type.label, color = Pos.colors.sub)
    x.byChannel.forEach { (n, v) -> Bar(n, v, x.gross) }
    GroupTitle("收款方式（退款已扣）")
    if (r.byMethod.isEmpty()) Text("无收款", style = Pos.type.label, color = Pos.colors.sub)
    r.byMethod.entries.sortedByDescending { it.value }.forEach { (m, v) -> Bar(m, v, r.revenue) }
    GroupTitle("商品销售（前 10）")
    if (x.items.isEmpty()) Text("无商品数据", style = Pos.type.label, color = Pos.colors.sub)
    x.items.take(10).forEachIndexed { i, d -> KeyValue("${i + 1}. ${d.name}", "${d.qty} 件 · ${yuan(d.amount)}") }
    GroupTitle("分类销售")
    val catTotal = x.categories.sumOf { it.second }
    x.categories.forEach { (n, v) -> Bar(n, v, catTotal) }
    GroupTitle("时段分布（收款）")
    if (r.hours.isEmpty()) Text("无数据", style = Pos.type.label, color = Pos.colors.sub)
    val peak = r.hours.values.maxOrNull() ?: 0L
    r.hours.forEach { (h, v) -> Bar("%02d:00".format(h), v, peak) }
}

@Composable
private fun ReportBody(r: SalesReport) {
    GroupTitle("营业概览")
    KeyValue("营业额（实收）", yuan(r.revenue)); KeyValue("单数", "${r.orders}"); KeyValue("客数", "${r.guests}"); KeyValue("客单价", yuan(r.average))
    KeyValue("菜品小计 / 服务费", "${yuan(r.sub)} / ${yuan(r.service)}"); KeyValue("优惠合计", yuan(r.discount))
    KeyValue("退菜", "${r.voidCount} 笔 · ${yuan(r.voidAmount)}"); KeyValue("赠送", "${r.giftCount} 笔 · ${yuan(r.giftAmount)}")
    if (r.untracked > 0) Text("其中 ${r.untracked} 笔为旧版本收款，无订单明细，只计入营业额", style = Pos.type.label, color = Pos.colors.warning)
    GroupTitle("收款方式")
    if (r.byMethod.isEmpty()) Text("无收款", style = Pos.type.label, color = Pos.colors.sub)
    r.byMethod.entries.sortedByDescending { it.value }.forEach { (m, v) -> Bar(m, v, r.revenue) }
    GroupTitle("菜品销量（前 10）")
    if (r.dishes.isEmpty()) Text("无菜品数据", style = Pos.type.label, color = Pos.colors.sub)
    r.dishes.take(10).forEachIndexed { i, d -> KeyValue("${i + 1}. ${d.name}", "${d.qty} 份 · ${yuan(d.amount)}") }
    GroupTitle("分类销售")
    val catTotal = r.categories.sumOf { it.second }
    r.categories.forEach { (n, v) -> Bar(n, v, catTotal) }
    GroupTitle("时段分布（收款）")
    if (r.hours.isEmpty()) Text("无数据", style = Pos.type.label, color = Pos.colors.sub)
    val peak = r.hours.values.maxOrNull() ?: 0L
    r.hours.forEach { (h, v) -> Bar("%02d:00".format(h), v, peak) }
    GroupTitle("会员")
    KeyValue("新增会员", "${r.newMembers} 人"); KeyValue("储值充值", yuan(r.recharge)); KeyValue("储值消费", yuan(r.storedConsume))
}

@Composable
private fun Bar(label: String, value: Long, max: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = Pos.type.label, color = Pos.colors.sub, modifier = Modifier.width(88.dp))
        Box(Modifier.weight(1f).height(12.dp).background(Pos.colors.tile)) {
            val f = if (max > 0) (value.toFloat() / max).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Pos.colors.primary))
        }
        Text(yuan(value), style = Pos.type.label, color = Pos.colors.ink, modifier = Modifier.width(110.dp))
    }
}
