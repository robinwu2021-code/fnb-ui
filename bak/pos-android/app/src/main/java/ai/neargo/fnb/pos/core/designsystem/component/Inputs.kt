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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.focusProperties
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
    /** 出现时自动获得焦点（弹窗里唯一 / 第一个要填的框用） */
    autoFocus: Boolean = false,
    /** 键盘「前往 / 完成」时执行（表单最后一个框直接提交） */
    onGo: (() -> Unit)? = null,
) {
    val c = Pos.colors
    val t = Pos.type
    val focus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val root = androidx.compose.ui.platform.LocalView.current.rootView
    val entry = remember { FieldNav.Entry(root, focus) }
    androidx.compose.runtime.DisposableEffect(entry) { FieldNav.add(entry); onDispose { FieldNav.remove(entry) } }
    // 单行框按键盘「下一项」跳到下一个输入框（表单一路填下去，不用伸手点被键盘挡住的框）；调用方指定了动作就照它的
    val opts = if (singleLine && (keyboard.imeAction == androidx.compose.ui.text.input.ImeAction.Default || keyboard.imeAction == androidx.compose.ui.text.input.ImeAction.Unspecified)) keyboard.copy(imeAction = androidx.compose.ui.text.input.ImeAction.Next) else keyboard
    if (autoFocus) androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // 点标签也能聚焦输入框
        Text(label, style = t.label, color = c.sub, modifier = Modifier.focusProperties { canFocus = false }.clickable(role = Role.Button, onClickLabel = "编辑$label") { focus.requestFocus() }.padding(bottom = 4.dp))
        BasicTextField(
            value, onChange, singleLine = singleLine,
            textStyle = t.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = opts,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                // 「下一个」：同一屏里紧挨着在下面的输入框；下面没有了就收起键盘（不绕回、不跳到按钮上）
                onNext = { FieldNav.below(entry)?.focus?.let { runCatching { it.requestFocus() } } ?: focusManager.clearFocus() },
                onGo = { focusManager.clearFocus(); onGo?.invoke() }, onDone = { focusManager.clearFocus(); onGo?.invoke() }),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                .onGloballyPositioned { entry.y = it.positionInWindow().y; entry.x = it.positionInWindow().x }.heightIn(min = PosSize.medium).clip(Pos.metrics.button).background(c.tile)
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

/** 「下一个」跳转：登记当前显示的输入框（所在窗口与位置），找同一窗口里下面最近的一个 */
private object FieldNav {
    class Entry(val root: android.view.View, val focus: FocusRequester) { var x = 0f; var y = 0f }
    private val all = mutableListOf<Entry>()
    fun add(e: Entry) { all += e }
    fun remove(e: Entry) { all -= e }
    /** 同一窗口里在它下面的（同一行靠右的也算），取最近的 */
    fun below(e: Entry): Entry? = all.filter { it !== e && it.root === e.root && (it.y > e.y + 1 || (kotlin.math.abs(it.y - e.y) <= 1 && it.x > e.x)) }
        .minWithOrNull(compareBy({ it.y }, { it.x }))
}
