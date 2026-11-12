package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.RetailRepository
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.model.BarcodeRules
import ai.neargo.fnb.pos.core.model.CartLine
import ai.neargo.fnb.pos.core.model.CashRules
import ai.neargo.fnb.pos.core.model.Category
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.RetailCartRules
import ai.neargo.fnb.pos.core.model.RetailCheckout
import ai.neargo.fnb.pos.core.model.RetailHeld
import ai.neargo.fnb.pos.core.model.RetailPricing
import ai.neargo.fnb.pos.core.model.RetailSale
import ai.neargo.fnb.pos.core.model.SpecGroup
import ai.neargo.fnb.pos.core.model.fenToInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** 收银右侧：扫码态 / 收款态 */
enum class PosMode { Scan, Pay }

/** 收银的浮层（同一时间只开一个） */
sealed interface RetailOverlay {
    data class Weigh(val productId: String, val grams: Int) : RetailOverlay
    data class Sku(val productId: String) : RetailOverlay
    data object Held : RetailOverlay
    data object Clear : RetailOverlay
    data object Custom : RetailOverlay
    data class Done(val sale: RetailSale, val received: Long, val change: Long, val save: Long, val credit: ai.neargo.fnb.pos.core.model.ExchangeCredit? = null, val refund: Long = 0) : RetailOverlay
}

/** 收银页的本地状态（购物车在本机内存，收款时才写进门店数据） */
data class CartState(
    val lines: List<CartLine> = emptyList(),
    val member: Boolean = false,
    val last: Long? = null,
    val open: Long? = null,
    val qty: Int = 1,
    val peek: Boolean = false,
    val peekId: String? = null,
    val unknown: String? = null,
    val cat: String = HOT,
    val mode: PosMode = PosMode.Scan,
    val method: String? = null,
    val cash: String = "",
    val overlay: RetailOverlay? = null,
    val processing: Boolean = false,
)

data class RetailPosUiState(
    val cart: CartState = CartState(),
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val specGroups: List<SpecGroup> = emptyList(),
    val held: List<RetailHeld> = emptyList(),
    val nextNo: String = "",
    val payMethods: List<String> = emptyList(),
    val wallet: Long = 0,
    val online: Boolean = true,
    val totals: RetailPricing.Totals = RetailPricing.Totals(0, 0, 0, 0, 0, 0, 0, 0),
    /** 换货抵扣（订单页换货后带过来，结算时用掉） */
    val credit: ai.neargo.fnb.pos.core.model.ExchangeCredit? = null,
    val settings: ai.neargo.fnb.pos.core.model.RetailSettings = ai.neargo.fnb.pos.core.model.RetailSettings(),
) {
    /** 快捷购物袋（设置里选的、还在售的商品） */
    val bags get() = settings.bagIds.mapNotNull { product(it) }
    /** 有抵扣时的多退少补 */
    val settle get() = credit?.let { RetailPricing.exchangeSettle(totals.due, it.amount) }
    /** 这次要收的钱：有抵扣时是补收部分 */
    val payDue get() = settle?.pay ?: totals.due
    /** 有抵扣且不用补收：直接结算（退差价或刚好抵扣） */
    val settleOnly get() = credit != null && payDue == 0L
    val shown: List<Product> get() = if (cart.cat == HOT) products.filter { it.hot } else products.filter { it.categoryId == cart.cat }
    fun product(id: String) = products.firstOrNull { it.id == id }
    fun groupsOf(p: Product) = specGroups.filter { it.id in p.specGroupIds }.sortedBy { p.specGroupIds.indexOf(it.id) }
    val last get() = cart.lines.firstOrNull { it.key == cart.last }
    /** 当前所选方式不能确认的原因；null = 可以确认 */
    val block: String? get() = when {
        cart.lines.isEmpty() && credit == null -> "购物车是空的"
        settleOnly -> null
        cart.method == null -> "请选择收款方式"
        cart.method == "现金" && (CashRules.amount(cart.cash) ?: 0) < payDue -> "实收不足"
        cart.method == "扫码" && !online -> "离线时扫码不可用"
        cart.method == "储值卡" && !cart.member -> "请先识别会员"
        cart.method == "储值卡" && wallet < payDue -> "储值余额不足"
        else -> null
    }
    fun lineUnit(l: CartLine) = if (l.custom) l.customPrice ?: 0 else product(l.productId)?.let { RetailCheckout.unitOf(it, l.sku) } ?: 0
    fun lineInfo(l: CartLine) = if (l.custom) RetailPricing.PriceInfo(l.customPrice ?: 0) else product(l.productId)?.let { RetailCheckout.info(it, l.sku) } ?: RetailPricing.PriceInfo(0)
    fun lineName(l: CartLine) = l.customName ?: product(l.productId)?.name ?: l.productId
    fun lineAmount(l: CartLine) = lineInfo(l).let { RetailPricing.lineAmount(l, it.unit) - RetailPricing.linePromo(l, it) }
}

