package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.app.appContainer
import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.CashPad
import ai.neargo.fnb.pos.core.designsystem.component.EmptyState
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.LocalPosLayout
import ai.neargo.fnb.pos.core.designsystem.component.Panel
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosChip
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosIconButton
import ai.neargo.fnb.pos.core.designsystem.component.PosSheet
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.QuantityStepper
import ai.neargo.fnb.pos.core.designsystem.component.Section
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.component.Workbench
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.BillSide
import ai.neargo.fnb.pos.core.model.CartLine
import ai.neargo.fnb.pos.core.model.CashRules
import ai.neargo.fnb.pos.core.model.Money
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.RetailCheckout
import ai.neargo.fnb.pos.core.model.RetailPricing
import ai.neargo.fnb.pos.core.model.ScanBuffer
import ai.neargo.fnb.pos.core.model.yuan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RetailPosRoute() {
    val vm = posViewModel { c, _ -> RetailPosViewModel(c.config, c.retail, c.device, c.messages, c.staff, c.ops) }
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by appContainer().prefs.prefs.collectAsStateWithLifecycle(null)
    CustomerSync(state)
    RetailPosScreen(state, vm::onAction, prefs?.layout?.bill ?: BillSide.Left)
}

/**
 * 客显同步：购物车在收银页内存里，有变化就推一份快照（明细、刚扫、应付、收款与找零）；
 * 收款完成推「收款成功」；购物车清空时不覆盖还在停留中的致谢页（下一位按得快也能看到找零）。
 */
@Composable
private fun CustomerSync(state: RetailPosUiState) {
    val stage = appContainer().customerStage
    val cart = state.cart
    LaunchedEffect(cart, state.products, state.credit) {
        val done = cart.overlay as? RetailOverlay.Done
        stage.value = when {
            done != null -> {
                val method = done.sale.pays.firstOrNull { it.method != ai.neargo.fnb.pos.core.model.EXCHANGE }?.method
                if (done.refund > 0) ai.neargo.fnb.pos.core.model.CustomerStage.Paid("零售", done.refund, 0, "退差价 · ${done.credit?.method.orEmpty()}", System.currentTimeMillis())
                else ai.neargo.fnb.pos.core.model.CustomerStage.Paid("零售", done.received, done.change, method ?: "换货抵扣", System.currentTimeMillis())
            }
            cart.lines.isEmpty() && state.credit == null ->
                stage.value.takeIf { it is ai.neargo.fnb.pos.core.model.CustomerStage.Paid } ?: ai.neargo.fnb.pos.core.model.CustomerStage.Browse
            else -> ai.neargo.fnb.pos.core.model.CustomerStage.Retail(ai.neargo.fnb.pos.core.model.CustomerDisplayRules.retailCart(
                state.products, cart.lines, cart.member, cart.last, state.credit?.amount ?: 0,
                method = cart.method.takeIf { cart.mode == PosMode.Pay }, received = CashRules.amount(cart.cash).takeIf { cart.method == "现金" && cart.mode == PosMode.Pay },
                promotions = state.promotions, extras = state.extras,
            ))
        }
    }
}

/**
 * 零售收银（原型 retail/RetailPos.tsx · W2）：左购物车 · 右扫码态 / 收款态；称重、规格矩阵、取单、清空、临时商品、收款完成是浮层。
 * 扫码枪按键盘输入：扫码框有焦点时回车提交；没有焦点时由页面根节点按按键间隔识别（ScanBuffer），不弹软键盘。
 */
@Composable
fun RetailPosScreen(state: RetailPosUiState, onAction: (RetailPosAction) -> Unit, side: BillSide = BillSide.Left) {
    val layout = LocalPosLayout.current
    val root = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    val overlay = state.cart.overlay
    LaunchedEffect(overlay == null, state.cart.mode) { if (overlay == null && !fieldFocused) runCatching { root.requestFocus() } }

    Box(
        Modifier.fillMaxSize().hidScanner(root, { fieldFocused }) { onAction(RetailPosAction.Scan(it)) },
    ) {
        Workbench(side, if (layout.wide) maxOf(layout.dockW, 400.dp) else layout.dockW, aside = { m -> CartPanel(state, onAction, m) }) { m ->
            if (state.cart.mode == PosMode.Pay) PayPane(state, onAction, m) else ScanPane(state, onAction, m) { fieldFocused = it }
        }
    }

    when (overlay) {
        is RetailOverlay.Weigh -> state.product(overlay.productId)?.let { WeighDialog(it, overlay.grams, onAction) }
        is RetailOverlay.Sku -> state.product(overlay.productId)?.let { SkuDialog(state, it, onAction) }
        RetailOverlay.Held -> HeldSheet(state, onAction)
        RetailOverlay.Clear -> ClearDialog(state, onAction)
        RetailOverlay.Custom -> CustomDialog(state.cart.unknown, onAction)
        is RetailOverlay.Reprice -> state.cart.lines.firstOrNull { it.key == overlay.key }?.let { RepriceDialog(state, it, onAction) }
        is RetailOverlay.Note -> state.cart.lines.firstOrNull { it.key == overlay.key }?.let { NoteDialog(state, it, onAction) }
        RetailOverlay.Discount -> DiscountDialog(state, onAction)
        RetailOverlay.Promos -> PromoDialog(state, onAction)
        RetailOverlay.Member -> MemberDialog(state, onAction)
        is RetailOverlay.PickProduct -> PosDialog("这个码对应多个商品", { onAction(RetailPosAction.Dismiss) }, subtitle = "条码 ${overlay.code}，请选顾客拿的那一个", footer = {
            PosButton("取消", { onAction(RetailPosAction.Dismiss) }, variant = BtnVariant.Text)
        }) {
            overlay.ids.mapNotNull { state.product(it) }.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Pos.metrics.button).background(Pos.colors.tile).clickable { onAction(RetailPosAction.PickProduct(p.id)) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(nameOf(p), style = Pos.type.strong, color = Pos.colors.ink, modifier = Modifier.weight(1f))
                    Text(priceText(p), style = Pos.type.strong.num(), color = Pos.colors.ink)
                }
            }
        }
        is RetailOverlay.Done -> DoneDialog(state, overlay, onAction)
        null -> Unit
    }
}

