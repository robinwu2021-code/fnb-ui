package ai.neargo.fnb.pos.feature.order

import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.FloorRepository
import ai.neargo.fnb.pos.core.data.PrintRepository
import ai.neargo.fnb.pos.core.model.HeldTicket
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.OrderRepository
import ai.neargo.fnb.pos.core.data.PreferencesRepository
import ai.neargo.fnb.pos.core.data.SessionRepository
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.OrderRules
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.Sku
import ai.neargo.fnb.pos.core.model.SpecGroup
import ai.neargo.fnb.pos.core.model.TableOrder
import ai.neargo.fnb.pos.core.model.TableStatus
import ai.neargo.fnb.pos.core.model.Totals
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 图文排列：图在上（卡片）/ 图在左（列表）。 */
enum class Arrange(val label: String) { Top("图在上"), Left("图在左") }

/** 菜单筛选（页面局部状态，保存在 SavedStateHandle）。 */
data class MenuFilter(val cat: String = HOT, val query: String = "", val tag: String? = null, val arrange: Arrange = Arrange.Top)

data class CategoryItem(val id: String, val name: String, val count: Int)
data class DishItem(val dish: Product, val qty: Int)

data class OrderUiState(
    val table: DiningTable? = null,
    val order: TableOrder = TableOrder(),
    val totals: Totals = OrderRules.totals(TableOrder()),
    val categories: List<CategoryItem> = emptyList(),
    val filter: MenuFilter = MenuFilter(),
    val dishes: List<DishItem> = emptyList(),
    val prefs: DisplayPrefs = DisplayPrefs(),
    val specGroups: List<SpecGroup> = emptyList(),
    val online: Boolean = true,
    val held: List<HeldTicket> = emptyList(),
    val tables: List<DiningTable> = emptyList(),
) {
    val canOrder get() = table?.status == TableStatus.Dining
    /** 某商品关联的规格组（按 ID 排序，与 SKU 生成顺序一致）。 */
    fun groupsOf(p: Product) = specGroups.filter { it.id in p.specGroupIds }.sortedBy { it.id }
}

sealed interface OrderAction {
    data class SelectCategory(val id: String) : OrderAction
    data class Search(val query: String) : OrderAction
    data class Filter(val tag: String?) : OrderAction
    data object ToggleArrange : OrderAction
    data class AddDish(val dish: Product, val sku: Sku? = null, val qty: Int = 1) : OrderAction
    data class ChangeQty(val uid: Long, val delta: Int) : OrderAction
    data object ClearNew : OrderAction
    data object SendToKitchen : OrderAction
    data class Advance(val uid: Long, val to: LineStatus) : OrderAction
    data object ToggleMember : OrderAction
    data class Notice(val message: String) : OrderAction
    data class LineNote(val uid: Long, val note: String, val done: (String?) -> Unit) : OrderAction
    data class OrderNote(val note: String, val done: (String?) -> Unit) : OrderAction
    data object Hold : OrderAction
    data class Retrieve(val ticketId: String) : OrderAction
    data object PreBill : OrderAction
    data class Change(val to: String, val done: (String?) -> Unit) : OrderAction
    data class Merge(val into: String, val done: (String?) -> Unit) : OrderAction
    data class Split(val uids: Set<Long>, val to: String, val guests: Int, val done: (String?) -> Unit) : OrderAction
}

const val HOT = "hot"

/** 快捷筛选：全部 / 辣味 / 素食（key 为标签）。 */
val QuickFilters = listOf(null to "全部", "辣" to "辣味", "素" to "素食")

/**
 * 点餐 ViewModel：读当前门店（已发布餐牌商品）、当前桌订单、偏好、网络与本地筛选；
 * 写操作经 OrderRepository，规则拒绝原因经 MessageBus 提示。离线送厨进入同步队列。
 */
