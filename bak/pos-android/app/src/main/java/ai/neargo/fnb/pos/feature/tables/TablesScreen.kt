package ai.neargo.fnb.pos.feature.tables

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.DetailBlock
import ai.neargo.fnb.pos.core.designsystem.component.EmptyState
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.railWidth
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosSheet
import ai.neargo.fnb.pos.core.designsystem.component.QuantityStepper
import ai.neargo.fnb.pos.core.designsystem.component.RailItem
import ai.neargo.fnb.pos.core.designsystem.component.SearchField
import ai.neargo.fnb.pos.core.designsystem.component.TableStateLabel
import ai.neargo.fnb.pos.core.designsystem.component.Workbench
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.PosSize
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.designsystem.theme.tone
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.TableStatus
import ai.neargo.fnb.pos.core.model.TableRules
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.feature.common.NoteDialog
import ai.neargo.fnb.pos.feature.common.OrderNoteTags
import ai.neargo.fnb.pos.feature.common.TablePickDialog
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import ai.neargo.fnb.pos.core.designsystem.component.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TablesRoute(onOrder: () -> Unit, onCheckout: (String) -> Unit) {
    val vm = posViewModel { c, h -> TablesViewModel(c.config, c.session, c.floor, c.orders, c.print, c.prefs, c.messages, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) {
        vm.effect.collect { e ->
            when (e) {
                TablesEffect.OpenOrder -> onOrder()
                is TablesEffect.OpenCheckout -> onCheckout(e.tableId)
            }
        }
    }
    TablesScreen(state, vm::onAction)
}

/** 桌台工作台（无状态）：区域栏 + 看板同一面板；详情在账单同侧；窄屏详情为底部 Sheet。 */
@Composable
fun TablesScreen(state: TablesUiState, onAction: (TablesAction) -> Unit) {
    val layout = LocalPosLayout.current
    var sheetOpen by remember { mutableStateOf(false) }
    var reservationsOpen by remember { mutableStateOf(false) }
    if (reservationsOpen) ReservationsDialog(state, onAction) { reservationsOpen = false }
    val board: @Composable (Modifier) -> Unit = { m -> Board(state, onAction, { onAction(TablesAction.Select(it)); if (!layout.wide) sheetOpen = true }, m, { reservationsOpen = true }) }

    if (layout.wide) {
        Workbench(state.detailSide, layout.dockW, aside = { m -> state.selected?.let { TableDetail(it, state.selectedPending, state, onAction, m) } }) { m -> board(m) }
    } else {
        board(Modifier.fillMaxSize().padding(Pos.metrics.gap))
    }

    if (sheetOpen && !layout.wide && state.selected != null) {
        PosSheet(onDismiss = { sheetOpen = false }, showHandle = false) {
            TableDetail(state.selected, state.selectedPending, state, { a -> sheetOpen = false; onAction(a) }, Modifier.fillMaxWidth().heightIn(max = 640.dp))
        }
    }
    state.opening?.let { t -> OpenTableDialog(t, onDismiss = { onAction(TablesAction.CancelOpen) }) { onAction(TablesAction.ConfirmOpen(it)) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Board(state: TablesUiState, onAction: (TablesAction) -> Unit, onSelect: (String) -> Unit, modifier: Modifier, onReservations: () -> Unit) {
    val c = Pos.colors
    val t = Pos.type
    val pad = Pos.metrics.pad
    val layout = LocalPosLayout.current
    Panel(modifier) {
        Row(Modifier.fillMaxSize()) {
            if (layout.wide) {
                LazyColumn(Modifier.width(railWidth()).fillMaxHeight().background(c.tile), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
                    items(state.areas, key = { it.first }) { (id, name) -> RailItem(name, state.areaCounts[id], id == state.area, { onAction(TablesAction.SelectArea(id)) }) }
                }
                androidx.compose.material3.VerticalDivider(color = c.divider)
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (!layout.wide) {
                    Row(
                        Modifier.fillMaxWidth().background(c.tile).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) { state.areas.forEach { (id, name) -> PosChip(name, id == state.area, { onAction(TablesAction.SelectArea(id)) }, count = state.areaCounts[id], height = 36.dp) } }
                }
                Row(Modifier.padding(start = pad, end = pad, top = pad, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("桌台", style = t.title, color = c.ink)
                        Text("堂食工作台 · 本机保存", style = t.label, color = c.sub)
                    }
                    PosButton("预订管理", onReservations, size = BtnSize.Small)
                    SearchField(state.query, { onAction(TablesAction.Search(it)) }, "搜索桌号", Modifier.width(if (layout.wide) 220.dp else 140.dp), height = PosSize.small)
                }
                FlowRow(Modifier.padding(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PosChip("全部", state.status == null, { onAction(TablesAction.SelectStatus(null)) }, count = state.statusCounts[null], height = PosSize.small)
                    TableStatus.entries.forEach { s ->
                        PosChip(
                            s.label, state.status == s, { onAction(TablesAction.SelectStatus(if (state.status == s) null else s)) },
                            count = state.statusCounts[s], height = PosSize.small,
                            leading = { Box(Modifier.size(6.dp).clip(CircleShape).background(s.tone().dot)) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).background(c.tile).padding(horizontal = pad, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TableView.entries.forEach { v ->
                        PosButton(v.label, { onAction(TablesAction.SetView(v)) }, size = BtnSize.Small, selected = state.view == v,
                            variant = if (state.view == v) BtnVariant.Secondary else BtnVariant.Text, textStyle = t.label)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${state.cards.size} 张桌台", style = t.label.num(), color = c.sub)
                }
                if (state.view == TableView.Floor) {
                    FloorPlan(state, onSelect, Modifier.fillMaxWidth().weight(1f).background(c.tile))
                } else LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (layout.width < 1100.dp) 150.dp else 164.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f).background(c.tile),
                    contentPadding = PaddingValues(pad),
                    horizontalArrangement = Arrangement.spacedBy(Pos.metrics.gap),
                    verticalArrangement = Arrangement.spacedBy(Pos.metrics.gap),
                ) {
                    items(state.cards, key = { it.table.id }) { item -> TableCard(item, item.table.id == state.selected?.table?.id) { onSelect(item.table.id) } }
                    if (state.cards.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("没有符合条件的桌台", action = "重置筛选") { onAction(TablesAction.ResetFilters) } }
                }
            }
        }
    }
}

/**
 * TableCard：M3 Card，五种整卡背景，扁平无阴影，禁止顶部色条。
 * 桌号第一层级；选中保留状态背景并加状态色 2dp 外环。
 */
@Composable
private fun TableCard(item: TableCardItem, selected: Boolean, onClick: () -> Unit) {
    val c = Pos.colors
    val t = Pos.type
    val tb = item.table
    val tone = tb.status.tone()
    Card(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 136.dp).semantics { this.selected = selected; contentDescription = "${tb.id} ${tb.status.label}" },
        shape = Pos.metrics.card,
        colors = CardDefaults.cardColors(containerColor = tone.bg, contentColor = c.ink),
        elevation = CardDefaults.cardElevation(0.dp),
        // 扁平：不用阴影或灰框；网格铺轻底，白色空闲卡靠色块区分。选中加状态色 2dp 外环
        border = if (selected) BorderStroke(2.dp, tone.ink) else null,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tb.id, style = t.tableNo.num(), color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
                TableStateLabel(tb.status.label, tone)
            }
            Text("${item.areaName} · ${tb.seats} 座", style = t.label, color = c.sub, maxLines = 1)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val mid = t.body.copy(fontWeight = FontWeight.Medium)
                when (tb.status) {
                    TableStatus.Dining -> {
                        Text("${tb.guests} 人", style = mid.num(), color = c.ink, modifier = Modifier.weight(1f))
                        Text("${item.minutes} 分钟", style = t.label.num(), color = c.sub)
                    }
                    TableStatus.Reserved -> {
                        Text(tb.reservation, style = mid.num(), color = c.ink)
                        Spacer(Modifier.width(8.dp))
                        Text("待到店", style = t.label, color = c.sub, maxLines = 1)
                    }
                    TableStatus.Idle -> Text("可开台", style = mid, color = c.ink)
                    TableStatus.Paid -> Text("已完成收款", style = mid, color = c.ink)
                    TableStatus.Cleaning -> Text("等待清洁", style = mid, color = c.ink)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (tb.status) { TableStatus.Dining -> "${item.totals.items} 份菜品"; TableStatus.Reserved -> "已留位"; else -> "堂食" },
                    style = t.label.num(), color = c.sub, modifier = Modifier.weight(1f),
                )
                Text(
                    when (tb.status) { TableStatus.Dining -> yuan(item.totals.due); TableStatus.Paid -> yuan(tb.paid); else -> "—" },
                    style = t.body.num().copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

/** 桌台详情：身份头（主题浅底色块）→ 信息 / 账单 / 快捷入口（内部滚动）→ 固定操作区。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableDetail(item: TableCardItem, pending: Int, state: TablesUiState, onAction: (TablesAction) -> Unit, modifier: Modifier) {
    var dialog by remember(item.table.id) { mutableStateOf<String?>(null) }
    val c = Pos.colors
    val t = Pos.type
    val tb = item.table
    val dining = tb.status == TableStatus.Dining
    val totals = item.totals
    when (dialog) {
        "note" -> NoteDialog("${tb.id} 整单备注", item.note, OrderNoteTags, { dialog = null }) { n, done -> onAction(TablesAction.SaveNote(tb.id, n, done)) }
        "change" -> TablePickDialog("${tb.id} 换桌", "整单移到空闲或已预订的桌台，原桌转待清台", state.all.filter { it.id != tb.id && (it.status == TableStatus.Idle || it.status == TableStatus.Reserved) && it.seats >= tb.guests },
            { dialog = null }, confirm = "换桌") { to, _, done -> onAction(TablesAction.Change(tb.id, to, done)) }
        "merge" -> TablePickDialog("${tb.id} 并台", "并入另一张用餐中的桌台，人数相加，原桌转待清台", state.all.filter { it.id != tb.id && it.status == TableStatus.Dining },
            { dialog = null }, confirm = "并台") { into, _, done -> onAction(TablesAction.Merge(tb.id, into, done)) }
        "pos" -> PositionDialog(item, { dialog = null }) { x, y, done -> onAction(TablesAction.SavePosition(tb.id, x, y, done)) }
    }
    Panel(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(lerp(c.surface, c.tint, 0.5f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${item.areaName} · ${tb.seats} 座", style = t.label, color = c.sub)
                    Text(tb.id, style = t.hero.num(), color = c.ink)
                }
                TableStateLabel(tb.status.label, tb.status.tone())
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                DetailBlock("桌台信息") {
                    KeyValue("用餐人数", if (dining) "${tb.guests}" else "—")
                    KeyValue("用餐时长", if (dining) "${item.minutes} 分钟" else "—")
                    if (tb.reservation.isNotEmpty()) KeyValue("预订时间", tb.reservation)
                }
                DetailBlock("当前账单") {
                    KeyValue("菜品数量", "${totals.items}")
                    KeyValue("新加 / 待送厨", "$pending")
                    KeyValue("优惠合计", yuan(-totals.discount))
                    if (item.note.isNotBlank()) KeyValue("整单备注", item.note)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(Pos.metrics.card).background(c.tile).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val paid = tb.status == TableStatus.Paid
                        Text(if (paid) "已结账（演示）" else "待结账", style = t.label, color = c.ink, modifier = Modifier.weight(1f))
                        Text(yuan(if (paid) tb.paid else totals.due), style = t.amountM.num(), color = c.ink)
                    }
                }
                DetailBlock("服务快捷入口", divider = false) {
                    FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val m = Modifier.weight(1f)
                        PosButton("打印预结单", { onAction(TablesAction.PreBill(tb.id)) }, m, enabled = dining && totals.items > 0, textStyle = t.label)
                        PosButton("整单备注", { dialog = "note" }, m, enabled = dining, textStyle = t.label)
                        PosButton("换桌", { dialog = "change" }, m, enabled = dining, textStyle = t.label)
                        PosButton("并台", { dialog = "merge" }, m, enabled = dining, textStyle = t.label)
                        if (state.view == TableView.Floor) PosButton("调整位置", { dialog = "pos" }, m, textStyle = t.label)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(c.control).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val m = Modifier.weight(1f)
                when (tb.status) {
                    TableStatus.Dining -> {
                        PosButton("继续点餐", { onAction(TablesAction.ContinueOrder(tb.id)) }, m, variant = BtnVariant.Soft, size = BtnSize.Large)
                        PosButton("结账", { onAction(TablesAction.Checkout(tb.id)) }, m, variant = BtnVariant.Primary, size = BtnSize.Large, enabled = totals.items > 0)
                    }
                    TableStatus.Idle -> PosButton("开台点餐", { onAction(TablesAction.RequestOpen(tb.id)) }, m, variant = BtnVariant.Primary, size = BtnSize.Large)
                    TableStatus.Reserved -> PosButton("到店开台", { onAction(TablesAction.RequestOpen(tb.id)) }, m, variant = BtnVariant.Primary, size = BtnSize.Large)
                    TableStatus.Paid -> PosButton("进入清台", { onAction(TablesAction.StartCleaning(tb.id)) }, m, variant = BtnVariant.Primary, size = BtnSize.Large)
                    TableStatus.Cleaning -> PosButton("清台完成", { onAction(TablesAction.FinishCleaning(tb.id)) }, m, variant = BtnVariant.Primary, size = BtnSize.Large)
                }
            }
        }
    }
}

/** 开台：人数 1 至座位数。 */
@Composable
private fun OpenTableDialog(tb: DiningTable, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var n by remember(tb.id) { mutableIntStateOf(minOf(2, tb.seats)) }
    PosDialog(
        "开台 ${tb.id}", onDismiss, maxWidth = 420.dp,
        subtitle = "${tb.seats} 座${if (tb.reservation.isNotEmpty()) " · 预订 ${tb.reservation}" else ""}",
        footer = {
            PosButton("取消", onDismiss, size = BtnSize.Large)
            PosButton("开台点餐", { onConfirm(n) }, variant = BtnVariant.Primary, size = BtnSize.Large)
        },
    ) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("用餐人数", style = Pos.type.body, color = Pos.colors.ink, modifier = Modifier.weight(1f))
            QuantityStepper(n, { n = (n + it).coerceIn(1, tb.seats) }, min = 1, max = tb.seats)
        }
    }
}

/** 平面位置：按区域分组的画布（900×460，左右滑动查看）；卡片 140×112，按坐标摆放。 */
@Composable
private fun FloorPlan(state: TablesUiState, onSelect: (String) -> Unit, modifier: Modifier) {
    val c = Pos.colors
    val t = Pos.type
    LazyColumn(modifier, contentPadding = PaddingValues(Pos.metrics.pad), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(state.floorAreas, key = { it.first }) { (area, cards) ->
            Column {
                Text("$area · ${cards.size} 张", style = t.section, color = c.ink)
                Text("示意位置 · 左右滑动查看 · 选中桌台后可在详情中调整坐标", style = t.label, color = c.sub, modifier = Modifier.padding(bottom = 6.dp))
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Box(Modifier.size(TableRules.CANVAS_W.dp, TableRules.CANVAS_H.dp).clip(Pos.metrics.card).background(c.control)) {
                        Text("入口", style = t.label, color = c.sub, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                        Text("通道", style = t.label, color = c.sub, modifier = Modifier.align(Alignment.Center))
                        cards.forEach { item ->
                            val tb = item.table
                            val tone = tb.status.tone()
                            val sel = tb.id == state.selected?.table?.id
                            Card(
                                { onSelect(tb.id) },
                                Modifier.offset(item.x.dp, item.y.dp).size(TableRules.CARD_W.dp, TableRules.CARD_H.dp).semantics { selected = sel; contentDescription = "${tb.id} ${tb.status.label}" },
                                shape = Pos.metrics.card, colors = CardDefaults.cardColors(containerColor = tone.bg),
                                elevation = CardDefaults.cardElevation(0.dp),
                                border = if (sel) BorderStroke(2.dp, tone.ink) else null,
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(tb.id, style = t.section.num(), color = c.ink, modifier = Modifier.weight(1f))
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(tone.dot))
                                    }
                                    Text("${tb.status.label} · ${tb.seats} 座", style = t.label, color = tone.ink)
                                    Spacer(Modifier.weight(1f))
                                    Text(if (tb.status == TableStatus.Dining) yuan(item.totals.due) else "—", style = t.body.num(), color = c.ink)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 调整平面位置：X 0–744、Y 0–332（画布单位），越界拒绝。 */
@Composable
private fun PositionDialog(item: TableCardItem, onDismiss: () -> Unit, onSave: (String, String, (String?) -> Unit) -> Unit) {
    var x by remember { mutableStateOf(item.x.toString()) }
    var y by remember { mutableStateOf(item.y.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    PosDialog("调整 ${item.table.id} 位置", onDismiss, maxWidth = 420.dp, subtitle = "画布 ${TableRules.CANVAS_W}×${TableRules.CANVAS_H}，卡片 ${TableRules.CARD_W}×${TableRules.CARD_H}", footer = {
        PosButton("取消", onDismiss, size = BtnSize.Large)
        PosButton("保存", { onSave(x, y) { e -> error = e; if (e == null) onDismiss() } }, variant = BtnVariant.Primary, size = BtnSize.Large)
    }) {
        PosTextField(x, { x = it }, "X（0–${TableRules.MAX_X}）", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
        PosTextField(y, { y = it }, "Y（0–${TableRules.MAX_Y}）", error = error, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}
