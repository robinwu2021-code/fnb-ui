package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.BizType
import ai.neargo.fnb.pos.core.model.RetailData
import ai.neargo.fnb.pos.core.model.ShiftState
import ai.neargo.fnb.pos.core.model.StoreData
import ai.neargo.fnb.pos.core.model.TableStatus

/**
 * 新门店模板：按业态从演示门店复制「资料」——分类、商品、规格、餐牌、区域桌台、优惠活动、门店与零售设置；
 * 「业务数据」一律不带：订单、收款、同步队列、打印任务、会员、员工（演示 PIN 不能给真实门店）、交班、日结、厨房任务、零售单据、库存流水。
 * 打印机和档口路由是设备相关的，新门店重新配。
 */
internal object StoreTemplates {
    fun fresh(demo: Map<String, StoreData>, biz: BizType, id: String, name: String, code: String, address: String, owner: String): StoreData {
        val t = demo.values.firstOrNull { it.biz == biz && it.products.isNotEmpty() } ?: error("没有 ${biz.label} 模板")
        return StoreData(
            id = id, name = name, code = code, address = address, owner = owner, biz = biz,
            settings = t.settings.copy(shortName = name),
            retail = RetailData(settings = t.retail.settings),
            areas = t.areas,
            tables = t.tables.map { it.copy(status = TableStatus.Idle, guests = 0, openedAt = 0, reservation = "", paid = 0, sessionId = "") },
            categories = t.categories, specGroups = t.specGroups, products = t.products, menus = t.menus,
            currentTableId = t.tables.firstOrNull()?.id.orEmpty(),
            shift = ShiftState(float = t.shift.float),
            walletBalance = 0,
            promotions = t.promotions,
            kdsStations = t.kdsStations, stationOf = t.stationOf, stationDispatch = t.stationDispatch, stationDelivery = t.stationDelivery,
        )
    }
}
