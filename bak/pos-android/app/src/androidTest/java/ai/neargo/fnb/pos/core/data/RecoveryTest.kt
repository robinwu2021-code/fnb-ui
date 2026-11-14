package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.TableStatus
import androidx.test.platform.app.InstrumentationRegistry
import android.content.ContentValues
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** N2-04：一致性备份、校验后恢复（恢复前保留副本）、坏库阻断后从备份恢复。 */
class RecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun backupRestoreRoundTripAndCorruptRecovery() = runBlocking {
        val name = "recovery-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val backups = File(context.filesDir, "backups")
        val before = backups.listFiles()?.map { it.name }?.toSet().orEmpty()
        try {
            SqliteSnapshotStore(context, name).use { db ->
                val w = LocalWorkspace(db, scope)
                w.enterDemo(); w.selectStore("s-main")
                val rec = LocalRecovery(context, db, w)
                val t = w.current.value!!.tables.first { it.status == TableStatus.Idle }.id
                assertTrue(w.openTable(t, 1).isSuccess)
                val point = db.recoveryPoint()
                assertEquals(SqliteSnapshotStore.DB_VERSION, point.dbVersion); assertEquals(1, point.unackedEvents)
                val backup = rec.backup().getOrThrow()
                // 备份之后继续营业
                assertTrue(w.addCategory("夜宵", "").isSuccess)
                assertEquals(2, db.journal.pending("s-main").size)
                rec.restore(backup.name).getOrThrow()
                assertEquals(TableStatus.Dining, w.tables.value.first { it.id == t }.status)
                assertTrue(w.current.value!!.categories.none { it.name == "夜宵" })
                assertEquals(1, db.journal.pending("s-main").size) // 事件与快照同一恢复点
                assertTrue(rec.status().backups.size >= 2) // 恢复前自动保留了当前库
                // 恢复后仍可写，聚合版本与日志一致
                assertTrue(w.addCategory("夜宵", "").isSuccess)

                // 损坏：快照不可解析 → 新实例阻断，不回落演示
                db.journal.pending("s-main")
                android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {
                    it.update("vault", ContentValues().apply { put("json", "{\"version\":42,\"stores\":{}}") }, "id=1", null)
                }
                val broken = LocalWorkspace(db, scope)
                assertTrue(runCatching { broken.restore() }.isFailure)
                assertNotNull(broken.blocked.value)
                val brokenRec = LocalRecovery(context, db, broken)
                assertTrue(brokenRec.backup().isFailure) // 坏库不做一致性备份
                val raw = brokenRec.exportRaw().getOrThrow()
                assertTrue(raw.raw && raw.size > 0)
                assertTrue(brokenRec.restore(raw.name).isFailure) // 原始导出不能用于恢复
                brokenRec.restore(backup.name).getOrThrow()
                assertNull(broken.blocked.value)
                assertEquals(TableStatus.Dining, broken.tables.value.first { it.id == t }.status)
            }
        } finally {
            scope.cancel(); context.deleteDatabase(name)
            backups.listFiles()?.filter { it.name !in before }?.forEach { it.delete() }
        }
    }
}
