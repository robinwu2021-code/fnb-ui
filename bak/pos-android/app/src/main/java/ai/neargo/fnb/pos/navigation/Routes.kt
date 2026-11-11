package ai.neargo.fnb.pos.navigation

import kotlinx.serialization.Serializable

/**
 * 类型安全路由（navigation-compose + kotlinx.serialization）。
 * 结账携带 tableId；登录与选店由会话守卫处理，不进入路由栈。
 */
@Serializable data object TablesRoute
@Serializable data object OrderRoute
@Serializable data class CheckoutRoute(val tableId: String)
@Serializable data object ShiftRoute
@Serializable data object ManageRoute
@Serializable data object SyncRoute
@Serializable data object PrintRoute
@Serializable data object AuthRoute
@Serializable data object ServeRoute
@Serializable data object ReportsRoute

/* 零售业态（门店业态 = 零售时的工作台） */
@Serializable data object RetailPosRoute
@Serializable data object RetailOrdersRoute
@Serializable data object GoodsRoute
@Serializable data object StockWorkRoute

/** 页头工作台导航当前项。餐饮：桌台 · 点餐；零售：收银 · 订单 · 商品 · 库存；系统管理两业态共用。 */
enum class TopLevel(val label: String) { Tables("桌台"), Order("点餐"), RetailPos("收银"), RetailOrders("订单"), Goods("商品"), StockWork("库存"), Manage("系统管理") }

/** 各业态在页头显示的工作台页签（系统管理是齿轮图标，不在这里） */
fun topTabs(biz: ai.neargo.fnb.pos.core.model.BizType) = if (biz == ai.neargo.fnb.pos.core.model.BizType.Retail) listOf(TopLevel.RetailPos, TopLevel.RetailOrders, TopLevel.Goods, TopLevel.StockWork) else listOf(TopLevel.Tables, TopLevel.Order)
