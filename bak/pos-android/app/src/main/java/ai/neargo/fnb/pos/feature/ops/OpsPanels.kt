package ai.neargo.fnb.pos.feature.ops

import ai.neargo.fnb.pos.core.common.PayMethods
import ai.neargo.fnb.pos.app.appContainer
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.GroupTitle
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.Member
import ai.neargo.fnb.pos.core.model.Money
import ai.neargo.fnb.pos.core.model.Promotion
import ai.neargo.fnb.pos.core.model.PromotionKind
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun time(ms: Long) = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ms))

/** 会员管理：搜索、新建、充值、流水。 */
@Composable
fun MembersPanel() {
    val c = appContainer()
    val store by c.config.current.collectAsStateWithLifecycle()
    val s = store ?: return
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var recharging by remember { mutableStateOf<Member?>(null) }
    var detail by remember { mutableStateOf<Member?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosTextField(q, { q = it }, "搜索手机号或姓名", Modifier.weight(1f))
            PosButton("新建会员", { creating = true }, variant = BtnVariant.Primary, modifier = Modifier.padding(top = 20.dp))
        }
        KeyValue("会员总数", "${s.members.size} 人 · 储值余额合计 ${yuan(Money.sum(s.members.map { it.balance }))}")
        val list = s.members.filter { q.isBlank() || it.phone.contains(q.trim()) || it.name.contains(q.trim()) }.sortedByDescending { it.createdAt }
        if (list.isEmpty()) Text(if (s.members.isEmpty()) "还没有会员" else "没有找到会员", style = Pos.type.body, color = Pos.colors.sub)
        list.take(50).forEach { m ->
            KeyValue("${m.name} · ${m.phone}", "余额 ${yuan(m.balance)} · 积分 ${m.points} · 消费 ${yuan(m.totalSpent)}（${m.visits} 次）") {
                PosButton("流水", { detail = m }, size = BtnSize.Small)
                PosButton("充值", { recharging = m }, size = BtnSize.Small, variant = BtnVariant.Primary)
            }
        }
    }
    if (creating) {
        var phone by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        PosDialog("新建会员", { creating = false }, footer = {
            PosButton("取消", { creating = false }, size = BtnSize.Large)
            PosButton("保存", { scope.launch { val r = c.ops.createMember(phone, name); error = r.exceptionOrNull()?.message; if (r.isSuccess) { creating = false; c.messages.post("已新建会员") } } }, variant = BtnVariant.Primary, size = BtnSize.Large)
        }) {
            PosTextField(phone, { phone = it.filter(Char::isDigit).take(11) }, "手机号", keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone), error = error)
            PosTextField(name, { name = it.take(20) }, "姓名（可选）")
        }
    }
    recharging?.let { m ->
        var amount by remember { mutableStateOf("") }
        var method by remember { mutableStateOf(PayMethods.CASH) }
        var error by remember { mutableStateOf<String?>(null) }
        PosDialog("会员充值", { recharging = null }, subtitle = "${m.name} · ${m.phone} · 当前余额 ${yuan(m.balance)}", footer = {
            PosButton("取消", { recharging = null }, size = BtnSize.Large)
            PosButton("确认充值", {
                val fen = Money.parse(amount, 5_000_000)
                if (fen == null || fen <= 0) error = "充值金额应为 0.01–50000 元"
                else scope.launch { val r = c.ops.recharge(m.id, fen, method); error = r.exceptionOrNull()?.message; if (r.isSuccess) { recharging = null; c.messages.post("已充值 ${yuan(fen)}") } }
            }, variant = BtnVariant.Primary, size = BtnSize.Large)
        }) {
            PosTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) }, "充值金额（元）", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal), error = error)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(PayMethods.CASH, PayMethods.QR, PayMethods.CARD).filter { it in s.settings.payMethods }.forEach { k -> PosButton(k, { method = k }, size = BtnSize.Small, selected = method == k) }
            }
        }
    }
    detail?.let { m ->
        PosDialog("余额与积分流水", { detail = null }, subtitle = "${m.name} · ${m.phone}", footer = { PosButton("关闭", { detail = null }, size = BtnSize.Large) }) {
            val txns = s.memberTxns.filter { it.memberId == m.id }.sortedByDescending { it.at }
            if (txns.isEmpty()) Text("暂无流水", style = Pos.type.body, color = Pos.colors.sub)
            txns.take(50).forEach { t ->
                KeyValue("${time(t.at)} · ${t.kind.label}${if (t.method.isNotBlank()) " · ${t.method}" else ""}",
                    if (t.points > 0) "+${t.points} 分" else "${if (t.amount >= 0) "+" else ""}${yuan(t.amount)} → ${yuan(t.balanceAfter)}")
            }
        }
    }
}

