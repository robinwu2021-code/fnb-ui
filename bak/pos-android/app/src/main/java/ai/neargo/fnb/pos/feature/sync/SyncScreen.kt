package ai.neargo.fnb.pos.feature.sync

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.cloud.CloudBadge
import ai.neargo.fnb.pos.core.cloud.CloudMonitor
import ai.neargo.fnb.pos.core.cloud.CloudSettings
import ai.neargo.fnb.pos.core.cloud.CloudState
import ai.neargo.fnb.pos.core.cloud.CloudProbe
import ai.neargo.fnb.pos.core.cloud.PushState
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
import ai.neargo.fnb.pos.core.model.TicketDelivery
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
    val deliveries: List<TicketDelivery> = emptyList(), val payments: List<PaymentRecord> = emptyList(), val stats: CommandStats = CommandStats(),
    val cloud: CloudState = CloudState(),
    val push: PushState = PushState(),
) {
    val kitchenAction get() = deliveries.count { it.needsAction }
    val kitchenPending get() = deliveries.count { g -> !g.needsAction && !g.cancelled && g.records.any { it.state == DeliveryState.Pending && it.supersededBy.isBlank() } }
}

class SyncViewModel(
    private val sync: SyncRepository, private val device: DeviceRepository, config: StoreConfigRepository,
    private val status: SyncStatusRepository, private val messages: MessageBus,
    private val cloud: CloudMonitor? = null, push: kotlinx.coroutines.flow.Flow<PushState> = kotlinx.coroutines.flow.flowOf(PushState()),
    private val probe: suspend (CloudSettings) -> Result<CloudProbe> = { Result.failure(IllegalStateException("未接入")) },
) : ViewModel() {
    private val cloudState = combine(cloud?.state ?: kotlinx.coroutines.flow.flowOf(CloudState()), push) { c, ps -> c to ps }
    val state = combine(sync.outbox, sync.pendingCount, device.status, sync.deliveryGroups, combine(config.current, status.stats, cloudState) { s, st, cs -> Triple(s, st, cs) }) { o, p, d, dl, (s, st, cs) ->
        SyncUiState(o, p, d, dl, s?.payments.orEmpty().sortedByDescending { it.at }, st, cs.first, cs.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    init { viewModelScope.launch { status.refresh() } }

    /** 演示队列补发：离线时只提示，不清队列；模拟结果不等于云端回执。 */
    fun syncNow() = viewModelScope.launchStorage(messages) {
        if (!device.status.value.online) { messages.post("当前离线，联网后会自动补发"); return@launchStorage }
        val n = sync.syncNow()
        messages.post(if (n > 0) "已模拟补发 $n 项（未接入云端）" else "没有待补发的演示队列")
    }

    /** 云端：立即上传一轮（不等探测防抖） */
    fun cloudSync() = viewModelScope.launch {
        val m = cloud ?: return@launch
        m.syncNow().onSuccess { r ->
            messages.post(when {
                r.stoppedBy != null -> "上传中断：${r.stoppedBy.message}（已上传 ${r.uploaded} 条，其余保留在本机）"
                r.issues > 0 -> "已上传 ${r.uploaded} 条，${r.issues} 条需处理"
                else -> "已上传 ${r.uploaded} 条" + if (r.waiting > 0) "，${r.waiting} 条等服务端接口" else ""
            })
        }.onFailure { messages.post(it.message ?: "同步失败") }
    }
    fun cloudTest(st: CloudSettings, done: (String) -> Unit) = viewModelScope.launch {
        probe(st).onSuccess { done("连接成功：门店 ${it.storeNo}，服务端桌台 ${it.tables} 张") }.onFailure { done("连接失败：${it.message}") }
    }
    fun cloudSave(st: CloudSettings) { cloud?.configure(st); messages.post(if (st.baseUrl.isBlank()) "已关闭云端上传" else "已保存云端设置") }
    fun cloudRetry(eventId: String) { cloud?.retry(eventId); messages.post("已重新排队上传") }
    fun cloudSkip(eventId: String) = viewModelScope.launch { cloud?.skip(eventId); messages.post("已跳过，这条不再上传；该订单后续事件继续") }

    fun confirm(ticketId: String, reason: String, done: (String?) -> Unit) = viewModelScope.launchStorage(messages) {
        val r = sync.confirmDelivery(ticketId, reason)
        done(r.exceptionOrNull()?.message)
        if (r.isSuccess) messages.post("已记录人工确认送达")
    }

    fun printForScreen(ticketId: String) = viewModelScope.launchStorage(messages) {
        sync.printForScreen(ticketId).onSuccess { messages.post("已加入改打纸单，送达后视为厨房已收到") }.onFailure { messages.post(it.message ?: "改打失败") }
    }
}

@Composable
fun SyncRoute() {
    val vm = posViewModel { c, _ -> SyncViewModel(c.sync, c.device, c.config, c.syncStatus, c.messages, c.cloud, c.cloudPush.state, c::testCloud) }
    val state by vm.state.collectAsStateWithLifecycle()
    SyncScreen(state, vm::syncNow, vm::confirm, vm::printForScreen, CloudActions(vm::cloudSync, vm::cloudTest, vm::cloudSave, vm::cloudRetry, { vm.cloudSkip(it) }))
}

private val fmt get() = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
private fun time(ms: Long) = if (ms > 0) fmt.format(Date(ms)) else "—"

/**
 * 同步中心（N3-03）：本机命令、主机回执、厨房任务、云事件、收款分别展示。
 * 「有网」不等于已同步；模拟结果明确标注；未得远端持久回执不清队列。
 */
@Composable
fun SyncScreen(state: SyncUiState, onSync: () -> Unit, onConfirm: (String, String, (String?) -> Unit) -> Unit, onPrintForScreen: (String) -> Unit = {}, cloud: CloudActions = CloudActions()) {
    var tab by rememberSaveable { mutableStateOf(SyncTab.Kitchen) }
    var confirming by remember { mutableStateOf<TicketDelivery?>(null) }
    val c = Pos.colors
    Panel(Modifier.fillMaxSize().padding(Pos.metrics.gap)) {
        Column(Modifier.padding(Pos.metrics.pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageHeader("同步中心", (if (state.device.online) "网络在线" else if (state.device.forceOffline) "离线（演示强制离线）" else "离线（无网络）") +
                " · 网络在线不代表已同步，各端点分别确认")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Endpoint("本机命令", "${state.stats.committed} 已提交", if (state.stats.conflicts + state.stats.rejected > 0) "冲突 ${state.stats.conflicts} · 拒绝 ${state.stats.rejected}" else "最近 ${time(state.stats.lastAcceptedAt)}", ok = state.stats.conflicts + state.stats.rejected == 0, Modifier.weight(1f)) { tab = SyncTab.Commands }
                Endpoint("主机回执", if (state.stats.singleDevice) "单机 · 本机即主机" else "—", "未决发送 ${state.stats.openCommands}", ok = state.stats.openCommands == 0, Modifier.weight(1f)) { tab = SyncTab.Commands }
                Endpoint("厨房任务", if (state.kitchenAction > 0) "${state.kitchenAction} 张待处理" else "无异常", "等待送达 ${state.kitchenPending} 张 · 按档口出单方式判定", ok = state.kitchenAction == 0, Modifier.weight(1f)) { tab = SyncTab.Kitchen }
                Endpoint("云事件", cloudTitle(state.cloud.badge), "待上传 ${state.stats.unackedEvents} 条", ok = state.cloud.badge is CloudBadge.Online || state.cloud.badge == CloudBadge.ViaHost, Modifier.weight(1f)) { tab = SyncTab.Cloud }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SyncTab.entries.forEach { t -> PosButton(t.label, { tab = t }, size = BtnSize.Small, selected = tab == t) }
            }
            when (tab) {
                SyncTab.Kitchen -> KitchenTab(state.deliveries, Modifier.weight(1f), onPrintForScreen) { confirming = it }
                SyncTab.Cloud -> CloudTab(state, onSync, cloud, Modifier.weight(1f))
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
            if (d.issue.isNotBlank()) Text("当前问题：${d.issue}", style = Pos.type.body, color = c.warning, modifier = Modifier.padding(bottom = 4.dp))
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

private fun channelText(d: KitchenDelivery?, name: String) = d?.let { "$name ${it.state.label}${if (it.source == "simulation" && it.channel == "paper") "（模拟）" else ""}" }

@Composable
private fun KitchenTab(items: List<TicketDelivery>, modifier: Modifier, onPrintForScreen: (String) -> Unit, onConfirm: (TicketDelivery) -> Unit) {
    val cols = listOf(Col("票据", 2f), Col("出单方式", 1f), Col("纸单 / 厨房屏", 1.8f), Col("状态", 0.9f), Col("说明", 1.6f), Col("更新时间", 1f), Col("操作", 1.4f, end = true))
    DataTable(cols, modifier, empty = "暂无厨房任务") {
        items(items.size) { i ->
            val g = items[i]
            val channels = listOfNotNull(channelText(g.paper, "纸单"), channelText(g.screen, "屏"), channelText(g.fallback, "改打")).joinToString(" · ")
            val manual = g.records.firstOrNull { it.state == DeliveryState.ManualConfirmed }
            TableRow(listOf(
                { Cell(g.label) }, { Cell(g.mode.label, sub = true) }, { Cell(channels.ifBlank { "—" }, sub = true) },
                {
                    when {
                        g.cancelled -> Tag("已取消")
                        g.needsAction -> Tag("需处理", bg = Pos.colors.dangerBg, fg = Pos.colors.danger)
                        g.records.any { it.state == DeliveryState.Pending && it.supersededBy.isBlank() } -> Tag("等待送达")
                        else -> Tag("已满足", bg = Pos.colors.tint, fg = Pos.colors.primary)
                    }
                },
                { Cell(when { g.issue.isNotBlank() -> g.issue; g.fallback?.let { it.state == DeliveryState.Delivered || it.state == DeliveryState.ManualConfirmed } == true && g.screen?.state == DeliveryState.Failed -> "厨房屏未收到，已改打纸单"; manual != null -> "${manual.confirmedBy}：${manual.reason}"; else -> g.records.firstOrNull { it.lastError.isNotBlank() }?.lastError ?: "—" }, sub = true) },
                { Cell(time(g.updatedAt), sub = true) },
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (g.canFallback) PosButton("改打纸单", { onPrintForScreen(g.ticketId) }, size = BtnSize.Small)
                        if (g.needsAction) PosButton("人工确认", { onConfirm(g) }, size = BtnSize.Small) else if (!g.canFallback) Cell("—", sub = true)
                    }
                },
            ), cols)
        }
    }
}

/** 云端操作（同步中心「云事件」页） */
class CloudActions(
    val sync: () -> Unit = {}, val test: (CloudSettings, (String) -> Unit) -> Unit = { _, _ -> },
    val save: (CloudSettings) -> Unit = {}, val retry: (String) -> Unit = {}, val skip: (String) -> Unit = {},
)

private fun cloudTitle(b: CloudBadge) = when (b) {
    CloudBadge.NotConfigured -> "未设置云端"
    CloudBadge.ViaHost -> "经主 POS 上传"
    is CloudBadge.Online -> "在线"
    is CloudBadge.Offline -> "离线"
    is CloudBadge.Syncing -> "上传中 ${b.done}/${b.total}"
    is CloudBadge.NeedsAction -> "${b.count} 条需处理"
}

@Composable
private fun CloudTab(state: SyncUiState, onSync: () -> Unit, actions: CloudActions, modifier: Modifier) {
    val c = Pos.colors
    val cs = state.cloud
    var editing by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val statusText = when (val b = cs.badge) {
            CloudBadge.NotConfigured -> "未设置（事件仅保存在本机，不会被标为已同步）"
            CloudBadge.ViaHost -> "副 POS 不直连云端，由主 POS 统一上传"
            is CloudBadge.Online -> "在线 · ${cs.settings.baseUrl}"
            is CloudBadge.Offline -> "离线" + (if (b.reason.isNotBlank()) "：${b.reason}" else "") + " · 网络恢复后自动上传"
            is CloudBadge.Syncing -> "正在上传 ${b.done}/${b.total}"
            is CloudBadge.NeedsAction -> "在线，${b.count} 条上传被服务端拒绝，需处理后该订单才继续上传"
        }
        KeyValue("云端服务", statusText, valueColor = when (cs.badge) { is CloudBadge.Online, CloudBadge.ViaHost -> c.ink; is CloudBadge.NeedsAction -> c.danger; else -> c.warning })
        if (cs.settings.configured) KeyValue("服务端门店", cs.settings.storeNo + (if (cs.tables > 0) " · 桌台 ${cs.tables} 张" else ""))
        KeyValue("待上传事件", "${state.stats.unackedEvents} 条（其中旧版 ${state.stats.legacyUnacked} 条，旧版不上传）")
        KeyValue("最早未上传", time(state.stats.oldestUnackedAt))
        cs.lastRound?.let { r ->  KeyValue("最近一次上传", "${time(cs.lastSyncAt)} · 上传 ${r.uploaded} 条 · 本机事件 ${r.local} 条 · 等服务端接口 ${r.waiting} 条" + (r.stoppedBy?.let { " · 中断：${it.message}" } ?: "")) }
        if (cs.settings.configured && cs.uploader) KeyValue("门店推送", state.push.let { p ->
            (if (p.connected) "已连接" else "未连接" + (if (p.lastError.isNotBlank()) "（${p.lastError}）" else "")) +
                (if (p.received > 0) " · 已收 ${p.received} 条 · 最近 ${p.lastTopic} ${time(p.lastAt)}" else "")
        }, valueColor = if (state.push.connected) c.ink else c.sub)
        KeyValue("已对接", "开台、换桌、送厨下单、日结；结账、交班待服务端接口；点菜草稿、备注、挂单、清台、催菜上菜只在本机")
        KeyValue("结账规则", CheckoutPolicy.STATUS)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cs.uploader) PosButton("云端设置", { editing = true }, size = BtnSize.Small)
            PosButton("立即上传", actions.sync, variant = BtnVariant.Primary, size = BtnSize.Small, enabled = cs.uploader && cs.settings.configured)
        }
        if (cs.issues.isNotEmpty()) {
            Text("需处理（服务端拒绝）：排除原因后点「重试」；已在后台处理则点「跳过」。处理前该订单后续事件暂停上传", style = Pos.type.strong, color = c.danger, modifier = Modifier.padding(top = 6.dp))
            val cols = listOf(Col("事件", 0.9f), Col("对象", 1.2f), Col("原因", 2.6f), Col("时间", 1f), Col("", 1.4f, end = true))
            DataTable(cols, Modifier.weight(1f), empty = "") {
                items(cs.issues.size) { i ->
                    val it = cs.issues[i]
                    TableRow(listOf(
                        { Cell(it.type) }, { Cell(it.aggregateId, sub = true) }, { Cell(if (it.code > 0) "${it.message}（${it.code}）" else it.message) },
                        { Cell(time(it.at), sub = true) }, { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PosButton("重试", { actions.retry(it.eventId) }, size = BtnSize.Small); PosButton("跳过", { actions.skip(it.eventId) }, size = BtnSize.Small) } },
                    ), cols)
                }
            }
        }
        // 有需处理时把空间给需处理列表；演示离线队列在没有需处理时显示
        if (cs.issues.isEmpty()) {
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
    if (editing) CloudSettingsDialog(cs.settings, actions) { editing = false }
}

/** 云端设置：地址、服务端门店号、终端令牌；可先测连接再保存；地址留空 = 关闭云端上传 */
@Composable
private fun CloudSettingsDialog(current: CloudSettings, actions: CloudActions, onClose: () -> Unit) {
    var url by remember { mutableStateOf(current.baseUrl) }
    var store by remember { mutableStateOf(current.storeNo) }
    var token by remember { mutableStateOf(current.token) }
    var result by remember { mutableStateOf<String?>(null) }
    val draft = CloudSettings(url, store, token)
    PosDialog("云端设置", onDismiss = onClose, subtitle = "只有主 POS / 单机连云端；副 POS 经主 POS 上传", footer = {
        PosButton("测试连接", { result = "连接中…"; actions.test(draft) { result = it } }, size = BtnSize.Large, enabled = draft.configured)
        PosButton("取消", onClose, size = BtnSize.Large)
        PosButton("保存", { actions.save(draft); onClose() }, variant = BtnVariant.Primary, size = BtnSize.Large, enabled = draft.configured || url.isBlank())
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PosTextField(url, { url = it; result = null }, "服务端地址", hint = "服务端根地址，例如 https://api.example.com（不带 /biz）", autoFocus = true)
            PosTextField(store, { store = it; result = null }, "服务端门店号", hint = "后台门店的 storeNo")
            PosTextField(token, { token = it; result = null }, "终端令牌", password = true, hint = "后台签发给本机的令牌，可留空（内网测试）")
            result?.let { Text(it, style = Pos.type.body, color = if (it.startsWith("连接成功")) Pos.colors.primary else Pos.colors.warning) }
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
