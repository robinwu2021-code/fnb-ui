package ai.neargo.fnb.pos.ui.tables

import ai.neargo.fnb.pos.data.DiningTable
import ai.neargo.fnb.pos.data.TableStatus
import ai.neargo.fnb.pos.data.Zone
import ai.neargo.fnb.pos.data.yuan
import ai.neargo.fnb.pos.state.PosStore
import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosDims
import ai.neargo.fnb.pos.theme.PosType
import ai.neargo.fnb.pos.theme.num
import ai.neargo.fnb.pos.ui.components.AmountRow
import ai.neargo.fnb.pos.ui.components.BtnVariant
import ai.neargo.fnb.pos.ui.components.Panel
import ai.neargo.fnb.pos.ui.components.PosButton
import ai.neargo.fnb.pos.ui.components.PosFilterChip
import ai.neargo.fnb.pos.ui.components.QuantityStepper
import ai.neargo.fnb.pos.ui.components.StatusLabel
import ai.neargo.fnb.pos.ui.components.Tone
import ai.neargo.fnb.pos.ui.shell.FrameLayout
import ai.neargo.fnb.pos.ui.shell.GridKind
import ai.neargo.fnb.pos.ui.shell.LocalPosLayout
import ai.neargo.fnb.pos.ui.shell.NavArea
import ai.neargo.fnb.pos.ui.shell.NavItem
import ai.neargo.fnb.pos.ui.shell.gridColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 桌台总览。导航区 = 桌区，内容区 = 状态筛选 + 桌台宫格，详情区 = 所选桌台。
 * 窄屏点桌台弹出详情抽屉。状态用底色加文字，不只靠颜色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TablesScreen(store: PosStore, onOpenOrder: () -> Unit) {
    val layout = LocalPosLayout.current
    val gap = PosDims.gap
    var zone by rememberSaveable { mutableStateOf(Zone.Hall) }
    var status by rememberSaveable { mutableStateOf<TableStatus?>(null) }
    var selectedId by rememberSaveable { mutableStateOf(store.tableId) }
    var sheetOpen by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<DiningTable?>(null) }

    val inZone = store.tables.filter { it.zone == zone }
    val shown = inZone.filter { status == null || it.status == status }
    val selected = store.tables.firstOrNull { it.id == selectedId }

    val act: (DiningTable) -> Unit = { t ->
        when (t.status) {
            TableStatus.Free -> opening = t
            TableStatus.Reserved -> opening = t // 到店转开台
            else -> { store.switchTo(t); onOpenOrder() }
        }
    }
    val detail: @Composable (Modifier) -> Unit = { m -> TableDetail(selected, store, m, onAct = act) }

    FrameLayout(
        nav = { vertical, m ->
            NavArea(
                Zone.entries.map { z -> NavItem(z.name, z.label) },
                zone.name, { zone = Zone.valueOf(it) }, vertical, m,
            )
        },
        detail = detail,
    ) { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PosFilterChip("全部 ${inZone.size}", status == null, { status = null })
                TableStatus.entries.forEach { s ->
                    PosFilterChip("${s.label} ${inZone.count { it.status == s }}", status == s, { status = if (status == s) null else s })
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns(GridKind.Tables, layout, maxWidth, gap)),
                    contentPadding = PaddingValues(bottom = gap),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items(shown, key = { it.id }) { t ->
                        TableTile(
                            t,
                            selected = t.id == selectedId,
                            current = t.id == store.tableId && t.status != TableStatus.Free,
                        ) {
                            selectedId = t.id
                            if (!layout.wide) sheetOpen = true
                        }
                    }
                }
            }
        }
    }

    if (sheetOpen && !layout.wide) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, containerColor = Pos.colors.surface, scrimColor = Pos.colors.scrim) {
            TableDetail(selected, store, Modifier.fillMaxWidth().padding(bottom = 24.dp)) { sheetOpen = false; act(it) }
        }
    }

    opening?.let { t ->
        OpenTableDialog(t, onDismiss = { opening = null }) { n ->
            store.openTable(t, n)
            opening = null
            onOpenOrder()
        }
    }
}

