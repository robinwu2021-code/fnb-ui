package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.Area
import ai.neargo.fnb.pos.core.model.Category
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.ManageRules
import ai.neargo.fnb.pos.core.model.Menu
import ai.neargo.fnb.pos.core.model.OrderLine
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.SpecGroup
import ai.neargo.fnb.pos.core.model.SpecOption
import ai.neargo.fnb.pos.core.model.BizType
import ai.neargo.fnb.pos.core.model.StoreData
import ai.neargo.fnb.pos.core.model.StoreSettings
import ai.neargo.fnb.pos.core.model.TableOrder
import ai.neargo.fnb.pos.core.model.TableStatus
import ai.neargo.fnb.pos.core.model.ShiftState

private const val WIKI = "https://thumb.wikimedia.org/wikipedia/commons/thumb/"

/** 演示门店初始数据（总店与原型同源；海岸店用于切店隔离）。只在 data 层使用。 */
internal object DemoCatalog {
    const val STAFF = "林晓 · 1号收银台"

    private val categories = listOf(
        Category("c-sig", "招牌", "Signature"), Category("c-cold", "凉菜", "Cold"), Category("c-hot", "热菜", "Hot"), Category("c-staple", "主食", "Staple"),
        Category("c-soup", "汤羹", "Soup"), Category("c-drink", "饮品", "Drinks"), Category("c-set", "套餐", "Sets"), Category("c-dessert", "甜品", "Dessert"),
    )

    private val specs = listOf(
        SpecGroup("g1", "份量", "Size", listOf(SpecOption("g1-1", "标准"), SpecOption("g1-2", "大份", 1000))),
        SpecGroup("g2", "辣度", "Spice", listOf(SpecOption("g2-1", "微辣"), SpecOption("g2-2", "中辣"), SpecOption("g2-3", "不辣"))),
    )

    private val demoAliases = mapOf(
        "1" to "gongbaojiding gbjd", "2" to "shuizhuyu szy", "3" to "xiaochaohuangniurou xchnr",
        "4" to "suanrongxilanhua srxlh", "5" to "mapodoufu mpdf", "6" to "suancaiyu scy",
        "7" to "fuqifeipian fqfp", "8" to "paihuanggua phg", "9" to "mifan mf", "10" to "dandanmian ddm",
        "11" to "fanqiedantang fqdt", "12" to "suanmeitang smt", "13" to "xianzhachengzhi xzcz",
        "14" to "hongtangciba htcb", "15" to "shuangrentaocan srtc",
    )

    private fun p(id: String, name: String, en: String, cat: String, price: Long, stock: Int?, tags: List<String> = emptyList(), spec: Boolean = false, hot: Boolean = false, image: String? = null): Product {
        val base = Product(id, name, en, cat, price, if (spec) null else stock, if (spec) specs.map { it.id } else emptyList(), emptyList(), image, tags, hot, searchAliases = demoAliases[id].orEmpty())
        return if (!spec) base else base.copy(skus = ManageRules.skus(specs, price, stock ?: 0, emptyList()).getOrThrow())
    }

