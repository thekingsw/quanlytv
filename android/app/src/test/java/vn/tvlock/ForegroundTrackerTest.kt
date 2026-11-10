package vn.tvlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.tvlock.ForegroundTracker.Detected
import vn.tvlock.ForegroundTracker.Win

/**
 * Test cho ForegroundTracker (hàm thuần). Ghim các lỗi 06–09/11:
 *  - lastPkg kẹt ở app hệ thống nên đếm giờ dừng / màn khóa không hiện
 *  - vào Settings / launcher mà vẫn bị tính giờ YouTube
 *  - quét cửa sổ nền làm màn khóa nhấp nháy 88 lần
 */
class ForegroundTrackerTest {

    companion object {
        const val OWN = "vn.tvlock"
        const val YT = "com.google.android.youtube.tv"
        const val SYSUI = "com.android.systemui"
        const val SETTINGS = "com.android.settings"
        const val LAUNCHER = "com.google.android.tvlauncher"
    }

    // ---- sự kiện cửa sổ ----

    @Test fun systemuiEventIsIgnored() {
        // Thanh âm lượng / bảng thông báo nằm TRÊN video: không được đổi foreground.
        assertTrue(ForegroundTracker.shouldIgnoreEvent(SYSUI))
    }

    @Test fun emptyPackageEventIsIgnored() {
        assertTrue(ForegroundTracker.shouldIgnoreEvent(""))
    }

    @Test fun realAppEventIsNotIgnored() {
        assertFalse(ForegroundTracker.shouldIgnoreEvent(YT))
    }

    @Test fun settingsAndLauncherEventsAreRealScreens() {
        // Vào Cài đặt / launcher là KHÔNG còn xem YouTube -> phải đổi foreground
        // để dừng đếm giờ (agent bản trước bỏ qua nên vẫn tính giờ YouTube).
        assertFalse(ForegroundTracker.shouldIgnoreEvent(SETTINGS))
        assertFalse(ForegroundTracker.shouldIgnoreEvent(LAUNCHER))
        assertFalse(ForegroundTracker.shouldIgnoreEvent("com.google.android.apps.tv.launcherx"))
    }

    @Test fun ownPackageEventIsNotIgnored() {
        // Màn khóa / màn hình chính phải ghi vào lastPkg để lockPoll biết đã hiện.
        assertFalse(ForegroundTracker.shouldIgnoreEvent(OWN))
    }

    @Test fun settingsStopsCountingButLockStillShows() {
        // lastPkg = Settings (app hệ thống): không tính giờ...
        val i = LockPolicy.Input(nowMs = 1_000L, lastPkg = SETTINGS, usedTodayMin = 10, dailyLimitMin = 90)
        assertFalse(LockPolicy.isCountingNow(i))
        // ...nhưng khi TV đang khóa thì màn khóa vẫn đè lên Settings (không phải đường bypass).
        val locked = i.copy(lockedBySchedule = true, scheduleReason = "21:00–07:00")
        assertTrue(LockPolicy.evaluate(locked) { it }.verdict is LockPolicy.Verdict.Lock)
    }

    // ---- quét cửa sổ ----

    @Test fun focusedRealAppWins() {
        val d = ForegroundTracker.detect(listOf(Win(YT, true), Win(LAUNCHER, false)), OWN)
        assertEquals(Detected.App(YT), d)
    }

    @Test fun focusedOwnWindowIsRecordedSoLockPollKnowsLockIsUp() {
        val d = ForegroundTracker.detect(listOf(Win(OWN, true), Win(YT, false)), OWN)
        assertEquals(Detected.App(OWN), d)
    }

    @Test fun focusedWindowPresentNeverScansBackgroundWindows() {
        // Cửa sổ focus của chính mình + app nền YouTube: phải là OWN, KHÔNG được
        // nhận YouTube phía sau (lỗi màn khóa nhấp nháy 88 lần, 08/11).
        val d = ForegroundTracker.detect(listOf(Win(OWN, true), Win(YT, false)), OWN)
        assertFalse(d == Detected.App(YT))
    }

    @Test fun focusedSystemuiOverlayFallsThroughToNextFocused() {
        val d = ForegroundTracker.detect(listOf(Win(SYSUI, true), Win(YT, true)), OWN)
        assertEquals(Detected.App(YT), d)
    }

    @Test fun onlyFocusedOverlayMeansUnknown() {
        // Có cửa sổ focus nhưng chỉ là overlay -> giữ nguyên giá trị cũ, không quét nền.
        val d = ForegroundTracker.detect(listOf(Win(SYSUI, true), Win(YT, false)), OWN)
        assertEquals(Detected.Unknown, d)
    }

    @Test fun focusedSettingsIsRecordedAsForeground() {
        val d = ForegroundTracker.detect(listOf(Win(SETTINGS, true)), OWN)
        assertEquals(Detected.App(SETTINGS), d)
    }

    @Test fun noFocusedWindowPicksFirstRealApp() {
        val wins = listOf(Win(SYSUI, false), Win(OWN, false), Win(LAUNCHER, false), Win(YT, false))
        assertEquals(Detected.App(YT), ForegroundTracker.detect(wins, OWN))
    }

    @Test fun noFocusedWindowAndOnlySystemAppsIsUnknown() {
        val wins = listOf(Win(SYSUI, false), Win(OWN, false), Win(SETTINGS, false))
        assertEquals(Detected.Unknown, ForegroundTracker.detect(wins, OWN))
    }

    @Test fun focusedWindowWithNullPackageStillCountsAsFocused() {
        // Cửa sổ focus không đọc được package (root null) + app nền: Unknown, không quét nền.
        val d = ForegroundTracker.detect(listOf(Win("", true), Win(YT, false)), OWN)
        assertEquals(Detected.Unknown, d)
    }

    @Test fun emptyWindowListIsUnknown() {
        assertEquals(Detected.Unknown, ForegroundTracker.detect(emptyList(), OWN))
    }
}
