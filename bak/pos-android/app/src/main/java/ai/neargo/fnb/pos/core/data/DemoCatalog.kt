package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.common.PayMethods
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
import ai.neargo.fnb.pos.core.model.*
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

    fun stores(now: Long): Map<String, StoreData> = listOf(main(now), coast(), mart(now), mart2(now)).associateBy { it.id }

    /** 第二家零售演示门店（门店间调拨用）：同一套商品目录，库存减半，没有历史单 */
    const val RETAIL_STORE2_ID = "s-mart2"
    private fun mart2(now: Long): StoreData = mart(now).let { m ->
        m.copy(id = RETAIL_STORE2_ID, name = "虹选便利 · 海岸城店", code = "HX002", address = "深圳市南山区海岸城 B1",
            settings = m.settings.copy(shortName = "虹选便利 · 海岸城店"),
            products = m.products.map { p -> p.copy(stock = p.stock?.let { it / 2 }, skus = p.skus.map { k -> k.copy(stock = k.stock / 2) }) },
            members = emptyList(), retail = RetailData(saleSeq = 1, docSeq = 1), shift = ShiftState(start = now))
    }

    /** 零售演示门店的 ID（旧数据升级时补上这一家） */
    const val RETAIL_STORE_ID = "s-mart"

    /** 零售演示门店：业态 = 零售，没有桌台。目录、历史单、挂单、库存单据、价签队列与原型 prototypes/pos-react/src/retail 同源。 */
    private fun mart(now: Long): StoreData {
        val cats = listOf(Category("r-fresh", "散称", "By weight"), Category("r-bakery", "烘焙", "Bakery"), Category("r-drinks", "饮品", "Drinks"),
            Category("r-snacks", "零食", "Snacks"), Category("r-apparel", "服饰", "Apparel"), Category("r-misc", "日用", "Daily"))
        val color = SpecGroup("r-color", "颜色", "Colour", listOf(SpecOption("white", "白"), SpecOption("black", "黑"), SpecOption("oat", "燕麦")))
        val size = SpecGroup("r-size", "尺码", "Size", listOf(SpecOption("s", "S"), SpecOption("m", "M"), SpecOption("l", "L"), SpecOption("xl", "XL")))
        val sockColor = SpecGroup("r-sock", "颜色", "Colour", listOf(SpecOption("grey", "灰"), SpecOption("white", "白"), SpecOption("black", "黑")))
        val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        fun d(days: Long) = today.plusDays(days).toString()
        val safety = mapOf("milk" to 6, "toast" to 4, "baguette" to 3, "croissant" to 10, "cola" to 12, "water" to 24)
        val expiry = mapOf("milk" to d(2), "croissant" to d(1), "yogurt" to d(1), "toast" to d(3), "eggtart" to d(0))
        // id, 名称, English, 售价（分）, 条码, 分类, 常用, 称重, 会员价, 促销, 库存（件；称重为克）
        data class R(val id: String, val n: String, val en: String, val p: Long, val code: String, val cat: String, val hot: Boolean = false, val weigh: Boolean = false, val mp: Long? = null, val promo: LinePromo? = null, val stock: Int = 36)
        val rows = listOf(
            R("bag", "购物袋（中）", "Shopping bag (M)", 30, "2000001", "r-misc", hot = true),
            R("water", "农夫山泉 550ml", "Spring water 550ml", 200, "6921168509256", "r-drinks", hot = true, promo = LinePromo.Half2),
            R("cola", "可口可乐 330ml", "Coca-Cola 330ml", 350, "6954767423579", "r-drinks", hot = true),
            R("milk", "纯牛奶 250ml × 12", "Whole milk 250ml × 12", 3990, "6907992500133", "r-drinks", hot = true, mp = 3690, stock = 4),
            R("chips", "薯片 原味 70g", "Potato chips 70g", 790, "6924743915763", "r-snacks", hot = true),
            R("apple", "富士苹果", "Fuji apple", 1280, "PLU 1001", "r-fresh", hot = true, weigh = true, stock = 18_500),
            R("banana", "香蕉", "Banana", 698, "PLU 1002", "r-fresh", weigh = true, stock = 18_500),
            R("cherry", "车厘子 JJ", "Cherries JJ", 9800, "PLU 1010", "r-fresh", weigh = true, stock = 18_500),
            R("grape", "阳光玫瑰葡萄", "Shine Muscat grapes", 2980, "PLU 1015", "r-fresh", weigh = true, stock = 18_500),
            R("tomato", "番茄", "Tomato", 760, "PLU 2003", "r-fresh", weigh = true, stock = 18_500),
            R("croissant", "牛角包", "Croissant", 600, "2100012", "r-bakery", hot = true),
            R("toast", "全麦吐司", "Whole-wheat toast", 1480, "2100020", "r-bakery", stock = 2),
            R("eggtart", "葡式蛋挞", "Egg tart", 550, "2100031", "r-bakery"),
            R("baguette", "法棍", "Baguette", 1200, "2100045", "r-bakery", stock = 0),
            R("coffee", "冷萃咖啡 300ml", "Cold brew 300ml", 1500, "6971284390011", "r-drinks"),
            R("tea", "无糖乌龙茶 500ml", "Oolong tea 500ml", 500, "6901939621271", "r-drinks", promo = LinePromo.Half2),
            R("yogurt", "原味酸奶 200g", "Plain yogurt 200g", 650, "6907992512853", "r-drinks"),
            R("nuts", "每日坚果 25g", "Daily nuts 25g", 490, "6970399920415", "r-snacks"),
            R("choc", "黑巧克力 100g", "Dark chocolate 100g", 1890, "6923450605882", "r-snacks"),
            R("gum", "口香糖", "Chewing gum", 990, "6923450656181", "r-snacks"),
        )
        val simple = rows.map { r ->
            Product(r.id, r.n, r.en, r.cat, r.p, stock = r.stock, hot = r.hot,
                retail = RetailInfo(listOf(r.code), if (r.weigh) "kg" else "件", r.weigh, r.p * 6 / 10 /* 进价示例：售价 6 折 */, r.mp, r.promo, expiry[r.id], safety[r.id]))
        }
        // 服饰：颜色 × 尺码，每格自己的库存和条码
        val teeStock = listOf(listOf(6, 2, 8, 0), listOf(3, 9, 1, 4), listOf(0, 5, 7, 2))
        val tee = Product("tee", "基础圆领 T 恤", "Basic crew tee", "r-apparel", 5900, specGroupIds = listOf("r-color", "r-size"),
            skus = ManageRules.skus(listOf(color, size), 5900, 0, emptyList()).getOrThrow().map { k ->
                val ci = color.options.indexOfFirst { it.id in k.optionIds }; val zi = size.options.indexOfFirst { it.id in k.optionIds }
                k.copy(stock = teeStock[ci][zi], manualStock = true, barcode = "23001%02d%d".format(ci * 4 + zi, ci))
            }, retail = RetailInfo(listOf("2300101"), costPrice = 3540))
        val socks = Product("socks", "棉袜 3 双装", "Cotton socks ×3", "r-apparel", 2900, specGroupIds = listOf("r-sock"),
            skus = ManageRules.skus(listOf(sockColor), 2900, 10, emptyList()).getOrThrow(), retail = RetailInfo(listOf("2300210"), costPrice = 1740))
        val products = simple + tee + socks
        val byId = products.associateBy { it.id }
        fun line(i: Int, id: String, q: Int, unit: Long = byId.getValue(id).price, grams: Int? = null, sku: String? = null, amount: Long? = null, returned: Int = 0) =
            RetailSaleLine("L$i", id, byId.getValue(id).name, sku, q, grams, unit, amount ?: if (grams != null) ai.neargo.fnb.pos.core.model.Money.roundHalfUp(unit * grams, 1000) else unit * q, returned)
        val h = 3_600_000L; val m = 60_000L
        val sales = listOf(
            RetailSale("#R0157", now - 9 * m, member = "王女士", lines = listOf(line(1, "cola", 2), line(2, "milk", 1, unit = 3690), line(3, "apple", 1, grams = 1236), line(4, "croissant", 3)), discount = 72, pays = listOf(SalePay(PayMethods.QR, 7700))),
            RetailSale("#R0155", now - 25 * m, lines = listOf(line(1, "tee", 1, sku = "白 · M"), line(2, "socks", 1, sku = "灰")), discount = 880, pays = listOf(SalePay(PayMethods.CASH, 7920))),
            RetailSale("#R0152", now - 50 * m, member = "王女士", lines = listOf(line(1, "water", 4, amount = 600), line(2, "chips", 2, returned = 1), line(3, "coffee", 1)), pays = listOf(SalePay(PayMethods.STORED, 3680)),
                returns = listOf(SaleReturn(now - 32 * m, 790, 1, ReturnRules.Reason.Unwanted, PayMethods.STORED))),
            RetailSale("#R0150", now - 78 * m, channel = SaleChannel.Mini, lines = listOf(line(1, "toast", 2), line(2, "eggtart", 4)), discount = 160, pays = listOf(SalePay(PayMethods.QR, 5000))),
            RetailSale("#R0149", now - 95 * m, lines = listOf(line(1, "cherry", 1, grams = 512, returned = 1), line(2, "grape", 1, grams = 862, returned = 1)), discount = 7, pays = listOf(SalePay(PayMethods.CARD, 7580)),
                returns = listOf(SaleReturn(now - 70 * m, 7580, 2, ReturnRules.Reason.Quality, PayMethods.CARD))),
            RetailSale("#R0146", now - 2 * h, channel = SaleChannel.Platform, lines = listOf(line(1, "nuts", 3)), pays = listOf(SalePay(PayMethods.QR, 1470))),
        )
        val held = listOf(
            RetailHeld("H0156", now - 18 * m, listOf(CartLine(1, "cola", 2), CartLine(2, "chips", 1), CartLine(3, "bag", 1)), why = "顾客回去拿钱包"),
            RetailHeld("H0153", now - 43 * m, listOf(CartLine(1, "tee", 1, sku = "黑 · L"), CartLine(2, "socks", 1, sku = "白")), member = "王女士", why = "等顾客试穿"),
        )
        val docs = listOf(
            StockDoc("RK0003", StockDocKind.Receive, StockDocState.Draft, now - 20 * m, "李娜", listOf(StockDocLine("milk", qty = 12), StockDocLine("yogurt", qty = 24)), supplier = "城北乳业"),
            StockDoc("RK0002", StockDocKind.Receive, StockDocState.Posted, now - 2 * h, "李娜", listOf(StockDocLine("cola", qty = 48), StockDocLine("water", qty = 72), StockDocLine("tea", qty = 24)), supplier = "可口可乐华南"),
            StockDoc("PD0002", StockDocKind.Count, StockDocState.Counting, now - h, "李娜", listOf(StockDocLine("cola", qty = 30), StockDocLine("water", qty = 36), StockDocLine("coffee", qty = 34)), scope = "饮品"),
            StockDoc("PD0001", StockDocKind.Count, StockDocState.Pending, now - 13 * h, "张伟", listOf(StockDocLine("croissant", qty = 33), StockDocLine("toast", qty = 2), StockDocLine("eggtart", qty = 36)), scope = "烘焙",
                snapshot = mapOf("croissant" to 36L, "toast" to 2L, "eggtart" to 36L), loss = 3),
            StockDoc("BS0002", StockDocKind.Loss, StockDocState.Draft, now - 40 * m, "李娜", listOf(StockDocLine("eggtart", qty = 4, reason = LossReason.Expired))),
            StockDoc("BS0001", StockDocKind.Loss, StockDocState.Posted, now - 14 * h, "张伟", listOf(StockDocLine("baguette", qty = 2, reason = LossReason.Damaged))),
        )
        return StoreData(
            id = RETAIL_STORE_ID, name = "虹选便利 · 科技园店", code = "HX001", address = "深圳市南山区科技园中区 6 号",
            settings = StoreSettings("虹选便利 · 科技园店", serviceBp = 0, payMethods = listOf(PayMethods.CASH, PayMethods.QR, PayMethods.CARD, PayMethods.STORED)),
            biz = BizType.Retail,
            areas = emptyList(), tables = emptyList(), categories = cats, specGroups = listOf(color, size, sockColor), products = products, menus = emptyList(),
            currentTableId = "", shift = ShiftState(start = now - 2 * h),
            members = listOf(Member("rm-wang", "13800001234", "王女士", balance = 52_000, points = 860, totalSpent = 186_000, visits = 23, createdAt = now - 90 * 24 * h),
                Member("rm-li", "13900005678", "李先生", balance = 0, points = 120, totalSpent = 24_000, visits = 4, createdAt = now - 20 * 24 * h)),
            retail = RetailData(sales, held, docs, listOf(LabelJob("milk", LabelWhy.Price, 4290), LabelJob("croissant", LabelWhy.Price, 650), LabelJob("tee", LabelWhy.New)), saleSeq = 158, docSeq = 4),
        )
    }

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
