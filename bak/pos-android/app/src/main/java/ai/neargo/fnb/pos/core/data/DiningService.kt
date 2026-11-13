package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.common.PayMethods
import ai.neargo.fnb.pos.core.common.PayWording
import ai.neargo.fnb.pos.core.common.PaymentRules
import ai.neargo.fnb.pos.core.model.*

/**
 * 餐饮业务服务（餐饮业态的独立接口实现）：预订、开台、结账、清台、换桌 / 并台 / 拆单，点菜、改量、备注、挂取单、送厨、退菜赠送折扣授权。
 * 和 [RetailService] 一样只依赖公共内核 [StoreKernel] 与公共收款 [Settlement]，不碰零售代码。
 * 两业态按门店业态切换（BizProfile）；厨房屏、打印网关、交班、同步等设备与运营能力仍在 LocalWorkspace。
 */
/** 餐饮结账的提示措辞（规则在公共 PaymentRules） */
private val DINING_WORDING = PayWording(
    scanOffline = "离线时该收款方式不可用", noMember = "请先绑定会员", walletShort = "请先绑定会员",
    balanceShort = { "会员余额不足（余额 ${yuan(it)}）" }, memberMustExistForAnyMethod = false,
)

internal class DiningService(private val k: StoreKernel) : FloorRepository, OrderRepository {
    // 内核薄封装：业务代码照原样调用
    private val state get() = k.vault
    private val settlement = Settlement(k)
    private fun clock() = k.clock()
    private fun newId(prefix: String) = k.newId(prefix)
    private fun actorName(v: Vault) = k.actorName(v)
    private fun supervisorAuth(v: Vault, who: String, secret: String) = k.supervisorAuth(v, who, secret)
    private fun StoreData.withCopies(job: PrintJob) = k.copies(this, job)
    private fun StoreData.kitchenJob(tableId: String, batch: String) = k.kitchenJob(this, tableId, batch)
    private fun page(type: String, tableId: String) = k.pageEvent(type, tableId)
    private fun fail(msg: String) = Result.failure<Unit>(IllegalStateException(msg))
    private fun currentStore(): StoreData? = state.value.store
    private suspend fun storeOp(event: LocalWorkspace.EventSpec? = null, check: (StoreData) -> String? = { null }, f: (StoreData) -> StoreData) = k.storeOp(event, check, f)
    private suspend fun <T> transaction(event: LocalWorkspace.EventSpec? = null, f: (Vault) -> Pair<Vault, T>): T = k.transaction(event, f)
    private suspend fun commit(event: LocalWorkspace.EventSpec? = null, f: (Vault) -> Vault): Vault = transaction(event) { v -> val n = f(v); n to n }
    private fun EventSpec(resolve: (StoreData, StoreData) -> Triple<String, String, List<String>>) = LocalWorkspace.EventSpec(resolve)

    override val tables = k.derive { it.store?.tables.orEmpty() }
    override val orders = k.derive { it.store?.orders.orEmpty() }

    // —— 桌台 ——
    override suspend fun createReservation(tableId: String, name: String, phone: String, guests: Int, arrivesAt: Long, note: String) = storeOp(event = page("ReservationCreated", tableId), check = { s ->
        ReservationRules.validate(s.table(tableId), s.reservations, name, phone, guests, arrivesAt, clock())
            ?: if (note.length > 200) "预订备注最多 200 字" else null
    }) { s ->
        val r = Reservation(newId("R"), tableId, name.trim(), phone.trim(), guests, arrivesAt, note.trim(), createdAt = clock())
        s.withTable(tableId) { it.copy(status = TableStatus.Reserved, reservation = ReservationRules.format(arrivesAt)) }
            .copy(reservations = s.reservations + r)
    }

    override suspend fun cancelReservation(id: String): Result<Unit> {
        val tableId = bookedTable(id) ?: return fail("预订已处理或不存在")
        return storeOp(event = page("ReservationCancelled", tableId), check = { s ->
            if (s.reservations.none { it.id == id && it.tableId == tableId && it.status == ReservationStatus.Booked }) "预订已处理或不存在" else null
        }) { s -> cancelled(s, id) }
    }

    private fun bookedTable(id: String) = currentStore()?.reservations?.firstOrNull { it.id == id && it.status == ReservationStatus.Booked }?.tableId

