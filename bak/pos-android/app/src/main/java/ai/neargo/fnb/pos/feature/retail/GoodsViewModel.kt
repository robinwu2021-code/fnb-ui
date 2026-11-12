package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.RetailRepository
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.model.BarcodeRules
import ai.neargo.fnb.pos.core.model.CashRules
import ai.neargo.fnb.pos.core.model.Category
import ai.neargo.fnb.pos.core.model.LabelJob
import ai.neargo.fnb.pos.core.model.LabelTpl
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.StockDocKind
import ai.neargo.fnb.pos.core.model.StockDocLine
import ai.neargo.fnb.pos.core.model.StockMovement
import ai.neargo.fnb.pos.core.model.StockWork
import ai.neargo.fnb.pos.core.model.yuan
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
import java.time.LocalDate

enum class GoodsSub(val label: String) { Items("商品资料"), Stock("库存"), Labels("价签") }
enum class StockFilter(val label: String) { All("全部"), Low("低库存"), Out("缺货"), Near("临期") }

/** 库存状态（列表、详情的标签用） */
enum class StockState(val label: String) { Out("缺货"), Low("低库存"), Near("临期"), Ok("充足") }

data class GoodsUiState(
    val sub: GoodsSub = GoodsSub.Items,
    val cat: String = HOT,
    val query: String = "",
    val selected: String? = null,
    val filter: StockFilter = StockFilter.All,
    val unpicked: Set<String> = emptySet(),
    val tpl: LabelTpl = LabelTpl.L60x40,
    val focus: String? = null,
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val queue: List<LabelJob> = emptyList(),
    val moves: List<StockMovement> = emptyList(),
    val printed: Int = 0,
    val today: LocalDate = LocalDate.now(),
    val expiryDays: Int = 3,
) {
    fun product(id: String?) = products.firstOrNull { it.id == id }
    fun stateOf(p: Product) = when {
        StockWork.book(p) <= 0 -> StockState.Out
        StockWork.low(p) -> StockState.Low
        StockWork.near(p, today, expiryDays) -> StockState.Near
        else -> StockState.Ok
    }
    fun matches(p: Product, f: StockFilter) = when (f) {
        StockFilter.All -> true
        StockFilter.Out -> StockWork.book(p) <= 0
        StockFilter.Low -> StockWork.low(p) && StockWork.book(p) > 0
        StockFilter.Near -> StockWork.near(p, today, expiryDays)
    }
    val items get() = products.filter { p ->
        val q = query.trim()
        if (q.isNotEmpty()) q in p.name || p.retail?.barcodes?.any { q in it } == true else cat == HOT && p.hot || p.categoryId == cat
    }
    val stockList get() = products.filter { matches(it, filter) }
    /** 库存金额：账面 × 进价（称重按克折算） */
    val stockValue get() = products.sumOf { StockWork.lineCost(it, maxOf(0, StockWork.book(it))) }
    val current get() = product(selected) ?: (if (sub == GoodsSub.Stock) stockList else items).firstOrNull()
    val picked get() = queue.map { it.productId }.filter { it !in unpicked }
    val preview get() = product(focus) ?: product(queue.firstOrNull()?.productId) ?: current
    fun movesOf(id: String) = moves.filter { it.productId == id }.sortedByDescending { it.at }.take(10)
}

sealed interface GoodsAction {
    data class Sub(val sub: GoodsSub) : GoodsAction
    data class Cat(val id: String) : GoodsAction
    data class Search(val q: String) : GoodsAction
    data class Select(val id: String) : GoodsAction
    data class SavePrice(val id: String, val text: String) : GoodsAction
    data class ToLabels(val id: String) : GoodsAction
    data class Filter(val f: StockFilter) : GoodsAction
    data class OpenDoc(val kind: StockDocKind, val id: String) : GoodsAction
    data class TogglePick(val id: String) : GoodsAction
    data object PickAll : GoodsAction
    data class Tpl(val tpl: LabelTpl) : GoodsAction
    data class Focus(val id: String) : GoodsAction
    data class AddLabel(val raw: String) : GoodsAction
    data object Print : GoodsAction
}

/** 零售商品（原型 GoodsWB.tsx）：商品资料改价 → 待打价签 · 库存（筛选、流水、去入库 / 盘点）· 价签（队列、模板、预览、打印） */
class GoodsViewModel(config: StoreConfigRepository, private val retail: RetailRepository, private val messages: MessageBus) : ViewModel() {
    private data class Local(val sub: GoodsSub = GoodsSub.Items, val cat: String = HOT, val query: String = "", val selected: String? = null, val filter: StockFilter = StockFilter.All,
        val unpicked: Set<String> = emptySet(), val tpl: LabelTpl? = null, val focus: String? = null)
    private val local = MutableStateFlow(Local())
    private val events = Channel<Unit>(Channel.BUFFERED)
    /** 建好库存单后切到「库存」页签 */
    val toStock = events.receiveAsFlow()