/* ---------------- 左：购物车 ---------------- */

@Composable
private fun CartPanel(state: RetailPosUiState, onAction: (RetailPosAction) -> Unit, modifier: Modifier) {
    val c = Pos.colors; val t = Pos.type
    val cart = state.cart; val tot = state.totals
    val pay = cart.mode == PosMode.Pay; val empty = cart.lines.isEmpty()
    var menu by remember { mutableStateOf(false) }
    Panel(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("购物车", style = t.title, color = c.ink)
                    Text("单号 ${state.nextNo}", style = t.label.num(), color = c.sub)
                    if (cart.member) Tag(state.memberRec?.let { "${it.name} · 积分 ${it.points}" } ?: "会员", bg = c.tint, fg = c.primary)
                    Spacer(Modifier.weight(1f))
                    if (!pay) Box {
                        PosIconButton(Icons.Rounded.MoreHoriz, "更多", { menu = true })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("开钱箱") }, { menu = false; onAction(RetailPosAction.OpenDrawer) })
                            DropdownMenuItem({ Text("清空购物车", color = if (empty) c.sub else c.danger) }, { menu = false; onAction(RetailPosAction.AskClear) }, enabled = !empty)
                        }
                    }
                }
                if (!pay) Row(Modifier.padding(vertical = 6.dp).padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PosChip(if (cart.member) "解除会员" else "会员", cart.member, { onAction(RetailPosAction.ToggleMember) })
                    PosButton("挂单", { onAction(RetailPosAction.Hold) }, size = BtnSize.Small, enabled = !empty)
                    PosButton(if (state.held.isEmpty()) "取单" else "取单 · ${state.held.size}", { onAction(RetailPosAction.ShowHeld) }, size = BtnSize.Small, enabled = state.held.isNotEmpty())
                    state.bags.forEach { b -> PosButton("+ ${nameOf(b).replace("购物袋", "袋")} ${yuan(b.price)}", { onAction(RetailPosAction.AddBag(b.id)) }, size = BtnSize.Small) }
                }
            }
            state.credit?.let { cr ->
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(Pos.metrics.card).background(c.tint).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("换货抵扣 · ${cr.no}", style = t.strong.num(), color = c.ink)
                        Text("退回 ${cr.pieces} 件 · 差价多退少补，退差价按原方式（${cr.method}）", style = t.label, color = c.sub)
                    }
                    Text("−${yuan(cr.amount)}", style = t.strong.num(), color = c.primary)
                }
            }
            if (empty) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.QrCodeScanner, null, tint = c.sub, modifier = Modifier.size(44.dp))
                    Text(if (state.credit != null) "扫要换的新商品" else "扫商品条码开始收银", style = t.strong, color = c.ink, modifier = Modifier.padding(top = 8.dp))
                    Text(if (state.credit != null) "抵扣 ${yuan(state.credit.amount)}；不换别的就直接退差价" else "也可以点右侧商品，或先扫会员码", style = t.label, color = c.sub)
                    if (!cart.member) PosButton("识别会员", { onAction(RetailPosAction.ToggleMember) }, variant = BtnVariant.Text, size = BtnSize.Small)
                }
            } else {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    items(cart.lines, key = { it.key }) { l -> CartLineRow(state, l, onAction) }
                    item {
                        Column(Modifier.padding(8.dp).clip(Pos.metrics.card).background(c.tile).padding(10.dp)) {
                            Text("整单优惠", style = t.label, color = c.sub)
                            if (tot.promo > 0) KeyValue("促销", "−${yuan(tot.promo)}", valueColor = c.primary)
                            if (cart.member) { if (tot.memberSave > 0) KeyValue("会员价", "−${yuan(tot.memberSave)}", valueColor = c.primary) }
                            else KeyValue("会员价", "未识别会员", valueColor = c.sub)
                            // 点行打开选择：优惠活动（门店配置）、整单折扣（主管授权）
                            val names = state.promotions.filter { it.id in cart.promoIds }.joinToString("、") { it.name + if (!state.promoValid(it.id)) "（未满门槛）" else "" }
                            KeyValue("优惠活动", if (tot.coupon > 0) "−${yuan(tot.coupon)}" else names.ifBlank { if (state.promotions.isEmpty()) "无可用活动" else "选择 ›" },
                                Modifier.clickable(enabled = !pay && state.promotions.isNotEmpty()) { onAction(RetailPosAction.AskPromos) }, valueColor = if (tot.coupon > 0) c.primary else c.sub)
                            if (names.isNotBlank() && tot.coupon > 0) Text(names, style = t.label, color = c.sub)
                            KeyValue("整单折扣", if (cart.discountPct < 100) "${pctText(cart.discountPct)} −${yuan(tot.manual)}" else "选择 ›",
                                Modifier.clickable(enabled = !pay) { onAction(RetailPosAction.AskDiscount) }, valueColor = if (tot.manual > 0) c.primary else c.sub)
                            if (tot.round > 0) KeyValue("抹零（到角）", "−${yuan(tot.round)}", valueColor = c.primary)
                        }
                    }
                }
            }
            // 底部：应收 + 收款入口
            Column(Modifier.fillMaxWidth().background(c.control).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val st = state.settle
                    Column(Modifier.weight(1f)) {
                        Text(when { st == null -> if (empty) "" else "应收"; st.refund > 0 -> "退差价"; st.even -> "刚好抵扣"; else -> "补收" }, style = t.label, color = c.sub)
                        Text(if (st != null) "新商品 ${yuan(tot.due)} − 抵扣 ${yuan(state.credit!!.amount)}" else "共 ${tot.pcs} 件${if (tot.save > 0) " · 已省 ${yuan(tot.save)}" else ""}", style = t.label.num(), color = c.sub)
                    }
                    Text(yuan(if (st != null && st.refund > 0) st.refund else state.payDue), style = t.amount.num(), color = c.ink)
                }
                if (pay) PosButton("继续加购", { onAction(RetailPosAction.BackToScan) }, Modifier.fillMaxWidth())
                else if (state.settleOnly) {
                    val st = state.settle!!
                    PosButton(if (st.refund > 0) "退差价 ${yuan(st.refund)}（${state.credit!!.method}）" else "确认换货", { onAction(RetailPosAction.Confirm) }, Modifier.fillMaxWidth(),
                        variant = BtnVariant.Primary, size = BtnSize.Large, loading = cart.processing)
                } else {
                    if (!empty) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if ("现金" in state.payMethods) PosButton("现金刚好 ${yuan(state.payDue)}", { onAction(RetailPosAction.QuickCash) }, Modifier.weight(1f), size = BtnSize.Small, loading = cart.processing)
                        if ("扫码" in state.payMethods) PosButton("扫码收款", { onAction(RetailPosAction.GoPay("扫码")) }, Modifier.weight(1f), size = BtnSize.Small, enabled = state.online)
                    }
                    PosButton(if (empty) "收款" else "${if (state.credit != null) "补收" else "收款"} ${yuan(state.payDue)}", { onAction(RetailPosAction.GoPay(null)) }, Modifier.fillMaxWidth(), variant = BtnVariant.Primary, size = BtnSize.Large, enabled = !empty)
                }
            }
        }
    }
}

