package ai.neargo.fnb.pos.feature.manage

import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosCheckRow
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosDivider
import ai.neargo.fnb.pos.core.designsystem.component.PosSelect
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.ProductImage
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.ManageRules
import ai.neargo.fnb.pos.core.model.StoreData
import ai.neargo.fnb.pos.core.model.yuan
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 商品编辑器（v3.3 六分区 + v3.4 SKU 表与本地图片）：
 * 01 基本资料与分类 · 02 图片 · 03 售价 · 04 规格关联 · 05 库存 · 06 餐牌关联 · SKU 组合表。
 * 取消不落盘；错误留在对应分区；图片读取中禁止保存。
 */
@Composable
fun ProductEditor(d: ProductDraft, store: StoreData, vm: ManageViewModel) {
    val c = Pos.colors
    val wide = LocalPosLayout.current.wide
    if (d.confirmDiscard) DiscardDialog(vm)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::pickImage) }

    PosDialog(
        if (d.isNew) "新建商品" else "编辑 ${d.name}", vm::requestClose, maxWidth = 980.dp,
        subtitle = "保存后立即用于点餐；未关联已发布餐牌的商品不会出现在点餐页",
        footer = {
            d.errors.values.firstOrNull()?.let { Text(it, style = Pos.type.label, color = c.danger, modifier = Modifier.weight(1f)) }
            PosButton("取消", vm::requestClose, size = BtnSize.Large)
            PosButton("保存商品", vm::saveProduct, variant = BtnVariant.Primary, size = BtnSize.Large, loading = d.saving, enabled = d.imageState != ImageState.Reading)
        },
    ) {
        val pair: @Composable (@Composable ColumnScope.() -> Unit, @Composable ColumnScope.() -> Unit) -> Unit = { a, b ->
            if (wide) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), content = a); Column(Modifier.weight(1f), content = b)
            } else { Column(content = a); Column(content = b) }
        }
        pair({
            Part("01", "基本资料与分类") {
                PosTextField(d.name, { v -> vm.edit { it.copy(name = v) } }, "商品名称", error = d.errors["name"])
                PosTextField(d.en, { v -> vm.edit { it.copy(en = v) } }, "英文名称", error = d.errors["en"])
                PosSelect("分类", store.categories, store.categories.firstOrNull { it.id == d.categoryId }, { it.name }, { cat -> vm.edit { it.copy(categoryId = cat.id) } })
                PosCheckRow("热销推荐（出现在点餐「热销」分类）", d.hot, { on -> vm.edit { it.copy(hot = on) } })
            }
        }, {
            Part("02", "商品图片") {
                ProductImage(d.image, d.name.ifBlank { "商品图片" }, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Pos.metrics.card))
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PosButton(if (d.image == null) "选择本地图片" else "替换图片", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        loading = d.imageState == ImageState.Reading)
                    if (d.image != null) PosButton("移除", vm::removeImage, variant = BtnVariant.Text)
                }
                Text(
                    when (d.imageState) {
                        ImageState.Reading -> "读取中…"
                        ImageState.Error -> d.imageError ?: "读取失败，请重新选择"
                        else -> d.errors["image"] ?: "JPG / PNG / WebP，单张 ≤ 5MB；图片保存在本机"
                    },
                    style = Pos.type.label, color = if (d.imageState == ImageState.Error || d.errors["image"] != null) c.danger else c.sub,
                )
            }
        })
        pair({
            Part("03", "销售价格") {
                PosTextField(d.price, { v -> vm.edit { it.copy(price = v) }; vm.regenerate() }, "基础售价（元）", error = d.errors["price"],
                    hint = "非负，最多两位小数，上限 99999；规格加价另计", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Part("05", "库存与可售状态") {
                if (d.skus.isNotEmpty()) {
                    Text("有规格组合时按 SKU 分别填写库存，合计 ${d.skuStock.values.sumOf { it.toIntOrNull() ?: 0 }} 份（只读）", style = Pos.type.body, color = c.sub)
                } else {
                    PosCheckRow("不限量", d.unlimited, { on -> vm.edit { it.copy(unlimited = on) } })
                    if (!d.unlimited) PosTextField(d.stock, { v -> vm.edit { it.copy(stock = v) } }, "可售份数", error = d.errors["stock"], hint = "0 表示沽清",
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
        }, {
            Part("04", "规格关联") {
                if (store.specGroups.isEmpty()) Text("暂无规格组，可在「规格组」新建", style = Pos.type.label, color = c.sub)
                store.specGroups.forEach { g ->
                    PosCheckRow(g.name, g.id in d.groupIds, { on ->
                        vm.edit { it.copy(groupIds = if (on) it.groupIds + g.id else it.groupIds - g.id) }; vm.regenerate()
                    }, trailing = g.options.joinToString(" / ") { it.name })
                }
            }
            Part("06", "餐牌关联") {
                store.menus.forEach { m ->
                    PosCheckRow(m.name, m.id in d.menuIds, { on -> vm.edit { it.copy(menuIds = if (on) it.menuIds + m.id else it.menuIds - m.id) } },
                        trailing = if (m.published) "已发布" else "草稿（发布后可见）")
                }
            }
        })
        if (d.skus.isNotEmpty() || d.errors["skus"] != null) {
            Part("SKU", "规格组合（${d.skus.size} / ${ManageRules.MAX_SKUS}）") {
                d.errors["skus"]?.let { Text(it, style = Pos.type.label, color = c.danger) }
                Row(Modifier.fillMaxWidth().background(c.tile).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("组合", style = Pos.type.label, color = c.sub, modifier = Modifier.weight(2f))
                    Text("售价（元）", style = Pos.type.label, color = c.sub, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text("库存", style = Pos.type.label, color = c.sub, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
                d.skus.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(2f)) {
                            Text(s.label, style = Pos.type.body, color = c.ink)
                            if (s.manualPrice) Text("已手动调价", style = Pos.type.label, color = c.sub)
                        }
                        CellInput(d.skuPrice[s.key] ?: "", { vm.editSkuPrice(s.key, it) }, "${s.label} 售价", Modifier.weight(1f), KeyboardType.Decimal)
                        Spacer(Modifier.width(8.dp))
                        CellInput(d.skuStock[s.key] ?: "", { vm.editSkuStock(s.key, it) }, "${s.label} 库存", Modifier.weight(1f), KeyboardType.Number)
                    }
                    PosDivider()
                }
                Text("最低售价 ${d.skuPrice.values.mapNotNull { ManageRules.price(it).getOrNull() }.minOrNull()?.let(::yuan) ?: "—"}；调整基础价时未手动调价的组合会重算", style = Pos.type.label, color = c.sub, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** 放弃编辑确认：继续编辑为主操作，放弃为危险操作。 */
@Composable
private fun DiscardDialog(vm: ManageViewModel) {
    PosDialog("放弃未保存的修改？", vm::keepEditing, maxWidth = 420.dp, subtitle = "商品资料、规格组合与图片改动都不会保存", footer = {
        PosButton("放弃修改", vm::closeEditor, variant = BtnVariant.Danger, size = BtnSize.Large)
        PosButton("继续编辑", vm::keepEditing, variant = BtnVariant.Primary, size = BtnSize.Large)
    }) {}
}

/** 编辑器分区：编号 + 标题，白底分区内容。 */
@Composable
private fun Part(no: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(Pos.metrics.card).background(Pos.colors.tile).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(no, style = Pos.type.label, color = Pos.colors.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = Pos.type.section, color = Pos.colors.ink)
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** 表格内输入：白底、右对齐、等宽数字。 */
@Composable
private fun CellInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, type: KeyboardType) {
    BasicTextField(
        value, onChange, singleLine = true,
        textStyle = Pos.type.body.copy(color = Pos.colors.ink, textAlign = TextAlign.End, fontFeatureSettings = "tnum"),
        cursorBrush = SolidColor(Pos.colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = modifier.clip(Pos.metrics.button).background(Pos.colors.surface).padding(horizontal = 10.dp, vertical = 10.dp).semantics { contentDescription = label },
    )
}
