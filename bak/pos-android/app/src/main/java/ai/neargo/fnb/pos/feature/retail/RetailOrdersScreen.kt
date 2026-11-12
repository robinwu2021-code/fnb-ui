package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.app.appContainer
import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.EmptyState
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosSwitch
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.QuantityStepper
import ai.neargo.fnb.pos.core.designsystem.component.SearchField
import ai.neargo.fnb.pos.core.designsystem.component.Section
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.component.Workbench
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.BillSide
import ai.neargo.fnb.pos.core.model.RetailSale
import ai.neargo.fnb.pos.core.model.ReturnRules
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RetailOrdersRoute(onExchange: () -> Unit) {
    val vm = posViewModel { c, _ -> RetailOrdersViewModel(c.config, c.retail, c.device, c.messages) }
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by appContainer().prefs.prefs.collectAsStateWithLifecycle(null)
    LaunchedEffect(vm) { vm.toPos.collect { onExchange() } }
    RetailOrdersScreen(state, vm::onAction, prefs?.layout?.bill ?: BillSide.Right)
}

private val hhmm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
private fun time(at: Long) = hhmm.format(java.util.Date(at))
private fun kgText(g: Int) = "%d.%03d kg".format(g / 1000, g % 1000)

@Composable
private fun statusColors(s: ReturnRules.SaleStatus): Pair<Color, Color> {
    val c = Pos.colors
    return when (s) { ReturnRules.SaleStatus.Paid -> c.tint to c.primary; ReturnRules.SaleStatus.PartialReturn -> c.control to c.warning; ReturnRules.SaleStatus.Returned -> c.dangerBg to c.danger }
}

@Composable
private fun StatusTag(s: ReturnRules.SaleStatus) { val (bg, fg) = statusColors(s); Tag(s.label, bg = bg, fg = fg) }

/** 零售订单（原型 RetailOrdersWB.tsx）：列表（状态筛选、搜索）· 详情（商品、优惠、收款、退货记录 · 补打 / 换货 / 退货） */
@Composable
fun RetailOrdersScreen(state: RetailOrdersUiState, onAction: (RetailOrdersAction) -> Unit, side: BillSide = BillSide.Right) {
    val layout = LocalPosLayout.current
    Workbench(side, if (layout.wide) maxOf(layout.dockW, 400.dp) else layout.dockW, aside = { m -> state.current?.let { SaleDetail(state, it, onAction, m) } ?: Panel(m) { EmptyState("没有订单", Modifier.padding(16.dp)) } }) { m ->
        SaleList(state, onAction, m)
    }
    state.flow?.let { ReturnDialog(state, it, onAction) }
}

