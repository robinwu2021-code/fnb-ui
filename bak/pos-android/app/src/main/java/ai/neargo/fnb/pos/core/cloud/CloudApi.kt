package ai.neargo.fnb.pos.core.cloud

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 餐饮服务端现有 Biz 接口（`/biz/x/food`）的客户端（下一批对接方案 §0：直连 Biz，不等 POS 草案）。
 * 约定（后端评审结论）：幂等键只放请求头 `Idempotency-Key`；门店只走 `X-Store-No` 头与路径里的 `{storeNo}`，
 * 不额外加 storeNo 参数或 body 字段；当班员工 `X-Operator`；数量传 JSON 数字；信封 `{code,msg,data}`。
 * 请求字段严格按服务端 DTO（服务端拒收未知字段）；响应忽略未知字段。
 */
interface CloudApi {
    /** 连通探测：取 1 张桌台（Biz 没有单独的门店信息接口） */
    suspend fun probe(storeNo: String): CloudProbe
    suspend fun tableList(storeNo: String): List<CloudTable>
    suspend fun openCheck(storeNo: String, key: String, tableNos: List<String>, partySize: Int, tableVersions: List<CloudTableVersion>): CloudCheck
    suspend fun check(checkNo: String): CloudCheck
    suspend fun transferCheck(key: String, checkNo: String, version: Long, targetTableNos: List<String>, tableVersions: List<CloudTableVersion>): CloudOperation
    /** 门店可售目录（菜单 + 菜品 + SKU），用于本机菜品 → 服务端菜单项映射 */
    suspend fun catalog(storeNo: String): CloudCatalog
    suspend fun createCart(storeNo: String, key: String, checkNo: String): CloudCart
    suspend fun cart(cartNo: String): CloudCart
    suspend fun replaceCartLine(key: String, cartNo: String, sourceLineRef: String, line: CloudLineInput, version: Long): CloudCartVersion
    suspend fun quote(storeNo: String, key: String, cartNo: String, version: Long): CloudQuote
    /** 确认报价：服务端不收幂等键，靠 confirmationDigest 自然幂等 */
    suspend fun confirmQuote(quoteNo: String, cartNo: String, version: Long, confirmationDigest: String): CloudQuote
    /** 员工下单（员工批次只能 POSTPAY） */
    suspend fun placeBatch(storeNo: String, key: String, cartNo: String, version: Long, quoteNo: String, confirmationDigest: String): CloudBatch
    suspend fun closePreview(key: String, businessDate: String, currency: String): CloudClose
    suspend fun closeSign(key: String, closeNo: String, snapshotHash: String): CloudClose
}

data class CloudProbe(val storeNo: String, val tables: Int)

/** TableVO：`status` 是启停（ACTIVE/DISABLED），`turnoverState` 才是空闲 / 用餐中 */
@Serializable data class CloudTable(val tableNo: String, val name: String, val code: String = "", val areaNo: String = "", val status: String = "ACTIVE",
    val turnoverState: String = "IDLE", val version: Long = 0)
@Serializable data class CloudTableVersion(val tableNo: String, val version: Long)
@Serializable data class CloudCheck(val checkNo: String, val storeNo: String = "", val businessDate: String = "", val status: String = "",
    val payMode: String? = null, val partySize: Int = 0, val tableNos: List<String> = emptyList(), val version: Long = 0)
@Serializable data class CloudOperation(val operationNo: String = "", val status: String = "", val version: Long = 0, val resultRef: String? = null)

@Serializable data class CloudCatalog(val storeNo: String = "", val menus: List<CloudCatalogMenu> = emptyList(), val items: List<CloudCatalogItem> = emptyList())
@Serializable data class CloudCatalogMenu(val menuNo: String, val revisionNo: String = "", val name: String = "")
@Serializable data class CloudCatalogItem(val menuNo: String, val menuItemNo: String, val goodsNo: String, val title: String, val skus: List<CloudCatalogSku> = emptyList())
@Serializable data class CloudCatalogSku(val skuNo: String, val spec: String? = null, val priceMinor: Long? = null, val currency: String? = null)

