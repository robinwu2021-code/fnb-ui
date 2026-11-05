package ai.neargo.fnb.pos.feature.checkout

import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.FloorRepository
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
    Cash("现金", false), Scan("扫码", true), Card("银行卡", false), Stored("储值卡", true),
}

data class CheckoutUiState(
    val table: DiningTable? = null,
    val order: TableOrder = TableOrder(),
    val totals: Totals = OrderRules.totals(TableOrder()),
    val method: PayMethod = PayMethod.Cash,
    val online: Boolean = true,
    val processing: Boolean = false,
    /** 收款成功的金额；非空时显示结果页。 */
    val paid: Int? = null,
) {
    /** 不能结账核对的原因（新加 / 待同步 / 空单）；null 表示可以。 */
    val block get() = OrderRules.checkoutBlock(totals)
    val canConfirm get() = block == null && !processing && (online || !method.needsNetwork)
}

sealed interface CheckoutAction {
    data class SelectMethod(val method: PayMethod) : CheckoutAction
    data class ChangeQty(val uid: Long, val delta: Int) : CheckoutAction
    data object SendToKitchen : CheckoutAction
    data object ToggleMember : CheckoutAction
    data object Confirm : CheckoutAction
    data object Done : CheckoutAction
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
    device: DeviceRepository,
    private val messages: MessageBus,
    saved: SavedStateHandle,
) : ViewModel() {
    private val tableId = saved.toRoute<CheckoutRoute>().tableId
    private data class Local(val method: PayMethod = PayMethod.Cash, val processing: Boolean = false, val paid: Int? = null)
    private val local = MutableStateFlow(Local())
    private val effects = Channel<CheckoutEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    val state: StateFlow<CheckoutUiState> = combine(floor.tables, orders.orders, device.status, local) { tables, all, d, l ->
        val o = all[tableId] ?: TableOrder()
        CheckoutUiState(tables.firstOrNull { it.id == tableId }, o, OrderRules.totals(o), l.method, d.online, l.processing, l.paid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckoutUiState())

    fun onAction(a: CheckoutAction) {
        when (a) {
            is CheckoutAction.SelectMethod -> local.value = local.value.copy(method = a.method)
            is CheckoutAction.ChangeQty -> viewModelScope.launch {
                (orders.changeQty(tableId, a.uid, a.delta) as? OrderResult.Rejected)?.let { messages.post(it.reason) }
            }
            CheckoutAction.SendToKitchen -> viewModelScope.launch {
                val r = orders.sendToKitchen(tableId, state.value.online)
                if (r.qty > 0) messages.post(if (r.queued) "离线：${r.qty} 份已存入同步队列" else "已送厨 ${r.qty} 份（演示）")
            }
            CheckoutAction.ToggleMember -> viewModelScope.launch {
                val on = !state.value.order.member
                orders.setMember(tableId, on)
                messages.post(if (on) "已绑定会员 · 95 折（演示）" else "已解绑会员")
            }
            CheckoutAction.Confirm -> {
                val s = state.value
                if (!s.canConfirm) return
                local.value = local.value.copy(processing = true)
                viewModelScope.launch {
                    delay(600) // 演示：模拟支付处理中，防重复提交
                    floor.settle(tableId, s.totals.due, s.method.label, s.online)
                        .onSuccess {
                            local.value = local.value.copy(processing = false, paid = s.totals.due)
                            if (!s.online) messages.post("离线收款已记账，联网后自动同步")
                        }
                        .onFailure { local.value = local.value.copy(processing = false); messages.post(it.message ?: "收款失败，账单已保留") }
                }
            }
            CheckoutAction.Done -> viewModelScope.launch { effects.send(CheckoutEffect.BackToTables) }
            is CheckoutAction.Notice -> messages.post(a.message)
        }
    }
}
