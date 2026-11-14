package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * 打印服务（两业态共用）：打印机与档口路由（主 / 备用 / 副本）、厨房单拆档口、预结单与补打、模拟打印，
 * 网口打印网关的领取与回写（失败退避、改投备用、结果未知人工核对），厨房送达记录。只依赖公共内核 [StoreKernel]。
 */
internal class PrintService(private val k: StoreKernel) {
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

    /** 厨房单按档口拆分：单档口时任务号为 P-批次（兼容旧数据），多档口为 P-批次@档口，与厨房屏任务卡一一对应。 */
    internal fun StoreData.kitchenJob(tableId: String, batch: String): StoreData {
        val groups = order(tableId).lines.filter { it.batchId == batch }.groupBy { KdsRules.stationFor(this, it.dishId) }
        if (groups.isEmpty()) return this
        val multi = kdsStations.size > 1
        val jobs = groups.entries.sortedBy { kdsStations.indexOf(it.key) }.map { (station, lines) ->
            PrintJob(if (groups.size == 1) "P-$batch" else "P-$batch@$station", PrintKind.Kitchen,
                "$tableId 厨房单${if (multi) "·$station" else ""} ${lines.sumOf { it.qty }} 份",
                Receipts.kitchen(settings.shortName, tableId, lines, order(tableId).note, clock(), if (multi) station else ""), clock(), station = station)
        }
        return copy(printJobs = printJobs + withCopies(jobs))
    }

    /** 打印状态 → 厨房送达状态：已发送视为送达打印机（非出纸确认），未知须人工核对。 */
    private fun deliveryOf(s: PrintState, otherwise: DeliveryState) = when (s) {
        PrintState.Printed, PrintState.Sent -> DeliveryState.Delivered
        PrintState.Failed -> DeliveryState.Failed
        PrintState.Unknown -> DeliveryState.Unknown
        else -> otherwise
    }

    /**
     * 任务的真实目标打印机：已确定的目标优先（未知结果不改投备机），否则按档口路由；无则模拟打印。
     * 尚未发出的任务若目标打印机已停用/删除，改用该路由的备用打印机。
     */
    private fun StoreData.targetOf(j: PrintJob): PrinterConfig? {
        val id = j.printerId.ifBlank { KdsRules.printRoute(printRoutes, kdsStations, j) }
        if (id.isBlank()) return null
        printers.firstOrNull { it.id == id && it.enabled }?.let { return it }
        return if (j.copyOf.isBlank() && (j.state == PrintState.Pending || j.state == PrintState.Failed)) backupOf(j)?.takeIf { it.id != id } else null
    }

    /** 主任务加上路由配置的副本（每台副本打印机一张，固定发往该机；停用的打印机不出副本）。 */
    internal fun StoreData.withCopies(jobs: List<PrintJob>): List<PrintJob> = jobs + jobs.flatMap { j ->
        KdsRules.printCopies(printRoutes, printCopies, kdsStations, j).mapNotNull { id -> printers.firstOrNull { it.id == id && it.enabled } }.map { p ->
            j.copy(id = "${j.id}~${p.id}", title = "${j.title}（副本·${p.name}）", body = listOf("** 副本 **") + j.body, printerId = p.id, copyOf = j.id)
        }
    }
    internal fun StoreData.withCopies(job: PrintJob) = withCopies(listOf(job))

    private fun StoreData.backupOf(j: PrintJob): PrinterConfig? {
        val id = KdsRules.printBackup(printRoutes, printBackups, kdsStations, j)
        return printers.firstOrNull { it.id == id && it.enabled }
    }

    private fun failoverBody(j: PrintJob, from: String, to: String, why: String) =
        if (j.failoverFrom.isNotBlank()) j.body else listOf("** ${to}代打（原 $from ${why.substringBefore('：').take(16)}） **") + j.body

    internal fun StoreData.withJob(id: String, f: (PrintJob) -> PrintJob): StoreData {
        val jobs = printJobs.map { if (it.id == id) f(it) else it }
        val j = jobs.first { it.id == id }
        return copy(printJobs = jobs, kitchenDeliveries = kitchenDeliveries.map { d ->
            if (d.jobId != id || d.state == DeliveryState.ManualConfirmed) d
            else d.copy(state = deliveryOf(j.state, d.state), attempts = j.attempts, lastError = j.error, updatedAt = clock(), source = if (j.printerId.isNotBlank()) "printer" else d.source)
        })
    }

