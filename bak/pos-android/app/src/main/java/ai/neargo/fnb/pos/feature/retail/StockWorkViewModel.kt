package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.RetailRepository
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.model.BarcodeRules
import ai.neargo.fnb.pos.core.model.LossReason
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.StockDoc
import ai.neargo.fnb.pos.core.model.StockDocKind
import ai.neargo.fnb.pos.core.model.StockDocLine
import ai.neargo.fnb.pos.core.model.StockDocState
import ai.neargo.fnb.pos.core.model.StockWork
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** 左栏：三种作业 + 调拨（未实现）+ 两种预警 */
enum class StockView(val label: String) { Receive("入库"), Count("盘点"), Loss("报损"), Transfer("调拨"), Low("低库存"), Near("临期") }
enum class DocFilter(val label: String) { All("全部"), Open("进行中"), Done("已完成") }

val StockView.kind get() = when (this) { StockView.Receive -> StockDocKind.Receive; StockView.Count -> StockDocKind.Count; StockView.Loss -> StockDocKind.Loss; StockView.Transfer -> StockDocKind.Transfer; else -> null }
val StockDoc.open get() = state in setOf(StockDocState.Draft, StockDocState.Counting, StockDocState.Pending, StockDocState.InTransit)

data class StockWorkUiState(
    val view: StockView = StockView.Receive,
    val filter: DocFilter = DocFilter.All,
    val selected: String? = null,
    val docs: List<StockDoc> = emptyList(),
    val products: List<Product> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val expiryDays: Int = 3,
    /** 新建单据弹窗：供应商 / 盘点范围 */
    val creating: StockDocKind? = null,
    val busy: Boolean = false,
    /** 账号下其他零售门店（调拨目标、来源名称） */
    val stores: List<ai.neargo.fnb.pos.core.model.StoreSummary> = emptyList(),
    val storeId: String = "",
) {
    fun storeName(id: String) = stores.firstOrNull { it.id == id }?.name ?: id
    val transferTargets get() = stores.filter { it.biz == ai.neargo.fnb.pos.core.model.BizType.Retail && it.id != storeId }
    fun product(id: String) = products.firstOrNull { it.id == id }
    val shown get() = docs.filter { it.kind == view.kind && when (filter) { DocFilter.All -> true; DocFilter.Open -> it.open; DocFilter.Done -> !it.open } }
    val current get() = docs.firstOrNull { it.no == selected && it.kind == view.kind } ?: shown.firstOrNull()
    fun openCount(k: StockDocKind) = docs.count { it.kind == k && it.open }
    val low get() = products.filter { StockWork.low(it) }
    val near get() = products.filter { StockWork.near(it, today, expiryDays) }

    /** 单据右侧的建议：入库建议低库存、报损建议临期、盘点建议同分类还没盘的 */
    fun suggest(d: StockDoc): List<Product> {
        if (!d.editable) return emptyList()
        val has = d.lines.map { it.productId }.toSet()
        return when (d.kind) {
            StockDocKind.Receive -> low
            StockDocKind.Loss -> near
            StockDocKind.Count -> products.filter { !it.hasSpec }
            StockDocKind.Transfer -> products.filter { !it.hasSpec && StockWork.book(it) > 0 }
        }.filter { it.id !in has }.take(if (d.kind == StockDocKind.Count) 8 else 6)
    }
}

sealed interface StockWorkAction {
    data class View(val v: StockView) : StockWorkAction
    data class Filter(val f: DocFilter) : StockWorkAction
    data class Select(val no: String) : StockWorkAction
    data object AskNew : StockWorkAction
    data class Create(val text: String) : StockWorkAction
    data object CancelNew : StockWorkAction
    data class Scan(val raw: String) : StockWorkAction
    data class Add(val productId: String) : StockWorkAction
    data class Step(val line: StockDocLine, val dir: Int) : StockWorkAction
    data class Reason(val line: StockDocLine, val reason: LossReason) : StockWorkAction
    data object Advance : StockWorkAction
    data object Void : StockWorkAction
    /** 预警页：低库存一键生成入库单 / 临期一键生成报损单 */
    data object RefillLow : StockWorkAction
    data object WriteOffNear : StockWorkAction
}

/** 库存作业（原型 StockWB.tsx）：入库、盘点（提交 → 审批过账）、报损、预警；调拨未实现 */
class StockWorkViewModel(config: StoreConfigRepository, private val retail: RetailRepository, private val messages: MessageBus, session: ai.neargo.fnb.pos.core.data.SessionRepository? = null) : ViewModel() {
    private data class Local(val view: StockView = StockView.Receive, val filter: DocFilter = DocFilter.All, val selected: String? = null, val creating: StockDocKind? = null, val busy: Boolean = false)
    private val local = MutableStateFlow(Local())

