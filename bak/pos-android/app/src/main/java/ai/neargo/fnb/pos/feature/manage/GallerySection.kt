package ai.neargo.fnb.pos.feature.manage

import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.CountBadge
import ai.neargo.fnb.pos.core.designsystem.component.GroupTitle
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosCheckRow
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosSelect
import ai.neargo.fnb.pos.core.designsystem.component.PosSwitch
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.QuantityStepper
import ai.neargo.fnb.pos.core.designsystem.component.RailItem
import ai.neargo.fnb.pos.core.designsystem.component.SearchField
import ai.neargo.fnb.pos.core.designsystem.component.Section as Block
import ai.neargo.fnb.pos.core.designsystem.component.TableStateLabel
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.colors
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.designsystem.theme.typography
import ai.neargo.fnb.pos.core.model.FontChoice
import ai.neargo.fnb.pos.core.designsystem.theme.tone
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderLine
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.Skin
import ai.neargo.fnb.pos.core.model.TableStatus
import ai.neargo.fnb.pos.core.model.yuan
import ai.neargo.fnb.pos.feature.order.Arrange
import ai.neargo.fnb.pos.feature.order.DishCard
import ai.neargo.fnb.pos.feature.order.OrderLineRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import ai.neargo.fnb.pos.core.designsystem.component.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 组件验收页：直接调用业务页使用的同一批组件，展示各变体、状态与长文本，便于逐项验收（规范 §9）。
 * 不在这里另做一份视觉实现；发现差异应修改公共组件。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.GallerySection() {
    val c = Pos.colors
    val t = Pos.type
    var cash by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf("全部") }
    var qty by remember { mutableIntStateOf(2) }
    var text by remember { mutableStateOf("宫保鸡丁") }
    var check by remember { mutableStateOf(true) }
    var sw by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf("热菜") }
    var q by remember { mutableStateOf("") }
    PageHeader("组件验收", "与点餐、桌台、管理页共用同一套组件；切换显示设置可检查皮肤、密度、圆角与字号")
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Block("现金键盘与找零") {
            ai.neargo.fnb.pos.core.designsystem.component.CashPad(cash, 25620, { cash = it })
        }
        Block("字体对照：标题与正文") {
            FontChoice.entries.forEach { font ->
                val sample = font.typography(1f)
                Text(font.label + " · Aa 0123456789", style = sample.title, color = c.ink)
                Text("宫保鸡丁 · Kung Pao Chicken · ¥48.00", style = sample.body.num(), color = c.sub)
                Spacer(Modifier.height(8.dp))
            }
        }
        Block("按钮：变体 × 尺寸 × 状态") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BtnVariant.entries.forEach { v -> PosButton(v.name, {}, variant = v) }
                PosButton("选中", {}, selected = true)
                PosButton("禁用", {}, variant = BtnVariant.Primary, enabled = false)
                PosButton("提交中", {}, variant = BtnVariant.Primary, loading = true)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BtnSize.entries.forEach { s -> PosButton("${s.name} ${s.height.value.toInt()}", {}, size = s, variant = BtnVariant.Soft) }
            }
        }
        Block("筛选、分类栏、步进器、搜索") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("全部", "辣味", "素食").forEach { PosChip(it, chip == it, { chip = it }, count = if (it == "全部") 8 else null) } }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(88.dp).background(c.tile)) {
                    RailItem("热销", 8, true, {}); RailItem("一个很长的分类名称", null, false, {})
                }
                QuantityStepper(qty, { qty = (qty + it).coerceIn(0, 9) }, max = 9)
                SearchField(q, { q = it }, "搜索菜名、拼音、英文或编号", Modifier.width(260.dp))
            }
        }
        Block("表单：输入、错误、选择、复选、开关") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    PosTextField(text, { text = it }, "商品名称")
                    PosTextField("1.234", {}, "基础售价（元）", error = "售价 应为非负金额，最多两位小数")
                }
                Column(Modifier.weight(1f)) {
                    PosSelect("分类", listOf("热菜", "凉菜", "主食"), sel, { it }, { sel = it })
                    PosCheckRow("热销推荐", check, { check = it }, trailing = "复选")
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("开关", style = t.body, color = c.ink, modifier = Modifier.weight(1f)); PosSwitch(sw, { sw = it }) }
                }
            }
        }
        Block("标签、角标、桌台五态") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Tag("草稿"); Tag("已发布", bg = c.tint, fg = c.primary); Tag("沽清", fg = c.danger); CountBadge(3); CountBadge(12)
                TableStatus.entries.forEach { s ->
                    Box(Modifier.clip(Pos.metrics.card).background(s.tone().bg).padding(horizontal = 10.dp, vertical = 6.dp)) { TableStateLabel(s.label, s.tone()) }
                }
            }
        }
        Block("业务组件：菜品卡（有图 / 无图 / 已点 / 沽清 / 长名）") {
            val p = Product("g1", "宫保鸡丁", "Kung Pao", "c", 4800, 3, listOf("辣"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(150.dp)) { DishCard(p, 0, false, Arrange.Top, {}, {}) }
                Box(Modifier.width(150.dp)) { DishCard(p, 2, false, Arrange.Top, {}, {}) }
                Box(Modifier.width(150.dp)) { DishCard(p.copy(stock = 0), 0, false, Arrange.Top, {}, {}) }
                Box(Modifier.width(150.dp)) { DishCard(p.copy(name = "秘制特色超长名称的招牌红烧肉套餐"), 0, false, Arrange.Top, {}, {}) }
            }
        }
        Block("业务组件：账单行（新加 / 待同步 / 已下单 / 催菜中 / 已上菜）") {
            GroupTitle("新加 · 待送厨 · 2 份", action = "清空")
            LineStatus.entries.forEachIndexed { i, s ->
                OrderLineRow(OrderLine(i.toLong(), "x", "宫保鸡丁", "大份 · 微辣", 5800, 1, s, note = if (i == 0) "少辣、打包" else ""), {}, if (s == LineStatus.New) null else ({}), onNote = if (s == LineStatus.New) ({}) else null)
            }
            KeyValue("金额等宽", yuan(25620))
        }
        Block("八套皮肤主色（当前皮肤在显示设置切换）") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skin.entries.forEach { s ->
                    val sc = s.colors()
                    Row(Modifier.clip(Pos.metrics.button).background(sc.tint).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp).clip(Pos.metrics.card).background(sc.primary))
                        Text("  ${s.label}", style = t.label, color = sc.primary)
                    }
                }
            }
        }
        Block("字号层级") {
            Text("页面标题 20", style = t.title, color = c.ink)
            Text("正文 14：中文长名称换行验证 Kung Pao Chicken with peanuts", style = t.body, color = c.ink)
            Text("辅助说明 12", style = t.label, color = c.sub)
            Text(yuan(1234567), style = t.amount.num(), color = c.ink)
        }
    }
}
