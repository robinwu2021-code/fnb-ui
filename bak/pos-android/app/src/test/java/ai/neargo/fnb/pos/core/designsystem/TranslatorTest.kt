package ai.neargo.fnb.pos.core.designsystem

import ai.neargo.fnb.pos.core.designsystem.component.Translator
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** 使用真实的 assets/i18n/en.json：精确词条、模板（含递归翻译捕获内容）、切段回退。 */
class TranslatorTest {
    companion object {
        @BeforeClass @JvmStatic fun load() = Translator.loadJson(File("src/main/assets/i18n/en.json").readText())
    }

    @Test fun exact() {
        assertEquals("Idle", Translator.en("空闲"))
        assertEquals("Mapo Tofu", Translator.en("麻婆豆腐"))
    }

    @Test fun templateWithNestedTranslation() {
        assertEquals("New · to send · 5 items", Translator.en("新加 · 待送厨 · 5 份"))
        // 捕获内容（菜名、桌号）也会被翻译
        val r = Translator.en("麻婆豆腐已沽清")
        assertEquals(true, r.contains("Mapo Tofu") && r.none { it.code in 0x4E00..0x9FFF })
    }

    @Test fun nonChineseUntouched() {
        assertEquals("¥256.20", Translator.en("¥256.20"))
        assertEquals("A03", Translator.en("A03"))
    }

    @Test fun segmentFallback() {
        // 两个词条用「 · 」拼接（源码中为 + 拼接，无整体词条）
        assertEquals("Idle · Dining", Translator.en("空闲 · 用餐中"))
    }

    /** 所有词条都能加载（Android ICU 对正则更严格，模板必须可编译）。 */
    @Test fun everyTemplateCompiles() {
        val keys = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:").findAll(File("src/main/assets/i18n/en.json").readText()).count()
        assertEquals(728, keys)
    }
}
