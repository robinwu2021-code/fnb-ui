package ai.neargo.fnb.pos.app

import ai.neargo.fnb.pos.core.data.launchStorage
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
import ai.neargo.fnb.pos.core.designsystem.component.tr
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.PosSize
import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import ai.neargo.fnb.pos.core.model.Session
import ai.neargo.fnb.pos.feature.checkout.CheckoutRoute as CheckoutScreenRoute
import ai.neargo.fnb.pos.feature.display.DisplaySheetRoute
import ai.neargo.fnb.pos.feature.auth.AuthRoute
import ai.neargo.fnb.pos.feature.auth.StorePicker
import ai.neargo.fnb.pos.feature.manage.ManageRoute as ManageScreenRoute
import ai.neargo.fnb.pos.feature.sync.SyncRoute as SyncScreenRoute
import ai.neargo.fnb.pos.navigation.ManageRoute
import ai.neargo.fnb.pos.navigation.SyncRoute
import ai.neargo.fnb.pos.core.data.SyncRepository
import ai.neargo.fnb.pos.core.model.StoreSummary
import ai.neargo.fnb.pos.core.model.Lang
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import ai.neargo.fnb.pos.feature.order.OrderRoute as OrderScreenRoute
import ai.neargo.fnb.pos.feature.shift.ShiftRoute as ShiftScreenRoute
import ai.neargo.fnb.pos.feature.print.PrintRoute as PrintScreenRoute
import ai.neargo.fnb.pos.navigation.PrintRoute
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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.RoomService
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Surface
import ai.neargo.fnb.pos.core.designsystem.component.Text
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

data class ShellUiState(
    val session: Session? = null, val device: DeviceStatus = DeviceStatus(), val prefs: DisplayPrefs? = null,
    val pending: Int = 0, val stores: List<StoreSummary> = emptyList(),
    /** 云端待上传事件数、厨房待处理（失败/未知）数。 */
    val cloudPending: Int = 0, val kitchenAction: Int = 0,
)

/** 应用壳 ViewModel：会话、设备状态、显示偏好（主题）。 */
class ShellViewModel(private val session: SessionRepository, device: DeviceRepository, private val prefs: PreferencesRepository, sync: SyncRepository, val messages: MessageBus,
    status: ai.neargo.fnb.pos.core.data.SyncStatusRepository) : ViewModel() {
    private val endpoints = combine(status.stats, sync.deliveryGroups) { st, dl -> st.unackedEvents to dl.count { it.needsAction } }
    val state = combine(session.session, device.status, prefs.prefs, sync.pendingCount, combine(session.stores, endpoints) { a, b -> a to b }) { s, d, p, n, (st, e) ->
        ShellUiState(s, d, p, n, st, e.first, e.second)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShellUiState())
    fun selectStore(id: String) = viewModelScope.launchStorage(messages) { session.selectStore(id) }
    fun logout() = viewModelScope.launchStorage(messages) { session.logout() }
    /** 页头语言图标：中文 ⇄ English，写入显示偏好。 */
    fun toggleLang() = viewModelScope.launchStorage(messages) { prefs.update { it.copy(lang = if (it.lang == Lang.Zh) Lang.En else Lang.Zh) } }
}

