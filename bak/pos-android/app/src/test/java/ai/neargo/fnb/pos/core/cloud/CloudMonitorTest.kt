package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.data.JournalEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 连通监控：未设置 / 副 POS / 断网 / 防抖上线 / 令牌失效 / 在线自动上传 / 需处理与解除 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudMonitorTest {
    private class Settings(var v: CloudSettings, var online: Long = 0) : CloudSettingsStore {
        override fun load() = v; override fun save(settings: CloudSettings) { v = settings }
        override fun lastOnlineAt() = online; override fun saveLastOnlineAt(at: Long) { online = at }
    }
    private class Api : CloudApi {
        var fail: CloudException? = null
        var openFail: CloudException? = null
        val opened = mutableListOf<String>()
        private fun f() { fail?.let { throw it } }
        override suspend fun storeProfile(storeNo: String) = run { f(); CloudProfile(storeNo, "海岸店", currency = "CNY", timezone = "Asia/Shanghai", serverTime = "2026-11-13T14:37:35.930027Z") }
        override suspend fun tableList(storeNo: String) = run { f(); listOf(CloudTable("t-1", "H01", version = 1)) }
        override suspend fun openCheck(storeNo: String, key: String, tableNos: List<String>, partySize: Int, tableVersions: List<CloudTableVersion>): CloudCheck {
            f(); openFail?.let { throw it }; opened += key
            return CloudCheck("C1", status = "OPEN", partySize = partySize, tableNos = tableNos, version = 1)
        }
        override suspend fun check(checkNo: String) = error("unused")
        override suspend fun transferCheck(key: String, checkNo: String, version: Long, targetTableNos: List<String>, tableVersions: List<CloudTableVersion>) = error("unused")
        override suspend fun catalog(storeNo: String) = error("unused")
        override suspend fun createCart(storeNo: String, key: String, checkNo: String) = error("unused")
        override suspend fun cart(cartNo: String) = error("unused")
        override suspend fun replaceCartLine(key: String, cartNo: String, sourceLineRef: String, line: CloudLineInput, version: Long) = error("unused")
        override suspend fun quote(storeNo: String, key: String, cartNo: String, version: Long) = error("unused")
        override suspend fun confirmQuote(quoteNo: String, cartNo: String, version: Long, confirmationDigest: String) = error("unused")
        override suspend fun placeBatch(storeNo: String, key: String, cartNo: String, version: Long, quoteNo: String, confirmationDigest: String, buyerNo: String?) = error("unused")
        override suspend fun openBusinessDay(key: String, businessDate: String) = error("unused")
        override suspend fun closePreview(key: String, businessDate: String, currency: String) = error("unused")
        override suspend fun closeSign(key: String, closeNo: String, snapshotHash: String) = error("unused")
        override suspend fun createCounterSettlement(key: String, checkNo: String) = error("unused")
        override suspend fun finalizeCounterSettlement(settlementNo: String, offlineRecordNos: List<String>) = error("unused")
        override suspend fun cancelCounterSettlement(settlementNo: String) = error("unused")
        override suspend fun counterSettlement(settlementNo: String) = error("unused")
        override suspend fun stations(storeNo: String) = error("unused")
        override suspend fun kitchenTasks(storeNo: String, stationNo: String) = error("unused")
        override suspend fun checkBatchNos(storeNo: String, checkNo: String) = error("unused")
        override suspend fun advanceTask(key: String, taskNo: String, action: String, version: Long, qty: Double) = error("unused")
        override suspend fun releaseService(key: String, checkNo: String, version: Long, reason: String) = error("unused")
        override suspend fun inventoryItemByBarcode(code: String) = error("unused")
        override suspend fun createInbound(body: CloudInboundBody) = error("unused")
        override suspend fun postInbound(no: String) = error("unused")
        override suspend fun createOutbound(body: CloudOutboundBody) = error("unused")
        override suspend fun postOutbound(no: String) = error("unused")
        override suspend fun openCount(itemIds: List<String>) = error("unused")
        override suspend fun fillCount(no: String, lines: List<CloudCountLine>) = error("unused")
        override suspend fun postCount(no: String) = error("unused")
        override suspend fun adjustInventory(itemId: String, countedQty: Int, reasonCode: String) = error("unused")
        override suspend fun recordOffline(key: String, body: CloudOfflineRecordBody) = error("unused")
    }
    private class Events : EventSource {
        val list = mutableListOf(JournalEvent(1, "ev-1", "order-1", 1, "TableOpened",
            """{"tableId":"H01","table":{"id":"H01","guests":2},"order":{"orderId":"o-1"}}""", envelope = "{}"))
        override suspend fun pending(storeId: String, limit: Int) = list.toList()
        override suspend fun ack(storeId: String, eventId: String) { list.removeAll { it.eventId == eventId } }
    }

    private val net = MutableStateFlow(true); private val up = MutableStateFlow(true); private val pending = MutableStateFlow(1)
    private val api = Api(); private val events = Events(); private val refs = MemoryCloudRefs()
    private fun monitor(s: CloudSettings = CloudSettings("http://x", "NS002")) =
        CloudMonitor(Settings(s), api, { "s-coast" }, { refs }, events, net, up, pending, { 1L })

    @Test fun notConfiguredAndSecondary() = runTest(UnconfinedTestDispatcher()) {
        val m = monitor(CloudSettings()); m.observe(backgroundScope); m.tick()
        assertEquals(CloudBadge.NotConfigured, m.state.value.badge)
        up.value = false; m.tick()
        assertEquals(CloudBadge.ViaHost, m.state.value.badge)
        assertTrue(m.syncNow().isFailure)
    }

    @Test fun debounceThenAutoUpload() = runTest(UnconfinedTestDispatcher()) {
        val m = monitor(); m.observe(backgroundScope)
        m.tick(); assertTrue(m.state.value.badge is CloudBadge.Offline)       // 一次成功还不算在线
        m.tick()
        assertEquals(listOf("ev-1"), api.opened)                                // 在线且有待上传：回放
        assertTrue(events.list.isEmpty())
        pending.value = 0; m.tick()
        assertEquals(CloudBadge.Online(0), m.state.value.badge)
        assertEquals("海岸店", m.state.value.profile?.storeName)
    }

    @Test fun networkLossAndUnauthorized() = runTest(UnconfinedTestDispatcher()) {
        val m = monitor(); m.observe(backgroundScope)
        pending.value = 0; m.tick(); m.tick(); assertTrue(m.state.value.health.online)
        net.value = false; m.tick()
        assertTrue(m.state.value.badge is CloudBadge.Offline)
        assertTrue(m.syncNow().isFailure)
        net.value = true; m.tick(); m.tick(); assertTrue(m.state.value.health.online)
        api.fail = CloudException.of(10401, "令牌失效"); m.tick()
        val b = m.state.value.badge as CloudBadge.Offline
        assertEquals("令牌失效", b.reason)                                       // 配置类错误立即离线
    }

    @Test fun transientFailureNeedsTwoMisses() = runTest(UnconfinedTestDispatcher()) {
        val m = monitor(); m.observe(backgroundScope); pending.value = 0
        m.tick(); m.tick()
        api.fail = CloudException(CloudErrorKind.Network, 0, "超时")
        m.tick(); assertTrue(m.state.value.health.online)
        m.tick(); assertFalse(m.state.value.health.online)
    }

    @Test fun rejectionNeedsActionThenResolve() = runTest(UnconfinedTestDispatcher()) {
        val m = monitor(); m.observe(backgroundScope)
        api.openFail = CloudException.of(10400, "桌台不空闲")
        m.tick(); m.tick()
        assertEquals(CloudBadge.NeedsAction(1), m.state.value.badge)
        api.openFail = null
        m.retry("ev-1")
        m.syncNow().getOrThrow()
        assertEquals(listOf("ev-1#1"), api.opened)                             // 重试换尝试键，避开服务端固定的拒绝结果
        assertTrue(events.list.isEmpty())
        assertTrue(m.state.value.issues.isEmpty())
    }

    @Test fun skipAcksWithoutUpload() = runTest(UnconfinedTestDispatcher()) {
        val m = monitor(); m.observe(backgroundScope)
        api.openFail = CloudException.of(10400, "桌台不空闲")
        m.tick(); m.tick()
        m.skip("ev-1")
        assertTrue(events.list.isEmpty()); assertTrue(api.opened.isEmpty())
        assertTrue(m.state.value.badge is CloudBadge.Online)
    }

    @Test fun offlineLimitWarnsThenBlocksDayCloseAndClearsOnReconnect() = runTest(UnconfinedTestDispatcher()) {
        val h = 3_600_000L; var now = 1_000 * h
        val store = Settings(CloudSettings("http://x", "NS002"))
        val m = CloudMonitor(store, api, { "s-coast" }, { refs }, events, net, up, pending, { now })
        m.observe(backgroundScope)
        pending.value = 0; m.tick(); m.tick()
        assertEquals(now, store.online)                                         // 连上即记时间（重启后还在）
        api.fail = CloudException(CloudErrorKind.Network, 0, "断网"); net.value = false; pending.value = 3
        now += 23 * h; m.tick(); assertEquals(CloudLimit.None, m.state.value.limit); assertNull(m.dayCloseBlock())
        now += 2 * h; m.tick()
        assertTrue(m.state.value.limit is CloudLimit.Warn); assertNull(m.dayCloseBlock())
        assertTrue((m.state.value.badge as CloudBadge.Offline).limit is CloudLimit.Warn)
        now += 48 * h; m.tick()
        assertTrue(m.state.value.limit is CloudLimit.Block); assertNotNull(m.dayCloseBlock())
        api.fail = null; net.value = true; pending.value = 0; m.tick(); m.tick()
        assertEquals(CloudLimit.None, m.state.value.limit); assertNull(m.dayCloseBlock())
        assertEquals(now, store.online)
    }

    @Test fun limitStartsFromConfigureNotFromNever() = runTest(UnconfinedTestDispatcher()) {
        var now = 5_000_000L
        val store = Settings(CloudSettings())
        val m = CloudMonitor(store, api, { "s-coast" }, { refs }, events, net, up, pending, { now })
        m.observe(backgroundScope)
        m.configure(CloudSettings("http://x", "NS002"))
        assertEquals(5_000_000L, store.online)
        net.value = false; now += 80 * 3_600_000L; m.tick()
        assertTrue(m.state.value.limit is CloudLimit.Block)                    // 设置后一直连不上，同样计时
    }

    @Test fun secondaryNeverBlocked() = runTest(UnconfinedTestDispatcher()) {
        val store = Settings(CloudSettings("http://x", "NS002"), online = 1)
        up.value = false; net.value = false
        val m = CloudMonitor(store, api, { "s-coast" }, { refs }, events, net, up, pending, { 1_000 * 3_600_000L })
        m.observe(backgroundScope); m.tick()
        assertNull(m.dayCloseBlock())
    }

    @Test fun priceDiffsComeFromRefs() = runTest(UnconfinedTestDispatcher()) {
        refs.put(CloudRef("pricediff", "K-1", "16200/17010"))
        val m = monitor(); m.observe(backgroundScope); m.refreshLocal()
        assertEquals(listOf(CloudPriceDiff("K-1", 16200, 17010)), m.state.value.priceDiffs)
    }

    @Test fun eventsWaitingForBackendAreNotPending() = runTest(UnconfinedTestDispatcher()) {
        // 预订事件服务端还没接口：留在本机，页头仍是「已全部上传」，另注明等接口的条数
        events.list.clear()
        events.list += JournalEvent(1, "ev-r", "reservation:r1", 1, "ReservationCreated", "{}", envelope = "{}")
        events.list += JournalEvent(2, "ev-r2", "reservation:r1", 2, "ReservationCancelled", "{}", envelope = "{}")
        pending.value = 2
        val m = monitor(); m.observe(backgroundScope)
        m.tick(); m.tick()                                       // 探测上线后回放一轮
        assertEquals(2, m.state.value.backendWait); assertEquals(0, m.state.value.uploadable)
        assertEquals(CloudBadge.Online(0, 2), m.state.value.badge)
        assertEquals(2, events.list.size)                       // 没有被 ack，留在本机
    }

    @Test fun backendWaitDoesNotTriggerOfflineLimit() = runTest(UnconfinedTestDispatcher()) {
        val h = 3_600_000L; var now = 1_000 * h
        val store = Settings(CloudSettings("http://x", "NS002"))
        events.list.clear(); events.list += JournalEvent(1, "ev-r", "reservation:r1", 1, "ReservationCreated", "{}", envelope = "{}")
        pending.value = 1
        val m = CloudMonitor(store, api, { "s-coast" }, { refs }, events, net, up, pending, { now })
        m.observe(backgroundScope); m.tick(); m.tick()
        api.fail = CloudException(CloudErrorKind.Network, 0, "断网"); net.value = false
        now += 100 * h; m.tick()
        assertEquals(CloudLimit.None, m.state.value.limit); assertNull(m.dayCloseBlock())   // 只有等接口的事件：断网再久也不挡日结
    }
}
