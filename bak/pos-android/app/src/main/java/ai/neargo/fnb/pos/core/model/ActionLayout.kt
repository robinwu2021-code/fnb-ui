package ai.neargo.fnb.pos.core.model

/**
 * 收银页操作布局（方案：docs/android/收银页操作布局与拖动方案-2026-11-12.md）。
 * 两种操作模式：分散（按钮在各自对象旁，现状）/ 混合（参考实机：选中行的操作集中在菜单和账单之间的竖条）。
 * 这里只有纯规则：操作登记表、竖条 / 「更多」的默认与校验、分栏宽度、拖动落点到 L1–L6 的换算。
 */
enum class ActionMode(val label: String) { Scatter("分散"), Hybrid("混合") }

/** 操作作用对象：决定在哪种模式下放哪 */
enum class ActionScope(val label: String) {
    /** 选中的这一行 */
    Line("选中行"),
    /** 新增一行（临时菜 / 临时商品）：和行操作放一起 */
    AddLine("新增一行"),
    /** 整单（会员、挂单、清空……）：账单头 */
    Order("整单"),
    /** 桌台级 / 低频：默认在「更多」 */
    Table("桌台"),
    /** 设备 */
    Device("设备"),
}

data class ActionSpec(val id: String, val label: String, val scope: ActionScope) {
    /** 能放进竖条的：只有作用于行的（竖条只放一种对象，按钮含义不混） */
    val railable get() = scope == ActionScope.Line || scope == ActionScope.AddLine
}

/** 竖条与「更多」里的操作（按 ID 存，顺序即显示顺序） */
data class RailConfig(val rail: List<String>, val more: List<String>)

object ActionLayout {
    const val RAIL_MAX = 12
    const val BILL_MIN = 320; const val BILL_MAX = 480
    const val RAIL_W_MIN = 64; const val RAIL_W_MAX = 96; const val RAIL_W_DEFAULT = 80

    // —— 登记表 ——
    val dining = listOf(
        ActionSpec("line.delete", "删除", ActionScope.Line),
        ActionSpec("line.note", "备注", ActionScope.Line),
        ActionSpec("line.gift", "赠菜", ActionScope.Line),
        ActionSpec("line.void", "退菜", ActionScope.Line),
        ActionSpec("line.rush", "催菜", ActionScope.Line),
        ActionSpec("line.serve", "上菜", ActionScope.Line),
        ActionSpec("table.adjust", "整单折扣", ActionScope.Table),
        ActionSpec("table.change", "换桌", ActionScope.Table),
        ActionSpec("table.merge", "并台", ActionScope.Table),
        ActionSpec("table.split", "拆单", ActionScope.Table),
        ActionSpec("table.prebill", "打印预结单", ActionScope.Table),
        ActionSpec("table.batch", "批量上菜 / 催菜", ActionScope.Table),
    )
    val retail = listOf(
        ActionSpec("line.delete", "删除", ActionScope.Line),
        ActionSpec("line.reprice", "改价", ActionScope.Line),
        ActionSpec("line.note", "备注", ActionScope.Line),
        ActionSpec("line.gift", "赠送", ActionScope.Line),
        ActionSpec("add.custom", "临时商品", ActionScope.AddLine),
        ActionSpec("order.clear", "清空购物车", ActionScope.Order),
        ActionSpec("device.drawer", "开钱箱", ActionScope.Device),
    )
    fun catalog(biz: BizType) = if (biz == BizType.Retail) retail else dining
    fun spec(biz: BizType, id: String) = catalog(biz).firstOrNull { it.id == id }

    /** 默认：能进竖条的都在竖条，其余在「更多」 */
    fun defaults(biz: BizType): RailConfig = catalog(biz).let { all -> RailConfig(all.filter { it.railable }.map { it.id }, all.filterNot { it.railable }.map { it.id }) }

