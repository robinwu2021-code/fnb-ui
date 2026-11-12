package ai.neargo.fnb.pos.feature.order

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.EmptyState
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.railWidth
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosIconButton
import ai.neargo.fnb.pos.core.designsystem.component.PosSheet
import ai.neargo.fnb.pos.core.designsystem.component.RailItem
import ai.neargo.fnb.pos.core.designsystem.component.SearchField
import ai.neargo.fnb.pos.core.designsystem.component.Workbench
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.PosSize
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.CatSide
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewAgenda
import ai.neargo.fnb.pos.core.designsystem.component.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 路由入口：取 ViewModel，订阅状态，交给无状态内容。 */
@Composable
fun OrderRoute(onCheckout: (String) -> Unit, onChangeTable: () -> Unit) {
    val vm = posViewModel { c, h -> OrderViewModel(c.session, c.config, c.orders, c.floor, c.print, c.prefs, c.device, c.messages, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    OrderScreen(state, vm::onAction, onCheckout = { state.table?.let { onCheckout(it.id) } }, onChangeTable = onChangeTable)
}

/**
 * 点餐工作台（无状态）。
 * 宽屏：分类与菜单共用一个大面板（分类位置 L1–L6），账单坞在左或右。
 * 窄屏：分类在上横排，账单收成底部摘要，点开全屏账单。
 */
@Composable
fun OrderScreen(state: OrderUiState, onAction: (OrderAction) -> Unit, onCheckout: () -> Unit, onChangeTable: () -> Unit) {
    val layout = LocalPosLayout.current
    var specDish by remember { mutableStateOf<Product?>(null) }
    var detailDish by remember { mutableStateOf<Product?>(null) }
    val onDish: (Product) -> Unit = { d ->
        when {
            d.soldOut -> onAction(OrderAction.Notice("${d.name}已沽清"))
            d.hasSpec || d.mealGroups.isNotEmpty() -> specDish = d
            else -> onAction(OrderAction.AddDish(d))
        }
    }
    val catalog: @Composable (Modifier, CatSide) -> Unit = { m, side ->
        Catalog(state, side, onAction, onDish, { detailDish = it }, onChangeTable, m)
    }

    // 编辑布局时按草稿显示（拖动即时生效，「完成」才写回）
    val editor = ai.neargo.fnb.pos.app.appContainer().layoutEditor
    val draft by editor.draft.collectAsStateWithLifecycle()
    val p = draft ?: state.prefs
    val editing = draft != null
    val hybrid = p.diningMode == ai.neargo.fnb.pos.core.model.ActionMode.Hybrid
    val ui = remember(state.table?.id) { DockUi() }
    if (layout.wide) {
        val base = layout.dockW.value.toInt()
        Workbench(p.layout.bill, ai.neargo.fnb.pos.core.model.ActionLayout.billWidth(p.billWidth, base).dp, aside = { m -> OrderDock(state, onAction, onCheckout, m, ui = ui, hybrid = hybrid) },
            rail = if (!hybrid) null else ({ m ->
                if (editing) ai.neargo.fnb.pos.feature.common.RailEditor(m, ai.neargo.fnb.pos.core.model.BizType.Dining, p.railOf(ai.neargo.fnb.pos.core.model.BizType.Dining),
                    { editor.rail(ai.neargo.fnb.pos.core.model.BizType.Dining, it) }, { onAction(OrderAction.Notice(it)) })
                else DiningRail(state, onAction, ui, p.railOf(ai.neargo.fnb.pos.core.model.BizType.Dining), m)
            }),
            railWidth = (if (editing) 120 else ai.neargo.fnb.pos.core.model.ActionLayout.railWidth(p.railWidth)).dp,
            editing = editing, onSideWidth = { editor.widenBill(it, base) }, onRailWidth = { editor.widenRail(it) }, onSide = { editor.billSide(it) },
        ) { m -> if (editing) CatalogWithMove(m, { s -> editor.catSide(s) }) { mm -> catalog(mm, p.layout.cat) } else catalog(m, p.layout.cat) }
    } else {
        CompactOrder(state, onAction, onCheckout) { m -> catalog(m, CatSide.Top) }
    }

    specDish?.let { d ->
        SpecDialog(d, state.groupsOf(d), onDismiss = { specDish = null }) { sku, qty, mealChoiceIds ->
            onAction(OrderAction.AddDish(d, sku, qty, mealChoiceIds)); specDish = null
        }
    }
    detailDish?.let { d ->
        DishDetailDialog(d, state.dishes.firstOrNull { it.dish.id == d.id }?.qty ?: 0, onDismiss = { detailDish = null }, onAdd = { detailDish = null; onDish(d) })
    }
}

/** 分类 + 菜单一个大面板；分类栏用轻底色块与菜单区分，扁平无阴影。 */
@Composable
private fun Catalog(
    state: OrderUiState,
    side: CatSide,
    onAction: (OrderAction) -> Unit,
    onDish: (Product) -> Unit,
    onLongPress: (Product) -> Unit,
    onChangeTable: () -> Unit,
    modifier: Modifier,
) {
    val rail: @Composable (Boolean) -> Unit = { vertical -> CategoryRail(state, { onAction(OrderAction.SelectCategory(it)) }, vertical) }
    val menu: @Composable (Modifier) -> Unit = { m -> MenuPane(state, onAction, onDish, onLongPress, onChangeTable, m) }
    Panel(modifier) {
        when (side) {
            CatSide.Left, CatSide.Right -> Row(Modifier.fillMaxSize()) {
                if (side == CatSide.Left) rail(true)
                menu(Modifier.weight(1f).fillMaxHeight())
                if (side == CatSide.Right) rail(true)
            }
            CatSide.Top, CatSide.Bottom -> Column(Modifier.fillMaxSize()) {
                if (side == CatSide.Top) rail(false)
                menu(Modifier.weight(1f).fillMaxWidth())
                if (side == CatSide.Bottom) rail(false)
            }
        }
    }
}

/** 分类栏：竖排 M3 NavigationDrawerItem；横排 FilterChip。计数为本桌已点份数。 */
@Composable
private fun CategoryRail(state: OrderUiState, onSelect: (String) -> Unit, vertical: Boolean) {
    val c = Pos.colors
    if (vertical) {
        LazyColumn(
            Modifier.width(railWidth()).fillMaxHeight().background(c.tile),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(state.categories, key = { it.id }) { cat -> RailItem(cat.name, cat.count.takeIf { it > 0 }, cat.id == state.filter.cat, { onSelect(cat.id) }) }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().background(c.tile).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.categories.forEach { cat ->
                PosChip(cat.name, cat.id == state.filter.cat, { onSelect(cat.id) }, count = cat.count.takeIf { it > 0 }, height = 36.dp)
            }
        }
    }
}

/** 菜单区：标题行、搜索 + 快捷筛选 + 排列切换（同一行）、菜品网格。 */
@Composable
private fun MenuPane(
    state: OrderUiState,
    onAction: (OrderAction) -> Unit,
    onDish: (Product) -> Unit,
    onLongPress: (Product) -> Unit,
    onChangeTable: () -> Unit,
    modifier: Modifier,
) {
    val c = Pos.colors
    val t = Pos.type
    val pad = Pos.metrics.pad
    val gap = Pos.metrics.gap
    val phone = LocalPosLayout.current.phone
    val f = state.filter
    Column(modifier.padding(start = pad, end = pad, top = pad)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("点餐", style = t.title, color = c.ink)
            Spacer(Modifier.width(10.dp))
            val tb = state.table
            Text(if (tb == null) "" else "${tb.id} · ${tb.guests} 人 / 堂食", style = t.label.num(), color = c.sub, modifier = Modifier.weight(1f), maxLines = 1)
            PosButton("换桌", onChangeTable, variant = BtnVariant.Text)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SearchField(f.query, { onAction(OrderAction.Search(it)) }, "搜索菜名、拼音、英文或编号", Modifier.weight(1f).widthIn(min = 120.dp))
            QuickFilters.forEach { (k, label) -> PosChip(label, f.tag == k, { onAction(OrderAction.Filter(k)) }) }
            PosIconButton(
                if (f.arrange == Arrange.Top) Icons.Outlined.ViewAgenda else Icons.AutoMirrored.Outlined.ViewList,
                "图文排列：${f.arrange.label}", { onAction(OrderAction.ToggleArrange) }, filled = false,
            )
        }
        Text("${state.dishes.size} 道菜 · 点击加菜", style = t.label, color = c.sub, modifier = Modifier.padding(vertical = 4.dp))

        val minCell = if (f.arrange == Arrange.Left) 215.dp else 138.dp
        LazyVerticalGrid(
            columns = if (phone && f.arrange == Arrange.Top) GridCells.Fixed(2) else GridCells.Adaptive(minCell),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = pad),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(state.dishes, key = { it.dish.id }) { item ->
                DishCard(item.dish, item.qty, state.prefs.photos, f.arrange, { onDish(item.dish) }, { onLongPress(item.dish) })
            }
            if (state.dishes.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("没有符合条件的菜品", action = "重置筛选") { onAction(OrderAction.Search("")); onAction(OrderAction.Filter(null)) }
                }
            }
        }
    }
}

