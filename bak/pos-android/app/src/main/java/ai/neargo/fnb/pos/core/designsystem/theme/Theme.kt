package ai.neargo.fnb.pos.core.designsystem.theme

import ai.neargo.fnb.pos.core.model.ButtonShape
import ai.neargo.fnb.pos.core.model.Density
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import ai.neargo.fnb.pos.core.model.PanelShape
import ai.neargo.fnb.pos.core.model.FontChoice
import ai.neargo.fnb.pos.R
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import ai.neargo.fnb.pos.core.model.Skin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 角色字号（§4.2，标准档 × 字号倍率）。400 正文、500 按钮、600 标题与菜名，700 只给核心金额。
 * 关键状态与操作文字不低于 12sp。
 */
@Immutable
class PosTypography(private val f: Float, private val family: FontFamily? = null, private val headingFamily: FontFamily? = family) {
    private fun s(v: Int, lh: Float, w: FontWeight = FontWeight.Normal) =
        TextStyle(fontSize = (v * f).sp, lineHeight = (v * f * lh).sp, fontWeight = w, fontFamily = family)

    val title = s(20, 1.3f, FontWeight.SemiBold).copy(fontFamily = headingFamily)
    val tableNo = s(22, 1.3f, FontWeight.SemiBold)
    val hero = s(28, 1.2f, FontWeight.SemiBold).copy(fontFamily = headingFamily)
    val body = s(14, 1.5f)
    val strong = s(14, 1.4f, FontWeight.SemiBold)
    val line = s(15, 1.4f, FontWeight.SemiBold)
    val button = s(14, 1.3f, FontWeight.Medium)
    val header = s(13, 1.3f, FontWeight.Medium)
    val label = s(12, 1.4f)
    val section = s(14, 1.4f, FontWeight.SemiBold).copy(fontFamily = headingFamily)
    val amount = s(26, 1.2f, FontWeight.Bold)
    val amountM = s(24, 1.2f, FontWeight.SemiBold)
}

/** Bundled variable fonts (OFL); missing Chinese glyphs use the Android system fallback. */
@OptIn(ExperimentalTextApi::class)
private fun bundledFont(resource: Int) = FontFamily(listOf(400, 500, 600, 700).map { w ->
    Font(resource, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
})

val Manrope = bundledFont(R.font.manrope)
val Inter = bundledFont(R.font.inter)
val Roboto = bundledFont(R.font.roboto)
val NotoSans = bundledFont(R.font.notosans)
val IBMPlexSans = bundledFont(R.font.ibmplexsans)
val Outfit = bundledFont(R.font.outfit)

fun FontChoice.typography(scale: Float): PosTypography {
    val body = when (this) {
        FontChoice.Manrope -> Manrope
        FontChoice.Inter, FontChoice.Outfit -> Inter
        FontChoice.Roboto -> Roboto
        FontChoice.NotoSans -> NotoSans
        FontChoice.IBMPlexSans -> IBMPlexSans
        FontChoice.System -> null
    }
    return PosTypography(scale, body, if (this == FontChoice.Outfit) Outfit else body)
}

/** 金额、数量、桌号用等宽数字。 */
fun TextStyle.num() = copy(fontFeatureSettings = "tnum")

@Immutable
data class PosMetrics(val gap: Dp, val pad: Dp, val panel: Shape, val card: Shape, val button: Shape)

/** 控件视觉高度（§6.1）。小于 48dp 的可点元素由 Compose 命中扩展补足触控热区。 */
object PosSize {
    val small = 36.dp
    val medium = 40.dp
    val large = 48.dp
    val header = 58.dp
    val footer = 36.dp
    val rail = 88.dp
}

val LocalPosColors = staticCompositionLocalOf { Skin.Pine.colors() }
val LocalPosType = staticCompositionLocalOf { PosTypography(1f) }
val LocalPosMetrics = staticCompositionLocalOf { metricsOf(DisplayPrefs()) }

fun metricsOf(p: DisplayPrefs): PosMetrics {
    val (gap, pad) = when (p.density) {
        Density.Compact -> 8.dp to 12.dp
        Density.Standard -> 20.dp to 20.dp
        Density.Airy -> 24.dp to 24.dp
    }
    val (panel, card) = when (p.panelShape) {
        PanelShape.Square -> 0.dp to 0.dp
        PanelShape.Sharp -> 4.dp to 2.dp
        PanelShape.Standard -> 8.dp to 4.dp
        PanelShape.Round -> 16.dp to 12.dp
    }
    val button = when (p.buttonShape) {
        ButtonShape.Square -> 0.dp
        ButtonShape.Small -> 4.dp
        ButtonShape.Medium -> 8.dp
        ButtonShape.Standard -> 12.dp
        ButtonShape.Pill -> 999.dp
    }
    return PosMetrics(gap, pad, RoundedCornerShape(panel), RoundedCornerShape(card), RoundedCornerShape(button))
}

/**
 * PosTheme：把显示偏好映射为颜色、字号、间距、形状，并同步给 Material 3。
 * Material 组件（Button、FilterChip、Card、Sheet、Switch…）直接使用这些角色色，扁平无阴影。
 */
@Composable
fun PosTheme(prefs: DisplayPrefs, content: @Composable () -> Unit) {
    val c = prefs.skin.colors()
    val t = prefs.font.typography(prefs.scale.factor)
    val m = metricsOf(prefs)
    val scheme = lightColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.tint, onPrimaryContainer = c.primary,
        secondaryContainer = c.tint, onSecondaryContainer = c.primary,
        background = c.canvas, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.tile, onSurfaceVariant = c.sub, surfaceTint = c.surface,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.control,
        outline = c.divider, outlineVariant = c.divider, error = c.danger, scrim = c.scrim,
    )
    CompositionLocalProvider(LocalPosColors provides c, LocalPosType provides t, LocalPosMetrics provides m, ai.neargo.fnb.pos.core.designsystem.component.LocalLang provides prefs.lang) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(bodyLarge = t.body, bodyMedium = t.body, labelLarge = t.button, labelMedium = t.label, titleLarge = t.title),
            shapes = Shapes(small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(12.dp)),
            content = content,
        )
    }
}

/** 主题入口：Pos.colors / Pos.type / Pos.metrics。 */
object Pos {
    val colors: PosColors @Composable get() = LocalPosColors.current
    val type: PosTypography @Composable get() = LocalPosType.current
    val metrics: PosMetrics @Composable get() = LocalPosMetrics.current
}