    val state: StateFlow<GoodsUiState> = combine(config.current, local) { s, l ->
        val products = s?.products.orEmpty()
        GoodsUiState(l.sub, l.cat, l.query, l.selected, l.filter, l.unpicked, l.tpl ?: s?.retail?.settings?.labelTpl ?: LabelTpl.L60x40, l.focus, products,
            s?.categories.orEmpty().filter { c -> products.any { it.categoryId == c.id } }, s?.retail?.labelQueue.orEmpty(), s?.stockMovements.orEmpty(),
            s?.retail?.labelPrints?.sumOf { it.productIds.size } ?: 0, LocalDate.now(), s?.retail?.settings?.expiryDays ?: 3)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GoodsUiState())

    fun onAction(a: GoodsAction) {
        val st = state.value
        when (a) {
            is GoodsAction.Sub -> local.update { it.copy(sub = a.sub, selected = null) }
            is GoodsAction.Cat -> local.update { it.copy(cat = a.id, query = "", selected = null) }
            is GoodsAction.Search -> local.update { it.copy(query = a.q, selected = null) }
            is GoodsAction.Select -> local.update { it.copy(selected = a.id) }
            is GoodsAction.SavePrice -> {
                val fen = CashRules.amount(a.text) ?: run { messages.post("售价格式不对"); return }
                val p = st.product(a.id) ?: return
                viewModelScope.launchStorage(messages) {
                    retail.setRetailPrice(a.id, fen).onSuccess { messages.post("${p.name} 改为 ${yuan(fen)}${if (StockWork.weighed(p)) "/kg" else ""}，已加入待打价签") }.onFailure { messages.post(it.message ?: "改价失败") }
                }
            }
            is GoodsAction.ToLabels -> viewModelScope.launchStorage(messages) {
                retail.queueLabel(a.id).onSuccess { local.update { it.copy(sub = GoodsSub.Labels, focus = a.id, unpicked = it.unpicked - a.id) } }.onFailure { messages.post(it.message ?: "加入失败") }
            }
            is GoodsAction.Filter -> local.update { it.copy(filter = a.f, selected = null) }
            is GoodsAction.OpenDoc -> {
                val p = st.product(a.id) ?: return
                if (p.hasSpec) { messages.post("${p.name} 有规格，请到「库存」页按规格扫码"); return }
                viewModelScope.launchStorage(messages) {
                    val no = retail.newStockDoc(a.kind).getOrElse { messages.post(it.message ?: "新建失败"); return@launchStorage }
                    retail.setStockDocLines(no, listOf(StockDocLine(p.id, null, if (a.kind == StockDocKind.Count) StockWork.book(p) else StockWork.refill(p)))).onFailure { messages.post(it.message ?: "保存失败") }
                    messages.post("已新建${a.kind.label}单 $no"); events.send(Unit)
                }
            }
            is GoodsAction.TogglePick -> local.update { it.copy(unpicked = if (a.id in it.unpicked) it.unpicked - a.id else it.unpicked + a.id) }
            GoodsAction.PickAll -> local.update { it.copy(unpicked = if (st.picked.size == st.queue.size) st.queue.map { q -> q.productId }.toSet() else emptySet()) }
            is GoodsAction.Tpl -> local.update { it.copy(tpl = a.tpl) }
            is GoodsAction.Focus -> local.update { it.copy(focus = a.id) }
            is GoodsAction.AddLabel -> {
                val raw = a.raw.trim(); if (raw.isEmpty()) return
                val p = BarcodeRules.findByCode(st.products, raw) { it.retail?.barcodes.orEmpty() + it.skus.mapNotNull { k -> k.barcode } } ?: st.products.firstOrNull { raw in it.name }
                if (p == null) { messages.post("未找到 $raw"); return }
                onAction(GoodsAction.ToLabels(p.id))
            }
            GoodsAction.Print -> {
                val ids = st.picked
                viewModelScope.launchStorage(messages) {
                    retail.printLabels(ids, st.tpl).onSuccess { n -> messages.post("已生成 $n 张价签（${st.tpl.label}）"); local.update { it.copy(focus = null) } }.onFailure { messages.post(it.message ?: "打印失败") }
                }
            }
        }
    }
}