@Composable
private fun CartLineRow(state: RetailPosUiState, l: CartLine, onAction: (RetailPosAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val pay = state.cart.mode == PosMode.Pay
    val info = state.lineInfo(l)
    val promo = RetailPricing.linePromo(l, info)
    val mem = if (state.cart.member) RetailPricing.lineMemberSave(l, info) else 0
    val open = !pay && state.cart.open == l.key
    val hl = !pay && state.cart.last == l.key
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(Pos.metrics.card).background(if (hl) c.tint else c.surface)
        .clickable(enabled = !pay) { onAction(RetailPosAction.ToggleOpen(l.key)) }.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(lineTitle(state, l), style = t.line, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = buildList {
                    if (l.grams != null) add("${kg(l.grams)} kg × ${yuan(info.unit)}/kg") else add("${yuan(info.unit)}/件")
                    l.sku?.let { add(it) }
                    if (l.custom) add("临时商品")
                    if (l.gift) add("赠送")
                    if (l.price != null) add("改价")
                    l.note?.let { add("备注：$it") }
                    if (promo > 0) add("${ai.neargo.fnb.pos.core.model.LinePromo.Half2.label} −${yuan(promo)}")
                    if (mem > 0) add("会员价 ${yuan(info.memberPrice ?: 0)} −${yuan(mem)}")
                }
                Text(meta.joinToString(" · "), style = t.label.num(), color = c.sub, maxLines = 2)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(yuan(state.lineAmount(l)), style = t.strong.num(), color = c.ink)
                if (l.grams == null) {
                    if (pay) Text("×${l.qty}", style = t.label.num(), color = c.sub)
                    else QuantityStepper(l.qty, { d -> onAction(RetailPosAction.Bump(l.key, d)) }, Modifier.padding(top = 4.dp))
                }
            }
        }
        if (open) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PosButton(if (l.gift) "取消赠送" else "赠送", { onAction(RetailPosAction.Gift(l.key)) }, size = BtnSize.Small)
            if (!l.custom && !l.gift) PosButton(if (l.price != null) "改价 · 已改" else "改价", { onAction(RetailPosAction.AskReprice(l.key)) }, size = BtnSize.Small)
            PosButton("备注", { onAction(RetailPosAction.AskNote(l.key)) }, size = BtnSize.Small)
            PosButton("删除", { onAction(RetailPosAction.Remove(l.key)) }, size = BtnSize.Small, variant = BtnVariant.Danger)
        }
    }
}

/* ---------------- 右 · 扫码态 ---------------- */

