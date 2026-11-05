package ai.neargo.fnb.pos.feature.auth

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.data.SessionRepository
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.StoreSummary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import ai.neargo.fnb.pos.core.designsystem.component.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 登录 / 注册表单状态：错误留在表单内，提交中禁止重复提交；密码不进入 SavedState。 */
data class AuthUiState(val busy: Boolean = false, val error: String? = null)

class AuthViewModel(private val session: SessionRepository) : ViewModel() {
    private val s = MutableStateFlow(AuthUiState())
    val state = s.asStateFlow()

    private fun run(block: suspend () -> Result<Unit>) {
        if (s.value.busy) return
        s.value = AuthUiState(busy = true)
        viewModelScope.launch {
            val r = block()
            s.value = AuthUiState(error = r.exceptionOrNull()?.message)
        }
    }

    fun login(email: String, pwd: String) = run { session.login(email, pwd) }
    fun register(merchant: String, email: String, pwd: String, confirm: String) = run { session.register(merchant, email, pwd, confirm) }
    fun demo() = run { session.enterDemo(); Result.success(Unit) }
    fun clearError() { s.value = s.value.copy(error = null) }
}

/**
 * 登录 / 注册（离线本机账号）：邮箱格式、密码至少 8 位、注册两次一致；
 * 本机只保存加盐哈希。阶段三改为服务端认证，界面不变。
 */
@Composable
fun AuthRoute() {
    val vm = posViewModel { c, _ -> AuthViewModel(c.session) }
    val state by vm.state.collectAsStateWithLifecycle()
    var register by rememberSaveable { mutableStateOf(false) }
    CenterCard {
        if (register) RegisterForm(state, vm::register, onSwitch = { register = false; vm.clearError() })
        else LoginForm(state, vm::login, vm::demo, onSwitch = { register = true; vm.clearError() })
    }
}

@Composable
private fun CenterCard(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Pos.colors.canvas).verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
        Panel(Modifier.widthIn(max = 440.dp).fillMaxWidth()) { Column(Modifier.padding(24.dp)) { content() } }
    }
}

@Composable
private fun Brand(subtitle: String) {
    Text("南山小馆 POS", style = Pos.type.title, color = Pos.colors.ink)
    Text(subtitle, style = Pos.type.label, color = Pos.colors.sub)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun LoginForm(state: AuthUiState, onLogin: (String, String) -> Unit, onDemo: () -> Unit, onSwitch: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    // 密码只放在内存，不进 SavedState
    var pwd by remember { mutableStateOf("") }
    Brand("登录 · 离线模式下使用本机账号")
    val e = fieldOf(state.error)
    PosTextField(email, { email = it }, "邮箱", error = e["email"], keyboard = KeyboardOptions(keyboardType = KeyboardType.Email))
    PosTextField(pwd, { pwd = it }, "密码（至少 8 位）", password = true, error = e["pwd"] ?: e["form"])
    Spacer(Modifier.height(12.dp))
    PosButton("登录", { onLogin(email, pwd) }, Modifier.fillMaxWidth(), variant = BtnVariant.Primary, size = BtnSize.Large, loading = state.busy)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        PosButton("注册商户", onSwitch, variant = BtnVariant.Text)
        PosButton("直接体验", onDemo, variant = BtnVariant.Text)
    }
}

@Composable
private fun RegisterForm(state: AuthUiState, onRegister: (String, String, String, String) -> Unit, onSwitch: () -> Unit) {
    var merchant by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Brand("注册商户 · 账号保存在本机，联网后可迁移")
    val e = fieldOf(state.error)
    PosTextField(merchant, { merchant = it }, "商户名称", error = e["merchant"])
    PosTextField(email, { email = it }, "邮箱", error = e["email"], keyboard = KeyboardOptions(keyboardType = KeyboardType.Email))
    PosTextField(pwd, { pwd = it }, "密码（至少 8 位）", password = true, error = e["pwd"])
    PosTextField(confirm, { confirm = it }, "确认密码", password = true, error = e["confirm"] ?: e["form"])
    Spacer(Modifier.height(12.dp))
    PosButton("注册并选择门店", { onRegister(merchant, email, pwd, confirm) }, Modifier.fillMaxWidth(), variant = BtnVariant.Primary, size = BtnSize.Large, loading = state.busy)
    PosButton("已有账号，去登录", onSwitch, Modifier.padding(top = 8.dp), variant = BtnVariant.Text)
}

/** 把仓库返回的错误归到对应字段，就地显示；无法归类的放在最后一个字段下。 */
private fun fieldOf(error: String?): Map<String, String> = when {
    error == null -> emptyMap()
    "商户" in error -> mapOf("merchant" to error)
    "两次" in error -> mapOf("confirm" to error)
    "邮箱" in error -> mapOf("email" to error)
    "密码" in error -> mapOf("pwd" to error)
    else -> mapOf("form" to error)
}

/** 选择门店：门店卡片（名称、编码、地址、桌台与用餐中数量）；切店数据隔离。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorePicker(stores: List<StoreSummary>, current: String?, onPick: (String) -> Unit, onLogout: () -> Unit, onCancel: (() -> Unit)? = null) {
    val c = Pos.colors
    val t = Pos.type
    Box(Modifier.fillMaxSize().background(c.canvas).verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("选择门店", style = t.title, color = c.ink)
                    Text("各门店的桌台、商品、餐牌与账单分别保存在本机，互不影响", style = t.label, color = c.sub)
                }
                if (onCancel != null) PosButton("返回", onCancel, variant = BtnVariant.Text)
                PosButton("退出登录", onLogout, variant = BtnVariant.Text)
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
                stores.forEach { s ->
                    val on = s.id == current
                    Card(
                        { onPick(s.id) }, Modifier.weight(1f).widthIn(min = 260.dp), shape = Pos.metrics.panel,
                        colors = CardDefaults.cardColors(containerColor = if (on) c.tint else c.surface), elevation = CardDefaults.cardElevation(0.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(s.name, style = t.section, color = if (on) c.primary else c.ink)
                            Text("门店码 ${s.code}", style = t.label, color = c.sub)
                            Text(s.address, style = t.label, color = c.sub)
                            Spacer(Modifier.height(10.dp))
                            Row {
                                Text("${s.tables} 张桌台", style = t.body, color = c.ink)
                                Spacer(Modifier.width(12.dp))
                                Text("用餐中 ${s.dining}", style = t.body, color = c.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
