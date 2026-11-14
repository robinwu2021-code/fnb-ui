package ai.neargo.fnb.pos.core.lan

import ai.neargo.fnb.pos.core.identity.DeviceKeyStore
import ai.neargo.fnb.pos.core.identity.Grants
import ai.neargo.fnb.pos.core.identity.thumbprint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.Socket
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 局域网认证加密通道（N5）。
 * 握手：双方临时 ECDH(P-256) + 各自设备私钥签名整段握手记录（身份认证、防中间人）；
 * 会话密钥：HKDF-SHA256(共享密钥, 双方随机数)，两个方向各一把 AES-256-GCM 密钥；
 * 报文：12 字节 nonce = 方向(4) + 递增序号(8)，接收方要求严格递增（防重放/乱序注入）。
 * 配对：一次性配对码只用于首次互认设备公钥（HMAC 证明），不是 PAKE，见实施记录的限制说明。
 */
object LanCrypto {
    const val PROTOCOL = "neargo-lan-v1"
    private val rnd = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val d64 = Base64.getDecoder()
    fun enc(b: ByteArray): String = b64.encodeToString(b)
    fun dec(s: String): ByteArray = d64.decode(s)
    fun random(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }
    fun sha256(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run { parts.forEach { update(it) }; digest() }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

    /** RFC 5869 HKDF-SHA256。 */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, len: Int): ByteArray {
        val prk = hmac(salt, ikm)
        var t = ByteArray(0); val out = java.io.ByteArrayOutputStream(); var i = 1
        while (out.size() < len) { t = hmac(prk, t + info.toByteArray() + byteArrayOf(i.toByte())); out.write(t); i++ }
        return out.toByteArray().copyOf(len)
    }

    fun ephemeral() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun ecdh(priv: java.security.PrivateKey, peerPub: ByteArray): ByteArray =
        KeyAgreement.getInstance("ECDH").run { init(priv); doPhase(Grants.ecKey(peerPub), true); generateSecret() }

    fun verify(pub: ByteArray, data: ByteArray, sig: ByteArray) = runCatching {
        Signature.getInstance("SHA256withECDSA").run { initVerify(Grants.ecKey(pub)); update(data); verify(sig) }
    }.getOrDefault(false)

    /** 常量时间比较。 */
    fun same(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)
}

@Serializable data class ClientHello(
    val protocol: String, val deviceId: String, val deviceName: String, val devicePub: String, val ephPub: String, val nonce: String,
    /** pair=首次配对（附配对码证明），session=已配对设备建立会话。 */
    val mode: String,
)
@Serializable data class ServerHello(val protocol: String, val hostId: String, val hostName: String, val hostPub: String, val ephPub: String, val nonce: String, val sig: String, val pairProof: String = "")
@Serializable data class ClientFinish(val sig: String, val pairProof: String = "")
/** 握手结果（加密后首帧由主机发出）：会话期限不超过主机授权；拒绝时说明原因。 */
@Serializable data class Welcome(val ok: Boolean, val reason: String = "", val sessionExpiresAt: Long = 0, val storeId: String = "", val tenantId: String = "")

/** 已建立的加密会话：线程不安全，调用方串行读写。 */
class SecureChannel internal constructor(private val socket: Socket, sendKey: ByteArray, recvKey: ByteArray, private val sendDir: Int) {
    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output = DataOutputStream(socket.getOutputStream().buffered())
    private val sk = SecretKeySpec(sendKey, "AES"); private val rk = SecretKeySpec(recvKey, "AES")
    private var sendSeq = 0L; private var recvSeq = -1L
    var peerId: String = ""; internal set
    var peerThumbprint: String = ""; internal set

