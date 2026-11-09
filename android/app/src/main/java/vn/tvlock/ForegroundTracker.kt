package vn.tvlock

/**
 * Single source of truth for "which app is in the foreground".
 *
 * (2026-11-09) Trước đây lastPkg/lastPkgStartMs bị ghi từ 4+ chỗ
 * (onAccessibilityEvent, refreshForegroundApp, Store.tick,
 * GuardService.onCreate), mỗi chỗ một quy tắc "app nào hợp lệ" — nên
 * lastPkg kẹt ở systemui/settings (ghi trước khi kiểm tra excluded),
 * làm đếm giờ dừng và màn khóa không hiện. Giờ mọi chỗ ghi đều đi qua
 * đây, với MỘT quy tắc duy nhất.
 *
 * Quy tắc:
 *  - App hệ thống được miễn trừ (systemui, settings, launcher) KHÔNG ghi
 *    đè app đang theo dõi — giá trị miễn trừ chặn cả đếm giờ lẫn hiện
 *    màn khóa.
 *  - App của chính mình (vn.tvlock — màn khóa) CÓ ghi đè, để lockPoll
 *    biết màn khóa đang hiện mà đừng gọi lại.
 *  - Ghi lại cùng một app thì giữ nguyên mốc bắt đầu (không reset) —
 *    reset bừa làm mất thời gian đã xem.
 *  - Khoảng cách thời gian được kẹp ở LockPolicy.addElapsed
 *    (MAX_COUNT_GAP_MS): tick là 1 giây nên khoảng cách lớn nghĩa là hệ
 *    thống bị treo/ngủ — không tính cả đêm ngủ thành giờ xem.
 */
object ForegroundTracker {

    data class State(val lastPkg: String = "", val lastPkgStartMs: Long = 0L)

    /**
     * Record a newly-foregrounded package. Returns the (possibly unchanged)
     * state — callers must use the returned value.
     */
    fun noteForeground(state: State, pkg: String, ownPkg: String, nowMs: Long): State {
        if (pkg.isEmpty()) return state
        // The lock screen itself: record it so the lock poll knows the
        // lock is up and does not re-show it.
        if (pkg == ownPkg) {
            if (pkg == state.lastPkg) return state
            return State(pkg, nowMs)
        }
        // Excluded system apps never overwrite the tracked app.
        if (LockPolicy.isExcluded(pkg)) return state
        if (pkg == state.lastPkg) return state
        return State(pkg, nowMs)
    }

    /**
     * Repair a state stuck at an excluded app (written by older buggy
     * versions): empty it so the lock can show and counting can resume.
     * Returns the (possibly unchanged) state.
     */
    fun clearIfExcluded(state: State, ownPkg: String, nowMs: Long): State {
        if (state.lastPkg.isNotEmpty() && state.lastPkg != ownPkg &&
            LockPolicy.isExcluded(state.lastPkg)) {
            return State("", nowMs)
        }
        return state
    }
}
