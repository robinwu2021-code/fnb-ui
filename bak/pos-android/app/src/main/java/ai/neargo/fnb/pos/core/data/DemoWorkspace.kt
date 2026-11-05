package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.Area
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.Dish
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.OrderRules
import ai.neargo.fnb.pos.core.model.Session
import ai.neargo.fnb.pos.core.model.TableOrder
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 门店目录演示实现：静态数据。 */
class DemoMenuRepository : MenuRepository {
    override val categories = DemoCatalog.categories
    override val dishes = DemoCatalog.dishes
    override val specs = DemoCatalog.specs
    override val areas = DemoCatalog.areas
    private val byId = dishes.associateBy { it.id }
    private val areaById = areas.associateBy { it.id }
    override fun dish(id: String) = byId.getValue(id)
    override fun area(id: String): Area = areaById.getValue(id)
}

/** 工作台快照：桌台、各桌订单、当前桌台、行 ID 序列。用于本地持久化与重启恢复。 */
@Serializable
data class Workspace(
    val tables: List<DiningTable>,
    val orders: Map<String, TableOrder>,
    val currentTableId: String,
    val seq: Long,
) {
    companion object {
        fun demo(): Workspace {
            val (orders, seq) = DemoCatalog.orders()
            return Workspace(DemoCatalog.tables, orders, DemoCatalog.START_TABLE, seq)
        }
    }
}

/** 快照存储：生产用 DataStore，测试用内存实现。 */
interface SnapshotStore {
    suspend fun load(): String?
    suspend fun save(json: String)
}

/**
 * 演示工作台：同一份快照同时实现会话 / 桌台 / 订单三个接口，保证开台、结账跨实体操作原子完成。
 * 每次修改后串行写入快照；进程重启后恢复草稿、送厨状态与当前桌台。
 * 正式服务接入时三者分别对接会话、门店目录、订单服务，界面不受影响。
 */
