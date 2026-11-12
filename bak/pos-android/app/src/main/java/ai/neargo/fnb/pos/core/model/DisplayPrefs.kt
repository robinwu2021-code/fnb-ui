package ai.neargo.fnb.pos.core.model

/**
 * 显示偏好（显示设置 → 外观 / 布局 / 文字）。只存语义 ID，具体色值、尺寸由设计系统映射。
 * 持久化时以枚举名保存，新增枚举不影响旧值读取，未知值回退默认。
 */
data class DisplayPrefs(
    val skin: Skin = Skin.Pine,
    val density: Density = Density.Compact,
    val scale: TextScale = TextScale.Standard,
    val panelShape: PanelShape = PanelShape.Standard,
    val buttonShape: ButtonShape = ButtonShape.Standard,
    val layout: OrderLayout = OrderLayout.L1,
    val photos: Boolean = true,
    val font: FontChoice = FontChoice.Manrope,
    val lang: Lang = Lang.Zh,
    /** 客显屏（本机副屏）：启用、显示订单明细、空闲时轮播。 */
    val customerDisplay: Boolean = true,
    val customerLines: Boolean = true,
    val customerSlides: Boolean = true,
    /** 本机电子秤（零售称重）：网口秤或串口服务器的地址；空 = 不接秤，称重弹窗手输。poll 为问答式秤的查询指令 */
    val scaleHost: String = "",
    val scalePort: Int = 4001,
    val scalePoll: String = "",
    val scaleGrams: Boolean = false,
    /** 收银页操作模式（分散 / 混合），餐饮、零售分开存 */
    val diningMode: ActionMode = ActionMode.Scatter,
    val retailMode: ActionMode = ActionMode.Scatter,
    /** 拖动调整的账单宽、竖条宽（dp）；0 = 默认 */
    val billWidth: Int = 0,
    val railWidth: Int = 0,
    /** 竖条与「更多」里的操作顺序；null = 默认 */
    val diningRail: RailConfig? = null,
    val retailRail: RailConfig? = null,
) {
    fun modeOf(biz: BizType) = if (biz == BizType.Retail) retailMode else diningMode
    fun railOf(biz: BizType) = ActionLayout.normalize(if (biz == BizType.Retail) retailRail else diningRail, biz)
    fun withMode(biz: BizType, m: ActionMode) = if (biz == BizType.Retail) copy(retailMode = m) else copy(diningMode = m)
    fun withRail(biz: BizType, c: RailConfig?) = if (biz == BizType.Retail) copy(retailRail = c) else copy(diningRail = c)
    /** 「恢复默认」只恢复布局相关的（分类 / 账单位置、宽度、竖条） */
    fun resetLayout(biz: BizType) = withRail(biz, null).copy(layout = DisplayPrefs().layout, billWidth = 0, railWidth = 0)
}

/** 字体随包提供，中文按字形回退系统 Noto Sans CJK。 */
enum class FontChoice(val label: String) { Manrope("Manrope"), Inter("Inter"), Roboto("Roboto"), NotoSans("Noto Sans"), IBMPlexSans("IBM Plex Sans"), Outfit("Outfit + Inter"), System("系统字体") }

/** 界面语言。 */
enum class Lang(val label: String) { Zh("中文"), En("English") }

/** 八套皮肤（规范 §3.2）。 */
enum class Skin(val label: String) { Pine("松绿"), Mono("墨黑"), Red("虹选红"), Blue("经典蓝"), Green("微信绿"), Teal("青碧"), Amber("琥珀"), Violet("紫") }

/** 密度：区块间距 / 内边距（规范 §5）。 */
enum class Density(val label: String) { Compact("紧凑"), Standard("标准"), Airy("舒展") }

/** 字号档：按语义角色等比缩放。 */
enum class TextScale(val label: String, val factor: Float) { Standard("标准", 1f), Large("大", 1.15f), XLarge("特大", 1.3f) }

/** 面板 / 卡片圆角档。 */
enum class PanelShape(val label: String) { Square("直角"), Sharp("小"), Standard("标准"), Round("大") }

/** 按钮圆角档。 */
enum class ButtonShape(val label: String) { Square("直角"), Small("4"), Medium("8"), Standard("12"), Pill("胶囊") }

enum class CatSide(val label: String) { Left("左"), Top("上"), Right("右"), Bottom("下") }
enum class BillSide(val label: String) { Left("左"), Right("右") }

/**
 * 点餐布局 L1–L6（v3 语义）：菜单分类位置 × 账单位置。
 * 旧工程 L5/L6 为「导航左/右 + 详情同侧」，编号相同但含义不同，不得直接迁移偏好值。
 */
enum class OrderLayout(val cat: CatSide, val bill: BillSide) {
    L1(CatSide.Left, BillSide.Right),
    L2(CatSide.Top, BillSide.Right),
    L3(CatSide.Top, BillSide.Left),
    L4(CatSide.Right, BillSide.Left),
    L5(CatSide.Bottom, BillSide.Right),
    L6(CatSide.Bottom, BillSide.Left),
}
