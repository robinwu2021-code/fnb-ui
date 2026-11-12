package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalEvent
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * 云端同步适配：用本机业务真实产生的事件（开台、换桌、日结）回放到内存版服务端（按 POS 合同草案的行为），
 * 验证顺序、幂等键、映射、冲突重读、网络中断、业务拒绝只挡同一聚合、未对接类型不阻塞其他聚合。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncTest {
    /** 记下本机命令，当作命令日志里的待上传事件 */
    private class Disk : SnapshotStore {
        var data: String? = null
        val commands = mutableListOf<JournalCommand>()
        override suspend fun load() = data
        override suspend fun save(json: String) { data = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { data = source; commands += command; return testReceipt(command) }
    }
    private class Source(private val disk: Disk) : EventSource {
        val acked = mutableSetOf<String>()
        override suspend fun pending(storeId: String, limit: Int) = disk.commands.filter { it.commandId !in acked }.mapIndexed { i, c ->
            JournalEvent(i.toLong(), c.commandId, c.aggregates.first().id, 1, c.type, c.payload, envelope = "{}")
        }
        override suspend fun ack(storeId: String, eventId: String) { acked += eventId }
    }

    /** 内存版服务端：同幂等键返回首次结果；版本不符 10409；可模拟断网 */
    private class FakeServer : CloudApi {
        var down = false
        var conflictOnce = false
        val calls = mutableListOf<String>()
        private val byKey = mutableMapOf<String, Any>()
        val tables = mutableMapOf("t-1" to CloudTable("t-1", "H01", status = "IDLE", version = 1), "t-2" to CloudTable("t-2", "H02", status = "IDLE", version = 1),
            "t-3" to CloudTable("t-3", "T01", status = "IDLE", version = 1))
        val checks = mutableMapOf<String, CloudCheck>()
        private var seq = 0
        private fun net() { if (down) throw CloudException(CloudErrorKind.Network, 0, "网络不通") }
        @Suppress("UNCHECKED_CAST") private fun <T> once(key: String, f: () -> T): T = byKey.getOrPut(key) { f() as Any } as T

        override suspend fun storeInfo(storeNo: String) = run { net(); CloudStoreInfo(CloudStore(storeNo, "南山小馆"), "2026-11-12") }
        override suspend fun tableList(storeNo: String) = run { net(); calls += "list"; tables.values.toList() }
        override suspend fun openTable(storeNo: String, key: String, tableNo: String, guestCount: Int) = run { net(); once(key) {
            calls += "open:$tableNo"
            val t = tables.getValue(tableNo)
            if (t.status != "IDLE") throw CloudException(CloudErrorKind.Rejected, 10400, "桌台不空闲")
            val c = CloudCheck("C${++seq}", "OPEN", listOf(tableNo), guestCount, version = 1); checks[c.checkNo] = c
            tables[tableNo] = t.copy(status = "IN_USE", checkNo = c.checkNo, version = t.version + 1)
            CloudOpen(c, tables.getValue(tableNo))
        } }
        override suspend fun checkDetail(storeNo: String, checkNo: String) = run { net(); CloudCheckDetail(checks.getValue(checkNo), emptyList(), CloudAmounts("CNY", 0, 0, 0, 0, 0, 0)) }
        override suspend fun transferTable(storeNo: String, key: String, checkNo: String, toTableNo: String, version: Long) = run { net(); once(key) {
            val c = checks.getValue(checkNo)
            if (conflictOnce) { conflictOnce = false; checks[checkNo] = c.copy(version = c.version + 1); throw CloudException.of(10409, "版本已变化") }
            if (version != c.version) throw CloudException.of(10409, "版本已变化")
            calls += "transfer:$checkNo->$toTableNo"
            c.copy(tableNos = listOf(toTableNo), version = c.version + 1).also { checks[checkNo] = it }
        } }
        override suspend fun menuList(storeNo: String) = run { net(); emptyList<CloudMenu>() }
        override suspend fun closePreview(storeNo: String, key: String, businessDate: String) = run { net(); calls += "closePreview:$businessDate"
            CloudClosePreview("D1", "PREVIEW", CloudAmounts("CNY", 0, 0, 0, 0, 0, 0), version = 3) }
        override suspend fun closeSign(storeNo: String, key: String, closeNo: String, version: Long) = run { net(); once(key) { calls += "closeSign:$closeNo@$version"; CloudClose(closeNo, "SIGNED") } }
    }

    private suspend fun shop(scope: kotlinx.coroutines.CoroutineScope): Pair<LocalWorkspace, Disk> {
        val disk = Disk()
        val w = LocalWorkspace(disk, scope) { 1_800_000_000_000L }
        w.enterDemo(); w.selectStore("s-coast")
        disk.commands.clear()
        return w to disk
    }

    @Test fun replaysOpenTransferAndDayClose() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow()
        w.changeTable("H01", "H02").getOrThrow()
        val server = FakeServer(); val refs = MemoryCloudRefs(); val src = Source(disk)
        val sync = CloudSync(server, refs, src)
        val r = sync.run("s-coast", "NS002")
        assertEquals(2, r.uploaded); assertNull(r.stoppedBy)
        assertEquals(listOf("list", "open:t-1", "transfer:C1->t-2"), server.calls)
        val orderId = w.current.value!!.orders.getValue("H02").orderId
        assertEquals("C1", refs.get("order", orderId)!!.remoteNo)
        assertEquals(2L, refs.get("order", orderId)!!.version)
        // 重放：没有待上传，不再调用
        assertEquals(0, sync.run("s-coast", "NS002").uploaded)
    }

    @Test fun networkStopsRoundAndRetryUsesSameKey() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow()
        val server = FakeServer().apply { down = true }; val refs = MemoryCloudRefs(); val src = Source(disk)
        val r1 = CloudSync(server, refs, src).run("s-coast", "NS002")
        assertEquals(CloudErrorKind.Network, r1.stoppedBy?.kind); assertTrue(src.acked.isEmpty())
        server.down = false
        assertEquals(1, CloudSync(server, refs, src).run("s-coast", "NS002").uploaded)
        assertEquals(1, server.checks.size)   // 只开了一次台
    }

    @Test fun conflictRereadsVersionOnce() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow(); w.changeTable("H01", "H02").getOrThrow()
        val server = FakeServer().apply { conflictOnce = true }; val refs = MemoryCloudRefs()
        val r = CloudSync(server, refs, Source(disk)).run("s-coast", "NS002")
        assertEquals(2, r.uploaded); assertEquals(0, r.issues)
        assertEquals(listOf("C1"), server.checks.values.filter { it.tableNos == listOf("t-2") }.map { it.checkNo })
    }

    @Test fun rejectionBlocksOnlyThatAggregate() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow()
        w.openTable("T01", 4).getOrThrow()
        w.changeTable("H01", "H02").getOrThrow()
        val server = FakeServer().apply { tables["t-1"] = tables.getValue("t-1").copy(status = "IN_USE") }   // 服务端 A01 已被占
        val refs = MemoryCloudRefs()
        val r = CloudSync(server, refs, Source(disk)).run("s-coast", "NS002")
        assertEquals(1, r.uploaded)                       // B01 开台照常上传
        assertEquals(1, r.issues)                         // A01 开台进需处理
        assertTrue(server.calls.none { it.startsWith("transfer") })   // A01 的换桌被挡住，不乱序上传
        assertEquals("TableOpened", refs.issues().single().type)
    }

    @Test fun unsupportedTypesWaitWithoutBlockingOthers() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow()
        val p = w.current.value!!.products.first { !it.hasSpec && (it.stock ?: 1) > 0 }
        w.addDish("H01", p, null, 1)                       // 点菜：服务端下单接口还没对接
        w.openTable("T01", 2).getOrThrow()
        val r = CloudSync(FakeServer(), MemoryCloudRefs(), Source(disk)).run("s-coast", "NS002")
        assertEquals(2, r.uploaded); assertTrue(r.waiting >= 1); assertEquals(0, r.issues)
    }

    @Test fun dayCloseUsesPreviewThenSign() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.closeDay("店长").getOrThrow()
        val server = FakeServer()
        CloudSync(server, MemoryCloudRefs(), Source(disk)).run("s-coast", "NS002")
        assertTrue(server.calls.any { it.startsWith("closePreview:") }); assertTrue(server.calls.contains("closeSign:D1@3"))
    }

    @Test fun healthDebounce() {
        var h = CloudHealth()
        h = h.success(); assertFalse(h.online); h = h.success(); assertTrue(h.online)
        h = h.failure("x"); assertTrue(h.online); h = h.failure("x"); assertFalse(h.online)
        assertEquals(10_000, h.nextProbeMs()); assertEquals(60_000, h.failure("x").failure("x").failure("x").nextProbeMs())
        assertFalse(CloudHealth(online = true).forceOffline("断网").online)
    }

    @Test fun errorCodeClassification() {
        assertEquals(CloudErrorKind.Conflict, CloudException.of(10409, "").kind)
        assertEquals(CloudErrorKind.Unauthorized, CloudException.of(10401, "").kind)
        assertEquals(CloudErrorKind.Rejected, CloudException.of(10400, "x").kind)
        assertEquals(CloudErrorKind.Network, CloudException.of(50001, "").kind)
        assertEquals(CloudErrorKind.Network, CloudException.of(10500, "").kind)
    }
}
