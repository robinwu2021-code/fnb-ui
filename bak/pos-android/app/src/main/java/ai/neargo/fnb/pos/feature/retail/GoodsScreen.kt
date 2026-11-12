package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.Ean13Barcode
import ai.neargo.fnb.pos.core.designsystem.component.EmptyState
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.RailItem
import ai.neargo.fnb.pos.core.designsystem.component.SearchField
import ai.neargo.fnb.pos.core.designsystem.component.Section
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.LabelRules
import ai.neargo.fnb.pos.core.model.LabelTpl
import ai.neargo.fnb.pos.core.model.LabelWhy
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.StockDocKind
import ai.neargo.fnb.pos.core.model.StockWork
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun GoodsRoute(onOpenStock: () -> Unit) {
    val vm = posViewModel { c, _ -> GoodsViewModel(c.config, c.retail, c.messages) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.toStock.collect { onOpenStock() } }
    GoodsScreen(state, vm::onAction)
}

private val mdhm = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())

@Composable
private fun StateTag(s: StockState) {
    val c = Pos.colors
    val (bg, fg) = when (s) { StockState.Out -> c.dangerBg to c.danger; StockState.Low -> c.control to c.warning; StockState.Near -> c.control to c.warning; StockState.Ok -> c.tint to c.primary }
    Tag(s.label, bg = bg, fg = fg)
}

/** 零售商品（原型 GoodsWB.tsx）：左栏 商品资料 / 库存 / 价签 · 中间列表 · 右边详情 */
@Composable
fun GoodsScreen(state: GoodsUiState, onAction: (GoodsAction) -> Unit) {
    val layout = LocalPosLayout.current
    val gap = Pos.metrics.gap
    val sideW = if (layout.wide) maxOf(layout.dockW, 420.dp) else layout.dockW
    Row(Modifier.fillMaxSize().padding(gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Panel(Modifier.width(140.dp).fillMaxHeight()) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RailItem(GoodsSub.Items.label, state.products.size, state.sub == GoodsSub.Items, { onAction(GoodsAction.Sub(GoodsSub.Items)) })
                RailItem(GoodsSub.Stock.label, state.products.count { StockWork.low(it) || StockWork.book(it) <= 0 }.takeIf { it > 0 }, state.sub == GoodsSub.Stock, { onAction(GoodsAction.Sub(GoodsSub.Stock)) })
                RailItem(GoodsSub.Labels.label, state.queue.size.takeIf { it > 0 }, state.sub == GoodsSub.Labels, { onAction(GoodsAction.Sub(GoodsSub.Labels)) })
            }
        }
        when (state.sub) {
            GoodsSub.Items -> {
                Panel(Modifier.weight(1f).fillMaxHeight()) { ItemList(state, onAction) }
                Panel(Modifier.width(sideW).fillMaxHeight()) { state.current?.let { ItemDetail(state, it, onAction) } ?: EmptyState("没有商品", Modifier.padding(16.dp)) }
            }
            GoodsSub.Stock -> {
                Panel(Modifier.weight(1f).fillMaxHeight()) { StockList(state, onAction) }
                Panel(Modifier.width(sideW).fillMaxHeight()) { state.current?.let { StockDetail(state, it, onAction) } ?: EmptyState("没有符合条件的商品", Modifier.padding(16.dp)) }
            }
            GoodsSub.Labels -> {
                Panel(Modifier.weight(1f).fillMaxHeight()) { LabelQueue(state, onAction) }
                Panel(Modifier.width(sideW).fillMaxHeight()) { LabelPreviewPanel(state, onAction) }
            }
        }
    }
}

/* ---------------- 商品资料 ---------------- */

