package ai.neargo.fnb.pos.feature.checkout

import ai.neargo.fnb.pos.core.common.PayMethods
import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.FloorRepository
import ai.neargo.fnb.pos.core.data.PrintRepository
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.model.PaymentRecord
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.OrderRepository
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.OrderRules
import ai.neargo.fnb.pos.core.model.TableOrder
import ai.neargo.fnb.pos.core.model.Totals
import ai.neargo.fnb.pos.navigation.CheckoutRoute
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PayMethod(val label: String, val needsNetwork: Boolean) {
    Cash(PayMethods.CASH, false), Scan(PayMethods.QR, true), Card(PayMethods.CARD, false), Stored(PayMethods.STORED, false),
}

data class CheckoutUiState(
    val table: DiningTable? = null,
    val order: TableOrder = TableOrder(),
    val totals: Totals = OrderRules.totals(TableOrder()),
    val method: PayMethod = PayMethod.Cash,
    val online: Boolean = true,
    val processing: Boolean = false,
    /** 收款成功的金额；非空时显示结果页。 */
    val paid: Long? = null,
    /** 本桌今日已结账记录（不计入本次应收）。 */
    val history: List<PaymentRecord> = emptyList(),
    val cash: String = "",
    val audits: List<ai.neargo.fnb.pos.core.model.AdjustmentAudit> = emptyList(),
    val wallet: Long = 50000,
    /** 厨房送达阻止原因（失败/未知）；null 表示无。 */
    val kitchenBlock: String? = null,
    /** 副 POS 连不上主机：只读原因 */
    val readOnly: String? = null,
) {
    /** 不能结账核对的原因（新加 / 待同步 / 空单）；null 表示可以。 */
    val block get() = OrderRules.checkoutBlock(totals) ?: readOnly ?: kitchenBlock ?: when {
        method == PayMethod.Cash && ai.neargo.fnb.pos.core.model.CashRules.change(cash, totals.due) == null -> "请核对现金实收"
        method == PayMethod.Stored && !order.member -> "请先绑定会员"
        method == PayMethod.Stored && wallet < totals.due -> "本机储值余额不足"
        else -> null
    }
    val canConfirm get() = block == null && !processing && (online || !method.needsNetwork)
}

sealed interface CheckoutAction {
    data class SelectMethod(val method: PayMethod) : CheckoutAction
    data class ChangeQty(val uid: Long, val delta: Int) : CheckoutAction
    data object SendToKitchen : CheckoutAction
    data object ToggleMember : CheckoutAction
    data object ToggleCoupon : CheckoutAction
    data object ToggleActivity : CheckoutAction
    data class CashInput(val value: String) : CheckoutAction
    data class Adjust(val request: ai.neargo.fnb.pos.core.model.AdjustmentRequest, val email: String, val password: String, val done: (String?) -> Unit) : CheckoutAction
    data object Confirm : CheckoutAction
    data object Done : CheckoutAction
    data object PreBill : CheckoutAction
    data class Notice(val message: String) : CheckoutAction
}

sealed interface CheckoutEffect { data object BackToTables : CheckoutEffect }

/**
 * 结账：路由参数 tableId 决定订单；未送厨不能核对；确认后（演示）推进桌台为已结账。
 * 真实支付需支付单与结果查询，超时先查单，不重复扣款。
 */
