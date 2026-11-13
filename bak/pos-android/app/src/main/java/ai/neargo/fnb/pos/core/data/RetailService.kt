package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * 零售业务服务（零售业态的独立接口实现）：收银入账、退换货、挂取单、库存作业、调拨、改价价签、线上订单、零售设置。
 * 只依赖公共内核 [StoreKernel]（门店事务、权限、单号、打印副本、主管授权）和公共收款 [Settlement]，不碰餐饮代码。
 * 餐饮的对应接口是 FloorRepository + OrderRepository；两业态按门店业态切换（BizProfile）。
 */
internal class RetailService(private val k: StoreKernel) : RetailRepository {
    // 内核薄封装：业务代码照原样调用
    private val state get() = k.vault
    private val settlement = Settlement(k)
    private fun clock() = k.clock()
    private fun newId(prefix: String) = k.newId(prefix)
    private fun actorName(v: Vault) = k.actorName(v)
    private fun need(perm: Perm) = k.need(perm)
    private fun supervisorAuth(v: Vault, who: String, secret: String) = k.supervisorAuth(v, who, secret)
    private fun StoreData.withCopies(job: PrintJob) = k.copies(this, job)
    private suspend fun storeOp(event: LocalWorkspace.EventSpec? = null, check: (StoreData) -> String? = { null }, f: (StoreData) -> StoreData) = k.storeOp(event, check, f)
    private suspend fun <T> transaction(event: LocalWorkspace.EventSpec? = null, f: (Vault) -> Pair<Vault, T>): T = k.transaction(event, f)
    private fun EventSpec(resolve: (StoreData, StoreData) -> Triple<String, String, List<String>>) = LocalWorkspace.EventSpec(resolve)

    // 规则在 StockDocRules 等；过账写 stockMovements，和餐饮盘点同一本流水
    override val retail: StateFlow<RetailData> = k.derive { it.store?.retail ?: RetailData() }

    private fun retailEvent(type: String, payload: String) = EventSpec { _, _ -> Triple(type, payload, listOf("retail")) }
    private fun StoreData.withRetail(f: (RetailData) -> RetailData) = copy(retail = f(retail))
    private fun retailOnly(s: StoreData) = if (s.biz != BizType.Retail) "当前门店不是零售业态" else null
    /** 账面：按件商品是件数，称重商品是克；SKU 用「商品#规格键」 */
    private fun StoreData.bookOf(pid: String, sku: String?): Long {
        val p = product(pid) ?: return 0
        return (if (sku == null) p.stock else p.skus.firstOrNull { it.key == sku }?.stock)?.toLong() ?: 0
    }
    private fun StoreData.setBook(pid: String, sku: String?, value: Long, reason: String, actor: String): StoreData {
        val p = product(pid) ?: return this
        val before = if (sku == null) p.stock else p.skus.firstOrNull { it.key == sku }?.stock
        val v = value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val label = if (sku == null) p.name else "${p.name} · ${p.skus.firstOrNull { it.key == sku }?.label.orEmpty()}"
        val move = StockMovement(newId("SM"), pid, sku, label, before, v, before?.let { v - it }, reason, actor, clock())
        return copy(stockMovements = stockMovements + move, products = products.map { x ->
            when {
                x.id != pid -> x
                sku == null -> x.copy(stock = v)
                else -> x.copy(skus = x.skus.map { if (it.key == sku) it.copy(stock = v, manualStock = true) else it })
            }
        })
    }

    override suspend fun holdCart(lines: List<CartLine>, member: String?, why: String): Result<String> {
        var no = ""
        return storeOp(event = retailEvent("RetailCartHeld", """{"lines":${lines.size}}"""), check = { s -> retailOnly(s) ?: if (lines.isEmpty()) "购物车是空的" else null }) { s ->
            // 挂单号单独编，不和零售单号混：取已用过的最大号 + 1
            val n = maxOf(s.retail.heldSeq, s.retail.held.maxOfOrNull { it.no.drop(1).toIntOrNull() ?: 0 } ?: 0) + 1
            no = "H%04d".format(n)
            s.withRetail { it.copy(held = it.held + RetailHeld(no, clock(), lines, member, why.trim().take(30)), heldSeq = n) }
        }.map { no }
    }

    override suspend fun recallHeld(no: String): Result<RetailHeld> {
        val h = state.value.store?.retail?.held?.firstOrNull { it.no == no } ?: return Result.failure(IllegalStateException("挂单不存在"))
        return storeOp(event = retailEvent("RetailCartRecalled", """{"no":"$no"}"""), check = { s -> retailOnly(s) ?: if (s.retail.held.none { it.no == no }) "挂单不存在" else null }) { s ->
            s.withRetail { it.copy(held = it.held.filterNot { x -> x.no == no }) }
        }.map { h }
    }

