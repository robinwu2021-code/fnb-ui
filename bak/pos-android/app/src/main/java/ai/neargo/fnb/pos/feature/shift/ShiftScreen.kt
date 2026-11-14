package ai.neargo.fnb.pos.feature.shift

import ai.neargo.fnb.pos.core.model.profile

import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.SessionRepository
import ai.neargo.fnb.pos.core.data.ShiftRepository
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.DetailBlock
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.Workbench
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.BillSide
import ai.neargo.fnb.pos.core.model.ManageRules
import ai.neargo.fnb.pos.core.model.ShiftRecord
import ai.neargo.fnb.pos.core.model.ShiftRules
import ai.neargo.fnb.pos.core.model.ShiftState
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import ai.neargo.fnb.pos.core.designsystem.component.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ShiftUiState(val shift: ShiftState = ShiftState(), val summary: ShiftRules.Summary = ShiftRules.summary(emptyList(), ShiftState()), val staff: String = "", val closing: Boolean = false, val retail: Boolean = false)

class ShiftViewModel(config: StoreConfigRepository, session: SessionRepository, private val shift: ShiftRepository, private val messages: MessageBus) : ViewModel() {
    val state = combine(config.current, session.session) { s, ss ->
        val st = s?.shift ?: ShiftState()
        ShiftUiState(st, ShiftRules.summary(s?.payments.orEmpty(), st, s?.adjustments.orEmpty()), ss.staff, retail = s?.profile?.tables == false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShiftUiState())

    /** 交班：实盘金额（元）校验后生成交班记录与交班单，开启新班次。 */
    fun close(counted: String, done: (String?) -> Unit) = viewModelScope.launchStorage(messages) {
        val fen = ai.neargo.fnb.pos.core.model.Money.input(counted, "实盘金额").getOrElse { done(it.message); return@launchStorage }
        shift.closeShift(fen, state.value.staff)
            .onSuccess { r -> messages.post("已交班，差额 ${yuan(r.cashCounted - r.cashExpected)}；交班单已加入打印队列"); done(null) }
            .onFailure { done(it.message) }
    }
}

@Composable
fun ShiftRoute() {
    val vm = posViewModel { c, _ -> ShiftViewModel(c.config, c.session, c.shift, c.messages) }
    val state by vm.state.collectAsStateWithLifecycle()
    ShiftScreen(state, vm::close)
}

private val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

/** 交班：本班收款汇总（按渠道）+ 钱箱核对 + 历史交班。数据来自本机收款记录。 */
@Composable
fun ShiftScreen(state: ShiftUiState, onClose: (String, (String?) -> Unit) -> Unit) {
    val layout = LocalPosLayout.current
    var closing by remember { mutableStateOf(false) }
    if (layout.wide) {
        Workbench(BillSide.Right, layout.dockW, aside = { m -> CashCheck(state, { closing = true }, m) }) { m -> Summary(state, m) }
    } else {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Pos.metrics.gap), verticalArrangement = Arrangement.spacedBy(Pos.metrics.gap)) {
            Summary(state, Modifier.fillMaxWidth())
            CashCheck(state, { closing = true }, Modifier.fillMaxWidth())
        }
    }
    if (closing) CloseDialog(state, { closing = false }, onClose)
}

@Composable
private fun Summary(state: ShiftUiState, modifier: Modifier) {
    val c = Pos.colors
    val t = Pos.type
    val s = state.summary
    Panel(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("当班汇总", style = t.title, color = c.ink)
            Text("${state.staff} · ${if (state.shift.start > 0) fmt.format(Date(state.shift.start)) + " 开班" else "未记录开班时间"}", style = t.label, color = c.sub)
            DetailBlock("营业额") {
                Text(yuan(s.total), style = t.amount.num(), color = c.ink)
                KeyValue("收款笔数", "${s.count} 笔")
                if (s.refundCount > 0) KeyValue("退款（已从营业额扣除）", "${s.refundCount} 笔 · ${yuan(-s.refund)}")
                if (!state.retail) KeyValue("结账桌台", "${s.tables} 桌")
                KeyValue("客单价", if (s.count > 0) yuan(s.total / s.count) else "—")
            }
            if (!state.retail) DetailBlock("退赠统计") {
                KeyValue("退菜笔数", "${s.voidCount}")
                KeyValue("退菜金额", yuan(s.voidAmount))
                KeyValue("赠送笔数", "${s.giftCount}")
                KeyValue("赠送金额", yuan(s.giftAmount))
            }
            DetailBlock("收款渠道") {
                if (s.byMethod.isEmpty()) Text("本班暂无收款", style = t.label, color = c.sub)
                s.byMethod.forEach { (m, v) -> KeyValue(m, yuan(v)) }
            }
            DetailBlock("历史交班", divider = false) {
                if (state.shift.history.isEmpty()) Text("暂无交班记录", style = t.label, color = c.sub)
                state.shift.history.reversed().forEach { r: ShiftRecord ->
                    KeyValue("${fmt.format(Date(r.start))} – ${fmt.format(Date(r.end))} · ${r.count} 笔", "${yuan(r.total)}（差额 ${yuan(r.cashCounted - r.cashExpected)}）")
                }
            }
        }
    }
}

@Composable
private fun CashCheck(state: ShiftUiState, onClose: () -> Unit, modifier: Modifier) {
    val c = Pos.colors
    val s = state.summary
    Panel(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                DetailBlock("钱箱核对", divider = false) {
                    KeyValue("备用金", yuan(state.shift.float))
                    KeyValue("现金收入", yuan(s.cash))
                    KeyValue("应有现金", yuan(s.cashExpected))
                    Text("交班时清点钱箱并填写实盘金额，系统计算差额并打印交班单", style = Pos.type.label, color = c.sub, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().background(c.control).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosButton("清点并交班", onClose, Modifier.weight(1f), variant = BtnVariant.Primary, size = BtnSize.Large)
            }
        }
    }
}

@Composable
private fun CloseDialog(state: ShiftUiState, onDismiss: () -> Unit, onClose: (String, (String?) -> Unit) -> Unit) {
    var counted by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val expected = state.summary.cashExpected
    val diff = ai.neargo.fnb.pos.core.model.Money.parse(counted)?.minus(expected)
    PosDialog("交班 · 钱箱实盘", onDismiss, maxWidth = 440.dp, subtitle = "应有现金 ${yuan(expected)}（备用金 + 现金收入）", footer = {
        PosButton("取消", onDismiss, size = BtnSize.Large)
        PosButton("确认交班", { onClose(counted) { e -> error = e; if (e == null) onDismiss() } }, variant = BtnVariant.Primary, size = BtnSize.Large)
    }) {
        PosTextField(counted, { counted = it }, "实盘金额（元）", error = error, keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Text(
            if (diff == null) "输入实盘金额后显示差额" else "差额 ${yuan(diff)}${if (diff == 0L) "（平账）" else if (diff > 0) "（长款）" else "（短款）"}",
            style = Pos.type.body, color = if (diff == null || diff == 0L) Pos.colors.sub else Pos.colors.warning, modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}
