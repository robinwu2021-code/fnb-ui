package ai.neargo.fnb.pos.feature.manage

import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.EmptyState
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosDivider
import ai.neargo.fnb.pos.core.designsystem.component.PosSelect
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.RailItem
import ai.neargo.fnb.pos.core.designsystem.component.Section as Block
import ai.neargo.fnb.pos.core.designsystem.component.TableStateLabel
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.tone
import ai.neargo.fnb.pos.core.data.areaName
import ai.neargo.fnb.pos.core.model.DiningTable
import ai.neargo.fnb.pos.core.model.StoreData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import ai.neargo.fnb.pos.core.designsystem.component.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ManageRoute(onDisplay: () -> Unit, onSwitchStore: () -> Unit) {
    val vm = posViewModel { c, h -> ManageViewModel(c.config, c.session, c.device, c.media, c.messages, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    ManageScreen(state, vm, onDisplay, onSwitchStore)
}

/**
 * 系统管理工作台：多级菜单（宽屏左侧可展开分组，窄屏顶部横排）+ 内容面板。
 * 所有配置保存在当前门店的本机数据中，离线可用。
 */
@Composable
fun ManageScreen(state: ManageUiState, vm: ManageViewModel, onDisplay: () -> Unit, onSwitchStore: () -> Unit) {
    val store = state.store ?: return
    val layout = LocalPosLayout.current
    val content: @Composable (Modifier) -> Unit = { m ->
        Column(m.padding(Pos.metrics.pad)) {
            when (state.section) {
                Section.Tables -> TablesSection(store, vm)
                Section.Areas -> AreasSection(store, vm)
                Section.Store -> StoreSection(store, vm)
                Section.Categories -> CategoriesSection(store, vm)
                Section.Products -> ProductsSection(store, vm)
                Section.Specs -> SpecsSection(store, vm)
                Section.Stock -> StockSection(store, vm)
                Section.Menus -> MenusSection(store, vm)
                Section.Devices -> DevicesSection(state, vm)
                Section.Payments -> PaymentsSection(state)
                Section.Staff -> StaffSection()
                Section.Gallery -> GallerySection()
                Section.Members -> SimplePage("会员管理", "会员档案、储值充值与流水；绑定会员在点餐或结账页操作") { Column(Modifier.verticalScroll(rememberScrollState())) { ai.neargo.fnb.pos.feature.ops.MembersPanel() } }
                Section.Promotions -> SimplePage("优惠活动", "满减与整单折扣；订单选用时保存快照，修改不影响已选的订单") { Column(Modifier.verticalScroll(rememberScrollState())) { ai.neargo.fnb.pos.feature.ops.PromotionsPanel() } }
                Section.Stations -> SimplePage("厨房档口", "按菜品分类把送厨拆到不同档口的厨房屏") { ai.neargo.fnb.pos.feature.kds.StationsPanel() }
                Section.Lan -> SimplePage("主副 POS", "局域网配对、主机仲裁与设备管理；副机失联时只读") { ai.neargo.fnb.pos.feature.lan.LanPanel() }
                Section.Auth -> SimplePage("授权中心", "设备身份与离线授权：到期、撤销、时钟异常时暂停新营业写入，账本保留") { ai.neargo.fnb.pos.feature.authcenter.AuthCenterPanel() }
                Section.Data -> SimplePage("数据与恢复", "备份、恢复点与迁移记录；恢复会替换本机数据，恢复前自动保留副本") { ai.neargo.fnb.pos.feature.recovery.DataRecoveryPanel() }
                Section.Display -> SimplePage("界面显示", "皮肤、密度、布局与字号为本机偏好，与门店业务配置分开保存") { PosButton("打开显示设置", onDisplay, variant = BtnVariant.Primary) }
                Section.SwitchStore -> SimplePage("切换门店", "当前：${store.settings.shortName}。切店后桌台、商品、餐牌与账单均为该门店数据") { PosButton("选择门店", onSwitchStore, variant = BtnVariant.Primary) }
                Section.Account -> SimplePage("账号登录", "当前账号：${state.user ?: "未登录"}。账号仅保存在本机（加盐哈希），联网服务接入后迁移") {
                    PosButton("退出登录", { vm.logout() }, variant = BtnVariant.Danger)
                }
            }
        }
    }
    state.draft?.let { ProductEditor(it, store, vm) }

    Panel(Modifier.fillMaxSize().padding(Pos.metrics.gap)) {
        if (layout.wide) {
            Row(Modifier.fillMaxSize()) {
                ManageNav(state.section, vm::select, Modifier.width(196.dp).fillMaxHeight())
                VerticalDivider(color = Pos.colors.divider)
                content(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Pos.colors.tile).horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Section.entries.forEach { s -> PosChip(s.label, s == state.section, { vm.select(s) }, height = 36.dp) }
                }
                content(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** 多级菜单：一级分组可展开 / 收起，选中页所在分组保持展开。 */
@Composable
private fun ManageNav(selected: Section, onSelect: (Section) -> Unit, modifier: Modifier) {
    val c = Pos.colors
    val groups = Section.entries.groupBy { it.group }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    LazyColumn(modifier.background(c.tile).padding(horizontal = 6.dp, vertical = 8.dp)) {
        groups.forEach { (g, items) ->
            val open = g !in collapsed || selected.group == g
            item(key = "g-$g") {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(role = Role.Button, onClickLabel = if (open) "收起$g" else "展开$g") {
                        collapsed = if (g in collapsed) collapsed - g else collapsed + g
                    }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(g, style = Pos.type.section, color = c.ink, modifier = Modifier.weight(1f))
                    Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.sub)
                }
            }
            if (open) items.forEach { s -> item(key = s.name) { RailItem(s.label, null, s == selected, { onSelect(s) }) } }
        }
    }
}

// —— 通用页面骨架 ——

/** 管理页标题行：标题 + 说明 + 右侧主操作。 */
@Composable
fun PageHeader(title: String, subtitle: String, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Pos.type.title, color = Pos.colors.ink)
            Text(subtitle, style = Pos.type.label, color = Pos.colors.sub)
        }
        action?.invoke(this)
    }
}

/** 紧凑表格：表头 + 行（极淡分隔），列宽按权重。 */
data class Col(val title: String, val weight: Float = 1f, val end: Boolean = false)

@Composable
fun DataTable(cols: List<Col>, modifier: Modifier = Modifier, empty: String = "暂无数据", rows: LazyListScope.() -> Unit) {
    val c = Pos.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth().background(c.tile).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            cols.forEach { col -> Text(col.title, style = Pos.type.label, color = c.sub, modifier = Modifier.weight(col.weight), textAlign = if (col.end) androidx.compose.ui.text.style.TextAlign.End else null) }
        }
        LazyColumn(Modifier.fillMaxWidth()) { rows() }
    }
}

