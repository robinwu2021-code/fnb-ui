package ai.neargo.fnb.pos.core.designsystem.component

import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.CashRules
import ai.neargo.fnb.pos.core.model.fenToInput
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun CashPad(value: String, due: Int, onChange: (String) -> Unit, enabled: Boolean = true) {
    PosTextField(value, { if (enabled && Regex("^\\d{0,5}(\\.\\d{0,2})?$").matches(it)) onChange(it) }, "现金实收（元）", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PosButton("刚好", { onChange(fenToInput(due)) }, Modifier.weight(1f), size = BtnSize.Small, enabled = enabled)
        listOf(20, 50, 100).forEach { n -> PosButton("¥$n", { onChange(n.toString()) }, Modifier.weight(1f), size = BtnSize.Small, enabled = enabled) }
    }
    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { key -> PosButton(key, { onChange(CashRules.key(value, key)) }, Modifier.weight(1f), enabled = enabled) } }
    }
    Row {
        val change = CashRules.change(value, due)
        Text(if (change != null) "找零 ${yuan(change)}" else if (value.isBlank()) "请填写实收金额" else "实收不足或金额格式不正确", style = Pos.type.strong.num(), color = if (change != null) Pos.colors.primary else Pos.colors.warning, modifier = Modifier.weight(1f))
        PosButton("清空", { onChange("") }, variant = BtnVariant.Text, size = BtnSize.Small, enabled = enabled)
    }
}
