package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.StoreData
import ai.neargo.fnb.pos.core.model.TableOrder

/* 门店数据的小工具：餐饮服务、局域网、同步等共用一份 */
internal fun StoreData.order(t: String) = orders[t] ?: TableOrder()
internal fun StoreData.table(t: String) = tables.firstOrNull { it.id == t }
internal fun StoreData.withOrder(t: String, o: TableOrder) = copy(orders = orders + (t to o))
internal fun StoreData.withTable(t: String, f: (DiningTable) -> DiningTable) = copy(tables = tables.map { if (it.id == t) f(it) else it })