/** 窄屏：菜单占满，底部摘要（桌号、已点份数、应收）+ 购物车主操作。 */
@Composable
private fun CompactOrder(state: OrderUiState, onAction: (OrderAction) -> Unit, onCheckout: () -> Unit, catalog: @Composable (Modifier) -> Unit) {
    val c = Pos.colors
    val t = Pos.type
    var dockOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        catalog(Modifier.weight(1f).fillMaxWidth().padding(Pos.metrics.gap))
        Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${state.table?.id ?: ""} · ${state.totals.items} 份已点", style = t.label.num(), color = c.sub)
                Text(yuan(state.totals.due), style = t.title.num(), color = c.ink)
            }
            PosButton("购物车", { dockOpen = true }, variant = BtnVariant.Primary, size = BtnSize.Large)
        }
    }
    if (dockOpen) {
        PosSheet(onDismiss = { dockOpen = false }, showHandle = false) {
            OrderDock(state, onAction, onCheckout = { dockOpen = false; onCheckout() }, Modifier.fillMaxWidth().fillMaxHeight(0.92f), onClose = { dockOpen = false })
        }
    }
}

/**
 * 混合模式的中间竖条（餐饮）：选中行的数量步进 + 按配置顺序的行操作（不适用的置灰）+「更多」里的桌台级操作。
 * 选中行：账单里点哪行选哪行，默认是最近加的一行。
 */
