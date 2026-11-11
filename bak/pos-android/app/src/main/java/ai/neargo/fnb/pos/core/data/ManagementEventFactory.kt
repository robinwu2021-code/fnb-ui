package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** 单个配置实体的前后内容；新建时 before 为 null。删除需墓碑（after=null），当前页面无删除入口。 */
@Serializable
internal data class ConfigChange(val entity: String, val id: String, val before: JsonElement?, val after: JsonElement?)

/** 门店配置事件：操作的主实体 + 本次全部变化实体（如商品保存连带的餐牌关联）。不含 tableId、账号或显示偏好。 */
@Serializable
internal data class ConfigEventPayload(val entity: String, val entityId: String, val changes: List<ConfigChange>)

@Serializable
internal data class StockAdjustedPayload(val movement: StockMovement, val product: Product)

/** 交班事件：固化交班记录及账本范围（本班收款、调整记录、相关打印任务、未同步项）。 */
@Serializable
internal data class ShiftClosedPayload(
    val record: ShiftRecord,
    val paymentIds: List<String>,
    val adjustmentIds: List<String>,
    val printJobIds: List<String>,
    val pendingOutboxIds: List<String>,
    val unsyncedPaymentIds: List<String>,
)

/** 管理与账本事件的纯载荷构造：不生成ID、不读库、不校验权限。 */
internal object ManagementEventFactory {
    private val json = Json { encodeDefaults = true }

    private fun <T> diff(entity: String, ser: KSerializer<T>, before: List<T>, after: List<T>, id: (T) -> String): List<ConfigChange> {
        val old = before.associateBy(id)
        val new = after.associateBy(id)
        return (old.keys + new.keys).distinct().mapNotNull { k ->
            val b = old[k]; val a = new[k]
            if (b == a) null else ConfigChange(entity, k, b?.let { json.encodeToJsonElement(ser, it) }, a?.let { json.encodeToJsonElement(ser, it) })
        }
    }

    /** 比较门店配置实体（桌台只比较配置字段：区域、座位、坐标）。 */
    fun configChanges(before: StoreData, after: StoreData): List<ConfigChange> {
        fun cfg(t: DiningTable) = DiningTable(t.id, t.areaId, t.seats, TableStatus.Idle, x = t.x, y = t.y)
        return buildList {
            addAll(diff("area", Area.serializer(), before.areas, after.areas) { it.id })
            addAll(diff("table", DiningTable.serializer(), before.tables.map(::cfg), after.tables.map(::cfg)) { it.id })
            addAll(diff("category", Category.serializer(), before.categories, after.categories) { it.id })
            addAll(diff("specGroup", SpecGroup.serializer(), before.specGroups, after.specGroups) { it.id })
            addAll(diff("product", Product.serializer(), before.products, after.products) { it.id })
            addAll(diff("menu", Menu.serializer(), before.menus, after.menus) { it.id })
            if (before.settings != after.settings) add(ConfigChange("settings", after.id,
                json.encodeToJsonElement(StoreSettings.serializer(), before.settings), json.encodeToJsonElement(StoreSettings.serializer(), after.settings)))
        }
    }

    fun config(entity: String, entityId: String, before: StoreData, after: StoreData): String {
        val changes = configChanges(before, after)
        // 主实体可以不变（如商品只改餐牌关联），但本次必须有配置变化
        require(changes.isNotEmpty()) { "配置事件缺少变化" }
        return json.encodeToString(ConfigEventPayload.serializer(), ConfigEventPayload(entity, entityId, changes))
    }

    fun stock(before: StoreData, after: StoreData): String {
        val m = requireNotNull(after.stockMovements.lastOrNull()?.takeIf { mv -> before.stockMovements.none { it.id == mv.id } }) { "库存事件缺少流水" }
        val p = after.products.first { it.id == m.productId }
        return json.encodeToString(StockAdjustedPayload.serializer(), StockAdjustedPayload(m, p))
    }

    fun shift(before: StoreData, after: StoreData): String {
        val rec = requireNotNull(after.shift.history.lastOrNull()?.takeIf { r -> before.shift.history.none { it.id == r.id } }) { "交班事件缺少记录" }
        val pays = before.payments.filter { it.at >= rec.start && it.at <= rec.end }
        return json.encodeToString(ShiftClosedPayload.serializer(), ShiftClosedPayload(
            record = rec,
            paymentIds = pays.map { it.id },
            adjustmentIds = before.adjustments.filter { it.at >= rec.start && it.at <= rec.end }.map { it.id },
            printJobIds = after.printJobs.filter { j -> before.printJobs.none { it.id == j.id } }.map { it.id },
            pendingOutboxIds = before.outbox.filter { it.state == OutboxState.Pending }.map { it.id },
            unsyncedPaymentIds = pays.filter { !it.synced }.map { it.id },
        ))
    }
}
