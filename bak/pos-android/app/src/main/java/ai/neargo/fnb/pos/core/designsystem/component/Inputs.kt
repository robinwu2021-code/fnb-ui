package ai.neargo.fnb.pos.core.designsystem.component

import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.PosSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * PosTextField：标签常驻、轻底无灰框；错误就地显示且保留输入（规范 §6.3）。
 * 错误时用危险色 1dp 环提示（必要状态，不属于常规灰框）。
 */
@Composable
fun PosTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    hint: String? = null,
    password: Boolean = false,
    singleLine: Boolean = true,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = Pos.colors
    val t = Pos.type
    val focus = remember { FocusRequester() }
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // 点标签也能聚焦输入框
        Text(label, style = t.label, color = c.sub, modifier = Modifier.clickable(role = Role.Button, onClickLabel = "编辑$label") { focus.requestFocus() }.padding(bottom = 4.dp))
        BasicTextField(
            value, onChange, singleLine = singleLine,
            textStyle = t.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = keyboard,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).heightIn(min = PosSize.medium).clip(Pos.metrics.button).background(c.tile)
                .then(if (error != null) Modifier.border(1.dp, c.danger, Pos.metrics.button) else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { contentDescription = label; if (error != null) this.error(error) },
        )
        when {
            error != null -> Text(error, style = t.label, color = c.danger, modifier = Modifier.padding(top = 4.dp))
            hint != null -> Text(hint, style = t.label, color = c.sub, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** PosSelect：标签 + 轻底选择框，M3 DropdownMenu 展开选项。 */
@Composable
fun <T> PosSelect(label: String, options: List<T>, selected: T?, text: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Pos.colors
    val t = Pos.type
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = t.label, color = c.sub, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = PosSize.medium).clip(Pos.metrics.button).background(c.tile)
                    .clickable(role = Role.DropdownList, onClickLabel = label) { open = true }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected?.let(text) ?: "请选择", style = t.body, color = if (selected == null) c.sub else c.ink, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.sub)
            }
            DropdownMenu(open, { open = false }, containerColor = c.surface, shadowElevation = 2.dp) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(text(o), style = t.body) },
                        trailingIcon = { if (o == selected) Icon(Icons.Rounded.Check, null, tint = c.primary, modifier = Modifier.size(18.dp)) },
                        onClick = { open = false; onSelect(o) },
                    )
                }
            }
        }
    }
}

/** PosCheckRow：M3 Checkbox + 标签，整行可点；用于规格组、餐牌、商品多选。 */
@Composable
fun PosCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, trailing: String? = null, enabled: Boolean = true) {
    val c = Pos.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Pos.metrics.button).clickable(enabled = enabled, role = Role.Checkbox) { onChange(!checked) }.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = c.primary, uncheckedColor = c.sub))
        Text(label, style = Pos.type.body, color = if (enabled) c.ink else c.sub, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = Pos.type.label, color = c.sub)
    }
}
