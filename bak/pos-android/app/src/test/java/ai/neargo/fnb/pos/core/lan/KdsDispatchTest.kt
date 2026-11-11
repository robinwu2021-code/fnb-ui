package ai.neargo.fnb.pos.core.lan

import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LanRole
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.identity.SoftwareDeviceKeys
import ai.neargo.fnb.pos.core.model.KdsDispatch
import ai.neargo.fnb.pos.core.model.KdsState
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/** 方案 C：同档口两块厨房屏——同步显示不变；先接单者负责、非负责屏被拒、释放、主管收回/指派。 */
class KdsDispatchTest {
    private class Store : SnapshotStore, CommandReceipts {
        @Volatile var json: String? = null
        private val ids = ConcurrentHashMap.newKeySet<String>()
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { json = source; ids += "${command.storeId}/${command.deviceId}/${command.commandId}"; return testReceipt(command) }
        override suspend fun committed(storeId: String, deviceId: String, commandId: String) = "$storeId/$deviceId/$commandId" in ids
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }

    private suspend fun ws(st: Store = Store()) = LocalWorkspace(st, scope).also { it.enterDemo(); it.selectStore("s-main") }
    private fun LocalWorkspace.s() = lanSnapshot().store!!
    private fun LocalWorkspace.inbox() = lanSnapshot().kdsInbox

    private class Screen(val ws: LocalWorkspace, val link: LanLink)

    private fun run(block: suspend (host: LocalWorkspace, a: Screen, b: Screen) -> Unit) = runBlocking {
        val hs = Store(); val h = ws(hs); h.updateLan { it.copy(role = LanRole.Host) }
        val server = LanHost(h, HostExecutor(h, h, h, hs, h, h), SoftwareDeviceKeys(), scope); server.start(0, "127.0.0.1")
        suspend fun screen(name: String): Screen {
            val k = ws(); val link = LanLink(k, SoftwareDeviceKeys(), scope, deviceName = name)
            assertTrue(link.pair("127.0.0.1:${server.status.value.port}", server.newPairingCode().code).isSuccess)
            k.setKdsStation("厨房")
            return Screen(k, link)
        }
        try { block(h, screen("1号屏"), screen("2号屏")) } finally { server.stop() }
    }

    private suspend fun LocalWorkspace.send() {
        val t = s().tables.first { it.status == TableStatus.Idle }.id
        openTable(t, 1); addDish(t, s().products.first { it.id == "5" }, null, 1); sendToKitchen(t, true)
    }

    @Test fun syncModeLetsEveryScreenAct() = run { h, a, b ->
        h.send(); a.link.kdsSync(); b.link.kdsSync()
        val t = a.ws.inbox().single()
        assertTrue(a.link.kdsAdvance(t.id, t.rev, KdsState.Accepted).ok)
        b.link.kdsSync()
        val t2 = b.ws.inbox().single()
        assertTrue(b.link.kdsAdvance(t2.id, t2.rev, KdsState.Cooking).ok) // 同步显示：另一块屏可以继续操作
        assertEquals("", h.s().kdsTickets.single().owner)
    }

    @Test fun firstScreenClaimsAndOthersAreRejected() = run { h, a, b ->
        assertTrue(h.saveDispatchModes(mapOf("厨房" to KdsDispatch.Claim)).isSuccess)
        assertTrue(h.saveDispatchModes(mapOf("不存在" to KdsDispatch.Claim)).isFailure)
        h.send(); a.link.kdsSync(); b.link.kdsSync()
        val t = a.ws.inbox().single()
        assertTrue(a.link.kdsAdvance(t.id, t.rev, KdsState.Accepted).ok)
        val ht = h.s().kdsTickets.single()
        assertEquals(a.ws.deviceId(), ht.owner); assertEquals("1号屏", ht.ownerName)
        // 2 号屏拿到最新版本后仍不能操作
        b.link.kdsSync()
        val bt = b.ws.inbox().single()
        assertEquals(a.ws.deviceId(), bt.owner)
        val r = b.link.kdsAdvance(bt.id, bt.rev, KdsState.Cooking)
        assertFalse(r.ok); assertTrue(r.message.contains("1号屏"))
        assertFalse(b.link.kdsRelease(bt.id, bt.rev).ok) // 非负责屏不能释放
        // 负责屏继续流转
        a.link.kdsSync(); val at = a.ws.inbox().single()
        assertTrue(a.link.kdsAdvance(at.id, at.rev, KdsState.Cooking).ok)
        // 释放：回到待接单，2 号屏可以接
        a.link.kdsSync(); val at2 = a.ws.inbox().single()
        assertTrue(a.link.kdsRelease(at2.id, at2.rev).ok)
        assertEquals(KdsState.Pending, h.s().kdsTickets.single().state); assertEquals("", h.s().kdsTickets.single().owner)
        b.link.kdsSync(); val bt2 = b.ws.inbox().single()
        assertTrue(b.link.kdsAdvance(bt2.id, bt2.rev, KdsState.Accepted).ok)
        assertEquals("2号屏", h.s().kdsTickets.single().ownerName)
    }

    @Test fun supervisorReclaimsAndAssigns() = run { h, a, b ->
        h.saveDispatchModes(mapOf("厨房" to KdsDispatch.Claim))
        h.send(); a.link.kdsSync()
        val t = a.ws.inbox().single()
        assertTrue(a.link.kdsAdvance(t.id, t.rev, KdsState.Cooking).ok)
        val id = t.id
        // 1 号屏离线：主管收回，卡回到待接单
        assertTrue(h.kdsReassign(id, null).isSuccess)
        assertEquals(KdsState.Pending, h.s().kdsTickets.single().state); assertEquals("", h.s().kdsTickets.single().owner)
        assertTrue(h.kdsReassign(id, null).isFailure) // 没有负责屏时不能再收回
        // 指派给 2 号屏：2 号屏可以直接操作，1 号屏被拒
        assertTrue(h.kdsReassign(id, b.ws.deviceId()).isSuccess)
        assertEquals("2号屏", h.s().kdsTickets.single().ownerName)
        assertTrue(h.kdsReassign(id, "不存在的设备").isFailure)
        a.link.kdsSync(); val at = a.ws.inbox().single()
        assertFalse(a.link.kdsAdvance(at.id, at.rev, KdsState.Accepted).ok)
        b.link.kdsSync(); val bt = b.ws.inbox().single()
        assertTrue(b.link.kdsAdvance(bt.id, bt.rev, KdsState.Accepted).ok)
        b.link.kdsSync(); val bt2 = b.ws.inbox().single()
        assertTrue(b.link.kdsAdvance(bt2.id, bt2.rev, KdsState.Done).ok)
        assertTrue(h.kdsReassign(id, null).isFailure) // 已完成不需要收回
    }
}
