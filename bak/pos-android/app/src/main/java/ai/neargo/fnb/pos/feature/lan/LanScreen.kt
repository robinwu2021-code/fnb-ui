package ai.neargo.fnb.pos.feature.lan

import ai.neargo.fnb.pos.app.appContainer
import ai.neargo.fnb.pos.core.data.AppContainer
import ai.neargo.fnb.pos.core.data.LanRole
import ai.neargo.fnb.pos.core.data.LanState
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.GroupTitle
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.lan.HostStatus
import ai.neargo.fnb.pos.core.lan.LinkStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun time(ms: Long) = if (ms <= 0 || ms == Long.MAX_VALUE) "—" else SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(ms))

/** 本机局域网 IPv4 地址（供副机手动填写）。 */
private fun localAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().map { it.hostAddress.orEmpty() }
}.getOrDefault(emptyList())

/** 主副 POS 管理（N5）：角色、配对、设备管理、连接与待确认操作。 */
@Composable
fun LanPanel(c: AppContainer = appContainer()) {
    val lan by c.lan.collectAsStateWithLifecycle()
    val host by c.lanHost.status.collectAsStateWithLifecycle()
    val link by c.lanLink.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }
    val notify: (Result<*>, String) -> Unit = { r, ok -> c.messages.post(r.fold({ ok }, { it.message ?: "操作失败" })) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupTitle("本机角色")
        KeyValue("当前角色", lan.role.label, valueColor = if (lan.role == LanRole.Standalone) Pos.colors.ink else Pos.colors.primary)
        Text("主 POS 是门店唯一协调方，副 POS 的开台、点餐、送厨、结账都经主 POS 执行；管理、库存、交班、打印仅主 POS 可做。", style = Pos.type.label, color = Pos.colors.sub)
        when (lan.role) {
            LanRole.Standalone -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosButton("设为主 POS", { scope.launch { notify(runCatching { c.setHostRole(true) }, "已设为主 POS") } }, variant = BtnVariant.Primary)
                PosButton("作为副 POS 连接主机", { connecting = true })
            }
            LanRole.Host -> HostSection(c, lan, host, { b -> scope.launch(block = b) }, notify)
            LanRole.Secondary -> SecondarySection(c, lan, link, { b -> scope.launch(block = b) }, notify)
        }
        if (lan.log.isNotEmpty()) {
            GroupTitle("记录")
            lan.log.takeLast(8).reversed().forEach { Text(it.substringAfter(' ').let { line -> "${time(it.substringBefore(' ').toLongOrNull() ?: 0)}  $line" }, style = Pos.type.label, color = Pos.colors.sub) }
        }
    }
    if (connecting) {
        var addr by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        PosDialog("连接主 POS", onDismiss = { connecting = false }, footer = {
            PosButton("取消", { connecting = false }, size = BtnSize.Large)
            PosButton("验证并配对", {
                busy = true
                scope.launch { val r = c.lanLink.pair(addr, code); busy = false; error = r.exceptionOrNull()?.message; if (r.isSuccess) { connecting = false; c.messages.post("已与主 POS 配对") } }
            }, variant = BtnVariant.Primary, size = BtnSize.Large, loading = busy)
        }) {
            Text("在主 POS 的「主副 POS」页生成 8 位配对码，填写主机地址（IP:端口）与配对码。配对后本机只认这台主机的密钥。", style = Pos.type.body, color = Pos.colors.sub, modifier = Modifier.padding(bottom = 8.dp))
            PosTextField(addr, { addr = it }, "主机地址", hint = "例如 192.168.1.20:47600")
            PosTextField(code, { code = it.filter(Char::isDigit).take(8) }, "配对码", error = error, keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }
}

@Composable
private fun HostSection(c: AppContainer, lan: LanState, host: HostStatus, launch: (suspend kotlinx.coroutines.CoroutineScope.() -> Unit) -> Any, notify: (Result<*>, String) -> Unit) {
    GroupTitle("主机服务")
    KeyValue("服务状态", if (host.running) "运行中 · 端口 ${host.port}" else "未运行", valueColor = if (host.running) Pos.colors.primary else Pos.colors.warning)
    KeyValue("本机地址", localAddresses().joinToString("、").ifBlank { "未获取到局域网地址" })
    host.code?.let { code ->
        val left = ((code.expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        KeyValue("配对码", "${code.code.chunked(4).joinToString(" ")} · ${left / 60} 分 ${left % 60} 秒内有效，最多 3 次尝试", valueColor = Pos.colors.primary)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PosButton("生成配对码", { c.lanHost.newPairingCode() }, variant = BtnVariant.Primary, enabled = host.running)
        if (!host.running) PosButton("启动服务", { launch { notify(runCatching { c.setHostRole(true) }, "主机服务已启动") } })
        PosButton("停止主 POS", { launch { notify(runCatching { c.setHostRole(false) }, "已回到单机") } }, variant = BtnVariant.Danger)
    }
    GroupTitle("已配对副机（${lan.paired.count { it.revokedAt == 0L }}）")
    if (lan.paired.isEmpty()) Text("暂无副机", style = Pos.type.body, color = Pos.colors.sub)
    lan.paired.forEach { d ->
        val online = d.deviceId in host.connected
        KeyValue(d.name, "${d.thumbprint.take(10)}… · 配对 ${time(d.pairedAt)} · 最近 ${time(d.lastSeen)}") {
            when {
                d.revokedAt > 0 -> Tag("已撤销", bg = Pos.colors.dangerBg, fg = Pos.colors.danger)
                else -> {
                    if (online) Tag("在线", bg = Pos.colors.tint, fg = Pos.colors.primary) else Tag("离线")
                    PosButton("撤销", { launch { notify(runCatching { c.revokeDevice(d.deviceId) }, "已撤销 ${d.name}") } }, size = BtnSize.Small, variant = BtnVariant.Danger)
                }
            }
        }
    }
}

@Composable
private fun SecondarySection(c: AppContainer, lan: LanState, link: LinkStatus, launch: (suspend kotlinx.coroutines.CoroutineScope.() -> Unit) -> Any, notify: (Result<*>, String) -> Unit) {
    GroupTitle("主机连接")
    KeyValue("主机", "${lan.hostName} · ${lan.hostAddress}")
    KeyValue("连接状态", if (link.connected) "已连接（加密）" else "未连接 · 本机只读", valueColor = if (link.connected) Pos.colors.primary else Pos.colors.danger)
    if (link.error.isNotBlank() && !link.connected) KeyValue("原因", link.error, valueColor = Pos.colors.warning)
    KeyValue("最近同步", time(maxOf(link.lastSyncAt, lan.replicaAt)))
    KeyValue("会话有效至", time(link.sessionExpiresAt.takeIf { it > 0 } ?: lan.sessionExpiresAt))
    KeyValue("待主机确认", "${lan.pending.size} 项（以同一编号重发，不会重复执行）")
    lan.pending.forEach { p -> Text("· ${p.summary} · ${time(p.createdAt)} · ${p.lastError.ifBlank { "等待中" }}", style = Pos.type.label, color = Pos.colors.warning) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PosButton("立即同步", { launch { notify(runCatching { c.lanLink.flushPending(); c.lanLink.pull() }, "已与主机同步") } }, variant = BtnVariant.Primary)
        PosButton("解除配对", { launch { c.lanLink.unpair(); c.messages.post("已解除配对，回到单机") } }, variant = BtnVariant.Danger)
    }
}

/** 副机失联提示：只读。 */
@Composable
fun LanBanner(c: AppContainer = appContainer()) {
    val lan by c.lan.collectAsStateWithLifecycle()
    val link by c.lanLink.status.collectAsStateWithLifecycle()
    if (lan.role != LanRole.Secondary) return
    val down = !link.connected
    if (!down && lan.pending.isEmpty()) return
    Row(Modifier.fillMaxWidth().background(if (down) Pos.colors.dangerBg else Pos.colors.tile).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (down) "主 POS 未连接：本机只读，恢复连接后继续（${lan.hostName}）" else "${lan.pending.size} 项操作等待主 POS 确认",
            style = Pos.type.label, color = if (down) Pos.colors.danger else Pos.colors.sub)
    }
}
