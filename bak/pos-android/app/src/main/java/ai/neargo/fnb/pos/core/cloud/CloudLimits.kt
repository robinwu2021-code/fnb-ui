package ai.neargo.fnb.pos.core.cloud

/**
 * 离线时限（切换方案 §5，按后端评审修正）：
 * 量的是「云端连不上多久」，不是「最早一条事件多老」——等后端接口的事件（结账、交班…）按设计一直留在本机，
 * 按事件年龄算会让网络正常的门店也被挡住日结。
 * - 只在已设置云端、本机是主 POS / 单机、且有待上传事件时生效；
 * - 连不上超过 24 小时：页头告警（平台幂等记录只承诺保留 24 小时，越久补传越可能出现结果未知）；
 * - 超过 72 小时：暂停日结，联网补传后恢复。
 */
sealed interface CloudLimit {
    data object None : CloudLimit
    data class Warn(val hours: Long, val message: String) : CloudLimit
    data class Block(val hours: Long, val message: String) : CloudLimit
}

object CloudLimits {
    const val WARN_MS = 24 * 3_600_000L
    const val BLOCK_MS = 72 * 3_600_000L

    fun evaluate(active: Boolean, online: Boolean, pending: Int, lastOnlineAt: Long, now: Long): CloudLimit {
        if (!active || online || pending <= 0 || lastOnlineAt <= 0) return CloudLimit.None
        val age = now - lastOnlineAt
        val h = age / 3_600_000L
        return when {
            age >= BLOCK_MS -> CloudLimit.Block(h, "云端已 $h 小时连不上（超过 72 小时）：日结暂停，请联网把 $pending 条事件补传后再日结")
            age >= WARN_MS -> CloudLimit.Warn(h, "云端已 $h 小时连不上：请尽快联网补传；超过 72 小时将暂停日结")
            else -> CloudLimit.None
        }
    }
}
