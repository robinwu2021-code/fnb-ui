package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 零售接真实会员档案：会员价、积分、储值扣款与流水；退到储值卡退回余额并扣回积分。 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetailMemberTest {
    private class Disk : SnapshotStore {
        var data: String? = null
        override suspend fun load() = data
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { save(source); return testReceipt(command) }
        override suspend fun save(json: String) { data = json }
    }
    private suspend fun mart(scope: kotlinx.coroutines.CoroutineScope) = LocalWorkspace(Disk(), scope) { 1_000_000L }.also { it.enterDemo(); it.selectStore(DemoCatalog.RETAIL_STORE_ID) }
    private fun LocalWorkspace.member(id: String) = current.value!!.members.first { it.id == id }

    @Test fun storedValueAndPointsGoToMember() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        val wang0 = w.member("rm-wang"); val ppy = w.current.value!!.settings.pointsPerYuan
        val lines = listOf(CartLine(1, "milk", 1)) // 会员价 36.90
        val sale = w.checkoutRetail(lines, true, "储值卡", 0, true, CheckoutExtras(memberId = "rm-wang")).getOrThrow()
        assertEquals(3690, sale.paid); assertEquals("王女士", sale.member); assertEquals("rm-wang", sale.memberId)
        val wang = w.member("rm-wang")
        assertEquals(wang0.balance - 3690, wang.balance); assertEquals(wang0.points + 36 * ppy, wang.points); assertEquals(wang0.visits + 1, wang.visits)
        val txns = w.current.value!!.memberTxns.filter { it.ref == sale.no }
        assertEquals(setOf(MemberTxnKind.Consume, MemberTxnKind.Points).filter { ppy > 0 || it == MemberTxnKind.Consume }.toSet(), txns.map { it.kind }.toSet())
        // 退到储值卡：退回余额、扣回积分
        w.returnRetail(ReturnRequest(sale.no, mapOf("L1" to 1), ReturnRules.Reason.Unwanted, true, "储值卡"), true).getOrThrow()
        assertEquals(wang0.balance, w.member("rm-wang").balance); assertEquals(wang0.points, w.member("rm-wang").points)
        // 余额不够
        assertEquals("会员储值余额不足", w.checkoutRetail(lines, true, "储值卡", 0, true, CheckoutExtras(memberId = "rm-li")).exceptionOrNull()?.message)
        assertEquals("会员不存在", w.checkoutRetail(lines, true, "现金", 9999, true, CheckoutExtras(memberId = "nope")).exceptionOrNull()?.message)
    }

    @Test fun createMemberInRetailStore() = runTest(UnconfinedTestDispatcher()) {
        val w = mart(backgroundScope)
        val m = w.createMember("13700001111", "赵女士").getOrThrow()
        val sale = w.checkoutRetail(listOf(CartLine(1, "cola", 2)), true, "现金", 1000, true, CheckoutExtras(memberId = m.id)).getOrThrow()
        assertEquals("赵女士", sale.member); assertEquals(1, w.member(m.id).visits)
    }
}
