package ai.neargo.fnb.pos.core.model

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * R1：零售规则对照测试。retail-fixtures.json 由冻结的 React 原型（prototypes/pos-react 的 src/domain 与 src/retail）
 * 随机生成并导出（金额换成分、重量换成克），这里逐条重放，结果必须与原型一致。生成脚本：bak/pos-android/tools/export-retail-fixtures.ts。
 */
class RetailRulesTest {
    private val fx: JsonObject by lazy { Json.parseToJsonElement(javaClass.classLoader!!.getResource("retail-fixtures.json")!!.readText()).jsonObject }
    private fun JsonElement.l() = jsonPrimitive.long
    private fun JsonElement.i() = jsonPrimitive.int
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement?.sOrNull() = this?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private val items by lazy { fx.getValue("items").jsonArray.associate { it.jsonObject.let { o -> o.getValue("id").s() to o } } }
    private fun info(id: String): RetailPricing.PriceInfo {
        val o = items.getValue(id)
        return RetailPricing.PriceInfo(o.getValue("p").l(), o["mp"]?.takeIf { it !is JsonNull }?.l(), if (o["promo"].sOrNull() == "half2") LinePromo.Half2 else null)
    }

    @Test fun pricingMatchesPrototype() {
        var n = 0
        fx.getValue("pricing").jsonArray.forEachIndexed { idx, c ->
            val o = c.jsonObject
            val lines = o.getValue("lines").jsonArray.mapIndexed { j, e -> val x = e.jsonObject
                CartLine(j.toLong(), x.getValue("id").s(), x.getValue("q").i(), x["grams"]?.takeIf { it !is JsonNull }?.i(), gift = x.getValue("gift").jsonPrimitive.boolean) }
            val t = RetailPricing.totals(lines, o.getValue("member").jsonPrimitive.boolean) { info(it) }
            val e = o.getValue("exp").jsonObject
            assertEquals("#$idx sub", e.getValue("sub").l(), t.sub)
            assertEquals("#$idx promo", e.getValue("promo").l(), t.promo)
            assertEquals("#$idx memberSave", e.getValue("memberSave").l(), t.memberSave)
            assertEquals("#$idx round", e.getValue("round").l(), t.round)
            assertEquals("#$idx due", e.getValue("due").l(), t.due)
            assertEquals("#$idx pcs", e.getValue("pcs").i(), t.pcs)
            n++
        }
        assertEquals(2000, n)
    }

    @Test fun refundMatchesPrototype() {
        fx.getValue("refund").jsonArray.forEachIndexed { idx, c ->
            val o = c.jsonObject
            val lines = o.getValue("lines").jsonArray.map { val x = it.jsonObject; ReturnRules.SoldLine(x.getValue("key").s(), x.getValue("q").i(), x.getValue("amt").l(), x.getValue("returned").i(), x.getValue("weighed").jsonPrimitive.boolean) }
            val disc = o.getValue("disc").l()
            val pick = o.getValue("pick").jsonObject.mapValues { it.value.i() }
            val e = o.getValue("exp").jsonObject
            val net = ReturnRules.allocate(lines, disc)
            e.getValue("net").jsonObject.forEach { (k, v) -> assertEquals("#$idx net $k", v.l(), net.getValue(k)) }
            assertEquals("#$idx refund", e.getValue("refund").l(), ReturnRules.refundAmount(lines, disc, pick))
            assertEquals("#$idx refundable", e.getValue("refundable").jsonArray.map { it.i() }, lines.map(ReturnRules::refundable))
            val st = when (e.getValue("status").s()) { "returned" -> ReturnRules.SaleStatus.Returned; "partial_return" -> ReturnRules.SaleStatus.PartialReturn; else -> ReturnRules.SaleStatus.Paid }
            assertEquals("#$idx status", st, ReturnRules.status(ReturnRules.applyReturn(lines, pick)))
        }
    }

    @Test fun exchangeMatchesPrototype() {
        fx.getValue("exchange").jsonArray.forEach { c -> val o = c.jsonObject; val e = o.getValue("exp").jsonObject
            val r = RetailPricing.exchangeSettle(o.getValue("newDue").l(), o.getValue("credit").l())
            assertEquals(e.getValue("pay").l(), r.pay); assertEquals(e.getValue("refund").l(), r.refund); assertEquals(e.getValue("even").jsonPrimitive.boolean, r.even)
        }
    }

    @Test fun barcodesMatchPrototype() {
        fx.getValue("codes").jsonArray.forEach { c -> val o = c.jsonObject; val raw = o.getValue("raw").s()
            assertEquals(raw, o.getValue("kind").s(), BarcodeRules.classify(raw).name.lowercase())
            val w = o["weigh"]?.takeIf { it !is JsonNull }?.jsonObject
            val p = BarcodeRules.parseWeigh(raw)
            if (w == null) assertNull(raw, p) else { assertEquals(w.getValue("plu").s(), p!!.plu); assertEquals(w.getValue("amount").l(), p.amount); assertEquals(w.getValue("grams").i(), p.grams) }
            o["ean13"].sOrNull()?.let { assertEquals(raw, it, BarcodeRules.toEan13(raw)); assertEquals(raw, o.getValue("bars").s(), BarcodeRules.ean13Bars(it)) }
        }
        fx.getValue("labelUnit").jsonArray.forEach { c -> val o = c.jsonObject; val it = items.getValue(o.getValue("id").s())
            val exp = o["exp"]?.takeIf { e -> e !is JsonNull }?.l()
            assertEquals(exp, BarcodeRules.labelPer500g(it.getValue("p").l(), it.getValue("weigh").jsonPrimitive.boolean))
        }
    }

