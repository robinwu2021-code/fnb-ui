package ai.neargo.fnb.pos.core.model

import org.junit.Assert.*
import org.junit.Test

class MealRulesTest {
    private val meal = Product("meal", "套餐", categoryId = "sets", price = 10000, mealGroups = listOf(
        MealGroup("main", "主菜", listOf(MealChoice("chicken", "鸡肉"), MealChoice("fish", "鱼", 2000))),
        MealGroup("drink", "饮品", listOf(MealChoice("tea", "茶"), MealChoice("juice", "果汁", 500))),
    ))

    @Test fun selectionsCarrySnapshotAndPerUnitPrice() {
        val picks = MealRules.resolve(meal, listOf("fish", "juice")).getOrThrow()
        assertEquals(12500, MealRules.price(meal.price, picks))
        assertEquals("主菜：鱼", picks.first().label)
        val renamed = meal.copy(mealGroups = meal.mealGroups.map { it.copy(name = "新名称") })
        assertEquals("主菜", picks.first().groupName)
        assertEquals("新名称", MealRules.resolve(renamed, listOf("fish", "juice")).getOrThrow().first().groupName)
    }

    @Test fun staleMissingAndWrongGroupChoicesAreRejected() {
        assertTrue(MealRules.resolve(meal, emptyList()).isFailure)
        assertTrue(MealRules.resolve(meal, listOf("removed", "tea")).isFailure)
        assertTrue(MealRules.resolve(meal, listOf("tea", "fish")).isFailure)
        assertTrue(MealRules.resolve(meal, listOf("fish", "tea", "juice")).isFailure)
    }

    @Test fun malformedCatalogCannotBeOrdered() {
        assertNotNull(MealRules.validate(listOf(MealGroup("empty", "主菜", emptyList()))))
        assertNotNull(MealRules.validate(listOf(MealGroup("g", "", listOf(MealChoice("c", "菜"))))))
        assertNotNull(MealRules.validate(listOf(MealGroup("g", "菜", listOf(MealChoice("c", "菜", -1))))))
        assertTrue(MealRules.resolve(meal.copy(mealGroups = emptyList()), emptyList()).isSuccess)
    }
}