    val state: StateFlow<StockWorkUiState> = combine(config.current, local, session?.stores ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())) { s, l, stores ->
        StockWorkUiState(l.view, l.filter, l.selected, s?.retail?.stockDocs.orEmpty(), s?.products.orEmpty(), LocalDate.now(), s?.retail?.settings?.expiryDays ?: 3, l.creating, l.busy, stores, s?.id.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StockWorkUiState())

    private fun cur() = state.value.current

    /** 改当前单据的行（整单写回） */
    private fun edit(f: (List<StockDocLine>) -> List<StockDocLine>) {
        val d = cur() ?: return
        if (!d.editable) { messages.post("单据已${d.state.label}，不能再改"); return }
        viewModelScope.launchStorage(messages) { retail.setStockDocLines(d.no, f(d.lines)).onFailure { messages.post(it.message ?: "保存失败") } }
    }

    private fun addProduct(p: Product, sku: String?, qty: Long? = null) {
        val d = cur() ?: run { messages.post("先新建一张单据"); return }
        if (p.hasSpec && sku == null) { messages.post("${p.name} 有规格，请扫规格条码"); return }
        val q = qty ?: when (d.kind) { StockDocKind.Receive -> if (StockWork.weighed(p)) 1000L else StockWork.refill(p).coerceAtMost(12); else -> StockWork.step(p) }
        edit { StockWork.add(it, p.id, sku, q, if (d.kind == StockDocKind.Loss) LossReason.Damaged else null) }
    }

    fun onAction(a: StockWorkAction) {
        val st = state.value
        when (a) {
            is StockWorkAction.View -> local.update { it.copy(view = a.v, selected = null) }
            is StockWorkAction.Filter -> local.update { it.copy(filter = a.f) }
            is StockWorkAction.Select -> local.update { it.copy(selected = a.no) }
            StockWorkAction.AskNew -> st.view.kind?.let { k -> local.update { it.copy(creating = k) } }
            StockWorkAction.CancelNew -> local.update { it.copy(creating = null) }
            is StockWorkAction.Create -> {
                val k = st.creating ?: return
                viewModelScope.launchStorage(messages) {
                    retail.newStockDoc(k, supplier = if (k == StockDocKind.Receive) a.text else "", scope = if (k == StockDocKind.Count) a.text else "", toStore = if (k == StockDocKind.Transfer) a.text else "")
                        .onSuccess { no -> local.update { it.copy(creating = null, selected = no, filter = DocFilter.All) }; messages.post("已新建${k.label}单 $no") }
                        .onFailure { messages.post(it.message ?: "新建失败") }
                }
            }
            is StockWorkAction.Scan -> {
                val raw = a.raw.trim()
                st.products.firstNotNullOfOrNull { p -> p.skus.firstOrNull { it.barcode == raw }?.let { p to it.key } }?.let { (p, k) -> addProduct(p, k); return }
                BarcodeRules.parseWeigh(raw)?.let { w ->
                    st.products.firstOrNull { StockWork.weighed(it) && it.retail!!.barcodes.any { b -> b.filter(Char::isDigit).toIntOrNull() == w.plu.toInt() } }?.let { addProduct(it, null, w.grams.toLong()); return }
                }
                BarcodeRules.findByCode(st.products, raw) { it.retail?.barcodes.orEmpty() }?.let { addProduct(it, null); return }
                messages.post("未找到条码 $raw")
            }
            is StockWorkAction.Add -> st.product(a.productId)?.let { addProduct(it, null) }
            is StockWorkAction.Step -> {
                val d = cur() ?: return; val p = st.product(a.line.productId) ?: return
                edit { StockWork.setQty(it, a.line.productId, a.line.sku, a.line.qty + a.dir * StockWork.step(p), keepZero = d.kind == StockDocKind.Count) }
            }
            is StockWorkAction.Reason -> edit { ls -> ls.map { if (it.productId == a.line.productId && it.sku == a.line.sku) it.copy(reason = a.reason) else it } }
            StockWorkAction.Advance -> {
                val d = cur() ?: return
                if (st.busy) return
                local.update { it.copy(busy = true) }
                viewModelScope.launchStorage(messages) {
                    try {
                        retail.advanceStockDoc(d.no).onSuccess { s ->
                            messages.post(when (s) { StockDocState.Pending -> "${d.no} 已提交，等主管审批"; StockDocState.Variance -> "${d.no} 已过账，有盘点差异"; else -> "${d.no} 已过账，库存已更新" })
                        }.onFailure { messages.post(it.message ?: "过账失败") }
                    } finally { local.update { it.copy(busy = false) } }
                }
            }
            StockWorkAction.Void -> { val d = cur() ?: return; viewModelScope.launchStorage(messages) { retail.voidStockDoc(d.no).onSuccess { messages.post("${d.no} 已作废") }.onFailure { messages.post(it.message ?: "作废失败") } } }
            StockWorkAction.RefillLow -> makeDoc(StockDocKind.Receive, "补货", st.low.map { StockDocLine(it.id, null, StockWork.refill(it)) })
            StockWorkAction.WriteOffNear -> makeDoc(StockDocKind.Loss, "", st.near.filter { StockWork.book(it) > 0 }.map { StockDocLine(it.id, null, StockWork.book(it), LossReason.Expired) })
        }
    }

    /** 预警一键建单：新建 → 写行 → 切到那张单 */
    private fun makeDoc(kind: StockDocKind, supplier: String, lines: List<StockDocLine>) {
        if (lines.isEmpty()) { messages.post("没有需要处理的商品"); return }
        viewModelScope.launchStorage(messages) {
            val no = retail.newStockDoc(kind, supplier = supplier).getOrElse { messages.post(it.message ?: "新建失败"); return@launchStorage }
            retail.setStockDocLines(no, lines).onFailure { messages.post(it.message ?: "保存失败") }
            local.update { it.copy(view = if (kind == StockDocKind.Receive) StockView.Receive else StockView.Loss, selected = no, filter = DocFilter.All) }
            messages.post("已生成${kind.label}单 $no · ${lines.size} 个商品")
        }
    }
}
