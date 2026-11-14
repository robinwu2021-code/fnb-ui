package ai.neargo.fnb.pos.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductSearchTest {
    private val dish = Product("P-42", "宫保鸡丁", "Kung Pao Chicken", "hot", 4800,
        searchAliases = "gōngbǎojīdīng, GBJD")

    @Test fun matchesChineseEnglishAndProductCode() {
        listOf("宫保", "  chicken  ", "kung pao", "p-42").forEach {
            assertTrue(it, ProductSearch.matches(dish, it))
        }
    }

    @Test fun matchesFullPinyinInitialsAndDiacriticsOffline() {
        listOf("gongbaojiding", "G B J D", "gōngbǎo", "gbj").forEach {
            assertTrue(it, ProductSearch.matches(dish, it))
        }
        assertFalse(ProductSearch.matches(dish, "shuizhuyu"))
    }

    @Test fun oldProductsNeedNoAliasAndBlankQueryIncludesAll() {
        val old = dish.copy(searchAliases = "")
        assertTrue(ProductSearch.matches(old, ""))
        assertTrue(ProductSearch.matches(old, "宫保"))
        assertFalse(ProductSearch.matches(old, "gbjd"))
    }

    @Test fun commaAndWhitespaceSeparateAliasesWithoutFalseCrossMatches() {
        val product = dish.copy(searchAliases = "gbjd；gongbao，chicken")
        assertTrue(ProductSearch.matches(product, "gongbao"))
        assertFalse(ProductSearch.matches(product, "jdgong"))
    }
}
