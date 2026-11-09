package vn.tvlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for PinGuard (pure logic). Run with:
 *   bash ~/workspace/manual-build/run_tvlock_tests.sh
 *
 * One shared brute-force guard for the TV lock PIN, the web PIN and the
 * reward code: 5 wrong attempts lock input for 5 minutes (2026-11-09).
 * Before this, the TV lock PIN — the one that unlocks forever — had no
 * lockout at all.
 */
class PinGuardTest {

    companion object {
        const val T0 = 1_000_000L
    }

    @Test fun freshStateNotLocked() {
        assertFalse(PinGuard.isLocked(PinGuard.State(), T0))
        assertEquals(0L, PinGuard.remainingLockMs(PinGuard.State(), T0))
    }

    @Test fun fourFailsNotLocked() {
        var s = PinGuard.State()
        repeat(4) { s = PinGuard.recordFailure(s, T0) }
        assertFalse(PinGuard.isLocked(s, T0))
        assertEquals(4, s.fails)
    }

    @Test fun fiveFailsLocksFiveMinutes() {
        var s = PinGuard.State()
        repeat(5) { s = PinGuard.recordFailure(s, T0) }
        assertTrue(PinGuard.isLocked(s, T0))
        assertEquals(5 * 60 * 1000L, PinGuard.remainingLockMs(s, T0))
        // The failure counter resets once the lock engages.
        assertEquals(0, s.fails)
    }

    @Test fun lockExpiresAfterFiveMinutes() {
        var s = PinGuard.State()
        repeat(5) { s = PinGuard.recordFailure(s, T0) }
        assertTrue(PinGuard.isLocked(s, T0 + 4 * 60 * 1000L))
        assertFalse(PinGuard.isLocked(s, T0 + 5 * 60 * 1000L))
        assertFalse(PinGuard.isLocked(s, T0 + 6 * 60 * 1000L))
    }

    @Test fun successResetsFailures() {
        var s = PinGuard.State()
        repeat(3) { s = PinGuard.recordFailure(s, T0) }
        s = PinGuard.recordSuccess(s)
        assertEquals(0, s.fails)
        assertFalse(PinGuard.isLocked(s, T0))
    }

    @Test fun failuresAfterLockCountFresh() {
        var s = PinGuard.State()
        repeat(5) { s = PinGuard.recordFailure(s, T0) }
        assertTrue(PinGuard.isLocked(s, T0))
        // Failures during the lock start a fresh count after expiry.
        val after = T0 + 5 * 60 * 1000L + 1000
        assertFalse(PinGuard.isLocked(s, after))
        s = PinGuard.recordFailure(s, after)
        assertEquals(1, s.fails)
        assertFalse(PinGuard.isLocked(s, after))
    }

    @Test fun remainingLockMsDecreases() {
        var s = PinGuard.State()
        repeat(5) { s = PinGuard.recordFailure(s, T0) }
        assertEquals(4 * 60 * 1000L, PinGuard.remainingLockMs(s, T0 + 60 * 1000L))
    }
}
