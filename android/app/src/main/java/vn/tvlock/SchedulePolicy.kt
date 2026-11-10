package vn.tvlock

import java.util.Calendar

/**
 * Pure lock-schedule logic — NO Android imports, NO SharedPreferences.
 *
 * (2026-11-09) Tách ra từ Store.kt theo review: matchingSchedules,
 * minutesUntilNextLock và unlockUntilEndOfLockWindow là chỗ ra lỗi lịch
 * (lịch 00:00–23:59 hiện "khóa cả ngày" nhưng 23:59 lại mở; lịch chồng
 * nhau chỉ lấy lịch đầu tiên) mà không có test nào. Mọi hàm ở đây đều
 * thuần (cùng input -> cùng output) để test được bằng JUnit trên JVM.
 *
 * Quy ước (giữ nguyên như Store.kt cũ):
 *  - days: 0 = Thứ 2 ... 6 = Chủ nhật.
 *  - startMin == endMin: khóa cả ngày.
 *  - startMin == 0 && endMin == 1439: cũng là khóa cả ngày (UI hiện vậy,
 *    nên logic phải khóa cả phút 1439 — lỗi 2026-11-09).
 *  - startMin > endMin: qua đêm.
 *  - Khoảng [start, end) — end exclusive, TRỪ trường hợp cả ngày ở trên.
 */
object SchedulePolicy {

    data class Schedule(val id: String, val days: List<Int>, val startMin: Int, val endMin: Int)

    fun dayNames(): List<String> =
        listOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật")

    /** Calendar.DAY_OF_WEEK (1=Sunday..7=Saturday) -> 0=Mon..6=Sun. */
    fun dayIndexOf(cal: Calendar): Int = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun minutesOfDay(nowMs: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun fmt(min: Int): String = String.format(java.util.Locale.US, "%02d:%02d", min / 60, min % 60)

    /** True when this schedule locks at nowMin on the given day. */
    fun matches(s: Schedule, today: Int, yesterday: Int, nowMin: Int): Boolean {
        if (s.days.isEmpty()) return false
        // All-day: start==end, or the explicit 00:00-23:59 range.
        if (s.startMin == s.endMin) return today in s.days
        if (s.startMin == 0 && s.endMin == 1439) return today in s.days
        if (s.startMin < s.endMin) return today in s.days && nowMin in s.startMin until s.endMin
        return (today in s.days && nowMin >= s.startMin) ||
            (yesterday in s.days && nowMin < s.endMin)
    }

    /** Schedules matching right now (union: any match locks the TV). */
    fun matchingSchedules(schedules: List<Schedule>, nowMs: Long): List<Schedule> {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        val today = dayIndexOf(c)
        val yesterday = (today + 6) % 7
        val nowMin = minutesOfDay(nowMs)
        return schedules.filter { matches(it, today, yesterday, nowMin) }
    }

    fun isLockedBySchedule(schedules: List<Schedule>, nowMs: Long): Boolean =
        matchingSchedules(schedules, nowMs).isNotEmpty()

    /** Minutes until the next schedule starts (0 when inside one, MAX when none). */
    fun minutesUntilNextLock(schedules: List<Schedule>, nowMs: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        val today = dayIndexOf(c)
        val nowMin = minutesOfDay(nowMs)
        if (isLockedBySchedule(schedules, nowMs)) return 0
        var best = Int.MAX_VALUE
        for (s in schedules) {
            if (s.days.isEmpty()) continue
            for (d in 0..7) {
                val day = (today + d) % 7
                if (day !in s.days) continue
                val delta = d * 1440 + s.startMin - nowMin
                if (delta <= 0) continue
                if (delta < best) best = delta
            }
        }
        return best
    }

    /** Seconds until the next schedule starts — same logic as
     * [minutesUntilNextLock] but exact to the second, for the 60s countdown. */
    fun secondsUntilNextLock(schedules: List<Schedule>, nowMs: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        val today = dayIndexOf(c)
        val secOfDay = c.get(Calendar.HOUR_OF_DAY) * 3600 +
            c.get(Calendar.MINUTE) * 60 + c.get(Calendar.SECOND)
        if (isLockedBySchedule(schedules, nowMs)) return 0
        var best = Int.MAX_VALUE
        for (s in schedules) {
            if (s.days.isEmpty()) continue
            for (d in 0..7) {
                val day = (today + d) % 7
                if (day !in s.days) continue
                val delta = d * 86400 + s.startMin * 60 - secOfDay
                if (delta <= 0) continue
                if (delta < best) best = delta
            }
        }
        return best
    }

    fun scheduleTimeText(s: Schedule): String {
        return if (s.startMin == s.endMin) "khóa cả ngày"
        else if (s.startMin == 0 && s.endMin == 1439) "khóa cả ngày"
        else fmt(s.startMin) + "–" + fmt(s.endMin) + if (s.startMin > s.endMin) " (qua đêm)" else ""
    }

    fun scheduleSummary(s: Schedule): String {
        val daysTxt = if (s.days.size == 7) "Hằng ngày"
            else s.days.sorted().joinToString(", ") { dayNames()[it] }
        return "$daysTxt: ${scheduleTimeText(s)}"
    }

    /**
     * End timestamp (ms) of the current lock window, or null when not inside
     * any schedule. When several schedules overlap, use the one ending
     * LATEST — taking the first match (old behavior) could re-lock early.
     * All-day schedules end at 00:00:00.000 tomorrow.
     */
    fun unlockUntilEndOfLockWindowMs(schedules: List<Schedule>, nowMs: Long): Long? {
        val match = matchingSchedules(schedules, nowMs)
        if (match.isEmpty()) return null
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        val nowMin = minutesOfDay(nowMs)
        var best: Long? = null
        for (s in match) {
            val end = Calendar.getInstance().apply { timeInMillis = nowMs }
            when {
                s.startMin == s.endMin || (s.startMin == 0 && s.endMin == 1439) -> {
                    // Include the entire 23:59 minute; boundary is midnight.
                    end.add(Calendar.DAY_OF_YEAR, 1)
                    end.set(Calendar.HOUR_OF_DAY, 0)
                    end.set(Calendar.MINUTE, 0)
                }
                s.startMin < s.endMin -> {
                    end.set(Calendar.HOUR_OF_DAY, s.endMin / 60)
                    end.set(Calendar.MINUTE, s.endMin % 60)
                }
                else -> { // overnight: ends tomorrow if we are past the start
                    if (nowMin >= s.startMin) end.add(Calendar.DAY_OF_YEAR, 1)
                    end.set(Calendar.HOUR_OF_DAY, s.endMin / 60)
                    end.set(Calendar.MINUTE, s.endMin % 60)
                }
            }
            end.set(Calendar.SECOND, 0)
            end.set(Calendar.MILLISECOND, 0)
            val t = end.timeInMillis
            if (best == null || t > best) best = t
        }
        return best
    }
}
