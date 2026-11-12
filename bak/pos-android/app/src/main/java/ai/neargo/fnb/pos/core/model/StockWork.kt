package ai.neargo.fnb.pos.core.model

import java.time.LocalDate

/**
 * 零售库存作业的界面规则（原型 retail/stock.ts）：账面、成本、低库存 / 临期预警、单据加行合并。
 * 数量：按件商品是件，称重商品是克；成本：按件是每件进价，称重是每 kg 进价。
 */
object StockWork {
    fun weighed(p: Product) = p.retail?.weighable == true

    /** 账面：有规格时取某一格；不指定规格取合计 */
    fun book(p: Product, sku: String? = null): Long = (if (sku != null) p.skus.firstOrNull { it.key == sku }?.stock else p.totalStock)?.toLong() ?: 0

    /** 单位进价（分）：没填进价按 0 */
    fun cost(p: Product) = p.retail?.costPrice ?: 0

    /** 一行的成本金额（称重按克折算，四舍五入到分） */
    fun lineCost(p: Product, qty: Long): Long = if (weighed(p)) Money.roundHalfUp(cost(p) * qty, 1000) else cost(p) * qty

    fun docCost(products: List<Product>, d: StockDoc): Long {
        val byId = products.associateBy { it.id }
        return d.lines.sumOf { l -> byId[l.productId]?.let { lineCost(it, l.qty) } ?: 0 }
    }

    /** 数量显示：称重 1.250 kg；按件 12 件 */
    fun qtyText(p: Product?, qty: Long): String = if (p != null && weighed(p)) "%d.%03d kg".format(qty / 1000, qty % 1000) else "$qty ${p?.retail?.unit ?: "件"}"

    /** 加减一步：称重 0.5 kg，按件 1 件 */
    fun step(p: Product) = if (weighed(p)) 500L else 1L

    /** 低库存：有安全库存且账面 ≤ 安全库存 */
    fun low(p: Product) = p.retail?.safetyStock?.let { StockDocRules.lowStock(book(p), it.toLong()) } == true

    /** 临期：有到期日且 3 天内到期（含已过期） */
    fun near(p: Product, today: LocalDate, days: Int = 3) = p.retail?.expiry?.let { runCatching { StockDocRules.nearExpiry(LocalDate.parse(it), today, days.toLong()) }.getOrDefault(false) } == true

    /** 建议补货量：补到安全库存的两倍，至少一箱 12 件 */
    fun refill(p: Product): Long = maxOf(12L, (p.retail?.safetyStock ?: 0) * 2L - book(p))

    /** 往单据加商品：同商品同规格合并数量；盘点单再扫同一件是「数到一件」+1 */
    fun add(lines: List<StockDocLine>, pid: String, sku: String?, qty: Long, reason: LossReason? = null): List<StockDocLine> {
        val i = lines.indexOfFirst { it.productId == pid && it.sku == sku }
        return if (i < 0) lines + StockDocLine(pid, sku, qty, reason) else lines.mapIndexed { j, l -> if (j == i) l.copy(qty = l.qty + qty) else l }
    }

    /** 改数量；入库 / 报损减到 0 就删行，盘点单允许 0（数过、确实没有） */
    fun setQty(lines: List<StockDocLine>, pid: String, sku: String?, qty: Long, keepZero: Boolean) =
        if (qty <= 0 && !keepZero) lines.filterNot { it.productId == pid && it.sku == sku }
        else lines.map { if (it.productId == pid && it.sku == sku) it.copy(qty = maxOf(0, qty)) else it }

    /** 盘点差异（提交前按当前账面，提交后按冻结的快照） */
    fun countDiff(products: List<Product>, d: StockDoc): StockDocRules.CountDiff {
        val byId = products.associateBy { it.id }
        fun key(l: StockDocLine) = l.productId + (l.sku?.let { "#$it" } ?: "")
        val book = if (d.snapshot.isNotEmpty()) d.snapshot else d.lines.associate { key(it) to (byId[it.productId]?.let { p -> book(p, it.sku) } ?: 0) }
        return StockDocRules.countDiff(book, d.lines.associate { key(it) to it.qty })
    }
}