class DemoWorkspace(
    private val store: SnapshotStore,
    private val menu: MenuRepository,
    private val scope: CoroutineScope,
) : SessionRepository, FloorRepository, OrderRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(Workspace.demo())
    private val writeLock = Mutex()

    /** 首次读取快照；无快照或解析失败时使用演示初始数据。 */
    suspend fun restore() {
        val saved = store.load() ?: return
        runCatching { json.decodeFromString(Workspace.serializer(), saved) }.onSuccess { state.value = it }
    }

    suspend fun resetDemo() = commit { Workspace.demo() }

    override val session: StateFlow<Session> = state.map { Session(DemoCatalog.STORE, DemoCatalog.STAFF, it.currentTableId) }
        .stateIn(scope, SharingStarted.Eagerly, Session(DemoCatalog.STORE, DemoCatalog.STAFF, state.value.currentTableId))
    override val tables: StateFlow<List<DiningTable>> = state.map { it.tables }.stateIn(scope, SharingStarted.Eagerly, state.value.tables)
    override val orders: StateFlow<Map<String, TableOrder>> = state.map { it.orders }.stateIn(scope, SharingStarted.Eagerly, state.value.orders)

    /** 原子修改并持久化。 */
    private suspend fun commit(f: (Workspace) -> Workspace): Workspace {
        val next = writeLock.withLock {
            val n = f(state.value)
            state.value = n
            n
        }
        // 写入时取最新快照，后发起的保存不会被先发起的旧快照覆盖
        scope.launch { writeLock.withLock { store.save(json.encodeToString(Workspace.serializer(), state.value)) } }
        return next
    }

    private fun Workspace.order(t: String) = orders[t] ?: TableOrder()
    private fun Workspace.table(t: String) = tables.firstOrNull { it.id == t }
    private fun Workspace.withOrder(t: String, o: TableOrder) = copy(orders = orders + (t to o))
    private fun Workspace.withTable(t: String, f: (DiningTable) -> DiningTable) = copy(tables = tables.map { if (it.id == t) f(it) else it })

    // —— 会话 ——
    override suspend fun selectTable(tableId: String) { commit { it.copy(currentTableId = tableId) } }

    // —— 桌台 ——
    override suspend fun openTable(tableId: String, guests: Int): Result<Unit> {
        val t = state.value.table(tableId) ?: return Result.failure(IllegalArgumentException("桌台不存在"))
        if (t.status != TableStatus.Idle && t.status != TableStatus.Reserved) return Result.failure(IllegalStateException("$tableId ${t.status.label}，不能开台"))
        if (guests !in 1..t.seats) return Result.failure(IllegalArgumentException("人数应为 1–${t.seats}"))
        commit { ws ->
            ws.withTable(tableId) { it.copy(status = TableStatus.Dining, guests = guests, minutes = 0, reservation = "", paid = 0) }
                .withOrder(tableId, TableOrder()).copy(currentTableId = tableId)
        }
        return Result.success(Unit)
    }

    override suspend fun settle(tableId: String, amount: Int): Result<Unit> {
        val ws = state.value
        if (ws.order(tableId).pending.isNotEmpty()) return Result.failure(IllegalStateException("还有未送厨菜品，先送厨再结账"))
        commit { it.withTable(tableId) { t -> t.copy(status = TableStatus.Paid, paid = amount) }.withOrder(tableId, TableOrder()) }
        return Result.success(Unit)
    }

    override suspend fun startCleaning(tableId: String): Result<Unit> = transition(tableId, TableStatus.Paid) { it.copy(status = TableStatus.Cleaning) }
    override suspend fun finishCleaning(tableId: String): Result<Unit> =
        transition(tableId, TableStatus.Cleaning) { it.copy(status = TableStatus.Idle, guests = 0, minutes = 0, paid = 0) }

    private suspend fun transition(tableId: String, from: TableStatus, f: (DiningTable) -> DiningTable): Result<Unit> {
        val t = state.value.table(tableId) ?: return Result.failure(IllegalArgumentException("桌台不存在"))
        if (t.status != from) return Result.failure(IllegalStateException("$tableId 当前为${t.status.label}"))
        commit { it.withTable(tableId, f) }
        return Result.success(Unit)
    }

    // —— 订单 ——
    private suspend fun mutate(tableId: String, rule: (Workspace, TableOrder) -> OrderResult): OrderResult {
        val ws = state.value
        if (ws.table(tableId)?.status != TableStatus.Dining) return OrderResult.Rejected("$tableId 未开台，请先在桌台开台")
        val r = rule(ws, ws.order(tableId))
        if (r is OrderResult.Ok) commit { it.withOrder(tableId, r.order).copy(seq = it.seq + 1) }
        return r
    }

    override suspend fun addDish(tableId: String, dish: Dish, spec: String, unit: Int, qty: Int) =
        mutate(tableId) { ws, o -> OrderRules.add(o, dish, spec, unit, qty, ws.seq) }

    override suspend fun changeQty(tableId: String, uid: Long, delta: Int) = mutate(tableId) { ws, o ->
        val line = o.lines.firstOrNull { it.uid == uid } ?: return@mutate OrderResult.Rejected("菜品已不在账单中")
        OrderRules.changeQty(o, uid, delta, menu.dish(line.dishId))
    }

    override suspend fun clearNew(tableId: String) { commit { it.withOrder(tableId, OrderRules.clearNew(it.order(tableId))) } }

    override suspend fun sendToKitchen(tableId: String): Int {
        val n = state.value.order(tableId).pending.sumOf { it.qty }
        if (n > 0) commit { it.withOrder(tableId, OrderRules.sendAll(it.order(tableId))) }
        return n
    }

    override suspend fun advance(tableId: String, uid: Long, to: LineStatus) {
        commit { it.withOrder(tableId, OrderRules.advance(it.order(tableId), uid, to)) }
    }

    override suspend fun setMember(tableId: String, member: Boolean) {
        commit { it.withOrder(tableId, it.order(tableId).copy(member = member)) }
    }
}
