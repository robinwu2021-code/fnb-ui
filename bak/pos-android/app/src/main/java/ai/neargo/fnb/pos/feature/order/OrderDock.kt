package ai.neargo.fnb.pos.feature.order

import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.EmptyState
import ai.neargo.fnb.pos.core.designsystem.component.GroupTitle
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosIconButton
import ai.neargo.fnb.pos.core.designsystem.component.TableStateLabel
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.designsystem.theme.tone
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderLine
import ai.neargo.fnb.pos.core.model.TableStatus
import ai.neargo.fnb.pos.core.designsystem.component.PosCheckRow
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosDivider
import ai.neargo.fnb.pos.feature.common.DishNoteTags
import ai.neargo.fnb.pos.feature.common.NoteDialog
import ai.neargo.fnb.pos.feature.common.OrderNoteTags
import ai.neargo.fnb.pos.feature.common.TablePickDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import ai.neargo.fnb.pos.core.designsystem.component.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** 账单坞的界面状态：弹窗、备注中的行、混合模式下选中的行。提到点餐页，竖条也能打开同样的弹窗 */
class DockUi {
    var dialog by mutableStateOf<String?>(null)
    var noteLine by mutableStateOf<OrderLine?>(null)
    var selected by mutableStateOf<Long?>(null)
    /** 选中行：选过的还在就用它，否则最近加的一行 */
    fun selectedLine(o: ai.neargo.fnb.pos.core.model.TableOrder) = o.lines.firstOrNull { it.uid == selected } ?: o.lines.maxByOrNull { it.uid }
}

/**
 * OrderDock：桌台身份 → 快捷入口 → 按状态分组的菜品清单（优先占空间）→ 金额与主次操作。
 * 结账为唯一实心主操作，送厨为次操作（主题浅底）。
 */
