package ai.neargo.fnb.pos.core.print

import ai.neargo.fnb.pos.core.data.DemoCatalog
import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.model.BarcodeRules
import ai.neargo.fnb.pos.core.model.LabelTpl
import ai.neargo.fnb.pos.core.model.PrintKind
import ai.neargo.fnb.pos.core.model.PrintState
import ai.neargo.fnb.pos.core.model.PrinterConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** 价签打印：进「标签机」路由，按 TSPL（GB18030、CRLF）发出，不发 ESC/POS 状态查询；条码扫回来能认出商品。 */
class LabelPrintTest {
    private class Store : SnapshotStore {
        @Volatile var json: String? = null
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { json = source; return testReceipt(command) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }

    @Test fun labelsGoToLabelPrinterAsTspl() = runBlocking {
        FakeEscPosPrinter().use { printer ->
            val w = LocalWorkspace(Store(), scope) { 1_800_000_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }
            w.savePrinter(PrinterConfig("lb1", "价签机", "127.0.0.1", printer.port)).getOrThrow()
            w.setRoute("标签机", "lb1").getOrThrow()
            assertEquals(2, w.printLabels(listOf("cola", "apple"), LabelTpl.L60x40).getOrThrow())
            val job = w.lanSnapshot().store!!.printJobs.single { it.kind == PrintKind.Label }
            assertEquals(1, PrintGateway(w).drain())
            assertEquals(PrintState.Sent, w.lanSnapshot().store!!.printJobs.first { it.id == job.id }.state)
            assertEquals(1, printer.awaitJobs(1))
            val sent = printer.text(0)
            assertTrue(sent.startsWith("SIZE 60 mm,40 mm\r\n")); assertEquals(2, Regex("PRINT 1,1\r\n").findAll(sent).count())
            assertTrue(sent.contains("可口可乐 330ml")) // GB18030 中文
            assertFalse(printer.jobs[0].contains(0x10)) // 没有 DLE EOT 状态查询
            // 价签上的码（EAN-13 前 12 位，校验位由标签机算）补上校验位就能在收银认出商品
            val d12 = Regex("BARCODE [^\\r]*\"(\\d{12})\"").findAll(sent).map { it.groupValues[1] }.toList()
            val ps = w.lanSnapshot().store!!.products
            assertEquals(listOf("cola", "apple"), d12.map { c -> BarcodeRules.findByCode(ps, c + BarcodeRules.ean13Check(c)) { it.retail?.barcodes.orEmpty() }?.id })
        }
    }

    @Test fun withoutRouteLabelsAreSimulated() = runBlocking {
        val w = LocalWorkspace(Store(), scope) { 1_800_000_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }
        w.printLabels(listOf("cola"), LabelTpl.L40x30).getOrThrow()
        assertEquals(0, PrintGateway(w).drain()) // 没有标签机：网关不领取
        assertTrue(w.lanSnapshot().store!!.printJobs.any { it.kind == PrintKind.Label })
    }
}

private fun ByteArray.contains(b: Int) = any { it.toInt() == b }
