package ai.neargo.fnb.pos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageRulesTest {
    private val size = SpecGroup("g1", "份量", options = listOf(SpecOption("s", "标准"), SpecOption("l", "大份", 2000)))
    private val temp = SpecGroup("g2", "温度", options = listOf(SpecOption("h", "热"), SpecOption("c", "冰")))

    @Test fun tableIdAndSeats() {
        assertNotNull(ManageRules.tableId("A 1", emptyList()))
        assertNotNull(ManageRules.tableId("a03", listOf("A03")))
        assertNull(ManageRules.tableId("VIP-1", listOf("A03")))
        assertTrue(ManageRules.seats("0").isFailure); assertTrue(ManageRules.seats("31").isFailure); assertEquals(30, ManageRules.seats("30").getOrThrow())
    }

    @Test fun priceAndStock() {
        assertEquals(4850, ManageRules.price("48.5").getOrThrow())
        assertTrue(ManageRules.price("1.234").isFailure)
        assertTrue(ManageRules.price("-1").isFailure)
        assertTrue(ManageRules.price("100000").isFailure)
        assertTrue(ManageRules.stock("1.5").isFailure)
    }

    @Test fun optionsParsing() {
        assertEquals(listOf("标准" to 0L, "大份" to 2000L), ManageRules.options("标准:0,大份:20").getOrThrow())
        assertTrue(ManageRules.options("标准:0,标准:1").isFailure)
        assertTrue(ManageRules.options("大份:1.234").isFailure)
    }

    /** 必测场景 2：两组各两选项 → 4 个 SKU；手动改价与库存后改基础价，手动值保留。 */
    @Test fun skuGenerationKeepsManualValues() {
        val first = ManageRules.skus(listOf(size, temp), 1000, 5, emptyList()).getOrThrow()
        assertEquals(4, first.size)
        val key = first.first { it.label == "大份 · 冰" }.key
        val edited = first.map { if (it.key == key) it.copy(price = 9900, manualPrice = true, stock = 1, manualStock = true) else it }
        val again = ManageRules.skus(listOf(size, temp), 1500, 5, edited).getOrThrow()
        val kept = again.first { it.key == key }
        assertEquals(9900, kept.price); assertEquals(1, kept.stock)
        assertEquals(1500, again.first { it.label == "标准 · 热" }.price) // 未手动调价的随基础价重算
        assertEquals(3500, again.first { it.label == "大份 · 热" }.price)
    }

    @Test fun skuLimit() {
        val big = SpecGroup("g3", "大组", options = (1..11).map { SpecOption("o$it", "选项$it") })
        assertTrue(ManageRules.skus(listOf(big, big.copy(id = "g4")), 100, 1, emptyList()).isFailure) // 121 > 100
    }

    @Test fun menuRelinkReplacesOnlyThisProductAndUnpublishesEmpty() {
        val menus = listOf(Menu("m1", "全天", productIds = listOf("p1", "p2"), published = true), Menu("m2", "午市", productIds = listOf("p1"), published = true))
        val r = ManageRules.relink(menus, "p1", setOf("m1"))
        assertEquals(listOf("p1", "p2"), r[0].productIds)
        assertEquals(emptyList<String>(), r[1].productIds)
        assertEquals(false, r[1].published) // 空餐牌自动停用
        assertNotNull(ManageRules.canPublish(r[1]))
        assertEquals(listOf("p1", "p2"), ManageRules.relink(r, "p1", setOf("m1"))[0].productIds) // 不重复添加
    }

    @Test fun accountValidation() {
        assertNotNull(ManageRules.email("abc"))
        assertNull(ManageRules.email("a@b.co"))
        assertNotNull(ManageRules.password("1234567"))
        val h = ManageRules.hash("password1", "salt")
        assertEquals(h, ManageRules.hash("password1", "salt"))
        assertTrue(h != ManageRules.hash("password1", "salt2"))
    }
}
