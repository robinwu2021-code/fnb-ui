package ai.neargo.fnb.pos.feature.sync

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.data.CloudLink
import ai.neargo.fnb.pos.core.data.CommandStats
import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.data.SyncRepository
import ai.neargo.fnb.pos.core.data.SyncStatusRepository
import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.CheckoutPolicy
import ai.neargo.fnb.pos.core.model.DeliveryState
import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.KitchenDelivery
import ai.neargo.fnb.pos.core.model.OutboxItem
import ai.neargo.fnb.pos.core.model.OutboxState
import ai.neargo.fnb.pos.core.model.PaymentRecord
import ai.neargo.fnb.pos.core.model.PaymentState
import ai.neargo.fnb.pos.core.model.yuan
import ai.neargo.fnb.pos.feature.manage.Cell
import ai.neargo.fnb.pos.feature.manage.Col
import ai.neargo.fnb.pos.feature.manage.DataTable
import ai.neargo.fnb.pos.feature.manage.PageHeader
import ai.neargo.fnb.pos.feature.manage.TableRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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

enum class SyncTab(val label: String) { Kitchen("厨房任务"), Cloud("云事件"), Commands("本机命令"), Payments("收款") }

data class SyncUiState(
    val items: List<OutboxItem> = emptyList(), val pending: Int = 0, val device: DeviceStatus = DeviceStatus(),
    val deliveries: List<KitchenDelivery> = emptyList(), val payments: List<PaymentRecord> = emptyList(), val stats: CommandStats = CommandStats(),
) {
    val kitchenAction get() = deliveries.count { it.needsAction }
    val kitchenPending get() = deliveries.count { it.state == DeliveryState.Pending }
}

class SyncViewModel(
    private val sync: SyncRepository, private val device: DeviceRepository, config: StoreConfigRepository,
    private val status: SyncStatusRepository, private val messages: MessageBus,
) : ViewModel() {
    val state = combine(sync.outbox, sync.pendingCount, device.status, sync.deliveries, combine(config.current, status.stats) { s, st -> s to st }) { o, p, d, dl, (s, st) ->
        SyncUiState(o, p, d, dl, s?.payments.orEmpty().sortedByDescending { it.at }, st)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    init { viewModelScope.launch { status.refresh() } }

    /** 演示队列补发：离线时只提示，不清队列；模拟结果不等于云端回执。 */
    fun syncNow() = viewModelScope.launchStorage(messages) {
        if (!device.status.value.online) { messages.post("当前离线，联网后会自动补发"); return@launchStorage }
        val n = sync.syncNow()
        messages.post(if (n > 0) "已模拟补发 $n 项（未接入云端）" else "没有待补发的演示队列")
    }

    fun confirm(ticketId: String, reason: String, done: (String?) -> Unit) = viewModelScope.launchStorage(messages) {
        val r = sync.confirmDelivery(ticketId, reason)
        done(r.exceptionOrNull()?.message)
        if (r.isSuccess) messages.post("已记录人工确认送达")
    }
}

@Composable
fun SyncRoute() {
    val vm = posViewModel { c, _ -> SyncViewModel(c.sync, c.device, c.config, c.syncStatus, c.messages) }
    val state by vm.state.collectAsStateWithLifecycle()
    SyncScreen(state, vm::syncNow, vm::confirm)
}

private val fmt get() = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
private fun time(ms: Long) = if (ms > 0) fmt.format(Date(ms)) else "—"

/**
 * 同步中心（N3-03）：本机命令、主机回执、厨房任务、云事件、收款分别展示。
 * 「有网」不等于已同步；模拟结果明确标注；未得远端持久回执不清队列。
 */
@Composable
fun SyncScreen(state: SyncUiState, onSync: () -> Unit, onConfirm: (String, String, (String?) -> Unit) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(SyncTab.Kitchen) }
    var confirming by remember { mutableStateOf<KitchenDelivery?>(null) }
    val c = Pos.colors
    Panel(Modifier.fillMaxSize().padding(Pos.metrics.gap)) {
        Column(Modifier.padding(Pos.metrics.pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageHeader("同步中心", (if (state.device.online) "网络在线" else if (state.device.forceOffline) "离线（演示强制离线）" else "离线（无网络）") +
                " · 网络在线不代表已同步，各端点分别确认")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Endpoint("本机命令", "${state.stats.committed} 已提交", if (state.stats.conflicts + state.stats.rejected > 0) "冲突 ${state.stats.conflicts} · 拒绝 ${state.stats.rejected}" else "最近 ${time(state.stats.lastAcceptedAt)}", ok = state.stats.conflicts + state.stats.rejected == 0, Modifier.weight(1f)) { tab = SyncTab.Commands }
                Endpoint("主机回执", if (state.stats.singleDevice) "单机 · 本机即主机" else "—", "未决发送 ${state.stats.openCommands}", ok = state.stats.openCommands == 0, Modifier.weight(1f)) { tab = SyncTab.Commands }
                Endpoint("厨房任务", if (state.kitchenAction > 0) "${state.kitchenAction} 张待处理" else "无异常", "待出单 ${state.kitchenPending} · 模拟打印", ok = state.kitchenAction == 0, Modifier.weight(1f)) { tab = SyncTab.Kitchen }
                Endpoint("云事件", if (CloudLink.CONNECTED) "已连接" else "未接入云端", "待上传 ${state.stats.unackedEvents} 条", ok = false, Modifier.weight(1f)) { tab = SyncTab.Cloud }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SyncTab.entries.forEach { t -> PosButton(t.label, { tab = t }, size = BtnSize.Small, selected = tab == t) }
            }
            when (tab) {
                SyncTab.Kitchen -> KitchenTab(state.deliveries, Modifier.weight(1f)) { confirming = it }
                SyncTab.Cloud -> CloudTab(state, onSync, Modifier.weight(1f))
                SyncTab.Commands -> CommandsTab(state.stats)
                SyncTab.Payments -> PaymentsTab(state.payments, Modifier.weight(1f))
            }
        }
    }
    confirming?.let { d ->
        var reason by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        PosDialog("人工确认送达", onDismiss = { confirming = null }, subtitle = d.label, footer = {
            PosButton("取消", { confirming = null }, size = BtnSize.Large)
            PosButton("确认送达", { onConfirm(d.ticketId, reason) { e -> error = e; if (e == null) confirming = null } }, variant = BtnVariant.Primary, size = BtnSize.Large)
        }) {
            Text("仅在已核实厨房收到该单（如口头或手写传递）时确认，将记录确认人和原因。不会重新出单。", style = Pos.type.body, color = c.sub, modifier = Modifier.padding(bottom = 8.dp))
            PosTextField(reason, { reason = it }, "确认原因", error = error, hint = "例如：已手写单交给厨房")
        }
    }
}