    // —— 网口打印网关（N6）：领取→锁外发送→回写；仅主 POS/单机 ——
    /** 领取下一张待发的真实打印任务并持久化为发送中（崩溃后据此判为结果未知）。 */
    suspend fun claimNextPrint(): Pair<PrintJob, PrinterConfig>? {
        if (!k.hostWritable()) return null
        return transaction { v ->
            val s = v.store ?: return@transaction v to null
            val now = clock()
            val j = s.printJobs.sortedBy { it.createdAt }.firstOrNull { (it.state == PrintState.Pending || (it.state == PrintState.Failed && it.nextAt <= now)) && s.targetOf(it) != null }
                ?: return@transaction v to null
            val p = s.targetOf(j)!!
            // 主打印机已停用/删除而改用备用：记下原打印机，便于队列显示
            val primary = j.printerId.ifBlank { KdsRules.printRoute(s.printRoutes, s.kdsStations, j) }
            val next = s.withJob(j.id) { it.copy(state = PrintState.Sending, printerId = p.id, attempts = it.attempts + 1, updatedAt = now,
                failoverFrom = if (it.failoverFrom.isBlank() && primary.isNotBlank() && primary != p.id) primary else it.failoverFrom) }
                .log(TaskLogKind.PrintAttempt, j.id, "Sending", "printer", "${p.name} ${p.host}:${p.port}")
            v.copy(stores = v.stores + (s.id to next)) to (next.printJobs.first { it.id == j.id } to p)
        }
    }

    /** 回写发送结果：未发出→失败并退避重试；已发出→已发送；中断/报错→结果未知（不自动重打）。 */
    suspend fun finishPrint(jobId: String, outcome: ai.neargo.fnb.pos.core.print.SendOutcome) {
        commit { v ->
            val s = v.store ?: return@commit v
            val j = s.printJobs.firstOrNull { it.id == jobId && it.state == PrintState.Sending } ?: return@commit v
            val now = clock()
            val (st, err) = when (outcome) {
                is ai.neargo.fnb.pos.core.print.SendOutcome.NotSent -> PrintState.Failed to outcome.reason
                is ai.neargo.fnb.pos.core.print.SendOutcome.Sent -> PrintState.Sent to (if (outcome.statusChecked) "" else "打印机未回报状态")
                is ai.neargo.fnb.pos.core.print.SendOutcome.Unknown -> PrintState.Unknown to outcome.reason
            }
            val backoff = minOf(60_000L, 2_000L shl minOf(j.attempts, 5))
            val firstFail = if (st == PrintState.Failed && j.firstFailAt == 0L) now else j.firstFailAt
            // 确定未发出且达到阈值：改投备用打印机（只改投一次；结果未知不改投，避免两台各出一张）
            val backup = if (st == PrintState.Failed && j.failoverFrom.isBlank() && j.copyOf.isBlank()) s.backupOf(j)?.takeIf { it.id != j.printerId } else null
            if (backup != null && (j.attempts >= KdsRules.FAILOVER_ATTEMPTS || now - firstFail >= KdsRules.FAILOVER_MS)) {
                val from = s.printers.firstOrNull { it.id == j.printerId }?.name ?: j.printerId
                return@commit v.copy(stores = v.stores + (s.id to s.withJob(jobId) {
                    it.copy(state = PrintState.Pending, printerId = backup.id, failoverFrom = j.printerId, firstFailAt = firstFail, nextAt = 0, updatedAt = now,
                        error = "已改投备用 ${backup.name}（$from：$err）", body = failoverBody(j, from, "备用机", err))
                }.log(TaskLogKind.PrintAttempt, jobId, "FailedOver", "printer", "$from → ${backup.name}：$err")))
            }
            v.copy(stores = v.stores + (s.id to s.withJob(jobId) { it.copy(state = st, error = err, nextAt = if (st == PrintState.Failed) now + backoff else 0, firstFailAt = firstFail, updatedAt = now) }
                .log(TaskLogKind.PrintAttempt, jobId, st.name, "printer", err)))
        }
    }

