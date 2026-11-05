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
import ai.neargo.fnb.pos.core.model.Dish
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

/** 规格弹窗：组合名称、独立价格、余量；同一道菜不同规格分成两行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecDialog(dish: Dish, specs: List<SpecGroup>, onDismiss: () -> Unit, onConfirm: (spec: String, unit: Int, qty: Int) -> Unit) {
    val c = Pos.colors
    val t = Pos.type
    val picks = remember { mutableStateListOf(*Array(specs.size) { 0 }) }
    var qty by remember { mutableIntStateOf(1) }
    val chosen = specs.mapIndexed { i, g -> g.options[picks[i]] }
    val unit = dish.price + chosen.sumOf { it.delta }
    val spec = chosen.joinToString(" · ") { it.name }

    PosDialog(
        dish.name, onDismiss, subtitle = "${dish.en} · ${dish.stockLabel}",
        footer = {
            Text(yuan(unit * qty), style = t.amountM.num(), color = c.ink, modifier = Modifier.weight(1f))
            QuantityStepper(qty, { qty = (qty + it).coerceAtLeast(1) }, min = 1, max = dish.stock ?: 99)
            PosButton("加入账单", { onConfirm(spec, unit, qty) }, variant = BtnVariant.Primary, size = BtnSize.Large)
        },
    ) {
        specs.forEachIndexed { gi, g ->
            Text(g.name, style = t.section, color = c.ink, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.options.forEachIndexed { oi, o -> PosChip(if (o.delta > 0) "${o.name} +${yuan(o.delta)}" else o.name, picks[gi] == oi, { picks[gi] = oi }) }
            }
        }
        Text("已选：$spec", style = t.label, color = c.sub, modifier = Modifier.padding(vertical = 12.dp))
    }
}

/** 长按菜品看详情：大图可双指缩放（Telephoto）。 */
@Composable
fun DishDetailDialog(dish: Dish, qty: Int, onDismiss: () -> Unit, onAdd: () -> Unit) {
    val c = Pos.colors
    val t = Pos.type
    PosDialog(
        dish.name, onDismiss, subtitle = "${dish.en} · 编号 ${dish.id}",
        footer = {
            Text(yuan(dish.price) + if (dish.hasSpec) " 起" else "", style = t.amountM.num(), color = c.ink, modifier = Modifier.weight(1f))
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