@Composable
fun OrderDock(state: OrderUiState, onAction: (OrderAction) -> Unit, onCheckout: () -> Unit, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null,
              ui: DockUi = remember(state.table?.id) { DockUi() }, hybrid: Boolean = false) {
    val c = Pos.colors
    val t = Pos.type
    val tb = state.table ?: return
    val totals = state.totals
    var detail by rememberSaveable { mutableStateOf(false) }
    var dialog by ui::dialog
    var noteLine by ui::noteLine
    var more by remember { mutableStateOf(false) }
    DockDialogs(state, dialog, { dialog = null }, onAction)
    noteLine?.let { l -> NoteDialog("${l.name} 备注", l.note, DishNoteTags, { noteLine = null }) { n, done -> onAction(OrderAction.LineNote(l.uid, n, done)) } }
    val sel = if (hybrid) ui.selectedLine(state.order)?.uid else null
    val pick: (OrderLine) -> (() -> Unit)? = { l -> if (hybrid) ({ ui.selected = l.uid }) else null }

    Panel(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tb.id, style = t.title.num(), color = c.ink)
                    Spacer(Modifier.width(8.dp))
                    TableStateLabel(tb.status.label, tb.status.tone())
                    Spacer(Modifier.weight(1f))
                    PosButton("加菜", { onClose?.invoke() }, variant = BtnVariant.Text, size = BtnSize.Small)
                    if (onClose != null) PosIconButton(Icons.Rounded.Close, "关闭账单", onClose, size = 36.dp)
                    // 混合模式：低频操作在竖条底「更多」，这里不重复
                    if (!hybrid) Box {
                        PosIconButton(Icons.Rounded.MoreHoriz, "更多操作", { more = true }, size = 36.dp)
                        DropdownMenu(more, { more = false }, containerColor = c.surface, shadowElevation = 2.dp) {
                            val item: @Composable (String, Boolean, () -> Unit) -> Unit = { label, enabled, act ->
                                DropdownMenuItem({ Text(label, style = t.body) }, { more = false; act() }, enabled = enabled)
                            }
                            item("主管授权 · 退赠折扣", state.canOrder) { dialog = "adjust" }
                            item("换桌", state.canOrder) { dialog = "change" }
                            item("并台", state.canOrder) { dialog = "merge" }
                            item("拆单", state.canOrder && state.order.lines.size > 1) { dialog = "split" }
                            item("整单备注", state.canOrder) { dialog = "note" }
                            item("打印预结单", state.order.lines.isNotEmpty()) { onAction(OrderAction.PreBill) }
                            item("取单（${state.held.size}）", state.held.isNotEmpty()) { dialog = "retrieve" }
                        }
                    }
                }
                Text("${tb.guests} 人 · 堂食 · 单号 #30${tb.id}", style = t.label.num(), color = c.sub)
                if (state.order.note.isNotBlank()) Text("整单备注：${state.order.note}", style = t.label, color = c.warning, maxLines = 2)
            }
            // 混合模式：整单操作做成账单头一行小按钮（竖条只放选中行的操作）
            if (hybrid) ai.neargo.fnb.pos.feature.common.OrderIcons(listOf(
                ai.neargo.fnb.pos.feature.common.RailItem("order.hold", "挂单", state.canOrder && state.order.pending.isNotEmpty()) { onAction(OrderAction.Hold) },
                ai.neargo.fnb.pos.feature.common.RailItem("order.retrieve", if (state.held.isEmpty()) "取单" else "取单 ${state.held.size}", state.canOrder && state.held.isNotEmpty()) { dialog = "retrieve" },
                ai.neargo.fnb.pos.feature.common.RailItem("order.clear", "清空", state.order.pending.isNotEmpty()) { onAction(OrderAction.ClearNew) },
                ai.neargo.fnb.pos.feature.common.RailItem("order.note", "备注", state.canOrder) { dialog = "note" },
                ai.neargo.fnb.pos.feature.common.RailItem("order.member", if (state.order.memberName.isNotBlank()) state.order.memberName else "会员", state.canOrder) { dialog = "member" },
                ai.neargo.fnb.pos.feature.common.RailItem("order.promo", "优惠", state.canOrder) { dialog = "promo" },
            )) else Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val m = Modifier.weight(1f)
                PosButton(if (state.order.memberName.isNotBlank()) state.order.memberName else "会员", { dialog = "member" }, m, size = BtnSize.Small, selected = state.order.member, textStyle = t.label, enabled = state.canOrder)
                PosButton(if (state.order.promotions.isNotEmpty()) "优惠 ${state.order.promotions.size}" else "优惠", { dialog = "promo" }, m, size = BtnSize.Small, textStyle = t.label, selected = state.order.promotions.isNotEmpty() || state.order.coupon, enabled = state.canOrder)
                PosButton("备注", { dialog = "note" }, m, size = BtnSize.Small, textStyle = t.label, enabled = state.canOrder, selected = state.order.note.isNotBlank())
                PosButton(if (state.order.pending.isEmpty() && state.held.isNotEmpty()) "取单 ${state.held.size}" else "挂单",
                    { if (state.order.pending.isEmpty() && state.held.isNotEmpty()) dialog = "retrieve" else onAction(OrderAction.Hold) }, m, size = BtnSize.Small, textStyle = t.label, enabled = state.canOrder)
            }

            state.cloudNote?.let { n -> CloudNoteRow(n) }

            // 新加 / 待同步分组插在顶部；数量变化时回到顶部，避免新行出现在可视区之外
            val list = rememberLazyListState()
            LaunchedEffect(state.order.pending.size, state.order.queued.size) { list.animateScrollToItem(0) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list) {
                val pending = state.order.pending
                val queued = state.order.queued
                val sent = state.order.sent.filter { it.status != LineStatus.Queued }
                if (pending.isNotEmpty()) {
                    item(key = "g-new") { if (hybrid) GroupTitle("新加 · 待送厨 · ${pending.sumOf { it.qty }} 份") else GroupTitle("新加 · 待送厨 · ${pending.sumOf { it.qty }} 份", action = "清空") { onAction(OrderAction.ClearNew) } }
                    items(pending, key = { it.uid }) { l -> OrderLineRow(l, { onAction(OrderAction.ChangeQty(l.uid, it)) }, null, onNote = { noteLine = l }, selected = l.uid == sel, onSelect = pick(l)) }
                }
                if (queued.isNotEmpty()) {
                    item(key = "g-queued") { GroupTitle("待同步 · ${queued.sumOf { it.qty }} 份 · ${if (state.online) "正在补发" else "联网后自动送厨"}") }
                    items(queued, key = { it.uid }) { l -> OrderLineRow(l, {}, null, selected = l.uid == sel, onSelect = pick(l)) }
                }
                if (sent.isNotEmpty()) {
                    item(key = "g-sent") { if (hybrid) GroupTitle("已下单 · ${sent.sumOf { it.qty }} 份") else GroupTitle("已下单 · ${sent.sumOf { it.qty }} 份", action = "批量") { dialog = "batch" } }
                    items(sent, key = { it.uid }) { l -> OrderLineRow(l, {}, { to -> onAction(OrderAction.Advance(l.uid, to)) }, selected = l.uid == sel, onSelect = pick(l)) }
                }
                if (state.order.lines.isEmpty()) {
                    item { EmptyState(if (state.canOrder) "还没有点菜。点菜品卡片加入" else "${tb.id} ${tb.status.label}，请先在桌台开台") }
                }
            }

            Column(Modifier.fillMaxWidth().background(c.control).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "金额明细") { detail = !detail }) {
                        Text("应收 · 明细 ${if (detail) "−" else "+"}", style = t.label, color = c.sub)
                        Text("${totals.items} 份", style = t.label.num(), color = c.sub)
                    }
                    Text(yuan(totals.due), style = t.amount.num(), color = c.ink, maxLines = 1, softWrap = false)
                }
                if (detail) {
                    KeyValue("菜品小计", yuan(totals.sub))
                    KeyValue("服务费 5%（演示）", yuan(totals.service))
                }
                Row {
                    Text("优惠 ${yuan(-totals.discount)}", style = t.label.num(), color = c.primary, modifier = Modifier.weight(1f))
                    Text("满200减10", style = t.label, color = c.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosButton(
                        if (totals.newItems > 0) "送厨 · ${totals.newItems}" else "送厨", { onAction(OrderAction.SendToKitchen) },
                        Modifier.weight(1f), variant = BtnVariant.Soft, size = BtnSize.Large, enabled = totals.newItems > 0,
                    )
                    PosButton("结账", onCheckout, Modifier.weight(1.2f), variant = BtnVariant.Primary, size = BtnSize.Large, enabled = totals.items > 0)
                }
            }
        }
    }
}