@Composable
fun TableRow(cells: List<@Composable () -> Unit>, cols: List<Col>, onClick: (() -> Unit)? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            cells.forEachIndexed { i, cell ->
                Row(Modifier.weight(cols[i].weight), horizontalArrangement = if (cols[i].end) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) { cell() }
            }
        }
        PosDivider()
    }
}

@Composable
fun Cell(text: String, sub: Boolean = false) = Text(
    text, style = Pos.type.body, color = if (sub) Pos.colors.sub else Pos.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
)

@Composable
private fun SimplePage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    PageHeader(title, subtitle)
    Column(content = content)
}

/** 简单表单弹窗：错误显示在表单内，保存成功才关闭。 */
@Composable
fun FormDialog(title: String, onDismiss: () -> Unit, onSave: ((String?) -> Unit) -> Unit, maxWidth: Dp = 480.dp, content: @Composable ColumnScope.(error: String?) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    PosDialog(title, onDismiss, maxWidth = maxWidth, footer = {
        PosButton("取消", onDismiss, size = BtnSize.Large)
        PosButton("保存", { busy = true; onSave { e -> busy = false; error = e; if (e == null) onDismiss() } }, variant = BtnVariant.Primary, size = BtnSize.Large, loading = busy)
    }) {
        content(error)
        if (error != null) Text(error!!, style = Pos.type.label, color = Pos.colors.danger, modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.heightIn(min = 8.dp))
    }
}

// —— 门店运营 ——

