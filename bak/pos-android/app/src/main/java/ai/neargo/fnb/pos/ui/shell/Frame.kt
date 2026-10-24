package ai.neargo.fnb.pos.ui.shell

import ai.neargo.fnb.pos.state.DetailSide
import ai.neargo.fnb.pos.state.NavSide
import ai.neargo.fnb.pos.state.PosFrame
import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosDims
import ai.neargo.fnb.pos.theme.PosType
import ai.neargo.fnb.pos.theme.num
import ai.neargo.fnb.pos.ui.components.Panel
import ai.neargo.fnb.pos.ui.components.PosFilterChip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 断点按应用宽度判断（《功能清单与布局方案》4.3）。
 * 窄屏断点从 1000 降到 960，给系统边距和分屏留余量（原型评审第 12 条）。
 */
enum class Tier { Compact, Medium, Expanded }

@Immutable
data class PosLayout(val tier: Tier, val frame: PosFrame, val width: Dp) {
    val wide get() = tier != Tier.Compact
    /** 详情区宽：≥1280 为 360，960–1279 为 320。 */
    val detailW get() = if (tier == Tier.Expanded) 360.dp else 320.dp
    /** 窄屏强制导航在上。 */
    val nav get() = if (wide) frame.nav else NavSide.Top
    val detail get() = frame.detail

    companion object {
        fun of(width: Dp, frame: PosFrame) = PosLayout(
            tier = when {
                width >= 1280.dp -> Tier.Expanded
                width >= 960.dp -> Tier.Medium
                else -> Tier.Compact
            },
            frame = frame,
            width = width,
        )
    }
}

val LocalPosLayout = staticCompositionLocalOf { PosLayout.of(1280.dp, PosFrame.L1) }

/**
 * 三区框架：导航区 Z3 · 内容区 Z4 · 详情区 Z5，位置按 L1–L6 摆放，三栏间距 pos-gap。
 * 导航在「上」时只盖住内容区，详情区保持满高（账单不被压短）。
 * 窄屏不画详情区，由页面自己收成底部条或抽屉。
 */
@Composable
fun FrameLayout(
    modifier: Modifier = Modifier,
    nav: (@Composable (vertical: Boolean, Modifier) -> Unit)? = null,
    detail: (@Composable (Modifier) -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val layout = LocalPosLayout.current
    val gap = PosDims.gap
    val sideNav = nav != null && layout.nav != NavSide.Top

    val center: @Composable (Modifier) -> Unit = { m ->
        if (nav != null && layout.nav == NavSide.Top) {
            Column(m, verticalArrangement = Arrangement.spacedBy(gap)) {
                nav(false, Modifier.fillMaxWidth())
                content(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            content(m)
        }
    }

    if (!layout.wide) {
        center(modifier.fillMaxSize().padding(horizontal = gap).padding(top = gap))
        return
    }

    Row(modifier.fillMaxSize().padding(gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
        val navSlot: @Composable () -> Unit = { nav?.invoke(true, Modifier.width(PosDims.railW).fillMaxHeight()) }
        val detailSlot: @Composable () -> Unit = { detail?.invoke(Modifier.width(layout.detailW).fillMaxHeight()) }

        if (detail != null && layout.detail == DetailSide.Left) detailSlot()
        if (sideNav && layout.nav == NavSide.Left) navSlot()
        center(Modifier.weight(1f).fillMaxHeight())
        if (sideNav && layout.nav == NavSide.Right) navSlot()
        if (detail != null && layout.detail == DetailSide.Right) detailSlot()
    }
}

data class NavItem(val id: String, val label: String, val count: Int = 0)

/** 导航区：竖排是导航栏（选中 tint 底 + 主色字，右上角数量），横排是筛选标签。 */
@Composable
fun NavArea(
    items: List<NavItem>,
    selected: String,
    onSelect: (String) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    /** 竖排时数量前的说明，例如「已点」；横排时数量直接拼在名字后。 */
    countLabel: String = "",
) {
    val c = Pos.colors
    if (!vertical) {
        Row(
            modifier.clip(PosDims.radiusFull).background(c.surface).horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { it ->
                PosFilterChip(if (it.count > 0) "${it.label} ${it.count}" else it.label, it.id == selected, { onSelect(it.id) })
            }
        }
        return
    }
    Panel(modifier) {
        LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.id }) { item ->
                val on = item.id == selected
                Box(
                    Modifier.fillMaxWidth().height(PosDims.ctl + 8.dp).clip(PosDims.radiusMd)
                        .background(if (on) c.primaryTint else Color.Transparent)
                        .clickable(role = Role.Tab) { onSelect(item.id) }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 96 宽的栏里角标会压住三字分类名，数量改放在名字下面一行
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            item.label, style = if (on) PosType.strong else PosType.body,
                            color = if (on) c.primaryText else c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (item.count > 0) {
                            Text(
                                if (countLabel.isEmpty()) "${item.count}" else "$countLabel ${item.count}",
                                style = PosType.caption.num(), color = c.primaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 宫格列数（4.3）：按所在区域的可用宽度算，再夹到断点规定的范围里。
 * 菜品 ≥1280 为 5–6 列、960–1279 为 4 列、窄屏 3 列（手机 2 列）；桌台 6 / 5 / 4（手机 2）。
 * 规范的列数是上限：标准密度或导航在侧边时宽度不够，按最小格宽少排一列，不把桌号、状态挤折行。
 */
enum class GridKind(val expanded: IntRange, val medium: IntRange, val compact: IntRange, val phone: Int, val minCell: Dp) {
    Menu(4..6, 3..4, 3..3, 2, 140.dp),
    Tables(5..6, 4..5, 4..4, 2, 124.dp),
}

fun gridColumns(kind: GridKind, layout: PosLayout, available: Dp, gap: Dp): Int {
    if (!layout.wide && layout.width < 600.dp) return kind.phone
    val range = when (layout.tier) {
        Tier.Expanded -> kind.expanded
        Tier.Medium -> kind.medium
        Tier.Compact -> kind.compact
    }
    val fit = ((available + gap) / (kind.minCell + gap)).toInt()
    return fit.coerceIn(range.first, range.last)
}
