package ai.neargo.fnb.pos.feature.manage

import ai.neargo.fnb.pos.core.model.profile

import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosCheckRow
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.ProductImage
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.data.menusOf
import ai.neargo.fnb.pos.core.model.Menu
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.Sku
import ai.neargo.fnb.pos.core.model.StoreData
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import ai.neargo.fnb.pos.core.designsystem.component.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ColumnScope.CategoriesSection(store: StoreData, vm: ManageViewModel) {
    var creating by remember { mutableStateOf(false) }
    val cols = listOf(Col("分类名称", 1.4f), Col("英文"), Col("商品数", 0.6f, end = true))
    PageHeader("商品分类", "分类名称门店内唯一；点餐分类栏与商品表单同步") { PosButton("新建分类", { creating = true }, variant = BtnVariant.Primary) }
    DataTable(cols, Modifier.weight(1f)) {
        items(store.categories.size) { i ->
            val c = store.categories[i]
            TableRow(listOf({ Cell(c.name) }, { Cell(c.en.ifBlank { "—" }, sub = true) }, { Cell("${store.products.count { it.categoryId == c.id }}") }), cols)
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        var en by remember { mutableStateOf("") }
        FormDialog("新建分类", { creating = false }, { done -> vm.save("已新建分类 $name", { addCategory(name, en) }, done) }) {
            PosTextField(name, { name = it }, "分类名称")
            PosTextField(en, { en = it }, "英文名称（可选）")
        }
    }
}

/** 商品资料：列表 + 新建 / 编辑（六分区编辑器）。未关联已发布餐牌的商品不进入点餐。 */
@Composable
internal fun ColumnScope.ProductsSection(store: StoreData, vm: ManageViewModel) {
    val retail = store.profile.retail
    val cols = listOf(Col("商品", 2f), Col("分类"), Col("售价", end = true), Col("库存", 0.7f, end = true), Col(if (retail) "条码" else "餐牌", 1.4f), Col("", 0.6f, end = true))
    PageHeader("商品资料", if (retail) "${store.products.size} 个商品；改价、价签在「商品」页签更顺手" else "${store.products.size} 个商品；只有关联已发布餐牌的商品出现在点餐页") { PosButton("新建商品", vm::newProduct, variant = BtnVariant.Primary) }
    DataTable(cols, Modifier.weight(1f)) {
        items(store.products.size) { i ->
            val p = store.products[i]
            val menus = store.menusOf(p.id)
            TableRow(listOf(
                {
                    ProductImage(p.image, p.name, Modifier.size(36.dp).clip(Pos.metrics.card))
                    Spacer(Modifier.width(8.dp))
                    Cell(p.name)
                },
                { Cell(store.categories.firstOrNull { it.id == p.categoryId }?.name ?: "—", sub = true) },
                { Cell(yuan(p.displayPrice) + if (p.retail?.weighable == true) "/kg" else if (p.hasSpec) " 起" else "") },
                // 零售称重商品库存按克存，显示成 kg
                { Cell(p.totalStock?.let { if (retail) ai.neargo.fnb.pos.core.model.StockWork.qtyText(p, it.toLong()) else it.toString() } ?: "不限") },
                { if (retail) Cell(p.retail?.barcodes?.firstOrNull() ?: "—", sub = true)
                  else if (menus.none { it.published }) Tag("未上架", fg = Pos.colors.warning) else Cell(menus.filter { it.published }.joinToString("、") { it.name }, sub = true) },
                { PosButton("编辑", { vm.editProduct(p) }, variant = BtnVariant.Text, size = BtnSize.Small) },
            ), cols, onClick = { vm.editProduct(p) })
        }
    }
}

@Composable
internal fun ColumnScope.SpecsSection(store: StoreData, vm: ManageViewModel) {
    var creating by remember { mutableStateOf(false) }
    val cols = listOf(Col("规格组"), Col("选项与加价", 3f), Col("商品数", 0.6f, end = true))
    PageHeader("规格组", "单选组；商品关联多组时自动生成 SKU 组合（最多 100 个）") { PosButton("新建规格组", { creating = true }, variant = BtnVariant.Primary) }
    DataTable(cols, Modifier.weight(1f)) {
        items(store.specGroups.size) { i ->
            val g = store.specGroups[i]
            TableRow(listOf({ Cell(g.name) }, { Cell(g.options.joinToString("，") { if (it.delta > 0) "${it.name} +${yuan(it.delta)}" else it.name }, sub = true) },
                { Cell("${store.products.count { g.id in it.specGroupIds }}") }), cols)
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        var en by remember { mutableStateOf("") }
        var opts by remember { mutableStateOf("") }
        FormDialog("新建规格组", { creating = false }, { done -> vm.save("已新建规格组 $name", { addSpecGroup(name, en, opts) }, done) }) {
            PosTextField(name, { name = it }, "规格组名称")
            PosTextField(en, { en = it }, "英文名称（可选）")
            PosTextField(opts, { opts = it }, "选项与加价", hint = "格式：标准:0,大份:20（加价单位元，最多两位小数）")
        }
    }
}

/** 库存盘点：按商品或 SKU 调整，必须填写原因；0 表示沽清。 */
@Composable
internal fun ColumnScope.StockSection(store: StoreData, vm: ManageViewModel) {
    var target by remember { mutableStateOf<Pair<Product, Sku?>?>(null) }
    val cols = listOf(Col("商品", 2f), Col("规格", 1.6f), Col("当前库存", end = true), Col("", 0.7f, end = true))
    PageHeader("库存盘点", "库存为本机可售上限；有 SKU 的商品按组合分别盘点")
    DataTable(cols, Modifier.weight(1f)) {
        store.products.forEach { p ->
            if (p.hasSpec) p.skus.forEach { s ->
                item(key = "${p.id}-${s.key}") {
                    TableRow(listOf({ Cell(p.name) }, { Cell(s.label, sub = true) }, { StockCell(s.stock) }, { PosButton("盘点", { target = p to s }, variant = BtnVariant.Text, size = BtnSize.Small) }), cols)
                }
            } else item(key = p.id) {
                TableRow(listOf({ Cell(p.name) }, { Cell("—", sub = true) }, { if (p.stock == null) Cell("不限") else StockCell(p.stock) },
                    { PosButton("盘点", { target = p to null }, variant = BtnVariant.Text, size = BtnSize.Small) }), cols)
            }
        }
    }
    target?.let { (p, s) ->
        var value by remember(p.id, s?.key) { mutableStateOf((s?.stock ?: p.stock ?: 0).toString()) }
        var reason by remember(p.id, s?.key) { mutableStateOf("") }
        FormDialog("盘点 ${p.name}${s?.let { " · ${it.label}" } ?: ""}", { target = null }, { done ->
            val n = value.trim().toIntOrNull()
            if (n == null || n < 0) done("库存应为非负整数") else vm.save("已更新库存", { adjustStock(p.id, s?.key, n, reason) }, done)
        }) {
            PosTextField(value, { value = it }, "盘点后数量", hint = "0 表示沽清，点餐不可再加", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            PosTextField(reason, { reason = it }, "原因", hint = "例如：晚市备货、报损")
        }
    }
}

@Composable
private fun StockCell(n: Int) = if (n == 0) Tag("沽清", fg = Pos.colors.danger) else Cell("$n")

/** 餐牌：名称唯一、至少一个商品；发布后商品进入点餐；空餐牌不可发布。 */
@Composable
internal fun ColumnScope.MenusSection(store: StoreData, vm: ManageViewModel) {
    var editing by remember { mutableStateOf<Menu?>(null) }
    var creating by remember { mutableStateOf(false) }
    val cols = listOf(Col("餐牌", 1.4f), Col("商品数", 0.6f, end = true), Col("状态"), Col("", 1.4f, end = true))
    PageHeader("餐牌管理", "点餐只显示已发布餐牌中的商品，多个餐牌取并集") { PosButton("新建餐牌", { creating = true }, variant = BtnVariant.Primary) }
    DataTable(cols, Modifier.weight(1f)) {
        items(store.menus.size) { i ->
            val m = store.menus[i]
            TableRow(listOf(
                { Cell(m.name) }, { Cell("${m.productIds.size}") },
                { if (m.published) Tag("已发布", bg = Pos.colors.tint, fg = Pos.colors.primary) else Tag("草稿") },
                {
                    PosButton("编辑", { editing = m }, variant = BtnVariant.Text, size = BtnSize.Small)
                    PosButton(if (m.published) "停用" else "发布", { vm.save(if (m.published) "已停用 ${m.name}" else "已发布 ${m.name}", { setPublished(m.id, !m.published) }) { e -> e?.let(vm::notice) } },
                        variant = if (m.published) BtnVariant.Secondary else BtnVariant.Soft, size = BtnSize.Small)
                },
            ), cols)
        }
    }
    if (creating || editing != null) {
        val m = editing
        var name by remember(m?.id) { mutableStateOf(m?.name ?: "") }
        var en by remember(m?.id) { mutableStateOf(m?.en ?: "") }
        var ids by remember(m?.id) { mutableStateOf(m?.productIds?.toSet() ?: emptySet()) }
        FormDialog(if (m == null) "新建餐牌" else "编辑 ${m.name}", { creating = false; editing = null }, { done ->
            vm.save(if (m == null) "已新建餐牌 $name（草稿）" else "已保存 $name", { saveMenu(m?.id, name, en, store.products.map { it.id }.filter { it in ids }) }, done)
        }, maxWidth = 560.dp) {
            PosTextField(name, { name = it }, "餐牌名称")
            PosTextField(en, { en = it }, "英文名称（可选）")
            Text("包含商品（${ids.size}）", style = Pos.type.section, color = Pos.colors.ink, modifier = Modifier.padding(top = 8.dp))
            store.products.forEach { p ->
                PosCheckRow(p.name, p.id in ids, { on -> ids = if (on) ids + p.id else ids - p.id }, trailing = yuan(p.displayPrice))
            }
        }
    }
}

