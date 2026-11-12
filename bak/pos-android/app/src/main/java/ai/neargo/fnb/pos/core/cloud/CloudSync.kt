package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.data.JournalEvent
import ai.neargo.fnb.pos.core.model.DayClose
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.Instant
import java.time.ZoneId

/**
 * 云端连通判定（纯规则，方案 §2）：连续 2 次失败判离线、连续 2 次成功判在线（防抖）；
 * 离线时探测间隔 5 → 10 → 30 → 60 秒封顶，在线时 30 秒。
 */
data class CloudHealth(val online: Boolean = false, val okStreak: Int = 0, val failStreak: Int = 0, val lastError: String = "") {
    fun success() = copy(okStreak = okStreak + 1, failStreak = 0, lastError = "").let { if (!online && it.okStreak >= 2) it.copy(online = true) else it }
    fun failure(error: String) = copy(failStreak = failStreak + 1, okStreak = 0, lastError = error).let { if (online && it.failStreak >= 2) it.copy(online = false) else it }
    /** 系统报告断网或强制离线：立即离线 */
    fun forceOffline(reason: String) = copy(online = false, okStreak = 0, failStreak = maxOf(failStreak, 2), lastError = reason)
    fun nextProbeMs(): Long = if (online) 30_000 else when (failStreak) { 0, 1 -> 5_000; 2 -> 10_000; 3 -> 30_000; else -> 60_000 }
}

/** 页头云图标四态（方案 §6） */
sealed interface CloudBadge {
    data object NotConfigured : CloudBadge
    data class Online(val pending: Int) : CloudBadge
    data class Offline(val pending: Int, val reason: String, val limit: CloudLimit = CloudLimit.None) : CloudBadge
    data class Syncing(val done: Int, val total: Int) : CloudBadge
    data class NeedsAction(val count: Int) : CloudBadge
    /** 副 POS：不直连云端，由主 POS 上传 */
    data object ViaHost : CloudBadge
}

/** 本机 id ↔ 服务端号 的映射（方案 §4.1：订单 → checkNo、桌台 → tableNo；带服务端 version） */
interface CloudRefs {
    fun get(kind: String, localId: String): CloudRef?
    /** 某类映射全部（用于价差等列表） */
    fun all(kind: String): List<CloudRef>
    fun put(ref: CloudRef)
    /** 需人工处理的上传（业务拒绝 / 冲突重试后仍失败） */
    fun issues(): List<CloudIssue>
    fun addIssue(issue: CloudIssue)
    fun resolve(eventId: String)
}
@Serializable data class CloudRef(val kind: String, val localId: String, val remoteNo: String, val version: Long = 0)
@Serializable data class CloudIssue(val eventId: String, val type: String, val aggregateId: String, val code: Int, val message: String, val at: Long)

class MemoryCloudRefs : CloudRefs {
    private val refs = LinkedHashMap<String, CloudRef>(); private val list = mutableListOf<CloudIssue>()
    override fun get(kind: String, localId: String) = refs["$kind/$localId"]
    override fun all(kind: String) = refs.values.filter { it.kind == kind }
    override fun put(ref: CloudRef) { refs["${ref.kind}/${ref.localId}"] = ref }
    override fun issues() = list.toList()
    override fun addIssue(issue: CloudIssue) { list.removeAll { it.eventId == issue.eventId }; list += issue }
    override fun resolve(eventId: String) { list.removeAll { it.eventId == eventId } }
}

/** 待上传事件来源：命令日志（`pending` 按序；`ack` 只在服务端确认持久接收后调用） */
interface EventSource {
    suspend fun pending(storeId: String, limit: Int): List<JournalEvent>
    suspend fun ack(storeId: String, eventId: String)
}

/** 一轮回放的结果：uploaded 已上传；local 只在本机的事件（直接标为已处理）；waiting 等后端接口或被同单前序事件挡住 */
data class SyncRound(val uploaded: Int, val waiting: Int, val issues: Int, val stoppedBy: CloudException? = null, val local: Int = 0)

