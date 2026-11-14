package ai.neargo.fnb.pos.core.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/** 设备密钥：Android Keystore，不可导出；是否硬件保护取决于设备。 */
class KeystoreDeviceKeys(private val alias: String = "neargo-device-v1") : DeviceKeyStore {
    private val ks get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun exists() = ks.containsAlias(alias)

    private fun ensure() {
        if (exists()) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build())
        }.generateKeyPair()
    }

    override fun publicKey(): ByteArray { ensure(); return ks.getCertificate(alias).publicKey.encoded }

    override fun sign(data: ByteArray): ByteArray {
        ensure()
        val key = ks.getKey(alias, null) as PrivateKey
        return Signature.getInstance("SHA256withECDSA").run { initSign(key); update(data); sign() }
    }
}

/**
 * 信任根：发行版仅信任生产根（当前未配置——尚无身份服务）；调试版额外信任本机开发签发器。
 * 开发签发器私钥保存在应用私有目录（明确是测试材料，不进 Keystore 冒充生产信任根）。
 */
class TrustAnchors(context: Context, private val debug: Boolean) {
    val production: List<TrustAnchor> = emptyList()
    val devIssuer: DevIssuer? = if (debug) DevIssuer(loadOrCreate(File(context.filesDir, "dev-issuer.key"))) else null
    val all: List<TrustAnchor> get() = production + listOfNotNull(devIssuer?.anchor)

    private fun loadOrCreate(f: File): KeyPair {
        runCatching {
            val (pub, priv) = f.readText().split('\n')
            val kf = KeyFactory.getInstance("EC")
            val d = java.util.Base64.getDecoder()
            return KeyPair(kf.generatePublic(X509EncodedKeySpec(d.decode(pub))), kf.generatePrivate(PKCS8EncodedKeySpec(d.decode(priv))))
        }
        val kp = DevIssuer.generate()
        val e = java.util.Base64.getEncoder()
        f.writeText(e.encodeToString(kp.public.encoded) + "\n" + e.encodeToString(kp.private.encoded))
        return kp
    }
}
