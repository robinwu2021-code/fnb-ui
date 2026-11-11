package ai.neargo.fnb.pos.core.identity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * 设备密钥（N4）：私钥不离开受控容器（Android Keystore）；对外只给公钥与指纹。
 * 复制数据库到另一台设备时拿不到私钥，指纹不同，绑定在旧指纹上的授权随即失效。
 */
interface DeviceKeyStore {
    /** X.509 编码公钥；首次调用时生成密钥对。 */
    fun publicKey(): ByteArray
    fun sign(data: ByteArray): ByteArray
    /** 是否已存在密钥（不触发生成）。 */
    fun exists(): Boolean
}

fun thumbprint(publicKey: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey))

/** 授权种类：默认期限短期 7 天、长期 90 天（用户已确认，可配置）。 */
@Serializable enum class GrantKind(val label: String) { Short("短期离线"), Long("长期离线") }

object GrantPolicy {
    var shortDays = 7
    var longDays = 90
    /** 本机会话最长时长，且不得超过授权到期。 */
    var sessionHours = 12
    /** 时钟回拨容差。 */
    const val CLOCK_TOLERANCE_MS = 5 * 60_000L
    fun days(kind: GrantKind) = if (kind == GrantKind.Short) shortDays else longDays
    /**
     * 无任何可信授权（本地待认领）时是否允许营业写入。方案默认只提供演示/配置能力，正式营业需明确批准；
     * 为保持现有本机注册体验，暂允许并在页面标注「待认领」——待门店/产品确认。
     */
    const val PENDING_CLAIM_ALLOWS_BUSINESS = true
}

/** OfflineGrant 载荷（compact JWS，typ=offline-grant+jws，alg=ES256）。 */
@Serializable
data class OfflineGrant(
    val iss: String, val sub: String, val aud: String, val jti: String,
    val iat: Long, val nbf: Long, val exp: Long,
    val tenantId: String, val storeIds: List<String>, val deviceKeyThumbprint: String,
    val roles: List<String>, val kind: GrantKind, val policyVersion: String, val authEpoch: Long,
)

@Serializable private data class JwsHeader(val alg: String, val typ: String, val kid: String)

/** 信任根：生产与开发测试严格隔离；发行版不信任开发签发器。 */
data class TrustAnchor(val issuer: String, val kid: String, val publicKey: ByteArray, val env: String)

object Grants {
    const val AUDIENCE = "neargo-pos"
    const val TYP = "offline-grant+jws"
    const val ALG = "ES256"
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val d64 = Base64.getUrlDecoder()

    fun encode(grant: OfflineGrant, kid: String, signer: (ByteArray) -> ByteArray): String {
        val h = b64.encodeToString(json.encodeToString(JwsHeader.serializer(), JwsHeader(ALG, TYP, kid)).toByteArray())
        val p = b64.encodeToString(json.encodeToString(OfflineGrant.serializer(), grant).toByteArray())
        return "$h.$p.${b64.encodeToString(signer("$h.$p".toByteArray()))}"
    }

    /** 只解析载荷（展示用）；不代表已验证。 */
    fun peek(token: String): OfflineGrant? = runCatching {
        json.decodeFromString(OfflineGrant.serializer(), String(d64.decode(token.split('.')[1])))
    }.getOrNull()

    /** 验签：固定算法与类型、受信签发方与 kid、受众；签名用 ES256（DER）。不检查期限（由评估器负责）。 */
    fun verify(token: String, anchors: List<TrustAnchor>): Result<Pair<OfflineGrant, TrustAnchor>> = runCatching {
        val parts = token.trim().split('.')
        require(parts.size == 3) { "授权格式无效" }
        val header = json.decodeFromString(JwsHeader.serializer(), String(d64.decode(parts[0])))
        require(header.alg == ALG && header.typ == TYP) { "授权类型或算法不受支持" }
        val grant = json.decodeFromString(OfflineGrant.serializer(), String(d64.decode(parts[1])))
        val anchor = anchors.firstOrNull { it.kid == header.kid && it.issuer == grant.iss } ?: error("签发方不受信任")
        require(grant.aud == AUDIENCE) { "授权受众不符" }
        val ok = Signature.getInstance("SHA256withECDSA").run {
            initVerify(ecKey(anchor.publicKey)); update("${parts[0]}.${parts[1]}".toByteArray()); verify(d64.decode(parts[2]))
        }
        require(ok) { "授权签名无效" }
        grant to anchor
    }

