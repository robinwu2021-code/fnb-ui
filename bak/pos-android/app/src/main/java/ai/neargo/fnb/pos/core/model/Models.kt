package ai.neargo.fnb.pos.core.model

import kotlinx.serialization.Serializable

/**
 * 业务实体。纯 Kotlin，不依赖 Android 或 UI。金额一律以「分」为单位，关联一律用稳定 ID，不用显示文案。
 * 全部数据按门店隔离（StoreData），离线时完整保存在本机。
 */

@Serializable data class Area(val id: String, val name: String, val en: String = "")

@Serializable data class Category(val id: String, val name: String, val en: String = "")

/** 规格选项：delta 为加价（分）。 */
@Serializable data class SpecOption(val id: String, val name: String, val delta: Long = 0)

/** 规格组：单选。 */
@Serializable data class SpecGroup(val id: String, val name: String, val en: String = "", val options: List<SpecOption>)

/**
 * SKU：规格组合。key 由排序后的「组 ID:选项 ID」拼成，改名不影响关联。
 * manualPrice / manualStock 记录是否手动调整，调整基础价或规格时保留手动值。
 */
@Serializable
data class Sku(
    val key: String,
    val optionIds: List<String>,
    val label: String,
    val price: Long,
    val stock: Int,
    val manualPrice: Boolean = false,
    val manualStock: Boolean = false,
)

@Serializable
data class Product(
    val id: String,
    val name: String,
    val en: String = "",
    val categoryId: String,
    /** 基础售价（分）。 */
    val price: Long,
    /** 无 SKU 时的可售份数；null 不限量；0 沽清。有 SKU 时以 SKU 库存为准。 */
    val stock: Int? = null,
    val specGroupIds: List<String> = emptyList(),
    val skus: List<Sku> = emptyList(),
    /** 远程 URL 或本机文件 URI。 */
    val image: String? = null,
    val tags: List<String> = emptyList(),
    val hot: Boolean = false,
    val mealGroups: List<MealGroup> = emptyList(),
    /** 离线搜索别名：空格或逗号分隔的全拼、首字母。 */
    val searchAliases: String = "",
) {
    val hasSpec get() = skus.isNotEmpty()
    /** 展示价：有 SKU 取最低 SKU 价。 */
    val displayPrice get() = (skus.minOfOrNull { it.price } ?: price) + mealGroups.sumOf { group -> group.choices.minOfOrNull { it.delta } ?: 0 }
    val totalStock: Int? get() = if (hasSpec) skus.sumOf { it.stock } else stock
    val soldOut get() = totalStock == 0
    val note get() = when {
        mealGroups.isNotEmpty() -> "套餐可换菜"
        hasSpec -> "可选规格"
        else -> tags.firstOrNull { it != "招牌" } ?: "门店现制"
    }
    val stockLabel get() = totalStock.let { s ->
        when {
            s == null -> "有货"
            s == 0 -> "已沽清"
            s <= 5 -> "仅剩 $s"
            else -> "有货"
        }
    }
}

/** 餐牌：只有已发布餐牌中的商品进入点餐；多个已发布餐牌取并集；空餐牌不可发布。 */
@Serializable data class Menu(val id: String, val name: String, val en: String = "", val productIds: List<String>, val published: Boolean)

@Serializable
enum class TableStatus(val label: String) {
    Idle("空闲"), Dining("用餐中"), Reserved("已预订"), Paid("已结账"), Cleaning("待清台"),
}

@Serializable
data class DiningTable(
    val id: String,
    val areaId: String,
    val seats: Int,
    val status: TableStatus,
    val guests: Int = 0,
    /** 开台时间（毫秒），用餐时长由当前时间计算。 */
    val openedAt: Long = 0,
    val reservation: String = "",
    val paid: Long = 0,
    /** 平面位置（画布单位 900×460，卡片 140×112）；-1 表示按区域内顺序自动排布。 */
    val x: Int = -1,
    val y: Int = -1,
    /** 桌台会话（本次营业占用）稳定ID；空闲时为空。桌号只是位置。 */
    val sessionId: String = "",
) {
    fun minutes(now: Long) = if (status == TableStatus.Dining && openedAt > 0) ((now - openedAt) / 60_000).toInt().coerceAtLeast(0) else 0
}

