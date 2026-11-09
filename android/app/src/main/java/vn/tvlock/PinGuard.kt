package vn.tvlock

/**
 * Shared brute-force guard for every code the TV asks for: the TV lock PIN,
 * the web PIN and the reward code.
 *
 * (2026-11-09) Trước đây chỉ mã thưởng có chống đoán mò (sai 5 lần thì
 * khóa 5 phút), còn PIN khóa TV — cái mở khóa vô hạn — thì không có gì
 * chặn. Giờ cả 3 loại mã dùng chung một cơ chế này.
 *
 * Pure: state is an explicit [State] value; Store persists one State per
 * code kind under its own prefs keys, WebServer keeps one in memory.
 */
object PinGuard {
    const val MAX_FAILS = 5
    const val LOCK_MS = 5 * 60 * 1000L

    data class State(val fails: Int = 0, val lockedUntilMs: Long = 0L)

    fun isLocked(state: State, nowMs: Long): Boolean = nowMs < state.lockedUntilMs

    fun remainingLockMs(state: State, nowMs: Long): Long =
        (state.lockedUntilMs - nowMs).coerceAtLeast(0L)

    /** Record a failed attempt. At MAX_FAILS, lock for LOCK_MS and reset the counter. */
    fun recordFailure(state: State, nowMs: Long): State {
        val fails = state.fails + 1
        return if (fails >= MAX_FAILS) State(0, nowMs + LOCK_MS)
        else State(fails, state.lockedUntilMs)
    }

    /** Record a success — clears the failure count (an active lock stays until it expires). */
    fun recordSuccess(state: State): State = State(0, state.lockedUntilMs)
}
