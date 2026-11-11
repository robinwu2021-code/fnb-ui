package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import ai.neargo.fnb.pos.navigation.TopLevel
import ai.neargo.fnb.pos.navigation.topTabs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

/** R0：门店业态决定进餐饮还是零售（同一个 App）；旧数据没有业态字段时按餐饮读。 */
@OptIn(ExperimentalCoroutinesApi::class)
class BizTypeTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }

    @Test fun storesCarryBizAndSessionFollowsSelectedStore() = runTest(UnconfinedTestDispatcher()) {
        val w = LocalWorkspace(Disk(), backgroundScope) { 1000000L }; w.enterDemo()
        val byId = w.stores.value.associateBy { it.id }
        assertEquals(BizType.Dining, byId.getValue("s-main").biz)
        assertEquals(BizType.Retail, byId.getValue(DemoCatalog.RETAIL_STORE_ID).biz)
        w.selectStore(DemoCatalog.RETAIL_STORE_ID)
        assertEquals(BizType.Retail, w.session.value.biz)
        w.selectStore("s-main")
        assertEquals(BizType.Dining, w.session.value.biz)
    }

    @Test fun oldSnapshotWithoutBizReadsAsDiningAndGainsRetailDemoStore() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk(); val w = LocalWorkspace(disk, backgroundScope) { 1000000L }; w.enterDemo(); w.selectStore("s-main"); yield()
        // 模拟旧版本快照：去掉所有 biz 字段和零售演示门店
        val json = kotlinx.serialization.json.Json.parseToJsonElement(disk.data!!)
        fun strip(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement = when (e) {
            is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(e.filterKeys { it != "biz" && it != DemoCatalog.RETAIL_STORE_ID }.mapValues { strip(it.value) })
            is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map { strip(it) })
            else -> e
        }
        disk.data = strip(json).toString()
        assertFalse(disk.data!!.contains(DemoCatalog.RETAIL_STORE_ID))
        val restored = LocalWorkspace(disk, backgroundScope) { 1000000L }; restored.restore(); yield()
        val byId = restored.stores.value.associateBy { it.id }
        assertEquals(BizType.Dining, byId.getValue("s-main").biz)
        assertEquals(BizType.Retail, byId.getValue(DemoCatalog.RETAIL_STORE_ID).biz)
    }

    @Test fun tabsFollowBiz() {
        assertEquals(listOf(TopLevel.Tables, TopLevel.Order), topTabs(BizType.Dining))
        assertEquals(listOf(TopLevel.RetailPos, TopLevel.RetailOrders, TopLevel.Goods, TopLevel.StockWork), topTabs(BizType.Retail))
    }
}
