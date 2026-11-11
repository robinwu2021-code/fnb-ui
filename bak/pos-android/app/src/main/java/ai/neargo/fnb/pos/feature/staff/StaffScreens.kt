package ai.neargo.fnb.pos.feature.staff

import ai.neargo.fnb.pos.app.appContainer
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
import ai.neargo.fnb.pos.core.model.Employee
import ai.neargo.fnb.pos.core.model.Perm
import ai.neargo.fnb.pos.core.model.Role
import ai.neargo.fnb.pos.core.model.Roles
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** 员工上岗：员工模式下无人上岗时全屏显示；选员工 → 输入 PIN。 */
@Composable
fun StaffLockScreen(storeName: String, onLogout: () -> Unit) {
    val c = appContainer()
    val st by c.staff.staff.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var who by remember { mutableStateOf<Employee?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Pos.colors.canvas).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().background(Pos.colors.surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("员工上岗", style = Pos.type.title, color = Pos.colors.ink)
            Text("$storeName · 请选择员工并输入 PIN", style = Pos.type.body, color = Pos.colors.sub)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                st.employees.filter { it.enabled }.sortedBy { it.role.ordinal }.forEach { e ->
                    PosButton("${e.name} · ${e.role.label}", { who = e; pin = ""; error = null }, size = BtnSize.Large, selected = who?.id == e.id)
                }
            }
            who?.let { e ->
                PosTextField(pin, { pin = it.filter(Char::isDigit).take(6); error = null }, "${e.name} 的 PIN", error = error, password = true, keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                PosButton("上岗", { scope.launch { c.staff.signInStaff(e.id, pin).onFailure { error = it.message } } }, variant = BtnVariant.Primary, size = BtnSize.Large, enabled = pin.length >= 4)
            }
            PosButton("退出门店账号", onLogout, variant = BtnVariant.Text)
        }
    }
}

/** 员工与权限管理：员工列表、新建/编辑（角色、PIN、启停），角色权限对照。 */
@Composable
fun StaffPanel() {
    val c = appContainer()
    val st by c.staff.staff.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Employee?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!st.required) Text("尚未启用员工模式：门店账号拥有全部权限。新建第一位员工（必须为店长）后，各设备需员工 PIN 上岗。", style = Pos.type.body, color = Pos.colors.warning)
        st.current?.let { KeyValue("当前上岗", "${it.name} · ${it.role.label}") }
        PosButton("新建员工", { creating = true }, variant = BtnVariant.Primary, enabled = st.can(Perm.Staff))
        GroupTitle("员工（${st.employees.size}）")
        st.employees.sortedBy { it.role.ordinal }.forEach { e ->
            KeyValue(e.name, e.role.label) {
                if (!e.enabled) Tag("已停用")
                PosButton("编辑", { editing = e }, size = BtnSize.Small, enabled = st.can(Perm.Staff))
            }
        }
        GroupTitle("角色权限")
        Row(Modifier.fillMaxWidth()) {
            Text("权限", style = Pos.type.label, color = Pos.colors.sub, modifier = Modifier.weight(1f))
            Role.entries.forEach { r -> Text(r.label, style = Pos.type.label, color = Pos.colors.sub, modifier = Modifier.width(72.dp)) }
        }
        Perm.entries.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(p.label, style = Pos.type.body, color = Pos.colors.ink, modifier = Modifier.weight(1f))
                Role.entries.forEach { r -> Text(if (p in Roles.perms(r)) "✓" else "—", style = Pos.type.body, color = if (p in Roles.perms(r)) Pos.colors.primary else Pos.colors.sub, modifier = Modifier.width(72.dp)) }
            }
        }
        Text("退菜、赠送、折扣由主管或店长以姓名 + PIN 授权；PIN 加盐保存在本机，不进入同步事件。", style = Pos.type.label, color = Pos.colors.sub)
    }
    if (creating || editing != null) {
        val e = editing
        var name by remember(e) { mutableStateOf(e?.name.orEmpty()) }
        var role by remember(e) { mutableStateOf(e?.role ?: if (st.employees.isEmpty()) Role.Owner else Role.Cashier) }
        var pin by remember(e) { mutableStateOf("") }
        var enabled by remember(e) { mutableStateOf(e?.enabled ?: true) }
        var error by remember(e) { mutableStateOf<String?>(null) }
        val close = { creating = false; editing = null }
        PosDialog(if (e == null) "新建员工" else "编辑员工", close, footer = {
            PosButton("取消", close, size = BtnSize.Large)
            PosButton("保存", {
                scope.launch {
                    val r = c.staff.saveEmployee(e?.id, name, role, pin.ifBlank { null }, enabled)
                    error = r.exceptionOrNull()?.message
                    if (r.isSuccess) { close(); c.messages.post("已保存员工") }
                }
            }, variant = BtnVariant.Primary, size = BtnSize.Large)
        }) {
            PosTextField(name, { name = it.take(12) }, "姓名")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Role.entries.forEach { r -> PosButton(r.label, { role = r }, size = BtnSize.Small, selected = role == r) }
            }
            PosTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, if (e == null) "PIN（4–6 位数字）" else "新 PIN（留空不修改）", password = true,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), error = error)
            if (e != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                PosButton("启用", { enabled = true }, size = BtnSize.Small, selected = enabled)
                PosButton("停用", { enabled = false }, size = BtnSize.Small, selected = !enabled)
            }
        }
    }
}
