package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * 厨房屏服务（餐饮）：主机权威任务卡（接单 / 制作 / 完成 / 释放 / 指派）、档口与出单方式配置、送达回执与超时、
 * 厨房屏未收到改打纸单；厨房屏设备侧的持久收件箱。只依赖公共内核 [StoreKernel]。
 */
internal class KdsService(private val k: StoreKernel) : KdsRepository {
    private val state get() = k.vault
    private fun clock() = k.clock()
    private fun newId(prefix: String) = k.newId(prefix)
    private fun actorName(v: Vault) = k.actorName(v)
    private fun need(perm: Perm) = k.need(perm)
    private fun fail(msg: String) = Result.failure<Unit>(IllegalStateException(msg))
    private fun currentStore(): StoreData? = state.value.store
    private suspend fun storeOp(event: LocalWorkspace.EventSpec? = null, check: (StoreData) -> String? = { null }, f: (StoreData) -> StoreData) = k.storeOp(event, check, f)
    private suspend fun <T> transaction(event: LocalWorkspace.EventSpec? = null, f: (Vault) -> Pair<Vault, T>): T = k.transaction(event, f)
    private suspend fun commit(event: LocalWorkspace.EventSpec? = null, f: (Vault) -> Vault): Vault = transaction(event) { v -> val n = f(v); n to n }
    private fun EventSpec(resolve: (StoreData, StoreData) -> Triple<String, String, List<String>>) = LocalWorkspace.EventSpec(resolve)
    private fun StoreData.log(kind: TaskLogKind, refId: String, result: String, source: String, detail: String = "") = k.taskLog(this, kind, refId, result, source, detail)


    /** 厨师操作（接单/开始制作/完成/撤回完成）：须带所见版本；单据已变（含退菜取消）则拒绝，迟到命令不能覆盖取消。 */
    override suspend fun kdsAdvance(ticketId: String, expectedRev: Int, to: KdsState): Result<Unit> {
        val tableId = currentStore()?.kdsTickets?.firstOrNull { it.id == ticketId }?.tableId ?: return fail("任务卡不存在")
        val origin = kotlinx.coroutines.currentCoroutineContext()[RemoteOrigin]
        val actor = origin?.let { o -> currentStore()?.employees?.firstOrNull { it.id == o.actorId }?.name ?: "厨房屏" } ?: actorName(state.value)
        return storeOp(event = EventSpec { _, a ->
            val t = a.kdsTickets.first { it.id == ticketId }
            Triple("KdsTicketAdvanced", kotlinx.serialization.json.Json.encodeToString(KdsTicket.serializer(), t), listOf("kds:$ticketId", "table:$tableId"))
        }, check = { s ->
            val t = s.kdsTickets.firstOrNull { it.id == ticketId }
            when {
                t == null -> "任务卡不存在"
                t.rev != expectedRev -> if (t.state == KdsState.Cancelled) "该单已取消（退菜），不能再操作" else "单据已更新（退菜或其他工位操作），请刷新后再试"
                else -> KdsRules.ownerCheck(KdsRules.dispatchOf(s, t.station), t, deviceOf(origin)) ?: KdsRules.transition(t.state, to)
            }
        }) { s ->
            s.copy(kdsTickets = s.kdsTickets.map {
                if (it.id != ticketId) it
                else {
                    // 分派模式：第一块操作的屏成为负责屏
                    val claim = KdsRules.dispatchOf(s, it.station) == KdsDispatch.Claim && it.owner.isBlank()
                    it.copy(state = to, rev = it.rev + 1, seq = s.kdsSeq + 1, updatedAt = clock(), by = actor,
                        owner = if (claim) deviceOf(origin) else it.owner, ownerName = if (claim) deviceName(deviceOf(origin)) else it.ownerName)
                }
            }, kdsSeq = s.kdsSeq + 1)
        }
    }