sealed interface RetailPosAction {
    data class Scan(val raw: String) : RetailPosAction
    data class Tap(val productId: String) : RetailPosAction
    data class SelectCat(val id: String) : RetailPosAction
    data class SetQty(val n: Int) : RetailPosAction
    data object TogglePeek : RetailPosAction
    data class PeekAdd(val productId: String) : RetailPosAction
    data class Bump(val key: Long, val d: Int) : RetailPosAction
    data class Undo(val key: Long) : RetailPosAction
    data class ToggleOpen(val key: Long) : RetailPosAction
    data class Gift(val key: Long) : RetailPosAction
    data class Remove(val key: Long) : RetailPosAction
    data object ToggleMember : RetailPosAction
    data object AddCustom : RetailPosAction
    /** 购物车里的快捷购物袋 */
    data class AddBag(val productId: String) : RetailPosAction
    data class CustomAdd(val name: String, val price: Long) : RetailPosAction
    data object OpenDrawer : RetailPosAction
    data object Hold : RetailPosAction
    data object ShowHeld : RetailPosAction
    data class Recall(val no: String) : RetailPosAction
    data object AskClear : RetailPosAction
    data object Clear : RetailPosAction
    data class SetWeight(val grams: Int) : RetailPosAction
    data class AddWeighed(val productId: String, val grams: Int) : RetailPosAction
    data class AddSku(val productId: String, val label: String) : RetailPosAction
    data object Dismiss : RetailPosAction
    data class GoPay(val method: String?) : RetailPosAction
    data object BackToScan : RetailPosAction
    data class SelectMethod(val method: String) : RetailPosAction
    data class CashInput(val text: String) : RetailPosAction
    data object Confirm : RetailPosAction
    /** 购物车底部「现金 刚好」：不进收款页，直接按应收现金入账 */
    data object QuickCash : RetailPosAction
    data object Next : RetailPosAction
}

const val HOT = "hot"
private val METHODS = listOf("现金", "扫码", "银行卡", "储值卡")

/**
 * 零售收银：购物车与扫码分流在本机（规则见 RetailCartRules / BarcodeRules），
 * 价格按门店商品资料实时算（RetailCheckout），挂单 / 取单 / 收款写门店数据（RetailRepository）。
 */