class CheckoutViewModel(
    private val floor: FloorRepository,
    private val orders: OrderRepository,
    config: StoreConfigRepository,
    private val print: PrintRepository,
    device: DeviceRepository,
    private val messages: MessageBus,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val tableId = saved.toRoute<CheckoutRoute>().tableId
    /** 小票预览文本（当前账单）。 */
    fun preview(): List<String> = print.billPreview(tableId)
    private data class Local(val method: PayMethod = PayMethod.Cash, val processing: Boolean = false, val paid: Long? = null, val cash: String = "")
    private val local = MutableStateFlow(Local(cash = saved["cash"] ?: ""))
    private val effects = Channel<CheckoutEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    val state: StateFlow<CheckoutUiState> = combine(config.current, device.status, local) { store, d, l ->
        val o = store?.orders?.get(tableId) ?: TableOrder()
        CheckoutUiState(store?.tables?.firstOrNull { it.id == tableId }, o, OrderRules.totals(o), l.method, d.online, l.processing, l.paid,
            store?.payments.orEmpty().filter { it.tableId == tableId }.sortedByDescending { it.at },
            l.cash, store?.adjustments.orEmpty().filter { it.tableId == tableId }, store?.members?.firstOrNull { it.id == o.memberId }?.balance ?: store?.walletBalance ?: 50000,
            store?.let { ai.neargo.fnb.pos.core.model.DeliveryRules.checkoutBlock(it, tableId) }, d.readOnly)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckoutUiState())

    fun onAction(a: CheckoutAction) {
        if (local.value.processing && a != CheckoutAction.Done) return
        when (a) {
            is CheckoutAction.CashInput -> { saved["cash"] = a.value; local.value = local.value.copy(cash = a.value) }
            is CheckoutAction.Adjust -> viewModelScope.launchStorage(messages) { orders.authorizeAdjustment(tableId, a.request, a.email, a.password).onSuccess { a.done(null) }.onFailure { a.done(it.message) } }
            CheckoutAction.ToggleCoupon -> viewModelScope.launchStorage(messages) { orders.setBenefits(tableId, !state.value.order.coupon, state.value.order.activity).onFailure { messages.post(it.message ?: "优惠不可用") } }
            CheckoutAction.ToggleActivity -> viewModelScope.launchStorage(messages) { orders.setBenefits(tableId, state.value.order.coupon, !state.value.order.activity).onFailure { messages.post(it.message ?: "优惠不可用") } }
            is CheckoutAction.SelectMethod -> local.value = local.value.copy(method = a.method)
            is CheckoutAction.ChangeQty -> viewModelScope.launchStorage(messages) {
                (orders.changeQty(tableId, a.uid, a.delta) as? OrderResult.Rejected)?.let { messages.post(it.reason) }
            }
            CheckoutAction.SendToKitchen -> viewModelScope.launchStorage(messages) {
                val r = orders.sendToKitchen(tableId, state.value.online)
                if (r.qty > 0) messages.post(if (r.queued) "离线：${r.qty} 份已存入同步队列" else "已送厨 ${r.qty} 份（演示）")
            }
            CheckoutAction.ToggleMember -> viewModelScope.launchStorage(messages) {
                val on = !state.value.order.member
                orders.setMember(tableId, on)
                messages.post(if (on) "已绑定会员 · 95 折（演示）" else "已解绑会员")
            }
            CheckoutAction.Confirm -> {
                val s = state.value
                if (!s.canConfirm) return
                local.value = local.value.copy(processing = true)
                viewModelScope.launchStorage(messages) {
                    delay(600) // 演示：模拟支付处理中，防重复提交
                    floor.settle(tableId, s.totals.due, s.method.label, s.online, if (s.method == PayMethod.Cash) ai.neargo.fnb.pos.core.model.CashRules.amount(s.cash) ?: 0 else s.totals.due)
                        .onSuccess {
                            local.value = local.value.copy(processing = false, paid = s.totals.due)
                            if (!s.online) messages.post("离线收款已记账，联网后自动同步")
                        }
                        .onFailure { local.value = local.value.copy(processing = false); messages.post(it.message ?: "收款失败，账单已保留") }
                }
            }
            CheckoutAction.PreBill -> viewModelScope.launchStorage(messages) { print.preBill(tableId).onSuccess { messages.post("预结单已加入打印队列") }.onFailure { messages.post(it.message ?: "打印失败") } }
            CheckoutAction.Done -> viewModelScope.launchStorage(messages) { effects.send(CheckoutEffect.BackToTables) }
            is CheckoutAction.Notice -> messages.post(a.message)
        }
    }
}
