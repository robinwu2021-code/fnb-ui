package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.OrderResult
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** N2-04：并发命令、切店中提交、坏库只读阻断。 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommandConcurrencyTest {
    private class Store : SnapshotStore {
        var json: String? = null
        var gate: CompletableDeferred<Unit>? = null
        val commands = mutableListOf<JournalCommand>()
        override suspend fun load() = json
        override suspend fun save(json: String) { gate?.await(); this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt {
            gate?.await(); json = source; commands += command; return testReceipt(command)
        }
    }

    @Test fun concurrentCommandsSerializeWithContiguousVersions() = runTest(UnconfinedTestDispatcher()) {
        val disk = Store()
        val w = LocalWorkspace(disk, backgroundScope) { 1_000_000L }
        w.enterDemo(); w.selectStore("s-main")
        val t = w.current.value!!.tables.first { it.status == TableStatus.Idle }.id
        w.openTable(t, 2)
        val oid = w.orders.value.getValue(t).orderId
        val p = w.current.value!!.products.first { it.id == "12" }
        disk.commands.clear()
        disk.gate = CompletableDeferred()
        val jobs = (1..6).map { async { w.addDish(t, p, null, 1) } }
        assertTrue(jobs.none { it.isCompleted })
        disk.gate!!.complete(Unit)
        assertTrue(jobs.awaitAll().all { it is OrderResult.Ok })
        assertEquals(6, w.orders.value.getValue(t).lines.sumOf { it.qty })
        assertEquals((1L..6L).toList(), disk.commands.map { c -> c.aggregates.first { it.id == "order:$oid" }.expected })
        assertEquals(6, disk.commands.map { it.commandId }.distinct().size)
    }

    @Test fun storeSwitchDuringCommitKeepsCommandInOriginalStore() = runTest(UnconfinedTestDispatcher()) {
        val disk = Store()
        val w = LocalWorkspace(disk, backgroundScope) { 1_000_000L }
        w.enterDemo(); w.selectStore("s-main")
        val t = w.current.value!!.tables.first { it.status == TableStatus.Idle }.id
        disk.gate = CompletableDeferred()
        val open = async { w.openTable(t, 1) }
        val switch = async { w.selectStore("s-coast") }
        disk.gate!!.complete(Unit)
        assertTrue(open.await().isSuccess); switch.await()
        assertEquals("s-coast", w.session.value.storeId)
        val saved = VaultCodec.decode(disk.json!!)
        assertEquals(TableStatus.Dining, saved.stores.getValue("s-main").tables.first { it.id == t }.status)
        assertEquals("s-main", disk.commands.single().storeId)
    }

    @Test fun unreadableSnapshotBlocksWritesAndKeepsOriginal() = runTest(UnconfinedTestDispatcher()) {
        val disk = Store()
        disk.json = "{\"version\":99,\"stores\":{}}"
        val w = LocalWorkspace(disk, backgroundScope) { 1_000_000L }
        assertTrue(runCatching { w.restore() }.isFailure)
        assertNotNull(w.blocked.value)
        assertTrue(runCatching { w.enterDemo() }.exceptionOrNull() is WorkspaceWriteException)
        assertTrue(w.addArea("露台", "").isFailure)
        assertEquals(0, w.process(true)); assertEquals(0, w.syncNow())
        assertEquals("{\"version\":99,\"stores\":{}}", disk.json) // 不回落演示、不覆盖原件
        assertTrue(disk.commands.isEmpty())
        // 修复后重新加载解除阻断
        disk.json = null
        w.reload()
        assertNull(w.blocked.value)
    }
}
