package ai.neargo.fnb.pos.ui.order

import ai.neargo.fnb.pos.data.Dish
import ai.neargo.fnb.pos.data.MockData
import ai.neargo.fnb.pos.data.yuan
import ai.neargo.fnb.pos.state.MenuView
import ai.neargo.fnb.pos.state.PosStore
import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosDims
import ai.neargo.fnb.pos.theme.PosType
import ai.neargo.fnb.pos.theme.num
import ai.neargo.fnb.pos.ui.components.CountBadge
import ai.neargo.fnb.pos.ui.components.DishImage
import ai.neargo.fnb.pos.ui.components.PosFilterChip
import ai.neargo.fnb.pos.ui.components.StatusLabel
import ai.neargo.fnb.pos.ui.components.Tone
import ai.neargo.fnb.pos.ui.shell.GridKind
import ai.neargo.fnb.pos.ui.shell.LocalPosLayout
import ai.neargo.fnb.pos.ui.shell.gridColumns
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Z4 菜单区：搜索、标签筛选、视图切换、菜品宫格。 */
@Composable
fun MenuPane(
    store: PosStore,
    cat: String,
    onDish: (Dish) -> Unit,
    onLongPress: (Dish) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    val layout = LocalPosLayout.current
    val gap = PosDims.gap
    var query by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }

    val dishes = remember(cat, query, tag) {
        val base = when {
            query.isNotBlank() -> MockData.dishes.filter { it.name.contains(query) || it.id.startsWith(query) }
            cat == "hot" -> MockData.hot.map(MockData::dish)
            else -> MockData.dishes.filter { it.cat == cat }
        }
        if (tag == null) base else base.filter { tag in it.tags }
    }

    val tags: @Composable () -> Unit = {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MockData.filterTags.forEach { t -> PosFilterChip(t, tag == t, { tag = if (tag == t) null else t }) }
        }
    }
    val viewToggle: @Composable () -> Unit = {
        val next = if (store.menuView == MenuView.Image) MenuView.Text else MenuView.Image
        Box(
            Modifier.size(PosDims.ctl).clip(PosDims.radiusFull).background(Pos.colors.surface)
                .clickable(role = Role.Button, onClickLabel = "切换为${next.label}视图") { store.menuView = next },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (store.menuView == MenuView.Image) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                "${next.label}视图", tint = Pos.colors.ink,
            )
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        if (layout.wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchInput(query, { query = it }, Modifier.weight(1f))
                tags()
                viewToggle()
            }
        } else {
            // 手机：搜索单独一行，标签在下面横滑，避免搜索框被挤窄
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchInput(query, { query = it }, Modifier.weight(1f))
                viewToggle()
            }
            tags()
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val cols = gridColumns(GridKind.Menu, layout, maxWidth, gap)
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding + gap),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(dishes, key = { it.id }) { d ->
                    val qty = store.qtyOf(d.id)
                    if (store.menuView == MenuView.Image) DishCard(d, qty, { onDish(d) }, { onLongPress(d) })
                    else DishTile(d, qty, { onDish(d) }, { onLongPress(d) })
                }
            }
        }
    }
}

@Composable
private fun SearchInput(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val c = Pos.colors
    Row(
        modifier.height(PosDims.ctl).clip(PosDims.radiusFull).background(c.surface).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.sub)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("菜名、编号", style = PosType.body, color = c.sub, maxLines = 1)
            BasicTextField(
                value, onChange,
                singleLine = true,
                textStyle = PosType.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * DiningMenuItem · 图文：图 3:2（比 4:3 每屏多露出约半行），单击加菜 / 带规格开弹窗，长按看详情。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DishCard(d: Dish, qty: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Pos.colors
    Column(
        Modifier
            .clip(PosDims.radiusLg)
            .background(if (qty > 0) c.primaryTint else c.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "详情")
            .alpha(if (d.soldOut) 0.55f else 1f),
    ) {
        Box {
            DishImage(d, Modifier.fillMaxWidth().aspectRatio(3f / 2f).clip(PosDims.radiusLg))
            if (qty > 0) CountBadge(qty, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(d.name, style = PosType.strong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(d.desc, style = PosType.caption, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            PriceRow(d)
        }
    }
}

/** DiningMenuItem · 文字：固定高 pos-tile-h（紧凑 88 / 标准 108），高峰期一屏看到更多菜。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DishTile(d: Dish, qty: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Pos.colors
    Box(
        Modifier
            .height(PosDims.tileH)
            .clip(PosDims.radiusLg)
            .background(if (qty > 0) c.primaryTint else c.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "详情")
            .alpha(if (d.soldOut) 0.55f else 1f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(d.name, style = PosType.strong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 24.dp))
            Text("${d.id} · ${d.desc}", style = PosType.caption, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            PriceRow(d)
        }
        if (qty > 0) CountBadge(qty, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun PriceRow(d: Dish) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(yuan(d.price, keepZero = false), style = PosType.strong.num().copy(fontWeight = PosType.hero.fontWeight), color = Pos.colors.ink)
        Spacer(Modifier.weight(1f))
        when {
            d.soldOut -> StatusLabel("已沽清", Tone.Danger)
            d.stock != null -> StatusLabel("剩 ${d.stock}", Tone.Warning)
            d.hasSpec -> StatusLabel("规格", Tone.Neutral, dot = false)
        }
    }
}
