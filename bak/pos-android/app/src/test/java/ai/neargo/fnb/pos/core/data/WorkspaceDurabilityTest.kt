package ai.neargo.fnb.pos.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceDurabilityTest {
    private class Disk : SnapshotStore {
        var value: String? = null
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        override suspend fun load() = value
        override suspend fun save(json: String) {
            gate?.await()
            if (fail) throw IOException("disk full")
            value = json
        }
    }

    @Test fun successIsDurableWithoutDrainingBackgroundScope() = runTest {
        val disk = Disk()
        val w = LocalWorkspace(disk, backgroundScope)
        w.enterDemo(); w.selectStore("s-main")
        assertEquals("s-main", VaultCodec.decode(requireNotNull(disk.value)).storeId)
        val restored = LocalWorkspace(disk, backgroundScope + UnconfinedTestDispatcher(testScheduler))
        restored.restore()
        assertEquals("s-main", restored.session.value.storeId)
    }

    @Test fun pendingSaveDoesNotPublishAndCallerWaits() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk()
        val w = LocalWorkspace(disk, backgroundScope)
        disk.gate = CompletableDeferred()
        val operation = async { w.enterDemo() }
        assertFalse(operation.isCompleted)
        assertNull(w.session.value.user)
        disk.gate!!.complete(Unit)
        operation.await()
        assertEquals("体验账号", w.session.value.user)
    }

    @Test fun failedSaveDoesNotChangeMemoryOrDisk() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk()
        val w = LocalWorkspace(disk, backgroundScope)
        w.enterDemo(); w.selectStore("s-main")
        val old = disk.value
        disk.fail = true
        val result = w.addArea("露台", "Terrace")
        assertTrue(result.isFailure)
        assertTrue(w.current.value!!.areas.none { it.name == "露台" })
        assertEquals(old, disk.value)
    }

    @Test fun concurrentAddsAndValidationUseLatestCommittedState() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk()
        val w = LocalWorkspace(disk, backgroundScope)
        w.enterDemo(); w.selectStore("s-main")
        val p = w.current.value!!.products.first { it.id == "12" }
        disk.gate = CompletableDeferred()
        val first = async { w.addDish("A02", p, null, 1) }
        val second = async { w.addDish("A02", p, null, 1) }
        disk.gate!!.complete(Unit)
        first.await(); second.await()
        assertEquals(2, w.orders.value.getValue("A02").lines.filter { it.dishId == "12" }.sumOf { it.qty })
        disk.gate = CompletableDeferred()
        val a = async { w.addArea("露台", "") }
        val b = async { w.addArea("露台", "") }
        disk.gate!!.complete(Unit)
        assertTrue(a.await().isSuccess)
        assertTrue(b.await().isFailure)
        assertEquals(1, w.current.value!!.areas.count { it.name == "露台" })
    }

    @Test fun cancellationDuringCommitKeepsDiskAndMemoryConsistent() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk()
        val w = LocalWorkspace(disk, backgroundScope)
        disk.gate = CompletableDeferred()
        val job = launch { w.enterDemo() }
        job.cancel()
        disk.gate!!.complete(Unit)
        job.join()
        val restored = LocalWorkspace(disk, backgroundScope)
        restored.restore()
        assertEquals(w.session.value.user, restored.session.value.user)
    }

    @Test fun concurrentKitchenSubmissionCreatesOneBatch() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk()
        val w = LocalWorkspace(disk, backgroundScope)
        w.enterDemo(); w.selectStore("s-main")
        disk.gate = CompletableDeferred()
        val a = async { w.sendToKitchen("A03", false) }
        val b = async { w.sendToKitchen("A03", false) }
        disk.gate!!.complete(Unit)
        assertEquals(5, a.await().qty)
        assertEquals(0, b.await().qty)
        assertEquals(1, w.outbox.value.size)
    }

    @Test fun failedOrderSaveReturnsRejection() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk()
        val w = LocalWorkspace(disk, backgroundScope)
        w.enterDemo(); w.selectStore("s-main")
        val original = w.orders.value
        disk.fail = true
        val p = w.current.value!!.products.first { it.id == "12" }
        assertTrue(w.addDish("A02", p, null, 1) is ai.neargo.fnb.pos.core.model.OrderResult.Rejected)
        assertEquals(original, w.orders.value)
    }

    @Test fun writeFailureIsReportedWithoutRunningSuccessContinuation() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk()
        val w = LocalWorkspace(disk, backgroundScope)
        w.enterDemo(); w.selectStore("s-main")
        val notices = mutableListOf<String>()
        val bus = object : MessageBus {
            override val messages = kotlinx.coroutines.flow.MutableSharedFlow<String>()
            override fun post(message: String) { notices += message }
        }
        disk.fail = true
        var success = false
        launchStorage(bus) { w.clearNew("A03"); success = true }.join()
        assertFalse(success)
        assertTrue(notices.single().contains("保存失败"))
        assertTrue(w.hold("A03").isFailure)
        assertTrue(w.closeShift(0, "staff").isFailure)
        assertTrue(w.register("门店", "boss@example.com", "password1", "password1").isFailure)
    }

    @Test fun syncSaveFailureKeepsOriginalPendingQueueAndKitchenLines() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk()
        val w = LocalWorkspace(disk, backgroundScope)
        w.enterDemo(); w.selectStore("s-main")
        w.sendToKitchen("A03", false)
        val before = w.current.value
        disk.fail = true
        assertTrue(runCatching { w.syncNow() }.exceptionOrNull() is WorkspaceWriteException)
        assertEquals(before, w.current.value)
        assertEquals(1, w.pendingCount.value)
    }

    @Test fun invalidOrFutureSnapshotFailsWithoutOverwriting() = runTest {
        for (source in listOf("broken json", "{\"stores\":{},\"version\":999}")) {
            val disk = Disk().also { it.value = source }
            assertTrue(runCatching { LocalWorkspace(disk, backgroundScope).restore() }.isFailure)
            assertEquals(source, disk.value)
        }
    }
}
