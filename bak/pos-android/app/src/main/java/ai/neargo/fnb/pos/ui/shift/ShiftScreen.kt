package ai.neargo.fnb.pos.ui.shift

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val EXPECTED_CASH = 248_400

/** 交班。内容区 = 当班数据，详情区 = 钱箱实盘与交班操作。数据为示例。 */
@Composable
fun ShiftScreen(store: PosStore) {
    val wide = LocalPosLayout.current.wide
    if (wide) {
        FrameLayout(detail = { m -> CashCheck(store, m) }) { m -> Summary(m) }
    } else {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(PosDims.gap),
            verticalArrangement = Arrangement.spacedBy(PosDims.gap),
        ) {
            Summary(Modifier.fillMaxWidth())
            CashCheck(store, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Summary(modifier: Modifier) {
    val c = Pos.colors
    Panel(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("当班汇总", style = PosType.display, color = c.ink)
            Text("林晓 · 1 号收银台 · 10:02 开班", style = PosType.sub, color = c.sub)
            Spacer(Modifier.height(16.dp))
            Text("营业额", style = PosType.sub, color = c.sub)
            Text(yuan(1_286_450), style = PosType.mega.num(), color = c.ink)
            Spacer(Modifier.height(12.dp))
            AmountRow("订单数", "86 单")
            AmountRow("客单价", yuan(14_959))
            AmountRow("微信", yuan(612_300))
            AmountRow("支付宝", yuan(301_250))
            AmountRow("现金", yuan(198_400))
            AmountRow("银行卡", yuan(104_500))
            AmountRow("会员余额", yuan(70_000))
            AmountRow("退菜", "3 道 · ${yuan(-12_600)}", valueColor = c.dangerText)
            AmountRow("赠送", "2 道 · ${yuan(-5_600)}")
        }
    }
}

@Composable
private fun CashCheck(store: PosStore, modifier: Modifier) {
    val c = Pos.colors
    Panel(modifier) {
        Column(Modifier.padding(20.dp)) {
            Text("钱箱核对", style = PosType.title, color = c.ink)
            Spacer(Modifier.height(12.dp))
            AmountRow("备用金", yuan(50_000))
            AmountRow("现金收入", yuan(198_400))
            AmountRow("应有现金", yuan(EXPECTED_CASH))
            AmountRow("实盘", yuan(EXPECTED_CASH))
            AmountRow("差额", yuan(0), valueColor = c.successText)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosButton("打印交班单", { store.toast("已打印交班单") }, variant = BtnVariant.Muted, height = PosDims.ctlL)
                PosButton("交班", { store.toast("已交班。下一位收银员开班后可继续收款") }, Modifier.weight(1f), height = PosDims.ctlL)
            }
        }
    }
}