@Composable
private fun ScanPane(state: RetailPosUiState, onAction: (RetailPosAction) -> Unit, modifier: Modifier, onFocus: (Boolean) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val cart = state.cart
    var text by remember { mutableStateOf("") }
    Panel(modifier) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 常驻扫码框
            Row(Modifier.fillMaxWidth().height(52.dp).clip(Pos.metrics.button).background(c.tile).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (cart.peek) Icons.Rounded.Search else Icons.Rounded.QrCodeScanner, null, tint = c.sub, modifier = Modifier.size(22.dp))
                if (cart.qty > 1 && !cart.peek) Tag("× ${cart.qty}", bg = c.tint, fg = c.primary)
                if (cart.peek) Tag("查价", bg = c.tint, fg = c.primary)
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text(if (cart.peek) "扫码或输入条码查价" else "扫码或输入条码 / PLU，回车加入", style = t.body, color = c.sub, maxLines = 1)
                    BasicTextField(text, { text = it.filterNot { ch -> ch == '\n' } }, singleLine = true, textStyle = t.body.copy(color = c.ink, fontSize = 17.sp), cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAction(RetailPosAction.Scan(text)); text = "" }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) }.onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { if (text.isNotBlank()) onAction(RetailPosAction.Scan(text)); text = ""; true } else false
                        }.semantics { contentDescription = "扫码框" })
                }
                Tag("扫码枪", bg = c.surface, fg = c.success)
            }
            ScanStrip(state, onAction)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PosChip("常用", cart.cat == HOT, { onAction(RetailPosAction.SelectCat(HOT)) })
                    state.categories.forEach { cat -> PosChip(nameOf(cat), cart.cat == cat.id, { onAction(RetailPosAction.SelectCat(cat.id)) }) }
                }
                Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(2, 3, 5).forEach { n -> PosChip("× $n", cart.qty == n, { onAction(RetailPosAction.SetQty(n)) }) }
                    PosChip("查价", cart.peek, { onAction(RetailPosAction.TogglePeek) })
                    PosButton("临时商品", { onAction(RetailPosAction.AddCustom) }, size = BtnSize.Small)
                }
            }
            LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.shown, key = { it.id }) { p -> ItemTile(state, p, onAction) }
            }
        }
    }
}

/** 扫码框下一行：未知条码 / 查价结果 / 刚扫的一件（撤销 · 再来一件）/ 提示 */
@Composable
private fun ScanStrip(state: RetailPosUiState, onAction: (RetailPosAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val cart = state.cart
    val strip = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Pos.metrics.button)
    when {
        cart.unknown != null -> Row(strip.background(c.dangerBg).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("未找到条码 ${cart.unknown}", style = t.body.num(), color = c.danger, modifier = Modifier.weight(1f))
            PosButton("按临时商品加入", { onAction(RetailPosAction.AddCustom) }, size = BtnSize.Small)
        }
        cart.peek -> {
            val p = cart.peekId?.let(state::product)
            if (p == null) Row(strip.background(c.tile).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("查价模式：扫码或点商品只看价格和库存，不加入购物车", style = t.label, color = c.sub)
            } else Row(strip.background(c.tile).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(nameOf(p), style = t.strong, color = c.ink, maxLines = 1)
                    val r = p.retail
                    Text(listOfNotNull(r?.barcodes?.firstOrNull(), r?.promo?.label, r?.memberPrice?.let { "会员价 ${yuan(it)}" }).joinToString(" · "), style = t.label.num(), color = c.sub)
                }
                Text(priceText(p), style = t.amountM.num(), color = c.ink)
                Text("库存 ${stockText(p)}", style = t.label.num(), color = c.sub)
                PosButton("加入", { onAction(RetailPosAction.PeekAdd(p.id)) }, size = BtnSize.Small, enabled = !p.soldOut)
            }
        }
        state.last != null -> {
            val l = state.last!!
            Row(strip.background(c.tile).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("刚扫", style = t.label, color = c.sub)
                Text(lineTitle(state, l), style = t.strong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(if (l.grams != null) "${kg(l.grams)} kg" else "× ${l.qty}", style = t.body.num(), color = c.ink)
                Text(yuan(state.lineAmount(l)), style = t.strong.num(), color = c.ink)
                Spacer(Modifier.weight(1f))
                Text("共 ${state.totals.pcs} 件", style = t.label.num(), color = c.sub)
                PosButton("撤销", { onAction(RetailPosAction.Undo(l.key)) }, size = BtnSize.Small, variant = BtnVariant.Text)
                if (l.grams == null && !l.custom) PosButton("再来一件", { onAction(RetailPosAction.Bump(l.key, 1)) }, size = BtnSize.Small)
            }
        }
        else -> Row(strip.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("无码商品点下方宫格；先点「× 2 / 3 / 5」再扫，一次加多件", style = t.label, color = c.sub)
        }
    }
}

