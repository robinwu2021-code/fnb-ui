package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.data.JournalEvent
import ai.neargo.fnb.pos.core.model.DayClose
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.ZoneId

/** 公共上传器（两业态共用）：日结；开钱箱和门店配置类事件只在本机 */
internal class CommonUploader(ctx: UploadContext) : Uploader(ctx) {
    override val types = setOf("DayClosed")
    override val localOnly = setOf("DrawerOpened")
    /** 配置类：菜品、菜单、员工、门店设置、活动等。不走营业回放——配置以云端为准、由云端下发到店（后续批次） */
    override val config = setOf("PromotionSaved", "StoreSettingsUpdated", "CategoryCreated", "SpecGroupCreated", "ProductCreated", "ProductUpdated",
        "MenuCreated", "MenuUpdated", "MenuPublished", "MenuUnpublished", "EmployeeSaved", "ProductsDeleted", "ProductsRepriced")

    override fun label(e: JournalEvent, p: JsonObject) = if (e.type == "DayClosed") "日结" else null

    override suspend fun upload(e: JournalEvent, p: JsonObject, key: String) {
                val d = json.decodeFromJsonElement(DayClose.serializer(), p)
                val date = businessDate(d.end, storeNo)
                api.openBusinessDay("$key:open", date)   // 幂等；没开过营业日 preview 会返回 10404
                val preview = api.closePreview("$key:preview", date, currency)
                if (preview.snapshotHash.isBlank()) throw CloudException(CloudErrorKind.Rejected, 10400, "日结预览未生成快照（状态 ${preview.status}），请到后台查看日结异常项")
                // 签核不在终端做：服务端要求签核人 ≠ 预览发起人（二人复核）、营业日已结束、且用同一次预览的 closeNo + snapshotHash。
                // 终端只开营业日 + 预览，记下待复核；店长用另一个账号在后台签核。
                refs.put(CloudRef("dayclose", date, "${preview.closeNo}|${preview.snapshotHash}", preview.version))
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
}
