package vn.tvlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for SchedulePolicy (pure logic). Run with:
 *   bash ~/workspace/manual-build/run_tvlock_tests.sh
 *
 * These tests pin down the regressions from 2026-11-09:
 *  - 00:00–23:59 shows "khóa cả ngày" but 23:59 was unlocked
 *    (scheduleTimeText said all-day, matchingSchedules excluded 1439)
 *  - overlapping schedules only unlocked until the FIRST one's end
 */
class SchedulePolicyTest {

    companion object {
        // 2026-11-08 is a Sunday (day 6), 2026-11-09 a Monday (day 0).
        fun msAt(year: Int, month: Int, day: Int, hour: Int, min: Int): Long {
            val c = Calendar.getInstance()
            c.set(year, month, day, hour, min, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        /** Sunday 2026-11-08 at hour:min. */
        fun sunday(hour: Int, min: Int): Long = msAt(2026, Calendar.NOVEMBER, 8, hour, min)

        /** Monday 2026-11-09 at hour:min. */
        fun monday(hour: Int, min: Int): Long = msAt(2026, Calendar.NOVEMBER, 9, hour, min)

        fun sched(id: String, days: List<Int>, startMin: Int, endMin: Int) =
            SchedulePolicy.Schedule(id, days, startMin, endMin)

        /** All days. */
        val ALL = listOf(0, 1, 2, 3, 4, 5, 6)
    }

    // ---- 23:59 bug ----

    @Test fun fullDayRangeLocksAt2359() {
        val s = sched("a", ALL, 0, 1439)
        assertTrue(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(23, 59)))
        assertTrue(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(0, 0)))
        assertTrue(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(12, 0)))
    }

    @Test fun fullDayRangeDisplaysAsAllDay() {
        val s = sched("a", ALL, 0, 1439)
        assertEquals("khóa cả ngày", SchedulePolicy.scheduleTimeText(s))
    }

    @Test fun startEqualsEndIsAllDay() {
        val s = sched("a", ALL, 480, 480)
        assertTrue(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(3, 0)))
        assertEquals("khóa cả ngày", SchedulePolicy.scheduleTimeText(s))
    }

    // ---- normal window ----

    @Test fun normalWindowMatchesInside() {
        val s = sched("a", ALL, 8 * 60, 23 * 60)
        assertTrue(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(8, 0)))
        assertTrue(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(12, 30)))
        assertTrue(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(22, 59)))
    }

    @Test fun normalWindowEndIsExclusive() {
        val s = sched("a", ALL, 8 * 60, 23 * 60)
        assertFalse(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(23, 0)))
        assertFalse(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(7, 59)))
    }

    @Test fun normalWindowRespectsDays() {
        val s = sched("a", listOf(0), 8 * 60, 23 * 60) // Monday only
        assertFalse(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(12, 0)))
        assertTrue(SchedulePolicy.isLockedBySchedule(listOf(s), monday(12, 0)))
    }

    // ---- overnight ----

    @Test fun overnightMatchesAfterStartAndBeforeEndNextDay() {
        val s = sched("a", ALL, 20 * 60, 7 * 60)
        assertTrue(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(20, 0)))
        assertTrue(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(23, 59)))
        assertTrue(SchedulePolicy.isLockedBySchedule(listOf(s), monday(6, 59)))
        assertFalse(SchedulePolicy.isLockedBySchedule(listOf(s), monday(7, 0)))
        assertFalse(SchedulePolicy.isLockedBySchedule(listOf(s), sunday(19, 59)))
    }

    // ---- overlapping schedules ----

    @Test fun overlappingSchedulesBothMatch() {
        val a = sched("a", ALL, 8 * 60, 12 * 60)
        val b = sched("b", ALL, 10 * 60, 23 * 60)
        val match = SchedulePolicy.matchingSchedules(listOf(a, b), sunday(11, 0))
        assertEquals(2, match.size)
    }

    @Test fun unlockUntilLatestEndingSchedule() {
        val a = sched("a", ALL, 8 * 60, 12 * 60)
        val b = sched("b", ALL, 10 * 60, 23 * 60)
        val end = SchedulePolicy.unlockUntilEndOfLockWindowMs(listOf(a, b), sunday(11, 0))
        assertNotNull(end)
        val c = Calendar.getInstance()
        c.timeInMillis = end!!
        assertEquals(23, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, c.get(Calendar.MINUTE))
    }

    @Test fun unlockUntilNullWhenNotInSchedule() {
        val a = sched("a", ALL, 8 * 60, 12 * 60)
        assertNull(SchedulePolicy.unlockUntilEndOfLockWindowMs(listOf(a), sunday(13, 0)))
    }

    @Test fun unlockUntilAllDayEndsAtNextMidnight() {
        val s = sched("a", ALL, 0, 1439)
        val end = SchedulePolicy.unlockUntilEndOfLockWindowMs(listOf(s), sunday(15, 30))
        assertNotNull(end)
        val c = Calendar.getInstance()
        c.timeInMillis = end!!
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, c.get(Calendar.MINUTE))
        assertEquals(9, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun sundayOnlyAllDayUnlockEndsAtNextMidnight() {
        val s = sched("sun", listOf(6), 0, 1439)
        val end = SchedulePolicy.unlockUntilEndOfLockWindowMs(listOf(s), sunday(15, 30))
        assertNotNull(end)
        assertTrue(end!! > sunday(23, 59) + 30_000L)
        assertFalse(SchedulePolicy.isLockedBySchedule(listOf(s), end))
    }

    @Test fun allDayWithStartEqualsEndAlsoEndsAtNextMidnight() {
        val s = sched("a", ALL, 600, 600)
        val end = SchedulePolicy.unlockUntilEndOfLockWindowMs(listOf(s), sunday(15, 30))
        assertNotNull(end)
        val c = Calendar.getInstance()
        c.timeInMillis = end!!
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, c.get(Calendar.MINUTE))
        assertEquals(9, c.get(Calendar.DAY_OF_MONTH))
    }

    // ---- minutesUntilNextLock ----

    @Test fun minutesUntilNextLockZeroWhenInside() {
        val s = sched("a", ALL, 8 * 60, 23 * 60)
        assertEquals(0, SchedulePolicy.minutesUntilNextLock(listOf(s), sunday(12, 0)))
    }

    @Test fun minutesUntilNextLockCountsDown() {
        val s = sched("a", ALL, 8 * 60, 23 * 60)
        assertEquals(60, SchedulePolicy.minutesUntilNextLock(listOf(s), sunday(7, 0)))
    }

    @Test fun minutesUntilNextLockMaxWhenNone() {
        assertEquals(Int.MAX_VALUE, SchedulePolicy.minutesUntilNextLock(emptyList(), sunday(12, 0)))
    }
}
