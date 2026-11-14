package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.data.JournalEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 零售上传器：进销存单据（过账的入库 / 报损 / 盘点）与单品库存调整。零售销售、退货等后端的「POS 销售导入」接口，暂留在本机。
 * 零售事件的主聚合都是整个零售（retail），阻塞按单号分——一张销售单 / 库存单被拒，不挡别的单。
 */
internal class RetailUploader(ctx: UploadContext) : Uploader(ctx) {
    override val types = setOf("StockDocPosted", "StockAdjusted")
    /** 零售本机操作：挂单 / 取单、补打小票；库存单的草稿、修改、提交待审批、作废只在本机，过账时一次上传 */
    override val localOnly = setOf("RetailCartHeld", "RetailCartRecalled", "RetailReceiptReprinted",
        "StockDocCreated", "StockDocUpdated", "StockDocSubmitted", "StockDocVoided")
    override val config = setOf("RetailPriceChanged", "LabelQueued", "LabelsPrinted", "RetailSettingsSaved")

    /** 餐饮门店的库存调整只在本机 */
    override fun handledLocally(e: JournalEvent) = super.handledLocally(e) || (!ctx.inventory && e.type == "StockAdjusted")

    override fun blockKey(e: JournalEvent, p: JsonObject?): String? {
        if (e.aggregateId != "retail") return null
        if (p == null) return "retail:${e.eventId}"
        return when (e.type) {
            "RetailSaleCompleted" -> "sale:" + p.str("no")
            "RetailReturned" -> "sale:" + p.str("ref").ifBlank { p.str("sale") }
            else -> if (e.type.startsWith("StockDoc")) "stockdoc:" + p.str("no") else "retail:${e.eventId}"
        }
    }

    override fun label(e: JournalEvent, p: JsonObject): String? = when (e.type) {
        "RetailSaleCompleted" -> p.str("no").takeIf { it.isNotBlank() }?.let { "销售单 $it" }
        "RetailReturned" -> p.str("ref").ifBlank { p.str("sale") }.takeIf { it.isNotBlank() }?.let { "退货 $it" }
        "StockDocPosted" -> p.str("no").takeIf { it.isNotBlank() }?.let { "库存单 $it" }
        "StockAdjusted" -> p.obj("movement").str("productName").takeIf { it.isNotBlank() }?.let { "库存调整 $it" }
        else -> null
    }

    override suspend fun upload(e: JournalEvent, p: JsonObject, key: String) {
        when (e.type) {
            "StockDocPosted" -> stockDoc(p, e.eventId)
            "StockAdjusted" -> stockAdjust(p)
        }
    }

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
}