/**
 * 行状态：新加（草稿）→ 待同步（离线送厨已落盘）→ 已下单 → 催菜中 → 已上菜。
 * 送厨后不可直接改数量，状态只进不退。
 */
@Serializable
enum class LineStatus(val label: String) { New("新加"), Queued("待同步"), Sent("已下单"), Rushing("催菜中"), Served("已上菜") }

/** 订单行：商品、规格、SKU 与单价均为下单时快照；batchId 为送厨批次（幂等键）。 */
@Serializable
data class OrderLine(
    val uid: Long,
    val dishId: String,
    val name: String,
    val spec: String,
    val unit: Long,
    val qty: Int,
    val status: LineStatus,
    val skuKey: String? = null,
    val batchId: String? = null,
    /** 菜品备注（少辣、不要葱…）；同菜同 SKU 同备注才合并。 */
    val note: String = "",
    val gifted: Boolean = false,
    val mealSelections: List<MealSelection> = emptyList(),
    /** 下单（送厨）时间，出菜看板计算等待时长；0 表示未下单或旧数据。 */
    val sentAt: Long = 0,
)

@Serializable
data class TableOrder(
    val lines: List<OrderLine> = emptyList(), val member: Boolean = false, val note: String = "", val discountPercent: Int = 100, val coupon: Boolean = false, val activity: Boolean = false,
    /** 订单稳定ID：开台生成，换桌保留；空表示无活动订单。 */
    val orderId: String = "",
    /** 并入本单的来源订单ID。 */
    val mergedOrderIds: List<String> = emptyList(),
    /** 拆出本单的来源订单ID。 */
    val splitFromOrderIds: List<String> = emptyList(),
    /** 绑定会员（空=未绑定；旧数据仅有 member=true 时为演示会员）。 */
    val memberId: String = "",
    val memberName: String = "",
    /** 开台时的费率快照（万分比）：服务费、会员折扣；修改门店设置不追溯已开订单。 */
    val serviceBp: Int = 500,
    val memberBp: Int = 500,
    /** 本单选用的优惠活动快照。 */
    val promotions: List<Promotion> = emptyList(),
) {
    val pending get() = lines.filter { it.status == LineStatus.New }
    val queued get() = lines.filter { it.status == LineStatus.Queued }
    val sent get() = lines.filter { it.status != LineStatus.New }
}

/** 金额：小计、服务费、优惠合计、应收；分项：会员、主管折扣、活动。items 份数；newItems 待送厨；queuedItems 待同步。 */
data class Totals(
    val sub: Long, val service: Long, val discount: Long, val due: Long, val items: Int, val newItems: Int, val queuedItems: Int = 0,
    val memberOff: Long = 0, val manualOff: Long = 0, val promoOff: Long = 0,
)

/** 优惠活动：满减（满 threshold 减 amount）或整单折扣（percent=90 即 9 折，可设门槛）。 */
@Serializable enum class PromotionKind(val label: String) { Threshold("满减"), Percent("整单折扣") }

@Serializable data class Promotion(
    val id: String, val name: String, val kind: PromotionKind, val threshold: Long = 0, val amount: Long = 0, val percent: Int = 100, val enabled: Boolean = true,
) {
    val summary get() = when (kind) {
        PromotionKind.Threshold -> "满${yuan(threshold).removePrefix("¥").removeSuffix(".00")}减${yuan(amount).removePrefix("¥").removeSuffix(".00")}"
        PromotionKind.Percent -> "${if (percent % 10 == 0) "${percent / 10}" else "${percent / 10.0}"}折" + if (threshold > 0) "（满${yuan(threshold).removePrefix("¥").removeSuffix(".00")}）" else ""
    }
}

/** 会员档案（本店本机；多机经主机共享）。 */
@Serializable data class Member(
    val id: String, val phone: String, val name: String, val balance: Long = 0, val points: Long = 0,
    val totalSpent: Long = 0, val visits: Int = 0, val createdAt: Long = 0,
)

@Serializable enum class MemberTxnKind(val label: String) { Recharge("充值"), Consume("储值消费"), Points("积分") }