/** 账单坞弹窗：整单备注、换桌、并台、拆单、取单。 */
@Composable
internal fun DockDialogs(state: OrderUiState, dialog: String?, close: () -> Unit, onAction: (OrderAction) -> Unit) {
    val tb = state.table ?: return
    val store = ai.neargo.fnb.pos.app.appContainer().config.current.value
    when (dialog) {
        "member" -> if (store != null) ai.neargo.fnb.pos.feature.common.MemberPickDialog(store, tb.id, state.order, close)
        "promo" -> if (store != null) ai.neargo.fnb.pos.feature.common.PromotionPickDialog(store, tb.id, state.order, close)
        "batch" -> {
            var selected by remember { mutableStateOf(setOf<Long>()) }
            val lines = state.order.lines.filter { it.status == LineStatus.Sent || it.status == LineStatus.Rushing }
            PosDialog("批量菜品操作", close, maxWidth = 560.dp, footer = {
                PosButton("取消", close)
                PosButton("催菜", { onAction(OrderAction.BatchAdvance(selected, LineStatus.Rushing)); close() }, enabled = selected.isNotEmpty())
                PosButton("已上菜", { onAction(OrderAction.BatchAdvance(selected, LineStatus.Served)); close() }, variant = BtnVariant.Primary, enabled = selected.isNotEmpty())
            }) {
                PosCheckRow("全选", lines.isNotEmpty() && selected.size == lines.size, { selected = if (it) lines.map { l -> l.uid }.toSet() else emptySet() })
                lines.forEach { l -> PosCheckRow("${l.name} ×${l.qty}", l.uid in selected, { selected = if (it) selected + l.uid else selected - l.uid }, trailing = l.status.label) }
                if (lines.isEmpty()) Text("没有可操作的菜品", style = Pos.type.body, color = Pos.colors.sub)
            }
        }
        "adjust" -> ai.neargo.fnb.pos.feature.common.AdjustmentDialog(state.order, state.audits, close) { request, email, password, done -> onAction(OrderAction.Adjust(request, email, password, done)) }
        "note" -> NoteDialog("${tb.id} 整单备注", state.order.note, OrderNoteTags, close) { n, done -> onAction(OrderAction.OrderNote(n, done)) }
        "change" -> TablePickDialog("${tb.id} 换桌", "整单移到空闲或已预订的桌台，原桌转待清台",
            state.tables.filter { it.id != tb.id && (it.status == TableStatus.Idle || it.status == TableStatus.Reserved) && it.seats >= tb.guests }, close, confirm = "换桌") { to, _, done ->
            onAction(OrderAction.Change(to, done))
        }
        "merge" -> TablePickDialog("${tb.id} 并台", "并入另一张用餐中的桌台，人数相加，原桌转待清台",
            state.tables.filter { it.id != tb.id && it.status == TableStatus.Dining }, close, confirm = "并台") { into, _, done -> onAction(OrderAction.Merge(into, done)) }
        "split" -> {
            var picked by remember { mutableStateOf(setOf<Long>()) }
            TablePickDialog("${tb.id} 拆单", "选择菜品后选目标桌；空闲桌会按人数开台", state.tables.filter { it.id != tb.id && it.status in setOf(TableStatus.Idle, TableStatus.Reserved, TableStatus.Dining) },
                close, needGuests = true, confirm = "拆到该桌", content = {
                    Text("选择要拆出的菜品（${picked.size}）", style = Pos.type.section, color = Pos.colors.ink)
                    state.order.lines.forEach { l ->
                        PosCheckRow("${l.name} · ${l.spec} ×${l.qty}", l.uid in picked, { on -> picked = if (on) picked + l.uid else picked - l.uid }, trailing = l.status.label)
                    }
                }) { to, guests, done -> onAction(OrderAction.Split(picked, to, guests, done)) }
        }
        "retrieve" -> PosDialog("取单到 ${tb.id}", close, maxWidth = 520.dp, subtitle = "挂起的菜品作为新加并入当前桌", footer = { PosButton("关闭", close, size = BtnSize.Large) }) {
            if (state.held.isEmpty()) Text("没有挂单", style = Pos.type.body, color = Pos.colors.sub)
            state.held.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("来自 ${h.tableId} · ${h.lines.sumOf { it.qty }} 份", style = Pos.type.strong, color = Pos.colors.ink)
                        Text(h.lines.joinToString("、") { "${it.name}×${it.qty}" }, style = Pos.type.label, color = Pos.colors.sub, maxLines = 2)
                    }
                    PosButton("取回", { onAction(OrderAction.Retrieve(h.id)); close() }, variant = BtnVariant.Soft, enabled = state.canOrder)
                }
                PosDivider()
            }
        }
    }
}

