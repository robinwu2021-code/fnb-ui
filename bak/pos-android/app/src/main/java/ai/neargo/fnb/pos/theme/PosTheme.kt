package ai.neargo.fnb.pos.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalPosColors = staticCompositionLocalOf { posColors(Skin.Forest, dark = false) }

/** 字号八档（tokens.json txt-*）。700 只给金额，600 给标题、菜名、按钮，其余 400。 */
object PosType {
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val sub = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val strong = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
    val title = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val display = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val hero = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    val mega = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
}

/** 金额、数量、桌号用等宽数字（sh-num）。 */
fun TextStyle.num() = copy(fontFeatureSettings = "tnum")

/**
 * 密度两档（《功能区与方案》第四节）：紧凑（默认）控件 44、卡片 88、间距 8；标准控件 48、卡片 108、间距 12。
 * 紧凑档也不低于 44 触控下限。
 */
enum class Density(val label: String) { Compact("紧凑"), Standard("标准") }

@Immutable
data class PosMetrics(
    val gap: Dp,
    val ctlS: Dp,
    val ctl: Dp,
    val ctlL: Dp,
    /** 文字视图的菜品卡高（pos-tile-h）。 */
    val tileH: Dp,
    /** 竖向导航栏宽。 */
    val railW: Dp,
)

fun posMetrics(d: Density) = when (d) {
    Density.Compact -> PosMetrics(gap = 8.dp, ctlS = 36.dp, ctl = 44.dp, ctlL = 52.dp, tileH = 88.dp, railW = 96.dp)
    Density.Standard -> PosMetrics(gap = 12.dp, ctlS = 40.dp, ctl = 48.dp, ctlL = 56.dp, tileH = 108.dp, railW = 108.dp)
}

val LocalPosMetrics = staticCompositionLocalOf { posMetrics(Density.Compact) }

/** 尺寸：随密度变化的值从 LocalPosMetrics 取；圆角和系统栏固定。 */
object PosDims {
    val gap: Dp @Composable get() = LocalPosMetrics.current.gap
    val ctlS: Dp @Composable get() = LocalPosMetrics.current.ctlS
    val ctl: Dp @Composable get() = LocalPosMetrics.current.ctl
    val ctlL: Dp @Composable get() = LocalPosMetrics.current.ctlL
    val tileH: Dp @Composable get() = LocalPosMetrics.current.tileH
    val railW: Dp @Composable get() = LocalPosMetrics.current.railW
    val sysBar = 56.dp
    val radiusSm = RoundedCornerShape(8.dp)
    val radiusMd = RoundedCornerShape(12.dp)
    val radiusLg = RoundedCornerShape(16.dp)
    val radiusFull = RoundedCornerShape(50)
}

@Composable
fun PosTheme(skin: Skin, dark: Boolean, density: Density = Density.Compact, content: @Composable () -> Unit) {
    val c = posColors(skin, dark)
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryTint,
            onPrimaryContainer = c.primaryText, background = c.bg, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.faint, onSurfaceVariant = c.sub,
            surfaceContainer = c.elev, surfaceContainerHigh = c.elev, surfaceContainerLow = c.surface,
            outline = c.line, outlineVariant = c.line, error = c.danger, scrim = c.scrim,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryTint,
            onPrimaryContainer = c.primaryText, background = c.bg, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.faint, onSurfaceVariant = c.sub,
            surfaceContainer = c.elev, surfaceContainerHigh = c.elev, surfaceContainerLow = c.surface,
            outline = c.line, outlineVariant = c.line, error = c.danger, scrim = c.scrim,
        )
    }
    CompositionLocalProvider(LocalPosColors provides c, LocalPosMetrics provides posMetrics(density)) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                small = PosDims.radiusSm,
                medium = PosDims.radiusMd,
                large = PosDims.radiusLg,
                extraLarge = PosDims.radiusLg,
            ),
            content = content,
        )
    }
}

object Pos {
    val colors: PosColors
        @Composable get() = LocalPosColors.current
}
