package vn.tvlock

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainderAttributionTest {
    private fun count(
        pkg: String, elapsedMs: Long, dailyRemainder: Long, appRemainder: Long,
        free: Boolean = false
    ) = LockPolicy.addElapsed(LockPolicy.CountInput(
        pkg = pkg, nowMs = 120_000L + elapsedMs, lastPkgStartMs = 120_000L,
        remainderMs = dailyRemainder, appRemainderMs = appRemainder,
        usedTodayMin = 0, bonusMin = 0, dailyLimitMin = 90,
        appRuleMode = if (free) "free" else ""
    ))

    @Test fun freeAppRemainderDoesNotLeakIntoDailyCounter() {
        val free = count("test.free", 59_000L, 0L, 0L, free = true)
        val youtube = count("com.google.android.youtube.tv", 1_000L,
            free.remainderMs, 0L)
        assertEquals(0, youtube.usedTodayMin)
        assertEquals(0, youtube.countedMin)
        assertEquals(1_000L, youtube.remainderMs)
        assertEquals(59_000L, free.appRemainderMs)
    }

    @Test fun meteredAppRemaindersStayWithTheirPackage() {
        val youtube = count("youtube", 30_000L, 0L, 0L)
        val netflix = count("netflix", 30_000L, youtube.remainderMs,
            0L)
        assertEquals(30_000L, youtube.appRemainderMs)
        assertEquals(30_000L, netflix.appRemainderMs)
        assertEquals(1, netflix.countedMin)
        assertEquals(1, netflix.usedTodayMin)
        assertEquals(0L, netflix.remainderMs)
    }

    @Test fun appMinuteIsCountedFromItsOwnRemainder() {
        val first = count("youtube", 40_000L, 0L, 30_000L)
        assertEquals(1, first.perAppAddMin)
        assertEquals(10_000L, first.appRemainderMs)
    }
}
