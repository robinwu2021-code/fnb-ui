package ai.neargo.fnb.pos.core.identity

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

/** N4：验签边界与授权状态评估（纯函数）。 */
class OfflineAuthTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private val issuer = DevIssuer()
    private val device = SoftwareDeviceKeys()
    private val tp = thumbprint(device.publicKey())
    private fun ctx(token: String?, user: String? = "boss@ns.cn", store: String? = "s-main", revoked: Set<String> = emptySet(), epoch: Long = 0, seen: Long = 0, session: Long = 0, bound: String = tp) =
        AuthContext(user, user == "体验账号", store, token, revoked, epoch, seen, session, bound)
    private fun token(kind: GrantKind = GrantKind.Short, at: Long = now, thumb: String = tp, epoch: Long = 1, stores: List<String> = listOf("s-main")) =
        issuer.issue(thumb, "dev-1", "t", stores, kind, at, authEpoch = epoch)
    private fun eval(c: AuthContext, t: Long = now, anchors: List<TrustAnchor> = listOf(issuer.anchor)) = OfflineAuthority.evaluate(c, t, tp, anchors)

    @Test fun signatureTypeIssuerAndAudienceAreEnforced() {
        val t = token()
        assertTrue(Grants.verify(t, listOf(issuer.anchor)).isSuccess)
        val (h, p, s) = t.split('.')
        val d = Base64.getUrlDecoder(); val e = Base64.getUrlEncoder().withoutPadding()
        val tampered = e.encodeToString(String(d.decode(p)).replace("\"Short\"", "\"Long\"").toByteArray())
        assertTrue(Grants.verify("$h.$tampered.$s", listOf(issuer.anchor)).isFailure) // 改期限/类型 → 签名失败
        val noneAlg = e.encodeToString(String(d.decode(h)).replace("ES256", "none").toByteArray())
        assertTrue(Grants.verify("$noneAlg.$p.$s", listOf(issuer.anchor)).isFailure)
        val otherType = e.encodeToString(String(d.decode(h)).replace("offline-grant+jws", "access+jwt").toByteArray())
        assertTrue(Grants.verify("$otherType.$p.$s", listOf(issuer.anchor)).isFailure)
        assertTrue(Grants.verify(t, listOf(DevIssuer().anchor)).isFailure) // 其他签发方
        assertTrue(Grants.verify(t, emptyList()).isFailure) // 发行版：不信任开发签发
        assertEquals(AuthState.Invalid, eval(ctx(t), anchors = emptyList()).state)
    }

    @Test fun shortAndLongDefaultsAndExpiry() {
        assertEquals(7, GrantPolicy.days(GrantKind.Short)); assertEquals(90, GrantPolicy.days(GrantKind.Long))
        val s = token(GrantKind.Short)
        assertEquals(AuthState.Active, eval(ctx(s)).state)
        assertEquals(AuthState.Active, eval(ctx(s), now + 7 * day - 1).state)
        assertEquals(AuthState.Expired, eval(ctx(s), now + 7 * day + 1).state)
        val l = token(GrantKind.Long)
        assertEquals(AuthState.Active, eval(ctx(l), now + 89 * day).state)
        assertEquals(AuthState.Expired, eval(ctx(l), now + 91 * day).state)
        assertFalse(eval(ctx(s), now + 8 * day).writable)
    }

    @Test fun revocationEpochStoreAndDeviceBinding() {
        val t = token()
        val jti = Grants.peek(t)!!.jti
        assertEquals(AuthState.Revoked, eval(ctx(t, revoked = setOf(jti))).state)
        assertEquals(AuthState.Revoked, eval(ctx(t, epoch = 2)).state) // 旧版本授权
        assertEquals(AuthState.WrongStore, eval(ctx(t, store = "s-coast")).state)
        val other = SoftwareDeviceKeys()
        assertEquals(AuthState.IdentityMismatch, eval(ctx(token(thumb = thumbprint(other.publicKey())))).state) // 复制他机授权
        assertEquals(AuthState.IdentityMismatch, eval(ctx(t, bound = "another-device")).state) // 数据绑定他机
    }

    @Test fun clockRollbackAndNotYetValid() {
        val t = token()
        assertEquals(AuthState.ClockAnomaly, eval(ctx(t, seen = now + day)).state)
        assertEquals(AuthState.Active, eval(ctx(t, seen = now + 60_000)).state) // 容差内
        assertEquals(AuthState.ClockAnomaly, eval(ctx(t), now - day).state) // 早于签发
        // 签发时间在容差内但尚未到 nbf：尚未生效
        assertEquals(AuthState.NotYetValid, eval(ctx(token()), now - 60_000).state)
    }

    @Test fun sessionCannotOutliveGrantAndPendingClaim() {
        assertEquals(now + 12 * 3_600_000L, OfflineAuthority.sessionExpiry(now, now + 7 * day))
        assertEquals(now + 3_600_000L, OfflineAuthority.sessionExpiry(now, now + 3_600_000L))
        assertEquals(AuthState.SessionExpired, eval(ctx(token(), session = now - 1)).state)
        assertEquals(AuthState.PendingClaim, eval(ctx(null)).state)
        assertEquals(GrantPolicy.PENDING_CLAIM_ALLOWS_BUSINESS, eval(ctx(null)).writable)
        assertEquals(AuthState.Demo, eval(ctx(null, user = "体验账号")).state)
    }
}
