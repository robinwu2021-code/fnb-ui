package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.RetailRepository
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.model.EXCHANGE
import ai.neargo.fnb.pos.core.model.ExchangeCredit
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.RetailSale
import ai.neargo.fnb.pos.core.model.ReturnRequest
import ai.neargo.fnb.pos.core.model.ReturnRules
import ai.neargo.fnb.pos.core.model.SaleReturn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ReturnMode { Return, Exchange }

/** 退货 / 换货流程（弹窗里的本地状态）。saleNo 为空且 step > 1 = 无单退货 */
data class ReturnFlow(
    val mode: ReturnMode = ReturnMode.Return,
    val step: Int = 1,
    val saleNo: String? = null,
    val pick: Map<String, Int> = emptyMap(),
    val reason: ReturnRules.Reason? = null,
    val restock: Boolean = true,
    val method: String = "",
    val approver: String = "",
    val secret: String = "",
    val query: String = "",
    val busy: Boolean = false,
    val done: SaleReturn? = null,
) { val noReceipt get() = saleNo == null }

data class RetailOrdersUiState(
    val sales: List<RetailSale> = emptyList(),
    val filter: ReturnRules.SaleStatus? = null,
    val query: String = "",
    val selected: String? = null,
    val flow: ReturnFlow? = null,
    val products: List<Product> = emptyList(),
    val credit: ExchangeCredit? = null,
    val online: Boolean = true,
) {
    val shown get() = sales.filter { (filter == null || it.status == filter) && (query.isBlank() || query.trim() in it.no || it.member?.contains(query.trim()) == true) }
    val current get() = sales.firstOrNull { it.no == selected } ?: shown.firstOrNull()
    fun sale(no: String?) = sales.firstOrNull { it.no == no }

    /** 不能退 / 换的原因；null = 可以 */
    fun blockOf(s: RetailSale): String? = when {
        ReturnRules.channelBlocked(s.channel.name.lowercase()) -> "即时零售平台单请在平台发起退款"
        s.status == ReturnRules.SaleStatus.Returned -> "已全部退货"
        else -> null
    }

    /** 无单退货能选的商品：按件、无规格（按现价退） */
    val looseItems get() = products.filter { it.retail?.weighable != true && !it.hasSpec && (flow?.query.isNullOrBlank() || flow!!.query.trim() in it.name || it.retail?.barcodes?.any { b -> flow.query.trim() in b } == true) }

    /** 这次要退的金额 / 件数（规则同入账，用 ReturnRules） */
    val refund: Long get() = flow?.let { f ->
        val s = sale(f.saleNo)
        if (s != null) ReturnRules.refundAmount(s.soldLines, s.discount, f.pick)
        else f.pick.entries.sumOf { (pid, n) -> (products.firstOrNull { it.id == pid }?.price ?: 0) * n }
    } ?: 0
    val pieces: Int get() = flow?.let { f -> sale(f.saleNo)?.let { ReturnRules.pickedPieces(it.soldLines, f.pick) } ?: f.pick.values.sum() } ?: 0
    /** 退款可选方式：有单按原单收款方式，无单只能现金 / 储值 */
    val methods: List<String> get() = flow?.let { f -> sale(f.saleNo)?.pays?.map { it.method }?.filter { it != EXCHANGE }?.distinct() ?: ReturnRules.NO_RECEIPT_METHODS } ?: emptyList()
}

sealed interface RetailOrdersAction {
    data class Filter(val status: ReturnRules.SaleStatus?) : RetailOrdersAction
    data class Search(val q: String) : RetailOrdersAction
    data class Select(val no: String) : RetailOrdersAction
    data class Reprint(val no: String) : RetailOrdersAction
    /** 打开退货 / 换货；no 为空从第 1 步「找原单」开始 */
    data class Open(val mode: ReturnMode, val no: String?) : RetailOrdersAction
    data class PickSale(val no: String?) : RetailOrdersAction
    data class Pick(val key: String, val qty: Int) : RetailOrdersAction
    data class Reason(val reason: ReturnRules.Reason) : RetailOrdersAction
    data class Restock(val on: Boolean) : RetailOrdersAction
    data class Method(val method: String) : RetailOrdersAction
    data class Auth(val who: String, val secret: String) : RetailOrdersAction
    data class FlowQuery(val q: String) : RetailOrdersAction
    data object Back : RetailOrdersAction
    data object Next : RetailOrdersAction
    data object Confirm : RetailOrdersAction
    data object Close : RetailOrdersAction
}