@Composable
private fun Endpoint(title: String, value: String, sub: String, ok: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Pos.colors
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(c.tile).clickable(role = Role.Button, onClick = onClick).padding(12.dp)) {
        Text(title, style = Pos.type.label, color = c.sub)
        Text(value, style = Pos.type.strong, color = if (ok) c.ink else c.warning)
        Text(sub, style = Pos.type.label, color = c.sub)
    }
}

@Composable
private fun stateTag(state: DeliveryState) = when (state) {
    DeliveryState.Delivered -> Tag(state.label, bg = Pos.colors.tint, fg = Pos.colors.primary)
    DeliveryState.ManualConfirmed -> Tag(state.label, bg = Pos.colors.tint, fg = Pos.colors.primary)
    DeliveryState.Pending -> Tag(state.label)
    DeliveryState.Failed, DeliveryState.Unknown -> Tag(state.label, bg = Pos.colors.dangerBg, fg = Pos.colors.danger)
}

private fun sourceLabel(s: String) = when (s) { "simulation" -> "模拟"; "legacy" -> "历史待核实"; "manual" -> "人工"; "local" -> "本机"; "" -> "历史"; else -> s }

@Composable
private fun KitchenTab(items: List<KitchenDelivery>, modifier: Modifier, onConfirm: (KitchenDelivery) -> Unit) {
    val cols = listOf(Col("票据", 2f), Col("目标", 1f), Col("状态", 1f), Col("来源", 0.8f), Col("说明", 1.6f), Col("更新时间", 1f), Col("操作", 1f, end = true))
    DataTable(cols, modifier, empty = "暂无厨房任务") {
        items(items.size) { i ->
            val d = items[i]
            TableRow(listOf(
                { Cell(d.label) }, { Cell(d.target, sub = true) }, { stateTag(d.state) }, { Cell(sourceLabel(d.source), sub = true) },
                { Cell(when { d.state == DeliveryState.ManualConfirmed -> "${d.confirmedBy}：${d.reason}"; d.lastError.isNotBlank() -> "${d.lastError}（换纸后自动重试）"; else -> "—" }, sub = true) },
                { Cell(time(d.updatedAt), sub = true) },
                { if (d.state != DeliveryState.Delivered && d.state != DeliveryState.ManualConfirmed) PosButton("人工确认", { onConfirm(d) }, size = BtnSize.Small) else Cell("—", sub = true) },
            ), cols)
        }
    }
}