@Composable
private fun ItemList(state: GoodsUiState, onAction: (GoodsAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(state.query, { onAction(GoodsAction.Search(it)) }, "搜商品名或条码", Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PosChip("常用", state.cat == HOT && state.query.isBlank(), { onAction(GoodsAction.Cat(HOT)) })
            state.categories.forEach { cat -> PosChip(cat.name, state.cat == cat.id && state.query.isBlank(), { onAction(GoodsAction.Cat(cat.id)) }) }
        }
        Row(Modifier.padding(horizontal = 10.dp)) {
            listOf("商品" to 2f, "条码" to 1.2f, "售价" to 0.9f, "会员价" to 0.8f, "库存" to 0.9f, "状态" to 0.8f).forEach { (h, w) -> Text(h, style = t.label, color = c.sub, modifier = Modifier.weight(w)) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(state.items, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Pos.metrics.button).background(if (state.current?.id == p.id) c.tint else Color.Transparent)
                    .clickable { onAction(GoodsAction.Select(p.id)) }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(2f)) {
                        Text(p.name, style = t.strong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (StockWork.weighed(p) || p.hasSpec) Text(if (StockWork.weighed(p)) "称重" else "多规格 · ${p.skus.size} 格", style = t.label, color = c.sub)
                    }
                    Text(p.retail?.barcodes?.firstOrNull().orEmpty(), style = t.label.num(), color = c.sub, modifier = Modifier.weight(1.2f), maxLines = 1)
                    Text(yuan(p.price) + if (StockWork.weighed(p)) "/kg" else "", style = t.body.num(), color = c.ink, modifier = Modifier.weight(0.9f))
                    Text(p.retail?.memberPrice?.let { yuan(it) } ?: "—", style = t.body.num(), color = c.sub, modifier = Modifier.weight(0.8f))
                    Text(StockWork.qtyText(p, StockWork.book(p)), style = t.body.num(), color = c.ink, modifier = Modifier.weight(0.9f))
                    Box(Modifier.weight(0.8f)) { StateTag(state.stateOf(p)) }
                }
            }
        }
    }
}

@Composable
private fun ItemDetail(state: GoodsUiState, p: Product, onAction: (GoodsAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    var draft by remember(p.id, p.price) { mutableStateOf(ai.neargo.fnb.pos.core.model.fenToInput(p.price)) }
    val cost = StockWork.cost(p)
    val changed = (ai.neargo.fnb.pos.core.model.CashRules.amount(draft) ?: 0) != p.price
    Column(Modifier.fillMaxSize()) {
        DetailHead(state, p)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("售价${if (StockWork.weighed(p)) "（每 kg）" else ""}") {
                PosTextField(draft, { if (Regex("^\\d{0,6}(\\.\\d{0,2})?$").matches(it)) draft = it }, "售价（元）", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    hint = "保存后收银立即按新价算，并自动加入待打价签" + if (p.hasSpec) "；没单独改过价的规格一起改" else "")
            }
            Section("资料") {
                KeyValue("会员价", p.retail?.memberPrice?.let { yuan(it) + if (it >= p.price) " · 不低于售价，不生效" else "" } ?: "—")
                KeyValue("进价", yuan(cost) + if (StockWork.weighed(p)) "/kg" else "")
                KeyValue("毛利率", if (p.price > 0 && cost > 0) "${(100 - cost * 100 / p.price)}%" else "—")
                KeyValue("库存", StockWork.qtyText(p, StockWork.book(p)))
                KeyValue("安全库存", p.retail?.safetyStock?.let { StockWork.qtyText(p, it.toLong()) } ?: "—")
                KeyValue("到期日", p.retail?.expiry ?: "—")
                p.retail?.promo?.let { KeyValue("促销", it.label) }
            }
        }
        Row(Modifier.fillMaxWidth().background(c.control).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PosButton("加入价签", { onAction(GoodsAction.ToLabels(p.id)) })
            PosButton("保存售价", { onAction(GoodsAction.SavePrice(p.id, draft)) }, Modifier.weight(1f), variant = BtnVariant.Primary, size = BtnSize.Large, enabled = changed && draft.isNotBlank())
        }
    }
}

@Composable
private fun DetailHead(state: GoodsUiState, p: Product) {
    val c = Pos.colors; val t = Pos.type
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, style = t.title, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); StateTag(state.stateOf(p))
        }
        Text(listOfNotNull(p.retail?.barcodes?.firstOrNull(), state.categories.firstOrNull { it.id == p.categoryId }?.name).joinToString(" · "), style = t.label.num(), color = c.sub)
    }
}

/* ---------------- 库存 ---------------- */

