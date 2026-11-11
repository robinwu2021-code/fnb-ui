package ai.neargo.fnb.pos.core.identity

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyStore
import java.security.Signature

/** N4：Android Keystore 设备密钥——稳定、可验签、不可导出私钥；授权绑定指纹。 */
class KeystoreIdentityTest {
    @Test fun keystoreKeyIsStableSignsAndBindsGrant() {
        val alias = "neargo-test-" + System.nanoTime()
        try {
            val a = KeystoreDeviceKeys(alias)
            assertFalse(a.exists())
            val pub = a.publicKey()
            assertTrue(a.exists())
            assertArrayEquals(pub, KeystoreDeviceKeys(alias).publicKey()) // 重开后同一身份
            val sig = a.sign("hello".toByteArray())
            assertTrue(Signature.getInstance("SHA256withECDSA").run { initVerify(Grants.ecKey(pub)); update("hello".toByteArray()); verify(sig) })
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertNull((ks.getKey(alias, null)).encoded) // 私钥不可导出
            val issuer = DevIssuer()
            val token = issuer.issue(thumbprint(pub), "dev-x", "t", listOf("s-main"), GrantKind.Short, System.currentTimeMillis())
            val ctx = AuthContext("u", false, "s-main", token, emptySet(), 0, 0, 0, thumbprint(pub))
            assertEquals(AuthState.Active, OfflineAuthority.evaluate(ctx, System.currentTimeMillis(), thumbprint(pub), listOf(issuer.anchor)).state)
            assertEquals(AuthState.IdentityMismatch, OfflineAuthority.evaluate(ctx, System.currentTimeMillis(), thumbprint(SoftwareDeviceKeys().publicKey()), listOf(issuer.anchor)).state)
        } finally { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }
}