/** OrderLineInput：数量是 JSON 数字；不传单位、不传价格（价格以服务端报价为准） */
@Serializable data class CloudLineInput(val sourceLineRef: String, val menuNo: String, val menuItemNo: String, val menuRevisionNo: String,
    val goodsNo: String, val skuNo: String, val qty: Int, val seatNo: String? = null, val courseNo: String? = null)
@Serializable data class CloudCart(val cartNo: String, val checkNo: String? = null, val status: String = "", val version: Long = 0)
@Serializable data class CloudCartVersion(val cartNo: String, val version: Long)
/**
 * 平台 QuoteVO（PricingCheckoutPort.java:73-82，已由餐饮会话核实）：确认摘要为 64 位小写 hex；
 * 金额在 totals 里，**没有 payableMinor**——应付取 receivableMinor，仍欠取 remainingDueMinor。
 */
@Serializable data class CloudQuote(val quoteNo: String, val confirmationDigest: String = "", val totals: CloudQuoteTotals? = null)
@Serializable data class CloudQuoteTotals(val goodsMinor: Long = 0, val discountMinor: Long = 0, val netMinor: Long = 0, val taxMinor: Long = 0,
    val receivableMinor: Long = 0, val remainingDueMinor: Long = 0, val feeMinor: Long? = null)
@Serializable data class CloudBatch(val batchNo: String, val checkNo: String? = null, val status: String = "", val currency: String? = null,
    val amountMinor: Long? = null, val version: Long = 0)
@Serializable data class CloudClose(val closeNo: String, val status: String = "", val snapshotHash: String = "", val version: Long = 0)

/** 服务端错误分类：决定同步怎么处理（重读、交人工、重试） */
enum class CloudErrorKind {
    /** 网络不通、超时、5xx、10500（同键首个请求仍在处理）：结果未知，保留，用同一幂等键重试 */
    Network,
    /** 10401 未登录 / token 失效、HTTP 403 无权限：停止同步，提示处理 */
    Unauthorized,
    /** 10409 版本冲突：重读最新后再按本机意图重放一次 */
    Conflict,
    /** 10404 对象不存在 */
    NotFound,
    /** 其他业务错误（10400 校验等）：交人工处理 */
    Rejected,
}

class CloudException(val kind: CloudErrorKind, val code: Int, message: String) : Exception(message) {
    companion object {
        fun of(code: Int, msg: String) = CloudException(when (code) {
            10401 -> CloudErrorKind.Unauthorized; 10409 -> CloudErrorKind.Conflict; 10404 -> CloudErrorKind.NotFound
            // 10500：平台幂等「首个请求还在跑」的兜底码，应重试而不是失败（IdempotencyService / GlobalExceptionHandler）
            10500, in 50000..59999 -> CloudErrorKind.Network; else -> CloudErrorKind.Rejected
        }, code, msg.ifBlank { "服务端错误 $code" })
    }
}

/** 连接参数：服务端根地址（如 https://api.example.com，不带 /biz）、门店号、终端令牌、当前员工 */
data class CloudConfig(val baseUrl: String, val storeNo: String, val token: String, val staffNo: () -> String = { "" }) {
    val enabled get() = baseUrl.isNotBlank() && storeNo.isNotBlank()
}

/** 底层 HTTP：信封解析、错误分类、公共请求头。餐饮 Biz 与推送共用 */
class CloudHttp(private val cfg: () -> CloudConfig, private val connectTimeoutMs: Int = 3_000, private val readTimeoutMs: Int = 10_000) {
    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    val config get() = cfg()