/**
 * PosShell：页头 → 工作台内容（直达底部，无页脚）；浮层（显示设置、提示）在最上层。
 * 宽屏页头含导航与图标工具栏；窄屏导航移到底部。页头样式不随业务页变化。
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
        dest?.hasRoute<ManageRoute>() == true -> TopLevel.Manage
        dest?.hasRoute<ai.neargo.fnb.pos.navigation.RetailPosRoute>() == true -> TopLevel.RetailPos
        dest?.hasRoute<ai.neargo.fnb.pos.navigation.RetailOrdersRoute>() == true -> TopLevel.RetailOrders
        dest?.hasRoute<ai.neargo.fnb.pos.navigation.GoodsRoute>() == true -> TopLevel.Goods
        dest?.hasRoute<ai.neargo.fnb.pos.navigation.StockWorkRoute>() == true -> TopLevel.StockWork
        else -> TopLevel.Order
    }
    val blocked by appContainer().recovery.blocked.collectAsStateWithLifecycle()
    blocked?.let { ai.neargo.fnb.pos.feature.recovery.BlockedRecoveryScreen(it); return }
    val session = state.session ?: return
    var pickingStore by rememberSaveable { mutableStateOf(false) }
    // 会话守卫：未登录 → 登录；未选店（或主动切店）→ 选择门店；否则进入工作台
    if (session.user == null) { AuthRoute(); return }
    if (session.storeId == null || pickingStore) {
        StorePicker(state.stores, session.storeId, onPick = { shell.selectStore(it); pickingStore = false }, onLogout = { pickingStore = false; shell.logout() },
            onCancel = if (session.storeId != null) ({ pickingStore = false }) else null)
        return
    }
    val container = appContainer()
    // 厨房屏设备：直接显示厨房屏（工位设备由配对授权，不需员工上岗）
    val kdsStation = container.kdsDevice.collectAsStateWithLifecycle().value.first
    val lanRole = container.lan.collectAsStateWithLifecycle().value.role
    if (kdsStation.isNotBlank() && lanRole == ai.neargo.fnb.pos.core.data.LanRole.Secondary) { ai.neargo.fnb.pos.feature.kds.KdsScreen(); return }
    val lockScope = androidx.compose.runtime.rememberCoroutineScope()
    val staffState by container.staff.staff.collectAsStateWithLifecycle()
    if (staffState.locked) { ai.neargo.fnb.pos.feature.staff.StaffLockScreen(session.store, onLogout = shell::logout); return }
    val notice: (String) -> Unit = shell.messages::post
    // 系统管理：需设置、员工、设备或会员相关权限（服务员不可进入）
    val manageAllowed = staffState.perms.any { it in setOf(ai.neargo.fnb.pos.core.model.Perm.Settings, ai.neargo.fnb.pos.core.model.Perm.Staff, ai.neargo.fnb.pos.core.model.Perm.Device, ai.neargo.fnb.pos.core.model.Perm.Member, ai.neargo.fnb.pos.core.model.Perm.Recharge) }
    val onManage = { if (manageAllowed) nav.goTop(ManageRoute) else shell.messages.post("${staffState.current?.name.orEmpty()}没有进入系统管理的权限") }
    // 换人后若当前页面超出新员工权限（系统管理 / 报表 / 交班），回到桌台
    androidx.compose.runtime.LaunchedEffect(staffState.current?.id, dest) {
        val denied = (dest?.hasRoute<ManageRoute>() == true && !manageAllowed) ||
            (dest?.hasRoute<ai.neargo.fnb.pos.navigation.ReportsRoute>() == true && ai.neargo.fnb.pos.core.model.Perm.Reports !in staffState.perms) ||
            (dest?.hasRoute<ShiftRoute>() == true && ai.neargo.fnb.pos.core.model.Perm.Shift !in staffState.perms)
        if (denied) nav.goTop(if (session.biz == ai.neargo.fnb.pos.core.model.BizType.Retail) ai.neargo.fnb.pos.navigation.RetailPosRoute else TablesRoute)
    }
    val go: (TopLevel) -> Unit = { nav.goTop(when (it) { TopLevel.Tables -> TablesRoute; TopLevel.Order -> OrderRoute; TopLevel.Manage -> ManageRoute
        TopLevel.RetailPos -> ai.neargo.fnb.pos.navigation.RetailPosRoute; TopLevel.RetailOrders -> ai.neargo.fnb.pos.navigation.RetailOrdersRoute; TopLevel.Goods -> ai.neargo.fnb.pos.navigation.GoodsRoute
        TopLevel.StockWork -> ai.neargo.fnb.pos.navigation.StockWorkRoute }) }
    // 按门店业态分流（同一个 App）：切到零售门店进收银，切回餐饮门店进点餐；当前页已属于该业态（或是共用页）就不动
    val retail = session.biz == ai.neargo.fnb.pos.core.model.BizType.Retail
    androidx.compose.runtime.LaunchedEffect(session.storeId, session.biz) {
        val inRetail = current in setOf(TopLevel.RetailPos, TopLevel.RetailOrders, TopLevel.Goods, TopLevel.StockWork)
        val inDining = dest == null || current in setOf(TopLevel.Tables, TopLevel.Order)
        if (retail && inDining) nav.goTop(ai.neargo.fnb.pos.navigation.RetailPosRoute)
        if (!retail && inRetail) nav.goTop(OrderRoute)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Pos.colors.canvas)) {
        val layout = PosLayout(maxWidth)
        CompositionLocalProvider(LocalPosLayout provides layout) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val actions = HeaderActions(state.device, state.pending, notice, state.cloudPending, state.kitchenAction, onShift = { nav.goTop(ShiftRoute) }, onPrint = { nav.goTop(PrintRoute) }, onServe = { nav.goTop(ai.neargo.fnb.pos.navigation.ServeRoute) }, dining = session.biz != ai.neargo.fnb.pos.core.model.BizType.Retail, onReports = { nav.goTop(ai.neargo.fnb.pos.navigation.ReportsRoute) }, perms = staffState.perms, staffMode = staffState.required,
                    onLock = { lockScope.launch { container.staff.signOutStaff() } }, onDisplay = { displayOpen = true }, onSync = { nav.goTop(SyncRoute) }, onStore = { pickingStore = true }, onLang = shell::toggleLang)
                if (layout.wide) Header(session, current, go, onManage, actions)
                else CompactHeader(session, actions)
                val authStatus by appContainer().auth.auth.collectAsStateWithLifecycle()
                ai.neargo.fnb.pos.feature.lan.LanBanner()
                ai.neargo.fnb.pos.feature.authcenter.AuthBanner(authStatus, onOpen = { nav.goTop(ai.neargo.fnb.pos.navigation.AuthRoute) }, onRelogin = shell::logout)
                Box(Modifier.weight(1f)) { PosNavHost(nav, notice, onDisplay = { displayOpen = true }, onSwitchStore = { pickingStore = true }) }
                if (!layout.wide) Box(Modifier.fillMaxWidth().background(Pos.colors.surface).padding(vertical = 2.dp), contentAlignment = Alignment.Center) { WorkNav(current, go, onManage, session.biz) }
            }
            ToastHost(shell.messages, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = if (layout.wide) 24.dp else 72.dp))
            if (displayOpen) DisplaySheetRoute(onDismiss = { displayOpen = false })
        }
    }
}

@Composable
private fun PosNavHost(nav: NavHostController, notice: (String) -> Unit, onDisplay: () -> Unit, onSwitchStore: () -> Unit) {
    NavHost(nav, startDestination = OrderRoute, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
        composable<TablesRoute> { TablesScreenRoute(onOrder = { nav.goTop(OrderRoute) }, onCheckout = { nav.navigate(CheckoutRoute(it)) }) }
        composable<OrderRoute> { OrderScreenRoute(onCheckout = { nav.navigate(CheckoutRoute(it)) }, onChangeTable = { nav.goTop(TablesRoute) }) }
        composable<CheckoutRoute> { CheckoutScreenRoute(onBack = { nav.popBackStack() }, onDone = { nav.goTop(TablesRoute) }) }
        composable<ShiftRoute> { ShiftScreenRoute() }
        composable<PrintRoute> { PrintScreenRoute() }
        composable<ManageRoute> { ManageScreenRoute(onDisplay, onSwitchStore) }
        composable<SyncRoute> { SyncScreenRoute() }
        composable<ai.neargo.fnb.pos.navigation.AuthRoute> { ai.neargo.fnb.pos.feature.authcenter.AuthCenterRoute() }
        composable<ai.neargo.fnb.pos.navigation.ServeRoute> { ai.neargo.fnb.pos.feature.ops.ServeRoute() }
        composable<ai.neargo.fnb.pos.navigation.ReportsRoute> { ai.neargo.fnb.pos.feature.ops.ReportsRoute() }
        composable<ai.neargo.fnb.pos.navigation.RetailPosRoute> { ai.neargo.fnb.pos.feature.retail.RetailPosRoute() }
        composable<ai.neargo.fnb.pos.navigation.RetailOrdersRoute> { ai.neargo.fnb.pos.feature.retail.RetailOrdersRoute { nav.goTop(ai.neargo.fnb.pos.navigation.RetailPosRoute) } }
        composable<ai.neargo.fnb.pos.navigation.GoodsRoute> { ai.neargo.fnb.pos.feature.retail.GoodsRoute { nav.goTop(ai.neargo.fnb.pos.navigation.StockWorkRoute) } }
        composable<ai.neargo.fnb.pos.navigation.StockWorkRoute> { ai.neargo.fnb.pos.feature.retail.StockWorkRoute() }
    }
}

/** 顶层页面切换：不堆栈，保留各页状态。 */
private fun NavHostController.goTop(route: Any) {
    // 结账页是一次性流程：经页头离开时丢弃，避免切回「点餐」时恢复出旧的结账 / 收款结果页
    if (currentDestination?.hasRoute<CheckoutRoute>() == true) popBackStack()
    goTopInternal(route)
}

