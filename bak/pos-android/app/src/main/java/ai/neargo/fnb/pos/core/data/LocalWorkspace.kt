package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.AdjustmentAudit
import ai.neargo.fnb.pos.core.model.AdjustmentKind
import ai.neargo.fnb.pos.core.model.AdjustmentRequest
import ai.neargo.fnb.pos.core.model.AdjustmentRules
import ai.neargo.fnb.pos.core.model.MealRules
import ai.neargo.fnb.pos.core.model.Account
import ai.neargo.fnb.pos.core.model.Area
import ai.neargo.fnb.pos.core.model.Category
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.ManageRules
import ai.neargo.fnb.pos.core.model.Menu
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.OrderRules
import ai.neargo.fnb.pos.core.model.OutboxItem
import ai.neargo.fnb.pos.core.model.OutboxKind
import ai.neargo.fnb.pos.core.model.OutboxState
import ai.neargo.fnb.pos.core.model.HeldTicket
import ai.neargo.fnb.pos.core.model.PrintJob
import ai.neargo.fnb.pos.core.model.PrintKind
import ai.neargo.fnb.pos.core.model.PrintState
import ai.neargo.fnb.pos.core.model.Receipts
import ai.neargo.fnb.pos.core.model.ShiftRecord
import ai.neargo.fnb.pos.core.model.ShiftRules
import ai.neargo.fnb.pos.core.model.ShiftState
import ai.neargo.fnb.pos.core.model.TableRules
import ai.neargo.fnb.pos.core.model.PaymentRecord
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.Session
import ai.neargo.fnb.pos.core.model.Sku
import ai.neargo.fnb.pos.core.model.SpecGroup
import ai.neargo.fnb.pos.core.model.SpecOption
import ai.neargo.fnb.pos.core.model.Reservation
import ai.neargo.fnb.pos.core.model.ReservationRules
import ai.neargo.fnb.pos.core.model.ReservationStatus
import ai.neargo.fnb.pos.core.model.StoreData
import ai.neargo.fnb.pos.core.model.StockMovement
import ai.neargo.fnb.pos.core.model.TaskLogKind
import ai.neargo.fnb.pos.core.model.TaskLogEntry
import ai.neargo.fnb.pos.core.model.TASK_LOG_LIMIT
import ai.neargo.fnb.pos.core.model.IdMapping
import ai.neargo.fnb.pos.core.model.Money
import ai.neargo.fnb.pos.core.model.*
import ai.neargo.fnb.pos.core.model.PrinterConfig
import ai.neargo.fnb.pos.core.identity.AuthContext
import ai.neargo.fnb.pos.core.identity.AuthState
import ai.neargo.fnb.pos.core.identity.AuthStatus
import ai.neargo.fnb.pos.core.identity.GrantKind
import ai.neargo.fnb.pos.core.identity.GrantPolicy
import ai.neargo.fnb.pos.core.identity.Grants
import ai.neargo.fnb.pos.core.identity.OfflineAuthority
import ai.neargo.fnb.pos.core.identity.thumbprint
import ai.neargo.fnb.pos.core.model.KitchenDelivery
import ai.neargo.fnb.pos.core.model.DeliveryState
import ai.neargo.fnb.pos.core.model.DeliveryRules
import ai.neargo.fnb.pos.core.model.PaymentState
import ai.neargo.fnb.pos.core.model.StoreSettings
import ai.neargo.fnb.pos.core.model.BizType
import ai.neargo.fnb.pos.core.model.StoreSummary
import ai.neargo.fnb.pos.core.model.TableOrder
import ai.neargo.fnb.pos.core.model.TableStatus
import ai.neargo.fnb.pos.core.model.yuan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.UUID

/** 本机全部数据：账号、会话、各门店快照。持久化为一份 JSON（schema v3，兼容 v2）。 */
@Serializable
data class Vault(
    val accounts: List<Account> = emptyList(),
    val user: String? = null,
    val storeId: String? = null,
    val stores: Map<String, StoreData>,
    val version: Int = 3,
    /** 旧单流 page-order-stream 的版本（只读水位，v2 信封后不再写入）。 */
    val commandRevisions: Map<String, Long> = emptyMap(),
    /** 本机设备ID：首次启动生成并持久保存；N4 前不是可信身份。 */
    val deviceId: String = "",
    val tenantId: String = "local-tenant",
    /** 协调权 epoch：受控切主时递增（N9）。 */
    val authorityEpoch: Long = 1,
    /** 每店各聚合的已提交版本（与命令日志 command_aggregates 同事务更新）。 */
    val aggregateRevisions: Map<String, Map<String, Long>> = emptyMap(),
    // —— N4 身份与离线授权（不含账号密码；授权为签名凭证原文） ——
    /** 数据绑定的设备公钥指纹：首次在本机落盘时绑定；与本机密钥不同即为复制来的数据。 */
    val deviceKeyThumbprint: String = "",
    val offlineGrant: String? = null,
    /** 本机已知撤销的授权 jti（联网时下发的拒绝清单；演示可本机撤销）。 */
    val revokedGrants: List<String> = emptyList(),
    /** 已知最大授权版本：低于此版本的授权视为被撤销，防止回退到旧凭证。 */
    val knownAuthEpoch: Long = 0,
    /** 本机记录过的最大系统时间，用于检测明显的时钟回拨。 */
    val lastSeenWall: Long = 0,
    val sessionExpiresAt: Long = 0,
    /** 授权相关操作记录（导入/签发/撤销/校时），不含凭证内容。 */
    val authLog: List<String> = emptyList(),
    /** 局域网主副 POS 状态（N5）。 */
    val lan: LanState = LanState(),
    /** 本机当班员工（员工模式下 PIN 上岗；换门店/退出登录清空）。 */
    val staffId: String = "",
    /** 厨房屏（KDS）：本机工位名（空=不是厨房屏）、持久收件箱与拉取游标。 */
    val kdsStation: String = "",
    val kdsInbox: List<KdsTicket> = emptyList(),
    val kdsCursor: Long = 0,
) {
    val store get() = storeId?.let { stores[it] }
}

/**
 * 快照存储：应用用 SQLite，测试用内存实现。
 * saveCommand 必须把快照与命令/事件原子提交；不支持的适配器须明确抛错，不得静默只存快照。
 */
interface SnapshotStore {
    suspend fun load(): String?
    suspend fun save(json: String)
    suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt
}

/** 局域网角色：单机、主 POS（唯一协调方）、副 POS（经主机仲裁，本机只保存只读副本）。 */
@Serializable enum class LanRole(val label: String) { Standalone("单机"), Host("主 POS"), Secondary("副 POS") }

/** 主机登记的副机。 */
@Serializable data class PairedDevice(
    val deviceId: String, val name: String, val publicKey: String, val thumbprint: String,
    val pairedAt: Long, val revokedAt: Long = 0, val lastSeen: Long = 0,
)

/** 副机待主机确认的意图：发送前保存，超时/重启后用同一命令ID重发。 */
@Serializable data class PendingIntent(
    val commandId: String, val storeId: String, val payload: String, val summary: String, val createdAt: Long,
    val attempts: Int = 0, val lastError: String = "", val nextAt: Long = 0,
)

@Serializable data class LanState(
    val role: LanRole = LanRole.Standalone,
    val port: Int = 47600,
    // 主机
    val paired: List<PairedDevice> = emptyList(),
    // 副机
    val hostAddress: String = "", val hostId: String = "", val hostName: String = "", val hostPublicKey: String = "",
    val sessionExpiresAt: Long = 0, val replicaToken: String = "", val replicaAt: Long = 0,
    val pending: List<PendingIntent> = emptyList(),
    val log: List<String> = emptyList(),
)

/** 主机执行副机意图时的命令来源：命令日志以副机设备/命令ID/操作人记录，用于同事务去重。 */
class RemoteOrigin(val deviceId: String, val commandId: String, val actorId: String) :
    kotlin.coroutines.AbstractCoroutineContextElement(RemoteOrigin) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<RemoteOrigin>
}

/** 命令被协调方判为冲突或拒绝（快照未写入）。 */
class WorkspaceCommandException(val receipt: JournalReceipt) : Exception(receipt.result)