@Composable
private fun ItemTile(state: RetailPosUiState, p: Product, onAction: (RetailPosAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val weigh = p.retail?.weighable == true
    val inCart = state.cart.lines.filter { it.productId == p.id && !it.custom }.sumOf { if (it.grams != null) 1 else it.qty }
    val out = p.soldOut
    val left = p.totalStock
    Box(Modifier.fillMaxWidth().height(96.dp).clip(Pos.metrics.card).background(if (inCart > 0) c.tint else c.tile)
        .clickable(enabled = !out) { onAction(RetailPosAction.Tap(p.id)) }.semantics { contentDescription = p.name }.padding(10.dp)) {
        Column(Modifier.fillMaxSize()) {
            Text(nameOf(p), style = t.strong, color = if (out) c.sub else c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (weigh) Icon(Icons.Rounded.Scale, null, tint = c.sub, modifier = Modifier.size(12.dp))
                Text(when { weigh -> "PLU"; p.hasSpec -> "多规格"; else -> p.retail?.barcodes?.firstOrNull()?.takeLast(6).orEmpty() }, style = t.label.num(), color = c.sub)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(priceText(p), style = t.strong.num(), color = c.ink, modifier = Modifier.weight(1f))
                when {
                    out -> Tag("缺货", bg = c.dangerBg, fg = c.danger)
                    p.id in state.expiryOff -> Tag("临期 ${pctText(state.settings.expiryPct)}", fg = c.warning)
                    !weigh && left != null && left <= 5 -> Tag("仅剩 $left", fg = c.warning)
                }
            }
        }
        if (inCart > 0) Text("$inCart", style = t.label.num().copy(fontWeight = FontWeight.SemiBold), color = c.onPrimary,
            modifier = Modifier.align(Alignment.TopEnd).clip(Pos.metrics.button).background(c.primary).padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

/* ---------------- 右 · 收款态 ---------------- */

@Composable
private fun PayPane(state: RetailPosUiState, onAction: (RetailPosAction) -> Unit, modifier: Modifier) {
    val c = Pos.colors; val t = Pos.type
    val cart = state.cart; val due = state.payDue
    Panel(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("收款", style = t.title, color = c.ink, modifier = Modifier.weight(1f))
                    Text(if (state.credit != null) "补收 " else "应收 ", style = t.body, color = c.sub)
                    Text(yuan(due), style = t.hero.num(), color = c.primary)
                }
                Section("收款方式") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.payMethods.forEach { m ->
                            PosButton(m, { onAction(RetailPosAction.SelectMethod(m)) }, Modifier.weight(1f), size = BtnSize.Large, selected = cart.method == m,
                                enabled = !(m == "扫码" && !state.online))
                        }
                    }
                    Text(when {
                        !state.online -> "离线：扫码不可用；现金、银行卡、储值本机记账，联网后同步"
                        "储值卡" in state.payMethods -> if (cart.member) "${state.memberRec?.name ?: "会员"}储值余额 ${yuan(state.wallet)}" else "储值卡需先识别会员"
                        else -> "本机记账；扫码为演示渠道"
                    }, style = t.label, color = if (state.online) c.sub else c.warning, modifier = Modifier.padding(top = 6.dp))
                }
                when (cart.method) {
                    null -> EmptyState("选择收款方式；顾客出示付款码时直接扫，自动切到扫码收款")
                    "现金" -> Section("现金收款") {
                        CashPad(cart.cash, due, { onAction(RetailPosAction.CashInput(it)) }, enabled = !cart.processing)
                    }
                    "扫码" -> WaitBlock("请顾客出示付款码", "用扫码枪扫顾客的付款码，或让顾客扫收款码；演示环境点「确认已收款」")
                    "银行卡" -> WaitBlock("请在刷卡机上刷卡 ${yuan(due)}", "刷卡机打出签购单后点「确认已收款」")
                    else -> WaitBlock("会员储值扣款 ${yuan(due)}", "扣款后余额 ${yuan(maxOf(0, state.wallet - due))}")
                }
            }
            Row(Modifier.fillMaxWidth().background(c.control).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosButton("返回加购", { onAction(RetailPosAction.BackToScan) }, Modifier.weight(0.6f), variant = BtnVariant.Text, size = BtnSize.Large)
                PosButton(state.block ?: if (cart.method == "现金") "确认收款 ${yuan(due)}" else "确认已收款 ${yuan(due)}", { onAction(RetailPosAction.Confirm) }, Modifier.weight(1.4f),
                    variant = BtnVariant.Primary, size = BtnSize.Large, enabled = state.block == null && !cart.processing, loading = cart.processing)
            }
        }
    }
}

@Composable
private fun WaitBlock(title: String, hint: String) {
    Section(title) { Text(hint, style = Pos.type.label, color = Pos.colors.sub) }
}

/* ---------------- 浮层 ---------------- */

@Composable
private fun WeighDialog(p: Product, grams: Int, onAction: (RetailPosAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    // 接了秤就读秤（稳定读数才能加入）；没接秤或切到手输就手输
    val scale by appContainer().scale.state.collectAsStateWithLifecycle()
    var manual by remember(p.id) { mutableStateOf(false) }
    var tare by remember(p.id) { mutableStateOf(0) }
    val live = (scale as? ai.neargo.fnb.pos.core.hardware.ScaleState.Live)?.reading
    val useScale = scale !is ai.neargo.fnb.pos.core.hardware.ScaleState.Off && !manual
    var input by remember(p.id) { mutableStateOf(if (grams > 0) kg(grams) else "") }
    val w = if (useScale) maxOf(0, (live?.grams ?: 0) - tare) else parseKg(input) ?: grams
    val ready = if (useScale) live?.stable == true && w > 0 else w > 0
    val amount = Money.roundHalfUp(p.price * w, 1000)
    PosDialog("称重 · ${nameOf(p)}", { onAction(RetailPosAction.Dismiss) }, subtitle = "${yuan(p.price)}/kg · ${p.retail?.barcodes?.firstOrNull().orEmpty()}", footer = {
        PosButton("取消", { onAction(RetailPosAction.Dismiss) }, variant = BtnVariant.Text)
        PosButton("加入 ${yuan(amount)}", { onAction(RetailPosAction.AddWeighed(p.id, w)) }, variant = BtnVariant.Primary, enabled = ready)
    }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(Pos.metrics.card).background(c.tile).padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${kg(w)} kg", style = t.hero.copy(fontSize = 44.sp).num(), color = c.ink)
            val (msg, color) = when {
                !useScale && scale is ai.neargo.fnb.pos.core.hardware.ScaleState.Off -> "本机没接电子秤：手输重量（系统管理 · 终端与打印 · 电子秤）" to c.warning
                !useScale -> "手输重量" to c.sub
                live?.overload == true -> "超出秤的量程" to c.danger
                scale is ai.neargo.fnb.pos.core.hardware.ScaleState.Error -> (scale as ai.neargo.fnb.pos.core.hardware.ScaleState.Error).message to c.danger
                live == null -> "等秤的读数…" to c.sub
                !live.stable -> "称重中，等读数稳定" to c.warning
                else -> ("读数稳定" + (if (tare > 0) " · 已去皮 ${tare}g" else "")) to c.primary
            }
            Text(msg, style = t.label, color = color)
        }
        if (useScale) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            PosButton("去皮", { tare = live?.grams ?: 0 }, Modifier.weight(1f), size = BtnSize.Small, enabled = live?.stable == true)
            PosButton("清除去皮", { tare = 0 }, Modifier.weight(1f), size = BtnSize.Small, enabled = tare > 0)
            PosButton("改为手输", { manual = true }, Modifier.weight(1f), size = BtnSize.Small)
        } else {
            PosTextField(input, { v -> if (Regex("^\\d{0,2}(\\.\\d{0,3})?$").matches(v)) input = v }, "重量（kg，最多 3 位小数）", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                listOf(250, 500, 1000).forEach { q -> PosButton("${kg(q)} kg", { input = kg(q) }, Modifier.weight(1f), size = BtnSize.Small) }
                PosButton("去皮 −12g", { input = kg(maxOf(0, w - 12)) }, Modifier.weight(1f), size = BtnSize.Small)
                PosButton("归零", { input = "" }, Modifier.weight(1f), size = BtnSize.Small)
                if (scale !is ai.neargo.fnb.pos.core.hardware.ScaleState.Off) PosButton("读秤", { manual = false }, Modifier.weight(1f), size = BtnSize.Small)
            }
        }
        KeyValue("金额", yuan(amount))
    }
}