    override suspend fun newStockDoc(kind: StockDocKind, supplier: String, scope: String, toStore: String): Result<String> {
        var no = ""
        val actor = actorName(state.value)
        return storeOp(event = EventSpec { b, a -> Triple("StockDocCreated", RetailEventPayloads.stockDoc(b, a, null), listOf("retail")) }, check = { s ->
            val to = state.value.stores[toStore]
            when {
                kind == StockDocKind.Transfer && (to == null || to.id == s.id) -> "请选择调入门店"
                kind == StockDocKind.Transfer && to!!.biz != BizType.Retail -> "只能调到零售门店"
                else -> retailOnly(s) ?: need(Perm.Settings)
            }
        }) { s ->
            no = "%s%04d".format(kind.prefix, s.retail.docSeq)
            val st = if (kind == StockDocKind.Count) StockDocState.Counting else StockDocState.Draft
            s.withRetail { it.copy(docSeq = it.docSeq + 1, stockDocs = listOf(StockDoc(no, kind, st, clock(), actor, supplier = supplier.trim().take(30), scope = scope.trim().take(30),
                toStore = if (kind == StockDocKind.Transfer) toStore else "")) + it.stockDocs) }
        }.map { no }
    }

    /**
     * 调拨发出：本店按单扣库存（单据变「已过账」），同时在调入门店生成一张「在途」调入单（商品按 ID 对，对不上按条码对）；
     * 调入门店确认收货时才加库存。两家店在同一次写入里改，不会只改一边。
     */
    private suspend fun transferOut(no: String): Result<StockDocState> {
        val actor = actorName(state.value)
        return persistenceResult {
            transaction(EventSpec { b, a -> Triple("StockDocPosted", RetailEventPayloads.stockDoc(b, a, no, mapOf("transfer" to kotlinx.serialization.json.JsonPrimitive(true))), listOf("retail")) }) { v ->
                val s = v.store ?: return@transaction v to Result.failure<StockDocState>(IllegalStateException("请先选择门店"))
                val d = s.retail.stockDocs.firstOrNull { it.no == no } ?: return@transaction v to Result.failure(IllegalStateException("单据不存在"))
                val to = v.stores[d.toStore] ?: return@transaction v to Result.failure(IllegalStateException("调入门店不存在"))
                (retailOnly(s) ?: need(Perm.Settings) ?: if (d.lines.isEmpty()) "单据里还没有商品" else null)?.let { return@transaction v to Result.failure(IllegalStateException(it)) }
                // 调入门店的商品：先按 ID，再按第一个条码
                fun target(pid: String) = to.products.firstOrNull { it.id == pid } ?: s.product(pid)?.retail?.barcodes?.firstOrNull()?.let { code -> to.products.firstOrNull { p -> p.retail?.barcodes?.contains(code) == true } }
                d.lines.firstOrNull { target(it.productId) == null }?.let { l -> return@transaction v to Result.failure(IllegalStateException("${s.product(l.productId)?.name ?: l.productId} 在${to.settings.shortName}没有建档")) }
                var from = s
                d.lines.forEach { l -> from = from.setBook(l.productId, l.sku, from.bookOf(l.productId, l.sku) - l.qty, "调拨发出 $no → ${to.settings.shortName}", actor) }
                from = from.withRetail { r -> r.copy(stockDocs = r.stockDocs.map { if (it.no == no) it.copy(state = StockDocState.Posted) else it }) }
                val inNo = "DR%04d".format(to.retail.docSeq)
                val incoming = StockDoc(inNo, StockDocKind.Transfer, StockDocState.InTransit, clock(), actor, d.lines.map { l -> l.copy(productId = target(l.productId)!!.id) }, ref = no, fromStore = s.id)
                val dest = to.copy(retail = to.retail.copy(docSeq = to.retail.docSeq + 1, stockDocs = listOf(incoming) + to.retail.stockDocs))
                v.copy(stores = v.stores + (s.id to from) + (to.id to dest)) to Result.success(StockDocState.Posted)
            }
        }
    }

    override suspend fun setStockDocLines(no: String, lines: List<StockDocLine>): Result<Unit> =
        storeOp(event = EventSpec { b, a -> Triple("StockDocUpdated", RetailEventPayloads.stockDoc(b, a, no), listOf("retail")) }, check = { s ->
            val d = s.retail.stockDocs.firstOrNull { it.no == no }
            when {
                d == null -> "单据不存在"
                !d.editable -> "单据已${d.state.label}，不能再改"
                lines.any { it.qty < 0 } -> "数量不能为负"
                lines.any { s.product(it.productId) == null } -> "商品不存在"
                else -> retailOnly(s) ?: need(Perm.Settings)
            }
        }) { s -> s.withRetail { r -> r.copy(stockDocs = r.stockDocs.map { if (it.no == no) it.copy(lines = lines) else it }) } }

