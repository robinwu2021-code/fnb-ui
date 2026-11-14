package ai.neargo.fnb.pos.feature.authcenter

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.data.AuthRepository
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.GroupTitle
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosDivider
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.identity.AuthState
import ai.neargo.fnb.pos.core.identity.AuthStatus
import ai.neargo.fnb.pos.core.identity.GrantKind
import ai.neargo.fnb.pos.core.identity.GrantPolicy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuthCenterViewModel(private val repo: AuthRepository, private val messages: MessageBus, val deviceId: String) : ViewModel() {
    val auth = repo.auth
    val devAvailable get() = repo.devIssuerAvailable
    private fun run(done: String, cb: (String?) -> Unit = {}, block: suspend () -> Result<Unit>) = viewModelScope.launchStorage(messages) {
        val r = block()
        cb(r.exceptionOrNull()?.message)
        messages.post(r.fold({ done }, { it.message ?: "操作失败" }))
    }
    fun issue(kind: GrantKind) = run("已签发${kind.label}授权（开发测试）") { repo.devIssue(kind) }
    fun import(token: String, cb: (String?) -> Unit) = run("已导入授权", cb) { repo.importGrant(token) }
    fun revoke() = run("已撤销本机授权") { repo.revokeCurrent() }
    fun confirmClock(email: String, pwd: String, cb: (String?) -> Unit) = run("已确认校时", cb) { repo.confirmClock(email, pwd) }
}

private fun time(ms: Long) = if (ms <= 0 || ms == Long.MAX_VALUE) "—" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
private fun remain(ms: Long): String = when {
    ms <= 0 -> "已到期"
    ms >= 86_400_000L -> "剩余 ${ms / 86_400_000L} 天 ${(ms % 86_400_000L) / 3_600_000L} 小时"
    else -> "剩余 ${ms / 3_600_000L} 小时 ${(ms % 3_600_000L) / 60_000L} 分"
}

@Composable
fun authCenterViewModel() = posViewModel { c, _ -> AuthCenterViewModel(c.auth, c.messages, c.deviceId()) }