    @Test fun countMatchesPrototype() {
        fx.getValue("count").jsonArray.forEach { c -> val o = c.jsonObject
            val book = o.getValue("book").jsonObject.mapValues { it.value.l() }; val counted = o.getValue("counted").jsonObject.mapValues { it.value.l() }
            val d = StockDocRules.countDiff(book, counted); val e = o.getValue("exp").jsonObject
            assertEquals(e.getValue("rows").jsonArray.map { val r = it.jsonObject; Triple(r.getValue("id").s(), r.getValue("book").l(), r.getValue("counted").l()) }, d.rows.map { Triple(it.id, it.book, it.counted) })
            assertEquals(e.getValue("gain").l(), d.gain); assertEquals(e.getValue("loss").l(), d.loss)
            assertEquals(e.getValue("after").jsonObject.mapValues { it.value.l() }, StockDocRules.applyCount(book, counted))
        }
    }

    @Test fun cartSequencesMatchPrototype() {
        fx.getValue("cart").jsonArray.forEachIndexed { idx, c -> val o = c.jsonObject
            var lines = emptyList<CartLine>(); var k = 0L
            o.getValue("ops").jsonArray.forEach { op -> val x = op.jsonObject
                when (x.getValue("op").s()) {
                    "add" -> { val r = RetailCartRules.scanAdd(lines, CartLine(0, x.getValue("id").s(), x.getValue("q").i(), x["grams"]?.takeIf { it !is JsonNull }?.i(), x["sku"].sOrNull())) { ++k }
                        assertEquals("#$idx last", x.getValue("last").l(), r.last); lines = r.lines }
                    "bump" -> lines = RetailCartRules.bump(lines, x.getValue("key").l(), x.getValue("d").i())
                    else -> lines = RetailCartRules.undoLast(lines, x.getValue("key").l())
                }
            }
            val e = o.getValue("exp").jsonObject
            assertEquals("#$idx lines", e.getValue("lines").jsonArray.map { val l = it.jsonObject; listOf(l.getValue("key").l(), l.getValue("id").s(), l.getValue("q").i(), l["grams"]?.takeIf { g -> g !is JsonNull }?.i(), l["sku"].sOrNull()) },
                lines.map { listOf(it.key, it.productId, it.qty, it.grams, it.sku) })
            assertEquals("#$idx pieces", e.getValue("pieces").i(), RetailCartRules.pieces(lines))
        }
        // 第二件半价：正好半分时 App 按金额规则 0.5 分进位；原型是浮点，可能算成舍去（如 ¥267.21 → 133.6049…），这类以整数规则为准
        var halfCents = 0
        fx.getValue("promo").jsonArray.forEach { c -> val o = c.jsonObject
            val q = o.getValue("q").i(); val p = o.getValue("p").l(); val got = RetailCartRules.promoOff(LinePromo.Half2, q, p)
            if ((q / 2) * p % 2 == 1L) { halfCents++; assertEquals((q / 2) * p / 2 + 1, got) } else assertEquals(o.getValue("exp").l(), got) }
        assertTrue("对照数据里应有半分的例子", halfCents > 0)
    }

    /* ---------- 单元断言（原型 domain-selftest 里的零售部分） ---------- */
    @Test fun unitAsserts() {
        assertEquals("9", BarcodeRules.ean13Check("695476742357"))
        assertEquals("2000020000019", BarcodeRules.toEan13("2000001"))
        assertEquals(95, BarcodeRules.ean13Bars("6954767423579").length)
        assertEquals(BarcodeRules.WeighCode("100100", 1582, 1236), BarcodeRules.parseWeigh("210010001582012367"))
        assertEquals(BarcodeRules.CodeKind.Member, BarcodeRules.classify("m77"))
        // 会员价高于售价不生效
        assertEquals(0, RetailPricing.lineMemberSave(CartLine(1, "milk", 1), RetailPricing.PriceInfo(3550, 3690)))
        // 称重行不合并、不能加减
        var k = 0L; var c = RetailCartRules.scanAdd(emptyList(), CartLine(0, "apple", 1, 500)) { ++k }.lines
        c = RetailCartRules.scanAdd(c, CartLine(0, "apple", 1, 300)) { ++k }.lines
        assertEquals(2, c.size); assertEquals(c, RetailCartRules.bump(c, c[0].key, 1))
        // 退货
        val sold = listOf(ReturnRules.SoldLine("a", 3, 3000), ReturnRules.SoldLine("w", 1, 1582, weighed = true))
        assertEquals(listOf(3, 1), ReturnRules.applyReturn(sold, mapOf("a" to 5, "w" to 1)).map { it.returned })
        assertFalse(ReturnRules.restockByDefault(ReturnRules.Reason.Quality))
        assertTrue(ReturnRules.channelBlocked("platform"))
        // 换货
        assertEquals(RetailPricing.Settle(590, 0), RetailPricing.exchangeSettle(5900, 5310))
        assertEquals(RetailPricing.Settle(0, 2410), RetailPricing.exchangeSettle(2900, 5310))
        // 库存
        assertEquals(mapOf("a" to 6L, "b" to 2L), StockDocRules.receive(mapOf("a" to 1L), listOf(StockDocRules.DocLine("a", 5), StockDocRules.DocLine("b", 2))))
        assertTrue(StockDocRules.nearExpiry(LocalDate.parse("2026-11-13"), LocalDate.parse("2026-11-11")))
        assertFalse(StockDocRules.nearExpiry(LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-11")))
    }
}
