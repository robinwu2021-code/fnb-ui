package ai.neargo.fnb.pos.state

import ai.neargo.fnb.pos.data.DiningTable
import ai.neargo.fnb.pos.data.Dish
import ai.neargo.fnb.pos.data.MockData
import ai.neargo.fnb.pos.data.TableStatus
import ai.neargo.fnb.pos.theme.Density
import ai.neargo.fnb.pos.theme.Skin
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class LineStatus { New, Sent }

enum class NavSide(val label: String) { Top("上"), Left("左"), Right("右") }
enum class DetailSide(val label: String) { Left("左"), Right("右") }

/**
 * 主框架 L1–L6：导航区（分类 / 桌区 / 订单状态）放上、左、右，详情区（账单坞 / 桌台详情 / 订单详情）放左、右。
 * 所有平板页同时生效；应用宽度 < 960dp 时强制导航在上、详情收成底部条。
 */
enum class PosFrame(val nav: NavSide, val detail: DetailSide, val note: String) {
    L1(NavSide.Left, DetailSide.Right, "默认 · 右手结账"),
    L2(NavSide.Top, DetailSide.Right, "宫格最宽"),
    L3(NavSide.Top, DetailSide.Left, "已点在左"),
    L4(NavSide.Right, DetailSide.Left, "左手看单"),
    L5(NavSide.Left, DetailSide.Left, "右手点菜"),
    L6(NavSide.Right, DetailSide.Right, "单手操作"),
}

enum class MenuView(val label: String) { Image("图文"), Text("文字") }

/** 订单行。同一道菜不同做法分成两行。 */
data class OrderLine(
    val uid: Int,
    val dishId: String,
    val name: String,
    val spec: String,
    val unit: Int,
    val qty: Int,
    val status: LineStatus,
    val rush: Boolean = false,
    val served: Boolean = false,
)

enum class DineMode(val label: String) { DineIn("堂食"), TakeOut("外带"), Delivery("外卖") }

data class Totals(val sub: Int, val service: Int, val member: Int, val round: Int, val due: Int, val items: Int)

/**
 * 收银台状态（骨架只演示一张当前订单）。
 * 接后端时，把这里的 mock 换成 repository，界面不需要改。
 */
class PosStore : ViewModel() {
    private var uid = 1

    // —— 设置 ——
    var skin by mutableStateOf(Skin.Forest)
    var dark by mutableStateOf(false)
    var frame by mutableStateOf(PosFrame.L1)
    var density by mutableStateOf(Density.Compact)
    var menuView by mutableStateOf(MenuView.Image)
    var online by mutableStateOf(true)
    var printerOk by mutableStateOf(true)

    // —— 桌台 ——
    val tables = mutableStateListOf<DiningTable>().apply { addAll(MockData.tables) }

    // —— 当前订单 ——
    var orderNo by mutableStateOf("#1024")
    var tableId by mutableStateOf("A03")
    var guests by mutableStateOf(4)
    var minutes by mutableStateOf(26)
    var mode by mutableStateOf(DineMode.DineIn)
    var member by mutableStateOf(false)
    var roundOff by mutableStateOf(false)
    val lines = mutableStateListOf(
        line("101", 1, LineStatus.New, spec = "大份 · 少糖", unit = 11800),
        line("401", 4, LineStatus.New),
        line("301", 1, LineStatus.Sent, spec = "微辣", served = true),
        line("201", 1, LineStatus.Sent),
    )

    val pending by derivedStateOf { lines.filter { it.status == LineStatus.New } }
    val sent by derivedStateOf { lines.filter { it.status == LineStatus.Sent } }

    val totals by derivedStateOf {
        val sub = lines.sumOf { it.unit * it.qty }
        val service = Math.round(sub * 0.1).toInt()
        val memberOff = if (member) Math.round(sub * 0.05).toInt() else 0
        val gross = sub + service - memberOff
        val round = if (roundOff) gross % 100 else 0
        Totals(sub, service, memberOff, round, gross - round, lines.sumOf { it.qty })
    }

    fun qtyOf(dishId: String) = lines.filter { it.dishId == dishId }.sumOf { it.qty }
    fun qtyInCat(cat: String) = lines.filter { MockData.dish(it.dishId).cat == cat }.sumOf { it.qty }

    // —— 提示条 ——
    var toast by mutableStateOf<String?>(null)
        private set
    private var toastJob: Job? = null

    fun toast(msg: String) {
        toast = msg
        toastJob?.cancel()
        toastJob = viewModelScope.launch { delay(2200); toast = null }
    }

    // —— 点餐 ——
    fun addDish(d: Dish, spec: String = "", unit: Int = d.price, qty: Int = 1): Boolean {
        if (d.soldOut) { toast("${d.name}已沽清"); return false }
        val i = lines.indexOfFirst { it.status == LineStatus.New && it.dishId == d.id && it.spec == spec }
        if (i >= 0) lines[i] = lines[i].copy(qty = lines[i].qty + qty)
        else lines.add(0, OrderLine(uid++, d.id, d.name, spec, unit, qty, LineStatus.New))
        return true
    }

    fun changeQty(l: OrderLine, delta: Int) {
        val i = lines.indexOfFirst { it.uid == l.uid }.takeIf { it >= 0 } ?: return
        val q = lines[i].qty + delta
        if (q <= 0) lines.removeAt(i) else lines[i] = lines[i].copy(qty = q)
    }

    fun remove(l: OrderLine) {
        lines.removeAll { it.uid == l.uid }
        toast("已删除 ${l.name}")
    }

    fun rush(l: OrderLine) {
        val i = lines.indexOfFirst { it.uid == l.uid }.takeIf { it >= 0 } ?: return
        lines[i] = lines[i].copy(rush = true)
        toast("已催菜：${l.name}")
    }

    fun sendToKitchen() {
        val n = pending.size
        if (n == 0) return
        for (i in lines.indices) if (lines[i].status == LineStatus.New) lines[i] = lines[i].copy(status = LineStatus.Sent)
        toast(
            when {
                !online -> "已离线送厨 $n 道菜，联网后自动同步"
                !printerOk -> "已送厨 $n 道菜。厨房打印机缺纸，到打印面板重打"
                else -> "已送厨 $n 道菜"
            },
        )
    }

    fun hold() {
        toast("已挂单 $orderNo")
    }

    // —— 桌台 ——
    fun openTable(t: DiningTable, guestCount: Int) {
        val i = tables.indexOfFirst { it.id == t.id }
        tables[i] = t.copy(status = TableStatus.Dining, guests = guestCount, minutes = 0)
        tableId = t.id
        guests = guestCount
        minutes = 0
        orderNo = "#" + (1032 + i)
        lines.clear()
        toast("${t.id} 已开台，$guestCount 人")
    }

    fun switchTo(t: DiningTable) {
        tableId = t.id
        guests = t.guests
        minutes = t.minutes
    }

    /** 收款成功：释放桌台，清空当前订单。 */
    fun settle() {
        val i = tables.indexOfFirst { it.id == tableId }
        if (i >= 0) tables[i] = tables[i].copy(status = TableStatus.Free, guests = 0, minutes = 0, amount = 0)
        lines.clear()
        member = false
        roundOff = false
    }

    private fun line(
        dishId: String, qty: Int, status: LineStatus,
        spec: String = "", unit: Int? = null, served: Boolean = false,
    ): OrderLine {
        val d = MockData.dish(dishId)
        return OrderLine(uid++, dishId, d.name, spec, unit ?: d.price, qty, status, served = served)
    }
}
