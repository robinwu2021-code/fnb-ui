package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.KitchenDelivery

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
    /** 当前账号能看到的门店完整数据（商户汇总报表用） */
    val visibleStores: StateFlow<List<ai.neargo.fnb.pos.core.model.StoreData>>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun register(merchant: String, email: String, password: String, confirm: String): Result<Unit>
    suspend fun enterDemo()
    suspend fun logout()
    suspend fun selectStore(storeId: String)
    suspend fun selectTable(tableId: String)
    /** 真实商户新建门店：按业态从演示门店复制商品、规格、桌台与设置（不带订单、流水、会员、员工），归当前账号；返回门店 ID */
    suspend fun createStore(name: String, code: String, address: String, biz: ai.neargo.fnb.pos.core.model.BizType): Result<String>
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
    /** 批量删商品：在用（未结账餐饮订单、零售挂单、未完成线上单）的不能删；同时移出餐牌和待打价签。返回删除数 */
    suspend fun deleteProducts(ids: Set<String>): Result<Int>
    /** 批量调价：按百分比（-90 到 +300），结果按角取整；没手动改过的规格价跟着走；零售开了自动价签就进待打价签 */
    suspend fun repriceProducts(ids: Set<String>, percent: Int): Result<Int>
    /** 批量删桌台：只能删空闲且没有预订的桌 */
    suspend fun deleteTables(ids: Set<String>): Result<Int>
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
    suspend fun settle(tableId: String, amount: Long, method: String, online: Boolean, received: Long = amount): Result<Unit>
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
    /** 厨房送达记录（N3）。 */
    val deliveries: StateFlow<List<KitchenDelivery>>
    /** 人工确认送达（失败/未知/待出单），记录人员与原因。 */
    suspend fun confirmDelivery(ticketId: String, reason: String): Result<Unit>
    /** 按任务（ticketId）汇总的送达状态，按档口出单方式判定是否需处理。 */
    val deliveryGroups: StateFlow<List<ai.neargo.fnb.pos.core.model.TicketDelivery>>
    /** 厨房屏未收到时改打纸单。 */
    suspend fun printForScreen(ticketId: String): Result<Unit>
    /** 当前门店的队列（含已同步记录）。 */
    val outbox: StateFlow<List<OutboxItem>>
    /** 所有门店待同步数量。 */
    val pendingCount: StateFlow<Int>
    /** 补发全部待同步项，返回本次完成数量。 */
    suspend fun syncNow(): Int
}

