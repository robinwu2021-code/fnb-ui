package ai.neargo.fnb.pos.feature.tables

import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.data.FloorRepository
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.data.areaName
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.OrderRepository
import ai.neargo.fnb.pos.core.data.PreferencesRepository
import ai.neargo.fnb.pos.core.data.SessionRepository
import ai.neargo.fnb.pos.core.model.BillSide
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.OrderRules
import ai.neargo.fnb.pos.core.model.TableOrder
import ai.neargo.fnb.pos.core.model.TableStatus
import ai.neargo.fnb.pos.core.model.TableRules
import ai.neargo.fnb.pos.core.data.PrintRepository
import ai.neargo.fnb.pos.core.model.Reservation
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

/** minutes 为用餐时长（由开台时间计算，每 30 秒刷新）。 */
data class TableCardItem(val table: DiningTable, val areaName: String, val totals: Totals, val minutes: Int, val x: Int = 0, val y: Int = 0, val note: String = "")

enum class TableView(val label: String) { Grid("卡片"), Floor("平面位置") }

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
    val view: TableView = TableView.Grid,
    /** 平面视图：按区域分组的卡片（含画布坐标）。 */
    val floorAreas: List<Pair<String, List<TableCardItem>>> = emptyList(),
    /** 换桌 / 并台的候选桌。 */
    val all: List<DiningTable> = emptyList(),
    val reservations: List<Reservation> = emptyList(),
)

sealed interface TablesAction {
    data class Reserve(val tableId: String, val name: String, val phone: String, val guests: Int, val at: Long, val note: String, val done: (String?) -> Unit) : TablesAction
    data class CancelReservation(val id: String, val done: (String?) -> Unit) : TablesAction
    data class ArriveReservation(val id: String, val done: (String?) -> Unit) : TablesAction
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
    data class SetView(val view: TableView) : TablesAction
    data class SavePosition(val id: String, val x: String, val y: String, val done: (String?) -> Unit) : TablesAction
    data class PreBill(val id: String) : TablesAction
    data class SaveNote(val id: String, val note: String, val done: (String?) -> Unit) : TablesAction
    data class Change(val from: String, val to: String, val done: (String?) -> Unit) : TablesAction
    data class Merge(val from: String, val into: String, val done: (String?) -> Unit) : TablesAction
}

/** 一次性导航效果，与持久 UiState 分开。 */
sealed interface TablesEffect {
    data object OpenOrder : TablesEffect
    data class OpenCheckout(val tableId: String) : TablesEffect
}

