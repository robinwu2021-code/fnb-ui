package ai.neargo.fnb.pos.core.lan

import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LanRole
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.RoutedConfig
import ai.neargo.fnb.pos.core.data.RoutedOrders
import ai.neargo.fnb.pos.core.data.RoutedFloor
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.identity.SoftwareDeviceKeys
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.TableStatus
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

/** N5：一主两副（真实本机套接字）——配对、并发仲裁、幂等、失联只读、撤销、身份校验。 */
class LanClusterTest {
    private class Store : SnapshotStore, CommandReceipts {
        @Volatile var json: String? = null
        val commands = java.util.Collections.synchronizedList(mutableListOf<JournalCommand>())
        private val ids = ConcurrentHashMap.newKeySet<String>()
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt {
            json = source; commands += command; ids += "${command.storeId}/${command.deviceId}/${command.commandId}"; return testReceipt(command)
        }
        override suspend fun committed(storeId: String, deviceId: String, commandId: String) = "$storeId/$deviceId/$commandId" in ids
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }

    private class Node(val ws: LocalWorkspace, val store: Store, val keys: SoftwareDeviceKeys)
    private suspend fun node(): Node {
        val st = Store(); val k = SoftwareDeviceKeys()
        val ws = LocalWorkspace(st, scope)
        ws.enterDemo(); ws.selectStore("s-main")
        return Node(ws, st, k)
    }

    private suspend fun cluster(block: suspend (host: Node, lanHost: LanHost, a: Pair<Node, LanLink>, b: Pair<Node, LanLink>) -> Unit) {
        val h = node()
        h.ws.updateLan { it.copy(role = LanRole.Host) }
        val exec = HostExecutor(h.ws, h.ws, h.ws, h.store)
        val server = LanHost(h.ws, exec, h.keys, scope)
        server.start(port = 0, bind = "127.0.0.1")
        val addr = "127.0.0.1:${server.status.value.port}"
        val sa = node(); val la = LanLink(sa.ws, sa.keys, scope, deviceName = "副机A")
        assertTrue(la.pair(addr, server.newPairingCode().code).isSuccess)
        val sb = node(); val lb = LanLink(sb.ws, sb.keys, scope, deviceName = "副机B")
        assertTrue(lb.pair(addr, server.newPairingCode().code).isSuccess)
        try { block(h, server, sa to la, sb to lb) } finally { server.stop() }
    }

    @Test fun pairingCodeIsOneTimeAndLimited() = runBlocking {
        val h = node(); h.ws.updateLan { it.copy(role = LanRole.Host) }
        val server = LanHost(h.ws, HostExecutor(h.ws, h.ws, h.ws, h.store), h.keys, scope)
        server.start(0, "127.0.0.1")
        val addr = "127.0.0.1:${server.status.value.port}"
        try {
            val code = server.newPairingCode().code
            val s = node(); val link = LanLink(s.ws, s.keys, scope)
            val wrong = if (code == "00000000") "11111111" else "00000000"
            repeat(3) { assertTrue(link.pair(addr, wrong).isFailure) }
            assertTrue(link.pair(addr, code).isFailure) // 已尝试 3 次，配对码作废
            val fresh = server.newPairingCode().code
            assertTrue(link.pair(addr, fresh).isSuccess)
            // 未发出与已发出未确认的区别：主机下线后，首次提交在连接阶段失败 → 只读，不留待确认
            assertTrue(node().let { LanLink(it.ws, it.keys, scope).pair(addr, fresh) }.isFailure) // 一次性
            assertEquals(1, h.ws.lanSnapshot().lan.paired.size)
            assertEquals(LanRole.Secondary, s.ws.lanSnapshot().lan.role)
        } finally { server.stop() }
    }

    @Test fun twoSecondariesConcurrentlyOrderThroughHost() = runBlocking {
        cluster { h, _, (a, la), (b, lb) ->
            val t = h.ws.lanSnapshot().store!!.tables.first { it.status == TableStatus.Idle }.id
            val fa = RoutedFloor(a.ws, la); val oa = RoutedOrders(a.ws, la); val ob = RoutedOrders(b.ws, lb)
            assertTrue(fa.openTable(t, 2).isSuccess)
            lb.pull()
            val pa = a.ws.lanSnapshot().store!!.products.first { it.id == "12" }
            val pb = b.ws.lanSnapshot().store!!.products.first { it.id == "9" }
            val jobs = (1..10).flatMap { listOf(async { oa.addDish(t, pa, null, 1) }, async { ob.addDish(t, pb, null, 1) }) }
            assertTrue(jobs.awaitAll().all { it is OrderResult.Ok })
            val hostOrder = h.ws.lanSnapshot().store!!.orders.getValue(t)
            assertEquals(20, hostOrder.lines.sumOf { it.qty })
            la.pull(); lb.pull()
            assertEquals(20, a.ws.lanSnapshot().store!!.orders.getValue(t).lines.sumOf { it.qty })
            assertEquals(hostOrder, b.ws.lanSnapshot().store!!.orders.getValue(t)) // 副本与主机一致
            // 命令日志：来源为副机设备，协调方为主机
            val fromA = h.store.commands.filter { it.deviceId == a.ws.deviceId() }
            assertEquals(11, fromA.size); assertTrue(fromA.all { it.coordinatorId == h.ws.deviceId() })
            assertEquals(10, h.store.commands.count { it.deviceId == b.ws.deviceId() })
            // 送厨走主机，打印任务在主机
            assertEquals(20, RoutedOrders(b.ws, lb).sendToKitchen(t, true).qty)
            assertTrue(h.ws.lanSnapshot().store!!.printJobs.any { it.title.startsWith(t) })
        }
    }