    private fun cancelled(s: StoreData, id: String): StoreData {
        val r = s.reservations.first { it.id == id }
        return s.withTable(r.tableId) { if (it.status == TableStatus.Reserved) it.copy(status = TableStatus.Idle, reservation = "") else it }
            .copy(reservations = s.reservations.map { if (it.id == id) it.copy(status = ReservationStatus.Cancelled, updatedAt = clock()) else it })
    }

    /** 预订到店：开台与预订状态在同一事务，产生独立的 ReservationCheckedIn 事件（不借用 TableOpened）。 */
    override suspend fun checkInReservation(id: String): Result<Unit> {
        val tableId = bookedTable(id) ?: return fail("预订已处理或不存在")
        return storeOp(event = page("ReservationCheckedIn", tableId), check = { s ->
            val r = s.reservations.firstOrNull { it.id == id && it.tableId == tableId && it.status == ReservationStatus.Booked }
                ?: return@storeOp "预订已处理或不存在"
            openBlock(s, tableId, r.guests)
        }) { s -> opened(s, tableId, s.reservations.first { it.id == id }.guests, arrivedId = id) }
    }

    override suspend fun openTable(tableId: String, guests: Int) =
        storeOp(event = page("TableOpened", tableId), check = { s -> openBlock(s, tableId, guests) }) { s -> opened(s, tableId, guests) }

    private fun openBlock(s: StoreData, tableId: String, guests: Int): String? {
        val t = s.table(tableId) ?: return "桌台不存在"
        return when {
            t.status != TableStatus.Idle && t.status != TableStatus.Reserved -> "$tableId ${t.status.label}，不能开台"
            guests !in 1..t.seats -> "人数应为 1–${t.seats}"
            else -> null
        }
    }

    /** arrivedId 为空时（直接开台）该桌所有待到店预订视为到店；否则只标记指定预订。 */
    private fun opened(s: StoreData, tableId: String, guests: Int, arrivedId: String? = null) =
        s.withTable(tableId) { it.copy(status = TableStatus.Dining, guests = guests, openedAt = clock(), reservation = "", paid = 0, sessionId = newId("TS")) }
            .withOrder(tableId, TableOrder(orderId = newId("O"), serviceBp = s.settings.serviceBp, memberBp = s.settings.memberBp)).copy(currentTableId = tableId,
                reservations = s.reservations.map {
                    val hit = if (arrivedId != null) it.id == arrivedId else it.tableId == tableId && it.status == ReservationStatus.Booked
                    if (hit) it.copy(status = ReservationStatus.Arrived, updatedAt = clock()) else it
                })

    override suspend fun settle(tableId: String, amount: Long, method: String, online: Boolean, received: Long): Result<Unit> {
        val pid = newId("P")
        // 储值支付同时修改会员余额：事件参与会员聚合
        val memberAgg = currentStore()?.order(tableId)?.memberId?.takeIf { it.isNotBlank() && method == PayMethods.STORED }
        val spec = page("PaymentRecorded", tableId).let { base -> if (memberAgg == null) base else EventSpec { b, a -> base.resolve(b, a).let { (t, p, ag) -> Triple(t, p, ag + "member:$memberAgg") } } }
        return storeOp(event = spec, check = { s ->
            val totals = OrderRules.totals(s.order(tableId))
            OrderRules.checkoutBlock(totals) ?: DeliveryRules.checkoutBlock(s, tableId) ?: when {
                amount != totals.due -> "账单已变化，请重新核对"
                else -> PaymentRules.amountBlock(amount, received)
                    ?: s.order(tableId).let { o -> PaymentRules.collectBlock(s, method, amount, received, online, o.memberId, o.member, DINING_WORDING) }
            }
        }) { s ->
            val pay = PaymentRules.record(pid, tableId, amount, method, clock(), online, received, s.order(tableId).orderId.ifBlank { null }, s.settings.currency)
            val receipt = PrintJob(newId("R"), PrintKind.Receipt, "$tableId 结账单", Receipts.bill("结账单", s, tableId, s.order(tableId), clock(), pay), clock())
            val o = s.order(tableId); val t = OrderRules.totals(o)
            val snap = SettledOrder(o.orderId, tableId, pid, clock(), o.lines, t.sub, t.service, t.discount, t.due, method, o.memberId, o.promotions.map { it.name }, s.table(tableId)?.guests ?: 0)
            val member = s.members.firstOrNull { it.id == o.memberId }
            // 收款记录、离线同步队列、会员积分 / 储值：公共收款 Settlement（零售收银同一份）
            var next = settlement.collect(s, pay, online, tableId, "$tableId $method 收款 ${yuan(amount)}", member?.id, null, pid, actorName(state.value))
            next.withTable(tableId) { it.copy(status = TableStatus.Paid, paid = amount) }.withOrder(tableId, TableOrder())
                .copy(printJobs = next.printJobs + next.withCopies(receipt), settledOrders = next.settledOrders + snap)
        }
    }

