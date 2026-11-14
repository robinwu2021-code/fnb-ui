package ai.neargo.fnb.pos.core.cloud

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
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
    /**
     * 门店基本信息（币种、时区、切日、营业日、服务端时间）。只需 biz:order:view，无请求体；
     * 同时是连通探测（门店号 / 令牌不对会报错）。主体没配市场时 market / currency / currencyScale 为 null。
     */
    suspend fun storeProfile(storeNo: String): CloudProfile
    /** 连通探测：就是读门店基本信息 */
    suspend fun probe(storeNo: String): CloudProbe = CloudProbe(storeNo, storeProfile(storeNo))
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
    suspend fun placeBatch(storeNo: String, key: String, cartNo: String, version: Long, quoteNo: String, confirmationDigest: String, buyerNo: String? = null): CloudBatch
    /** 门店营业策略：时区与切日分钟（营业日 = 本地时间减去切日分钟后的日期）；没有权限或没配策略时返回错误，调用方回退设备时区 */
    suspend fun storePolicy(storeNo: String): CloudPolicy = storeProfile(storeNo).let { CloudPolicy(it.timezone, it.cutoverMinute) }
    /** 开营业日（幂等：当天已开过就原样返回）；日结预览前固定先开，没开过 preview 返回 10404 */
    suspend fun openBusinessDay(key: String, businessDate: String)
    suspend fun closePreview(key: String, businessDate: String, currency: String): CloudClose
    /** 柜台后付整桌结账：建结算单（无请求体，应收由服务端重读核心算出；同账台已有未结束的单 → 10409） */
    suspend fun createCounterSettlement(key: String, checkNo: String): CloudSettlement
    /** 收齐确认：按结算单号幂等、可安全重试；整桌 PAID、关单、能放台就放台 */
    suspend fun finalizeCounterSettlement(settlementNo: String, offlineRecordNos: List<String>): CloudSettlement
    suspend fun cancelCounterSettlement(settlementNo: String): CloudSettlement
    /** 读结算单（新部署之后结的单带 receivedMinor / changeMinor） */
    suspend fun counterSettlement(settlementNo: String): CloudSettlement
    // —— 结账后放台（R4：菜没上齐不放台；POS 不上报上菜，结账时代表这桌已吃完，把该账单的厨房任务推到已上菜再放台） ——
    suspend fun stations(storeNo: String): List<CloudStation>
    /** 某工位未完成的厨房任务（HELD / QUEUED / MAKING / READY） */
    suspend fun kitchenTasks(storeNo: String, stationNo: String): List<CloudTask>
    /** 账单下的下单批次号 */
    suspend fun checkBatchNos(storeNo: String, checkNo: String): List<String>
    /** 推进任务：action = start / ready / serve；请求体的 version 是任务当前版本，每步 +1 */
    suspend fun advanceTask(key: String, taskNo: String, action: String, version: Long, qty: Double)
    /** 放台（checkCollaboration/releaseService）：要求账单所有任务已上菜或作废；CLOSED 的账单也能放；不接受 query 参数 */
    suspend fun releaseService(key: String, checkNo: String, version: Long, reason: String)
    /** 外部记账（平台 /biz/payment/offline-records）：柜台结账 bizNo 必须等于结算单号；现金按实收面额记 */
    suspend fun recordOffline(key: String, body: CloudOfflineRecordBody): CloudOfflineRecord

    // —— 进销存（/biz/inventory，零售门店；没有幂等键：先建草稿记住单号，再按单号过账，过账幂等） ——
    /** 按条码找物料；没命中返回 null */
    suspend fun inventoryItemByBarcode(code: String): CloudInvItem?
    suspend fun createInbound(body: CloudInboundBody): String
    suspend fun postInbound(no: String)
    suspend fun createOutbound(body: CloudOutboundBody): String
    suspend fun postOutbound(no: String)
    /** 开盘点单（同库位已有开着的盘点单 → 冲突） */
    suspend fun openCount(itemIds: List<String>): String
    /** 录实盘（逐行覆盖，可重复） */
    suspend fun fillCount(no: String, lines: List<CloudCountLine>)
    suspend fun postCount(no: String)
    /** 单品调整：countedQty 为实盘绝对值 */
    suspend fun adjustInventory(itemId: String, countedQty: Int, reasonCode: String)
    suspend fun closeSign(key: String, closeNo: String, snapshotHash: String): CloudClose
}

