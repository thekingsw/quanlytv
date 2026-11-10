package vn.tvlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.tvlock.LockPolicy.Verdict
import java.util.Calendar
import java.util.Random

/**
 * Test cho LockPolicy.evaluate — "QUYẾT ĐỊNH DUY NHẤT" hiện / không hiện màn khóa.
 * Mỗi test ghim một ca đã từng gây lỗi hoặc một quy tắc thứ tự ưu tiên.
 */
class LockDecisionTest {

    companion object {
        const val OWN = "vn.tvlock"
        const val YT = "com.google.android.youtube.tv"
        const val NEWS = "ar.tvplayer.tv"
        const val SETTINGS = "com.android.settings"
        const val LAUNCHER = "com.google.android.tvlauncher"
        const val SYSUI = "com.android.systemui"
    }

    private fun msAt(h: Int, m: Int): Long {
        val c = Calendar.getInstance()
        c.set(2026, Calendar.NOVEMBER, 9, h, m, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun base(pkg: String = YT) = LockPolicy.Input(nowMs = msAt(20, 0), lastPkg = pkg)
    private fun locked(pkg: String = YT) = base(pkg).copy(lockedBySchedule = true, scheduleReason = "21:00–07:00")
    private fun eval(i: LockPolicy.Input) = LockPolicy.evaluate(i) { it }

    // ---- ca cơ bản ----

    @Test fun notLockedAllows() {
        val d = eval(base())
        assertFalse(d.wholeLocked)
        assertEquals(Verdict.Allow, d.verdict)
    }

    @Test fun scheduleLockShowsWholeTvLock() {
        val d = eval(locked())
        assertTrue(d.wholeLocked)
        val v = d.verdict as Verdict.Lock
        assertTrue(v.wholeTv)
        assertTrue(v.reason.contains("21:00"))
    }

    @Test fun manualLockToday() {
        val i = base().copy(manualLocked = true, manualLockedDate = "2026-11-09", todayDate = "2026-11-09")
        assertTrue((eval(i).verdict as Verdict.Lock).wholeTv)
    }

    @Test fun manualLockFromYesterdayDoesNotLock() {
        val i = base().copy(manualLocked = true, manualLockedDate = "2026-11-08", todayDate = "2026-11-09")
        assertEquals(Verdict.Allow, eval(i).verdict)
    }

    @Test fun dailyLimitReachedLocks() {
        val i = base().copy(usedTodayMin = 90, dailyLimitMin = 90)
        assertTrue((eval(i).verdict as Verdict.Lock).wholeTv)
    }

    @Test fun dailyLimitReachedButBonusLeftAllows() {
        val i = base().copy(usedTodayMin = 90, dailyLimitMin = 90, bonusMin = 5)
        assertEquals(Verdict.Allow, eval(i).verdict)
    }

    // ---- chốt "đang ở app mình" (lỗi hồi quy của agent bản trước) ----

    @Test fun ownPackageNeverGetsLockLaunchedEvenWhenTvLocked() {
        // Trong giờ khóa, phụ huynh vào Cài đặt của app (sau khi nhập PIN) hoặc màn khóa
        // đang hiện: KHÔNG được mở màn khóa đè lên (GuardService từng làm vậy, mỗi 3 giây).
        val d = eval(locked(OWN))
        assertTrue("TV vẫn đang khóa toàn bộ", d.wholeLocked)
        assertEquals(Verdict.Allow, d.verdict)
    }

    // ---- Cài đặt / launcher hệ thống không phải đường bypass ----

    @Test fun lockShowsOverSystemSettings() {
        assertTrue(eval(locked(SETTINGS)).verdict is Verdict.Lock)
    }

    @Test fun lockShowsOverSystemLauncher() {
        assertTrue(eval(locked(LAUNCHER)).verdict is Verdict.Lock)
        assertTrue(eval(locked("com.google.android.apps.tv.launcherx")).verdict is Verdict.Lock)
    }

    @Test fun lockShowsOverSystemui() {
        assertTrue(eval(locked(SYSUI)).verdict is Verdict.Lock)
    }

    @Test fun systemAppsAreAllowedWhenNotLocked() {
        for (p in listOf(SETTINGS, LAUNCHER, SYSUI)) assertEquals(p, Verdict.Allow, eval(base(p)).verdict)
    }

    // ---- ngoại lệ ----

    @Test fun tempUnlockOverridesEverything() {
        val i = locked().copy(unlockUntilMs = msAt(20, 5))
        val d = eval(i)
        assertFalse(d.wholeLocked)
        assertEquals(Verdict.Allow, d.verdict)
    }

    @Test fun tempUnlockExpiredLocksAgain() {
        val i = locked().copy(unlockUntilMs = msAt(19, 59))
        assertTrue(eval(i).verdict is Verdict.Lock)
    }

    @Test fun newsAppInNewsSlotIsExemptEvenWhenLocked() {
        val i = locked(NEWS).copy(newsPkg = NEWS, newsStartMin = 19 * 60 + 30, newsEndMin = 20 * 60 + 30)
        assertEquals(Verdict.Allow, eval(i).verdict)
    }

    @Test fun newsAppOutsideNewsSlotIsLocked() {
        val i = locked(NEWS).copy(newsPkg = NEWS, newsStartMin = 18 * 60, newsEndMin = 19 * 60)
        assertTrue(eval(i).verdict is Verdict.Lock)
    }

    @Test fun emptyForegroundWhenLockedStillLocks() {
        // Không biết app nào mà TV đang khóa -> khóa (fail-safe).
        assertTrue(eval(locked("")).verdict is Verdict.Lock)
    }

    @Test fun emptyForegroundWhenNotLockedAllows() {
        assertEquals(Verdict.Allow, eval(base("")).verdict)
    }

    // ---- chặn riêng từng app ----

    @Test fun blockedAppLocksOnlyThatApp() {
        val i = base().copy(appRule = LockPolicy.AppRule("blocked", 0))
        val v = eval(i).verdict as Verdict.Lock
        assertFalse("chặn riêng 1 app KHÔNG phải khóa toàn TV", v.wholeTv)
    }

    @Test fun perAppLimitReachedLocksThatApp() {
        val i = base().copy(appRule = LockPolicy.AppRule("limit", 30), perAppUsedMin = 30)
        assertFalse((eval(i).verdict as Verdict.Lock).wholeTv)
    }

    @Test fun perAppLimitNotReachedAllows() {
        val i = base().copy(appRule = LockPolicy.AppRule("limit", 30), perAppUsedMin = 29)
        assertEquals(Verdict.Allow, eval(i).verdict)
    }

    @Test fun wholeTvLockOutranksPerAppRule() {
        val i = locked().copy(appRule = LockPolicy.AppRule("blocked", 0))
        assertTrue((eval(i).verdict as Verdict.Lock).wholeTv)
    }

    // ---- fuzz: evaluate == hành vi cũ gộp từ GuardService + lockPoll (đã thêm chốt app mình) ----

    private fun oldBehaviour(i: LockPolicy.Input): Verdict {
        if (i.nowMs < i.unlockUntilMs) return Verdict.Allow
        val pkg = i.lastPkg
        if (pkg == i.ownPkg) return Verdict.Allow
        if (LockPolicy.isLockedNow(i)) return Verdict.Lock(LockPolicy.lockReason(i), true)
        if (pkg.isNotEmpty() && !LockPolicy.isExcluded(pkg)) {
            val r = LockPolicy.blockReason(pkg, pkg, i)
            if (r != null) return Verdict.Lock(r, false)
        }
        return Verdict.Allow
    }

    @Test fun evaluateMatchesOldBehaviourOnRandomInputs() {
        val rnd = Random(42)
        val pkgs = listOf(YT, NEWS, "com.netflix.ninja", SETTINGS, LAUNCHER, SYSUI, OWN, "")
        val dates = listOf("", "2026-11-08", "2026-11-09")
        var mismatches = 0
        repeat(50_000) {
            val now = msAt(rnd.nextInt(24), rnd.nextInt(60))
            val pkg = pkgs[rnd.nextInt(pkgs.size)]
            val rule = if (pkg.isEmpty()) LockPolicy.AppRule() else when (rnd.nextInt(4)) {
                0 -> LockPolicy.AppRule("blocked", 0)
                1 -> LockPolicy.AppRule("limit", rnd.nextInt(60) + 1)
                2 -> LockPolicy.AppRule("free", 0)
                else -> LockPolicy.AppRule()
            }
            val input = LockPolicy.Input(
                nowMs = now, lastPkg = pkg,
                newsPkg = if (rnd.nextBoolean()) NEWS else "", newsStartMin = 19 * 60, newsEndMin = 20 * 60,
                manualLocked = rnd.nextInt(4) == 0, manualLockedDate = dates[rnd.nextInt(3)], todayDate = "2026-11-09",
                unlockUntilMs = if (rnd.nextInt(4) == 0) now + rnd.nextInt(600_000) - 100_000 else 0L,
                usedTodayMin = rnd.nextInt(150), dailyLimitMin = if (rnd.nextInt(5) == 0) 0 else rnd.nextInt(120) + 1,
                bonusMin = if (rnd.nextBoolean()) 0 else rnd.nextInt(20),
                lockedBySchedule = rnd.nextInt(6) == 0, scheduleReason = "x",
                appRule = rule, perAppUsedMin = rnd.nextInt(90))
            if (eval(input).verdict != oldBehaviour(input)) mismatches++
        }
        assertEquals("evaluate lệch hành vi cũ", 0, mismatches)
    }

    @Test fun wholeLockedFlagAlwaysEqualsIsLockedNow() {
        val rnd = Random(9)
        repeat(20_000) {
            val now = msAt(rnd.nextInt(24), rnd.nextInt(60))
            val input = LockPolicy.Input(nowMs = now, lastPkg = listOf(YT, OWN, SETTINGS, "")[rnd.nextInt(4)],
                manualLocked = rnd.nextBoolean(), manualLockedDate = "2026-11-09", todayDate = "2026-11-09",
                lockedBySchedule = rnd.nextInt(5) == 0, usedTodayMin = rnd.nextInt(100), dailyLimitMin = rnd.nextInt(90))
            assertEquals(LockPolicy.isLockedNow(input), eval(input).wholeLocked)
        }
    }
}
