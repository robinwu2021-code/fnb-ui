package ai.neargo.fnb.pos.core.designsystem.component

import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.BillSide
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 自适应断点：按应用实际可用宽度判断，不按设备型号或横竖屏。 */
@Immutable
data class PosLayout(val width: Dp) {
    /** 宽屏：菜单 + 账单、桌台 + 详情双栏。 */
    val wide get() = width >= 960.dp
    /** 账单坞宽：≥1190dp 为 350，否则 340。 */
    val dockW get() = if (width >= 1190.dp) 350.dp else 340.dp
    /** 手机：页头精简、菜品两列。 */
    val phone get() = width < 600.dp
}

val LocalPosLayout = staticCompositionLocalOf { PosLayout(1280.dp) }

/**
 * 工作台：主面板 + 侧栏（账单坞 / 桌台详情），侧栏位置跟随布局设置；两栏之间只露画布间隙。
 * 混合模式在主面板和侧栏之间多一条竖条（rail）。
 * 编辑布局时：栏间出现宽度把手（拖动改侧栏 / 竖条宽），侧栏顶上出现移动把手（拖到左半边 / 右半边换位置）。
 */
@Composable
fun Workbench(
    side: BillSide,
    sideWidth: Dp,
    modifier: Modifier = Modifier,
    aside: @Composable (Modifier) -> Unit,
    rail: (@Composable (Modifier) -> Unit)? = null,
    railWidth: Dp = 80.dp,
    editing: Boolean = false,
    onSideWidth: (Float) -> Unit = {},
    onRailWidth: (Float) -> Unit = {},
    onSide: ((BillSide) -> Unit)? = null,
    main: @Composable (Modifier) -> Unit,
) {
    val gap = Pos.metrics.gap
    var box by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var drag by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val left = side == BillSide.Left
    // 把手往「侧栏变宽」的方向拖为正
    val sideHandle: @Composable () -> Unit = { if (editing) WidthHandle({ dx -> onSideWidth(if (left) dx else -dx) }, "拖动调整账单宽度") }
    val railHandle: @Composable () -> Unit = { if (editing && rail != null) WidthHandle({ dx -> onRailWidth(if (left) dx else -dx) }, "拖动调整竖条宽度") }
    val asideBox: @Composable () -> Unit = {
        Box(Modifier.width(sideWidth).fillMaxHeight()) {
            aside(Modifier.fillMaxSize())
            if (editing && onSide != null) MoveHandle("拖到左 / 右", onMove = { drag = it }, onDrop = { p -> onSide(ai.neargo.fnb.pos.core.model.ActionLayout.billDrop(p.x - box.left, box.width)) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
        }
    }
    Box(modifier.fillMaxSize().onGloballyPositioned { box = it.boundsInWindow() }) {
        Row(Modifier.fillMaxSize().padding(gap), horizontalArrangement = Arrangement.spacedBy(if (editing) 0.dp else gap)) {
            if (left) { asideBox(); sideHandle(); rail?.let { it(Modifier.width(railWidth).fillMaxHeight()); railHandle() } }
            main(Modifier.weight(1f).fillMaxHeight())
            if (!left) { rail?.let { railHandle(); it(Modifier.width(railWidth).fillMaxHeight()) }; sideHandle(); asideBox() }
        }
        // 拖动中：提示会落到哪半边
        drag?.let { p ->
            val toLeft = ai.neargo.fnb.pos.core.model.ActionLayout.billDrop(p.x - box.left, box.width) == BillSide.Left
            Row(Modifier.fillMaxSize().padding(gap)) {
                if (toLeft) DropZone(Modifier.weight(1f).fillMaxHeight(), "放到左边") else Spacer(Modifier.weight(1f))
                if (!toLeft) DropZone(Modifier.weight(1f).fillMaxHeight(), "放到右边") else Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 分类 / 区域栏宽：中文 88dp；英文词更长，放宽到 112dp，避免截断。 */
@Composable
fun railWidth(): Dp = if (LocalLang.current == ai.neargo.fnb.pos.core.model.Lang.En) 112.dp else ai.neargo.fnb.pos.core.designsystem.theme.PosSize.rail
