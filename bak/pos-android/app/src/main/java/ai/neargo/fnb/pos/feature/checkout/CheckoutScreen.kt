package ai.neargo.fnb.pos.feature.checkout

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosIconButton
import ai.neargo.fnb.pos.core.designsystem.component.Section
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.yuan
import ai.neargo.fnb.pos.feature.order.OrderLineRow
import ai.neargo.fnb.pos.feature.common.ReceiptDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import ai.neargo.fnb.pos.core.designsystem.component.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

@Composable
fun CheckoutRoute(onBack: () -> Unit, onDone: () -> Unit) {
    val vm = posViewModel { c, h -> CheckoutViewModel(c.floor, c.orders, c.config, c.print, c.device, c.messages, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.effect.collect { onDone() } }
    var preview by remember { mutableStateOf<List<String>?>(null) }
    CheckoutScreen(state, vm::onAction, onBack, onPreview = { preview = vm.preview() })
    preview?.let { ReceiptDialog("小票预览 · ${state.table?.id ?: ""}", it, { preview = null }) { PosButton("打印预结单", { vm.onAction(CheckoutAction.PreBill); preview = null }, variant = BtnVariant.Soft, size = BtnSize.Large) } }
}

/**
 * 账单与结账（无状态）：左侧本次待结账（新加 / 已送厨分组）+ 已结账记录；右侧优惠资产、金额明细、收款方式。
 * 固定底部：返回点餐（文字）· 送厨（次）· 结账核对（主）。历史收款不计入本次应收。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutScreen(state: CheckoutUiState, onAction: (CheckoutAction) -> Unit, onBack: () -> Unit, onPreview: () -> Unit = {}) {
    val c = Pos.colors
    val t = Pos.type
    val wide = LocalPosLayout.current.wide
    val tb = state.table ?: return
    val totals = state.totals
    var adjustment by remember { mutableStateOf(false) }
    if (adjustment) ai.neargo.fnb.pos.feature.common.AdjustmentDialog(state.order, state.audits, { adjustment = false }) { request, email, password, done -> onAction(CheckoutAction.Adjust(request, email, password, done)) }

    state.paid?.let { amount ->
        PaidView(amount, state.method, state.online) { onAction(CheckoutAction.Done) }
        return
    }

    Box(Modifier.fillMaxSize().padding(Pos.metrics.gap), contentAlignment = Alignment.TopCenter) {
        Panel(Modifier.widthIn(max = 1080.dp).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("账单与结账", style = t.title, color = c.ink, modifier = Modifier.weight(1f))
                    PosIconButton(Icons.Rounded.Close, "关闭结账", onBack)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(c.tint).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${tb.id} · ${tb.guests} 人 · 堂食", style = t.strong.num(), color = c.ink, modifier = Modifier.weight(1f))
                    Text("#30${tb.id} · ${totals.items} 份菜品", style = t.label.num(), color = c.sub)
                }

                val left: @Composable (Modifier) -> Unit = { m -> BillColumn(state, onAction, onBack, m) }
                val right: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PosButton("小票预览", onPreview, size = BtnSize.Small)
                            PosButton("打印预结单", { onAction(CheckoutAction.PreBill) }, size = BtnSize.Small, enabled = state.order.lines.isNotEmpty())
                        }
                        Section("优惠与资产") {
                            PosButton("主管授权 · 退赠折扣", { adjustment = true }, size = BtnSize.Small, enabled = !state.processing && state.order.lines.isNotEmpty())
                            FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val cm = Modifier.weight(1f)
                                BenefitCard("会员优惠", if (state.order.member) "已享 95 折" else "绑定享 95 折", state.order.member, cm) { onAction(CheckoutAction.ToggleMember) }
                                BenefitCard("优惠券", "满200减10", state.order.coupon, cm) { onAction(CheckoutAction.ToggleCoupon) }
                                BenefitCard("储值卡 · 演示", "余额 ${yuan(state.wallet)}", state.method == PayMethod.Stored, cm) { onAction(CheckoutAction.SelectMethod(PayMethod.Stored)) }
                                BenefitCard("本机活动 · 演示", "满100减5", state.order.activity, cm) { onAction(CheckoutAction.ToggleActivity) }
                            }
                        }
                        Section("金额明细") {
                            KeyValue("菜品小计", yuan(totals.sub))
                            KeyValue("服务费 5%（演示）", yuan(totals.service))
                            if (totals.discount > 0) KeyValue("优惠合计", yuan(-totals.discount), valueColor = c.primary)
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("本次应收", style = t.body, color = c.primary, modifier = Modifier.weight(1f))
                                Text(yuan(totals.due), style = t.amount.num(), color = c.primary)
                            }
                        }
                        Section("收款方式") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PayMethod.entries.forEach { p ->
                                    PosButton(p.label, { onAction(CheckoutAction.SelectMethod(p)) }, Modifier.weight(1f), size = BtnSize.Large,
                                        selected = state.method == p, enabled = state.online || !p.needsNetwork)
                                }
                            }
                            Text(
                                if (!state.online) "离线：扫码不可用；现金、银行卡与本机储值演示可记账" else "本机演示；储值仅扣减本机演示余额，不执行真实支付",
                                style = t.label, color = if (state.online) c.sub else c.warning, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (state.method == PayMethod.Cash) Section("现金收款") {
                            ai.neargo.fnb.pos.core.designsystem.component.CashPad(state.cash, totals.due, { onAction(CheckoutAction.CashInput(it)) }, enabled = !state.processing)
                        }
                    }
                }
                if (wide) {
                    Row(Modifier.weight(1f).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        left(Modifier.weight(1f).fillMaxSize())
                        right(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        left(Modifier.fillMaxWidth().heightIn(max = 560.dp))
                        right(Modifier.fillMaxWidth())
                    }
                }
                Row(Modifier.fillMaxWidth().background(c.control).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PosButton("返回点餐", onBack, Modifier.weight(if (wide) 0.6f else 0.8f), variant = BtnVariant.Text, size = BtnSize.Large)
                    PosButton(if (totals.newItems > 0) "送厨 · ${totals.newItems}" else "送厨", { onAction(CheckoutAction.SendToKitchen) }, Modifier.weight(1f),
                        variant = BtnVariant.Soft, size = BtnSize.Large, enabled = totals.newItems > 0)
                    PosButton(
                        when {
                            state.processing -> "收款处理中"
                            state.block != null -> state.block!!
                            else -> "结账核对 ${yuan(totals.due)}"
                        },
                        { onAction(CheckoutAction.Confirm) }, Modifier.weight(1.6f), variant = BtnVariant.Primary, size = BtnSize.Large,
                        enabled = state.canConfirm || state.processing, loading = state.processing,
                    )
                }
            }
        }
    }
}

/** 左栏：本次待结账（分组清单，可继续改新加数量）+ 已结账记录。 */
@Composable
private fun BillColumn(state: CheckoutUiState, onAction: (CheckoutAction) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    val c = Pos.colors
    val t = Pos.type
    Column(modifier.clip(Pos.metrics.card).background(c.tile).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("本次待结账", style = t.section, color = c.ink, modifier = Modifier.weight(1f))
            PosButton("继续加菜", onBack, variant = BtnVariant.Text, size = BtnSize.Small)
        }
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().clip(Pos.metrics.card).background(c.surface).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
            listOf("新加 · 待送厨" to state.order.pending, "已送厨 · 未结账" to state.order.sent).filter { it.second.isNotEmpty() }.forEach { (title, ls) ->
                Row(Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                    Text(title, style = t.label.copy(fontWeight = FontWeight.SemiBold), color = c.primary, modifier = Modifier.weight(1f))
                    Text("${ls.sumOf { it.qty }} 份 · ${yuan(ls.sumOf { if (it.gifted) 0 else it.unit * it.qty })}", style = t.label.num(), color = c.sub)
                }
                ls.forEach { l -> OrderLineRow(l, { onAction(CheckoutAction.ChangeQty(l.uid, it)) }, null) }
            }
            if (state.order.lines.isEmpty()) Text("本单没有菜品", style = t.body, color = c.sub, modifier = Modifier.padding(vertical = 16.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("已结账记录 ${state.history.size}", style = t.section, color = c.ink)
        if (state.history.isEmpty()) Text("本桌今日暂无已结账记录", style = t.label, color = c.sub, modifier = Modifier.padding(top = 6.dp))
        state.history.take(3).forEach { p ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(Pos.metrics.card).background(c.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.id, style = t.strong, color = c.ink)
                    Text("${java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(p.at))} · ${p.method}", style = t.label.num(), color = c.sub)
                }
                Text(yuan(p.amount), style = t.strong.num(), color = c.ink)
                Spacer(Modifier.size(8.dp))
                Tag(if (p.synced) "已结账" else "待同步", fg = if (p.synced) c.sub else c.warning)
            }
        }
        Text("历史已结账金额独立展示，不计入本次应收。", style = t.label, color = c.sub, modifier = Modifier.padding(top = 6.dp))
    }
}

