package ai.neargo.fnb.pos.feature.common

import ai.neargo.fnb.pos.core.designsystem.component.*
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun AdjustmentDialog(order: TableOrder, audits: List<AdjustmentAudit>, onDismiss: () -> Unit,
    onSubmit: (AdjustmentRequest, String, String, (String?) -> Unit) -> Unit) {
    var kind by remember { mutableStateOf(AdjustmentKind.Void) }
    var uid by remember { mutableStateOf<Long?>(null) }
    var qty by remember { mutableStateOf("1") }
    var percent by remember { mutableStateOf(order.discountPercent.toString()) }
    var reason by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val choices = order.lines.filter { it.status != LineStatus.Queued && (kind != AdjustmentKind.Void || it.status != LineStatus.New) && (kind != AdjustmentKind.Gift || !it.gifted) }
    PosDialog("主管授权 · 退赠折扣", { if (!busy) onDismiss() }, maxWidth = 600.dp, subtitle = "本机商户账号可授权；体验账号使用演示主管", footer = {
        PosButton("取消", onDismiss, size = BtnSize.Large, enabled = !busy)
        PosButton("授权并执行", {
            val request = AdjustmentRequest(kind, uid, qty.toIntOrNull() ?: 0, percent.toIntOrNull() ?: 0, reason)
            val rejected = AdjustmentRules.apply(order, request) as? OrderResult.Rejected
            if (rejected != null) error = rejected.reason else {
                busy = true
                onSubmit(request, email, password) { message -> busy = false; password = ""; if (message == null) onDismiss() else error = message }
            }
        }, variant = BtnVariant.Primary, size = BtnSize.Large, enabled = !busy, loading = busy)
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdjustmentKind.entries.forEach { value -> PosButton(value.label, { kind = value; uid = null; error = null }, Modifier.weight(1f), selected = kind == value, enabled = !busy) }
        }
        if (kind == AdjustmentKind.Discount) {
            PosTextField(percent, { percent = it }, "折扣百分比（80 表示八折，100 恢复原价）", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("折扣在会员优惠后计算，不减服务费", style = Pos.type.label, color = Pos.colors.sub)
        } else {
            PosSelect("选择菜品", choices, choices.firstOrNull { it.uid == uid }, { "${it.name} · ${it.spec} ×${it.qty}" }, { uid = it.uid })
            if (choices.isEmpty()) Text("没有可操作的菜品", style = Pos.type.body, color = Pos.colors.sub)
            if (kind == AdjustmentKind.Void) PosTextField(qty, { qty = it }, "退菜份数", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            else Text("整行赠送；保留原价和份数，账单不收取该行金额", style = Pos.type.label, color = Pos.colors.sub)
        }
        PosTextField(reason, { reason = it }, "操作原因", hint = "2–100 字，将保留在操作记录中")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosTextField(email, { email = it }, "主管姓名或账号", Modifier.weight(1f))
            PosTextField(password, { password = it }, "主管 PIN 或密码", Modifier.weight(1f), password = true)
        }
        Text("演示主管：manager@demo.local / demo12345（仅直接体验可用）", style = Pos.type.label, color = Pos.colors.sub)
        error?.let { Text(it, color = Pos.colors.danger, style = Pos.type.label) }
        if (audits.isNotEmpty()) {
            Text("本桌操作记录", style = Pos.type.section, color = Pos.colors.ink)
            audits.takeLast(10).reversed().forEach { a ->
                Text("${a.kind.label} · ${a.lineName.ifBlank { a.detail }} · ${yuan(a.amount)}", style = Pos.type.strong, color = Pos.colors.ink)
                Text("${a.reason} · ${a.supervisor} · ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(a.at))}", style = Pos.type.label, color = Pos.colors.sub)
            }
        }
    }
}
