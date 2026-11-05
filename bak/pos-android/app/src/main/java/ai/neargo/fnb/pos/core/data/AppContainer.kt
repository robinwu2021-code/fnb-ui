package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import android.content.Context
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.posStore: DataStore<Preferences> by preferencesDataStore(name = "pos")

/**
 * 手动依赖容器（应用级单例）。页面越来越多或接入服务后，可平滑换成 Hilt。
 * 演示仓库在启动时同步恢复快照，避免首帧闪现初始数据后再跳变。
 */
class AppContainer(context: Context) {
    private val dataStore = context.applicationContext.posStore
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val menu: MenuRepository = DemoMenuRepository()
    private val workspace = DemoWorkspace(DataStoreSnapshot(dataStore), menu, appScope).also { runBlocking { it.restore() } }
    val session: SessionRepository = workspace
    val floor: FloorRepository = workspace
    val orders: OrderRepository = workspace
    val prefs: PreferencesRepository = DataStorePreferences(dataStore)
    val device: DeviceRepository = InMemoryDevice()
    val messages: MessageBus = SimpleMessageBus()

    /** 显示设置里的「重置演示数据」。 */
    fun resetDemo() = appScope.launch { workspace.resetDemo() }
}

private class DataStoreSnapshot(private val ds: DataStore<Preferences>) : SnapshotStore {
    private val key = stringPreferencesKey("workspace_v1")
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
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    override val prefs = ds.data.map { p ->
        val d = DisplayPrefs()
        DisplayPrefs(
            skin = p.enum(K.skin, d.skin), density = p.enum(K.density, d.density), scale = p.enum(K.scale, d.scale),
            panelShape = p.enum(K.panel, d.panelShape), buttonShape = p.enum(K.button, d.buttonShape),
            layout = p.enum(K.layout, d.layout), photos = p[K.photos] ?: d.photos,
        )
    }

    override suspend fun update(transform: (DisplayPrefs) -> DisplayPrefs) {
        val n = transform(prefs.first())
        ds.edit {
            it[K.skin] = n.skin.name; it[K.density] = n.density.name; it[K.scale] = n.scale.name
            it[K.panel] = n.panelShape.name; it[K.button] = n.buttonShape.name; it[K.layout] = n.layout.name
            it[K.photos] = n.photos
        }
    }
}

private class InMemoryDevice : DeviceRepository {
    private val s = MutableStateFlow(DeviceStatus())
    override val status = s.asStateFlow()
    override fun set(status: DeviceStatus) { s.value = status }
}

private class SimpleMessageBus : MessageBus {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val messages = flow.asSharedFlow()
    override fun post(message: String) { flow.tryEmit(message) }
}