    override suspend fun startCleaning(tableId: String) = transition(tableId, TableStatus.Paid, "TableCleaningStarted") { it.copy(status = TableStatus.Cleaning) }
    override suspend fun finishCleaning(tableId: String) = transition(tableId, TableStatus.Cleaning, "TableCleaningFinished") { it.copy(status = TableStatus.Idle, guests = 0, openedAt = 0, paid = 0, sessionId = "") }

    private suspend fun transition(tableId: String, from: TableStatus, type: String, f: (DiningTable) -> DiningTable) = storeOp(event = page(type, tableId), check = { s ->
        val t = s.table(tableId) ?: return@storeOp "桌台不存在"
        if (t.status != from) "$tableId 当前为${t.status.label}" else null
    }) { it.withTable(tableId, f) }

    override suspend fun setPosition(tableId: String, x: Int, y: Int) = storeOp(event = page("TablePositionChanged", tableId), check = { s ->
        when {
            s.table(tableId) == null -> "桌台不存在"
            x !in 0..TableRules.MAX_X || y !in 0..TableRules.MAX_Y -> "坐标范围：X 0–${TableRules.MAX_X}，Y 0–${TableRules.MAX_Y}"
            else -> null
        }
    }) { it.withTable(tableId) { t -> t.copy(x = x, y = y) } }

    override suspend fun changeTable(from: String, to: String) = storeOp(event = page("TableChanged", from), check = { s ->
        if (s.reservations.any { it.tableId == to && it.status == ReservationStatus.Booked }) return@storeOp "目标桌台已有预订"
        val a = s.table(from); val b = s.table(to)
        if (a == null || b == null) "桌台不存在" else TableRules.canChange(a, b)
    }) { s ->
        val a = s.table(from)!!
        // 换桌：订单ID保留，目标桌开新的桌台会话
        s.withTable(to) { it.copy(status = TableStatus.Dining, guests = a.guests, openedAt = a.openedAt, reservation = "", sessionId = newId("TS")) }
            .withTable(from) { it.copy(status = TableStatus.Cleaning, guests = 0, openedAt = 0) }
            .withOrder(to, s.order(from)).withOrder(from, TableOrder())
            .copy(currentTableId = to)
    }

    override suspend fun mergeTable(from: String, into: String) = storeOp(event = page("TableMerged", from), check = { s ->
        val a = s.table(from); val b = s.table(into)
        if (a == null || b == null) "桌台不存在" else TableRules.canMerge(a, b) ?: TableRules.compatible(s.order(from), s.order(into))
    }) { s ->
        val a = s.table(from)!!
        s.withTable(into) { it.copy(guests = it.guests + a.guests, openedAt = minOf(it.openedAt, a.openedAt)) }
            .withTable(from) { it.copy(status = TableStatus.Cleaning, guests = 0, openedAt = 0) }
            .withOrder(into, TableRules.merge(s.order(from), s.order(into))).withOrder(from, TableOrder())
            .copy(currentTableId = into)
    }

