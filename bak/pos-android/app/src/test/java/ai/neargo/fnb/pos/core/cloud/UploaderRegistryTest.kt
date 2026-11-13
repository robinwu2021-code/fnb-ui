package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.data.JournalEvent
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

/** 各业态上传器的事件分工：不重叠；等后端接口的事件清单显式列出，新增接口时要同步改这里 */
class UploaderRegistryTest {
    private fun uploaders(inventory: Boolean = true): List<EventUploader> {
        val ctx = UploadContext(CloudSyncTest.FakeServer(), MemoryCloudRefs(), "NS", ZoneId.of("UTC"), "CNY", "", inventory)
        return listOf(CommonUploader(ctx), DiningUploader(ctx), RetailUploader(ctx))
    }
    private fun ev(type: String, aggregate: String = "x") = JournalEvent(1, "e1", aggregate, 1, type, "{}", envelope = "{}")

    /** 服务端还没有接口、按设计留在本机的事件（不算待上传） */
    private val waitingForBackend = setOf(
        "ReservationCreated", "ReservationCancelled", "ReservationCheckedIn", "TableMerged", "OrderSplit", "OrderMemberChanged", "OrderBenefitsChanged",
        "DishVoided", "DishGifted", "OrderDiscounted", "MemberCreated", "MemberRecharged", "ShiftClosed", "OnlineOrderReceived", "OnlineOrderAdvanced", "OnlineOrderRejected",
        "RetailSaleCompleted", "RetailReturned",
    )

    @Test fun eachTypeBelongsToExactlyOneUploaderAndOneKind() {
        val all = uploaders().flatMap { u -> u.types.map { it to "${u::class.simpleName}.types" } + u.localOnly.map { it to "${u::class.simpleName}.localOnly" } + u.config.map { it to "${u::class.simpleName}.config" } }
        val dup = all.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
        assertTrue("事件类型被重复认领：$dup", dup.isEmpty())
    }

    @Test fun waitingListIsNotHandledByAnyone() {
        val handled = uploaders().flatMap { it.types + it.localOnly + it.config }.toSet()
        val stale = waitingForBackend intersect handled
        assertTrue("这些事件已有上传器处理，请从等后端清单里删掉：$stale", stale.isEmpty())
    }

    @Test fun inventoryAdjustIsLocalOnlyForDiningStores() {
        assertTrue(uploaders(inventory = false).any { it.handledLocally(ev("StockAdjusted")) })
        assertFalse(uploaders(inventory = true).any { it.handledLocally(ev("StockAdjusted")) })
        assertTrue(uploaders(inventory = true).any { "StockAdjusted" in it.types })
    }

    @Test fun blockKeysAndLabelsComeFromTheRightUploader() {
        val retail = ev("RetailSaleCompleted", "retail").copy(payload = """{"no":"#R0158"}""")
        val p = kotlinx.serialization.json.Json.parseToJsonElement(retail.payload) as kotlinx.serialization.json.JsonObject
        assertEquals("sale:#R0158", uploaders().firstNotNullOfOrNull { it.blockKey(retail, p) })              // 零售按单号挡
        assertEquals("销售单 #R0158", uploaders().firstNotNullOfOrNull { it.label(retail, p) })
        assertNull(uploaders().firstNotNullOfOrNull { it.blockKey(ev("TableOpened", "order:O-1"), null) })    // 餐饮回退按主聚合
        val table = kotlinx.serialization.json.Json.parseToJsonElement("""{"tableId":"H01"}""") as kotlinx.serialization.json.JsonObject
        assertEquals("桌 H01", uploaders().firstNotNullOfOrNull { it.label(ev("PaymentRecorded"), table) })
        assertEquals("日结", uploaders().firstNotNullOfOrNull { it.label(ev("DayClosed"), table) })
    }
}
