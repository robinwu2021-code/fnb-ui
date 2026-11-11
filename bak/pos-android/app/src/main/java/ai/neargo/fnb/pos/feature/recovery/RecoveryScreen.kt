package ai.neargo.fnb.pos.feature.recovery

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.data.BackupInfo
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.RecoveryRepository
import ai.neargo.fnb.pos.core.data.RecoveryStatus
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.GroupTitle
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosDivider
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecoveryViewModel(private val recovery: RecoveryRepository, private val messages: MessageBus) : ViewModel() {
    private val _status = MutableStateFlow<RecoveryStatus?>(null)
    val status: StateFlow<RecoveryStatus?> = _status
    val busy = MutableStateFlow(false)
    val blocked = recovery.blocked

    fun refresh() = viewModelScope.launch { _status.value = recovery.status() }

    private fun run(done: String, block: suspend () -> Result<*>) = viewModelScope.launch {
        busy.value = true
        val r = block()
        busy.value = false
        messages.post(r.fold({ done }, { it.message ?: "操作失败" }))
        refresh()
    }

    fun backup() = run("已生成备份") { recovery.backup() }
    fun exportRaw() = run("已导出原始数据库副本") { recovery.exportRaw() }
    fun restore(name: String) = run("已从备份恢复，并已保留恢复前数据") { recovery.restore(name) }
}

private fun time(ms: Long) = if (ms <= 0) "—" else SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(ms))
private fun size(b: Long) = if (b < 1024 * 1024) "${b / 1024} KB" else "%.1f MB".format(b / 1048576.0)

/** 数据与恢复面板：恢复点、迁移记录、备份列表。正常及阻断状态共用。 */
@Composable
fun DataRecoveryPanel(vm: RecoveryViewModel = posViewModel { c, _ -> RecoveryViewModel(c.recovery, c.messages) }) {
    val status by vm.status.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<BackupInfo?>(null) }
    LaunchedEffect(Unit) { vm.refresh() }
    val s = status ?: return
    val c = Pos.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupTitle("当前恢复点")
        KeyValue("本机设备 ID", s.deviceId.ifBlank { "未分配" })
        val p = s.point
        if (p == null) Text("数据库无法打开，无法读取恢复点", style = Pos.type.body, color = c.danger)
        else {
            KeyValue("数据库版本", "v${p.dbVersion}")
            KeyValue("最新事件序号", p.lastSequence.toString())
            KeyValue("未确认事件", "${p.unackedEvents} 条（其中旧版 ${p.legacyEvents} 条）")
            KeyValue("未决发送命令", "${p.openCommands} 条")
            KeyValue("待同步队列", "${p.pendingQueue} 项")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosButton("立即备份", vm::backup, variant = BtnVariant.Primary, enabled = s.blocked == null, loading = busy)
            PosButton("导出原始数据库", vm::exportRaw, loading = busy)
        }
        Text("备份为同一恢复点的完整副本（业务、事件、命令收发记录及版本），保存在本机应用目录。", style = Pos.type.label, color = c.sub)
        PosDivider()
        GroupTitle("备份（${s.backups.size}）")
        if (s.backups.isEmpty()) Text("暂无备份", style = Pos.type.body, color = c.sub)
        s.backups.forEach { b ->
            KeyValue(b.name, "${time(b.at)} · ${size(b.size)}${if (b.raw) " · 原始导出" else ""}") {
                if (!b.raw) PosButton("恢复", { confirm = b }, size = BtnSize.Small, enabled = !busy)
            }
        }
        PosDivider()
        GroupTitle("迁移记录")
        if (s.migrations.isEmpty()) Text("—", style = Pos.type.body, color = c.sub)
        s.migrations.forEach { (v, at, note) -> KeyValue("v$v · ${time(at)}", note) }
    }
    confirm?.let { b ->
        PosDialog("从备份恢复", onDismiss = { confirm = null }, footer = {
            PosButton("取消", { confirm = null }, size = BtnSize.Large)
            PosButton("确认恢复", { vm.restore(b.name); confirm = null }, variant = BtnVariant.Danger, size = BtnSize.Large)
        }) {
            Text("将用「${b.name}」（${time(b.at)}）替换本机当前数据。", style = Pos.type.body, color = c.ink)
            Text("恢复前会自动保留当前数据的副本。备份之后产生的订单、收款与事件不会出现在恢复后的数据中，请核对缺口。", style = Pos.type.body, color = c.danger, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

/** 启动时本地库无法读取：只读诊断页。不进入工作台、不回落演示数据。 */
@Composable
fun BlockedRecoveryScreen(reason: String) {
    Column(Modifier.fillMaxSize().background(Pos.colors.canvas).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().background(Pos.colors.surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("本地数据需要恢复", style = Pos.type.title, color = Pos.colors.ink)
            Text(reason, style = Pos.type.body, color = Pos.colors.danger)
            Text("为避免覆盖原有订单与收款，已暂停所有营业操作。可先导出原始数据库交技术支持，或从备份恢复。", style = Pos.type.body, color = Pos.colors.sub)
            PosDivider()
            DataRecoveryPanel()
        }
    }
}
