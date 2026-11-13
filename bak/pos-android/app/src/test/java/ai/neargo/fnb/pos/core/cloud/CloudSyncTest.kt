package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalEvent
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.model.Product
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * 云端同步适配（Biz 接口）：用本机业务真实产生的事件回放到内存版服务端（按 Biz 接口行为），
 * 验证顺序、幂等键、桌台 / 账单映射、送厨下单拆步、冲突重读、网络中断、业务拒绝只挡同一聚合、只在本机的事件直接处理。
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

    /** 内存版 Biz 服务端：同幂等键返回首次结果；版本不符 10409；可模拟断网 */
    class FakeServer(names: List<String> = listOf("H01", "H02", "H03", "H04", "T01", "T02"), val catalogItems: List<CloudCatalogItem> = emptyList()) : CloudApi {
        var down = false
        var conflictOnce = false
        val calls = mutableListOf<String>()
        val keys = mutableListOf<String>()
        private val byKey = mutableMapOf<String, Any>()
        val tables = names.mapIndexed { i, n -> "t-${i + 1}" to CloudTable("t-${i + 1}", n, version = 1) }.toMap().toMutableMap()
        val checks = mutableMapOf<String, CloudCheck>()
        val carts = mutableMapOf<String, Pair<Long, MutableMap<String, CloudLineInput>>>()
        val batches = mutableListOf<Pair<String, List<CloudLineInput>>>()
        private var seq = 0
        private fun net() { if (down) throw CloudException(CloudErrorKind.Network, 0, "网络不通") }
        @Suppress("UNCHECKED_CAST") private fun <T> once(key: String, f: () -> T): T { keys += key; return byKey.getOrPut(key) { f() as Any } as T }
        private fun tv(v: List<CloudTableVersion>) = v.forEach { if (tables.getValue(it.tableNo).version != it.version) throw CloudException.of(10409, "桌台版本已变化") }

        override suspend fun probe(storeNo: String) = run { net(); CloudProbe(storeNo, tables.size) }
        override suspend fun tableList(storeNo: String) = run { net(); calls += "tables"; tables.values.toList() }
        override suspend fun openCheck(storeNo: String, key: String, tableNos: List<String>, partySize: Int, tableVersions: List<CloudTableVersion>) = run { net(); once(key) {
            tv(tableVersions); val no = tableNos.single(); calls += "open:$no"
            val t = tables.getValue(no)
            if (t.turnoverState != "IDLE") throw CloudException(CloudErrorKind.Rejected, 10400, "桌台不空闲")
            val c = CloudCheck("C${++seq}", status = "OPEN", partySize = partySize, tableNos = listOf(no), version = 1); checks[c.checkNo] = c
            tables[no] = t.copy(turnoverState = "IN_USE", version = t.version + 1); c
        } }
        override suspend fun check(checkNo: String) = run { net(); checks.getValue(checkNo) }
        override suspend fun transferCheck(key: String, checkNo: String, version: Long, targetTableNos: List<String>, tableVersions: List<CloudTableVersion>) = run { net(); once(key) {
            val c = checks.getValue(checkNo)
            if (conflictOnce) { conflictOnce = false; checks[checkNo] = c.copy(version = c.version + 1); throw CloudException.of(10409, "版本已变化") }
            if (version != c.version) throw CloudException.of(10409, "版本已变化")
            tv(tableVersions); val to = targetTableNos.single(); calls += "transfer:$checkNo->$to"
            c.tableNos.forEach { n -> tables[n] = tables.getValue(n).let { it.copy(turnoverState = "IDLE", version = it.version + 1) } }
            tables[to] = tables.getValue(to).let { it.copy(turnoverState = "IN_USE", version = it.version + 1) }
            checks[checkNo] = c.copy(tableNos = listOf(to), version = c.version + 1); CloudOperation("OP$seq", "DONE")
        } }
        override suspend fun catalog(storeNo: String) = run { net(); calls += "catalog"; CloudCatalog(storeNo, listOf(CloudCatalogMenu("M1", "R7")), catalogItems) }
        override suspend fun createCart(storeNo: String, key: String, checkNo: String) = run { net(); once(key) {
            val no = "CT${++seq}"; carts[no] = 1L to mutableMapOf(); calls += "cart:$checkNo"; CloudCart(no, checkNo, "OPEN", 1)
        } }
        override suspend fun cart(cartNo: String) = run { net(); CloudCart(cartNo, version = carts.getValue(cartNo).first) }
        override suspend fun replaceCartLine(key: String, cartNo: String, sourceLineRef: String, line: CloudLineInput, version: Long) = run { net(); once(key) {
            val (v, lines) = carts.getValue(cartNo)
            if (v != version) throw CloudException.of(10409, "购物车版本已变化")
            lines[sourceLineRef] = line; carts[cartNo] = v + 1 to lines; CloudCartVersion(cartNo, v + 1)
        } }
        override suspend fun quote(storeNo: String, key: String, cartNo: String, version: Long) = run { net(); once(key) { CloudQuote("Q$cartNo", "D$version") } }
        override suspend fun confirmQuote(quoteNo: String, cartNo: String, version: Long, confirmationDigest: String) = run { net(); CloudQuote(quoteNo, confirmationDigest) }
        override suspend fun placeBatch(storeNo: String, key: String, cartNo: String, version: Long, quoteNo: String, confirmationDigest: String) = run { net(); once(key) {
            val lines = carts.getValue(cartNo).second.values.toList(); batches += cartNo to lines; calls += "batch:${lines.size}"
            CloudBatch("B${++seq}", status = "ACCEPTED", amountMinor = 1, version = 1)
        } }
        override suspend fun closePreview(key: String, businessDate: String, currency: String) = run { net(); calls += "closePreview:$businessDate:$currency"
            CloudClose("D1", "REVIEW", snapshotHash = "h1", version = 3) }
        override suspend fun closeSign(key: String, closeNo: String, snapshotHash: String) = run { net(); once(key) { calls += "closeSign:$closeNo@$snapshotHash"; CloudClose(closeNo, "SIGNED") } }
    }

    private suspend fun shop(scope: kotlinx.coroutines.CoroutineScope): Pair<LocalWorkspace, Disk> {
        val disk = Disk()
        val w = LocalWorkspace(disk, scope) { 1_800_000_000_000L }
        w.enterDemo(); w.selectStore("s-coast")
        disk.commands.clear()
        return w to disk
    }

    /** 服务端目录照本机菜品建（菜名、规格名一致） */
    private fun catalogOf(products: List<Product>) = products.map { p ->
        CloudCatalogItem("M1", "MI-${p.id}", "G-${p.id}", p.name,
            if (p.skus.isEmpty()) listOf(CloudCatalogSku("S-${p.id}", "", p.price)) else p.skus.map { CloudCatalogSku("S-${p.id}-${it.key}", it.label, it.price) })
    }

    @Test fun replaysOpenAndTransfer() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow()
        w.changeTable("H01", "H02").getOrThrow()
        val server = FakeServer(); val refs = MemoryCloudRefs(); val src = Source(disk)
        val sync = CloudSync(server, refs, src)
        val r = sync.run("s-coast", "NS002")
        assertEquals(2, r.uploaded); assertNull(r.stoppedBy)
        assertEquals(listOf("tables", "open:t-1", "transfer:C1->t-2"), server.calls)
        val orderId = w.current.value!!.orders.getValue("H02").orderId
        assertEquals("C1", refs.get("order", orderId)!!.remoteNo)
        assertEquals(2L, refs.get("order", orderId)!!.version)
        assertEquals("IN_USE", server.tables.getValue("t-2").turnoverState); assertEquals("IDLE", server.tables.getValue("t-1").turnoverState)
        assertEquals(0, sync.run("s-coast", "NS002").uploaded)
    }

    @Test fun kitchenSubmitPlacesBatchWithDerivedKeys() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val products = w.current.value!!.products
        w.openTable("H01", 2).getOrThrow()
        val plain = products.first { it.skus.isEmpty() && (it.stock ?: 1) > 0 }
        val spec = products.first { it.skus.isNotEmpty() }
        w.addDish("H01", plain, null, 2)
        w.addDish("H01", spec, spec.skus.first { it.stock > 0 }, 1)
        w.changeQty("H01", w.current.value!!.orders.getValue("H01").lines.first().uid, 1)   // 草稿改数量：只在本机
        w.sendToKitchen("H01", true)
        val server = FakeServer(catalogItems = catalogOf(products)); val refs = MemoryCloudRefs(); val src = Source(disk)
        val r = CloudSync(server, refs, src).run("s-coast", "NS002")
        assertEquals(2, r.uploaded)                     // 开台 + 送厨
        assertEquals(3, r.local)                        // 两次加菜 + 改数量
        assertEquals(0, r.waiting); assertEquals(0, r.issues)
        val (_, lines) = server.batches.single()
        assertEquals(setOf("G-${plain.id}" to 3, "G-${spec.id}" to 1), lines.map { it.goodsNo to it.qty }.toSet())
        assertTrue(lines.all { it.menuRevisionNo == "R7" && it.sourceLineRef.startsWith("L") })
        val ev = disk.commands.single { it.type == "KitchenSubmitted" }.commandId
        assertTrue(server.keys.containsAll(listOf("$ev:cart", "$ev:quote", "$ev:batch")))
        assertEquals(2, server.keys.count { it.startsWith("$ev:line:") })
        assertEquals(disk.commands.size, src.acked.size)
    }

    @Test fun kitchenSubmitInterruptedResumesWithSameKeys() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val products = w.current.value!!.products
        w.openTable("H01", 2).getOrThrow()
        w.addDish("H01", products.first { it.skus.isEmpty() && (it.stock ?: 1) > 0 }, null, 1)
        w.sendToKitchen("H01", true)
        val fake = FakeServer(catalogItems = catalogOf(products))
        val server = object : CloudApi by fake {
            var failQuote = true
            override suspend fun quote(storeNo: String, key: String, cartNo: String, version: Long): CloudQuote {
                if (failQuote) { failQuote = false; throw CloudException(CloudErrorKind.Network, 0, "超时") }
                return fake.quote(storeNo, key, cartNo, version)
            }
        }
        val refs = MemoryCloudRefs(); val src = Source(disk)
        val r1 = CloudSync(server, refs, src).run("s-coast", "NS002")
        assertEquals(CloudErrorKind.Network, r1.stoppedBy?.kind); assertEquals(1, r1.uploaded)   // 开台已传，送厨停在报价
        val r2 = CloudSync(server, refs, src).run("s-coast", "NS002")
        assertEquals(1, r2.uploaded); assertNull(r2.stoppedBy)
        assertEquals(1, fake.carts.size); assertEquals(1, fake.batches.size)   // 购物车、批次都只建一次
    }

    @Test fun unknownDishGoesToNeedsActionAndBlocksOnlyThatOrder() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val products = w.current.value!!.products
        w.openTable("H01", 2).getOrThrow()
        w.addDish("H01", products.first { it.skus.isEmpty() && (it.stock ?: 1) > 0 }, null, 1)
        w.sendToKitchen("H01", true)
        w.changeTable("H01", "H03").getOrThrow()
        w.openTable("T01", 4).getOrThrow()
        val server = FakeServer(catalogItems = emptyList()); val refs = MemoryCloudRefs()
        val r = CloudSync(server, refs, Source(disk)).run("s-coast", "NS002")
        assertEquals(2, r.uploaded)                     // H01 开台、T01 开台
        assertEquals("KitchenSubmitted", refs.issues().single().type)
        assertTrue(refs.issues().single().message.contains("服务端菜单里没有"))
        assertTrue(server.calls.none { it.startsWith("transfer") })   // 同单的换桌被挡住
    }

    @Test fun networkStopsRoundAndRetryUsesSameKey() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow()
        val server = FakeServer().apply { down = true }; val refs = MemoryCloudRefs(); val src = Source(disk)
        val r1 = CloudSync(server, refs, src).run("s-coast", "NS002")
        assertEquals(CloudErrorKind.Network, r1.stoppedBy?.kind); assertTrue(src.acked.isEmpty())
        server.down = false
        assertEquals(1, CloudSync(server, refs, src).run("s-coast", "NS002").uploaded)
        assertEquals(1, server.checks.size)
    }

    @Test fun conflictRereadsVersionOnce() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow(); w.changeTable("H01", "H02").getOrThrow()
        val server = FakeServer().apply { conflictOnce = true }
        val r = CloudSync(server, MemoryCloudRefs(), Source(disk)).run("s-coast", "NS002")
        assertEquals(2, r.uploaded); assertEquals(0, r.issues)
        assertEquals(listOf("t-2"), server.checks.getValue("C1").tableNos)
    }

    @Test fun rejectionBlocksOnlyThatAggregate() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow()
        w.openTable("T01", 4).getOrThrow()
        w.changeTable("H01", "H02").getOrThrow()
        val server = FakeServer().apply { tables["t-1"] = tables.getValue("t-1").copy(turnoverState = "IN_USE") }
        val refs = MemoryCloudRefs()
        val r = CloudSync(server, refs, Source(disk)).run("s-coast", "NS002")
        assertEquals(1, r.uploaded); assertEquals(1, r.issues)
        assertTrue(server.calls.none { it.startsWith("transfer") })
        assertEquals("TableOpened", refs.issues().single().type)
    }

    @Test fun localOnlyEventsDoNotBlockTheirOrder() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow()
        w.setOrderNote("H01", "靠窗")                    // 只在本机：不挡后面的换桌
        w.changeTable("H01", "H02").getOrThrow()
        w.finishCleaning("H01").getOrThrow()               // 换桌后原桌已是待清台；清台完成：只在本机
        val r = CloudSync(FakeServer(), MemoryCloudRefs(), Source(disk)).run("s-coast", "NS002")
        assertEquals(2, r.uploaded); assertEquals(2, r.local); assertEquals(0, r.waiting)
    }

    @Test fun dayCloseUsesPreviewThenSignWithSnapshotHash() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.closeDay("店长").getOrThrow()
        val server = FakeServer()
        CloudSync(server, MemoryCloudRefs(), Source(disk), currency = "CNY").run("s-coast", "NS002")
        assertTrue(server.calls.any { it.startsWith("closePreview:") && it.endsWith(":CNY") }); assertTrue(server.calls.contains("closeSign:D1@h1"))
    }

    @Test fun retailBlocksPerSaleAndConfigIsLocal() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk()
        val w = LocalWorkspace(disk, backgroundScope) { 1_800_000_000_000L }
        w.enterDemo(); w.selectStore(ai.neargo.fnb.pos.core.data.DemoCatalog.RETAIL_STORE_ID); disk.commands.clear()
        w.checkoutRetail(listOf(ai.neargo.fnb.pos.core.model.CartLine(1, "cola", 1)), false, "现金", 10_00, true).getOrThrow()
        w.checkoutRetail(listOf(ai.neargo.fnb.pos.core.model.CartLine(1, "water", 2)), false, "现金", 10_00, true).getOrThrow()
        w.setRetailPrice("cola", 380).getOrThrow()                                  // 改价：配置类
        w.holdCart(listOf(ai.neargo.fnb.pos.core.model.CartLine(1, "cola", 1)), null, "稍后").getOrThrow()   // 挂单：只在本机
        val src = Source(disk)
        val r = CloudSync(FakeServer(), MemoryCloudRefs(), src).run("s", "NS")
        assertEquals(0, r.uploaded); assertEquals(2, r.waiting)                  // 两张销售单各自等后端接口
        assertTrue(r.local >= 2)                                                  // 改价（及其价签）+ 挂单直接处理
        assertEquals(disk.commands.count { it.type == "RetailSaleCompleted" }, disk.commands.size - src.acked.size)
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
