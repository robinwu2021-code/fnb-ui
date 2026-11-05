package ai.neargo.fnb.pos.app

import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.PreferencesRepository
import ai.neargo.fnb.pos.core.data.SessionRepository
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosIconButton
import ai.neargo.fnb.pos.core.designsystem.component.PosLayout
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.PosSize
import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import ai.neargo.fnb.pos.core.model.Session
import ai.neargo.fnb.pos.feature.checkout.CheckoutRoute as CheckoutScreenRoute
import ai.neargo.fnb.pos.feature.display.DisplaySheetRoute
import ai.neargo.fnb.pos.feature.order.OrderRoute as OrderScreenRoute
import ai.neargo.fnb.pos.feature.shift.ShiftScreen
import ai.neargo.fnb.pos.feature.tables.TablesRoute as TablesScreenRoute
import ai.neargo.fnb.pos.navigation.CheckoutRoute
import ai.neargo.fnb.pos.navigation.OrderRoute
import ai.neargo.fnb.pos.navigation.ShiftRoute
import ai.neargo.fnb.pos.navigation.TablesRoute
import ai.neargo.fnb.pos.navigation.TopLevel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ShellUiState(val session: Session? = null, val device: DeviceStatus = DeviceStatus(), val prefs: DisplayPrefs? = null)

/** 应用壳 ViewModel：会话、设备状态、显示偏好（主题）。 */
class ShellViewModel(session: SessionRepository, device: DeviceRepository, prefs: PreferencesRepository, val messages: MessageBus) : ViewModel() {
    val state = combine(session.session, device.status, prefs.prefs) { s, d, p -> ShellUiState(s, d, p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShellUiState())
}

/**
 * PosShell：页头 → 工作台内容 → 页脚；浮层（显示设置、提示）在最上层。
 * 宽屏页头含导航；窄屏导航移到底部，页脚保留。页头页脚样式不随业务页变化。
 */
@Composable
fun PosApp(shell: ShellViewModel) {
    val state by shell.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    var displayOpen by rememberSaveable { mutableStateOf(false) }
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val current = when {
        dest?.hasRoute<TablesRoute>() == true -> TopLevel.Tables
        else -> TopLevel.Order
    }
    val session = state.session ?: return
    val notice: (String) -> Unit = shell.messages::post
    val onManage = { notice("系统管理（桌台、商品、SKU、餐牌）按 v3.3 在后续版本接入") }
    val go: (TopLevel) -> Unit = { nav.goTop(if (it == TopLevel.Tables) TablesRoute else OrderRoute) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Pos.colors.canvas)) {
        val layout = PosLayout(maxWidth)
        CompositionLocalProvider(LocalPosLayout provides layout) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (layout.wide) Header(session, state.device, current, go, onManage, notice) { displayOpen = true }
                else CompactHeader(session, notice) { displayOpen = true }
                Box(Modifier.weight(1f)) { PosNavHost(nav, notice) }
                if (!layout.wide) Box(Modifier.fillMaxWidth().background(Pos.colors.surface).padding(vertical = 2.dp), contentAlignment = Alignment.Center) { WorkNav(current, go, onManage) }
                Footer(state.device, compact = !layout.wide, onShift = { nav.goTop(ShiftRoute) }, notice = notice)
            }
            ToastHost(shell.messages, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 64.dp))
            if (displayOpen) DisplaySheetRoute(onDismiss = { displayOpen = false })
        }
    }
}

@Composable
private fun PosNavHost(nav: NavHostController, notice: (String) -> Unit) {
    NavHost(nav, startDestination = OrderRoute, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
        composable<TablesRoute> { TablesScreenRoute(onOrder = { nav.goTop(OrderRoute) }, onCheckout = { nav.navigate(CheckoutRoute(it)) }) }
        composable<OrderRoute> { OrderScreenRoute(onCheckout = { nav.navigate(CheckoutRoute(it)) }, onChangeTable = { nav.goTop(TablesRoute) }) }
        composable<CheckoutRoute> { CheckoutScreenRoute(onBack = { nav.popBackStack() }, onDone = { nav.goTop(TablesRoute) }) }
        composable<ShiftRoute> { ShiftScreen(notice) }
    }
}