    fun url(path: String, query: Map<String, String> = emptyMap()) = cfg().baseUrl.trimEnd('/') + path +
        (if (query.isEmpty()) "" else "?" + query.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" })

    suspend fun <T> get(path: String, query: Map<String, String>, ser: KSerializer<T>): T = call("GET", url(path, query), null, null, ser)
    suspend fun <B, T> send(method: String, path: String, key: String?, body: B, bodySer: KSerializer<B>, ser: KSerializer<T>): T =
        call(method, url(path), key, json.encodeToString(bodySer, body), ser)

    /** 打开连接并加公共头（SSE 也用） */
    fun open(url: String, method: String, key: String? = null, readTimeoutMs: Int = this.readTimeoutMs): HttpURLConnection {
        val c = cfg()
        val conn = try { URL(url).openConnection() as HttpURLConnection } catch (e: Exception) { throw CloudException(CloudErrorKind.Network, 0, "云端地址无效：${e.message}") }
        conn.requestMethod = method; conn.connectTimeout = connectTimeoutMs; conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("Accept", "application/json")
        if (c.token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${c.token}")
        conn.setRequestProperty("X-Store-No", c.storeNo)
        c.staffNo().takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("X-Operator", it) }
        key?.let { conn.setRequestProperty("Idempotency-Key", it) }
        return conn
    }

    private suspend fun <T> call(method: String, url: String, key: String?, body: String?, ser: KSerializer<T>): T = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val conn = open(url, method, key)
        try {
            if (body != null) {
                conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val text = (if (status < 400) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status == 401) throw CloudException(CloudErrorKind.Unauthorized, 10401, "登录已失效，请重新登录")
            if (status == 403) throw CloudException(CloudErrorKind.Unauthorized, 10403, "终端账号没有该门店或该操作的权限")
            if (status >= 500) throw CloudException(CloudErrorKind.Network, status, "服务端暂不可用（HTTP $status）")
            val env = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrElse { throw CloudException(CloudErrorKind.Network, status, "服务端返回格式错误") }
            val code = env["code"]?.jsonPrimitive?.intOrNull ?: throw CloudException(CloudErrorKind.Network, status, "服务端返回缺少 code")
            if (code != 0) throw CloudException.of(code, env["msg"]?.jsonPrimitive?.content.orEmpty())
            json.decodeFromJsonElement(ser, env["data"] ?: JsonNull)
        } catch (e: IOException) {
            throw CloudException(CloudErrorKind.Network, 0, "网络不通：${e.message ?: e.javaClass.simpleName}")
        } finally { conn.disconnect() }
    }
}

/** Biz 实现。路径带 `{storeNo}` 的按路径；其余靠 `X-Store-No` 头定位门店 */
class HttpCloudApi(private val http: CloudHttp) : CloudApi {
    constructor(cfg: () -> CloudConfig, connectTimeoutMs: Int = 3_000, readTimeoutMs: Int = 10_000) : this(CloudHttp(cfg, connectTimeoutMs, readTimeoutMs))

    private fun s(storeNo: String) = "$FOOD/stores/${enc(storeNo)}"