    override suspend fun advanceStockDoc(no: String): Result<StockDocState> {
        val actor = actorName(state.value)
        state.value.store?.retail?.stockDocs?.firstOrNull { it.no == no }?.let { d -> if (d.kind == StockDocKind.Transfer && d.state == StockDocState.Draft) return transferOut(no) }
        var next = StockDocState.Draft
        return storeOp(event = EventSpec { b, a ->
            val d = a.retail.stockDocs.first { it.no == no }
            Triple(if (d.state == StockDocState.Pending) "StockDocSubmitted" else "StockDocPosted", RetailEventPayloads.stockDoc(b, a, no), listOf("retail") + d.lines.map { "stock:${it.productId}" })
        }, check = { s ->
            val d = s.retail.stockDocs.firstOrNull { it.no == no }
            when {
                d == null -> "单据不存在"
                d.lines.isEmpty() -> "单据里还没有商品"
                d.state !in setOf(StockDocState.Draft, StockDocState.Counting, StockDocState.Pending, StockDocState.InTransit) -> "单据已${d.state.label}"
                else -> retailOnly(s) ?: need(Perm.Settings)
            }
        }) { s0 ->
            val d = s0.retail.stockDocs.first { it.no == no }
            fun key(l: StockDocLine) = l.productId + (l.sku?.let { "#$it" } ?: "")
            var s = s0
            val updated: StockDoc = when {
                d.kind == StockDocKind.Receive -> { d.lines.forEach { l -> s = s.setBook(l.productId, l.sku, s.bookOf(l.productId, l.sku) + l.qty, "入库 ${d.no}", actor) }; d.copy(state = StockDocState.Posted) }
                // 调入：确认收货，加库存
                d.kind == StockDocKind.Transfer -> { d.lines.forEach { l -> s = s.setBook(l.productId, l.sku, s.bookOf(l.productId, l.sku) + l.qty, "调拨收货 ${d.no}（${d.ref}）", actor) }; d.copy(state = StockDocState.Posted) }
                d.kind == StockDocKind.Loss -> { d.lines.forEach { l -> s = s.setBook(l.productId, l.sku, s.bookOf(l.productId, l.sku) - l.qty, "报损 ${d.no} · ${l.reason?.label ?: "其他"}", actor) }; d.copy(state = StockDocState.Posted) }
                d.state == StockDocState.Counting -> {
                    // 提交：冻结账面快照，算差异，等审批
                    val snap = d.lines.associate { key(it) to s.bookOf(it.productId, it.sku) }
                    val diff = StockDocRules.countDiff(snap, d.lines.associate { key(it) to it.qty })
                    d.copy(state = StockDocState.Pending, snapshot = snap, gain = diff.gain, loss = diff.loss)
                }
                else -> {
                    // 审批过账：按差异（实盘 − 提交时冻结的账面）调整当前账面，提交后的入库、销售不会被覆盖掉
                    d.lines.forEach { l ->
                        val frozen = d.snapshot[key(l)] ?: s.bookOf(l.productId, l.sku)
                        s = s.setBook(l.productId, l.sku, s.bookOf(l.productId, l.sku) + (l.qty - frozen), "盘点 ${d.no}", actor)
                    }
                    d.copy(state = if (d.gain != 0L || d.loss != 0L) StockDocState.Variance else StockDocState.Posted)
                }
            }
            next = updated.state
            s.withRetail { r -> r.copy(stockDocs = r.stockDocs.map { if (it.no == no) updated else it }) }
        }.map { next }
    }