@Composable
private fun CloudTab(state: SyncUiState, onSync: () -> Unit, modifier: Modifier) {
    val c = Pos.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyValue("云端服务", if (CloudLink.CONNECTED) "已连接" else "未接入（事件仅保存在本机，不会被标为已同步）", valueColor = if (CloudLink.CONNECTED) c.ink else c.warning)
        KeyValue("待上传事件", "${state.stats.unackedEvents} 条（其中旧版 ${state.stats.legacyUnacked} 条）")
        KeyValue("最早未上传", time(state.stats.oldestUnackedAt))
        KeyValue("结账规则", CheckoutPolicy.STATUS)
        Row(Modifier.fillMaxWidth()) {
            Text("演示离线队列 · 待补发 ${state.pending} 项（补发为模拟结果）", style = Pos.type.strong, color = c.ink, modifier = Modifier.weight(1f).padding(top = 8.dp))
            PosButton("模拟补发", onSync, variant = BtnVariant.Primary, size = BtnSize.Small, enabled = state.pending > 0)
        }
        val cols = listOf(Col("类型", 0.7f), Col("内容", 2.4f), Col("产生时间", 1.2f), Col("状态", 1.1f), Col("完成时间", 1.2f, end = true))
        DataTable(cols, Modifier.weight(1f), empty = "暂无离线操作") {
            items(state.items.size) { i ->
                val it = state.items[i]
                TableRow(listOf(
                    { Cell(it.kind.label) }, { Cell(it.summary) }, { Cell(time(it.createdAt), sub = true) },
                    { if (it.state == OutboxState.Pending) Tag("待补发", fg = Pos.colors.warning) else Tag("已补发 · ${sourceLabel(it.ackSource)}", bg = Pos.colors.tint, fg = Pos.colors.primary) },
                    { Cell(time(it.doneAt), sub = true) },
                ), cols)
            }
        }
    }
}

@Composable
private fun CommandsTab(s: CommandStats) {
    Column {
        KeyValue("协调方", if (s.singleDevice) "单机模式：本机即主 POS，命令在本机提交即为主机接受" else "主 POS")
        KeyValue("已提交命令", "${s.committed} 条")
        KeyValue("冲突回执", "${s.conflicts} 条", valueColor = if (s.conflicts > 0) Pos.colors.warning else Pos.colors.ink)
        KeyValue("拒绝回执", "${s.rejected} 条", valueColor = if (s.rejected > 0) Pos.colors.warning else Pos.colors.ink)
        KeyValue("最近提交", time(s.lastAcceptedAt))
        KeyValue("未决发送命令", "${s.openCommands} 条（多机联网后使用）")
        if (!s.available) Text("命令日志暂不可读", style = Pos.type.body, color = Pos.colors.danger)
    }
}

@Composable
private fun PaymentsTab(items: List<PaymentRecord>, modifier: Modifier) {
    val cols = listOf(Col("桌台", 0.7f), Col("方式", 0.8f), Col("金额", 1f), Col("状态", 1.1f), Col("来源", 0.9f), Col("云端", 1f), Col("时间", 1.2f, end = true))
    DataTable(cols, modifier, empty = "暂无收款") {
        items(items.size) { i ->
            val p = items[i]
            TableRow(listOf(
                { Cell(p.tableId) }, { Cell(p.method) }, { Cell(yuan(p.amount)) },
                { if (p.state == PaymentState.Unknown) Tag("${p.state.label} · 仅可查单", bg = Pos.colors.dangerBg, fg = Pos.colors.danger) else Tag(p.state.label) },
                { Cell(sourceLabel(p.source), sub = true) },
                { Cell(if (p.synced) "已补发（模拟）" else "待上传", sub = true) },
                { Cell(time(p.at), sub = true) },
            ), cols)
        }
    }
}
