package vn.tvlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for LockPolicy (pure logic). Run with:
 *   bash ~/workspace/manual-build/run_tvlock_tests.sh
 *
 * These tests pin down the regressions from 2026-11-06..08 so a future
 * fix cannot silently re-break them:
 *  - news app in its slot is never locked (web said "locked" 2026-11-07)
 *  - lockPoll must NOT re-show the lock when it is already showing
 *    (lock screen refreshed 88x, 2026-11-08)
 *  - re-showing the lock must not reset reward-code mode (singleTop)
 *  - +N adds to a running temp unlock instead of overwriting it
 *  - counting: news is free, bonus pool is spent before the daily total
 *    grows past the limit, sub-minute remainder carries over
 */
class LockPolicyTest {

    companion object {
        const val NEWS = "ar.tvplayer.tv"
        const val YT = "com.google.android.youtube.tv"
        const val OWN = "vn.tvlock"

        /** A timestamp on 2026-11-08 at hour:min (local timezone). */
        fun msAt(hour: Int, min: Int): Long {
            val c = Calendar.getInstance()
            c.set(2026, Calendar.NOVEMBER, 8, hour, min, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        /** Base input: 19:00, YouTube in foreground, 90' daily limit, 30' used. */
        fun base(nowMs: Long = msAt(19, 0)): LockPolicy.Input = LockPolicy.Input(
            nowMs = nowMs,
            lastPkg = YT,
            newsPkg = NEWS,
            newsStartMin = 18 * 60 + 30,
            newsEndMin = 20 * 60,
            dailyLimitMin = 90,
            usedTodayMin = 30
        )
    }

    // ---- isInWindow ----

    @Test fun windowNormal() {
        assertTrue(LockPolicy.isInWindow(600, 480, 720))
        assertFalse(LockPolicy.isInWindow(479, 480, 720))
        assertFalse(LockPolicy.isInWindow(720, 480, 720)) // end exclusive
    }

    @Test fun windowOvernight() {
        // 22:00-07:00 wraps midnight.
        assertTrue(LockPolicy.isInWindow(23 * 60, 22 * 60, 7 * 60))
        assertTrue(LockPolicy.isInWindow(3 * 60, 22 * 60, 7 * 60))
        assertFalse(LockPolicy.isInWindow(12 * 60, 22 * 60, 7 * 60))
    }

    @Test fun windowEmpty() {
        assertFalse(LockPolicy.isInWindow(600, 600, 600))
    }

    // ---- isNewsExempt ----

    @Test fun newsExemptInSlot() {
        val input = base(msAt(19, 0))
        assertTrue(LockPolicy.isNewsExempt(NEWS, input))
    }

    @Test fun newsNotExemptOutsideSlot() {
        val input = base(msAt(15, 0))
        assertFalse(LockPolicy.isNewsExempt(NEWS, input))
    }

    @Test fun otherAppNotExemptInSlot() {
        val input = base(msAt(19, 0))
        assertFalse(LockPolicy.isNewsExempt(YT, input))
    }

    @Test fun newsExemptNeedsNewsPkg() {
        val input = base().copy(newsPkg = "")
        assertFalse(LockPolicy.isNewsExempt(NEWS, input))
    }

    // ---- isExcluded ----

    @Test fun excludedPackages() {
        assertTrue(LockPolicy.isExcluded(OWN))
        assertTrue(LockPolicy.isExcluded("com.android.systemui"))
        assertTrue(LockPolicy.isExcluded("com.android.settings"))
        assertFalse(LockPolicy.isExcluded(YT))
        assertFalse(LockPolicy.isExcluded(NEWS))
    }

    // ---- isLockedNow ----

    @Test fun notLockedNormally() {
        assertFalse(LockPolicy.isLockedNow(base()))
    }

    @Test fun newsNeverLockedEvenWhenScheduleAndLimitHit() {
        // 2026-11-07: web said "locked" while the news app was on in its slot.
        val input = base(msAt(19, 0)).copy(
            lastPkg = NEWS,
            lockedBySchedule = true,
            usedTodayMin = 90,
            bonusMin = 0
        )
        assertFalse(LockPolicy.isLockedNow(input))
    }

    @Test fun tempUnlockBeatsScheduleAndLimit() {
        val now = msAt(19, 0)
        val input = base(now).copy(
            unlockUntilMs = now + 10 * 60 * 1000,
            lockedBySchedule = true,
            usedTodayMin = 90,
            bonusMin = 0
        )
        assertFalse(LockPolicy.isLockedNow(input))
    }

    @Test fun manualLockLocks() {
        assertTrue(LockPolicy.isLockedNow(base().copy(manualLocked = true)))
    }

    @Test fun scheduleLocks() {
        assertTrue(LockPolicy.isLockedNow(base().copy(lockedBySchedule = true)))
    }

    @Test fun dailyLimitLocksOnlyWhenBonusEmpty() {
        assertTrue(LockPolicy.isLockedNow(base().copy(usedTodayMin = 90, bonusMin = 0)))
        assertFalse(LockPolicy.isLockedNow(base().copy(usedTodayMin = 90, bonusMin = 5)))
        assertFalse(LockPolicy.isLockedNow(base().copy(usedTodayMin = 89, bonusMin = 0)))
    }

    @Test fun unlimitedNeverLocksOnLimit() {
        assertFalse(LockPolicy.isLockedNow(base().copy(dailyLimitMin = 0, usedTodayMin = 999)))
    }

    // ---- blockReason ----

    @Test fun blockReasonNewsIsNull() {
        val input = base(msAt(19, 0)).copy(lockedBySchedule = true, manualLocked = true)
        assertNull(LockPolicy.blockReason(NEWS, "Thời sự", input))
    }

    @Test fun blockReasonTempUnlockIsNull() {
        val now = msAt(19, 0)
        val input = base(now).copy(unlockUntilMs = now + 600_000, lockedBySchedule = true)
        assertNull(LockPolicy.blockReason(YT, "YouTube", input))
    }

    @Test fun blockReasonBlockedApp() {
        val input = base().copy(appRule = LockPolicy.AppRule("blocked"))
        assertEquals("App YouTube đã bị chặn", LockPolicy.blockReason(YT, "YouTube", input))
    }

    @Test fun blockReasonAppLimit() {
        val input = base().copy(
            appRule = LockPolicy.AppRule("limit", 30),
            perAppUsedMin = 30
        )
        assertEquals("Hết giờ cho app YouTube", LockPolicy.blockReason(YT, "YouTube", input))
        val under = input.copy(perAppUsedMin = 29)
        assertNull(LockPolicy.blockReason(YT, "YouTube", under))
    }

    @Test fun blockReasonScheduleAndManual() {
        assertEquals("Đã khóa", LockPolicy.blockReason(YT, "YouTube", base().copy(manualLocked = true)))
        val sched = base().copy(lockedBySchedule = true, scheduleReason = "08:00–23:00")
        assertTrue(LockPolicy.blockReason(YT, "YouTube", sched)!!.contains("Đến giờ khóa"))
    }

    // ---- shouldShowLock (the lockPoll guard) ----

    @Test fun shouldShowLockForRealAppWhenLocked() {
        val input = base().copy(lockedBySchedule = true)
        assertTrue(LockPolicy.shouldShowLock(input))
    }

    @Test fun shouldNotReshowWhenLockAlreadyShowing() {
        // 2026-11-08: lockPoll re-showed the lock ~1x/second (88x in log)
        // because refreshForegroundApp picked a background app as lastPkg.
        // When lastPkg IS the lock itself, never re-show.
        val input = base().copy(lastPkg = OWN, lockedBySchedule = true)
        assertFalse(LockPolicy.shouldShowLock(input))
    }

    @Test fun shouldNotShowWhenNotLocked() {
        assertFalse(LockPolicy.shouldShowLock(base()))
    }

    @Test fun shouldNotShowForExcludedApp() {
        val input = base().copy(lastPkg = "com.android.systemui", lockedBySchedule = true)
        assertFalse(LockPolicy.shouldShowLock(input))
    }

    @Test fun shouldNotShowForNewsInSlot() {
        val input = base(msAt(19, 0)).copy(lastPkg = NEWS, lockedBySchedule = true)
        assertFalse(LockPolicy.shouldShowLock(input))
    }

    @Test fun shouldShowWhenLastPkgEmptyAndLocked() {
        // Fail-safe: foreground app unknown but TV is locked -> show the
        // lock rather than leave it open (matches the old lockPoll).
        val input = base().copy(lastPkg = "", lockedBySchedule = true)
        assertTrue(LockPolicy.shouldShowLock(input))
    }

    // ---- addElapsed (counting) ----

    private fun countBase(nowMs: Long = msAt(19, 0)): LockPolicy.CountInput =
        LockPolicy.CountInput(
            pkg = YT,
            nowMs = nowMs,
            lastPkgStartMs = nowMs - 6 * 60 * 1000, // 6 minutes of viewing
            remainderMs = 0,
            usedTodayMin = 30,
            bonusMin = 0,
            dailyLimitMin = 90,
            appRuleMode = ""
        )

    @Test fun countingAddsMinutes() {
        val r = LockPolicy.addElapsed(countBase())
        assertEquals(6, r.countedMin)
        assertEquals(36, r.usedTodayMin)
        assertEquals(6, r.perAppAddMin)
    }

    @Test fun countingSkipsNews() {
        // 2026-11-07: news time must not consume the daily limit.
        val input = countBase().copy(pkg = NEWS, newsPkg = NEWS, newsStartMin = 18 * 60 + 30, newsEndMin = 20 * 60)
        val r = LockPolicy.addElapsed(input)
        assertEquals(0, r.countedMin)
        assertEquals(30, r.usedTodayMin)
    }

    @Test fun countingSkipsExcluded() {
        val r = LockPolicy.addElapsed(countBase().copy(pkg = OWN))
        assertEquals(0, r.countedMin)
    }

    @Test fun countingFreeAppDoesNotConsumeDaily() {
        val r = LockPolicy.addElapsed(countBase().copy(appRuleMode = "free"))
        assertEquals(6, r.perAppAddMin)
        assertEquals(30, r.usedTodayMin) // daily total untouched
    }

    @Test fun countingOverflowSpendsBonusFirst() {
        // 30' used of 90' limit, 65' watched -> 60' fills daily, 5' from bonus.
        val input = countBase().copy(
            lastPkgStartMs = msAt(19, 0) - 65 * 60 * 1000,
            usedTodayMin = 30,
            bonusMin = 10
        )
        val r = LockPolicy.addElapsed(input)
        assertEquals(65, r.countedMin)
        assertEquals(90, r.usedTodayMin)
        assertEquals(5, r.bonusMin)
    }

    @Test fun countingOverflowWithoutBonusGrowsPastLimit() {
        val input = countBase().copy(
            lastPkgStartMs = msAt(19, 0) - 65 * 60 * 1000,
            usedTodayMin = 30,
            bonusMin = 0
        )
        val r = LockPolicy.addElapsed(input)
        assertEquals(95, r.usedTodayMin)
        assertEquals(0, r.bonusMin)
    }

    @Test fun countingUnlimitedAddsAll() {
        val r = LockPolicy.addElapsed(countBase().copy(dailyLimitMin = 0))
        assertEquals(36, r.usedTodayMin)
    }

    @Test fun countingCarriesRemainder() {
        // 30s watched + 40s remainder = 70s -> 1 minute, 10s remainder left.
        val input = countBase().copy(
            lastPkgStartMs = msAt(19, 0) - 30 * 1000,
            remainderMs = 40 * 1000
        )
        val r = LockPolicy.addElapsed(input)
        assertEquals(1, r.countedMin)
        assertEquals(10_000L, r.remainderMs)
    }

    @Test fun countingSubMinuteKeepsRemainder() {
        val input = countBase().copy(
            lastPkgStartMs = msAt(19, 0) - 30 * 1000,
            remainderMs = 0
        )
        val r = LockPolicy.addElapsed(input)
        assertEquals(0, r.countedMin)
        assertEquals(30_000L, r.remainderMs)
    }

    @Test fun countingZeroStartCountsNothing() {
        val r = LockPolicy.addElapsed(countBase().copy(lastPkgStartMs = 0))
        assertEquals(0, r.countedMin)
        assertEquals(0L, r.remainderMs)
    }
}
