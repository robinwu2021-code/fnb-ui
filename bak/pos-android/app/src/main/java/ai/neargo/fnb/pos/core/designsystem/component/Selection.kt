package ai.neargo.fnb.pos.core.designsystem.component

import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.PosSize
import ai.neargo.fnb.pos.core.designsystem.theme.num
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * PosChip：基于 M3 FilterChip。扁平：无描边、无阴影；轻底，选中主题浅底 + 主色字 + 600。
 * 用于快捷筛选、状态筛选、横排分类和设置选项。
 */
@Composable
fun PosChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    height: Dp = PosSize.medium,
) {
    val c = Pos.colors
    val t = Pos.type
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(height),
        label = {
            Text(text, style = t.label.copy(fontSize = t.body.fontSize * 0.93f, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
            if (count != null) {
                Spacer(Modifier.width(6.dp))
                Text("$count", style = t.label.num().copy(fontWeight = FontWeight.SemiBold))
            }
        },
        leadingIcon = leading,
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.tile, labelColor = c.ink,
            selectedContainerColor = c.tint, selectedLabelColor = c.primary,
        ),
        border = null,
        elevation = FilterChipDefaults.filterChipElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    )
}

/**
 * RailItem：基于 M3 可选中 Surface（selected + onClick，带涟漪与选中语义）。菜单分类与桌台区域共用。
 * 名称左、计数右；选中主题浅底 + 主色字 + 左侧 3dp 指示条（非底线、非阴影）。
 * 不用 NavigationDrawerItem：其固定内边距在 88dp 栏宽下会截断名称。
 */
@Composable
fun RailItem(label: String, count: Int?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Pos.colors
    val t = Pos.type
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(6.dp),
        color = if (selected) c.tint else Color.Transparent,
        contentColor = if (selected) c.primary else c.ink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) c.primary else Color.Transparent))
            Text(
                label, style = if (selected) t.body.copy(fontWeight = FontWeight.SemiBold) else t.body,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 5.dp),
            )
            if (count != null) Text("$count", style = t.label.num(), color = if (selected) c.primary else c.sub, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

/** QuantityStepper：M3 IconButton ×2 + 数量；轻底一体，键视觉 32。 */
@Composable
fun QuantityStepper(qty: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 0, max: Int = Int.MAX_VALUE) {
    val c = Pos.colors
    Row(modifier.clip(Pos.metrics.button).background(c.tile).padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
        StepKey(Icons.Rounded.Remove, "减少", enabled = qty > min) { onChange(-1) }
        Text(
            "$qty", style = Pos.type.label.num().copy(fontWeight = FontWeight.SemiBold), color = c.ink,
            modifier = Modifier.width(24.dp), textAlign = TextAlign.Center,
        )
        StepKey(Icons.Rounded.Add, "增加", enabled = qty < max) { onChange(1) }
    }
}

@Composable
private fun StepKey(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick, Modifier.size(32.dp), enabled = enabled, shape = Pos.metrics.button,
        colors = IconButtonDefaults.iconButtonColors(contentColor = Pos.colors.ink, disabledContentColor = Pos.colors.sub.copy(alpha = 0.4f)),
    ) { Icon(icon, label, Modifier.size(16.dp)) }
}

/** 搜索框：轻底无灰框，占位即标签；可清空；宽度可收缩，不独占工具栏。 */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, height: Dp = PosSize.medium) {
    val c = Pos.colors
    val t = Pos.type
    Row(
        modifier.height(height).clip(Pos.metrics.button).background(c.tile).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.sub, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = t.label.copy(fontSize = t.body.fontSize * 0.93f), color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = t.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            IconButton({ onChange("") }, Modifier.size(height)) { Icon(Icons.Rounded.Close, "清空搜索", tint = c.sub, modifier = Modifier.size(16.dp)) }
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

/** PosSwitch：M3 Switch，未选中轨道用控件底，无描边。 */
@Composable
fun PosSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Pos.colors
    Switch(
        checked, onChange, modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary, checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = c.control, uncheckedThumbColor = c.sub, uncheckedBorderColor = Color.Transparent,
        ),
    )
}
