package ai.neargo.fnb.pos.core.print

import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.JournalReceipt
import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.testReceipt
import ai.neargo.fnb.pos.core.model.DeliveryState
import ai.neargo.fnb.pos.core.model.DeliveryRules
import ai.neargo.fnb.pos.core.model.PrintKind
import ai.neargo.fnb.pos.core.model.PrintState
import ai.neargo.fnb.pos.core.model.PrinterConfig
import ai.neargo.fnb.pos.core.model.TableStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * 假 ESC/POS 网口打印机：回应 DLE EOT 状态查询；可模拟缺纸、发送中断开、发送后报错。
 * jobs 记录每次连接收到的打印数据（去掉状态查询字节）。
 */
class FakeEscPosPrinter(var paperEnd: Boolean = false, var dropAfterBytes: Int = -1, var errorAfter: Boolean = false, var silent: Boolean = false) : AutoCloseable {
    private val server = ServerSocket(0)
    val port get() = server.localPort
    val jobs = CopyOnWriteArrayList<ByteArray>()
    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    s.use { sock ->
                        val inp = sock.getInputStream(); val out = sock.getOutputStream()
                        val data = ByteArrayOutputStream(); var total = 0
                        sock.soTimeout = 3_000
                        try {
                            while (true) {
                                val b = inp.read(); if (b < 0) break
                                if (b == 0x10) { // DLE EOT n
                                    val eot = inp.read(); val n = inp.read()
                                    if (eot == 0x04 && !silent) {
                                        val printed = data.size() > 0
                                        val v = when (n) { 4 -> if (paperEnd) 0x72 else 0x12; 1 -> if (paperEnd) 0x1A else 0x12; 2 -> if (errorAfter && printed) 0x52 else 0x12; else -> 0x12 }
                                        out.write(v); out.flush()
                                    }
                                    continue
                                }
                                data.write(b); total++
                                if (dropAfterBytes in 0..total) break
                            }
                        } catch (_: Exception) {}
                        if (data.size() > 0) jobs += data.toByteArray()
                    }
                }
            }
        }
    }
    fun text(i: Int) = String(jobs[i], EscPos.CHARSET)
    /** 等待打印机登记收到的数据（连接关闭后才登记）。 */
    fun awaitJobs(n: Int): Int { val end = System.currentTimeMillis() + 3_000; while (jobs.size < n && System.currentTimeMillis() < end) Thread.sleep(10); return jobs.size }
    override fun close() = server.close()
}

class EscPosPrintTest {
    private class Store : SnapshotStore {
        @Volatile var json: String? = null
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { json = source; return testReceipt(command) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }
    private class Clock(@Volatile var now: Long = 1_800_000_000_000L)

    private suspend fun ws(clock: Clock = Clock(), store: Store = Store()): LocalWorkspace {
        val w = LocalWorkspace(store, scope) { clock.now }
        w.enterDemo(); w.selectStore("s-main")
        return w
    }
    private fun LocalWorkspace.s() = lanSnapshot().store!!
    private suspend fun LocalWorkspace.kitchenOrder(): String {
        val t = s().tables.first { it.status == TableStatus.Idle }.id
        openTable(t, 1); addDish(t, s().products.first { it.id == "12" }, null, 1); sendToKitchen(t, true)
        return t
    }

