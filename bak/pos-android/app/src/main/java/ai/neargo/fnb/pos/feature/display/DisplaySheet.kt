package ai.neargo.fnb.pos.feature.display

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.PreferencesRepository
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosSheet
import ai.neargo.fnb.pos.core.designsystem.component.PosSwitch
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.colors
import ai.neargo.fnb.pos.core.model.BillSide
import ai.neargo.fnb.pos.core.model.ButtonShape
import ai.neargo.fnb.pos.core.model.CatSide
import ai.neargo.fnb.pos.core.model.Density
import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import ai.neargo.fnb.pos.core.model.OrderLayout
import ai.neargo.fnb.pos.core.model.PanelShape
import ai.neargo.fnb.pos.core.model.Skin
import ai.neargo.fnb.pos.core.model.TextScale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DisplayUiState(val prefs: DisplayPrefs = DisplayPrefs(), val device: DeviceStatus = DeviceStatus())

sealed interface DisplayAction {
    data class Update(val transform: (DisplayPrefs) -> DisplayPrefs) : DisplayAction
    data object RestoreDefaults : DisplayAction
    data class ForceOffline(val on: Boolean) : DisplayAction
    data class PrinterOk(val ok: Boolean) : DisplayAction
    data object ResetDemoData : DisplayAction
}

/** 显示设置 ViewModel：偏好持久化到 DataStore，即时生效，不影响订单。 */
class DisplayViewModel(
    private val prefs: PreferencesRepository,
    private val device: DeviceRepository,
    private val messages: MessageBus,
    private val resetDemo: () -> Unit,
) : ViewModel() {
    val state = combine(prefs.prefs, device.status, ::DisplayUiState).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DisplayUiState())

    fun onAction(a: DisplayAction) {
        when (a) {
            is DisplayAction.Update -> viewModelScope.launch { prefs.update(a.transform) }
            DisplayAction.RestoreDefaults -> viewModelScope.launch { prefs.update { DisplayPrefs() } }
            is DisplayAction.ForceOffline -> device.setForceOffline(a.on)
            is DisplayAction.PrinterOk -> device.setPrinterOk(a.ok)
            DisplayAction.ResetDemoData -> { resetDemo(); messages.post("已重置演示桌台与订单") }
        }
    }
}

private enum class Group(val label: String) { Look("外观"), Layout("布局"), Text("文字") }

@Composable
fun DisplaySheetRoute(onDismiss: () -> Unit) {
    val vm = posViewModel { c, _ -> DisplayViewModel(c.prefs, c.device, c.messages) { c.resetDemo() } }
    val state by vm.state.collectAsStateWithLifecycle()
    DisplaySheet(state, vm::onAction, onDismiss)
}

