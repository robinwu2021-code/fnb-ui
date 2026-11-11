package ai.neargo.fnb.pos.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 云端连接：当前未接入任何云端服务（N8）。事件只在真实持久 ACK 后才能标确认。 */
object CloudLink {
    const val CONNECTED = false
}

/**
 * 本机命令与云事件统计（N3-03）。来源为本机命令日志；云端未接入时事件全部为待上传。
 * 单机模式下本机即主 POS：命令在本机提交即为主机接受。
 */
data class CommandStats(
    val committed: Int = 0, val conflicts: Int = 0, val rejected: Int = 0, val lastAcceptedAt: Long = 0,
    val unackedEvents: Int = 0, val legacyUnacked: Int = 0, val oldestUnackedAt: Long = 0,
    val openCommands: Int = 0, val singleDevice: Boolean = true, val available: Boolean = false,
)

interface SyncStatusRepository {
    val stats: StateFlow<CommandStats>
    suspend fun refresh()
}

internal class LocalSyncStatus(private val db: SqliteSnapshotStore, private val workspace: LocalWorkspace, scope: CoroutineScope) : SyncStatusRepository {
    private val s = MutableStateFlow(CommandStats())
    override val stats: StateFlow<CommandStats> = s

    init {
        // 每次本机数据变化后重新统计（事件与快照同事务提交）
        scope.launch { workspace.current.collectLatest { refresh() } }
    }

    override suspend fun refresh() {
        val storeId = workspace.current.value?.id ?: return
        s.value = withContext(Dispatchers.IO) { runCatching { db.commandStats(storeId) }.getOrDefault(CommandStats()) }
    }
}