    fun ecKey(encoded: ByteArray): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
}

/** 授权状态：只有 Active / Demo / PendingClaim(按策略) 允许新营业写入。 */
enum class AuthState(val label: String, val writable: Boolean) {
    Demo("演示账号", true),
    Active("离线授权有效", true),
    PendingClaim("本地待认领（未完成云端注册）", GrantPolicy.PENDING_CLAIM_ALLOWS_BUSINESS),
    NotYetValid("授权尚未生效", false),
    Expired("离线授权已过期", false),
    Revoked("授权已撤销", false),
    ClockAnomaly("设备时间异常", false),
    IdentityMismatch("设备身份不符（数据可能来自其他设备）", false),
    WrongStore("授权不含当前门店", false),
    Invalid("授权无效", false),
    SessionExpired("登录已过期", false),
}

data class AuthStatus(
    val state: AuthState, val grant: OfflineGrant? = null, val issuerEnv: String = "", val reason: String = "",
    val remainingMs: Long = 0, val deviceThumbprint: String = "", val sessionExpiresAt: Long = 0,
) {
    val writable get() = state.writable
    /** 无法写入时的完整说明（暂停新营业写入，账本保留可查询）。 */
    val blockMessage get() = "${state.label}${if (reason.isNotBlank()) "：$reason" else ""}。已暂停新营业写入，账本保留可查询与导出，请在「授权中心」续期或导入激活包"
}

/** 评估所需的本机持久状态（存于 Vault）。 */
data class AuthContext(
    val user: String?, val demo: Boolean, val storeId: String?, val grantToken: String?, val revoked: Set<String>,
    val knownEpoch: Long, val lastSeenWall: Long, val sessionExpiresAt: Long, val boundThumbprint: String,
)

object OfflineAuthority {
    /** 纯函数评估：签名、设备绑定、撤销、epoch、时钟、期限、门店、会话。 */
    fun evaluate(ctx: AuthContext, now: Long, deviceThumbprint: String, anchors: List<TrustAnchor>): AuthStatus {
        fun st(s: AuthState, reason: String = "", g: OfflineGrant? = null, env: String = "") =
            AuthStatus(s, g, env, reason, g?.let { (it.exp - now).coerceAtLeast(0) } ?: 0, deviceThumbprint, ctx.sessionExpiresAt)
        if (ctx.lastSeenWall > 0 && now + GrantPolicy.CLOCK_TOLERANCE_MS < ctx.lastSeenWall) return st(AuthState.ClockAnomaly, "当前时间早于本机已记录时间，可能被回拨")
        if (ctx.user != null && ctx.sessionExpiresAt > 0 && now > ctx.sessionExpiresAt) return st(AuthState.SessionExpired, "请重新登录")
        if (ctx.boundThumbprint.isNotBlank() && ctx.boundThumbprint != deviceThumbprint) return st(AuthState.IdentityMismatch, "本机密钥与数据绑定的设备不同")
        val token = ctx.grantToken ?: return if (ctx.demo) st(AuthState.Demo) else st(AuthState.PendingClaim)
        val (g, anchor) = Grants.verify(token, anchors).getOrElse { return st(AuthState.Invalid, it.message.orEmpty()) }
        return when {
            g.deviceKeyThumbprint != deviceThumbprint -> st(AuthState.IdentityMismatch, "授权绑定的设备密钥与本机不同", g, anchor.env)
            g.jti in ctx.revoked -> st(AuthState.Revoked, "", g, anchor.env)
            g.authEpoch < ctx.knownEpoch -> st(AuthState.Revoked, "授权版本低于本机已知版本", g, anchor.env)
            now + GrantPolicy.CLOCK_TOLERANCE_MS < g.iat -> st(AuthState.ClockAnomaly, "当前时间早于授权签发时间", g, anchor.env)
            now < g.nbf -> st(AuthState.NotYetValid, "", g, anchor.env)
            now > g.exp -> st(AuthState.Expired, "", g, anchor.env)
            ctx.storeId != null && ctx.storeId !in g.storeIds -> st(AuthState.WrongStore, "", g, anchor.env)
            else -> st(AuthState.Active, "", g, anchor.env)
        }
    }

    /** 会话截止：不得超过父授权到期。 */
    fun sessionExpiry(now: Long, grantExp: Long?): Long = minOf(now + GrantPolicy.sessionHours * 3_600_000L, grantExp ?: Long.MAX_VALUE)
}