/** 本机打印队列：厨房单 / 预结单 / 结账单 / 交班单；缺纸失败保留，恢复后重试；补打单独标识。 */
interface PrintRepository {
    /** 网口打印机配置与档口路由（N6，仅主 POS/单机）。 */
    suspend fun savePrinter(p: ai.neargo.fnb.pos.core.model.PrinterConfig): Result<Unit>
    suspend fun removePrinter(id: String): Result<Unit>
    suspend fun setRoute(station: String, printerId: String?): Result<Unit>
    /** 路由的备用打印机（null=不设）。 */
    suspend fun setBackup(station: String, printerId: String?): Result<Unit>
    /** 路由的副本打印机（空=不打副本）。 */
    suspend fun setCopies(station: String, printerIds: List<String>): Result<Unit>
    /** 手动改投：失败或排队中的任务改由指定打印机打印。 */
    suspend fun redirectPrint(jobId: String, printerId: String): Result<Unit>
    suspend fun testPrint(printerId: String): Result<Unit>
    /** 人工核对结果未知的任务：printed=已出纸；否则生成关联补打。 */
    suspend fun resolveUnknown(jobId: String, printed: Boolean, reason: String): Result<Unit>
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
    suspend fun closeShift(counted: Long, staff: String): Result<ShiftRecord>
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


/** 身份与授权依赖（N4）。 */
class IdentityContext(
    val keys: ai.neargo.fnb.pos.core.identity.DeviceKeyStore,
    val anchors: () -> List<ai.neargo.fnb.pos.core.identity.TrustAnchor>,
    val devIssuer: ai.neargo.fnb.pos.core.identity.DevIssuer?,
)

/** 授权中心（N4）。 */
interface AuthRepository {
    val auth: StateFlow<ai.neargo.fnb.pos.core.identity.AuthStatus>
    val devIssuerAvailable: Boolean
    suspend fun refreshAuth()
    suspend fun importGrant(token: String): Result<Unit>
    suspend fun devIssue(kind: ai.neargo.fnb.pos.core.identity.GrantKind): Result<Unit>
    suspend fun revokeCurrent(): Result<Unit>
    suspend fun confirmClock(email: String, password: String): Result<Unit>
}


/** 日常运营：会员、优惠、日结（N 日常运营）。 */
interface OperationsRepository {
    suspend fun createMember(phone: String, name: String): Result<ai.neargo.fnb.pos.core.model.Member>
    suspend fun recharge(memberId: String, amount: Long, method: String): Result<Unit>
    suspend fun bindMember(tableId: String, memberId: String?): Result<Unit>
    suspend fun setPromotions(tableId: String, ids: List<String>): Result<Unit>
    suspend fun savePromotion(p: ai.neargo.fnb.pos.core.model.Promotion): Result<Unit>
    suspend fun closeDay(staff: String): Result<ai.neargo.fnb.pos.core.model.DayClose>
}


/** 当前员工状态：required=已启用员工模式（需 PIN 上岗）；perms=当班员工（或门店账号）权限。 */
data class StaffState(
    val employees: List<ai.neargo.fnb.pos.core.model.Employee> = emptyList(),
    val current: ai.neargo.fnb.pos.core.model.Employee? = null,
    val required: Boolean = false,
    val perms: Set<ai.neargo.fnb.pos.core.model.Perm> = ai.neargo.fnb.pos.core.model.Perm.entries.toSet(),
) {
    val locked get() = required && current == null
    fun can(p: ai.neargo.fnb.pos.core.model.Perm) = p in perms
}

/** 员工与权限。 */
interface StaffRepository {
    val staff: StateFlow<StaffState>
    suspend fun signInStaff(employeeId: String, pin: String): Result<Unit>
    suspend fun signOutStaff()
    suspend fun saveEmployee(id: String?, name: String, role: ai.neargo.fnb.pos.core.model.Role, pin: String?, enabled: Boolean = true): Result<Unit>
}


/** 厨房屏（N7）：厨师操作经主机仲裁。 */
interface KdsRepository {
    suspend fun kdsAdvance(ticketId: String, expectedRev: Int, to: ai.neargo.fnb.pos.core.model.KdsState): Result<Unit>
    /** 分派模式：负责屏释放任务卡。 */
    suspend fun kdsRelease(ticketId: String, expectedRev: Int): Result<Unit>
    /** 分派模式：主管收回（deviceId=null）或指派负责屏（仅主机）。 */
    suspend fun kdsReassign(ticketId: String, deviceId: String?): Result<Unit>
    /** 档口分派方式（同步显示 / 先接单者负责）。 */
    suspend fun saveDispatchModes(modes: Map<String, ai.neargo.fnb.pos.core.model.KdsDispatch>): Result<Unit>
    /** 档口配置：档口列表（第一个为默认）与分类映射。 */
    suspend fun saveKdsStations(stations: List<String>, mapping: Map<String, String>): Result<Unit>
    /** 档口出单方式（仅纸单/仅厨房屏/任一/两者）。 */
    suspend fun saveDeliveryModes(modes: Map<String, ai.neargo.fnb.pos.core.model.DeliveryMode>): Result<Unit>
}

@kotlinx.serialization.Serializable
internal data class KitchenStationsPayload(val stations: List<String>, val stationOf: Map<String, String>)

@kotlinx.serialization.Serializable
internal data class KitchenDeliveryPolicyPayload(val modes: Map<String, ai.neargo.fnb.pos.core.model.DeliveryMode>)

@kotlinx.serialization.Serializable
internal data class KitchenDispatchPayload(val modes: Map<String, ai.neargo.fnb.pos.core.model.KdsDispatch>)

/**
 * 零售（门店业态 = 零售）：挂单 / 取单、库存作业单据。收银、退换货、价签的写操作随 R3–R6 补进来。
 */
interface RetailRepository {
    val retail: StateFlow<ai.neargo.fnb.pos.core.model.RetailData>
    suspend fun holdCart(lines: List<ai.neargo.fnb.pos.core.model.CartLine>, member: String?, why: String): Result<String>
    suspend fun recallHeld(no: String): Result<ai.neargo.fnb.pos.core.model.RetailHeld>
    suspend fun newStockDoc(kind: ai.neargo.fnb.pos.core.model.StockDocKind, supplier: String = "", scope: String = "", toStore: String = ""): Result<String>
    suspend fun setStockDocLines(no: String, lines: List<ai.neargo.fnb.pos.core.model.StockDocLine>): Result<Unit>
    /** 入库 / 报损：过账；盘点：盘点中 → 提交（冻结账面）→ 再调一次为审批过账 */
    suspend fun advanceStockDoc(no: String): Result<ai.neargo.fnb.pos.core.model.StockDocState>
    suspend fun voidStockDoc(no: String): Result<Unit>
    /** 零售收款入账：写零售单、收款记录（交班汇总包含）、小票、离线时进同步队列，并扣库存 */
    /** 退货 / 换货：原单记退货、退款记负数收款（换货不退钱，记抵扣）、回库存或记报损；无单退货要主管授权 */
    suspend fun returnRetail(req: ai.neargo.fnb.pos.core.model.ReturnRequest, online: Boolean): Result<ai.neargo.fnb.pos.core.model.SaleReturn>
    /** 补打零售小票 */
    suspend fun reprintSale(no: String): Result<Unit>
    /** 改售价：收银立即按新价算，商品进「待打价签」（已在队列里的保留最早的原价） */
    suspend fun setRetailPrice(productId: String, price: Long): Result<Unit>
    /** 手动把商品加进待打价签 */
    suspend fun queueLabel(productId: String): Result<Unit>
    /** 手动开钱箱（找零、换零钱）：要收银权限，记事件留痕 */
    suspend fun openDrawer(): Result<Unit>
    /** 打印选中的价签：生成标签机指令并记录，打完从队列移走；返回张数 */
    suspend fun printLabels(productIds: List<String>, tpl: ai.neargo.fnb.pos.core.model.LabelTpl): Result<Int>
    /** 线上订单：模拟来单（演示；真实平台接入后由平台推送）、接单、拣货完成、顾客取走 / 送达、拒单 */
    suspend fun simulateOnlineOrder(channel: ai.neargo.fnb.pos.core.model.SaleChannel): Result<String>
    suspend fun advanceOnline(no: String): Result<ai.neargo.fnb.pos.core.model.OnlineState>
    suspend fun rejectOnline(no: String, reason: String): Result<Unit>
    /** 保存零售设置 */
    suspend fun saveRetailSettings(settings: ai.neargo.fnb.pos.core.model.RetailSettings): Result<Unit>
    suspend fun checkoutRetail(lines: List<ai.neargo.fnb.pos.core.model.CartLine>, member: Boolean, method: String, received: Long, online: Boolean,
                               extras: ai.neargo.fnb.pos.core.model.CheckoutExtras = ai.neargo.fnb.pos.core.model.CheckoutExtras()): Result<ai.neargo.fnb.pos.core.model.RetailSale>
    /** 当前操作人（员工姓名；没有员工时是登录账号），记在单据和授权说明里 */
    fun actor(): String
    /** 主管授权（改价、整单折扣用）：通过返回授权人 */
    suspend fun authorize(who: String, secret: String): Result<String>
}
