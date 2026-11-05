package ai.neargo.fnb.pos.navigation

import kotlinx.serialization.Serializable

/**
 * 类型安全路由（navigation-compose + kotlinx.serialization）。
 * 结账携带 tableId，返回同一张桌的订单；登录 / 选店 / 系统管理按 v3.2–v3.3 后续补齐。
 */
@Serializable data object TablesRoute
@Serializable data object OrderRoute
@Serializable data class CheckoutRoute(val tableId: String)
@Serializable data object ShiftRoute

/** 页头工作台导航当前项。 */
enum class TopLevel(val label: String) { Tables("桌台"), Order("点餐") }