@Composable
private fun StockList(state: GoodsUiState, onAction: (GoodsAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("库存", style = t.title, color = c.ink, modifier = Modifier.weight(1f))
            Text("库存金额（按进价）${yuan(state.stockValue)}", style = t.label.num(), color = c.sub)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StockFilter.entries.forEach { f -> PosChip(f.label, state.filter == f, { onAction(GoodsAction.Filter(f)) }, count = state.products.count { state.matches(it, f) }) }
        }
        Row(Modifier.padding(horizontal = 10.dp)) {
            listOf("商品" to 2f, "库存" to 1f, "安全库存" to 0.9f, "到期日" to 1f, "状态" to 0.8f).forEach { (h, w) -> Text(h, style = t.label, color = c.sub, modifier = Modifier.weight(w)) }
        }
        if (state.stockList.isEmpty()) EmptyState("没有${state.filter.label}商品")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(state.stockList, key = { it.id }) { p ->
                val book = StockWork.book(p); val safety = p.retail?.safetyStock
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Pos.metrics.button).background(if (state.current?.id == p.id) c.tint else Color.Transparent)
                    .clickable { onAction(GoodsAction.Select(p.id)) }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(2f)) {
                        Text(p.name, style = t.strong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.retail?.barcodes?.firstOrNull().orEmpty(), style = t.label.num(), color = c.sub)
                    }
                    Text(StockWork.qtyText(p, book), style = t.body.num().let { if (safety != null && book <= safety) it.copy(fontWeight = FontWeight.SemiBold) else it },
                        color = if (safety != null && book <= safety || book <= 0) c.danger else c.ink, modifier = Modifier.weight(1f))
                    Text(safety?.let { StockWork.qtyText(p, it.toLong()) } ?: "—", style = t.body.num(), color = c.sub, modifier = Modifier.weight(0.9f))
                    Text(p.retail?.expiry ?: "—", style = t.body.num(), color = if (StockWork.near(p, state.today)) c.warning else c.sub, modifier = Modifier.weight(1f))
                    Box(Modifier.weight(0.8f)) { StateTag(state.stateOf(p)) }
                }
            }
        }
    }
}

@Composable
private fun StockDetail(state: GoodsUiState, p: Product, onAction: (GoodsAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val moves = state.movesOf(p.id)
    Column(Modifier.fillMaxSize()) {
        DetailHead(state, p)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("库存") {
                KeyValue("安全库存", p.retail?.safetyStock?.let { StockWork.qtyText(p, it.toLong()) } ?: "—")
                KeyValue("进价", yuan(StockWork.cost(p)) + if (StockWork.weighed(p)) "/kg" else "")
                KeyValue("到期日", p.retail?.expiry ?: "—")
                if (p.hasSpec) p.skus.forEach { k -> KeyValue("· ${k.label}", "${k.stock} 件", valueColor = if (k.stock <= 0) c.danger else c.ink) }
            }
            Section("最近变动") {
                if (moves.isEmpty()) Text("还没有库存变动", style = t.label, color = c.sub)
                moves.forEach { m ->
                    val d = m.delta ?: 0
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.reason + (m.skuKey?.let { " · " + m.productName.substringAfter(" · ", "") }.orEmpty()), style = t.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${mdhm.format(java.util.Date(m.at))} · ${m.actor}", style = t.label.num(), color = c.sub)
                        }
                        Text((if (d > 0) "+" else if (d < 0) "−" else "") + StockWork.qtyText(p, kotlin.math.abs(d).toLong()), style = t.strong.num(), color = if (d < 0) c.danger else c.primary)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(c.control).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("账面库存", style = t.label, color = c.sub)
                    Text("库存金额 ${yuan(StockWork.lineCost(p, maxOf(0, StockWork.book(p))))}", style = t.label.num(), color = c.sub)
                }
                Text(StockWork.qtyText(p, StockWork.book(p)), style = t.amountM.num(), color = c.ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PosButton("去盘点", { onAction(GoodsAction.OpenDoc(StockDocKind.Count, p.id)) })
                PosButton("去入库", { onAction(GoodsAction.OpenDoc(StockDocKind.Receive, p.id)) }, Modifier.weight(1f), variant = BtnVariant.Primary, size = BtnSize.Large)
            }
        }
    }
}

/* ---------------- 价签 ---------------- */

