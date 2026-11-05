package ai.neargo.fnb.pos.feature.order

import ai.neargo.fnb.pos.core.data.FloorRepository
import ai.neargo.fnb.pos.core.data.MenuRepository
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.OrderRepository
import ai.neargo.fnb.pos.core.data.PreferencesRepository
import ai.neargo.fnb.pos.core.data.SessionRepository
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import ai.neargo.fnb.pos.core.model.Dish
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.OrderRules
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

/** 菜单筛选（页面局部状态，保存在 SavedStateHandle，进程重建后保留）。 */
data class MenuFilter(val cat: String = "hot", val query: String = "", val tag: String? = null, val arrange: Arrange = Arrange.Top)

data class CategoryItem(val id: String, val name: String, val count: Int)
data class DishItem(val dish: Dish, val qty: Int)

data class OrderUiState(
    val table: DiningTable? = null,
    val order: TableOrder = TableOrder(),
    val totals: Totals = OrderRules.totals(TableOrder()),
    val categories: List<CategoryItem> = emptyList(),
    val filter: MenuFilter = MenuFilter(),
    val dishes: List<DishItem> = emptyList(),
    val prefs: DisplayPrefs = DisplayPrefs(),
    val specs: List<SpecGroup> = emptyList(),
) {
    val canOrder get() = table?.status == TableStatus.Dining
}

sealed interface OrderAction {
    data class SelectCategory(val id: String) : OrderAction
    data class Search(val query: String) : OrderAction
    data class Filter(val tag: String?) : OrderAction
    data object ToggleArrange : OrderAction
    data class AddDish(val dish: Dish, val spec: String = "", val unit: Int = dish.price, val qty: Int = 1) : OrderAction
    data class ChangeQty(val uid: Long, val delta: Int) : OrderAction
    data object ClearNew : OrderAction
    data object SendToKitchen : OrderAction
    data class Advance(val uid: Long, val to: LineStatus) : OrderAction
    data object ToggleMember : OrderAction
    data class Notice(val message: String) : OrderAction
}

/** 快捷筛选：全部 / 辣味 / 素食（key 为标签）。 */
val QuickFilters = listOf(null to "全部", "辣" to "辣味", "素" to "素食")

/**
 * 点餐 ViewModel：读会话（当前桌）、订单、偏好与本地筛选，输出 OrderUiState；
 * 写操作经 OrderRepository，规则拒绝的原因通过 MessageBus 提示。
 */
class OrderViewModel(
    private val menu: MenuRepository,
    private val session: SessionRepository,
    floor: FloorRepository,
    private val orders: OrderRepository,
    prefs: PreferencesRepository,
    private val messages: MessageBus,
    private val saved: SavedStateHandle,
) : ViewModel() {

    private val filter = saved.getStateFlow(KEY_CAT, "hot").let { cat ->
        combine(cat, saved.getStateFlow(KEY_QUERY, ""), saved.getStateFlow<String?>(KEY_TAG, null), saved.getStateFlow(KEY_ARRANGE, Arrange.Top.name)) { c, q, t, a ->
            MenuFilter(c, q, t, Arrange.valueOf(a))
        }
    }

    val state: StateFlow<OrderUiState> = combine(session.session, floor.tables, orders.orders, prefs.prefs, filter) { s, tables, all, p, f ->
        val order = all[s.currentTableId] ?: TableOrder()
        val q = f.query.trim()
        val base = when {
            q.isNotEmpty() -> menu.dishes.filter { it.name.contains(q) || it.en.contains(q, ignoreCase = true) || it.id == q }
            f.cat == "hot" -> menu.dishes.filter { it.hot }
            else -> menu.dishes.filter { it.cat == f.cat }
        }.let { list -> if (f.tag == null) list else list.filter { f.tag in it.tags } }
        OrderUiState(
            table = tables.firstOrNull { it.id == s.currentTableId },
            order = order,
            totals = OrderRules.totals(order),
            categories = menu.categories.map { c ->
                CategoryItem(c.id, c.name, order.lines.filter { l -> menu.dish(l.dishId).let { if (c.id == "hot") it.hot else it.cat == c.id } }.sumOf { it.qty })
            },
            filter = f,
            dishes = base.map { DishItem(it, OrderRules.qtyOf(order, it.id)) },
            prefs = p,
            specs = menu.specs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderUiState())

    private val tableId get() = session.session.value.currentTableId

    fun onAction(a: OrderAction) {
        when (a) {
            is OrderAction.SelectCategory -> { saved[KEY_CAT] = a.id; saved[KEY_QUERY] = "" }
            is OrderAction.Search -> saved[KEY_QUERY] = a.query
            is OrderAction.Filter -> saved[KEY_TAG] = a.tag
            OrderAction.ToggleArrange -> saved[KEY_ARRANGE] = (if (state.value.filter.arrange == Arrange.Top) Arrange.Left else Arrange.Top).name
            is OrderAction.AddDish -> launchResult { orders.addDish(tableId, a.dish, a.spec, a.unit, a.qty) }
            is OrderAction.ChangeQty -> launchResult { orders.changeQty(tableId, a.uid, a.delta) }
            OrderAction.ClearNew -> viewModelScope.launch { orders.clearNew(tableId); messages.post("已清空新加菜品") }
            OrderAction.SendToKitchen -> viewModelScope.launch {
                val n = orders.sendToKitchen(tableId)
                if (n > 0) messages.post("已送厨 $n 份（演示）")
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
        }
    }

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
