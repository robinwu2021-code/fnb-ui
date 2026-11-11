package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.EmptyState
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.RailItem
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.LossReason
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.StockDoc
import ai.neargo.fnb.pos.core.model.StockDocKind
import ai.neargo.fnb.pos.core.model.StockDocLine
import ai.neargo.fnb.pos.core.model.StockDocState
import ai.neargo.fnb.pos.core.model.StockWork
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StockWorkRoute() {
    val vm = posViewModel { c, _ -> StockWorkViewModel(c.config, c.retail, c.messages) }
    val state by vm.state.collectAsStateWithLifecycle()
    StockWorkScreen(state, vm::onAction)
}

private val mmdd = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())

@Composable
private fun docTag(s: StockDocState) {
    val c = Pos.colors
    val (bg, fg) = when (s) {
        StockDocState.Draft, StockDocState.Counting -> c.control to c.ink
        StockDocState.Pending -> c.control to c.warning
        StockDocState.Posted -> c.tint to c.primary
        StockDocState.Variance -> c.dangerBg to c.danger
        StockDocState.Void -> c.control to c.sub
    }
    Tag(s.label, bg = bg, fg = fg)
}

/** 库存作业（原型 StockWB.tsx）：左作业类型 + 预警 · 中单据卡片 · 右当前单据（扫码累加 → 过账 / 提交审批） */
@Composable
fun StockWorkScreen(state: StockWorkUiState, onAction: (StockWorkAction) -> Unit) {
    val layout = LocalPosLayout.current
    val gap = Pos.metrics.gap
    val root = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(state.creating == null) { if (state.creating == null && !fieldFocused) runCatching { root.requestFocus() } }
    Row(Modifier.fillMaxSize().hidScanner(root, { fieldFocused || state.creating != null }) { onAction(StockWorkAction.Scan(it)) }.padding(gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Panel(Modifier.width(140.dp).fillMaxHeight()) { Rail(state, onAction) }
        when (state.view) {
            StockView.Transfer -> Panel(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.padding(24.dp)) {
                    Text("调拨", style = Pos.type.title)
                    Tag("未实现", fg = Pos.colors.warning)
                    EmptyState("门店之间调拨要等多门店库存同步接入后再做")
                }
            }
            StockView.Low, StockView.Near -> Panel(Modifier.weight(1f).fillMaxHeight()) { Alerts(state, onAction) }
            else -> {
                Panel(Modifier.weight(1f).fillMaxHeight()) { DocList(state, onAction) }
                Panel(Modifier.width(if (layout.wide) maxOf(layout.dockW, 420.dp) else layout.dockW).fillMaxHeight()) {
                    state.current?.let { DocPanel(state, it, onAction) { fieldFocused = it } } ?: EmptyState("还没有${state.view.label}单，点「新建」开始", Modifier.padding(16.dp))
                }
            }
        }
    }
    state.creating?.let { NewDocDialog(it, onAction) }
}

@Composable
private fun Rail(state: StockWorkUiState, onAction: (StockWorkAction) -> Unit) {
    val c = Pos.colors
    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(StockView.Receive, StockView.Count, StockView.Loss).forEach { v ->
            RailItem(v.label, state.openCount(v.kind!!).takeIf { it > 0 }, state.view == v, { onAction(StockWorkAction.View(v)) })
        }
        RailItem(StockView.Transfer.label, null, state.view == StockView.Transfer, { onAction(StockWorkAction.View(StockView.Transfer)) })
        Text("预警", style = Pos.type.label, color = c.sub, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp))
        RailItem(StockView.Low.label, state.low.size, state.view == StockView.Low, { onAction(StockWorkAction.View(StockView.Low)) })
        RailItem(StockView.Near.label, state.near.size, state.view == StockView.Near, { onAction(StockWorkAction.View(StockView.Near)) })
    }
}