private fun NavHostController.goTopInternal(route: Any) = navigate(route) {
    popUpTo(graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** 工作台导航：桌台 · 点餐 · 系统管理（齿轮）。 */
@Composable
private fun WorkNav(current: TopLevel, onNavigate: (TopLevel) -> Unit, onManage: () -> Unit, biz: ai.neargo.fnb.pos.core.model.BizType = ai.neargo.fnb.pos.core.model.BizType.Dining) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ai.neargo.fnb.pos.navigation.topTabs(biz).forEach { r -> PosButton(r.label, { onNavigate(r) }, selected = current == r, textStyle = Pos.type.header) }
        PosIconButton(Icons.Outlined.Settings, "系统管理", onManage, selected = current == TopLevel.Manage)
    }
}

/** 页头右侧操作：原页头（消息 / 打印 / 语言 / 显示）与原页脚（在线 / 钱箱 / 交班 / 帮助）合并，统一为图标。 */
private class HeaderActions(
    val device: DeviceStatus, val pending: Int, val notice: (String) -> Unit, val cloudPending: Int, val kitchenAction: Int,
    val onShift: () -> Unit, val onPrint: () -> Unit, val onServe: () -> Unit, val onReports: () -> Unit,
    /** 出菜（厨房）只在餐饮门店有 */
    val dining: Boolean = true,
    val perms: Set<ai.neargo.fnb.pos.core.model.Perm> = ai.neargo.fnb.pos.core.model.Perm.entries.toSet(), val staffMode: Boolean = false, val onLock: () -> Unit = {}, val onDisplay: () -> Unit, val onSync: () -> Unit, val onStore: () -> Unit, val onLang: () -> Unit,
)

