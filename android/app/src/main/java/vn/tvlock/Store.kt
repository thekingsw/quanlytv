package vn.tvlock

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("tvlock", Context.MODE_PRIVATE)

    var pin: String
        get() = prefs.getString("pin", "1234") ?: "1234"
        set(v) = prefs.edit().putString("pin", v).apply()

    var dailyLimitMin: Int
        get() = prefs.getInt("dailyLimitMin", 90)
        set(v) = prefs.edit().putInt("dailyLimitMin", v).apply()

    var lockStartMin: Int
        get() = prefs.getInt("lockStartMin", 1260)
        set(v) = prefs.edit().putInt("lockStartMin", v).apply()

    var lockEndMin: Int
        get() = prefs.getInt("lockEndMin", 420)
        set(v) = prefs.edit().putInt("lockEndMin", v).apply()

    var newsStartMin: Int
        get() = prefs.getInt("newsStartMin", 1140)
        set(v) = prefs.edit().putInt("newsStartMin", v).apply()

    var newsEndMin: Int
        get() = prefs.getInt("newsEndMin", 1200)
        set(v) = prefs.edit().putInt("newsEndMin", v).apply()

    var newsPkg: String
        get() = prefs.getString("newsPkg", "") ?: ""
        set(v) = prefs.edit().putString("newsPkg", v).apply()

    var manualLocked: Boolean
        get() = prefs.getBoolean("manualLocked", false)
        set(v) = prefs.edit().putBoolean("manualLocked", v).apply()

    var unlockUntilMs: Long
        get() = prefs.getLong("unlockUntilMs", 0L)
        set(v) = prefs.edit().putLong("unlockUntilMs", v).apply()

    var usedTodayMin: Int
        get() = prefs.getInt("usedTodayMin", 0)
        set(v) = prefs.edit().putInt("usedTodayMin", v).apply()

    var usedDate: String
        get() = prefs.getString("usedDate", "") ?: ""
        set(v) = prefs.edit().putString("usedDate", v).apply()

    var lastPkg: String
        get() = prefs.getString("lastPkg", "") ?: ""
        set(v) = prefs.edit().putString("lastPkg", v).apply()

    var lastPkgStartMs: Long
        get() = prefs.getLong("lastPkgStartMs", 0L)
        set(v) = prefs.edit().putLong("lastPkgStartMs", v).apply()

    private var remainderMs: Long
        get() = prefs.getLong("usedRemainderMs", 0L)
        set(v) = prefs.edit().putLong("usedRemainderMs", v).apply()

    fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun resetUsedIfNewDay() {
        val t = todayStr()
        if (usedDate != t) {
            usedDate = t
            usedTodayMin = 0
            remainderMs = 0L
        }
    }

    fun minutesOfDay(nowMs: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun isInWindow(nowMin: Int, start: Int, end: Int): Boolean {
        if (start == end) return false
        return if (start < end) nowMin in start until end else nowMin >= start || nowMin < end
    }

    fun isInLockWindow(nowMs: Long): Boolean = isInWindow(minutesOfDay(nowMs), lockStartMin, lockEndMin)
    fun isInNewsSlot(nowMs: Long): Boolean = isInWindow(minutesOfDay(nowMs), newsStartMin, newsEndMin)

    /** True when the TV should be locked right now (ignoring the news-slot exception). */
    fun isLockedNow(nowMs: Long): Boolean {
        resetUsedIfNewDay()
        if (nowMs < unlockUntilMs) return false
        if (manualLocked) return true
        if (isInLockWindow(nowMs)) return true
        if (dailyLimitMin > 0 && usedTodayMin >= dailyLimitMin) return true
        return false
    }

    fun remainingMin(): Int {
        resetUsedIfNewDay()
        if (dailyLimitMin <= 0) return Int.MAX_VALUE
        return (dailyLimitMin - usedTodayMin).coerceAtLeast(0)
    }

    fun lockReason(nowMs: Long): String {
        resetUsedIfNewDay()
        if (manualLocked && nowMs >= unlockUntilMs) return "Đã khóa"
        if (isInLockWindow(nowMs)) return "Đến giờ khóa"
        if (dailyLimitMin > 0 && usedTodayMin >= dailyLimitMin) return "Hết giờ xem"
        if (isInNewsSlot(nowMs)) return "Giờ thời sự — chỉ xem thời sự"
        return "Đã khóa"
    }

    fun isExcluded(pkg: String): Boolean {
        return pkg == "vn.tvlock" || pkg == "com.google.android.tvlauncher" ||
            pkg == "com.android.systemui" || pkg == "com.android.settings" ||
            pkg == "com.google.android.apps.tv.launcherx" || pkg == "com.google.android.tvlauncher"
    }

    /** Is this package exempt from counting right now (news app inside the news slot)? */
    fun isNewsExempt(pkg: String, nowMs: Long): Boolean {
        return newsPkg.isNotEmpty() && pkg == newsPkg && isInNewsSlot(nowMs)
    }

    /** Add elapsed viewing time for a package; updates lastPkgStartMs to now. */
    fun addElapsed(pkg: String, nowMs: Long) {
        if (pkg.isEmpty() || isExcluded(pkg)) return
        resetUsedIfNewDay()
        val start = lastPkgStartMs
        if (start <= 0L || nowMs <= start) return
        if (isNewsExempt(pkg, nowMs)) return
        val total = remainderMs + (nowMs - start)
        val mins = (total / 60000L).toInt()
        remainderMs = total % 60000L
        if (mins > 0) usedTodayMin = usedTodayMin + mins
    }

    /** Accumulate time for the currently-tracked package up to now (called on a tick). */
    fun tick(nowMs: Long) {
        val pkg = lastPkg
        if (pkg.isEmpty()) return
        addElapsed(pkg, nowMs)
        lastPkgStartMs = nowMs
    }

    fun fmt(min: Int): String = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

    fun addMinutes(minutes: Int) {
        unlockUntilMs = System.currentTimeMillis() + minutes * 60000L
        manualLocked = false
    }

    /** Unlock until the end of the current lock window (or +60 min if not in one). */
    fun unlockUntilEndOfLockWindow(nowMs: Long) {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        val nowMin = minutesOfDay(nowMs)
        if (isInLockWindow(nowMs)) {
            c.set(Calendar.HOUR_OF_DAY, lockEndMin / 60)
            c.set(Calendar.MINUTE, lockEndMin % 60)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (lockEndMin <= nowMin) c.add(Calendar.DAY_OF_YEAR, 1)
            unlockUntilMs = c.timeInMillis
        } else {
            unlockUntilMs = nowMs + 60 * 60000L
        }
        manualLocked = false
    }
}
