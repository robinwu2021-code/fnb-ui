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
    private class Settings(var v: CloudSettings) : CloudSettingsStore { override fun load() = v; override fun save(settings: CloudSettings) { v = settings } }
    private class Api : CloudApi {
        var fail: CloudException? = null
        var openFail: CloudException? = null
        val opened = mutableListOf<String>()
        private fun f() { fail?.let { throw it } }
        override suspend fun storeInfo(storeNo: String) = run { f(); CloudStoreInfo(CloudStore(storeNo, "店"), "2026-11-12") }
        override suspend fun tableList(storeNo: String) = run { f(); listOf(CloudTable("t-1", "H01", status = "IDLE", version = 1)) }
        override suspend fun openTable(storeNo: String, key: String, tableNo: String, guestCount: Int): CloudOpen {
            f(); openFail?.let { throw it }; opened += key
            return CloudOpen(CloudCheck("C1", "OPEN", listOf(tableNo), guestCount, version = 1), CloudTable(tableNo, "H01", status = "IN_USE", version = 2))
        }
        override suspend fun checkDetail(storeNo: String, checkNo: String) = error("unused")
        override suspend fun transferTable(storeNo: String, key: String, checkNo: String, toTableNo: String, version: Long) = error("unused")
        override suspend fun menuList(storeNo: String) = emptyList<CloudMenu>()
        override suspend fun closePreview(storeNo: String, key: String, businessDate: String) = error("unused")
        override suspend fun closeSign(storeNo: String, key: String, closeNo: String, version: Long) = error("unused")
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
        assertEquals("2026-11-12", m.state.value.businessDate)
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
}