/**
 * 云端同步适配（下一批对接方案 §0–§2）：在主 POS / 单机上按命令日志顺序回放到餐饮 Biz 接口。
 * - 事件三类：上传 [SUPPORTED]、只在本机 [LOCAL_ONLY]（直接 ack，不挡同单后续事件）、其余等后端接口。
 * - 幂等键 = 本机事件 ID；一条事件拆成多次调用时按「事件ID:步骤[:行]」派生，重放时规则不变。
 * - 网络错误：本轮停止，事件保留（结果未知不重建）。
 * - 业务拒绝 / 冲突重放后仍失败：记入「需处理」，这个聚合后续事件暂停，其他聚合照常。
 */
class CloudSync(
    private val api: CloudApi,
    private val refs: CloudRefs,
    private val events: EventSource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val currency: String = "CNY",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var catalog: CloudCatalog? = null

    companion object {
        /** 已对接：开台、换桌、送厨（购物车 → 报价 → 员工下单）、日结 */
        val SUPPORTED = setOf("TableOpened", "TableChanged", "KitchenSubmitted", "DayClosed")
        /**
         * 只在本机：点菜草稿在送厨时一次带上；备注、挂单是本机操作；待清台服务端没有（评审 #26）；
         * 厨房屏店内由主 POS 仲裁；催菜 / 上菜需服务端厨房任务号，暂无「按批次查任务」接口，先不上报。
         */
        val LOCAL_ONLY = setOf("DishAdded", "DishQuantityChanged", "DishNoteChanged", "OrderDraftCleared", "OrderHeld", "OrderRetrieved",
            "OrderNoteChanged", "TablePositionChanged", "TableCleaningStarted", "TableCleaningFinished",
            "KdsTicketAdvanced", "KdsTicketReleased", "KdsTicketReassigned", "DishRushed", "DishServed")
    }

    suspend fun run(storeId: String, storeNo: String, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): SyncRound {
        val list = events.pending(storeId, 500)
        val blocked = refs.issues().map { it.aggregateId }.toMutableSet()
        var uploaded = 0; var waiting = 0; var local = 0
        if (list.any { it.type in SUPPORTED }) try { refreshTables(storeNo) } catch (x: CloudException) {
            return SyncRound(0, list.size, refs.issues().size, x)
        }
        list.forEachIndexed { i, e ->
            progress(i, list.size)
            when {
                e.aggregateId in blocked -> waiting++
                // 旧版事件（envelope 为空，迁移前的本机流）不上传
                e.envelope.isBlank() -> { waiting++; blocked += e.aggregateId }
                e.type in LOCAL_ONLY -> { events.ack(storeId, e.eventId); local++ }
                e.type !in SUPPORTED -> { waiting++; blocked += e.aggregateId }   // 等后端接口；同单后续事件要等它
                else -> try {
                    upload(e, storeNo)
                    events.ack(storeId, e.eventId); uploaded++
                } catch (x: CancellationException) { throw x }
                catch (x: CloudException) {
                    when (x.kind) {
                        CloudErrorKind.Network, CloudErrorKind.Unauthorized -> return SyncRound(uploaded, list.size - uploaded - local, refs.issues().size, x, local)
                        else -> { refs.addIssue(CloudIssue(e.eventId, e.type, e.aggregateId, x.code, x.message.orEmpty(), clock())); blocked += e.aggregateId }
                    }
                } catch (x: Exception) {
                    // 本机载荷无法转换（字段缺失、格式不符）：不重试，交人工，不影响其他聚合
                    refs.addIssue(CloudIssue(e.eventId, e.type, e.aggregateId, 0, "本机事件无法转换：${x.message ?: x.javaClass.simpleName}", clock())); blocked += e.aggregateId
                }
            }
        }
        progress(list.size, list.size)
        return SyncRound(uploaded, waiting, refs.issues().size, local = local)
    }

    /** 桌台：本机桌号 = 服务端桌台 name（门店里写在桌上的号）；拉服务端桌台表建立映射并带回桌台版本 */
    private suspend fun refreshTables(storeNo: String) {
        api.tableList(storeNo).forEach { t -> refs.put(CloudRef("table", t.name, t.tableNo, t.version)) }
    }

    private fun table(local: String) = refs.get("table", local)
        ?: throw CloudException(CloudErrorKind.NotFound, 10404, "服务端没有桌台 $local，请先在后台建桌")

    /**
     * 幂等键 = 本机事件 ID；人工在「需处理」点了重试则为「事件ID#第几次」（服务端按键固定业务结果，同键重试只会得到同样的拒绝）
     */
    private fun keyOf(e: JournalEvent) = refs.get("attempt", e.eventId)?.let { "${e.eventId}#${it.remoteNo}" } ?: e.eventId

    private fun checkOf(orderId: String) = refs.get("order", orderId)
        ?: throw CloudException(CloudErrorKind.NotFound, 10404, "订单还没有上云（开台未同步）")

    private suspend fun upload(e: JournalEvent, storeNo: String) {
        val p = json.parseToJsonElement(e.payload).jsonObject
        val key = keyOf(e)
        when (e.type) {
            "TableOpened" -> {
                val tableId = p.str("tableId"); val order = p.obj("order")
                val guests = p.obj("table")["guests"]!!.jsonPrimitive.int
                // 桌台版本冲突（同轮里前一条事件刚改过这张桌）：重读桌台表再试一次
                val c = withTableRetry(storeNo) { val t = table(tableId); api.openCheck(storeNo, key, listOf(t.remoteNo), guests, listOf(CloudTableVersion(t.remoteNo, t.version))) }
                refs.put(CloudRef("order", order.str("orderId"), c.checkNo, c.version))
            }
            "TableChanged" -> {
                // 载荷：tableId = 原桌；受影响桌台里带着同一订单的那张是目标桌
                val moved = p["affectedTables"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it.obj("order").str("orderId").isNotBlank() }
                    ?: throw CloudException(CloudErrorKind.Rejected, 10400, "换桌事件缺少目标桌")
                val orderId = moved.obj("order").str("orderId"); val target = moved.obj("table").str("id")
                val check = checkOf(orderId)
                // 版本冲突：重读账单与桌台，按本机意图重放一次；仍冲突交人工
                suspend fun attempt(v: Long) { val t = table(target); api.transferCheck(key, check.remoteNo, v, listOf(t.remoteNo), listOf(CloudTableVersion(t.remoteNo, t.version))) }
                try { attempt(check.version) } catch (x: CloudException) {
                    if (x.kind != CloudErrorKind.Conflict) throw x
                    refreshTables(storeNo); attempt(api.check(check.remoteNo).version)
                }
                refs.put(check.copy(version = api.check(check.remoteNo).version))   // 转台返回操作单，不含账单版本
            }
            "KitchenSubmitted" -> submitBatch(p, storeNo, key)
            "DayClosed" -> {
                val d = json.decodeFromJsonElement(DayClose.serializer(), p)
                val date = Instant.ofEpochMilli(d.end).atZone(zone).toLocalDate().toString()
                val preview = api.closePreview("$key:preview", date, currency)
                if (preview.snapshotHash.isBlank()) throw CloudException(CloudErrorKind.Rejected, 10400, "日结预览未生成快照（状态 ${preview.status}），请到后台查看日结异常项")
                api.closeSign("$key:sign", preview.closeNo, preview.snapshotHash)
            }
        }
    }

    /**
     * 送厨 → 员工下单（方案 §2）：本批新菜建购物车、逐行写入、报价、确认、下单。
     * 每步幂等键固定（事件ID:cart / :line:<uid> / :quote / :batch），中断后整条重放，已完成的步骤服务端原样返回。
     * 菜已下厨，价差不拒单：记 pricediff 映射，结账时以服务端应收为准。
     */
    private suspend fun submitBatch(p: JsonObject, storeNo: String, key: String) {
        val order = p.obj("order")
        val lines = order["lines"]!!.jsonArray.map { it.jsonObject }
        val batchId = lines.filter { it.str("batchId").isNotBlank() }.maxByOrNull { it.num("sentAt") }?.str("batchId")
            ?: throw CloudException(CloudErrorKind.Rejected, 10400, "送厨事件里没有批次")
        val batch = lines.filter { it.str("batchId") == batchId }
        if (batch.any { (it["mealSelections"] as? JsonArray)?.isNotEmpty() == true })
            throw CloudException(CloudErrorKind.Rejected, 10400, "套餐行暂不支持上传（服务端套餐选择待对接）")
        val check = checkOf(order.str("orderId"))
        val inputs = batch.map { l -> lineInput(storeNo, l) }

        val cart = refs.get("cart", batchId)?.let { CloudCart(it.remoteNo, version = it.version) }
            ?: api.createCart(storeNo, "$key:cart", check.remoteNo).also { refs.put(CloudRef("cart", batchId, it.cartNo, it.version)) }
        var version = cart.version
        inputs.forEach { (uid, line) ->
            version = try { api.replaceCartLine("$key:line:$uid", cart.cartNo, line.sourceLineRef, line, version).version } catch (x: CloudException) {
                // 人工重试换了尝试键，或购物车被别处改过：读购物车最新版本再写一次（按 sourceLineRef 整行替换，重复写无副作用）
                if (x.kind != CloudErrorKind.Conflict) throw x
                api.replaceCartLine("$key:line:$uid:r", cart.cartNo, line.sourceLineRef, line, api.cart(cart.cartNo).version).version
            }
        }
        val q = api.quote(storeNo, "$key:quote", cart.cartNo, version)
        val confirmed = api.confirmQuote(q.quoteNo, cart.cartNo, version, q.confirmationDigest)
        val b = api.placeBatch(storeNo, "$key:batch", cart.cartNo, version, q.quoteNo, confirmed.confirmationDigest.ifBlank { q.confirmationDigest })
        refs.put(CloudRef("batch", batchId, b.batchNo, b.version))
        val localMinor = batch.sumOf { it.num("unit") * it.num("qty") }
        b.amountMinor?.takeIf { it != localMinor }?.let { refs.put(CloudRef("pricediff", batchId, "$localMinor/$it")) }
    }

    /** 本机菜品 → 服务端菜单项：按菜名对应，多规格按规格名对应（菜单由云端下发前的过渡做法） */
    private suspend fun lineInput(storeNo: String, l: JsonObject): Pair<Long, CloudLineInput> {
        val cat = catalog ?: api.catalog(storeNo).also { catalog = it }
        val name = l.str("name").trim(); val spec = l.str("spec").trim()
        val item = cat.items.firstOrNull { it.title.trim() == name }
            ?: throw CloudException(CloudErrorKind.Rejected, 10400, "服务端菜单里没有「$name」，请在后台上架后重试")
        val sku = item.skus.singleOrNull() ?: item.skus.firstOrNull { it.spec?.trim() == spec }
            ?: throw CloudException(CloudErrorKind.Rejected, 10400, "服务端「$name」没有规格「$spec」")
        val revision = cat.menus.firstOrNull { it.menuNo == item.menuNo }?.revisionNo.orEmpty()
        val uid = l.num("uid")
        return uid to CloudLineInput("L$uid", item.menuNo, item.menuItemNo, revision, item.goodsNo, sku.skuNo, l.num("qty").toInt())
    }

    private suspend fun <T> withTableRetry(storeNo: String, op: suspend () -> T): T =
        try { op() } catch (x: CloudException) { if (x.kind != CloudErrorKind.Conflict) throw x; refreshTables(storeNo); op() }

    private fun JsonObject.str(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content.orEmpty()
    private fun JsonObject.obj(k: String) = this[k]!!.jsonObject
    private fun JsonObject.num(k: String) = this[k]?.jsonPrimitive?.long ?: 0L
}
