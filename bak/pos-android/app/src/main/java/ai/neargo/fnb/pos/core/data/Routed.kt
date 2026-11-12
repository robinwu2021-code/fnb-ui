package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.lan.IntentResult
import kotlinx.coroutines.flow.stateIn
import ai.neargo.fnb.pos.core.lan.LanLink
import ai.neargo.fnb.pos.core.lan.RemoteIntent
import ai.neargo.fnb.pos.core.lan.RemoteIntent as I
import ai.neargo.fnb.pos.core.model.AdjustmentRequest
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.ShiftRecord
import ai.neargo.fnb.pos.core.model.Sku
import ai.neargo.fnb.pos.core.model.StoreSettings
import ai.neargo.fnb.pos.core.model.TableOrder

/**
 * 副 POS 写入路由（N5）：读取一律来自本机（副机为主机只读副本）；
 * 副机的营业写入转为意图交主机仲裁，管理/库存/交班/打印仅主 POS 可执行。单机或主机时直接调用本机仓库。
 */
private const val HOST_ONLY = "仅主 POS 可执行此操作"
const val HOST_DOWN = "主 POS 未连接，本机只读"

/** 设备状态加上主副 POS 连接：副 POS 连不上主机时标记只读，确认收款等按钮直接显示原因（不用等点了才弹提示） */
class RoutedDevice(private val base: DeviceRepository, lan: kotlinx.coroutines.flow.StateFlow<LanState>, link: kotlinx.coroutines.flow.StateFlow<ai.neargo.fnb.pos.core.lan.LinkStatus>,
                   scope: kotlinx.coroutines.CoroutineScope) : DeviceRepository by base {
    override val status = kotlinx.coroutines.flow.combine(base.status, lan, link) { d, l, k -> d.copy(readOnly = readOnlyReason(l.role, k.connected)) }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, base.status.value.copy(readOnly = readOnlyReason(lan.value.role, link.value.connected)))
}

fun readOnlyReason(role: LanRole, connected: Boolean): String? = if (role == LanRole.Secondary && !connected) HOST_DOWN else null

private fun IntentResult.unit(): Result<Unit> = if (ok) Result.success(Unit) else Result.failure(IllegalStateException(message))

class RoutedFloor(private val ws: LocalWorkspace, private val link: LanLink) : FloorRepository by ws {
    private suspend fun go(i: RemoteIntent, s: String, local: suspend () -> Result<Unit>) = if (link.isSecondary) link.submit(i, s).unit() else local()
    override suspend fun createReservation(tableId: String, name: String, phone: String, guests: Int, arrivesAt: Long, note: String) =
        go(I.CreateReservation(tableId, name, phone, guests, arrivesAt, note), "$tableId 预订") { ws.createReservation(tableId, name, phone, guests, arrivesAt, note) }
    override suspend fun cancelReservation(id: String) = go(I.CancelReservation(id), "取消预订") { ws.cancelReservation(id) }
    override suspend fun checkInReservation(id: String) = go(I.CheckInReservation(id), "预订到店") { ws.checkInReservation(id) }
    /** 副机：主机执行成功后，本机的选桌跟到目标桌（主机那边不动本机选桌） */
    private suspend fun Result<Unit>.thenSelect(tableId: String) = also { if (it.isSuccess && link.isSecondary) runCatching { ws.selectTable(tableId) } }
    override suspend fun openTable(tableId: String, guests: Int) = go(I.OpenTable(tableId, guests), "$tableId 开台") { ws.openTable(tableId, guests) }.thenSelect(tableId)
    override suspend fun settle(tableId: String, amount: Long, method: String, online: Boolean, received: Long) =
        go(I.Settle(tableId, amount, method, online, received), "$tableId 结账") { ws.settle(tableId, amount, method, online, received) }
    override suspend fun startCleaning(tableId: String) = go(I.StartCleaning(tableId), "$tableId 清台") { ws.startCleaning(tableId) }
    override suspend fun finishCleaning(tableId: String) = go(I.FinishCleaning(tableId), "$tableId 清台完成") { ws.finishCleaning(tableId) }
    override suspend fun setPosition(tableId: String, x: Int, y: Int) = go(I.SetPosition(tableId, x, y), "$tableId 位置") { ws.setPosition(tableId, x, y) }
    override suspend fun changeTable(from: String, to: String) = go(I.ChangeTable(from, to), "$from 换桌 $to") { ws.changeTable(from, to) }.thenSelect(to)
    override suspend fun mergeTable(from: String, into: String) = go(I.MergeTable(from, into), "$from 并台 $into") { ws.mergeTable(from, into) }.thenSelect(into)
    override suspend fun splitLines(from: String, uids: Set<Long>, to: String, guests: Int) = go(I.SplitLines(from, uids, to, guests), "$from 拆单 $to") { ws.splitLines(from, uids, to, guests) }
}

