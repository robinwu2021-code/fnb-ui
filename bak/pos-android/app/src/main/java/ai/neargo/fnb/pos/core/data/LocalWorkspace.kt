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
class LocalWorkspace(
    private val store: SnapshotStore,
    private val scope: CoroutineScope,
    /** 身份与授权；为空时（单元测试）不做授权限制。 */
    private val identity: IdentityContext? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : SessionRepository, StoreConfigRepository, FloorRepository, OrderRepository, SyncRepository, PrintRepository, ShiftRepository, AuthRepository, OperationsRepository, StaffRepository, KdsRepository {

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
            val withRetail = if (DemoCatalog.RETAIL_STORE_ID !in migrated.stores && demo.containsKey(DemoCatalog.RETAIL_STORE_ID)) migrated.copy(stores = migrated.stores + (DemoCatalog.RETAIL_STORE_ID to demo.getValue(DemoCatalog.RETAIL_STORE_ID))) else migrated
            val restored = withRetail.copy(
                deviceId = withRetail.deviceId.ifBlank { state.value.deviceId },
                // 旧数据（N4 前）首次由本机加载时绑定本机密钥
                deviceKeyThumbprint = withRetail.deviceKeyThumbprint.ifBlank { thumb },
                stores = withRetail.stores.mapValues { upgradeStates(assignOrderIds(ReservationRules.importLegacy(it.value, clock()), "legacy-v3")) },
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
            staffOf(v)?.let { "${it.name} · ${it.role.label}" } ?: if (s?.employees.isNullOrEmpty()) DemoCatalog.STAFF else "未上岗", s?.currentTableId ?: "", s?.biz ?: BizType.Dining)
    }
    override val stores: StateFlow<List<StoreSummary>> = derive { v ->
        v.stores.values.map { s -> StoreSummary(s.id, s.settings.shortName, s.code, s.address, s.tables.size, s.tables.count { it.status == TableStatus.Dining }, s.biz) }
    }
    override val current: StateFlow<StoreData?> = derive { it.store }
    override val tables: StateFlow<List<DiningTable>> = derive { it.store?.tables.orEmpty() }
    override val orders: StateFlow<Map<String, TableOrder>> = derive { it.store?.orders.orEmpty() }
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

    private fun StoreData.order(t: String) = orders[t] ?: TableOrder()
    private fun StoreData.table(t: String) = tables.firstOrNull { it.id == t }
    private fun StoreData.withOrder(t: String, o: TableOrder) = copy(orders = orders + (t to o))
    private fun StoreData.withTable(t: String, f: (DiningTable) -> DiningTable) = copy(tables = tables.map { if (it.id == t) f(it) else it })
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

    // —— 厨房屏（N7）：主机权威任务卡 ——
    /** 退菜同步到任务卡：退菜份数计入，全部退完则取消（版本 +1，变更序号前进）。 */
    private fun StoreData.kdsVoid(batchId: String, uid: Long, qty: Int, reason: String): StoreData {
        val t = KdsRules.ticketOf(kdsTickets, batchId, uid) ?: return this
        val ticketId = t.id
        val lines = t.lines.map { if (it.uid == uid) it.copy(voidQty = (it.voidQty + qty).coerceAtMost(it.qty)) else it }
        val cancelled = lines.all { it.active == 0 }
        val n = t.copy(lines = lines, rev = t.rev + 1, seq = kdsSeq + 1, updatedAt = clock(), state = if (cancelled) KdsState.Cancelled else t.state,
            note = if (cancelled) "全部退菜：$reason" else "部分退菜：$reason")
        return copy(kdsTickets = kdsTickets.map { if (it.id == ticketId) n else it }, kdsSeq = kdsSeq + 1)
    }

    /** 厨师操作（接单/开始制作/完成/撤回完成）：须带所见版本；单据已变（含退菜取消）则拒绝，迟到命令不能覆盖取消。 */
    override suspend fun kdsAdvance(ticketId: String, expectedRev: Int, to: KdsState): Result<Unit> {
        val tableId = currentStore()?.kdsTickets?.firstOrNull { it.id == ticketId }?.tableId ?: return fail("任务卡不存在")
        val origin = kotlinx.coroutines.currentCoroutineContext()[RemoteOrigin]
        val actor = origin?.let { o -> currentStore()?.employees?.firstOrNull { it.id == o.actorId }?.name ?: "厨房屏" } ?: actorName(state.value)
        return storeOp(event = EventSpec { _, a ->
            val t = a.kdsTickets.first { it.id == ticketId }
            Triple("KdsTicketAdvanced", kotlinx.serialization.json.Json.encodeToString(KdsTicket.serializer(), t), listOf("kds:$ticketId", "table:$tableId"))
        }, check = { s ->
            val t = s.kdsTickets.firstOrNull { it.id == ticketId }
            when {
                t == null -> "任务卡不存在"
                t.rev != expectedRev -> if (t.state == KdsState.Cancelled) "该单已取消（退菜），不能再操作" else "单据已更新（退菜或其他工位操作），请刷新后再试"
                else -> KdsRules.ownerCheck(KdsRules.dispatchOf(s, t.station), t, deviceOf(origin)) ?: KdsRules.transition(t.state, to)
            }
        }) { s ->
            s.copy(kdsTickets = s.kdsTickets.map {
                if (it.id != ticketId) it
                else {
                    // 分派模式：第一块操作的屏成为负责屏
                    val claim = KdsRules.dispatchOf(s, it.station) == KdsDispatch.Claim && it.owner.isBlank()
                    it.copy(state = to, rev = it.rev + 1, seq = s.kdsSeq + 1, updatedAt = clock(), by = actor,
                        owner = if (claim) deviceOf(origin) else it.owner, ownerName = if (claim) deviceName(deviceOf(origin)) else it.ownerName)
                }
            }, kdsSeq = s.kdsSeq + 1)
        }
    }

    private fun deviceOf(origin: RemoteOrigin?) = origin?.deviceId ?: state.value.deviceId
    private fun deviceName(id: String) = if (id == state.value.deviceId) "主 POS" else state.value.lan.paired.firstOrNull { it.deviceId == id }?.name ?: "厨房屏"

    /** 负责屏释放：卡回到待接单，其他屏可以接（已完成/已取消的卡不能释放）。 */
    override suspend fun kdsRelease(ticketId: String, expectedRev: Int): Result<Unit> {
        val tableId = currentStore()?.kdsTickets?.firstOrNull { it.id == ticketId }?.tableId ?: return fail("任务卡不存在")
        val origin = kotlinx.coroutines.currentCoroutineContext()[RemoteOrigin]
        return storeOp(event = EventSpec { _, a ->
            Triple("KdsTicketReleased", kotlinx.serialization.json.Json.encodeToString(KdsTicket.serializer(), a.kdsTickets.first { it.id == ticketId }), listOf("kds:$ticketId", "table:$tableId"))
        }, check = { s ->
            val t = s.kdsTickets.firstOrNull { it.id == ticketId }
            when {
                t == null -> "任务卡不存在"
                t.rev != expectedRev -> "单据已更新，请刷新后再试"
                t.owner.isBlank() -> "这张卡还没有负责屏"
                t.owner != deviceOf(origin) -> "只有负责屏可以释放"
                t.state == KdsState.Done || t.state == KdsState.Cancelled -> "已完成或已取消的卡不能释放"
                else -> null
            }
        }) { s -> s.reassignKds(ticketId, "", "") }
    }

    /** 主管收回或指派负责屏（仅主机）：收回后卡回到待接单；指派给已配对的设备。 */
    override suspend fun kdsReassign(ticketId: String, deviceId: String?): Result<Unit> {
        val tableId = currentStore()?.kdsTickets?.firstOrNull { it.id == ticketId }?.tableId ?: return fail("任务卡不存在")
        return storeOp(event = EventSpec { _, a ->
            Triple("KdsTicketReassigned", kotlinx.serialization.json.Json.encodeToString(KdsTicket.serializer(), a.kdsTickets.first { it.id == ticketId }), listOf("kds:$ticketId", "table:$tableId"))
        }, check = { s ->
            val t = s.kdsTickets.firstOrNull { it.id == ticketId }
            when {
                need(Perm.Adjust) != null -> need(Perm.Adjust)
                state.value.lan.role == LanRole.Secondary -> "只能在主 POS 收回或指派"
                t == null -> "任务卡不存在"
                t.state == KdsState.Done || t.state == KdsState.Cancelled -> "已完成或已取消的卡不需要收回"
                deviceId != null && state.value.lan.paired.none { it.deviceId == deviceId && it.revokedAt == 0L } -> "设备不存在或已撤销"
                deviceId == null && t.owner.isBlank() -> "这张卡还没有负责屏"
                else -> null
            }
        }) { s -> s.reassignKds(ticketId, deviceId.orEmpty(), deviceId?.let { deviceName(it) }.orEmpty()) }
    }

    private fun StoreData.reassignKds(ticketId: String, owner: String, name: String) = copy(kdsTickets = kdsTickets.map {
        if (it.id != ticketId) it
        else it.copy(owner = owner, ownerName = name, state = if (owner.isBlank()) KdsState.Pending else it.state, rev = it.rev + 1, seq = kdsSeq + 1, updatedAt = clock())
    }, kdsSeq = kdsSeq + 1)

    /** 档口分派方式（门店设置权限）。 */
    override suspend fun saveDispatchModes(modes: Map<String, KdsDispatch>) = storeOp(event = EventSpec { _, a ->
        Triple("KitchenDispatchSaved", kotlinx.serialization.json.Json.encodeToString(KitchenDispatchPayload.serializer(), KitchenDispatchPayload(a.stationDispatch)), listOf(Aggregates.CATALOG))
    }, check = { s -> if (modes.keys.any { it !in s.kdsStations }) "档口不存在" else null }) { s ->
        s.copy(stationDispatch = modes.filterValues { it != KdsDispatch.Sync })
    }

    /** 厨房屏增量拉取：返回 seq 大于游标的任务卡及新游标。 */
    fun kdsPull(storeId: String, cursor: Long, station: String): Pair<List<KdsTicket>, Long> {
        val s = state.value.stores[storeId] ?: return emptyList<KdsTicket>() to cursor
        val list = s.kdsTickets.filter { it.seq > cursor && (station.isBlank() || it.station == station) }.sortedBy { it.seq }.take(200)
        return list to (list.maxOfOrNull { it.seq } ?: cursor)
    }

    /** 厨房屏持久接收回执：记录送达（与厨师接单无关）。 */
    suspend fun kdsAck(acks: List<Pair<String, Int>>) {
        commit { v ->
            val s = v.store ?: return@commit v
            val now = clock()
            val map = acks.toMap()
            if (s.kdsTickets.none { (map[it.id] ?: 0) > it.deliveredRev }) return@commit v
            v.copy(stores = v.stores + (s.id to s.copy(kdsTickets = s.kdsTickets.map { t ->
                val r = map[t.id] ?: 0
                if (r > t.deliveredRev) t.copy(deliveredRev = r, deliveredAt = if (t.deliveredAt == 0L) now else t.deliveredAt) else t
            }, kitchenDeliveries = s.kitchenDeliveries.map { d ->
                // 厨房屏迟到收到也自动解除「未收到」
                if (d.channel == "screen" && (map[d.ticketId] ?: 0) > 0 && d.state != DeliveryState.Delivered && d.state != DeliveryState.ManualConfirmed)
                    d.copy(state = DeliveryState.Delivered, lastError = "", updatedAt = now) else d
            })))
        }
    }

    /** 厨房屏超过时限仍未收到：送达记录记为失败（主机/单机执行；迟到回执会自动解除）。返回本次标记数。 */
    suspend fun expireScreenDeliveries(): Int {
        if (blockedState.value != null || state.value.lan.role == LanRole.Secondary) return 0
        var n = 0
        commit { v ->
            val s = v.store ?: return@commit v
            val now = clock()
            val delivered = s.kdsTickets.filter { it.deliveredRev > 0 || it.state == KdsState.Cancelled }.map { it.id }.toSet()
            val due = s.kitchenDeliveries.filter { it.channel == "screen" && it.state == DeliveryState.Pending && now - it.createdAt >= DeliveryRules.SCREEN_TIMEOUT_MS }
            if (due.isEmpty()) return@commit v
            v.copy(stores = v.stores + (s.id to s.copy(kitchenDeliveries = s.kitchenDeliveries.map { d ->
                if (d !in due) d
                else if (d.ticketId in delivered) d.copy(state = DeliveryState.Delivered, updatedAt = now)
                else { n++; d.copy(state = DeliveryState.Failed, lastError = "${DeliveryRules.SCREEN_TIMEOUT_MS / 1000} 秒内没有${d.station}厨房屏收到", updatedAt = now) }
            })))
        }
        return n
    }

    /** 档口出单方式（门店设置权限），只影响之后送厨的单。 */
    override suspend fun saveDeliveryModes(modes: Map<String, DeliveryMode>) = storeOp(event = EventSpec { _, a ->
        Triple("KitchenDeliveryPolicySaved", kotlinx.serialization.json.Json.encodeToString(KitchenDeliveryPolicyPayload.serializer(), KitchenDeliveryPolicyPayload(a.stationDelivery)), listOf(Aggregates.CATALOG))
    }, check = { s -> if (modes.keys.any { it !in s.kdsStations }) "档口不存在" else null }) { s ->
        s.copy(stationDelivery = modes.filterValues { it != DeliveryMode.Paper })
    }

    /** 厨房屏未收到：改打一张纸单补上，送达后视为厨房屏已满足。 */
    override suspend fun printForScreen(ticketId: String) = storeOp(check = { s ->
        val g = DeliveryRules.groups(s).firstOrNull { it.ticketId == ticketId }
        when {
            need(Perm.Checkout) != null -> need(Perm.Checkout)
            g == null -> "送达记录不存在"
            !g.canFallback -> "该单不需要改打纸单"
            s.kdsTickets.none { it.id == ticketId } -> "任务卡不存在"
            else -> null
        }
    }) { s ->
        val g = DeliveryRules.groups(s).first { it.ticketId == ticketId }
        val t = s.kdsTickets.first { it.id == ticketId }
        val lines = s.order(t.tableId).lines.filter { l -> t.lines.any { it.uid == l.uid && it.active > 0 } }
            .map { l -> l.copy(qty = t.lines.first { it.uid == l.uid }.active) }
        val job = PrintJob(newId("FB"), PrintKind.Kitchen, "${t.tableId} 厨房单·${t.station}（屏未收到改打）",
            listOf("** 厨房屏未收到，改打纸单 **") + Receipts.kitchen(s.settings.shortName, t.tableId, lines, t.note, clock(), t.station), clock(), station = t.station)
        val d = KitchenDelivery(ticketId, job.id, t.tableId, t.orderId, job.title, target = "${t.station}打印机", createdAt = clock(), updatedAt = clock(),
            station = t.station, covers = "screen")
        s.copy(printJobs = s.printJobs + job, kitchenDeliveries = s.kitchenDeliveries.map { if (it.jobId == g.fallback?.jobId) it.copy(supersededBy = job.id) else it } + d)
            .log(TaskLogKind.PrintRequested, job.id, "queued", "manual", "厨房屏未收到改打 $ticketId")
    }

    /** 厨房档口配置（门店设置权限）。 */
    override suspend fun saveKdsStations(stations: List<String>, mapping: Map<String, String>) = storeOp(event = EventSpec { _, a ->
        Triple("KitchenStationsSaved", kotlinx.serialization.json.Json.encodeToString(KitchenStationsPayload.serializer(), KitchenStationsPayload(a.kdsStations, a.stationOf)), listOf(Aggregates.CATALOG))
    }, check = { s -> KdsRules.validateStations(stations.map { it.trim() }, mapping) ?: if (mapping.keys.any { k -> s.categories.none { it.id == k } }) "分类不存在" else null }) { s ->
        s.copy(kdsStations = stations.map { it.trim() }, stationOf = mapping)
    }

    // —— 厨房屏设备侧：持久收件箱 ——
    val kdsDevice: StateFlow<Triple<String, List<KdsTicket>, Long>> = derive { Triple(it.kdsStation, it.kdsInbox, it.kdsCursor) }

    /** 设置/切换本机档口：档口变化时清空收件箱并从头拉取本档口任务。 */
    suspend fun setKdsStation(station: String) {
        commit { v -> val st = station.trim(); if (st == v.kdsStation) v else v.copy(kdsStation = st, kdsInbox = emptyList(), kdsCursor = 0) }
    }

    /** 收到任务卡：去重合并后落盘，返回需回执的（id, rev）。 */
    suspend fun kdsReceive(tickets: List<KdsTicket>, cursor: Long): List<Pair<String, Int>> {
        var accepted = emptyList<KdsTicket>()
        commit { v ->
            val (merged, acc) = KdsRules.merge(v.kdsInbox, tickets)
            accepted = acc
            // 只保留未完结或最近 2 小时内更新的卡，避免收件箱无限增长
            val keep = merged.filter { it.state !in setOf(KdsState.Done, KdsState.Cancelled) || clock() - it.updatedAt < 2 * 3_600_000L }
            v.copy(kdsInbox = keep, kdsCursor = maxOf(v.kdsCursor, cursor))
        }
        return accepted.map { it.id to it.rev }
    }

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
        val token = issuer.issue(thumb, v.deviceId, v.tenantId, v.stores.keys.toList(), kind, clock(), authEpoch = maxOf(1, v.knownAuthEpoch))
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
    override suspend fun selectStore(storeId: String) { commit { if (storeId in it.stores) it.copy(storeId = storeId, staffId = if (storeId == it.storeId) it.staffId else "") else it } }
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

    // —— 桌台 ——
    override suspend fun createReservation(tableId: String, name: String, phone: String, guests: Int, arrivesAt: Long, note: String) = storeOp(event = page("ReservationCreated", tableId), check = { s ->
        ReservationRules.validate(s.table(tableId), s.reservations, name, phone, guests, arrivesAt, clock())
            ?: if (note.length > 200) "预订备注最多 200 字" else null
    }) { s ->
        val r = Reservation(newId("R"), tableId, name.trim(), phone.trim(), guests, arrivesAt, note.trim(), createdAt = clock())
        s.withTable(tableId) { it.copy(status = TableStatus.Reserved, reservation = ReservationRules.format(arrivesAt)) }
            .copy(reservations = s.reservations + r)
    }

    override suspend fun cancelReservation(id: String): Result<Unit> {
        val tableId = bookedTable(id) ?: return fail("预订已处理或不存在")
        return storeOp(event = page("ReservationCancelled", tableId), check = { s ->
            if (s.reservations.none { it.id == id && it.tableId == tableId && it.status == ReservationStatus.Booked }) "预订已处理或不存在" else null
        }) { s -> cancelled(s, id) }
    }

    private fun bookedTable(id: String) = currentStore()?.reservations?.firstOrNull { it.id == id && it.status == ReservationStatus.Booked }?.tableId

    private fun cancelled(s: StoreData, id: String): StoreData {
        val r = s.reservations.first { it.id == id }
        return s.withTable(r.tableId) { if (it.status == TableStatus.Reserved) it.copy(status = TableStatus.Idle, reservation = "") else it }
            .copy(reservations = s.reservations.map { if (it.id == id) it.copy(status = ReservationStatus.Cancelled, updatedAt = clock()) else it })
    }

    /** 预订到店：开台与预订状态在同一事务，产生独立的 ReservationCheckedIn 事件（不借用 TableOpened）。 */
    override suspend fun checkInReservation(id: String): Result<Unit> {
        val tableId = bookedTable(id) ?: return fail("预订已处理或不存在")
        return storeOp(event = page("ReservationCheckedIn", tableId), check = { s ->
            val r = s.reservations.firstOrNull { it.id == id && it.tableId == tableId && it.status == ReservationStatus.Booked }
                ?: return@storeOp "预订已处理或不存在"
            openBlock(s, tableId, r.guests)
        }) { s -> opened(s, tableId, s.reservations.first { it.id == id }.guests, arrivedId = id) }
    }

    override suspend fun openTable(tableId: String, guests: Int) =
        storeOp(event = page("TableOpened", tableId), check = { s -> openBlock(s, tableId, guests) }) { s -> opened(s, tableId, guests) }

    private fun openBlock(s: StoreData, tableId: String, guests: Int): String? {
        val t = s.table(tableId) ?: return "桌台不存在"
        return when {
            t.status != TableStatus.Idle && t.status != TableStatus.Reserved -> "$tableId ${t.status.label}，不能开台"
            guests !in 1..t.seats -> "人数应为 1–${t.seats}"
            else -> null
        }
    }

    /** arrivedId 为空时（直接开台）该桌所有待到店预订视为到店；否则只标记指定预订。 */
    private fun opened(s: StoreData, tableId: String, guests: Int, arrivedId: String? = null) =
        s.withTable(tableId) { it.copy(status = TableStatus.Dining, guests = guests, openedAt = clock(), reservation = "", paid = 0, sessionId = newId("TS")) }
            .withOrder(tableId, TableOrder(orderId = newId("O"), serviceBp = s.settings.serviceBp, memberBp = s.settings.memberBp)).copy(currentTableId = tableId,
                reservations = s.reservations.map {
                    val hit = if (arrivedId != null) it.id == arrivedId else it.tableId == tableId && it.status == ReservationStatus.Booked
                    if (hit) it.copy(status = ReservationStatus.Arrived, updatedAt = clock()) else it
                })

    override suspend fun settle(tableId: String, amount: Long, method: String, online: Boolean, received: Long): Result<Unit> {
        val pid = newId("P")
        // 储值支付同时修改会员余额：事件参与会员聚合
        val memberAgg = currentStore()?.order(tableId)?.memberId?.takeIf { it.isNotBlank() && method == "储值卡" }
        val spec = page("PaymentRecorded", tableId).let { base -> if (memberAgg == null) base else EventSpec { b, a -> base.resolve(b, a).let { (t, p, ag) -> Triple(t, p, ag + "member:$memberAgg") } } }
        return storeOp(event = spec, check = { s ->
            val totals = OrderRules.totals(s.order(tableId))
            OrderRules.checkoutBlock(totals) ?: DeliveryRules.checkoutBlock(s, tableId) ?: when {
                amount != totals.due -> "账单已变化，请重新核对"
                !Money.withinBill(amount) || !Money.withinBill(received) -> "金额超出上限"
                s.settings.currency !in Money.SUPPORTED -> "不支持的币种：${s.settings.currency}"
                method == "现金" && received < amount -> "实收不足，不能确认收款"
                method !in listOf("现金", "银行卡", "扫码", "储值卡") -> "请选择收款方式"
                method !in s.settings.payMethods -> "门店未启用「$method」"
                !online && method == "扫码" -> "离线时该收款方式不可用"
                method == "储值卡" -> {
                    val o = s.order(tableId)
                    val m = s.members.firstOrNull { it.id == o.memberId }
                    when {
                        o.memberId.isNotBlank() && m == null -> "会员不存在"
                        m != null && m.balance < amount -> "会员余额不足（余额 ${yuan(m.balance)}）"
                        m == null && (!o.member || s.walletBalance < amount) -> "请先绑定会员"
                        else -> null
                    }
                }
                else -> null
            }
        }) { s ->
            val pay = PaymentRecord(pid, tableId, amount, method, clock(), synced = online, received = if (method == "现金") received else amount, change = if (method == "现金") received - amount else 0L,
                orderId = s.order(tableId).orderId.ifBlank { null }, currency = s.settings.currency,
                state = if (method == "扫码") PaymentState.Succeeded else PaymentState.LocalRecorded, source = if (method == "扫码") "simulation" else "local")
            val out = if (online) s.outbox else s.outbox + OutboxItem(pid, OutboxKind.Payment, tableId, "$tableId $method 收款 ${yuan(amount)}", clock())
            val receipt = PrintJob(newId("R"), PrintKind.Receipt, "$tableId 结账单", Receipts.bill("结账单", s, tableId, s.order(tableId), clock(), pay), clock())
            val o = s.order(tableId); val t = OrderRules.totals(o)
            val snap = SettledOrder(o.orderId, tableId, pid, clock(), o.lines, t.sub, t.service, t.discount, t.due, method, o.memberId, o.promotions.map { it.name }, s.table(tableId)?.guests ?: 0)
            val member = s.members.firstOrNull { it.id == o.memberId }
            val actor = actorName(state.value)
            var members = s.members; var txns = s.memberTxns; var wallet = s.walletBalance
            if (member != null) {
                val stored = method == "储值卡"
                val pts = (amount / 100) * s.settings.pointsPerYuan
                val m2 = member.copy(balance = if (stored) member.balance - amount else member.balance, points = member.points + pts, totalSpent = member.totalSpent + amount, visits = member.visits + 1)
                members = members.map { if (it.id == member.id) m2 else it }
                if (stored) txns = txns + MemberTxn(newId("MT"), member.id, MemberTxnKind.Consume, -amount, m2.balance, clock(), pid, actor, method)
                if (pts > 0) txns = txns + MemberTxn(newId("MT"), member.id, MemberTxnKind.Points, 0, m2.balance, clock(), pid, actor, points = pts)
            } else if (method == "储值卡") wallet -= amount // 旧版演示余额
            s.withTable(tableId) { it.copy(status = TableStatus.Paid, paid = amount) }.withOrder(tableId, TableOrder())
                .copy(payments = s.payments + pay, outbox = out, printJobs = s.printJobs + s.withCopies(receipt), walletBalance = wallet,
                    members = members, memberTxns = txns, settledOrders = s.settledOrders + snap)
        }
    }

    override suspend fun startCleaning(tableId: String) = transition(tableId, TableStatus.Paid, "TableCleaningStarted") { it.copy(status = TableStatus.Cleaning) }
    override suspend fun finishCleaning(tableId: String) = transition(tableId, TableStatus.Cleaning, "TableCleaningFinished") { it.copy(status = TableStatus.Idle, guests = 0, openedAt = 0, paid = 0, sessionId = "") }

    private suspend fun transition(tableId: String, from: TableStatus, type: String, f: (DiningTable) -> DiningTable) = storeOp(event = page(type, tableId), check = { s ->
        val t = s.table(tableId) ?: return@storeOp "桌台不存在"
        if (t.status != from) "$tableId 当前为${t.status.label}" else null
    }) { it.withTable(tableId, f) }

    override suspend fun setPosition(tableId: String, x: Int, y: Int) = storeOp(event = page("TablePositionChanged", tableId), check = { s ->
        when {
            s.table(tableId) == null -> "桌台不存在"
            x !in 0..TableRules.MAX_X || y !in 0..TableRules.MAX_Y -> "坐标范围：X 0–${TableRules.MAX_X}，Y 0–${TableRules.MAX_Y}"
            else -> null
        }
    }) { it.withTable(tableId) { t -> t.copy(x = x, y = y) } }

    override suspend fun changeTable(from: String, to: String) = storeOp(event = page("TableChanged", from), check = { s ->
        if (s.reservations.any { it.tableId == to && it.status == ReservationStatus.Booked }) return@storeOp "目标桌台已有预订"
        val a = s.table(from); val b = s.table(to)
        if (a == null || b == null) "桌台不存在" else TableRules.canChange(a, b)
    }) { s ->
        val a = s.table(from)!!
        // 换桌：订单ID保留，目标桌开新的桌台会话
        s.withTable(to) { it.copy(status = TableStatus.Dining, guests = a.guests, openedAt = a.openedAt, reservation = "", sessionId = newId("TS")) }
            .withTable(from) { it.copy(status = TableStatus.Cleaning, guests = 0, openedAt = 0) }
            .withOrder(to, s.order(from)).withOrder(from, TableOrder())
            .copy(currentTableId = to)
    }

    override suspend fun mergeTable(from: String, into: String) = storeOp(event = page("TableMerged", from), check = { s ->
        val a = s.table(from); val b = s.table(into)
        if (a == null || b == null) "桌台不存在" else TableRules.canMerge(a, b) ?: TableRules.compatible(s.order(from), s.order(into))
    }) { s ->
        val a = s.table(from)!!
        s.withTable(into) { it.copy(guests = it.guests + a.guests, openedAt = minOf(it.openedAt, a.openedAt)) }
            .withTable(from) { it.copy(status = TableStatus.Cleaning, guests = 0, openedAt = 0) }
            .withOrder(into, TableRules.merge(s.order(from), s.order(into))).withOrder(from, TableOrder())
            .copy(currentTableId = into)
    }

    override suspend fun splitLines(from: String, uids: Set<Long>, to: String, guests: Int) = storeOp(event = page("OrderSplit", from), check = { s ->
        if (s.reservations.any { it.tableId == to && it.status == ReservationStatus.Booked }) return@storeOp "目标桌台已有预订"
        val b = s.table(to)
        when {
            s.table(from)?.status != TableStatus.Dining -> "$from 未在用餐"
            b == null -> "桌台不存在"
            from == to -> "请选择另一张桌台"
            else -> TableRules.canSplit(s.order(from), uids, b, guests) ?: TableRules.compatible(s.order(from), s.order(to))
        }
    }) { s ->
        val (rest, moved) = TableRules.split(s.order(from), uids)
        val b = s.table(to)!!
        val opened = if (b.status == TableStatus.Dining) s else s.withTable(to) { it.copy(status = TableStatus.Dining, guests = guests, openedAt = clock(), reservation = "", sessionId = newId("TS")) }
        val target = opened.order(to)
        val source = s.order(from)
        // 拆单：目标沿用已有订单或生成新订单；记录来源订单，不当作并台
        val merged = TableRules.merge(source.copy(lines = moved), target).copy(
            orderId = target.orderId.ifBlank { newId("O") }, mergedOrderIds = target.mergedOrderIds,
            splitFromOrderIds = (target.splitFromOrderIds + source.orderId).filter { it.isNotBlank() }.distinct(),
        )
        opened.withOrder(from, rest).withOrder(to, merged)
    }

    // —— 打印 ——
    /** 厨房单按档口拆分：单档口时任务号为 P-批次（兼容旧数据），多档口为 P-批次@档口，与厨房屏任务卡一一对应。 */
    private fun StoreData.kitchenJob(tableId: String, batch: String): StoreData {
        val groups = order(tableId).lines.filter { it.batchId == batch }.groupBy { KdsRules.stationFor(this, it.dishId) }
        if (groups.isEmpty()) return this
        val multi = kdsStations.size > 1
        val jobs = groups.entries.sortedBy { kdsStations.indexOf(it.key) }.map { (station, lines) ->
            PrintJob(if (groups.size == 1) "P-$batch" else "P-$batch@$station", PrintKind.Kitchen,
                "$tableId 厨房单${if (multi) "·$station" else ""} ${lines.sumOf { it.qty }} 份",
                Receipts.kitchen(settings.shortName, tableId, lines, order(tableId).note, clock(), if (multi) station else ""), clock(), station = station)
        }
        return copy(printJobs = printJobs + withCopies(jobs))
    }

    /** 打印状态 → 厨房送达状态：已发送视为送达打印机（非出纸确认），未知须人工核对。 */
    private fun deliveryOf(s: PrintState, otherwise: DeliveryState) = when (s) {
        PrintState.Printed, PrintState.Sent -> DeliveryState.Delivered
        PrintState.Failed -> DeliveryState.Failed
        PrintState.Unknown -> DeliveryState.Unknown
        else -> otherwise
    }

    /**
     * 任务的真实目标打印机：已确定的目标优先（未知结果不改投备机），否则按档口路由；无则模拟打印。
     * 尚未发出的任务若目标打印机已停用/删除，改用该路由的备用打印机。
     */
    private fun StoreData.targetOf(j: PrintJob): PrinterConfig? {
        val id = j.printerId.ifBlank { KdsRules.printRoute(printRoutes, kdsStations, j) }
        if (id.isBlank()) return null
        printers.firstOrNull { it.id == id && it.enabled }?.let { return it }
        return if (j.copyOf.isBlank() && (j.state == PrintState.Pending || j.state == PrintState.Failed)) backupOf(j)?.takeIf { it.id != id } else null
    }

    /** 主任务加上路由配置的副本（每台副本打印机一张，固定发往该机；停用的打印机不出副本）。 */
    private fun StoreData.withCopies(jobs: List<PrintJob>): List<PrintJob> = jobs + jobs.flatMap { j ->
        KdsRules.printCopies(printRoutes, printCopies, kdsStations, j).mapNotNull { id -> printers.firstOrNull { it.id == id && it.enabled } }.map { p ->
            j.copy(id = "${j.id}~${p.id}", title = "${j.title}（副本·${p.name}）", body = listOf("** 副本 **") + j.body, printerId = p.id, copyOf = j.id)
        }
    }
    private fun StoreData.withCopies(job: PrintJob) = withCopies(listOf(job))

    private fun StoreData.backupOf(j: PrintJob): PrinterConfig? {
        val id = KdsRules.printBackup(printRoutes, printBackups, kdsStations, j)
        return printers.firstOrNull { it.id == id && it.enabled }
    }

    private fun failoverBody(j: PrintJob, from: String, to: String, why: String) =
        if (j.failoverFrom.isNotBlank()) j.body else listOf("** ${to}代打（原 $from ${why.substringBefore('：').take(16)}） **") + j.body

    private fun StoreData.withJob(id: String, f: (PrintJob) -> PrintJob): StoreData {
        val jobs = printJobs.map { if (it.id == id) f(it) else it }
        val j = jobs.first { it.id == id }
        return copy(printJobs = jobs, kitchenDeliveries = kitchenDeliveries.map { d ->
            if (d.jobId != id || d.state == DeliveryState.ManualConfirmed) d
            else d.copy(state = deliveryOf(j.state, d.state), attempts = j.attempts, lastError = j.error, updatedAt = clock(), source = if (j.printerId.isNotBlank()) "printer" else d.source)
        })
    }

    // —— 网口打印网关（N6）：领取→锁外发送→回写；仅主 POS/单机 ——
    /** 领取下一张待发的真实打印任务并持久化为发送中（崩溃后据此判为结果未知）。 */
    suspend fun claimNextPrint(): Pair<PrintJob, PrinterConfig>? {
        if (blockedState.value != null || state.value.lan.role == LanRole.Secondary) return null
        return transaction { v ->
            val s = v.store ?: return@transaction v to null
            val now = clock()
            val j = s.printJobs.sortedBy { it.createdAt }.firstOrNull { (it.state == PrintState.Pending || (it.state == PrintState.Failed && it.nextAt <= now)) && s.targetOf(it) != null }
                ?: return@transaction v to null
            val p = s.targetOf(j)!!
            // 主打印机已停用/删除而改用备用：记下原打印机，便于队列显示
            val primary = j.printerId.ifBlank { KdsRules.printRoute(s.printRoutes, s.kdsStations, j) }
            val next = s.withJob(j.id) { it.copy(state = PrintState.Sending, printerId = p.id, attempts = it.attempts + 1, updatedAt = now,
                failoverFrom = if (it.failoverFrom.isBlank() && primary.isNotBlank() && primary != p.id) primary else it.failoverFrom) }
                .log(TaskLogKind.PrintAttempt, j.id, "Sending", "printer", "${p.name} ${p.host}:${p.port}")
            v.copy(stores = v.stores + (s.id to next)) to (next.printJobs.first { it.id == j.id } to p)
        }
    }

    /** 回写发送结果：未发出→失败并退避重试；已发出→已发送；中断/报错→结果未知（不自动重打）。 */
    suspend fun finishPrint(jobId: String, outcome: ai.neargo.fnb.pos.core.print.SendOutcome) {
        commit { v ->
            val s = v.store ?: return@commit v
            val j = s.printJobs.firstOrNull { it.id == jobId && it.state == PrintState.Sending } ?: return@commit v
            val now = clock()
            val (st, err) = when (outcome) {
                is ai.neargo.fnb.pos.core.print.SendOutcome.NotSent -> PrintState.Failed to outcome.reason
                is ai.neargo.fnb.pos.core.print.SendOutcome.Sent -> PrintState.Sent to (if (outcome.statusChecked) "" else "打印机未回报状态")
                is ai.neargo.fnb.pos.core.print.SendOutcome.Unknown -> PrintState.Unknown to outcome.reason
            }
            val backoff = minOf(60_000L, 2_000L shl minOf(j.attempts, 5))
            val firstFail = if (st == PrintState.Failed && j.firstFailAt == 0L) now else j.firstFailAt
            // 确定未发出且达到阈值：改投备用打印机（只改投一次；结果未知不改投，避免两台各出一张）
            val backup = if (st == PrintState.Failed && j.failoverFrom.isBlank() && j.copyOf.isBlank()) s.backupOf(j)?.takeIf { it.id != j.printerId } else null
            if (backup != null && (j.attempts >= KdsRules.FAILOVER_ATTEMPTS || now - firstFail >= KdsRules.FAILOVER_MS)) {
                val from = s.printers.firstOrNull { it.id == j.printerId }?.name ?: j.printerId
                return@commit v.copy(stores = v.stores + (s.id to s.withJob(jobId) {
                    it.copy(state = PrintState.Pending, printerId = backup.id, failoverFrom = j.printerId, firstFailAt = firstFail, nextAt = 0, updatedAt = now,
                        error = "已改投备用 ${backup.name}（$from：$err）", body = failoverBody(j, from, "备用机", err))
                }.log(TaskLogKind.PrintAttempt, jobId, "FailedOver", "printer", "$from → ${backup.name}：$err")))
            }
            v.copy(stores = v.stores + (s.id to s.withJob(jobId) { it.copy(state = st, error = err, nextAt = if (st == PrintState.Failed) now + backoff else 0, firstFailAt = firstFail, updatedAt = now) }
                .log(TaskLogKind.PrintAttempt, jobId, st.name, "printer", err)))
        }
    }

    /** 人工核对结果未知的任务：已出纸 → 记为已打印；未出纸 → 原任务作废并生成关联补打。记录人员与原因。 */
    override suspend fun resolveUnknown(jobId: String, printed: Boolean, reason: String) = storeOp(check = { s ->
        val j = s.printJobs.firstOrNull { it.id == jobId }
        when {
            need(Perm.Checkout) != null -> need(Perm.Checkout)
            j == null -> "打印任务不存在"
            j.state != PrintState.Unknown -> "只有结果未知的任务需要核对"
            reason.isBlank() -> "请填写核对说明"
            else -> null
        }
    }) { s ->
        val actor = actorName(state.value)
        val j = s.printJobs.first { it.id == jobId }
        if (printed) s.withJob(jobId) { it.copy(state = PrintState.Printed, resolution = "$actor：已出纸 · ${reason.trim()}", updatedAt = clock()) }
            .log(TaskLogKind.PrintAttempt, jobId, "ConfirmedPrinted", "manual", "$actor：${reason.trim()}")
        else {
            val copy = j.copy(id = newId("RP"), title = "${j.title}（补打）", body = listOf("** 补打 **") + j.body, createdAt = clock(), state = PrintState.Pending,
                attempts = 0, reprint = true, error = "", reprintOf = j.id, resolution = "", nextAt = 0)
            val base = s.withJob(jobId) { it.copy(state = PrintState.Failed, error = "人工确认未出纸，已补打", resolution = "$actor：未出纸 · ${reason.trim()}", nextAt = Long.MAX_VALUE, updatedAt = clock()) }
            base.copy(printJobs = base.printJobs + copy, kitchenDeliveries = base.kitchenDeliveries.map { if (it.jobId == jobId) it.copy(supersededBy = copy.id) else it })
                .log(TaskLogKind.PrintReprinted, copy.id, "queued", "manual", "原任务 $jobId 未出纸：$actor")
        }
    }

    override suspend fun savePrinter(p: PrinterConfig) = storeOp(check = { s ->
        when {
            need(Perm.Settings) != null -> need(Perm.Settings)
            p.name.isBlank() -> "请填写打印机名称"
            !Regex("^[A-Za-z0-9.-]{1,253}$").matches(p.host.trim()) -> "请填写打印机 IP 或主机名"
            p.port !in 1..65535 -> "端口应为 1–65535"
            p.width !in setOf(32, 48) -> "纸宽应为 58mm（32 列）或 80mm（48 列）"
            s.printers.any { it.id != p.id && it.name == p.name.trim() } -> "打印机名称重复"
            else -> null
        }
    }) { s -> val q = p.copy(name = p.name.trim(), host = p.host.trim()); s.copy(printers = s.printers.filterNot { it.id == q.id } + q) }

    override suspend fun removePrinter(id: String) = storeOp(check = { s ->
        need(Perm.Settings) ?: if (s.printJobs.any { it.printerId == id && it.state in setOf(PrintState.Sending, PrintState.Unknown) }) "该打印机有发送中或结果未知的任务，请先核对" else null
    }) { s -> s.copy(printers = s.printers.filterNot { it.id == id }, printRoutes = s.printRoutes.filterValues { it != id }, printBackups = s.printBackups.filterValues { it != id },
        printCopies = s.printCopies.mapValues { (_, l) -> l - id }.filterValues { it.isNotEmpty() }) }

    override suspend fun setRoute(station: String, printerId: String?) = storeOp(check = { s ->
        need(Perm.Settings) ?: if (printerId != null && s.printers.none { it.id == printerId }) "打印机不存在" else null
    }) { s -> s.copy(printRoutes = if (printerId == null) s.printRoutes - station else s.printRoutes + (station to printerId)) }

    /** 路由的备用打印机（门店设置权限）；不能与主打印机相同。 */
    override suspend fun setBackup(station: String, printerId: String?) = storeOp(check = { s ->
        need(Perm.Settings) ?: when {
            printerId == null -> null
            s.printers.none { it.id == printerId } -> "打印机不存在"
            s.printRoutes[station] == printerId -> "备用打印机不能与主打印机相同"
            else -> null
        }
    }) { s -> s.copy(printBackups = if (printerId == null) s.printBackups - station else s.printBackups + (station to printerId)) }

    /** 路由的副本打印机（门店设置权限）：最多 3 台，不能含主打印机。 */
    override suspend fun setCopies(station: String, printerIds: List<String>) = storeOp(check = { s ->
        need(Perm.Settings) ?: when {
            printerIds.size > KdsRules.MAX_COPIES -> "副本打印机最多 ${KdsRules.MAX_COPIES} 台"
            printerIds.any { id -> s.printers.none { it.id == id } } -> "打印机不存在"
            s.printRoutes[station] != null && s.printRoutes[station] in printerIds -> "副本打印机不能与主打印机相同"
            else -> null
        }
    }) { s -> s.copy(printCopies = if (printerIds.isEmpty()) s.printCopies - station else s.printCopies + (station to printerIds.distinct())) }

    /** 手动改投：失败或排队中的任务改由指定打印机打印（结果未知须先核对）。 */
    override suspend fun redirectPrint(jobId: String, printerId: String) = storeOp(check = { s ->
        val j = s.printJobs.firstOrNull { it.id == jobId }
        val p = s.printers.firstOrNull { it.id == printerId }
        when {
            need(Perm.Checkout) != null -> need(Perm.Checkout)
            j == null -> "打印任务不存在"
            j.state == PrintState.Unknown -> "结果未知的任务请先核对，避免重复出单"
            j.state != PrintState.Failed && j.state != PrintState.Pending -> "只有失败或排队中的任务可以改投"
            p == null || !p.enabled -> "目标打印机不存在或已停用"
            s.targetOf(j)?.id == printerId -> "已经是这台打印机"
            else -> null
        }
    }) { s ->
        val j = s.printJobs.first { it.id == jobId }
        val p = s.printers.first { it.id == printerId }
        val from = s.targetOf(j)?.name ?: "模拟打印"
        s.withJob(jobId) {
            it.copy(state = PrintState.Pending, printerId = p.id, failoverFrom = j.printerId.ifBlank { "manual" }, nextAt = 0, error = "已手动改投 ${p.name}", updatedAt = clock(),
                body = failoverBody(j, from, p.name, "手动改投"))
        }.log(TaskLogKind.PrintAttempt, jobId, "Redirected", "manual", "$from → ${p.name}：${actorName(state.value)}")
    }

    override suspend fun testPrint(printerId: String) = storeOp(check = { s -> need(Perm.Settings) ?: if (s.printers.none { it.id == printerId }) "打印机不存在" else null }) { s ->
        val p = s.printers.first { it.id == printerId }
        val w = p.width
        val body = listOf("打印测试", "-".repeat(w), "打印机：${p.name}", "地址：${p.host}:${p.port}", "纸宽：${if (w == 32) "58mm" else "80mm"}", "中文：南山小馆 · 宫保鸡丁 ¥48.00", "-".repeat(w),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(clock())))
        s.copy(printJobs = s.printJobs + PrintJob(newId("T"), PrintKind.Test, "测试页 ${p.name}", body, clock(), printerId = p.id))
    }

    /** 为缺少送达记录的厨房任务补记录；状态取自任务当前结果。 */
    private fun ensureDeliveries(s: StoreData, source: String): StoreData {
        val known = s.kitchenDeliveries.map { it.jobId }.toSet()
        val missing = s.printJobs.filter { it.kind == PrintKind.Kitchen && it.id !in known && it.copyOf.isBlank() }
        if (missing.isEmpty()) return s
        return s.copy(kitchenDeliveries = s.kitchenDeliveries + missing.map { j ->
            val table = j.title.substringBefore(' ')
            // 补打沿用原单所属的任务（ticketId）与替代关系，汇总时归到同一张卡
            val origin = s.kitchenDeliveries.firstOrNull { it.jobId == j.reprintOf && j.reprintOf.isNotBlank() }
            KitchenDelivery(
                ticketId = origin?.ticketId ?: j.id.removePrefix("P-"), jobId = j.id, tableId = table, orderId = origin?.orderId ?: s.orders[table]?.orderId.orEmpty(), label = j.title,
                station = j.station, covers = origin?.covers.orEmpty(),
                target = if (j.station.isNotBlank() && s.kdsStations.size > 1) "${j.station}打印机" else "厨房打印机",
                state = deliveryOf(j.state, DeliveryState.Pending),
                source = source, attempts = j.attempts, lastError = j.error, createdAt = j.createdAt, updatedAt = clock(),
            )
        })
    }

    override val deliveries: StateFlow<List<KitchenDelivery>> = derive { it.store?.kitchenDeliveries.orEmpty().sortedByDescending { d -> d.createdAt } }
    override val deliveryGroups: StateFlow<List<TicketDelivery>> = derive { v -> v.store?.let { DeliveryRules.groups(it) }.orEmpty() }

    /** 人工确认送达：只用于失败/未知/待出单，必须填写原因，记录人员。 */
    override suspend fun confirmDelivery(ticketId: String, reason: String) = storeOp(check = { s ->
        val open = s.kitchenDeliveries.filter { it.ticketId == ticketId && it.supersededBy.isBlank() && it.state != DeliveryState.Delivered && it.state != DeliveryState.ManualConfirmed }
        when {
            s.kitchenDeliveries.none { it.ticketId == ticketId } -> "送达记录不存在"
            open.isEmpty() -> "已送达，无需确认"
            reason.isBlank() -> "请填写确认原因"
            reason.length > 60 -> "原因最多 60 字"
            else -> null
        }
    }) { s ->
        val actor = actorName(state.value)
        s.copy(kitchenDeliveries = s.kitchenDeliveries.map {
            if (it.ticketId == ticketId && it.supersededBy.isBlank() && it.state != DeliveryState.Delivered && it.state != DeliveryState.ManualConfirmed) it.copy(state = DeliveryState.ManualConfirmed, source = "manual", confirmedBy = actor, reason = reason.trim(), updatedAt = clock()) else it
        }).log(TaskLogKind.PrintAttempt, ticketId, "ManualConfirmed", "manual", "${actor}：${reason.trim()}")
    }

    override suspend fun preBill(tableId: String) = storeOp(check = { s ->
        if (s.order(tableId).lines.isEmpty()) "$tableId 没有菜品" else null
    }) { s ->
        val job = PrintJob(newId("B"), PrintKind.PreBill, "$tableId 预结单", Receipts.bill("预结单", s, tableId, s.order(tableId), clock()), clock())
        s.copy(printJobs = s.printJobs + s.withCopies(job)).log(TaskLogKind.PrintRequested, job.id, "queued", "local", tableId)
    }

    override suspend fun reprint(jobId: String) = storeOp(check = { s -> if (s.printJobs.none { it.id == jobId }) "打印任务不存在" else null }) { s ->
        val j = s.printJobs.first { it.id == jobId }
        val copy = j.copy(id = newId("RP"), title = "${j.title}（补打）", body = listOf("** 补打 **") + j.body, createdAt = clock(), state = PrintState.Pending, attempts = 0, reprint = true, error = "",
            reprintOf = j.id, resolution = "", nextAt = 0, printerId = "")
        s.copy(printJobs = s.printJobs + copy).log(TaskLogKind.PrintReprinted, copy.id, "queued", "local", "原任务 ${j.id}")
    }

    override suspend fun process(kitchenOk: Boolean): Int {
        if (blockedState.value != null || state.value.lan.role == LanRole.Secondary) return 0
        var failed = 0
        val s = currentStore() ?: return 0
        if (s.printJobs.none { (it.state == PrintState.Pending || it.state == PrintState.Failed) && s.targetOf(it) == null }) return 0
        storeOp { st ->
            var logged = st
            val jobs = st.printJobs.map { j ->
                when {
                    // 有真实打印机的任务由网关处理；已有结果的任务不再模拟
                    st.targetOf(j) != null || j.state !in setOf(PrintState.Pending, PrintState.Failed) -> j
                    j.state == PrintState.Printed -> j
                    j.kind.printer == "厨房" && !kitchenOk -> {
                        if (j.state == PrintState.Pending) failed++
                        j.copy(state = PrintState.Failed, attempts = j.attempts + if (j.state == PrintState.Pending) 1 else 0, error = "厨房打印机缺纸")
                    }
                    else -> j.copy(state = PrintState.Printed, attempts = j.attempts + 1, error = "")
                }.also { n -> if (n != j && n.state != j.state) logged = logged.log(TaskLogKind.PrintAttempt, j.id, n.state.name, "simulation", n.error) }
            }
            val byJob = jobs.associateBy { it.id }
            logged.copy(printJobs = jobs, kitchenDeliveries = logged.kitchenDeliveries.map { d ->
                val j = byJob[d.jobId]
                if (j == null || d.state == DeliveryState.ManualConfirmed) d
                else {
                    val st = deliveryOf(j.state, d.state)
                    if (st == d.state && j.attempts == d.attempts) d else d.copy(state = st, attempts = j.attempts, lastError = j.error, updatedAt = clock(), source = "simulation")
                }
            })
        }.getOrThrow()
        return failed
    }

    override fun billPreview(tableId: String): List<String> {
        val s = currentStore() ?: return emptyList()
        return Receipts.bill("预结单预览", s, tableId, s.order(tableId), clock())
    }

    // —— 交班 ——
    override suspend fun closeShift(counted: Long, staff: String): Result<ShiftRecord> = persistenceResult {
        transaction(EventSpec { b, a -> Triple("ShiftClosed", ManagementEventFactory.shift(b, a), listOf(Aggregates.SHIFT)) }) { v ->
            if (!Money.withinBill(counted)) return@transaction v to Result.failure(IllegalArgumentException("实盘金额应为非负且不超过上限"))
            val s = v.store ?: return@transaction v to Result.failure(IllegalStateException("请先选择门店"))
            val sum = ShiftRules.summary(s.payments, s.shift, s.adjustments)
            val start = if (s.shift.start > 0) s.shift.start else s.payments.minOfOrNull { it.at } ?: clock()
            val rec = ShiftRecord(newId("S"), start, clock(), staff, sum.total, sum.count, sum.byMethod, s.shift.float, sum.cashExpected, counted, sum.voidAmount, sum.voidCount, sum.giftAmount, sum.giftCount)
            val next = s.copy(
                shift = ShiftState(start = clock(), float = s.shift.float, history = s.shift.history + rec),
                printJobs = s.printJobs + s.withCopies(PrintJob(newId("SH"), PrintKind.Shift, "交班单 $staff", Receipts.shift(rec), clock())),
            )
            v.copy(stores = v.stores + (s.id to next)) to Result.success(rec)
        }
    }

    // —— 订单 ——
    private suspend fun mutate(tableId: String, event: EventSpec? = null, rule: (StoreData, TableOrder) -> OrderResult): OrderResult = try {
        transaction(event) { v ->
            val s = v.store ?: return@transaction v to OrderResult.Rejected("请先选择门店")
            if (s.table(tableId)?.status != TableStatus.Dining) return@transaction v to OrderResult.Rejected("$tableId 未开台，请先在桌台开台")
            val result = rule(s, s.order(tableId))
            val next = if (result is OrderResult.Ok && result.order != s.order(tableId)) v.copy(stores = v.stores + (s.id to s.withOrder(tableId, result.order).copy(seq = s.seq + 1))) else v
            next to result
        }
    } catch (e: WorkspaceWriteException) { OrderResult.Rejected(requireNotNull(e.message)) }
    catch (e: WorkspaceRejectedException) { OrderResult.Rejected(requireNotNull(e.message)) }

    override suspend fun addDish(tableId: String, product: Product, sku: Sku?, qty: Int, note: String, mealChoiceIds: List<String>) =
        mutate(tableId, page("DishAdded", tableId)) { s, o -> OrderRules.add(o, product, sku, qty, s.seq, note, mealChoiceIds) }

    override suspend fun setBenefits(tableId: String, coupon: Boolean, activity: Boolean) = storeOp(event = page("OrderBenefitsChanged", tableId), check = { s ->
        val sub = OrderRules.totals(s.order(tableId)).sub
        when {
            s.table(tableId)?.status != TableStatus.Dining -> "请先开台"
            coupon && sub < 20000 -> "菜品小计未满 200 元"
            activity && sub < 10000 -> "菜品小计未满 100 元"
            else -> null
        }
    }) { s -> s.withOrder(tableId, s.order(tableId).copy(coupon = coupon, activity = activity)) }

    override suspend fun authorizeAdjustment(tableId: String, request: AdjustmentRequest, email: String, password: String): Result<Unit> = runCatching {
        val type = when (request.kind) {
            AdjustmentKind.Void -> "DishVoided"
            AdjustmentKind.Gift -> "DishGifted"
            AdjustmentKind.Discount -> "OrderDiscounted"
        }
        commit(page(type, tableId)) { v ->
            val account = v.accounts.firstOrNull { it.supervisor && it.email.equals(email.trim(), true) }
            val demo = v.user == "体验账号" && email.trim() == "manager@demo.local" && password == "demo12345"
            // 员工模式：主管/店长以姓名（或员工ID）+ PIN 授权，需具备「退菜、赠送、折扣授权」权限
            val emp = v.store?.employees?.firstOrNull { it.enabled && (it.id == email.trim() || it.name == email.trim()) }
            val empOk = emp != null && Perm.Adjust in Roles.perms(emp.role) && ManageRules.hash(password, emp.salt) == emp.pinHash
            require(empOk || demo || (account != null && ManageRules.hash(password, account.salt) == account.hash)) {
                if (emp != null && Perm.Adjust !in Roles.perms(emp.role)) "${emp.name}（${emp.role.label}）无授权权限" else "主管账号或密码（PIN）不正确"
            }
            val s = v.store ?: error("请先选择门店")
            require(s.table(tableId)?.status == TableStatus.Dining) { "请先开台" }
            val old = s.order(tableId)
            val result = AdjustmentRules.apply(old, request)
            val updated = (result as? OrderResult.Ok)?.order ?: error((result as OrderResult.Rejected).reason)
            val line = old.lines.firstOrNull { it.uid == request.uid }
            val amount = when (request.kind) {
                AdjustmentKind.Gift -> (line?.unit ?: 0) * (line?.qty ?: 0)
                AdjustmentKind.Void -> if (line?.gifted == true) 0 else (line?.unit ?: 0) * request.quantity
                AdjustmentKind.Discount -> OrderRules.totals(old).due - OrderRules.totals(updated).due
            }
            val audit = AdjustmentAudit(newId("A"), tableId, request.kind, actorName(v), emp?.takeIf { empOk }?.name ?: email.trim(), request.reason.trim(), amount,
                if (request.kind == AdjustmentKind.Gift) line?.qty ?: 0 else if (request.kind == AdjustmentKind.Void) request.quantity else 0,
                clock(), line?.name.orEmpty(), if (request.kind == AdjustmentKind.Discount) "${request.percent}%" else "")
            var next = s.withOrder(tableId, updated).copy(adjustments = s.adjustments + audit)
            if (request.kind == AdjustmentKind.Void && updated.lines.isEmpty()) next = next.withTable(tableId) { it.copy(status = TableStatus.Cleaning, guests = 0, openedAt = 0, paid = 0) }
            if (request.kind == AdjustmentKind.Void && line?.batchId != null) next = next.kdsVoid(line.batchId, line.uid, request.quantity, audit.reason)
            if (request.kind == AdjustmentKind.Void) {
                val station = line?.let { KdsRules.stationFor(s, it.dishId) }.orEmpty()
                val tag = if (s.kdsStations.size > 1 && station.isNotBlank()) "·$station" else ""
                next = next.copy(printJobs = next.printJobs + next.withCopies(PrintJob(newId("V"), PrintKind.Kitchen,
                    "$tableId 退菜单$tag", listOf("【退菜单$tag】$tableId", "${line?.name} ×${request.quantity}", line?.spec.orEmpty(), "原因：${audit.reason}", "授权：${audit.supervisor}"), clock(), station = station)))
            }
            v.copy(stores = v.stores + (s.id to next))
        }
        Unit
    }

    override suspend fun setLineNote(tableId: String, uid: Long, note: String) = mutate(tableId, page("DishNoteChanged", tableId)) { _, o -> OrderRules.setNote(o, uid, note) }

    override suspend fun setOrderNote(tableId: String, note: String) = storeOp(event = page("OrderNoteChanged", tableId), check = { s ->
        if (s.table(tableId)?.status != TableStatus.Dining) "$tableId 未在用餐" else if (note.length > 60) "整单备注最多 60 字" else null
    }) { it.withOrder(tableId, it.order(tableId).copy(note = note.trim())) }

    override suspend fun hold(tableId: String): Result<Int> = persistenceResult {
        transaction(page("OrderHeld", tableId)) { v ->
            val s = v.store ?: return@transaction v to Result.failure(IllegalStateException("请先选择门店"))
            val draft = s.order(tableId).pending
            if (draft.isEmpty()) return@transaction v to Result.failure(IllegalStateException("没有可挂起的新加菜品"))
            val next = s.withOrder(tableId, OrderRules.clearNew(s.order(tableId)))
                .copy(held = s.held + HeldTicket(newId("H"), tableId, draft, s.order(tableId).note, clock()))
            v.copy(stores = v.stores + (s.id to next)) to Result.success(draft.sumOf { it.qty })
        }
    }

    override suspend fun retrieve(ticketId: String, tableId: String) = storeOp(event = page("OrderRetrieved", tableId), check = { s ->
        when {
            s.held.none { it.id == ticketId } -> "挂单不存在或已取回"
            s.table(tableId)?.status != TableStatus.Dining -> "$tableId 未在用餐，请先开台"
            else -> null
        }
    }) { s ->
        val t = s.held.first { it.id == ticketId }
        var seq = s.seq
        val lines = t.lines.map { it.copy(uid = seq++, status = LineStatus.New, batchId = null) }
        val o = s.order(tableId)
        val at = o.lines.indexOfLast { it.status == LineStatus.New } + 1
        s.withOrder(tableId, o.copy(lines = o.lines.take(at) + lines + o.lines.drop(at))).copy(held = s.held - t, seq = seq)
    }

    override suspend fun changeQty(tableId: String, uid: Long, delta: Int) = mutate(tableId, page("DishQuantityChanged", tableId)) { s, o ->
        val line = o.lines.firstOrNull { it.uid == uid } ?: return@mutate OrderResult.Rejected("菜品已不在账单中")
        val p = s.product(line.dishId) ?: return@mutate OrderResult.Rejected("${line.name}已下架")
        OrderRules.changeQty(o, uid, delta, p)
    }

    override suspend fun clearNew(tableId: String) {
        storeOp(event = page("OrderDraftCleared", tableId), check = { s -> if (s.table(tableId)?.status != TableStatus.Dining) "$tableId 未在用餐" else null }) {
            it.withOrder(tableId, OrderRules.clearNew(it.order(tableId)))
        }.orReject()
    }

    override suspend fun sendToKitchen(tableId: String, online: Boolean): SendResult = transaction(page("KitchenSubmitted", tableId)) { v ->
        val s = v.store ?: return@transaction v to SendResult(0, false)
        val n = s.order(tableId).pending.sumOf { it.qty }
        if (n == 0) return@transaction v to SendResult(0, false)
        val batch = newId("K-${s.id}")
        val out = if (online) s.outbox else s.outbox + OutboxItem(batch, OutboxKind.Kitchen, tableId, "$tableId 送厨 $n 份", clock())
        val sent = OrderRules.send(s.order(tableId), batch, online, clock())
        // 厨房单走本机/局域网，不依赖云端：离线也立即出单；云端待同步另由 outbox 与事件表示
        // 按档口拆任务卡：单档口卡号即批次号，多档口为「批次@档口」；各卡独立接单与制作
        val groups = sent.lines.filter { it.batchId == batch }.groupBy { KdsRules.stationFor(s, it.dishId) }
        var seq = s.kdsSeq
        val tickets = groups.entries.sortedBy { s.kdsStations.indexOf(it.key) }.map { (station, ls) ->
            KdsTicket(if (groups.size == 1) batch else "$batch@$station", tableId, sent.orderId, station, ls.map { KdsLine(it.uid, it.name, it.spec, it.note, it.qty) },
                clock(), seq = ++seq, updatedAt = clock(), note = sent.note)
        }
        // 出单方式含厨房屏的档口：同时生成厨房屏送达记录（回执即送达，超时未收到为失败）
        val screens = tickets.filter { DeliveryRules.modeOf(s, it.station) != DeliveryMode.Paper }.map { t ->
            KitchenDelivery(t.id, "S-${t.id}", tableId, sent.orderId, "$tableId 厨房屏·${t.station} ${t.lines.sumOf { it.qty }} 份", target = "${t.station}厨房屏",
                source = "screen", createdAt = clock(), updatedAt = clock(), channel = "screen", station = t.station)
        }
        val next = s.withOrder(tableId, sent).copy(outbox = out, seq = s.seq + 1, kdsTickets = s.kdsTickets + tickets, kdsSeq = seq,
            kitchenDeliveries = s.kitchenDeliveries + screens).kitchenJob(tableId, batch)
        v.copy(stores = v.stores + (s.id to next)) to SendResult(n, !online)
    }

    override suspend fun advance(tableId: String, uid: Long, to: LineStatus) {
        val type = if (to == LineStatus.Rushing) "DishRushed" else "DishServed"
        storeOp(event = page(type, tableId), check = { s -> if (s.table(tableId)?.status != TableStatus.Dining) "$tableId 未在用餐" else null }) {
            it.withOrder(tableId, OrderRules.advance(it.order(tableId), uid, to))
        }.orReject()
    }
    override suspend fun setMember(tableId: String, member: Boolean) {
        storeOp(event = page("OrderMemberChanged", tableId), check = { s -> if (s.table(tableId)?.status != TableStatus.Dining) "$tableId 未在用餐" else null }) {
            it.withOrder(tableId, it.order(tableId).copy(member = member))
        }.orReject()
    }

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
