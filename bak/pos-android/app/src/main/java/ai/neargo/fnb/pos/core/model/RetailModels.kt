package ai.neargo.fnb.pos.core.model

import kotlinx.serialization.Serializable

/*
 * 零售业务数据（门店业态 = 零售）。字段全部带默认值，旧数据和餐饮门店读出为空。
 * 规则在 RetailCartRules / RetailPricing / BarcodeRules / ReturnRules / StockDocRules。
 * 数量：按件商品是件数；称重商品库存与单据数量用克。金额整数分。
 */

/** 商品的零售字段 */
@Serializable
data class RetailInfo(
    /** 条码（可多个）：标准码、店内码、PLU */
    val barcodes: List<String> = emptyList(),
    /** 计价单位：件 / kg（称重商品单价按 kg） */
    val unit: String = "件",
    val weighable: Boolean = false,
    /** 进价（分）；称重商品按 kg */
    val costPrice: Long? = null,
    /** 会员价（分）；只在低于售价时生效 */
    val memberPrice: Long? = null,
    val promo: LinePromo? = null,
    /** 到期日 yyyy-MM-dd（临期预警） */
    val expiry: String? = null,
    /** 安全库存（件或克） */
    val safetyStock: Int? = null,
)

@Serializable enum class SaleChannel(val label: String) { Store("门店"), Mini("小程序自提"), Platform("即时零售平台") }

/** 零售单的一行：amount 是这一行的实付小计（促销、会员价后），退货再按整单优惠分摊 */
@Serializable
data class RetailSaleLine(
    val key: String, val productId: String, val name: String, val sku: String? = null,
    val qty: Int, val grams: Int? = null, val unit: Long, val amount: Long, val returned: Int = 0,
)

@Serializable data class SalePay(val method: String, val amount: Long)

/** 退货 / 换货记录 */
@Serializable
data class SaleReturn(val at: Long, val amount: Long, val pieces: Int, val reason: ReturnRules.Reason, val method: String, val exchange: Boolean = false)

@Serializable
data class RetailSale(
    val no: String, val at: Long, val channel: SaleChannel = SaleChannel.Store, val member: String? = null,
    val lines: List<RetailSaleLine>, /** 整单优惠（券、抹零等），退货时按行分摊 */ val discount: Long = 0,
    val pays: List<SalePay>, val returns: List<SaleReturn> = emptyList(),
) {
    val soldLines get() = lines.map { ReturnRules.SoldLine(it.key, it.qty, it.amount, it.returned, it.grams != null) }
    val status get() = ReturnRules.status(soldLines)
    val paid get() = pays.sumOf { it.amount }
    val pieces get() = lines.sumOf { if (it.grams != null) 1 else it.qty }
}

/** 挂起的购物车 */
@Serializable data class RetailHeld(val no: String, val at: Long, val lines: List<CartLine>, val member: String? = null, val why: String = "")

@Serializable enum class StockDocKind(val label: String, val prefix: String) { Receive("入库", "RK"), Count("盘点", "PD"), Loss("报损", "BS") }

/** 单据状态：入库 / 报损 草稿 → 已过账；盘点 盘点中 → 待审批 → 已完成 / 有差异；草稿可作废 */
@Serializable enum class StockDocState(val label: String) { Draft("草稿"), Counting("盘点中"), Pending("待审批"), Posted("已过账"), Variance("有差异"), Void("已作废") }

@Serializable enum class LossReason(val label: String) { Expired("过期"), Damaged("破损"), Return("退货不回库") }

@Serializable data class StockDocLine(val productId: String, val sku: String? = null, val qty: Long, val reason: LossReason? = null)

@Serializable
data class StockDoc(
    val no: String, val kind: StockDocKind, val state: StockDocState, val at: Long, val actor: String,
    val lines: List<StockDocLine> = emptyList(), val supplier: String = "", val scope: String = "", val ref: String = "",
    /** 盘点提交时冻结的账面快照：之后卖出不影响这张单的差异 */
    val snapshot: Map<String, Long> = emptyMap(), val gain: Long = 0, val loss: Long = 0,
) {
    val editable get() = state == StockDocState.Draft || state == StockDocState.Counting
}

@Serializable enum class LabelWhy(val label: String) { Price("改价"), New("新品"), Manual("手动加入") }
@Serializable data class LabelJob(val productId: String, val why: LabelWhy, val oldPrice: Long? = null)

/** 门店的全部零售数据 */
@Serializable
data class RetailData(
    val sales: List<RetailSale> = emptyList(),
    val held: List<RetailHeld> = emptyList(),
    val stockDocs: List<StockDoc> = emptyList(),
    val labelQueue: List<LabelJob> = emptyList(),
    val saleSeq: Int = 1,
    val docSeq: Int = 1,
)
