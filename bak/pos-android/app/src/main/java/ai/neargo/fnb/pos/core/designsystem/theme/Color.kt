package ai.neargo.fnb.pos.core.designsystem.theme

import ai.neargo.fnb.pos.core.model.Skin
import ai.neargo.fnb.pos.core.model.TableStatus
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 颜色角色（规范 v3.5 §3）。组件只引用角色，不写色值；换皮肤只换 primary / primaryDark / tint。
 * 扁平风格：分区靠色块和留白，不靠阴影或渐变；divider 只用于列表行之间的极淡分隔。
 */
@Immutable
data class PosColors(
    val canvas: Color = Color(0xFFF3F5F4),
    val surface: Color = Color(0xFFFFFFFF),
    val tile: Color = Color(0xFFF7F9F8),
    val control: Color = Color(0xFFF0F4F2),
    val divider: Color = Color(0xFFEDF0EF),
    val ink: Color = Color(0xFF181A1C),
    val sub: Color = Color(0xFF5C5E61),
    val primary: Color,
    val primaryDark: Color,
    val tint: Color,
    val onPrimary: Color = Color.White,
    val danger: Color = Color(0xFFB70F0F),
    val dangerBg: Color = Color(0xFFFCEBEA),
    val warning: Color = Color(0xFF8F5000),
    val success: Color = Color(0xFF005C31),
    val focus: Color = Color(0xFF1C51D9),
    val scrim: Color = Color(0x660C0E12),
)

/** 皮肤主色三件套（主色 / 深主色 / 浅底），白字与主色字对比度均 ≥ 4.5:1。 */
fun Skin.colors(): PosColors {
    val (p, d, t) = when (this) {
        Skin.Pine -> Triple(0xFF306F5F, 0xFF215749, 0xFFE5F1EB)
        Skin.Mono -> Triple(0xFF242629, 0xFF111315, 0xFFECEEEF)
        Skin.Red -> Triple(0xFFB31710, 0xFF930001, 0xFFFCEBE8)
        Skin.Blue -> Triple(0xFF1C51D9, 0xFF103CB1, 0xFFEAF0FF)
        Skin.Green -> Triple(0xFF00754B, 0xFF005B39, 0xFFE2F4EB)
        Skin.Teal -> Triple(0xFF017070, 0xFF005858, 0xFFE1F2F1)
        Skin.Amber -> Triple(0xFF895500, 0xFF6C4100, 0xFFFAF0DF)
        Skin.Violet -> Triple(0xFF6A3FC6, 0xFF532CA0, 0xFFEEE9F9)
    }
    return PosColors(primary = Color(p), primaryDark = Color(d), tint = Color(t))
}

/** 桌台五态（§3.3）：整卡背景 + 状态文字，不随皮肤变化，禁止顶部色条。 */
@Immutable
data class TableTone(val bg: Color, val ink: Color, val dot: Color)

fun TableStatus.tone(): TableTone = when (this) {
    TableStatus.Idle -> TableTone(Color(0xFFFFFFFF), Color(0xFF52635C), Color(0xFF7E928B))
    TableStatus.Dining -> TableTone(Color(0xFFE5F3EB), Color(0xFF216548), Color(0xFF306F5F))
    TableStatus.Reserved -> TableTone(Color(0xFFFFF2D9), Color(0xFF8A5A14), Color(0xFFA66B23))
    TableStatus.Paid -> TableTone(Color(0xFFE8EFFB), Color(0xFF3A5E98), Color(0xFF637AAF))
    TableStatus.Cleaning -> TableTone(Color(0xFFF9E8E3), Color(0xFF995546), Color(0xFFA3584F))
}
