package ai.neargo.fnb.pos.core.cloud

import org.junit.Assert.*
import org.junit.Test

class CloudLimitsTest {
    private val h = 3_600_000L
    private val now = 1_800_000_000_000L

    @Test fun onlyWhenActiveOfflineAndPending() {
        assertEquals(CloudLimit.None, CloudLimits.evaluate(false, false, 5, now - 100 * h, now))   // 未设置云端 / 副 POS
        assertEquals(CloudLimit.None, CloudLimits.evaluate(true, true, 5, now - 100 * h, now))     // 现在在线
        assertEquals(CloudLimit.None, CloudLimits.evaluate(true, false, 0, now - 100 * h, now))    // 没有待上传
        assertEquals(CloudLimit.None, CloudLimits.evaluate(true, false, 5, 0, now))                // 从未连上过（刚设置）
    }

    @Test fun warnAt24AndBlockAt72Hours() {
        assertEquals(CloudLimit.None, CloudLimits.evaluate(true, false, 5, now - 23 * h, now))
        val w = CloudLimits.evaluate(true, false, 5, now - 24 * h, now)
        assertTrue(w is CloudLimit.Warn); assertEquals(24L, (w as CloudLimit.Warn).hours)
        assertTrue(CloudLimits.evaluate(true, false, 5, now - 71 * h - 59 * 60_000, now) is CloudLimit.Warn)
        val b = CloudLimits.evaluate(true, false, 5, now - 72 * h, now)
        assertTrue(b is CloudLimit.Block); assertTrue((b as CloudLimit.Block).message.contains("5 条"))
    }
}
