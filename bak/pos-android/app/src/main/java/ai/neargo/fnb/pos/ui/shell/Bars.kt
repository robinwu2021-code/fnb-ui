package ai.neargo.fnb.pos.ui.shell

import ai.neargo.fnb.pos.state.PosStore
import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosDims
import ai.neargo.fnb.pos.theme.PosType
import ai.neargo.fnb.pos.theme.num
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardMembership
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val bottomRoutes = listOf(
    Route.Order to Icons.Rounded.RestaurantMenu,
    Route.Tables to Icons.Rounded.TableRestaurant,
    Route.Orders to Icons.AutoMirrored.Rounded.ReceiptLong,
    Route.Shift to Icons.Rounded.Schedule,
)

/** Z1 系统栏（平板）：门店与收银员、入口、时钟。入口异常时带状态点。 */
@Composable
fun SysBar(store: PosStore, current: Route, onNavigate: (Route) -> Unit, onSettings: () -> Unit) {
    val c = Pos.colors
    // 960–1279 宽时系统栏放不下全部文字：页面入口保留文字，状态入口只留图标（异常时仍带状态点和文字）
    val roomy = LocalPosLayout.current.tier == Tier.Expanded
    Row(
        Modifier.fillMaxWidth().height(PosDims.sysBar).background(c.surface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("滨江店 · 1 号收银台", style = PosType.strong, color = c.ink)
            Text("收银员 林晓", style = PosType.caption, color = c.sub)
        }
        Spacer(Modifier.width(if (roomy) 24.dp else 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            bottomRoutes.forEach { (r, icon) ->
                EntryButton(r.label, icon, selected = current == r) { onNavigate(r) }
            }
            EntryButton("会员", Icons.Rounded.CardMembership, highlighted = store.member, showLabel = roomy || store.member) {
                store.member = !store.member
                store.toast(if (store.member) "已绑定会员 王女士 · 会员价 95 折" else "已解绑会员")
            }
            EntryButton("打印", Icons.Rounded.Print, warning = !store.printerOk, showLabel = roomy || !store.printerOk) {
                store.toast(if (store.printerOk) "打印机正常" else "厨房打印机缺纸。换纸后重打")
            }
            EntryButton(
                if (store.online) "网络" else "离线",
                if (store.online) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                warning = !store.online,
                showLabel = roomy || !store.online,
            ) { store.toast(if (store.online) "门店服务器、支付网关正常" else "离线中，待同步 2 单") }
            EntryButton("设置", Icons.Rounded.Settings, showLabel = roomy, onClick = onSettings)
        }
        Clock()
    }
}

@Composable
private fun EntryButton(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    highlighted: Boolean = false,
    warning: Boolean = false,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Pos.colors
    val bg = when {
        selected -> c.primaryTint
        warning -> c.warningTint
        else -> Color.Transparent
    }
    val fg = when {
        selected || highlighted -> c.primaryText
        warning -> c.warningText
        else -> c.ink
    }
    Row(
        Modifier.height(PosDims.ctl).clip(PosDims.radiusFull).background(bg)
            .clickable(role = Role.Tab, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            if (warning || highlighted) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape)
                        .background(if (warning) c.warningText else c.primary),
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text(label, style = PosType.body, color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { delay(15_000); now = LocalTime.now() }
    }
    Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = PosType.title.num(), color = Pos.colors.ink)
}

/** 手机版顶部：当前桌台 + 状态 + 设置。 */
@Composable
fun CompactTopBar(store: PosStore, current: Route, onSettings: () -> Unit) {
    val c = Pos.colors
    Row(
        Modifier.fillMaxWidth().height(PosDims.sysBar).background(c.surface).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (current == Route.Order || current == Route.Checkout) "${store.tableId} · ${store.guests} 人" else current.label,
                style = PosType.title, color = c.ink,
            )
            if (current == Route.Order) Text("${store.orderNo} · ${store.mode.label}", style = PosType.caption.num(), color = c.sub)
        }
        if (!store.online) Text("离线", style = PosType.caption, color = c.warningText, modifier = Modifier.padding(end = 4.dp))
        if (!store.printerOk) Icon(Icons.Rounded.Print, "打印异常", tint = c.warningText, modifier = Modifier.size(20.dp))
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "设置", tint = c.ink) }
    }
}

@Composable
fun CompactNavBar(current: Route, onNavigate: (Route) -> Unit) {
    val c = Pos.colors
    NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
        bottomRoutes.forEach { (r, icon) ->
            NavigationBarItem(
                selected = current == r,
                onClick = { onNavigate(r) },
                icon = { Icon(icon, null) },
                label = { Text(r.label, style = PosType.caption) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.primaryText, selectedTextColor = c.primaryText,
                    indicatorColor = c.primaryTint, unselectedIconColor = c.sub, unselectedTextColor = c.sub,
                ),
            )
        }
    }
}
