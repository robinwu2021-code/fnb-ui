package ai.neargo.fnb.pos.core.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SnapshotMigrationTest {
    private class Disk(var value: String? = null) : SnapshotStore {
        var writes = 0
        var fail = false
        override suspend fun load() = value
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) {
            if (fail) throw IOException("unavailable")
            writes++
            value = json
        }
    }
    private val valid = "{\"version\":3,\"stores\":{}}"

    @Test fun importsOnceAndRetainsLegacySource() = runTest {
        val old = Disk(valid); val current = Disk()
        val store = MigratingSnapshotStore(current, old)
        assertEquals(valid, store.load())
        assertEquals(valid, old.value)
        old.value = "outdated"
        assertEquals(valid, store.load())
        assertEquals(1, current.writes)
    }
    @Test fun rejectsCorruptAndUnsupportedLegacyBeforeImport() = runTest {
        for (source in listOf("bad json", "{\"version\":99,\"stores\":{}}", "{\"version\":\"bad\",\"stores\":{}}")) {
            val current = Disk()
            val old = Disk(source)
            assertTrue(runCatching { MigratingSnapshotStore(current, old).load() }.isFailure)
            assertNull(current.value)
            assertEquals(source, old.value)
        }
    }
    @Test fun failedImportCanRetryWithoutDeletingLegacy() = runTest {
        val current = Disk().also { it.fail = true }
        val old = Disk(valid)
        val store = MigratingSnapshotStore(current, old)
        assertTrue(runCatching { store.load() }.isFailure)
        assertEquals(valid, old.value)
        assertNull(current.value)
        current.fail = false
        assertEquals(valid, store.load())
    }
    @Test fun invalidPrimaryNeverFallsBackToStaleLegacy() = runTest {
        val current = Disk("invalid primary")
        val store = MigratingSnapshotStore(current, Disk(valid))
        assertEquals("invalid primary", store.load())
        assertEquals(0, current.writes) // 恢复层负责报告无效新库，不能回滚旧副本
    }
}