@Composable
private fun SaleList(state: RetailOrdersUiState, onAction: (RetailOrdersAction) -> Unit, modifier: Modifier) {
    val c = Pos.colors; val t = Pos.type
    Panel(modifier) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(state.query, { onAction(RetailOrdersAction.Search(it)) }, "搜索单号 / 会员", Modifier.weight(1f))
                PosButton("退货", { onAction(RetailOrdersAction.Open(ReturnMode.Return, null)) }, variant = BtnVariant.Soft)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PosChip("全部", state.filter == null, { onAction(RetailOrdersAction.Filter(null)) }, count = state.sales.size)
                ReturnRules.SaleStatus.entries.forEach { s -> PosChip(s.label, state.filter == s, { onAction(RetailOrdersAction.Filter(s)) }, count = state.sales.count { it.status == s }) }
            }
            val shown = state.shown
            Text("${shown.size} 单 · 实收 ${yuan(shown.sumOf { it.paid })}", style = t.label.num(), color = c.sub)
            if (shown.isEmpty()) EmptyState("没有符合条件的订单")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.no }) { s ->
                    val sel = state.current?.no == s.no
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Pos.metrics.button).background(if (sel) c.tint else Color.Transparent)
                        .clickable { onAction(RetailOrdersAction.Select(s.no)) }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.width(80.dp)) { StatusTag(s.status) }
                        Text(s.no, style = t.strong.num(), color = c.ink, modifier = Modifier.width(72.dp))
                        Text(listOfNotNull(s.channel.label, s.member).joinToString(" · "), style = t.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${s.pieces} 件 · ${time(s.at)}", style = t.label.num(), color = c.sub, modifier = Modifier.weight(1f))
                        Text(yuan(s.paid), style = t.strong.num(), color = c.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleDetail(state: RetailOrdersUiState, s: RetailSale, onAction: (RetailOrdersAction) -> Unit, modifier: Modifier) {
    val c = Pos.colors; val t = Pos.type
    val block = state.blockOf(s)
    Panel(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.no, style = t.title.num(), color = c.ink, modifier = Modifier.weight(1f)); StatusTag(s.status)
                }
                Text(listOfNotNull(s.channel.label, s.member, time(s.at)).joinToString(" · "), style = t.label.num(), color = c.sub)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Section("商品 · ${s.pieces} 件") {
                    s.lines.forEach { l ->
                        val gone = ReturnRules.refundable(s.soldLines.first { it.key == l.key }) == 0
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.name + (l.sku?.let { " · $it" } ?: ""), style = t.body.let { if (gone) it.copy(textDecoration = TextDecoration.LineThrough) else it }, color = if (gone) c.sub else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(if (l.grams != null) "${kgText(l.grams)} × ${yuan(l.unit)}/kg" else "${yuan(l.unit)} × ${l.qty}",
                                    if (l.returned > 0) (if (l.grams != null) "已退" else "已退 ${l.returned}") else null).joinToString(" · "), style = t.label.num(), color = if (l.returned > 0) c.warning else c.sub)
                            }
                            Text(yuan(l.amount), style = t.strong.num(), color = if (gone) c.sub else c.ink)
                        }
                    }
                }
                if (s.discount > 0) Section("整单优惠") { KeyValue("抹零 / 券", "−${yuan(s.discount)}", valueColor = c.primary) }
                Section("收款") { s.pays.forEach { p -> KeyValue(p.method, yuan(p.amount)) } }
                if (s.returns.isNotEmpty()) Section("退货记录") {
                    s.returns.forEach { r -> KeyValue("${time(r.at)} · ${r.pieces} 件 · ${r.reason.label} · ${if (r.exchange) "换货抵扣" else r.method}", "−${yuan(r.amount)}", valueColor = c.primary) }
                }
            }
            Column(Modifier.fillMaxWidth().background(c.control).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("实收 · ${s.pieces} 件", style = t.label, color = c.sub, modifier = Modifier.weight(1f))
                    Text(yuan(s.paid), style = t.amount.num(), color = c.ink)
                }
                if (block != null) Text(block, style = t.label, color = c.warning)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PosButton("补打小票", { onAction(RetailOrdersAction.Reprint(s.no)) }, size = BtnSize.Small)
                    PosButton("换货", { onAction(RetailOrdersAction.Open(ReturnMode.Exchange, s.no)) }, size = BtnSize.Small, enabled = block == null)
                    PosButton("退货", { onAction(RetailOrdersAction.Open(ReturnMode.Return, s.no)) }, Modifier.weight(1f), variant = BtnVariant.Primary, size = BtnSize.Small, enabled = block == null)
                }
            }
        }
    }
}

/* ---------------- 退货 / 换货弹窗（原型 ReturnDialog.tsx） ---------------- */

@Composable
private fun ReturnDialog(state: RetailOrdersUiState, f: ReturnFlow, onAction: (RetailOrdersAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val ex = f.mode == ReturnMode.Exchange
    val sale = state.sale(f.saleNo)
    val close = { onAction(RetailOrdersAction.Close) }
    f.done?.let { r ->
        PosDialog("退款完成", close, subtitle = f.saleNo ?: "无单退货", footer = { PosButton("完成", close, variant = BtnVariant.Primary) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("已退 ${r.method}", style = t.label, color = c.sub)
                Text(yuan(r.amount), style = t.hero.num(), color = c.ink)
            }
            KeyValue("退回件数", "${r.pieces} 件"); KeyValue("原因", r.reason.label)
            Text(if (f.restock) "商品已回库存" else "商品未回库存，已记一张报损单", style = t.label, color = c.sub, modifier = Modifier.padding(vertical = 8.dp))
            Text("退货小票已加入打印队列", style = t.label, color = c.sub, modifier = Modifier.padding(bottom = 8.dp))
        }
        return
    }
    val steps = if (ex) listOf("找原单", "选退回商品", "原因与抵扣") else listOf("找原单", "选商品", "原因与金额", "退款")
    val title = (if (ex) "换货" else "退货") + if (f.noReceipt && f.step > 1) " · 无单退货" else ""
    PosDialog(title, close, subtitle = steps.mapIndexed { i, s -> "${if (i + 1 == f.step) "●" else "○"} ${i + 1} $s" }.joinToString("   "), maxWidth = 760.dp, footer = {
        if (f.step > 1) PosButton("上一步", { onAction(RetailOrdersAction.Back) }, variant = BtnVariant.Text)
        when {
            f.step == 1 -> PosButton("取消", close, variant = BtnVariant.Text)
            f.step == 2 -> PosButton("下一步", { onAction(RetailOrdersAction.Next) }, variant = BtnVariant.Primary, enabled = state.pieces > 0)
            f.step == 3 && ex -> PosButton("记抵扣，去收银 ${yuan(state.refund)}", { onAction(RetailOrdersAction.Confirm) }, variant = BtnVariant.Primary, enabled = f.reason != null && !f.busy, loading = f.busy)
            f.step == 3 -> PosButton("下一步", { onAction(RetailOrdersAction.Next) }, variant = BtnVariant.Primary, enabled = f.reason != null)
            else -> PosButton("确认退款 ${yuan(state.refund)}", { onAction(RetailOrdersAction.Confirm) }, variant = BtnVariant.Danger,
                enabled = state.refund > 0 && !f.busy && (!state.needAuth || (f.approver.isNotBlank() && f.secret.isNotBlank())), loading = f.busy)
        }
    }) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (f.step) {
                1 -> FindSale(state, ex, onAction)
                2 -> PickLines(state, f, sale, onAction)
                3 -> ReasonStep(state, f, sale, ex, onAction)
                else -> RefundStep(state, f, onAction)
            }
        }
    }
}

