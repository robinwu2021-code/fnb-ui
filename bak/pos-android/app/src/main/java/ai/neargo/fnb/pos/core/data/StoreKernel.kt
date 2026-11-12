package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.MemberTxn
import ai.neargo.fnb.pos.core.model.MemberTxnKind
import ai.neargo.fnb.pos.core.model.OutboxItem
import ai.neargo.fnb.pos.core.model.OutboxKind
import ai.neargo.fnb.pos.core.model.PaymentRecord
import ai.neargo.fnb.pos.core.model.Perm
import ai.neargo.fnb.pos.core.model.PrintJob
import ai.neargo.fnb.pos.core.model.StoreData
import kotlinx.coroutines.flow.StateFlow

/**
 * 公共内核（两业态共用的门店事务能力）：由 LocalWorkspace 提供，业态服务只通过它读写门店数据。
 * 事务（校验与变更基于同一已提交版本、命令日志与事件同事务）、权限、单号、打印副本、主管授权都在这里，业态服务不各写一份。
 */
internal interface StoreKernel {
    val vault: StateFlow<Vault>
    fun clock(): Long
    fun newId(prefix: String): String
    fun actorName(v: Vault): String
    /** 当前员工缺这个权限时返回原因 */
    fun need(perm: Perm): String?
    fun <T> derive(f: (Vault) -> T): StateFlow<T>
    /** 改当前门店：check 返回原因就拒绝 */
    suspend fun storeOp(event: LocalWorkspace.EventSpec? = null, check: (StoreData) -> String? = { null }, f: (StoreData) -> StoreData): Result<Unit>
    /** 改整个账本（跨门店，如调拨） */
    suspend fun <T> transaction(event: LocalWorkspace.EventSpec? = null, f: (Vault) -> Pair<Vault, T>): T
    /** 打印任务加上路由配置的副本 */
    fun copies(s: StoreData, job: PrintJob): List<PrintJob>
    /** 主管授权：员工姓名 / ID + PIN、主管账号 + 密码或体验账号；返回授权人 */
    fun supervisorAuth(v: Vault, who: String, secret: String): Result<String>
}

/**
 * 公共收款（端架构「领域服务只有一套」）：收款 / 退款记录、离线进同步队列、会员消费与退款。
 * 餐饮结账和零售收银、退货、换货都走这里，规则只有一份。
 */
internal class Settlement(private val k: StoreKernel) {
    /** 记一笔收款（退款为负数）；离线时同时进同步队列，联网后补传 */
    fun record(s: StoreData, pay: PaymentRecord, online: Boolean, scope: String, summary: String): StoreData =
        s.copy(payments = s.payments + pay, outbox = if (online) s.outbox else s.outbox + OutboxItem(pay.id, OutboxKind.Payment, scope, summary, k.clock()))

    /** 会员消费：累计消费、来店次数、按实付给积分；储值支付扣会员余额并记流水 */
    fun memberSpend(s: StoreData, memberId: String, paid: Long, stored: Boolean, ref: String, actor: String, method: String): StoreData {
        val m = s.members.firstOrNull { it.id == memberId } ?: return s
        val pts = (paid / 100) * s.settings.pointsPerYuan
        val m2 = m.copy(balance = if (stored) m.balance - paid else m.balance, points = m.points + pts, totalSpent = m.totalSpent + paid, visits = m.visits + 1)
        var txns = s.memberTxns
        if (stored) txns = txns + MemberTxn(k.newId("MT"), m.id, MemberTxnKind.Consume, -paid, m2.balance, k.clock(), ref, actor, method)
        if (pts > 0) txns = txns + MemberTxn(k.newId("MT"), m.id, MemberTxnKind.Points, 0, m2.balance, k.clock(), ref, actor, points = pts)
        return s.copy(members = s.members.map { if (it.id == m.id) m2 else it }, memberTxns = txns)
    }

    /** 会员退款：退到储值卡就退回余额；按退款扣回积分（不扣成负数）；累计消费相应减少 */
    fun memberRefund(s: StoreData, memberId: String, amount: Long, toStored: Boolean, ref: String, actor: String): StoreData {
        val m = s.members.firstOrNull { it.id == memberId } ?: return s
        val back = if (toStored) amount else 0L
        val pts = minOf(m.points, (amount / 100) * s.settings.pointsPerYuan)
        val m2 = m.copy(balance = m.balance + back, points = m.points - pts, totalSpent = maxOf(0, m.totalSpent - amount))
        var txns = s.memberTxns
        if (back > 0) txns = txns + MemberTxn(k.newId("MT"), m.id, MemberTxnKind.Consume, back, m2.balance, k.clock(), ref, actor, "储值卡")
        if (pts > 0) txns = txns + MemberTxn(k.newId("MT"), m.id, MemberTxnKind.Points, 0, m2.balance, k.clock(), ref, actor, points = -pts)
        return s.copy(members = s.members.map { if (it.id == m.id) m2 else it }, memberTxns = txns)
    }
}