/** DiningTableTile：空闲 faint、用餐中 info tint、待结账 warning tint、已预订 primary tint；选中时外加主色字的桌号。 */
@Composable
private fun TableTile(t: DiningTable, selected: Boolean, current: Boolean, onClick: () -> Unit) {
    val c = Pos.colors
    val (bg, tone) = statusLook(t.status)
    Column(
        Modifier.height(PosDims.tileH + 24.dp).clip(PosDims.radiusMd)
            .background(if (selected) c.primaryTint else bg)
            .clickable(role = Role.Button, onClick = onClick).padding(12.dp),
    ) {
        // 桌号和状态都不折行，两端对齐
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(t.id, style = PosType.display.num(), color = if (selected) c.primaryText else c.ink, maxLines = 1, softWrap = false)
            StatusLabel(if (current) "当前" else t.status.label, if (current) Tone.Primary else tone)
        }
        Spacer(Modifier.weight(1f))
        when (t.status) {
            TableStatus.Free -> Text(if (t.seats > 0) "${t.seats} 座" else "外带", style = PosType.sub, color = c.sub)
            TableStatus.Reserved -> Text(t.note ?: "", style = PosType.sub, color = c.sub, maxLines = 1)
            else -> {
                // 6 列时一行放不下「人数 · 时长 · 金额」，金额单独一行
                Text(
                    if (t.zone == Zone.Take) (t.note ?: "外带") else "${t.guests}/${t.seats} 人 · ${t.minutes} 分钟",
                    style = PosType.sub.num(), color = c.sub, maxLines = 1,
                )
                Text(yuan(t.amount), style = PosType.strong.num(), color = c.ink)
            }
        }
    }
}

@Composable
private fun statusLook(s: TableStatus) = with(Pos.colors) {
    when (s) {
        TableStatus.Free -> faint to Tone.Neutral
        TableStatus.Dining -> infoTint to Tone.Info
        TableStatus.Billing -> warningTint to Tone.Warning
        TableStatus.Reserved -> primaryTint to Tone.Primary
    }
}

/** 详情区：所选桌台的人数、时长、金额；空桌只显示「开台」。一屏一个实心主操作。 */
@Composable
private fun TableDetail(t: DiningTable?, store: PosStore, modifier: Modifier, onAct: (DiningTable) -> Unit) {
    val c = Pos.colors
    Panel(modifier) {
        if (t == null) {
            Text("选择一张桌台查看详情", style = PosType.body, color = c.sub, modifier = Modifier.padding(20.dp))
            return@Panel
        }
        Column(Modifier.padding(20.dp)) {
            Row {
                Text(t.id, style = PosType.hero.num(), color = c.ink, modifier = Modifier.weight(1f))
                StatusLabel(t.status.label, statusLook(t.status).second)
            }
            Text("${t.zone.label}${if (t.seats > 0) " · ${t.seats} 座" else ""}", style = PosType.sub, color = c.sub)
            Spacer(Modifier.height(16.dp))
            when (t.status) {
                TableStatus.Free -> Text("空闲，可开台", style = PosType.body, color = c.sub)
                TableStatus.Reserved -> AmountRow("预订", t.note ?: "—")
                else -> {
                    if (t.zone != Zone.Take) AmountRow("人数", "${t.guests} / ${t.seats}")
                    AmountRow("用餐时长", "${t.minutes} 分钟")
                    AmountRow("服务员", "周敏")
                    AmountRow("付款时机", "先吃后付")
                    Spacer(Modifier.height(8.dp))
                    Text("消费", style = PosType.sub, color = c.sub)
                    Text(yuan(t.amount), style = PosType.mega.num(), color = c.ink)
                }
            }
            Spacer(Modifier.height(20.dp))
            when (t.status) {
                TableStatus.Free -> PosButton("开台", { onAct(t) }, Modifier.fillMaxWidth(), height = PosDims.ctlL)
                TableStatus.Reserved -> PosButton("到店开台", { onAct(t) }, Modifier.fillMaxWidth(), height = PosDims.ctlL)
                TableStatus.Dining -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosButton("预结单", { store.toast("已打印 ${t.id} 预结单") }, variant = BtnVariant.Muted, height = PosDims.ctlL)
                    PosButton("点餐", { onAct(t) }, Modifier.weight(1f), height = PosDims.ctlL)
                }
                TableStatus.Billing -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosButton("加菜", { onAct(t) }, variant = BtnVariant.Soft, height = PosDims.ctlL)
                    PosButton("去结账", { onAct(t) }, Modifier.weight(1f), height = PosDims.ctlL)
                }
            }
        }
    }
}

@Composable
private fun OpenTableDialog(t: DiningTable, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val c = Pos.colors
    var n by remember { mutableIntStateOf(minOf(2, t.seats.coerceAtLeast(1))) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 420.dp).clip(PosDims.radiusLg).background(c.elev).padding(20.dp)) {
            Text("开台 ${t.id}", style = PosType.display, color = c.ink)
            Text("${t.zone.label} · ${t.seats} 座${t.note?.let { " · $it" } ?: ""}", style = PosType.sub, color = c.sub)
            Spacer(Modifier.height(20.dp))
            Row {
                Text("人数", style = PosType.body, color = c.ink, modifier = Modifier.weight(1f))
                QuantityStepper(n, { n = (n + it).coerceIn(1, 30) }, min = 1)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosButton("取消", onDismiss, variant = BtnVariant.Muted, height = PosDims.ctlL)
                PosButton("开台", { onConfirm(n) }, Modifier.weight(1f), height = PosDims.ctlL)
            }
        }
    }
}
