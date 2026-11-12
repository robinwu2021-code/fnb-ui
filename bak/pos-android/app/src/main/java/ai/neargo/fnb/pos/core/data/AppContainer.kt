package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import ai.neargo.fnb.pos.core.model.PrintState
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.posStore: DataStore<Preferences> by preferencesDataStore(name = "pos")

/**
 * 手动依赖容器（应用级单例）。页面越来越多或接入服务后，可平滑换成 Hilt。
 * 本机仓库在启动时同步恢复快照，避免首帧闪现初始数据后再跳变。
 */
class AppContainer(context: Context) {
    private val dataStore = context.applicationContext.posStore
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = SqliteSnapshotStore(context.applicationContext)
    /** 恢复失败不崩溃也不回落演示：workspace 进入只读阻断，界面显示恢复页。 */
    private val trust = ai.neargo.fnb.pos.core.identity.TrustAnchors(context.applicationContext, ai.neargo.fnb.pos.BuildConfig.DEBUG)
    private val identity = IdentityContext(ai.neargo.fnb.pos.core.identity.KeystoreDeviceKeys(), { trust.all }, trust.devIssuer)
    private val workspace = LocalWorkspace(MigratingSnapshotStore(database, DataStoreSnapshot(dataStore)), appScope, identity = identity).also {
        runBlocking { runCatching { it.restore() }.onFailure { e -> android.util.Log.e("AppContainer", "本地数据恢复失败", e) } }
    }
    val recovery: RecoveryRepository = LocalRecovery(context.applicationContext, database, workspace)
    val syncStatus: SyncStatusRepository = LocalSyncStatus(database, workspace, appScope)
    val auth: AuthRepository = workspace
    /** 员工与权限：员工管理仅主 POS（副机员工来自主机副本，可本机 PIN 上岗）。 */
    val staff: StaffRepository = workspace
    fun deviceId() = workspace.deviceId()
    /** 厨房屏（N7）：本机工位、收件箱、游标。 */
    val kdsDevice get() = workspace.kdsDevice
    suspend fun setKdsStation(station: String) = workspace.setKdsStation(station)
    val kds: KdsRepository = workspace
    /** 本机设备 ID（厨房屏判断负责屏）。 */
    val deviceId get() = workspace.deviceId()
    /** 客显屏阶段（结账页写入；客显屏与设置预览读取）。 */
    val customerStage = kotlinx.coroutines.flow.MutableStateFlow<ai.neargo.fnb.pos.core.model.CustomerStage>(ai.neargo.fnb.pos.core.model.CustomerStage.Browse)
    val session: SessionRepository = workspace
    // —— 主副 POS（N5）：副机写入经主机仲裁；主机执行器直接使用本机仓库 ——
    private val deviceKeys get() = identity.keys
    val lanLink = ai.neargo.fnb.pos.core.lan.LanLink(workspace, deviceKeys, appScope, deviceName = android.os.Build.MODEL ?: "副 POS")
    val lanHost = ai.neargo.fnb.pos.core.lan.LanHost(workspace, ai.neargo.fnb.pos.core.lan.HostExecutor(workspace, workspace, workspace, database, workspace, workspace, retail = workspace, session = workspace), deviceKeys, appScope,
        hostName = android.os.Build.MODEL ?: "主 POS")
    val lan get() = workspace.lanState
    suspend fun setHostRole(on: Boolean) {
        if (on) { workspace.updateLan("设为主 POS") { it.copy(role = LanRole.Host) }; kotlinx.coroutines.withContext(Dispatchers.IO) { lanHost.start() } }
        else { lanHost.stop(); workspace.updateLan("停止主 POS，回到单机") { it.copy(role = LanRole.Standalone) } }
    }
    suspend fun revokeDevice(deviceId: String) {
        workspace.updateLan("撤销副机 $deviceId") { l -> l.copy(paired = l.paired.map { if (it.deviceId == deviceId) it.copy(revokedAt = System.currentTimeMillis()) else it }) }
        lanHost.disconnect(deviceId)
    }
    val config: StoreConfigRepository = RoutedConfig(workspace, lanLink)
    val floor: FloorRepository = RoutedFloor(workspace, lanLink)
    val orders: OrderRepository = RoutedOrders(workspace, lanLink)
    val sync: SyncRepository = RoutedSync(workspace, lanLink)
    val print: PrintRepository = RoutedPrint(workspace, lanLink)
    val shift: ShiftRepository = RoutedShift(workspace, lanLink)
    val ops: OperationsRepository = RoutedOps(workspace, lanLink)
    /** 零售（R3 起）：先只走本机；副 POS 经主机仲裁放在后面做。 */
    val retail: RetailRepository = RoutedRetail(workspace, lanLink)
    val prefs: PreferencesRepository = DataStorePreferences(dataStore)
    /** 收银台购物车草稿（本机） */
    val cartDrafts: CartDraftStore = DataStoreCartDrafts(dataStore)
    val device: DeviceRepository = RoutedDevice(NetworkDevice(context.applicationContext, appScope), workspace.lanState, lanLink.status, appScope)
    val messages: MessageBus = SimpleMessageBus()
    /** 本机图片库：商品图片选择后复制到应用私有目录。 */
    val media = LocalMedia(context.applicationContext)
    /** 运行期能力：第一期全离线（线上平台订单、云同步关闭） */
    val runtime = ai.neargo.fnb.pos.core.model.RuntimeProfile.Phase1Offline
    /** 本机电子秤（零售称重）：按本机偏好里的地址连接 */
    val scale = ai.neargo.fnb.pos.core.hardware.NetworkScale(appScope)

