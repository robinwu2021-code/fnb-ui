package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.core.designsystem.component.EmptyState
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 零售工作台（门店业态 = 零售时显示）。R0 先放占位，R3 起逐页替换成正式页面。 */
enum class RetailPage(val title: String, val next: String) {
    Pos("收银", "R3：扫码加购、称重、选规格、查价、挂取单、收款"),
    Orders("订单", "R4：订单列表、退货、换货"),
    Goods("商品", "R6：商品资料与改价、库存、价签"),
}

@Composable
fun RetailPlaceholder(page: RetailPage) {
    Panel(Modifier.fillMaxSize().padding(12.dp)) {
        Column(Modifier.padding(24.dp)) {
            Text(page.title, style = Pos.type.header)
            EmptyState("零售${page.title}页面开发中 · ${page.next}")
        }
    }
}
