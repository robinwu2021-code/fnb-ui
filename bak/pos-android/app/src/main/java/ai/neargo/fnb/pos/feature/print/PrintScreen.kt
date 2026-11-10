package ai.neargo.fnb.pos.feature.print

import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.PrintRepository
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.PrintJob
import ai.neargo.fnb.pos.core.model.PrintState
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

data class PrintUiState(val jobs: List<PrintJob> = emptyList(), val device: DeviceStatus = DeviceStatus())

class PrintViewModel(private val print: PrintRepository, private val device: DeviceRepository, private val messages: MessageBus) : ViewModel() {
    val state = combine(print.jobs, device.status, ::PrintUiState).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrintUiState())
    fun reprint(id: String) = viewModelScope.launchStorage(messages) { print.reprint(id).onSuccess { messages.post("已加入补打（标注「补打」，不重复收款）") }.onFailure { messages.post(it.message ?: "补打失败") } }
    fun retry() = viewModelScope.launchStorage(messages) {
        if (!device.status.value.printerOk) { messages.post("厨房打印机仍缺纸，换纸后会自动重试"); return@launchStorage }
        print.process(true)
    }
}

@Composable
fun PrintRoute() {
    val vm = posViewModel { c, _ -> PrintViewModel(c.print, c.device, c.messages) }
    val state by vm.state.collectAsStateWithLifecycle()
    PrintScreen(state, vm::reprint, vm::retry)
}

/** 打印队列：厨房单、预结单、结账单、交班单；失败保留可重试，补打单独标识。 */
@Composable
fun PrintScreen(state: PrintUiState, onReprint: (String) -> Unit, onRetry: () -> Unit) {
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
    var preview by remember { mutableStateOf<PrintJob?>(null) }
    val failed = state.jobs.count { it.state == PrintState.Failed }
    val cols = listOf(Col("类型", 0.7f), Col("内容", 2f), Col("打印机", 0.6f), Col("时间", 1.1f), Col("状态", 0.9f), Col("", 1.2f, end = true))
    Panel(Modifier.fillMaxSize().padding(Pos.metrics.gap)) {
        Column(Modifier.padding(Pos.metrics.pad)) {
            PageHeader("打印队列", "前台打印机正常；厨房打印机${if (state.device.printerOk) "正常" else "缺纸"} · 失败 $failed 张（本机模拟打印，未连接真实设备）") {
                PosButton("重试失败", onRetry, variant = BtnVariant.Primary, enabled = failed > 0)
            }
            DataTable(cols, Modifier.weight(1f)) {
                items(state.jobs.size) { i ->
                    val j = state.jobs[i]
                    TableRow(listOf(
                        { Cell(j.kind.label) }, { Cell(j.title) }, { Cell(j.kind.printer, sub = true) }, { Cell(fmt.format(Date(j.createdAt)), sub = true) },
                        {
                            when (j.state) {
                                PrintState.Printed -> Tag("已打印", bg = Pos.colors.tint, fg = Pos.colors.primary)
                                PrintState.Pending -> Tag("排队中")
                                PrintState.Failed -> Tag(j.error.ifBlank { "失败" }, fg = Pos.colors.danger)
                            }
                        },
                        {
                            PosButton("预览", { preview = j }, variant = BtnVariant.Text, size = BtnSize.Small)
                            PosButton("补打", { onReprint(j.id) }, variant = BtnVariant.Text, size = BtnSize.Small, enabled = j.state == PrintState.Printed)
                        },
                    ), cols)
                }
            }
        }
    }
    preview?.let { j -> ReceiptDialog(j.title, j.body, { preview = null }) }
}
