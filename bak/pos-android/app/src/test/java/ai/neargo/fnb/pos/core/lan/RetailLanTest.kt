package ai.neargo.fnb.pos.core.lan

import ai.neargo.fnb.pos.core.data.DemoCatalog
import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LanRole
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.RoutedRetail
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.identity.SoftwareDeviceKeys
import ai.neargo.fnb.pos.core.model.CartLine
import ai.neargo.fnb.pos.core.model.ReturnRequest
import ai.neargo.fnb.pos.core.model.ReturnRules
import ai.neargo.fnb.pos.core.model.StockDocKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/** 零售接入主副 POS：副机收款、退货、挂取单经主机仲裁入账；库存单据、改价等只能在主机做。 */
class RetailLanTest {
    private class Store : SnapshotStore, CommandReceipts {
        @Volatile var json: String? = null
        private val ids = ConcurrentHashMap.newKeySet<String>()
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt {
            json = source; ids += "${command.storeId}/${command.deviceId}/${command.commandId}"; return testReceipt(command)
        }
        override suspend fun committed(storeId: String, deviceId: String, commandId: String) = "$storeId/$deviceId/$commandId" in ids
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }

    private class Node(val ws: LocalWorkspace, val store: Store, val keys: SoftwareDeviceKeys)
    private suspend fun node() = Store().let { st -> Node(LocalWorkspace(st, scope).also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }, st, SoftwareDeviceKeys()) }

    @Test fun secondariesSellAndReturnThroughHost() = runBlocking {
        val h = node(); h.ws.updateLan { it.copy(role = LanRole.Host) }
        val server = LanHost(h.ws, HostExecutor(h.ws, h.ws, h.ws, h.store, retail = h.ws), h.keys, scope)
        server.start(port = 0, bind = "127.0.0.1")
        val addr = "127.0.0.1:${server.status.value.port}"
        try {
            val a = node(); val la = LanLink(a.ws, a.keys, scope, deviceName = "副机A"); assertTrue(la.pair(addr, server.newPairingCode().code).isSuccess)
            val b = node(); val lb = LanLink(b.ws, b.keys, scope, deviceName = "副机B"); assertTrue(lb.pair(addr, server.newPairingCode().code).isSuccess)
            val ra = RoutedRetail(a.ws, la); val rb = RoutedRetail(b.ws, lb)
            val cola0 = h.ws.lanSnapshot().store!!.products.first { it.id == "cola" }.stock!!
            // 两台副机同时收款：主机串行入账，单号不重复，库存两次都扣
            val sales = listOf(
                async { ra.checkoutRetail(listOf(CartLine(1, "cola", 2)), false, "现金", 1000, true) },
                async { rb.checkoutRetail(listOf(CartLine(1, "cola", 3)), false, "现金", 2000, true) },
            ).awaitAll().map { it.getOrThrow() }
            assertEquals(2, sales.map { it.no }.toSet().size)
            val hs = h.ws.lanSnapshot().store!!
            assertEquals(cola0 - 5, hs.products.first { it.id == "cola" }.stock)
            assertTrue(sales.all { s -> hs.retail.sales.any { it.no == s.no } })
            assertEquals(2, hs.payments.count { it.orderId in sales.map { s -> s.no } })
            // 副机退货：主机记退货、退款
            val r = ra.returnRetail(ReturnRequest(sales[0].no, mapOf("L1" to 1), ReturnRules.Reason.Unwanted, true, "现金"), true).getOrThrow()
            assertEquals(350, r.amount)
            assertEquals(1, h.ws.lanSnapshot().store!!.retail.sales.first { it.no == sales[0].no }.lines.first().returned)
            // 挂单 / 取单
            val held = ra.holdCart(listOf(CartLine(1, "chips", 1)), null, "顾客暂离").getOrThrow()
            assertTrue(h.ws.lanSnapshot().store!!.retail.held.any { it.no == held })
            rb.recallHeld(held).getOrThrow().let { assertEquals("chips", it.lines.single().productId) }
            assertTrue(h.ws.lanSnapshot().store!!.retail.held.none { it.no == held })
            // 管理类只能在主机
            assertEquals("仅主 POS 可执行此操作", ra.newStockDoc(StockDocKind.Receive).exceptionOrNull()?.message)
            assertTrue(ra.setRetailPrice("cola", 999).isFailure)
        } finally { server.stop() }
    }
}
