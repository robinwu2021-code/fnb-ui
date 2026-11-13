package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.common.PayMethods
import ai.neargo.fnb.pos.core.data.JournalEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 餐饮上传器：开台、换桌、送厨下单、结账（柜台结算单 → 外部记账 → 确认 → 放台）；点菜草稿、催菜上菜、厨房屏等只在本机 */
internal class DiningUploader(ctx: UploadContext) : Uploader(ctx) {
    override val types = setOf("TableOpened", "TableChanged", "KitchenSubmitted", "PaymentRecorded")
    /**
     * 只在本机：点菜草稿在送厨时一次带上；备注、挂单是本机操作；待清台服务端没有（评审 #26）；
     * 厨房屏店内由主 POS 仲裁；催菜 / 上菜需服务端厨房任务号，暂无「按批次查任务」接口，先不上报。
     */
    override val localOnly = setOf("DishAdded", "DishQuantityChanged", "DishNoteChanged", "OrderDraftCleared", "OrderHeld", "OrderRetrieved",
        "OrderNoteChanged", "TablePositionChanged", "TableCleaningStarted", "TableCleaningFinished",
        "KdsTicketAdvanced", "KdsTicketReleased", "KdsTicketReassigned", "DishRushed", "DishServed")
    override val config = setOf("AreaCreated", "TableConfigCreated", "TableConfigUpdated", "KitchenStationsSaved", "KitchenDeliveryPolicySaved",
        "KitchenDispatchSaved", "TablesDeleted")

    companion object {
        /** 本机收款方式 → 外部记账方式码；扫码、储值走平台通道（未对接） */
        val OFFLINE_METHOD = mapOf(PayMethods.CASH to "CASH", PayMethods.CARD to "EXTERNAL_POS")
    }

    private var catalog: CloudCatalog? = null

    override fun label(e: JournalEvent, p: JsonObject) = p.str("tableId").takeIf { it.isNotBlank() }?.let { "桌 $it" }

    /** 桌台：本机桌号 = 服务端桌台 name（门店里写在桌上的号）；拉服务端桌台表建立映射并带回桌台版本 */
    override suspend fun prepare() = refreshTables(storeNo)

    private suspend fun refreshTables(storeNo: String) {
        api.tableList(storeNo).forEach { t -> refs.put(CloudRef("table", t.name, t.tableNo, t.version)) }
    }

    private fun table(local: String) = refs.get("table", local)
        ?: throw CloudException(CloudErrorKind.NotFound, 10404, "服务端没有桌台 $local，请先在后台建桌")

    private fun checkOf(orderId: String) = refs.get("order", orderId)
        ?: throw CloudException(CloudErrorKind.NotFound, 10404, "订单还没有上云（开台未同步）")

    override suspend fun upload(e: JournalEvent, p: JsonObject, key: String) {
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
}
