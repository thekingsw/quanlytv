package vn.tvlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ForegroundTracker (pure logic). Run with:
 *   bash ~/workspace/manual-build/run_tvlock_tests.sh
 *
 * These tests pin down the regressions from 2026-11-06..09:
 *  - lastPkg got stuck at a system app (systemui/settings) because
 *    onAccessibilityEvent assigned before checking excluded — counting
 *    stopped and the lock screen never showed
 *  - re-reporting the same app reset its start time, losing viewed time
 */
class ForegroundTrackerTest {

    companion object {
        const val OWN = "vn.tvlock"
        const val YT = "com.google.android.youtube.tv"
        const val SYSUI = "com.android.systemui"
        const val SETTINGS = "com.android.settings"
        const val LAUNCHER = "com.google.android.tvlauncher"
        const val T0 = 1_000_000L
    }

    @Test fun realAppOverwrites() {
        val s = ForegroundTracker.noteForeground(ForegroundTracker.State(), YT, OWN, T0)
        assertEquals(YT, s.lastPkg)
        assertEquals(T0, s.lastPkgStartMs)
    }

    @Test fun systemuiDoesNotOverwrite() {
        val before = ForegroundTracker.State(YT, T0)
        val s = ForegroundTracker.noteForeground(before, SYSUI, OWN, T0 + 1000)
        assertEquals(YT, s.lastPkg)
        assertEquals(T0, s.lastPkgStartMs)
    }

    @Test fun settingsDoesNotOverwrite() {
        val before = ForegroundTracker.State(YT, T0)
        val s = ForegroundTracker.noteForeground(before, SETTINGS, OWN, T0 + 1000)
        assertEquals(YT, s.lastPkg)
        assertEquals(T0, s.lastPkgStartMs)
    }

    @Test fun launcherDoesNotOverwrite() {
        val before = ForegroundTracker.State(YT, T0)
        val s = ForegroundTracker.noteForeground(before, LAUNCHER, OWN, T0 + 1000)
        assertEquals(YT, s.lastPkg)
        assertEquals(T0, s.lastPkgStartMs)
    }

    @Test fun ownPkgDoesOverwrite() {
        val before = ForegroundTracker.State(YT, T0)
        val s = ForegroundTracker.noteForeground(before, OWN, OWN, T0 + 1000)
        assertEquals(OWN, s.lastPkg)
        assertEquals(T0 + 1000, s.lastPkgStartMs)
    }

    @Test fun sameAppKeepsOriginalStart() {
        val before = ForegroundTracker.State(YT, T0)
        val s = ForegroundTracker.noteForeground(before, YT, OWN, T0 + 5000)
        assertEquals(YT, s.lastPkg)
        assertEquals(T0, s.lastPkgStartMs)
    }

    @Test fun emptyPkgDoesNotChange() {
        val before = ForegroundTracker.State(YT, T0)
        val s = ForegroundTracker.noteForeground(before, "", OWN, T0 + 1000)
        assertEquals(before, s)
    }

    @Test fun switchingAppsResetsStart() {
        val before = ForegroundTracker.State(YT, T0)
        val s = ForegroundTracker.noteForeground(before, "com.netflix.ninja", OWN, T0 + 60000)
        assertEquals("com.netflix.ninja", s.lastPkg)
        assertEquals(T0 + 60000, s.lastPkgStartMs)
    }

    @Test fun clearIfExcludedClearsStuckSystemApp() {
        val before = ForegroundTracker.State(SYSUI, T0)
        val s = ForegroundTracker.clearIfExcluded(before, OWN, T0 + 1000)
        assertEquals("", s.lastPkg)
    }

    @Test fun clearIfExcludedKeepsRealApp() {
        val before = ForegroundTracker.State(YT, T0)
        val s = ForegroundTracker.clearIfExcluded(before, OWN, T0 + 1000)
        assertEquals(before, s)
    }

    @Test fun clearIfExcludedKeepsOwnPkg() {
        val before = ForegroundTracker.State(OWN, T0)
        val s = ForegroundTracker.clearIfExcluded(before, OWN, T0 + 1000)
        assertEquals(before, s)
    }

    @Test fun clearIfExcludedKeepsEmpty() {
        val before = ForegroundTracker.State("", 0L)
        val s = ForegroundTracker.clearIfExcluded(before, OWN, T0)
        assertEquals(before, s)
    }
}