data class CloudProbe(val storeNo: String, val profile: CloudProfile) {
    val storeName get() = profile.storeName
}

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
    val goodsNo: String, val skuNo: String, val qty: Int, val seatNo: String? = null, val courseNo: String? = null,
    /** 真实后端实测：必须带，空数组即可（缺则 10400「请求参数有误」） */
    val selections: List<String> = emptyList(), val bundleChoices: List<String> = emptyList())
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
/** CounterSettlementVO：应收 dueMinor；status FROZEN / COLLECTING / SETTLED / CANCELLED；received / change / receiptNo 在 finalize 响应里有，GET 也返回 received / change（新部署之后结的单才有，之前的要重调一次 finalize 才补上） */
@Serializable data class CloudSettlement(val settlementNo: String, val checkNo: String = "", val status: String = "", val currency: String = "",
    val dueMinor: Long = 0, val receivedMinor: Long? = null, val changeMinor: Long? = null, val receiptNo: String? = null,
    val checkStatus: String = "", val tableReleased: Boolean = false)
/** RecordBody：未知字段 10400；amountMinor 只收整数；method 柜台结账只用 CASH / EXTERNAL_POS；receivedAt 为带偏移的 ISO-8601（截到秒，31 天内） */
@Serializable data class CloudOfflineRecordBody(val amountMinor: Long, val currency: String, val method: String, val bizNo: String? = null,
    val note: String? = null, val receivedAt: String? = null)
@Serializable data class CloudOfflineRecord(val recordNo: String, val amountMinor: Long = 0, val method: String = "", val bizNo: String? = null, val status: String = "")
/** 物料（BalanceVO）：itemId 形如 ITM…；baseUom 为基本单位 */
@Serializable data class CloudInvItem(val itemId: String, val skuNo: String? = null, val name: String = "", val baseUom: String = "")
/** 单据行：数量只收整数；出库行只用 itemId / qty / uom */
@Serializable data class CloudInvLine(val itemId: String, val qty: Int, val uom: String? = null, val unitCostMinor: Long? = null)
/** 入库：sourceType PURCHASE 时每行要 unitCostMinor；occurredAt 为不带时区的本地时间 yyyy-MM-ddTHH:mm:ss */
@Serializable data class CloudInboundBody(val sourceType: String, val supplierName: String? = null, val occurredAt: String, val remark: String, val lines: List<CloudInvLine>)
/** 出库：报损 purpose=SCRAP，reasonCode 取 CHECK / BROKEN / EXPIRED / GIFT / OTHER，不能带 targetNo */
@Serializable data class CloudOutboundBody(val purpose: String, val reasonCode: String, val occurredAt: String, val remark: String, val lines: List<CloudInvLine>)
@Serializable data class CloudCountLine(val itemId: String, val countedQty: Int, val reasonCode: String)
@Serializable data class CloudStation(val configNo: String, val name: String = "", val enabled: Boolean = true)
@Serializable data class CloudTask(val taskNo: String, val batchNo: String, val status: String = "", val version: Long = 0, val qty: Double = 0.0)
/** 门店基本信息。时间约定：Instant 带 Z（小数位 0–9 位不固定，用标准 ISO 解析），businessDate 是门店时区的日历日 */
@Serializable data class CloudProfile(
    val storeNo: String = "", val storeName: String = "", val status: String = "", val market: String? = null,
    val currency: String? = null, val currencyScale: Int? = null, val timezone: String = "", val cutoverMinute: Int = 0,
    val payPolicy: String = "", val businessDate: String = "", val serverTime: String = "",
) {
    /** 服务端时间 − 本机时间（毫秒）；解析不了返回 null */
    fun clockSkewMs(localNow: Long): Long? = runCatching { java.time.Instant.parse(serverTime).toEpochMilli() - localNow }.getOrNull()
}
@Serializable data class CloudPolicy(val timezone: String = "", val cutoverMinute: Int = 0)
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
data class CloudConfig(val baseUrl: String, val storeNo: String, val token: String, val staffNo: () -> String = { "" }, val buyerNo: String = "") {
    val enabled get() = baseUrl.isNotBlank() && storeNo.isNotBlank()
}

/** 底层 HTTP：信封解析、错误分类、公共请求头。餐饮 Biz 与推送共用 */
class CloudHttp(private val cfg: () -> CloudConfig, private val connectTimeoutMs: Int = 3_000, private val readTimeoutMs: Int = 10_000) {
    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    val config get() = cfg()

