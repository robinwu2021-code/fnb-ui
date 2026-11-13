package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.CartLine
import ai.neargo.fnb.pos.core.model.ReturnRequest
import ai.neargo.fnb.pos.core.model.ReturnRules
import ai.neargo.fnb.pos.core.model.StockDocKind
import ai.neargo.fnb.pos.core.model.StockDocLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.*
import org.junit.Test

/** 零售事件载荷：销售、退货、库存单都带齐当时的业务事实（云端回放不再读当前快照） */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailEventPayloadsTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        val commands = mutableListOf<JournalCommand>()
        override suspend fun load() = data
        override suspend fun save(json: String) { data = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { data = source; commands += command; return testReceipt(command) }
    }

    private suspend fun shop(scope: kotlinx.coroutines.CoroutineScope): Pair<LocalWorkspace, Disk> {
        val disk = Disk()
        val w = LocalWorkspace(disk, scope) { 1_800_000_000_000L }
        w.enterDemo(); w.selectStore(DemoCatalog.RETAIL_STORE_ID)
        disk.commands.clear()
        return w to disk
    }
    private fun Disk.payload(type: String) = Json.parseToJsonElement(commands.last { it.type == type }.payload).jsonObject

    @Test fun saleCarriesFullSale() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val sale = w.checkoutRetail(listOf(CartLine(1, "cola", 3), CartLine(2, "water", 1)), false, "现金", 100_00, true).getOrThrow()
        val p = disk.payload("RetailSaleCompleted")
        assertEquals(sale.no, p["no"]!!.jsonPrimitive.content); assertEquals("现金", p["method"]!!.jsonPrimitive.content)
        assertEquals(sale.paid, p["amount"]!!.jsonPrimitive.long)
        val s = p["sale"]!!.jsonObject
        assertEquals(sale.lines.size, s["lines"]!!.jsonArray.size)
        assertEquals(sale.lines.sumOf { it.amount }, s["lines"]!!.jsonArray.sumOf { it.jsonObject["amount"]!!.jsonPrimitive.long })
        assertEquals("现金", s["pays"]!!.jsonArray.single().jsonObject["method"]!!.jsonPrimitive.content)
    }

    @Test fun partialReturnCarriesReturnedLines() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val sale = w.checkoutRetail(listOf(CartLine(1, "cola", 3)), false, "现金", 100_00, true).getOrThrow()
        val key = sale.lines.single().key
        w.returnRetail(ReturnRequest(sale.no, mapOf(key to 2), ReturnRules.Reason.Unwanted, restock = true, method = "现金"), true).getOrThrow()
        val p = disk.payload("RetailReturned")
        assertEquals(sale.no, p["sale"]!!.jsonPrimitive.content); assertEquals(sale.no, p["ref"]!!.jsonPrimitive.content)
        val line = p["lines"]!!.jsonArray.single().jsonObject
        assertEquals(key, line["key"]!!.jsonPrimitive.content); assertEquals(2L, line["qty"]!!.jsonPrimitive.long)
        assertTrue(p["return"]!!.jsonObject["amount"]!!.jsonPrimitive.long > 0)
        assertEquals("true", p["restock"]!!.jsonPrimitive.content)
    }

    @Test fun stockDocEventsCarryDoc() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val no = w.newStockDoc(StockDocKind.Receive, supplier = "供应商A").getOrThrow()
        assertEquals(no, disk.payload("StockDocCreated")["no"]!!.jsonPrimitive.content)
        w.setStockDocLines(no, listOf(StockDocLine("cola", null, 24))).getOrThrow()
        assertEquals(24L, disk.payload("StockDocUpdated")["doc"]!!.jsonObject["lines"]!!.jsonArray.single().jsonObject["qty"]!!.jsonPrimitive.long)
        w.advanceStockDoc(no).getOrThrow()
        val posted = disk.payload("StockDocPosted")
        assertEquals("Posted", posted["state"]!!.jsonPrimitive.content); assertEquals("Receive", posted["kind"]!!.jsonPrimitive.content)
        assertEquals("供应商A", posted["doc"]!!.jsonObject["supplier"]!!.jsonPrimitive.content)
    }
}