/** 优惠活动：满减 / 整单折扣，可启停；订单使用时保存快照。 */
@Composable
fun PromotionsPanel() {
    val c = appContainer()
    val store by c.config.current.collectAsStateWithLifecycle()
    val s = store ?: return
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Promotion?>(null) }
    val save: (Promotion, (String?) -> Unit) -> Unit = { p, done -> scope.launch { val r = c.ops.savePromotion(p); done(r.exceptionOrNull()?.message); if (r.isSuccess) c.messages.post("已保存活动") } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PosButton("新建活动", { editing = Promotion("promo-" + UUID.randomUUID().toString().take(8), "", PromotionKind.Threshold) }, variant = BtnVariant.Primary)
        s.promotions.forEach { p ->
            KeyValue(p.name, "${p.kind.label} · ${p.summary}") {
                if (!p.enabled) Tag("已停用")
                PosButton(if (p.enabled) "停用" else "启用", { save(p.copy(enabled = !p.enabled)) {} }, size = BtnSize.Small)
                PosButton("编辑", { editing = p }, size = BtnSize.Small)
            }
        }
        Text("满减按菜品小计判断门槛；整单折扣在会员与主管折扣之后计算，同一订单只能用一个。", style = Pos.type.label, color = Pos.colors.sub)
    }
    editing?.let { p ->
        var name by remember { mutableStateOf(p.name) }
        var kind by remember { mutableStateOf(p.kind) }
        var threshold by remember { mutableStateOf(if (p.threshold > 0) ai.neargo.fnb.pos.core.model.fenToInput(p.threshold) else "") }
        var amount by remember { mutableStateOf(if (p.amount > 0) ai.neargo.fnb.pos.core.model.fenToInput(p.amount) else "") }
        var rate by remember { mutableStateOf(if (p.kind == PromotionKind.Percent) "${p.percent / 10.0}".removeSuffix(".0") else "") }
        var error by remember { mutableStateOf<String?>(null) }
        PosDialog(if (p.name.isBlank()) "新建活动" else "编辑活动", { editing = null }, footer = {
            PosButton("取消", { editing = null }, size = BtnSize.Large)
            PosButton("保存", {
                val q = p.copy(name = name, kind = kind, threshold = Money.parse(threshold.ifBlank { "0" }) ?: -1, amount = Money.parse(amount.ifBlank { "0" }) ?: -1,
                    percent = rate.toDoubleOrNull()?.let { Math.round(it * 10).toInt() } ?: 100)
                save(q) { e -> error = e; if (e == null) editing = null }
            }, variant = BtnVariant.Primary, size = BtnSize.Large)
        }) {
            PosTextField(name, { name = it.take(20) }, "活动名称")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                PromotionKind.entries.forEach { k -> PosButton(k.label, { kind = k }, size = BtnSize.Small, selected = kind == k) }
            }
            PosTextField(threshold, { threshold = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) }, if (kind == PromotionKind.Threshold) "满（元）" else "门槛（元，0 不限）", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (kind == PromotionKind.Threshold) PosTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) }, "减（元）", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal), error = error)
            else PosTextField(rate, { rate = it.filter { ch -> ch.isDigit() || ch == '.' }.take(3) }, "折扣（折，5–9.9）", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal), error = error)
        }
    }
}
