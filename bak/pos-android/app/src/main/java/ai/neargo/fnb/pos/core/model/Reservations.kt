package ai.neargo.fnb.pos.core.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
enum class ReservationStatus(val label: String) { Booked("待到店"), Cancelled("已取消"), Arrived("已到店") }

@Serializable
data class Reservation(val id: String, val tableId: String, val name: String, val phone: String,
    val guests: Int, val arrivesAt: Long, val note: String = "", val status: ReservationStatus = ReservationStatus.Booked,
    val createdAt: Long = 0, val updatedAt: Long = 0)

/** A booking holds its table until cancelled or checked in; double bookings are rejected. */
object ReservationRules {
    fun importLegacy(store: StoreData, now: Long): StoreData {
        val records = store.tables.filter { t -> t.status == TableStatus.Reserved && store.reservations.none { it.tableId == t.id && it.status == ReservationStatus.Booked } }.map { t ->
            val text = t.reservation
            val time = parse(text) ?: parse(format(now).take(10) + " " + text) ?: now + 3_600_000
            Reservation("legacy-${t.id}", t.id, "历史预留（待补充联系人）", "", t.seats, time, "由旧版桌台预留迁入", createdAt = now)
        }
        return if (records.isEmpty()) store else store.copy(reservations = store.reservations + records)
    }
    fun format(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(java.util.Date(time))
    fun parse(value: String): Long? = runCatching {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { isLenient = false }
        val position = java.text.ParsePosition(0)
        val date = f.parse(value.trim(), position)
        if (position.index == value.trim().length && date != null && f.format(date) == value.trim()) date.time else null
    }.getOrNull()
    fun validate(table: DiningTable?, bookings: List<Reservation>, name: String, phone: String, guests: Int, at: Long, now: Long): String? = when {
        table == null -> "桌台不存在"
        table.status != TableStatus.Idle || bookings.any { it.tableId == table.id && it.status == ReservationStatus.Booked } -> "桌台已占用，请选择空闲桌台"
        name.trim().isEmpty() || name.trim().length > 40 -> "联系人应为 1–40 字"
        phone.trim().length !in 6..24 || phone.count { it.isDigit() } < 6 || phone.trim().any { !it.isDigit() && it !in "+ -()" } -> "请输入有效联系电话"
        guests !in 1..table.seats -> "预订人数超过桌台座位数"
        at <= now -> "预订时间必须晚于当前时间"
        at - now > 366L * 24 * 60 * 60 * 1000 -> "预订时间不能超过一年"
        else -> null
    }
}