    override suspend fun splitLines(from: String, uids: Set<Long>, to: String, guests: Int) = storeOp(event = page("OrderSplit", from), check = { s ->
        if (s.reservations.any { it.tableId == to && it.status == ReservationStatus.Booked }) return@storeOp "目标桌台已有预订"
        val b = s.table(to)
        when {
            s.table(from)?.status != TableStatus.Dining -> "$from 未在用餐"
            b == null -> "桌台不存在"
            from == to -> "请选择另一张桌台"
            else -> TableRules.canSplit(s.order(from), uids, b, guests) ?: TableRules.compatible(s.order(from), s.order(to))
        }
    }) { s ->
        val (rest, moved) = TableRules.split(s.order(from), uids)
        val b = s.table(to)!!
        val opened = if (b.status == TableStatus.Dining) s else s.withTable(to) { it.copy(status = TableStatus.Dining, guests = guests, openedAt = clock(), reservation = "", sessionId = newId("TS")) }
        val target = opened.order(to)
        val source = s.order(from)
        // 拆单：目标沿用已有订单或生成新订单；记录来源订单，不当作并台
        val merged = TableRules.merge(source.copy(lines = moved), target).copy(
            orderId = target.orderId.ifBlank { newId("O") }, mergedOrderIds = target.mergedOrderIds,
            splitFromOrderIds = (target.splitFromOrderIds + source.orderId).filter { it.isNotBlank() }.distinct(),
        )
        opened.withOrder(from, rest).withOrder(to, merged)
    }

    // —— 订单 ——
    private suspend fun mutate(tableId: String, event: LocalWorkspace.EventSpec? = null, rule: (StoreData, TableOrder) -> OrderResult): OrderResult = try {
        transaction(event) { v ->
            val s = v.store ?: return@transaction v to OrderResult.Rejected("请先选择门店")
            if (s.table(tableId)?.status != TableStatus.Dining) return@transaction v to OrderResult.Rejected("$tableId 未开台，请先在桌台开台")
            val result = rule(s, s.order(tableId))
            val next = if (result is OrderResult.Ok && result.order != s.order(tableId)) v.copy(stores = v.stores + (s.id to s.withOrder(tableId, result.order).copy(seq = s.seq + 1))) else v
            next to result
        }
    } catch (e: WorkspaceWriteException) { OrderResult.Rejected(requireNotNull(e.message)) }
    catch (e: WorkspaceRejectedException) { OrderResult.Rejected(requireNotNull(e.message)) }

    override suspend fun addDish(tableId: String, product: Product, sku: Sku?, qty: Int, note: String, mealChoiceIds: List<String>) =
        mutate(tableId, page("DishAdded", tableId)) { s, o -> OrderRules.add(o, product, sku, qty, s.seq, note, mealChoiceIds) }

    override suspend fun setBenefits(tableId: String, coupon: Boolean, activity: Boolean) = storeOp(event = page("OrderBenefitsChanged", tableId), check = { s ->
        val sub = OrderRules.totals(s.order(tableId)).sub
        when {
            s.table(tableId)?.status != TableStatus.Dining -> "请先开台"
            coupon && sub < 20000 -> "菜品小计未满 200 元"
            activity && sub < 10000 -> "菜品小计未满 100 元"
            else -> null
        }
    }) { s -> s.withOrder(tableId, s.order(tableId).copy(coupon = coupon, activity = activity)) }

    override suspend fun authorizeAdjustment(tableId: String, request: AdjustmentRequest, email: String, password: String): Result<Unit> = runCatching {
        val type = when (request.kind) {
            AdjustmentKind.Void -> "DishVoided"
            AdjustmentKind.Gift -> "DishGifted"
            AdjustmentKind.Discount -> "OrderDiscounted"
        }
        commit(page(type, tableId)) { v ->
            // 员工模式：主管/店长以姓名（或员工ID）+ PIN 授权，需具备「退菜、赠送、折扣授权」权限
            val approver = supervisorAuth(v, email, password).getOrThrow()
            val s = v.store ?: error("请先选择门店")
            require(s.table(tableId)?.status == TableStatus.Dining) { "请先开台" }
            val old = s.order(tableId)
            val result = AdjustmentRules.apply(old, request)
            val updated = (result as? OrderResult.Ok)?.order ?: error((result as OrderResult.Rejected).reason)
            val line = old.lines.firstOrNull { it.uid == request.uid }
            val amount = when (request.kind) {
                AdjustmentKind.Gift -> (line?.unit ?: 0) * (line?.qty ?: 0)
                AdjustmentKind.Void -> if (line?.gifted == true) 0 else (line?.unit ?: 0) * request.quantity
                AdjustmentKind.Discount -> OrderRules.totals(old).due - OrderRules.totals(updated).due
            }
            val audit = AdjustmentAudit(newId("A"), tableId, request.kind, actorName(v), approver, request.reason.trim(), amount,
                if (request.kind == AdjustmentKind.Gift) line?.qty ?: 0 else if (request.kind == AdjustmentKind.Void) request.quantity else 0,
                clock(), line?.name.orEmpty(), if (request.kind == AdjustmentKind.Discount) "${request.percent}%" else "")
            var next = s.withOrder(tableId, updated).copy(adjustments = s.adjustments + audit)
            if (request.kind == AdjustmentKind.Void && updated.lines.isEmpty()) next = next.withTable(tableId) { it.copy(status = TableStatus.Cleaning, guests = 0, openedAt = 0, paid = 0) }
            if (request.kind == AdjustmentKind.Void && line?.batchId != null) next = next.kdsVoid(line.batchId, line.uid, request.quantity, audit.reason)
            if (request.kind == AdjustmentKind.Void) {
                val station = line?.let { KdsRules.stationFor(s, it.dishId) }.orEmpty()
                val tag = if (s.kdsStations.size > 1 && station.isNotBlank()) "·$station" else ""
                next = next.copy(printJobs = next.printJobs + next.withCopies(PrintJob(newId("V"), PrintKind.Kitchen,
                    "$tableId 退菜单$tag", listOf("【退菜单$tag】$tableId", "${line?.name} ×${request.quantity}", line?.spec.orEmpty(), "原因：${audit.reason}", "授权：${audit.supervisor}"), clock(), station = station)))
            }
            v.copy(stores = v.stores + (s.id to next))
        }
        Unit
    }

