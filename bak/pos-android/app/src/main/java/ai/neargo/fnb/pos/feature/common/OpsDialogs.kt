package ai.neargo.fnb.pos.feature.common

import ai.neargo.fnb.pos.app.appContainer
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosDivider
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.OrderRules
import ai.neargo.fnb.pos.core.model.PromotionKind
import ai.neargo.fnb.pos.core.model.StoreData
import ai.neargo.fnb.pos.core.model.TableOrder
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import kotlinx.coroutines.launch

/** 本单会员：按手机号/姓名查找并绑定，可当场新建；已绑定可解绑。 */
@Composable
fun MemberPickDialog(store: StoreData, tableId: String, order: TableOrder, onDismiss: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun bind(id: String?) = scope.launch {
        val r = c.ops.bindMember(tableId, id)
        if (r.isSuccess) { c.messages.post(if (id == null) "已解绑会员" else "已绑定会员，享会员折扣"); onDismiss() } else error = r.exceptionOrNull()?.message
    }
    val hits = store.members.filter { q.isNotBlank() && (it.phone.contains(q.trim()) || it.name.contains(q.trim())) }.take(8)
    PosDialog("本单会员", onDismiss, subtitle = if (order.memberId.isNotBlank()) "当前：${order.memberName}" else "会员折扣 ${100 - order.memberBp / 100} 折起算，储值与积分记入会员", footer = {
        if (order.member) PosButton("解绑会员", { bind(null) }, size = BtnSize.Large, variant = BtnVariant.Danger)
        PosButton("关闭", onDismiss, size = BtnSize.Large)
        if (creating) PosButton("新建并绑定", {
            scope.launch {
                val r = c.ops.createMember(q, name)
                r.onSuccess { m -> bind(m.id) }.onFailure { error = it.message }
            }
        }, variant = BtnVariant.Primary, size = BtnSize.Large)
    }) {
        PosTextField(q, { q = it.take(11); error = null }, "手机号或姓名", error = error, keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone))
        hits.forEach { m ->
            KeyValue("${m.name} · ${m.phone}", "余额 ${yuan(m.balance)} · 积分 ${m.points}") {
                PosButton(if (m.id == order.memberId) "已绑定" else "绑定", { bind(m.id) }, size = BtnSize.Small, variant = BtnVariant.Primary, enabled = m.id != order.memberId)
            }
        }
        if (q.isNotBlank() && hits.isEmpty()) Text("没有找到会员", style = Pos.type.label, color = Pos.colors.sub, modifier = Modifier.padding(vertical = 6.dp))
        PosDivider(Modifier.padding(vertical = 6.dp))
        if (!creating) PosButton("新建会员", { creating = true }, size = BtnSize.Small)
        else {
            Text("以上方输入的 11 位手机号新建会员", style = Pos.type.label, color = Pos.colors.sub)
            PosTextField(name, { name = it.take(20) }, "姓名（可选）")
        }
    }
}

/** 本单优惠活动：多选；门槛不足的显示原因；整单折扣类只能一个。 */
@Composable
fun PromotionPickDialog(store: StoreData, tableId: String, order: TableOrder, onDismiss: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(order.promotions.map { it.id }.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    val sub = OrderRules.totals(order.copy(promotions = emptyList(), coupon = false, activity = false)).sub
    PosDialog("优惠活动", onDismiss, subtitle = "菜品小计 ${yuan(sub)}", footer = {
        PosButton("取消", onDismiss, size = BtnSize.Large)
        PosButton("应用", {
            scope.launch {
                val r = c.ops.setPromotions(tableId, chosen.toList())
                if (r.isSuccess) { c.messages.post(if (chosen.isEmpty()) "已取消优惠活动" else "已应用 ${chosen.size} 个活动"); onDismiss() } else error = r.exceptionOrNull()?.message
            }
        }, variant = BtnVariant.Primary, size = BtnSize.Large)
    }) {
        val list = store.promotions.filter { it.enabled }
        if (list.isEmpty()) Text("没有启用的活动，可在「系统管理 → 优惠活动」配置", style = Pos.type.body, color = Pos.colors.sub)
        list.forEach { p ->
            val ok = sub >= p.threshold
            KeyValue(p.name, p.summary + if (!ok) " · 未满门槛" else "") {
                Tag(p.kind.label)
                PosButton(if (p.id in chosen) "已选" else "选用", {
                    chosen = if (p.id in chosen) chosen - p.id
                    else (if (p.kind == PromotionKind.Percent) chosen.filterNot { id -> store.promotions.firstOrNull { it.id == id }?.kind == PromotionKind.Percent }.toSet() else chosen) + p.id
                }, size = BtnSize.Small, selected = p.id in chosen, enabled = ok || p.id in chosen)
            }
        }
        if (error != null) Text(error!!, style = Pos.type.label, color = Pos.colors.danger, modifier = Modifier.padding(top = 6.dp))
    }
}