    /** 人工核对结果未知的任务：已出纸 → 记为已打印；未出纸 → 原任务作废并生成关联补打。记录人员与原因。 */
    suspend fun resolveUnknown(jobId: String, printed: Boolean, reason: String) = storeOp(check = { s ->
        val j = s.printJobs.firstOrNull { it.id == jobId }
        when {
            need(Perm.Checkout) != null -> need(Perm.Checkout)
            j == null -> "打印任务不存在"
            j.state != PrintState.Unknown -> "只有结果未知的任务需要核对"
            reason.isBlank() -> "请填写核对说明"
            else -> null
        }
    }) { s ->
        val actor = actorName(state.value)
        val j = s.printJobs.first { it.id == jobId }
        if (printed) s.withJob(jobId) { it.copy(state = PrintState.Printed, resolution = "$actor：已出纸 · ${reason.trim()}", updatedAt = clock()) }
            .log(TaskLogKind.PrintAttempt, jobId, "ConfirmedPrinted", "manual", "$actor：${reason.trim()}")
        else {
            val copy = j.copy(id = newId("RP"), title = "${j.title}（补打）", body = listOf("** 补打 **") + j.body, createdAt = clock(), state = PrintState.Pending,
                attempts = 0, reprint = true, error = "", reprintOf = j.id, resolution = "", nextAt = 0)
            val base = s.withJob(jobId) { it.copy(state = PrintState.Failed, error = "人工确认未出纸，已补打", resolution = "$actor：未出纸 · ${reason.trim()}", nextAt = Long.MAX_VALUE, updatedAt = clock()) }
            base.copy(printJobs = base.printJobs + copy, kitchenDeliveries = base.kitchenDeliveries.map { if (it.jobId == jobId) it.copy(supersededBy = copy.id) else it })
                .log(TaskLogKind.PrintReprinted, copy.id, "queued", "manual", "原任务 $jobId 未出纸：$actor")
        }
    }

    suspend fun savePrinter(p: PrinterConfig) = storeOp(check = { s ->
        when {
            need(Perm.Settings) != null -> need(Perm.Settings)
            p.name.isBlank() -> "请填写打印机名称"
            !Regex("^[A-Za-z0-9.-]{1,253}$").matches(p.host.trim()) -> "请填写打印机 IP 或主机名"
            p.port !in 1..65535 -> "端口应为 1–65535"
            p.width !in setOf(32, 48) -> "纸宽应为 58mm（32 列）或 80mm（48 列）"
            s.printers.any { it.id != p.id && it.name == p.name.trim() } -> "打印机名称重复"
            else -> null
        }
    }) { s -> val q = p.copy(name = p.name.trim(), host = p.host.trim()); s.copy(printers = s.printers.filterNot { it.id == q.id } + q) }

    suspend fun removePrinter(id: String) = storeOp(check = { s ->
        need(Perm.Settings) ?: if (s.printJobs.any { it.printerId == id && it.state in setOf(PrintState.Sending, PrintState.Unknown) }) "该打印机有发送中或结果未知的任务，请先核对" else null
    }) { s -> s.copy(printers = s.printers.filterNot { it.id == id }, printRoutes = s.printRoutes.filterValues { it != id }, printBackups = s.printBackups.filterValues { it != id },
        printCopies = s.printCopies.mapValues { (_, l) -> l - id }.filterValues { it.isNotEmpty() }) }

    suspend fun setRoute(station: String, printerId: String?) = storeOp(check = { s ->
        need(Perm.Settings) ?: if (printerId != null && s.printers.none { it.id == printerId }) "打印机不存在" else null
    }) { s -> s.copy(printRoutes = if (printerId == null) s.printRoutes - station else s.printRoutes + (station to printerId)) }

    /** 路由的备用打印机（门店设置权限）；不能与主打印机相同。 */
    suspend fun setBackup(station: String, printerId: String?) = storeOp(check = { s ->
        need(Perm.Settings) ?: when {
            printerId == null -> null
            s.printers.none { it.id == printerId } -> "打印机不存在"
            s.printRoutes[station] == printerId -> "备用打印机不能与主打印机相同"
            else -> null
        }
    }) { s -> s.copy(printBackups = if (printerId == null) s.printBackups - station else s.printBackups + (station to printerId)) }

    /** 路由的副本打印机（门店设置权限）：最多 3 台，不能含主打印机。 */
    suspend fun setCopies(station: String, printerIds: List<String>) = storeOp(check = { s ->
        need(Perm.Settings) ?: when {
            printerIds.size > KdsRules.MAX_COPIES -> "副本打印机最多 ${KdsRules.MAX_COPIES} 台"
            printerIds.any { id -> s.printers.none { it.id == id } } -> "打印机不存在"
            s.printRoutes[station] != null && s.printRoutes[station] in printerIds -> "副本打印机不能与主打印机相同"
            else -> null
        }
    }) { s -> s.copy(printCopies = if (printerIds.isEmpty()) s.printCopies - station else s.printCopies + (station to printerIds.distinct())) }