@Composable
private fun DiningRail(state: OrderUiState, onAction: (OrderAction) -> Unit, ui: DockUi, cfg: ai.neargo.fnb.pos.core.model.RailConfig, modifier: Modifier) {
    val sel = ui.selectedLine(state.order)
    val isNew = sel?.status == ai.neargo.fnb.pos.core.model.LineStatus.New
    val sent = sel != null && !isNew && sel.status != ai.neargo.fnb.pos.core.model.LineStatus.Queued
    val can = state.canOrder
    fun item(id: String): ai.neargo.fnb.pos.feature.common.RailItem? {
        val label = ai.neargo.fnb.pos.core.model.ActionLayout.spec(ai.neargo.fnb.pos.core.model.BizType.Dining, id)?.label ?: return null
        return when (id) {
            "line.delete" -> ai.neargo.fnb.pos.feature.common.RailItem(id, label, can && isNew) { sel?.let { onAction(OrderAction.ChangeQty(it.uid, -it.qty)) } }
            "line.note" -> ai.neargo.fnb.pos.feature.common.RailItem(id, label, can && isNew) { ui.noteLine = sel }
            "line.gift", "line.void" -> ai.neargo.fnb.pos.feature.common.RailItem(id, label, can && sent) { ui.dialog = "adjust" }
            "line.rush" -> ai.neargo.fnb.pos.feature.common.RailItem(id, label, sel?.status == ai.neargo.fnb.pos.core.model.LineStatus.Sent) { sel?.let { onAction(OrderAction.Advance(it.uid, ai.neargo.fnb.pos.core.model.LineStatus.Rushing)) } }
            "line.serve" -> ai.neargo.fnb.pos.feature.common.RailItem(id, label, sel?.status == ai.neargo.fnb.pos.core.model.LineStatus.Sent || sel?.status == ai.neargo.fnb.pos.core.model.LineStatus.Rushing) {
                sel?.let { onAction(OrderAction.Advance(it.uid, ai.neargo.fnb.pos.core.model.LineStatus.Served)) } }
            "table.adjust" -> ai.neargo.fnb.pos.feature.common.RailItem(id, label, can && state.order.lines.isNotEmpty()) { ui.dialog = "adjust" }
            "table.change" -> ai.neargo.fnb.pos.feature.common.RailItem(id, label, can) { ui.dialog = "change" }
            "table.merge" -> ai.neargo.fnb.pos.feature.common.RailItem(id, label, can) { ui.dialog = "merge" }
            "table.split" -> ai.neargo.fnb.pos.feature.common.RailItem(id, label, can && state.order.lines.size > 1) { ui.dialog = "split" }
            "table.prebill" -> ai.neargo.fnb.pos.feature.common.RailItem(id, label, state.order.lines.isNotEmpty()) { onAction(OrderAction.PreBill) }
            "table.batch" -> ai.neargo.fnb.pos.feature.common.RailItem(id, label, state.order.sent.isNotEmpty()) { ui.dialog = "batch" }
            else -> null
        }
    }
    ai.neargo.fnb.pos.feature.common.ActionRail(
        modifier, title = sel?.let { "选中 · ${it.name}" } ?: "未选中",
        qty = sel?.qty, onQty = if (can && isNew) ({ d -> sel?.let { onAction(OrderAction.ChangeQty(it.uid, d)) } }) else null,
        items = cfg.rail.mapNotNull(::item), more = cfg.more.mapNotNull(::item),
    )
}

