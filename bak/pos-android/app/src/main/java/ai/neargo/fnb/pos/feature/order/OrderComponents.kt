package ai.neargo.fnb.pos.feature.order

import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.CountBadge
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDivider
import ai.neargo.fnb.pos.core.designsystem.component.ProductImage
import ai.neargo.fnb.pos.core.designsystem.component.QuantityStepper
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.Dish
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderLine
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * DishCard：M3 Card（零阴影），轻底；已点为主题浅底 + 数量角标；沽清保留、灰化。
 * 有图 / 无图 × 图在上 / 图在左。单击加菜（带规格开弹窗），长按看详情。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DishCard(dish: Dish, qty: Int, photos: Boolean, arrange: Arrange, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Pos.colors
    val picked = qty > 0
    val shape = Pos.metrics.card
    Card(
        modifier = Modifier.clip(shape)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "详情")
            .semantics { contentDescription = "${dish.name} ${yuan(dish.price)} ${dish.stockLabel}${if (picked) " 已点 $qty" else ""}" },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (picked) c.tint else c.tile, contentColor = c.ink),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box {
            val dim = Modifier.alpha(if (dish.soldOut) 0.5f else 1f)
            when {
                photos && arrange == Arrange.Top -> Column {
                    ProductImage(dish.image, dish.name, Modifier.fillMaxWidth().aspectRatio(3f / 2f).then(dim))
                    DishInfo(dish, Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
                }
                photos -> Row(Modifier.height(96.dp)) {
                    ProductImage(dish.image, dish.name, Modifier.width(96.dp).fillMaxHeight().then(dim))
                    DishInfo(dish, Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp))
                }
                else -> DishInfo(
                    dish,
                    Modifier.fillMaxWidth().heightIn(min = if (arrange == Arrange.Left) 84.dp else 105.dp)
                        .padding(start = 12.dp, end = 12.dp, top = if (picked) 24.dp else 12.dp, bottom = 12.dp),
                )
            }
            if (picked) CountBadge(qty, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
    }
}

@Composable
private fun DishInfo(d: Dish, modifier: Modifier) {
    val c = Pos.colors
    val t = Pos.type
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(d.name, style = t.strong, color = if (d.soldOut) c.sub else c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(d.note, style = t.label, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(yuan(d.price), style = t.body.num().copy(fontWeight = FontWeight.Bold), color = c.ink, maxLines = 1, softWrap = false)
            if (d.hasSpec) Text(" 起", style = t.label, color = c.sub)
            Spacer(Modifier.weight(1f))
            Text(d.stockLabel, style = t.label, color = if (d.soldOut) c.danger else c.sub, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * OrderLineRow：名称 + 金额；新加行带步进器（只改草稿），已送厨行显示状态与催菜 / 已上菜。
 * 账单坞与结账页共用。
 */
@Composable
fun OrderLineRow(
    line: OrderLine,
    onQty: (Int) -> Unit,
    onAdvance: ((LineStatus) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = Pos.colors
    val t = Pos.type
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(line.name, style = t.line, color = c.ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(yuan(line.unit * line.qty), style = t.line.num(), color = c.ink, maxLines = 1, softWrap = false)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.spec, style = t.label, color = c.sub, modifier = Modifier.weight(1f), maxLines = 2)
            if (line.status == LineStatus.New) {
                QuantityStepper(line.qty, onQty)
            } else {
                val stateColor = when (line.status) {
                    LineStatus.Rushing -> c.warning
                    LineStatus.Served -> c.success
                    else -> c.primary
                }
                Text("${line.status.label} · ×${line.qty}", style = t.label.num(), color = stateColor)
            }
        }
        if (onAdvance != null && line.status != LineStatus.New && line.status != LineStatus.Served) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (line.status == LineStatus.Sent) PosButton("催菜", { onAdvance(LineStatus.Rushing) }, variant = BtnVariant.Text, size = BtnSize.Small, textStyle = t.label)
                PosButton("已上菜", { onAdvance(LineStatus.Served) }, variant = BtnVariant.Text, size = BtnSize.Small, textStyle = t.label)
            }
        }
        PosDivider(Modifier.padding(top = 6.dp))
    }
}
