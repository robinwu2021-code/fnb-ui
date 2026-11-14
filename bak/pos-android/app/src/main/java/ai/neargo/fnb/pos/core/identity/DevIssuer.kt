package ai.neargo.fnb.pos.core.identity

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/**
 * 开发测试签发器（N4）：模拟云端/激活服务签发 OfflineGrant，仅用于调试版与测试。
 * 信任根 env=dev，发行版不信任（见 TrustAnchors）；生产签发由身份服务提供，不在本机。
 */
class DevIssuer(private val keys: KeyPair = generate()) {
    val issuer = "neargo-dev-issuer"
    val kid = "dev-" + thumbprint(keys.public.encoded).take(12)
    val anchor get() = TrustAnchor(issuer, kid, keys.public.encoded, env = "dev")

    fun issue(
        deviceThumbprint: String, deviceId: String, tenantId: String, storeIds: List<String>, kind: GrantKind,
        now: Long, roles: List<String> = listOf("cashier", "supervisor"), authEpoch: Long = 1, days: Int = GrantPolicy.days(kind),
    ): String {
        val g = OfflineGrant(
            iss = issuer, sub = deviceId, aud = Grants.AUDIENCE, jti = "grant-" + UUID.randomUUID(), iat = now, nbf = now,
            exp = now + days * 86_400_000L, tenantId = tenantId, storeIds = storeIds, deviceKeyThumbprint = deviceThumbprint,
            roles = roles, kind = kind, policyVersion = "dev-1", authEpoch = authEpoch,
        )
        return Grants.encode(g, kid) { data -> Signature.getInstance("SHA256withECDSA").run { initSign(keys.private); update(data); sign() } }
    }

    companion object {
        fun generate(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    }
}

/** 软件密钥（单元测试 / 无 Keystore 环境）。 */
class SoftwareDeviceKeys(private val keys: KeyPair = DevIssuer.generate()) : DeviceKeyStore {
    override fun publicKey() = keys.public.encoded
    override fun exists() = true
    override fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run { initSign(keys.private); update(data); sign() }
}
