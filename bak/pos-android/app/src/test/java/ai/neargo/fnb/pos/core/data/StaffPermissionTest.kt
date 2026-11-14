package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 员工与权限：首个店长、PIN 上岗与锁定、角色权限、主管 PIN 授权、审计操作人、无员工兼容。 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaffPermissionTest {
    private class Store : SnapshotStore {
        var json: String? = null
        val commands = mutableListOf<JournalCommand>()
        override suspend fun load() = json
        override suspend fun save(json: String) { this.json = json }
        override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt { json = source; commands += command; return testReceipt(command) }
    }
    private class Clock(var now: Long = 1_800_000_000_000L)
    private fun runWs(block: suspend TestScope.(LocalWorkspace, Store, Clock) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val d = Store(); val c = Clock()
        val w = LocalWorkspace(d, backgroundScope) { c.now }
        w.enterDemo(); w.selectStore("s-main")
        block(w, d, c)
    }
    private fun LocalWorkspace.s() = lanSnapshot().store!!
    private fun LocalWorkspace.idle() = s().tables.first { it.status == TableStatus.Idle }.id
    private fun LocalWorkspace.emp(name: String) = s().employees.first { it.name == name }
    private suspend fun LocalWorkspace.team() {
        assertTrue(saveEmployee(null, "老板", Role.Owner, "1111").isSuccess) // 首个店长，自动上岗
        assertTrue(saveEmployee(null, "张主管", Role.Manager, "2222").isSuccess)
        assertTrue(saveEmployee(null, "小李", Role.Cashier, "3333").isSuccess)
        assertTrue(saveEmployee(null, "小王", Role.Waiter, "4444").isSuccess)
    }

    @Test fun noEmployeesKeepsFullAccess() = runWs { w, _, _ ->
        assertFalse(w.staff.value.required)
        assertTrue(w.addCategory("夜宵", "").isSuccess)
        assertEquals(DemoCatalog.STAFF, w.session.value.staff)
    }

    @Test fun firstEmployeeMustBeOwnerAndAutoSignsIn() = runWs { w, d, _ ->
        assertTrue(w.saveEmployee(null, "小李", Role.Cashier, "3333").exceptionOrNull()!!.message!!.contains("店长"))
        assertTrue(w.saveEmployee(null, "老板", Role.Owner, "12").isFailure) // PIN 太短
        assertTrue(w.saveEmployee(null, "老板", Role.Owner, "1111").isSuccess)
        assertEquals("老板", w.staff.value.current?.name)
        assertEquals("老板 · 店长", w.session.value.staff)
        val ev = d.commands.last { it.type == "EmployeeSaved" }
        assertFalse(ev.payload.contains("pinHash") || ev.payload.contains("salt") || ev.payload.contains("1111"))
        // 不能停用唯一店长
        assertTrue(w.saveEmployee(w.emp("老板").id, "老板", Role.Owner, null, enabled = false).isFailure)
    }

    @Test fun rolesLimitOperations() = runWs { w, d, _ ->
        w.team()
        // 服务员：可点餐，不能结账/建会员/改设置/交班/日结
        w.signOutStaff()
        assertTrue(w.staff.value.locked)
        assertTrue(w.openTable(w.idle(), 1).exceptionOrNull()!!.message!!.contains("PIN 上岗"))
        assertTrue(w.signInStaff(w.emp("小王").id, "4444").isSuccess)
        val t = w.idle()
        assertTrue(w.openTable(t, 1).isSuccess)
        w.addDish(t, w.s().products.first { it.id == "12" }, null, 1); w.sendToKitchen(t, true)
        val due = OrderRules.totals(w.s().orders.getValue(t)).due
        assertTrue(w.settle(t, due, "现金", true, due).exceptionOrNull()!!.message!!.contains("结账收款"))
        assertTrue(w.createMember("13900000000", "").isFailure)
        assertTrue(w.addCategory("夜宵", "").exceptionOrNull()!!.message!!.contains("小王"))
        assertTrue(w.closeShift(50_000, "小王").isFailure)
        assertTrue(w.saveEmployee(null, "新人", Role.Waiter, "5555").isFailure)
        // 收银：可结账、可交班，不能日结、不能改设置
        w.signOutStaff(); assertTrue(w.signInStaff(w.emp("小李").id, "3333").isSuccess)
        assertTrue(w.settle(t, due, "现金", true, due).isSuccess)
        assertEquals(w.emp("小李").id, d.commands.last { it.type == "PaymentRecorded" }.actorId)
        assertTrue(w.addCategory("夜宵", "").isFailure)
        assertTrue(w.closeDay("小李").exceptionOrNull()?.message.orEmpty().let { it.contains("日结") || it.contains("未结账") })
        // 主管：可改设置，不能管员工
        w.signOutStaff(); w.signInStaff(w.emp("张主管").id, "2222")
        assertTrue(w.addCategory("夜宵", "").isSuccess)
        assertTrue(w.saveEmployee(null, "新人", Role.Waiter, "5555").exceptionOrNull()!!.message!!.contains("员工与权限"))
        assertTrue(w.savePrinter(PrinterConfig("p", "前台", "10.0.0.1")).isSuccess)
        // 服务员不能配置打印机
        w.signOutStaff(); w.signInStaff(w.emp("小王").id, "4444")
        assertTrue(w.savePrinter(PrinterConfig("p2", "后厨", "10.0.0.2")).isFailure)
    }

    @Test fun pinLockoutAndDisabled() = runWs { w, _, c ->
        w.team(); w.signOutStaff()
        val id = w.emp("小李").id
        repeat(5) { assertTrue(w.signInStaff(id, "0000").isFailure) }
        assertTrue(w.signInStaff(id, "3333").exceptionOrNull()!!.message!!.contains("过多"))
        c.now += 61_000
        assertTrue(w.signInStaff(id, "3333").isSuccess)
        w.signOutStaff(); w.signInStaff(w.emp("老板").id, "1111")
        assertTrue(w.saveEmployee(id, "小李", Role.Cashier, null, enabled = false).isSuccess)
        w.signOutStaff()
        assertTrue(w.signInStaff(id, "3333").exceptionOrNull()!!.message!!.contains("停用"))
        // 重置 PIN
        w.signInStaff(w.emp("老板").id, "1111")
        assertTrue(w.saveEmployee(id, "小李", Role.Cashier, "9999", enabled = true).isSuccess)
        w.signOutStaff()
        assertTrue(w.signInStaff(id, "3333").isFailure); assertTrue(w.signInStaff(id, "9999").isSuccess)
    }

    @Test fun supervisorPinAuthorizesAdjustmentAndAudits() = runWs { w, _, _ ->
        w.team(); w.signOutStaff(); w.signInStaff(w.emp("小李").id, "3333")
        val t = w.idle(); w.openTable(t, 1)
        w.addDish(t, w.s().products.first { it.id == "12" }, null, 2); w.sendToKitchen(t, true)
        val uid = w.s().orders.getValue(t).lines.first().uid
        val req = AdjustmentRequest(AdjustmentKind.Gift, uid, reason = "老客")
        assertTrue(w.authorizeAdjustment(t, req, "小王", "4444").exceptionOrNull()!!.message!!.contains("无授权权限"))
        assertTrue(w.authorizeAdjustment(t, req, "张主管", "0000").isFailure)
        assertTrue(w.authorizeAdjustment(t, req, "张主管", "2222").isSuccess)
        val audit = w.s().adjustments.last()
        assertEquals("小李", audit.operator); assertEquals("张主管", audit.supervisor)
    }

    @Test fun storeSwitchClearsStaff() = runWs { w, _, _ ->
        w.team()
        assertNotNull(w.staff.value.current)
        w.selectStore("s-coast"); w.selectStore("s-main")
        assertNull(w.staff.value.current)
        assertTrue(w.staff.value.locked)
    }
}