    fun send(plain: ByteArray) {
        val nonce = ByteBuffer.allocate(12).putInt(sendDir).putLong(sendSeq++).array()
        val ct = Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.ENCRYPT_MODE, sk, GCMParameterSpec(128, nonce)); doFinal(plain) }
        writeFrame(output, nonce + ct)
    }

    fun receive(): ByteArray {
        val frame = readFrame(input)
        require(frame.size > 12) { "报文过短" }
        val nonce = frame.copyOf(12)
        val bb = ByteBuffer.wrap(nonce)
        require(bb.int != sendDir) { "报文方向错误" }
        val seq = bb.long
        require(seq > recvSeq) { "重放或乱序报文" }
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE, rk, GCMParameterSpec(128, nonce)); doFinal(frame, 12, frame.size - 12) }
        recvSeq = seq
        return plain
    }

    fun close() = runCatching { socket.close() }
}

internal const val MAX_FRAME = 8 * 1024 * 1024

internal fun writeFrame(out: DataOutputStream, data: ByteArray) { out.writeInt(data.size); out.write(data); out.flush() }
internal fun readFrame(inp: DataInputStream): ByteArray {
    val n = try { inp.readInt() } catch (e: EOFException) { throw java.io.IOException("连接已关闭") }
    require(n in 1..MAX_FRAME) { "报文长度无效" }
    return ByteArray(n).also { inp.readFully(it) }
}

/** 握手（明文阶段只交换公钥、随机数与签名；身份由签名与固定公钥/配对码证明）。 */
object LanHandshake {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun transcript(c: ClientHello, s: ServerHello) =
        LanCrypto.sha256(json.encodeToString(ClientHello.serializer(), c).toByteArray(), json.encodeToString(ServerHello.serializer(), s.copy(sig = "", pairProof = "")).toByteArray())

    private fun keys(shared: ByteArray, c: ClientHello, s: ServerHello): Pair<ByteArray, ByteArray> {
        val okm = LanCrypto.hkdf(shared, LanCrypto.dec(c.nonce) + LanCrypto.dec(s.nonce), LanCrypto.PROTOCOL, 64)
        return okm.copyOf(32) to okm.copyOfRange(32, 64) // c→s, s→c
    }

    private fun pairKey(code: String, th: ByteArray) = LanCrypto.hkdf(code.toByteArray(), th, "$PROTOCOL-pair", 32)
    private const val PROTOCOL = LanCrypto.PROTOCOL

    /**
     * 副机发起。pinnedHostPub 为已配对主机公钥（session 模式必须匹配）；pairCode 用于首次配对。
     * 返回通道与主机公钥（配对时由调用方保存）。
     */
    data class ClientSession(val channel: SecureChannel, val server: ServerHello, val welcome: Welcome)

    fun client(socket: Socket, deviceId: String, deviceName: String, keys: DeviceKeyStore, pinnedHostPub: ByteArray?, pairCode: String?): ClientSession {
        socket.soTimeout = 10_000
        val inp = DataInputStream(socket.getInputStream().buffered()); val out = DataOutputStream(socket.getOutputStream().buffered())
        val eph = LanCrypto.ephemeral()
        val hello = ClientHello(PROTOCOL, deviceId, deviceName, LanCrypto.enc(keys.publicKey()), LanCrypto.enc(eph.public.encoded), LanCrypto.enc(LanCrypto.random(32)), if (pairCode != null) "pair" else "session")
        writeFrame(out, json.encodeToString(ClientHello.serializer(), hello).toByteArray())
        val sh = json.decodeFromString(ServerHello.serializer(), String(readFrame(inp)))
        require(sh.protocol == PROTOCOL) { "协议版本不符" }
        val th = transcript(hello, sh)
        val hostPub = LanCrypto.dec(sh.hostPub)
        require(LanCrypto.verify(hostPub, th, LanCrypto.dec(sh.sig))) { "主机签名无效" }
        if (pinnedHostPub != null) require(LanCrypto.same(pinnedHostPub, hostPub)) { "主机身份与配对记录不符" }
        if (pairCode != null) require(LanCrypto.same(LanCrypto.hmac(pairKey(pairCode, th), "host".toByteArray() + th), LanCrypto.dec(sh.pairProof))) { "配对码不正确或主机不可信" }
        val finish = ClientFinish(LanCrypto.enc(keys.sign(th)), pairCode?.let { LanCrypto.enc(LanCrypto.hmac(pairKey(it, th), "device".toByteArray() + th)) } ?: "")
        writeFrame(out, json.encodeToString(ClientFinish.serializer(), finish).toByteArray())
        val (c2s, s2c) = keys(LanCrypto.ecdh(eph.private, LanCrypto.dec(sh.ephPub)), hello, sh)
        val ch = SecureChannel(socket, c2s, s2c, sendDir = 1)
        ch.peerId = sh.hostId; ch.peerThumbprint = thumbprint(hostPub)
        val welcome = json.decodeFromString(Welcome.serializer(), String(ch.receive()))
        if (!welcome.ok) { ch.close(); error(welcome.reason) }
        return ClientSession(ch, sh, welcome)
    }

