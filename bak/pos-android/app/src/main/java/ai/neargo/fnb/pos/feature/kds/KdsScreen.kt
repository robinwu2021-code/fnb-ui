package ai.neargo.fnb.pos.feature.kds

import ai.neargo.fnb.pos.app.appContainer
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.KdsDispatch
import ai.neargo.fnb.pos.core.model.KdsRules
import ai.neargo.fnb.pos.core.model.KdsState
import ai.neargo.fnb.pos.core.model.KdsTicket
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 厨房屏（N7）：任务卡持久保存在本机，按游标从主 POS 增量拉取；厨师操作带所见版本经主机仲裁。
 * 断线时仍显示已收任务，但操作需主机在线。
 */
@Composable
fun KdsScreen() {
    val c = appContainer()
    val (station, inbox, _) = c.kdsDevice.collectAsStateWithLifecycle().value
    val link by c.lanLink.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    if (picking) StationPickDialog(station) { picking = false }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    fun act(t: KdsTicket, to: KdsState) = scope.launch {
        val r = c.lanLink.kdsAdvance(t.id, t.rev, to)
        message = if (r.ok) null else r.message
    }
    fun release(t: KdsTicket) = scope.launch {
        val r = c.lanLink.kdsRelease(t.id, t.rev)
        message = if (r.ok) null else r.message
    }
    // 分派模式：别的屏负责的卡单独折叠，不占待接单/制作中
    val store by c.config.current.collectAsStateWithLifecycle()
    val claim = store?.let { KdsRules.dispatchOf(it, station) } == KdsDispatch.Claim
    val me = c.deviceId
    fun mine(t: KdsTicket) = !claim || t.owner.isBlank() || t.owner == me
    val others = if (claim) inbox.filter { it.owner.isNotBlank() && it.owner != me && it.state != KdsState.Done && it.state != KdsState.Cancelled }.sortedBy { it.createdAt } else emptyList()
    var showOthers by remember { mutableStateOf(false) }
    val pending = inbox.filter { it.state == KdsState.Pending && mine(it) }.sortedBy { it.createdAt }
    val working = inbox.filter { (it.state == KdsState.Accepted || it.state == KdsState.Cooking) && mine(it) }.sortedBy { it.createdAt }
    val finished = inbox.filter { it.state == KdsState.Done || it.state == KdsState.Cancelled }.sortedByDescending { it.updatedAt }.take(12)
    Column(Modifier.fillMaxSize().background(Pos.colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().background(Pos.colors.surface).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("厨房屏 · $station", style = Pos.type.title, color = Pos.colors.ink)
            Tag(if (link.connected) "主 POS 已连接" else "主 POS 未连接 · 仅显示已收任务", bg = if (link.connected) Pos.colors.tint else Pos.colors.dangerBg, fg = if (link.connected) Pos.colors.primary else Pos.colors.danger)
            Text("待接单 ${pending.size} · 制作中 ${working.size}" + if (claim) " · 先接单者负责" else "", style = Pos.type.body, color = Pos.colors.sub, modifier = Modifier.weight(1f))
            if (claim) PosButton(if (showOthers) "收起其他屏" else "其他屏 ${others.size}", { showOthers = !showOthers }, size = BtnSize.Small, selected = showOthers)
            PosButton("刷新", { scope.launch { runCatching { c.lanLink.kdsSync() }.onFailure { message = it.message } } }, size = BtnSize.Small)
            PosButton("切换档口", { picking = true }, size = BtnSize.Small)
            PosButton("退出厨房屏", { scope.launch { c.setKdsStation("") } }, size = BtnSize.Small, variant = BtnVariant.Text)
        }
        message?.let { Text(it, style = Pos.type.body, color = Pos.colors.danger, modifier = Modifier.fillMaxWidth().background(Pos.colors.dangerBg).padding(horizontal = 16.dp, vertical = 6.dp)) }
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Lane("待接单", pending, now, Modifier.weight(1f)) { t ->
                PosButton("接单", { act(t, KdsState.Accepted) }, size = BtnSize.Small)
                PosButton("开始制作", { act(t, KdsState.Cooking) }, size = BtnSize.Small, variant = BtnVariant.Primary)
            }
            Lane("制作中", working, now, Modifier.weight(1f)) { t ->
                if (t.state == KdsState.Accepted) PosButton("开始制作", { act(t, KdsState.Cooking) }, size = BtnSize.Small)
                PosButton("完成", { act(t, KdsState.Done) }, size = BtnSize.Small, variant = BtnVariant.Primary)
                if (claim && t.owner == me) PosButton("释放", { release(t) }, size = BtnSize.Small, variant = BtnVariant.Text)
            }
            if (claim && showOthers) Lane("其他屏负责", others, now, Modifier.weight(1f)) { t ->
                Tag(t.ownerName.ifBlank { "其他屏" }, bg = Pos.colors.tile, fg = Pos.colors.sub)
            }
            Lane("最近完成 / 已取消", finished, now, Modifier.weight(1f)) { t ->
                if (t.state == KdsState.Done) PosButton("撤回完成", { act(t, KdsState.Cooking) }, size = BtnSize.Small, variant = BtnVariant.Text)
            }
        }
    }
}

@Composable
private fun Lane(title: String, list: List<KdsTicket>, now: Long, modifier: Modifier, actions: @Composable (KdsTicket) -> Unit) {
    Column(modifier.fillMaxHeight().background(Pos.colors.tile).padding(10.dp)) {
        Text("$title（${list.size}）", style = Pos.type.strong, color = Pos.colors.ink, modifier = Modifier.padding(bottom = 8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.isEmpty()) Text("暂无", style = Pos.type.label, color = Pos.colors.sub)
            list.forEach { t -> Card(t, now, actions) }
        }
    }
}

@Composable
private fun Card(t: KdsTicket, now: Long, actions: @Composable (KdsTicket) -> Unit) {
    val mins = ((now - t.createdAt) / 60_000).toInt()
    val cancelled = t.state == KdsState.Cancelled
    val late = !cancelled && t.state != KdsState.Done && mins >= 15
    Column(Modifier.fillMaxWidth().background(if (cancelled) Pos.colors.dangerBg else Pos.colors.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.tableId, style = Pos.type.title, color = Pos.colors.ink)
            Tag(t.state.label, bg = if (cancelled) Pos.colors.dangerBg else Pos.colors.tint, fg = if (cancelled) Pos.colors.danger else Pos.colors.primary)
            Text("$mins 分钟", style = Pos.type.label, color = if (late) Pos.colors.danger else Pos.colors.sub, modifier = Modifier.weight(1f))
        }
        t.lines.forEach { l ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${l.name} ×${l.active}", style = Pos.type.strong.copy(textDecoration = if (l.active == 0) TextDecoration.LineThrough else null),
                    color = if (l.active == 0) Pos.colors.sub else Pos.colors.ink)
                if (l.voidQty > 0) Tag("已退 ${l.voidQty}", bg = Pos.colors.dangerBg, fg = Pos.colors.danger)
            }
            val extra = listOfNotNull(l.spec.takeIf { it != "标准做法" }, l.note.takeIf { it.isNotBlank() }?.let { "备注：$it" }).joinToString(" · ")
            if (extra.isNotBlank()) Text(extra, style = Pos.type.label, color = Pos.colors.warning)
        }
        if (t.note.isNotBlank()) Text(t.note, style = Pos.type.label, color = Pos.colors.sub)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions(t) }
    }
}
