package ai.neargo.fnb.pos.core.model

/** 资料批量整理（建店后删演示商品、批量调价）：纯规则，门店数据进、门店数据出 */
object CatalogBulk {
    /** 在用的商品：未结账的餐饮订单、零售挂单 / 购物车抵扣、未完成的线上单 */
    fun inUse(s: StoreData): Set<String> =
        s.orders.values.flatMap { o -> o.lines.map { it.dishId } }.toSet() +
            s.held.flatMap { h -> h.lines.map { it.dishId } } +
            s.retail.held.flatMap { h -> h.lines.map { it.productId } } +
            s.retail.online.filter { it.state != OnlineState.Done && it.state != OnlineState.Rejected }.flatMap { o -> o.lines.map { it.productId } }

    fun delete(s: StoreData, ids: Set<String>): StoreData = s.copy(
        products = s.products.filterNot { it.id in ids },
        menus = s.menus.map { m -> m.copy(productIds = m.productIds - ids) },
        retail = s.retail.copy(labelQueue = s.retail.labelQueue.filterNot { it.productId in ids }, settings = s.retail.settings.copy(bagIds = s.retail.settings.bagIds - ids)),
    )

    /** 调价：price × (100 + percent) / 100，按角四舍五入，最低 1 角；规格价没手动改过的跟着基础价 */
    fun newPrice(price: Long, percent: Int): Long = maxOf(10L, Math.round(price * (100 + percent) / 100.0 / 10.0) * 10)

    fun reprice(s: StoreData, ids: Set<String>, percent: Int): StoreData {
        val old = s.products.filter { it.id in ids }.associate { it.id to it.price }
        val products = s.products.map { p ->
            if (p.id !in ids) p else {
                val np = newPrice(p.price, percent)
                p.copy(price = np, retail = p.retail?.let { r -> r.copy(memberPrice = r.memberPrice?.let { newPrice(it, percent) }) },
                    skus = p.skus.map { k -> if (k.manualPrice) k.copy(price = newPrice(k.price, percent)) else k.copy(price = np + (k.price - p.price)) })
            }
        }
        val labels = if (s.biz == BizType.Retail && s.retail.settings.autoLabel)
            ids.map { LabelJob(it, LabelWhy.Price, s.retail.labelQueue.firstOrNull { j -> j.productId == it }?.oldPrice ?: old[it]) } + s.retail.labelQueue.filterNot { it.productId in ids }
        else s.retail.labelQueue
        return s.copy(products = products, retail = s.retail.copy(labelQueue = labels))
    }
}
