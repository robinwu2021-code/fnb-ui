package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.identity.AuthState
import ai.neargo.fnb.pos.core.identity.DevIssuer
import ai.neargo.fnb.pos.core.identity.GrantKind
import ai.neargo.fnb.pos.core.identity.Grants
import ai.neargo.fnb.pos.core.identity.SoftwareDeviceKeys
import ai.neargo.fnb.pos.core.identity.thumbprint
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** N4：授权接入工作区——到期只读不删账本、复制数据不可写、会话不延长授权、信封带授权引用。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthWorkspaceTest {
    private val day = 86_400_000L
    private class Store : SnapshotStore {
        var json: String? = null
        val commands = mutableListOf<JournalCommand>()
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { json = source; commands += command; return testReceipt(command) }
    }
    private class Clock(var now: Long = 1_800_000_000_000L)

    private fun runWs(dev: DevIssuer? = DevIssuer(), block: suspend TestScope.(LocalWorkspace, Store, Clock, IdentityContext) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val disk = Store(); val clock = Clock()
        val issuer = dev ?: DevIssuer()
        val id = IdentityContext(SoftwareDeviceKeys(), { listOfNotNull(dev?.anchor) }, dev)
        val w = LocalWorkspace(disk, backgroundScope, id) { clock.now }
        w.register("南山小馆", "boss@ns.cn", "secret123", "secret123")
        w.selectStore("s-main")
        block(w, disk, clock, id)
    }
    private fun LocalWorkspace.idle() = current.value!!.tables.first { it.status == TableStatus.Idle }.id

    @Test fun pendingClaimThenDevGrantAndEnvelopeReference() = runWs { w, d, _, _ ->
        assertEquals(AuthState.PendingClaim, w.auth.value.state)
        assertTrue(w.devIssue(GrantKind.Short).isSuccess)
        assertEquals(AuthState.Active, w.auth.value.state)
        assertEquals("dev", w.auth.value.issuerEnv)
        assertTrue(w.openTable(w.idle(), 1).isSuccess)
        val c = d.commands.last()
        assertEquals(w.auth.value.grant!!.jti, c.authorizationRef); assertEquals("dev-1", c.policyVersion)
    }

    @Test fun expiryBlocksNewWritesButKeepsLedger() = runWs { w, d, clock, _ ->
        w.devIssue(GrantKind.Short)
        val t = w.idle()
        w.openTable(t, 1); w.addDish(t, w.current.value!!.products.first { it.id == "12" }, null, 1); w.sendToKitchen(t, false)
        val before = w.current.value!!
        clock.now += 8 * day
        w.logout(); assertTrue(w.login("boss@ns.cn", "secret123").isSuccess); w.selectStore("s-main") // 过期后仍可登录查询
        assertEquals(AuthState.Expired, w.auth.value.state)
        val r = w.addDish(t, w.current.value!!.products.first { it.id == "12" }, null, 1)
        assertTrue(r is OrderResult.Rejected && r.reason.contains("已暂停新营业写入"))
        assertTrue(w.addCategory("夜宵", "").exceptionOrNull()!!.message!!.contains("离线授权已过期"))
        val s = w.current.value!!
        assertEquals(before.orders, s.orders); assertEquals(before.outbox, s.outbox); assertEquals(before.payments, s.payments) // 账本不删
        // 续期：导入新授权后恢复
        assertTrue(w.devIssue(GrantKind.Long).isSuccess)
        assertEquals(AuthState.Active, w.auth.value.state)
        assertTrue(w.addCategory("夜宵", "").isSuccess)
    }

    @Test fun sessionCappedByGrantAndExpiresSeparately() = runWs { w, _, clock, id ->
        val expired = id.devIssuer!!.issue(thumbprint(id.keys.publicKey()), w.deviceId(), "local-tenant", listOf("s-main"), GrantKind.Short, clock.now - 2 * day, days = 1)
        assertTrue(w.importGrant(expired).isFailure) // 已过期的授权不能导入
        val shortGrant = Grants.peek(id.devIssuer!!.issue(thumbprint(id.keys.publicKey()), w.deviceId(), "local-tenant", listOf("s-main"), GrantKind.Short, clock.now))!!
        w.devIssue(GrantKind.Short)
        w.logout(); w.login("boss@ns.cn", "secret123"); w.selectStore("s-main")
        val session = w.auth.value.sessionExpiresAt
        assertTrue(session <= w.auth.value.grant!!.exp)
        assertEquals(clock.now + 12 * 3_600_000L, session)
        clock.now += 13 * 3_600_000L
        w.refreshAuth()
        assertEquals(AuthState.SessionExpired, w.auth.value.state)
        assertTrue(w.addCategory("夜宵", "").isFailure)
        assertTrue(shortGrant.exp > clock.now) // 授权本身仍有效，只是会话过期
    }

    @Test fun copiedDataOnAnotherDeviceCannotWrite() = runTest(UnconfinedTestDispatcher()) {
        val disk = Store(); val now = 1_800_000_000_000L
        val issuer = DevIssuer()
        val a = IdentityContext(SoftwareDeviceKeys(), { listOf(issuer.anchor) }, issuer)
        val w = LocalWorkspace(disk, backgroundScope, a) { now }
        w.register("南山小馆", "boss@ns.cn", "secret123", "secret123"); w.selectStore("s-main"); w.devIssue(GrantKind.Long)
        assertTrue(w.addCategory("夜宵", "").isSuccess)
        val copied = disk.json
        // 同一份数据装到另一台设备（另一把密钥）
        val b = IdentityContext(SoftwareDeviceKeys(), { listOf(issuer.anchor) }, issuer)
        val other = LocalWorkspace(disk, backgroundScope, b) { now }; other.restore()
        assertEquals(AuthState.IdentityMismatch, other.auth.value.state)
        assertTrue(other.addCategory("早茶", "").isFailure)
        assertEquals(copied, disk.json) // 未写入
        // 他机授权不能导入本机
        assertTrue(other.importGrant(disk.json!!.let { VaultCodec.decode(it).offlineGrant!! }).isFailure)
    }

    @Test fun revocationRollbackAndClockAnomaly() = runWs { w, _, clock, id ->
        w.devIssue(GrantKind.Long)
        assertTrue(w.revokeCurrent().isSuccess)
        assertEquals(AuthState.Revoked, w.auth.value.state)
        assertTrue(w.addCategory("夜宵", "").isFailure)
        // 更高版本授权恢复；回退到低版本被拒绝
        val tp = thumbprint(id.keys.publicKey())
        assertTrue(w.importGrant(id.devIssuer!!.issue(tp, w.deviceId(), "local-tenant", listOf("s-main"), GrantKind.Short, clock.now, authEpoch = 3)).isSuccess)
        assertTrue(w.importGrant(id.devIssuer!!.issue(tp, w.deviceId(), "local-tenant", listOf("s-main"), GrantKind.Long, clock.now, authEpoch = 2)).isFailure)
        assertTrue(w.addCategory("夜宵", "").isSuccess)
        // 时钟回拨：本机记录过更晚时间
        clock.now -= 2 * day
        w.refreshAuth()
        assertEquals(AuthState.ClockAnomaly, w.auth.value.state)
        assertTrue(w.addCategory("早茶", "").isFailure)
        assertTrue(w.confirmClock("boss@ns.cn", "wrong-pass").isFailure)
        assertTrue(w.confirmClock("boss@ns.cn", "secret123").isSuccess)
        // 现有授权签发时间晚于（已确认正确的）当前时间，仍判异常：需导入按正确时间签发的新授权
        assertEquals(AuthState.ClockAnomaly, w.auth.value.state)
        assertTrue(w.importGrant(id.devIssuer!!.issue(tp, w.deviceId(), "local-tenant", listOf("s-main"), GrantKind.Short, clock.now, authEpoch = 4)).isSuccess)
        assertEquals(AuthState.Active, w.auth.value.state)
        assertTrue(w.addCategory("早茶", "").isSuccess)
    }

    @Test fun releaseBuildHasNoDevIssuer() = runWs(dev = null) { w, _, _, _ ->
        assertFalse(w.devIssuerAvailable)
        assertTrue(w.devIssue(GrantKind.Short).isFailure)
        assertTrue(w.importGrant(DevIssuer().issue("x", "d", "t", listOf("s-main"), GrantKind.Short, 1_800_000_000_000L)).isFailure)
        assertEquals(AuthState.PendingClaim, w.auth.value.state)
    }
}
