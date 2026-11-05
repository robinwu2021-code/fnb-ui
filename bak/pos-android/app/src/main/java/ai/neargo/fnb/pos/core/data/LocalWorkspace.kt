package ai.neargo.fnb.pos.core.data

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
import ai.neargo.fnb.pos.core.model.PaymentRecord
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.Session
import ai.neargo.fnb.pos.core.model.Sku
import ai.neargo.fnb.pos.core.model.SpecGroup
import ai.neargo.fnb.pos.core.model.SpecOption
import ai.neargo.fnb.pos.core.model.StoreData
import ai.neargo.fnb.pos.core.model.StoreSettings
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** 本机全部数据：账号、会话、各门店快照。持久化为一份 JSON（schema v2）。 */
@Serializable
data class Vault(
    val accounts: List<Account> = emptyList(),
    val user: String? = null,
    val storeId: String? = null,
    val stores: Map<String, StoreData>,
    val version: Int = 2,
) {
    val store get() = storeId?.let { stores[it] }
}

/** 快照存储：生产用 DataStore，测试用内存实现。 */
interface SnapshotStore {
    suspend fun load(): String?
    suspend fun save(json: String)
}

/**
 * 完整离线的本机仓库：同一份 Vault 同时实现会话 / 门店配置 / 桌台 / 订单 / 同步队列接口，
 * 保证开台、结账、补发等跨实体操作原子完成；每次修改后串行落盘，进程重启后恢复。
 * 阶段三接服务时各接口分别对接远端，界面与 ViewModel 不变。
 */
