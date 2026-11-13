package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.LooseReturn
import ai.neargo.fnb.pos.core.model.RetailSale
import ai.neargo.fnb.pos.core.model.SaleReturn
import ai.neargo.fnb.pos.core.model.StockDoc
import ai.neargo.fnb.pos.core.model.StoreData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/**
 * 零售事件载荷（云端回放用）：事件要带齐当时的业务事实，回放时不能再读当前快照。
 * 旧字段（no / amount / method / sale / kind / state …）保留，新增完整对象：
 * - 销售：整张 RetailSale（行、数量 / 克重、单价、行小计、整单优惠、各笔收款、会员）。
 * - 退货：本次退货记录、退回的行与数量；有单退记原单号，无单退记 NR 号。
 * - 库存单：整张 StockDoc（行、原因、盘点快照、调拨两端）。
 */
internal object RetailEventPayloads {
    private val json = Json { encodeDefaults = true }

    /** 退回的一行：key = 原单行号（无单退为 L 序号）；qty 件数，称重行为整行（grams 为原克重） */
    @Serializable data class ReturnedLine(val key: String, val productId: String, val name: String, val sku: String? = null, val qty: Int, val grams: Int? = null, val unit: Long)

    private fun obj(vararg pairs: Pair<String, JsonElement?>) = JsonObject(pairs.filter { it.second != null }.associate { it.first to it.second!! }).toString()
    private fun s(v: String) = JsonPrimitive(v)

    /** 本次新增的销售（收款后排在最前） */
    fun sale(b: StoreData, a: StoreData, method: String): String {
        val x = a.retail.sales.first { n -> b.retail.sales.none { it.no == n.no } }
        return obj("no" to s(x.no), "amount" to JsonPrimitive(x.paid), "method" to s(method), "sale" to json.encodeToJsonElement(RetailSale.serializer(), x))
    }

    /** 本次退货：有单退比对原单各行已退数量；无单退取新增的 LooseReturn */
    fun returned(b: StoreData, a: StoreData, saleNo: String?, exchange: Boolean, method: String, restock: Boolean): String {
        val (ref, record, lines) = if (saleNo != null) {
            val before = b.retail.sales.first { it.no == saleNo }; val after = a.retail.sales.first { it.no == saleNo }
            val r: SaleReturn = after.returns.last()
            val back = after.lines.mapNotNull { l ->
                val n = l.returned - (before.lines.firstOrNull { it.key == l.key }?.returned ?: 0)
                if (n > 0) ReturnedLine(l.key, l.productId, l.name, l.sku, n, l.grams, l.unit) else null
            }
            Triple(saleNo, r, back)
        } else {
            val lr: LooseReturn = a.retail.looseReturns.first { n -> b.retail.looseReturns.none { it.no == n.no } }
            Triple(lr.no, SaleReturn(lr.at, lr.amount, lr.lines.sumOf { if (it.grams != null) 1 else it.qty }, lr.reason, lr.method), lr.lines.map { ReturnedLine(it.key, it.productId, it.name, it.sku, it.qty, it.grams, it.unit) })
        }
        return obj("sale" to s(saleNo.orEmpty()), "ref" to s(ref), "exchange" to JsonPrimitive(exchange), "method" to s(method), "restock" to JsonPrimitive(restock),
            "return" to json.encodeToJsonElement(SaleReturn.serializer(), record),
            "lines" to json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(ReturnedLine.serializer()), lines))
    }

    /** 库存单：no 已知时取该单，否则取本次新建的单 */
    fun stockDoc(b: StoreData, a: StoreData, no: String?, extra: Map<String, JsonElement> = emptyMap()): String {
        val d: StockDoc = (no?.let { n -> a.retail.stockDocs.firstOrNull { it.no == n } }
            ?: a.retail.stockDocs.firstOrNull { n -> b.retail.stockDocs.none { it.no == n.no } })
            ?: return JsonObject(extra + ("no" to s(no.orEmpty()))).toString()
        return JsonObject(extra + mapOf("no" to s(d.no), "kind" to s(d.kind.name), "state" to s(d.state.name), "doc" to json.encodeToJsonElement(StockDoc.serializer(), d))).toString()
    }
}
