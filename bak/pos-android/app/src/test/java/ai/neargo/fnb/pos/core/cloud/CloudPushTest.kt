package ai.neargo.fnb.pos.core.cloud

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** 门店推送：SSE 解析、终端登记、Last-Event-ID 续传、reset、定向消息去重与回执、端号门店不一致重登记 */
class CloudPushTest {
    private class Store : PushStore {
        val terminals = mutableMapOf<String, String>(); val last = mutableMapOf<String, String>()
        override fun installId() = "install-1"
        override fun terminalNo(storeNo: String) = terminals[storeNo]
        override fun saveTerminalNo(storeNo: String, terminalNo: String?) { if (terminalNo == null) terminals.remove(storeNo) else terminals[storeNo] = terminalNo }
        override fun lastEventId(storeNo: String) = last[storeNo]
        override fun saveLastEventId(storeNo: String, id: String) { last[storeNo] = id }
    }

    private lateinit var server: HttpServer
    private val requests = mutableListOf<Triple<String, Map<String, String>, String>>()
    private var sse: (Map<String, String>) -> Pair<Int, String> = { 200 to "" }
    private var registered = 0

    @Before fun up() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val h = ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            synchronized(requests) { requests += Triple(ex.requestMethod + " " + ex.requestURI, h, body) }
            val (status, text) = when {
                ex.requestURI.path == "/biz/push/terminals" -> 200 to """{"code":0,"data":{"terminalNo":"TN${++registered}"}}"""
                ex.requestURI.path == "/biz/push/acks" -> 200 to """{"code":0,"data":{"applied":2}}"""
                else -> sse(h)
            }
            val bytes = text.toByteArray(); ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        server.start()
    }
    @After fun down() = server.stop(0)

    private fun push(store: Store, frames: MutableList<PushFrame> = mutableListOf(), resets: IntArray = IntArray(1)) = CloudPush(
        CloudHttp({ CloudConfig("http://127.0.0.1:${server.address.port}", "NS002", "tok") }), store, "0.1.0",
        onFrame = { frames += it }, onReset = { resets[0]++ })

    @Test fun parserHandlesIdsEventsCommentsAndMultiline() {
        val p = SseParser()
        val out = listOf(": heartbeat", "id: 7", "event: push", "data: {\"seq\":7,", "data: \"topic\":\"trade.order\"}", "", "", "event: message", "data: x", "")
            .mapNotNull { p.feed(it) }
        assertEquals(2, out.size)
        assertEquals(SseEvent("7", "push", "{\"seq\":7,\n\"topic\":\"trade.order\"}"), out[0])
        assertEquals(SseEvent(null, "message", "x"), out[1])
    }

    @Test fun registersSubscribesResumesAndAcks() = runBlocking {
        sse = { _ -> 200 to "id: 41\nevent: push\ndata: {\"seq\":41,\"topic\":\"trade.order\",\"refType\":\"ORDER\",\"refNo\":\"O1\",\"refVersion\":2}\n\n" +
            "event: message\ndata: {\"messageNo\":\"M1\",\"kind\":\"app.upgrade\"}\n\n" +
            "event: message\ndata: {\"messageNo\":\"M1\",\"kind\":\"app.upgrade\"}\n\n" }
        val store = Store(); val frames = mutableListOf<PushFrame>()
        val p = push(store, frames)
        p.connectOnce()
        assertEquals("O1", frames.single().refNo)
        assertEquals("41", store.last["NS002"]); assertEquals("TN1", store.terminals["NS002"])
        val reg = requests.first { it.first == "POST /biz/push/terminals" }
        assertTrue(reg.third.contains("\"clientType\":\"POS\"") && reg.third.contains("\"installId\":\"install-1\""))
        val sub = requests.first { it.first.startsWith("GET /biz/stores/NS002/push/stream") }
        assertTrue(sub.first.contains("terminalNo=TN1")); assertNull(sub.second["last-event-id"])
        val acks = requests.filter { it.first == "POST /biz/push/acks" }
        assertEquals(1, acks.size)                                           // 同一 messageNo 只回执一次
        assertTrue(acks[0].third.contains("RECEIVED") && acks[0].third.contains("UNSUPPORTED_KIND"))

        p.connectOnce()                                                     // 服务端断开后重连：带 Last-Event-ID，不重登记
        val again = requests.last { it.first.startsWith("GET /biz/stores/NS002/push/stream") }
        assertEquals("41", again.second["last-event-id"]); assertEquals(1, registered)
        assertEquals(1, p.state.value.let { if (it.received >= 1) 1 else 0 })
    }

    @Test fun resetSavesHeadAndAsksForReload() = runBlocking {
        sse = { _ -> 200 to "event: reset\ndata: {\"head\":99}\n\n" }
        val store = Store(); val resets = IntArray(1)
        push(store, resets = resets).connectOnce()
        assertEquals(1, resets[0]); assertEquals("99", store.last["NS002"])
    }

    @Test fun storeMismatchReregisters() = runBlocking {
        val store = Store().apply { terminals["NS002"] = "OLD" }
        sse = { _ -> if (requests.count { it.first.startsWith("GET") } == 1) 400 to "" else 200 to "" }
        push(store).connectOnce()
        assertEquals("TN1", store.terminals["NS002"])
        assertTrue(requests.last { it.first.startsWith("GET") }.first.contains("terminalNo=TN1"))
    }
}