    /**
     * 读出来的配置整理成合法的：去掉不认识的和重复的、不能进竖条的挪到「更多」、竖条超出上限的挪到「更多」最前，
     * 新版本新增的操作按默认位置补在末尾。null = 用默认。
     */
    fun normalize(saved: RailConfig?, biz: BizType): RailConfig {
        if (saved == null) return defaults(biz)
        val known = catalog(biz).associateBy { it.id }
        val seen = mutableSetOf<String>()
        val rail0 = saved.rail.filter { it in known && seen.add(it) }
        val more0 = saved.more.filter { it in known && seen.add(it) }
        val railOk = rail0.filter { known.getValue(it).railable }
        val rail = railOk.take(RAIL_MAX)
        val more = railOk.drop(RAIL_MAX) + rail0.filterNot { known.getValue(it).railable } + more0
        val missing = catalog(biz).filter { it.id !in seen }
        return RailConfig(rail + missing.filter { it.railable && rail.size < RAIL_MAX }.map { it.id }, more + missing.filterNot { it.railable && rail.size < RAIL_MAX }.map { it.id })
            .let { c -> if (c.rail.size > RAIL_MAX) RailConfig(c.rail.take(RAIL_MAX), c.rail.drop(RAIL_MAX) + c.more) else c }
    }

    /**
     * 编辑器里的一整条列表（竖条在上、「更多」在下，中间一条分隔），拖动后按新顺序换算回配置。
     * divider = 分隔条在列表里的下标（它之前的是竖条）。不合规返回原因。
     */
    fun fromList(order: List<String>, divider: Int, biz: BizType): Result<RailConfig> {
        val rail = order.take(divider); val more = order.drop(divider)
        val bad = rail.firstOrNull { spec(biz, it)?.railable == false }
        return when {
            bad != null -> Result.failure(IllegalArgumentException("「${spec(biz, bad)!!.label}」作用于整单 / 桌台 / 设备，不能放进竖条"))
            rail.size > RAIL_MAX -> Result.failure(IllegalArgumentException("竖条最多 $RAIL_MAX 个"))
            else -> Result.success(RailConfig(rail, more))
        }
    }

    /** 拖动一项：从 from 挪到 to（编辑器列表里含分隔条，分隔条用 DIVIDER 表示） */
    fun drag(list: List<String>, from: Int, to: Int): List<String> {
        if (from == to || from !in list.indices) return list
        val m = list.toMutableList(); val x = m.removeAt(from); m.add(to.coerceIn(0, m.size), x); return m
    }
    const val DIVIDER = "—more—"

    // —— 分栏宽度（dp），0 = 默认 ——
    fun billWidth(saved: Int, default: Int) = if (saved <= 0) default else saved.coerceIn(BILL_MIN, BILL_MAX)
    fun railWidth(saved: Int) = if (saved <= 0) RAIL_W_DEFAULT else saved.coerceIn(RAIL_W_MIN, RAIL_W_MAX)

    // —— 区块拖动：落点 → L1–L6 ——
    /** 账单拖到哪半边就在哪边 */
    fun billDrop(x: Float, width: Float): BillSide = if (x < width / 2) BillSide.Left else BillSide.Right

    /** 分类栏拖到离哪条边最近就贴哪边 */
    fun catDrop(x: Float, y: Float, w: Float, h: Float): CatSide {
        val d = mapOf(CatSide.Left to x / w, CatSide.Right to (w - x) / w, CatSide.Top to y / h, CatSide.Bottom to (h - y) / h)
        return d.minBy { it.value }.key
    }

    /**
     * 分类位置 + 账单位置 → 布局。L1–L6 没有「分类和账单同一侧」的组合：分类自动换到另一侧。
     */
    fun layoutOf(cat: CatSide, bill: BillSide): OrderLayout {
        val c = when {
            cat == CatSide.Left && bill == BillSide.Left -> CatSide.Right
            cat == CatSide.Right && bill == BillSide.Right -> CatSide.Left
            else -> cat
        }
        return OrderLayout.entries.first { it.cat == c && it.bill == bill }
    }

    // —— 偏好里存成一行文本 ——
    fun encode(c: RailConfig?): String = c?.let { it.rail.joinToString(",") + "|" + it.more.joinToString(",") }.orEmpty()
    fun decode(s: String?): RailConfig? = s?.takeIf { it.contains('|') }?.split('|', limit = 2)?.let { (a, b) ->
        RailConfig(a.split(',').filter { it.isNotBlank() }, b.split(',').filter { it.isNotBlank() })
    }
}
