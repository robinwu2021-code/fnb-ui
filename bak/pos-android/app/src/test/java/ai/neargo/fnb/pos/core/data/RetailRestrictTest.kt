package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

/** 零售设置补齐：临期自动折扣、禁售时段、一码多品。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailRestrictTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private suspend fun mart(scope: kotlinx.coroutines.CoroutineScope) = LocalWorkspace(Disk(), scope) { 1_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }

    @Test fun expiryDiscountAppliesAtCheckout() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        w.saveRetailSettings(w.retail.value.settings.copy(expiryPct = 80)).getOrThrow()
        val today = java.time.Instant.ofEpochMilli(1_000_000L).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val ps = RetailCheckout.priced(w.current.value!!.products, w.retail.value.settings, today)
        assertEquals(3192, ps.first { it.id == "milk" }.price) // 临期：39.90 × 8 折
        assertEquals(350, ps.first { it.id == "cola" }.price)   // 不临期
        val sale = w.checkoutRetail(listOf(CartLine(1, "milk", 1)), true, "现金", 9999, true).getOrThrow()
        assertEquals(3190, sale.paid) // 打折后低于会员价，会员价不再减；抹零到角
        assertTrue(w.saveRetailSettings(w.retail.value.settings.copy(expiryPct = 5)).isFailure)
    }

    @Test fun restrictWindowAndSharedBarcode() = runTest(UnconfinedTestDispatcher()) {
        val rs = RetailSettings(restrictFrom = "22:00", restrictTo = "08:00", restrictCats = listOf("r-drinks"))
        assertTrue(RetailCheckout.inRestrict(rs, LocalTime.of(23, 0))); assertTrue(RetailCheckout.inRestrict(rs, LocalTime.of(7, 59)))
        assertFalse(RetailCheckout.inRestrict(rs, LocalTime.of(8, 0))); assertFalse(RetailCheckout.inRestrict(rs, LocalTime.of(12, 0)))
        assertFalse(RetailCheckout.inRestrict(RetailSettings(), LocalTime.of(23, 0)))
        val w = mart(backgroundScope)
        val ps = w.current.value!!.products
        assertEquals("可口可乐 330ml 在 22:00–08:00 禁售", RetailCheckout.restrictBlock(ps, listOf(CartLine(1, "cola", 1)), rs, LocalTime.of(23, 0)))
        assertNull(RetailCheckout.restrictBlock(ps, listOf(CartLine(1, "chips", 1)), rs, LocalTime.of(23, 0)))
        assertTrue(w.saveRetailSettings(rs.copy(restrictFrom = "25:00")).isFailure)
        w.saveRetailSettings(rs.copy(restrictFrom = "00:00", restrictTo = "23:59")).getOrThrow()
        assertTrue(w.checkoutRetail(listOf(CartLine(1, "cola", 1)), false, "现金", 9999, true).exceptionOrNull()?.message!!.contains("禁售"))
        // 一码多品
        val dup = ps + ps.first { it.id == "cola" }.copy(id = "cola-zero", name = "零度可乐 330ml")
        assertEquals(listOf("cola", "cola-zero"), BarcodeRules.findAllByCode(dup, "6954767423579") { it.retail?.barcodes.orEmpty() }.map { it.id })
    }
}
