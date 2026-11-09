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
        val perAppUsedMin: Int = 0,
        /** Ngày khóa tay được bật ("yyyy-MM-dd"). Khóa tay chỉ có tác
         * dụng trong ngày — sang hôm sau tự reset về mở (2026-11-09). */
        val manualLockedDate: String = "",
        /** Ngày hôm nay ("yyyy-MM-dd") để so với manualLockedDate. */
        val todayDate: String = ""
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

    /** Khóa tay chỉ có tác dụng trong ngày bật nó — sang hôm sau tự reset
     * về mở (2026-11-09). Ngày trống (dữ liệu cũ chưa ghi ngày) cũng coi
     * như hết hạn. */
    fun isManualLockedToday(input: Input): Boolean =
        input.manualLocked && input.manualLockedDate.isNotEmpty() &&
            input.manualLockedDate == input.todayDate

    /**
     * HÀM DUY NHẤT quyết định khóa hay mở (2026-11-09 — gom về 1 chỗ,
     * bỏ shouldShowLock riêng lẻ). Check lần lượt:
     *  1. trong giờ khóa (lịch)? → khóa.
     *  2. còn giờ xem trong ngày không? hết giờ → khóa.
     *  3. khóa tay (trong ngày)? → khóa.
     * Tất cả đều không → mở.
     * Mở tạm (mã thưởng / +N phút) và app thời sự trong khung thời sự
     * ghi đè lên trên (mở), vì đó là ngoại lệ đã có từ trước.
     */
    fun isLockedNow(input: Input): Boolean {
        // Mở tạm ghi đè mọi lý do khóa.
        if (input.nowMs < input.unlockUntilMs) return false
        // App thời sự trong khung giờ thời sự — miễn trừ tuyệt đối.
        if (isNewsExempt(input.lastPkg, input)) return false
        // 1. Trong giờ khóa (lịch)?
        if (input.lockedBySchedule) return true
        // 2. Hết giờ xem trong ngày (không còn giờ)?
        val limit = input.dailyLimitMin
        if (limit > 0 && input.usedTodayMin >= limit && input.bonusMin <= 0) return true
        // 3. Khóa tay (chỉ trong ngày)?
        if (isManualLockedToday(input)) return true
        // Tất cả đều không → mở.
        return false
    }

    fun lockReason(input: Input): String {
        if (isManualLockedToday(input) && input.nowMs >= input.unlockUntilMs) return "Đã khóa"
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
        if (isManualLockedToday(input)) return "Đã khóa"
        if (input.lockedBySchedule) return "Đến giờ khóa (${input.scheduleReason})"
        val limit = input.dailyLimitMin
        if (limit > 0 && input.usedTodayMin >= limit && input.bonusMin <= 0)
            return "Hết giờ xem — đã hết cả phút thưởng"
        return null
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

    /**
     * Max elapsed time counted in one step (5 min). The tick is 1s, so a
     * bigger gap means the system was suspended (TV asleep) — don't count
     * a whole night of sleep as viewing time (2026-11-09).
     */
    const val MAX_COUNT_GAP_MS = 5 * 60 * 1000L

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
        // Clamp the gap: a suspended system (TV asleep) must not turn
        // hours of sleep into viewing minutes.
        val elapsed = minOf(input.nowMs - start, MAX_COUNT_GAP_MS)
        val total = input.remainderMs + elapsed
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