/** 会员流水：余额变动必须有流水，balanceAfter 便于对账。 */
@Serializable data class MemberTxn(
    val id: String, val memberId: String, val kind: MemberTxnKind, val amount: Long, val balanceAfter: Long, val at: Long,
    val ref: String = "", val actor: String = "", val method: String = "", val points: Long = 0,
)

/** 结账时的订单快照：报表依据（菜品、优惠、会员）。 */
@Serializable data class SettledOrder(
    val orderId: String, val tableId: String, val paymentId: String, val at: Long, val lines: List<OrderLine>,
    val sub: Long, val service: Long, val discount: Long, val due: Long, val method: String,
    val memberId: String = "", val promotions: List<String> = emptyList(), val guests: Int = 0,
)

/** 日结记录：营业日汇总（固定快照）。 */
@Serializable data class DayClose(
    val id: String, val start: Long, val end: Long, val staff: String,
    val revenue: Long, val received: Long, val orders: Int, val guests: Int, val discount: Long,
    val byMethod: Map<String, Long>, val voidAmount: Long = 0, val giftAmount: Long = 0,
    val recharge: Long = 0, val newMembers: Int = 0,
)

/** 收款记录（本机落盘，联网后同步）。 */
@Serializable
data class PaymentRecord(val id: String, val tableId: String, val amount: Long, val method: String, val at: Long, val synced: Boolean = false, val received: Long = amount, val change: Long = 0,
    /** 所属订单；null 表示旧版本记录，关联未知（不按桌号推断）。 */
    val orderId: String? = null,
    val currency: String = Money.CURRENCY,
    val state: PaymentState = PaymentState.LocalRecorded,
    /** local=本机记账，simulation=演示渠道，空=旧版本记录。 */
    val source: String = "")

/** 支付状态轴（N3）：未知只能查单，不能引导重复支付。 */
@Serializable enum class PaymentState(val label: String) {
    LocalRecorded("本机已记账"), Processing("处理中"), Unknown("结果未知"), Succeeded("成功"), Failed("失败"), Refunding("退款中"), Refunded("已退款")
}

/** 厨房送达状态轴（N3）：送达 ≠ 厨师接单；未知不能自动改为失败重派。 */
@Serializable enum class DeliveryState(val label: String) {
    Pending("待出单"), Delivered("已送达"), Failed("失败"), Unknown("结果未知"), ManualConfirmed("人工确认送达")
}

/** 厨房送达记录：每张厨房票据（送厨批次、退菜单、补打）一条。 */
@Serializable data class KitchenDelivery(
    val ticketId: String, val jobId: String, val tableId: String, val orderId: String, val label: String,
    val target: String = "厨房打印机", val state: DeliveryState = DeliveryState.Pending,
    /** simulation=模拟打印结果，legacy=升级前记录（历史待核实），manual=人工确认。 */
    val source: String = "simulation",
    val attempts: Int = 0, val lastError: String = "", val createdAt: Long = 0, val updatedAt: Long = 0,
    val confirmedBy: String = "", val reason: String = "",
    /** 已由补打任务接替（原记录不再阻止结账，由补打记录追踪）。 */
    val supersededBy: String = "",
) {
    val needsAction get() = (state == DeliveryState.Failed || state == DeliveryState.Unknown) && supersededBy.isBlank()
}

@Serializable enum class OutboxKind(val label: String) { Kitchen("送厨"), Payment("收款") }
@Serializable enum class OutboxState(val label: String) { Pending("待同步"), Done("已同步") }

/** 同步队列项：离线产生的业务操作，联网后按创建顺序补发；id 即幂等键。 */
@Serializable
data class OutboxItem(
    val id: String,
    val kind: OutboxKind,
    val tableId: String,
    val summary: String,
    val createdAt: Long,
    val state: OutboxState = OutboxState.Pending,
    val attempts: Int = 0,
    val doneAt: Long = 0,
    /** 完成来源：simulation=模拟补发，legacy=旧版本标记；都不是云端持久回执。 */
    val ackSource: String = "",
)