@Composable
private fun FindSale(state: RetailOrdersUiState, ex: Boolean, onAction: (RetailOrdersAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    Text("选原单（扫小票上的单号也可以）", style = t.label, color = c.sub)
    state.sales.take(20).forEach { s ->
        val why = state.blockOf(s)
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Pos.metrics.button).clickable(enabled = why == null) { onAction(RetailOrdersAction.PickSale(s.no)) }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.width(80.dp)) { StatusTag(s.status) }
            Text(s.no, style = t.strong.num(), color = if (why == null) c.ink else c.sub, modifier = Modifier.width(72.dp))
            Text(why ?: listOfNotNull(s.channel.label, s.member).joinToString(" · "), style = t.body, color = if (why == null) c.ink else c.sub, maxLines = 1, modifier = Modifier.weight(1f))
            Text("${s.pieces} 件 · ${time(s.at)}", style = t.label.num(), color = c.sub)
            Text(yuan(s.paid), style = t.strong.num(), color = c.ink)
        }
    }
    if (ex) Text("换货：先退回原单里的商品，金额记成抵扣，再到收银扫新商品，多退少补", style = t.label, color = c.sub)
    else Row(Modifier.fillMaxWidth().clip(Pos.metrics.card).background(c.tile).clickable { onAction(RetailOrdersAction.PickSale(null)) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("无单退货", style = t.strong, color = c.ink)
            Text("找不到原单：按现价退，只能退现金或储值", style = t.label, color = c.sub)
        }
        if (state.settings.noReceiptAuth) Tag("需主管授权", fg = c.warning)
    }
}

@Composable
private fun PickLines(state: RetailOrdersUiState, f: ReturnFlow, sale: RetailSale?, onAction: (RetailOrdersAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    if (sale != null) {
        val net = remember(sale) { ReturnRules.allocate(sale.soldLines, sale.discount) }
        Text("${sale.no} · ${sale.channel.label} · ${time(sale.at)}  勾选要退的商品", style = t.label.num(), color = c.sub)
        sale.lines.forEach { l ->
            val left = ReturnRules.refundable(sale.soldLines.first { it.key == l.key })
            val q = f.pick[l.key] ?: 0
            val unit = if (l.grams != null) net.getValue(l.key) else net.getValue(l.key) / l.qty
            PickRow(l.name + (l.sku?.let { " · $it" } ?: ""),
                if (l.grams != null) "${kgText(l.grams)} · 整行退" else "可退 $left · ${yuan(unit)}/件" + if (l.returned > 0) " · 已退 ${l.returned}" else "",
                q, if (l.grams != null) 1 else left, l.grams != null, unit * q) { onAction(RetailOrdersAction.Pick(l.key, it)) }
        }
    } else {
        SearchField(f.query, { onAction(RetailOrdersAction.FlowQuery(it)) }, "搜商品名或条码", Modifier.fillMaxWidth())
        state.looseItems.take(12).forEach { p ->
            val q = f.pick[p.id] ?: 0
            PickRow(p.name, "现价 ${yuan(p.price)}/件", q, 99, false, p.price * q) { onAction(RetailOrdersAction.Pick(p.id, it)) }
        }
    }
    Row(Modifier.fillMaxWidth().clip(Pos.metrics.card).background(c.tint).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (state.pieces > 0) "已选 ${state.pieces} 件" else "还没选商品", style = t.body, color = c.ink, modifier = Modifier.weight(1f))
        Text(yuan(state.refund), style = t.amountM.num(), color = c.primary)
    }
}

