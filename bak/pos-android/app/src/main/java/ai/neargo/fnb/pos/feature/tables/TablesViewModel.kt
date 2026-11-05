package ai.neargo.fnb.pos.feature.tables

import ai.neargo.fnb.pos.core.data.FloorRepository
import ai.neargo.fnb.pos.core.data.MenuRepository
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.OrderRepository
import ai.neargo.fnb.pos.core.data.PreferencesRepository
import ai.neargo.fnb.pos.core.data.SessionRepository
import ai.neargo.fnb.pos.core.model.Area
import ai.neargo.fnb.pos.core.model.BillSide
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.OrderRules
import ai.neargo.fnb.pos.core.model.TableOrder
import ai.neargo.fnb.pos.core.model.TableStatus
import ai.neargo.fnb.pos.core.model.Totals
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TableCardItem(val table: DiningTable, val areaName: String, val totals: Totals)

data class TablesUiState(
    val areas: List<Pair<String, String>> = emptyList(),
    val areaCounts: Map<String, Int> = emptyMap(),
    val area: String = "all",
    val status: TableStatus? = null,
    val query: String = "",
    val statusCounts: Map<TableStatus?, Int> = emptyMap(),
    val cards: List<TableCardItem> = emptyList(),
    val selected: TableCardItem? = null,
    val selectedPending: Int = 0,
    val detailSide: BillSide = BillSide.Right,
    val opening: DiningTable? = null,
)

sealed interface TablesAction {
    data class SelectArea(val id: String) : TablesAction
    data class SelectStatus(val status: TableStatus?) : TablesAction
    data class Search(val query: String) : TablesAction
    data class Select(val id: String) : TablesAction
    data object ResetFilters : TablesAction
    data class RequestOpen(val id: String) : TablesAction
    data object CancelOpen : TablesAction
    data class ConfirmOpen(val guests: Int) : TablesAction
    data class ContinueOrder(val id: String) : TablesAction
    data class Checkout(val id: String) : TablesAction
    data class StartCleaning(val id: String) : TablesAction
    data class FinishCleaning(val id: String) : TablesAction
    data class Notice(val message: String) : TablesAction
}

/** 一次性导航效果，与持久 UiState 分开。 */
sealed interface TablesEffect {
    data object OpenOrder : TablesEffect
    data class OpenCheckout(val tableId: String) : TablesEffect
}

class TablesViewModel(
    private val menu: MenuRepository,
    private val session: SessionRepository,
    private val floor: FloorRepository,
    orders: OrderRepository,
    prefs: PreferencesRepository,
    private val messages: MessageBus,
    private val saved: SavedStateHandle,
) : ViewModel() {

    private data class Local(val area: String, val status: TableStatus?, val query: String, val selected: String?, val opening: String?)

    private val local = MutableStateFlow(Local(saved["area"] ?: "all", null, "", saved["selected"], null))
    private val effects = Channel<TablesEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    val state: StateFlow<TablesUiState> = combine(floor.tables, orders.orders, session.session, prefs.prefs, local) { tables, all, s, p, l ->
        fun card(t: DiningTable) = TableCardItem(t, menu.area(t.area).name, OrderRules.totals(all[t.id] ?: TableOrder()))
        val inArea = tables.filter { l.area == "all" || it.area == l.area }
        val shown = inArea.filter { (l.status == null || it.status == l.status) && (l.query.isBlank() || it.id.contains(l.query.trim(), ignoreCase = true)) }
        val selId = l.selected ?: s.currentTableId
        val sel = tables.firstOrNull { it.id == selId } ?: tables.firstOrNull()
        TablesUiState(
            areas = listOf("all" to "全部") + menu.areas.map(Area::id).zip(menu.areas.map(Area::short)),
            areaCounts = (listOf("all") + menu.areas.map(Area::id)).associateWith { a -> tables.count { a == "all" || it.area == a } },
            area = l.area, status = l.status, query = l.query,
            statusCounts = (listOf<TableStatus?>(null) + TableStatus.entries).associateWith { st -> inArea.count { st == null || it.status == st } },
            cards = shown.map(::card),
            selected = sel?.let(::card),
            selectedPending = sel?.let { all[it.id]?.pending?.sumOf { l -> l.qty } } ?: 0,
            detailSide = p.layout.bill,
            opening = l.opening?.let { id -> tables.firstOrNull { it.id == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TablesUiState())

    fun onAction(a: TablesAction) {
        when (a) {
            is TablesAction.SelectArea -> { saved["area"] = a.id; local.value = local.value.copy(area = a.id) }
            is TablesAction.SelectStatus -> local.value = local.value.copy(status = a.status)
            is TablesAction.Search -> local.value = local.value.copy(query = a.query)
            is TablesAction.Select -> { saved["selected"] = a.id; local.value = local.value.copy(selected = a.id) }
            TablesAction.ResetFilters -> local.value = local.value.copy(area = "all", status = null, query = "")
            is TablesAction.RequestOpen -> local.value = local.value.copy(opening = a.id)
            TablesAction.CancelOpen -> local.value = local.value.copy(opening = null)
            is TablesAction.ConfirmOpen -> viewModelScope.launch {
                val id = local.value.opening ?: return@launch
                floor.openTable(id, a.guests)
                    .onSuccess { local.value = local.value.copy(opening = null); messages.post("$id 已开台，${a.guests} 人"); effects.send(TablesEffect.OpenOrder) }
                    .onFailure { messages.post(it.message ?: "开台失败") }
            }
            is TablesAction.ContinueOrder -> viewModelScope.launch { session.selectTable(a.id); effects.send(TablesEffect.OpenOrder) }
            is TablesAction.Checkout -> viewModelScope.launch { session.selectTable(a.id); effects.send(TablesEffect.OpenCheckout(a.id)) }
            is TablesAction.StartCleaning -> viewModelScope.launch { floor.startCleaning(a.id).report("${a.id} 进入清台") }
            is TablesAction.FinishCleaning -> viewModelScope.launch { floor.finishCleaning(a.id).report("${a.id} 已清台，可开台") }
            is TablesAction.Notice -> messages.post(a.message)
        }
    }

    private fun Result<Unit>.report(ok: String) = onSuccess { messages.post(ok) }.onFailure { messages.post(it.message ?: "操作失败") }
}
