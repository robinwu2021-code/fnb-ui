package ai.neargo.fnb.pos.feature.shift

import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.DetailBlock
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.Workbench
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.BillSide
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val EXPECTED_CASH = 248_400

/**
 * 交班（演示，无状态）：当班汇总 + 钱箱核对。v3 原型中交班仅为页脚入口，
 * 正式流程（权限、钱箱指令、交班单打印回执）按后续规范接入，届时补 ViewModel 与 Repository。
 */
@Composable
fun ShiftScreen(onNotice: (String) -> Unit) {
    val layout = LocalPosLayout.current
    if (layout.wide) {
        Workbench(BillSide.Right, layout.dockW, aside = { m -> CashCheck(onNotice, m) }) { m -> Summary(m) }
    } else {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Pos.metrics.gap), verticalArrangement = Arrangement.spacedBy(Pos.metrics.gap)) {
            Summary(Modifier.fillMaxWidth())
            CashCheck(onNotice, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Summary(modifier: Modifier) {
    val c = Pos.colors
    val t = Pos.type
    Panel(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("当班汇总", style = t.title, color = c.ink)
            Text("林晓 · 1号收银台 · 10:02 开班 · 演示数据", style = t.label, color = c.sub)
            DetailBlock("营业额") {
                Text(yuan(1_286_450), style = t.amount.num(), color = c.ink)
                KeyValue("订单数", "86 单")
                KeyValue("客单价", yuan(14_959))
            }
            DetailBlock("收款渠道") {
                KeyValue("扫码", yuan(913_550)); KeyValue("现金", yuan(198_400)); KeyValue("银行卡", yuan(104_500)); KeyValue("储值卡", yuan(70_000))
            }
            DetailBlock("调整", divider = false) {
                KeyValue("退菜", "3 份 · ${yuan(-12_600)}", valueColor = c.danger)
                KeyValue("赠送", "2 份 · ${yuan(-5_600)}")
            }
        }
    }
}

@Composable
private fun CashCheck(onNotice: (String) -> Unit, modifier: Modifier) {
    val c = Pos.colors
    Panel(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                DetailBlock("钱箱核对", divider = false) {
                    KeyValue("备用金", yuan(50_000)); KeyValue("现金收入", yuan(198_400)); KeyValue("应有现金", yuan(EXPECTED_CASH)); KeyValue("实盘", yuan(EXPECTED_CASH))
                    KeyValue("差额", yuan(0), valueColor = c.success)
                }
            }
            Row(Modifier.fillMaxWidth().background(c.control).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosButton("打印交班单", { onNotice("交班单已加入打印队列（演示）") }, Modifier.weight(1f), size = BtnSize.Large)
                PosButton("交班", { onNotice("交班需主管授权（演示）") }, Modifier.weight(1f), variant = BtnVariant.Primary, size = BtnSize.Large)
            }
        }
    }
}
