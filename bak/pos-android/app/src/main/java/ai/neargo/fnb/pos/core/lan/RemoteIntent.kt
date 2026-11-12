package ai.neargo.fnb.pos.core.lan

import ai.neargo.fnb.pos.core.data.FloorRepository
import ai.neargo.fnb.pos.core.data.OrderRepository
import ai.neargo.fnb.pos.core.data.RemoteOrigin
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.model.AdjustmentRequest
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 副机发给主机的业务意图（N5）：只描述「要做什么」，价格、库存、权限由主机按当前状态重验，
 * 不接受副机提交的订单快照。管理/库存/交班不在此列——仅主 POS 可执行。
 */
@Serializable
sealed interface RemoteIntent {
    @Serializable data class OpenTable(val tableId: String, val guests: Int) : RemoteIntent
    @Serializable data class AddDish(val tableId: String, val productId: String, val skuKey: String?, val qty: Int, val note: String = "", val mealChoiceIds: List<String> = emptyList()) : RemoteIntent
    @Serializable data class ChangeQty(val tableId: String, val uid: Long, val delta: Int) : RemoteIntent
    @Serializable data class LineNote(val tableId: String, val uid: Long, val note: String) : RemoteIntent
    @Serializable data class OrderNote(val tableId: String, val note: String) : RemoteIntent
    @Serializable data class ClearNew(val tableId: String) : RemoteIntent
    @Serializable data class SendToKitchen(val tableId: String, val online: Boolean) : RemoteIntent
    @Serializable data class Advance(val tableId: String, val uid: Long, val to: LineStatus) : RemoteIntent
    @Serializable data class SetMember(val tableId: String, val member: Boolean) : RemoteIntent
    @Serializable data class SetBenefits(val tableId: String, val coupon: Boolean, val activity: Boolean) : RemoteIntent
    @Serializable data class Hold(val tableId: String) : RemoteIntent
    @Serializable data class Retrieve(val ticketId: String, val tableId: String) : RemoteIntent
    @Serializable data class Settle(val tableId: String, val amount: Long, val method: String, val online: Boolean, val received: Long) : RemoteIntent
    @Serializable data class StartCleaning(val tableId: String) : RemoteIntent
    @Serializable data class FinishCleaning(val tableId: String) : RemoteIntent
    @Serializable data class ChangeTable(val from: String, val to: String) : RemoteIntent
    @Serializable data class MergeTable(val from: String, val into: String) : RemoteIntent
    @Serializable data class SplitLines(val from: String, val uids: Set<Long>, val to: String, val guests: Int) : RemoteIntent
    @Serializable data class CreateReservation(val tableId: String, val name: String, val phone: String, val guests: Int, val arrivesAt: Long, val note: String) : RemoteIntent
    @Serializable data class CancelReservation(val id: String) : RemoteIntent
    @Serializable data class CheckInReservation(val id: String) : RemoteIntent
    @Serializable data class SetPosition(val tableId: String, val x: Int, val y: Int) : RemoteIntent
    /** 主管授权：凭据只在加密通道内传给主机验证，不保存、不进事件。 */
    @Serializable data class KdsAdvance(val ticketId: String, val rev: Int, val to: ai.neargo.fnb.pos.core.model.KdsState) : RemoteIntent
    @Serializable data class KdsRelease(val ticketId: String, val rev: Int) : RemoteIntent
    @Serializable data class BindMember(val tableId: String, val memberId: String?) : RemoteIntent
    @Serializable data class SetPromotions(val tableId: String, val ids: List<String>) : RemoteIntent
    @Serializable data class Adjust(val tableId: String, val request: AdjustmentRequest, val email: String, val password: String) : RemoteIntent
    // 零售：收款、退换货、挂单 / 取单、补打小票由主机按当前价格与库存重算后入账
    @Serializable data class RetailCheckout(val lines: List<ai.neargo.fnb.pos.core.model.CartLine>, val member: Boolean, val method: String, val received: Long, val online: Boolean, val extras: ai.neargo.fnb.pos.core.model.CheckoutExtras = ai.neargo.fnb.pos.core.model.CheckoutExtras()) : RemoteIntent
    @Serializable data class RetailReturn(val req: ai.neargo.fnb.pos.core.model.ReturnRequest, val online: Boolean) : RemoteIntent
    @Serializable data class RetailHold(val lines: List<ai.neargo.fnb.pos.core.model.CartLine>, val member: String?, val why: String) : RemoteIntent
    @Serializable data class RetailRecall(val no: String) : RemoteIntent
    @Serializable data class RetailReprint(val no: String) : RemoteIntent
}