class TablesViewModel(
    config: StoreConfigRepository,
    private val session: SessionRepository,
    private val floor: FloorRepository,
    private val orders: OrderRepository,
    private val print: PrintRepository,
    prefs: PreferencesRepository,
    private val messages: MessageBus,
    private val saved: SavedStateHandle,
) : ViewModel() {

    private data class Local(val area: String, val status: TableStatus?, val query: String, val selected: String?, val opening: String?, val view: TableView = TableView.Grid)

    private val local = MutableStateFlow(Local(saved["area"] ?: "all", null, "", saved["selected"], null))
    private val effects = Channel<TablesEffect>(Channel.BUFFERED)
    /** 用餐时长刷新节拍。 */
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }
    val effect = effects.receiveAsFlow()

    val state: StateFlow<TablesUiState> = combine(config.current, session.session, prefs.prefs, local, ticker) { store, s, p, l, now ->
        val tables = store?.tables.orEmpty()
        val all = store?.orders.orEmpty()
        fun card(t: DiningTable): TableCardItem {
            val (x, y) = TableRules.position(t, tables.filter { it.areaId == t.areaId }.indexOf(t))
            return TableCardItem(t, store?.areaName(t.areaId) ?: "", OrderRules.totals(all[t.id] ?: TableOrder()), t.minutes(now), x, y, all[t.id]?.note.orEmpty())
        }
        val inArea = tables.filter { l.area == "all" || it.areaId == l.area }
        val shown = inArea.filter { (l.status == null || it.status == l.status) && (l.query.isBlank() || it.id.contains(l.query.trim(), ignoreCase = true)) }
        val selId = l.selected ?: s.currentTableId
        val sel = tables.firstOrNull { it.id == selId } ?: tables.firstOrNull()
        TablesUiState(
            areas = listOf("all" to "全部") + store?.areas.orEmpty().map { it.id to it.name },
            areaCounts = (listOf("all") + store?.areas.orEmpty().map { it.id }).associateWith { a -> tables.count { a == "all" || it.areaId == a } },
            area = l.area, status = l.status, query = l.query,
            statusCounts = (listOf<TableStatus?>(null) + TableStatus.entries).associateWith { st -> inArea.count { st == null || it.status == st } },
            cards = shown.map(::card),
            selected = sel?.let(::card),
            selectedPending = sel?.let { all[it.id]?.pending?.sumOf { l -> l.qty } } ?: 0,
            detailSide = p.layout.bill,
            opening = l.opening?.let { id -> tables.firstOrNull { it.id == id } },
            view = l.view,
            floorAreas = store?.areas.orEmpty().filter { l.area == "all" || it.id == l.area }.map { a -> a.name to shown.filter { it.areaId == a.id }.map(::card) },
            all = tables,
            reservations = store?.reservations.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TablesUiState())

    fun onAction(a: TablesAction) {
        when (a) {
            is TablesAction.Reserve -> viewModelScope.launchStorage(messages) { floor.createReservation(a.tableId, a.name, a.phone, a.guests, a.at, a.note).finish("预订已保存", a.done) }
            is TablesAction.CancelReservation -> viewModelScope.launchStorage(messages) { floor.cancelReservation(a.id).finish("预订已取消", a.done) }
            is TablesAction.ArriveReservation -> viewModelScope.launchStorage(messages) {
                floor.checkInReservation(a.id).onSuccess { a.done(null); effects.send(TablesEffect.OpenOrder) }.onFailure { a.done(it.message ?: "开台失败") }
            }
            is TablesAction.SelectArea -> { saved["area"] = a.id; local.value = local.value.copy(area = a.id) }
            is TablesAction.SelectStatus -> local.value = local.value.copy(status = a.status)
            is TablesAction.Search -> local.value = local.value.copy(query = a.query)
            is TablesAction.Select -> { saved["selected"] = a.id; local.value = local.value.copy(selected = a.id) }
            TablesAction.ResetFilters -> local.value = local.value.copy(area = "all", status = null, query = "")
            is TablesAction.RequestOpen -> local.value = local.value.copy(opening = a.id)
            TablesAction.CancelOpen -> local.value = local.value.copy(opening = null)
            is TablesAction.ConfirmOpen -> viewModelScope.launchStorage(messages) {
                val id = local.value.opening ?: return@launchStorage
                floor.openTable(id, a.guests)
                    .onSuccess { local.value = local.value.copy(opening = null); messages.post("$id 已开台，${a.guests} 人"); effects.send(TablesEffect.OpenOrder) }
                    .onFailure { messages.post(it.message ?: "开台失败") }
            }
            is TablesAction.ContinueOrder -> viewModelScope.launchStorage(messages) { session.selectTable(a.id); effects.send(TablesEffect.OpenOrder) }
            is TablesAction.Checkout -> viewModelScope.launchStorage(messages) { session.selectTable(a.id); effects.send(TablesEffect.OpenCheckout(a.id)) }
            is TablesAction.StartCleaning -> viewModelScope.launchStorage(messages) { floor.startCleaning(a.id).report("${a.id} 进入清台") }
            is TablesAction.FinishCleaning -> viewModelScope.launchStorage(messages) { floor.finishCleaning(a.id).report("${a.id} 已清台，可开台") }
            is TablesAction.Notice -> messages.post(a.message)
            is TablesAction.SetView -> local.value = local.value.copy(view = a.view)
            is TablesAction.SavePosition -> viewModelScope.launchStorage(messages) {
                val p = TableRules.validPosition(a.x, a.y).getOrElse { a.done(it.message); return@launchStorage }
                floor.setPosition(a.id, p.first, p.second).finish("${a.id} 位置已保存", a.done)
            }
            is TablesAction.PreBill -> viewModelScope.launchStorage(messages) { print.preBill(a.id).report("${a.id} 预结单已加入打印队列") }
            is TablesAction.SaveNote -> viewModelScope.launchStorage(messages) { orders.setOrderNote(a.id, a.note).finish("${a.id} 整单备注已保存", a.done) }
            is TablesAction.Change -> viewModelScope.launchStorage(messages) {
                floor.changeTable(a.from, a.to).finish("${a.from} 已换到 ${a.to}，原桌待清台", a.done)
                saved["selected"] = a.to; local.value = local.value.copy(selected = a.to)
            }
            is TablesAction.Merge -> viewModelScope.launchStorage(messages) {
                floor.mergeTable(a.from, a.into).finish("${a.from} 已并入 ${a.into}，原桌待清台", a.done)
                saved["selected"] = a.into; local.value = local.value.copy(selected = a.into)
            }
        }
    }

    private fun Result<Unit>.finish(ok: String, done: (String?) -> Unit) { onSuccess { messages.post(ok) }; done(exceptionOrNull()?.message) }
    private fun Result<Unit>.report(ok: String) = onSuccess { messages.post(ok) }.onFailure { messages.post(it.message ?: "操作失败") }
}
