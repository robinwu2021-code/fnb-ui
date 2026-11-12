package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.core.data.CartDraft
import ai.neargo.fnb.pos.core.data.DemoCatalog
import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.HOST_DOWN
import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LanRole
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.MemoryCartDrafts
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.readOnlyReason
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.model.CartLine
import ai.neargo.fnb.pos.core.model.DeviceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 收银台购物车草稿：重启后恢复、变动即存、收完删除；副 POS 失联时确认按钮显示只读原因。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailPosDraftTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private class Device(s: DeviceStatus = DeviceStatus()) : DeviceRepository {
        override val status = MutableStateFlow(s)
        override fun setForceOffline(on: Boolean) {}
        override fun setPrinterOk(ok: Boolean) {}
    }
    private class Bus : MessageBus {
        val posted = mutableListOf<String>()
        override val messages = MutableSharedFlow<String>()
        override fun post(message: String) { posted += message }
    }

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private suspend fun mart(scope: kotlinx.coroutines.CoroutineScope) =
        LocalWorkspace(Disk(), scope) { 1_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }

    @Test fun restoresDraftAndSavesChanges() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        val pid = w.current.value!!.products.first { it.retail?.weighable != true && !it.hasSpec && (it.stock ?: 0) > 0 }.id
        val drafts = MemoryCartDrafts().apply { saved[DemoCatalog.RETAIL_STORE_ID] = CartDraft(listOf(CartLine(7, pid, 2)), discountPct = 90, approver = "店长") }
        val bus = Bus()
        val vm = RetailPosViewModel(w, w, Device(), bus, drafts = drafts)
        // 重启后恢复：行、整单折扣、授权人都在，并提示一句
        assertEquals(listOf(CartLine(7, pid, 2)), vm.state.value.cart.lines)
        assertEquals(90, vm.state.value.cart.discountPct); assertEquals("店长", vm.state.value.cart.approver)
        assertTrue(bus.posted.any { it.startsWith("已恢复上次未收完的购物车") })
        // 变动即存
        vm.onAction(RetailPosAction.PickProduct(pid))
        assertEquals(3, drafts.saved.getValue(DemoCatalog.RETAIL_STORE_ID).lines.sumOf { it.qty })
        // 新加的行号不和恢复的行撞
        assertTrue(vm.state.value.cart.lines.map { it.key }.toSet().size == vm.state.value.cart.lines.size)
        // 清空就删草稿
        vm.onAction(RetailPosAction.Clear)
        assertNull(drafts.saved[DemoCatalog.RETAIL_STORE_ID])
    }

    @Test fun checkoutClearsDraft() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        val p = w.current.value!!.products.first { it.retail?.weighable != true && !it.hasSpec && (it.stock ?: 0) > 0 && it.price < 10_000 }
        val drafts = MemoryCartDrafts().apply { saved[DemoCatalog.RETAIL_STORE_ID] = CartDraft(listOf(CartLine(1, p.id, 1))) }
        val vm = RetailPosViewModel(w, w, Device(), Bus(), drafts = drafts)
        vm.onAction(RetailPosAction.QuickCash)
        assertTrue(vm.state.value.cart.lines.isEmpty())
        assertNull(drafts.saved[DemoCatalog.RETAIL_STORE_ID])
    }

    @Test fun hostDownBlocksConfirm() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(HOST_DOWN, readOnlyReason(LanRole.Secondary, connected = false))
        assertNull(readOnlyReason(LanRole.Secondary, connected = true))
        assertNull(readOnlyReason(LanRole.Host, connected = false))
        val w = mart(backgroundScope)
        val p = w.current.value!!.products.first { it.retail?.weighable != true && !it.hasSpec && (it.stock ?: 0) > 0 }
        val vm = RetailPosViewModel(w, w, Device(DeviceStatus(readOnly = HOST_DOWN)), Bus(), drafts = MemoryCartDrafts())
        vm.onAction(RetailPosAction.PickProduct(p.id))
        vm.onAction(RetailPosAction.GoPay("现金"))
        assertEquals(HOST_DOWN, vm.state.value.block)
    }
}