class RetailPosViewModel(
    config: StoreConfigRepository,
    private val retail: RetailRepository,
    device: DeviceRepository,
    private val messages: MessageBus,
) : ViewModel() {
    private val cart = MutableStateFlow(CartState())
    private var seq = System.currentTimeMillis()

    val state: StateFlow<RetailPosUiState> = combine(config.current, cart, device.status) { store, c, d ->
        if (store == null) return@combine RetailPosUiState(cart = c)
        val products = store.products
        RetailPosUiState(
            cart = c, products = products,
            categories = store.categories.filter { cat -> products.any { it.categoryId == cat.id } },
            specGroups = store.specGroups, held = store.retail.held, nextNo = "#R%04d".format(store.retail.saleSeq),
            payMethods = METHODS.filter { it in store.settings.payMethods }, wallet = store.walletBalance, online = d.online,
            totals = RetailCheckout.totals(products, c.lines, c.member), credit = store.retail.credit, settings = store.retail.settings,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RetailPosUiState())

    private fun set(f: (CartState) -> CartState) = cart.update(f)
    private fun s() = state.value

    private fun add(line: CartLine) {
        val r = RetailCartRules.scanAdd(cart.value.lines, line) { ++seq }
        // 促销品限购（零售设置）：超了就不加，提示原因
        RetailCheckout.limitBlock(s().products, r.lines, s().settings.promoLimit)?.let { messages.post(it); set { c -> c.copy(qty = 1, overlay = null) }; return }
        set { it.copy(lines = r.lines, last = r.last, unknown = null, qty = 1, overlay = null, mode = PosMode.Scan, method = null, cash = "") }
    }

    /** 加一件商品：缺货拦下；称重开称重弹窗；多规格开规格矩阵；否则按「× N」加购 */
    private fun pick(p: Product, qty: Int = cart.value.qty) {
        val w = p.retail?.weighable == true
        when {
            p.soldOut -> messages.post("${p.name} 缺货")
            w -> set { it.copy(overlay = RetailOverlay.Weigh(p.id, 0), peek = false, peekId = null) }
            p.hasSpec -> set { it.copy(overlay = RetailOverlay.Sku(p.id), peek = false, peekId = null) }
            else -> add(CartLine(0, p.id, qty))
        }
    }

    /** 扫到一个码：付款码去收款、会员码识别会员、称重码直接加称重行、商品码按商品类型分流、找不到出横幅 */
    private fun scan(raw0: String) {
        val raw = raw0.trim(); if (raw.isEmpty()) return
        val c = cart.value; val products = s().products
        when (BarcodeRules.classify(raw)) {
            BarcodeRules.CodeKind.Pay -> {
                if (c.lines.isEmpty()) messages.post("购物车是空的，先扫商品") else set { it.copy(mode = PosMode.Pay, method = "扫码", cash = "") }
                return
            }
            BarcodeRules.CodeKind.Member -> { set { it.copy(member = true) }; messages.post("已识别会员"); return }
            BarcodeRules.CodeKind.Weigh -> {
                val w = BarcodeRules.parseWeigh(raw)!!
                val p = products.firstOrNull { it.retail?.weighable == true && it.retail.barcodes.any { b -> b.filter(Char::isDigit).toIntOrNull() == w.plu.toInt() } }
                if (p == null) set { it.copy(unknown = raw, peekId = null) } else { add(CartLine(0, p.id, 1, grams = w.grams)); messages.post("称重码：${p.name} ${w.grams}g") }
                return
            }
            else -> Unit
        }
        if (c.mode == PosMode.Pay) set { it.copy(mode = PosMode.Scan, method = null, cash = "") }
        // 规格条码直接加这一格；商品码按商品分流
        products.firstNotNullOfOrNull { p -> p.skus.firstOrNull { it.barcode != null && it.barcode == raw.filterNot(Char::isWhitespace) }?.let { p to it } }?.let { (p, k) ->
            if (c.peek) { set { it.copy(peekId = p.id, unknown = null) }; return }
            RetailCheckout.addBlock(p, k.label)?.let { messages.post(it); return }
            add(CartLine(0, p.id, c.qty, sku = k.label)); return
        }
        val p = BarcodeRules.findByCode(products, raw) { it.retail?.barcodes.orEmpty() }
        when {
            p == null -> set { it.copy(unknown = raw, peekId = null) }
            c.peek -> set { it.copy(peekId = p.id, unknown = null) }
            else -> pick(p)
        }
    }

    fun onAction(a: RetailPosAction) {
        when (a) {
            is RetailPosAction.Scan -> scan(a.raw)
            is RetailPosAction.Tap -> s().product(a.productId)?.let { p -> if (cart.value.peek) set { it.copy(peekId = p.id) } else pick(p) }
            is RetailPosAction.SelectCat -> set { it.copy(cat = a.id) }
            is RetailPosAction.SetQty -> set { it.copy(qty = if (it.qty == a.n) 1 else a.n) }
            RetailPosAction.TogglePeek -> set { it.copy(peek = !it.peek, peekId = null, unknown = null) }
            is RetailPosAction.PeekAdd -> s().product(a.productId)?.let { p -> set { it.copy(peek = false, peekId = null) }; pick(p, 1) }
            is RetailPosAction.Bump -> {
                val l = RetailCartRules.bump(cart.value.lines, a.key, a.d)
                if (a.d > 0) RetailCheckout.limitBlock(s().products, l, s().settings.promoLimit)?.let { messages.post(it); return }
                set { c -> c.copy(lines = l, last = if (a.d > 0) a.key else c.last?.takeIf { k -> l.any { it.key == k } }) }
            }
            is RetailPosAction.AddBag -> s().product(a.productId)?.let { p -> add(CartLine(0, p.id, 1)) }
            is RetailPosAction.Undo -> set { c -> val l = RetailCartRules.undoLast(c.lines, a.key); c.copy(lines = l, last = a.key.takeIf { k -> l.any { it.key == k } }) }
            is RetailPosAction.ToggleOpen -> set { it.copy(open = if (it.open == a.key) null else a.key) }
            is RetailPosAction.Gift -> set { it.copy(lines = RetailCartRules.toggleGift(it.lines, a.key), open = null) }
            is RetailPosAction.Remove -> set { c -> c.copy(lines = RetailCartRules.remove(c.lines, a.key), open = null, last = c.last.takeIf { it != a.key }) }
            RetailPosAction.ToggleMember -> set { it.copy(member = !it.member) }
            RetailPosAction.AddCustom -> set { it.copy(overlay = RetailOverlay.Custom) }
            is RetailPosAction.CustomAdd -> if (a.price > 0) add(CartLine(0, "custom", 1, customName = a.name.trim().ifBlank { "临时商品" }, customPrice = a.price))
            RetailPosAction.OpenDrawer -> messages.post("已发送开钱箱指令（演示）")
            RetailPosAction.Hold -> hold { set { CartState(cat = it.cat) } }
            RetailPosAction.ShowHeld -> set { it.copy(overlay = RetailOverlay.Held) }
            is RetailPosAction.Recall -> viewModelScope.launchStorage(messages) {
                // 当前购物车有东西：先挂起来，再取
                if (cart.value.lines.isNotEmpty()) retail.holdCart(cart.value.lines, if (cart.value.member) "会员" else null, "取单时自动挂起").onFailure { messages.post(it.message ?: "挂单失败"); return@launchStorage }
                retail.recallHeld(a.no).onSuccess { h ->
                    set { CartState(lines = h.lines, member = h.member != null, last = h.lines.firstOrNull()?.key, cat = it.cat) }
                    messages.post("已取回挂单 ${h.no}")
                }.onFailure { messages.post(it.message ?: "取单失败") }
            }
            RetailPosAction.AskClear -> if (cart.value.lines.isNotEmpty()) set { it.copy(overlay = RetailOverlay.Clear) }
            RetailPosAction.Clear -> set { CartState(cat = it.cat) }
            is RetailPosAction.SetWeight -> set { c -> (c.overlay as? RetailOverlay.Weigh)?.let { c.copy(overlay = it.copy(grams = a.grams.coerceIn(0, 99_999))) } ?: c }
            is RetailPosAction.AddWeighed -> if (a.grams > 0) add(CartLine(0, a.productId, 1, grams = a.grams))
            is RetailPosAction.AddSku -> s().product(a.productId)?.let { p ->
                RetailCheckout.addBlock(p, a.label)?.let { messages.post(it) } ?: add(CartLine(0, p.id, 1, sku = a.label))
            }
            RetailPosAction.Dismiss -> set { it.copy(overlay = null) }
            is RetailPosAction.GoPay -> if (cart.value.lines.isNotEmpty() && !s().settleOnly) set { it.copy(mode = PosMode.Pay, method = a.method, cash = "", open = null, peek = false) }
            RetailPosAction.BackToScan -> set { it.copy(mode = PosMode.Scan, method = null, cash = "") }
            is RetailPosAction.SelectMethod -> set { it.copy(method = a.method, cash = "") }
            is RetailPosAction.CashInput -> set { it.copy(cash = a.text) }
            RetailPosAction.Confirm -> if (s().settleOnly) checkout("", 0) else checkout(cart.value.method ?: return, CashRules.amount(cart.value.cash) ?: s().payDue)
            RetailPosAction.QuickCash -> checkout("现金", s().payDue)
            RetailPosAction.Next -> set { CartState(cat = it.cat) }
        }
    }

    private fun hold(after: () -> Unit) = viewModelScope.launchStorage(messages) {
        val c = cart.value
        retail.holdCart(c.lines, if (c.member) "会员" else null, "顾客暂离").onSuccess { messages.post("已挂单 $it，可在「取单」取回"); after() }.onFailure { messages.post(it.message ?: "挂单失败") }
    }

    private fun checkout(method: String, received: Long) {
        val c = cart.value
        if (c.processing) return
        set { it.copy(processing = true) }
        val save = s().totals.save; val credit = s().credit; val pay = s().payDue; val refund = s().settle?.refund ?: 0
        viewModelScope.launchStorage(messages) {
            try {
                retail.checkoutRetail(c.lines, c.member, method, if (method == "现金") received else pay, s().online)
                    .onSuccess { sale -> set { CartState(cat = it.cat, overlay = RetailOverlay.Done(sale, if (method == "现金") received else pay, if (method == "现金") received - pay else 0, save, credit, refund)) } }
                    .onFailure { messages.post(it.message ?: "收款失败") }
            } finally { set { it.copy(processing = false) } }
        }
    }
}
