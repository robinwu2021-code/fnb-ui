package ai.neargo.fnb.pos.feature.common

import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.QuantityStepper
import ai.neargo.fnb.pos.core.designsystem.component.TableStateLabel
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.tone
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.TableStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** 常用备注标签。 */
val DishNoteTags = listOf("少辣", "不要辣", "不要葱", "不要香菜", "少盐", "打包", "先上")
val OrderNoteTags = listOf("有小孩", "赶时间", "过敏：花生", "生日", "VIP")

/** 保存回调：返回错误（null 表示成功并关闭）。 */
typealias Done = (String?) -> Unit

/**
 * 备注弹窗：快捷标签追加 + 自由输入；错误留在弹窗内。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteDialog(title: String, initial: String, tags: List<String>, onDismiss: () -> Unit, onSave: (String, Done) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    PosDialog(title, onDismiss, maxWidth = 480.dp, footer = {
        PosButton("清空", { text = "" }, variant = BtnVariant.Text)
        PosButton("取消", onDismiss, size = BtnSize.Large)
        PosButton("保存", { onSave(text) { e -> error = e; if (e == null) onDismiss() } }, variant = BtnVariant.Primary, size = BtnSize.Large)
    }) {
        FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { tag ->
                val on = tag in text.split("、")
                PosChip(tag, on, {
                    val parts = text.split("、").map { it.trim() }.filter { it.isNotEmpty() }
                    text = (if (on) parts - tag else parts + tag).joinToString("、")
                }, height = 36.dp)
            }
        }
        PosTextField(text, { text = it }, "备注内容", error = error, singleLine = false)
    }
}

/**
 * 选桌弹窗：按状态过滤可选桌台（换桌 / 并台 / 拆单 / 取单）；needGuests 时空闲桌需填人数。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TablePickDialog(
    title: String,
    subtitle: String,
    candidates: List<DiningTable>,
    onDismiss: () -> Unit,
    needGuests: Boolean = false,
    confirm: String = "确认",
    content: (@Composable ColumnScope.() -> Unit)? = null,
    onPick: (tableId: String, guests: Int, Done) -> Unit,
) {
    var picked by remember { mutableStateOf<DiningTable?>(null) }
    var guests by remember { mutableIntStateOf(2) }
    var error by remember { mutableStateOf<String?>(null) }
    val c = Pos.colors
    PosDialog(title, onDismiss, subtitle = subtitle, maxWidth = 640.dp, footer = {
        error?.let { Text(it, style = Pos.type.label, color = c.danger, modifier = Modifier.weight(1f)) }
        PosButton("取消", onDismiss, size = BtnSize.Large)
        PosButton(confirm, { picked?.let { t -> onPick(t.id, guests) { e -> error = e; if (e == null) onDismiss() } } }, variant = BtnVariant.Primary, size = BtnSize.Large, enabled = picked != null)
    }) {
        content?.invoke(this)
        if (candidates.isEmpty()) Text("没有可选择的桌台", style = Pos.type.body, color = c.sub, modifier = Modifier.padding(vertical = 16.dp))
        FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            candidates.forEach { t ->
                val on = picked?.id == t.id
                Card(
                    { picked = t; guests = guests.coerceIn(1, t.seats) }, Modifier.width(132.dp).semantics { selected = on }, shape = Pos.metrics.card,
                    colors = CardDefaults.cardColors(containerColor = if (on) c.tint else t.status.tone().bg), elevation = CardDefaults.cardElevation(0.dp),
                    border = if (on) androidx.compose.foundation.BorderStroke(2.dp, c.primary) else null,
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(t.id, style = Pos.type.section, color = c.ink)
                        TableStateLabel(t.status.label, t.status.tone())
                        Text("${t.seats} 座", style = Pos.type.label, color = c.sub)
                    }
                }
            }
        }
        val p = picked
        if (needGuests && p != null && p.status != TableStatus.Dining) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${p.id} 用餐人数", style = Pos.type.body, color = c.ink, modifier = Modifier.weight(1f))
                QuantityStepper(guests, { guests = (guests + it).coerceIn(1, p.seats) }, min = 1, max = p.seats)
            }
        }
    }
}

/** 小票预览：等宽排版，模拟 58mm 热敏纸。 */
@Composable
fun ReceiptDialog(title: String, lines: List<String>, onDismiss: () -> Unit, actions: @Composable () -> Unit = {}) {
    PosDialog(title, onDismiss, maxWidth = 460.dp, subtitle = "本机预览，实际打印以打印机为准", footer = {
        actions()
        PosButton("关闭", onDismiss, size = BtnSize.Large)
    }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(4.dp)).background(Pos.colors.tile).padding(16.dp)) {
            lines.forEach { Text(it, style = Pos.type.label.copy(fontFamily = FontFamily.Monospace), color = Pos.colors.ink) }
        }
    }
}