@Serializable
data class StoreSettings(
    val shortName: String, val receiptFooter: String = "谢谢惠顾，欢迎再来", val hours: String = "10:00–22:00", val currency: String = Money.CURRENCY,
    /** 服务费率（万分比，0–2000）。 */
    val serviceBp: Int = 500,
    /** 会员折扣（万分比，500=95 折）。 */
    val memberBp: Int = 500,
    /** 启用的收款方式。 */
    val payMethods: List<String> = listOf("现金", "扫码", "银行卡", "储值卡"),
    /** 每消费 1 元积 N 分（0=不积分）。 */
    val pointsPerYuan: Int = 1,
)

/** 一家门店的全部本地数据。 */
@Serializable
data class StoreData(
    val id: String,
    val name: String,
    val code: String,
    val address: String,
    val settings: StoreSettings,
    val areas: List<Area>,
    val tables: List<DiningTable>,
    val categories: List<Category>,
    val specGroups: List<SpecGroup>,
    val products: List<Product>,
    val menus: List<Menu>,
    val orders: Map<String, TableOrder> = emptyMap(),
    val payments: List<PaymentRecord> = emptyList(),
    val outbox: List<OutboxItem> = emptyList(),
    val currentTableId: String,
    val seq: Long = 1,
    /** 挂单：暂存的草稿菜品，可在任意用餐中的桌台取回。 */
    val held: List<HeldTicket> = emptyList(),
    /** 本机打印队列。 */
    val printJobs: List<PrintJob> = emptyList(),
    val shift: ShiftState = ShiftState(),
    val adjustments: List<AdjustmentAudit> = emptyList(),
    val walletBalance: Long = 50000,
    val reservations: List<Reservation> = emptyList(),
    /** 库存流水：每次盘点/调整保存前后数量与原因，不只覆盖余额。 */
    val stockMovements: List<StockMovement> = emptyList(),
    /** 本机设备任务日志（打印/同步尝试），不属于营业事件；仅保留最近 TASK_LOG_LIMIT 条。 */
    val taskLog: List<TaskLogEntry> = emptyList(),
    /** 升级时为活动订单补发的ID映射（旧数据无订单ID）。 */
    val idMappings: List<IdMapping> = emptyList(),
    val kitchenDeliveries: List<KitchenDelivery> = emptyList(),
    /** 门店打印机（仅主 POS/单机作为打印网关）。 */
    val printers: List<PrinterConfig> = emptyList(),
    /** 档口路由：厨房 / 前台 → 打印机 ID。 */
    val printRoutes: Map<String, String> = emptyMap(),
    // —— 日常运营 ——
    val members: List<Member> = emptyList(),
    val memberTxns: List<MemberTxn> = emptyList(),
    val promotions: List<Promotion> = defaultPromotions(),
    val settledOrders: List<SettledOrder> = emptyList(),
    /** 当前营业日开始时间（0=尚未开始，取最早收款时间）。 */
    val dayStart: Long = 0,
    val dayCloses: List<DayClose> = emptyList(),
    /** 员工（空表示尚未启用员工模式：门店账号按店长处理）。 */
    val employees: List<Employee> = emptyList(),
) {
    /** 点餐可见商品：属于任一已发布餐牌。 */
    val visibleProducts get(): List<Product> {
        val ids = menus.filter { it.published }.flatMap { it.productIds }.toSet()
        return products.filter { it.id in ids }
    }
}

/** 库存流水。before 为 null 表示调整前不限量；skuKey 为 null 表示商品级库存。 */
@Serializable data class StockMovement(
    val id: String, val productId: String, val skuKey: String?, val productName: String,
    val before: Int?, val after: Int, val delta: Int?, val reason: String, val actor: String, val at: Long,
)

/** 设备任务日志分类：请求/补打为本机操作；尝试类当前全部来自模拟引擎。 */
@Serializable enum class TaskLogKind { PrintRequested, PrintReprinted, PrintAttempt, SyncAttempt }

/** source：local=本机操作，simulation=模拟引擎结果（不可当作真实设备或云端回执）。 */
@Serializable data class TaskLogEntry(
    val id: String, val kind: TaskLogKind, val refId: String, val result: String,
    val source: String, val at: Long, val detail: String = "",
)

const val TASK_LOG_LIMIT = 500