@Composable
private fun SkuDialog(state: RetailPosUiState, p: Product, onAction: (RetailPosAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    var pick by remember(p.id) { mutableStateOf<String?>(null) }
    val groups = state.groupsOf(p)
    val rows = groups.firstOrNull()?.options.orEmpty()
    val cols = groups.getOrNull(1)?.options
    PosDialog("选规格 · ${nameOf(p)}", { onAction(RetailPosAction.Dismiss) }, subtitle = "${yuan(p.price)} · 点一格加入，数字是库存", maxWidth = 640.dp, footer = {
        PosButton("取消", { onAction(RetailPosAction.Dismiss) }, variant = BtnVariant.Text)
        PosButton(pick?.let { "加入 $it" } ?: "请选择规格", { pick?.let { onAction(RetailPosAction.AddSku(p.id, it)) } }, variant = BtnVariant.Primary, enabled = pick != null)
    }) {
        val cell: @Composable (Modifier, ai.neargo.fnb.pos.core.model.Sku?) -> Unit = { m, k ->
            val n = k?.stock ?: 0; val on = k != null && pick == k.label
            Box(m.height(52.dp).clip(Pos.metrics.button).background(if (on) c.tint else c.tile).clickable(enabled = n > 0) { pick = k?.label }
                .semantics { contentDescription = k?.label ?: "" }, contentAlignment = Alignment.Center) {
                Text(if (n <= 0) "缺货" else if (n <= 2) "仅剩 $n" else "$n", style = t.body.num().let { if (on) it.copy(fontWeight = FontWeight.SemiBold) else it },
                    color = when { on -> c.primary; n <= 0 -> c.sub; n <= 2 -> c.warning; else -> c.ink })
            }
        }
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (cols != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(groups[0].name, style = t.label, color = c.sub, modifier = Modifier.width(72.dp))
                cols.forEach { z -> Text(z.name, style = t.strong, color = c.sub, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = t.strong, color = c.ink, modifier = Modifier.width(72.dp))
                    if (cols == null) cell(Modifier.weight(1f), p.skus.firstOrNull { r.id in it.optionIds })
                    else cols.forEach { z -> cell(Modifier.weight(1f), p.skus.firstOrNull { r.id in it.optionIds && z.id in it.optionIds }) }
                }
            }
        }
    }
}

@Composable
private fun HeldSheet(state: RetailPosUiState, onAction: (RetailPosAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    val fmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    PosSheet({ onAction(RetailPosAction.Dismiss) }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("挂单 · ${state.held.size}", style = t.title, color = c.ink)
            if (state.cart.lines.isNotEmpty()) Text("当前购物车有 ${state.cart.lines.size} 行，取单时会先把它挂起", style = t.label, color = c.warning)
            if (state.held.isEmpty()) EmptyState("没有挂单")
            state.held.forEach { h ->
                val tot = RetailCheckout.totals(state.products, h.lines, h.member != null)
                Row(Modifier.fillMaxWidth().clip(Pos.metrics.card).background(c.tile).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${h.no} · ${yuan(tot.due)}", style = t.strong.num(), color = c.ink)
                        Text(listOfNotNull(fmt.format(java.util.Date(h.at)), "${tot.pcs} 件", h.member?.let { id -> state.members.firstOrNull { it.id == id }?.name ?: id }, h.why.ifBlank { null }).joinToString(" · "), style = t.label.num(), color = c.sub)
                    }
                    PosButton("取单", { onAction(RetailPosAction.Recall(h.no)) }, size = BtnSize.Small, variant = BtnVariant.Soft)
                }
            }
        }
    }
}

