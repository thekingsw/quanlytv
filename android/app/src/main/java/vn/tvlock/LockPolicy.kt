package vn.tvlock

import java.util.Calendar

/**
 * Pure lock-decision logic — NO Android imports, NO SharedPreferences.
 *
 * (2026-11-08) Tách ra từ Store.kt vì Store ôm hết mọi thứ (1201 dòng:
 * state + quyết định + đếm giờ + JSON + file) nên sửa 1 chỗ là ảnh hưởng
 * chỗ khác (màn khóa refresh 88 lần, nhập mã thưởng bị reset). Mọi quyết
 * định khóa/đếm giờ đều đi qua đây, nhận state tường minh qua [Input] /
 * [CountInput] và trả kết quả thuần — test được bằng JUnit trên JVM,
 * không cần máy thật hay giả lập.
 *
 * Quy tắc: hàm nào ở đây cũng phải là hàm thuần (cùng input -> cùng
 * output, không đọc/ghi state ngoài). Store chỉ làm 2 việc: đọc state từ
 * SharedPreferences thành Input, rồi áp kết quả trả về vào lại prefs.
 */
object LockPolicy {

    /** Per-app rule: mode = "" | "blocked" | "limit" | "free". */
    data class AppRule(val mode: String = "", val minutes: Int = 0)

    /** Snapshot of everything a lock decision needs. */
    data class Input(
        val nowMs: Long,
        val lastPkg: String,
        val ownPkg: String = "vn.tvlock",
        val newsPkg: String = "",
        val newsStartMin: Int = 0,
        val newsEndMin: Int = 0,
        val manualLocked: Boolean = false,
        val unlockUntilMs: Long = 0L,
        val usedTodayMin: Int = 0,
        val dailyLimitMin: Int = 0,   // 0 = unlimited
        val bonusMin: Int = 0,
        val lockedBySchedule: Boolean = false,
        val scheduleReason: String = "",
        val appRule: AppRule = AppRule(),
        val perAppUsedMin: Int = 0
    )