    override suspend fun setLineNote(tableId: String, uid: Long, note: String) = mutate(tableId, page("DishNoteChanged", tableId)) { _, o -> OrderRules.setNote(o, uid, note) }

    override suspend fun setOrderNote(tableId: String, note: String) = storeOp(event = page("OrderNoteChanged", tableId), check = { s ->
        if (s.table(tableId)?.status != TableStatus.Dining) "$tableId 未在用餐" else if (note.length > 60) "整单备注最多 60 字" else null
    }) { it.withOrder(tableId, it.order(tableId).copy(note = note.trim())) }

    override suspend fun hold(tableId: String): Result<Int> = persistenceResult {
        transaction(page("OrderHeld", tableId)) { v ->
            val s = v.store ?: return@transaction v to Result.failure(IllegalStateException("请先选择门店"))
            val draft = s.order(tableId).pending
            if (draft.isEmpty()) return@transaction v to Result.failure(IllegalStateException("没有可挂起的新加菜品"))
            val next = s.withOrder(tableId, OrderRules.clearNew(s.order(tableId)))
                .copy(held = s.held + HeldTicket(newId("H"), tableId, draft, s.order(tableId).note, clock()))
            v.copy(stores = v.stores + (s.id to next)) to Result.success(draft.sumOf { it.qty })
        }
    }

    override suspend fun retrieve(ticketId: String, tableId: String) = storeOp(event = page("OrderRetrieved", tableId), check = { s ->
        when {
            s.held.none { it.id == ticketId } -> "挂单不存在或已取回"
            s.table(tableId)?.status != TableStatus.Dining -> "$tableId 未在用餐，请先开台"
            else -> null
        }
    }) { s ->
        val t = s.held.first { it.id == ticketId }
        var seq = s.seq
        val lines = t.lines.map { it.copy(uid = seq++, status = LineStatus.New, batchId = null) }
        val o = s.order(tableId)
        val at = o.lines.indexOfLast { it.status == LineStatus.New } + 1
        s.withOrder(tableId, o.copy(lines = o.lines.take(at) + lines + o.lines.drop(at))).copy(held = s.held - t, seq = seq)
    }

    override suspend fun changeQty(tableId: String, uid: Long, delta: Int) = mutate(tableId, page("DishQuantityChanged", tableId)) { s, o ->
        val line = o.lines.firstOrNull { it.uid == uid } ?: return@mutate OrderResult.Rejected("菜品已不在账单中")
        val p = s.product(line.dishId) ?: return@mutate OrderResult.Rejected("${line.name}已下架")
        OrderRules.changeQty(o, uid, delta, p)
    }

    override suspend fun clearNew(tableId: String) {
        storeOp(event = page("OrderDraftCleared", tableId), check = { s -> if (s.table(tableId)?.status != TableStatus.Dining) "$tableId 未在用餐" else null }) {
            it.withOrder(tableId, OrderRules.clearNew(it.order(tableId)))
        }.orReject()
    }

