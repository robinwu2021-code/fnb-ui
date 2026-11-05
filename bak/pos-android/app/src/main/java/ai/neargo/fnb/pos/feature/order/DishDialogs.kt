package ai.neargo.fnb.pos.feature.order

import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.ProductImage
import ai.neargo.fnb.pos.core.designsystem.component.QuantityStepper
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.ManageRules
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.Sku
import ai.neargo.fnb.pos.core.model.SpecGroup
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

/**
 * 规格弹窗（v3.4）：每组单选，组合对应一个 SKU；显示该 SKU 独立价格与余量。
 * 沽清组合不能加入；余量上限为 SKU 库存。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecDialog(dish: Product, groups: List<SpecGroup>, onDismiss: () -> Unit, onConfirm: (sku: Sku, qty: Int) -> Unit) {
    val c = Pos.colors
    val t = Pos.type
    // 默认选中第一个有库存的组合
    val first = dish.skus.firstOrNull { it.stock > 0 } ?: dish.skus.firstOrNull()
    val picks = remember { mutableStateListOf(*groups.map { g -> first?.optionIds?.firstOrNull { id -> g.options.any { it.id == id } } ?: g.options.first().id }.toTypedArray()) }
    var qty by remember { mutableIntStateOf(1) }
    val sku = ManageRules.findSku(dish, picks.toList())
    val soldOut = sku == null || sku.stock == 0

    PosDialog(
        dish.name, onDismiss, subtitle = "${dish.en} · ${if (sku == null) "组合不可售" else if (sku.stock == 0) "该组合已沽清" else "余 ${sku.stock} 份"}",
        footer = {
            Text(if (sku == null) "—" else yuan(sku.price * qty), style = t.amountM.num(), color = c.ink, modifier = Modifier.weight(1f))
            QuantityStepper(qty, { qty = (qty + it).coerceIn(1, (sku?.stock ?: 1).coerceAtLeast(1)) }, min = 1, max = sku?.stock ?: 1)
            PosButton("加入账单", { sku?.let { onConfirm(it, qty) } }, variant = BtnVariant.Primary, size = BtnSize.Large, enabled = !soldOut)
        },
    ) {
        groups.forEachIndexed { gi, g ->
            Text(g.name, style = t.section, color = c.ink, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.options.forEach { o -> PosChip(if (o.delta > 0) "${o.name} +${yuan(o.delta)}" else o.name, picks[gi] == o.id, { picks[gi] = o.id; qty = 1 }) }
            }
        }
        Text(if (sku == null) "所选组合未配置 SKU" else "已选：${sku.label}", style = t.label, color = if (soldOut) c.danger else c.sub, modifier = Modifier.padding(vertical = 12.dp))
    }
}

/** 长按菜品看详情：大图可双指缩放（Telephoto）。 */
@Composable
fun DishDetailDialog(dish: Product, qty: Int, onDismiss: () -> Unit, onAdd: () -> Unit) {
    val c = Pos.colors
    val t = Pos.type
    PosDialog(
        dish.name, onDismiss, subtitle = "${dish.en} · 编号 ${dish.id}",
        footer = {
            Text(yuan(dish.displayPrice) + if (dish.hasSpec) " 起" else "", style = t.amountM.num(), color = c.ink, modifier = Modifier.weight(1f))
            PosButton("关闭", onDismiss)
            if (!dish.soldOut) PosButton(if (dish.hasSpec) "选规格" else "加一份", onAdd, variant = BtnVariant.Primary, size = BtnSize.Large)
        },
    ) {
        val imgMod = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(Pos.metrics.card)
        if (dish.image != null) ZoomableAsyncImage(model = dish.image, contentDescription = dish.name, contentScale = ContentScale.Crop, modifier = imgMod.background(c.control))
        else ProductImage(null, dish.name, imgMod)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(dish.stockLabel, fg = if (dish.soldOut) c.danger else c.sub)
            if (qty > 0) Tag("已点 $qty", bg = c.tint, fg = c.primary)
            dish.tags.forEach { Tag(it) }
        }
        Text("双指缩放查看大图", style = t.label, color = c.sub, modifier = Modifier.padding(vertical = 10.dp))
    }
}