@Composable
private fun LabelQueue(state: GoodsUiState, onAction: (GoodsAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(text, { text = it }, "输入条码或商品名，加入待打价签", Modifier.weight(1f))
            PosButton("加入", { onAction(GoodsAction.AddLabel(text)); text = "" }, enabled = text.isNotBlank())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("待打价签 · ${state.queue.size}", style = t.section, color = c.ink, modifier = Modifier.weight(1f))
            if (state.queue.isNotEmpty()) PosButton(if (state.picked.size == state.queue.size) "全不选" else "全选", { onAction(GoodsAction.PickAll) }, variant = BtnVariant.Text, size = BtnSize.Small)
        }
        if (state.queue.isEmpty()) EmptyState("没有待打价签。改价后会自动加进来，也可以在商品资料里点「加入价签」")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(state.queue, key = { it.productId }) { q ->
                val p = state.product(q.productId) ?: return@items
                val on = q.productId in state.picked
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Pos.metrics.button).background(if (state.preview?.id == p.id) c.tint else Color.Transparent)
                    .clickable { onAction(GoodsAction.Focus(p.id)) }.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(on, { onAction(GoodsAction.TogglePick(p.id)) }, colors = CheckboxDefaults.colors(checkedColor = c.primary))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = t.strong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when (q.why) { LabelWhy.Price -> "改价 ${q.oldPrice?.let { yuan(it) } ?: ""} → ${yuan(p.price)}"; else -> q.why.label }, style = t.label.num(), color = if (q.why == LabelWhy.Price) c.warning else c.sub)
                    }
                    Text(yuan(p.price) + if (StockWork.weighed(p)) "/kg" else "", style = t.strong.num(), color = c.ink)
                }
            }
        }
    }
}

@Composable
private fun LabelPreviewPanel(state: GoodsUiState, onAction: (GoodsAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val n = state.picked.size
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("价签预览", style = t.title, color = c.ink)
            Text("标签机：未接入（型号待定），先生成 TSPL 指令并记录", style = t.label, color = c.warning)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { LabelTpl.entries.forEach { tp -> PosChip(tp.label, state.tpl == tp, { onAction(GoodsAction.Tpl(tp)) }) } }
            Box(Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(Pos.metrics.card).background(c.control).padding(16.dp), contentAlignment = Alignment.Center) {
                state.preview?.let { PriceLabel(it, state.tpl) } ?: Text("选一个商品看预览", style = t.label, color = c.sub)
            }
            Text("售价、会员价、单位价随商品资料变化；条码按 EAN-13 编码（店内码、PLU 补成 13 位，收银扫价签能认出）", style = t.label, color = c.sub)
            if (state.printed > 0) Text("本店已生成 ${state.printed} 张价签", style = t.label.num(), color = c.sub)
        }
        Row(Modifier.fillMaxWidth().background(c.control).padding(12.dp)) {
            PosButton(if (n > 0) "打印选中的 $n 张" else "请勾选要打的价签", { onAction(GoodsAction.Print) }, Modifier.weight(1f), variant = BtnVariant.Primary, size = BtnSize.Large, enabled = n > 0)
        }
    }
}

/** 一张价签纸样（按 4dp / 毫米画）：内容取 LabelRules.face，和打印指令同源 */
@Composable
fun PriceLabel(p: Product, tpl: LabelTpl) {
    val f = remember(p, tpl) { LabelRules.face(p) }
    val ink = Color(0xFF111111)
    val w = (tpl.wMm * 4).dp; val h = (tpl.hMm * 4).dp
    val paper = Modifier.size(w, h).clip(RoundedCornerShape(4.dp)).background(Color.White)
    when (tpl) {
        LabelTpl.Shelf -> Row(paper) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(f.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(f.unit, fontSize = 10.sp, color = ink)
                Ean13Barcode(f.ean13, 120.dp, 18.dp, color = ink)
            }
            Column(Modifier.fillMaxHeight().background(Color(0xFFEDEDED)).padding(horizontal = 10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.End) {
                Text(f.price, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                f.member?.let { Text(it, fontSize = 10.sp, color = ink) }
            }
        }
        else -> {
            val small = tpl == LabelTpl.L40x30
            Column(paper.padding(10.dp)) {
                Text(f.name, fontSize = if (small) 12.sp else 15.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = if (small) 1 else 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                if (!small) Text(f.unit, fontSize = 10.sp, color = ink)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Ean13Barcode(f.ean13, if (small) 84.dp else 110.dp, if (small) 20.dp else 26.dp, color = ink)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(f.price, fontSize = if (small) 18.sp else 24.sp, fontWeight = FontWeight.ExtraBold, color = ink, maxLines = 1)
                        if (!small) f.member?.let { Text(it, fontSize = 10.sp, color = ink) }
                    }
                }
            }
        }
    }
}