class RoutedOrders(private val ws: LocalWorkspace, private val link: LanLink) : OrderRepository by ws {
    private suspend fun go(i: RemoteIntent, s: String, local: suspend () -> Result<Unit>) = if (link.isSecondary) link.submit(i, s).unit() else local()
    private suspend fun order(tableId: String, i: RemoteIntent, s: String, local: suspend () -> OrderResult): OrderResult {
        if (!link.isSecondary) return local()
        val r = link.submit(i, s)
        return if (r.ok) OrderResult.Ok(ws.lanSnapshot().store?.orders?.get(tableId) ?: TableOrder()) else OrderResult.Rejected(r.message)
    }
    private suspend fun unit(i: RemoteIntent, s: String, local: suspend () -> Unit) {
        if (!link.isSecondary) return local()
        val r = link.submit(i, s)
        if (!r.ok) throw WorkspaceRejectedException(r.message)
    }
    override suspend fun addDish(tableId: String, product: Product, sku: Sku?, qty: Int, note: String, mealChoiceIds: List<String>) =
        order(tableId, I.AddDish(tableId, product.id, sku?.key, qty, note, mealChoiceIds), "$tableId 加 ${product.name}×$qty") { ws.addDish(tableId, product, sku, qty, note, mealChoiceIds) }
    override suspend fun authorizeAdjustment(tableId: String, request: AdjustmentRequest, email: String, password: String) =
        go(I.Adjust(tableId, request, email, password), "$tableId 主管授权") { ws.authorizeAdjustment(tableId, request, email, password) }
    override suspend fun setBenefits(tableId: String, coupon: Boolean, activity: Boolean) = go(I.SetBenefits(tableId, coupon, activity), "$tableId 优惠") { ws.setBenefits(tableId, coupon, activity) }
    override suspend fun setLineNote(tableId: String, uid: Long, note: String) = order(tableId, I.LineNote(tableId, uid, note), "$tableId 菜品备注") { ws.setLineNote(tableId, uid, note) }
    override suspend fun setOrderNote(tableId: String, note: String) = go(I.OrderNote(tableId, note), "$tableId 整单备注") { ws.setOrderNote(tableId, note) }
    override suspend fun hold(tableId: String): Result<Int> {
        if (!link.isSecondary) return ws.hold(tableId)
        val r = link.submit(I.Hold(tableId), "$tableId 挂单")
        return if (r.ok) Result.success(r.qty) else Result.failure(IllegalStateException(r.message))
    }
    override suspend fun retrieve(ticketId: String, tableId: String) = go(I.Retrieve(ticketId, tableId), "$tableId 取单") { ws.retrieve(ticketId, tableId) }
    override suspend fun changeQty(tableId: String, uid: Long, delta: Int) = order(tableId, I.ChangeQty(tableId, uid, delta), "$tableId 改数量") { ws.changeQty(tableId, uid, delta) }
    override suspend fun clearNew(tableId: String) = unit(I.ClearNew(tableId), "$tableId 清空新菜") { ws.clearNew(tableId) }
    override suspend fun sendToKitchen(tableId: String, online: Boolean): SendResult {
        if (!link.isSecondary) return ws.sendToKitchen(tableId, online)
        val r = link.submit(I.SendToKitchen(tableId, online), "$tableId 送厨")
        if (!r.ok && r.qty == 0 && r.message != "没有待送厨的菜品") throw WorkspaceRejectedException(r.message)
        return SendResult(r.qty, queued = false)
    }
    override suspend fun advance(tableId: String, uid: Long, to: LineStatus) = unit(I.Advance(tableId, uid, to), "$tableId 菜品状态") { ws.advance(tableId, uid, to) }
    override suspend fun setMember(tableId: String, member: Boolean) = unit(I.SetMember(tableId, member), "$tableId 会员") { ws.setMember(tableId, member) }
}