class OrderViewModel(
    private val session: SessionRepository,
    config: StoreConfigRepository,
    private val orders: OrderRepository,
    private val floor: FloorRepository,
    private val print: PrintRepository,
    prefs: PreferencesRepository,
    private val device: DeviceRepository,
    private val messages: MessageBus,
    private val saved: SavedStateHandle,
) : ViewModel() {

    private val filter = combine(
        saved.getStateFlow(KEY_CAT, HOT), saved.getStateFlow(KEY_QUERY, ""),
        saved.getStateFlow<String?>(KEY_TAG, null), saved.getStateFlow(KEY_ARRANGE, Arrange.Top.name),
    ) { c, q, t, a -> MenuFilter(c, q, t, Arrange.valueOf(a)) }

    val state: StateFlow<OrderUiState> = combine(config.current, prefs.prefs, filter, device.status) { store, p, f, d ->
        if (store == null) return@combine OrderUiState(prefs = p)
        val order = store.orders[store.currentTableId] ?: TableOrder()
        val visible = store.visibleProducts
        val q = f.query.trim()
        val base = when {
            q.isNotEmpty() -> visible.filter { it.name.contains(q) || it.en.contains(q, ignoreCase = true) || it.id == q }
            f.cat == HOT -> visible.filter { it.hot }
            else -> visible.filter { it.categoryId == f.cat }
        }.let { list -> if (f.tag == null) list else list.filter { f.tag in it.tags } }
        fun inCat(id: String, pid: String) = store.products.firstOrNull { it.id == pid }?.let { if (id == HOT) it.hot else it.categoryId == id } == true
        OrderUiState(
            table = store.tables.firstOrNull { it.id == store.currentTableId },
            order = order,
            totals = OrderRules.totals(order),
            categories = listOf(CategoryItem(HOT, "热销", order.lines.filter { inCat(HOT, it.dishId) }.sumOf { it.qty })) +
                store.categories.map { c -> CategoryItem(c.id, c.name, order.lines.filter { inCat(c.id, it.dishId) }.sumOf { it.qty }) },
            filter = f,
            dishes = base.map { DishItem(it, OrderRules.qtyOf(order, it.id)) },
            prefs = p,
            specGroups = store.specGroups,
            online = d.online,
            held = store.held,
            tables = store.tables,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderUiState())

    private val tableId get() = session.session.value.currentTableId

    fun onAction(a: OrderAction) {
        when (a) {
            is OrderAction.SelectCategory -> { saved[KEY_CAT] = a.id; saved[KEY_QUERY] = "" }
            is OrderAction.Search -> saved[KEY_QUERY] = a.query
            is OrderAction.Filter -> saved[KEY_TAG] = a.tag
            OrderAction.ToggleArrange -> saved[KEY_ARRANGE] = (if (state.value.filter.arrange == Arrange.Top) Arrange.Left else Arrange.Top).name
            is OrderAction.AddDish -> launchResult { orders.addDish(tableId, a.dish, a.sku, a.qty) }
            is OrderAction.ChangeQty -> launchResult { orders.changeQty(tableId, a.uid, a.delta) }
            OrderAction.ClearNew -> viewModelScope.launch { orders.clearNew(tableId); messages.post("已清空新加菜品") }
            OrderAction.SendToKitchen -> viewModelScope.launch {
                val r = orders.sendToKitchen(tableId, device.status.value.online)
                if (r.qty > 0) messages.post(if (r.queued) "离线：${r.qty} 份已存入同步队列，联网后自动送厨" else "已送厨 ${r.qty} 份（演示）")
            }
            is OrderAction.Advance -> viewModelScope.launch {
                orders.advance(tableId, a.uid, a.to)
                messages.post(if (a.to == LineStatus.Rushing) "已催菜" else "已标记上菜")
            }
            OrderAction.ToggleMember -> viewModelScope.launch {
                val on = !state.value.order.member
                orders.setMember(tableId, on)
                messages.post(if (on) "已绑定会员 · 95 折（演示）" else "已解绑会员")
            }
            is OrderAction.Notice -> messages.post(a.message)
            is OrderAction.LineNote -> viewModelScope.launch {
                when (val r = orders.setLineNote(tableId, a.uid, a.note)) {
                    is OrderResult.Ok -> a.done(null)
                    is OrderResult.Rejected -> a.done(r.reason)
                }
            }
            is OrderAction.OrderNote -> viewModelScope.launch { orders.setOrderNote(tableId, a.note).finish("整单备注已保存", a.done) }
            OrderAction.Hold -> viewModelScope.launch {
                orders.hold(tableId).onSuccess { messages.post("已挂单 $it 份，可在「取单」取回") }.onFailure { messages.post(it.message ?: "挂单失败") }
            }
            is OrderAction.Retrieve -> viewModelScope.launch {
                orders.retrieve(a.ticketId, tableId).onSuccess { messages.post("已取单到 $tableId") }.onFailure { messages.post(it.message ?: "取单失败") }
            }
            OrderAction.PreBill -> viewModelScope.launch { print.preBill(tableId).onSuccess { messages.post("预结单已加入打印队列") }.onFailure { messages.post(it.message ?: "打印失败") } }
            is OrderAction.Change -> viewModelScope.launch { val from = tableId; floor.changeTable(from, a.to).finish("$from 已换到 ${a.to}，原桌待清台", a.done) }
            is OrderAction.Merge -> viewModelScope.launch { val from = tableId; floor.mergeTable(from, a.into).finish("$from 已并入 ${a.into}，原桌待清台", a.done) }
            is OrderAction.Split -> viewModelScope.launch { floor.splitLines(tableId, a.uids, a.to, a.guests).finish("已拆出 ${a.uids.size} 道菜到 ${a.to}", a.done) }
        }
    }

    private fun Result<Unit>.finish(ok: String, done: (String?) -> Unit) { onSuccess { messages.post(ok) }; done(exceptionOrNull()?.message) }

    private fun launchResult(block: suspend () -> OrderResult) = viewModelScope.launch {
        val r = block()
        if (r is OrderResult.Rejected) messages.post(r.reason)
    }

    private companion object {
        const val KEY_CAT = "cat"
        const val KEY_QUERY = "query"
        const val KEY_TAG = "tag"
        const val KEY_ARRANGE = "arrange"
    }
}