    /** 手动改投：失败或排队中的任务改由指定打印机打印（结果未知须先核对）。 */
    suspend fun redirectPrint(jobId: String, printerId: String) = storeOp(check = { s ->
        val j = s.printJobs.firstOrNull { it.id == jobId }
        val p = s.printers.firstOrNull { it.id == printerId }
        when {
            need(Perm.Checkout) != null -> need(Perm.Checkout)
            j == null -> "打印任务不存在"
            j.state == PrintState.Unknown -> "结果未知的任务请先核对，避免重复出单"
            j.state != PrintState.Failed && j.state != PrintState.Pending -> "只有失败或排队中的任务可以改投"
            p == null || !p.enabled -> "目标打印机不存在或已停用"
            s.targetOf(j)?.id == printerId -> "已经是这台打印机"
            else -> null
        }
    }) { s ->
        val j = s.printJobs.first { it.id == jobId }
        val p = s.printers.first { it.id == printerId }
        val from = s.targetOf(j)?.name ?: "模拟打印"
        s.withJob(jobId) {
            it.copy(state = PrintState.Pending, printerId = p.id, failoverFrom = j.printerId.ifBlank { "manual" }, nextAt = 0, error = "已手动改投 ${p.name}", updatedAt = clock(),
                body = failoverBody(j, from, p.name, "手动改投"))
        }.log(TaskLogKind.PrintAttempt, jobId, "Redirected", "manual", "$from → ${p.name}：${actorName(state.value)}")
    }

