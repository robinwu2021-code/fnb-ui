package ai.neargo.fnb.pos.core.model

/**
 * 业态配置（端架构「业态配置」一层）：两业态的界面差异只在这里声明，页面和共用模块读它，不再各自写 `if (biz == Retail)`。
 * 业务规则的差异不在这里：餐饮走 FloorRepository / OrderRepository，零售走 RetailRepository，两套独立接口。
 * 新增业态 = 加一个 profile + 一套业务接口。
 */
data class BizProfile(
    val biz: BizType,
    /** 有桌台：桌台管理、桌台区域、餐牌、厨房档口、出菜、厨房屏、交班「结账桌台」、退赠统计 */
    val tables: Boolean,
    /** 零售专属：零售设置、标签机路由、电子秤、商品资料「条码」列与 kg 库存 */
    val retail: Boolean,
    /** 门店设置里的服务费率、整单会员折扣（零售按商品会员价，不用） */
    val serviceFee: Boolean,
    /** 系统管理「库存盘点」（零售走「库存」页签的库存作业） */
    val manageStock: Boolean,
    /** 无权限或切店时回落的首页 */
    val home: Home,
) {
    enum class Home { Tables, RetailPos }

    companion object {
        val Dining = BizProfile(BizType.Dining, tables = true, retail = false, serviceFee = true, manageStock = true, home = Home.Tables)
        val Retail = BizProfile(BizType.Retail, tables = false, retail = true, serviceFee = false, manageStock = false, home = Home.RetailPos)
        fun of(biz: BizType) = when (biz) { BizType.Dining -> Dining; BizType.Retail -> Retail }
    }
}

val StoreData.profile get() = BizProfile.of(biz)

/**
 * 运行期能力（端架构「按网络状态做能力降级」）：第一期全离线——单机 + 店内局域网，不依赖互联网。
 * 依赖互联网的能力（线上平台订单、云同步、跨设备调拨）在第一期关闭；扫码收款按现有规则联网才可用。
 */
data class RuntimeProfile(
    /** 小程序 / 即时零售平台订单（需要平台接口） */
    val onlineOrders: Boolean,
    /** 跨设备的门店间调拨（需要云端同步）；同一设备上的门店之间不受影响 */
    val cloudSync: Boolean,
) {
    companion object {
        /** 第一期：全离线 */
        val Phase1Offline = RuntimeProfile(onlineOrders = false, cloudSync = false)
        /** 演示 / 开发：打开全部能力（「模拟来单」等） */
        val Full = RuntimeProfile(onlineOrders = true, cloudSync = true)
    }
}
