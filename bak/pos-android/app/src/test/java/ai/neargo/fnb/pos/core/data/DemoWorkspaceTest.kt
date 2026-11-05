package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoWorkspaceTest {
    private class MemoryStore : SnapshotStore {
        var json: String? = null
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
    }

    private val menu = DemoMenuRepository()

    private fun workspace(store: SnapshotStore, scope: CoroutineScope) = DemoWorkspace(store, menu, scope)

    /** 必测场景 1：A 桌草稿加菜 → 切 B 桌 → 返回 A 桌，行项目不串。 */
    @Test fun ordersAreIsolatedPerTable() = runTest(UnconfinedTestDispatcher()) {
        val ws = workspace(MemoryStore(), backgroundScope)
        val a03Before = ws.orders.value.getValue("A03").lines.size
        ws.addDish("A02", menu.dish("12"), "", 1200, 1)
        ws.selectTable("A02")
        ws.selectTable("A03")
        assertEquals(a03Before, ws.orders.value.getValue("A03").lines.size)
        assertTrue(ws.orders.value.getValue("A02").lines.any { it.dishId == "12" && it.status == LineStatus.New })
    }

    @Test fun cannotOrderOnIdleTable() = runTest(UnconfinedTestDispatcher()) {
        val ws = workspace(MemoryStore(), backgroundScope)
        assertTrue(ws.addDish("A01", menu.dish("9"), "", 300, 1) is OrderResult.Rejected)
    }

    @Test fun openTableValidatesGuestsAndClearsOrder() = runTest(UnconfinedTestDispatcher()) {
        val ws = workspace(MemoryStore(), backgroundScope)
        assertTrue(ws.openTable("A01", 3).isFailure) // A01 只有 2 座
        assertTrue(ws.openTable("A01", 2).isSuccess)
        assertEquals(TableStatus.Dining, ws.tables.value.first { it.id == "A01" }.status)
        assertEquals("A01", ws.session.value.currentTableId)
        assertTrue(ws.openTable("A01", 2).isFailure) // 重复开台被拒
    }

    @Test fun settleRequiresKitchenFirst() = runTest(UnconfinedTestDispatcher()) {
        val ws = workspace(MemoryStore(), backgroundScope)
        assertTrue(ws.settle("A03", 100).isFailure) // A03 有未送厨菜品
        ws.sendToKitchen("A03")
        assertTrue(ws.settle("A03", 25620).isSuccess)
        val t = ws.tables.value.first { it.id == "A03" }
        assertEquals(TableStatus.Paid, t.status); assertEquals(25620, t.paid)
        assertTrue(ws.orders.value.getValue("A03").lines.isEmpty())
        assertTrue(ws.finishCleaning("A03").isFailure) // 必须先进入清台
        assertTrue(ws.startCleaning("A03").isSuccess)
        assertTrue(ws.finishCleaning("A03").isSuccess)
    }

    /** 重启恢复：快照写入后，新实例读回草稿与当前桌台。 */
    @Test fun snapshotSurvivesRestart() = runTest(UnconfinedTestDispatcher()) {
        val store = MemoryStore()
        val ws = workspace(store, backgroundScope)
        ws.addDish("A02", menu.dish("12"), "", 1200, 2)
        ws.selectTable("A02")
        advanceUntilIdle()
        assertNotNull(store.json)

        val restored = workspace(store, backgroundScope)
        restored.restore()
        assertEquals("A02", restored.session.value.currentTableId)
        assertEquals(2, restored.orders.value.getValue("A02").lines.first { it.dishId == "12" }.qty)
    }
}
