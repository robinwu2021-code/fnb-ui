package ai.neargo.fnb.pos.core.model

import kotlinx.serialization.Serializable

/** 员工角色（门店内）。 */
@Serializable enum class Role(val label: String) { Owner("店长"), Manager("主管"), Cashier("收银"), Waiter("服务员") }

/** 权限点：按营业动作划分，数据层统一校验。 */
enum class Perm(val label: String) {
    Order("开台、点餐、送厨、出菜"),
    Checkout("结账收款"),
    Adjust("退菜、赠送、折扣授权"),
    Member("会员建档"),
    Recharge("会员充值"),
    Shift("交班"),
    DayClose("日结"),
    Reports("查看报表"),
    Settings("门店、菜品、活动、打印设置"),
    Staff("员工与权限管理"),
    Device("设备与系统（主副 POS、授权、备份）"),
}

/** 员工：PIN 加盐哈希保存；不进事件载荷。 */
@Serializable data class Employee(
    val id: String, val name: String, val role: Role, val salt: String, val pinHash: String,
    val enabled: Boolean = true, val createdAt: Long = 0,
)

object Roles {
    private val all = Perm.entries.toSet()
    fun perms(role: Role): Set<Perm> = when (role) {
        Role.Owner -> all
        Role.Manager -> all - Perm.Staff - Perm.Device
        Role.Cashier -> setOf(Perm.Order, Perm.Checkout, Perm.Member, Perm.Recharge, Perm.Shift)
        Role.Waiter -> setOf(Perm.Order)
    }
}

object StaffRules {
    fun pin(p: String): String? = if (Regex("^\\d{4,6}$").matches(p)) null else "PIN 为 4–6 位数字"

    /**
     * 事件类型 → 所需权限。退菜/赠送/折扣由主管凭据单独授权，不在此校验；未列出的类型不需要额外权限。
     */
    fun permFor(eventType: String): Perm? = when (eventType) {
        "PaymentRecorded", "RetailSaleCompleted", "RetailReturned", "RetailReceiptReprinted" -> Perm.Checkout
        "MemberCreated" -> Perm.Member
        "MemberRecharged" -> Perm.Recharge
        "ShiftClosed" -> Perm.Shift
        "DayClosed" -> Perm.DayClose
        "EmployeeSaved" -> Perm.Staff
        "StockAdjusted", "PromotionSaved", "StoreSettingsUpdated", "AreaCreated", "TableConfigCreated", "TableConfigUpdated",
        "CategoryCreated", "SpecGroupCreated", "ProductCreated", "ProductUpdated", "MenuCreated", "MenuUpdated",
        "MenuPublished", "MenuUnpublished", "TablePositionChanged", "KitchenStationsSaved", "KitchenDeliveryPolicySaved", "KitchenDispatchSaved",
        // 零售库存作业（入库 / 盘点 / 报损的建单、修改、过账、作废）
        "StockDocCreated", "StockDocUpdated", "StockDocSubmitted", "StockDocPosted", "StockDocVoided",
        // 零售商品：改价、价签
        "RetailPriceChanged", "LabelQueued", "LabelsPrinted", "RetailSettingsSaved" -> Perm.Settings
        "DishVoided", "DishGifted", "OrderDiscounted" -> null
        // 厨房屏操作：由已配对工位设备授权
        "KdsTicketAdvanced", "KdsTicketReleased" -> null
        // 收回/指派负责屏：主管级
        "KdsTicketReassigned" -> Perm.Adjust
        else -> Perm.Order
    }
}
