package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** 现有页面事件的兼容载荷v1；管理/设备事件应使用各自载荷，不伪造tableId。 */
@Serializable
internal data class BusinessEventPayload(
    val tableId: String,
    val table: DiningTable,
    val order: TableOrder,
    val affectedTables: List<AffectedTableSnapshot>,
    val addedHeldTickets: List<HeldTicket>,
    val removedHeldTicketIds: List<String>,
    val addedPrintJobs: List<PrintJob>,
    val addedOutboxItems: List<OutboxItem>,
    val payment: PaymentRecord? = null,
    val audit: AdjustmentAudit? = null,
)

@Serializable
internal data class AffectedTableSnapshot(val table: DiningTable, val order: TableOrder)

/** 纯载荷构造：不生成ID、不读数据库、不校验权限、不执行打印或收款。 */
internal object BusinessEventFactory {
    private val json = Json { encodeDefaults = true }
    private val adjustmentTypes = setOf("DishVoided", "DishGifted", "OrderDiscounted")

    fun build(type: String, tableId: String, before: StoreData, after: StoreData): BusinessEventPayload {
        require(before.id == after.id) { "事件前后门店不一致" }
        val table = requireNotNull(after.tables.firstOrNull { it.id == tableId }) { "事件目标桌台不存在" }
        return BusinessEventPayload(
            tableId = tableId,
            table = table,
            order = after.orders[tableId] ?: TableOrder(),
            affectedTables = after.tables.filter { changed ->
                before.tables.firstOrNull { it.id == changed.id } != changed || before.orders[changed.id] != after.orders[changed.id]
            }.map { AffectedTableSnapshot(it, after.orders[it.id] ?: TableOrder()) },
            addedHeldTickets = after.held.filter { ticket -> before.held.none { it.id == ticket.id } },
            removedHeldTicketIds = before.held.filter { ticket -> after.held.none { it.id == ticket.id } }.map { it.id },
            addedPrintJobs = after.printJobs.filter { job -> before.printJobs.none { it.id == job.id } },
            addedOutboxItems = after.outbox.filter { item -> before.outbox.none { it.id == item.id } },
            payment = if (type == "PaymentRecorded") after.payments.last { it.tableId == tableId } else null,
            audit = if (type in adjustmentTypes) after.adjustments.last { it.tableId == tableId } else null,
        )
    }

    fun encode(payload: BusinessEventPayload): String {
        val fields = json.encodeToJsonElement(BusinessEventPayload.serializer(), payload).jsonObject
        // 只省略兼容载荷顶层的可选证据，保留订单内部null及默认字段。
        return JsonObject(fields.filterNot { (key, value) -> key in setOf("payment", "audit") && value == JsonNull }).toString()
    }
}
