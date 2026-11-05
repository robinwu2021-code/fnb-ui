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
            d.hasSpec -> specDish = d
            else -> onAction(OrderAction.AddDish(d))
        }
    }
    val catalog: @Composable (Modifier, CatSide) -> Unit = { m, side ->
        Catalog(state, side, onAction, onDish, { detailDish = it }, onChangeTable, m)
    }

    if (layout.wide) {
        Workbench(state.prefs.layout.bill, layout.dockW, aside = { m -> OrderDock(state, onAction, onCheckout, m) }) { m -> catalog(m, state.prefs.layout.cat) }
    } else {
        CompactOrder(state, onAction, onCheckout) { m -> catalog(m, CatSide.Top) }
    }

    specDish?.let { d ->
        SpecDialog(d, state.groupsOf(d), onDismiss = { specDish = null }) { sku, qty ->
            onAction(OrderAction.AddDish(d, sku, qty)); specDish = null
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
            SearchField(f.query, { onAction(OrderAction.Search(it)) }, "搜索菜名、英文或编号", Modifier.weight(1f).widthIn(min = 120.dp))
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