/** 旧活动订单迁移映射：桌号 → 新订单ID / 桌台会话ID。 */
@Serializable data class IdMapping(val tableId: String, val orderId: String, val sessionId: String, val at: Long, val source: String = "legacy-v3")

/** 挂单票据：从某桌草稿暂存，取回时作为新加菜品并入目标桌。 */
@Serializable data class HeldTicket(val id: String, val tableId: String, val lines: List<OrderLine>, val note: String, val at: Long)

@Serializable enum class PrintKind(val label: String, val printer: String) {
    Kitchen("厨房单", "厨房"), PreBill("预结单", "前台"), Receipt("结账单", "前台"), Shift("交班单", "前台"), Test("测试页", "指定"),
}
/**
 * 打印状态（N6 分级）：Printed 仅用于模拟/历史与人工确认；真实打印机发出后为 Sent（已写入且状态正常，仍非出纸确认），
 * Unknown 须人工核对、不得自动重打；Failed 表示确定未发出，可安全自动重试。
 */
@Serializable enum class PrintState(val label: String) {
    Pending("排队中"), Printed("已打印"), Failed("打印失败"), Sending("发送中"), Sent("已发送"), Unknown("结果未知"),
}

/** 网口 ESC/POS 打印机（RAW 9100）。width：58mm=32 列、80mm=48 列。 */
@Serializable data class PrinterConfig(
    val id: String, val name: String, val host: String, val port: Int = 9100, val width: Int = 32,
    val enabled: Boolean = true, val statusQuery: Boolean = true,
)

/** 打印任务：缺纸等失败保留在队列，打印机恢复后自动重试；补打单独标识，不重复收款。 */
@Serializable
data class PrintJob(
    val id: String,
    val kind: PrintKind,
    val title: String,
    val body: List<String>,
    val createdAt: Long,
    val state: PrintState = PrintState.Pending,
    val attempts: Int = 0,
    val reprint: Boolean = false,
    val error: String = "",
    /** 目标打印机（首次发送时确定；空=按档口路由，路由无打印机则为模拟打印）。 */
    val printerId: String = "",
    /** 补打关联的原任务。 */
    val reprintOf: String = "",
    /** 确定未发出后下次自动重试时间。 */
    val nextAt: Long = 0,
    /** 人工核对记录（人员：原因）。 */
    val resolution: String = "",
    val updatedAt: Long = 0,
)

/** 交班记录：按本班收款汇总，钱箱实盘与应有现金比较。 */
@Serializable
data class ShiftRecord(
    val id: String,
    val start: Long,
    val end: Long,
    val staff: String,
    val total: Long,
    val count: Int,
    val byMethod: Map<String, Long>,
    val float: Long,
    val cashExpected: Long,
    val cashCounted: Long,
    val voidAmount: Long = 0,
    val voidCount: Int = 0,
    val giftAmount: Long = 0,
    val giftCount: Int = 0,
)

/** 当前班次：开班时间、备用金、历史交班。 */
@Serializable data class ShiftState(val start: Long = 0, val float: Long = 50_000, val history: List<ShiftRecord> = emptyList())

/** 本地账号：只存加盐哈希，不存明文密码。 */
@Serializable data class Account(val email: String, val merchant: String, val salt: String, val hash: String, val supervisor: Boolean = true)

/** 会话（页头展示用）。 */
data class Session(
    val user: String?,
    val storeId: String?,
    val store: String,
    val staff: String,
    val currentTableId: String,
)

/** 设备与网络：online = 系统网络可用且未强制离线。 */
data class DeviceStatus(
    val network: Boolean = true,
    val forceOffline: Boolean = false,
    val printerOk: Boolean = true,
) {
    val online get() = network && !forceOffline
}

/** 门店卡片（选店页）。 */
data class StoreSummary(val id: String, val name: String, val code: String, val address: String, val tables: Int, val dining: Int)


/** 默认活动（与旧版写死的券/活动一致，便于迁移）。 */
fun defaultPromotions() = listOf(
    Promotion("promo-200-10", "满200减10", PromotionKind.Threshold, threshold = 20_000, amount = 1_000),
    Promotion("promo-100-5", "满100减5", PromotionKind.Threshold, threshold = 10_000, amount = 500),
)