/** AppHeader（宽屏）：门店 / 员工·终端、工作台导航；右侧图标工具栏。无页脚，内容区直达底部。 */
@Composable
private fun Header(session: Session, current: TopLevel, onNavigate: (TopLevel) -> Unit, onManage: () -> Unit, actions: HeaderActions) {
    val c = Pos.colors
    val t = Pos.type
    Row(
        Modifier.fillMaxWidth().heightIn(min = PosSize.header).background(c.surface).padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.width(140.dp).clickable(role = Role.Button, onClickLabel = "切换门店", onClick = actions.onStore)) {
            Text(session.store, style = t.strong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(session.staff, style = t.label, color = c.sub, maxLines = 1)
        }
        WorkNav(current, onNavigate, onManage, session.biz)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(actions)
            if (actions.dining) ToolIcon(Icons.Outlined.RoomService, "出菜", onClick = actions.onServe)
            if (ai.neargo.fnb.pos.core.model.Perm.Reports in actions.perms) ToolIcon(Icons.Outlined.Assessment, "报表与日结", onClick = actions.onReports)
            ToolIcon(Icons.Outlined.Notifications, "消息 2 条", badge = 2) { actions.notice("2 条消息：A06 预订 19:00 到店；厨房已出 A02 麻婆豆腐（演示）") }
            PrinterIcon(actions)
            ToolIcon(Icons.Outlined.PointOfSale, "钱箱") { actions.notice("钱箱操作需要权限，演示环境未连接钱箱") }
            if (ai.neargo.fnb.pos.core.model.Perm.Shift in actions.perms) ToolIcon(Icons.Outlined.SwapHoriz, "交班", onClick = actions.onShift)
            if (actions.staffMode) ToolIcon(Icons.Outlined.Lock, "锁屏换人", onClick = actions.onLock)
            ToolIcon(Icons.Outlined.Translate, "语言：中文 / EN", onClick = actions.onLang)
            ToolIcon(Icons.AutoMirrored.Outlined.HelpOutline, "帮助") { actions.notice("帮助中心 · POS 3.0 演示终端") }
            ToolIcon(Icons.Outlined.Tune, "显示设置", filled = true, onClick = actions.onDisplay)
        }
    }
}

