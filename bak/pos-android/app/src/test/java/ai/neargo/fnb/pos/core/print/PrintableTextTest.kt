package ai.neargo.fnb.pos.core.print

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

/** 打到纸上的字只用 GBK 能表示的：¥ 换全角 ￥，四字节字符换 ?，常用符号不动。 */
class PrintableTextTest {
    private val gbk: Charset = Charset.forName("GBK")

    @Test fun yenBecomesFullWidthAndRareCharsAreReplaced() {
        assertEquals("应收 ￥8.00", EscPos.printable("应收 ¥8.00"))
        assertEquals("面 ?", EscPos.printable("面 😀"))
        assertEquals("可乐 ×2 · 改价 ¥3.50 → ¥3.80".replace("¥", "￥"), EscPos.printable("可乐 ×2 · 改价 ¥3.50 → ¥3.80"))
    }

    @Test fun encodedBytesAreAllGbk() {
        val receipt = EscPos.encode(listOf("虹选便利 · 科技园店", "富士苹果  632g  ¥8.09", "应收 ¥8.00 😀"))
        val label = Tspl.encode(listOf("TEXT 250,220,\"TSS24.BF2\",0,2,2,\"¥3.50\""))
        listOf(receipt, label).forEach { bytes ->
            // GB18030 的四字节字符以 0x81–0xFE 开头、第二字节 0x30–0x39；不应出现
            val four = (0 until bytes.size - 1).any { i -> (bytes[i].toInt() and 0xFF) in 0x81..0xFE && (bytes[i + 1].toInt() and 0xFF) in 0x30..0x39 && i > 0 && (bytes[i - 1].toInt() and 0xFF) < 0x80 }
            assertFalse(four)
        }
        assertTrue(String(label, gbk).contains("￥3.50"))
    }
}
