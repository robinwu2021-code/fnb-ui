package ai.neargo.fnb.pos.feature.common

import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.ActionLayout
import ai.neargo.fnb.pos.core.model.BizType
import ai.neargo.fnb.pos.core.model.RailConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 竖条上的一个操作 */
data class RailItem(val id: String, val label: String, val enabled: Boolean, val onClick: () -> Unit)

/**
 * 混合模式的中间竖条：只放「选中的这一行」的操作。顶上是选中行名称与数量步进（固定，不能拖走），
 * 下面按配置顺序排操作（不适用的置灰，位置不变），底部「更多 ›」放低频操作。
 */
@Composable
fun ActionRail(
    modifier: Modifier,
    title: String,
    qty: Int?,
    onQty: ((Int) -> Unit)?,
    items: List<RailItem>,
    more: List<RailItem>,
) {
    val c = Pos.colors; val t = Pos.type
    var open by remember { mutableStateOf(false) }
    Panel(modifier) {
        Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = t.label, color = c.sub, maxLines = 2, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            // 数量步进：－ / 数量 / ＋
            Column(Modifier.fillMaxWidth().clip(Pos.metrics.button)) {
                val on = qty != null && onQty != null
                RailCell("－", on, c.primary, c.onPrimary) { onQty?.invoke(-1) }
                Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).background(c.tile), contentAlignment = Alignment.Center) {
                    Text(qty?.toString() ?: "—", style = t.strong.copy(fontSize = t.strong.fontSize * 1.2f), color = c.ink)
                }
                RailCell("＋", on, c.primary, c.onPrimary) { onQty?.invoke(1) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items.forEach { i -> RailButton(i) }
            }
            if (more.isNotEmpty()) Box {
                PosButton("更多 ›", { open = true }, Modifier.fillMaxWidth(), size = BtnSize.Medium, textStyle = t.label)
                DropdownMenu(open, { open = false }, containerColor = c.surface, shadowElevation = 2.dp) {
                    more.forEach { m -> DropdownMenuItem({ Text(m.label, style = t.body) }, { open = false; m.onClick() }, enabled = m.enabled) }
                }
            }
        }
    }
}

@Composable
private fun RailCell(text: String, enabled: Boolean, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).background(if (enabled) bg else Pos.colors.control).clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = if (text == "＋") "加一" else "减一" }, contentAlignment = Alignment.Center) {
        Text(text, style = Pos.type.strong, color = if (enabled) fg else Pos.colors.sub)
    }
}

/**
 * 编辑竖条：一整条列表，竖条里的在分隔线上面、「更多」里的在下面；长按拖动排序，拖过分隔线就是进出竖条。
 * 不合规（整单 / 桌台 / 设备操作进竖条、超过上限）松手时弹回并提示。
 */
@Composable
fun RailEditor(modifier: Modifier, biz: BizType, cfg: RailConfig, onChange: (RailConfig) -> Unit, onError: (String) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val rowH = 44.dp
    val rowPx = with(LocalDensity.current) { (rowH + 4.dp).toPx() }
    val list = cfg.rail + ActionLayout.DIVIDER + cfg.more
    var dragging by remember { mutableIntStateOf(-1) }
    var dy by remember { mutableFloatStateOf(0f) }
    Panel(modifier) {
        Column(Modifier.fillMaxSize().padding(6.dp)) {
            Text("长按拖动", style = t.label, color = c.primary, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = TextAlign.Center)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                list.forEachIndexed { i, id ->
                    val divider = id == ActionLayout.DIVIDER
                    val lifted = i == dragging
                    val label = if (divider) "— 以下在「更多」 —" else ActionLayout.spec(biz, id)?.label ?: id
                    Box(
                        Modifier.fillMaxWidth().height(rowH)
                            .graphicsLayer { if (lifted) { translationY = dy; shadowElevation = 12f; scaleX = 1.04f; scaleY = 1.04f } }
                            .clip(Pos.metrics.button)
                            .background(when { divider -> c.canvas; lifted -> c.tint; else -> c.control })
                            .then(if (divider) Modifier else Modifier.pointerInput(list) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = i; dy = 0f },
                                    onDrag = { ch, d -> ch.consume(); dy += d.y },
                                    onDragCancel = { dragging = -1; dy = 0f },
                                    onDragEnd = {
                                        val to = (i + (dy / rowPx).roundToInt()).coerceIn(0, list.size - 1)
                                        val moved = ActionLayout.drag(list, i, to)
                                        ActionLayout.fromList(moved.filter { it != ActionLayout.DIVIDER }, moved.indexOf(ActionLayout.DIVIDER), biz)
                                            .onSuccess(onChange).onFailure { onError(it.message ?: "不能这样放") }
                                        dragging = -1; dy = 0f
                                    },
                                )
                            }),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = t.label, color = if (divider) c.sub else c.ink, maxLines = 1) }
                }
            }
        }
    }
}

/** 整单操作（混合模式账单头的小图标行） */
@Composable
fun OrderIcons(items: List<RailItem>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { i -> PosButton(i.label, i.onClick, Modifier.weight(1f), variant = BtnVariant.Text, size = BtnSize.Small, enabled = i.enabled, textStyle = Pos.type.label) }
    }
}

/** 竖条按钮：文字可折两行（竖条窄，「临时商品」这类四个字也放得下），不适用的置灰 */
@Composable
private fun RailButton(i: RailItem) {
    val c = Pos.colors
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Pos.metrics.button).background(c.control)
        .clickable(enabled = i.enabled, onClick = i.onClick).padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(i.label, style = Pos.type.label, color = if (i.enabled) c.ink else c.sub.copy(alpha = 0.55f), maxLines = 2, textAlign = TextAlign.Center)
    }
}
