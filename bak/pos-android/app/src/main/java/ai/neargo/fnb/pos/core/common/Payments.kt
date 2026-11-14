package ai.neargo.fnb.pos.core.common

import ai.neargo.fnb.pos.core.model.Money
import ai.neargo.fnb.pos.core.model.PaymentRecord
import ai.neargo.fnb.pos.core.model.PaymentState
import ai.neargo.fnb.pos.core.model.StoreData

/** 收款方式（本机展示名同时是存储值，不改字面）。餐饮、零售、云端同步都只引用这里，不再各写字符串。 */
object PayMethods {
    const val CASH = "现金"
    const val CARD = "银行卡"
    const val QR = "扫码"
    const val STORED = "储值卡"
    val ALL = listOf(CASH, CARD, QR, STORED)

    /** 需要联网才能收的方式（离线不可用） */
    fun needsNetwork(method: String) = method == QR
}

/** 两业态收款提示的措辞与细微口径差异：规则只有一份，措辞各自保留（零售的提示有测试逐字断言） */
class PayWording(
    /** 离线时扫码收款 */
    val scanOffline: String,
    /** 储值卡但没有会员（旧版演示储值也没绑定） */
    val noMember: String,
    /** 旧版演示储值余额不足 */
    val walletShort: String,
    /** 会员储值余额不足（参数是会员现有余额，分） */
    val balanceShort: (Long) -> String,
    /** 绑定的会员不存在：零售任何收款方式都要拦，餐饮只在储值卡时拦 */
    val memberMustExistForAnyMethod: Boolean,
)

/** 公共收款规则（餐饮结账与零售收银共用）：校验与收款记录构造。账单金额、业务规则（折扣、限购、账单已变等）仍由各业态自己先判。 */
object PaymentRules {
    /** 单据金额与实收都不能超过单据上限 */
    fun amountBlock(billTotal: Long, received: Long): String? =
        if (!Money.withinBill(billTotal) || !Money.withinBill(received)) "金额超出上限" else null

    /**
     * 收款方式校验：币种、方式在白名单、门店已启用、现金实收够、离线不能扫码、会员 / 储值余额。
     * [due] 本次要收的金额（分）；[memberId] 本单会员（空 = 无）；[memberFlag] 旧版「会员价」开关（没有真实会员时用）。
     */
    fun collectBlock(s: StoreData, method: String, due: Long, received: Long, online: Boolean, memberId: String, memberFlag: Boolean, w: PayWording): String? {
        val m = memberId.takeIf { it.isNotBlank() }?.let { id -> s.members.firstOrNull { it.id == id } }
        return when {
            s.settings.currency !in Money.SUPPORTED -> "不支持的币种：${s.settings.currency}"
            method !in PayMethods.ALL -> "请选择收款方式"
            method !in s.settings.payMethods -> "门店未启用「$method」"
            method == PayMethods.CASH && received < due -> "实收不足，不能确认收款"
            !online && PayMethods.needsNetwork(method) -> w.scanOffline
            memberId.isNotBlank() && m == null && (w.memberMustExistForAnyMethod || method == PayMethods.STORED) -> "会员不存在"
            method == PayMethods.STORED && m != null && m.balance < due -> w.balanceShort(m.balance)
            method == PayMethods.STORED && m == null && (!memberFlag || s.walletBalance < due) -> if (!memberFlag) w.noMember else w.walletShort
            else -> null
        }
    }

    /** 一笔正数收款记录：现金记实收与找零；扫码是演示渠道（成功），其余本机记账 */
    fun record(id: String, scope: String, amount: Long, method: String, at: Long, online: Boolean, received: Long, orderId: String?, currency: String) =
        PaymentRecord(id, scope, amount, method, at, synced = online,
            received = if (method == PayMethods.CASH) received else amount, change = if (method == PayMethods.CASH) received - amount else 0L,
            orderId = orderId, currency = currency,
            state = if (method == PayMethods.QR) PaymentState.Succeeded else PaymentState.LocalRecorded, source = if (method == PayMethods.QR) "simulation" else "local")
}