@Composable
private fun ColumnScope.TablesSection(store: StoreData, vm: ManageViewModel) {
    var editing by remember { mutableStateOf<DiningTable?>(null) }
    var creating by remember { mutableStateOf(false) }
    val cols = listOf(Col("桌号"), Col("区域", 1.4f), Col("座位", 0.6f), Col("状态"), Col("", 0.6f, end = true))
    PageHeader("桌台管理", "桌号门店内唯一，仅字母数字短横线；座位 1–30。新桌台同步到桌台工作台") {
        PosButton("新建桌台", { creating = true }, variant = BtnVariant.Primary)
    }
    DataTable(cols, Modifier.weight(1f)) {
        items(store.tables.size) { i ->
            val t = store.tables[i]
            TableRow(listOf({ Cell(t.id) }, { Cell(store.areaName(t.areaId)) }, { Cell("${t.seats}") }, { TableStateLabel(t.status.label, t.status.tone()) },
                { PosButton("编辑", { editing = t }, variant = BtnVariant.Text, size = BtnSize.Small) }), cols)
        }
    }
    if (creating || editing != null) {
        val t = editing
        var id by remember(t?.id) { mutableStateOf(t?.id ?: "") }
        var area by remember(t?.id) { mutableStateOf(t?.areaId ?: store.areas.firstOrNull()?.id) }
        var seats by remember(t?.id) { mutableStateOf(t?.seats?.toString() ?: "4") }
        FormDialog(if (t == null) "新建桌台" else "编辑桌台 ${t.id}", { creating = false; editing = null }, { done ->
            vm.save(if (t == null) "已新建桌台 ${id.uppercase()}" else "已保存 ${t.id}", { saveTable(id, area.orEmpty(), seats, t == null) }, done)
        }) {
            if (t == null) PosTextField(id, { id = it }, "桌号", hint = "例如 A10、VIP-1")
            PosSelect("所属区域", store.areas, store.areas.firstOrNull { it.id == area }, { it.name }, { area = it.id })
            PosTextField(seats, { seats = it }, "座位数", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }
}

@Composable
private fun ColumnScope.AreasSection(store: StoreData, vm: ManageViewModel) {
    var creating by remember { mutableStateOf(false) }
    val cols = listOf(Col("区域名称", 1.4f), Col("英文"), Col("桌台数", 0.6f, end = true))
    PageHeader("桌台区域", "区域名称必填且门店内不重复；桌台工作台的区域栏与此同步") { PosButton("新建区域", { creating = true }, variant = BtnVariant.Primary) }
    DataTable(cols, Modifier.weight(1f)) {
        items(store.areas.size) { i ->
            val a = store.areas[i]
            TableRow(listOf({ Cell(a.name) }, { Cell(a.en.ifBlank { "—" }, sub = true) }, { Cell("${store.tables.count { it.areaId == a.id }}") }), cols)
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        var en by remember { mutableStateOf("") }
        FormDialog("新建区域", { creating = false }, { done -> vm.save("已新建区域 $name", { addArea(name, en) }, done) }) {
            PosTextField(name, { name = it }, "区域名称")
            PosTextField(en, { en = it }, "英文名称（可选）")
        }
    }
}

@Composable
private fun ColumnScope.StoreSection(store: StoreData, vm: ManageViewModel) {
    var short by remember(store.id) { mutableStateOf(store.settings.shortName) }
    var footer by remember(store.id) { mutableStateOf(store.settings.receiptFooter) }
    var hours by remember(store.id) { mutableStateOf(store.settings.hours) }
    var service by remember(store.id) { mutableStateOf("${store.settings.serviceBp / 100.0}".removeSuffix(".0")) }
    var memberRate by remember(store.id) { mutableStateOf("${(10_000 - store.settings.memberBp) / 1000.0}".removeSuffix(".0")) }
    var points by remember(store.id) { mutableStateOf(store.settings.pointsPerYuan.toString()) }
    var methods by remember(store.id) { mutableStateOf(store.settings.payMethods.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    PageHeader("门店与营业", "门店简称同步页头；费率修改只影响之后新开的订单")
    Column(Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        Block("门店信息") {
            Cell("门店码 ${store.code}", sub = true)
            Cell(store.address, sub = true)
        }
        Spacer(Modifier.heightIn(min = 8.dp))
        PosTextField(short, { short = it }, "门店简称", error = error)
        PosTextField(hours, { hours = it }, "营业时段（示例说明）")
        PosTextField(footer, { footer = it }, "小票页脚", singleLine = false)
        Block("计价与会员") {
            PosTextField(service, { service = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, "服务费率（%，0–20）")
            PosTextField(memberRate, { memberRate = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) }, "会员折扣（折，例如 9.5）")
            PosTextField(points, { points = it.filter(Char::isDigit).take(3) }, "每消费 1 元积分（0 不积分）")
        }
        Block("收款方式") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("现金", "扫码", "银行卡", "储值卡").forEach { m ->
                    PosButton(m, { methods = if (m in methods) methods - m else methods + m }, size = BtnSize.Small, selected = m in methods)
                }
            }
        }
        PosButton("保存", {
            val bp = service.toDoubleOrNull()?.let { Math.round(it * 100).toInt() } ?: -1
            val mb = memberRate.toDoubleOrNull()?.let { 10_000 - Math.round(it * 1000).toInt() } ?: -1
            vm.saveSettings(store.settings.copy(shortName = short, receiptFooter = footer, hours = hours, serviceBp = bp, memberBp = mb,
                pointsPerYuan = points.toIntOrNull() ?: -1, payMethods = listOf("现金", "扫码", "银行卡", "储值卡").filter { it in methods })) { error = it }
        }, Modifier.padding(top = 8.dp), variant = BtnVariant.Primary, size = BtnSize.Large)
    }
}

// —— 设备与系统（信息页，不冒充真实设备） ——

@Composable
private fun ColumnScope.DevicesSection(state: ManageUiState, vm: ManageViewModel) {
    val d = state.device
    PageHeader("终端与打印", "网口 ESC/POS 打印机由主 POS 统一发送；未配置的档口为模拟打印（受显示设置的缺纸开关影响）")
    Column(Modifier.verticalScroll(rememberScrollState())) { ai.neargo.fnb.pos.feature.print.PrintersPanel() }
    PosDivider(Modifier.padding(vertical = 8.dp))
    val cols = listOf(Col("设备"), Col("用途", 1.4f), Col("状态", end = true))
    DataTable(cols, Modifier.weight(1f)) {
        item { TableRow(listOf({ Cell("POS 3.0 · 1号收银台") }, { Cell("本终端", sub = true) }, { Cell(if (d.online) "在线" else "离线") }), cols) }
        item { TableRow(listOf({ Cell("前台打印机") }, { Cell("结账单、预结单", sub = true) }, { Cell("正常") }), cols) }
        item { TableRow(listOf({ Cell("厨房打印机") }, { Cell("送厨单、催菜单", sub = true) }, { Cell(if (d.printerOk) "正常" else "缺纸") }), cols, onClick = { vm.notice(if (d.printerOk) "厨房打印机正常（演示）" else "厨房打印机缺纸，换纸后补打") }) }
    }
}

@Composable
private fun ColumnScope.PaymentsSection(state: ManageUiState) {
    PageHeader("收款与费用", "当前计费为本地演示规则，不修改真实费率或支付凭证")
    val cols = listOf(Col("渠道"), Col("离线可用"), Col("说明", 2f))
    DataTable(cols, Modifier.weight(1f)) {
        listOf(Triple("现金", "是", "本地记账，联网后同步"), Triple("银行卡", "是", "外接刷卡终端，本地记账"), Triple("扫码", "否", "需联网"), Triple("储值卡", "是", "本机会员演示余额，本地扣减"), Triple("服务费", "—", "菜品小计 × 5%（演示）"))
            .forEach { (a, b, c) -> item { TableRow(listOf({ Cell(a) }, { Cell(b) }, { Cell(c, sub = true) }), cols) } }
    }
    Text(if (state.device.online) "当前在线" else "当前离线：扫码与储值卡在结账页禁用", style = Pos.type.label, color = Pos.colors.sub)
}

@Composable
private fun ColumnScope.StaffSection() {
    PageHeader("员工与权限", "员工 PIN 上岗；按角色在数据层校验每项营业操作")
    ai.neargo.fnb.pos.feature.staff.StaffPanel()
}

@Composable
internal fun Empty(text: String) = EmptyState(text)