    override suspend fun probe(storeNo: String) =
        CloudProbe(storeNo, http.get("${s(storeNo)}/tables", mapOf("page" to "1", "size" to "1"), Page.serializer(CloudTable.serializer())).total)
    override suspend fun tableList(storeNo: String) =
        http.get("${s(storeNo)}/tables", mapOf("page" to "1", "size" to "500"), Page.serializer(CloudTable.serializer())).records
    override suspend fun openCheck(storeNo: String, key: String, tableNos: List<String>, partySize: Int, tableVersions: List<CloudTableVersion>) =
        http.send("POST", "${s(storeNo)}/checks", key, OpenCheckBody(tableNos, partySize, tableVersions), OpenCheckBody.serializer(), CheckEnvelope.serializer()).check
    override suspend fun check(checkNo: String) = http.get("$FOOD/checks/${enc(checkNo)}", emptyMap(), CheckEnvelope.serializer()).check
    override suspend fun transferCheck(key: String, checkNo: String, version: Long, targetTableNos: List<String>, tableVersions: List<CloudTableVersion>) =
        http.send("POST", "$FOOD/checks/${enc(checkNo)}:transfer", key, TransferBody(version, targetTableNos, tableVersions), TransferBody.serializer(), OperationEnvelope.serializer()).operation
    override suspend fun catalog(storeNo: String) = http.get("${s(storeNo)}/catalog", mapOf("orderSource" to "POS", "fulfillment" to "DINE_IN"), CloudCatalog.serializer())
    override suspend fun createCart(storeNo: String, key: String, checkNo: String) =
        http.send("POST", "${s(storeNo)}/carts", key, CreateCartBody(checkNo), CreateCartBody.serializer(), CartEnvelope.serializer()).cart
    override suspend fun cart(cartNo: String) = http.get("$FOOD/carts/${enc(cartNo)}", emptyMap(), CartEnvelope.serializer()).cart
    override suspend fun replaceCartLine(key: String, cartNo: String, sourceLineRef: String, line: CloudLineInput, version: Long) =
        http.send("PUT", "$FOOD/carts/${enc(cartNo)}/lines/${enc(sourceLineRef)}", key, ReplaceLineBody(line, version), ReplaceLineBody.serializer(), CloudCartVersion.serializer())
    override suspend fun quote(storeNo: String, key: String, cartNo: String, version: Long) =
        http.send("POST", "${s(storeNo)}/quotes", key, QuoteBody(cartNo, version), QuoteBody.serializer(), CloudQuote.serializer())
    override suspend fun confirmQuote(quoteNo: String, cartNo: String, version: Long, confirmationDigest: String) =
        http.send("POST", "$FOOD/quotes/${enc(quoteNo)}:confirm", null, ConfirmBody(cartNo, version, confirmationDigest), ConfirmBody.serializer(), CloudQuote.serializer())
    override suspend fun placeBatch(storeNo: String, key: String, cartNo: String, version: Long, quoteNo: String, confirmationDigest: String) =
        http.send("POST", "${s(storeNo)}/batches", key, BatchBody(cartNo, version, quoteNo, confirmationDigest), BatchBody.serializer(), CloudBatch.serializer())
    override suspend fun closePreview(key: String, businessDate: String, currency: String) =
        http.send("POST", "$FOOD/closes:preview", key, PreviewBody(businessDate, currency), PreviewBody.serializer(), CloudClose.serializer())
    override suspend fun closeSign(key: String, closeNo: String, snapshotHash: String) =
        http.send("POST", "$FOOD/closes:sign", key, SignBody(closeNo, snapshotHash), SignBody.serializer(), CloudClose.serializer())

    companion object {
        const val FOOD = "/biz/x/food"
        internal fun enc(v: String): String = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
    }
}

// —— 请求体（只含服务端 DTO 里有的字段）与响应包装 ——
@Serializable internal data class Page<T>(val records: List<T> = emptyList(), val total: Int = 0, val page: Int = 1, val size: Int = 0)
@Serializable internal data class OpenCheckBody(val tableNos: List<String>, val partySize: Int, val tableVersions: List<CloudTableVersion>)
@Serializable internal data class TransferBody(val version: Long, val targetTableNos: List<String>, val tableVersions: List<CloudTableVersion>)
@Serializable internal data class CreateCartBody(val checkNo: String, val orderSource: String = "POS", val fulfillment: String = "DINE_IN")
@Serializable internal data class ReplaceLineBody(val line: CloudLineInput, val version: Long)
@Serializable internal data class QuoteBody(val cartNo: String, val version: Long)
@Serializable internal data class ConfirmBody(val cartNo: String, val version: Long, val confirmationDigest: String)
@Serializable internal data class BatchBody(val cartNo: String, val version: Long, val quoteNo: String, val confirmationDigest: String, val payMode: String = "POSTPAY")
@Serializable internal data class PreviewBody(val businessDate: String, val currency: String)
@Serializable internal data class SignBody(val closeNo: String, val snapshotHash: String)
@Serializable internal data class CheckEnvelope(val check: CloudCheck)
@Serializable internal data class OperationEnvelope(val operation: CloudOperation)
@Serializable internal data class CartEnvelope(val cart: CloudCart)