    private val products = listOf(
        p("1", "宫保鸡丁", "Kung Pao Chicken", "c-hot", 4800, 32, listOf("招牌", "辣"), spec = true, hot = true,
            image = WIKI + "8/8e/Kung_Pao_Chicken_at_Yujiayan_Restaurant_%2820230510123120%29.jpg/500px-Kung_Pao_Chicken_at_Yujiayan_Restaurant_%2820230510123120%29.jpg"),
        p("2", "水煮鱼", "Sichuan Boiled Fish", "c-sig", 8800, 8, listOf("招牌", "辣"), spec = true, hot = true,
            image = WIKI + "4/44/Shuizhuyu_at_Hamarkand_Silk_Road_Cuisine%2C_Beiqinglu_%2820230121154615%29.jpg/500px-Shuizhuyu_at_Hamarkand_Silk_Road_Cuisine%2C_Beiqinglu_%2820230121154615%29.jpg"),
        p("3", "小炒黄牛肉", "Stir-fried Beef", "c-hot", 6800, 15, hot = true,
            image = WIKI + "1/13/%E6%B9%98%E8%8F%9C%E9%A6%86%E4%B9%8B%E5%B0%8F%E7%82%92%E9%BB%84%E7%89%9B%E8%82%89.jpg/500px-%E6%B9%98%E8%8F%9C%E9%A6%86%E4%B9%8B%E5%B0%8F%E7%82%92%E9%BB%84%E7%89%9B%E8%82%89.jpg"),
        p("4", "蒜蓉西兰花", "Garlic Broccoli", "c-cold", 2800, 40, listOf("素")),
        p("5", "麻婆豆腐", "Mapo Tofu", "c-hot", 2600, 26, listOf("辣"), hot = true, image = WIKI + "8/88/Chen_Mapo_Tofu.jpg/500px-Chen_Mapo_Tofu.jpg"),
        p("6", "酸菜鱼", "Pickled Cabbage Fish", "c-sig", 7800, 5, hot = true,
            image = WIKI + "5/54/%E9%85%B8%E8%8F%9C%E9%B1%BC_Preserved_Mustard_Green_with_Fish_-_Charming_Spice_AUD24.80_%284104355401%29.jpg/500px-%E9%85%B8%E8%8F%9C%E9%B1%BC_Preserved_Mustard_Green_with_Fish_-_Charming_Spice_AUD24.80_%284104355401%29.jpg"),
        p("7", "夫妻肺片", "Spicy Beef Slices", "c-cold", 3800, 12, hot = true, image = WIKI + "4/45/Fuqi_Feipian.jpg/500px-Fuqi_Feipian.jpg"),
        p("8", "拍黄瓜", "Smashed Cucumber", "c-cold", 1600, 50, listOf("素")),
        p("9", "米饭", "Steamed Rice", "c-staple", 300, null, hot = true),
        p("10", "担担面", "Dan Dan Noodles", "c-staple", 2200, 0, image = WIKI + "d/d2/Dan-dan_noodles%2C_Shanghai.jpg/500px-Dan-dan_noodles%2C_Shanghai.jpg"),
        p("11", "番茄蛋汤", "Tomato Egg Soup", "c-soup", 1800, 20),
        p("12", "酸梅汤", "Sour Plum Drink", "c-drink", 1200, 60, listOf("新品"), hot = true, image = WIKI + "6/60/Sour_plum_drink.jpg/500px-Sour_plum_drink.jpg"),
        p("13", "鲜榨橙汁", "Fresh Orange Juice", "c-drink", 1800, 3),
        p("14", "红糖糍粑", "Brown Sugar Ciba", "c-dessert", 2200, 10, listOf("新品")),
        p("15", "双人套餐", "Set for Two", "c-set", 16800, 6, listOf("套餐")).copy(mealGroups = listOf(
            ai.neargo.fnb.pos.core.model.MealGroup("set-main", "主菜", listOf(ai.neargo.fnb.pos.core.model.MealChoice("set-chicken", "宫保鸡丁"), ai.neargo.fnb.pos.core.model.MealChoice("set-fish", "水煮鱼", 4000))),
            ai.neargo.fnb.pos.core.model.MealGroup("set-drink", "饮品", listOf(ai.neargo.fnb.pos.core.model.MealChoice("set-plum", "酸梅汤"), ai.neargo.fnb.pos.core.model.MealChoice("set-orange", "鲜榨橙汁", 600))),
        )),
    )

    private val menus = listOf(
        Menu("m1", "全天菜单", "All day", products.filter { it.id != "15" }.map { it.id }, published = true),
        Menu("m2", "午市套餐", "Lunch sets", listOf("15"), published = false),
    )

    fun stores(now: Long): Map<String, StoreData> = listOf(main(now), coast(), mart(now)).associateBy { it.id }

    /** 零售演示门店的 ID（旧数据升级时补上这一家） */
    const val RETAIL_STORE_ID = "s-mart"

