package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.Area
import ai.neargo.fnb.pos.core.model.Category
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.Dish
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderLine
import ai.neargo.fnb.pos.core.model.SpecGroup
import ai.neargo.fnb.pos.core.model.SpecOption
import ai.neargo.fnb.pos.core.model.TableOrder
import ai.neargo.fnb.pos.core.model.TableStatus

private const val WIKI = "https://thumb.wikimedia.org/wikipedia/commons/thumb/"

/** 演示数据，与 prototypes/v3/menu.mjs、workbenches/tables.mjs 同源。只在 data 层使用，界面不直接引用。 */
internal object DemoCatalog {
    const val STORE = "南山小馆 · 总店"
    const val STAFF = "林晓 · 1号收银台"
    const val START_TABLE = "A03"

    val categories = listOf(
        Category("hot", "热销"), Category("招牌", "招牌"), Category("凉菜", "凉菜"), Category("热菜", "热菜"),
        Category("主食", "主食"), Category("汤羹", "汤羹"), Category("饮品", "饮品"), Category("套餐", "套餐"), Category("甜品", "甜品"),
    )

    val dishes = listOf(
        Dish("1", "宫保鸡丁", "Kung Pao Chicken", "热菜", 4800, 32, listOf("招牌", "辣"), hasSpec = true, hot = true,
            image = WIKI + "8/8e/Kung_Pao_Chicken_at_Yujiayan_Restaurant_%2820230510123120%29.jpg/500px-Kung_Pao_Chicken_at_Yujiayan_Restaurant_%2820230510123120%29.jpg"),
        Dish("2", "水煮鱼", "Sichuan Boiled Fish", "招牌", 8800, 8, listOf("招牌", "辣"), hasSpec = true, hot = true,
            image = WIKI + "4/44/Shuizhuyu_at_Hamarkand_Silk_Road_Cuisine%2C_Beiqinglu_%2820230121154615%29.jpg/500px-Shuizhuyu_at_Hamarkand_Silk_Road_Cuisine%2C_Beiqinglu_%2820230121154615%29.jpg"),
        Dish("3", "小炒黄牛肉", "Stir-fried Beef", "热菜", 6800, 15, hot = true,
            image = WIKI + "1/13/%E6%B9%98%E8%8F%9C%E9%A6%86%E4%B9%8B%E5%B0%8F%E7%82%92%E9%BB%84%E7%89%9B%E8%82%89.jpg/500px-%E6%B9%98%E8%8F%9C%E9%A6%86%E4%B9%8B%E5%B0%8F%E7%82%92%E9%BB%84%E7%89%9B%E8%82%89.jpg"),
        Dish("4", "蒜蓉西兰花", "Garlic Broccoli", "凉菜", 2800, 40, listOf("素")),
        Dish("5", "麻婆豆腐", "Mapo Tofu", "热菜", 2600, 26, listOf("辣"), hot = true,
            image = WIKI + "8/88/Chen_Mapo_Tofu.jpg/500px-Chen_Mapo_Tofu.jpg"),
        Dish("6", "酸菜鱼", "Pickled Cabbage Fish", "招牌", 7800, 5, hot = true,
            image = WIKI + "5/54/%E9%85%B8%E8%8F%9C%E9%B1%BC_Preserved_Mustard_Green_with_Fish_-_Charming_Spice_AUD24.80_%284104355401%29.jpg/500px-%E9%85%B8%E8%8F%9C%E9%B1%BC_Preserved_Mustard_Green_with_Fish_-_Charming_Spice_AUD24.80_%284104355401%29.jpg"),
        Dish("7", "夫妻肺片", "Spicy Beef Slices", "凉菜", 3800, 12, hot = true,
            image = WIKI + "4/45/Fuqi_Feipian.jpg/500px-Fuqi_Feipian.jpg"),
        Dish("8", "拍黄瓜", "Smashed Cucumber", "凉菜", 1600, 50, listOf("素")),
        Dish("9", "米饭", "Steamed Rice", "主食", 300, null, hot = true),
        Dish("10", "担担面", "Dan Dan Noodles", "主食", 2200, 0,
            image = WIKI + "d/d2/Dan-dan_noodles%2C_Shanghai.jpg/500px-Dan-dan_noodles%2C_Shanghai.jpg"),
        Dish("11", "番茄蛋汤", "Tomato Egg Soup", "汤羹", 1800, 20),
        Dish("12", "酸梅汤", "Sour Plum Drink", "饮品", 1200, 60, listOf("新品"), hot = true,
            image = WIKI + "6/60/Sour_plum_drink.jpg/500px-Sour_plum_drink.jpg"),
        Dish("13", "鲜榨橙汁", "Fresh Orange Juice", "饮品", 1800, 3),
        Dish("14", "红糖糍粑", "Brown Sugar Ciba", "甜品", 2200, 10, listOf("新品")),
        Dish("15", "双人套餐", "Set for Two", "套餐", 16800, 6, listOf("套餐"), hasSpec = true),
    )

