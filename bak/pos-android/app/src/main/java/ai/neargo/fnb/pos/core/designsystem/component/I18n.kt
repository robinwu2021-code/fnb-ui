package ai.neargo.fnb.pos.core.designsystem.component

import ai.neargo.fnb.pos.core.model.Lang
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * 界面翻译（中文为源语言）：
 * 1. 精确匹配词条；2. 模板匹配（{0}{1}… 占位，捕获内容递归翻译）；3. 按分隔符切段逐段翻译。
 * 词条位于 assets/i18n/en.json（键为源码中的中文模板）。阶段三如需更多语言，按同样结构增加文件。
 */
object Translator {
    private var exact: Map<String, String> = emptyMap()
    private var templates: List<Pair<Regex, String>> = emptyList()
    private val cache = ConcurrentHashMap<String, String>()
    private val separators = Regex("( · |，|、|：|；| / |（|）)")

    /** 读取词条；任何解析错误都只回退为中文显示，不影响启动。 */
    fun load(context: Context) {
        runCatching { doLoad(context) }.onFailure { android.util.Log.w("Translator", "i18n load failed", it) }
    }

    private fun doLoad(context: Context) {
        if (exact.isNotEmpty()) return
        loadJson(context.assets.open("i18n/en.json").bufferedReader().use { it.readText() })
    }

    /** 从 JSON 文本加载（纯 Kotlin，可单测）。 */
    fun loadJson(json: String) {
        cache.clear()
        val obj = Json.parseToJsonElement(json).jsonObject
        val ex = HashMap<String, String>()
        val tpl = ArrayList<Pair<Regex, String>>()
        obj.forEach { (k, e) ->
            val v = e.jsonPrimitive.content
            if (Regex("\\{\\d+\\}").containsMatchIn(k)) {
                // 占位符按出现顺序编号：捕获组 i 对应 {i}
                val parts = k.split(Regex("\\{\\d+\\}"))
                val order = Regex("\\{(\\d+)\\}").findAll(k).map { it.groupValues[1].toInt() }.toList()
                val pattern = parts.joinToString("(.+?)") { Regex.escape(it) }
                var out = v
                order.forEachIndexed { gi, idx -> out = out.replace("{$idx}", "\u0000${gi + 1}\u0000") }
                tpl += Regex("^$pattern$", RegexOption.DOT_MATCHES_ALL) to out
            } else ex[k] = v
        }
        exact = ex
        // 字面量越长越具体，优先匹配
        templates = tpl.sortedByDescending { it.first.pattern.length }
    }

    fun en(s: String): String {
        if (s.isEmpty() || s.none { it.code in 0x4E00..0x9FFF }) return s
        return cache.getOrPut(s) { exact[s] ?: template(s) ?: segments(s) }
    }

    private fun template(s: String): String? {
        for ((re, out) in templates) {
            val m = re.matchEntire(s) ?: continue
            var r = out
            m.groupValues.drop(1).forEachIndexed { i, g -> r = r.replace("\u0000${i + 1}\u0000", en(g)) }
            return r
        }
        return null
    }

    private fun segments(s: String): String {
        val tokens = mutableListOf<String>()
        var last = 0
        separators.findAll(s).forEach { m -> tokens += s.substring(last, m.range.first); tokens += m.value; last = m.range.last + 1 }
        tokens += s.substring(last)
        if (tokens.size == 1) return exact[s.trim()]?.let { s.replace(s.trim(), it) } ?: s
        return tokens.joinToString("") { tk ->
            when (tk) {
                " · ", " / " -> tk
                "，" -> ", "; "、" -> ", "; "：" -> ": "; "；" -> "; "; "（" -> " ("; "）" -> ")"
                else -> exact[tk.trim()]?.let { tk.replace(tk.trim(), it) } ?: template(tk) ?: tk
            }
        }
    }
}

val LocalLang = staticCompositionLocalOf { Lang.Zh }

/** 按当前语言翻译展示文本（用于图标名称、无障碍描述等非 Text 场景）。 */
@Composable
fun tr(s: String): String = if (LocalLang.current == Lang.En) Translator.en(s) else s

/**
 * 统一文本组件：参数与 Material 3 Text 一致，渲染前按当前语言翻译。
 * 业务代码引用本组件，切换语言无需逐处修改。
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = tr(text), modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, style = style,
    )
}
