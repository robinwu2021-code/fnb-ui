package ai.neargo.fnb.pos.core.lan

import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LanRole
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.RoutedFloor
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.identity.SoftwareDeviceKeys
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

/** 副机开台 / 换桌：副机自己的选桌跟到目标桌；主机收银员正在看的桌不被换掉。 */
class LanTableSelectionTest {
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

    @Test fun secondaryOpenFollowsLocallyHostKeepsItsTable() = runBlocking {
        val hs = Store(); val hk = SoftwareDeviceKeys()
        val host = LocalWorkspace(hs, scope).also { it.enterDemo(); it.selectStore("s-coast") }
        host.updateLan { it.copy(role = LanRole.Host) }
        val server = LanHost(host, HostExecutor(host, host, host, hs, session = host), hk, scope)
        server.start(0, "127.0.0.1")
        try {
            val sec = LocalWorkspace(Store(), scope).also { it.enterDemo(); it.selectStore("s-coast") }
            val link = LanLink(sec, SoftwareDeviceKeys(), scope, deviceName = "副机")
            assertTrue(link.pair("127.0.0.1:${server.status.value.port}", server.newPairingCode().code).isSuccess)
            val floor = RoutedFloor(sec, link)
            host.selectTable("H01")
            sec.selectTable("H01")
            // 副机开 H03：副机选到 H03，主机仍在 H01
            floor.openTable("H03", 2).getOrThrow()
            assertEquals(TableStatus.Dining, host.lanSnapshot().store!!.tables.first { it.id == "H03" }.status)
            assertEquals("H01", host.lanSnapshot().store!!.currentTableId)
            assertEquals("H03", sec.lanSnapshot().store!!.currentTableId)
            // 副机把 H03 换到 H04：副机跟到 H04，主机不动
            floor.changeTable("H03", "H04").getOrThrow()
            assertEquals("H04", sec.lanSnapshot().store!!.currentTableId)
            assertEquals("H01", host.lanSnapshot().store!!.currentTableId)
            // 主机正看着被换走的桌：跟到新桌
            host.selectTable("H04")
            floor.changeTable("H04", "H02").getOrThrow()
            assertEquals("H02", host.lanSnapshot().store!!.currentTableId)
        } finally { server.stop() }
    }
}
