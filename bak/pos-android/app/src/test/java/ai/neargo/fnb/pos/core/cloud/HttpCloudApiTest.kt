package ai.neargo.fnb.pos.core.cloud

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** HTTP 层：请求头（令牌、门店、员工、幂等键）、body 字段、信封解析、错误码分类、断网 */
class HttpCloudApiTest {
    private lateinit var server: HttpServer
    private val seen = mutableListOf<Pair<Map<String, String>, String>>()
    private var reply: (String) -> Pair<Int, String> = { 200 to "{}" }

    @Before fun up() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val h = ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            synchronized(seen) { seen += h to (ex.requestURI.toString() + " " + body) }
            val (status, text) = reply(ex.requestURI.path)
            val bytes = text.toByteArray(); ex.sendResponseHeaders(status, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }
    @After fun down() = server.stop(0)

    private fun api(base: String = "http://127.0.0.1:${server.address.port}/api") =
        HttpCloudApi({ CloudConfig(base, "NS002", "tok", { "S001" }) }, connectTimeoutMs = 500, readTimeoutMs = 1000)

    @Test fun openTableSendsHeadersAndParsesEnvelope() = runBlocking {
        reply = { 200 to """{"code":0,"msg":"ok","data":{"check":{"checkNo":"C1","status":"OPEN","tableNos":["t-1"],"guestCount":2,"version":1,"extra":1},
            "table":{"tableNo":"t-1","name":"H01","status":"IN_USE","checkNo":"C1","version":2}}}""" }
        val r = api().openTable("NS002", "ev-1", "t-1", 2)
        assertEquals("C1", r.check.checkNo); assertEquals(2L, r.table.version)
        val (h, req) = seen.single()
        assertEquals("Bearer tok", h["authorization"]); assertEquals("NS002", h["x-store-no"]); assertEquals("S001", h["x-operator"]); assertEquals("ev-1", h["idempotency-key"])
        assertTrue(req.startsWith("/api/table/open ")); assertTrue(req.contains("\"tableNo\":\"t-1\"")); assertTrue(req.contains("\"guestCount\":2")); assertFalse(req.contains("idempotencyKey"))   // 幂等键只走请求头
    }

    @Test fun tableListReadsPagedRecordsWithQuery() = runBlocking {
        reply = { 200 to """{"code":0,"data":{"records":[{"tableNo":"t-1","name":"H01","status":"IDLE","version":1}],"total":1}}""" }
        assertEquals("H01", api().tableList("NS002").single().name)
        assertTrue(seen.single().second.startsWith("/api/table/list?storeNo=NS002&page=1&size=500"))
        assertNull(seen.single().first["idempotency-key"])
    }

    @Test fun businessCodesAreClassified() = runBlocking {
        for ((code, kind) in listOf(10409 to CloudErrorKind.Conflict, 10401 to CloudErrorKind.Unauthorized, 10404 to CloudErrorKind.NotFound, 10400 to CloudErrorKind.Rejected)) {
            reply = { 200 to """{"code":$code,"msg":"m$code","data":null}""" }
            val e = runCatching { api().checkDetail("NS002", "C1") }.exceptionOrNull() as CloudException
            assertEquals(kind, e.kind); assertEquals(code, e.code)
        }
        reply = { 200 to """{"code":10500,"msg":"处理中","data":null}""" }
        assertEquals(CloudErrorKind.Network, (runCatching { api().storeInfo("NS002") }.exceptionOrNull() as CloudException).kind)   // 首个请求仍在处理：可重试
        reply = { 401 to "" }
        assertEquals(CloudErrorKind.Unauthorized, (runCatching { api().storeInfo("NS002") }.exceptionOrNull() as CloudException).kind)
        reply = { 503 to "busy" }
        assertEquals(CloudErrorKind.Network, (runCatching { api().storeInfo("NS002") }.exceptionOrNull() as CloudException).kind)
        reply = { 200 to "<html>" }
        assertEquals(CloudErrorKind.Network, (runCatching { api().storeInfo("NS002") }.exceptionOrNull() as CloudException).kind)
    }

    @Test fun unreachableIsNetwork() = runBlocking {
        val port = server.address.port; server.stop(0)
        val e = runCatching { api("http://127.0.0.1:$port").storeInfo("NS002") }.exceptionOrNull() as CloudException
        assertEquals(CloudErrorKind.Network, e.kind)
        val bad = runCatching { api("not a url").storeInfo("NS002") }.exceptionOrNull() as CloudException
        assertEquals(CloudErrorKind.Network, bad.kind)
    }
}
