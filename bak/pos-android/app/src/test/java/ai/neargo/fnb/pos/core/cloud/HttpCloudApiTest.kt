package ai.neargo.fnb.pos.core.cloud

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** HTTP 层（Biz 接口）：路径、请求头（令牌、门店、员工、幂等键）、body 字段、信封解析、错误码分类、断网 */
class HttpCloudApiTest {
    private lateinit var server: HttpServer
    private val seen = mutableListOf<Triple<String, Map<String, String>, String>>()
    private var reply: (String) -> Pair<Int, String> = { 200 to "{}" }

    @Before fun up() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val h = ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            synchronized(seen) { seen += Triple(ex.requestMethod + " " + ex.requestURI.toString(), h, body) }
            val (status, text) = reply(ex.requestURI.path)
            val bytes = text.toByteArray(); ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong()); if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        server.start()
    }
    @After fun down() = server.stop(0)

    private fun base() = "http://127.0.0.1:${server.address.port}"
    private fun api(base: String = base()) = HttpCloudApi({ CloudConfig(base, "NS002", "tok", { "S001" }) }, connectTimeoutMs = 500, readTimeoutMs = 1000)

    @Test fun openCheckUsesBizPathHeadersAndStrictBody() = runBlocking {
        reply = { 200 to """{"code":0,"msg":"success","data":{"check":{"checkNo":"C1","storeNo":"NS002","businessDate":"2026-11-12","status":"OPEN","partySize":2,"tableNos":["t-1"],"version":1,"extra":1}}}""" }
        val c = api().openCheck("NS002", "ev-1", listOf("t-1"), 2, listOf(CloudTableVersion("t-1", 3)))
        assertEquals("C1", c.checkNo); assertEquals(1L, c.version)
        val (req, h, body) = seen.single()
        assertEquals("POST /biz/x/food/stores/NS002/checks", req)
        assertEquals("Bearer tok", h["authorization"]); assertEquals("NS002", h["x-store-no"]); assertEquals("S001", h["x-operator"]); assertEquals("ev-1", h["idempotency-key"])
        assertEquals("""{"tableNos":["t-1"],"partySize":2,"tableVersions":[{"tableNo":"t-1","version":3}],"sharingMode":"EXCLUSIVE","seats":[],"payMode":"POSTPAY"}""", body)   // 不带 storeNo、不带幂等键；payMode / sharingMode / seats 是真实后端必填
    }

    @Test fun cartLineSendsNumberQtyWithoutNulls() = runBlocking {
        reply = { 200 to """{"code":0,"data":{"cartNo":"CT1","version":5}}""" }
        val v = api().replaceCartLine("ev-2:line:7", "CT1", "L7", CloudLineInput("L7", "M1", "MI1", "R1", "G1", "S1", 2), 4)
        assertEquals(5L, v.version)
        val (req, h, body) = seen.single()
        assertEquals("PUT /biz/x/food/carts/CT1/lines/L7", req); assertEquals("ev-2:line:7", h["idempotency-key"])
        assertEquals("""{"line":{"sourceLineRef":"L7","menuNo":"M1","menuItemNo":"MI1","menuRevisionNo":"R1","goodsNo":"G1","skuNo":"S1","qty":2,"selections":[],"bundleChoices":[]},"version":4}""", body)   // selections / bundleChoices 空数组是真实后端必填
    }

    @Test fun confirmQuoteHasNoIdempotencyKeyAndBatchIsPostpay() = runBlocking {
        reply = { p -> 200 to if (p.endsWith(":confirm")) """{"code":0,"data":{"quoteNo":"Q1","confirmationDigest":"d2","totals":{}}}""" else """{"code":0,"data":{"batchNo":"B1","status":"ACCEPTED","amountMinor":3800,"version":1}}""" }
        assertEquals("d2", api().confirmQuote("Q1", "CT1", 5, "d1").confirmationDigest)
        assertEquals(3800L, api().placeBatch("NS002", "ev-2:batch", "CT1", 5, "Q1", "d2", "U1").amountMinor)
        assertNull(seen[0].second["idempotency-key"]); assertEquals("POST /biz/x/food/quotes/Q1:confirm", seen[0].first)
        assertTrue(seen[1].third.contains("\"payMode\":\"POSTPAY\"") && seen[1].third.contains("\"buyerNo\":\"U1\""))
    }

    @Test fun counterSettlementEmptyBodyAndOfflineRecordStrictBody() = runBlocking {
        reply = { p -> 200 to when {
            p.endsWith("/counter-settlements") -> """{"code":0,"data":{"settlementNo":"CS1","checkNo":"C1","status":"FROZEN","currency":"CNY","dueMinor":8700,"receivedMinor":null,"items":[],"checkStatus":"OPEN","tableReleased":false}}"""
            p.endsWith(":finalize") -> """{"code":0,"data":{"settlementNo":"CS1","status":"SETTLED","dueMinor":8700,"receivedMinor":10000,"changeMinor":1300,"receiptNo":"RC1","checkStatus":"CLOSED","tableReleased":true}}"""
            else -> """{"code":0,"data":{"recordNo":"OR1","amountMinor":10000,"currency":"CNY","method":"CASH","bizNo":"CS1","status":"VALID","history":[]}}"""
        } }
        val st = api().createCounterSettlement("ev-9:settle", "C1")
        assertEquals(8700L, st.dueMinor); assertNull(st.receivedMinor)
        assertEquals("POST /biz/x/food/checks/C1/counter-settlements", seen[0].first); assertEquals("", seen[0].third); assertEquals("ev-9:settle", seen[0].second["idempotency-key"])
        val rec = api().recordOffline("ev-9:pay", CloudOfflineRecordBody(10000, "CNY", "CASH", "CS1", "POS H01", "2026-11-13T12:30:00+08:00"))
        assertEquals("OR1", rec.recordNo)
        assertEquals("POST /biz/payment/offline-records", seen[1].first)
        assertEquals("""{"amountMinor":10000,"currency":"CNY","method":"CASH","bizNo":"CS1","note":"POS H01","receivedAt":"2026-11-13T12:30:00+08:00"}""", seen[1].third)
        val done = api().finalizeCounterSettlement("CS1", listOf("OR1"))
        assertEquals(1300L, done.changeMinor); assertTrue(done.tableReleased)
        assertNull(seen[2].second["idempotency-key"]); assertEquals("""{"offlineRecordNos":["OR1"]}""", seen[2].third)
        val nulls = CloudOfflineRecordBody(500, "CNY", "EXTERNAL_POS")
        api().recordOffline("k", nulls); assertEquals("""{"amountMinor":500,"currency":"CNY","method":"EXTERNAL_POS"}""", seen[3].third)   // 空字段不发
    }

    @Test fun tableListReadsPagedRecords() = runBlocking {
        reply = { 200 to """{"code":0,"data":{"records":[{"tableNo":"t-1","storeNo":"NS002","name":"H01","capacity":2,"status":"ACTIVE","turnoverState":"IDLE","version":1}],"total":1}}""" }
        assertEquals("H01", api().tableList("NS002").single().name)
        assertEquals("GET /biz/x/food/stores/NS002/tables?page=1&size=100", seen[0].first)   // 真实后端分页上限 100
        assertNull(seen[0].second["idempotency-key"])
    }

    @Test fun storeProfileIsTheProbeAndParsesAnyFractionDigits() = runBlocking {
        reply = { 200 to """{"code":0,"data":{"storeNo":"ST-M0001","storeName":"海岸店","entityNo":"M0001","status":"ACTIVE","market":"CN","currency":"CNY","currencyScale":2,"timezone":"Asia/Shanghai","cutoverMinute":240,"payPolicy":"POSTPAY","businessDate":"2026-11-13","businessDayStartsAt":"2026-11-12T20:00:00Z","businessDayEndsAt":"2026-11-13T20:00:00Z","serverTime":"2026-11-13T14:37:35.930027Z"}}""" }
        val p = api().probe("ST-M0001")
        assertEquals("GET /biz/x/food/stores/ST-M0001/profile", seen[0].first)
        assertEquals("海岸店", p.storeName); assertEquals("CNY", p.profile.currency); assertEquals(240, p.profile.cutoverMinute)
        val at = java.time.Instant.parse("2026-11-13T14:37:35.930027Z").toEpochMilli()
        assertEquals(5_000L, p.profile.clockSkewMs(at - 5_000))
        assertEquals(1L, CloudProfile(serverTime = "2026-11-13T14:37:35.001Z").clockSkewMs(java.time.Instant.parse("2026-11-13T14:37:35Z").toEpochMilli()))   // 毫秒位
        assertNull(CloudProfile(serverTime = "").clockSkewMs(0))
        // 主体没配市场：币种为 null，其余照常
        reply = { 200 to """{"code":0,"data":{"storeNo":"S","storeName":"n","market":null,"currency":null,"currencyScale":null,"timezone":"Asia/Shanghai","cutoverMinute":0}}""" }
        assertNull(api().storeProfile("S").currency)
        assertEquals(CloudPolicy("Asia/Shanghai", 0), api().storePolicy("S"))
    }

    @Test fun businessCodesAreClassified() = runBlocking {
        for ((code, kind) in listOf(10409 to CloudErrorKind.Conflict, 10401 to CloudErrorKind.Unauthorized, 10404 to CloudErrorKind.NotFound, 10400 to CloudErrorKind.Rejected, 10500 to CloudErrorKind.Network)) {
            reply = { 200 to """{"code":$code,"msg":"m$code","data":null}""" }
            val e = runCatching { api().check("C1") }.exceptionOrNull() as CloudException
            assertEquals(kind, e.kind); assertEquals(code, e.code)
        }
        reply = { 401 to "" }
        assertEquals(CloudErrorKind.Unauthorized, (runCatching { api().probe("NS002") }.exceptionOrNull() as CloudException).kind)
        reply = { 403 to "" }
        assertEquals(CloudErrorKind.Unauthorized, (runCatching { api().probe("NS002") }.exceptionOrNull() as CloudException).kind)
        reply = { 503 to "busy" }
        assertEquals(CloudErrorKind.Network, (runCatching { api().probe("NS002") }.exceptionOrNull() as CloudException).kind)
        reply = { 200 to "<html>" }
        assertEquals(CloudErrorKind.Network, (runCatching { api().probe("NS002") }.exceptionOrNull() as CloudException).kind)
    }

    @Test fun unreachableIsNetwork() = runBlocking {
        val port = server.address.port; server.stop(0)
        assertEquals(CloudErrorKind.Network, (runCatching { api("http://127.0.0.1:$port").probe("NS002") }.exceptionOrNull() as CloudException).kind)
        assertEquals(CloudErrorKind.Network, (runCatching { api("not a url").probe("NS002") }.exceptionOrNull() as CloudException).kind)
    }
}
