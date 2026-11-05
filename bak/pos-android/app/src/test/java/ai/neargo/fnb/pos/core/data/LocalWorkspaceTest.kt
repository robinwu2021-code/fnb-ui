package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.OutboxKind
import ai.neargo.fnb.pos.core.model.OutboxState
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalWorkspaceTest {
    private class MemoryStore : SnapshotStore {
        var json: String? = null
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
    }

    private fun ws(store: SnapshotStore, scope: CoroutineScope) = LocalWorkspace(store, scope) { 1_000_000L }
    private suspend fun LocalWorkspace.main() = also { enterDemo(); selectStore("s-main") }
    private fun LocalWorkspace.product(id: String) = current.value!!.products.first { it.id == id }

    private fun runWs(block: suspend TestScope.(LocalWorkspace, MemoryStore) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val store = MemoryStore()
        block(ws(store, backgroundScope).main(), store)
    }

    /** 必测场景 1：A 桌加菜 → 切 B 桌 → 回 A 桌，不串单。 */
    @Test fun ordersIsolatedPerTable() = runWs { w, _ ->
        val before = w.orders.value.getValue("A03").lines.size
        w.addDish("A02", w.product("12"), null, 1)
        w.selectTable("A02"); w.selectTable("A03")
        assertEquals(before, w.orders.value.getValue("A03").lines.size)
        assertTrue(w.orders.value.getValue("A02").lines.any { it.dishId == "12" && it.status == LineStatus.New })
    }

    /** 切店隔离：海岸店看不到总店订单，切回后恢复。 */
    @Test fun storesIsolated() = runWs { w, _ ->
        w.addDish("A02", w.product("12"), null, 1)
        w.selectStore("s-coast")
        assertTrue(w.orders.value.values.all { it.lines.isEmpty() })
        assertEquals("H01", w.session.value.currentTableId)
        w.selectStore("s-main")
        assertTrue(w.orders.value.getValue("A02").lines.any { it.dishId == "12" })
    }

    /** 必测场景 5：离线送厨 → 落盘 → 重启 → 补发，只产生一个批次，重复补发不重复。 */
    @Test fun offlineKitchenQueueSurvivesRestartAndSyncsOnce() = runWs { w, store ->
        val r = w.sendToKitchen("A03", online = false)
        assertEquals(5, r.qty); assertTrue(r.queued)
        assertTrue(w.orders.value.getValue("A03").lines.filter { it.status == LineStatus.Queued }.sumOf { it.qty } == 5)
        assertEquals(1, w.pendingCount.value)
        advanceUntilIdle()

        val restored = ws(store, backgroundScope).also { it.restore() }
        assertEquals(1, restored.pendingCount.value)
        assertEquals(1, restored.syncNow())
        assertEquals(0, restored.syncNow())
        val lines = restored.orders.value.getValue("A03").lines
        assertTrue(lines.none { it.status == LineStatus.Queued })
        assertEquals(1, restored.outbox.value.count { it.kind == OutboxKind.Kitchen && it.state == OutboxState.Done })
    }

    @Test fun offlinePaymentQueuedAndCheckoutBlockedByQueuedLines() = runWs { w, _ ->
        w.sendToKitchen("A03", online = false)
        assertTrue(w.settle("A03", 100, "现金", online = false).isFailure) // 待同步行阻止结账
        w.syncNow()
        assertTrue(w.settle("A03", 25620, "现金", online = false).isSuccess)
        val s = w.current.value!!
        assertEquals(TableStatus.Paid, s.tables.first { it.id == "A03" }.status)
        assertEquals(false, s.payments.single().synced)
        assertEquals(1, w.pendingCount.value)
        w.syncNow()
        assertEquals(true, w.current.value!!.payments.single().synced)
    }

    @Test fun localAccountRegisterLoginLogout() = runWs { w, _ ->
        assertTrue(w.register("南山", "boss@ns.cn", "password1", "password2").isFailure)
        assertTrue(w.register("南山", "boss@ns.cn", "password1", "password1").isSuccess)
        assertNull(w.session.value.storeId) // 注册后先选店
        w.logout()
        assertTrue(w.login("boss@ns.cn", "wrongpass1").isFailure)
        assertTrue(w.login("boss@ns.cn", "password1").isSuccess)
        assertEquals("boss@ns.cn", w.session.value.user)
    }

    /** 商品需加入已发布餐牌才进入点餐；解除后隐藏，已有订单行保留。 */
    @Test fun productVisibilityFollowsPublishedMenus() = runWs { w, _ ->
        val draftMenu = w.current.value!!.menus.first { !it.published }
        assertTrue(w.current.value!!.visibleProducts.none { it.id == "15" })
        assertTrue(w.setPublished(draftMenu.id, true).isSuccess)
        assertTrue(w.current.value!!.visibleProducts.any { it.id == "15" })
        w.addDish("A02", w.product("12"), null, 1)
        assertTrue(w.saveProduct(w.product("12"), emptySet()).isSuccess)
        assertTrue(w.current.value!!.visibleProducts.none { it.id == "12" })
        assertTrue(w.orders.value.getValue("A02").lines.any { it.dishId == "12" })
    }

    @Test fun tableAndAreaManagement() = runWs { w, _ ->
        assertTrue(w.addArea("一层大厅", "").isFailure)
        assertTrue(w.addArea("露台", "Terrace").isSuccess)
        val area = w.current.value!!.areas.last().id
        assertTrue(w.saveTable("A03", area, "4", isNew = true).isFailure)
        assertTrue(w.saveTable("t9", area, "31", isNew = true).isFailure)
        assertTrue(w.saveTable("t9", area, "6", isNew = true).isSuccess)
        assertTrue(w.openTable("T9", 6).isSuccess)
        assertTrue(w.addDish("T9", w.product("9"), null, 2) is OrderResult.Ok)
    }

    @Test fun stockAdjustRequiresReasonAndAppliesToSku() = runWs { w, _ ->
        val p = w.product("1")
        val sku = p.skus.first()
        assertTrue(w.adjustStock(p.id, sku.key, 0, "").isFailure)
        assertTrue(w.adjustStock(p.id, sku.key, 0, "售罄").isSuccess)
        assertEquals(0, w.product("1").skus.first { it.key == sku.key }.stock)
        assertTrue(w.addDish("A02", w.product("1"), w.product("1").skus.first { it.key == sku.key }, 1) is OrderResult.Rejected)
    }
}
