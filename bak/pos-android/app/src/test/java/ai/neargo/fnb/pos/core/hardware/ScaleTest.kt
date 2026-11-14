package ai.neargo.fnb.pos.core.hardware

import ai.neargo.fnb.pos.core.model.ScaleRules
import ai.neargo.fnb.pos.core.model.ScaleRules.Reading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

/** 电子秤：常见读数格式解析、切行，以及网口秤（假秤）连续输出 / 问答两种方式读数。 */
class ScaleTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }

    @Test fun parsesCommonFrames() {
        assertEquals(Reading(1234, true), ScaleRules.parse("ST,GS,+  1.234kg"))
        assertEquals(Reading(500, false), ScaleRules.parse("US,GS,+  0.500kg"))
        assertEquals(Reading(618, true), ScaleRules.parse("ST,NT,   0.618 kg"))
        assertEquals(Reading(1234, true), ScaleRules.parse("+001234 g"))
        assertEquals(Reading(862, true), ScaleRules.parse("\u0002 0.862 KG\u0003"))
        assertEquals(Reading(250, true), ScaleRules.parse("0.25"))
        assertEquals(Reading(250, true), ScaleRules.parse("250", ScaleRules.Unit.G))
        assertEquals(Reading(0, true), ScaleRules.parse("ST,GS,-  0.012kg")) // 去皮后拿走托盘
        assertTrue(ScaleRules.parse("OL,GS,+ ----- kg")!!.overload)
        assertNull(ScaleRules.parse("")); assertNull(ScaleRules.parse("READY"))
        val buf = StringBuilder()
        assertEquals(listOf("ST,GS,+1.000kg"), ScaleRules.lines(buf, "ST,GS,+1.000kg\r\nST,GS,+1."))
        assertEquals(listOf("ST,GS,+1.001kg"), ScaleRules.lines(buf, "001kg\r\n"))
    }

    /** 假网口秤：连续输出（或收到查询指令才回一帧） */
    private class FakeScale(val frames: List<String>, val pollOnly: Boolean = false) : AutoCloseable {
        val server = ServerSocket(0)
        init {
            thread(isDaemon = true) {
                val s = runCatching { server.accept() }.getOrNull() ?: return@thread
                s.use { sock ->
                    val out = sock.getOutputStream(); val inp = sock.getInputStream()
                    var i = 0
                    try {
                        while (true) {
                            if (pollOnly) { if (inp.read() != 'W'.code) continue; inp.read() }
                            out.write((frames[i % frames.size] + "\r\n").toByteArray()); out.flush(); i++
                            if (!pollOnly) Thread.sleep(50)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        override fun close() = server.close()
    }

    private fun NetworkScale.await(cond: (ScaleState) -> Boolean): ScaleState {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) { val s = state.value; if (cond(s)) return s; Thread.sleep(20) }
        return state.value
    }

    @Test fun readsContinuousOutput() {
        FakeScale(listOf("US,GS,+  0.400kg", "ST,GS,+  0.862kg")).use { fake ->
            val scale = NetworkScale(scope)
            scale.configure("127.0.0.1", fake.server.localPort, "")
            val s = scale.await { (it as? ScaleState.Live)?.reading?.stable == true }
            assertEquals(Reading(862, true), (s as ScaleState.Live).reading)
            scale.configure("", 0, ""); assertEquals(ScaleState.Off, scale.state.value)
        }
    }

    @Test fun pollsQuestionAnswerScale() {
        FakeScale(listOf("ST,GS,+  1.236kg"), pollOnly = true).use { fake ->
            val scale = NetworkScale(scope)
            scale.configure("127.0.0.1", fake.server.localPort, "W\\r")
            assertEquals(Reading(1236, true), (scale.await { (it as? ScaleState.Live)?.reading != null } as ScaleState.Live).reading)
        }
    }

    @Test fun reportsUnreachableScale() {
        val port = ServerSocket(0).use { it.localPort } // 关掉的端口
        val scale = NetworkScale(scope)
        scale.configure("127.0.0.1", port, "")
        assertTrue(scale.await { it is ScaleState.Error } is ScaleState.Error)
    }
}