class LocalWorkspace(
    private val store: SnapshotStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : SessionRepository, StoreConfigRepository, FloorRepository, OrderRepository, SyncRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(Vault(stores = DemoCatalog.stores(clock())))
    private val writeLock = Mutex()

    /** 读取快照；无快照、旧版本或解析失败时使用演示初始数据。 */
    suspend fun restore() {
        val saved = store.load() ?: return
        runCatching { json.decodeFromString(Vault.serializer(), saved) }.onSuccess { if (it.version == 2) state.value = it }
    }

    /** 重置当前门店为演示初始数据（账号与登录状态保留）。 */
    suspend fun resetDemo() = commit { v ->
        val fresh = DemoCatalog.stores(clock())
        v.copy(stores = v.stores + (v.storeId?.let { id -> fresh[id]?.let { mapOf(id to it) } } ?: fresh))
    }

    private fun <T> derive(f: (Vault) -> T): StateFlow<T> = state.map(f).stateIn(scope, SharingStarted.Eagerly, f(state.value))

    override val session: StateFlow<Session> = derive { v ->
        val s = v.store
        Session(v.user, s?.id, s?.settings?.shortName ?: "未选择门店", DemoCatalog.STAFF, s?.currentTableId ?: "")
    }
    override val stores: StateFlow<List<StoreSummary>> = derive { v ->
        v.stores.values.map { s -> StoreSummary(s.id, s.settings.shortName, s.code, s.address, s.tables.size, s.tables.count { it.status == TableStatus.Dining }) }
    }
    override val current: StateFlow<StoreData?> = derive { it.store }
    override val tables: StateFlow<List<DiningTable>> = derive { it.store?.tables.orEmpty() }
    override val orders: StateFlow<Map<String, TableOrder>> = derive { it.store?.orders.orEmpty() }
    override val outbox: StateFlow<List<OutboxItem>> = derive { it.store?.outbox.orEmpty().sortedByDescending { o -> o.createdAt } }
    override val pendingCount: StateFlow<Int> = derive { v -> v.stores.values.sumOf { s -> s.outbox.count { it.state == OutboxState.Pending } } }

    /** 原子修改并持久化（写入时取最新快照，后发起的保存不会被旧快照覆盖）。 */
    private suspend fun commit(f: (Vault) -> Vault): Vault {
        val next = writeLock.withLock { f(state.value).also { state.value = it } }
        scope.launch { writeLock.withLock { store.save(json.encodeToString(Vault.serializer(), state.value)) } }
        return next
    }

    private fun fail(msg: String) = Result.failure<Unit>(IllegalStateException(msg))
    private fun currentStore(): StoreData? = state.value.store

    /** 修改当前门店；未选店时失败。 */
    private suspend fun storeOp(check: (StoreData) -> String? = { null }, f: (StoreData) -> StoreData): Result<Unit> {
        val s = currentStore() ?: return fail("请先选择门店")
        check(s)?.let { return fail(it) }
        commit { v -> v.copy(stores = v.stores + (s.id to f(v.stores.getValue(s.id)))) }
        return Result.success(Unit)
    }

    private fun StoreData.order(t: String) = orders[t] ?: TableOrder()
    private fun StoreData.table(t: String) = tables.firstOrNull { it.id == t }
    private fun StoreData.withOrder(t: String, o: TableOrder) = copy(orders = orders + (t to o))
    private fun StoreData.withTable(t: String, f: (DiningTable) -> DiningTable) = copy(tables = tables.map { if (it.id == t) f(it) else it })
    private fun newId(prefix: String) = "$prefix-${UUID.randomUUID().toString().take(8)}"

    // —— 会话 ——
    override suspend fun login(email: String, password: String): Result<Unit> {
        ManageRules.email(email)?.let { return fail(it) }
        ManageRules.password(password)?.let { return fail(it) }
        val acc = state.value.accounts.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) } ?: return fail("该邮箱未在本机注册，请先注册或直接体验")
        if (ManageRules.hash(password, acc.salt) != acc.hash) return fail("密码不正确")
        commit { it.copy(user = acc.email, storeId = null) }
        return Result.success(Unit)
    }

    override suspend fun register(merchant: String, email: String, password: String, confirm: String): Result<Unit> {
        ManageRules.required(merchant, "商户名称")?.let { return fail(it) }
        ManageRules.email(email)?.let { return fail(it) }
        ManageRules.password(password)?.let { return fail(it) }
        if (password != confirm) return fail("两次输入的密码不一致")
        if (state.value.accounts.any { it.email.equals(email.trim(), ignoreCase = true) }) return fail("该邮箱已注册，请直接登录")
        val salt = UUID.randomUUID().toString()
        val acc = Account(email.trim(), merchant.trim(), salt, ManageRules.hash(password, salt))
        commit { it.copy(accounts = it.accounts + acc, user = acc.email, storeId = null) }
        return Result.success(Unit)
    }

    override suspend fun enterDemo() { commit { it.copy(user = "体验账号", storeId = null) } }
    override suspend fun logout() { commit { it.copy(user = null, storeId = null) } }
    override suspend fun selectStore(storeId: String) { commit { if (storeId in it.stores) it.copy(storeId = storeId) else it } }
    override suspend fun selectTable(tableId: String) { storeOp { it.copy(currentTableId = tableId) } }

    // —— 门店配置 ——
    override suspend fun addArea(name: String, en: String) = storeOp(
        check = { s -> ManageRules.required(name, "区域名称") ?: ManageRules.unique(name, s.areas.map { it.name }, "区域") },
    ) { s -> s.copy(areas = s.areas + Area(newId("area"), name.trim(), en.trim())) }

    override suspend fun saveTable(id: String, areaId: String, seats: String, isNew: Boolean): Result<Unit> {
        val n = ManageRules.seats(seats).getOrElse { return fail(it.message!!) }
        return storeOp(check = { s ->
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

    override suspend fun addCategory(name: String, en: String) = storeOp(
        check = { s -> ManageRules.required(name, "分类名称") ?: ManageRules.unique(name, s.categories.map { it.name }, "分类") },
    ) { s -> s.copy(categories = s.categories + Category(newId("c"), name.trim(), en.trim())) }

    override suspend fun addSpecGroup(name: String, en: String, options: String): Result<Unit> {
        val opts = ManageRules.options(options).getOrElse { return fail(it.message!!) }
        val gid = newId("g")
        return storeOp(check = { s -> ManageRules.required(name, "规格组名称") ?: ManageRules.unique(name, s.specGroups.map { it.name }, "规格组") }) { s ->
            s.copy(specGroups = s.specGroups + SpecGroup(gid, name.trim(), en.trim(), opts.mapIndexed { i, (n, d) -> SpecOption("$gid-${i + 1}", n, d) }))
        }
    }

    override suspend fun saveProduct(product: Product, menuIds: Set<String>) = storeOp(check = { s ->
        ManageRules.required(product.name, "商品名称") ?: ManageRules.required(product.en, "英文名称")
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
        return storeOp(check = { s -> if (s.product(productId) == null) "商品不存在" else null }) { s ->
            s.copy(products = s.products.map { p ->
                when {
                    p.id != productId -> p
                    skuKey == null -> p.copy(stock = value)
                    else -> p.copy(skus = p.skus.map { if (it.key == skuKey) it.copy(stock = value, manualStock = true) else it })
                }
            })
        }
    }

    override suspend fun saveMenu(id: String?, name: String, en: String, productIds: List<String>) = storeOp(check = { s ->
        ManageRules.required(name, "餐牌名称") ?: ManageRules.unique(name, s.menus.filter { it.id != id }.map { it.name }, "餐牌")
            ?: if (productIds.isEmpty()) "餐牌至少包含一个商品" else null
    }) { s ->
        if (id == null) s.copy(menus = s.menus + Menu(newId("m"), name.trim(), en.trim(), productIds.distinct(), published = false))
        else s.copy(menus = s.menus.map { if (it.id == id) it.copy(name = name.trim(), en = en.trim(), productIds = productIds.distinct()) else it })
    }

    override suspend fun setPublished(menuId: String, published: Boolean) = storeOp(check = { s ->
        val m = s.menus.firstOrNull { it.id == menuId } ?: return@storeOp "餐牌不存在"
        if (published) ManageRules.canPublish(m) else null
    }) { s -> s.copy(menus = s.menus.map { if (it.id == menuId) it.copy(published = published) else it }) }

    override suspend fun saveSettings(settings: StoreSettings) = storeOp(check = { ManageRules.required(settings.shortName, "门店简称") }) { s ->
        s.copy(settings = settings.copy(shortName = settings.shortName.trim()))
    }

    // —— 桌台 ——
    override suspend fun openTable(tableId: String, guests: Int) = storeOp(check = { s ->
        val t = s.table(tableId) ?: return@storeOp "桌台不存在"
        when {
            t.status != TableStatus.Idle && t.status != TableStatus.Reserved -> "$tableId ${t.status.label}，不能开台"
            guests !in 1..t.seats -> "人数应为 1–${t.seats}"
            else -> null
        }
    }) { s ->
        s.withTable(tableId) { it.copy(status = TableStatus.Dining, guests = guests, openedAt = clock(), reservation = "", paid = 0) }
            .withOrder(tableId, TableOrder()).copy(currentTableId = tableId)
    }

    override suspend fun settle(tableId: String, amount: Int, method: String, online: Boolean): Result<Unit> {
        val pid = newId("P")
        return storeOp(check = { s -> OrderRules.checkoutBlock(OrderRules.totals(s.order(tableId))) }) { s ->
            val pay = PaymentRecord(pid, tableId, amount, method, clock(), synced = online)
            val out = if (online) s.outbox else s.outbox + OutboxItem(pid, OutboxKind.Payment, tableId, "$tableId $method 收款 ${yuan(amount)}", clock())
            s.withTable(tableId) { it.copy(status = TableStatus.Paid, paid = amount) }.withOrder(tableId, TableOrder())
                .copy(payments = s.payments + pay, outbox = out)
        }
    }

    override suspend fun startCleaning(tableId: String) = transition(tableId, TableStatus.Paid) { it.copy(status = TableStatus.Cleaning) }
    override suspend fun finishCleaning(tableId: String) = transition(tableId, TableStatus.Cleaning) { it.copy(status = TableStatus.Idle, guests = 0, openedAt = 0, paid = 0) }

    private suspend fun transition(tableId: String, from: TableStatus, f: (DiningTable) -> DiningTable) = storeOp(check = { s ->
        val t = s.table(tableId) ?: return@storeOp "桌台不存在"
        if (t.status != from) "$tableId 当前为${t.status.label}" else null
    }) { it.withTable(tableId, f) }

    // —— 订单 ——
    private suspend fun mutate(tableId: String, rule: (StoreData, TableOrder) -> OrderResult): OrderResult {
        val s = currentStore() ?: return OrderResult.Rejected("请先选择门店")
        if (s.table(tableId)?.status != TableStatus.Dining) return OrderResult.Rejected("$tableId 未开台，请先在桌台开台")
        val r = rule(s, s.order(tableId))
        if (r is OrderResult.Ok) commit { v -> v.copy(stores = v.stores + (s.id to v.stores.getValue(s.id).withOrder(tableId, r.order).let { it.copy(seq = it.seq + 1) })) }
        return r
    }

    override suspend fun addDish(tableId: String, product: Product, sku: Sku?, qty: Int) =
        mutate(tableId) { s, o -> OrderRules.add(o, product, sku, qty, s.seq) }

    override suspend fun changeQty(tableId: String, uid: Long, delta: Int) = mutate(tableId) { s, o ->
        val line = o.lines.firstOrNull { it.uid == uid } ?: return@mutate OrderResult.Rejected("菜品已不在账单中")
        val p = s.product(line.dishId) ?: return@mutate OrderResult.Rejected("${line.name}已下架")
        OrderRules.changeQty(o, uid, delta, p)
    }

    override suspend fun clearNew(tableId: String) { storeOp { it.withOrder(tableId, OrderRules.clearNew(it.order(tableId))) } }

    override suspend fun sendToKitchen(tableId: String, online: Boolean): SendResult {
        val s = currentStore() ?: return SendResult(0, false)
        val n = s.order(tableId).pending.sumOf { it.qty }
        if (n == 0) return SendResult(0, false)
        val batch = "K-${s.id}-${s.seq}-${clock()}"
        storeOp { st ->
            val out = if (online) st.outbox else st.outbox + OutboxItem(batch, OutboxKind.Kitchen, tableId, "$tableId 送厨 $n 份", clock())
            st.withOrder(tableId, OrderRules.send(st.order(tableId), batch, online)).copy(outbox = out, seq = st.seq + 1)
        }
        return SendResult(n, !online)
    }

    override suspend fun advance(tableId: String, uid: Long, to: LineStatus) { storeOp { it.withOrder(tableId, OrderRules.advance(it.order(tableId), uid, to)) } }
    override suspend fun setMember(tableId: String, member: Boolean) { storeOp { it.withOrder(tableId, it.order(tableId).copy(member = member)) } }

    // —— 同步 ——
    /**
     * 补发所有门店的待同步项（按创建顺序）。当前对接本地模拟服务：补发即成功。
     * 幂等：只处理 Pending；送厨只把该批次的待同步行改为已下单，重复补发不会重复出单。
     */
    override suspend fun syncNow(): Int {
        var done = 0
        commit { v ->
            v.copy(stores = v.stores.mapValues { (_, s) ->
                var st = s
                s.outbox.filter { it.state == OutboxState.Pending }.sortedBy { it.createdAt }.forEach { item ->
                    st = when (item.kind) {
                        OutboxKind.Kitchen -> st.withOrder(item.tableId, OrderRules.markBatchSent(st.order(item.tableId), item.id))
                        OutboxKind.Payment -> st.copy(payments = st.payments.map { if (it.id == item.id) it.copy(synced = true) else it })
                    }
                    st = st.copy(outbox = st.outbox.map { if (it.id == item.id) it.copy(state = OutboxState.Done, attempts = it.attempts + 1, doneAt = clock()) else it })
                    done++
                }
                st
            })
        }
        return done
    }
}