/** 零售订单：列表、详情、退货四步（含无单退货）、换货三步 → 抵扣带回收银 */
class RetailOrdersViewModel(
    config: StoreConfigRepository,
    private val retail: RetailRepository,
    device: DeviceRepository,
    private val messages: MessageBus,
) : ViewModel() {
    private data class Local(val filter: ReturnRules.SaleStatus? = null, val query: String = "", val selected: String? = null, val flow: ReturnFlow? = null)
    private val local = MutableStateFlow(Local())
    private val events = Channel<Unit>(Channel.BUFFERED)
    /** 换货确认后通知页面切到收银 */
    val toPos = events.receiveAsFlow()

    val state: StateFlow<RetailOrdersUiState> = combine(config.current, local, device.status) { store, l, d ->
        RetailOrdersUiState(store?.retail?.sales.orEmpty(), l.filter, l.query, l.selected, l.flow, store?.products.orEmpty(), store?.retail?.credit, d.online)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RetailOrdersUiState())

    private fun flow(f: (ReturnFlow) -> ReturnFlow) = local.update { l -> l.copy(flow = l.flow?.let(f)) }

    fun onAction(a: RetailOrdersAction) {
        val st = state.value
        when (a) {
            is RetailOrdersAction.Filter -> local.update { it.copy(filter = a.status) }
            is RetailOrdersAction.Search -> local.update { it.copy(query = a.q) }
            is RetailOrdersAction.Select -> local.update { it.copy(selected = a.no) }
            is RetailOrdersAction.Reprint -> viewModelScope.launchStorage(messages) { retail.reprintSale(a.no).onSuccess { messages.post("${a.no} 小票已加入打印队列") }.onFailure { messages.post(it.message ?: "补打失败") } }
            is RetailOrdersAction.Open -> {
                if (a.mode == ReturnMode.Exchange && st.credit != null) { messages.post("上一笔换货（${st.credit.no}）还没在收银结算"); return }
                val s = st.sale(a.no)
                local.update { it.copy(flow = ReturnFlow(a.mode, if (s != null) 2 else 1, s?.no, method = s?.pays?.firstOrNull { p -> p.method != EXCHANGE }?.method ?: "现金")) }
            }
            is RetailOrdersAction.PickSale -> {
                val s = st.sale(a.no)
                flow { it.copy(step = 2, saleNo = s?.no, pick = emptyMap(), method = s?.pays?.firstOrNull { p -> p.method != EXCHANGE }?.method ?: "现金") }
            }
            is RetailOrdersAction.Pick -> flow { f -> f.copy(pick = (f.pick + (a.key to a.qty)).filterValues { it > 0 }) }
            is RetailOrdersAction.Reason -> flow { it.copy(reason = a.reason, restock = ReturnRules.restockByDefault(a.reason)) }
            is RetailOrdersAction.Restock -> flow { it.copy(restock = a.on) }
            is RetailOrdersAction.Method -> flow { it.copy(method = a.method) }
            is RetailOrdersAction.Auth -> flow { it.copy(approver = a.who, secret = a.secret) }
            is RetailOrdersAction.FlowQuery -> flow { it.copy(query = a.q) }
            RetailOrdersAction.Back -> flow { it.copy(step = maxOf(1, it.step - 1)) }
            RetailOrdersAction.Next -> flow { it.copy(step = minOf(4, it.step + 1)) }
            RetailOrdersAction.Close -> local.update { it.copy(flow = null) }
            RetailOrdersAction.Confirm -> confirm()
        }
    }

    private fun confirm() {
        val st = state.value; val f = st.flow ?: return
        if (f.busy) return
        flow { it.copy(busy = true) }
        val ex = f.mode == ReturnMode.Exchange
        viewModelScope.launchStorage(messages) {
            try {
                retail.returnRetail(ReturnRequest(f.saleNo, f.pick, f.reason ?: ReturnRules.Reason.Other, f.restock, f.method, ex, f.approver, f.secret), st.online)
                    .onSuccess { r ->
                        if (ex) { local.update { it.copy(flow = null) }; messages.post("已记换货抵扣，请在收银扫新商品"); events.send(Unit) }
                        else flow { it.copy(done = r) }
                    }.onFailure { messages.post(it.message ?: "退货失败") }
            } finally { flow { it.copy(busy = false) } }
        }
    }
}