/** 授权中心：设备身份、当前离线授权、导入/签发/撤销/校时、策略说明。 */
@Composable
fun AuthCenterPanel(vm: AuthCenterViewModel = authCenterViewModel()) {
    val a by vm.auth.collectAsStateWithLifecycle()
    var importing by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf(false) }
    val c = Pos.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupTitle("当前状态")
        KeyValue("授权状态", a.state.label, valueColor = if (a.writable && a.state != AuthState.PendingClaim) c.primary else c.warning)
        if (a.reason.isNotBlank()) KeyValue("原因", a.reason, valueColor = c.warning)
        KeyValue("营业写入", if (a.writable) "允许" else "已暂停（账本保留，可查询与导出）", valueColor = if (a.writable) c.ink else c.danger)
        KeyValue("本次登录有效至", time(a.sessionExpiresAt))
        a.grant?.let { g ->
            GroupTitle("离线授权")
            KeyValue("类型", "${g.kind.label}（默认 ${GrantPolicy.days(g.kind)} 天）")
            KeyValue("签发方", if (a.issuerEnv == "dev") "${g.iss} · 开发测试签发，非生产" else g.iss, valueColor = if (a.issuerEnv == "dev") c.warning else c.ink)
            KeyValue("有效期", "${time(g.nbf)} 至 ${time(g.exp)}")
            KeyValue("剩余", remain(a.remainingMs))
            KeyValue("角色", g.roles.joinToString("、"))
            KeyValue("门店", g.storeIds.joinToString("、"))
            KeyValue("授权版本", "epoch ${g.authEpoch} · 策略 ${g.policyVersion}")
        }
        GroupTitle("设备身份")
        KeyValue("设备 ID", vm.deviceId)
        KeyValue("设备公钥指纹", a.deviceThumbprint.take(16) + "…")
        Text("私钥保存在 Android Keystore，不可导出。数据复制到其他设备后，因指纹不同，授权会失效。", style = Pos.type.label, color = c.sub)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            PosButton("导入激活包", { importing = true }, variant = BtnVariant.Primary)
            if (vm.devAvailable) {
                PosButton("开发签发 · 短期 7 天", { vm.issue(GrantKind.Short) })
                PosButton("开发签发 · 长期 90 天", { vm.issue(GrantKind.Long) })
            }
            if (a.grant != null) PosButton("撤销本机授权", { revoking = true }, variant = BtnVariant.Danger)
            if (a.state == AuthState.ClockAnomaly) PosButton("主管确认校时", { clock = true })
        }
        PosDivider()
        GroupTitle("策略说明")
        listOf(
            "短期离线授权默认 ${GrantPolicy.shortDays} 天，长期默认 ${GrantPolicy.longDays} 天，可配置；续期须联网或导入新签名激活包，本机不能自行续期。",
            "登录会话最长 ${GrantPolicy.sessionHours} 小时，且不超过授权到期时间。",
            "授权到期、撤销、设备时间异常或设备身份不符时，暂停新营业写入，已有账本、待同步队列与打印任务保留。",
            "断网期间无法即时收到撤销；最长离线期限即最长暴露窗口。",
            "无任何授权（本地待认领）：未完成云端注册，营业数据标记为待认领；是否允许正式营业待门店确认。",
            if (vm.devAvailable) "当前为调试版：信任开发测试签发器，仅用于测试，发行版不信任。" else "发行版：仅信任生产签发，当前未配置身份服务。",
        ).forEach { line -> Row { Text("·  ", style = Pos.type.label, color = c.sub); Text(line, style = Pos.type.label, color = c.sub) } }
    }
    if (importing) {
        var token by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        PosDialog("导入激活包", onDismiss = { importing = false }, footer = {
            PosButton("取消", { importing = false }, size = BtnSize.Large)
            PosButton("验证并导入", { vm.import(token) { e -> error = e; if (e == null) importing = false } }, variant = BtnVariant.Primary, size = BtnSize.Large)
        }) {
            Text("粘贴管理员提供的签名激活包（扫码或文件内容）。会校验签名、签发方、绑定设备、门店、版本与期限。", style = Pos.type.body, color = Pos.colors.sub, modifier = Modifier.padding(bottom = 8.dp))
            PosTextField(token, { token = it }, "激活包", error = error, singleLine = false)
        }
    }
    if (revoking) PosDialog("撤销本机授权", onDismiss = { revoking = false }, footer = {
        PosButton("取消", { revoking = false }, size = BtnSize.Large)
        PosButton("确认撤销", { vm.revoke(); revoking = false }, variant = BtnVariant.Danger, size = BtnSize.Large)
    }) { Text("模拟收到云端撤销清单：立即暂停新营业写入，已有数据保留。恢复需导入新的授权。", style = Pos.type.body, color = Pos.colors.ink) }
    if (clock) {
        var email by remember { mutableStateOf("") }
        var pwd by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        PosDialog("主管确认校时", onDismiss = { clock = false }, footer = {
            PosButton("取消", { clock = false }, size = BtnSize.Large)
            PosButton("确认当前时间正确", { vm.confirmClock(email, pwd) { e -> error = e; if (e == null) clock = false } }, variant = BtnVariant.Primary, size = BtnSize.Large)
        }) {
            Text("确认设备时间已校正后，由主管验证身份并重置本机时间锚点，操作会被记录。", style = Pos.type.body, color = Pos.colors.sub, modifier = Modifier.padding(bottom = 8.dp))
            PosTextField(email, { email = it }, "主管账号")
            PosTextField(pwd, { pwd = it }, "主管密码", password = true, error = error)
        }
    }
}

@Composable
fun AuthCenterRoute() {
    Panel(Modifier.fillMaxSize().padding(Pos.metrics.gap)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Pos.metrics.pad)) {
            Text("授权中心", style = Pos.type.title, color = Pos.colors.ink)
            Text("设备身份与离线授权", style = Pos.type.label, color = Pos.colors.sub, modifier = Modifier.padding(bottom = 12.dp))
            AuthCenterPanel()
        }
    }
}

/** 授权提示条：不可写或待认领时显示在页头下方。 */
@Composable
fun AuthBanner(a: AuthStatus, onOpen: () -> Unit, onRelogin: () -> Unit) {
    if (a.writable && a.state != AuthState.PendingClaim) return
    val c = Pos.colors
    val danger = !a.writable
    Row(
        Modifier.fillMaxWidth().background(if (danger) c.dangerBg else c.tile).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (danger) a.blockMessage else "${a.state.label}：营业数据标记为待认领，导入激活包后完成授权", style = Pos.type.label, color = if (danger) c.danger else c.sub, modifier = Modifier.weight(1f))
        if (a.state == AuthState.SessionExpired) PosButton("重新登录", onRelogin, size = BtnSize.Small, variant = BtnVariant.Primary)
        else PosButton("授权中心", onOpen, size = BtnSize.Small)
    }
}
