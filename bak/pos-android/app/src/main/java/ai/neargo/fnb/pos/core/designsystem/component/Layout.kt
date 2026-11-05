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

/** 工作台双栏：主面板 + 侧栏（账单坞 / 桌台详情），侧栏位置跟随布局设置；两栏之间只露画布间隙。 */
@Composable
fun Workbench(
    side: BillSide,
    sideWidth: Dp,
    modifier: Modifier = Modifier,
    aside: @Composable (Modifier) -> Unit,
    main: @Composable (Modifier) -> Unit,
) {
    val gap = Pos.metrics.gap
    Row(modifier.fillMaxSize().padding(gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
        if (side == BillSide.Left) aside(Modifier.width(sideWidth).fillMaxHeight())
        main(Modifier.weight(1f).fillMaxHeight())
        if (side == BillSide.Right) aside(Modifier.width(sideWidth).fillMaxHeight())
    }
}
