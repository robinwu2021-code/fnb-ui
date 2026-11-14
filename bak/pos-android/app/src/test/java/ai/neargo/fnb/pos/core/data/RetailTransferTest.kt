package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 门店间调拨：发出扣本店库存并在调入店生成在途单，调入店确认收货才加库存。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailTransferTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }

    @Test fun transferOutThenReceive() = runTest(UnconfinedTestDispatcher()) {
        val w = LocalWorkspace(Disk(), backgroundScope) { 1_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }
        fun stock(store: String, id: String) = w.lanSnapshot().stores.getValue(store).products.first { it.id == id }.stock!!
        assertTrue(w.newStockDoc(StockDocKind.Transfer, toStore = "s-main").isFailure) // 餐饮店不行
        assertTrue(w.newStockDoc(StockDocKind.Transfer, toStore = DemoCatalog.RETAIL_STORE_ID).isFailure) // 不能调给自己
        val no = w.newStockDoc(StockDocKind.Transfer, toStore = DemoCatalog.RETAIL_STORE2_ID).getOrThrow()
        w.setStockDocLines(no, listOf(StockDocLine("cola", null, 10))).getOrThrow()
        val a0 = stock(DemoCatalog.RETAIL_STORE_ID, "cola"); val b0 = stock(DemoCatalog.RETAIL_STORE2_ID, "cola")
        assertEquals(StockDocState.Posted, w.advanceStockDoc(no).getOrThrow())
        assertEquals(a0 - 10, stock(DemoCatalog.RETAIL_STORE_ID, "cola"))
        assertEquals(b0, stock(DemoCatalog.RETAIL_STORE2_ID, "cola")) // 在途，还没加
        val incoming = w.lanSnapshot().stores.getValue(DemoCatalog.RETAIL_STORE2_ID).retail.stockDocs.first()
        assertEquals(StockDocState.InTransit, incoming.state); assertEquals(no, incoming.ref); assertEquals(DemoCatalog.RETAIL_STORE_ID, incoming.fromStore)
        // 调入店确认收货
        w.selectStore(DemoCatalog.RETAIL_STORE2_ID)
        assertEquals(StockDocState.Posted, w.advanceStockDoc(incoming.no).getOrThrow())
        assertEquals(b0 + 10, stock(DemoCatalog.RETAIL_STORE2_ID, "cola"))
        assertTrue(w.advanceStockDoc(incoming.no).isFailure) // 不能重复收货
    }
}