    /** 零售演示门店：业态 = 零售，没有桌台；商品目录在 R2 补齐（原型 prototypes/pos-react/src/retail/data.ts）。 */
    private fun mart(now: Long): StoreData = StoreData(
        id = RETAIL_STORE_ID, name = "虹选便利 · 科技园店", code = "HX001", address = "深圳市南山区科技园中区 6 号",
        settings = StoreSettings("虹选便利 · 科技园店", serviceBp = 0, payMethods = listOf("现金", "扫码", "银行卡", "储值卡")),
        biz = BizType.Retail,
        areas = emptyList(), tables = emptyList(), categories = categories, specGroups = specs, products = emptyList(), menus = emptyList(),
        currentTableId = "", shift = ShiftState(start = now - 2 * 3_600_000L),
    )

    private fun main(now: Long): StoreData {
        val areas = listOf(Area("hall", "一层大厅", "Hall"), Area("window", "窗边", "Window"), Area("private", "包间", "Private"), Area("upstairs", "二层", "Upstairs"))
        val tables = listOf(
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
                id = t.first, areaId = t.second, seats = t.third, status = s,
                guests = if (s == TableStatus.Dining) t.third else 0,
                openedAt = if (s == TableStatus.Dining) now - (18 + i * 7) * 60_000L else 0,
                reservation = if (s == TableStatus.Reserved) "19:00" else "",
                paid = if (s == TableStatus.Paid) 18800 else 0,
            )
        }
        var uid = 1L
        val map = tables.associate { it.id to mutableListOf<OrderLine>() }
        fun seed(t: String, dishId: String, qty: Int, st: LineStatus, skuLabel: String? = null) {
            val d = products.first { it.id == dishId }
            val sku = skuLabel?.let { l -> d.skus.first { it.label == l } }
            map.getValue(t).add(OrderLine(uid++, d.id, d.name, sku?.label ?: "标准做法", sku?.price ?: d.price, qty, st, sku?.key, if (st == LineStatus.New) null else "seed"))
        }
        seed("A03", "1", 1, LineStatus.New, "标准 · 微辣"); seed("A03", "9", 4, LineStatus.New)
        seed("A03", "6", 1, LineStatus.Sent); seed("A03", "7", 1, LineStatus.Served); seed("A03", "3", 1, LineStatus.Rushing)
        seed("A02", "5", 1, LineStatus.Sent); seed("A02", "9", 1, LineStatus.Served)
        seed("B02", "6", 1, LineStatus.Served)
        seed("V01", "2", 1, LineStatus.Sent, "标准 · 微辣"); seed("V01", "4", 1, LineStatus.Served)
        seed("C02", "3", 1, LineStatus.Sent); seed("C02", "12", 2, LineStatus.Sent)
        return StoreData(
            id = "s-main", name = "南山小馆 · 总店", code = "NS001", address = "深圳市南山区科技园南路 18 号",
            settings = StoreSettings("南山小馆 · 总店"),
            areas = areas, tables = tables, categories = categories, specGroups = specs, products = products, menus = menus,
            orders = map.mapValues { TableOrder(it.value) }, currentTableId = "A03", seq = uid,
            shift = ShiftState(start = now - 4 * 3_600_000L),
        )
    }

    private fun coast(): StoreData {
        val areas = listOf(Area("hall", "大厅", "Hall"), Area("terrace", "露台", "Terrace"))
        val tables = listOf("H01" to "hall", "H02" to "hall", "H03" to "hall", "H04" to "hall", "T01" to "terrace", "T02" to "terrace")
            .map { (id, a) -> DiningTable(id, a, if (a == "terrace") 4 else 2, TableStatus.Idle) }
        return StoreData(
            id = "s-coast", name = "南山小馆 · 海岸店", code = "NS002", address = "深圳市南山区海岸城 3 层",
            settings = StoreSettings("南山小馆 · 海岸店"),
            areas = areas, tables = tables, categories = categories, specGroups = specs, products = products, menus = menus,
            orders = tables.associate { it.id to TableOrder() }, currentTableId = "H01",
        )
    }
}