@Composable
private fun DocList(state: StockWorkUiState, onAction: (StockWorkAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val k = state.view.kind!!
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${k.label}单", style = t.title, color = c.ink, modifier = Modifier.weight(1f))
            PosButton("新建${k.label}单", { onAction(StockWorkAction.AskNew) }, variant = BtnVariant.Soft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DocFilter.entries.forEach { f ->
                val n = state.docs.count { it.kind == k && when (f) { DocFilter.All -> true; DocFilter.Open -> it.open; DocFilter.Done -> !it.open } }
                PosChip(f.label, state.filter == f, { onAction(StockWorkAction.Filter(f)) }, count = n)
            }
        }
        if (state.shown.isEmpty()) EmptyState("没有单据")
        LazyVerticalGrid(GridCells.Adaptive(210.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.shown, key = { it.no }) { d ->
                val sel = state.current?.no == d.no
                Column(Modifier.fillMaxWidth().heightIn(min = 112.dp).clip(Pos.metrics.card).background(if (sel) c.tint else c.tile)
                    .clickable { onAction(StockWorkAction.Select(d.no)) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(d.no, style = t.strong.num(), color = c.ink, modifier = Modifier.weight(1f)); docTag(d.state) }
                    Text(listOfNotNull(d.supplier.ifBlank { null }, d.scope.ifBlank { null }?.let { "范围 · $it" }, d.ref.ifBlank { null }?.let { "关联 $it" }).joinToString(" · ").ifBlank { " " },
                        style = t.label, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${d.actor} · ${mmdd.format(java.util.Date(d.at))}", style = t.label.num(), color = c.sub)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text("${d.lines.size} 种商品", style = t.label, color = c.ink, modifier = Modifier.weight(1f))
                        if (d.kind != StockDocKind.Count) Text(yuan(StockWork.docCost(state.products, d)), style = t.strong.num(), color = c.ink)
                        else if (d.snapshot.isNotEmpty()) Text("盘盈 ${d.gain} · 盘亏 ${d.loss}", style = t.label.num(), color = if (d.loss > 0) c.danger else c.ink)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocPanel(state: StockWorkUiState, d: StockDoc, onAction: (StockWorkAction) -> Unit, onFocus: (Boolean) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    var text by remember(d.no) { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(d.no, style = t.title.num(), color = c.ink, modifier = Modifier.weight(1f)); docTag(d.state) }
            Text(listOfNotNull(d.supplier.ifBlank { null }?.let { "供应商 · $it" }, d.scope.ifBlank { null }?.let { "范围 · $it" }, d.ref.ifBlank { null }?.let { "关联 $it" }, "${d.actor} · ${mmdd.format(java.util.Date(d.at))}").joinToString(" · "),
                style = t.label.num(), color = c.sub)
            if (d.editable) Row(Modifier.fillMaxWidth().height(44.dp).clip(Pos.metrics.button).background(c.tile).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.QrCodeScanner, null, tint = c.sub, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (text.isEmpty()) Text(if (d.kind == StockDocKind.Count) "扫码数一件加一件" else "扫码加商品，回车", style = t.body, color = c.sub, maxLines = 1)
                    BasicTextField(text, { text = it }, singleLine = true, textStyle = t.body.copy(color = c.ink), cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAction(StockWorkAction.Scan(text)); text = "" }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) }.semantics { contentDescription = "单据扫码框" })
                }
            }
            val sug = state.suggest(d)
            if (sug.isNotEmpty()) {
                Text(when (d.kind) { StockDocKind.Receive -> "低库存，建议补货"; StockDocKind.Loss -> "临期商品"; else -> "还没盘的商品" }, style = t.label, color = c.sub)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sug.forEach { p -> PosButton("+ ${p.name}", { onAction(StockWorkAction.Add(p.id)) }, size = BtnSize.Small) }
                }
            }
        }
        if (d.lines.isEmpty()) EmptyState("扫码或点上面的建议加商品", Modifier.padding(16.dp).weight(1f))
        else LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(d.lines, key = { it.productId + "#" + it.sku }) { l -> DocLineRow(state, d, l, onAction) }
        }
        DocFoot(state, d, onAction)
    }
}