    fun minutesOfDay(nowMs: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun isInWindow(nowMin: Int, start: Int, end: Int): Boolean {
        if (start == end) return false
        return if (start < end) nowMin in start until end else nowMin >= start || nowMin < end
    }

    fun isNewsExempt(pkg: String, newsPkg: String, nowMs: Long, newsStartMin: Int, newsEndMin: Int): Boolean {
        return newsPkg.isNotEmpty() && pkg == newsPkg &&
            isInWindow(minutesOfDay(nowMs), newsStartMin, newsEndMin)
    }

    fun isNewsExempt(pkg: String, input: Input): Boolean =
        isNewsExempt(pkg, input.newsPkg, input.nowMs, input.newsStartMin, input.newsEndMin)

    fun isExcluded(pkg: String): Boolean {
        return pkg == "vn.tvlock" || pkg == "com.google.android.tvlauncher" ||
            pkg == "com.android.systemui" || pkg == "com.android.settings" ||
            pkg == "com.google.android.apps.tv.launcherx"
    }

    /** True when the TV should be locked right now (whole-TV rules). */
    fun isLockedNow(input: Input): Boolean {
        // App thời sự trong khung giờ thời sự — miễn trừ tuyệt đối khỏi
        // màn khóa (kể cả khi đã hết giờ xem ngày / đến lịch khóa).
        if (isNewsExempt(input.lastPkg, input)) return false
        if (input.nowMs < input.unlockUntilMs) return false
        if (input.manualLocked) return true
        if (input.lockedBySchedule) return true
        // Daily limit only locks when the bonus pool is also exhausted.
        val limit = input.dailyLimitMin
        if (limit > 0 && input.usedTodayMin >= limit && input.bonusMin <= 0) return true
        return false
    }

    fun lockReason(input: Input): String {
        if (input.manualLocked && input.nowMs >= input.unlockUntilMs) return "Đã khóa"
        if (input.lockedBySchedule) return "Đến giờ khóa (${input.scheduleReason})"
        val limit = input.dailyLimitMin
        if (limit > 0 && input.usedTodayMin >= limit && input.bonusMin <= 0)
            return "Hết giờ xem — đã hết cả phút thưởng"
        if (limit > 0 && input.usedTodayMin >= limit) return "Hết giờ xem"
        return "Đã khóa"
    }

    /**
     * Per-app block check. Priority: news exemption > PIN temp-unlock >
     * per-app blocked/limit > manual lock / lock window / daily limit
     * (bonus exhausted). Returns null when the app may run.
     */
    fun blockReason(pkg: String, label: String, input: Input): String? {
        if (isNewsExempt(pkg, input)) return null
        if (input.nowMs < input.unlockUntilMs) return null
        val rule = input.appRule
        if (rule.mode == "blocked") return "App $label đã bị chặn"
        if (rule.mode == "limit" && input.perAppUsedMin >= rule.minutes)
            return "Hết giờ cho app $label"
        if (input.manualLocked) return "Đã khóa"
        if (input.lockedBySchedule) return "Đến giờ khóa (${input.scheduleReason})"
        val limit = input.dailyLimitMin
        if (limit > 0 && input.usedTodayMin >= limit && input.bonusMin <= 0)
            return "Hết giờ xem — đã hết cả phút thưởng"
        return null
    }

    /**
     * The lockPoll guard: should the lock screen be (re)shown for the
     * current foreground app? False when the TV is not locked, the lock is
     * already showing (lastPkg == own pkg), or the app is exempt (excluded
     * / news in its slot). An empty lastPkg still shows the lock
     * (fail-safe: when the foreground app is unknown but the TV is locked,
     * lock rather than leave it open) — this matches the old lockPoll.
     * Re-showing when the lock is already up recreates LockActivity and
     * resets its state (reward-code mode) — the "màn khóa refresh 88 lần"
     * bug (2026-11-08).
     */
    fun shouldShowLock(input: Input): Boolean {
        if (!isLockedNow(input)) return false
        val pkg = input.lastPkg
        if (pkg == input.ownPkg) return false
        if (isExcluded(pkg)) return false
        if (isNewsExempt(pkg, input)) return false
        return true
    }

    /**
     * Is watch time being counted right now — only when the foreground app
     * is known and countable (not excluded, not news-exempt, not "free").
     * When false, time-based limits show stale numbers, so countdowns for
     * them must not be displayed.
     */
    fun isCountingNow(input: Input): Boolean {
        val pkg = input.lastPkg
        if (pkg.isEmpty() || isExcluded(pkg)) return false
        if (isNewsExempt(pkg, input)) return false
        if (input.appRule.mode == "free") return false
        return true
    }

    // ---- Counting (pure arithmetic; mirrors Store.addElapsed) ----

    data class CountInput(
        val pkg: String,
        val nowMs: Long,
        val lastPkgStartMs: Long,
        val remainderMs: Long,
        val usedTodayMin: Int,
        val bonusMin: Int,
        val dailyLimitMin: Int,   // 0 = unlimited
        val appRuleMode: String,  // "blocked"/"limit"/"free"/""
        val newsPkg: String = "",
        val newsStartMin: Int = 0,
        val newsEndMin: Int = 0
    )

    data class CountResult(
        val countedMin: Int,    // minutes added to the per-app total
        val usedTodayMin: Int,
        val bonusMin: Int,
        val remainderMs: Long,
        val perAppAddMin: Int
    )

    /**
     * Add elapsed viewing time for a package. Per-app minutes are always
     * recorded. "free" apps do not consume the daily limit. Minutes past
     * the daily limit are spent from the bonus pool (minute by minute);
     * only when the pool is empty does usedTodayMin grow past the limit.
     * News-exempt and excluded packages count nothing. The sub-minute
     * remainder is carried over even when no full minute elapsed.
     */
    fun addElapsed(input: CountInput): CountResult {
        val noChange = CountResult(0, input.usedTodayMin, input.bonusMin, input.remainderMs, 0)
        if (input.pkg.isEmpty() || isExcluded(input.pkg)) return noChange
        val start = input.lastPkgStartMs
        if (start <= 0L || input.nowMs <= start) return noChange
        if (isNewsExempt(input.pkg, input.newsPkg, input.nowMs, input.newsStartMin, input.newsEndMin))
            return noChange
        val total = input.remainderMs + (input.nowMs - start)
        val mins = (total / 60000L).toInt()
        val newRemainder = total % 60000L
        if (mins <= 0) return CountResult(0, input.usedTodayMin, input.bonusMin, newRemainder, 0)
        var used = input.usedTodayMin
        var bonus = input.bonusMin
        if (input.appRuleMode == "free") {
            return CountResult(mins, used, bonus, newRemainder, mins)
        }
        val limit = input.dailyLimitMin
        if (limit <= 0) {
            used += mins
            return CountResult(mins, used, bonus, newRemainder, mins)
        }
        val room = (limit - used).coerceAtLeast(0)
        val toDaily = minOf(mins, room)
        val overflow = mins - toDaily
        used += toDaily
        if (overflow > 0) {
            if (bonus > 0) {
                val spend = minOf(overflow, bonus)
                bonus -= spend
                used += (overflow - spend)
            } else {
                used += overflow
            }
        }
        return CountResult(mins, used, bonus, newRemainder, mins)
    }
}
