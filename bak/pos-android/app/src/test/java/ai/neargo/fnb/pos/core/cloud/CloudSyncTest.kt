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
        var buyers = mutableListOf<String?>()
        override suspend fun placeBatch(storeNo: String, key: String, cartNo: String, version: Long, quoteNo: String, confirmationDigest: String, buyerNo: String?) = run { net(); once(key) {
            buyers += buyerNo
            val lines = carts.getValue(cartNo).second.values.toList(); batches += cartNo to lines; calls += "batch:${lines.size}"
            CloudBatch("B${++seq}", status = "ACCEPTED", amountMinor = 1, version = 1)
        } }
        override suspend fun openBusinessDay(key: String, businessDate: String) { net(); calls += "openDay:$businessDate" }
        override suspend fun closePreview(key: String, businessDate: String, currency: String) = run { net(); calls += "closePreview:$businessDate:$currency"
            CloudClose("D1", "REVIEW", snapshotHash = "h1", version = 3) }
        override suspend fun closeSign(key: String, closeNo: String, snapshotHash: String) = run { net(); once(key) { calls += "closeSign:$closeNo@$snapshotHash"; CloudClose(closeNo, "SIGNED") } }

        /** 结算单应收：默认等于测试设定值 */
        var due: Long = 0
        var changedOnce = false
        val settlements = mutableMapOf<String, CloudSettlement>()
        val records = mutableMapOf<String, CloudOfflineRecordBody>()
        override suspend fun createCounterSettlement(key: String, checkNo: String) = run { net(); once(key) {
            if (settlements.values.any { it.checkNo == checkNo && it.status == "FROZEN" }) throw CloudException.of(10409, "已有未结束的结算单")
            calls += "settle:$checkNo"; CloudSettlement("CS${++seq}", checkNo, "FROZEN", "CNY", due).also { settlements[it.settlementNo] = it }
        } }
        override suspend fun recordOffline(key: String, body: CloudOfflineRecordBody) = run { net(); once(key) {
            calls += "record:${body.method}:${body.amountMinor}:${body.bizNo}"; val no = "OR${++seq}"; records[no] = body; CloudOfflineRecord(no, body.amountMinor, body.method, body.bizNo)
        } }
        override suspend fun finalizeCounterSettlement(settlementNo: String, offlineRecordNos: List<String>): CloudSettlement { net()
            if (changedOnce) { changedOnce = false; throw CloudException.of(20027, "账单已变") }
            val st = settlements.getValue(settlementNo); val total = offlineRecordNos.sumOf { records.getValue(it).amountMinor }
            if (total < st.dueMinor) throw CloudException.of(20024, "收款还差")
            calls += "finalize:$settlementNo"
            return st.copy(status = "SETTLED", receivedMinor = total, changeMinor = total - st.dueMinor, receiptNo = "RC1", checkStatus = "CLOSED", tableReleased = !holdTable).also { settlements[settlementNo] = it }
        }
        override suspend fun counterSettlement(settlementNo: String) = run { net(); settlements.getValue(settlementNo).let { if (it.status == "FROZEN") it.copy(dueMinor = due) else it } }
        // —— 进销存 ——
        var invItems: Map<String, String> = emptyMap()   // 条码 → 物料
        var failPostOnce = false
        val inbounds = mutableMapOf<String, CloudInboundBody>(); val outbounds = mutableMapOf<String, CloudOutboundBody>()
        val counts = mutableMapOf<String, MutableMap<String, Int>>(); val posted = mutableListOf<String>(); val adjusts = mutableListOf<Pair<String, Int>>()
        override suspend fun inventoryItemByBarcode(code: String) = run { net(); invItems[code]?.let { CloudInvItem(it) } }
        override suspend fun createInbound(body: CloudInboundBody) = run { net(); "IN${++seq}".also { inbounds[it] = body } }
        override suspend fun postInbound(no: String) { net(); if (failPostOnce) { failPostOnce = false; throw CloudException(CloudErrorKind.Network, 0, "超时") }; if (no !in posted) posted += no }
        override suspend fun createOutbound(body: CloudOutboundBody) = run { net(); "OUT${++seq}".also { outbounds[it] = body } }
        override suspend fun postOutbound(no: String) { net(); if (no !in posted) posted += no }
        override suspend fun openCount(itemIds: List<String>) = run { net(); "CNT${++seq}".also { counts[it] = mutableMapOf() } }
        override suspend fun fillCount(no: String, lines: List<CloudCountLine>) { net(); lines.forEach { counts.getValue(no)[it.itemId] = it.countedQty } }
        override suspend fun postCount(no: String) { net(); if (no !in posted) posted += no }
        override suspend fun adjustInventory(itemId: String, countedQty: Int, reasonCode: String) { net(); adjusts += itemId to countedQty }
        // —— 结账后放台（R4） ——
        var holdTable = false                      // finalize 返回 tableReleased=false
        var releaseFailOnce = false
        val taskSteps = mutableListOf<String>(); val released = mutableListOf<String>()
        val taskState = mutableMapOf<String, Pair<String, Long>>()    // taskNo → 状态、版本
        override suspend fun stations(storeNo: String) = run { net(); listOf(CloudStation("ST1", "热厨")) }
        override suspend fun checkBatchNos(storeNo: String, checkNo: String) = run { net(); batches.map { it.first }.map { "B$it" } }
        override suspend fun kitchenTasks(storeNo: String, stationNo: String) = run { net(); batches.indices.map { i ->
            val no = "TK$i"; val (st, v) = taskState.getOrPut(no) { "QUEUED" to 0L }; CloudTask(no, "B${batches[i].first}", st, v, 1.0) }.filter { taskState.getValue(it.taskNo).first != "SERVED" } }
        override suspend fun advanceTask(key: String, taskNo: String, action: String, version: Long, qty: Double) { net()
            val (st, v) = taskState.getValue(taskNo)
            if (v != version) throw CloudException.of(10409, "任务版本已变化")
            val next = mapOf("start" to ("QUEUED" to "MAKING"), "ready" to ("MAKING" to "READY"), "serve" to ("READY" to "SERVED")).getValue(action)
            if (st != next.first) throw CloudException.of(10409, "状态不对"); taskState[taskNo] = next.second to v + 1; taskSteps += "$taskNo:$action"
        }
        override suspend fun releaseService(key: String, checkNo: String, version: Long, reason: String) { net()
            if (releaseFailOnce) { releaseFailOnce = false; throw CloudException.of(10409, "有菜没上齐") }
            if (taskState.values.any { it.first != "SERVED" }) throw CloudException.of(10409, "有菜没上齐")
            released += checkNo
        }
        override suspend fun cancelCounterSettlement(settlementNo: String) = run { net(); calls += "cancel:$settlementNo"; settlements.getValue(settlementNo).copy(status = "CANCELLED").also { settlements[settlementNo] = it } }
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
        val sync = CloudSync(server, refs, src, buyerNo = "U1")
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
        val r = CloudSync(server, refs, src, buyerNo = "U1").run("s-coast", "NS002")
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
        val r1 = CloudSync(server, refs, src, buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(CloudErrorKind.Network, r1.stoppedBy?.kind); assertEquals(1, r1.uploaded)   // 开台已传，送厨停在报价
        val r2 = CloudSync(server, refs, src, buyerNo = "U1").run("s-coast", "NS002")
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
        val r = CloudSync(server, refs, Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(2, r.uploaded)                     // H01 开台、T01 开台
        assertEquals("KitchenSubmitted", refs.issues().single().type)
        assertTrue(refs.issues().single().message.contains("服务端菜单里没有"))
        assertTrue(server.calls.none { it.startsWith("transfer") })   // 同单的换桌被挡住
    }

    @Test fun networkStopsRoundAndRetryUsesSameKey() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow()
        val server = FakeServer().apply { down = true }; val refs = MemoryCloudRefs(); val src = Source(disk)
        val r1 = CloudSync(server, refs, src, buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(CloudErrorKind.Network, r1.stoppedBy?.kind); assertTrue(src.acked.isEmpty())
        server.down = false
        assertEquals(1, CloudSync(server, refs, src, buyerNo = "U1").run("s-coast", "NS002").uploaded)
        assertEquals(1, server.checks.size)
    }

    @Test fun conflictRereadsVersionOnce() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.openTable("H01", 2).getOrThrow(); w.changeTable("H01", "H02").getOrThrow()
        val server = FakeServer().apply { conflictOnce = true }
        val r = CloudSync(server, MemoryCloudRefs(), Source(disk), buyerNo = "U1").run("s-coast", "NS002")
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
        val r = CloudSync(server, refs, Source(disk), buyerNo = "U1").run("s-coast", "NS002")
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
        val r = CloudSync(FakeServer(), MemoryCloudRefs(), Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(2, r.uploaded); assertEquals(2, r.local); assertEquals(0, r.waiting)
    }

    @Test fun dayCloseOpensDayAndPreviewsThenWaitsForReview() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        w.closeDay("店长").getOrThrow()
        val server = FakeServer(); val refs = MemoryCloudRefs(); val src = Source(disk)
        val r = CloudSync(server, refs, src, currency = "CNY", buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(1, r.uploaded); assertEquals(0, r.issues); assertEquals(disk.commands.size, src.acked.size)   // 日结事件已完成，不留需处理
        val review = refs.all("dayclose").single(); assertTrue(review.remoteNo.startsWith("D1|h1"))                 // 记下待复核的 closeNo 与快照哈希
        assertTrue(server.calls.any { it.startsWith("closePreview:") && it.endsWith(":CNY") })
        val order = server.calls.filter { it.startsWith("openDay:") || it.startsWith("closePreview:") || it.startsWith("closeSign:") }.map { it.substringBefore(':') }
        assertEquals(listOf("openDay", "closePreview"), order)                    // 终端只开营业日 + 预览；签核要另一个账号（二人复核）
        assertTrue(server.calls.none { it.startsWith("closeSign:") })
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
        val r = CloudSync(FakeServer(), MemoryCloudRefs(), src, buyerNo = "U1").run("s", "NS")
        assertEquals(0, r.uploaded); assertEquals(2, r.waiting)                  // 两张销售单各自等后端接口
        assertTrue(r.local >= 2)                                                  // 改价（及其价签）+ 挂单直接处理
        assertEquals(disk.commands.count { it.type == "RetailSaleCompleted" }, disk.commands.size - src.acked.size)
    }

    /** 开台、点一道菜、送厨后结账；返回本机应收 */
    private suspend fun paidTable(w: LocalWorkspace, method: String, received: Long? = null): Long {
        w.openTable("H01", 2).getOrThrow()
        w.addDish("H01", w.current.value!!.products.first { it.skus.isEmpty() && (it.stock ?: 1) > 0 }, null, 1)
        w.sendToKitchen("H01", true)
        val due = ai.neargo.fnb.pos.core.model.OrderRules.totals(w.current.value!!.orders.getValue("H01")).due
        w.settle("H01", due, method, true, received ?: due).getOrThrow()
        return due
    }

    @Test fun cashCheckoutCreatesSettlementRecordsFaceAmountAndFinalizes() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val products = w.current.value!!.products
        val due = paidTable(w, "现金", 100_00)
        val server = FakeServer(catalogItems = catalogOf(products)).apply { this.due = due }
        val refs = MemoryCloudRefs()
        val r = CloudSync(server, refs, Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(0, r.issues); assertNull(r.stoppedBy)
        assertTrue(server.calls.contains("record:CASH:10000:CS4") || server.calls.any { it.startsWith("record:CASH:10000:CS") })   // 现金按实收面额记
        assertTrue(server.calls.any { it.startsWith("finalize:") })
        val ev = disk.commands.single { it.type == "PaymentRecorded" }.commandId
        assertTrue(server.keys.containsAll(listOf("$ev:settle", "$ev:pay")))
        assertEquals("RC1", refs.get("settled", ev)!!.remoteNo)
    }

    @Test fun cardAmountMismatchGoesToNeedsActionWithoutRecordingMoney() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val products = w.current.value!!.products
        val due = paidTable(w, "银行卡")
        val server = FakeServer(catalogItems = catalogOf(products)).apply { this.due = due - 300 }   // 服务端少 3 元（服务费没绑）
        val refs = MemoryCloudRefs()
        CloudSync(server, refs, Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        assertEquals("PaymentRecorded", refs.issues().single().type)
        assertTrue(refs.issues().single().message.contains("刷卡多收"))
        assertTrue(server.calls.none { it.startsWith("record:") })                     // 先比金额，不先记钱
        // 后台核对后重试：复用同一张结算单，不重建
        server.due = due
        refs.put(CloudRef("attempt", refs.issues().single().eventId, "1")); refs.resolve(refs.issues().single().eventId)
        CloudSync(server, refs, Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(1, server.settlements.size); assertTrue(server.calls.any { it.startsWith("finalize:") })
    }

    @Test fun qrPaymentWaitsWithoutNeedsAction() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        paidTable(w, "扫码")
        val refs = MemoryCloudRefs()
        val r = CloudSync(FakeServer(catalogItems = catalogOf(w.current.value!!.products)), refs, Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(0, r.issues); assertTrue(r.waiting >= 1)
    }

    @Test fun checkChangedCancelsSettlementSoRetryRebuilds() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val due = paidTable(w, "现金")
        val server = FakeServer(catalogItems = catalogOf(w.current.value!!.products)).apply { this.due = due; changedOnce = true }
        val refs = MemoryCloudRefs()
        CloudSync(server, refs, Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(20027, refs.issues().single().code)
        assertTrue(server.calls.any { it.startsWith("cancel:") })
        val ev = refs.issues().single().eventId
        refs.put(CloudRef("attempt", ev, "1")); refs.resolve(ev)
        CloudSync(server, refs, Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(2, server.settlements.size)                                       // 撤销后重建了新结算单
        assertTrue(server.calls.any { it.startsWith("finalize:") })
    }

    private suspend fun retailShop(scope: kotlinx.coroutines.CoroutineScope): Pair<LocalWorkspace, Disk> {
        val disk = Disk()
        val w = LocalWorkspace(disk, scope) { 1_800_000_000_000L }
        w.enterDemo(); w.selectStore(ai.neargo.fnb.pos.core.data.DemoCatalog.RETAIL_STORE_ID); disk.commands.clear()
        return w to disk
    }
    /** 服务端物料：演示零售门店全部商品按第一个条码建账 */
    private fun itemsOf(w: LocalWorkspace) = w.current.value!!.products.mapNotNull { p -> p.retail?.barcodes?.firstOrNull()?.let { it to "ITM-${p.id}" } }.toMap()

    private fun line(pid: String, qty: Long, reason: ai.neargo.fnb.pos.core.model.LossReason? = null) = ai.neargo.fnb.pos.core.model.StockDocLine(pid, null, qty, reason)

    @Test fun receivePostedCreatesDraftThenPostsAndDraftStepsStayLocal() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = retailShop(backgroundScope)
        val no = w.newStockDoc(ai.neargo.fnb.pos.core.model.StockDocKind.Receive, supplier = "供应商A").getOrThrow()
        w.setStockDocLines(no, listOf(line("cola", 24), line("water", 12))).getOrThrow()
        w.advanceStockDoc(no).getOrThrow()
        val server = FakeServer().apply { invItems = itemsOf(w); failPostOnce = true }
        val refs = MemoryCloudRefs(); val src = Source(disk)
        val r1 = CloudSync(server, refs, src, inventory = true, buyerNo = "U1").run("s", "NS")
        assertEquals(CloudErrorKind.Network, r1.stoppedBy?.kind); assertEquals(2, r1.local)      // 建单、改单只在本机
        CloudSync(server, refs, src, inventory = true, buyerNo = "U1").run("s", "NS")
        assertEquals(1, server.inbounds.size)                                                     // 过账失败重试：不重复建单
        val body = server.inbounds.values.single()
        assertEquals("POS $no", body.remark); assertEquals("供应商A", body.supplierName)
        assertEquals(setOf("ITM-cola" to 24, "ITM-water" to 12), body.lines.map { it.itemId to it.qty }.toSet())
        assertEquals(listOf(server.inbounds.keys.single()), server.posted)
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""").matches(body.occurredAt))   // 不带时区的本地时间
    }

    @Test fun lossSplitsByReasonIntoScrapOutbounds() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = retailShop(backgroundScope)
        val no = w.newStockDoc(ai.neargo.fnb.pos.core.model.StockDocKind.Loss).getOrThrow()
        w.setStockDocLines(no, listOf(line("milk", 1, ai.neargo.fnb.pos.core.model.LossReason.Expired), line("chips", 2, ai.neargo.fnb.pos.core.model.LossReason.Damaged))).getOrThrow()
        w.advanceStockDoc(no).getOrThrow()
        val server = FakeServer().apply { invItems = itemsOf(w) }
        CloudSync(server, MemoryCloudRefs(), Source(disk), inventory = true, buyerNo = "U1").run("s", "NS")
        assertEquals(setOf("SCRAP/EXPIRED/ITM-milk", "SCRAP/BROKEN/ITM-chips"), server.outbounds.values.map { "${it.purpose}/${it.reasonCode}/${it.lines.single().itemId}" }.toSet())
        assertEquals(2, server.posted.size)
    }

    @Test fun approvedCountUploadsCountedQuantities() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = retailShop(backgroundScope)
        val no = w.newStockDoc(ai.neargo.fnb.pos.core.model.StockDocKind.Count).getOrThrow()
        w.setStockDocLines(no, listOf(line("cola", 7))).getOrThrow()
        w.advanceStockDoc(no).getOrThrow()                        // 提交待审批：只在本机
        val server = FakeServer().apply { invItems = itemsOf(w) }
        val refs = MemoryCloudRefs(); val src = Source(disk)
        CloudSync(server, refs, src, inventory = true, buyerNo = "U1").run("s", "NS")
        assertTrue(server.counts.isEmpty())
        w.advanceStockDoc(no).getOrThrow()                        // 审批过账
        CloudSync(server, refs, src, inventory = true, buyerNo = "U1").run("s", "NS")
        assertEquals(mapOf("ITM-cola" to 7), server.counts.values.single()); assertEquals(1, server.posted.size)
    }

    @Test fun weighedWaitsAndUnknownBarcodeNeedsAction() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = retailShop(backgroundScope)
        val a = w.newStockDoc(ai.neargo.fnb.pos.core.model.StockDocKind.Receive).getOrThrow(); w.setStockDocLines(a, listOf(line("apple", 5000))).getOrThrow(); w.advanceStockDoc(a).getOrThrow()
        val b = w.newStockDoc(ai.neargo.fnb.pos.core.model.StockDocKind.Receive).getOrThrow(); w.setStockDocLines(b, listOf(line("cola", 6))).getOrThrow(); w.advanceStockDoc(b).getOrThrow()
        val server = FakeServer().apply { invItems = itemsOf(w) - "6954767423579" }   // 可乐在服务端没建账
        val refs = MemoryCloudRefs()
        val r = CloudSync(server, refs, Source(disk), inventory = true, buyerNo = "U1").run("s", "NS")
        assertTrue(r.waiting >= 1)                                                     // 称重：等后端支持小数
        assertTrue(refs.issues().single().message.contains("6954767423579"))           // 条码对不上：需处理
        assertTrue(server.inbounds.isEmpty())
    }

    @Test fun stockAdjustUploadsAbsoluteOnlyForRetail() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = retailShop(backgroundScope)
        w.adjustStock("cola", null, 30, "盘点").getOrThrow()
        val server = FakeServer().apply { invItems = itemsOf(w) }
        CloudSync(server, MemoryCloudRefs(), Source(disk), inventory = true, buyerNo = "U1").run("s", "NS")
        assertEquals(listOf("ITM-cola" to 30), server.adjusts)
        val (d, disk2) = shop(backgroundScope)                                       // 餐饮门店：只在本机
        d.adjustStock(d.current.value!!.products.first().id, null, 9, "沽清").getOrThrow()
        val r = CloudSync(FakeServer(), MemoryCloudRefs(), Source(disk2), buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(1, r.local); assertEquals(0, r.uploaded)
    }

    @Test fun missingBuyerNoIsNeedsActionWithClearMessageAndNoCart() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val products = w.current.value!!.products
        w.openTable("H01", 2).getOrThrow()
        w.addDish("H01", products.first { it.skus.isEmpty() && (it.stock ?: 1) > 0 }, null, 1)
        w.sendToKitchen("H01", true)
        val server = FakeServer(catalogItems = catalogOf(products)); val refs = MemoryCloudRefs()
        CloudSync(server, refs, Source(disk)).run("s-coast", "NS002")                   // 没填买家号
        assertTrue(refs.issues().single().message.contains("买家号")); assertTrue(server.carts.isEmpty())   // 还没建购物车就拦下
        assertEquals(listOf("U1"), run { CloudSync(server, refs, Source(disk), buyerNo = "U1").also { refs.resolve(refs.issues().single().eventId) }.run("s-coast", "NS002"); server.buyers })
    }

    @Test fun heldTableIsReleasedByServingTasksThenReleaseService() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val due = paidTable(w, "现金")
        val server = FakeServer(catalogItems = catalogOf(w.current.value!!.products)).apply { this.due = due; holdTable = true }
        val refs = MemoryCloudRefs()
        val r = CloudSync(server, refs, Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(0, r.issues)
        assertEquals(listOf("TK0:start", "TK0:ready", "TK0:serve"), server.taskSteps)            // 先把任务推到已上菜
        assertEquals(1, server.released.size)                                                       // 再放台
        val ev = disk.commands.single { it.type == "PaymentRecorded" }.commandId
        assertNotNull(refs.get("released", ev))
    }

    @Test fun releaseFailureRetriesOnlyTheReleaseNotTheMoney() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val due = paidTable(w, "现金")
        val server = FakeServer(catalogItems = catalogOf(w.current.value!!.products)).apply { this.due = due; holdTable = true; releaseFailOnce = true }
        val refs = MemoryCloudRefs()
        CloudSync(server, refs, Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        val issue = refs.issues().single(); assertEquals("PaymentRecorded", issue.type)
        refs.put(CloudRef("attempt", issue.eventId, "1")); refs.resolve(issue.eventId)
        CloudSync(server, refs, Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        assertEquals(1, server.records.size); assertEquals(1, server.settlements.size)              // 钱只记一次、结算单只建一张
        assertEquals(1, server.released.size); assertEquals(0, refs.issues().size)
    }

    @Test fun releasedAtFinalizeSkipsTaskSteps() = runTest(UnconfinedTestDispatcher()) {
        val (w, disk) = shop(backgroundScope)
        val due = paidTable(w, "银行卡")
        val server = FakeServer(catalogItems = catalogOf(w.current.value!!.products)).apply { this.due = due }   // 服务端直接放台
        CloudSync(server, MemoryCloudRefs(), Source(disk), buyerNo = "U1").run("s-coast", "NS002")
        assertTrue(server.taskSteps.isEmpty() && server.released.isEmpty())
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