    init {
        SyncEngine(device, sync, messages, appScope).start()
        PrintEngine(device, print, messages, appScope).start()
        if (workspace.lanState.value.role == LanRole.Host) appScope.launch(Dispatchers.IO) { runCatching { lanHost.start() }.onFailure { messages.post("主 POS 服务启动失败：${it.message}") } }
        lanLink.start()
        appScope.launch { prefs.prefs.collect { p -> scale.configure(p.scaleHost, p.scalePort, p.scalePoll, if (p.scaleGrams) ai.neargo.fnb.pos.core.model.ScaleRules.Unit.G else ai.neargo.fnb.pos.core.model.ScaleRules.Unit.Kg) } }
        // 网口打印网关：仅主 POS/单机领取任务（副 POS 不打印）
        ai.neargo.fnb.pos.core.print.PrintGateway(workspace).start(appScope)
        // 授权到期、会话过期随时间发生：定时重新评估
        appScope.launch { while (true) { workspace.refreshAuth(); kotlinx.coroutines.delay(30_000) } }
        // 厨房屏送达超时：定时检查（仅主机/单机生效）
        appScope.launch { while (true) { kotlinx.coroutines.delay(10_000); runCatching { workspace.expireScreenDeliveries() } } }
    }

    /** 显示设置里的「重置演示数据」：只重置当前门店。 */
    suspend fun resetDemo() { workspace.resetDemo() }
}

/**
 * 同步引擎：联网且有待同步项时自动补发（短暂延迟，等网络稳定）。离线时什么也不做，数据留在本机队列。
 */
private class SyncEngine(private val device: DeviceRepository, private val sync: SyncRepository, private val messages: MessageBus, private val scope: CoroutineScope) {
    fun start() = scope.launch {
        combine(device.status.map { it.online }, sync.pendingCount) { online, n -> online && n > 0 }
            .distinctUntilChanged()
            .collectLatest { ready ->
                if (!ready) return@collectLatest
                delay(1_200)
                try {
                    val n = sync.syncNow()
                    if (n > 0) messages.post("网络已恢复，已同步 $n 项离线操作")
                } catch (e: WorkspaceWriteException) { messages.post(requireNotNull(e.message)) }
            }
    }
}

/**
 * 打印引擎：有排队或失败任务时处理；厨房打印机缺纸时厨房单失败并提示，换纸后自动重试。
 * 当前为本机模拟打印（仅改变任务状态），阶段三对接打印机 SDK 回执。
 */
private class PrintEngine(private val device: DeviceRepository, private val print: PrintRepository, private val messages: MessageBus, private val scope: CoroutineScope) {
    fun start() = scope.launch {
        combine(device.status.map { it.printerOk }, print.jobs.map { js -> js.count { it.state == PrintState.Pending } to js.count { it.state == PrintState.Failed } }) { ok, (p, f) ->
            Triple(ok, p, if (ok) f else 0)
        }.distinctUntilChanged().collectLatest { (ok, pending, retry) ->
            if (pending + retry == 0) return@collectLatest
            delay(600)
            try {
                val failed = print.process(ok)
                if (failed > 0) messages.post("厨房打印机缺纸，$failed 张厨房单待重打（换纸后自动重试）")
            } catch (e: WorkspaceWriteException) { messages.post(requireNotNull(e.message)) }
        }
    }
}

/** 设备状态：系统默认网络回调 + 演示开关。 */
private class NetworkDevice(context: Context, scope: CoroutineScope) : DeviceRepository {
    private val s = MutableStateFlow(DeviceStatus(network = context.isNetworkUp()))
    override val status = s.asStateFlow()
    override fun setForceOffline(on: Boolean) = s.update { it.copy(forceOffline = on) }
    override fun setPrinterOk(ok: Boolean) = s.update { it.copy(printerOk = ok) }