    @Test fun encodesChineseWithInitBoldAndCut() {
        val b = EscPos.encode(listOf("厨房单 A01", "宫保鸡丁 ×1"))
        assertArrayEquals(byteArrayOf(0x1B, 0x40), b.copyOf(2))
        assertTrue(String(b, EscPos.CHARSET).contains("宫保鸡丁 ×1"))
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 0x42, 0x00), b.copyOfRange(b.size - 4, b.size))
    }

    @Test fun printsThroughGatewayAndMarksKitchenDelivered() = runBlocking {
        FakeEscPosPrinter().use { printer ->
            val w = ws()
            w.savePrinter(PrinterConfig("k1", "厨房热菜", "127.0.0.1", printer.port))
            w.setRoute("厨房", "k1")
            w.kitchenOrder()
            assertEquals(1, PrintGateway(w).drain())
            val job = w.s().printJobs.first { it.kind == PrintKind.Kitchen }
            assertEquals(PrintState.Sent, job.state); assertEquals("k1", job.printerId)
            assertEquals(1, printer.awaitJobs(1)); assertTrue(printer.text(0).contains("酸梅汤"))
            assertEquals(DeliveryState.Delivered, w.s().kitchenDeliveries.single().state); assertEquals("printer", w.s().kitchenDeliveries.single().source)
            // 前台单未配置路由：仍走模拟，不发往厨房打印机
            w.preBill(w.s().tables.first { it.status == TableStatus.Dining }.id)
            assertEquals(0, PrintGateway(w).drain()); assertEquals(1, printer.jobs.size)
        }
    }

    @Test fun offlineFailsThenRetriesOnceWhenBack() = runBlocking {
        val clock = Clock(); val w = ws(clock)
        val port = ServerSocket(0).use { it.localPort } // 此时没有打印机监听
        w.savePrinter(PrinterConfig("k1", "厨房", "127.0.0.1", port)); w.setRoute("厨房", "k1")
        w.kitchenOrder()
        PrintGateway(w).drain()
        val failed = w.s().printJobs.single { it.kind == PrintKind.Kitchen }
        assertEquals(PrintState.Failed, failed.state); assertTrue(failed.error.contains("无法连接"))
        assertEquals(DeliveryState.Failed, w.s().kitchenDeliveries.single().state)
        assertEquals(0, PrintGateway(w).drain()) // 退避期内不重试
        FakeEscPosPrinter().use { printer ->
            w.savePrinter(PrinterConfig("k1", "厨房", "127.0.0.1", printer.port))
            clock.now += 60_000
            assertEquals(1, PrintGateway(w).drain())
            assertEquals(PrintState.Sent, w.s().printJobs.single { it.kind == PrintKind.Kitchen }.state)
            assertEquals(1, printer.awaitJobs(1))
            assertEquals(0, PrintGateway(w).drain()); assertEquals(1, printer.jobs.size) // 只打一次
        }
    }

    @Test fun paperEndIsNotSent() = runBlocking {
        FakeEscPosPrinter(paperEnd = true).use { printer ->
            val w = ws()
            w.savePrinter(PrinterConfig("k1", "厨房", "127.0.0.1", printer.port)); w.setRoute("厨房", "k1")
            w.kitchenOrder(); PrintGateway(w).drain()
            val j = w.s().printJobs.single { it.kind == PrintKind.Kitchen }
            assertEquals(PrintState.Failed, j.state); assertEquals("打印机缺纸", j.error)
            assertTrue(printer.jobs.isEmpty()) // 未发送任何打印数据
        }
    }

    @Test fun midSendDropIsUnknownAndNeedsManualCheck() = runBlocking {
        FakeEscPosPrinter(errorAfter = true).use { printer ->
            val clock = Clock(); val w = ws(clock)
            w.savePrinter(PrinterConfig("k1", "厨房", "127.0.0.1", printer.port)); w.setRoute("厨房", "k1")
            val t = w.kitchenOrder(); PrintGateway(w).drain()
            val j = w.s().printJobs.single { it.kind == PrintKind.Kitchen }
            assertEquals(PrintState.Unknown, j.state)
            assertEquals(DeliveryState.Unknown, w.s().kitchenDeliveries.single().state)
            assertNotNull(DeliveryRules.checkoutBlock(w.s(), t)) // 未知阻止结账
            clock.now += 3_600_000
            assertEquals(0, PrintGateway(w).drain()); assertEquals(1, printer.awaitJobs(1)) // 不自动重打
            assertTrue(w.resolveUnknown(j.id, printed = false, reason = " ").isFailure)
            printer.errorAfter = false
            assertTrue(w.resolveUnknown(j.id, printed = false, reason = "厨房没收到").isSuccess)
            val re = w.s().printJobs.single { it.reprintOf == j.id }
            assertEquals(PrintState.Pending, re.state)
            assertNull(DeliveryRules.checkoutBlock(w.s(), t).takeIf { w.s().kitchenDeliveries.any { d -> d.jobId == j.id && d.needsAction } })
            assertEquals(1, PrintGateway(w).drain())
            assertEquals(PrintState.Sent, w.s().printJobs.single { it.id == re.id }.state)
            assertEquals(2, printer.awaitJobs(2)); assertTrue(printer.text(1).contains("补打"))
            assertNull(DeliveryRules.checkoutBlock(w.s(), t))
        }
    }

    @Test fun droppedConnectionDuringWriteIsUnknown() {
        FakeEscPosPrinter(dropAfterBytes = 10).use { printer ->
            val big = EscPos.encode(List(2000) { "宫保鸡丁 ×1 ¥48.00 测试长单据内容" })
            val r = EscPosClient().send("127.0.0.1", printer.port, big)
            assertTrue("$r", r is SendOutcome.Unknown)
        }
    }

    @Test fun restartWhileSendingBecomesUnknown() = runBlocking {
        FakeEscPosPrinter().use { printer ->
            val store = Store(); val w = ws(store = store)
            w.savePrinter(PrinterConfig("k1", "厨房", "127.0.0.1", printer.port)); w.setRoute("厨房", "k1")
            w.kitchenOrder()
            val (job, _) = w.claimNextPrint()!! // 已标记发送中后进程退出
            val restarted = LocalWorkspace(store, scope); restarted.restore()
            val j = restarted.lanSnapshot().store!!.printJobs.single { it.id == job.id }
            assertEquals(PrintState.Unknown, j.state); assertTrue(j.error.contains("重启"))
            assertEquals(0, PrintGateway(restarted).drain()); assertTrue(printer.jobs.isEmpty())
        }
    }

    @Test fun printerValidationAndTestPage() = runBlocking {
        FakeEscPosPrinter().use { printer ->
            val w = ws()
            assertTrue(w.savePrinter(PrinterConfig("x", "", "127.0.0.1")).isFailure)
            assertTrue(w.savePrinter(PrinterConfig("x", "前台", "bad host!")).isFailure)
            assertTrue(w.savePrinter(PrinterConfig("x", "前台", "127.0.0.1", 70000)).isFailure)
            assertTrue(w.savePrinter(PrinterConfig("x", "前台", "127.0.0.1", printer.port, width = 48)).isSuccess)
            assertTrue(w.testPrint("x").isSuccess)
            assertEquals(1, PrintGateway(w).drain())
            printer.awaitJobs(1); assertTrue(printer.text(0).contains("打印测试") && printer.text(0).contains("80mm"))
        }
    }

    @Test fun kitchenTicketsSplitByStationAndRouteToStationPrinters() = runBlocking {
        FakeEscPosPrinter().use { hot -> FakeEscPosPrinter().use { bar ->
            val w = ws()
            assertTrue(w.saveKdsStations(listOf("热菜", "吧台", "凉菜"), mapOf("c-drink" to "吧台")).isSuccess)
            w.savePrinter(PrinterConfig("k1", "热菜机", "127.0.0.1", hot.port)); w.savePrinter(PrinterConfig("b1", "吧台机", "127.0.0.1", bar.port))
            w.setRoute("厨房", "k1") // 旧「厨房」路由：默认档口未单独设置时沿用
            w.setRoute("吧台", "b1")
            val t = w.s().tables.first { it.status == TableStatus.Idle }.id
            w.openTable(t, 2)
            w.addDish(t, w.s().products.first { it.id == "5" }, null, 1)
            w.addDish(t, w.s().products.first { it.id == "12" }, null, 2)
            w.sendToKitchen(t, true)
            val jobs = w.s().printJobs.filter { it.kind == PrintKind.Kitchen }
            assertEquals(listOf("热菜", "吧台"), jobs.map { it.station })
            assertTrue(jobs.all { it.id.contains("@") })
            // 打印单号与厨房屏任务卡一一对应
            assertEquals(w.s().kdsTickets.map { "P-" + it.id }.toSet(), jobs.map { it.id }.toSet())
            assertEquals(2, PrintGateway(w).drain())
            hot.awaitJobs(1); bar.awaitJobs(1)
            assertTrue(hot.text(0).contains("【厨房单·热菜】") && hot.text(0).contains("麻婆豆腐") && !hot.text(0).contains("酸梅汤"))
            assertTrue(bar.text(0).contains("【厨房单·吧台】") && bar.text(0).contains("酸梅汤") && !bar.text(0).contains("麻婆豆腐"))
            assertEquals(setOf("热菜打印机", "吧台打印机"), w.s().kitchenDeliveries.map { it.target }.toSet())
            assertTrue(w.s().kitchenDeliveries.all { it.state == DeliveryState.Delivered })
            // 退吧台饮品：退菜单只打到吧台打印机
            val drink = w.s().orders.getValue(t).lines.first { it.dishId == "12" }
            assertTrue(w.authorizeAdjustment(t, ai.neargo.fnb.pos.core.model.AdjustmentRequest(ai.neargo.fnb.pos.core.model.AdjustmentKind.Void, drink.uid, 1, reason = "不要了"), "manager@demo.local", "demo12345").isSuccess)
            assertEquals(1, PrintGateway(w).drain())
            bar.awaitJobs(2); assertTrue(bar.text(1).contains("退菜单·吧台")); assertEquals(1, hot.jobs.size)
            // 未设置打印机的档口跟随默认档口；前台不受厨房路由影响
            val rules = ai.neargo.fnb.pos.core.model.KdsRules
            val cold = jobs.first().copy(station = "凉菜")
            assertEquals("k1", rules.printRoute(w.s().printRoutes, w.s().kdsStations, cold))
            w.setRoute("热菜", "b1")
            assertEquals("b1", rules.printRoute(w.s().printRoutes, w.s().kdsStations, cold))
            assertEquals("", rules.printRoute(w.s().printRoutes, w.s().kdsStations, cold.copy(kind = PrintKind.PreBill)))
        } }
    }
}
