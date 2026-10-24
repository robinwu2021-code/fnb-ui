package ai.neargo.fnb.pos.ui.orders

import ai.neargo.fnb.pos.data.MockData
import ai.neargo.fnb.pos.data.OrderSummary
import ai.neargo.fnb.pos.data.yuan
import ai.neargo.fnb.pos.state.PosStore
import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosDims
import ai.neargo.fnb.pos.theme.PosType
import ai.neargo.fnb.pos.theme.num
import ai.neargo.fnb.pos.ui.components.AmountRow
import ai.neargo.fnb.pos.ui.components.BtnVariant
import ai.neargo.fnb.pos.ui.components.Panel
import ai.neargo.fnb.pos.ui.components.PosButton
import ai.neargo.fnb.pos.ui.components.StatusLabel
import ai.neargo.fnb.pos.ui.components.Tone
import ai.neargo.fnb.pos.ui.shell.FrameLayout
import ai.neargo.fnb.pos.ui.shell.LocalPosLayout
import ai.neargo.fnb.pos.ui.shell.NavArea
import ai.neargo.fnb.pos.ui.shell.NavItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val statuses = listOf("进行中", "挂起", "待结账", "已结账")

private fun tone(status: String) = when (status) {
    "待结账", "挂起" -> Tone.Warning
    "已结账" -> Tone.Neutral
    else -> Tone.Primary
}

/** 订单列表。导航区 = 状态，内容区 = 列表，详情区 = 所选订单。取单、补打、退款在后续迭代接入。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(store: PosStore) {
    val c = Pos.colors
    val layout = LocalPosLayout.current
    var filter by rememberSaveable { mutableStateOf("all") }
    var selectedNo by rememberSaveable { mutableStateOf(MockData.orders.first().no) }
    var sheetOpen by remember { mutableStateOf(false) }
    val shown = MockData.orders.filter { filter == "all" || it.status == filter }
    val selected = MockData.orders.firstOrNull { it.no == selectedNo }

    FrameLayout(
        nav = { vertical, m ->
            NavArea(
                listOf(NavItem("all", "全部")) + statuses.map { s -> NavItem(s, s, MockData.orders.count { it.status == s }) },
                filter, { filter = it }, vertical, m,
            )
        },
        detail = { m -> OrderDetail(selected, store, m) },
    ) { m ->
        Panel(m) {
            LazyColumn {
                items(shown, key = { it.no }) { o ->
                    val on = layout.wide && o.no == selectedNo
                    Row(
                        Modifier.fillMaxWidth().background(if (on) c.primaryTint else Color.Transparent)
                            .clickable { selectedNo = o.no; if (!layout.wide) sheetOpen = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(o.label, style = PosType.strong, color = if (on) c.primaryText else c.ink)
                                Spacer(Modifier.width(8.dp))
                                Text(o.no, style = PosType.sub.num(), color = c.sub)
                            }
                            Text("${o.time} · ${o.items} 道", style = PosType.caption.num(), color = c.sub)
                        }
                        StatusLabel(o.status, tone(o.status))
                        Spacer(Modifier.width(16.dp))
                        Text(yuan(o.amount), style = PosType.strong.num(), color = c.ink)
                    }
                    HorizontalDivider(color = c.faint, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (sheetOpen && !layout.wide) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, containerColor = c.surface, scrimColor = c.scrim) {
            OrderDetail(selected, store, Modifier.fillMaxWidth().padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun OrderDetail(o: OrderSummary?, store: PosStore, modifier: Modifier) {
    val c = Pos.colors
    Panel(modifier) {
        if (o == null) {
            Text("选择一张订单查看详情", style = PosType.body, color = c.sub, modifier = Modifier.padding(20.dp))
            return@Panel
        }
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.label, style = PosType.hero, color = c.ink, modifier = Modifier.weight(1f))
                StatusLabel(o.status, tone(o.status))
            }
            Text("${o.no} · ${o.time} 下单", style = PosType.sub.num(), color = c.sub)
            Spacer(Modifier.height(16.dp))
            AmountRow("菜品", "${o.items} 道")
            AmountRow("收银员", "林晓")
            Spacer(Modifier.height(8.dp))
            Text(if (o.status == "已结账") "实收" else "应收", style = PosType.sub, color = c.sub)
            Text(yuan(o.amount), style = PosType.mega.num(), color = c.ink)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosButton("补打", { store.toast("已补打 ${o.no} 结账单（补打）") }, variant = BtnVariant.Muted, height = PosDims.ctlL)
                when (o.status) {
                    "挂起" -> PosButton("取单", { store.toast("已取单 ${o.no}") }, Modifier.weight(1f), height = PosDims.ctlL)
                    "已结账" -> PosButton("退款", { store.toast("退款需要主管授权") }, Modifier.weight(1f), variant = BtnVariant.Danger, height = PosDims.ctlL)
                    else -> PosButton("查看", { store.toast("${o.no} 详情在后续迭代接入") }, Modifier.weight(1f), variant = BtnVariant.Soft, height = PosDims.ctlL)
                }
            }
        }
    }
}
