package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.common.PayMethods
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
    data class Online(val pending: Int, val waiting: Int = 0) : CloudBadge
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
/** label：给店员看的对象（桌 H01、销售单 R0158、库存单 RK0003…）；aggregateId 是内部阻塞键 */
@Serializable data class CloudIssue(val eventId: String, val type: String, val aggregateId: String, val code: Int, val message: String, val at: Long, val label: String = "")

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

/** 这条事件按载荷判定还不能上传（服务端接口未对接）：保留在本机，挡同单后续事件，不进需处理 */
class WaitForBackend(message: String) : Exception(message)

/** 一轮回放的结果：uploaded 已上传；local 只在本机的事件（直接标为已处理）；waiting 等后端接口或被同单前序事件挡住 */
/**
 * 一轮回放的结果：uploaded 已上传；local 只在本机的事件（直接标为已处理）；waiting 被挡住没上传的（等后端接口，或被需处理的同单前序事件挡住）；
 * backendWait 其中「等服务端接口」的那部分——它们按设计一直留在本机，不算待上传（页头、离线时限都不该受它们影响）。
 */
data class SyncRound(val uploaded: Int, val waiting: Int, val issues: Int, val stoppedBy: CloudException? = null, val local: Int = 0, val backendWait: Int = 0)

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
    /** 当前门店是零售（进销存单据上传）；餐饮门店的库存调整只在本机 */
    private val inventory: Boolean = false,
    /** 员工下单的买家号（真实后端要求买家号或会话号之一；散客方案待后端定） */
    private val buyerNo: String = "",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var catalog: CloudCatalog? = null

    companion object {
        /** 已对接：开台、换桌、送厨（购物车 → 报价 → 员工下单）、结账（柜台结算单 → 外部记账 → 确认）、日结 */
        val SUPPORTED = setOf("TableOpened", "TableChanged", "KitchenSubmitted", "PaymentRecorded", "DayClosed",
            // 零售进销存：过账的入库 / 报损 / 盘点、单品调整
            "StockDocPosted", "StockAdjusted")
        /** 库存单的草稿、修改、提交待审批、作废：只在本机，过账时一次上传 */
        val STOCK_DRAFT = setOf("StockDocCreated", "StockDocUpdated", "StockDocSubmitted", "StockDocVoided")
        /** 本机收款方式 → 外部记账方式码；扫码、储值走平台通道（未对接） */
        val OFFLINE_METHOD = mapOf(PayMethods.CASH to "CASH", PayMethods.CARD to "EXTERNAL_POS")
        /**
         * 只在本机：点菜草稿在送厨时一次带上；备注、挂单是本机操作；待清台服务端没有（评审 #26）；
         * 厨房屏店内由主 POS 仲裁；催菜 / 上菜需服务端厨房任务号，暂无「按批次查任务」接口，先不上报。
         */
        val LOCAL_ONLY = setOf("DishAdded", "DishQuantityChanged", "DishNoteChanged", "OrderDraftCleared", "OrderHeld", "OrderRetrieved",
            "OrderNoteChanged", "TablePositionChanged", "TableCleaningStarted", "TableCleaningFinished",
            "KdsTicketAdvanced", "KdsTicketReleased", "KdsTicketReassigned", "DishRushed", "DishServed",
            // 零售本机操作：挂单 / 取单、补打小票、开钱箱
            "RetailCartHeld", "RetailCartRecalled", "RetailReceiptReprinted", "DrawerOpened")
        /**
         * 配置类：菜品、菜单、桌台、员工、改价、价签、门店设置等。不走营业回放——配置以云端为准、由云端下发到店（后续批次），
         * 本机改动不上传，直接标已处理，不占待上传数。
         */
        val CONFIG = setOf("PromotionSaved", "StoreSettingsUpdated", "AreaCreated", "TableConfigCreated", "TableConfigUpdated", "CategoryCreated",
            "SpecGroupCreated", "ProductCreated", "ProductUpdated", "MenuCreated", "MenuUpdated", "MenuPublished", "MenuUnpublished",
            "KitchenStationsSaved", "KitchenDeliveryPolicySaved", "KitchenDispatchSaved", "EmployeeSaved", "ProductsDeleted", "ProductsRepriced",
            "TablesDeleted", "RetailPriceChanged", "LabelQueued", "LabelsPrinted", "RetailSettingsSaved")
    }

    /**
     * 「需处理 / 等后端」时挡住哪些后续事件：默认按事件主聚合（订单、桌台）；
     * 零售事件的主聚合都是整个零售（retail），改按单号挡——一张销售单被拒不挡其他销售单。
     */
    private fun blockKeyOf(e: JournalEvent): String {
        if (e.aggregateId != "retail") return e.aggregateId
        val p = runCatching { json.parseToJsonElement(e.payload).jsonObject }.getOrNull() ?: return "retail:${e.eventId}"
        return when (e.type) {
            "RetailSaleCompleted" -> "sale:" + p.str("no")
            "RetailReturned" -> "sale:" + p.str("ref").ifBlank { p.str("sale") }
            else -> if (e.type.startsWith("StockDoc")) "stockdoc:" + p.str("no") else "retail:${e.eventId}"
        }
    }

    suspend fun run(storeId: String, storeNo: String, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): SyncRound {
        val list = events.pending(storeId, 500)
        // 挡住的键 → 是否因「等服务端接口」（false = 因需处理）；需处理记的是挡住的键（见 blockKeyOf）
        val blocked = refs.issues().associate { it.aggregateId to false }.toMutableMap()
        var uploaded = 0; var waiting = 0; var local = 0; var backendWait = 0
        if (list.any { it.type in SUPPORTED }) try { refreshTables(storeNo) } catch (x: CloudException) {
            return SyncRound(0, list.size, refs.issues().size, x)
        }
        list.forEachIndexed { i, e ->
            progress(i, list.size)
            val key = blockKeyOf(e)
            when {
                key in blocked -> { waiting++; if (blocked[key] == true) backendWait++ }
                // 旧版事件（envelope 为空，迁移前的本机流）不上传
                e.envelope.isBlank() -> { waiting++; backendWait++; blocked[key] = true }
                e.type in LOCAL_ONLY || e.type in CONFIG || e.type in STOCK_DRAFT || (!inventory && e.type == "StockAdjusted") -> { events.ack(storeId, e.eventId); local++ }
                e.type !in SUPPORTED -> { waiting++; backendWait++; blocked[key] = true }   // 等后端接口；同单后续事件要等它
                else -> try {
                    upload(e, storeNo)
                    events.ack(storeId, e.eventId); uploaded++
                } catch (x: CancellationException) { throw x }
                catch (x: WaitForBackend) { waiting++; backendWait++; blocked[key] = true }   // 这条按载荷判定还不能上传（如扫码收款）：保留并挡同单
                catch (x: CloudException) {
                    when (x.kind) {
                        CloudErrorKind.Network, CloudErrorKind.Unauthorized -> return SyncRound(uploaded, list.size - uploaded - local, refs.issues().size, x, local)
                        else -> { refs.addIssue(CloudIssue(e.eventId, e.type, key, x.code, x.message.orEmpty(), clock(), labelOf(e))); blocked.putIfAbsent(key, false) }
                    }
                } catch (x: Exception) {
                    // 本机载荷无法转换（字段缺失、格式不符）：不重试，交人工，不影响其他聚合
                    refs.addIssue(CloudIssue(e.eventId, e.type, key, 0, "本机事件无法转换：${x.message ?: x.javaClass.simpleName}", clock(), labelOf(e))); blocked.putIfAbsent(key, false)
                }
            }
        }
        progress(list.size, list.size)
        return SyncRound(uploaded, waiting, refs.issues().size, local = local, backendWait = backendWait)
    }

    /** 需处理里显示的对象：从事件载荷取桌号 / 单号，取不到留空（界面回退显示内部键） */
    private fun labelOf(e: JournalEvent): String = runCatching {
        val p = json.parseToJsonElement(e.payload).jsonObject
        when (e.type) {
            "TableOpened", "TableChanged", "KitchenSubmitted", "PaymentRecorded" -> p.str("tableId").takeIf { it.isNotBlank() }?.let { "桌 $it" }
            "DayClosed" -> "日结"
            "RetailSaleCompleted" -> p.str("no").takeIf { it.isNotBlank() }?.let { "销售单 $it" }
            "RetailReturned" -> p.str("ref").ifBlank { p.str("sale") }.takeIf { it.isNotBlank() }?.let { "退货 $it" }
            "StockDocPosted" -> p.str("no").takeIf { it.isNotBlank() }?.let { "库存单 $it" }
            "StockAdjusted" -> p.obj("movement").str("productName").takeIf { it.isNotBlank() }?.let { "库存调整 $it" }
            else -> null
        }
    }.getOrNull().orEmpty()

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
            "PaymentRecorded" -> settle(p, key, e.eventId, storeNo)
            "StockDocPosted" -> stockDoc(p, e.eventId)
            "StockAdjusted" -> stockAdjust(p)
            "DayClosed" -> {
                val d = json.decodeFromJsonElement(DayClose.serializer(), p)
                val date = businessDate(d.end, storeNo)
                api.openBusinessDay("$key:open", date)   // 幂等；没开过营业日 preview 会返回 10404
                val preview = api.closePreview("$key:preview", date, currency)
                if (preview.snapshotHash.isBlank()) throw CloudException(CloudErrorKind.Rejected, 10400, "日结预览未生成快照（状态 ${preview.status}），请到后台查看日结异常项")
                // 签核不在终端做：服务端要求签核人 ≠ 预览发起人（二人复核）、营业日已结束、且用同一次预览的 closeNo + snapshotHash。
                // 终端只开营业日 + 预览，记下待复核；店长用另一个账号在后台签核。
                refs.put(CloudRef("dayclose", date, "${preview.closeNo}|${preview.snapshotHash}", preview.version))
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
        if (buyerNo.isBlank()) throw CloudException(CloudErrorKind.Rejected, 10400, "服务端员工下单要买家号（散客下单待后端支持）：请在同步中心「云端设置」里填默认买家号后重试")
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
        val b = api.placeBatch(storeNo, "$key:batch", cart.cartNo, version, q.quoteNo, confirmed.confirmationDigest.ifBlank { q.confirmationDigest }, buyerNo)
        refs.put(CloudRef("batch", batchId, b.batchNo, b.version))
        val localMinor = batch.sumOf { it.num("unit") * it.num("qty") }
        b.amountMinor?.takeIf { it != localMinor }?.let { refs.put(CloudRef("pricediff", batchId, "$localMinor/$it")) }
    }

    /**
     * 结账 → 柜台后付整桌结账（下一批方案 §3，后端 counter-settlements）：
     * 建结算单（键 事件ID:settle，按事件记住结算单号，人工重试复用）→ 比对服务端应收 → 外部记账（键 事件ID:pay，bizNo = 结算单号，
     * 现金按实收面额记，找零由服务端算）→ finalize（按结算单号幂等）。服务端说账单已变（20027）时撤销结算单，重试会重建。
     */
    private suspend fun settle(p: JsonObject, key: String, eventId: String, storeNo: String) {
        val pay = p["payment"] as? JsonObject ?: throw CloudException(CloudErrorKind.Rejected, 10400, "结账事件缺少收款记录")
        val method = pay.str("method")
        val code = OFFLINE_METHOD[method] ?: throw WaitForBackend("$method 收款走平台通道，待对接")
        val check = checkOf(pay.str("orderId").ifBlank { throw CloudException(CloudErrorKind.Rejected, 10400, "收款记录没有订单号（旧版本数据）") })
        val amount = pay.num("amount"); val received = if (code == "CASH") pay.num("received").coerceAtLeast(amount) else amount
        val currency = pay.str("currency").ifBlank { currency }

        // 人工重试复用同一张结算单，但要重读最新应收（后台可能已调整）；已取消则重建
        // 已确认过：只差放台（上一轮放台失败后的重试）
        if (refs.get("settled", eventId) != null) { ensureReleased(eventId, key, storeNo, check.remoteNo, refs.get("settled", eventId)!!.version == 1L); return }
        val known = refs.get("settlement", eventId)?.takeIf { it.remoteNo.isNotBlank() }?.let { api.counterSettlement(it.remoteNo) }?.takeIf { it.status != "CANCELLED" }
        val st = known ?: api.createCounterSettlement("$key:settle", check.remoteNo).also { refs.put(CloudRef("settlement", eventId, it.settlementNo, it.dueMinor)) }
        if (st.status == "SETTLED") { refs.put(CloudRef("settled", eventId, st.receiptNo.orEmpty().ifBlank { "-" }, if (st.tableReleased) 1 else 0)); ensureReleased(eventId, key, storeNo, check.remoteNo, st.tableReleased); return }
        // 先比金额再记钱：记了账的钱只能用于一次实收，金额对不上时不该先记
        when {
            received < st.dueMinor -> throw CloudException(CloudErrorKind.Rejected, 20024,
                "服务端应收 ${minor(st.dueMinor)}，本机实收 ${minor(received)}，还差 ${minor(st.dueMinor - received)}（多为服务费 / 价差未对齐，请在后台核对后重试）")
            code != "CASH" && amount > st.dueMinor -> throw CloudException(CloudErrorKind.Rejected, 20025,
                "服务端应收 ${minor(st.dueMinor)}，本机刷卡 ${minor(amount)}，刷卡多收不能确认（请在后台核对后重试）")
        }
        val at = java.time.Instant.ofEpochMilli(pay.num("at")).atZone(zone).toOffsetDateTime().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        val record = api.recordOffline("$key:pay", CloudOfflineRecordBody(received, currency, code, st.settlementNo, "POS ${pay.str("tableId")}", at))
        try {
            val done = api.finalizeCounterSettlement(st.settlementNo, listOf(record.recordNo))
            refs.put(CloudRef("settled", eventId, done.receiptNo.orEmpty().ifBlank { "-" }, if (done.tableReleased) 1 else 0))
            ensureReleased(eventId, key, storeNo, check.remoteNo, done.tableReleased)
        } catch (x: CloudException) {
            // 账单已变：撤销结算单（台账解冻），清掉记住的单号，人工重试时重建
            if (x.code == 20027) { runCatching { api.cancelCounterSettlement(st.settlementNo) }; refs.put(CloudRef("settlement", eventId, "", 0)) }
            throw x
        }
    }

    private var policy: CloudPolicy? = null
    private var policyLoaded = false

    /**
     * 营业日 = 门店本地时间减去切日分钟后的日期（服务端按门店时区和切日点划营业日；设备时区可能不同）。
     * 读不到策略（没权限、没配）时回退设备时区、不减切日；网络 / 登录问题照常抛出让整轮停下。
     */
    private suspend fun businessDate(at: Long, storeNo: String): String {
        if (!policyLoaded) {
            policy = try { api.storePolicy(storeNo) } catch (x: CloudException) {
                if (x.kind == CloudErrorKind.Network || x.kind == CloudErrorKind.Unauthorized) throw x else null
            }
            policyLoaded = true
        }
        val z = policy?.timezone?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
        return Instant.ofEpochMilli(at).atZone(z).minusMinutes((policy?.cutoverMinute ?: 0).toLong()).toLocalDate().toString()
    }

    // —— 零售进销存 ——
    private val ts = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    /** 服务端时间字段是不带时区的本地时间 */
    private fun local(at: Long) = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDateTime().format(ts)

    /** 本机商品（规格）→ 进销存物料：按条码找，找过的记住。称重商品服务端只收整数，等后端支持小数 */
    private suspend fun itemOf(productId: String, sku: String?, barcode: String, weighable: Boolean): String {
        val local = productId + (sku?.let { "#$it" } ?: "")
        refs.get("item", local)?.let { return it.remoteNo }
        if (weighable) throw WaitForBackend("称重商品按克计量，服务端库存只收整数，等后端支持小数")
        if (barcode.isBlank()) throw CloudException(CloudErrorKind.Rejected, 10400, "商品 $productId 没有条码，无法对应服务端物料，请补条码后重试")
        val item = try { api.inventoryItemByBarcode(barcode) } catch (x: CloudException) {
            // 门店没开进销存时这组接口不存在（推测为 10500 / 10404）：保留等待，不停整轮、不进需处理
            if (x.code == 10500 || x.kind == CloudErrorKind.NotFound) throw WaitForBackend("服务端门店未开启进销存") else throw x
        } ?: throw CloudException(CloudErrorKind.Rejected, 10400, "服务端没有条码 $barcode 对应的物料（或物料未启用），请在后台建账后重试")
        refs.put(CloudRef("item", local, item.itemId))
        return item.itemId
    }

    private fun qtyOf(v: Long): Int = if (v in 0..Int.MAX_VALUE) v.toInt() else throw CloudException(CloudErrorKind.Rejected, 10400, "数量 $v 超出范围")

    /**
     * 库存单过账 → 进销存单据。服务端没有幂等键、备注读不回来，所以先建草稿、立即记住服务端单号，再按单号过账（过账幂等）。
     * 建单请求超时时服务端可能多出一张未过账草稿（不影响库存），需人工在后台作废。
     */
    private suspend fun stockDoc(p: JsonObject, eventId: String) {
        val doc = p.obj("doc")
        val no = doc.str("no"); val kind = doc.str("kind"); val state = doc.str("state")
        val items = (p["items"] as? JsonArray)?.map { it.jsonObject }.orEmpty().associateBy { it.str("productId") + (it.str("sku").ifBlank { null }?.let { k -> "#$k" } ?: "") }
        val lines = doc["lines"]!!.jsonArray.map { it.jsonObject }
        suspend fun item(l: JsonObject): String {
            val k = l.str("productId") + (l.str("sku").ifBlank { null }?.let { "#$it" } ?: "")
            val it = items[k] ?: throw CloudException(CloudErrorKind.Rejected, 10400, "库存单缺少商品条码信息（旧版本数据）")
            return itemOf(l.str("productId"), l.str("sku").ifBlank { null }, it.str("barcode"), it["weighable"]?.jsonPrimitive?.content == "true")
        }
        val at = local(doc.num("at")); val remark = "POS $no"
        when (kind) {
            "Receive" -> {
                val inv = lines.map { l -> CloudInvLine(item(l), qtyOf(l.num("qty")), unitCostMinor = items.values.firstOrNull { it.str("productId") == l.str("productId") }?.get("costPrice")?.jsonPrimitive?.content?.toLongOrNull()) }
                val source = if (inv.all { it.unitCostMinor != null }) "PURCHASE" else "OTHER"
                val docNo = remembered(eventId) { api.createInbound(CloudInboundBody(source, doc.str("supplier").ifBlank { null }, at, remark, inv)) }
                api.postInbound(docNo)
            }
            "Loss" -> {
                // 一张报损单的行可能原因不同：服务端一张出库单一个原因，按原因拆单
                val byReason = lines.groupBy { reasonCode(it.str("reason")) }
                byReason.forEach { (code, ls) ->
                    val inv = ls.map { l -> CloudInvLine(item(l), qtyOf(l.num("qty"))) }
                    val docNo = remembered("$eventId#$code") { api.createOutbound(CloudOutboundBody("SCRAP", code, at, "$remark $code", inv)) }
                    api.postOutbound(docNo)
                }
            }
            "Count" -> {
                if (state != "Posted" && state != "Variance") return   // 只有审批过账的盘点上传
                val counted = lines.map { l -> item(l) to qtyOf(l.num("qty")) }
                val docNo = remembered(eventId) { api.openCount(counted.map { it.first }) }
                api.fillCount(docNo, counted.map { (id, q) -> CloudCountLine(id, q, "CHECK") })
                api.postCount(docNo)
            }
            "Transfer" -> throw WaitForBackend("跨店调拨需服务端库位对应，待对接")
            else -> throw CloudException(CloudErrorKind.Rejected, 10400, "未知库存单类型 $kind")
        }
    }

    /** 单品调整：上传实盘绝对值（重放结果不变） */
    private suspend fun stockAdjust(p: JsonObject) {
        val m = p.obj("movement"); val prod = p.obj("product")
        val sku = m.str("skuKey").ifBlank { null }
        val retail = prod["retail"] as? JsonObject
        val barcode = sku?.let { k -> prod["skus"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it.str("key") == k }?.str("barcode") }?.ifBlank { null }
            ?: (retail?.get("barcodes") as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content.orEmpty()
        val id = itemOf(m.str("productId"), sku, barcode, retail?.get("weighable")?.jsonPrimitive?.content == "true")
        api.adjustInventory(id, qtyOf(m.num("after")), "CHECK")
    }

    private fun reasonCode(r: String) = when (r) { "Expired" -> "EXPIRED"; "Damaged" -> "BROKEN"; else -> "OTHER" }

    /** 建单拿到服务端单号后立即记住；重试只过账，不重复建单 */
    private suspend fun remembered(local: String, create: suspend () -> String): String =
        refs.get("invdoc", local)?.remoteNo?.takeIf { it.isNotBlank() } ?: create().also { refs.put(CloudRef("invdoc", local, it)) }

    /**
     * 结账后放台：服务端规则 R4——账单的厨房任务没全部上菜 / 作废就不放台。POS 现场结账就代表这桌已吃完，
     * 所以把本账单批次下未完成的任务依次推到已上菜（start → ready → serve，每步版本 +1），再放台。
     * 没有这一步，结过账的桌在服务端会一直占用，下次开台被拒。每步键固定，失败重试只补没做完的步。
     */
    private suspend fun ensureReleased(eventId: String, key: String, storeNo: String, checkNo: String, alreadyReleased: Boolean) {
        if (alreadyReleased || refs.get("released", eventId) != null) { refs.put(CloudRef("released", eventId, checkNo)); return }
        val batchNos = api.checkBatchNos(storeNo, checkNo).toSet()
        val tasks = api.stations(storeNo).filter { it.enabled }.flatMap { api.kitchenTasks(storeNo, it.configNo) }.filter { it.batchNo in batchNos }
        for (t in tasks) {
            var v = t.version
            val steps = when (t.status) { "READY" -> listOf("serve"); "MAKING" -> listOf("ready", "serve"); else -> listOf("start", "ready", "serve") }
            for (a in steps) { api.advanceTask("$key:task:${t.taskNo}:$a", t.taskNo, a, v, t.qty); v++ }
        }
        api.releaseService("$key:release", checkNo, api.check(checkNo).version, "结账完成放台")
        refs.put(CloudRef("released", eventId, checkNo))
    }

    private fun minor(v: Long) = "%d.%02d".format(v / 100, v % 100)

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
