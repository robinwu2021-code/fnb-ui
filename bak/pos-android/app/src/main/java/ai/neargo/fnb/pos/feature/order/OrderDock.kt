package ai.neargo.fnb.pos.feature.order

import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.EmptyState
import ai.neargo.fnb.pos.core.designsystem.component.GroupTitle
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosIconButton
import ai.neargo.fnb.pos.core.designsystem.component.TableStateLabel
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.designsystem.theme.tone
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * OrderDock：桌台身份 → 快捷入口 → 按状态分组的菜品清单（优先占空间）→ 金额与主次操作。
 * 结账为唯一实心主操作，送厨为次操作（主题浅底）。
 */
@Composable
fun OrderDock(state: OrderUiState, onAction: (OrderAction) -> Unit, onCheckout: () -> Unit, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    val c = Pos.colors
    val t = Pos.type
    val tb = state.table ?: return
    val totals = state.totals
    var detail by rememberSaveable { mutableStateOf(false) }

    Panel(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tb.id, style = t.title.num(), color = c.ink)
                    Spacer(Modifier.width(8.dp))
                    TableStateLabel(tb.status.label, tb.status.tone())
                    Spacer(Modifier.weight(1f))
                    PosButton("加菜", { onClose?.invoke() }, variant = BtnVariant.Text, size = BtnSize.Small)
                    if (onClose != null) PosIconButton(Icons.Rounded.Close, "关闭账单", onClose, size = 36.dp)
                    else PosIconButton(Icons.Rounded.MoreHoriz, "更多操作", { onAction(OrderAction.Notice("转台、并台、拆单按后续规范接入")) }, size = 36.dp)
                }
                Text("${tb.guests} 人 · 堂食 · 单号 #30${tb.id}", style = t.label.num(), color = c.sub)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val m = Modifier.weight(1f)
                PosButton("会员", { onAction(OrderAction.ToggleMember) }, m, size = BtnSize.Small, selected = state.order.member, textStyle = t.label, enabled = state.canOrder)
                PosButton("优惠券", { onAction(OrderAction.Notice("优惠券：满200减10（演示，结账时核对）")) }, m, size = BtnSize.Small, textStyle = t.label)
                PosButton("备注", { onAction(OrderAction.Notice("整单备注（演示）")) }, m, size = BtnSize.Small, textStyle = t.label)
                PosButton("挂单", { onAction(OrderAction.Notice("已挂单 #30${tb.id}（演示）")) }, m, size = BtnSize.Small, textStyle = t.label)
            }

            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                val pending = state.order.pending
                val sent = state.order.sent
                if (pending.isNotEmpty()) {
                    item(key = "g-new") { GroupTitle("新加 · 待送厨 · ${pending.sumOf { it.qty }} 份", action = "清空") { onAction(OrderAction.ClearNew) } }
                    items(pending, key = { it.uid }) { l -> OrderLineRow(l, { onAction(OrderAction.ChangeQty(l.uid, it)) }, null) }
                }
                if (sent.isNotEmpty()) {
                    item(key = "g-sent") { GroupTitle("已下单 · ${sent.sumOf { it.qty }} 份", action = "批量") { onAction(OrderAction.Notice("批量催菜 / 上菜（演示）")) } }
                    items(sent, key = { it.uid }) { l -> OrderLineRow(l, {}, { to -> onAction(OrderAction.Advance(l.uid, to)) }) }
                }
                if (state.order.lines.isEmpty()) {
                    item { EmptyState(if (state.canOrder) "还没有点菜。点菜品卡片加入" else "${tb.id} ${tb.status.label}，请先在桌台开台") }
                }
            }

            Column(Modifier.fillMaxWidth().background(c.control).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "金额明细") { detail = !detail }) {
                        Text("应收 · 明细 ${if (detail) "−" else "+"}", style = t.label, color = c.sub)
                        Text("${totals.items} 份", style = t.label.num(), color = c.sub)
                    }
                    Text(yuan(totals.due), style = t.amount.num(), color = c.ink, maxLines = 1, softWrap = false)
                }
                if (detail) {
                    KeyValue("菜品小计", yuan(totals.sub))
                    KeyValue("服务费 5%（演示）", yuan(totals.service))
                }
                Row {
                    Text("优惠 ${yuan(-totals.discount)}", style = t.label.num(), color = c.primary, modifier = Modifier.weight(1f))
                    Text("满200减10", style = t.label, color = c.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosButton(
                        if (totals.newItems > 0) "送厨 · ${totals.newItems}" else "送厨", { onAction(OrderAction.SendToKitchen) },
                        Modifier.weight(1f), variant = BtnVariant.Soft, size = BtnSize.Large, enabled = totals.newItems > 0,
                    )
                    PosButton("结账", onCheckout, Modifier.weight(1.2f), variant = BtnVariant.Primary, size = BtnSize.Large, enabled = totals.items > 0)
                }
            }
        }
    }
}
