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
        val todayDate: String = "",
        /**
         * Phiên PIN của phụ huynh (có hạn): vừa nhập ĐÚNG PIN để vào Cài đặt thì khóa
         * toàn TV tạm nhường tới mốc này để chỉnh cài đặt. Thay cho cách cũ "lastPkg ==
         * vn.tvlock thì không khóa" — lastPkg do Trợ năng cập nhật nên có thể CŨ (kẹt ở
         * vn.tvlock) và biến thành lỗ hổng: TV mở khóa vĩnh viễn (sự cố tối 10/11).
         */
        val adminSessionUntilMs: Long = 0L
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
        val newsEndMin: Int = 0,
        val appRemainderMs: Long = 0L
    )

    data class CountResult(
        val countedMin: Int,
        val usedTodayMin: Int,
        val bonusMin: Int,
        val remainderMs: Long,
        val perAppAddMin: Int,
        val appRemainderMs: Long = 0L
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
        val noChange = CountResult(
            0, input.usedTodayMin, input.bonusMin, input.remainderMs, 0, input.appRemainderMs
        )
        if (input.pkg.isEmpty() || isExcluded(input.pkg)) return noChange
        val start = input.lastPkgStartMs
        if (start <= 0L || input.nowMs <= start) return noChange
        if (isNewsExempt(input.pkg, input.newsPkg, input.nowMs, input.newsStartMin, input.newsEndMin))
            return noChange

        val elapsed = input.nowMs - start
        val appTotal = input.appRemainderMs + elapsed
        val perAppAddMin = (appTotal / 60000L).toInt()
        val newAppRemainder = appTotal % 60000L

        // A free app contributes to its own counter, but never to the shared
        // daily counter or its fractional remainder.
        if (input.appRuleMode == "free") {
            return CountResult(
                perAppAddMin, input.usedTodayMin, input.bonusMin,
                input.remainderMs, perAppAddMin, newAppRemainder
            )
        }

        val dailyTotal = input.remainderMs + elapsed
        val mins = (dailyTotal / 60000L).toInt()
        val newRemainder = dailyTotal % 60000L
        if (mins <= 0) {
            return CountResult(
                0, input.usedTodayMin, input.bonusMin, newRemainder,
                perAppAddMin, newAppRemainder
            )
        }
        var used = input.usedTodayMin
        var bonus = input.bonusMin
        val limit = input.dailyLimitMin
        if (limit <= 0) {
            used += mins
            return CountResult(mins, used, bonus, newRemainder, perAppAddMin, newAppRemainder)
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
        return CountResult(mins, used, bonus, newRemainder, perAppAddMin, newAppRemainder)
    }

    // ====================================================================
    // QUYẾT ĐỊNH DUY NHẤT: có hiện màn khóa không, và khóa kiểu nào.
    //
    // Trước đây câu hỏi này được trả lời ở ~8 chỗ (GuardService x2, Trợ năng x3,
    // HomeActivity x3), mỗi chỗ tự ghép điều kiện nên lệch nhau: có chỗ có chốt
    // "đang ở app mình thì đừng mở khóa", có chỗ không.
    // QUY TẮC: muốn đổi luật khóa -> sửa [evaluate] + thêm test ở LockDecisionTest.
    // Đừng thêm điều kiện khóa ở nơi khác (ArchitectureTest sẽ báo đỏ).
    // ====================================================================

    sealed class Verdict {
        /** Không cần hiện màn khóa. */
        object Allow : Verdict()
        /**
         * Hiện màn khóa. [wholeTv] = true khi khóa TOÀN TV (khóa tay / lịch / hết
         * giờ ngày) -> màn khóa tự đóng khi hết khóa; false khi chỉ chặn riêng
         * 1 app (app bị chặn / hết giờ app).
         */
        data class Lock(val reason: String, val wholeTv: Boolean) : Verdict()
    }

    /** [wholeLocked] = TV đang khóa toàn bộ (kể cả khi verdict là Allow vì đang ở app mình). */
    data class Decision(val wholeLocked: Boolean, val verdict: Verdict)

    /**
     * Quyết định cho app foreground hiện tại ([Input.lastPkg]). Thứ tự (đừng đổi
     * nếu không có test mới):
     *  1. đang mở tạm (PIN / mã thưởng / +phút)   -> Allow
     *  2. TV khóa toàn bộ + phụ huynh đang trong phiên PIN (có hạn) -> Allow, để chỉnh
     *     Cài đặt của app / hệ thống mà không bị màn khóa đè lên.
     *  3. TV đang khóa toàn bộ                    -> Lock(whole), BẤT KỂ lastPkg là gì:
     *     YouTube, Cài đặt, launcher, RỖNG, hay chính vn.tvlock. Lý do: lastPkg do Trợ
     *     năng cập nhật (sự kiện đổi cửa sổ) nên có thể CŨ; nếu khóa phụ thuộc nó thì chỉ
     *     cần một sự kiện bị lỡ (vd quay lại TVMate từ màn PIN Cài đặt) là TV mở vĩnh viễn
     *     dù web báo "đóng" (sự cố 10/11). KHÔNG được thêm lại chốt "lastPkg == ownPkg
     *     thì Allow" ở bước này — phiên PIN ở bước 2 mới là cách đúng, vì nó có hạn.
     *     App thời sự trong khung thời sự đã được [isLockedNow] miễn trừ từ trước.
     *  4. chính app mình / app hệ thống (không bị tính giờ) -> Allow (chỉ cho quy tắc
     *     riêng từng app; khóa toàn TV đã xử lý ở trên)
     *  5. app thời sự trong khung thời sự         -> Allow
     *  6. quy tắc riêng của app / hết giờ app     -> Lock(per-app)
     *  7. còn lại                                 -> Allow
     */
    fun evaluate(input: Input, labelOf: (String) -> String): Decision {
        val whole = isLockedNow(input)
        val pkg = input.lastPkg
        val verdict: Verdict = when {
            input.nowMs < input.unlockUntilMs -> Verdict.Allow
            whole && input.nowMs < input.adminSessionUntilMs -> Verdict.Allow
            whole -> Verdict.Lock(lockReason(input), true)
            pkg == input.ownPkg || isExcluded(pkg) -> Verdict.Allow
            isNewsExempt(pkg, input) -> Verdict.Allow
            else -> {
                val r = blockReason(pkg, labelOf(pkg), input)
                if (r != null) Verdict.Lock(r, false) else Verdict.Allow
            }
        }
        return Decision(whole, verdict)
    }

    // ---- Chống đếm nhầm khi hệ thống bị treo (TV ngủ / standby) ----

    /**
     * Tick chạy mỗi 1 giây. Khoảng cách giữa 2 lần cộng giờ dài hơn ngưỡng này
     * nghĩa là hệ thống đã bị treo (TV ngủ, tiến trình bị đóng băng), KHÔNG phải trẻ
     * xem liên tục -> bỏ qua hẳn khoảng đó. (Kẹp xuống 5 phút thay vì bỏ qua thì mỗi
     * lần TV ngủ/thức vẫn bị tính oan 5 phút giờ xem.)
     */
    const val MAX_TICK_GAP_MS = 60_000L

    /** true nếu khoảng [startMs, nowMs] là do hệ thống treo, không được tính giờ. */
    fun isSuspendGap(startMs: Long, nowMs: Long): Boolean =
        startMs > 0L && nowMs - startMs > MAX_TICK_GAP_MS

    /** [addElapsed] có chặn khoảng treo. Store dùng hàm này, không gọi [addElapsed] trực tiếp. */
    fun addElapsedGuarded(input: CountInput): CountResult =
        if (isSuspendGap(input.lastPkgStartMs, input.nowMs))
            CountResult(0, input.usedTodayMin, input.bonusMin, input.remainderMs, 0,
                input.appRemainderMs)
        else addElapsed(input)
}
