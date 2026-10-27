package vn.tvlock

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Store(private val ctx: Context) {
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

    // ---------- v3: bonus minutes (reward pool, never expires at midnight) ----------
    var bonusMin: Int
        get() = prefs.getInt("bonusMin", 0)
        set(v) = prefs.edit().putInt("bonusMin", v.coerceAtLeast(0)).apply()

    fun addBonus(minutes: Int) {
        if (minutes > 0) {
            bonusMin = bonusMin + minutes
            clearWarnings()
        }
    }

    // ---------- v3: per-app rules ----------
    data class AppRule(val mode: String, val minutes: Int)

    private fun appRulesJson(): JSONObject {
        return try { JSONObject(prefs.getString("appRules", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    }

    fun appRules(): Map<String, AppRule> {
        val out = mutableMapOf<String, AppRule>()
        val j = appRulesJson()
        val keys = j.keys()
        while (keys.hasNext()) {
            val pkg = keys.next()
            val r = j.optJSONObject(pkg) ?: continue
            out[pkg] = AppRule(r.optString("mode", "normal"), r.optInt("minutes", 0))
        }
        return out
    }

    fun appRule(pkg: String): AppRule = appRules()[pkg] ?: AppRule("normal", 0)

    fun setAppRule(pkg: String, mode: String, minutes: Int) {
        val j = appRulesJson()
        if (mode == "normal") j.remove(pkg)
        else {
            val r = JSONObject()
            r.put("mode", mode)
            r.put("minutes", minutes)
            j.put(pkg, r)
        }
        prefs.edit().putString("appRules", j.toString()).apply()
    }

    fun appRulesCount(): Int = appRules().size

    /** Minutes left for this app's own limit, or Int.MAX_VALUE when it has no limit rule. */
    fun appLimitRemainingMin(pkg: String): Int {
        val r = appRule(pkg)
        if (r.mode != "limit") return Int.MAX_VALUE
        return (r.minutes - perAppMin(pkg)).coerceAtLeast(0)
    }

    // ---------- v3: per-app minutes today ----------
    private fun perAppJson(): JSONObject {
        return try { JSONObject(prefs.getString("perAppToday", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    }

    fun perAppToday(): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        val j = perAppJson()
        val keys = j.keys()
        while (keys.hasNext()) {
            val pkg = keys.next()
            out[pkg] = j.optInt(pkg, 0)
        }
        return out
    }

    fun perAppMin(pkg: String): Int = perAppToday()[pkg] ?: 0

    private fun addPerAppMin(pkg: String, minutes: Int) {
        if (minutes <= 0) return
        val j = perAppJson()
        j.put(pkg, j.optInt(pkg, 0) + minutes)
        prefs.edit().putString("perAppToday", j.toString()).apply()
    }

    // ---------- v3: 14-day history ----------
    data class DayEntry(val date: String, val total: Int, val perApp: Map<String, Int>)

    private fun historyJson(): JSONObject {
        return try { JSONObject(prefs.getString("history", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    }

    fun historyDays(): List<DayEntry> {
        val j = historyJson()
        val out = mutableListOf<DayEntry>()
        val keys = j.keys()
        while (keys.hasNext()) {
            val date = keys.next()
            val d = j.optJSONObject(date) ?: continue
            val perApp = mutableMapOf<String, Int>()
            val pa = d.optJSONObject("perApp")
            if (pa != null) {
                val pk = pa.keys()
                while (pk.hasNext()) {
                    val pkg = pk.next()
                    perApp[pkg] = pa.optInt(pkg, 0)
                }
            }
            out.add(DayEntry(date, d.optInt("total", 0), perApp))
        }
        return out.sortedBy { it.date }.takeLast(14)
    }

    fun historyDayTotal(date: String): Int {
        return historyDays().firstOrNull { it.date == date }?.total ?: -1
    }

    fun yesterdayStr(): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    fun yesterdayTotal(): Int = historyDayTotal(yesterdayStr())

    private fun finalizeDay() {
        val date = usedDate
        if (date.isEmpty() || date == todayStr()) return
        val perApp = perAppToday()
        if (usedTodayMin <= 0 && perApp.isEmpty()) return
        val j = historyJson()
        val d = JSONObject()
        d.put("total", usedTodayMin)
        val pa = JSONObject()
        for ((pkg, min) in perApp) pa.put(pkg, min)
        d.put("perApp", pa)
        j.put(date, d)
        // Trim to the 14 most recent days.
        val dates = mutableListOf<String>()
        val keys = j.keys()
        while (keys.hasNext()) dates.add(keys.next())
        dates.sorted().dropLast(14).forEach { j.remove(it) }
        prefs.edit().putString("history", j.toString()).apply()
    }

    // ---------- v3: warned milestones (reset daily / when extra time is granted) ----------
    private fun warnedJson(): JSONObject {
        return try { JSONObject(prefs.getString("warned", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    }

    fun warned(kind: String, threshold: Int): Boolean {
        return warnedJson().optBoolean("$kind:$threshold", false)
    }

    fun markWarned(kind: String, threshold: Int) {
        val j = warnedJson()
        j.put("$kind:$threshold", true)
        prefs.edit().putString("warned", j.toString()).apply()
    }

    fun clearWarnings() {
        prefs.edit().putString("warned", "{}").apply()
    }

    fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun resetUsedIfNewDay() {
        val t = todayStr()
        if (usedDate != t) {
            finalizeDay()
            usedDate = t
            usedTodayMin = 0
            remainderMs = 0L
            prefs.edit().putString("perAppToday", "{}").apply()
            clearWarnings()
        }
    }

    /** Reset today's counters (used by Settings "Reset số phút đã xem hôm nay"). */
    fun resetTodayUsage() {
        usedTodayMin = 0
        remainderMs = 0L
        prefs.edit().putString("perAppToday", "{}").apply()
        clearWarnings()
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

    /** Minutes until the lock window starts (0 when inside it, MAX when the window is disabled). */
    fun minutesUntilLockWindow(nowMs: Long): Int {
        if (lockStartMin == lockEndMin) return Int.MAX_VALUE
        if (isInLockWindow(nowMs)) return 0
        val nowMin = minutesOfDay(nowMs)
        return if (nowMin < lockStartMin) lockStartMin - nowMin
        else lockStartMin + 1440 - nowMin
    }

    /** True when the TV should be locked right now (whole-TV rules; per-app rules are separate). */
    fun isLockedNow(nowMs: Long): Boolean {
        resetUsedIfNewDay()
        if (nowMs < unlockUntilMs) return false
        if (manualLocked) return true
        if (isInLockWindow(nowMs)) return true
        // Daily limit only locks when the bonus pool is also exhausted.
        if (dailyLimitMin > 0 && usedTodayMin >= dailyLimitMin && bonusMin <= 0) return true
        return false
    }

    /** Minutes of daily limit left (excluding bonus). */
    fun dailyRemainingMin(): Int {
        resetUsedIfNewDay()
        if (dailyLimitMin <= 0) return Int.MAX_VALUE
        return (dailyLimitMin - usedTodayMin).coerceAtLeast(0)
    }

    /** Effective minutes left: daily limit + bonus pool. */
    fun remainingMin(): Int {
        val d = dailyRemainingMin()
        if (d == Int.MAX_VALUE) return Int.MAX_VALUE
        return d + bonusMin
    }

    fun lockReason(nowMs: Long): String {
        resetUsedIfNewDay()
        if (manualLocked && nowMs >= unlockUntilMs) return "Đã khóa"
        if (isInLockWindow(nowMs)) return "Đến giờ khóa"
        if (dailyLimitMin > 0 && usedTodayMin >= dailyLimitMin && bonusMin <= 0)
            return "Hết giờ xem — đã hết cả phút thưởng"
        if (dailyLimitMin > 0 && usedTodayMin >= dailyLimitMin) return "Hết giờ xem"
        if (isInNewsSlot(nowMs)) return "Giờ thời sự — chỉ xem thời sự"
        return "Đã khóa"
    }

    /**
     * Per-app block check. Priority: news slot > PIN temp-unlock > per-app
     * blocked/limit > manual lock / lock window / daily limit (bonus exhausted).
     * Returns null when the app may run.
     */
    fun blockReason(pkg: String, nowMs: Long, label: String): String? {
        resetUsedIfNewDay()
        if (isInNewsSlot(nowMs) && newsPkg.isNotEmpty() && pkg != newsPkg)
            return "Giờ thời sự — chỉ xem thời sự"
        if (nowMs < unlockUntilMs) return null
        val rule = appRule(pkg)
        if (rule.mode == "blocked") return "App $label đã bị chặn"
        if (rule.mode == "limit" && perAppMin(pkg) >= rule.minutes)
            return "Hết giờ cho app $label"
        if (manualLocked) return "Đã khóa"
        if (isInLockWindow(nowMs)) return "Đến giờ khóa"
        if (dailyLimitMin > 0 && usedTodayMin >= dailyLimitMin && bonusMin <= 0)
            return "Hết giờ xem — đã hết cả phút thưởng"
        return null
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

    /** Display label for a package (fallback: the package name). */
    fun appLabel(pkg: String): String {
        return try {
            ctx.packageManager.getApplicationInfo(pkg, 0).loadLabel(ctx.packageManager).toString()
        } catch (_: Exception) { pkg }
    }

    /**
     * Add elapsed viewing time for a package; updates lastPkgStartMs to now.
     * Per-app minutes are always recorded. "free" apps do not consume the daily
     * limit. Minutes past the daily limit are spent from the bonus pool
     * (minute by minute); only when the pool is empty does usedTodayMin grow
     * past the limit and the lock engage.
     */
    fun addElapsed(pkg: String, nowMs: Long) {
        if (pkg.isEmpty() || isExcluded(pkg)) return
        resetUsedIfNewDay()
        val start = lastPkgStartMs
        if (start <= 0L || nowMs <= start) return
        if (isNewsExempt(pkg, nowMs)) return
        val total = remainderMs + (nowMs - start)
        val mins = (total / 60000L).toInt()
        remainderMs = total % 60000L
        if (mins <= 0) return
        addPerAppMin(pkg, mins)
        val rule = appRule(pkg)
        if (rule.mode == "free") return
        if (dailyLimitMin <= 0) {
            usedTodayMin = usedTodayMin + mins
            return
        }
        val room = (dailyLimitMin - usedTodayMin).coerceAtLeast(0)
        val toDaily = minOf(mins, room)
        val overflow = mins - toDaily
        usedTodayMin = usedTodayMin + toDaily
        if (overflow > 0) {
            if (bonusMin > 0) {
                val spend = minOf(overflow, bonusMin)
                bonusMin = bonusMin - spend
                usedTodayMin = usedTodayMin + (overflow - spend)
            } else {
                usedTodayMin = usedTodayMin + overflow
            }
        }
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
        clearWarnings()
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
        clearWarnings()
    }
}
