package vn.tvlock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class GuardAccessibilityService : AccessibilityService() {
    private lateinit var store: Store

    private val warningListener = object : WarningBus.Listener {
        override fun showWarning(title: String, sub: String) = WarningOverlay.show(title, sub)
        override fun hideWarning() = WarningOverlay.hide()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = Store(this)
        // v1.7: nhận banner nhắc từ GuardService để vẽ overlay — chỉ service
        // trợ năng mới vẽ được cửa sổ TYPE_ACCESSIBILITY_OVERLAY.
        WarningOverlay.attach(this)
        WarningBus.listener = warningListener
    }

    override fun onDestroy() {
        WarningBus.listener = null
        WarningOverlay.detach()
        super.onDestroy()
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
        val reason = store.blockReason(pkg, now, store.appLabel(pkg))
        if (reason != null) launchLock(reason)
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