/**
 * 显示设置：外观 / 布局 / 文字三组，每项唯一、即时生效；关闭回原页面，订单不丢。
 * 语言入口在页头，图文排列在点餐工具栏，这里不重复。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplaySheet(state: DisplayUiState, onAction: (DisplayAction) -> Unit, onDismiss: () -> Unit) {
    val c = Pos.colors
    val t = Pos.type
    val p = state.prefs
    var group by rememberSaveable { mutableStateOf(Group.Look) }
    var shapeOpen by rememberSaveable { mutableStateOf(false) }
    val set: ((DisplayPrefs) -> DisplayPrefs) -> Unit = { onAction(DisplayAction.Update(it)) }

    PosSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("显示设置", style = t.title, color = c.ink, modifier = Modifier.weight(1f))
                PosButton("恢复默认", { onAction(DisplayAction.RestoreDefaults) }, variant = BtnVariant.Text)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Group.entries.forEach { g -> PosChip(g.label, group == g, { group = g }) } }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            when (group) {
                Group.Look -> {
                    Label("皮肤")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Skin.entries.forEach { s ->
                            PosChip(s.label, p.skin == s, { set { it.copy(skin = s) } }, leading = { Box(Modifier.size(12.dp).clip(CircleShape).background(s.colors().primary)) })
                        }
                    }
                    Label("密度")
                    Options(Density.entries, p.density, { it.label }) { v -> set { it.copy(density = v) } }
                    Spacer(Modifier.height(12.dp))
                    PosButton(if (shapeOpen) "收起形状微调" else "形状微调", { shapeOpen = !shapeOpen }, variant = BtnVariant.Text)
                    if (shapeOpen) {
                        Label("面板 / 卡片圆角")
                        Options(PanelShape.entries, p.panelShape, { it.label }) { v -> set { it.copy(panelShape = v) } }
                        Label("按钮圆角")
                        Options(ButtonShape.entries, p.buttonShape, { it.label }) { v -> set { it.copy(buttonShape = v) } }
                    }
                }
                Group.Layout -> {
                    Label("分类 / 账单位置")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrderLayout.entries.forEach { l -> LayoutOption(l, p.layout == l) { set { it.copy(layout = l) } } }
                    }
                    if (!LocalPosLayout.current.wide) Text("当前宽度不足 960dp：分类在上，账单收成底部摘要；宽屏时生效", style = t.label, color = c.warning, modifier = Modifier.padding(top = 8.dp))
                    Label("菜品卡")
                    Options(listOf(true, false), p.photos, { if (it) "有图" else "无图" }) { v -> set { it.copy(photos = v) } }
                }
                Group.Text -> {
                    Label("字号")
                    Options(TextScale.entries, p.scale, { it.label }) { v -> set { it.copy(scale = v) } }
                    Text("字体：当前使用系统无衬线回退；Manrope + Noto Sans SC 随包字体待接入", style = t.label, color = c.sub, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Label("演示")
            Toggle("强制离线（演示断网，送厨与收款进入同步队列）", state.device.forceOffline) { onAction(DisplayAction.ForceOffline(it)) }
            Toggle("模拟打印机缺纸", !state.device.printerOk) { onAction(DisplayAction.PrinterOk(!it)) }
            PosButton("重置当前门店演示数据", { onAction(DisplayAction.ResetDemoData) }, variant = BtnVariant.Danger)
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = Pos.type.section, color = Pos.colors.ink, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))

@Composable
private fun <T> Options(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items.forEach { o -> PosChip(label(o), o == selected, { onSelect(o) }) } }
}

/** 布局选项：M3 Card，缩略图画出分类栏（主色浅）、菜单（白）、账单（主色）。 */
@Composable
private fun LayoutOption(l: OrderLayout, selected: Boolean, onClick: () -> Unit) {
    val c = Pos.colors
    Card(
        onClick, Modifier.width(124.dp).semantics { this.selected = selected }, shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) c.tint else c.tile), elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            val r = RoundedCornerShape(2.dp)
            val cat = @Composable { m: Modifier -> Box(m.clip(r).background(c.primary.copy(alpha = 0.35f))) }
            val bill = @Composable { Box(Modifier.width(28.dp).fillMaxHeight().clip(r).background(c.primary.copy(alpha = 0.8f))) }
            Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(4.dp)).background(c.canvas).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (l.bill == BillSide.Left) bill()
                Row(Modifier.weight(1f).fillMaxHeight().clip(r).background(c.surface)) {
                    when (l.cat) {
                        CatSide.Left -> { cat(Modifier.width(10.dp).fillMaxHeight()); Spacer(Modifier.weight(1f)) }
                        CatSide.Right -> { Spacer(Modifier.weight(1f)); cat(Modifier.width(10.dp).fillMaxHeight()) }
                        CatSide.Top -> Column(Modifier.fillMaxWidth()) { cat(Modifier.fillMaxWidth().height(8.dp)) }
                        CatSide.Bottom -> Column(Modifier.fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.Bottom) { cat(Modifier.fillMaxWidth().height(8.dp)) }
                    }
                }
                if (l.bill == BillSide.Right) bill()
            }
            Spacer(Modifier.height(6.dp))
            Text(l.name, style = Pos.type.strong, color = if (selected) c.primary else c.ink)
            Text("分类${l.cat.label} · 账单${l.bill.label}", style = Pos.type.label, color = c.sub)
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // 整行可切换（含标签），开关本身不再单独接收点击
    Row(Modifier.fillMaxWidth().height(48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Pos.type.body, color = Pos.colors.ink, modifier = Modifier.weight(1f))
        PosSwitch(checked, null)
    }
}