    private fun deviceOf(origin: RemoteOrigin?) = origin?.deviceId ?: state.value.deviceId
    private fun deviceName(id: String) = if (id == state.value.deviceId) "主 POS" else state.value.lan.paired.firstOrNull { it.deviceId == id }?.name ?: "厨房屏"

    /** 负责屏释放：卡回到待接单，其他屏可以接（已完成/已取消的卡不能释放）。 */
    override suspend fun kdsRelease(ticketId: String, expectedRev: Int): Result<Unit> {
        val tableId = currentStore()?.kdsTickets?.firstOrNull { it.id == ticketId }?.tableId ?: return fail("任务卡不存在")
        val origin = kotlinx.coroutines.currentCoroutineContext()[RemoteOrigin]
        return storeOp(event = EventSpec { _, a ->
            Triple("KdsTicketReleased", kotlinx.serialization.json.Json.encodeToString(KdsTicket.serializer(), a.kdsTickets.first { it.id == ticketId }), listOf("kds:$ticketId", "table:$tableId"))
        }, check = { s ->
            val t = s.kdsTickets.firstOrNull { it.id == ticketId }
            when {
                t == null -> "任务卡不存在"
                t.rev != expectedRev -> "单据已更新，请刷新后再试"
                t.owner.isBlank() -> "这张卡还没有负责屏"
                t.owner != deviceOf(origin) -> "只有负责屏可以释放"
                t.state == KdsState.Done || t.state == KdsState.Cancelled -> "已完成或已取消的卡不能释放"
                else -> null
            }
        }) { s -> s.reassignKds(ticketId, "", "") }
    }

    /** 主管收回或指派负责屏（仅主机）：收回后卡回到待接单；指派给已配对的设备。 */
    override suspend fun kdsReassign(ticketId: String, deviceId: String?): Result<Unit> {
        val tableId = currentStore()?.kdsTickets?.firstOrNull { it.id == ticketId }?.tableId ?: return fail("任务卡不存在")
        return storeOp(event = EventSpec { _, a ->
            Triple("KdsTicketReassigned", kotlinx.serialization.json.Json.encodeToString(KdsTicket.serializer(), a.kdsTickets.first { it.id == ticketId }), listOf("kds:$ticketId", "table:$tableId"))
        }, check = { s ->
            val t = s.kdsTickets.firstOrNull { it.id == ticketId }
            when {
                need(Perm.Adjust) != null -> need(Perm.Adjust)
                state.value.lan.role == LanRole.Secondary -> "只能在主 POS 收回或指派"
                t == null -> "任务卡不存在"
                t.state == KdsState.Done || t.state == KdsState.Cancelled -> "已完成或已取消的卡不需要收回"
                deviceId != null && state.value.lan.paired.none { it.deviceId == deviceId && it.revokedAt == 0L } -> "设备不存在或已撤销"
                deviceId == null && t.owner.isBlank() -> "这张卡还没有负责屏"
                else -> null
            }
        }) { s -> s.reassignKds(ticketId, deviceId.orEmpty(), deviceId?.let { deviceName(it) }.orEmpty()) }
    }

    private fun StoreData.reassignKds(ticketId: String, owner: String, name: String) = copy(kdsTickets = kdsTickets.map {
        if (it.id != ticketId) it
        else it.copy(owner = owner, ownerName = name, state = if (owner.isBlank()) KdsState.Pending else it.state, rev = it.rev + 1, seq = kdsSeq + 1, updatedAt = clock())
    }, kdsSeq = kdsSeq + 1)

    /** 档口分派方式（门店设置权限）。 */
    override suspend fun saveDispatchModes(modes: Map<String, KdsDispatch>) = storeOp(event = EventSpec { _, a ->
        Triple("KitchenDispatchSaved", kotlinx.serialization.json.Json.encodeToString(KitchenDispatchPayload.serializer(), KitchenDispatchPayload(a.stationDispatch)), listOf(Aggregates.CATALOG))
    }, check = { s -> if (modes.keys.any { it !in s.kdsStations }) "档口不存在" else null }) { s ->
        s.copy(stationDispatch = modes.filterValues { it != KdsDispatch.Sync })
    }

