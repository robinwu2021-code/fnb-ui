package ai.neargo.fnb.pos.core.lan

import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LanRole
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.identity.SoftwareDeviceKeys
import ai.neargo.fnb.pos.core.model.AdjustmentKind
import ai.neargo.fnb.pos.core.model.AdjustmentRequest
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

/** N7 厨房屏：持久接收与回执、去重、重启恢复、状态流转、退菜取消与迟到命令、命令幂等。 */
class KdsTest {
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

    private fun kdsRun(block: suspend (host: LocalWorkspace, kds: LocalWorkspace, kdsStore: Store, link: LanLink, exec: HostExecutor) -> Unit) = runBlocking {
        val hs = Store(); val h = ws(hs); h.updateLan { it.copy(role = LanRole.Host) }
        val exec = HostExecutor(h, h, h, hs, h, h)
        val server = LanHost(h, exec, SoftwareDeviceKeys(), scope); server.start(0, "127.0.0.1")
        val ks = Store(); val k = ws(ks); val keys = SoftwareDeviceKeys()
        val link = LanLink(k, keys, scope, deviceName = "厨房屏")
        assertTrue(link.pair("127.0.0.1:${server.status.value.port}", server.newPairingCode().code).isSuccess)
        k.setKdsStation("厨房")
        try { block(h, k, ks, link, exec) } finally { server.stop() }
    }
    private fun LocalWorkspace.s() = lanSnapshot().store!!
    private suspend fun LocalWorkspace.send(qty: Int = 2): String {
        val t = s().tables.first { it.status == TableStatus.Idle }.id
        openTable(t, 1); addDish(t, s().products.first { it.id == "12" }, null, qty); sendToKitchen(t, true)
        return t
    }
    private fun LocalWorkspace.inbox() = lanSnapshot().kdsInbox

    @Test fun deliveryIsPersistedAckedAndDeduplicated() = kdsRun { h, k, ks, link, _ ->
        h.send()
        assertEquals(1, link.kdsSync())
        val t = k.inbox().single()
        assertEquals(KdsState.Pending, t.state)
        val ht = h.s().kdsTickets.single()
        assertEquals(1, ht.deliveredRev); assertTrue(ht.deliveredAt > 0)
        assertEquals(KdsState.Pending, ht.state) // 送达≠接单
        assertEquals(0, link.kdsSync()); assertEquals(1, k.inbox().size)
        // 游标回退（重投）：去重，不新增卡片
        k.kdsReceive(h.kdsPull("s-main", 0, "厨房").first, 0)
        assertEquals(1, k.inbox().size)
        // 重启厨房屏：收件箱与游标仍在
        val restarted = LocalWorkspace(ks, scope); restarted.restore()
        assertEquals(t.id, restarted.inbox().single().id); assertTrue(restarted.lanSnapshot().kdsCursor > 0)
    }

    @Test fun chefFlowAndRecall() = kdsRun { h, k, _, link, _ ->
        h.send(); link.kdsSync()
        var t = k.inbox().single()
        assertTrue(link.kdsAdvance(t.id, t.rev, KdsState.Accepted).ok)
        t = k.inbox().single(); assertEquals(KdsState.Accepted, t.state)
        assertTrue(link.kdsAdvance(t.id, t.rev, KdsState.Cooking).ok)
        t = k.inbox().single()
        assertTrue(link.kdsAdvance(t.id, t.rev, KdsState.Done).ok)
        assertEquals(KdsState.Done, h.s().kdsTickets.single().state)
        t = k.inbox().single()
        assertTrue(link.kdsAdvance(t.id, t.rev, KdsState.Cooking).ok) // 撤回完成
        assertEquals(KdsState.Cooking, h.s().kdsTickets.single().state)
        assertFalse(link.kdsAdvance(t.id, t.rev, KdsState.Done).ok) // 旧版本被拒
        assertEquals("厨房屏", h.s().kdsTickets.single().by)
    }

    @Test fun voidCancelsAndLateCommandIsRejected() = kdsRun { h, k, _, link, _ ->
        val table = h.send(qty = 2); link.kdsSync()
        val seen = k.inbox().single()
        val uid = h.s().orders.getValue(table).lines.single().uid
        // 部分退菜：任务卡同步
        assertTrue(h.authorizeAdjustment(table, AdjustmentRequest(AdjustmentKind.Void, uid, 1, reason = "客人不要了"), "manager@demo.local", "demo12345").isSuccess)
        assertEquals(1, h.s().kdsTickets.single().lines.single().voidQty)
        assertFalse(link.kdsAdvance(seen.id, seen.rev, KdsState.Accepted).ok) // 看到的是旧版本
        link.kdsSync()
        assertEquals(1, k.inbox().single().lines.single().active)
        // 全部退菜：取消；迟到的完成命令不能恢复
        val cur = k.inbox().single()
        assertTrue(link.kdsAdvance(cur.id, cur.rev, KdsState.Cooking).ok)
        val cooking = k.inbox().single()
        assertTrue(h.authorizeAdjustment(table, AdjustmentRequest(AdjustmentKind.Void, uid, 1, reason = "全部取消"), "manager@demo.local", "demo12345").isSuccess)
        assertEquals(KdsState.Cancelled, h.s().kdsTickets.single().state)
        val late = link.kdsAdvance(cooking.id, cooking.rev, KdsState.Done)
        assertFalse(late.ok); assertTrue(late.message.contains("已取消"))
        assertEquals(KdsState.Cancelled, h.s().kdsTickets.single().state)
        assertEquals(KdsState.Cancelled, k.inbox().single().state)
    }

    @Test fun sameCommandIdAppliesOnce() = kdsRun { h, k, _, link, exec ->
        h.send(); link.kdsSync()
        val t = k.inbox().single()
        val i = RemoteIntent.KdsAdvance(t.id, t.rev, KdsState.Accepted)
        assertTrue(exec.execute("s-main", k.deviceId(), "kds-1", "kds", i).ok)
        val again = exec.execute("s-main", k.deviceId(), "kds-1", "kds", i)
        assertTrue(again.ok && again.duplicate)
        assertEquals(t.rev + 1, h.s().kdsTickets.single().rev)
    }
}