class RoutedConfig(private val ws: LocalWorkspace, private val link: LanLink) : StoreConfigRepository by ws {
    private inline fun host(local: () -> Result<Unit>) = if (link.isSecondary) Result.failure(IllegalStateException(HOST_ONLY)) else local()
    override suspend fun addArea(name: String, en: String) = host { return ws.addArea(name, en) }
    override suspend fun saveTable(id: String, areaId: String, seats: String, isNew: Boolean) = host { return ws.saveTable(id, areaId, seats, isNew) }
    override suspend fun addCategory(name: String, en: String) = host { return ws.addCategory(name, en) }
    override suspend fun addSpecGroup(name: String, en: String, options: String) = host { return ws.addSpecGroup(name, en, options) }
    override suspend fun saveProduct(product: Product, menuIds: Set<String>) = host { return ws.saveProduct(product, menuIds) }
    override suspend fun adjustStock(productId: String, skuKey: String?, value: Int, reason: String) = host { return ws.adjustStock(productId, skuKey, value, reason) }
    override suspend fun saveMenu(id: String?, name: String, en: String, productIds: List<String>) = host { return ws.saveMenu(id, name, en, productIds) }
    override suspend fun setPublished(menuId: String, published: Boolean) = host { return ws.setPublished(menuId, published) }
    override suspend fun saveSettings(settings: StoreSettings) = host { return ws.saveSettings(settings) }
}

class RoutedShift(private val ws: LocalWorkspace, private val link: LanLink) : ShiftRepository by ws {
    override suspend fun closeShift(counted: Long, staff: String): Result<ShiftRecord> =
        if (link.isSecondary) Result.failure(IllegalStateException(HOST_ONLY)) else ws.closeShift(counted, staff)
}

class RoutedPrint(private val ws: LocalWorkspace, private val link: LanLink) : PrintRepository by ws {
    override suspend fun preBill(tableId: String) = if (link.isSecondary) Result.failure(IllegalStateException("打印由主 POS 负责，请在主 POS 操作")) else ws.preBill(tableId)
    override suspend fun reprint(jobId: String) = if (link.isSecondary) Result.failure(IllegalStateException("打印由主 POS 负责，请在主 POS 操作")) else ws.reprint(jobId)
    private fun hostOnly() = Result.failure<Unit>(IllegalStateException("打印由主 POS 负责，请在主 POS 操作"))
    override suspend fun savePrinter(p: ai.neargo.fnb.pos.core.model.PrinterConfig) = if (link.isSecondary) hostOnly() else ws.savePrinter(p)
    override suspend fun removePrinter(id: String) = if (link.isSecondary) hostOnly() else ws.removePrinter(id)
    override suspend fun setRoute(station: String, printerId: String?) = if (link.isSecondary) hostOnly() else ws.setRoute(station, printerId)
    override suspend fun testPrint(printerId: String) = if (link.isSecondary) hostOnly() else ws.testPrint(printerId)
    override suspend fun setBackup(station: String, printerId: String?) = if (link.isSecondary) hostOnly() else ws.setBackup(station, printerId)
    override suspend fun setCopies(station: String, printerIds: List<String>) = if (link.isSecondary) hostOnly() else ws.setCopies(station, printerIds)
    override suspend fun redirectPrint(jobId: String, printerId: String) = if (link.isSecondary) hostOnly() else ws.redirectPrint(jobId, printerId)
    override suspend fun resolveUnknown(jobId: String, printed: Boolean, reason: String) = if (link.isSecondary) hostOnly() else ws.resolveUnknown(jobId, printed, reason)
}