    /** 厨房屏增量拉取：返回 seq 大于游标的任务卡及新游标。 */
    fun kdsPull(storeId: String, cursor: Long, station: String): Pair<List<KdsTicket>, Long> {
        val s = state.value.stores[storeId] ?: return emptyList<KdsTicket>() to cursor
        val list = s.kdsTickets.filter { it.seq > cursor && (station.isBlank() || it.station == station) }.sortedBy { it.seq }.take(200)
        return list to (list.maxOfOrNull { it.seq } ?: cursor)
    }

    /** 厨房屏持久接收回执：记录送达（与厨师接单无关）。 */
    suspend fun kdsAck(acks: List<Pair<String, Int>>) {
        commit { v ->
            val s = v.store ?: return@commit v
            val now = clock()
            val map = acks.toMap()
            if (s.kdsTickets.none { (map[it.id] ?: 0) > it.deliveredRev }) return@commit v
            v.copy(stores = v.stores + (s.id to s.copy(kdsTickets = s.kdsTickets.map { t ->
                val r = map[t.id] ?: 0
                if (r > t.deliveredRev) t.copy(deliveredRev = r, deliveredAt = if (t.deliveredAt == 0L) now else t.deliveredAt) else t
            }, kitchenDeliveries = s.kitchenDeliveries.map { d ->
                // 厨房屏迟到收到也自动解除「未收到」
                if (d.channel == "screen" && (map[d.ticketId] ?: 0) > 0 && d.state != DeliveryState.Delivered && d.state != DeliveryState.ManualConfirmed)
                    d.copy(state = DeliveryState.Delivered, lastError = "", updatedAt = now) else d
            })))
        }
    }

    /** 厨房屏超过时限仍未收到：送达记录记为失败（主机/单机执行；迟到回执会自动解除）。返回本次标记数。 */
    suspend fun expireScreenDeliveries(): Int {
        if (!k.hostWritable()) return 0
        var n = 0
        commit { v ->
            val s = v.store ?: return@commit v
            val now = clock()
            val delivered = s.kdsTickets.filter { it.deliveredRev > 0 || it.state == KdsState.Cancelled }.map { it.id }.toSet()
            val due = s.kitchenDeliveries.filter { it.channel == "screen" && it.state == DeliveryState.Pending && now - it.createdAt >= DeliveryRules.SCREEN_TIMEOUT_MS }
            if (due.isEmpty()) return@commit v
            v.copy(stores = v.stores + (s.id to s.copy(kitchenDeliveries = s.kitchenDeliveries.map { d ->
                if (d !in due) d
                else if (d.ticketId in delivered) d.copy(state = DeliveryState.Delivered, updatedAt = now)
                else { n++; d.copy(state = DeliveryState.Failed, lastError = "${DeliveryRules.SCREEN_TIMEOUT_MS / 1000} 秒内没有${d.station}厨房屏收到", updatedAt = now) }
            })))
        }
        return n
    }

    /** 档口出单方式（门店设置权限），只影响之后送厨的单。 */
    override suspend fun saveDeliveryModes(modes: Map<String, DeliveryMode>) = storeOp(event = EventSpec { _, a ->
        Triple("KitchenDeliveryPolicySaved", kotlinx.serialization.json.Json.encodeToString(KitchenDeliveryPolicyPayload.serializer(), KitchenDeliveryPolicyPayload(a.stationDelivery)), listOf(Aggregates.CATALOG))
    }, check = { s -> if (modes.keys.any { it !in s.kdsStations }) "档口不存在" else null }) { s ->
        s.copy(stationDelivery = modes.filterValues { it != DeliveryMode.Paper })
    }