    init {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                s.update { it.copy(network = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) }
            }
            override fun onLost(network: Network) = s.update { it.copy(network = false) }
        })
    }
}

private fun Context.isNetworkUp(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java) ?: return false
    return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

private class DataStoreSnapshot(private val ds: DataStore<Preferences>) : SnapshotStore {
    private val key = stringPreferencesKey("vault_v2")
    override suspend fun load(): String? = ds.data.first()[key]
    override suspend fun save(json: String) { ds.edit { it[key] = json } }
    /** 旧 DataStore 只用于一次性导入，不能承载命令事务。 */
    override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt =
        throw UnsupportedOperationException("旧版存储不支持命令事务")
}

/** 购物车草稿存在本机偏好里（按门店一个键，JSON） */
private class DataStoreCartDrafts(private val ds: DataStore<Preferences>) : CartDraftStore {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private fun key(storeId: String) = stringPreferencesKey("cart_draft_$storeId")
    override suspend fun load(storeId: String): CartDraft? =
        ds.data.first()[key(storeId)]?.let { runCatching { json.decodeFromString(CartDraft.serializer(), it) }.getOrNull() }
    override suspend fun save(storeId: String, draft: CartDraft?) {
        ds.edit { if (draft == null || draft.lines.isEmpty()) it.remove(key(storeId)) else it[key(storeId)] = json.encodeToString(CartDraft.serializer(), draft) }
    }
}

private class DataStorePreferences(private val ds: DataStore<Preferences>) : PreferencesRepository {
    private object K {
        val skin = stringPreferencesKey("skin")
        val density = stringPreferencesKey("density")
        val scale = stringPreferencesKey("scale")
        val panel = stringPreferencesKey("panel_shape")
        val button = stringPreferencesKey("button_shape")
        /** v3 语义的布局键；不读取旧工程的 frame 值。 */
        val layout = stringPreferencesKey("order_layout_v3")
        val photos = booleanPreferencesKey("photos")
        val font = stringPreferencesKey("font")
        val lang = stringPreferencesKey("lang")
        val cd = booleanPreferencesKey("customer_display")
        val cdLines = booleanPreferencesKey("customer_lines")
        val cdSlides = booleanPreferencesKey("customer_slides")
        val scaleHost = stringPreferencesKey("scale_host")
        val scalePort = intPreferencesKey("scale_port")
        val scalePoll = stringPreferencesKey("scale_poll")
        val scaleGrams = booleanPreferencesKey("scale_grams")
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    override val prefs = ds.data.map { p ->
        val d = DisplayPrefs()
        DisplayPrefs(
            skin = p.enum(K.skin, d.skin), density = p.enum(K.density, d.density), scale = p.enum(K.scale, d.scale),
            panelShape = p.enum(K.panel, d.panelShape), buttonShape = p.enum(K.button, d.buttonShape),
            layout = p.enum(K.layout, d.layout), photos = p[K.photos] ?: d.photos,
            font = p.enum(K.font, d.font), lang = p.enum(K.lang, d.lang),
            customerDisplay = p[K.cd] ?: d.customerDisplay, customerLines = p[K.cdLines] ?: d.customerLines, customerSlides = p[K.cdSlides] ?: d.customerSlides,
            scaleHost = p[K.scaleHost] ?: d.scaleHost, scalePort = p[K.scalePort] ?: d.scalePort, scalePoll = p[K.scalePoll] ?: d.scalePoll, scaleGrams = p[K.scaleGrams] ?: d.scaleGrams,
        )
    }

    override suspend fun update(transform: (DisplayPrefs) -> DisplayPrefs) {
        val n = transform(prefs.first())
        ds.edit {
            it[K.skin] = n.skin.name; it[K.density] = n.density.name; it[K.scale] = n.scale.name
            it[K.panel] = n.panelShape.name; it[K.button] = n.buttonShape.name; it[K.layout] = n.layout.name
            it[K.photos] = n.photos; it[K.font] = n.font.name; it[K.lang] = n.lang.name
            it[K.cd] = n.customerDisplay; it[K.cdLines] = n.customerLines; it[K.cdSlides] = n.customerSlides
            it[K.scaleHost] = n.scaleHost; it[K.scalePort] = n.scalePort; it[K.scalePoll] = n.scalePoll; it[K.scaleGrams] = n.scaleGrams
        }
    }
}

private class SimpleMessageBus : MessageBus {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val messages = flow.asSharedFlow()
    override fun post(message: String) { flow.tryEmit(message) }
}