    /** 门店今天（按本机时区）、现在时刻：临期折扣、禁售时段用 */
    private fun today() = java.time.Instant.ofEpochMilli(clock()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    private fun nowTime() = java.time.Instant.ofEpochMilli(clock()).atZone(java.time.ZoneId.systemDefault()).toLocalTime()

    override fun actor(): String = actorName(state.value)

    override suspend fun authorize(who: String, secret: String): Result<String> = supervisorAuth(state.value, who, secret)

    override suspend fun checkoutRetail(lines: List<CartLine>, member: Boolean, method: String, received: Long, online: Boolean, extras: CheckoutExtras): Result<RetailSale> {
        var sale: RetailSale? = null
        val actor = actorName(state.value)
        return storeOp(event = EventSpec { b, a -> val x = a.retail.sales.first(); Triple("RetailSaleCompleted", RetailEventPayloads.sale(b, a, method), listOf("retail") + x.lines.filter { it.productId.isNotBlank() }.map { "stock:${it.productId}" }) }, check = { s ->
            val ps = RetailCheckout.priced(s.products, s.retail.settings, today())
            val t = RetailCheckout.totalsWith(ps, s.promotions, lines, member, extras)
            val credit = s.retail.credit
            val st = RetailPricing.exchangeSettle(t.due, credit?.amount ?: 0)
            // 活动、限售时段、限购：按顺序只算一次（前面的校验没过就不算）
            var block: String? = null; var blockDone = false
            fun ruleBlock(): String? { if (!blockDone) { blockDone = true
                block = RetailCheckout.promotionBlock(ps, s.promotions, lines, extras.promotionIds) ?: RetailCheckout.restrictBlock(ps, lines, s.retail.settings, nowTime()) ?: RetailCheckout.limitBlock(ps, lines, s.retail.settings.promoLimit) }
                return block }
            when {
                lines.isEmpty() && credit == null -> "购物车是空的"
                lines.any { !it.custom && s.product(it.productId) == null } -> "购物车里有已下架的商品"
                extras.discountPct !in 50..100 -> "整单折扣在 5 折到不打折之间"
                lines.any { (it.price ?: 0) < 0 } -> "改后单价不能为负"
                RetailCheckout.needsApproval(lines, extras) && extras.approver.isBlank() -> "改价、整单折扣需要主管授权"
                ruleBlock() != null -> ruleBlock()
                !Money.withinBill(t.due) || !Money.withinBill(received) -> "金额超出上限"
                // 换货抵扣后不用再收钱：收款方式不看
                credit != null && st.pay == 0L -> retailOnly(s) ?: need(Perm.Checkout)
                method !in listOf("现金", "银行卡", "扫码", "储值卡") -> "请选择收款方式"
                method !in s.settings.payMethods -> "门店未启用「$method」"
                method == "现金" && received < st.pay -> "实收不足，不能确认收款"
                !online && method == "扫码" -> "离线时扫码收款不可用"
                extras.memberId.isNotBlank() && s.members.none { it.id == extras.memberId } -> "会员不存在"
                method == "储值卡" && extras.memberId.isNotBlank() && s.members.first { it.id == extras.memberId }.balance < st.pay -> "会员储值余额不足"
                method == "储值卡" && extras.memberId.isBlank() && (!member || s.walletBalance < st.pay) -> if (!member) "请先识别会员" else "储值余额不足"
                else -> retailOnly(s) ?: need(Perm.Checkout)
            }
        }) { s0 ->
            val ps0 = RetailCheckout.priced(s0.products, s0.retail.settings, today())
            val t = RetailCheckout.totalsWith(ps0, s0.promotions, lines, member, extras)
            val credit = s0.retail.credit
            val st = RetailPricing.exchangeSettle(t.due, credit?.amount ?: 0)
            val no = "#R%04d".format(s0.retail.saleSeq)
            // 改价、折扣、活动的说明（带授权人），订单详情和小票显示
            val adjust = listOfNotNull(
                lines.count { it.price != null }.takeIf { it > 0 }?.let { "改价 $it 行" },
                extras.discountPct.takeIf { it < 100 }?.let { "整单 ${it / 10.0} 折".replace(".0 折", " 折") },
                s0.promotions.filter { it.id in extras.promotionIds }.joinToString("、") { it.name }.ifBlank { null },
            ).joinToString(" · ").let { if (it.isNotBlank() && extras.approver.isNotBlank() && RetailCheckout.needsApproval(lines, extras)) "$it（授权：${extras.approver}）" else it }
            val cash = method == "现金"
            val pays = listOfNotNull(credit?.let { SalePay(EXCHANGE, minOf(it.amount, t.due)) }.takeIf { (it?.amount ?: 0) > 0 }, if (st.pay > 0) SalePay(method, st.pay) else null)
            val mem = s0.members.firstOrNull { it.id == extras.memberId }
            val x = RetailSale(no, clock(), member = mem?.name ?: if (member) "会员" else null, memberId = mem?.id.orEmpty(), lines = RetailCheckout.saleLines(ps0, lines, member), discount = t.round + t.coupon + t.manual, pays = pays, adjust = adjust)
            var s = s0
            // 扣库存：按件扣件数，称重扣克；临时商品不管库存
            lines.filter { !it.custom }.forEach { l ->
                val p = s.product(l.productId)!!; val key = RetailCheckout.skuKeyOf(p, l.sku)
                s = s.setBook(l.productId, key, s.bookOf(l.productId, key) - (l.grams ?: l.qty).toLong(), "销售 $no", actor)
            }
            // 收款记录：补收按所选方式；换货退差价按原单收款方式记负数
            val pay = when {
                st.pay > 0 -> PaymentRecord(newId("P"), "零售", st.pay, method, clock(), synced = online, received = if (cash) received else st.pay, change = if (cash) received - st.pay else 0L,
                    orderId = no, currency = s0.settings.currency, state = if (method == "扫码") PaymentState.Succeeded else PaymentState.LocalRecorded, source = if (method == "扫码") "simulation" else "local")
                st.refund > 0 -> PaymentRecord(newId("P"), "零售", -st.refund, credit!!.method, clock(), synced = online, orderId = no, currency = s0.settings.currency, state = PaymentState.Refunded, source = "local")
                else -> null
            }
            // 收款记录、离线同步队列、会员积分 / 储值：公共收款 Settlement（餐饮结账同一份）
            if (pay != null) {
                s = settlement.record(s, pay, online, "零售", "$no ${pay.method} ${if (pay.amount < 0) "退差价" else "收款"} ${yuan(kotlin.math.abs(pay.amount))}")
                val creditMember = credit?.memberId?.ifBlank { null }
                when {
                    // 换货退差价到储值卡：退回原单会员；没有会员才记旧版演示余额
                    pay.amount < 0 && pay.method == "储值卡" && creditMember != null -> s = settlement.memberRefund(s, creditMember, -pay.amount, true, no, actor)
                    pay.method == "储值卡" && mem == null -> s = s.copy(walletBalance = s.walletBalance - pay.amount)
                }
            }
            // 会员：按实付给积分（换货抵扣部分不再给）；储值卡付款扣会员余额
            if (mem != null) {
                val paid = maxOf(0L, pay?.amount ?: 0L)
                s = settlement.memberSpend(s, mem.id, paid, pay != null && pay.method == "储值卡" && pay.amount > 0, no, actor, "储值卡")
            }
            val body = saleReceipt(s0, x, credit, pay)
            sale = x
            s.copy(printJobs = s.printJobs + s.withCopies(PrintJob(newId("R"), PrintKind.Receipt, "零售小票 $no", body, clock())))
                .withRetail { it.copy(sales = listOf(x) + it.sales, saleSeq = it.saleSeq + 1, credit = null) }
        }.map { sale!! }
    }

    /** 零售小票正文：补打时 pay 为 null，只列单上的收款 */
    private fun saleReceipt(s: StoreData, x: RetailSale, credit: ExchangeCredit? = null, pay: PaymentRecord? = null): List<String> =
        listOf(s.settings.shortName, "单号 ${x.no}") + x.lines.flatMap { l -> listOfNotNull("${l.name}${l.sku?.let { " $it" }.orEmpty()}  ${if (l.grams != null) "${l.grams}g" else "×${l.qty}"}  ${yuan(l.amount)}", l.note.ifBlank { null }?.let { "  备注：$it" }) } +
            listOfNotNull(x.adjust.ifBlank { null }, if (x.discount > 0) "整单优惠 ${yuan(x.discount)}" else null, "应收 ${yuan(x.lines.sumOf { it.amount } - x.discount)}",
                credit?.let { "换货抵扣（${it.no}）${yuan(it.amount)}" }) +
            x.pays.filter { it.method != EXCHANGE }.map { "${it.method} ${yuan(it.amount)}" } +
            listOfNotNull(pay?.takeIf { it.change > 0 }?.let { "实收 ${yuan(it.received)} 找零 ${yuan(it.change)}" }, pay?.takeIf { it.amount < 0 }?.let { "退差价 ${it.method} ${yuan(-it.amount)}" }, s.settings.receiptFooter)

    override suspend fun returnRetail(req: ReturnRequest, online: Boolean): Result<SaleReturn> {
        var out: SaleReturn? = null
        val actor = actorName(state.value)
        val rs = state.value.store?.retail?.settings ?: RetailSettings()
        // 无单退货：按设置要主管授权；不要授权时记当前操作员
        val approver = if (req.saleNo == null) { if (rs.noReceiptAuth) supervisorAuth(state.value, req.approver, req.secret).getOrElse { return Result.failure(it) } else actor } else ""
        return storeOp(event = EventSpec { b, a -> Triple("RetailReturned", RetailEventPayloads.returned(b, a, req.saleNo, req.exchange, req.method, req.restock), listOf("retail")) }, check = { s ->
            val sale = req.saleNo?.let { no -> s.retail.sales.firstOrNull { it.no == no } }
            val pieces = sale?.let { ReturnRules.pickedPieces(it.soldLines, req.pick) } ?: req.pick.values.sum()
            when {
                req.saleNo != null && sale == null -> "原单不存在"
                sale != null && ReturnRules.channelBlocked(sale.channel.name.lowercase()) -> "即时零售平台单请在平台发起退款"
                sale != null && s.retail.settings.returnDays > 0 && clock() - sale.at > s.retail.settings.returnDays * 86_400_000L -> "已超过 ${s.retail.settings.returnDays} 天退货期限"
                pieces <= 0 -> "请选择要退的商品"
                req.saleNo == null && req.pick.keys.any { s.product(it) == null } -> "商品不存在"
                req.exchange && req.saleNo == null -> "换货需要原单"
                req.exchange && s.retail.credit != null -> "上一笔换货还没在收银结算"
                req.exchange -> retailOnly(s) ?: need(Perm.Checkout)
                req.saleNo == null && req.method !in ReturnRules.NO_RECEIPT_METHODS -> "无单退货只能退现金或储值"
                sale != null && s.retail.settings.originalMethod && sale.pays.none { it.method == req.method } -> "只能按原收款方式退"
                // 线上支付（小程序单）原路退回，不看门店收款方式
                sale != null && req.method == ONLINE_PAY && sale.pays.none { it.method == ONLINE_PAY } -> "原单没有线上支付"
                sale != null && req.method != ONLINE_PAY && req.method !in s.settings.payMethods -> "门店未启用「${req.method}」"
                !online && (req.method == "扫码" || req.method == ONLINE_PAY) -> "离线时${req.method}退款不可用"
                else -> retailOnly(s) ?: need(Perm.Checkout)
            }
        }) { s0 ->
            var s = s0
            val sale = req.saleNo?.let { no -> s0.retail.sales.first { it.no == no } }
            val ref = sale?.no ?: "NR%04d".format(s0.retail.looseReturns.size + 1)
            // 退回的行：有单取原单行（称重整行），无单按现价
            val back: List<Pair<RetailSaleLine, Int>> = if (sale != null) sale.lines.mapNotNull { l ->
                val n = minOf(req.pick[l.key] ?: 0, ReturnRules.refundable(sale.soldLines.first { it.key == l.key }))
                if (n > 0) l to n else null
            } else req.pick.filterValues { it > 0 }.entries.mapIndexed { i, (pid, n) ->
                val p = s0.product(pid)!!; RetailSaleLine("L${i + 1}", pid, p.name, null, n, null, p.price, p.price * n) to n
            }
            val amount = if (sale != null) ReturnRules.refundAmount(sale.soldLines, sale.discount, req.pick) else back.sumOf { it.first.amount }
            val pieces = back.sumOf { (l, n) -> if (l.grams != null) 1 else n }
            val method = if (req.exchange) EXCHANGE else req.method
            val r = SaleReturn(clock(), amount, pieces, req.reason, method, req.exchange)
            // 库存：回库加回去（称重按原重量）；不回库记一张已过账的报损单（东西没回货架，账面不动）
            if (req.restock) back.filter { it.first.productId.isNotBlank() }.forEach { (l, n) ->
                val p = s.product(l.productId) ?: return@forEach; val key = RetailCheckout.skuKeyOf(p, l.sku)
                s = s.setBook(l.productId, key, s.bookOf(l.productId, key) + (l.grams ?: n).toLong(), "退货 $ref", actor)
            } else {
                val docLines = back.filter { it.first.productId.isNotBlank() }.map { (l, n) -> StockDocLine(l.productId, s.product(l.productId)?.let { RetailCheckout.skuKeyOf(it, l.sku) }, (l.grams ?: n).toLong(), LossReason.Return) }
                if (docLines.isNotEmpty()) s = s.withRetail { it.copy(docSeq = it.docSeq + 1, stockDocs = listOf(StockDoc("%s%04d".format(StockDocKind.Loss.prefix, it.docSeq), StockDocKind.Loss, StockDocState.Posted, clock(), actor, docLines, ref = ref)) + it.stockDocs) }
            }
            if (!req.exchange) {
                val pay = PaymentRecord(newId("P"), "零售", -amount, req.method, clock(), synced = online, orderId = ref, currency = s0.settings.currency, state = PaymentState.Refunded, source = "local")
                val mem = sale?.memberId?.let { id -> s.members.firstOrNull { it.id == id } }
                // 退款记录与会员退款：公共收款 Settlement
                s = settlement.record(s, pay, online, "零售", "$ref ${req.method} 退款 ${yuan(amount)}")
                s = if (mem != null) settlement.memberRefund(s, mem.id, amount, req.method == "储值卡", "退货 $ref", actor)
                    else if (req.method == "储值卡") s.copy(walletBalance = s.walletBalance + amount) else s
                val body = listOf(s0.settings.shortName, "退货小票", "原单 $ref") + back.map { (l, n) -> "${l.name}${l.sku?.let { " $it" }.orEmpty()}  ${if (l.grams != null) "${l.grams}g" else "×$n"}" } +
                    listOfNotNull("退款 ${req.method} ${yuan(amount)}", "原因：${req.reason.label}", approver.ifBlank { null }?.let { "授权：$it" })
                s = s.copy(printJobs = s.printJobs + s.withCopies(PrintJob(newId("R"), PrintKind.Receipt, "退货小票 $ref", body, clock())))
            }
            out = r
            s.withRetail { d ->
                if (sale != null) d.copy(sales = d.sales.map { x ->
                    if (x.no != sale.no) x else {
                        val applied = ReturnRules.applyReturn(x.soldLines, req.pick).associateBy { it.key }
                        x.copy(lines = x.lines.map { it.copy(returned = applied.getValue(it.key).returned) }, returns = x.returns + r)
                    }
                }, credit = if (req.exchange) ExchangeCredit(sale.no, amount, pieces, sale.pays.firstOrNull { it.method != EXCHANGE }?.method ?: "现金", sale.memberId) else d.credit)
                else d.copy(looseReturns = listOf(LooseReturn(ref, r.at, back.map { it.first }, amount, req.reason, req.method, approver, req.restock)) + d.looseReturns)
            }
        }.map { out!! }
    }

    override suspend fun reprintSale(no: String): Result<Unit> = storeOp(event = retailEvent("RetailReceiptReprinted", """{"no":"$no"}"""), check = { s ->
        retailOnly(s) ?: if (s.retail.sales.none { it.no == no }) "原单不存在" else need(Perm.Checkout)
    }) { s ->
        val x = s.retail.sales.first { it.no == no }
        s.copy(printJobs = s.printJobs + s.withCopies(PrintJob(newId("R"), PrintKind.Receipt, "零售小票 $no（补打）", saleReceipt(s, x), clock(), reprint = true)))
    }

    override suspend fun setRetailPrice(productId: String, price: Long): Result<Unit> = storeOp(event = retailEvent("RetailPriceChanged", """{"product":"$productId","price":$price}"""), check = { s ->
        val p = s.product(productId)
        when {
            p == null -> "商品不存在"
            price <= 0 || !Money.withinBill(price) -> "售价要大于 0"
            price == p.price -> "售价没有变化"
            else -> retailOnly(s) ?: need(Perm.Settings)
        }
    }) { s ->
        val p = s.product(productId)!!
        // 规格价没手动改过的跟着基础价走
        val np = p.copy(price = price, skus = p.skus.map { if (it.manualPrice) it else it.copy(price = price) })
        s.copy(products = s.products.map { if (it.id == productId) np else it }).withRetail { r ->
            if (!r.settings.autoLabel) return@withRetail r
            val had = r.labelQueue.firstOrNull { it.productId == productId }
            val job = LabelJob(productId, LabelWhy.Price, had?.oldPrice ?: p.price)
            r.copy(labelQueue = listOf(job) + r.labelQueue.filterNot { it.productId == productId })
        }
    }

    // 钱箱两业态都有：不限零售门店
    override suspend fun openDrawer(): Result<Unit> = storeOp(event = EventSpec { _, _ -> Triple("DrawerOpened", """{"by":"manual"}""", listOf("drawer")) }, check = { _ -> need(Perm.Checkout) }) { s ->
        settlement.openDrawer(s, "手动")
    }

    override suspend fun queueLabel(productId: String): Result<Unit> = storeOp(event = retailEvent("LabelQueued", """{"product":"$productId"}"""), check = { s ->
        retailOnly(s) ?: if (s.product(productId) == null) "商品不存在" else need(Perm.Settings)
    }) { s ->
        if (s.retail.labelQueue.any { it.productId == productId }) s
        else s.withRetail { it.copy(labelQueue = listOf(LabelJob(productId, LabelWhy.Manual)) + it.labelQueue) }
    }

    override suspend fun printLabels(productIds: List<String>, tpl: LabelTpl): Result<Int> {
        val actor = actorName(state.value)
        var n = 0
        return storeOp(event = retailEvent("LabelsPrinted", """{"count":${productIds.size},"tpl":"$tpl"}"""), check = { s ->
            when {
                productIds.isEmpty() -> "请选择要打的价签"
                productIds.any { s.product(it) == null } -> "商品不存在"
                else -> retailOnly(s) ?: need(Perm.Settings)
            }
        }) { s ->
            val program = productIds.flatMap { LabelRules.tspl(s.product(it)!!, tpl) }
            n = productIds.size
            // 进打印队列：路由「标签机」配了打印机就发过去（TSPL）；没配就是模拟打印，记录照样保留
            val job = PrintJob(newId("L"), PrintKind.Label, "价签 ${productIds.size} 张（${tpl.label}）", program, clock())
            s.copy(printJobs = s.printJobs + job)
                .withRetail { r -> r.copy(labelQueue = r.labelQueue.filterNot { it.productId in productIds }, labelPrints = (listOf(LabelPrint(clock(), tpl, productIds, actor, program)) + r.labelPrints).take(50)) }
        }.map { n }
    }

    /** 拣货单：商品、规格、数量，按分类排，方便拣货 */
    private fun pickList(s: StoreData, o: OnlineOrder) = PrintJob(newId("R"), PrintKind.Receipt, "拣货单 ${o.no}",
        listOf("【拣货单】${o.no} · ${o.channel.label}", "${o.customer} ${o.phone}", if (o.delivery) "配送" else "到店自提") +
            o.lines.sortedBy { l -> s.product(l.productId)?.categoryId.orEmpty() }.map { l -> "□ ${s.product(l.productId)?.name ?: l.productId}${l.sku?.let { " $it" }.orEmpty()} ×${l.qty}" } +
            listOfNotNull(o.note.ifBlank { null }?.let { "备注：$it" }), clock())

    /** 演示线上单可挑的商品：在售、按件、无规格 */
    private fun onlinePool(s: StoreData) = s.products.filter { it.retail?.weighable != true && !it.hasSpec && (it.stock ?: 0) > 0 && it.price >= 100 }

    override suspend fun simulateOnlineOrder(channel: SaleChannel): Result<String> {
        var no = ""
        return storeOp(event = retailEvent("OnlineOrderReceived", """{"channel":"$channel"}"""), check = { s ->
            when {
                channel == SaleChannel.Store -> "门店渠道不是线上订单"
                onlinePool(s).isEmpty() -> "没有可下单的在售商品"
                else -> retailOnly(s) ?: need(Perm.Checkout)
            }
        }) { s ->
            val seq = s.retail.onlineSeq
            // 演示：从在售、按件、无规格的商品里轮流挑 2–3 样
            val pool = onlinePool(s)
            val picks = (0 until 2 + seq % 2).map { pool[(seq * 3 + it * 5) % pool.size] }.distinctBy { it.id }
            val lines = picks.mapIndexed { i, p -> CartLine(i + 1L, p.id, 1 + (seq + i) % 2) }
            val t = RetailCheckout.totals(s.products, lines, false)
            no = (if (channel == SaleChannel.Mini) "XC" else "PT") + "%04d".format(seq)
            val tail = "%04d".format((seq * 7919) % 10000)
            var o = OnlineOrder(no, channel, clock(), "顾客", "尾号 $tail", lines, t.due, delivery = channel == SaleChannel.Platform,
                saleLines = RetailCheckout.saleLines(s.products, lines, false))
            var next = s
            if (s.retail.settings.autoAccept) {
                o = o.copy(state = OnlineState.Picking)
                if (s.retail.settings.printPick) next = next.copy(printJobs = next.printJobs + next.withCopies(pickList(s, o)))
            }
            next.withRetail { it.copy(online = listOf(o) + it.online, onlineSeq = seq + 1) }
        }.map { no }
    }

    override suspend fun advanceOnline(no: String): Result<OnlineState> {
        var out = OnlineState.New
        val actor = actorName(state.value)
        return storeOp(event = retailEvent("OnlineOrderAdvanced", """{"no":"$no"}"""), check = { s ->
            val o = s.retail.online.firstOrNull { it.no == no }
            when {
                o == null -> "线上订单不存在"
                o.state == OnlineState.Done || o.state == OnlineState.Rejected -> "订单${o.state.label}"
                o.state == OnlineState.Ready && o.lines.any { s.product(it.productId) == null } -> "有商品已下架，请联系顾客"
                else -> retailOnly(s) ?: need(Perm.Checkout)
            }
        }) { s0 ->
            val o = s0.retail.online.first { it.no == no }
            var s = s0
            val next = when (o.state) {
                OnlineState.New -> { if (s.retail.settings.printPick) s = s.copy(printJobs = s.printJobs + s.withCopies(pickList(s, o))); o.copy(state = OnlineState.Picking) }
                OnlineState.Picking -> o.copy(state = OnlineState.Ready)
                else -> {
                    // 完成：生成零售单（渠道 = 小程序 / 平台），扣库存，记「线上支付」收款（钱在平台，不进现金）
                    val saleNo = "#R%04d".format(s.retail.saleSeq)
                    o.lines.forEach { l -> val p = s.product(l.productId)!!; val key = RetailCheckout.skuKeyOf(p, l.sku)
                        s = s.setBook(l.productId, key, s.bookOf(l.productId, key) - l.qty.toLong(), "线上订单 ${o.no}（$saleNo）", actor) }
                    val sale = RetailSale(saleNo, clock(), o.channel, member = null, lines = o.saleLines, discount = o.saleLines.sumOf { it.amount } - o.amount,
                        pays = listOf(SalePay(ONLINE_PAY, o.amount)), adjust = "${o.channel.label} ${o.no}")
                    val pay = PaymentRecord(newId("P"), "零售", o.amount, ONLINE_PAY, clock(), synced = true, orderId = saleNo, currency = s.settings.currency, state = PaymentState.Succeeded, source = "online")
                    s = s.copy(payments = s.payments + pay).withRetail { it.copy(sales = listOf(sale) + it.sales, saleSeq = it.saleSeq + 1) }
                    o.copy(state = OnlineState.Done, saleNo = saleNo)
                }
            }
            out = next.state
            s.withRetail { r -> r.copy(online = r.online.map { if (it.no == no) next else it }) }
        }.map { out }
    }

    override suspend fun rejectOnline(no: String, reason: String): Result<Unit> = storeOp(event = retailEvent("OnlineOrderRejected", """{"no":"$no"}"""), check = { s ->
        val o = s.retail.online.firstOrNull { it.no == no }
        when {
            o == null -> "线上订单不存在"
            o.state !in setOf(OnlineState.New, OnlineState.Picking) -> "订单${o.state.label}，不能拒单"
            reason.isBlank() -> "请填写拒单原因（会告诉顾客）"
            else -> retailOnly(s) ?: need(Perm.Checkout)
        }
    }) { s -> s.withRetail { r -> r.copy(online = r.online.map { if (it.no == no) it.copy(state = OnlineState.Rejected, note = reason.trim().take(30)) else it }) } }

    override suspend fun saveRetailSettings(settings: RetailSettings): Result<Unit> = storeOp(event = retailEvent("RetailSettingsSaved", """{"promoLimit":${settings.promoLimit},"returnDays":${settings.returnDays}}"""), check = { s ->
        when {
            settings.expiryDays !in 0..30 -> "临期提醒天数在 0–30 之间"
            settings.promoLimit !in 0..99 -> "限购件数在 0–99 之间"
            settings.returnDays !in 0..365 -> "退货期限在 0–365 天之间"
            settings.bagIds.any { s.product(it) == null } -> "购物袋商品不存在"
            settings.expiryPct !in 10..100 -> "临期折扣在 1–10 折之间"
            (settings.restrictFrom.isNotBlank() || settings.restrictTo.isNotBlank()) && listOf(settings.restrictFrom, settings.restrictTo).any { runCatching { java.time.LocalTime.parse(it) }.isFailure } -> "禁售时段请填 HH:mm，例如 22:00"
            settings.restrictCats.any { c -> s.categories.none { it.id == c } } -> "禁售分类不存在"
            else -> retailOnly(s) ?: need(Perm.Settings)
        }
    }) { s -> s.withRetail { it.copy(settings = settings) } }

    override suspend fun voidStockDoc(no: String): Result<Unit> =
        storeOp(event = EventSpec { b, a -> Triple("StockDocVoided", RetailEventPayloads.stockDoc(b, a, no), listOf("retail")) }, check = { s ->
            val d = s.retail.stockDocs.firstOrNull { it.no == no }
            when { d == null -> "单据不存在"; !d.editable -> "只有草稿和盘点中的单据能作废"; else -> retailOnly(s) ?: need(Perm.Settings) }
        }) { s -> s.withRetail { r -> r.copy(stockDocs = r.stockDocs.map { if (it.no == no) it.copy(state = StockDocState.Void) else it }) } }
}
