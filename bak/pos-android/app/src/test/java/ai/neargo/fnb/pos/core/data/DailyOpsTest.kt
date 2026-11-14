package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 日常运营：会员、优惠、设置快照、结账快照、报表、日结、出菜时长。 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyOpsTest {
    private class Store : SnapshotStore {
        var json: String? = null
        val commands = mutableListOf<JournalCommand>()
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { json = source; commands += command; return testReceipt(command) }
    }
    private class Clock(var now: Long = 1_800_000_000_000L)
    private fun runWs(block: suspend TestScope.(LocalWorkspace, Store, Clock) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val d = Store(); val c = Clock()
        val w = LocalWorkspace(d, backgroundScope) { c.now }
        w.enterDemo(); w.selectStore("s-main")
        block(w, d, c)
    }
    private fun LocalWorkspace.s() = lanSnapshot().store!!
    private fun LocalWorkspace.idle() = s().tables.first { it.status == TableStatus.Idle }.id
    private fun LocalWorkspace.p(id: String) = s().products.first { it.id == id }
    private fun LocalWorkspace.due(t: String) = OrderRules.totals(s().orders.getValue(t)).due
    private suspend fun LocalWorkspace.orderOf(t: String, vararg items: Pair<String, Int>) {
        openTable(t, 2); items.forEach { (id, q) -> addDish(t, p(id), null, q) }; sendToKitchen(t, true)
    }

    @Test fun settingsSnapshotDoesNotRepriceOpenOrders() = runWs { w, _, _ ->
        val a = w.idle(); w.orderOf(a, "12" to 10) // 酸梅汤 12 元 ×10 = 120
        assertEquals(600L, OrderRules.totals(w.s().orders.getValue(a)).service)
        assertTrue(w.saveSettings(w.s().settings.copy(serviceBp = 1000, memberBp = 1000)).isSuccess)
        assertEquals(600L, OrderRules.totals(w.s().orders.getValue(a)).service) // 已开订单不变
        val b = w.idle(); w.orderOf(b, "12" to 10)
        assertEquals(1200L, OrderRules.totals(w.s().orders.getValue(b)).service)
        assertTrue(w.saveSettings(w.s().settings.copy(serviceBp = 2500)).isFailure)
        assertTrue(w.saveSettings(w.s().settings.copy(payMethods = emptyList())).isFailure)
        assertTrue(w.s().orders.getValue(b).lines.all { it.sentAt == 1_800_000_000_000L })
    }

    @Test fun memberLifecycleStoredValueAndPoints() = runWs { w, d, c ->
        assertTrue(w.createMember("12345", "").isFailure)
        val m = w.createMember("13800138000", "王小明").getOrThrow()
        assertTrue(w.createMember("13800138000", "重复").isFailure)
        assertTrue(w.recharge(m.id, 0, "现金").isFailure)
        assertTrue(w.recharge(m.id, 10_000, "储值卡").isFailure)
        assertTrue(w.recharge(m.id, 10_000, "现金").isSuccess)
        assertEquals(10_000L, w.s().members.single().balance)
        val t = w.idle(); w.orderOf(t, "12" to 10)
        assertTrue(w.bindMember(t, m.id).isSuccess)
        val o = w.s().orders.getValue(t)
        assertEquals("王小明", o.memberName)
        val tot = OrderRules.totals(o)
        assertEquals(600L, tot.memberOff) // 95 折
        assertTrue(tot.due > 10_000) // 余额不足
        assertTrue(w.settle(t, tot.due, "储值卡", true, tot.due).exceptionOrNull()!!.message!!.contains("余额不足"))
        assertTrue(w.recharge(m.id, 5_000, "扫码").isSuccess)
        c.now += 1000
        assertTrue(w.settle(t, tot.due, "储值卡", true, tot.due).isSuccess)
        val after = w.s().members.single()
        assertEquals(15_000L - tot.due, after.balance)
        assertEquals(tot.due / 100, after.points); assertEquals(1, after.visits); assertEquals(tot.due, after.totalSpent)
        val txns = w.s().memberTxns
        assertEquals(listOf(MemberTxnKind.Recharge, MemberTxnKind.Recharge, MemberTxnKind.Consume, MemberTxnKind.Points), txns.map { it.kind })
        assertEquals(after.balance, txns.first { it.kind == MemberTxnKind.Consume }.balanceAfter)
        val pay = d.commands.last { it.type == "PaymentRecorded" }
        assertTrue(pay.aggregates.any { it.id == "member:${m.id}" })
        assertTrue(d.commands.map { it.type }.containsAll(listOf("MemberCreated", "MemberRecharged", "OrderMemberChanged")))
    }

    @Test fun promotionsValidateAndSnapshot() = runWs { w, _, _ ->
        val t = w.idle(); w.orderOf(t, "12" to 10) // 小计 120
        val promos = w.s().promotions
        assertTrue(w.setPromotions(t, listOf("promo-200-10")).exceptionOrNull()!!.message!!.contains("未满"))
        assertTrue(w.setPromotions(t, listOf("promo-100-5")).isSuccess)
        assertEquals(500L, OrderRules.totals(w.s().orders.getValue(t)).promoOff)
        val pct = Promotion("p90", "9折", PromotionKind.Percent, percent = 90)
        assertTrue(w.savePromotion(pct).isSuccess)
        assertTrue(w.savePromotion(Promotion("p80", "8折", PromotionKind.Percent, percent = 80)).isSuccess)
        assertTrue(w.setPromotions(t, listOf("p90", "p80")).exceptionOrNull()!!.message!!.contains("只能选一个"))
        assertTrue(w.setPromotions(t, listOf("p90", "promo-100-5")).isSuccess)
        val tot = OrderRules.totals(w.s().orders.getValue(t))
        assertEquals(1_200L + 500L, tot.promoOff) // 9 折 120→12 元 + 满减 5 元
        // 修改活动配置不影响已选订单（快照）
        assertTrue(w.savePromotion(pct.copy(percent = 50)).isSuccess)
        assertEquals(tot, OrderRules.totals(w.s().orders.getValue(t)))
        assertTrue(w.savePromotion(pct.copy(enabled = false)).isSuccess)
        assertTrue(w.setPromotions(t, listOf("p90")).isFailure) // 停用不可再选
        assertTrue(w.savePromotion(Promotion("bad", "错", PromotionKind.Threshold, threshold = 100, amount = 200)).isFailure)
        assertTrue(promos.size >= 2)
    }

    @Test fun disabledPayMethodRejected() = runWs { w, _, _ ->
        val t = w.idle(); w.orderOf(t, "12" to 1)
        w.saveSettings(w.s().settings.copy(payMethods = listOf("现金")))
        assertTrue(w.settle(t, w.due(t), "银行卡", true, w.due(t)).exceptionOrNull()!!.message!!.contains("未启用"))
        assertTrue(w.settle(t, w.due(t), "现金", true, w.due(t)).isSuccess)
    }

    @Test fun reportsAndDayClose() = runWs { w, d, c ->
        // 演示数据有未结账桌：日结被拒
        assertTrue(w.closeDay("林晓").exceptionOrNull()!!.message!!.contains("未结账"))
        // 结清所有用餐中的桌
        for (tb in w.s().tables.filter { it.status == TableStatus.Dining }) {
            val o = w.s().orders[tb.id] ?: continue
            if (o.lines.isEmpty()) continue
            w.sendToKitchen(tb.id, true)
            c.now += 60_000
            assertTrue(w.settle(tb.id, w.due(tb.id), "现金", true, w.due(tb.id) + 10_000).isSuccess)
        }
        val start = ReportRules.dayStart(w.s())
        val r = ReportRules.build(w.s(), start, c.now + 1)
        assertEquals(Money.sum(w.s().payments.filter { it.at >= start }.map { it.amount }), r.revenue)
        assertTrue(r.dishes.isNotEmpty() && r.dishes.zipWithNext().all { (a, b) -> a.amount >= b.amount })
        assertTrue(r.categories.isNotEmpty())
        assertEquals(r.revenue, r.byMethod["现金"] ?: 0L)
        val day = w.closeDay("林晓").getOrThrow()
        assertEquals(r.revenue, day.revenue); assertEquals(r.orders, day.orders)
        assertEquals(c.now + 1, w.s().dayStart)
        assertTrue(w.s().printJobs.any { it.title.startsWith("日结单") && it.body.first().contains("日结单") })
        assertEquals("DayClosed", d.commands.last().type)
        assertEquals(0L, ReportRules.build(w.s(), w.s().dayStart, c.now + 1).revenue) // 新营业日
        assertEquals(1, w.s().dayCloses.size)
    }

    @Test fun legacyPaymentCountedButUntracked() = runWs { w, _, _ ->
        val s = w.s()
        val r = ReportRules.build(s, 0, Long.MAX_VALUE)
        assertEquals(s.payments.size, r.untracked) // 演示历史收款无订单快照
        assertEquals(Money.sum(s.payments.map { it.amount }), r.revenue)
    }

    @Test fun legacyCouponToggleStillPrices() = runWs { w, _, _ ->
        val t = w.idle(); w.orderOf(t, "12" to 20) // 240
        assertTrue(w.setBenefits(t, coupon = true, activity = true).isSuccess)
        assertEquals(1_500L, OrderRules.totals(w.s().orders.getValue(t)).promoOff)
        assertTrue(w.setPromotions(t, listOf("promo-200-10")).isSuccess) // 新活动替换旧开关
        val o = w.s().orders.getValue(t)
        assertFalse(o.coupon || o.activity); assertEquals(1_000L, OrderRules.totals(o).promoOff)
    }
}