    @Test fun sameCommandIdExecutesOnce() = runBlocking {
        cluster { h, _, (a, _), _ ->
            val t = h.ws.lanSnapshot().store!!.tables.first { it.status == TableStatus.Idle }.id
            h.ws.openTable(t, 1)
            val exec = HostExecutor(h.ws, h.ws, h.ws, h.store)
            val intent = RemoteIntent.AddDish(t, "12", null, 1)
            val first = exec.execute("s-main", a.ws.deviceId(), "cmd-1", "boss", intent)
            val again = exec.execute("s-main", a.ws.deviceId(), "cmd-1", "boss", intent) // 回执丢失后重发
            assertTrue(first.ok && !first.duplicate); assertTrue(again.ok && again.duplicate)
            assertEquals(1, h.ws.lanSnapshot().store!!.orders.getValue(t).lines.sumOf { it.qty })
            assertFalse(exec.execute("s-coast", a.ws.deviceId(), "cmd-2", "boss", intent).ok) // 门店不一致
        }
    }

    @Test fun lostHostMakesSecondaryReadOnlyThenReconnects() = runBlocking {
        cluster { h, server, (a, la), _ ->
            val t = h.ws.lanSnapshot().store!!.tables.first { it.status == TableStatus.Idle }.id
            val port = server.status.value.port
            server.stop()
            runCatching { la.pull() } // 后台循环发现断线
            val r = RoutedFloor(a.ws, la).openTable(t, 1)
            assertTrue(r.exceptionOrNull()!!.message!!.contains("副 POS 只读"))
            assertTrue(a.ws.lanSnapshot().lan.pending.isEmpty()) // 失联不排队新写入
            assertEquals(TableStatus.Idle, h.ws.lanSnapshot().store!!.tables.first { it.id == t }.status)
            // 副机本机不能绕过主机直接写
            assertTrue(a.ws.openTable(t, 1).isFailure)
            assertTrue(RoutedConfig(a.ws, la).addCategory("夜宵", "").exceptionOrNull()!!.message!!.contains("仅主 POS"))
            // 主机恢复（同端口），副机按固定主机公钥重连
            server.start(port, "127.0.0.1")
            assertTrue(RoutedFloor(a.ws, la).openTable(t, 1).isSuccess)
            assertEquals(TableStatus.Dining, h.ws.lanSnapshot().store!!.tables.first { it.id == t }.status)
        }
    }

    @Test fun revokedImpostorAndFakeHostAreRejected() = runBlocking {
        cluster { h, server, (a, la), (b, lb) ->
            // 撤销 B：立即断开，之后无法握手
            h.ws.updateLan { l -> l.copy(paired = l.paired.map { if (it.deviceId == b.ws.deviceId()) it.copy(revokedAt = 1) else it }) }
            server.disconnect(b.ws.deviceId())
            val t = h.ws.lanSnapshot().store!!.tables.first { it.status == TableStatus.Idle }.id
            assertTrue(RoutedFloor(b.ws, lb).openTable(t, 1).isFailure)
            assertTrue(runCatching { lb.pull() }.isFailure)
            // A 的数据被复制到另一台设备（同设备ID、同主机记录，但密钥不同）
            val copy = Store(); copy.json = a.store.json
            val impWs = LocalWorkspace(copy, scope); impWs.restore()
            assertEquals(a.ws.deviceId(), impWs.deviceId())
            val impostor = LanLink(impWs, SoftwareDeviceKeys(), scope)
            assertTrue(runCatching { impostor.pull() }.exceptionOrNull()!!.message!!.contains("设备密钥与配对记录不符"))
            // 伪造主机：不同密钥监听另一端口，A 改指向它 → 拒绝
            val fake = node(); fake.ws.updateLan { it.copy(role = LanRole.Host) }
            val fakeServer = LanHost(fake.ws, HostExecutor(fake.ws, fake.ws, fake.ws, fake.store), fake.keys, scope)
            fakeServer.start(0, "127.0.0.1")
            try {
                a.ws.updateLan { it.copy(hostAddress = "127.0.0.1:${fakeServer.status.value.port}") }
                val other = LanLink(a.ws, a.keys, scope)
                assertTrue(runCatching { other.pull() }.exceptionOrNull()!!.message!!.contains("主机身份与配对记录不符"))
            } finally { fakeServer.stop() }
            assertNotNull(la)
        }
    }
}
