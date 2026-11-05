package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.Area
import ai.neargo.fnb.pos.core.model.Category
import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import ai.neargo.fnb.pos.core.model.Dish
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.Session
import ai.neargo.fnb.pos.core.model.SpecGroup
import ai.neargo.fnb.pos.core.model.TableOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository 接口：界面与 ViewModel 只依赖这些接口。
 * 当前实现为本地演示（DemoWorkspace）；接服务时替换实现，保持接口与错误语义不变。
 */

/** 门店目录：分类、菜品、规格、区域。 */
interface MenuRepository {
    val categories: List<Category>
    val dishes: List<Dish>
    val specs: List<SpecGroup>
    val areas: List<Area>
    fun dish(id: String): Dish
    fun area(id: String): Area
}

/** 会话：门店、员工终端、当前工作桌台。 */
interface SessionRepository {
    val session: StateFlow<Session>
    suspend fun selectTable(tableId: String)
}

/** 桌台：状态流转。开台、结账会同时影响订单，由实现保证原子性。 */
interface FloorRepository {
    val tables: StateFlow<List<DiningTable>>
    suspend fun openTable(tableId: String, guests: Int): Result<Unit>
    suspend fun settle(tableId: String, amount: Int): Result<Unit>
    suspend fun startCleaning(tableId: String): Result<Unit>
    suspend fun finishCleaning(tableId: String): Result<Unit>
}

/** 订单：按桌台隔离保存；规则校验失败时返回 Rejected，不修改数据。 */
interface OrderRepository {
    val orders: StateFlow<Map<String, TableOrder>>
    suspend fun addDish(tableId: String, dish: Dish, spec: String, unit: Int, qty: Int): OrderResult
    suspend fun changeQty(tableId: String, uid: Long, delta: Int): OrderResult
    suspend fun clearNew(tableId: String)
    suspend fun sendToKitchen(tableId: String): Int
    suspend fun advance(tableId: String, uid: Long, to: LineStatus)
    suspend fun setMember(tableId: String, member: Boolean)
}

/** 显示偏好：本机持久化，即时生效。 */
interface PreferencesRepository {
    val prefs: Flow<DisplayPrefs>
    suspend fun update(transform: (DisplayPrefs) -> DisplayPrefs)
}

/** 设备与网络（演示开关）。真实实现接打印、钱箱和连接状态回执。 */
interface DeviceRepository {
    val status: StateFlow<DeviceStatus>
    fun set(status: DeviceStatus)
}

/** 一次性提示（Toast）。与持久 UiState 分开，避免重组时重复弹出。 */
interface MessageBus {
    val messages: SharedFlow<String>
    fun post(message: String)
}
