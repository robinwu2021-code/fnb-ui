package ai.neargo.fnb.pos.core.common

import ai.neargo.fnb.pos.core.data.DemoCatalog
import ai.neargo.fnb.pos.core.model.PaymentState
import ai.neargo.fnb.pos.core.model.StoreData
import org.junit.Assert.*
import org.junit.Test

/** 公共收款规则：规则只有一份，餐饮 / 零售只是措辞不同 */
class PaymentRulesTest {
    private val dining = PayWording("离线时该收款方式不可用", "请先绑定会员", "请先绑定会员", { "会员余额不足（余额 $it）" }, memberMustExistForAnyMethod = false)
    private val retail = PayWording("离线时扫码收款不可用", "请先识别会员", "储值余额不足", { "会员储值余额不足" }, memberMustExistForAnyMethod = true)
    private fun store(): StoreData = DemoCatalog.stores(1_800_000_000_000L).getValue(DemoCatalog.RETAIL_STORE_ID)
    private fun block(s: StoreData, method: String, due: Long = 1000, received: Long = 1000, online: Boolean = true, member: String = "", flag: Boolean = false, w: PayWording = dining) =
        PaymentRules.collectBlock(s, method, due, received, online, member, flag, w)

    @Test fun methodListEnabledAndCash() {
        val s = store()
        assertEquals("请选择收款方式", block(s, "支付宝"))
        assertEquals("门店未启用「${PayMethods.CARD}」", block(s.copy(settings = s.settings.copy(payMethods = listOf(PayMethods.CASH))), PayMethods.CARD))
        assertEquals("实收不足，不能确认收款", block(s, PayMethods.CASH, due = 1000, received = 999))
        assertNull(block(s, PayMethods.CASH, due = 1000, received = 1000))
        assertNull(block(s, PayMethods.CARD))
    }

    @Test fun scanNeedsNetworkWithEachWording() {
        val s = store()
        assertEquals("离线时该收款方式不可用", block(s, PayMethods.QR, online = false, w = dining))
        assertEquals("离线时扫码收款不可用", block(s, PayMethods.QR, online = false, w = retail))
        assertNull(block(s, PayMethods.QR, online = true))
    }

    @Test fun storedValueRules() {
        val s = store(); val m = s.members.first().copy(balance = 500)
        val withM = s.copy(members = listOf(m) + s.members.drop(1), walletBalance = 0)
        assertEquals("会员余额不足（余额 500）", block(withM, PayMethods.STORED, due = 1000, member = m.id, w = dining))
        assertEquals("会员储值余额不足", block(withM, PayMethods.STORED, due = 1000, member = m.id, w = retail))
        assertNull(block(withM, PayMethods.STORED, due = 500, member = m.id))
        // 没有真实会员：没开会员价开关 → 提示去识别 / 绑定；开了开关但演示余额不够 → 各自措辞
        assertEquals("请先绑定会员", block(withM, PayMethods.STORED, w = dining))
        assertEquals("请先识别会员", block(withM, PayMethods.STORED, w = retail))
        assertEquals("请先绑定会员", block(withM, PayMethods.STORED, flag = true, w = dining))
        assertEquals("储值余额不足", block(withM, PayMethods.STORED, flag = true, w = retail))
        assertNull(block(withM.copy(walletBalance = 5000), PayMethods.STORED, flag = true))
    }

    @Test fun missingMemberBlocksAnyMethodOnlyForRetail() {
        val s = store()
        assertEquals("会员不存在", block(s, PayMethods.CARD, member = "ghost", w = retail))
        assertNull(block(s, PayMethods.CARD, member = "ghost", w = dining))               // 餐饮只在储值卡时拦
        assertEquals("会员不存在", block(s, PayMethods.STORED, member = "ghost", w = dining))
    }

    @Test fun amountLimitAndUnsupportedCurrency() {
        val s = store()
        assertNull(PaymentRules.amountBlock(1000, 1000))
        assertEquals("金额超出上限", PaymentRules.amountBlock(Long.MAX_VALUE / 2, 1000))
        assertEquals("金额超出上限", PaymentRules.amountBlock(1000, Long.MAX_VALUE / 2))
        assertEquals("不支持的币种：XXX", block(s.copy(settings = s.settings.copy(currency = "XXX")), PayMethods.CARD))
    }

    @Test fun recordShapesByMethod() {
        val cash = PaymentRules.record("P1", "H01", 3200, PayMethods.CASH, 7, true, 5000, "O-1", "CNY")
        assertEquals(5000, cash.received); assertEquals(1800, cash.change); assertEquals(PaymentState.LocalRecorded, cash.state); assertEquals("local", cash.source); assertTrue(cash.synced)
        val qr = PaymentRules.record("P2", "H01", 3200, PayMethods.QR, 7, false, 0, null, "CNY")
        assertEquals(3200, qr.received); assertEquals(0, qr.change); assertEquals(PaymentState.Succeeded, qr.state); assertEquals("simulation", qr.source); assertFalse(qr.synced); assertNull(qr.orderId)
    }

    @Test fun methodConstantsKeepStoredValues() {
        // 展示名同时是存储值：改字面会让旧数据对不上
        assertEquals(listOf("现金", "银行卡", "扫码", "储值卡"), PayMethods.ALL)
    }
}