/** 优惠资产卡：M3 Card（零阴影），白底；选中主题浅底 + 主色字。 */
@Composable
private fun BenefitCard(label: String, value: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Pos.colors
    Card(
        onClick, modifier.heightIn(min = 56.dp).semantics { selected = on }, shape = Pos.metrics.button,
        colors = CardDefaults.cardColors(containerColor = if (on) c.tint else c.surface), elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = Pos.type.label, color = c.sub)
            Text(value, style = Pos.type.strong, color = if (on) c.primary else c.ink)
        }
    }
}

/** 支付结果（演示）。 */
@Composable
private fun PaidView(amount: Long, method: PayMethod, online: Boolean, onDone: () -> Unit) {
    val c = Pos.colors
    val t = Pos.type
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/success.json"))
    val progress by animateLottieCompositionAsState(composition, iterations = 1)
    val dynamic = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.COLOR, c.primary.toArgb(), "Circle", "**"),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, c.onPrimary.toArgb(), "Check", "**"),
    )
    val party = remember {
        listOf(Party(speed = 0f, maxSpeed = 30f, damping = 0.9f, spread = 360,
            colors = listOf(c.primary.toArgb(), c.tint.toArgb(), 0xFFFFC56B.toInt(), 0xFF8FB3FF.toInt()),
            position = Position.Relative(0.5, 0.35), emitter = Emitter(duration = 120, TimeUnit.MILLISECONDS).max(120)))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Panel(Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LottieAnimation(composition, { progress }, dynamicProperties = dynamic, modifier = Modifier.size(120.dp))
                Text("已收款 ${yuan(amount)}", style = t.amount.num(), color = c.ink)
                Text(if (online) "${method.label} · 演示收款，未连接支付服务" else "${method.label} · 离线记账，联网后自动同步", style = t.label, color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(20.dp))
                PosButton("返回桌台", onDone, Modifier.fillMaxWidth(), variant = BtnVariant.Primary, size = BtnSize.Large)
            }
        }
        KonfettiView(Modifier.fillMaxSize(), parties = party)
    }
}
