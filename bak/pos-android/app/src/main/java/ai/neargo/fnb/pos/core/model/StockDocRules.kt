package ai.neargo.fnb.pos.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 零售库存作业规则（移植自原型 domain/stock.ts）：入库 · 扣减 · 盘点差异与过账 · 报损 · 临期 · 低库存。
 * 数量用 Long：按件商品是件数，称重商品是克。
 */
object StockDocRules {
    data class DocLine(val id: String, val qty: Long)
    data class DiffRow(val id: String, val book: Long, val counted: Long) { val diff get() = counted - book }
    data class CountDiff(val rows: List<DiffRow>, val gain: Long, val loss: Long)

    fun receive(book: Map<String, Long>, lines: List<DocLine>): Map<String, Long> {
        val out = LinkedHashMap(book); lines.forEach { out[it.id] = (out[it.id] ?: 0) + it.qty }; return out
    }

    fun deduct(book: Map<String, Long>, id: String, qty: Long): Map<String, Long> = LinkedHashMap(book).also { it[id] = (book[id] ?: 0) - qty }

    /** 盘点差异：实盘 − 账面，只列有差异的；账面有、实盘没录的按 0 算 */
    fun countDiff(book: Map<String, Long>, counted: Map<String, Long>): CountDiff {
        val ids = LinkedHashSet<String>().apply { addAll(book.keys); addAll(counted.keys) }
        val rows = ids.map { DiffRow(it, book[it] ?: 0, counted[it] ?: 0) }.filter { it.diff != 0L }
        return CountDiff(rows, rows.filter { it.diff > 0 }.sumOf { it.diff }, rows.filter { it.diff < 0 }.sumOf { -it.diff })
    }

    /** 盘点过账：实盘覆盖账面（只改盘到的） */
    fun applyCount(book: Map<String, Long>, counted: Map<String, Long>): Map<String, Long> = LinkedHashMap(book).apply { putAll(counted) }

    fun writeOff(book: Map<String, Long>, lines: List<DocLine>): Map<String, Long> = lines.fold(book) { b, l -> deduct(b, l.id, l.qty) }

    /** 按变化量改库存（销售为负、退货回库为正） */
    fun applyDelta(book: Map<String, Long>, delta: Map<String, Long>): Map<String, Long> = LinkedHashMap(book).apply { delta.forEach { (id, d) -> this[id] = (this[id] ?: 0) + d } }

    /** 临期：距到期 ≤ days 天（含当天），已过期也算 */
    fun nearExpiry(expiry: LocalDate, today: LocalDate, days: Long = 3) = ChronoUnit.DAYS.between(today, expiry) <= days

    fun lowStock(left: Long, safety: Long) = left <= safety
}