    fun url(path: String, query: Map<String, String> = emptyMap()) = cfg().baseUrl.trimEnd('/') + path +
        (if (query.isEmpty()) "" else "?" + query.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" })

    suspend fun <T> get(path: String, query: Map<String, String>, ser: KSerializer<T>): T = call("GET", url(path, query), null, null, ser)
    /** 无请求体的 POST（建结算单、取消） */
    suspend fun <T> postEmpty(path: String, key: String?, ser: KSerializer<T>): T = call("POST", url(path), key, "", ser)
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
                conn.doOutput = true
                if (body.isNotEmpty()) conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
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

    override suspend fun storeProfile(storeNo: String) = http.get("${s(storeNo)}/profile", emptyMap(), CloudProfile.serializer())
    /** 真实后端分页上限 100（超过报「最大不能超过100」）：翻页取全 */
    override suspend fun tableList(storeNo: String): List<CloudTable> {
        val all = mutableListOf<CloudTable>(); var page = 1
        while (true) {
            val r = http.get("${s(storeNo)}/tables", mapOf("page" to page.toString(), "size" to "100"), Page.serializer(CloudTable.serializer()))
            all += r.records
            if (r.records.isEmpty() || all.size >= r.total) return all
            page++
        }
    }
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
    override suspend fun placeBatch(storeNo: String, key: String, cartNo: String, version: Long, quoteNo: String, confirmationDigest: String, buyerNo: String?) =
        http.send("POST", "${s(storeNo)}/batches", key, BatchBody(cartNo, version, quoteNo, confirmationDigest, buyerNo = buyerNo), BatchBody.serializer(), CloudBatch.serializer())
    override suspend fun openBusinessDay(key: String, businessDate: String) {
        http.send("POST", "$FOOD/business-days/${enc(businessDate)}:open", key, EmptyBody(), EmptyBody.serializer(), kotlinx.serialization.json.JsonElement.serializer().nullable)
    }
    override suspend fun closePreview(key: String, businessDate: String, currency: String) =
        http.send("POST", "$FOOD/closes:preview", key, PreviewBody(businessDate, currency), PreviewBody.serializer(), CloudClose.serializer())
    override suspend fun createCounterSettlement(key: String, checkNo: String) =
        http.postEmpty("$FOOD/checks/${enc(checkNo)}/counter-settlements", key, CloudSettlement.serializer())
    override suspend fun finalizeCounterSettlement(settlementNo: String, offlineRecordNos: List<String>) =
        http.send("POST", "$FOOD/counter-settlements/${enc(settlementNo)}:finalize", null, FinalizeBody(offlineRecordNos), FinalizeBody.serializer(), CloudSettlement.serializer())
    override suspend fun cancelCounterSettlement(settlementNo: String) =
        http.postEmpty("$FOOD/counter-settlements/${enc(settlementNo)}:cancel", null, CloudSettlement.serializer())
    override suspend fun counterSettlement(settlementNo: String) =
        http.get("$FOOD/counter-settlements/${enc(settlementNo)}", emptyMap(), CloudSettlement.serializer())
    override suspend fun recordOffline(key: String, body: CloudOfflineRecordBody) =
        http.send("POST", "/biz/payment/offline-records", key, body, CloudOfflineRecordBody.serializer(), CloudOfflineRecord.serializer())
    override suspend fun stations(storeNo: String) =
        http.get("${s(storeNo)}/stations", mapOf("page" to "1", "size" to "50"), Page.serializer(CloudStation.serializer())).records
    override suspend fun kitchenTasks(storeNo: String, stationNo: String) =
        http.get("${s(storeNo)}/kitchen/tasks", mapOf("stationNo" to stationNo, "page" to "1", "size" to "100"), Page.serializer(CloudTask.serializer())).records
    override suspend fun checkBatchNos(storeNo: String, checkNo: String) =
        http.get("${s(storeNo)}/batches", mapOf("checkNo" to checkNo, "page" to "1", "size" to "100"), Page.serializer(CloudBatch.serializer())).records.map { it.batchNo }
    override suspend fun advanceTask(key: String, taskNo: String, action: String, version: Long, qty: Double) {
        http.send("POST", "$FOOD/tasks/${enc(taskNo)}:$action", key, TaskBody(version, qty, "POS 结账"), TaskBody.serializer(), kotlinx.serialization.json.JsonElement.serializer().nullable)
    }
    override suspend fun releaseService(key: String, checkNo: String, version: Long, reason: String) {
        http.send("POST", "$FOOD/business-operations/checkCollaboration/releaseService", key, ReleaseBody(checkNo, version, reason), ReleaseBody.serializer(), kotlinx.serialization.json.JsonElement.serializer().nullable)
    }
    override suspend fun inventoryItemByBarcode(code: String) =
        http.get("$INV/items/by-barcode", mapOf("code" to code), CloudInvItem.serializer().nullable)
    override suspend fun createInbound(body: CloudInboundBody) = http.send("POST", "$INV/inbounds", null, body, CloudInboundBody.serializer(), DocNo.serializer()).no
    override suspend fun postInbound(no: String) { http.postEmpty("$INV/inbounds/${enc(no)}/post", null, kotlinx.serialization.json.JsonElement.serializer().nullable) }
    override suspend fun createOutbound(body: CloudOutboundBody) = http.send("POST", "$INV/outbounds", null, body, CloudOutboundBody.serializer(), DocNo.serializer()).no
    override suspend fun postOutbound(no: String) { http.postEmpty("$INV/outbounds/${enc(no)}/post", null, kotlinx.serialization.json.JsonElement.serializer().nullable) }
    override suspend fun openCount(itemIds: List<String>) = http.send("POST", "$INV/counts", null, CountOpen(itemIds), CountOpen.serializer(), DocNo.serializer()).no
    override suspend fun fillCount(no: String, lines: List<CloudCountLine>) {
        http.send("PUT", "$INV/counts/${enc(no)}/lines", null, lines, kotlinx.serialization.builtins.ListSerializer(CloudCountLine.serializer()), kotlinx.serialization.json.JsonElement.serializer().nullable)
    }
    override suspend fun postCount(no: String) { http.postEmpty("$INV/counts/${enc(no)}/post", null, kotlinx.serialization.json.JsonElement.serializer().nullable) }
    override suspend fun adjustInventory(itemId: String, countedQty: Int, reasonCode: String) {
        http.send("POST", "$INV/adjust", null, AdjustBody(itemId, countedQty, reasonCode), AdjustBody.serializer(), kotlinx.serialization.json.JsonElement.serializer().nullable)
    }
    override suspend fun closeSign(key: String, closeNo: String, snapshotHash: String) =
        http.send("POST", "$FOOD/closes:sign", key, SignBody(closeNo, snapshotHash), SignBody.serializer(), CloudClose.serializer())

    companion object {
        const val FOOD = "/biz/x/food"
        const val INV = "/biz/inventory"
        internal fun enc(v: String): String = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
    }
}

// —— 请求体（只含服务端 DTO 里有的字段）与响应包装 ——
@Serializable internal data class Page<T>(val records: List<T> = emptyList(), val total: Int = 0, val page: Int = 1, val size: Int = 0)
/** 真实后端实测：payMode、sharingMode、seats（可空数组但必须有）都不能缺，缺则 10400「不能为空」 */
@Serializable internal data class OpenCheckBody(val tableNos: List<String>, val partySize: Int, val tableVersions: List<CloudTableVersion>,
    val sharingMode: String = "EXCLUSIVE", val seats: List<String> = emptyList(), val payMode: String = "POSTPAY")
@Serializable internal data class TransferBody(val version: Long, val targetTableNos: List<String>, val tableVersions: List<CloudTableVersion>)
@Serializable internal data class CreateCartBody(val checkNo: String, val orderSource: String = "POS", val fulfillment: String = "DINE_IN")
@Serializable internal data class ReplaceLineBody(val line: CloudLineInput, val version: Long)
@Serializable internal data class QuoteBody(val cartNo: String, val version: Long)
@Serializable internal data class ConfirmBody(val cartNo: String, val version: Long, val confirmationDigest: String)
/** 真实后端实测：必须且只能带 buyerNo 与 partyNo 之一（partyNo 要等于购物车 sessionNo；POS 建的购物车 sessionNo 为空，所以用 buyerNo） */
@Serializable internal data class BatchBody(val cartNo: String, val version: Long, val quoteNo: String, val confirmationDigest: String, val payMode: String = "POSTPAY", val buyerNo: String? = null)
@Serializable internal data class PreviewBody(val businessDate: String, val currency: String)
@Serializable internal data class SignBody(val closeNo: String, val snapshotHash: String)
@Serializable internal class EmptyBody
@Serializable internal data class TaskBody(val version: Long, val qty: Double, val reason: String)
@Serializable internal data class ReleaseBody(val check: String, val version: Long, val reason: String)
@Serializable internal data class DocNo(val no: String)
@Serializable internal data class CountOpen(val itemIds: List<String>)
@Serializable internal data class AdjustBody(val itemId: String, val countedQty: Int, val reasonCode: String)
@Serializable internal data class FinalizeBody(val offlineRecordNos: List<String>)
@Serializable internal data class CheckEnvelope(val check: CloudCheck)
@Serializable internal data class OperationEnvelope(val operation: CloudOperation)
@Serializable internal data class CartEnvelope(val cart: CloudCart)
