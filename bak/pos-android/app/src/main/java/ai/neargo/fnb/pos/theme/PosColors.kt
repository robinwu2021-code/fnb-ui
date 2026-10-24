package ai.neargo.fnb.pos.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 角色 token，取值与 pos-ds/project/tokens.json（v2）一致。
 * 组件只引用角色，不直接写色值；换皮肤只换 primary 四个值。
 */
@Immutable
data class PosColors(
    val bg: Color,
    val surface: Color,
    val elev: Color,
    val faint: Color,
    val line: Color,
    val ink: Color,
    val sub: Color,
    val scrim: Color,
    val primary: Color,
    val primaryTint: Color,
    val primaryText: Color,
    val onPrimary: Color,
    val success: Color = Color(0xFF12B76A),
    val successTint: Color = Color(0x2412B76A),
    val successText: Color,
    val warning: Color = Color(0xFFF79009),
    val warningTint: Color = Color(0x24F79009),
    val warningText: Color,
    val danger: Color = Color(0xFFF04438),
    val dangerTint: Color = Color(0x24F04438),
    val dangerText: Color,
    val infoTint: Color,
    val infoText: Color,
    val isDark: Boolean,
)

/** 皮肤：浅色与深色各四个主色值。 */
enum class Skin(
    val label: String,
    val light: SkinValues,
    val dark: SkinValues,
) {
    Forest("松绿", SkinValues(0xFF216857, 0xFF1B5748, 0xFFFFFFFF), SkinValues(0xFF5CC0A0, 0xFF6FD0B0, 0xFF06201A)),
    Indigo("靛蓝", SkinValues(0xFF3855A5, 0xFF2F4890, 0xFFFFFFFF), SkinValues(0xFF8EA6EC, 0xFFA3B6F0, 0xFF0B1430)),
    Amber("暖橙", SkinValues(0xFF96521A, 0xFF7F4515, 0xFFFFFFFF), SkinValues(0xFFE3A066, 0xFFEAB07C, 0xFF2A1606)),
    Wine("绛红", SkinValues(0xFF93374C, 0xFF7D2E40, 0xFFFFFFFF), SkinValues(0xFFE58EA4, 0xFFECA2B4, 0xFF2B0D15)),
    Graphite("石墨", SkinValues(0xFF424E5E, 0xFF38424F, 0xFFFFFFFF), SkinValues(0xFFB3BFCF, 0xFFC3CDDB, 0xFF111821)),
    Tea("茶青", SkinValues(0xFF4D6A2B, 0xFF405924, 0xFFFFFFFF), SkinValues(0xFFA8C76E, 0xFFB6D282, 0xFF141D07)),
    Petrol("黛蓝", SkinValues(0xFF1F5F7A, 0xFF1A5167, 0xFFFFFFFF), SkinValues(0xFF6CC0DE, 0xFF86CDE6, 0xFF04202B)),
}

data class SkinValues(val primary: Long, val primaryText: Long, val onPrimary: Long)

fun posColors(skin: Skin, dark: Boolean): PosColors {
    val s = if (dark) skin.dark else skin.light
    val primary = Color(s.primary)
    return if (!dark) {
        PosColors(
            bg = Color(0xFFECEDEF),
            surface = Color(0xFFFFFFFF),
            elev = Color(0xFFFFFFFF),
            faint = Color(0xFFE4E5E8),
            line = Color(0xFFDCDEE2),
            ink = Color(0xFF17181A),
            sub = Color(0xFF63676E),
            scrim = Color(0x800C0E12),
            primary = primary,
            primaryTint = primary.copy(alpha = 0.12f),
            primaryText = Color(s.primaryText),
            onPrimary = Color(s.onPrimary),
            successText = Color(0xFF067647),
            warningText = Color(0xFFB54708),
            dangerText = Color(0xFFB42318),
            infoTint = Color(0x242E90FA),
            infoText = Color(0xFF175CD3),
            isDark = false,
        )
    } else {
        PosColors(
            bg = Color(0xFF000000),
            surface = Color(0xFF161719),
            elev = Color(0xFF1D2023),
            faint = Color(0xFF23262B),
            line = Color(0xFF2E3036),
            ink = Color(0xFFF3F4F6),
            sub = Color(0xFF9AA1AC),
            scrim = Color(0xA8000000),
            primary = primary,
            primaryTint = primary.copy(alpha = 0.20f),
            primaryText = Color(s.primaryText),
            onPrimary = Color(s.onPrimary),
            successText = Color(0xFF47CD89),
            warningText = Color(0xFFFDB022),
            dangerText = Color(0xFFF97066),
            infoTint = Color(0x332E90FA),
            infoText = Color(0xFF84CAFF),
            isDark = true,
        )
    }
}
