package ai.neargo.fnb.pos.ui.shell

import ai.neargo.fnb.pos.state.PosStore
import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosType
import ai.neargo.fnb.pos.ui.checkout.CheckoutScreen
import ai.neargo.fnb.pos.ui.order.OrderScreen
import ai.neargo.fnb.pos.ui.orders.OrdersScreen
import ai.neargo.fnb.pos.ui.shift.ShiftScreen
import ai.neargo.fnb.pos.ui.tables.TablesScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** 页面路由，与 pos-app/src/pages.json 对应。 */
enum class Route(val path: String, val label: String) {
    Order("order", "点餐"),
    Tables("tables", "桌台"),
    Orders("orders", "订单"),
    Checkout("checkout", "结账"),
    Shift("shift", "交班"),
}

@Composable
fun PosApp(store: PosStore) {
    val nav = rememberNavController()
    var settingsOpen by remember { mutableStateOf(false) }
    val backStack by nav.currentBackStackEntryAsState()
    val current = Route.entries.firstOrNull { it.path == backStack?.destination?.route } ?: Route.Order

    BoxWithConstraints(Modifier.fillMaxSize().background(Pos.colors.bg)) {
        // 按应用自身宽度判断断点，不按窗口宽度
        val layout = PosLayout.of(maxWidth, store.frame)
        CompositionLocalProvider(LocalPosLayout provides layout) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (layout.wide) {
                    SysBar(store, current, onNavigate = { nav.go(it) }, onSettings = { settingsOpen = true })
                } else {
                    CompactTopBar(store, current, onSettings = { settingsOpen = true })
                }
                Box(Modifier.weight(1f)) {
                    PosNavHost(nav, store)
                }
                if (!layout.wide && current != Route.Checkout) {
                    CompactNavBar(current, onNavigate = { nav.go(it) })
                }
            }
            ToastHost(store.toast, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
        }
    }

    if (settingsOpen) SettingsSheet(store, onDismiss = { settingsOpen = false })
}

@Composable
private fun PosNavHost(nav: NavHostController, store: PosStore) {
    NavHost(nav, startDestination = Route.Order.path) {
        composable(Route.Order.path) { OrderScreen(store, onCheckout = { nav.navigate(Route.Checkout.path) }) }
        composable(Route.Tables.path) { TablesScreen(store, onOpenOrder = { nav.go(Route.Order) }) }
        composable(Route.Orders.path) { OrdersScreen(store) }
        composable(Route.Checkout.path) {
            CheckoutScreen(store, onBack = { nav.popBackStack() }, onDone = { nav.go(Route.Tables) })
        }
        composable(Route.Shift.path) { ShiftScreen(store) }
    }
}

/** 顶层页面之间切换：不堆栈，保留各页状态。 */
fun NavHostController.go(r: Route) = navigate(r.path) {
    popUpTo(graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** 提示条：墨色底，写结果。 */
@Composable
private fun ToastHost(msg: String?, modifier: Modifier) {
    val c = Pos.colors
    AnimatedVisibility(
        visible = msg != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        var last by remember { mutableStateOf("") }
        if (msg != null) last = msg
        Text(
            last,
            style = PosType.body,
            color = c.surface,
            modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(c.ink)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
