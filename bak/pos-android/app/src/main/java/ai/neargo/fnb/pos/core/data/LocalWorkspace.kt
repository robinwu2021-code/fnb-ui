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
    val commandRevisions: Map<String, Long> = emptyMap(),
) {
    val store get() = storeId?.let { stores[it] }
}

/** 快照存储：应用用 SQLite，测试用内存实现。 */
interface SnapshotStore {
    suspend fun load(): String?
    suspend fun save(json: String)
    suspend fun saveCommand(source: String, command: JournalCommand) { save(source) }
}

/**
 * 完整离线的本机仓库：同一份 Vault 同时实现会话 / 门店配置 / 桌台 / 订单 / 同步队列接口，
 * 保证开台、结账、补发等跨实体操作原子完成；每次修改先串行落盘再发布状态，进程重启后恢复。
 * 阶段三接服务时各接口分别对接远端，界面与 ViewModel 不变。
 */
class LocalWorkspace(
    private val store: SnapshotStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : SessionRepository, StoreConfigRepository, FloorRepository, OrderRepository, SyncRepository, PrintRepository, ShiftRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(Vault(stores = DemoCatalog.stores(clock()).mapValues { ReservationRules.importLegacy(it.value, clock()) }))
    private val writeLock = Mutex()

    /** 读取并校验快照；只有无快照才使用演示数据，损坏或未知版本报错并保留原件。 */
    suspend fun restore() {
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
            val restored = migrated.copy(stores = migrated.stores.mapValues { ReservationRules.importLegacy(it.value, clock()) })
            withContext(NonCancellable) {
                store.save(json.encodeToString(Vault.serializer(), restored))
                state.value = restored
            }
        }
    }

    /** 重置当前门店为演示初始数据（账号与登录状态保留）。 */
    suspend fun resetDemo() = commit { v ->
        val fresh = DemoCatalog.stores(clock()).mapValues { ReservationRules.importLegacy(it.value, clock()) }
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
    override val jobs: StateFlow<List<PrintJob>> = derive { it.store?.printJobs.orEmpty().sortedByDescending { j -> j.createdAt } }
    override val pendingCount: StateFlow<Int> = derive { v -> v.stores.values.sumOf { s -> s.outbox.count { it.state == OutboxState.Pending } } }

    /** 校验和修改均在锁内执行；落盘完成才发布状态及返回成功。 */
    private suspend fun <T> transaction(event: Pair<String, String>? = null, f: (Vault) -> Pair<Vault, T>): T = writeLock.withLock {
        val (candidate, result) = f(state.value)
        var next = candidate
        if (next != state.value) withContext(NonCancellable) {
            if (event != null) {
                val storeId = requireNotNull(next.storeId)
                val revision = state.value.commandRevisions[storeId] ?: 0L
                next = next.copy(commandRevisions = next.commandRevisions + (storeId to revision + 1))
                val data = next.stores.getValue(storeId)
                val payload = BusinessEventFactory.encode(
                    BusinessEventFactory.build(event.first, event.second, state.value.stores.getValue(storeId), data),
                )
                val command = JournalCommand(storeId, "local-page", UUID.randomUUID().toString(), "page-order-stream", revision, event.first, payload)
                try { store.saveCommand(json.encodeToString(Vault.serializer(), next), command) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { throw WorkspaceWriteException(e) }
            } else saveSnapshot(store, json.encodeToString(Vault.serializer(), next))
            state.value = next
        }
        result
    }

    private suspend fun commit(event: Pair<String, String>? = null, f: (Vault) -> Vault): Vault = transaction(event) { v ->
        val next = f(v)
        next to next
    }

    private fun fail(msg: String) = Result.failure<Unit>(IllegalStateException(msg))
    private fun currentStore(): StoreData? = state.value.store

    /** 修改当前门店；校验与业务变更基于同一已提交版本。 */
    private suspend fun storeOp(event: Pair<String, String>? = null, check: (StoreData) -> String? = { null }, f: (StoreData) -> StoreData): Result<Unit> = persistenceResult {
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
            commit { it.copy(user = acc.email, storeId = null) }
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
                else v.copy(accounts = v.accounts + acc, user = acc.email, storeId = null) to Result.success(Unit)
            }
        }
    }

    override suspend fun enterDemo() { commit { it.copy(user = "体验账号", storeId = null) } }
    override suspend fun logout() { commit { it.copy(user = null, storeId = null) } }
    override suspend fun selectStore(storeId: String) { commit { if (storeId in it.stores) it.copy(storeId = storeId) else it } }
    override suspend fun selectTable(tableId: String) { storeOp { it.copy(currentTableId = tableId) }.getOrThrow() }

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
    override suspend fun createReservation(tableId: String, name: String, phone: String, guests: Int, arrivesAt: Long, note: String) = storeOp(check = { s ->
        ReservationRules.validate(s.table(tableId), s.reservations, name, phone, guests, arrivesAt, clock())
            ?: if (note.length > 200) "预订备注最多 200 字" else null
    }) { s ->
        val r = Reservation(newId("R"), tableId, name.trim(), phone.trim(), guests, arrivesAt, note.trim(), createdAt = clock())
        s.withTable(tableId) { it.copy(status = TableStatus.Reserved, reservation = ReservationRules.format(arrivesAt)) }
            .copy(reservations = s.reservations + r)
    }

    override suspend fun cancelReservation(id: String) = storeOp(check = { s ->
        if (s.reservations.none { it.id == id && it.status == ReservationStatus.Booked }) "预订已处理或不存在" else null
    }) { s ->
        val r = s.reservations.first { it.id == id }
        s.withTable(r.tableId) { if (it.status == TableStatus.Reserved) it.copy(status = TableStatus.Idle, reservation = "") else it }
            .copy(reservations = s.reservations.map { if (it.id == id) it.copy(status = ReservationStatus.Cancelled, updatedAt = clock()) else it })
    }

    override suspend fun checkInReservation(id: String): Result<Unit> {
        val s = currentStore() ?: return fail("请先选择门店")
        val r = s.reservations.firstOrNull { it.id == id && it.status == ReservationStatus.Booked } ?: return fail("预订已处理或不存在")
        return openTable(r.tableId, r.guests)
    }

    override suspend fun openTable(tableId: String, guests: Int) = storeOp(event = "TableOpened" to tableId, check = { s ->
        val t = s.table(tableId) ?: return@storeOp "桌台不存在"
        when {
            t.status != TableStatus.Idle && t.status != TableStatus.Reserved -> "$tableId ${t.status.label}，不能开台"
            guests !in 1..t.seats -> "人数应为 1–${t.seats}"
            else -> null
        }
    }) { s ->
        s.withTable(tableId) { it.copy(status = TableStatus.Dining, guests = guests, openedAt = clock(), reservation = "", paid = 0) }
            .withOrder(tableId, TableOrder()).copy(currentTableId = tableId,
                reservations = s.reservations.map { if (it.tableId == tableId && it.status == ReservationStatus.Booked) it.copy(status = ReservationStatus.Arrived, updatedAt = clock()) else it })
    }

    override suspend fun settle(tableId: String, amount: Int, method: String, online: Boolean, received: Int): Result<Unit> {
        val pid = newId("P")
        return storeOp(event = "PaymentRecorded" to tableId, check = { s ->
            val totals = OrderRules.totals(s.order(tableId))
            OrderRules.checkoutBlock(totals) ?: when {
                amount != totals.due -> "账单已变化，请重新核对"
                method == "现金" && received < amount -> "实收不足，不能确认收款"
                method !in listOf("现金", "银行卡", "扫码", "储值卡") -> "请选择收款方式"
                !online && method == "扫码" -> "离线时该收款方式不可用"
                method == "储值卡" && (!s.order(tableId).member || s.walletBalance < amount) -> "本机会员未绑定或储值余额不足"
                else -> null
            }
        }) { s ->
            val pay = PaymentRecord(pid, tableId, amount, method, clock(), synced = online, received = if (method == "现金") received else amount, change = if (method == "现金") received - amount else 0)
            val out = if (online) s.outbox else s.outbox + OutboxItem(pid, OutboxKind.Payment, tableId, "$tableId $method 收款 ${yuan(amount)}", clock())
            val receipt = PrintJob(newId("R"), PrintKind.Receipt, "$tableId 结账单", Receipts.bill("结账单", s, tableId, s.order(tableId), clock(), pay), clock())
            s.withTable(tableId) { it.copy(status = TableStatus.Paid, paid = amount) }.withOrder(tableId, TableOrder())
                .copy(payments = s.payments + pay, outbox = out, printJobs = s.printJobs + receipt, walletBalance = if (method == "储值卡") s.walletBalance - amount else s.walletBalance)
        }
    }

    override suspend fun startCleaning(tableId: String) = transition(tableId, TableStatus.Paid) { it.copy(status = TableStatus.Cleaning) }
    override suspend fun finishCleaning(tableId: String) = transition(tableId, TableStatus.Cleaning) { it.copy(status = TableStatus.Idle, guests = 0, openedAt = 0, paid = 0) }

    private suspend fun transition(tableId: String, from: TableStatus, f: (DiningTable) -> DiningTable) = storeOp(check = { s ->
        val t = s.table(tableId) ?: return@storeOp "桌台不存在"
        if (t.status != from) "$tableId 当前为${t.status.label}" else null
    }) { it.withTable(tableId, f) }

    override suspend fun setPosition(tableId: String, x: Int, y: Int) = storeOp(check = { s ->
        when {
            s.table(tableId) == null -> "桌台不存在"
            x !in 0..TableRules.MAX_X || y !in 0..TableRules.MAX_Y -> "坐标范围：X 0–${TableRules.MAX_X}，Y 0–${TableRules.MAX_Y}"
            else -> null
        }
    }) { it.withTable(tableId) { t -> t.copy(x = x, y = y) } }

    override suspend fun changeTable(from: String, to: String) = storeOp(event = "TableChanged" to from, check = { s ->
        if (s.reservations.any { it.tableId == to && it.status == ReservationStatus.Booked }) return@storeOp "目标桌台已有预订"
        val a = s.table(from); val b = s.table(to)
        if (a == null || b == null) "桌台不存在" else TableRules.canChange(a, b)
    }) { s ->
        val a = s.table(from)!!
        s.withTable(to) { it.copy(status = TableStatus.Dining, guests = a.guests, openedAt = a.openedAt, reservation = "") }
            .withTable(from) { it.copy(status = TableStatus.Cleaning, guests = 0, openedAt = 0) }
            .withOrder(to, s.order(from)).withOrder(from, TableOrder())
            .copy(currentTableId = to)
    }

    override suspend fun mergeTable(from: String, into: String) = storeOp(event = "TableMerged" to from, check = { s ->
        val a = s.table(from); val b = s.table(into)
        if (a == null || b == null) "桌台不存在" else TableRules.canMerge(a, b) ?: TableRules.compatible(s.order(from), s.order(into))
    }) { s ->
        val a = s.table(from)!!
        s.withTable(into) { it.copy(guests = it.guests + a.guests, openedAt = minOf(it.openedAt, a.openedAt)) }
            .withTable(from) { it.copy(status = TableStatus.Cleaning, guests = 0, openedAt = 0) }
            .withOrder(into, TableRules.merge(s.order(from), s.order(into))).withOrder(from, TableOrder())
            .copy(currentTableId = into)
    }

    override suspend fun splitLines(from: String, uids: Set<Long>, to: String, guests: Int) = storeOp(event = "OrderSplit" to from, check = { s ->
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
        val opened = if (b.status == TableStatus.Dining) s else s.withTable(to) { it.copy(status = TableStatus.Dining, guests = guests, openedAt = clock(), reservation = "") }
        val target = opened.order(to)
        opened.withOrder(from, rest).withOrder(to, TableRules.merge(s.order(from).copy(lines = moved), target))
    }

    // —— 打印 ——
    private fun StoreData.kitchenJob(tableId: String, batch: String): StoreData {
        val lines = order(tableId).lines.filter { it.batchId == batch }
        if (lines.isEmpty()) return this
        return copy(printJobs = printJobs + PrintJob("P-$batch", PrintKind.Kitchen, "$tableId 厨房单 ${lines.sumOf { it.qty }} 份",
            Receipts.kitchen(settings.shortName, tableId, lines, order(tableId).note, clock()), clock()))
    }

    override suspend fun preBill(tableId: String) = storeOp(check = { s ->
        if (s.order(tableId).lines.isEmpty()) "$tableId 没有菜品" else null
    }) { s -> s.copy(printJobs = s.printJobs + PrintJob(newId("B"), PrintKind.PreBill, "$tableId 预结单", Receipts.bill("预结单", s, tableId, s.order(tableId), clock()), clock())) }

    override suspend fun reprint(jobId: String) = storeOp(check = { s -> if (s.printJobs.none { it.id == jobId }) "打印任务不存在" else null }) { s ->
        val j = s.printJobs.first { it.id == jobId }
        s.copy(printJobs = s.printJobs + j.copy(id = newId("RP"), title = "${j.title}（补打）", body = listOf("** 补打 **") + j.body, createdAt = clock(), state = PrintState.Pending, attempts = 0, reprint = true, error = ""))
    }

    override suspend fun process(kitchenOk: Boolean): Int {
        var failed = 0
        val s = currentStore() ?: return 0
        if (s.printJobs.none { it.state != PrintState.Printed }) return 0
        storeOp { st ->
            st.copy(printJobs = st.printJobs.map { j ->
                when {
                    j.state == PrintState.Printed -> j
                    j.kind.printer == "厨房" && !kitchenOk -> {
                        if (j.state == PrintState.Pending) failed++
                        j.copy(state = PrintState.Failed, attempts = j.attempts + if (j.state == PrintState.Pending) 1 else 0, error = "厨房打印机缺纸")
                    }
                    else -> j.copy(state = PrintState.Printed, attempts = j.attempts + 1, error = "")
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
    override suspend fun closeShift(counted: Int, staff: String): Result<ShiftRecord> = persistenceResult {
        transaction { v ->
            if (counted < 0) return@transaction v to Result.failure(IllegalArgumentException("实盘金额应为非负"))
            val s = v.store ?: return@transaction v to Result.failure(IllegalStateException("请先选择门店"))
            val sum = ShiftRules.summary(s.payments, s.shift, s.adjustments)
            val start = if (s.shift.start > 0) s.shift.start else s.payments.minOfOrNull { it.at } ?: clock()
            val rec = ShiftRecord(newId("S"), start, clock(), staff, sum.total, sum.count, sum.byMethod, s.shift.float, sum.cashExpected, counted, sum.voidAmount, sum.voidCount, sum.giftAmount, sum.giftCount)
            val next = s.copy(
                shift = ShiftState(start = clock(), float = s.shift.float, history = s.shift.history + rec),
                printJobs = s.printJobs + PrintJob(newId("SH"), PrintKind.Shift, "交班单 $staff", Receipts.shift(rec), clock()),
            )
            v.copy(stores = v.stores + (s.id to next)) to Result.success(rec)
        }
    }

    // —— 订单 ——
    private suspend fun mutate(tableId: String, event: Pair<String, String>? = null, rule: (StoreData, TableOrder) -> OrderResult): OrderResult = try {
        transaction(event) { v ->
            val s = v.store ?: return@transaction v to OrderResult.Rejected("请先选择门店")
            if (s.table(tableId)?.status != TableStatus.Dining) return@transaction v to OrderResult.Rejected("$tableId 未开台，请先在桌台开台")
            val result = rule(s, s.order(tableId))
            val next = if (result is OrderResult.Ok) v.copy(stores = v.stores + (s.id to s.withOrder(tableId, result.order).copy(seq = s.seq + 1))) else v
            next to result
        }
    } catch (e: WorkspaceWriteException) { OrderResult.Rejected(requireNotNull(e.message)) }

    override suspend fun addDish(tableId: String, product: Product, sku: Sku?, qty: Int, note: String, mealChoiceIds: List<String>) =
        mutate(tableId, "DishAdded" to tableId) { s, o -> OrderRules.add(o, product, sku, qty, s.seq, note, mealChoiceIds) }

    override suspend fun setBenefits(tableId: String, coupon: Boolean, activity: Boolean) = storeOp(check = { s ->
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
        commit(type to tableId) { v ->
            val account = v.accounts.firstOrNull { it.supervisor && it.email.equals(email.trim(), true) }
            val demo = v.user == "体验账号" && email.trim() == "manager@demo.local" && password == "demo12345"
            require(demo || (account != null && ManageRules.hash(password, account.salt) == account.hash)) { "主管账号或密码不正确" }
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
            val audit = AdjustmentAudit(newId("A"), tableId, request.kind, v.user ?: DemoCatalog.STAFF, email.trim(), request.reason.trim(), amount,
                if (request.kind == AdjustmentKind.Gift) line?.qty ?: 0 else if (request.kind == AdjustmentKind.Void) request.quantity else 0,
                clock(), line?.name.orEmpty(), if (request.kind == AdjustmentKind.Discount) "${request.percent}%" else "")
            var next = s.withOrder(tableId, updated).copy(adjustments = s.adjustments + audit)
            if (request.kind == AdjustmentKind.Void && updated.lines.isEmpty()) next = next.withTable(tableId) { it.copy(status = TableStatus.Cleaning, guests = 0, openedAt = 0, paid = 0) }
            if (request.kind == AdjustmentKind.Void) next = next.copy(printJobs = next.printJobs + PrintJob(newId("V"), PrintKind.Kitchen,
                "$tableId 退菜单", listOf("【退菜单】$tableId", "${line?.name} ×${request.quantity}", line?.spec.orEmpty(), "原因：${audit.reason}", "授权：${audit.supervisor}"), clock()))
            v.copy(stores = v.stores + (s.id to next))
        }
        Unit
    }

    override suspend fun setLineNote(tableId: String, uid: Long, note: String) = mutate(tableId) { _, o -> OrderRules.setNote(o, uid, note) }

    override suspend fun setOrderNote(tableId: String, note: String) = storeOp(check = { s ->
        if (s.table(tableId)?.status != TableStatus.Dining) "$tableId 未在用餐" else if (note.length > 60) "整单备注最多 60 字" else null
    }) { it.withOrder(tableId, it.order(tableId).copy(note = note.trim())) }

    override suspend fun hold(tableId: String): Result<Int> = persistenceResult {
        transaction("OrderHeld" to tableId) { v ->
            val s = v.store ?: return@transaction v to Result.failure(IllegalStateException("请先选择门店"))
            val draft = s.order(tableId).pending
            if (draft.isEmpty()) return@transaction v to Result.failure(IllegalStateException("没有可挂起的新加菜品"))
            val next = s.withOrder(tableId, OrderRules.clearNew(s.order(tableId)))
                .copy(held = s.held + HeldTicket(newId("H"), tableId, draft, s.order(tableId).note, clock()))
            v.copy(stores = v.stores + (s.id to next)) to Result.success(draft.sumOf { it.qty })
        }
    }

    override suspend fun retrieve(ticketId: String, tableId: String) = storeOp(event = "OrderRetrieved" to tableId, check = { s ->
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

    override suspend fun changeQty(tableId: String, uid: Long, delta: Int) = mutate(tableId, "DishQuantityChanged" to tableId) { s, o ->
        val line = o.lines.firstOrNull { it.uid == uid } ?: return@mutate OrderResult.Rejected("菜品已不在账单中")
        val p = s.product(line.dishId) ?: return@mutate OrderResult.Rejected("${line.name}已下架")
        OrderRules.changeQty(o, uid, delta, p)
    }

    override suspend fun clearNew(tableId: String) { storeOp { it.withOrder(tableId, OrderRules.clearNew(it.order(tableId))) }.getOrThrow() }

    override suspend fun sendToKitchen(tableId: String, online: Boolean): SendResult = transaction("KitchenSubmitted" to tableId) { v ->
        val s = v.store ?: return@transaction v to SendResult(0, false)
        val n = s.order(tableId).pending.sumOf { it.qty }
        if (n == 0) return@transaction v to SendResult(0, false)
        val batch = newId("K-${s.id}")
        val out = if (online) s.outbox else s.outbox + OutboxItem(batch, OutboxKind.Kitchen, tableId, "$tableId 送厨 $n 份", clock())
        val sent = OrderRules.send(s.order(tableId), batch, online)
        val withOrder = s.withOrder(tableId, sent).copy(outbox = out, seq = s.seq + 1)
        val next = if (online) withOrder.kitchenJob(tableId, batch) else withOrder
        v.copy(stores = v.stores + (s.id to next)) to SendResult(n, !online)
    }

    override suspend fun advance(tableId: String, uid: Long, to: LineStatus) { storeOp { it.withOrder(tableId, OrderRules.advance(it.order(tableId), uid, to)) }.getOrThrow() }
    override suspend fun setMember(tableId: String, member: Boolean) { storeOp { it.withOrder(tableId, it.order(tableId).copy(member = member)) }.getOrThrow() }

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
                        // 换桌 / 拆单后批次可能已在其他桌，按批次号在全店查找
                        OutboxKind.Kitchen -> {
                            val table = st.orders.entries.firstOrNull { (_, o) -> o.lines.any { it.batchId == item.id } }?.key ?: item.tableId
                            st.withOrder(table, OrderRules.markBatchSent(st.order(table), item.id)).kitchenJob(table, item.id)
                        }
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