/**
 * 完整离线的本机仓库：同一份 Vault 同时实现会话 / 门店配置 / 桌台 / 订单 / 同步队列接口，
 * 保证开台、结账、补发等跨实体操作原子完成；每次修改先串行落盘再发布状态，进程重启后恢复。
 * 阶段三接服务时各接口分别对接远端，界面与 ViewModel 不变。
 */
private const val DEMO_USER = "体验账号"

class LocalWorkspace(
    private val store: SnapshotStore,
    private val scope: CoroutineScope,
    /** 身份与授权；为空时（单元测试）不做授权限制。 */
    private val identity: IdentityContext? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : SessionRepository, StoreConfigRepository, FloorRepository, OrderRepository, SyncRepository, PrintRepository, ShiftRepository, AuthRepository, OperationsRepository, StaffRepository, KdsRepository, RetailRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // 设备ID在首次创建时生成，随第一次保存落盘；不能等到下次启动再补，否则协调方登记会变化
    private val state = MutableStateFlow(Vault(stores = DemoCatalog.stores(clock()).mapValues { assignOrderIds(ReservationRules.importLegacy(it.value, clock()), "demo") }, deviceId = "dev-" + UUID.randomUUID()))
    private val writeLock = Mutex()

    private val thumb: String by lazy { identity?.let { thumbprint(it.keys.publicKey()) } ?: "" }
    private val authState = MutableStateFlow(AuthStatus(AuthState.Demo))
    override val auth: StateFlow<AuthStatus> = authState
    override val devIssuerAvailable: Boolean get() = identity?.devIssuer != null

    private fun evaluate(v: Vault): AuthStatus = if (identity == null) AuthStatus(AuthState.Demo) else OfflineAuthority.evaluate(
        AuthContext(v.user, v.user == "体验账号", v.storeId, v.offlineGrant, v.revokedGrants.toSet(), v.knownAuthEpoch, v.lastSeenWall, v.sessionExpiresAt, v.deviceKeyThumbprint),
        clock(), thumb, identity.anchors(),
    )

    /** 时间流逝后重新评估（到期、会话过期）；由容器定时调用。 */
    override suspend fun refreshAuth() { authState.value = evaluate(state.value) }

    private val blockedState = MutableStateFlow<String?>(null)
    /** 启动恢复失败原因；非空时拒绝所有写入，避免用演示数据覆盖原库。 */
    val blocked: StateFlow<String?> = blockedState
    fun deviceId() = state.value.deviceId

    /** 读取并校验快照；只有无快照才使用演示数据，损坏或未知版本报错、进入只读阻断并保留原件。 */
    suspend fun restore() {
        try { restoreSnapshot(); blockedState.value = null }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { blockedState.value = "本地数据无法读取：${e.message ?: e.javaClass.simpleName}"; throw e }
    }

    /** 恢复备份后重新加载。 */
    suspend fun reload() = restore()

    private suspend fun restoreSnapshot() {
        val saved = store.load() ?: return
        writeLock.withLock {
            val loaded = VaultCodec.decode(saved)
            val demo = DemoCatalog.stores(clock())
            val migrated = if (loaded.version == 2) loaded.copy(version = 3, stores = loaded.stores.mapValues { (id, data) ->
                val seed = demo[id]?.products.orEmpty().associateBy { it.id }
                data.copy(products = data.products.map { p ->
                    val original = seed[p.id]?.takeIf { it.name == p.name }
                    p.copy(searchAliases = p.searchAliases.ifBlank { original?.searchAliases.orEmpty() }, mealGroups = p.mealGroups.ifEmpty { original?.mealGroups.orEmpty() })
                })
            }) else loaded
            // 零售演示门店：旧数据里没有就补上（只补这一家，用户删掉的其他门店不恢复）
            // R0 时补进来的是空的零售门店（没有商品和零售数据）：换成完整的演示目录
            // 只给体验账号补：真实账号的门店以账号为准，不往里塞演示门店
            val demoUser = migrated.user == "体验账号"
            val emptyMart = migrated.stores[DemoCatalog.RETAIL_STORE_ID]?.let { it.biz == BizType.Retail && it.products.isEmpty() && it.retail == RetailData() } == true
            val withRetail = if (demoUser && (DemoCatalog.RETAIL_STORE_ID !in migrated.stores || emptyMart) && demo.containsKey(DemoCatalog.RETAIL_STORE_ID)) migrated.copy(stores = migrated.stores + (DemoCatalog.RETAIL_STORE_ID to demo.getValue(DemoCatalog.RETAIL_STORE_ID))) else migrated
            // 第二家零售演示门店（调拨用）：旧数据里没有就补上
            val withRetail2 = if (demoUser && DemoCatalog.RETAIL_STORE2_ID !in withRetail.stores && demo.containsKey(DemoCatalog.RETAIL_STORE2_ID)) withRetail.copy(stores = withRetail.stores + (DemoCatalog.RETAIL_STORE2_ID to demo.getValue(DemoCatalog.RETAIL_STORE2_ID))) else withRetail
            val restored = withRetail2.copy(
                deviceId = withRetail2.deviceId.ifBlank { state.value.deviceId },
                // 旧数据（N4 前）首次由本机加载时绑定本机密钥
                deviceKeyThumbprint = withRetail2.deviceKeyThumbprint.ifBlank { thumb },
                stores = withRetail2.stores.mapValues { upgradeStates(assignOrderIds(ReservationRules.importLegacy(it.value, clock()), "legacy-v3")) },
            )
            withContext(NonCancellable) {
                store.save(json.encodeToString(Vault.serializer(), restored))
                state.value = restored
                authState.value = evaluate(restored)
            }
        }
    }

    /**
     * 为没有订单ID的活动订单补发稳定ID并记录映射；已结账历史（收款无 orderId）保持关联未知，不按桌号推断。
     */
    private fun assignOrderIds(s: StoreData, source: String): StoreData {
        val maps = mutableListOf<IdMapping>()
        val tables = s.tables.map { t ->
            val o = s.orders[t.id]
            val active = t.status == TableStatus.Dining || (o != null && o.lines.isNotEmpty())
            if (!active || (t.sessionId.isNotBlank() && !o?.orderId.isNullOrBlank())) t
            else {
                val m = IdMapping(t.id, o?.orderId?.ifBlank { null } ?: newId("O"), t.sessionId.ifBlank { newId("TS") }, clock(), source)
                maps += m; t.copy(sessionId = m.sessionId)
            }
        }
        if (maps.isEmpty()) return s
        val orders = s.orders + maps.associate { m -> m.tableId to (s.orders[m.tableId] ?: TableOrder()).copy(orderId = m.orderId) }
        return s.copy(tables = tables, orders = orders, idMappings = s.idMappings + maps)
    }

    /**
     * N3 状态拆分升级：旧「已同步」标 legacy（非云端回执）；旧厨房任务补送达记录（legacy，历史待核实）；
     * 旧版离线批次曾等联网才出厨房单，未出单的现在补出，避免升级后永不出单。
     */
    private fun upgradeStates(s0: StoreData): StoreData {
        var s = ensureDeliveries(s0.copy(outbox = s0.outbox.map { if (it.state == OutboxState.Done && it.ackSource.isBlank()) it.copy(ackSource = "legacy") else it }), "legacy")
        // 重启时仍在发送中：结果未知，须人工核对，不自动重打
        s.printJobs.filter { it.state == PrintState.Sending }.forEach { j -> s = s.withJob(j.id) { it.copy(state = PrintState.Unknown, error = "应用重启时正在发送，结果未知") } }
        val printed = s.printJobs.map { it.id }.toSet()
        s.orders.forEach { (table, o) ->
            o.lines.filter { it.status == LineStatus.Queued }.mapNotNull { it.batchId }.distinct().filter { b -> printed.none { it == "P-$b" || it.startsWith("P-$b@") } }.forEach { b -> s = s.kitchenJob(table, b) }
        }
        return ensureDeliveries(s, "simulation")
    }

    /** 重置当前门店为演示初始数据（账号与登录状态保留）。 */
    suspend fun resetDemo() = commit { v ->
        val fresh = DemoCatalog.stores(clock()).mapValues { assignOrderIds(ReservationRules.importLegacy(it.value, clock()), "demo") }
        v.copy(stores = v.stores + (v.storeId?.let { id -> fresh[id]?.let { mapOf(id to it) } } ?: fresh))
    }

    private fun <T> derive(f: (Vault) -> T): StateFlow<T> = state.map(f).stateIn(scope, SharingStarted.Eagerly, f(state.value))

    override val session: StateFlow<Session> = derive { v ->
        val s = v.store
        Session(v.user, s?.id, s?.settings?.shortName ?: "未选择门店",
            // 没设员工：演示门店显示演示收银员，真实门店提示去设员工
            staffOf(v)?.let { "${it.name} · ${it.role.label}" } ?: if (s?.employees.isNullOrEmpty()) (if (s?.owner.isNullOrEmpty()) DemoCatalog.STAFF else "未设员工") else "未上岗", s?.currentTableId ?: "", s?.biz ?: BizType.Dining)
    }
    override val stores: StateFlow<List<StoreSummary>> = derive { v ->
        v.stores.values.filter { visible(v, it) }.map { s -> StoreSummary(s.id, s.settings.shortName, s.code, s.address, s.tables.size, s.tables.count { it.status == TableStatus.Dining }, s.biz) }
    }
    override val current: StateFlow<StoreData?> = derive { it.store }
    // 桌台、订单流由餐饮服务提供
    override val tables: StateFlow<List<DiningTable>> get() = dining.tables
    override val orders: StateFlow<Map<String, TableOrder>> get() = dining.orders
    override val outbox: StateFlow<List<OutboxItem>> = derive { it.store?.outbox.orEmpty().sortedByDescending { o -> o.createdAt } }
    override val jobs: StateFlow<List<PrintJob>> = derive { it.store?.printJobs.orEmpty().sortedByDescending { j -> j.createdAt } }
    override val pendingCount: StateFlow<Int> = derive { v -> v.stores.values.sumOf { s -> s.outbox.count { it.state == OutboxState.Pending } } }

    /** 校验和修改均在锁内执行；落盘完成才发布状态及返回成功。 */
    private suspend fun <T> transaction(event: EventSpec? = null, f: (Vault) -> Pair<Vault, T>): T = writeLock.withLock {
        blockedState.value?.let { throw WorkspaceWriteException(IllegalStateException(it)) }
        // 营业写入（带事件）须有效授权；授权失效时保留账本，只拒绝新写入
        val status = evaluate(state.value)
        if (event != null && state.value.lan.role == LanRole.Secondary) throw WorkspaceRejectedException("副 POS 不直接写入业务数据，请经主 POS 执行")
        if (event != null && !status.writable) { authState.value = status; throw WorkspaceRejectedException(status.blockMessage) }
        val origin = kotlinx.coroutines.currentCoroutineContext()[RemoteOrigin]
        val (candidate, result) = f(state.value)
        // 每张厨房票据都有送达记录（送厨、退菜、补打）；纯计算，无新增时不改变对象
        var next = candidate.storeId?.let { id -> candidate.stores[id]?.let { s0 -> ensureDeliveries(s0, "simulation").takeIf { it !== s0 }?.let { candidate.copy(stores = candidate.stores + (id to it)) } } } ?: candidate
        if (next != state.value) withContext(NonCancellable) {
            next = next.copy(lastSeenWall = maxOf(next.lastSeenWall, clock()), deviceKeyThumbprint = next.deviceKeyThumbprint.ifBlank { thumb })
            if (event != null) {
                val storeId = requireNotNull(next.storeId)
                val (type, payload, aggregates) = event.resolve(state.value.stores.getValue(storeId), next.stores.getValue(storeId))
                // 员工模式：按事件类型校验权限（远端命令按副机当班员工）；无权限不落盘
                permit(state.value, StaffRules.permFor(type), origin)?.let { throw WorkspaceRejectedException(it) }
                val known = state.value.aggregateRevisions[storeId].orEmpty()
                val versions = aggregates.distinct().map { AggregateVersion(it, known[it] ?: 0L) }
                next = next.copy(aggregateRevisions = next.aggregateRevisions + (storeId to known + versions.associate { it.id to it.expected + 1 }))
                val commandId = origin?.commandId ?: UUID.randomUUID().toString()
                val command = JournalCommand(
                    storeId = storeId, deviceId = origin?.deviceId ?: state.value.deviceId, commandId = commandId, type = type, payload = payload,
                    aggregates = versions, tenantId = state.value.tenantId, actorId = origin?.actorId ?: actorIdOf(state.value),
                    coordinatorId = state.value.deviceId, authorityEpoch = state.value.authorityEpoch, occurredAt = clock(),
                    authorizationRef = status.grant?.jti, policyVersion = status.grant?.policyVersion,
                )
                val receipt = try { store.saveCommand(json.encodeToString(Vault.serializer(), next), command) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { throw WorkspaceWriteException(e) }
                if (receipt.status != ReceiptStatus.Committed) throw WorkspaceWriteException(WorkspaceCommandException(receipt))
            } else saveSnapshot(store, json.encodeToString(Vault.serializer(), next))
            state.value = next
            authState.value = evaluate(next)
        }
        result
    }

    private suspend fun commit(event: EventSpec? = null, f: (Vault) -> Vault): Vault = transaction(event) { v ->
        val next = f(v)
        next to next
    }

    /** 事件规格：在锁内用提交前后的门店数据决定事件类型与载荷。 */
    /** resolve 返回（事件类型，载荷，参与聚合）；参与聚合在同一事务内全部校验版本。 */
    internal class EventSpec(val resolve: (before: StoreData, after: StoreData) -> Triple<String, String, List<String>>)

    /** 页面订单/桌台事件（兼容载荷 v1）；聚合按受影响的订单、桌台、预订划分。 */
    private fun page(type: String, tableId: String) = EventSpec { b, a ->
        Triple(type, BusinessEventFactory.encode(BusinessEventFactory.build(type, tableId, b, a)), Aggregates.page(tableId, b, a))
    }

    /** 门店配置事件：聚合为门店目录。 */
    private fun config(type: String, entity: String, id: () -> String) = EventSpec { b, a -> Triple(type, ManagementEventFactory.config(entity, id(), b, a), listOf(Aggregates.CATALOG)) }

    private fun StoreData.log(kind: TaskLogKind, refId: String, result: String, source: String, detail: String = "") =
        copy(taskLog = (taskLog + TaskLogEntry(newId("T"), kind, refId, result, source, clock(), detail)).takeLast(TASK_LOG_LIMIT))

    private fun fail(msg: String) = Result.failure<Unit>(IllegalStateException(msg))
    private fun currentStore(): StoreData? = state.value.store

    /** 修改当前门店；校验与业务变更基于同一已提交版本。 */
    private suspend fun storeOp(event: EventSpec? = null, check: (StoreData) -> String? = { null }, f: (StoreData) -> StoreData): Result<Unit> = persistenceResult {
        transaction(event) { v ->
            val s = v.store ?: return@transaction v to fail("请先选择门店")
            check(s)?.let { return@transaction v to fail(it) }
            v.copy(stores = v.stores + (s.id to f(s))) to Result.success(Unit)
        }
    }

    private fun newId(prefix: String) = "$prefix-${UUID.randomUUID().toString()}"

    // —— 会话 ——
    override suspend fun login(email: String, password: String): Result<Unit> {
        ManageRules.email(email)?.let { return fail(it) }
        ManageRules.password(password)?.let { return fail(it) }
        val acc = state.value.accounts.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) } ?: return fail("该邮箱未在本机注册，请先注册或直接体验")
        if (ManageRules.hash(password, acc.salt) != acc.hash) return fail("密码不正确")
        return persistenceResult {
            commit { it.copy(user = acc.email, storeId = null, staffId = "", sessionExpiresAt = sessionEnd(it)) }
            Result.success(Unit)
        }
    }

    override suspend fun register(merchant: String, email: String, password: String, confirm: String): Result<Unit> {
        ManageRules.required(merchant, "商户名称")?.let { return fail(it) }
        ManageRules.email(email)?.let { return fail(it) }
        ManageRules.password(password)?.let { return fail(it) }
        if (password != confirm) return fail("两次输入的密码不一致")
        if (state.value.accounts.any { it.email.equals(email.trim(), ignoreCase = true) }) return fail("该邮箱已注册，请直接登录")
        val salt = UUID.randomUUID().toString()
        val acc = Account(email.trim(), merchant.trim(), salt, ManageRules.hash(password, salt))
        return persistenceResult {
            transaction { v ->
                if (v.accounts.any { it.email.equals(acc.email, true) }) v to fail("该邮箱已注册，请直接登录")
                else v.copy(accounts = v.accounts + acc, user = acc.email, storeId = null, sessionExpiresAt = sessionEnd(v)) to Result.success(Unit)
            }
        }
    }

    override suspend fun enterDemo() { commit { it.copy(user = "体验账号", storeId = null, staffId = "", sessionExpiresAt = sessionEnd(it)) } }
    override suspend fun logout() { commit { it.copy(user = null, storeId = null, staffId = "", sessionExpiresAt = 0) } }

    /** 本机会话截止：最长 12 小时，且不超过当前有效授权的到期时间（会话不能延长父授权）。 */
    private fun sessionEnd(v: Vault): Long {
        val st = evaluate(v)
        return OfflineAuthority.sessionExpiry(clock(), st.grant?.takeIf { st.state == AuthState.Active }?.exp)
    }

    // —— 厨房屏：实现在 KdsService（只依赖公共内核），这里只委托 ——
    private val kds by lazy { KdsService(kernel) }
    override suspend fun kdsAdvance(ticketId: String, expectedRev: Int, to: KdsState) = kds.kdsAdvance(ticketId, expectedRev, to)
    override suspend fun kdsRelease(ticketId: String, expectedRev: Int) = kds.kdsRelease(ticketId, expectedRev)
    override suspend fun kdsReassign(ticketId: String, deviceId: String?) = kds.kdsReassign(ticketId, deviceId)
    override suspend fun saveDispatchModes(modes: Map<String, KdsDispatch>) = kds.saveDispatchModes(modes)
    override suspend fun saveDeliveryModes(modes: Map<String, DeliveryMode>) = kds.saveDeliveryModes(modes)
    override suspend fun saveKdsStations(stations: List<String>, mapping: Map<String, String>) = kds.saveKdsStations(stations, mapping)
    override suspend fun printForScreen(ticketId: String) = kds.printForScreen(ticketId)
    fun kdsPull(storeId: String, cursor: Long, station: String) = kds.kdsPull(storeId, cursor, station)
    suspend fun kdsAck(acks: List<Pair<String, Int>>) = kds.kdsAck(acks)
    suspend fun expireScreenDeliveries() = kds.expireScreenDeliveries()
    val kdsDevice: StateFlow<Triple<String, List<KdsTicket>, Long>> get() = kds.kdsDevice
    suspend fun setKdsStation(station: String) = kds.setKdsStation(station)
    suspend fun kdsReceive(tickets: List<KdsTicket>, cursor: Long) = kds.kdsReceive(tickets, cursor)

    // —— 员工与权限 ——
    private fun staffOf(v: Vault): Employee? = v.store?.employees?.firstOrNull { it.id == v.staffId && it.enabled }
    /** 审计用操作人名称：当班员工，否则门店账号。 */
    private fun actorName(v: Vault) = staffOf(v)?.name ?: v.user ?: DemoCatalog.STAFF
    private fun actorIdOf(v: Vault) = staffOf(v)?.id ?: v.user ?: DemoCatalog.STAFF
    /** 副机发送意图时使用的操作人ID。 */
    fun actorId() = actorIdOf(state.value)

    /** 权限：未启用员工模式（无员工）时门店账号按店长处理；远端命令按副机当班员工校验。 */
    private fun permit(v: Vault, perm: Perm?, origin: RemoteOrigin? = null): String? {
        val s = v.store ?: return null
        if (perm == null || s.employees.isEmpty()) return null
        val e = if (origin != null) s.employees.firstOrNull { it.id == origin.actorId } else staffOf(v)
        return when {
            e == null -> if (origin != null) "副机当班员工未上岗或不存在" else "请先用员工 PIN 上岗"
            !e.enabled -> "员工 ${e.name} 已停用"
            perm !in Roles.perms(e.role) -> "${e.name}（${e.role.label}）没有「${perm.label}」权限"
            else -> null
        }
    }
    private fun need(perm: Perm) = permit(state.value, perm)

    override val staff: StateFlow<StaffState> = derive { v ->
        val s = v.store
        StaffState(s?.employees.orEmpty(), staffOf(v), required = !s?.employees.isNullOrEmpty(),
            perms = if (s == null || s.employees.isEmpty()) Perm.entries.toSet() else staffOf(v)?.let { Roles.perms(it.role) } ?: emptySet())
    }

    private val pinFailures = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Long>>()

    /** PIN 上岗：连续错 5 次锁定 1 分钟（本机内存）。 */
    override suspend fun signInStaff(employeeId: String, pin: String): Result<Unit> = persistenceResult {
        transaction { v ->
            val s = v.store ?: return@transaction v to fail("请先选择门店")
            val e = s.employees.firstOrNull { it.id == employeeId } ?: return@transaction v to fail("员工不存在")
            val (n, until) = pinFailures[employeeId] ?: (0 to 0L)
            if (until > clock()) return@transaction v to fail("PIN 错误次数过多，请 ${(until - clock()) / 1000 + 1} 秒后再试")
            if (!e.enabled) return@transaction v to fail("员工已停用")
            if (ManageRules.hash(pin, e.salt) != e.pinHash) {
                pinFailures[employeeId] = (n + 1).let { c -> c to if (c >= 5) clock() + 60_000 else 0L }
                return@transaction v to fail("PIN 不正确")
            }
            pinFailures.remove(employeeId)
            v.copy(staffId = e.id) to Result.success(Unit)
        }
    }

    override suspend fun signOutStaff() { commit { it.copy(staffId = "") } }

    /**
     * 新建/编辑员工（Staff 权限）。首个员工必须为店长（创建后当前设备自动以其上岗）；至少保留一名启用的店长。
     * 事件载荷不含 PIN 盐与哈希。
     */
    override suspend fun saveEmployee(id: String?, name: String, role: Role, pin: String?, enabled: Boolean): Result<Unit> {
        val eid = id ?: newId("E")
        return storeOp(event = EventSpec { _, a ->
            val e = a.employees.first { it.id == eid }
            Triple("EmployeeSaved", """{"id":"${e.id}","name":${kotlinx.serialization.json.JsonPrimitive(e.name)},"role":"${e.role}","enabled":${e.enabled}}""", listOf("staff"))
        }, check = { s ->
            val old = s.employees.firstOrNull { it.id == eid }
            val after = s.employees.filterNot { it.id == eid } + Employee(eid, name.trim(), role, "", "", enabled)
            when {
                id != null && old == null -> "员工不存在"
                name.isBlank() || name.trim().length > 12 -> "姓名 1–12 字"
                s.employees.any { it.id != eid && it.name == name.trim() } -> "员工姓名重复"
                old == null && pin == null -> "请设置 PIN"
                pin != null && StaffRules.pin(pin) != null -> StaffRules.pin(pin)
                s.employees.isEmpty() && role != Role.Owner -> "第一位员工必须是店长"
                after.none { it.role == Role.Owner && it.enabled } -> "至少保留一名启用的店长"
                else -> need(Perm.Staff)
            }
        }) { s ->
            val old = s.employees.firstOrNull { it.id == eid }
            val salt = if (pin != null) UUID.randomUUID().toString() else old!!.salt
            val e = Employee(eid, name.trim(), role, salt, if (pin != null) ManageRules.hash(pin, salt) else old!!.pinHash, enabled, old?.createdAt ?: clock())
            s.copy(employees = s.employees.filterNot { it.id == eid } + e)
        }.also { r -> if (r.isSuccess && state.value.store?.employees?.size == 1 && id == null) commit { it.copy(staffId = eid) } }
    }

    // —— 局域网（N5）：设备管理与副本，均为本机记录，不产生营业事件 ——
    val lanState: StateFlow<LanState> = derive { it.lan }
    fun lanSnapshot(): Vault = state.value
    fun tenantId() = state.value.tenantId
    fun currentUser() = state.value.user

    private fun lanLogged(l: LanState, line: String) = l.copy(log = (l.log + "${clock()} $line").takeLast(200))

    suspend fun updateLan(note: String? = null, f: (LanState) -> LanState) {
        commit { v -> val n = f(v.lan); if (n == v.lan) v else v.copy(lan = if (note != null) lanLogged(n, note) else n) }
    }

    /** 副机：用主机快照替换本店只读副本，保留本机的选桌状态。 */
    suspend fun applyReplica(store: StoreData, token: String) {
        commit { v ->
            if (v.lan.role != LanRole.Secondary) return@commit v
            val keep = v.stores[store.id]?.currentTableId?.takeIf { id -> store.tables.any { it.id == id } } ?: store.currentTableId
            v.copy(stores = v.stores + (store.id to store.copy(currentTableId = keep)), lan = v.lan.copy(replicaToken = token, replicaAt = clock()))
        }
    }

    // —— 日常运营（会员 / 优惠 / 日结） ——
    override suspend fun createMember(phone: String, name: String): Result<Member> = persistenceResult {
        val id = newId("M")
        transaction(EventSpec { _, a -> Triple("MemberCreated", OpsEvents.member(a.members.first { it.id == id }), listOf("member:$id")) }) { v ->
            val s = v.store ?: return@transaction v to Result.failure(IllegalStateException("请先选择门店"))
            val p = phone.trim(); val n = name.trim()
            val err = when {
                !Regex("^1\\d{10}$").matches(p) -> "请填写 11 位手机号"
                n.length > 20 -> "姓名最多 20 字"
                s.members.any { it.phone == p } -> "该手机号已是会员"
                else -> null
            }
            if (err != null) return@transaction v to Result.failure(IllegalStateException(err))
            val m = Member(id, p, n.ifBlank { "会员${p.takeLast(4)}" }, createdAt = clock())
            v.copy(stores = v.stores + (s.id to s.copy(members = s.members + m))) to Result.success(m)
        }
    }

    override suspend fun recharge(memberId: String, amount: Long, method: String): Result<Unit> {
        val tx = newId("MT")
        return storeOp(event = EventSpec { _, a -> Triple("MemberRecharged", OpsEvents.member(a.members.first { it.id == memberId }, a.memberTxns.first { it.id == tx }), listOf("member:$memberId")) }, check = { s ->
            when {
                s.members.none { it.id == memberId } -> "会员不存在"
                amount <= 0 || amount > 5_000_000 -> "充值金额应为 0.01–50000 元"
                method !in listOf("现金", "扫码", "银行卡") -> "请选择充值方式"
                method !in s.settings.payMethods -> "门店未启用「$method」"
                else -> null
            }
        }) { s ->
            val m = s.members.first { it.id == memberId }
            val m2 = m.copy(balance = Math.addExact(m.balance, amount))
            s.copy(members = s.members.map { if (it.id == memberId) m2 else it },
                memberTxns = s.memberTxns + MemberTxn(tx, memberId, MemberTxnKind.Recharge, amount, m2.balance, clock(), actor = actorName(state.value), method = method))
        }
    }

    /** 本单绑定/解绑会员（memberId=null 解绑）；会员折扣按订单快照费率。 */
    override suspend fun bindMember(tableId: String, memberId: String?) = storeOp(event = page("OrderMemberChanged", tableId), check = { s ->
        when {
            s.table(tableId)?.status != TableStatus.Dining -> "$tableId 未在用餐"
            memberId != null && s.members.none { it.id == memberId } -> "会员不存在"
            else -> null
        }
    }) { s ->
        val m = memberId?.let { id -> s.members.first { it.id == id } }
        s.withOrder(tableId, s.order(tableId).copy(member = m != null, memberId = m?.id.orEmpty(), memberName = m?.name.orEmpty()))
    }

    /** 本单选用活动：校验启用与门槛，保存活动快照（修改配置不影响已选订单）。 */
    override suspend fun setPromotions(tableId: String, ids: List<String>) = storeOp(event = page("OrderBenefitsChanged", tableId), check = { s ->
        val chosen = ids.distinct().map { id -> s.promotions.firstOrNull { it.id == id } ?: return@storeOp "活动不存在" }
        if (s.table(tableId)?.status != TableStatus.Dining) "$tableId 未在用餐" else OrderRules.promotionBlock(s.order(tableId), chosen)
    }) { s ->
        val chosen = ids.distinct().map { id -> s.promotions.first { it.id == id } }
        s.withOrder(tableId, s.order(tableId).copy(promotions = chosen, coupon = false, activity = false))
    }

    override suspend fun savePromotion(p: Promotion) = storeOp(event = config("PromotionSaved", "promotion") { p.id }, check = { s ->
        when {
            p.name.isBlank() || p.name.length > 20 -> "活动名称 1–20 字"
            s.promotions.any { it.id != p.id && it.name == p.name.trim() } -> "活动名称重复"
            p.threshold < 0 || p.threshold > Money.MAX_BILL -> "门槛金额无效"
            p.kind == PromotionKind.Threshold && (p.amount <= 0 || p.amount > p.threshold) -> "减额应大于 0 且不超过门槛"
            p.kind == PromotionKind.Percent && p.percent !in 50..99 -> "折扣应为 5–9.9 折"
            else -> null
        }
    }) { s -> val q = p.copy(name = p.name.trim()); s.copy(promotions = s.promotions.filterNot { it.id == q.id } + q) }

    /** 日结：汇总当前营业日，生成日结单并开启新营业日。 */
    override suspend fun closeDay(staff: String): Result<DayClose> = persistenceResult {
        transaction(EventSpec { b, a -> Triple("DayClosed", OpsEvents.day(a.dayCloses.last()), listOf("day")) }) { v ->
            val s = v.store ?: return@transaction v to Result.failure(IllegalStateException("请先选择门店"))
            ReportRules.closeBlock(s)?.let { return@transaction v to Result.failure(IllegalStateException(it)) }
            val now = clock(); val start = ReportRules.dayStart(s).takeIf { it > 0 } ?: now
            val r = ReportRules.build(s, start, now + 1)
            val d = DayClose(newId("D"), start, now, staff, r.revenue, r.revenue, r.orders, r.guests, r.discount, r.byMethod, r.voidAmount, r.giftAmount, r.recharge, r.newMembers)
            val job = PrintJob(newId("DC"), PrintKind.Shift, "日结单 ${s.settings.shortName}", ReportRules.dayCloseReceipt(s.settings.shortName, d), now)
            // 新营业日从日结时刻的下一毫秒开始：与已日结区间 [start, now] 不重叠，避免同一毫秒的收款被重复统计
            v.copy(stores = v.stores + (s.id to s.copy(dayStart = now + 1, dayCloses = s.dayCloses + d, printJobs = s.printJobs + s.withCopies(job)))) to Result.success(d)
        }
    }

    // —— 授权中心 ——
    private fun authLogged(v: Vault, line: String) = v.copy(authLog = (v.authLog + "${clock()} ${v.user ?: "-"} $line").takeLast(200))

    /** 导入激活包/授权：验签、设备绑定、版本不回退、未过期；成功后替换当前授权。 */
    override suspend fun importGrant(token: String): Result<Unit> = persistenceResult {
        val id = identity ?: return@persistenceResult fail("本机未启用离线授权")
        transaction { v ->
            val (g, _) = Grants.verify(token, id.anchors()).getOrElse { return@transaction v to fail(it.message ?: "授权无效") }
            val now = clock()
            val reason = when {
                g.deviceKeyThumbprint != thumb -> "该授权属于其他设备，不能在本机使用"
                g.jti in v.revokedGrants -> "该授权已被撤销"
                g.authEpoch < v.knownAuthEpoch -> "授权版本低于本机已知版本，不能回退"
                now + GrantPolicy.CLOCK_TOLERANCE_MS < g.iat -> "设备时间早于授权签发时间，请先校时"
                now > g.exp -> "该授权已过期"
                else -> null
            }
            if (reason != null) return@transaction v to fail(reason)
            authLogged(v.copy(offlineGrant = token.trim(), knownAuthEpoch = maxOf(v.knownAuthEpoch, g.authEpoch),
                sessionExpiresAt = if (v.user != null) minOf(maxOf(v.sessionExpiresAt, 0), g.exp).takeIf { it > 0 } ?: OfflineAuthority.sessionExpiry(now, g.exp) else 0),
                "导入授权 ${g.kind.label} 至 ${g.exp}") to Result.success(Unit)
        }
    }

    /** 调试版：开发测试签发器为本机签发（模拟云端/激活服务）。发行版不可用。 */
    override suspend fun devIssue(kind: GrantKind): Result<Unit> {
        val id = identity ?: return fail("本机未启用离线授权")
        val issuer = id.devIssuer ?: return fail("发行版不提供开发签发，请导入正式激活包")
        val v = state.value
        // 授权只覆盖当前账号能看到的门店（真实商户自己的门店，不带演示门店）
        val token = issuer.issue(thumb, v.deviceId, v.tenantId, v.stores.values.filter { visible(v, it) }.map { it.id }, kind, clock(), authEpoch = maxOf(1, v.knownAuthEpoch))
        return importGrant(token)
    }

    /** 撤销当前授权（模拟收到云端撤销清单）：立即生效，账本保留。 */
    override suspend fun revokeCurrent(): Result<Unit> = persistenceResult {
        transaction { v ->
            val g = v.offlineGrant?.let { Grants.peek(it) } ?: return@transaction v to fail("本机没有授权")
            authLogged(v.copy(revokedGrants = (v.revokedGrants + g.jti).distinct()), "撤销授权 ${g.jti}") to Result.success(Unit)
        }
    }

    /** 时钟异常：主管确认当前时间正确后重置本机时间锚点（记录人员）。 */
    override suspend fun confirmClock(email: String, password: String): Result<Unit> = persistenceResult {
        transaction { v ->
            val acc = v.accounts.firstOrNull { it.supervisor && it.email.equals(email.trim(), true) }
            val demo = v.user == "体验账号" && email.trim() == "manager@demo.local" && password == "demo12345"
            if (!demo && (acc == null || ManageRules.hash(password, acc.salt) != acc.hash)) return@transaction v to fail("主管账号或密码不正确")
            authLogged(v.copy(lastSeenWall = clock()), "主管 ${email.trim()} 确认校时") to Result.success(Unit)
        }
    }
    override suspend fun selectStore(storeId: String) { commit { v -> val st = v.stores[storeId]; if (st != null && visible(v, st)) v.copy(storeId = storeId, staffId = if (storeId == v.storeId) v.staffId else "") else v } }

    /**
     * 门店可见范围：体验账号只看演示门店；真实账号建了自己的门店后只看自己的，还没建的先看演示门店（试用）。
     */
    private fun visible(v: Vault, s: StoreData): Boolean {
        val u = v.user
        if (u == null || u == DEMO_USER) return s.owner.isEmpty()
        return if (v.stores.values.any { it.owner == u }) s.owner == u else s.owner.isEmpty()
    }

    override suspend fun createStore(name: String, code: String, address: String, biz: BizType): Result<String> = persistenceResult {
        transaction { v ->
            val u = v.user
            val n = name.trim(); val c = code.trim().uppercase(); val a = address.trim()
            val why = when {
                u == null -> "请先登录"
                u == DEMO_USER -> "体验账号不能新建门店，请注册商户"
                v.lan.role == LanRole.Secondary -> "副 POS 不能新建门店，请在主 POS 上建"
                n.isEmpty() || n.length > 20 -> "门店名称 1–20 字"
                !Regex("^[A-Z0-9-]{2,12}$").matches(c) -> "门店码 2–12 位字母、数字或短横线"
                v.stores.values.any { it.code.equals(c, true) } -> "门店码 $c 已被使用"
                a.length > 60 -> "地址最多 60 字"
                else -> null
            }
            if (why != null) return@transaction v to Result.failure(IllegalStateException(why))
            val st = StoreTemplates.fresh(DemoCatalog.stores(clock()), biz, newId("s"), n, c, a, u!!)
            v.copy(stores = v.stores + (st.id to st), storeId = st.id, staffId = "") to Result.success(st.id)
        }
    }
    override suspend fun selectTable(tableId: String) { storeOp { it.copy(currentTableId = tableId) }.getOrThrow() }

    // —— 门店配置 ——
    override suspend fun addArea(name: String, en: String): Result<Unit> {
        val id = newId("area")
        return storeOp(event = config("AreaCreated", "area") { id },
        check = { s -> ManageRules.required(name, "区域名称") ?: ManageRules.unique(name, s.areas.map { it.name }, "区域") },
    ) { s -> s.copy(areas = s.areas + Area(id, name.trim(), en.trim())) }
    }

    override suspend fun saveTable(id: String, areaId: String, seats: String, isNew: Boolean): Result<Unit> {
        val n = ManageRules.seats(seats).getOrElse { return fail(it.message!!) }
        val key = if (isNew) id.trim().uppercase() else id
        return storeOp(event = config(if (isNew) "TableConfigCreated" else "TableConfigUpdated", "table") { key }, check = { s ->
            when {
                s.areas.none { it.id == areaId } -> "请选择区域"
                isNew -> ManageRules.tableId(id, s.tables.map { it.id })
                s.table(id) == null -> "桌台不存在"
                (s.table(id)!!.guests > n) -> "当前 ${s.table(id)!!.guests} 人用餐，座位数不能小于人数"
                else -> null
            }
        }) { s ->
            if (isNew) s.copy(tables = s.tables + DiningTable(id.trim().uppercase(), areaId, n, TableStatus.Idle), orders = s.orders + (id.trim().uppercase() to TableOrder()))
            else s.withTable(id) { it.copy(areaId = areaId, seats = n) }
        }
    }

    override suspend fun addCategory(name: String, en: String): Result<Unit> {
        val id = newId("c")
        return storeOp(event = config("CategoryCreated", "category") { id },
        check = { s -> ManageRules.required(name, "分类名称") ?: ManageRules.unique(name, s.categories.map { it.name }, "分类") },
    ) { s -> s.copy(categories = s.categories + Category(id, name.trim(), en.trim())) }
    }

    override suspend fun addSpecGroup(name: String, en: String, options: String): Result<Unit> {
        val opts = ManageRules.options(options).getOrElse { return fail(it.message!!) }
        val gid = newId("g")
        return storeOp(event = config("SpecGroupCreated", "specGroup") { gid }, check = { s -> ManageRules.required(name, "规格组名称") ?: ManageRules.unique(name, s.specGroups.map { it.name }, "规格组") }) { s ->
            s.copy(specGroups = s.specGroups + SpecGroup(gid, name.trim(), en.trim(), opts.mapIndexed { i, (n, d) -> SpecOption("$gid-${i + 1}", n, d) }))
        }
    }

    override suspend fun saveProduct(product: Product, menuIds: Set<String>) = storeOp(event = EventSpec { b, a ->
        Triple(if (b.products.any { it.id == product.id }) "ProductUpdated" else "ProductCreated", ManagementEventFactory.config("product", product.id, b, a), listOf(Aggregates.CATALOG))
    }, check = { s ->
        MealRules.validate(product.mealGroups) ?: ManageRules.required(product.name, "商品名称") ?: ManageRules.required(product.en, "英文名称")
            ?: if (s.categories.none { it.id == product.categoryId }) "请选择分类" else null
                ?: ManageRules.unique(product.name, s.products.filter { it.id != product.id }.map { it.name }, "商品")
    }) { s ->
        val exists = s.products.any { it.id == product.id }
        val products = if (exists) s.products.map { if (it.id == product.id) product else it } else s.products + product
        s.copy(products = products, menus = ManageRules.relink(s.menus, product.id, menuIds))
    }

    override suspend fun adjustStock(productId: String, skuKey: String?, value: Int, reason: String): Result<Unit> {
        if (value < 0) return fail("库存应为非负整数")
        if (reason.isBlank()) return fail("请填写盘点原因")
        val actor = actorName(state.value)
        return storeOp(event = EventSpec { b, a -> Triple("StockAdjusted", ManagementEventFactory.stock(b, a), listOf("stock:$productId")) }, check = { s ->
            val p = s.product(productId)
            when {
                p == null -> "商品不存在"
                skuKey != null && p.skus.none { it.key == skuKey } -> "规格不存在"
                reason.length > 60 -> "原因最多 60 字"
                else -> null
            }
        }) { s ->
            val p = s.product(productId)!!
            val before = if (skuKey == null) p.stock else p.skus.first { it.key == skuKey }.stock
            val label = if (skuKey == null) p.name else "${p.name} · ${p.skus.first { it.key == skuKey }.label}"
            val move = StockMovement(newId("SM"), productId, skuKey, label, before, value, before?.let { value - it }, reason.trim(), actor, clock())
            s.copy(stockMovements = s.stockMovements + move, products = s.products.map { p ->
                when {
                    p.id != productId -> p
                    skuKey == null -> p.copy(stock = value)
                    else -> p.copy(skus = p.skus.map { if (it.key == skuKey) it.copy(stock = value, manualStock = true) else it })
                }
            })
        }
    }


    // —— 零售：实现在 RetailService（只依赖公共内核 StoreKernel），这里只委托，供主机仲裁和测试按原接口调用 ——
    /** 公共内核：业态服务只通过它改门店数据（事务、权限、单号、打印副本、主管授权） */
    internal val kernel: StoreKernel = object : StoreKernel {
        override val vault: StateFlow<Vault> get() = state
        override fun clock() = this@LocalWorkspace.clock()
        override fun newId(prefix: String) = this@LocalWorkspace.newId(prefix)
        override fun actorName(v: Vault) = this@LocalWorkspace.actorName(v)
        override fun need(perm: Perm) = this@LocalWorkspace.need(perm)
        override fun <T> derive(f: (Vault) -> T) = this@LocalWorkspace.derive(f)
        override suspend fun storeOp(event: EventSpec?, check: (StoreData) -> String?, f: (StoreData) -> StoreData) = this@LocalWorkspace.storeOp(event, check, f)
        override suspend fun <T> transaction(event: EventSpec?, f: (Vault) -> Pair<Vault, T>): T = this@LocalWorkspace.transaction(event, f)
        override fun copies(s: StoreData, job: PrintJob) = s.withCopies(job)
        override fun supervisorAuth(v: Vault, who: String, secret: String) = this@LocalWorkspace.supervisorAuth(v, who, secret)
        override fun pageEvent(type: String, tableId: String) = page(type, tableId)
        override fun kitchenJob(s: StoreData, tableId: String, batch: String) = s.kitchenJob(tableId, batch)
        override fun hostWritable() = blockedState.value == null && state.value.lan.role != LanRole.Secondary
        override fun taskLog(s: StoreData, kind: TaskLogKind, refId: String, result: String, source: String, detail: String) = s.log(kind, refId, result, source, detail)
    }
    private val settlement = Settlement(kernel)
    private val retailService by lazy { RetailService(kernel) }
    override val retail: StateFlow<RetailData> get() = retailService.retail
    override suspend fun holdCart(lines: List<ai.neargo.fnb.pos.core.model.CartLine>, member: String?, why: String): Result<String> = retailService.holdCart(lines, member, why)
    override suspend fun recallHeld(no: String): Result<ai.neargo.fnb.pos.core.model.RetailHeld> = retailService.recallHeld(no)
    override suspend fun newStockDoc(kind: ai.neargo.fnb.pos.core.model.StockDocKind, supplier: String, scope: String, toStore: String): Result<String> = retailService.newStockDoc(kind, supplier, scope, toStore)
    override suspend fun setStockDocLines(no: String, lines: List<ai.neargo.fnb.pos.core.model.StockDocLine>): Result<Unit> = retailService.setStockDocLines(no, lines)
    override suspend fun advanceStockDoc(no: String): Result<ai.neargo.fnb.pos.core.model.StockDocState> = retailService.advanceStockDoc(no)
    override suspend fun voidStockDoc(no: String): Result<Unit> = retailService.voidStockDoc(no)
    override suspend fun returnRetail(req: ai.neargo.fnb.pos.core.model.ReturnRequest, online: Boolean): Result<ai.neargo.fnb.pos.core.model.SaleReturn> = retailService.returnRetail(req, online)
    override suspend fun reprintSale(no: String): Result<Unit> = retailService.reprintSale(no)
    override suspend fun setRetailPrice(productId: String, price: Long): Result<Unit> = retailService.setRetailPrice(productId, price)
    override suspend fun queueLabel(productId: String): Result<Unit> = retailService.queueLabel(productId)
    override suspend fun openDrawer() = retailService.openDrawer()
    override suspend fun printLabels(productIds: List<String>, tpl: ai.neargo.fnb.pos.core.model.LabelTpl): Result<Int> = retailService.printLabels(productIds, tpl)
    override suspend fun simulateOnlineOrder(channel: ai.neargo.fnb.pos.core.model.SaleChannel): Result<String> = retailService.simulateOnlineOrder(channel)
    override suspend fun advanceOnline(no: String): Result<ai.neargo.fnb.pos.core.model.OnlineState> = retailService.advanceOnline(no)
    override suspend fun rejectOnline(no: String, reason: String): Result<Unit> = retailService.rejectOnline(no, reason)
    override suspend fun saveRetailSettings(settings: ai.neargo.fnb.pos.core.model.RetailSettings): Result<Unit> = retailService.saveRetailSettings(settings)
    override suspend fun checkoutRetail(lines: List<ai.neargo.fnb.pos.core.model.CartLine>, member: Boolean, method: String, received: Long, online: Boolean, extras: ai.neargo.fnb.pos.core.model.CheckoutExtras): Result<ai.neargo.fnb.pos.core.model.RetailSale> = retailService.checkoutRetail(lines, member, method, received, online, extras)
    override fun actor(): String = retailService.actor()
    override suspend fun authorize(who: String, secret: String): Result<String> = retailService.authorize(who, secret)

    /** 主管授权（退菜、折扣、无单退货共用）：员工姓名或 ID + PIN（需「授权」权限）、主管账号 + 密码，或体验账号；返回授权人 */
    private fun supervisorAuth(v: Vault, who: String, secret: String): Result<String> = runCatching {
        val account = v.accounts.firstOrNull { it.supervisor && it.email.equals(who.trim(), true) }
        val demo = v.user == "体验账号" && who.trim() == "manager@demo.local" && secret == "demo12345"
        val emp = v.store?.employees?.firstOrNull { it.enabled && (it.id == who.trim() || it.name == who.trim()) }
        val empOk = emp != null && Perm.Adjust in Roles.perms(emp.role) && ManageRules.hash(secret, emp.salt) == emp.pinHash
        require(empOk || demo || (account != null && ManageRules.hash(secret, account.salt) == account.hash)) {
            if (emp != null && Perm.Adjust !in Roles.perms(emp.role)) "${emp.name}（${emp.role.label}）无授权权限" else "主管账号或密码（PIN）不正确"
        }
        emp?.takeIf { empOk }?.name ?: who.trim()
    }

    override suspend fun saveMenu(id: String?, name: String, en: String, productIds: List<String>): Result<Unit> {
        val key = id ?: newId("m")
        return storeOp(event = config(if (id == null) "MenuCreated" else "MenuUpdated", "menu") { key }, check = { s ->
        ManageRules.required(name, "餐牌名称") ?: ManageRules.unique(name, s.menus.filter { it.id != id }.map { it.name }, "餐牌")
            ?: if (productIds.isEmpty()) "餐牌至少包含一个商品" else null
    }) { s ->
        if (id == null) s.copy(menus = s.menus + Menu(key, name.trim(), en.trim(), productIds.distinct(), published = false))
        else s.copy(menus = s.menus.map { if (it.id == id) it.copy(name = name.trim(), en = en.trim(), productIds = productIds.distinct()) else it })
    }
    }

    override suspend fun setPublished(menuId: String, published: Boolean) = storeOp(event = config(if (published) "MenuPublished" else "MenuUnpublished", "menu") { menuId }, check = { s ->
        val m = s.menus.firstOrNull { it.id == menuId } ?: return@storeOp "餐牌不存在"
        if (published) ManageRules.canPublish(m) else null
    }) { s -> s.copy(menus = s.menus.map { if (it.id == menuId) it.copy(published = published) else it }) }

    override suspend fun saveSettings(settings: StoreSettings) = storeOp(event = EventSpec { b, a -> Triple("StoreSettingsUpdated", ManagementEventFactory.config("settings", a.id, b, a), listOf(Aggregates.CATALOG)) },
        check = {
            ManageRules.required(settings.shortName, "门店简称") ?: when {
                settings.serviceBp !in 0..2000 -> "服务费率应为 0–20%"
                settings.memberBp !in 0..5000 -> "会员折扣应为 5–10 折"
                settings.pointsPerYuan !in 0..100 -> "积分倍率应为 0–100"
                settings.payMethods.isEmpty() -> "至少启用一种收款方式"
                settings.payMethods.any { it !in listOf("现金", "扫码", "银行卡", "储值卡") } -> "收款方式无效"
                else -> null
            }
        }) { s ->
        s.copy(settings = settings.copy(shortName = settings.shortName.trim()))
    }

    // —— 桌台：实现在 DiningService（只依赖公共内核），这里只委托 ——
    private val dining by lazy { DiningService(kernel) }
    override suspend fun createReservation(tableId: String, name: String, phone: String, guests: Int, arrivesAt: Long, note: String) = dining.createReservation(tableId, name, phone, guests, arrivesAt, note)
    override suspend fun cancelReservation(id: String) = dining.cancelReservation(id)
    override suspend fun checkInReservation(id: String) = dining.checkInReservation(id)
    override suspend fun openTable(tableId: String, guests: Int) = dining.openTable(tableId, guests)
    override suspend fun settle(tableId: String, amount: Long, method: String, online: Boolean, received: Long) = dining.settle(tableId, amount, method, online, received)
    override suspend fun startCleaning(tableId: String) = dining.startCleaning(tableId)
    override suspend fun finishCleaning(tableId: String) = dining.finishCleaning(tableId)
    override suspend fun setPosition(tableId: String, x: Int, y: Int) = dining.setPosition(tableId, x, y)
    override suspend fun changeTable(from: String, to: String) = dining.changeTable(from, to)
    override suspend fun mergeTable(from: String, into: String) = dining.mergeTable(from, into)
    override suspend fun splitLines(from: String, uids: Set<Long>, to: String, guests: Int) = dining.splitLines(from, uids, to, guests)

    // —— 打印与网口打印网关：实现在 PrintService（只依赖公共内核），这里只委托 ——
    private val printing by lazy { PrintService(kernel) }
    private fun StoreData.withCopies(job: PrintJob) = with(printing) { withCopies(job) }
    private fun StoreData.kitchenJob(tableId: String, batch: String) = with(printing) { kitchenJob(tableId, batch) }
    private fun ensureDeliveries(s: StoreData, source: String) = printing.ensureDeliveries(s, source)
    private fun StoreData.withJob(id: String, f: (PrintJob) -> PrintJob) = with(printing) { withJob(id, f) }
    suspend fun claimNextPrint() = printing.claimNextPrint()
    suspend fun finishPrint(jobId: String, outcome: ai.neargo.fnb.pos.core.print.SendOutcome) = printing.finishPrint(jobId, outcome)
    override suspend fun resolveUnknown(jobId: String, printed: Boolean, reason: String) = printing.resolveUnknown(jobId, printed, reason)
    override suspend fun savePrinter(p: PrinterConfig) = printing.savePrinter(p)
    override suspend fun removePrinter(id: String) = printing.removePrinter(id)
    override suspend fun setRoute(station: String, printerId: String?) = printing.setRoute(station, printerId)
    override suspend fun setBackup(station: String, printerId: String?) = printing.setBackup(station, printerId)
    override suspend fun setCopies(station: String, printerIds: List<String>) = printing.setCopies(station, printerIds)
    override suspend fun redirectPrint(jobId: String, printerId: String) = printing.redirectPrint(jobId, printerId)
    override suspend fun testPrint(printerId: String) = printing.testPrint(printerId)
    override val deliveries: StateFlow<List<KitchenDelivery>> get() = printing.deliveries
    override val deliveryGroups: StateFlow<List<TicketDelivery>> get() = printing.deliveryGroups
    override suspend fun confirmDelivery(ticketId: String, reason: String) = printing.confirmDelivery(ticketId, reason)
    override suspend fun preBill(tableId: String) = printing.preBill(tableId)
    override suspend fun reprint(jobId: String) = printing.reprint(jobId)
    override suspend fun process(kitchenOk: Boolean) = printing.process(kitchenOk)
    override fun billPreview(tableId: String) = printing.billPreview(tableId)

    // —— 交班 ——
    override suspend fun closeShift(counted: Long, staff: String): Result<ShiftRecord> = persistenceResult {
        transaction(EventSpec { b, a -> Triple("ShiftClosed", ManagementEventFactory.shift(b, a), listOf(Aggregates.SHIFT)) }) { v ->
            if (!Money.withinBill(counted)) return@transaction v to Result.failure(IllegalArgumentException("实盘金额应为非负且不超过上限"))
            val s = v.store ?: return@transaction v to Result.failure(IllegalStateException("请先选择门店"))
            val sum = ShiftRules.summary(s.payments, s.shift, s.adjustments)
            val start = if (s.shift.start > 0) s.shift.start else s.payments.minOfOrNull { it.at } ?: clock()
            val rec = ShiftRecord(newId("S"), start, clock(), staff, sum.total, sum.count, sum.byMethod, s.shift.float, sum.cashExpected, counted, sum.voidAmount, sum.voidCount, sum.giftAmount, sum.giftCount, sum.refundCount, sum.refund)
            val next = s.copy(
                shift = ShiftState(start = clock(), float = s.shift.float, history = s.shift.history + rec),
                printJobs = s.printJobs + s.withCopies(PrintJob(newId("SH"), PrintKind.Shift, "交班单 $staff", Receipts.shift(rec), clock())),
            )
            v.copy(stores = v.stores + (s.id to next)) to Result.success(rec)
        }
    }

    // —— 订单：实现在 DiningService ——
    override suspend fun addDish(tableId: String, product: Product, sku: Sku?, qty: Int, note: String, mealChoiceIds: List<String>) = dining.addDish(tableId, product, sku, qty, note, mealChoiceIds)
    override suspend fun setBenefits(tableId: String, coupon: Boolean, activity: Boolean) = dining.setBenefits(tableId, coupon, activity)
    override suspend fun authorizeAdjustment(tableId: String, request: AdjustmentRequest, email: String, password: String) = dining.authorizeAdjustment(tableId, request, email, password)
    override suspend fun setLineNote(tableId: String, uid: Long, note: String) = dining.setLineNote(tableId, uid, note)
    override suspend fun setOrderNote(tableId: String, note: String) = dining.setOrderNote(tableId, note)
    override suspend fun hold(tableId: String) = dining.hold(tableId)
    override suspend fun retrieve(ticketId: String, tableId: String) = dining.retrieve(ticketId, tableId)
    override suspend fun changeQty(tableId: String, uid: Long, delta: Int) = dining.changeQty(tableId, uid, delta)
    override suspend fun clearNew(tableId: String) = dining.clearNew(tableId)
    override suspend fun sendToKitchen(tableId: String, online: Boolean) = dining.sendToKitchen(tableId, online)
    override suspend fun advance(tableId: String, uid: Long, to: LineStatus) = dining.advance(tableId, uid, to)
    override suspend fun setMember(tableId: String, member: Boolean) = dining.setMember(tableId, member)

    // —— 同步 ——
    /**
     * 补发所有门店的待同步项（按创建顺序）。当前对接本地模拟服务：补发即成功。
     * 幂等：只处理 Pending；送厨只把该批次的待同步行改为已下单，重复补发不会重复出单。
     */
    override suspend fun syncNow(): Int {
        if (blockedState.value != null || state.value.lan.role == LanRole.Secondary) return 0
        var done = 0
        commit { v ->
            v.copy(stores = v.stores.mapValues { (_, s) ->
                var st = s
                s.outbox.filter { it.state == OutboxState.Pending }.sortedBy { it.createdAt }.forEach { item ->
                    st = when (item.kind) {
                        // 换桌 / 拆单后批次可能已在其他桌，按批次号在全店查找
                        OutboxKind.Kitchen -> {
                            val table = st.orders.entries.firstOrNull { (_, o) -> o.lines.any { it.batchId == item.id } }?.key ?: item.tableId
                            st.withOrder(table, OrderRules.markBatchSent(st.order(table), item.id))
                        }
                        OutboxKind.Payment -> st.copy(payments = st.payments.map { if (it.id == item.id) it.copy(synced = true) else it })
                    }
                    st = st.copy(outbox = st.outbox.map { if (it.id == item.id) it.copy(state = OutboxState.Done, attempts = it.attempts + 1, doneAt = clock(), ackSource = "simulation") else it })
                        .log(TaskLogKind.SyncAttempt, item.id, "Done", "simulation", item.kind.name)
                    done++
                }
                st
            })
        }
        return done
    }
}