/** 编辑布局时，分类 + 菜单面板上方的移动把手：拖到哪条边，分类栏就贴哪边 */
@Composable
private fun CatalogWithMove(modifier: Modifier, onCat: (ai.neargo.fnb.pos.core.model.CatSide) -> Unit, content: @Composable (Modifier) -> Unit) {
    var box by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var drag by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    Box(modifier.onGloballyPositioned { box = it.boundsInWindow() }) {
        content(Modifier.fillMaxSize())
        drag?.let { p ->
            val side = ai.neargo.fnb.pos.core.model.ActionLayout.catDrop(p.x - box.left, p.y - box.top, box.width, box.height)
            val zone = when (side) {
                CatSide.Left -> Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.18f)
                CatSide.Right -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.18f)
                CatSide.Top -> Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.16f)
                CatSide.Bottom -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.16f)
            }
            ai.neargo.fnb.pos.core.designsystem.component.DropZone(zone, "分类放到${side.label}边")
        }
        ai.neargo.fnb.pos.core.designsystem.component.MoveHandle("拖动分类栏", onMove = { drag = it },
            onDrop = { p -> onCat(ai.neargo.fnb.pos.core.model.ActionLayout.catDrop(p.x - box.left, p.y - box.top, box.width, box.height)) },
            modifier = Modifier.align(Alignment.Center))
    }
}