class RoutedSync(private val ws: LocalWorkspace, private val link: LanLink) : SyncRepository by ws {
    override suspend fun confirmDelivery(ticketId: String, reason: String) =
        if (link.isSecondary) Result.failure(IllegalStateException(HOST_ONLY)) else ws.confirmDelivery(ticketId, reason)
    override suspend fun printForScreen(ticketId: String) =
        if (link.isSecondary) Result.failure(IllegalStateException(HOST_ONLY)) else ws.printForScreen(ticketId)
}


/** 日常运营路由：本单会员/活动经主机执行；建会员、充值、活动配置、日结仅主 POS。 */
class RoutedOps(private val ws: LocalWorkspace, private val link: LanLink, private val dayCloseBlock: () -> String? = { null }) : OperationsRepository {
    private fun hostOnly() = Result.failure<Nothing>(IllegalStateException(HOST_ONLY))
    override suspend fun createMember(phone: String, name: String) = if (link.isSecondary) hostOnly() else ws.createMember(phone, name)
    override suspend fun recharge(memberId: String, amount: Long, method: String) = if (link.isSecondary) hostOnly() else ws.recharge(memberId, amount, method)
    override suspend fun bindMember(tableId: String, memberId: String?) =
        if (link.isSecondary) link.submit(I.BindMember(tableId, memberId), "$tableId 会员").unit() else ws.bindMember(tableId, memberId)
    override suspend fun setPromotions(tableId: String, ids: List<String>) =
        if (link.isSecondary) link.submit(I.SetPromotions(tableId, ids), "$tableId 优惠活动").unit() else ws.setPromotions(tableId, ids)
    override suspend fun savePromotion(p: ai.neargo.fnb.pos.core.model.Promotion) = if (link.isSecondary) hostOnly() else ws.savePromotion(p)
    override suspend fun closeDay(staff: String) = when {
        link.isSecondary -> hostOnly()
        // 离线时限：云端连不上超过 72 小时暂停日结（切换方案 §5）
        else -> dayCloseBlock()?.let { Result.failure(IllegalStateException(it)) } ?: ws.closeDay(staff)
    }
}

/**
 * 零售路由：副 POS 的收款、退换货、挂单 / 取单、补打小票转成意图交主机仲裁（价格、库存、权限由主机重验，按命令号去重）；
 * 库存单据、改价、价签、零售设置只能在主 POS 做。成功后副机已拉回主机快照，结果从本机副本按单号取。
 */
class RoutedRetail(private val ws: LocalWorkspace, private val link: LanLink) : RetailRepository by ws {
    private fun hostOnly() = Result.failure<Nothing>(IllegalStateException(HOST_ONLY))
    private fun retailOf() = ws.lanSnapshot().store?.retail

    override suspend fun checkoutRetail(lines: List<ai.neargo.fnb.pos.core.model.CartLine>, member: Boolean, method: String, received: Long, online: Boolean,
                                        extras: ai.neargo.fnb.pos.core.model.CheckoutExtras): Result<ai.neargo.fnb.pos.core.model.RetailSale> {
        if (!link.isSecondary) return ws.checkoutRetail(lines, member, method, received, online, extras)
        val r = link.submit(I.RetailCheckout(lines, member, method, received, online, extras), "零售收款 $method")
        if (!r.ok) return Result.failure(IllegalStateException(r.message))
        // 按主机回传的单号精确取；副本还没有就拉一次，仍没有就报已入账（不拿别的单顶替）
        val find = { retailOf()?.sales?.firstOrNull { it.no == r.message } }
        return (find() ?: run { runCatching { link.pull() }; find() })?.let { Result.success(it) } ?: Result.failure(IllegalStateException("已入账，等主机同步后到订单页查看"))
    }

