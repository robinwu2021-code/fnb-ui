package ai.neargo.fnb.pos.feature.sync

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.SyncRepository
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.OutboxItem
import ai.neargo.fnb.pos.core.model.OutboxState
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

data class SyncUiState(val items: List<OutboxItem> = emptyList(), val pending: Int = 0, val device: DeviceStatus = DeviceStatus(), val syncing: Boolean = false)

class SyncViewModel(private val sync: SyncRepository, private val device: DeviceRepository, private val messages: MessageBus) : ViewModel() {
    val state = combine(sync.outbox, sync.pendingCount, device.status) { o, p, d -> SyncUiState(o, p, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    /** 立即同步：离线时只提示，不清队列。 */
    fun syncNow() = viewModelScope.launch {
        if (!device.status.value.online) { messages.post("当前离线，联网后会自动同步"); return@launch }
        val n = sync.syncNow()
        messages.post(if (n > 0) "已同步 $n 项" else "没有待同步的操作")
    }
}

@Composable
fun SyncRoute() {
    val vm = posViewModel { c, _ -> SyncViewModel(c.sync, c.device, c.messages) }
    val state by vm.state.collectAsStateWithLifecycle()
    SyncScreen(state, vm::syncNow)
}

/** 同步队列：离线产生的送厨批次与收款，按时间倒序；联网后自动按创建顺序补发。 */
@Composable
fun SyncScreen(state: SyncUiState, onSync: () -> Unit) {
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
    val cols = listOf(Col("类型", 0.7f), Col("内容", 2.4f), Col("产生时间", 1.2f), Col("状态", 0.9f), Col("同步时间", 1.2f, end = true))
    Panel(Modifier.fillMaxSize().padding(Pos.metrics.gap)) {
        Column(Modifier.padding(Pos.metrics.pad)) {
            PageHeader(
                "同步队列",
                (if (state.device.online) "在线" else if (state.device.forceOffline) "离线（演示强制离线）" else "离线（无网络）") +
                    " · 待同步 ${state.pending} 项 · 批次号即幂等键，重复补发不会重复出单",
            ) { PosButton("立即同步", onSync, variant = BtnVariant.Primary, enabled = state.pending > 0) }
            DataTable(cols, Modifier.weight(1f), empty = "暂无离线操作") {
                items(state.items.size) { i ->
                    val it = state.items[i]
                    TableRow(listOf(
                        { Cell(it.kind.label) }, { Cell(it.summary) }, { Cell(fmt.format(Date(it.createdAt)), sub = true) },
                        { if (it.state == OutboxState.Pending) Tag("待同步", fg = Pos.colors.warning) else Tag("已同步", bg = Pos.colors.tint, fg = Pos.colors.primary) },
                        { Cell(if (it.doneAt > 0) fmt.format(Date(it.doneAt)) else "—", sub = true) },
                    ), cols)
                }
            }
        }
    }
}
