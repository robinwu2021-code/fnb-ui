package ai.neargo.fnb.pos.core.model

import kotlinx.serialization.Serializable

/**
 * 业务实体。纯 Kotlin，不依赖 Android 或 UI，供 Repository、ViewModel 和单元测试共用。
 * 金额一律以「分」为单位。
 */

data class Category(val id: String, val name: String)

data class Dish(
    val id: String,
    val name: String,
    val en: String,
    val cat: String,
    val price: Int,
    /** null 不限量；0 已沽清。 */
    val stock: Int?,
    val tags: List<String> = emptyList(),
    val hasSpec: Boolean = false,
    val hot: Boolean = false,
    val image: String? = null,
) {
    val soldOut get() = stock == 0
    /** 卡片副标题：可选规格 > 口味 / 新品标签 > 门店现制。 */
    val note get() = when {
        hasSpec -> "可选规格"
        else -> tags.firstOrNull { it != "招牌" } ?: "门店现制"
    }
    val stockLabel get() = when {
        stock == null -> "有货"
        stock == 0 -> "已沽清"
        stock <= 5 -> "仅剩 $stock"
        else -> "有货"
    }
}

/** 规格组：单选。delta 为加价（分）。 */
data class SpecGroup(val name: String, val options: List<SpecOption>)
data class SpecOption(val name: String, val delta: Int = 0)

data class Area(val id: String, val name: String, val short: String)

@Serializable
enum class TableStatus(val label: String) {
    Idle("空闲"), Dining("用餐中"), Reserved("已预订"), Paid("已结账"), Cleaning("待清台"),
}

@Serializable
data class DiningTable(
    val id: String,
    val area: String,
    val seats: Int,
    val status: TableStatus,
    val guests: Int = 0,
    val minutes: Int = 0,
    val reservation: String = "",
    val paid: Int = 0,
)

/** 行状态：新加（草稿）→ 已下单 → 催菜中 → 已上菜。送厨后不可直接改数量，状态不回退。 */
@Serializable
enum class LineStatus(val label: String) { New("新加"), Sent("已下单"), Rushing("催菜中"), Served("已上菜") }

/** 订单行。同一道菜不同规格分成两行；unit 为下单时的价格快照。 */
@Serializable
data class OrderLine(
    val uid: Long,
    val dishId: String,
    val name: String,
    val spec: String,
    val unit: Int,
    val qty: Int,
    val status: LineStatus,
)

/** 一张桌台的当前订单。member 为是否绑定会员（演示 95 折）。 */
@Serializable
data class TableOrder(val lines: List<OrderLine> = emptyList(), val member: Boolean = false) {
    val pending get() = lines.filter { it.status == LineStatus.New }
    val sent get() = lines.filter { it.status != LineStatus.New }
}

/** 金额：小计、服务费 5%（演示）、优惠、应收；items 为份数，newItems 为待送厨份数。 */
data class Totals(val sub: Int, val service: Int, val discount: Int, val due: Int, val items: Int, val newItems: Int)

/** 门店会话：门店、员工终端、当前工作桌台。 */
data class Session(val store: String, val staff: String, val currentTableId: String)

/** 设备与网络状态（演示开关）。 */
data class DeviceStatus(val online: Boolean = true, val printerOk: Boolean = true)
