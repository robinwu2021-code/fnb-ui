package ai.neargo.fnb.pos.ui.order

import ai.neargo.fnb.pos.data.Dish
import ai.neargo.fnb.pos.data.MockData
import ai.neargo.fnb.pos.data.yuan
import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosDims
import ai.neargo.fnb.pos.theme.PosType
import ai.neargo.fnb.pos.theme.num
import ai.neargo.fnb.pos.ui.components.BtnVariant
import ai.neargo.fnb.pos.ui.components.DishImage
import ai.neargo.fnb.pos.ui.components.PosButton
import ai.neargo.fnb.pos.ui.components.PosFilterChip
import ai.neargo.fnb.pos.ui.components.QuantityStepper
import ai.neargo.fnb.pos.ui.components.StatusLabel
import ai.neargo.fnb.pos.ui.components.Tone
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

/** 规格弹窗（560 宽）：份量、口味单选，数量，确认加入。同一道菜不同做法分成两行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecDialog(dish: Dish, onDismiss: () -> Unit, onConfirm: (spec: String, unit: Int, qty: Int) -> Unit) {
    val c = Pos.colors
    val picks = remember { mutableStateListOf(*Array(MockData.specs.size) { if (it == 0) 1 else 0 }) }
    var qty by remember { mutableIntStateOf(1) }
    val chosen = MockData.specs.mapIndexed { i, g -> g.options[picks[i]] }
    val unit = dish.price + chosen.sumOf { it.delta }
    val spec = chosen.filterIndexed { i, o -> !(i == 0 && o.delta == 0) && o.name != "正常" }.joinToString(" · ") { it.name }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 560.dp).fillMaxWidth().clip(PosDims.radiusLg)
                .background(c.elev).padding(20.dp),
        ) {
            Text(dish.name, style = PosType.display, color = c.ink)
            Text(dish.desc, style = PosType.sub, color = c.sub)
            MockData.specs.forEachIndexed { gi, g ->
                Spacer(Modifier.height(16.dp))
                Text(g.name, style = PosType.sub, color = c.sub)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.options.forEachIndexed { oi, o ->
                        val label = when {
                            o.delta > 0 -> "${o.name} +${yuan(o.delta, false)}"
                            o.delta < 0 -> "${o.name} -${yuan(-o.delta, false)}"
                            else -> o.name
                        }
                        PosFilterChip(label, picks[gi] == oi, { picks[gi] = oi })
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(yuan(unit * qty), style = PosType.hero.num(), color = c.ink, modifier = Modifier.weight(1f))
                QuantityStepper(qty, { qty = (qty + it).coerceAtLeast(1) }, min = 1)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosButton("取消", onDismiss, variant = BtnVariant.Muted, height = PosDims.ctlL)
                PosButton("加入", { onConfirm(spec, unit, qty) }, Modifier.weight(1f), height = PosDims.ctlL)
            }
        }
    }
}

/** 长按菜品看详情：大图可双指缩放（Telephoto）。 */
@Composable
fun DishDetailDialog(dish: Dish, qty: Int, onDismiss: () -> Unit, onAdd: () -> Unit) {
    val c = Pos.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(24.dp).widthIn(max = 560.dp).fillMaxWidth().clip(PosDims.radiusLg).background(c.elev)) {
            val imgMod = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
            if (dish.image != null) {
                ZoomableAsyncImage(
                    model = dish.image,
                    contentDescription = dish.name,
                    contentScale = ContentScale.Crop,
                    modifier = imgMod.background(c.faint),
                )
            } else {
                DishImage(dish, imgMod)
            }
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dish.name, style = PosType.display, color = c.ink, modifier = Modifier.weight(1f))
                    Text(yuan(dish.price, false), style = PosType.hero.num(), color = c.ink)
                }
                Text("编号 ${dish.id} · ${dish.desc}", style = PosType.sub, color = c.sub)
                if (dish.tags.isNotEmpty() || dish.soldOut || qty > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (dish.soldOut) StatusLabel("已沽清", Tone.Danger)
                        if (qty > 0) StatusLabel("已点 $qty", Tone.Primary)
                        dish.tags.forEach { StatusLabel(it, Tone.Neutral, dot = false) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("双指缩放查看大图", style = PosType.caption, color = c.sub)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosButton("关闭", onDismiss, variant = BtnVariant.Muted, height = PosDims.ctlL)
                    if (!dish.soldOut) PosButton(if (dish.hasSpec) "选规格" else "加一份", onAdd, Modifier.weight(1f), height = PosDims.ctlL)
                }
            }
        }
    }
}