/** 窄屏页头：门店名 + 状态、消息、显示；低频操作收进「更多」菜单。 */
@Composable
private fun CompactHeader(session: Session, actions: HeaderActions) {
    val c = Pos.colors
    var more by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(c.surface).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(session.store, style = Pos.type.strong, color = c.ink, modifier = Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "切换门店", onClick = actions.onStore), maxLines = 1, overflow = TextOverflow.Ellipsis)
        StatusIcon(actions)
        ToolIcon(Icons.Outlined.Notifications, "消息 2 条", badge = 2) { actions.notice("2 条消息（演示）") }
        ToolIcon(Icons.Outlined.Tune, "显示设置", filled = true, onClick = actions.onDisplay)
        Box {
            ToolIcon(Icons.Outlined.MoreVert, "更多") { more = true }
            DropdownMenu(more, { more = false }, containerColor = c.surface, shadowElevation = 2.dp) {
                val item: @Composable (androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit) -> Unit = { icon, label, act ->
                    DropdownMenuItem(text = { Text(label, style = Pos.type.body) }, leadingIcon = { Icon(icon, null) }, onClick = { more = false; act() })
                }
                item(Icons.Outlined.Print, if (actions.device.printerOk) "打印队列" else "打印队列（缺纸）", actions.onPrint)
                if (actions.dining) item(Icons.Outlined.RoomService, "出菜", actions.onServe)
                item(Icons.Outlined.Assessment, "报表与日结", actions.onReports)
                item(Icons.Outlined.SwapHoriz, "交班", actions.onShift)
                item(Icons.Outlined.Translate, "语言：中文 / EN", actions.onLang)
                item(Icons.AutoMirrored.Outlined.HelpOutline, "帮助") { actions.notice("帮助中心 · POS 3.0 演示终端") }
            }
        }
    }
}

/** 在线 / 同步状态：在线为主色云勾，离线为警示色云叉；点按说明详情。 */
@Composable
private fun StatusIcon(a: HeaderActions) {
    val online = a.device.online
    // N3：网络在线不等于已同步；厨房与云端分别说明，不出现「数据已同步」
    val parts = buildList {
        add(if (online) "网络在线" else "离线 · 厨房单本机照常出单")
        if (a.kitchenAction > 0) add("厨房 ${a.kitchenAction} 张待处理")
        add(if (ai.neargo.fnb.pos.core.data.CloudLink.CONNECTED) "云端待上传 ${a.cloudPending} 条" else "云端未接入 · ${a.cloudPending} 条事件在本机")
    }
    val attention = a.kitchenAction + a.pending
    ToolIcon(
        if (online) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff, parts.joinToString(" · "),
        badge = attention.takeIf { it > 0 }, tint = if (online && a.kitchenAction == 0) Pos.colors.primary else Pos.colors.warning, onClick = a.onSync,
    )
}

/** 打印机：正常为默认色，缺纸为危险色并带圆点。 */
@Composable
private fun PrinterIcon(a: HeaderActions) {
    val ok = a.device.printerOk
    ToolIcon(Icons.Outlined.Print, if (ok) "打印队列 · 打印机正常" else "打印队列 · 厨房打印机缺纸", tint = if (ok) null else Pos.colors.danger, dot = !ok, onClick = a.onPrint)
}

/**
 * 页头图标按钮：M3 IconButton + PlainTooltip（长按 / 悬停显示名称），名称同时作为无障碍描述。
 * badge 为数量角标，dot 为异常圆点；filled 用于显示设置这类常驻入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badge: Int? = null,
    dot: Boolean = false,
    tint: androidx.compose.ui.graphics.Color? = null,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Pos.colors
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        BadgedBox(badge = {
            when {
                badge != null -> Badge(containerColor = c.primary, contentColor = c.onPrimary) { Text("$badge") }
                dot -> Badge(containerColor = c.danger)
            }
        }) {
            if (filled) {
                FilledIconButton(onClick, Modifier.size(40.dp), shape = Pos.metrics.button,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.control, contentColor = tint ?: c.ink)) { Icon(icon, tr(label), Modifier.size(20.dp)) }
            } else {
                IconButton(onClick, Modifier.size(40.dp), colors = IconButtonDefaults.iconButtonColors(contentColor = tint ?: c.ink)) { Icon(icon, tr(label), Modifier.size(20.dp)) }
            }
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