@Composable
private fun DocLineRow(state: StockWorkUiState, d: StockDoc, l: StockDocLine, onAction: (StockWorkAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val p: Product = state.product(l.productId) ?: return
    val skuLabel = l.sku?.let { k -> p.skus.firstOrNull { it.key == k }?.label }
    val key = l.productId + (l.sku?.let { "#$it" } ?: "")
    val book = if (d.kind == StockDocKind.Count && d.snapshot.isNotEmpty()) d.snapshot[key] ?: 0 else StockWork.book(p, l.sku)
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(Pos.metrics.card).background(c.surface).padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name + (skuLabel?.let { " · $it" } ?: ""), style = t.line, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = when (d.kind) {
                    StockDocKind.Receive -> listOfNotNull("进价 ${yuan(StockWork.cost(p))}${if (StockWork.weighed(p)) "/kg" else ""}", "账面 ${StockWork.qtyText(p, book)}", if (StockWork.low(p)) "低库存" else null)
                    StockDocKind.Count -> listOfNotNull("账面 ${StockWork.qtyText(p, book)}", (l.qty - book).takeIf { it != 0L && d.state != StockDocState.Posted && d.state != StockDocState.Variance }?.let { "差异 ${if (it > 0) "+" else ""}${if (StockWork.weighed(p)) StockWork.qtyText(p, it) else it}" })
                    StockDocKind.Loss -> listOfNotNull("账面 ${StockWork.qtyText(p, book)}", if (!d.editable) l.reason?.label ?: "破损" else null)
                }
                Text(meta.joinToString(" · "), style = t.label.num(), color = if (d.kind == StockDocKind.Count && l.qty < book) c.danger else c.sub)
            }
            if (d.editable) Row(verticalAlignment = Alignment.CenterVertically) {
                PosButton("−", { onAction(StockWorkAction.Step(l, -1)) }, size = BtnSize.Small)
                Text(StockWork.qtyText(p, l.qty), style = t.strong.num(), color = c.ink, modifier = Modifier.padding(horizontal = 8.dp))
                PosButton("+", { onAction(StockWorkAction.Step(l, 1)) }, size = BtnSize.Small)
            } else Text(StockWork.qtyText(p, l.qty), style = t.strong.num(), color = c.ink)
            Text(if (d.kind == StockDocKind.Count) "" else yuan(StockWork.lineCost(p, l.qty)), style = t.strong.num(), color = c.ink, modifier = Modifier.width(72.dp).padding(start = 8.dp))
        }
        if (d.kind == StockDocKind.Loss && d.editable) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(LossReason.Expired, LossReason.Damaged).forEach { r -> PosChip(r.label, (l.reason ?: LossReason.Damaged) == r, { onAction(StockWorkAction.Reason(l, r)) }) }
        }
    }
}

