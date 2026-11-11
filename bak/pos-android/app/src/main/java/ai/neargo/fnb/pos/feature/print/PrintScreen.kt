package ai.neargo.fnb.pos.feature.print

import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.model.KdsRules
import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.PrintRepository
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.PrintJob
import ai.neargo.fnb.pos.core.model.PrintState
import ai.neargo.fnb.pos.core.model.PrinterConfig
import ai.neargo.fnb.pos.feature.common.ReceiptDialog
import ai.neargo.fnb.pos.feature.manage.Cell
import ai.neargo.fnb.pos.feature.manage.Col
import ai.neargo.fnb.pos.feature.manage.DataTable
import ai.neargo.fnb.pos.feature.manage.PageHeader
import ai.neargo.fnb.pos.feature.manage.TableRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PrintUiState(
    val jobs: List<PrintJob> = emptyList(), val device: DeviceStatus = DeviceStatus(),
    val printers: List<PrinterConfig> = emptyList(), val routes: Map<String, String> = emptyMap(),
    val stations: List<String> = emptyList(),
) {
    /** 已发出的任务显示实际目标；未发出的显示将按路由发往的打印机；模拟打印完成的历史任务标「模拟」。 */
    fun printerName(j: PrintJob): String {
        val id = j.printerId.ifBlank { if (j.state == PrintState.Pending || j.state == PrintState.Failed) KdsRules.printRoute(routes, stations, j) else "" }
        return printers.firstOrNull { it.id == id }?.name ?: "${j.station.ifBlank { j.kind.printer }}（模拟）"
    }
}

class PrintViewModel(private val print: PrintRepository, private val device: DeviceRepository, config: StoreConfigRepository, private val messages: MessageBus) : ViewModel() {
    val state = combine(print.jobs, device.status, config.current) { j, d, s -> PrintUiState(j, d, s?.printers.orEmpty(), s?.printRoutes.orEmpty(), s?.kdsStations.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrintUiState())
    fun reprint(id: String) = viewModelScope.launchStorage(messages) { print.reprint(id).onSuccess { messages.post("已加入补打（标注「补打」，不重复收款）") }.onFailure { messages.post(it.message ?: "补打失败") } }
    fun retry() = viewModelScope.launchStorage(messages) {
        if (!device.status.value.printerOk) { messages.post("模拟厨房打印机仍缺纸，换纸后会自动重试"); return@launchStorage }
        print.process(true)
    }
    fun resolve(id: String, printed: Boolean, reason: String, done: (String?) -> Unit) = viewModelScope.launchStorage(messages) {
        val r = print.resolveUnknown(id, printed, reason)
        done(r.exceptionOrNull()?.message)
        if (r.isSuccess) messages.post(if (printed) "已记录：已出纸" else "已记录未出纸，并生成关联补打")
    }
}

@Composable
fun PrintRoute() {
    val vm = posViewModel { c, _ -> PrintViewModel(c.print, c.device, c.config, c.messages) }
    val state by vm.state.collectAsStateWithLifecycle()
    PrintScreen(state, vm::reprint, vm::retry, vm::resolve)
}

@Composable
private fun stateTag(j: PrintJob) = when (j.state) {
    PrintState.Printed -> Tag(if (j.resolution.isNotBlank()) "已出纸（人工确认）" else "已打印", bg = Pos.colors.tint, fg = Pos.colors.primary)
    PrintState.Sent -> Tag(if (j.error.isBlank()) "已发送 · 状态正常" else "已发送 · 未回状态", bg = Pos.colors.tint, fg = Pos.colors.primary)
    PrintState.Pending -> Tag("排队中")
    PrintState.Sending -> Tag("发送中")
    PrintState.Failed -> Tag(j.error.ifBlank { "失败" }, fg = Pos.colors.danger)
    PrintState.Unknown -> Tag("结果未知 · 待核对", bg = Pos.colors.dangerBg, fg = Pos.colors.danger)
}

/** 打印队列：已发送≠已出纸；结果未知须人工核对，不自动重打；补打关联原任务。 */
@Composable
fun PrintScreen(state: PrintUiState, onReprint: (String) -> Unit, onRetry: () -> Unit, onResolve: (String, Boolean, String, (String?) -> Unit) -> Unit) {
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
    var preview by remember { mutableStateOf<PrintJob?>(null) }
    var resolving by remember { mutableStateOf<PrintJob?>(null) }
    val failed = state.jobs.count { it.state == PrintState.Failed }
    val unknown = state.jobs.count { it.state == PrintState.Unknown }
    val cols = listOf(Col("类型", 0.6f), Col("内容", 2f), Col("打印机", 1f), Col("时间", 1f), Col("状态", 1.2f), Col("", 1.6f, end = true))
    Panel(Modifier.fillMaxSize().padding(Pos.metrics.gap)) {
        Column(Modifier.padding(Pos.metrics.pad)) {
            PageHeader("打印队列", "网口打印机 ${state.printers.count { it.enabled }} 台 · 失败 $failed 张 · 待核对 $unknown 张 · 「已发送」表示已写入打印机，不等于已确认出纸") {
                PosButton("重试模拟打印", onRetry, variant = BtnVariant.Primary, enabled = failed > 0)
            }
            DataTable(cols, Modifier.weight(1f)) {
                items(state.jobs.size) { i ->
                    val j = state.jobs[i]
                    TableRow(listOf(
                        { Cell(j.kind.label) },
                        { Cell(j.title + (if (j.reprintOf.isNotBlank()) " · 原任务 ${j.reprintOf.takeLast(6)}" else "") + (if (j.resolution.isNotBlank()) " · ${j.resolution}" else "")) },
                        { Cell(state.printerName(j), sub = true) }, { Cell(fmt.format(Date(j.createdAt)), sub = true) },
                        { stateTag(j) },
                        {
                            PosButton("预览", { preview = j }, variant = BtnVariant.Text, size = BtnSize.Small)
                            if (j.state == PrintState.Unknown) PosButton("核对", { resolving = j }, variant = BtnVariant.Danger, size = BtnSize.Small)
                            else PosButton("补打", { onReprint(j.id) }, variant = BtnVariant.Text, size = BtnSize.Small, enabled = j.state == PrintState.Printed || j.state == PrintState.Sent)
                        },
                    ), cols)
                }
            }
        }
    }
    preview?.let { j -> ReceiptDialog(j.title, j.body, { preview = null }) }
    resolving?.let { j ->
        var reason by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        PosDialog("核对打印结果", onDismiss = { resolving = null }, subtitle = j.title, footer = {
            PosButton("取消", { resolving = null }, size = BtnSize.Large)
            PosButton("未出纸 · 补打", { onResolve(j.id, false, reason) { e -> error = e; if (e == null) resolving = null } }, size = BtnSize.Large)
            PosButton("已出纸", { onResolve(j.id, true, reason) { e -> error = e; if (e == null) resolving = null } }, variant = BtnVariant.Primary, size = BtnSize.Large)
        }) {
            Text("发送中断或打印机报错，可能已部分或全部出纸。请到打印机旁核对后选择，系统不会自动重打，避免厨房重复做菜。", style = Pos.type.body, color = Pos.colors.sub, modifier = Modifier.padding(bottom = 8.dp))
            Text("原因：${j.error}", style = Pos.type.label, color = Pos.colors.warning, modifier = Modifier.padding(bottom = 8.dp))
            PosTextField(reason, { reason = it }, "核对说明", error = error, hint = "例如：厨房已收到完整单据")
        }
    }
}
