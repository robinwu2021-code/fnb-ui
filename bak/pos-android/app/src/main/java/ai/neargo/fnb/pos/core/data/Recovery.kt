package ai.neargo.fnb.pos.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecoveryPoint(
    val at: Long, val dbVersion: Int, val lastSequence: Long, val unackedEvents: Int,
    val openCommands: Int, val legacyEvents: Int, val pendingQueue: Int,
)

data class BackupInfo(val name: String, val size: Long, val at: Long, val raw: Boolean)

data class RecoveryStatus(
    val deviceId: String, val point: RecoveryPoint?, val migrations: List<Triple<Int, Long, String>>, val backups: List<BackupInfo>,
    val blocked: String?,
)

/** 数据备份与恢复（N2-04）。恢复不清库、不回落演示；坏库只读并可导出。 */
interface RecoveryRepository {
    /** 启动恢复失败原因；非空时业务写入全部被拒绝。 */
    val blocked: StateFlow<String?>
    suspend fun status(): RecoveryStatus
    suspend fun backup(): Result<BackupInfo>
    suspend fun exportRaw(): Result<BackupInfo>
    /** 校验备份后替换当前库；替换前自动备份当前库（损坏时改为原样导出）。 */
    suspend fun restore(name: String): Result<Unit>
}

internal class LocalRecovery(
    private val context: Context,
    private val db: SqliteSnapshotStore,
    private val workspace: LocalWorkspace,
    private val clock: () -> Long = System::currentTimeMillis,
) : RecoveryRepository {
    private val dir get() = File(context.filesDir, "backups")
    override val blocked: StateFlow<String?> = workspace.blocked
    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(clock()))

    override suspend fun status(): RecoveryStatus = withContext(Dispatchers.IO) {
        RecoveryStatus(
            deviceId = workspace.deviceId(),
            point = runCatching { db.recoveryPoint() }.getOrNull(),
            migrations = runCatching { db.migrations() }.getOrDefault(emptyList()),
            backups = list(), blocked = blocked.value,
        )
    }

    private fun list() = (dir.listFiles()?.toList().orEmpty()).filter { it.name.endsWith(".db") }.sortedByDescending { it.lastModified() }
        .map { BackupInfo(it.name, it.length(), it.lastModified(), it.name.startsWith("raw-")) }

    override suspend fun backup(): Result<BackupInfo> = runCatching {
        check(blocked.value == null) { "本地库未能正常打开，请使用「导出原始数据库」" }
        val f = File(dir, "pos-${stamp()}.db")
        db.backupTo(f)
        BackupInfo(f.name, f.length(), f.lastModified(), false)
    }

    override suspend fun exportRaw(): Result<BackupInfo> = runCatching {
        withContext(Dispatchers.IO) {
            val f = File(dir, "raw-${stamp()}.db")
            db.exportRaw(f)
            BackupInfo(f.name, f.length(), f.lastModified(), true)
        }
    }

    override suspend fun restore(name: String): Result<Unit> = runCatching {
        val source = File(dir, name)
        require(source.parentFile == dir && source.exists() && !name.startsWith("raw-")) { "备份不存在或不可用于恢复" }
        // 1. 在临时副本上打开并完整校验（含升级），不触碰当前库
        val probeName = "restore-probe-${stamp()}.db"
        val probe = context.getDatabasePath(probeName)
        withContext(Dispatchers.IO) { source.copyTo(probe, overwrite = true) }
        try {
            SqliteSnapshotStore(context, probeName).use { s -> VaultCodec.decode(requireNotNull(s.load()) { "备份中没有门店数据" }); s.recoveryPoint() }
            // 2. 当前库先留一份：正常时做一致性备份，坏库时原样导出
            if (blocked.value == null) backup().getOrThrow() else exportRaw().getOrThrow()
            // 3. 替换并重新加载；加载失败保持只读阻断
            withContext(Dispatchers.IO) { db.replaceWith(probe) }
            workspace.reload()
        } finally { context.deleteDatabase(probeName) }
    }
}
