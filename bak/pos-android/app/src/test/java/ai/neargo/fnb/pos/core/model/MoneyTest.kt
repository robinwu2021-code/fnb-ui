package ai.neargo.fnb.pos.core.model

import ai.neargo.fnb.pos.core.data.Vault
import ai.neargo.fnb.pos.core.data.VaultCodec
import org.junit.Assert.*
import org.junit.Test

/** 金额专项：Long 分、上限、四舍五入、溢出、旧 Int 数据兼容。 */
class MoneyTest {
    @Test fun roundingIsHalfUpOnIntegers() {
        assertEquals(0L, Money.rate(9, 500))      // 0.45 分 → 0
        assertEquals(1L, Money.rate(10, 500))     // 0.5 分 → 1
        assertEquals(1L, Money.rate(29, 500))     // 1.45 → 1
        assertEquals(2L, Money.rate(30, 500))     // 1.5 → 2
        assertEquals(1_281L, Money.rate(25_620, 500))
        assertEquals(Money.MAX_BILL, Money.rate(Money.MAX_BILL, 10_000))
        assertTrue(runCatching { Money.rate(-1, 500) }.isFailure)
    }

    @Test fun overflowIsDetectedNotWrapped() {
        assertTrue(runCatching { Money.line(Long.MAX_VALUE / 2, 3) }.exceptionOrNull() is ArithmeticException)
        assertTrue(runCatching { Money.sum(listOf(Long.MAX_VALUE, 1)) }.exceptionOrNull() is ArithmeticException)
        // 超过旧 Int 上限的金额现在可以正确表示
        assertEquals(2_999_970_000L, Money.line(Money.MAX_UNIT, 300))
    }

    @Test fun parseAcceptsTwoDecimalsWithinLimit() {
        assertEquals(4850L, Money.parse("48.5")); assertEquals(1L, Money.parse("0.01"))
        assertEquals(Money.MAX_BILL, Money.parse("99999999.99"))
        assertNull(Money.parse("100000000")); assertNull(Money.parse("1.234")); assertNull(Money.parse("-1")); assertNull(Money.parse("abc"))
        assertNull(Money.parse("100000", Money.MAX_UNIT))
        assertEquals("¥99999999.99", yuan(Money.MAX_BILL))
        assertEquals("−¥0.05", yuan(-5))
        assertTrue(ManageRules.price("100000").isFailure)
        assertEquals(Money.MAX_UNIT, ManageRules.price("99999").getOrThrow())
    }

    @Test fun orderLimitsRejectInsteadOfOverflowing() {
        val p = Product("p", "龙虾", categoryId = "c", price = Money.MAX_UNIT, stock = null)
        assertTrue(OrderRules.add(TableOrder(), p, null, Money.MAX_QTY + 1, 1) is OrderResult.Rejected) // 单行超 999 份
        val big = OrderRules.add(TableOrder(), p, null, 900, 1)
        assertTrue(big is OrderResult.Ok)
        val order = (big as OrderResult.Ok).order
        assertEquals(Money.line(Money.MAX_UNIT, 900), OrderRules.totals(order).sub)
        assertTrue(OrderRules.totals(order).due > Int.MAX_VALUE) // 超过旧 Int 能表示的范围
        val more = OrderRules.add(order, p, null, 900, 2, note = "第二批")
        assertTrue(more is OrderResult.Rejected && more.reason.contains("单据金额超出上限"))
        val p2 = p.copy(id = "p2", price = Money.MAX_UNIT + 1)
        assertTrue(OrderRules.add(TableOrder(), p2, null, 1, 1) is OrderResult.Rejected) // 单价超限
    }

    @Test fun cashInputSupportsLargeBills() {
        assertEquals(1_000_000_000L, CashRules.amount("10000000"))
        assertEquals(500L, CashRules.change("100", 9_500))
        assertEquals("12345678.9", CashRules.key("12345678.", "9"))
        assertEquals("12345678", CashRules.key("12345678", "9")) // 超过 8 位整数不再接受
    }

    @Test fun legacyIntJsonDecodesAsLongWithCurrencyDefault() {
        val legacy = """{"version":3,"stores":{"s":{"id":"s","name":"n","code":"c","address":"a","settings":{"shortName":"x"},"areas":[],"tables":[{"id":"T1","areaId":"a","seats":2,"status":"Paid","paid":25620}],"categories":[],"specGroups":[],"products":[{"id":"p","name":"n","categoryId":"c","price":4800}],"menus":[],"payments":[{"id":"P1","tableId":"T1","amount":25620,"method":"现金","at":1}],"currentTableId":"T1","walletBalance":50000,"shift":{"start":0,"float":50000,"history":[{"id":"S","start":0,"end":1,"staff":"x","total":25620,"count":1,"byMethod":{"现金":25620},"float":50000,"cashExpected":75620,"cashCounted":75620}]}}}}"""
        val v: Vault = VaultCodec.decode(legacy)
        val s = v.stores.getValue("s")
        assertEquals(25_620L, s.payments.single().amount); assertEquals(Money.CURRENCY, s.payments.single().currency)
        assertEquals(4_800L, s.products.single().price); assertEquals(25_620L, s.tables.single().paid)
        assertEquals(75_620L, s.shift.history.single().cashExpected); assertEquals(Money.CURRENCY, s.settings.currency)
    }

    @Test fun totalsUnchangedForPrototypeBill() {
        val lines = listOf(4800L to 1, 300L to 4, 7800L to 1, 3800L to 1, 6800L to 1).mapIndexed { i, (u, q) ->
            OrderLine(i.toLong(), "$i", "x", OrderRules.DEFAULT_SPEC, u, q, LineStatus.Sent)
        }
        val t = OrderRules.totals(TableOrder(lines, member = true, discountPercent = 90, coupon = true, activity = true))
        assertEquals(24_400L, t.sub); assertEquals(1_220L, t.service)
        // 会员 1220 + 九折 (24400-1220)×10% = 2318 + 券 1000 + 活动 500
        assertEquals(1_220L + 2_318L + 1_000L + 500L, t.discount)
        assertEquals(24_400L + 1_220L - 5_038L, t.due)
    }
}