/** 本单云端提示：上传被拒去同步中心处理；价差说明结账时以服务端应收为准 */
@androidx.compose.runtime.Composable
private fun CloudNoteRow(n: ai.neargo.fnb.pos.feature.order.CloudNote) {
    val c = ai.neargo.fnb.pos.core.designsystem.theme.Pos.colors
    val parts = buildList {
        if (n.issues > 0) add("云端 ${n.issues} 条上传被拒，请到同步中心处理")
        if (n.diffMinor != 0L) add("服务端下单金额 ${ai.neargo.fnb.pos.core.model.yuan(n.remoteMinor)}，本机 ${ai.neargo.fnb.pos.core.model.yuan(n.localMinor)}（" +
            (if (n.diffMinor > 0) "多 " else "少 ") + ai.neargo.fnb.pos.core.model.yuan(kotlin.math.abs(n.diffMinor)) + "），联网结账以服务端为准")
    }
    if (parts.isEmpty()) return
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)
        .background((if (n.issues > 0) c.danger else c.warning).copy(alpha = 0.08f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))) {
        ai.neargo.fnb.pos.core.designsystem.component.Text(parts.joinToString("；"), style = ai.neargo.fnb.pos.core.designsystem.theme.Pos.type.label,
            color = if (n.issues > 0) c.danger else c.warning, modifier = androidx.compose.ui.Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
