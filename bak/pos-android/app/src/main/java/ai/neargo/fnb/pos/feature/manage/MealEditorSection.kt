package ai.neargo.fnb.pos.feature.manage

import ai.neargo.fnb.pos.core.designsystem.component.*
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID

/** Product-local substitution groups; all changes remain in the product draft until save. */
@Composable
fun MealEditorSection(d: ProductDraft, vm: ManageViewModel) {
    fun update(id: String, change: (MealGroup) -> MealGroup) = vm.edit { draft ->
        draft.copy(mealGroups = draft.mealGroups.map { if (it.id == id) change(it) else it })
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("07 套餐换菜", style = Pos.type.section, color = Pos.colors.ink)
        Text("每组必选一项，第一项默认选中；加价计入每份套餐售价", style = Pos.type.label, color = Pos.colors.sub)
        d.errors["mealGroups"]?.let { Text(it, style = Pos.type.label, color = Pos.colors.danger) }
        d.mealGroups.forEach { group ->
            PosDivider()
            PosTextField(group.name, { value -> update(group.id) { it.copy(name = value) } }, "换菜组名称")
            group.choices.forEach { choice ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        PosTextField(choice.name, { value -> update(group.id) { g -> g.copy(choices = g.choices.map { if (it.id == choice.id) it.copy(name = value) else it }) } }, "换菜选项")
                    }
                    Box(Modifier.weight(1f)) {
                        // Store cents as text while typing via a dedicated draft field, including invalid input.
                        val key = "${group.id}:${choice.id}"
                        PosTextField(d.mealPrices[key] ?: fenToInput(choice.delta), { value ->
                            vm.edit { it.copy(mealPrices = it.mealPrices + (key to value)) }
                        }, "加价（元）")
                    }
                    PosButton("删除选项", { update(group.id) { it.copy(choices = it.choices.filterNot { c -> c.id == choice.id }) } }, variant = BtnVariant.Text)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosButton("添加选项", { update(group.id) { it.copy(choices = it.choices + MealChoice(UUID.randomUUID().toString(), "")) } }, enabled = group.choices.size < 12)
                PosButton("删除换菜组", { vm.edit { it.copy(mealGroups = it.mealGroups.filterNot { g -> g.id == group.id }) } }, variant = BtnVariant.Text)
            }
        }
        PosButton("添加换菜组", { vm.edit { it.copy(mealGroups = it.mealGroups + MealGroup(UUID.randomUUID().toString(), "", listOf(MealChoice(UUID.randomUUID().toString(), "")))) } }, enabled = d.mealGroups.size < 8)
    }
}