/** 主机执行结果：ok=业务已执行（或重复请求返回原结果）；qty 用于送厨/挂单数量。 */
@Serializable data class IntentResult(val ok: Boolean, val message: String = "", val qty: Int = 0, val duplicate: Boolean = false)

object Intents {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "kind" }
    fun encode(i: RemoteIntent) = json.encodeToString(RemoteIntent.serializer(), i)
    fun decode(s: String) = json.decodeFromString(RemoteIntent.serializer(), s)
}

/** 主机查询某副机命令是否已提交过（与业务同事务写入的命令日志 Inbox）。 */
fun interface CommandReceipts { suspend fun committed(storeId: String, deviceId: String, commandId: String): Boolean }

/**
 * 主机仲裁：所有副机意图串行执行（单写协调），先查回执去重再执行；
 * 执行时以副机设备/命令ID/操作人作为命令来源写入日志，使去重记录与业务提交同一事务。
 */
class HostExecutor(
    private val floor: FloorRepository, private val orders: OrderRepository, private val config: StoreConfigRepository,
    private val receipts: CommandReceipts, private val ops: ai.neargo.fnb.pos.core.data.OperationsRepository? = null,
    private val kds: ai.neargo.fnb.pos.core.data.KdsRepository? = null,
    private val retail: ai.neargo.fnb.pos.core.data.RetailRepository? = null,
) {
    private val lock = Mutex()

    suspend fun execute(storeId: String, deviceId: String, commandId: String, actorId: String, intent: RemoteIntent): IntentResult = lock.withLock {
        if (config.current.value?.id != storeId) return@withLock IntentResult(false, "主机当前门店与请求不一致")
        if (receipts.committed(storeId, deviceId, commandId)) return@withLock IntentResult(true, "重复请求，已返回原结果", duplicate = true)
        withContext(RemoteOrigin(deviceId, commandId, actorId)) { run(intent) }
    }

    private fun r(x: Result<Unit>) = x.fold({ IntentResult(true) }, { IntentResult(false, it.message ?: "操作失败") })
    private fun o(x: OrderResult) = when (x) { is OrderResult.Ok -> IntentResult(true); is OrderResult.Rejected -> IntentResult(false, x.reason) }
    private suspend fun unit(block: suspend () -> Unit) = runCatching { block() }.fold({ IntentResult(true) }, { IntentResult(false, it.message ?: "操作失败") })

    private suspend fun run(i: RemoteIntent): IntentResult = when (i) {
        is RemoteIntent.OpenTable -> r(floor.openTable(i.tableId, i.guests))
        is RemoteIntent.AddDish -> {
            val p = config.current.value?.products?.firstOrNull { it.id == i.productId }
            if (p == null) IntentResult(false, "商品不存在或已下架")
            else {
                val sku = i.skuKey?.let { k -> p.skus.firstOrNull { it.key == k } }
                if (i.skuKey != null && sku == null) IntentResult(false, "${p.name}的规格已变更，请重新选择") else o(orders.addDish(i.tableId, p, sku, i.qty, i.note, i.mealChoiceIds))
            }
        }
        is RemoteIntent.ChangeQty -> o(orders.changeQty(i.tableId, i.uid, i.delta))
        is RemoteIntent.LineNote -> o(orders.setLineNote(i.tableId, i.uid, i.note))
        is RemoteIntent.OrderNote -> r(orders.setOrderNote(i.tableId, i.note))
        is RemoteIntent.ClearNew -> unit { orders.clearNew(i.tableId) }
        is RemoteIntent.SendToKitchen -> runCatching { orders.sendToKitchen(i.tableId, i.online) }.fold({ IntentResult(it.qty > 0, if (it.qty > 0) "" else "没有待送厨的菜品", it.qty) }, { IntentResult(false, it.message ?: "送厨失败") })
        is RemoteIntent.Advance -> unit { orders.advance(i.tableId, i.uid, i.to) }
        is RemoteIntent.SetMember -> unit { orders.setMember(i.tableId, i.member) }
        is RemoteIntent.SetBenefits -> r(orders.setBenefits(i.tableId, i.coupon, i.activity))
        is RemoteIntent.Hold -> orders.hold(i.tableId).fold({ IntentResult(true, qty = it) }, { IntentResult(false, it.message ?: "挂单失败") })
        is RemoteIntent.Retrieve -> r(orders.retrieve(i.ticketId, i.tableId))
        is RemoteIntent.Settle -> r(floor.settle(i.tableId, i.amount, i.method, i.online, i.received))
        is RemoteIntent.StartCleaning -> r(floor.startCleaning(i.tableId))
        is RemoteIntent.FinishCleaning -> r(floor.finishCleaning(i.tableId))
        is RemoteIntent.ChangeTable -> r(floor.changeTable(i.from, i.to))
        is RemoteIntent.MergeTable -> r(floor.mergeTable(i.from, i.into))
        is RemoteIntent.SplitLines -> r(floor.splitLines(i.from, i.uids, i.to, i.guests))
        is RemoteIntent.CreateReservation -> r(floor.createReservation(i.tableId, i.name, i.phone, i.guests, i.arrivesAt, i.note))
        is RemoteIntent.CancelReservation -> r(floor.cancelReservation(i.id))
        is RemoteIntent.CheckInReservation -> r(floor.checkInReservation(i.id))
        is RemoteIntent.SetPosition -> r(floor.setPosition(i.tableId, i.x, i.y))
        is RemoteIntent.Adjust -> r(orders.authorizeAdjustment(i.tableId, i.request, i.email, i.password))
        is RemoteIntent.KdsAdvance -> kds?.let { r(it.kdsAdvance(i.ticketId, i.rev, i.to)) } ?: IntentResult(false, "主机不支持厨房屏")
        is RemoteIntent.KdsRelease -> kds?.let { r(it.kdsRelease(i.ticketId, i.rev)) } ?: IntentResult(false, "主机不支持厨房屏")
        is RemoteIntent.BindMember -> ops?.let { r(it.bindMember(i.tableId, i.memberId)) } ?: IntentResult(false, "主机不支持会员操作")
        is RemoteIntent.SetPromotions -> ops?.let { r(it.setPromotions(i.tableId, i.ids)) } ?: IntentResult(false, "主机不支持优惠操作")
        // 零售：成功时 message 带回单号，副机拉快照后按单号取结果
        is RemoteIntent.RetailCheckout -> retail?.let { x -> x.checkoutRetail(i.lines, i.member, i.method, i.received, i.online, i.extras).fold({ IntentResult(true, it.no) }, { IntentResult(false, it.message ?: "收款失败") }) } ?: noRetail()
        is RemoteIntent.RetailReturn -> retail?.let { x -> x.returnRetail(i.req, i.online).fold({ IntentResult(true) }, { IntentResult(false, it.message ?: "退货失败") }) } ?: noRetail()
        is RemoteIntent.RetailHold -> retail?.let { x -> x.holdCart(i.lines, i.member, i.why).fold({ IntentResult(true, it) }, { IntentResult(false, it.message ?: "挂单失败") }) } ?: noRetail()
        is RemoteIntent.RetailRecall -> retail?.let { x -> x.recallHeld(i.no).fold({ IntentResult(true, it.no) }, { IntentResult(false, it.message ?: "取单失败") }) } ?: noRetail()
        is RemoteIntent.RetailReprint -> retail?.let { r(it.reprintSale(i.no)) } ?: noRetail()
    }

    private fun noRetail() = IntentResult(false, "主机不支持零售操作")
}
