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

    private val workspace = LocalWorkspace(DataStoreSnapshot(dataStore), appScope).also { runBlocking { it.restore() } }
    val session: SessionRepository = workspace
    val config: StoreConfigRepository = workspace
    val floor: FloorRepository = workspace
    val orders: OrderRepository = workspace
    val sync: SyncRepository = workspace
    val print: PrintRepository = workspace
    val shift: ShiftRepository = workspace
    val prefs: PreferencesRepository = DataStorePreferences(dataStore)
    val device: DeviceRepository = NetworkDevice(context.applicationContext, appScope)
    val messages: MessageBus = SimpleMessageBus()
    /** 本机图片库：商品图片选择后复制到应用私有目录。 */
    val media = LocalMedia(context.applicationContext)

    init {
        SyncEngine(device, sync, messages, appScope).start()
        PrintEngine(device, print, messages, appScope).start()
    }

    /** 显示设置里的「重置演示数据」：只重置当前门店。 */
    fun resetDemo() = appScope.launch { workspace.resetDemo() }
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
                val n = sync.syncNow()
                if (n > 0) messages.post("网络已恢复，已同步 $n 项离线操作")
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
            val failed = print.process(ok)
            if (failed > 0) messages.post("厨房打印机缺纸，$failed 张厨房单待重打（换纸后自动重试）")
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
        )
    }

    override suspend fun update(transform: (DisplayPrefs) -> DisplayPrefs) {
        val n = transform(prefs.first())
        ds.edit {
            it[K.skin] = n.skin.name; it[K.density] = n.density.name; it[K.scale] = n.scale.name
            it[K.panel] = n.panelShape.name; it[K.button] = n.buttonShape.name; it[K.layout] = n.layout.name
            it[K.photos] = n.photos; it[K.font] = n.font.name; it[K.lang] = n.lang.name
        }
    }
}

private class SimpleMessageBus : MessageBus {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val messages = flow.asSharedFlow()
    override fun post(message: String) { flow.tryEmit(message) }
}
