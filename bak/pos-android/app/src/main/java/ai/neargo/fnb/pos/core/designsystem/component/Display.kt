package ai.neargo.fnb.pos.core.designsystem.component

import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.TableTone
import ai.neargo.fnb.pos.core.designsystem.theme.num
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 面板：M3 Surface，内容面 + 面板圆角，零阴影、零色调提升。 */
@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = Pos.colors.surface, content: @Composable () -> Unit) {
    Surface(modifier, shape = Pos.metrics.panel, color = color, tonalElevation = 0.dp, shadowElevation = 0.dp) { content() }
}

/** 数量角标：M3 Badge，主色实心；与状态标签分开。 */
@Composable
fun CountBadge(n: Int, modifier: Modifier = Modifier) {
    Badge(modifier, containerColor = Pos.colors.primary, contentColor = Pos.colors.onPrimary) {
        Text("$n", style = Pos.type.label.num().copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp))
    }
}

/** Tag：浅底小标签，不可点击，不伪装成按钮。 */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, bg: Color = Pos.colors.control, fg: Color = Pos.colors.sub) {
    Text(text, style = Pos.type.label, color = fg, maxLines = 1, modifier = modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp))
}

/** 桌台状态：圆点 + 文字，颜色不单独表达状态。 */
@Composable
fun TableStateLabel(text: String, tone: TableTone, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tone.dot))
        Spacer(Modifier.width(5.dp))
        Text(text, style = Pos.type.label, color = tone.ink, maxLines = 1, softWrap = false)
    }
}

/** 列表分隔：M3 HorizontalDivider，1dp 极淡，扁平风格下唯一的线条。 */
@Composable
fun PosDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = Pos.colors.divider)

/** 账单分组标题：轻底条，左侧说明、右侧文字操作。 */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val c = Pos.colors
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(4.dp)).background(c.tile).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Pos.type.label.num(), color = c.sub, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        if (action != null) PosButton(action, onAction, variant = BtnVariant.Text, size = BtnSize.Small, textStyle = Pos.type.label)
    }
}

/** 键值行：标签辅助色，值正文色 500，金额等宽。 */
@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Pos.colors.ink, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val t = Pos.type
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = t.label.copy(fontSize = t.body.fontSize * 0.93f), color = Pos.colors.sub)
        trailing?.invoke(this)
        Spacer(Modifier.weight(1f))
        Text(value, style = t.body.num().copy(fontWeight = FontWeight.Medium), color = valueColor)
    }
}

/** 详情区块：小标题 + 内容，区块之间 1dp 分隔。 */
@Composable
fun DetailBlock(title: String, modifier: Modifier = Modifier, divider: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = Pos.type.section, color = Pos.colors.ink, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        content()
        Spacer(Modifier.size(12.dp))
        if (divider) PosDivider()
    }
}

/** 轻底分区卡（结账右侧各区）。 */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(Pos.metrics.card).background(Pos.colors.tile).padding(12.dp)) {
        Text(title, style = Pos.type.section, color = Pos.colors.ink, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

/** 空状态：说明 + 下一步。 */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.padding(vertical = 24.dp)) {
        Text(text, style = Pos.type.body, color = Pos.colors.sub)
        if (action != null) PosButton(action, onAction, variant = BtnVariant.Text)
    }
}
