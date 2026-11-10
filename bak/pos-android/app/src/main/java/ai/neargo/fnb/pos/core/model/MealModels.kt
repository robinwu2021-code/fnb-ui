package ai.neargo.fnb.pos.core.model

import kotlinx.serialization.Serializable

@Serializable data class MealChoice(val id: String, val name: String, val delta: Int = 0)
@Serializable data class MealGroup(val id: String, val name: String, val choices: List<MealChoice>)
/** Immutable order snapshot: later catalog edits cannot change an ordered meal. */
@Serializable data class MealSelection(val groupId: String, val groupName: String, val choiceId: String, val choiceName: String, val delta: Int) {
    val label get() = "$groupName：$choiceName"
}

object MealRules {
    fun validate(groups: List<MealGroup>): String? {
        if (groups.size > 8) return "套餐最多 8 个换菜组"
        if (groups.map { it.id }.distinct().size != groups.size) return "套餐换菜组重复"
        groups.forEach { g ->
            if (g.name.isBlank() || g.name.length > 30) return "换菜组名称需为 1–30 字"
            if (g.choices.isEmpty() || g.choices.size > 12) return "每个换菜组需有 1–12 个选项"
            if (g.choices.map { it.id }.distinct().size != g.choices.size) return "套餐换菜选项重复"
            if (g.choices.any { it.name.isBlank() || it.name.length > 30 }) return "换菜选项名称需为 1–30 字"
            if (g.choices.any { it.delta !in 0..9999900 }) return "换菜加价需为 0–99999 元"
        }
        return null
    }

    fun resolve(product: Product, choiceIds: List<String>): Result<List<MealSelection>> = runCatching {
        validate(product.mealGroups)?.let { error(it) }
        require(choiceIds.size == product.mealGroups.size) { "请为每个套餐换菜组选一项" }
        product.mealGroups.mapIndexed { index, g ->
            val choice = g.choices.firstOrNull { it.id == choiceIds[index] } ?: error("套餐换菜选项已更新，请重新选择")
            MealSelection(g.id, g.name, choice.id, choice.name, choice.delta)
        }
    }

    fun price(base: Int, selections: List<MealSelection>): Int = Math.addExact(base, selections.sumOf { it.delta })
}
