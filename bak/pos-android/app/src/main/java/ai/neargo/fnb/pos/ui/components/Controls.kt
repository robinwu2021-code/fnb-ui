package ai.neargo.fnb.pos.ui.components

import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosDims
import ai.neargo.fnb.pos.theme.PosType
import ai.neargo.fnb.pos.theme.num
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class BtnVariant { Solid, Soft, Muted, Danger, DangerSolid }

/** sh-btn：胶囊按钮。一屏只有一个 Solid。按下下沉 1px。 */
@Composable
fun PosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BtnVariant = BtnVariant.Solid,
    height: Dp = PosDims.ctl,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = Pos.colors
    val (bg, fg) = when (variant) {
        BtnVariant.Solid -> c.primary to c.onPrimary
        BtnVariant.Soft -> c.primaryTint to c.primaryText
        BtnVariant.Muted -> c.faint to c.ink
        BtnVariant.Danger -> Color.Transparent to c.dangerText
        BtnVariant.DangerSolid -> c.danger to Color.White
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .offset(y = if (pressed) 1.dp else 0.dp)
            .height(height)
            .clip(PosDims.radiusFull)
            .background(bg.copy(alpha = if (enabled) bg.alpha else bg.alpha * 0.5f))
            .then(if (variant == BtnVariant.Danger) Modifier.border(1.dp, c.danger, PosDims.radiusFull) else Modifier)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(6.dp)) }
        Text(text, style = PosType.strong, color = if (enabled) fg else fg.copy(alpha = 0.5f), maxLines = 1)
    }
}

/** DiningQuantityStepper：中性块底，数量等宽数字。 */
@Composable
fun QuantityStepper(qty: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 0) {
    val c = Pos.colors
    Row(
        modifier.height(PosDims.ctlS).clip(PosDims.radiusFull).background(c.faint),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepKey(Icons.Rounded.Remove, "减少", enabled = qty > min) { onChange(-1) }
        Text(
            "$qty", style = PosType.strong.num(), color = c.ink,
            modifier = Modifier.width(28.dp), textAlign = TextAlign.Center,
        )
        StepKey(Icons.Rounded.Add, "增加") { onChange(1) }
    }
}

@Composable
private fun StepKey(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(PosDims.ctlS).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) Pos.colors.ink else Pos.colors.sub, modifier = Modifier.size(18.dp))
    }
}

enum class Tone { Neutral, Primary, Success, Warning, Danger, Info }

/** DiningStatusLabel：圆点 + 文字，标签不加粗。 */
@Composable
fun StatusLabel(text: String, tone: Tone, modifier: Modifier = Modifier, dot: Boolean = true) {
    val c = Pos.colors
    val (bg, fg, dotColor) = when (tone) {
        Tone.Neutral -> Triple(c.faint, c.sub, c.sub)
        Tone.Primary -> Triple(c.primaryTint, c.primaryText, c.primary)
        Tone.Success -> Triple(c.successTint, c.successText, c.success)
        Tone.Warning -> Triple(c.warningTint, c.warningText, c.warning)
        Tone.Danger -> Triple(c.dangerTint, c.dangerText, c.danger)
        Tone.Info -> Triple(c.infoTint, c.infoText, c.infoText)
    }
    Row(
        modifier.clip(PosDims.radiusFull).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = PosType.caption, color = fg, maxLines = 1, softWrap = false)
    }
}

/** FilterChip：选中用 tint 底 + 主色字（底色和字色两处同时变）。 */
@Composable
fun PosFilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Pos.colors
    Box(
        modifier
            .defaultMinSize(minHeight = PosDims.ctlS)
            .clip(PosDims.radiusFull)
            .background(if (selected) c.primaryTint else c.faint)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PosType.body, color = if (selected) c.primaryText else c.ink)
    }
}

/** SegmentedControl：中性块底，选中段为 surface。 */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Pos.colors
    Row(modifier.height(PosDims.ctlS).clip(PosDims.radiusFull).background(c.faint).padding(3.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier
                    .height(30.dp)
                    .clip(PosDims.radiusFull)
                    .background(if (on) c.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(o) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), style = if (on) PosType.sub.copy(fontWeight = PosType.strong.fontWeight) else PosType.sub, color = if (on) c.ink else c.sub)
            }
        }
    }
}

/** 数量角标：主色实心。 */
@Composable
fun CountBadge(n: Int, modifier: Modifier = Modifier) {
    val c = Pos.colors
    Box(
        modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(PosDims.radiusFull).background(c.primary).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", style = PosType.caption.num(), color = c.onPrimary)
    }
}

/** 面板：surface + 大圆角，不用描边和阴影。 */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(PosDims.radiusLg).background(Pos.colors.surface)) { content() }
}

/** 金额明细的一行。 */
@Composable
fun AmountRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Pos.colors.ink, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PosType.sub, color = Pos.colors.sub)
        if (trailing != null) { Spacer(Modifier.width(6.dp)); trailing() }
        Spacer(Modifier.weight(1f))
        Text(value, style = PosType.sub.num(), color = valueColor)
    }
}