@Composable
private fun PickRow(name: String, meta: String, q: Int, max: Int, whole: Boolean, amount: Long, onQty: (Int) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val dis = max == 0
    Row(Modifier.fillMaxWidth().clip(Pos.metrics.button).background(if (q > 0) c.tint else c.tile).clickable(enabled = !dis) { onQty(if (q > 0) 0 else 1) }
        .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(name, style = t.strong, color = if (dis) c.sub else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (dis) "已全部退" else meta, style = t.label.num(), color = c.sub)
        }
        if (!whole && !dis) QuantityStepper(q, { d -> onQty((q + d).coerceIn(0, max)) }, max = max)
        else Text(if (q > 0) "退" else "—", style = t.body, color = c.sub)
        Text(yuan(amount), style = t.strong.num(), color = if (q > 0) c.ink else c.sub, modifier = Modifier.width(72.dp))
    }
}

@Composable
private fun ReasonStep(state: RetailOrdersUiState, f: ReturnFlow, sale: RetailSale?, ex: Boolean, onAction: (RetailOrdersAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    Text("退货原因", style = t.section, color = c.ink)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ReturnRules.Reason.entries.forEach { r -> PosButton(r.label, { onAction(RetailOrdersAction.Reason(r)) }, Modifier.weight(1f), selected = f.reason == r) }
    }
    Row(Modifier.fillMaxWidth().clip(Pos.metrics.card).background(c.tile).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("退回商品回库存", style = t.strong, color = c.ink)
            Text(if (f.restock) "能再卖：加回库存" else "不能再卖：不回库存，自动记一张报损单", style = t.label, color = c.sub)
        }
        PosSwitch(f.restock, { onAction(RetailOrdersAction.Restock(it)) })
    }
    Row(Modifier.fillMaxWidth().clip(Pos.metrics.card).background(c.tint).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ex) "换货抵扣" else "应退金额", style = t.body, color = c.ink, modifier = Modifier.weight(1f))
        Text(yuan(state.refund), style = t.amountM.num(), color = c.primary)
    }
    if (ex) Text("确认后这 ${state.pieces} 件记为退回，${yuan(state.refund)} 带到收银：新商品比它贵补收差价，便宜退差价到原收款方式", style = t.label, color = c.sub)
    if (sale != null && sale.discount > 0) Text("原单有整单优惠 ${yuan(sale.discount)}，已按各行实付比例分摊后计算退款", style = t.label, color = c.sub)
}

@Composable
private fun RefundStep(state: RetailOrdersUiState, f: ReturnFlow, onAction: (RetailOrdersAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    Text(if (f.noReceipt) "退款方式（无单只能退现金或储值）" else if (state.settings.originalMethod) "退款方式（按原收款方式退）" else "退款方式", style = t.section, color = c.ink)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("现金", "扫码", "银行卡", "储值卡").forEach { m ->
            PosButton(m, { onAction(RetailOrdersAction.Method(m)) }, Modifier.weight(1f), size = BtnSize.Large, selected = f.method == m,
                enabled = m in state.methods && !(m == "扫码" && !state.online))
        }
    }
    if (!state.online && f.method == "扫码") Text("离线时扫码退款不可用", style = t.label, color = c.warning)
    Row(Modifier.fillMaxWidth().clip(Pos.metrics.card).background(c.tint).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("退款金额", style = t.body, color = c.ink)
            Text("${state.pieces} 件 · ${f.reason?.label.orEmpty()} · ${if (f.restock) "回库存" else "记报损"}", style = t.label, color = c.sub)
        }
        Text(yuan(state.refund), style = t.amountM.num(), color = c.primary)
    }
    if (state.needAuth) Section("主管授权") {
        PosTextField(f.approver, { onAction(RetailOrdersAction.Auth(it, f.secret)) }, "主管姓名 / 账号")
        PosTextField(f.secret, { onAction(RetailOrdersAction.Auth(f.approver, it)) }, "PIN / 密码", password = true, keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Text("体验账号可用 manager@demo.local / demo12345", style = t.label, color = c.sub)
    }
    Spacer(Modifier.padding(2.dp))
}
