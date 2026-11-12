package ai.neargo.fnb.pos.core.cloud

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 餐饮服务端 POS 合同（ai-shop `docs/api/pos-fnb`，草案 v0.1）的客户端，第一批：门店、桌台、开台、账单、转台、餐牌、日结。
 * 约定按「不一致 API 评审」与后端评审意见：幂等键只放请求头 `Idempotency-Key`、每个请求带 `X-Store-No`、
 * 当班员工带 `X-Operator`（与支付终端同名）、金额为 Minor 整数 + currency、信封 `{code,msg,data}`，业务错误按 code 分类。
 * 字段严格按草案，请求不带多余字段（服务端拒绝未知字段）。
 */
interface CloudApi {
    suspend fun storeInfo(storeNo: String): CloudStoreInfo
    suspend fun tableList(storeNo: String): List<CloudTable>
    suspend fun openTable(storeNo: String, key: String, tableNo: String, guestCount: Int): CloudOpen
    suspend fun checkDetail(storeNo: String, checkNo: String): CloudCheckDetail
    suspend fun transferTable(storeNo: String, key: String, checkNo: String, toTableNo: String, version: Long): CloudCheck
    suspend fun menuList(storeNo: String): List<CloudMenu>
    suspend fun closePreview(storeNo: String, key: String, businessDate: String): CloudClosePreview
    suspend fun closeSign(storeNo: String, key: String, closeNo: String, version: Long): CloudClose
}

@Serializable data class CloudStore(val storeNo: String, val name: String, val timeZone: String = "", val currency: String = "CNY")
@Serializable data class CloudStoreInfo(val store: CloudStore, val businessDate: String, val payModes: List<String> = emptyList(), val capabilities: List<String> = emptyList())
@Serializable data class CloudTable(val tableNo: String, val name: String, val areaNo: String = "", val status: String, val checkNo: String? = null, val version: Long)
@Serializable data class CloudCheck(val checkNo: String, val status: String, val tableNos: List<String>, val guestCount: Int, val payMode: String = "", val version: Long)
@Serializable data class CloudOpen(val check: CloudCheck, val table: CloudTable)
@Serializable data class CloudAmounts(val currency: String, val subtotalMinor: Long, val discountMinor: Long, val taxMinor: Long, val payableMinor: Long, val paidMinor: Long, val refundedMinor: Long)
@Serializable data class CloudOrderLine(val lineNo: String, val name: String, val quantity: String, val unit: String, val amountMinor: Long)
@Serializable data class CloudCheckDetail(val check: CloudCheck, val lines: List<CloudOrderLine>, val amounts: CloudAmounts)
@Serializable data class CloudMenu(val menuNo: String, val name: String, val revision: String, val channel: String = "")
@Serializable data class CloudClosePreview(val closeNo: String, val status: String, val amounts: CloudAmounts, val blockingItems: List<CloudBlocking> = emptyList(), val version: Long = 0)
@Serializable data class CloudBlocking(val type: String, val referenceNo: String, val reason: String)
@Serializable data class CloudClose(val closeNo: String, val status: String, val signedAt: String = "")

/** 服务端错误分类：决定同步怎么处理（重读、交人工、重试） */
enum class CloudErrorKind {
    /** 网络不通、超时、5xx、10500（同键首个请求仍在处理）：结果未知，保留，用同一幂等键重试 */
    Network,
    /** 10401 未登录 / token 失效：停止同步，提示重新登录 */
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

/** 连接参数：地址、门店号、终端令牌、当前员工（PIN 上岗的员工号，可空） */
data class CloudConfig(val baseUrl: String, val storeNo: String, val token: String, val staffNo: () -> String = { "" }) {
    val enabled get() = baseUrl.isNotBlank() && storeNo.isNotBlank()
}

/** HTTP 实现：GET 参数走 query，POST 走 JSON；连接 3 秒、读 10 秒超时 */
class HttpCloudApi(private val cfg: () -> CloudConfig, private val connectTimeoutMs: Int = 3_000, private val readTimeoutMs: Int = 10_000) : CloudApi {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    override suspend fun storeInfo(storeNo: String) = get("/store/info", mapOf("storeNo" to storeNo), CloudStoreInfo.serializer())
    override suspend fun tableList(storeNo: String) = get("/table/list", mapOf("storeNo" to storeNo, "page" to "1", "size" to "500"), CloudTablesPage.serializer()).records
    override suspend fun openTable(storeNo: String, key: String, tableNo: String, guestCount: Int) =
        post("/table/open", key, mapOf("storeNo" to storeNo, "tableNo" to tableNo, "guestCount" to guestCount), CloudOpen.serializer())
    override suspend fun checkDetail(storeNo: String, checkNo: String) = get("/check/detail", mapOf("storeNo" to storeNo, "checkNo" to checkNo), CloudCheckDetail.serializer())
    override suspend fun transferTable(storeNo: String, key: String, checkNo: String, toTableNo: String, version: Long) =
        post("/table/transfer", key, mapOf("storeNo" to storeNo, "checkNo" to checkNo, "targetTableNo" to toTableNo, "version" to version), CloudCheckEnvelope.serializer()).check
    override suspend fun menuList(storeNo: String) = get("/menu/list", mapOf("storeNo" to storeNo), CloudMenusEnvelope.serializer()).menus
    override suspend fun closePreview(storeNo: String, key: String, businessDate: String) =
        post("/close/preview", key, mapOf("storeNo" to storeNo, "businessDate" to businessDate), CloudClosePreview.serializer())
    override suspend fun closeSign(storeNo: String, key: String, closeNo: String, version: Long) =
        post("/close/sign", key, mapOf("storeNo" to storeNo, "closeNo" to closeNo, "version" to version), CloudClose.serializer())

    // —— 传输 ——
    private suspend fun <T> get(path: String, query: Map<String, String>, ser: KSerializer<T>): T =
        call("GET", path + "?" + query.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }, null, null, ser)

    private suspend fun <T> post(path: String, key: String, body: Map<String, Any>, ser: KSerializer<T>): T =
        call("POST", path, key, JsonObject(body.mapValues { (_, v) -> toJson(v) }).toString(), ser)

    private fun toJson(v: Any): JsonElement = when (v) {
        is String -> kotlinx.serialization.json.JsonPrimitive(v)
        is Number -> kotlinx.serialization.json.JsonPrimitive(v)
        is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
        else -> JsonNull
    }

    private suspend fun <T> call(method: String, path: String, key: String?, body: String?, ser: KSerializer<T>): T = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val c = cfg()
        val conn = try { URL(c.baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection }
        catch (e: Exception) { throw CloudException(CloudErrorKind.Network, 0, "云端地址无效：${e.message}") }
        try {
            conn.requestMethod = method; conn.connectTimeout = connectTimeoutMs; conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("Accept", "application/json")
            if (c.token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${c.token}")
            conn.setRequestProperty("X-Store-No", c.storeNo)
            c.staffNo().takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("X-Operator", it) }
            key?.let { conn.setRequestProperty("Idempotency-Key", it) }
            if (body != null) {
                conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val text = (if (status < 400) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status == 401) throw CloudException(CloudErrorKind.Unauthorized, 10401, "登录已失效，请重新登录")
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

@Serializable internal data class CloudTablesPage(val records: List<CloudTable>, val total: Int = 0, val page: Int = 1, val size: Int = 0)
@Serializable internal data class CloudCheckEnvelope(val check: CloudCheck)
@Serializable internal data class CloudMenusEnvelope(val menus: List<CloudMenu>)
