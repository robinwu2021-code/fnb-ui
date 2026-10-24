package ai.neargo.fnb.pos.data

/** 示例数据（本地演示用，不接后端），与 pos-app/src/mock/data.ts 同源。金额一律以「分」为单位。 */

data class Category(val id: String, val name: String)

data class Dish(
    val id: String,
    val name: String,
    val cat: String,
    val price: Int,
    val desc: String,
    val tags: List<String> = emptyList(),
    val soldOut: Boolean = false,
    val stock: Int? = null,
    val hasSpec: Boolean = false,
    val image: String? = null,
)

enum class Zone(val label: String) { Hall("大厅"), Room("包间"), Take("外带") }

enum class TableStatus(val label: String) { Free("空闲"), Dining("用餐中"), Billing("待结账"), Reserved("已预订") }

data class DiningTable(
    val id: String,
    val zone: Zone,
    val seats: Int,
    val status: TableStatus,
    val guests: Int = 0,
    val minutes: Int = 0,
    val amount: Int = 0,
    val note: String? = null,
)

data class OrderSummary(
    val no: String,
    val label: String,
    val status: String,
    val items: Int,
    val amount: Int,
    val time: String,
)

/** 规格组：单选。delta 为加价（分）。 */
data class SpecGroup(val name: String, val options: List<SpecOption>)
data class SpecOption(val name: String, val delta: Int = 0)

private const val IMG = "https://www.themealdb.com/images/media/meals/"

object MockData {
    val categories = listOf(
        Category("hot", "热销"), Category("sig", "招牌菜"), Category("cold", "凉菜"), Category("wok", "热菜"),
        Category("staple", "主食"), Category("soup", "汤羹"), Category("drink", "饮品"), Category("set", "套餐"),
    )

    val dishes = listOf(
        Dish("101", "松鼠桂鱼", "sig", 8800, "招牌 · 酸甜", hasSpec = true, image = IMG + "1529442316.jpg"),
        Dish("102", "酸菜鱼", "sig", 6200, "新品", listOf("新品"), hasSpec = true, image = IMG + "1520084413.jpg"),
        Dish("103", "东坡肉", "sig", 4800, "今日售完", soldOut = true, image = IMG + "wxuvuv1511299147.jpg"),
        Dish("201", "口水鸡", "cold", 3200, "凉菜 · 辣", listOf("辣"), image = IMG + "cj56fs1762340001.jpg"),
        Dish("202", "拍黄瓜", "cold", 1200, "素", listOf("素"), image = IMG + "93iok31766436070.jpg"),
        Dish("301", "宫保鸡丁", "wok", 3800, "微辣 · 含花生", listOf("辣"), hasSpec = true, image = IMG + "1525872624.jpg"),
        Dish("302", "干煸四季豆", "wok", 2600, "特价 · 原价 ¥32", listOf("特价", "素"), image = IMG + "c7lzrl1683208757.jpg"),
        Dish("303", "上汤时蔬", "wok", 2800, "时蔬", listOf("素"), image = IMG + "zry07j1763779321.jpg"),
        Dish("401", "米饭", "staple", 300, "按碗", image = IMG + "kw92t41604181871.jpg"),
        Dish("402", "葱油拌面", "staple", 1600, "主食", image = IMG + "118oj61763423896.jpg"),
        Dish("501", "番茄蛋汤", "soup", 1800, "汤羹", image = IMG + "1529446137.jpg"),
        Dish("601", "桂花乌龙", "drink", 1200, "按杯", stock = 8),
        Dish("701", "双人套餐", "set", 12800, "套餐 · 特价", listOf("特价"), image = IMG + "wuyd2h1765655837.jpg"),
    )

    val hot = listOf("101", "301", "201", "302", "401", "601", "701", "102")

    val filterTags = listOf("辣", "素", "新品", "特价")

    val specs = listOf(
        SpecGroup("份量", listOf(SpecOption("小份", -1000), SpecOption("中份"), SpecOption("大份", 3000))),
        SpecGroup("口味", listOf(SpecOption("正常"), SpecOption("少糖"), SpecOption("微辣"), SpecOption("不辣"))),
    )

    val tables = listOf(
        DiningTable("A01", Zone.Hall, 2, TableStatus.Free),
        DiningTable("A02", Zone.Hall, 2, TableStatus.Dining, 2, 52, 9600),
        DiningTable("A03", Zone.Hall, 4, TableStatus.Dining, 4, 26, 20350),
        DiningTable("A04", Zone.Hall, 4, TableStatus.Free),
        DiningTable("A05", Zone.Hall, 6, TableStatus.Dining, 6, 41, 27600),
        DiningTable("A06", Zone.Hall, 4, TableStatus.Free),
        DiningTable("A07", Zone.Hall, 4, TableStatus.Free),
        DiningTable("A08", Zone.Hall, 4, TableStatus.Billing, 3, 86, 36800),
        DiningTable("A09", Zone.Hall, 6, TableStatus.Reserved, note = "19:00 · 陈先生"),
        DiningTable("A10", Zone.Hall, 4, TableStatus.Free),
        DiningTable("B01", Zone.Room, 10, TableStatus.Dining, 8, 33, 68800),
        DiningTable("B02", Zone.Room, 10, TableStatus.Free),
        DiningTable("B03", Zone.Room, 10, TableStatus.Billing, 6, 95, 51200),
        DiningTable("T31", Zone.Take, 0, TableStatus.Dining, 0, 6, 2600, "外带 #1031"),
    )

    val orders = listOf(
        OrderSummary("#1024", "A03", "进行中", 6, 20350, "18:06"),
        OrderSummary("#1021", "A02", "进行中", 4, 9600, "17:40"),
        OrderSummary("#1031", "外带", "挂起", 2, 2600, "18:44"),
        OrderSummary("#1018", "A08", "待结账", 7, 36800, "17:06"),
        OrderSummary("#1019", "A03", "已结账", 3, 3720, "18:30"),
    )

    fun dish(id: String) = dishes.first { it.id == id }
}

/** 金额统一两位小数；菜单里整数价可省略小数（keepZero = false）。 */
fun yuan(fen: Int, keepZero: Boolean = true): String {
    val sign = if (fen < 0) "-" else ""
    val abs = kotlin.math.abs(fen)
    return if (!keepZero && abs % 100 == 0) "$sign¥${abs / 100}"
    else "$sign¥${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