@Composable
private fun ClearDialog(state: RetailPosUiState, onAction: (RetailPosAction) -> Unit) {
    PosDialog("清空购物车？", { onAction(RetailPosAction.Dismiss) }, footer = {
        PosButton("取消", { onAction(RetailPosAction.Dismiss) }, variant = BtnVariant.Text)
        PosButton("改为挂单", { onAction(RetailPosAction.Hold) })
        PosButton("清空", { onAction(RetailPosAction.Clear) }, variant = BtnVariant.Danger)
    }) {
        Text("购物车里 ${state.cart.lines.size} 行、共 ${state.totals.pcs} 件会被移除，不能撤销。顾客只是暂时离开的话，用「挂单」。", style = Pos.type.body, color = Pos.colors.ink, modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun CustomDialog(code: String?, onAction: (RetailPosAction) -> Unit) {
    var name by remember { mutableStateOf("临时商品") }
    var price by remember { mutableStateOf("") }
    val fen = CashRules.amount(price) ?: 0
    PosDialog("临时商品", { onAction(RetailPosAction.Dismiss) }, subtitle = code?.let { "未建档条码 $it" } ?: "无码商品按手输价格加入，不扣库存", footer = {
        PosButton("取消", { onAction(RetailPosAction.Dismiss) }, variant = BtnVariant.Text)
        PosButton("加入 ${yuan(fen)}", { onAction(RetailPosAction.CustomAdd(name, fen)) }, variant = BtnVariant.Primary, enabled = fen > 0 && Money.withinBill(fen))
    }) {
        PosTextField(name, { if (it.length <= 20) name = it }, "名称", autoFocus = true)
        PosTextField(price, { if (Regex("^\\d{0,6}(\\.\\d{0,2})?$").matches(it)) price = it }, "单价（元）", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
}

@Composable
private fun DoneDialog(state: RetailPosUiState, d: RetailOverlay.Done, onAction: (RetailPosAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    PosDialog(if (d.credit != null) "换货完成" else "收款完成", { onAction(RetailPosAction.Next) }, subtitle = "${d.sale.no} · ${d.sale.pieces} 件", footer = {
        PosButton("下一位", { onAction(RetailPosAction.Next) }, variant = BtnVariant.Primary, size = BtnSize.Large)
    }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val cashPay = d.sale.pays.any { it.method == "现金" }
            Text(when { d.refund > 0 -> "已退差价（${d.credit!!.method}）"; cashPay -> "找零"; else -> "已收" }, style = t.label, color = c.sub)
            Text(yuan(when { d.refund > 0 -> d.refund; cashPay -> d.change; else -> d.received }), style = t.hero.copy(fontSize = 44.sp).num(), color = c.ink)
        }
        KeyValue(if (d.credit != null) "新商品" else "应收", yuan(d.sale.paid))
        d.credit?.let { KeyValue("换货抵扣 ${it.no}", "−${yuan(it.amount)}", valueColor = c.primary) }
        if (d.sale.pays.any { it.method == "现金" }) KeyValue("实收", yuan(d.received))
        d.sale.pays.filter { it.method != ai.neargo.fnb.pos.core.model.EXCHANGE }.forEach { KeyValue("收款 · ${it.method}", yuan(it.amount)) }
        if (d.save > 0) KeyValue("本单优惠", "−${yuan(d.save)}", valueColor = c.primary)
        Text(if (state.online) "小票已加入打印队列，库存已扣减" else "离线：已本机记账并进入同步队列；小票已加入打印队列", style = t.label, color = c.sub, modifier = Modifier.padding(vertical = 8.dp))
    }
}

/* ---------------- 小工具 ---------------- */

private fun kg(grams: Int) = "%d.%03d".format(grams / 1000, grams % 1000)
private fun parseKg(s: String): Int? = s.toBigDecimalOrNull()?.movePointRight(3)?.toInt()?.takeIf { it in 0..99_999 }
private fun priceText(p: Product) = if (p.retail?.weighable == true) "${yuan(p.price)}/kg" else yuan(p.displayPrice)
private fun stockText(p: Product): String = if (p.retail?.weighable == true) p.stock?.let { "${kg(it)} kg" } ?: "不限" else p.totalStock?.toString() ?: "不限"

/* ---------------- 改价、备注、整单折扣、优惠活动 ---------------- */

private fun pctText(p: Int) = if (p % 10 == 0) "${p / 10} 折" else "${p / 10.0} 折"

/** 主管授权输入：当班员工有「折扣授权」权限时不显示 */
@Composable
private fun AuthFields(state: RetailPosUiState, who: String, secret: String, onWho: (String) -> Unit, onSecret: (String) -> Unit) {
    if (state.canAdjust) { Text("当班员工有折扣授权权限，直接生效", style = Pos.type.label, color = Pos.colors.sub, modifier = Modifier.padding(vertical = 6.dp)); return }
    Section("主管授权") {
        PosTextField(who, onWho, "主管姓名 / 账号")
        PosTextField(secret, onSecret, "PIN / 密码", password = true, keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
    }
}

@Composable
private fun RepriceDialog(state: RetailPosUiState, l: CartLine, onAction: (RetailPosAction) -> Unit) {
    val p = state.product(l.productId) ?: return
    val orig = RetailCheckout.unitOf(p, l.sku)
    var text by remember(l.key) { mutableStateOf(ai.neargo.fnb.pos.core.model.fenToInput(l.price ?: orig)) }
    var who by remember { mutableStateOf("") }; var secret by remember { mutableStateOf("") }
    val fen = CashRules.amount(text)
    val per = if (l.grams != null) "/kg" else "/件"
    PosDialog("改价 · ${lineTitle(state, l)}", { onAction(RetailPosAction.Dismiss) }, subtitle = "原价 ${yuan(orig)}$per；改过价的行不再叠加促销和会员价", footer = {
        if (l.price != null) PosButton("恢复原价", { onAction(RetailPosAction.Reprice(l.key, null, "", "")) }, variant = BtnVariant.Text)
        PosButton("取消", { onAction(RetailPosAction.Dismiss) }, variant = BtnVariant.Text)
        PosButton("确认改价", { onAction(RetailPosAction.Reprice(l.key, fen, who, secret)) }, variant = BtnVariant.Primary,
            enabled = fen != null && fen >= 0 && fen != orig && (state.canAdjust || (who.isNotBlank() && secret.isNotBlank())))
    }) {
        PosTextField(text, { if (Regex("^\\d{0,6}(\\.\\d{0,2})?$").matches(it)) text = it }, "新单价（元$per）", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal), autoFocus = true)
        AuthFields(state, who, secret, { who = it }, { secret = it })
    }
}

@Composable
private fun NoteDialog(state: RetailPosUiState, l: CartLine, onAction: (RetailPosAction) -> Unit) {
    var text by remember(l.key) { mutableStateOf(l.note.orEmpty()) }
    PosDialog("备注 · ${lineTitle(state, l)}", { onAction(RetailPosAction.Dismiss) }, subtitle = "打到小票上，最多 30 字", footer = {
        PosButton("取消", { onAction(RetailPosAction.Dismiss) }, variant = BtnVariant.Text)
        PosButton("保存", { onAction(RetailPosAction.SetNote(l.key, text)) }, variant = BtnVariant.Primary)
    }) {
        PosTextField(text, { if (it.length <= 30) text = it }, "备注（如：礼品包装、顾客自带袋）", autoFocus = true)
    }
}

@Composable
private fun DiscountDialog(state: RetailPosUiState, onAction: (RetailPosAction) -> Unit) {
    var pct by remember { mutableStateOf(if (state.cart.discountPct < 100) state.cart.discountPct else 90) }
    var who by remember { mutableStateOf("") }; var secret by remember { mutableStateOf("") }
    val base = state.totals.sub - state.totals.promo - state.totals.memberSave
    PosDialog("整单折扣", { onAction(RetailPosAction.Dismiss) }, subtitle = "对促销、会员价之后的金额打折；需要主管授权", footer = {
        if (state.cart.discountPct < 100) PosButton("取消折扣", { onAction(RetailPosAction.Discount(100, "", "")) }, variant = BtnVariant.Text)
        PosButton("取消", { onAction(RetailPosAction.Dismiss) }, variant = BtnVariant.Text)
        PosButton("确认 ${pctText(pct)}", { onAction(RetailPosAction.Discount(pct, who, secret)) }, variant = BtnVariant.Primary,
            enabled = state.canAdjust || (who.isNotBlank() && secret.isNotBlank()))
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf(95, 90, 88, 85, 80, 70).forEach { p -> PosChip(pctText(p), pct == p, { pct = p }) }
        }
        KeyValue("折扣金额", "−${yuan(ai.neargo.fnb.pos.core.model.Money.rate(base, (100 - pct) * 100))}")
        AuthFields(state, who, secret, { who = it }, { secret = it })
    }
}

@Composable
private fun PromoDialog(state: RetailPosUiState, onAction: (RetailPosAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    PosDialog("优惠活动", { onAction(RetailPosAction.Dismiss) }, subtitle = "门店「会员与营销 · 优惠活动」里配置；整单折扣类只能选一个", footer = {
        PosButton("完成", { onAction(RetailPosAction.Dismiss) }, variant = BtnVariant.Primary)
    }) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.promotions.forEach { p ->
                val on = p.id in state.cart.promoIds
                val ok = state.totals.sub >= p.threshold
                Row(Modifier.fillMaxWidth().clip(Pos.metrics.button).background(if (on) c.tint else c.tile).clickable { onAction(RetailPosAction.TogglePromo(p.id)) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = t.strong, color = c.ink)
                        Text(p.kind.label + " · " + p.summary + if (!ok) " · 还差 ${yuan(p.threshold - state.totals.sub)}" else "", style = t.label, color = if (ok) c.sub else c.warning)
                    }
                    Text(if (on) "已选" else "选用", style = t.strong, color = if (on) c.primary else c.sub)
                }
            }
        }
    }
}

/** 识别会员：按手机号或姓名找档案，找不到可以新建（和餐饮共用会员档案） */
@Composable
private fun MemberDialog(state: RetailPosUiState, onAction: (RetailPosAction) -> Unit) {
    val c = Pos.colors; val t = Pos.type
    var q by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val hits = state.members.filter { q.isNotBlank() && (it.phone.contains(q.trim()) || it.name.contains(q.trim())) }.take(8)
    val phoneOk = Regex("^1\\d{10}$").matches(q.trim())
    PosDialog("识别会员", { onAction(RetailPosAction.Dismiss) }, subtitle = "也可以直接扫会员码（M + 手机号）；会员价、积分、储值按档案算", footer = {
        PosButton("取消", { onAction(RetailPosAction.Dismiss) }, variant = BtnVariant.Text)
        if (q.isNotBlank() && hits.isEmpty()) PosButton("新建并识别", { onAction(RetailPosAction.NewMember(q.trim(), name.trim())) }, variant = BtnVariant.Primary, enabled = phoneOk && name.isNotBlank())
    }) {
        PosTextField(q, { q = it.take(11) }, "手机号或姓名", keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone), autoFocus = true)
        hits.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Pos.metrics.button).background(c.tile).clickable { onAction(RetailPosAction.PickMember(m.id)) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${m.name} · ${m.phone}", style = t.strong.num(), color = c.ink)
                    Text("储值 ${yuan(m.balance)} · 积分 ${m.points} · 来店 ${m.visits} 次", style = t.label.num(), color = c.sub)
                }
                Text("识别", style = t.strong, color = c.primary)
            }
        }
        if (q.isNotBlank() && hits.isEmpty()) {
            Text(if (phoneOk) "没有这个会员，填姓名就能新建" else "没有找到；新建会员请输 11 位手机号", style = t.label, color = c.sub, modifier = Modifier.padding(vertical = 6.dp))
            if (phoneOk) PosTextField(name, { name = it.take(10) }, "姓名")
        }
        if (q.isBlank()) Text("门店共 ${state.members.size} 位会员", style = t.label, color = c.sub, modifier = Modifier.padding(vertical = 6.dp))
    }
}
