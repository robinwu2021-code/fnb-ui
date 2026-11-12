package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 真实商户建店：按业态复制资料、不带业务数据、归当前账号；建了自己的门店后只看自己的；体验账号与副 POS 不能建。 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateStoreTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }

    @Test fun merchantCreatesRetailStoreFromTemplate() = runTest(UnconfinedTestDispatcher()) {
        val disk = Disk()
        val w = LocalWorkspace(disk, backgroundScope) { 1_800_000_000_000L }
        w.register("东门便利", "boss@dm.cn", "secret123", "secret123").getOrThrow()
        // 还没建店：先看演示门店（试用）
        assertTrue(w.stores.value.any { it.id == "s-main" })
        assertEquals("门店码 HX001 已被使用", w.createStore("东门店", "hx001", "", BizType.Retail).exceptionOrNull()?.message)
        assertTrue(w.createStore("", "DM01", "", BizType.Retail).isFailure)
        val id = w.createStore("东门店", "dm-01", "深圳市福田区", BizType.Retail).getOrThrow()
        val s = w.current.value!!
        assertEquals(id, s.id); assertEquals("boss@dm.cn", s.owner); assertEquals("DM-01", s.code); assertEquals(BizType.Retail, s.biz)
        assertEquals("东门店", s.settings.shortName)
        // 资料复制，业务数据不带
        val demo = w.lanSnapshot().stores.getValue(DemoCatalog.RETAIL_STORE_ID)
        assertEquals(demo.products.map { it.id }, s.products.map { it.id })
        assertTrue(s.retail.sales.isEmpty() && s.retail.stockDocs.isEmpty() && s.payments.isEmpty() && s.members.isEmpty() && s.employees.isEmpty() && s.printers.isEmpty())
        assertEquals(demo.retail.settings, s.retail.settings)
        // 建了自己的门店：只看自己的，演示门店选不进去
        assertEquals(listOf(id), w.stores.value.map { it.id })
        w.selectStore("s-main"); assertEquals(id, w.current.value!!.id)
        // 能收银
        w.checkoutRetail(listOf(CartLine(1, "cola", 1)), false, "现金", 10_00, true).getOrThrow()
        // 重新加载后还在
        val r = LocalWorkspace(disk, backgroundScope) { 1_800_000_000_000L }; r.restore()
        assertEquals("boss@dm.cn", r.lanSnapshot().stores.getValue(id).owner)
    }

    @Test fun diningStoreTablesStartIdle() = runTest(UnconfinedTestDispatcher()) {
        val w = LocalWorkspace(Disk(), backgroundScope) { 1_800_000_000_000L }
        w.register("北门小馆", "boss@bm.cn", "secret123", "secret123").getOrThrow()
        w.createStore("北门店", "BM01", "", BizType.Dining).getOrThrow()
        val s = w.current.value!!
        assertTrue(s.tables.isNotEmpty() && s.tables.all { it.status == TableStatus.Idle })
        assertTrue(s.orders.isEmpty() && s.reservations.isEmpty() && s.kdsTickets.isEmpty())
        w.openTable(s.tables.first().id, 2).getOrThrow()
    }

    @Test fun demoAndSecondaryCannotCreate() = runTest(UnconfinedTestDispatcher()) {
        val w = LocalWorkspace(Disk(), backgroundScope) { 1_800_000_000_000L }
        w.enterDemo()
        assertEquals("体验账号不能新建门店，请注册商户", w.createStore("A", "AA01", "", BizType.Retail).exceptionOrNull()?.message)
        w.register("甲", "a@b.cn", "secret123", "secret123").getOrThrow()
        w.updateLan { it.copy(role = LanRole.Secondary) }
        assertTrue(w.createStore("A", "AA01", "", BizType.Retail).isFailure)
    }
}
