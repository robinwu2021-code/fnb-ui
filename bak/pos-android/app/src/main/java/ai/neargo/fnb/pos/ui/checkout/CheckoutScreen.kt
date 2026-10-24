package ai.neargo.fnb.pos.ui.checkout

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
import ai.neargo.fnb.pos.ui.shell.FrameLayout
import ai.neargo.fnb.pos.ui.shell.LocalPosLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

enum class PayMethod(val label: String, val needsNetwork: Boolean) {
    WeChat("微信", true), Alipay("支付宝", true), Cash("现金", false), Card("银行卡", false), Balance("会员余额", true),
}

/** 结账：支付方式 + 现金找零键盘；收款成功后 Lottie 对勾 + 彩带。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutScreen(store: PosStore, onBack: () -> Unit, onDone: () -> Unit) {
    val c = Pos.colors
    val wide = LocalPosLayout.current.wide
    val due = store.totals.due
    var method by remember { mutableStateOf(if (store.online) PayMethod.WeChat else PayMethod.Cash) }
    var cash by remember { mutableIntStateOf(0) } // 收到的现金（分）
    var paid by remember { mutableStateOf<Int?>(null) }

    if (paid != null) {
        PaidView(paid!!, change = if (method == PayMethod.Cash) cash - paid!! else 0, method) {
            store.settle(); onDone()
        }
        return
    }

    val canPay = method != PayMethod.Cash || cash >= due
    val summary: @Composable (Modifier) -> Unit = { m ->
        Panel(m) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = c.ink) }
                    Text("${store.tableId} · ${store.orderNo}", style = PosType.title.num(), color = c.ink)
                }
                Spacer(Modifier.height(12.dp))
                store.lines.forEach { l -> AmountRow("${l.name}${if (l.spec.isNotEmpty()) "（${l.spec}）" else ""} × ${l.qty}", yuan(l.unit * l.qty)) }
                Spacer(Modifier.height(8.dp))
                val t = store.totals
                AmountRow("服务费", yuan(t.service))
                if (t.member > 0) AmountRow("会员价", yuan(-t.member), valueColor = c.primaryText)
                if (t.round > 0) AmountRow("抹零", yuan(-t.round))
                Spacer(Modifier.height(12.dp))
                Text("应收", style = PosType.sub, color = c.sub)
                Text(yuan(due), style = PosType.mega.num(), color = c.ink)
            }
        }
    }
    val payPane: @Composable (Modifier) -> Unit = { m ->
        Panel(m) {
            Column(Modifier.padding(20.dp)) {
                Text("支付方式", style = PosType.sub, color = c.sub)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayMethod.entries.forEach { p ->
                        val offline = p.needsNetwork && !store.online
                        val on = p == method
                        Column(
                            Modifier.width(120.dp).height(64.dp).clip(PosDims.radiusMd)
                                .background(if (on) c.primaryTint else c.faint)
                                .clickable(enabled = !offline, role = Role.RadioButton) { method = p }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(p.label, style = PosType.strong, color = if (offline) c.sub else if (on) c.primaryText else c.ink)
                            // 用不了就不摆灰按钮：就地写原因
                            if (offline) Text("离线不可用", style = PosType.caption, color = c.warningText)
                        }
                    }
                }
                if (method == PayMethod.Cash) {
                    Spacer(Modifier.height(16.dp))
                    CashPad(due, cash, onChange = { cash = it })
                }
                Spacer(Modifier.height(16.dp))
                PosButton(
                    "确认收款 ${yuan(due)}",
                    { paid = due },
                    Modifier.fillMaxWidth(),
                    height = PosDims.ctlL,
                    enabled = canPay && due > 0,
                )
            }
        }
    }

    if (wide) {
        // 结账页：不显示导航区；账单在详情区（跟随 L1–L6 的左右），收款工具在内容区
        FrameLayout(detail = { m -> summary(m) }) { m ->
            Box(m.verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) { payPane(Modifier.widthIn(max = 560.dp).fillMaxWidth()) }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PosDims.gap), verticalArrangement = Arrangement.spacedBy(PosDims.gap)) {
            summary(Modifier.fillMaxWidth())
            payPane(Modifier.fillMaxWidth())
        }
    }
}

/** DiningCashPad：快捷面额 + 数字键盘，实时算找零。 */
@Composable
private fun CashPad(due: Int, cash: Int, onChange: (Int) -> Unit) {
    val c = Pos.colors
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("实收", style = PosType.caption, color = c.sub)
                Text(yuan(cash), style = PosType.hero.num(), color = c.ink)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("找零", style = PosType.caption, color = c.sub)
                Text(
                    if (cash >= due) yuan(cash - due) else "还差 ${yuan(due - cash)}",
                    style = PosType.title.num(),
                    color = if (cash >= due) c.primaryText else c.warningText,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val quick = listOf(due, ((due + 4999) / 5000) * 5000, ((due + 9999) / 10000) * 10000, 50000).distinct()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quick.forEach { q -> PosButton(yuan(q, false), { onChange(q) }, Modifier.weight(1f), variant = BtnVariant.Soft, height = PosDims.ctl) }
        }
        Spacer(Modifier.height(8.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(PosDims.ctlL).clip(PosDims.radiusMd).background(c.faint)
                            .clickable(role = Role.Button) {
                                // 键盘按元输入
                                val yuanPart = cash / 100
                                val next = when (k) {
                                    "⌫" -> yuanPart / 10
                                    "00" -> yuanPart * 100
                                    else -> yuanPart * 10 + k.toInt()
                                }
                                onChange((next.coerceAtMost(99_999)) * 100)
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, style = PosType.title.num(), color = c.ink) }
                }
            }
        }
    }
}

@Composable
private fun PaidView(amount: Int, change: Int, method: PayMethod, onDone: () -> Unit) {
    val c = Pos.colors
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/success.json"))
    val progress by animateLottieCompositionAsState(composition, iterations = 1)
    val dynamic = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.COLOR, c.primary.toArgb(), "Circle", "**"),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, c.onPrimary.toArgb(), "Check", "**"),
    )
    val party = remember {
        listOf(
            Party(
                speed = 0f, maxSpeed = 30f, damping = 0.9f, spread = 360,
                colors = listOf(c.primary.toArgb(), c.warning.toArgb(), c.success.toArgb(), 0xFF2E90FA.toInt()),
                position = Position.Relative(0.5, 0.35),
                emitter = Emitter(duration = 120, TimeUnit.MILLISECONDS).max(120),
            ),
        )
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Panel(Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth()) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LottieAnimation(composition, { progress }, dynamicProperties = dynamic, modifier = Modifier.size(140.dp))
                Text("已收款 ${yuan(amount)}", style = PosType.hero.num(), color = c.ink)
                Text(
                    if (method == PayMethod.Cash) "现金 · 找零 ${yuan(change)}" else method.label,
                    style = PosType.body.num(), color = c.sub, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                PosButton("返回桌台", onDone, Modifier.fillMaxWidth(), height = PosDims.ctlL)
            }
        }
        KonfettiView(Modifier.fillMaxSize(), parties = party)
    }
}
