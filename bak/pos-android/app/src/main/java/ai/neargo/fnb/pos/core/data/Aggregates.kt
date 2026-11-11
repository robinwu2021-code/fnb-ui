package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.StoreData

/**
 * 聚合划分（N2-02）：Order、TableSession(按桌台实体)、Reservation、Catalog、StockLedger(按商品)、Shift。
 * 一个命令可参与多个聚合（换桌/并台/拆单/到店开台），在同一门店事务中全部校验版本，任一冲突整笔拒绝。
 */
internal object Aggregates {
    const val CATALOG = "catalog"
    const val SHIFT = "shift"
    /** 旧单流（v1 信封）：只读，用于迁移水位。 */
    const val LEGACY_STREAM = "page-order-stream"

    fun page(tableId: String, before: StoreData, after: StoreData): List<String> {
        val ids = linkedSetOf<String>()
        val changedTables = (before.tables.map { it.id } + after.tables.map { it.id }).distinct().filter { id ->
            before.tables.firstOrNull { it.id == id } != after.tables.firstOrNull { it.id == id } || before.orders[id] != after.orders[id]
        }
        // 主聚合优先：目标桌的订单，其次桌台
        for (id in listOf(tableId) + changedTables.filter { it != tableId }) {
            val b = before.orders[id]; val a = after.orders[id]
            if (b != a) listOfNotNull(b?.orderId, a?.orderId).filter { it.isNotBlank() }.forEach { ids += "order:$it" }
            if (before.tables.firstOrNull { it.id == id } != after.tables.firstOrNull { it.id == id } || id == tableId) ids += "table:$id"
        }
        after.reservations.filter { r -> before.reservations.none { it == r } }.forEach { ids += "reservation:${it.id}" }
        return ids.toList()
    }
}