    /** 厨房屏未收到：改打一张纸单补上，送达后视为厨房屏已满足。 */
    suspend fun printForScreen(ticketId: String) = storeOp(check = { s ->
        val g = DeliveryRules.groups(s).firstOrNull { it.ticketId == ticketId }
        when {
            need(Perm.Checkout) != null -> need(Perm.Checkout)
            g == null -> "送达记录不存在"
            !g.canFallback -> "该单不需要改打纸单"
            s.kdsTickets.none { it.id == ticketId } -> "任务卡不存在"
            else -> null
        }
    }) { s ->
        val g = DeliveryRules.groups(s).first { it.ticketId == ticketId }
        val t = s.kdsTickets.first { it.id == ticketId }
        val lines = s.order(t.tableId).lines.filter { l -> t.lines.any { it.uid == l.uid && it.active > 0 } }
            .map { l -> l.copy(qty = t.lines.first { it.uid == l.uid }.active) }
        val job = PrintJob(newId("FB"), PrintKind.Kitchen, "${t.tableId} 厨房单·${t.station}（屏未收到改打）",
            listOf("** 厨房屏未收到，改打纸单 **") + Receipts.kitchen(s.settings.shortName, t.tableId, lines, t.note, clock(), t.station), clock(), station = t.station)
        val d = KitchenDelivery(ticketId, job.id, t.tableId, t.orderId, job.title, target = "${t.station}打印机", createdAt = clock(), updatedAt = clock(),
            station = t.station, covers = "screen")
        s.copy(printJobs = s.printJobs + job, kitchenDeliveries = s.kitchenDeliveries.map { if (it.jobId == g.fallback?.jobId) it.copy(supersededBy = job.id) else it } + d)
            .log(TaskLogKind.PrintRequested, job.id, "queued", "manual", "厨房屏未收到改打 $ticketId")
    }

    /** 厨房档口配置（门店设置权限）。 */
    override suspend fun saveKdsStations(stations: List<String>, mapping: Map<String, String>) = storeOp(event = EventSpec { _, a ->
        Triple("KitchenStationsSaved", kotlinx.serialization.json.Json.encodeToString(KitchenStationsPayload.serializer(), KitchenStationsPayload(a.kdsStations, a.stationOf)), listOf(Aggregates.CATALOG))
    }, check = { s -> KdsRules.validateStations(stations.map { it.trim() }, mapping) ?: if (mapping.keys.any { k -> s.categories.none { it.id == k } }) "分类不存在" else null }) { s ->
        s.copy(kdsStations = stations.map { it.trim() }, stationOf = mapping)
    }

    // —— 厨房屏设备侧：持久收件箱 ——
    val kdsDevice: StateFlow<Triple<String, List<KdsTicket>, Long>> = k.derive { Triple(it.kdsStation, it.kdsInbox, it.kdsCursor) }

    /** 设置/切换本机档口：档口变化时清空收件箱并从头拉取本档口任务。 */
    suspend fun setKdsStation(station: String) {
        commit { v -> val st = station.trim(); if (st == v.kdsStation) v else v.copy(kdsStation = st, kdsInbox = emptyList(), kdsCursor = 0) }
    }

    /** 收到任务卡：去重合并后落盘，返回需回执的（id, rev）。 */
    suspend fun kdsReceive(tickets: List<KdsTicket>, cursor: Long): List<Pair<String, Int>> {
        var accepted = emptyList<KdsTicket>()
        commit { v ->
            val (merged, acc) = KdsRules.merge(v.kdsInbox, tickets)
            accepted = acc
            // 只保留未完结或最近 2 小时内更新的卡，避免收件箱无限增长
            val keep = merged.filter { it.state !in setOf(KdsState.Done, KdsState.Cancelled) || clock() - it.updatedAt < 2 * 3_600_000L }
            v.copy(kdsInbox = keep, kdsCursor = maxOf(v.kdsCursor, cursor))
        }
        return accepted.map { it.id to it.rev }
    }
}
