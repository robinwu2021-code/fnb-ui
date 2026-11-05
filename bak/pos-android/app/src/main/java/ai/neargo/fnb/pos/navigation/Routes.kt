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

/** 页头工作台导航当前项。 */
enum class TopLevel(val label: String) { Tables("桌台"), Order("点餐"), Manage("系统管理") }