    val specs = listOf(
        SpecGroup("份量", listOf(SpecOption("标准"), SpecOption("大份", 1000))),
        SpecGroup("辣度", listOf(SpecOption("微辣"), SpecOption("中辣"), SpecOption("不辣"))),
    )

    val areas = listOf(
        Area("hall", "一层大厅", "大厅"), Area("window", "窗边", "窗边"), Area("private", "包间", "包间"), Area("upstairs", "二层", "二层"),
    )

    val tables: List<DiningTable> = listOf(
        Triple("A01", "hall", 2) to TableStatus.Idle, Triple("A02", "hall", 4) to TableStatus.Dining,
        Triple("A03", "hall", 4) to TableStatus.Dining, Triple("A05", "hall", 4) to TableStatus.Cleaning,
        Triple("A06", "hall", 6) to TableStatus.Reserved, Triple("A08", "hall", 2) to TableStatus.Dining,
        Triple("B01", "window", 2) to TableStatus.Idle, Triple("B02", "window", 4) to TableStatus.Dining,
        Triple("B03", "window", 4) to TableStatus.Paid, Triple("B05", "window", 2) to TableStatus.Idle,
        Triple("V01", "private", 6) to TableStatus.Dining, Triple("V02", "private", 8) to TableStatus.Reserved,
        Triple("C01", "upstairs", 4) to TableStatus.Idle, Triple("C02", "upstairs", 4) to TableStatus.Dining,
        Triple("C03", "upstairs", 6) to TableStatus.Reserved, Triple("C05", "upstairs", 2) to TableStatus.Idle,
    ).mapIndexed { i, (t, s) ->
        DiningTable(
            id = t.first, area = t.second, seats = t.third, status = s,
            guests = if (s == TableStatus.Dining) t.third else 0,
            minutes = if (s == TableStatus.Dining) 18 + i * 7 else 0,
            reservation = if (s == TableStatus.Reserved) "19:00" else "",
            paid = if (s == TableStatus.Paid) 18800 else 0,
        )
    }

    /** 初始订单：A03 与原型一致，其余用餐桌给少量已下单菜。返回（订单表，下一个行 ID）。 */
    fun orders(): Pair<Map<String, TableOrder>, Long> {
        var uid = 1L
        val map = tables.associate { it.id to mutableListOf<OrderLine>() }
        fun seed(t: String, dishId: String, qty: Int, st: LineStatus, spec: String = "标准做法") {
            val d = dishes.first { it.id == dishId }
            map.getValue(t).add(OrderLine(uid++, d.id, d.name, spec, d.price, qty, st))
        }
        seed("A03", "1", 1, LineStatus.New, "标准 · 微辣"); seed("A03", "9", 4, LineStatus.New)
        seed("A03", "6", 1, LineStatus.Sent); seed("A03", "7", 1, LineStatus.Served); seed("A03", "3", 1, LineStatus.Rushing)
        seed("A02", "5", 1, LineStatus.Sent); seed("A02", "9", 1, LineStatus.Served)
        seed("B02", "6", 1, LineStatus.Served)
        seed("V01", "2", 1, LineStatus.Sent); seed("V01", "4", 1, LineStatus.Served)
        seed("C02", "3", 1, LineStatus.Sent); seed("C02", "12", 2, LineStatus.Sent)
        return map.mapValues { TableOrder(it.value) } to uid
    }
}
