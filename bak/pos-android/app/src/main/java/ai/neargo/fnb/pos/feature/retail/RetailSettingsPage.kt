package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.app.appContainer
import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosSwitch
import ai.neargo.fnb.pos.core.designsystem.component.QuantityStepper
import ai.neargo.fnb.pos.core.designsystem.component.Section
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.LabelTpl
import ai.neargo.fnb.pos.core.model.RetailSettings
import ai.neargo.fnb.pos.core.model.StoreData
import ai.neargo.fnb.pos.core.model.yuan
import ai.neargo.fnb.pos.feature.manage.PageHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 系统管理 · 零售设置（原型 biz/index.ts 的零售设置菜单）：只放会改变行为的项，每项注明用在哪里。
 * 改完点「保存」才写门店数据。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.RetailSettingsPage(store: StoreData) {
    val c = Pos.colors; val t = Pos.type
    val app = appContainer()
    val scope = rememberCoroutineScope()
    val saved = store.retail.settings
    var d by remember(saved) { mutableStateOf(saved) }
    PageHeader("零售设置", "价签、临期、限购、购物袋、退换货；改完点保存") {
        PosButton("还原", { d = saved }, variant = BtnVariant.Text, enabled = d != saved)
        PosButton("保存", {
            scope.launchStorage(app.messages) { app.retail.saveRetailSettings(d).onSuccess { app.messages.post("零售设置已保存") }.onFailure { app.messages.post(it.message ?: "保存失败") } }
        }, variant = BtnVariant.Primary, enabled = d != saved)
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("条码规则") {
            KeyValue("店内码", "2 开头 7 位（如 2000001）")
            KeyValue("称重码", "18 位：2 + 商品码 6 + 金额 5 + 重量 5 + 校验 1")
            KeyValue("价签条码", "印 EAN-13；店内码、PLU 补成 13 位，收银扫得出")
            KeyValue("一码多品", "同一个码对应多个商品时，收银弹出让收银员选")
            Text("条码格式目前固定，接电子秤时按秤的标签格式开放配置", style = t.label, color = c.sub)
        }
        Section("价签") {
            Text("默认模板（「商品 · 价签」打开时用这个）", style = t.label, color = c.sub)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                LabelTpl.entries.forEach { tp -> PosChip(tp.label, d.labelTpl == tp, { d = d.copy(labelTpl = tp) }) }
            }
            SwitchRow("改价后自动进待打价签", "在「商品资料」改售价后，商品自动加进价签队列", d.autoLabel) { d = d.copy(autoLabel = it) }
        }
        Section("临期与预警") {
            StepRow("临期提前提醒", if (d.expiryDays == 0) "只提醒当天到期和已过期的" else "到期前 ${d.expiryDays} 天起算临期（库存预警、商品页状态）", d.expiryDays, "天", 0, 30) { d = d.copy(expiryDays = it) }
            Text("临期自动折扣（收银时临期商品按这个折扣卖，宫格上标「临期 × 折」）", style = t.label, color = c.sub, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf(100 to "不打折", 90 to "9 折", 80 to "8 折", 70 to "7 折", 50 to "5 折").forEach { (v, l) -> PosChip(l, d.expiryPct == v, { d = d.copy(expiryPct = v) }) }
            }
        }
        Section("限购") {
            StepRow("促销品每单限购", if (d.promoLimit == 0) "不限购" else "有促销（如第二件半价）的商品，每单最多 ${d.promoLimit} 件；收银加购和收款都会拦", d.promoLimit, "件", 0, 99) { d = d.copy(promoLimit = it) }
        }
        Section("禁售时段") {
            Text("时段内勾选分类的商品不能卖（如烟酒夜间禁售）；可以跨零点，例如 22:00–08:00", style = t.label, color = c.sub)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ai.neargo.fnb.pos.core.designsystem.component.PosTextField(d.restrictFrom, { v -> if (Regex("^[0-9:]{0,5}$").matches(v)) d = d.copy(restrictFrom = v) }, "开始（HH:mm）", Modifier.weight(1f))
                ai.neargo.fnb.pos.core.designsystem.component.PosTextField(d.restrictTo, { v -> if (Regex("^[0-9:]{0,5}$").matches(v)) d = d.copy(restrictTo = v) }, "结束（HH:mm）", Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                store.categories.forEach { cat ->
                    val on = cat.id in d.restrictCats
                    PosChip(nameOf(cat), on, { d = d.copy(restrictCats = if (on) d.restrictCats - cat.id else d.restrictCats + cat.id) })
                }
            }
        }
        Section("购物袋") {
            Text("勾选的商品在购物车上方显示快捷按钮，一点就加", style = t.label, color = c.sub)
            val candidates = store.products.filter { "袋" in it.name || it.id in d.bagIds }
            if (candidates.isEmpty()) Text("没有名字里带「袋」的商品，先在商品资料里建一个", style = t.label, color = c.warning)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                candidates.forEach { p ->
                    val on = p.id in d.bagIds
                    PosChip("${nameOf(p)} ${yuan(p.price)}", on, { d = d.copy(bagIds = if (on) d.bagIds - p.id else d.bagIds + p.id) })
                }
            }
        }
        Section("线上订单（小程序、即时零售平台）") {
            SwitchRow("自动接单", "来单直接进入拣货，不用收银员点「接单」", d.autoAccept) { d = d.copy(autoAccept = it) }
            SwitchRow("接单后打印拣货单", "按分类排好商品，方便拣货", d.printPick) { d = d.copy(printPick = it) }
        }
        Section("退换货") {
            StepRow("退货期限", if (d.returnDays == 0) "不限期限" else "售出 ${d.returnDays} 天内可以退换，超过的订单不能退", d.returnDays, "天", 0, 365) { d = d.copy(returnDays = it) }
            SwitchRow("无单退货需主管授权", "找不到原单时按现价退，只退现金或储值", d.noReceiptAuth) { d = d.copy(noReceiptAuth = it) }
            SwitchRow("有单退货原路退回", if (d.originalMethod) "只能退回原收款方式" else "可以退到门店启用的任一收款方式", d.originalMethod) { d = d.copy(originalMethod = it) }
        }
        if (d != saved) Text("有未保存的修改", style = t.label, color = c.warning)
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Pos.type.body, color = Pos.colors.ink)
            Text(hint, style = Pos.type.label, color = Pos.colors.sub)
        }
        PosSwitch(on, onChange)
    }
}

@Composable
private fun StepRow(title: String, hint: String, value: Int, unit: String, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Pos.type.body, color = Pos.colors.ink)
            Text(hint, style = Pos.type.label, color = Pos.colors.sub)
        }
        QuantityStepper(value, { delta -> onChange((value + delta).coerceIn(min, max)) }, min = min, max = max)
        Text(unit, style = Pos.type.body, color = Pos.colors.sub, modifier = Modifier.padding(start = 6.dp))
    }
}