    override suspend fun sendToKitchen(tableId: String, online: Boolean): SendResult = transaction(page("KitchenSubmitted", tableId)) { v ->
        val s = v.store ?: return@transaction v to SendResult(0, false)
        val n = s.order(tableId).pending.sumOf { it.qty }
        if (n == 0) return@transaction v to SendResult(0, false)
        val batch = newId("K-${s.id}")
        val out = if (online) s.outbox else s.outbox + OutboxItem(batch, OutboxKind.Kitchen, tableId, "$tableId 送厨 $n 份", clock())
        val sent = OrderRules.send(s.order(tableId), batch, online, clock())
        // 厨房单走本机/局域网，不依赖云端：离线也立即出单；云端待同步另由 outbox 与事件表示
        // 按档口拆任务卡：单档口卡号即批次号，多档口为「批次@档口」；各卡独立接单与制作
        val groups = sent.lines.filter { it.batchId == batch }.groupBy { KdsRules.stationFor(s, it.dishId) }
        var seq = s.kdsSeq
        val tickets = groups.entries.sortedBy { s.kdsStations.indexOf(it.key) }.map { (station, ls) ->
            KdsTicket(if (groups.size == 1) batch else "$batch@$station", tableId, sent.orderId, station, ls.map { KdsLine(it.uid, it.name, it.spec, it.note, it.qty) },
                clock(), seq = ++seq, updatedAt = clock(), note = sent.note)
        }
        // 出单方式含厨房屏的档口：同时生成厨房屏送达记录（回执即送达，超时未收到为失败）
        val screens = tickets.filter { DeliveryRules.modeOf(s, it.station) != DeliveryMode.Paper }.map { t ->
            KitchenDelivery(t.id, "S-${t.id}", tableId, sent.orderId, "$tableId 厨房屏·${t.station} ${t.lines.sumOf { it.qty }} 份", target = "${t.station}厨房屏",
                source = "screen", createdAt = clock(), updatedAt = clock(), channel = "screen", station = t.station)
        }
        val next = s.withOrder(tableId, sent).copy(outbox = out, seq = s.seq + 1, kdsTickets = s.kdsTickets + tickets, kdsSeq = seq,
            kitchenDeliveries = s.kitchenDeliveries + screens).kitchenJob(tableId, batch)
        v.copy(stores = v.stores + (s.id to next)) to SendResult(n, !online)
    }

    override suspend fun advance(tableId: String, uid: Long, to: LineStatus) {
        val type = if (to == LineStatus.Rushing) "DishRushed" else "DishServed"
        storeOp(event = page(type, tableId), check = { s -> if (s.table(tableId)?.status != TableStatus.Dining) "$tableId 未在用餐" else null }) {
            it.withOrder(tableId, OrderRules.advance(it.order(tableId), uid, to))
        }.orReject()
    }
    override suspend fun setMember(tableId: String, member: Boolean) {
        storeOp(event = page("OrderMemberChanged", tableId), check = { s -> if (s.table(tableId)?.status != TableStatus.Dining) "$tableId 未在用餐" else null }) {
            it.withOrder(tableId, it.order(tableId).copy(member = member))
        }.orReject()
    }

    // —— 厨房屏任务卡：退菜同步 ——
    /** 退菜同步到任务卡：退菜份数计入，全部退完则取消（版本 +1，变更序号前进）。 */
    private fun StoreData.kdsVoid(batchId: String, uid: Long, qty: Int, reason: String): StoreData {
        val t = KdsRules.ticketOf(kdsTickets, batchId, uid) ?: return this
        val ticketId = t.id
        val lines = t.lines.map { if (it.uid == uid) it.copy(voidQty = (it.voidQty + qty).coerceAtMost(it.qty)) else it }
        val cancelled = lines.all { it.active == 0 }
        val n = t.copy(lines = lines, rev = t.rev + 1, seq = kdsSeq + 1, updatedAt = clock(), state = if (cancelled) KdsState.Cancelled else t.state,
            note = if (cancelled) "全部退菜：$reason" else "部分退菜：$reason")
        return copy(kdsTickets = kdsTickets.map { if (it.id == ticketId) n else it }, kdsSeq = kdsSeq + 1)
    }
}