    suspend fun testPrint(printerId: String) = storeOp(check = { s -> need(Perm.Settings) ?: if (s.printers.none { it.id == printerId }) "打印机不存在" else null }) { s ->
        val p = s.printers.first { it.id == printerId }
        val w = p.width
        val body = listOf("打印测试", "-".repeat(w), "打印机：${p.name}", "地址：${p.host}:${p.port}", "纸宽：${if (w == 32) "58mm" else "80mm"}", "中文：门店收银 · 中文与金额 ¥48.00", "-".repeat(w),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(clock())))
        s.copy(printJobs = s.printJobs + PrintJob(newId("T"), PrintKind.Test, "测试页 ${p.name}", body, clock(), printerId = p.id))
    }

    /** 为缺少送达记录的厨房任务补记录；状态取自任务当前结果。 */
    internal fun ensureDeliveries(s: StoreData, source: String): StoreData {
        val known = s.kitchenDeliveries.map { it.jobId }.toSet()
        val missing = s.printJobs.filter { it.kind == PrintKind.Kitchen && it.id !in known && it.copyOf.isBlank() }
        if (missing.isEmpty()) return s
        return s.copy(kitchenDeliveries = s.kitchenDeliveries + missing.map { j ->
            val table = j.title.substringBefore(' ')
            // 补打沿用原单所属的任务（ticketId）与替代关系，汇总时归到同一张卡
            val origin = s.kitchenDeliveries.firstOrNull { it.jobId == j.reprintOf && j.reprintOf.isNotBlank() }
            KitchenDelivery(
                ticketId = origin?.ticketId ?: j.id.removePrefix("P-"), jobId = j.id, tableId = table, orderId = origin?.orderId ?: s.orders[table]?.orderId.orEmpty(), label = j.title,
                station = j.station, covers = origin?.covers.orEmpty(),
                target = if (j.station.isNotBlank() && s.kdsStations.size > 1) "${j.station}打印机" else "厨房打印机",
                state = deliveryOf(j.state, DeliveryState.Pending),
                source = source, attempts = j.attempts, lastError = j.error, createdAt = j.createdAt, updatedAt = clock(),
            )
        })
    }

    val deliveries: StateFlow<List<KitchenDelivery>> = k.derive { it.store?.kitchenDeliveries.orEmpty().sortedByDescending { d -> d.createdAt } }
    val deliveryGroups: StateFlow<List<TicketDelivery>> = k.derive { v -> v.store?.let { DeliveryRules.groups(it) }.orEmpty() }

    /** 人工确认送达：只用于失败/未知/待出单，必须填写原因，记录人员。 */
    suspend fun confirmDelivery(ticketId: String, reason: String) = storeOp(check = { s ->
        val open = s.kitchenDeliveries.filter { it.ticketId == ticketId && it.supersededBy.isBlank() && it.state != DeliveryState.Delivered && it.state != DeliveryState.ManualConfirmed }
        when {
            s.kitchenDeliveries.none { it.ticketId == ticketId } -> "送达记录不存在"
            open.isEmpty() -> "已送达，无需确认"
            reason.isBlank() -> "请填写确认原因"
            reason.length > 60 -> "原因最多 60 字"
            else -> null
        }
    }) { s ->
        val actor = actorName(state.value)
        s.copy(kitchenDeliveries = s.kitchenDeliveries.map {
            if (it.ticketId == ticketId && it.supersededBy.isBlank() && it.state != DeliveryState.Delivered && it.state != DeliveryState.ManualConfirmed) it.copy(state = DeliveryState.ManualConfirmed, source = "manual", confirmedBy = actor, reason = reason.trim(), updatedAt = clock()) else it
        }).log(TaskLogKind.PrintAttempt, ticketId, "ManualConfirmed", "manual", "${actor}：${reason.trim()}")
    }

    suspend fun preBill(tableId: String) = storeOp(check = { s ->
        if (s.order(tableId).lines.isEmpty()) "$tableId 没有菜品" else null
    }) { s ->
        val job = PrintJob(newId("B"), PrintKind.PreBill, "$tableId 预结单", Receipts.bill("预结单", s, tableId, s.order(tableId), clock()), clock())
        s.copy(printJobs = s.printJobs + s.withCopies(job)).log(TaskLogKind.PrintRequested, job.id, "queued", "local", tableId)
    }

    suspend fun reprint(jobId: String) = storeOp(check = { s -> if (s.printJobs.none { it.id == jobId }) "打印任务不存在" else null }) { s ->
        val j = s.printJobs.first { it.id == jobId }
        val copy = j.copy(id = newId("RP"), title = "${j.title}（补打）", body = listOf("** 补打 **") + j.body, createdAt = clock(), state = PrintState.Pending, attempts = 0, reprint = true, error = "",
            reprintOf = j.id, resolution = "", nextAt = 0, printerId = "")
        s.copy(printJobs = s.printJobs + copy).log(TaskLogKind.PrintReprinted, copy.id, "queued", "local", "原任务 ${j.id}")
    }

    suspend fun process(kitchenOk: Boolean): Int {
        if (!k.hostWritable()) return 0
        var failed = 0
        val s = currentStore() ?: return 0
        if (s.printJobs.none { (it.state == PrintState.Pending || it.state == PrintState.Failed) && s.targetOf(it) == null }) return 0
        storeOp { st ->
            var logged = st
            val jobs = st.printJobs.map { j ->
                when {
                    // 有真实打印机的任务由网关处理；已有结果的任务不再模拟
                    st.targetOf(j) != null || j.state !in setOf(PrintState.Pending, PrintState.Failed) -> j
                    j.state == PrintState.Printed -> j
                    j.kind.printer == "厨房" && !kitchenOk -> {
                        if (j.state == PrintState.Pending) failed++
                        j.copy(state = PrintState.Failed, attempts = j.attempts + if (j.state == PrintState.Pending) 1 else 0, error = "厨房打印机缺纸")
                    }
                    else -> j.copy(state = PrintState.Printed, attempts = j.attempts + 1, error = "")
                }.also { n -> if (n != j && n.state != j.state) logged = logged.log(TaskLogKind.PrintAttempt, j.id, n.state.name, "simulation", n.error) }
            }
            val byJob = jobs.associateBy { it.id }
            logged.copy(printJobs = jobs, kitchenDeliveries = logged.kitchenDeliveries.map { d ->
                val j = byJob[d.jobId]
                if (j == null || d.state == DeliveryState.ManualConfirmed) d
                else {
                    val st = deliveryOf(j.state, d.state)
                    if (st == d.state && j.attempts == d.attempts) d else d.copy(state = st, attempts = j.attempts, lastError = j.error, updatedAt = clock(), source = "simulation")
                }
            })
        }.getOrThrow()
        return failed
    }

    fun billPreview(tableId: String): List<String> {
        val s = currentStore() ?: return emptyList()
        return Receipts.bill("预结单预览", s, tableId, s.order(tableId), clock())
    }
}
