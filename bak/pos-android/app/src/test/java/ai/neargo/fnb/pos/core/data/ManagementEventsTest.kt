package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.OrderRules
import ai.neargo.fnb.pos.core.model.StoreSettings
import ai.neargo.fnb.pos.core.model.TableStatus
import ai.neargo.fnb.pos.core.model.TaskLogKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/** N1-03：门店配置、库存流水、交班事件；打印/同步只进本机任务日志。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManagementEventsTest {
    private class JournalStore : SnapshotStore {
        var json: String? = null
        val commands = mutableListOf<JournalCommand>()
        var failNext = false
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt {
            if (failNext) { failNext = false; error("disk") }
            json = source; commands += command
            return testReceipt(command)
        }
    }

    private fun runWs(block: suspend TestScope.(LocalWorkspace, JournalStore) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val disk = JournalStore()
        val w = LocalWorkspace(disk, backgroundScope) { 1_000_000L }
        w.enterDemo(); w.selectStore("s-main")
        disk.commands.clear()
        block(w, disk)
    }
    private fun JournalStore.types() = commands.map { it.type }
    private fun JournalStore.payload(i: Int = commands.lastIndex): JsonObject = Json.parseToJsonElement(commands[i].payload).jsonObject
    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content

    @Test fun configEntitiesEmitTypedEventsWithoutTableId() = runWs { w, d ->
        assertTrue(w.addArea("露台", "Terrace").isSuccess)
        assertTrue(w.addArea("露台", "Terrace").isFailure) // 重名
        val area = w.current.value!!.areas.last()
        assertEquals("area", d.payload().str("entity"))
        assertEquals(area.id, d.payload().str("entityId"))
        assertFalse(d.payload().containsKey("tableId"))

        assertTrue(w.saveTable("T01", area.id, "4", isNew = true).isSuccess)
        assertTrue(w.saveTable("T01", area.id, "6", isNew = false).isSuccess)
        val upd = d.payload().getValue("changes").jsonArray.single().jsonObject
        assertEquals("4", upd.getValue("before").jsonObject.str("seats"))
        assertEquals("6", upd.getValue("after").jsonObject.str("seats"))

        assertTrue(w.addCategory("夜宵", "Late night").isSuccess)
        assertTrue(w.addSpecGroup("测试规格", "Test spec", "标准:0,大份:5").isSuccess)
        val settings = w.current.value!!.settings
        assertTrue(w.saveSettings(settings.copy(receiptFooter = "欢迎下次光临")).isSuccess)
        assertTrue(w.saveSettings(StoreSettings(" ")).isFailure)
        assertEquals(listOf("AreaCreated", "TableConfigCreated", "TableConfigUpdated", "CategoryCreated", "SpecGroupCreated"), d.types().take(5))
        assertEquals("StoreSettingsUpdated", d.types().last())
        assertTrue(d.commands.none { "password" in it.payload || "\"hash\"" in it.payload || "\"font\"" in it.payload })
    }

    @Test fun productAndMenuEventsCarryLinkedChanges() = runWs { w, d ->
        val p = w.current.value!!.products.first()
        assertTrue(w.saveMenu(null, "早市", "Morning", listOf(p.id)).isSuccess)
        val menu = w.current.value!!.menus.last()
        assertEquals("MenuCreated", d.types().last())
        val publish = w.setPublished(menu.id, true)
        if (publish.isSuccess) assertEquals("MenuPublished", d.types().last())
        val menus = w.current.value!!.menus.filter { p.id in it.productIds }.map { it.id }.toSet() - menu.id
        assertTrue(w.saveProduct(p.copy(price = p.price + 100), menus).isSuccess)
        assertEquals("ProductUpdated", d.types().last())
        val changes = d.payload().getValue("changes").jsonArray.map { it.jsonObject.str("entity") }
        assertTrue("product" in changes && "menu" in changes) // 解除早市关联也被记录
        assertEquals(p.id, d.payload().str("entityId"))
    }

    @Test fun stockAdjustmentWritesLedgerAndEvent() = runWs { w, d ->
        val p = w.current.value!!.products.first { it.skus.isEmpty() && it.stock != null }
        assertTrue(w.adjustStock(p.id, null, -1, "盘点").isFailure)
        assertTrue(w.adjustStock(p.id, null, 9, " ").isFailure)
        assertTrue(w.adjustStock(p.id, "nope", 9, "盘点").isFailure)
        assertTrue(d.commands.isEmpty())
        assertTrue(w.adjustStock(p.id, null, p.stock!! + 3, "到货").isSuccess)
        val m = w.current.value!!.stockMovements.single()
        assertEquals(p.stock, m.before); assertEquals(3, m.delta); assertEquals("到货", m.reason); assertEquals("体验账号", m.actor)
        assertEquals("StockAdjusted", d.types().single())
        val mv = d.payload().getValue("movement").jsonObject
        assertEquals(m.id, mv.str("id")); assertEquals("3", mv.str("delta"))
        assertEquals((p.stock!! + 3).toString(), d.payload().getValue("product").jsonObject.str("stock"))
        val sku = w.current.value!!.products.first { it.skus.isNotEmpty() }
        assertTrue(w.adjustStock(sku.id, sku.skus.first().key, 2, "报损").isSuccess)
        assertEquals(sku.skus.first().key, w.current.value!!.stockMovements.last().skuKey)
    }

    @Test fun shiftClosedFixesRecordAndLedgerScope() = runWs { w, d ->
        val t = w.current.value!!.tables.first { it.status == TableStatus.Idle }.id
        w.openTable(t, 1); w.addDish(t, w.current.value!!.products.first { it.id == "12" }, null, 1); w.sendToKitchen(t, true)
        val due = OrderRules.totals(w.orders.value.getValue(t)).due
        w.settle(t, due, "现金", false, due)
        d.commands.clear()
        assertTrue(w.closeShift(-1, "林晓").isFailure)
        assertTrue(d.commands.isEmpty())
        val rec = w.closeShift(50_000 + due, "林晓").getOrThrow()
        assertEquals("ShiftClosed", d.types().single())
        val p = d.payload()
        assertEquals(rec.id, p.getValue("record").jsonObject.str("id"))
        val pay = w.current.value!!.payments.last().id
        assertTrue(p.getValue("paymentIds").jsonArray.any { it.jsonPrimitive.content == pay })
        assertTrue(p.getValue("unsyncedPaymentIds").jsonArray.any { it.jsonPrimitive.content == pay })
        assertEquals(1, p.getValue("printJobIds").jsonArray.size)
        assertTrue(p.getValue("pendingOutboxIds").jsonArray.isNotEmpty())
    }

    @Test fun printAndSyncGoToTaskLogNotBusinessEvents() = runWs { w, d ->
        val t = w.current.value!!.tables.first { it.status == TableStatus.Idle }.id
        w.openTable(t, 1); w.addDish(t, w.current.value!!.products.first { it.id == "12" }, null, 1)
        w.sendToKitchen(t, false)
        d.commands.clear()
        assertTrue(w.preBill(t).isSuccess)
        w.process(kitchenOk = true)
        val job = w.jobs.value.first { it.kind == ai.neargo.fnb.pos.core.model.PrintKind.PreBill }
        assertTrue(w.reprint(job.id).isSuccess)
        assertEquals(1, w.syncNow())
        w.process(kitchenOk = false)
        assertTrue(d.commands.isEmpty())
        val log = w.current.value!!.taskLog
        val kinds = log.map { it.kind }
        assertTrue(kinds.indexOf(TaskLogKind.PrintRequested) < kinds.indexOf(TaskLogKind.PrintReprinted))
        assertTrue(kinds.indexOf(TaskLogKind.PrintReprinted) < kinds.indexOf(TaskLogKind.SyncAttempt))
        assertTrue(log.filter { it.kind == TaskLogKind.PrintAttempt || it.kind == TaskLogKind.SyncAttempt }.all { it.source == "simulation" })
        // 缺纸：新的厨房单失败并记入日志
        w.addDish(t, w.current.value!!.products.first { it.id == "9" }, null, 1)
        w.sendToKitchen(t, true)
        w.process(kitchenOk = false)
        assertTrue(w.current.value!!.taskLog.any { it.kind == TaskLogKind.PrintAttempt && it.result == "Failed" })
        assertTrue(d.commands.all { it.type == "DishAdded" || it.type == "KitchenSubmitted" })
        assertTrue(log.first { it.kind == TaskLogKind.PrintReprinted }.detail.contains(job.id))
    }

    @Test fun failedWriteKeepsStockAndConfigUnchanged() = runWs { w, d ->
        val before = w.current.value
        val p = before!!.products.first { it.skus.isEmpty() && it.stock != null }
        d.failNext = true
        assertTrue(w.adjustStock(p.id, null, 1, "盘点").isFailure)
        d.failNext = true
        assertTrue(w.addCategory("新分类", "").isFailure)
        assertEquals(before, w.current.value)
        assertTrue(d.commands.isEmpty())
    }
}
