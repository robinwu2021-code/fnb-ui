package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.Menu
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.OutboxItem
import ai.neargo.fnb.pos.core.model.PrintJob
import ai.neargo.fnb.pos.core.model.ShiftRecord
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.Session
import ai.neargo.fnb.pos.core.model.Sku
import ai.neargo.fnb.pos.core.model.StoreData
import ai.neargo.fnb.pos.core.model.StoreSettings
import ai.neargo.fnb.pos.core.model.StoreSummary
import ai.neargo.fnb.pos.core.model.TableOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository 接口：ViewModel 只依赖这些接口。
 * 当前实现为完整离线的本机仓库（LocalWorkspace）；阶段三接服务时替换实现，保持接口与错误语义不变。
 * 写操作失败统一返回 Result.failure / OrderResult.Rejected，message 可直接展示。
 */

/** 会话：本地账号、门店选择、当前工作桌台。 */
interface SessionRepository {
    val session: StateFlow<Session>
    val stores: StateFlow<List<StoreSummary>>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun register(merchant: String, email: String, password: String, confirm: String): Result<Unit>
    suspend fun enterDemo()
    suspend fun logout()
    suspend fun selectStore(storeId: String)
    suspend fun selectTable(tableId: String)
}

/** 门店配置：区域、桌台、分类、规格组、商品、库存、餐牌、门店设置。 */
interface StoreConfigRepository {
    /** 当前门店全部数据；未选店时为 null。 */
    val current: StateFlow<StoreData?>
    suspend fun addArea(name: String, en: String): Result<Unit>
    suspend fun saveTable(id: String, areaId: String, seats: String, isNew: Boolean): Result<Unit>
    suspend fun addCategory(name: String, en: String): Result<Unit>
    suspend fun addSpecGroup(name: String, en: String, options: String): Result<Unit>
    suspend fun saveProduct(product: Product, menuIds: Set<String>): Result<Unit>
    suspend fun adjustStock(productId: String, skuKey: String?, value: Int, reason: String): Result<Unit>
    suspend fun saveMenu(id: String?, name: String, en: String, productIds: List<String>): Result<Unit>
    suspend fun setPublished(menuId: String, published: Boolean): Result<Unit>
    suspend fun saveSettings(settings: StoreSettings): Result<Unit>
}

/** 桌台状态流转。开台、结账会同时影响订单，由实现保证原子性。 */
interface FloorRepository {
    val tables: StateFlow<List<DiningTable>>
    suspend fun createReservation(tableId: String, name: String, phone: String, guests: Int, arrivesAt: Long, note: String): Result<Unit>
    suspend fun cancelReservation(id: String): Result<Unit>
    suspend fun checkInReservation(id: String): Result<Unit>
    suspend fun openTable(tableId: String, guests: Int): Result<Unit>
    /** 结账：有新加或待同步行时拒绝；离线收款进入同步队列。 */
    suspend fun settle(tableId: String, amount: Int, method: String, online: Boolean, received: Int = amount): Result<Unit>
    suspend fun startCleaning(tableId: String): Result<Unit>
    suspend fun finishCleaning(tableId: String): Result<Unit>
    /** 平面位置（画布单位）。 */
    suspend fun setPosition(tableId: String, x: Int, y: Int): Result<Unit>
    /** 换桌：整单移到空闲 / 预订桌，原桌转待清台。 */
    suspend fun changeTable(from: String, to: String): Result<Unit>
    /** 并台：from 并入 into，人数相加，原桌转待清台。 */
    suspend fun mergeTable(from: String, into: String): Result<Unit>
    /** 拆单：选中行移到目标桌（空闲桌按 guests 开台）。 */
    suspend fun splitLines(from: String, uids: Set<Long>, to: String, guests: Int): Result<Unit>
}

/** 送厨结果：份数与是否进入离线队列。 */
data class SendResult(val qty: Int, val queued: Boolean)

/** 订单：按桌台隔离保存；规则校验失败返回 Rejected，不修改数据。 */
interface OrderRepository {
    val orders: StateFlow<Map<String, TableOrder>>
    suspend fun addDish(tableId: String, product: Product, sku: Sku?, qty: Int, note: String = "", mealChoiceIds: List<String> = emptyList()): OrderResult
    suspend fun authorizeAdjustment(tableId: String, request: ai.neargo.fnb.pos.core.model.AdjustmentRequest, email: String, password: String): Result<Unit>
    suspend fun setBenefits(tableId: String, coupon: Boolean, activity: Boolean): Result<Unit>
    suspend fun setLineNote(tableId: String, uid: Long, note: String): OrderResult
    suspend fun setOrderNote(tableId: String, note: String): Result<Unit>
    /** 挂单：把草稿菜品暂存为票据并从桌台移除，返回份数。 */
    suspend fun hold(tableId: String): Result<Int>
    /** 取单：票据菜品作为新加并入目标桌（目标需用餐中）。 */
    suspend fun retrieve(ticketId: String, tableId: String): Result<Unit>
    suspend fun changeQty(tableId: String, uid: Long, delta: Int): OrderResult
    suspend fun clearNew(tableId: String)
    suspend fun sendToKitchen(tableId: String, online: Boolean): SendResult
    suspend fun advance(tableId: String, uid: Long, to: LineStatus)
    suspend fun setMember(tableId: String, member: Boolean)
}

/** 同步队列：离线产生的送厨批次与收款，联网后按创建顺序补发，幂等。 */
interface SyncRepository {
    /** 当前门店的队列（含已同步记录）。 */
    val outbox: StateFlow<List<OutboxItem>>
    /** 所有门店待同步数量。 */
    val pendingCount: StateFlow<Int>
    /** 补发全部待同步项，返回本次完成数量。 */
    suspend fun syncNow(): Int
}

/** 本机打印队列：厨房单 / 预结单 / 结账单 / 交班单；缺纸失败保留，恢复后重试；补打单独标识。 */
interface PrintRepository {
    val jobs: StateFlow<List<PrintJob>>
    suspend fun preBill(tableId: String): Result<Unit>
    suspend fun reprint(jobId: String): Result<Unit>
    /** 处理排队与失败任务：kitchenOk=false 时厨房任务失败，前台任务照常打印。返回本次新失败数。 */
    suspend fun process(kitchenOk: Boolean): Int
    /** 当前账单的小票预览文本（不入队）。 */
    fun billPreview(tableId: String): List<String>
}

/** 交班：本班收款汇总、钱箱实盘、生成交班单并开启新班。 */
interface ShiftRepository {
    suspend fun closeShift(counted: Int, staff: String): Result<ShiftRecord>
}

/** 显示偏好：本机持久化，即时生效。 */
interface PreferencesRepository {
    val prefs: Flow<DisplayPrefs>
    suspend fun update(transform: (DisplayPrefs) -> DisplayPrefs)
}

/** 设备与网络：系统网络检测 + 演示开关（强制离线、打印缺纸）。 */
interface DeviceRepository {
    val status: StateFlow<DeviceStatus>
    fun setForceOffline(on: Boolean)
    fun setPrinterOk(ok: Boolean)
}

/** 一次性提示（Toast）。与持久 UiState 分开，避免重组时重复弹出。 */
interface MessageBus {
    val messages: SharedFlow<String>
    fun post(message: String)
}

/** 门店菜单辅助：按 ID 取实体。 */
fun StoreData.product(id: String): Product? = products.firstOrNull { it.id == id }
fun StoreData.areaName(id: String): String = areas.firstOrNull { it.id == id }?.name ?: id
fun StoreData.menusOf(productId: String): List<Menu> = menus.filter { productId in it.productIds }