/** 顶层页面切换：不堆栈，保留各页状态。 */
private fun NavHostController.goTop(route: Any) = navigate(route) {
    popUpTo(graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** 工作台导航：桌台 · 点餐 · 系统管理（齿轮）。 */
@Composable
private fun WorkNav(current: TopLevel, onNavigate: (TopLevel) -> Unit, onManage: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TopLevel.entries.forEach { r -> PosButton(r.label, { onNavigate(r) }, selected = current == r, textStyle = Pos.type.header) }
        PosIconButton(Icons.Outlined.Settings, "系统管理", onManage)
    }
}

/** AppHeader（宽屏）：门店 / 员工·终端、工作台导航、消息、打印、语言、显示。 */
@Composable
private fun Header(session: Session, device: DeviceStatus, current: TopLevel, onNavigate: (TopLevel) -> Unit, onManage: () -> Unit, notice: (String) -> Unit, onDisplay: () -> Unit) {
    val c = Pos.colors
    val t = Pos.type
    Row(
        Modifier.fillMaxWidth().heightIn(min = PosSize.header).background(c.surface).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.width(130.dp)) {
            Text(session.store, style = t.strong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(session.staff, style = t.label, color = c.sub, maxLines = 1)
        }
        WorkNav(current, onNavigate, onManage)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            PosButton("消息 2", { notice("2 条消息：A06 预订 19:00 到店；厨房已出 A02 麻婆豆腐（演示）") }, variant = BtnVariant.Text, textStyle = t.header)
            PosButton(
                if (device.printerOk) "打印正常" else "打印缺纸",
                { notice(if (device.printerOk) "厨房、前台打印机在线（演示）" else "厨房打印机缺纸，换纸后补打") },
                variant = if (device.printerOk) BtnVariant.Text else BtnVariant.Danger, textStyle = t.header,
            )
            PosButton("EN", { notice("英文界面将随 i18n 资源接入（演示）") }, variant = BtnVariant.Text, textStyle = t.header)
            PosButton("显示", onDisplay, textStyle = t.header)
        }
    }
}

@Composable
private fun CompactHeader(session: Session, notice: (String) -> Unit, onDisplay: () -> Unit) {
    val c = Pos.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(c.surface).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(session.store, style = Pos.type.strong, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        PosButton("消息 2", { notice("2 条消息（演示）") }, variant = BtnVariant.Text, textStyle = Pos.type.header)
        PosButton("EN", { notice("英文界面将随 i18n 资源接入（演示）") }, variant = BtnVariant.Text, textStyle = Pos.type.header)
        PosButton("显示", onDisplay, textStyle = Pos.type.header)
    }
}

/** AppFooter：在线 / 同步状态 + 钱箱、交班、帮助；版本标识。 */
@Composable
private fun Footer(device: DeviceStatus, compact: Boolean, onShift: () -> Unit, notice: (String) -> Unit) {
    val c = Pos.colors
    val t = Pos.type
    Row(Modifier.fillMaxWidth().heightIn(min = PosSize.footer).background(c.surface).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (device.online) c.primary else c.warning))
        Spacer(Modifier.width(6.dp))
        Text(if (device.online) "在线 · 数据已同步（演示）" else "离线 · 2 单待同步（演示）", style = t.label, color = if (device.online) c.ink else c.warning, maxLines = 1, modifier = Modifier.weight(1f))
        if (!compact) PosButton("钱箱", { notice("钱箱操作需要权限，演示环境未连接钱箱") }, variant = BtnVariant.Text, size = BtnSize.Small, textStyle = t.label)
        PosButton("交班", onShift, variant = BtnVariant.Text, size = BtnSize.Small, textStyle = t.label)
        PosButton("帮助", { notice("帮助中心（演示）") }, variant = BtnVariant.Text, size = BtnSize.Small, textStyle = t.label)
        if (!compact) {
            Spacer(Modifier.width(12.dp))
            Text("POS 3.0 · 演示终端", style = t.label, color = c.sub, maxLines = 1)
        }
    }
}

/** 提示条：订阅 MessageBus 的一次性消息，正文色底，2.2 秒后消失。 */
@Composable
private fun ToastHost(bus: MessageBus, modifier: Modifier) {
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bus) {
        bus.messages.collect { msg = it }
    }
    LaunchedEffect(msg) {
        if (msg != null) { delay(2200); msg = null }
    }
    var last by remember { mutableStateOf("") }
    msg?.let { last = it }
    AnimatedVisibility(visible = msg != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Surface(Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(8.dp), color = Pos.colors.ink, contentColor = Pos.colors.surface, shadowElevation = 0.dp) {
            Text(last, style = Pos.type.body, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}