    override suspend fun returnRetail(req: ai.neargo.fnb.pos.core.model.ReturnRequest, online: Boolean): Result<ai.neargo.fnb.pos.core.model.SaleReturn> {
        if (!link.isSecondary) return ws.returnRetail(req, online)
        val r = link.submit(I.RetailReturn(req, online), if (req.exchange) "换货 ${req.saleNo}" else "退货 ${req.saleNo ?: "无单"}")
        if (!r.ok) return Result.failure(IllegalStateException(r.message))
        // 主机回传这笔退货的时间戳：按原单（或无单退货）+ 时间精确取，取不到拉一次，仍没有就报已入账
        val at = r.message.toLongOrNull()
        val find = {
            val d = retailOf()
            if (req.saleNo != null) d?.sales?.firstOrNull { it.no == req.saleNo }?.returns?.firstOrNull { it.at == at }
            else d?.looseReturns?.firstOrNull { it.at == at }?.let { ai.neargo.fnb.pos.core.model.SaleReturn(it.at, it.amount, it.lines.sumOf { l -> l.qty }, it.reason, it.method) }
        }
        val ret = find() ?: run { runCatching { link.pull() }; find() }
        return ret?.let { Result.success(it) } ?: Result.failure(IllegalStateException("已入账，等主机同步后到订单页查看"))
    }

    override suspend fun holdCart(lines: List<ai.neargo.fnb.pos.core.model.CartLine>, member: String?, why: String): Result<String> {
        if (!link.isSecondary) return ws.holdCart(lines, member, why)
        val r = link.submit(I.RetailHold(lines, member, why), "零售挂单")
        return if (r.ok) Result.success(r.message) else Result.failure(IllegalStateException(r.message))
    }

    override suspend fun recallHeld(no: String): Result<ai.neargo.fnb.pos.core.model.RetailHeld> {
        if (!link.isSecondary) return ws.recallHeld(no)
        // 取单前先记下挂单内容（主机执行后副本里就没有了）
        // 别的副机刚挂的单本机副本可能还没有：先从主机拉一次
        val h = retailOf()?.held?.firstOrNull { it.no == no }
            ?: run { runCatching { link.pull() }; retailOf()?.held?.firstOrNull { it.no == no } }
            ?: return Result.failure(IllegalStateException("挂单不存在"))
        val r = link.submit(I.RetailRecall(no), "零售取单 $no")
        return if (r.ok) Result.success(h) else Result.failure(IllegalStateException(r.message))
    }

    override suspend fun reprintSale(no: String): Result<Unit> = if (link.isSecondary) link.submit(I.RetailReprint(no), "补打 $no").unit() else ws.reprintSale(no)

    // 管理类：只能在主 POS
    override suspend fun newStockDoc(kind: ai.neargo.fnb.pos.core.model.StockDocKind, supplier: String, scope: String, toStore: String) = if (link.isSecondary) hostOnly() else ws.newStockDoc(kind, supplier, scope, toStore)
    override suspend fun setStockDocLines(no: String, lines: List<ai.neargo.fnb.pos.core.model.StockDocLine>) = if (link.isSecondary) hostOnly() else ws.setStockDocLines(no, lines)
    override suspend fun advanceStockDoc(no: String) = if (link.isSecondary) hostOnly() else ws.advanceStockDoc(no)
    override suspend fun voidStockDoc(no: String) = if (link.isSecondary) hostOnly() else ws.voidStockDoc(no)
    override suspend fun setRetailPrice(productId: String, price: Long) = if (link.isSecondary) hostOnly() else ws.setRetailPrice(productId, price)
    override suspend fun queueLabel(productId: String) = if (link.isSecondary) hostOnly() else ws.queueLabel(productId)
    // 钱箱接在主 POS 的小票机上
    override suspend fun openDrawer() = if (link.isSecondary) hostOnly() else ws.openDrawer()
    override suspend fun printLabels(productIds: List<String>, tpl: ai.neargo.fnb.pos.core.model.LabelTpl) = if (link.isSecondary) hostOnly() else ws.printLabels(productIds, tpl)
    override suspend fun saveRetailSettings(settings: ai.neargo.fnb.pos.core.model.RetailSettings) = if (link.isSecondary) hostOnly() else ws.saveRetailSettings(settings)
    // 线上订单由主 POS 接单处理（平台推送到主机）
    override suspend fun simulateOnlineOrder(channel: ai.neargo.fnb.pos.core.model.SaleChannel) = if (link.isSecondary) hostOnly() else ws.simulateOnlineOrder(channel)
    override suspend fun advanceOnline(no: String) = if (link.isSecondary) hostOnly() else ws.advanceOnline(no)
    override suspend fun rejectOnline(no: String, reason: String) = if (link.isSecondary) hostOnly() else ws.rejectOnline(no, reason)
}
