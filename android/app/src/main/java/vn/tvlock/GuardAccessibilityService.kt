package vn.tvlock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class GuardAccessibilityService : AccessibilityService() {
    private lateinit var store: Store

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = Store(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (!::store.isInitialized) store = Store(this)
        val now = System.currentTimeMillis()
        val prev = store.lastPkg
        // Accumulate time for the previous foreground app.
        if (prev.isNotEmpty() && prev != pkg) {
            store.addElapsed(prev, now)
        }
        store.lastPkg = pkg
        store.lastPkgStartMs = now
        if (store.isExcluded(pkg) || pkg == packageName) return
        if (isBlocked(pkg, now)) {
            launchLock(store.lockReason(now))
        }
    }

    private fun isBlocked(pkg: String, now: Long): Boolean {
        // News slot: only the news app may run (when one is chosen).
        if (store.isInNewsSlot(now) && store.newsPkg.isNotEmpty() && pkg != store.newsPkg) return true
        if (now < store.unlockUntilMs) return false
        return store.isLockedNow(now)
    }

    private fun launchLock(reason: String) {
        // Don't relaunch if LockActivity is already foreground.
        if (store.lastPkg == packageName) return
        val i = Intent(this, LockActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        i.putExtra("reason", reason)
        i.putExtra("mode", "lock")
        try { startActivity(i) } catch (_: Exception) { }
    }

    override fun onInterrupt() {}
}
