package ai.neargo.fnb.pos.ui.order

import ai.neargo.fnb.pos.data.Dish
import ai.neargo.fnb.pos.data.yuan
import ai.neargo.fnb.pos.state.PosStore
import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosDims
import ai.neargo.fnb.pos.theme.PosType
import ai.neargo.fnb.pos.theme.num
import ai.neargo.fnb.pos.ui.components.BtnVariant
import ai.neargo.fnb.pos.ui.components.CountBadge
import ai.neargo.fnb.pos.ui.components.PosButton
import ai.neargo.fnb.pos.ui.shell.FrameLayout
import ai.neargo.fnb.pos.ui.shell.LocalPosLayout
import ai.neargo.fnb.pos.ui.shell.NavArea
import ai.neargo.fnb.pos.ui.shell.NavItem
import ai.neargo.fnb.pos.data.MockData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 点餐页。
 * 平板：分类区 · 菜单区 · 结单区，位置按 L1–L6 摆放，三栏各自滚动。
 * 手机：分类顶部横排，结单区收成底部毛玻璃条，点开是抽屉。
 */
@Composable
fun OrderScreen(store: PosStore, onCheckout: () -> Unit) {
    val layout = LocalPosLayout.current
    var cat by rememberSaveable { mutableStateOf("hot") }
    var specDish by remember { mutableStateOf<Dish?>(null) }
    var detailDish by remember { mutableStateOf<Dish?>(null) }

    val onDish: (Dish) -> Unit = { d -> if (d.hasSpec && !d.soldOut) specDish = d else store.addDish(d) }

    val navItems = MockData.categories.map { NavItem(it.id, it.name, if (it.id == "hot") 0 else store.qtyInCat(it.id)) }
    val nav: @Composable (Boolean, Modifier) -> Unit = { vertical, m -> NavArea(navItems, cat, { cat = it }, vertical, m, countLabel = "已点") }

    if (layout.wide) {
        FrameLayout(
            nav = nav,
            detail = { m -> OrderDock(store, onCheckout, m) },
        ) { m -> MenuPane(store, cat, onDish, onLongPress = { detailDish = it }, modifier = m) }
    } else {
        CompactOrder(store, cat, nav, onDish, { detailDish = it }, onCheckout)
    }

    specDish?.let { d ->
        SpecDialog(d, onDismiss = { specDish = null }) { spec, unit, qty ->
            store.addDish(d, spec, unit, qty)
            specDish = null
        }
    }
    detailDish?.let { d -> DishDetailDialog(d, store.qtyOf(d.id), onDismiss = { detailDish = null }, onAdd = { onDish(d); detailDish = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactOrder(
    store: PosStore,
    cat: String,
    nav: @Composable (Boolean, Modifier) -> Unit,
    onDish: (Dish) -> Unit,
    onLongPress: (Dish) -> Unit,
    onCheckout: () -> Unit,
) {
    val c = Pos.colors
    val haze = rememberHazeState()
    var dockOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        FrameLayout(Modifier.hazeSource(haze), nav = nav) { m ->
            MenuPane(store, cat, onDish, onLongPress, modifier = m, bottomPadding = 80.dp)
        }

        // 底部购物车条：Haze 毛玻璃，菜单从下面透出来
        val t = store.totals
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(PosDims.gap)
                .fillMaxWidth()
                .height(64.dp)
                .clip(PosDims.radiusFull)
                .hazeBlur(
                    HazeInput.Sources(haze),
                    HazeBlurStyle {
                        blurRadius(24.dp)
                        backgroundColor(c.surface)
                        noiseFactor(0f)
                    },
                )
                .clickable { dockOpen = true }
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Icon(Icons.Rounded.ShoppingBag, "已点", tint = c.ink, modifier = Modifier.size(26.dp))
                if (t.items > 0) CountBadge(t.items, Modifier.align(Alignment.TopEnd).padding(start = 14.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(yuan(t.due), style = PosType.title.num(), color = c.ink)
                Text("待下单 ${store.pending.sumOf { it.qty }} · 已下单 ${store.sent.sumOf { it.qty }}", style = PosType.caption, color = c.sub)
            }
            if (store.pending.isNotEmpty()) {
                PosButton("送厨 · ${store.pending.size}", store::sendToKitchen, variant = BtnVariant.Soft)
                Spacer(Modifier.width(6.dp))
            }
            PosButton("结账", onCheckout, height = PosDims.ctlL, enabled = t.items > 0)
        }
    }

    if (dockOpen) {
        ModalBottomSheet(
            onDismissRequest = { dockOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surface,
            scrimColor = c.scrim,
        ) {
            OrderDock(
                store,
                onCheckout = { dockOpen = false; onCheckout() },
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            )
        }
    }
}