@Composable
private fun DocFoot(state: StockWorkUiState, d: StockDoc, onAction: (StockWorkAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    Column(Modifier.fillMaxWidth().background(c.control).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (d.kind == StockDocKind.Count) {
                val diff = StockWork.countDiff(state.products, d)
                Text("盘点差异 · ${d.lines.size} 种", style = t.label, color = c.sub, modifier = Modifier.weight(1f))
                Text(if (diff.gain == 0L && diff.loss == 0L) "无差异" else "盘盈 ${diff.gain} · 盘亏 ${diff.loss}", style = t.amountM.num(), color = if (diff.loss > 0) c.danger else c.ink)
            } else {
                Text("${d.kind.label}成本 · ${d.lines.size} 种", style = t.label, color = c.sub, modifier = Modifier.weight(1f))
                Text(yuan(StockWork.docCost(state.products, d)), style = t.amount.num(), color = c.ink)
            }
        }
        when {
            d.state == StockDocState.Draft -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PosButton("作废", { onAction(StockWorkAction.Void) }, variant = BtnVariant.Text)
                PosButton(if (d.kind == StockDocKind.Receive) "确认入库" else "确认报损", { onAction(StockWorkAction.Advance) }, Modifier.weight(1f), variant = BtnVariant.Primary, size = BtnSize.Large,
                    enabled = d.lines.isNotEmpty() && !state.busy, loading = state.busy)
            }
            d.state == StockDocState.Counting -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PosButton("作废", { onAction(StockWorkAction.Void) }, variant = BtnVariant.Text)
                PosButton("提交审批", { onAction(StockWorkAction.Advance) }, Modifier.weight(1f), variant = BtnVariant.Primary, size = BtnSize.Large, enabled = d.lines.isNotEmpty() && !state.busy, loading = state.busy)
            }
            d.state == StockDocState.Pending -> {
                Text("账面已冻结在提交时；审批通过后按差异调整账面（提交后的入库、销售照常算），盘盈盘亏记入流水", style = t.label, color = c.warning)
                PosButton("审批过账", { onAction(StockWorkAction.Advance) }, Modifier.fillMaxWidth(), variant = BtnVariant.Primary, size = BtnSize.Large, enabled = !state.busy, loading = state.busy)
            }
            else -> Text("单据${d.state.label}，只读", style = t.label, color = c.sub)
        }
    }
}

@Composable
private fun Alerts(state: StockWorkUiState, onAction: (StockWorkAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val low = state.view == StockView.Low
    val items = if (low) state.low else state.near
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (low) "低库存 · ${items.size}" else "临期 · ${items.size}", style = t.title, color = c.ink)
                Text(if (low) "账面不高于安全库存的商品" else "3 天内到期（含已过期）的商品", style = t.label, color = c.sub)
            }
            PosButton(if (low) "一键生成入库单" else "一键生成报损单", { onAction(if (low) StockWorkAction.RefillLow else StockWorkAction.WriteOffNear) }, variant = BtnVariant.Primary, enabled = items.isNotEmpty())
        }
        if (items.isEmpty()) EmptyState(if (low) "没有低库存商品" else "没有临期商品")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(items, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Pos.metrics.button).background(c.tile).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(p.name, style = t.strong, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("账面 ${StockWork.qtyText(p, StockWork.book(p))}", style = t.label.num(), color = if (StockWork.book(p) == 0L) c.danger else c.ink)
                    Text(if (low) "安全库存 ${p.retail?.safetyStock ?: 0}" else "到期 ${p.retail?.expiry.orEmpty()}", style = t.label.num(), color = c.warning)
                    Text(if (low) "建议补 ${StockWork.refill(p)}" else "", style = t.label.num(), color = c.sub)
                }
            }
        }
    }
}

@Composable
private fun NewDocDialog(k: StockDocKind, onAction: (StockWorkAction) -> Unit) {
    var text by remember { mutableStateOf("") }
    PosDialog("新建${k.label}单", { onAction(StockWorkAction.CancelNew) }, subtitle = when (k) {
        StockDocKind.Receive -> "供应商送货入库，确认后加到账面"
        StockDocKind.Count -> "数完提交，主管审批后实盘覆盖账面"
        StockDocKind.Loss -> "过期、破损的商品，确认后从账面扣掉"
    }, footer = {
        PosButton("取消", { onAction(StockWorkAction.CancelNew) }, variant = BtnVariant.Text)
        PosButton("新建", { onAction(StockWorkAction.Create(text.trim())) }, variant = BtnVariant.Primary)
    }) {
        when (k) {
            StockDocKind.Receive -> PosTextField(text, { if (it.length <= 30) text = it }, "供应商（可不填）")
            StockDocKind.Count -> PosTextField(text, { if (it.length <= 30) text = it }, "盘点范围（如：饮品货架，可不填）")
            StockDocKind.Loss -> Text("报损单不需要填写额外信息", style = Pos.type.body, color = Pos.colors.sub, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
