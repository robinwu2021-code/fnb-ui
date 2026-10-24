package ai.neargo.fnb.pos.ui.order

import ai.neargo.fnb.pos.data.yuan
import ai.neargo.fnb.pos.state.DineMode
import ai.neargo.fnb.pos.state.OrderLine
import ai.neargo.fnb.pos.state.PosStore
import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosDims
import ai.neargo.fnb.pos.theme.PosType
import ai.neargo.fnb.pos.theme.num
import ai.neargo.fnb.pos.ui.components.AmountRow
import ai.neargo.fnb.pos.ui.components.BtnVariant
import ai.neargo.fnb.pos.ui.components.Panel
import ai.neargo.fnb.pos.ui.components.PosButton
import ai.neargo.fnb.pos.ui.components.QuantityStepper
import ai.neargo.fnb.pos.ui.components.Segmented
import ai.neargo.fnb.pos.ui.components.StatusLabel
import ai.neargo.fnb.pos.ui.components.Tone
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox

/** Z2 + Z5 + Z6：桌台订单条、订单明细、金额与操作区。 */
@Composable
fun OrderDock(store: PosStore, onCheckout: () -> Unit, modifier: Modifier = Modifier) {
    val c = Pos.colors
    Panel(modifier) {
        Column {
            // Z2 桌台订单条
            Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(store.tableId, style = PosType.display.num(), color = c.ink)
                    Text("${store.guests} 人 · ${store.minutes} 分钟 · ${store.orderNo}", style = PosType.caption.num(), color = c.sub)
                }
                Segmented(DineMode.entries, store.mode, { it.label }, { store.mode = it })
            }
            HorizontalDivider(color = c.faint)

            // Z5 明细：待下单在上（可改），已下单在下（只能催菜）
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (store.pending.isNotEmpty()) {
                    item { Section("待下单", store.pending.sumOf { it.qty }) }
                    items(store.pending, key = { it.uid }) { l -> PendingLine(store, l) }
                }
                if (store.sent.isNotEmpty()) {
                    item { Section("已下单", store.sent.sumOf { it.qty }) }
                    items(store.sent, key = { it.uid }) { l -> SentLine(store, l) }
                }
                if (store.lines.isEmpty()) {
                    item {
                        Text(
                            "还没有点菜。点左侧菜品加入",
                            style = PosType.body, color = c.sub,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }

            // Z6 金额与操作区
            HorizontalDivider(color = c.faint)
            val t = store.totals
            Column(Modifier.padding(16.dp, 10.dp)) {
                AmountRow("小计", yuan(t.sub))
                AmountRow("服务费 10%", yuan(t.service))
                AmountRow("会员价 95 折", if (t.member > 0) yuan(-t.member) else "未绑定", valueColor = if (t.member > 0) c.primaryText else c.sub) {
                    MiniSwitch(store.member) { store.member = it }
                }
                AmountRow("抹零", if (t.round > 0) yuan(-t.round) else "—") {
                    MiniSwitch(store.roundOff) { store.roundOff = it }
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text("应收", style = PosType.strong, color = c.ink)
                    Spacer(Modifier.weight(1f))
                    Text(yuan(t.due), style = PosType.mega.num(), color = c.ink)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(PosDims.gap)) {
                    PosButton("挂单", store::hold, variant = BtnVariant.Muted, height = PosDims.ctlL)
                    PosButton(
                        if (store.pending.isEmpty()) "送厨" else "送厨 · ${store.pending.size}",
                        store::sendToKitchen,
                        variant = BtnVariant.Soft, height = PosDims.ctlL,
                        enabled = store.pending.isNotEmpty(),
                    )
                    PosButton("结账", onCheckout, Modifier.weight(1f), height = PosDims.ctlL, enabled = t.items > 0)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, n: Int) {
    Text(
        "$title · $n", style = PosType.caption, color = Pos.colors.sub,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** 待下单行：步进器改数量，左滑删除（saket/swipe）。 */
@Composable
private fun PendingLine(store: PosStore, l: OrderLine) {
    val c = Pos.colors
    val delete = SwipeAction(
        onSwipe = { store.remove(l) },
        icon = { Icon(Icons.Rounded.Delete, "删除", tint = Color.White, modifier = Modifier.padding(16.dp)) },
        background = c.danger,
    )
    SwipeableActionsBox(endActions = listOf(delete), swipeThreshold = 96.dp, backgroundUntilSwipeThreshold = c.faint) {
        Row(
            Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineText(l, Modifier.weight(1f))
            QuantityStepper(l.qty, { store.changeQty(l, it) })
        }
    }
}

/** 已下单行：状态标签 + 催菜。赠送、退菜需授权，骨架里先不做。 */
@Composable
private fun SentLine(store: PosStore, l: OrderLine) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineText(l, Modifier.weight(1f))
        when {
            l.served -> StatusLabel("已上菜", Tone.Neutral)
            l.rush -> StatusLabel("催菜中", Tone.Warning)
            else -> PosButton("催菜", { store.rush(l) }, variant = BtnVariant.Muted, height = PosDims.ctlS)
        }
    }
}

@Composable
private fun LineText(l: OrderLine, modifier: Modifier) {
    val c = Pos.colors
    Column(modifier) {
        Text(l.name, style = PosType.strong, color = c.ink)
        Row {
            if (l.spec.isNotEmpty()) {
                Text(l.spec, style = PosType.caption, color = c.sub)
                Spacer(Modifier.width(6.dp))
            }
            Text("${yuan(l.unit)} × ${l.qty}", style = PosType.caption.num(), color = c.sub)
        }
    }
}

@Composable
private fun MiniSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Pos.colors
    Switch(
        checked, onChange, Modifier.scale(0.7f).height(24.dp),
        colors = SwitchDefaults.colors(
            checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary,
            uncheckedTrackColor = c.faint, uncheckedThumbColor = c.sub, uncheckedBorderColor = c.sub,
        ),
    )
}
