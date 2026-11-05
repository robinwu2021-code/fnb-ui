package ai.neargo.fnb.pos.core.designsystem.component

import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.PosSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 按钮变体（§6.1）。一个操作区只有一个 Primary。
 * 旧 B 端映射：pay/main→Primary，send/soft→Soft，sec→Secondary，ghost→Text，danger→Danger。
 */
enum class BtnVariant { Primary, Secondary, Soft, Text, Danger }

/** 视觉高度：small 36、medium 40、large 48。 */
enum class BtnSize(val height: Dp, val padH: Dp) { Small(PosSize.small, 10.dp), Medium(PosSize.medium, 12.dp), Large(PosSize.large, 16.dp) }

/**
 * PosButton：基于 Material 3 Button / TextButton。扁平：零阴影、无描边；
 * 选中只换底色（主题浅底 + 主色字），按下由 M3 涟漪反馈。loading 时禁止重复提交。
 */
@Composable
fun PosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BtnVariant = BtnVariant.Secondary,
    size: BtnSize = BtnSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    selected: Boolean = false,
    textStyle: TextStyle? = null,
) {
    val c = Pos.colors
    val (bg, fg) = when {
        selected -> c.tint to c.primary
        else -> when (variant) {
            BtnVariant.Primary -> c.primary to c.onPrimary
            BtnVariant.Secondary -> c.control to c.ink
            BtnVariant.Soft -> c.tint to c.primary
            BtnVariant.Text -> Color.Transparent to c.primary
            BtnVariant.Danger -> c.dangerBg to c.danger
        }
    }
    val style = (textStyle ?: Pos.type.button).let { if (variant == BtnVariant.Primary || selected) it.copy(fontWeight = FontWeight.SemiBold) else it }
    val content: @Composable () -> Unit = {
        if (loading) {
            CircularProgressIndicator(Modifier.size(14.dp), color = fg, strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
    val m = modifier.height(size.height).semantics { this.selected = selected }
    if (variant == BtnVariant.Text && !selected) {
        TextButton(
            onClick, m, enabled = enabled && !loading, shape = Pos.metrics.button,
            colors = ButtonDefaults.textButtonColors(contentColor = fg, disabledContentColor = fg.copy(alpha = 0.45f)),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) { content() }
    } else {
        Button(
            onClick, m, enabled = enabled && !loading, shape = Pos.metrics.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = bg, contentColor = fg,
                disabledContainerColor = bg.copy(alpha = bg.alpha * 0.5f), disabledContentColor = fg.copy(alpha = 0.6f),
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            contentPadding = PaddingValues(horizontal = size.padH),
        ) { content() }
    }
}

/** PosIconButton：基于 M3 FilledIconButton / IconButton，视觉 40 方块；必须带本地化名称。 */
@Composable
fun PosIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    filled: Boolean = true,
    size: Dp = PosSize.medium,
) {
    val c = Pos.colors
    val m = modifier.size(size).semantics { this.selected = selected }
    if (filled || selected) {
        FilledIconButton(
            onClick, m, shape = Pos.metrics.button,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (selected) c.tint else c.control, contentColor = if (selected) c.primary else c.ink,
            ),
        ) { Icon(icon, label, Modifier.size(20.dp)) }
    } else {
        IconButton(onClick, m, colors = IconButtonDefaults.iconButtonColors(contentColor = c.ink)) { Icon(icon, label, Modifier.size(20.dp)) }
    }
}