    /**
     * 主机应答。authorize 决定是否接受该设备：返回 null 表示接受（并给出 Welcome），否则为拒绝原因。
     * pairCode 回调：mode=pair 时提供当前有效配对码（无则拒绝）。
     */
    fun server(
        socket: Socket, hostId: String, hostName: String, keys: DeviceKeyStore,
        pairCode: () -> String?, onHello: (ClientHello) -> Unit = {}, authorize: (hello: ClientHello, paired: Boolean) -> Welcome,
    ): Pair<SecureChannel, ClientHello>? {
        socket.soTimeout = 10_000
        val inp = DataInputStream(socket.getInputStream().buffered()); val out = DataOutputStream(socket.getOutputStream().buffered())
        val hello = json.decodeFromString(ClientHello.serializer(), String(readFrame(inp)))
        require(hello.protocol == PROTOCOL) { "协议版本不符" }
        onHello(hello)
        val eph = LanCrypto.ephemeral()
        val base = ServerHello(PROTOCOL, hostId, hostName, LanCrypto.enc(keys.publicKey()), LanCrypto.enc(eph.public.encoded), LanCrypto.enc(LanCrypto.random(32)), "")
        val th = transcript(hello, base)
        val code = if (hello.mode == "pair") pairCode() else null
        val sh = base.copy(sig = LanCrypto.enc(keys.sign(th)), pairProof = code?.let { LanCrypto.enc(LanCrypto.hmac(pairKey(it, th), "host".toByteArray() + th)) } ?: "")
        writeFrame(out, json.encodeToString(ServerHello.serializer(), sh).toByteArray())
        val fin = json.decodeFromString(ClientFinish.serializer(), String(readFrame(inp)))
        val devicePub = LanCrypto.dec(hello.devicePub)
        val (c2s, s2c) = keys(LanCrypto.ecdh(eph.private, LanCrypto.dec(hello.ephPub)), hello, sh)
        val ch = SecureChannel(socket, s2c, c2s, sendDir = 2)
        ch.peerId = hello.deviceId; ch.peerThumbprint = thumbprint(devicePub)
        val sigOk = LanCrypto.verify(devicePub, th, LanCrypto.dec(fin.sig))
        val pairOk = hello.mode != "pair" || (code != null && LanCrypto.same(LanCrypto.hmac(pairKey(code, th), "device".toByteArray() + th), runCatching { LanCrypto.dec(fin.pairProof) }.getOrDefault(ByteArray(0))))
        val welcome = when {
            !sigOk -> Welcome(false, "设备签名无效")
            hello.mode == "pair" && code == null -> Welcome(false, "主机当前没有有效的配对码")
            !pairOk -> Welcome(false, "配对码不正确")
            else -> authorize(hello, hello.mode == "pair")
        }
        ch.send(json.encodeToString(Welcome.serializer(), welcome).toByteArray())
        if (!welcome.ok) { ch.close(); return null }
        return ch to hello
    }
}
