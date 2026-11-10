package vn.tvlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Kịch bản NHIỀU BƯỚC cho phần dư thời gian (tick mỗi 1 giây như GuardService).
 * Ghim lỗi 10/11: một remainder dùng chung cho mọi app khiến
 *  - 59 giây xem app `free` bị tính vào giới hạn ngày;
 *  - đổi app đúng nhịp (59s tính giờ + 1s app `free`) né được giới hạn ngày;
 *  - số phút từng app bị gán sai app (giới hạn riêng từng app né được).
 * KHÔNG sửa bằng cách "xóa phần dư khi đổi app": đổi app mỗi 59 giây sẽ không bao giờ
 * tích đủ 1 phút (xem [alternatingEvery59SecondsStillCountsAlmostAllTime]).
 */
class RemainderScenarioTest {

    companion object {
        const val YT = "com.google.android.youtube.tv"
        const val NF = "com.netflix.ninja"
        const val T3 = "app.three"
        const val FREE = "ar.edu.free.app"
    }

    private class Sim(val limit: Int) {
        var t = 1_000_000L
        var rem = 0L
        var used = 0
        var bonus = 0
        val appRem = HashMap<String, Long>()
        val perApp = HashMap<String, Int>()

        fun watch(pkg: String, seconds: Int, free: Boolean = false) {
            repeat(seconds) {
                val r = LockPolicy.addElapsed(LockPolicy.CountInput(
                    pkg = pkg, nowMs = t + 1000, lastPkgStartMs = t, remainderMs = rem,
                    appRemainderMs = appRem[pkg] ?: 0L, usedTodayMin = used, bonusMin = bonus,
                    dailyLimitMin = limit, appRuleMode = if (free) "free" else ""))
                rem = r.remainderMs
                appRem[pkg] = r.appRemainderMs
                used = r.usedTodayMin
                bonus = r.bonusMin
                if (r.perAppAddMin > 0) perApp[pkg] = (perApp[pkg] ?: 0) + r.perAppAddMin
                t += 1000
            }
        }
        fun app(p: String) = perApp[p] ?: 0
    }

    @Test fun freeAppSecondsNeverBecomeDailyMinutes() {
        val s = Sim(9999)
        s.watch(FREE, 59, free = true)
        s.watch(YT, 1)
        assertEquals(0, s.used)
        assertEquals(0, s.app(YT))
    }

    @Test fun cannotBypassDailyLimitByHoppingToFreeAppEveryMinute() {
        val s = Sim(9999)
        repeat(60) { s.watch(YT, 59); s.watch(FREE, 1, free = true) }
        assertEquals("xem thật 59 phút phải tính 59 phút ngày", 59, s.used)
        assertEquals(59, s.app(YT))
        assertEquals(1, s.app(FREE))
    }

    @Test fun alternatingTwoMeteredAppsAttributesMinutesCorrectly() {
        val s = Sim(9999)
        repeat(30) { s.watch(YT, 30); s.watch(NF, 30) }
        assertEquals(30, s.used)
        assertEquals(15, s.app(YT))
        assertEquals(15, s.app(NF))
    }

    @Test fun alternatingEvery59SecondsStillCountsAlmostAllTime() {
        val s = Sim(9999)
        repeat(30) { s.watch(YT, 59); s.watch(NF, 59) }
        val totalMin = 60 * 59 / 60
        assertTrue("daily=${s.used}", Math.abs(s.used - totalMin) <= 1)
        assertTrue(s.app(YT) >= 28 && s.app(NF) >= 28)
    }

    @Test fun randomViewingSequencesMatchExactSecondTotals() {
        val rnd = Random(2026)
        repeat(300) {
            val s = Sim(1_000_000)
            val sec = HashMap<String, Int>()
            var meteredSec = 0
            repeat(rnd.nextInt(40) + 10) {
                val p = listOf(YT, NF, T3, FREE)[rnd.nextInt(4)]
                val n = rnd.nextInt(120) + 1
                s.watch(p, n, free = p == FREE)
                sec[p] = (sec[p] ?: 0) + n
                if (p != FREE) meteredSec += n
            }
            assertEquals(meteredSec / 60, s.used)
            for ((p, n) in sec) assertEquals("app $p", n / 60, s.app(p))
        }
    }

    @Test fun dailyLimitStillEnforcedAfterSplit() {
        val s = Sim(10)
        s.watch(YT, 15 * 60)
        assertTrue("used=${s.used}", s.used >= 10)
        assertTrue(s.bonus >= 0)
    }
}
