package ai.neargo.fnb.pos.feature.tables

import ai.neargo.fnb.pos.core.designsystem.component.*
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.ReservationRules
import ai.neargo.fnb.pos.core.model.ReservationStatus
import ai.neargo.fnb.pos.core.model.TableStatus
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ReservationsDialog(state: TablesUiState, onAction: (TablesAction) -> Unit, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cancelId by remember { mutableStateOf<String?>(null) }
    if (creating) NewReservationDialog(state, onAction) { creating = false }
    cancelId?.let { id ->
        PosDialog("取消预订", { if (!busy) cancelId = null }, footer = {
            PosButton("返回", { cancelId = null }, enabled = !busy)
            PosButton("确认取消", { busy = true; onAction(TablesAction.CancelReservation(id) { e -> busy = false; error = e; cancelId = null }) }, enabled = !busy, variant = BtnVariant.Primary)
        }) { Text("取消后桌台将恢复空闲", style = Pos.type.body) }
    }
    PosDialog("预订管理", onDismiss, maxWidth = 760.dp, subtitle = "预订后立即留台，到店后转为用餐中", footer = {
        PosButton("关闭", onDismiss)
        PosButton("新建预订", { creating = true }, variant = BtnVariant.Primary)
    }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { Text(it, color = Pos.colors.danger) }
            if (state.reservations.isEmpty()) EmptyState("暂无预订")
            state.reservations.sortedWith(compareBy({ it.status != ReservationStatus.Booked }, { it.arrivesAt })).forEach { r ->
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${r.tableId} · ${r.name} · ${r.status.label}", style = Pos.type.section)
                        Text("${ReservationRules.format(r.arrivesAt)} · ${r.guests} 人", style = Pos.type.body)
                        Text(r.phone, style = Pos.type.label)
                        if (r.note.isNotBlank()) Text(r.note, style = Pos.type.label)
                        if (r.status == ReservationStatus.Booked) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PosButton("取消预订", { cancelId = r.id }, enabled = !busy)
                            PosButton("到店开台", { busy = true; onAction(TablesAction.ArriveReservation(r.id) { e -> busy = false; error = e; if (e == null) onDismiss() }) }, enabled = !busy, variant = BtnVariant.Primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewReservationDialog(state: TablesUiState, onAction: (TablesAction) -> Unit, onDismiss: () -> Unit) {
    val available = state.all.filter { it.status == TableStatus.Idle }
    var tableId by remember { mutableStateOf(available.firstOrNull()?.id.orEmpty()) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var guests by remember { mutableIntStateOf(1) }
    var at by remember { mutableStateOf(ReservationRules.format(System.currentTimeMillis() + 3_600_000)) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    PosDialog("新建预订", { if (!busy) onDismiss() }, maxWidth = 560.dp, footer = {
        PosButton("取消", onDismiss, enabled = !busy)
        PosButton("保存预订", {
            val parsed = ReservationRules.parse(at)
            if (parsed == null) error = "时间格式应为 yyyy-MM-dd HH:mm"
            else { busy = true; onAction(TablesAction.Reserve(tableId, name, phone, guests, parsed, note) { e -> busy = false; error = e; if (e == null) onDismiss() }) }
        }, enabled = !busy && available.isNotEmpty(), variant = BtnVariant.Primary)
    }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (available.isEmpty()) Text("没有可预订的空闲桌台")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                available.forEach { table -> PosChip("${table.id} · ${table.seats} 座", tableId == table.id, { tableId = table.id; guests = guests.coerceAtMost(table.seats) }) }
            }
            PosTextField(name, { name = it }, "联系人")
            PosTextField(phone, { phone = it }, "联系电话")
            Text("预订人数", style = Pos.type.label)
            QuantityStepper(guests, { guests += it }, min = 1, max = available.firstOrNull { it.id == tableId }?.seats ?: 1)
            PosTextField(at, { at = it }, "到店时间（yyyy-MM-dd HH:mm）")
            PosTextField(note, { note = it }, "预订备注")
            error?.let { Text(it, color = Pos.colors.danger) }
        }
    }
}
