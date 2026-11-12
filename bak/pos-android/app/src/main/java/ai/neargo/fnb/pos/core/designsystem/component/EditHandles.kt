package ai.neargo.fnb.pos.core.designsystem.component

import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/* 「编辑布局」状态下的拖动部件：分栏宽度把手、区块移动把手、落点提示。平时不出现。 */

/** 编辑布局时两栏之间的拖动把手：左右拖改宽度，回调 dp */
@Composable
fun WidthHandle(onDrag: (Float) -> Unit, label: String) {
    val density = LocalDensity.current
    Box(Modifier.width(14.dp).fillMaxHeight().pointerInput(Unit) {
        detectHorizontalDragGestures { ch, dx -> ch.consume(); onDrag(with(density) { dx.toDp().value }) }
    }.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.width(4.dp).height(56.dp).clip(Pos.metrics.button).background(Pos.colors.primary))
    }
}

/**
 * 编辑布局时贴在区块上的拖动把手：拖动时回调手指在窗口里的位置，松手回调最后的位置。
 */
@Composable
fun MoveHandle(label: String, onMove: (androidx.compose.ui.geometry.Offset?) -> Unit, onDrop: (androidx.compose.ui.geometry.Offset) -> Unit, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var acc by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Row(modifier.clip(Pos.metrics.button).background(Pos.colors.primary)
        .onGloballyPositioned { origin = it.positionInWindow() }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { p -> acc = origin + p },
                onDrag = { ch, d -> ch.consume(); acc += d; onMove(acc) },
                onDragEnd = { onDrop(acc); onMove(null) },
                onDragCancel = { onMove(null) },
            )
        }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✥ $label", style = Pos.type.label, color = Pos.colors.onPrimary)
    }
}

/** 编辑布局时的半透明落点提示 */
@Composable
fun DropZone(modifier: Modifier, text: String) {
    Box(modifier.clip(Pos.metrics.card).background(Pos.colors.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(text, style = Pos.type.strong, color = Pos.colors.primary)
    }
}
