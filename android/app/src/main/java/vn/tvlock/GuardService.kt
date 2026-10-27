package vn.tvlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class GuardService : Service() {
    private lateinit var store: Store
    private var web: WebServer? = null
    private var tickThread: Thread? = null
    @Volatile private var running = false

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("guard", "Khóa TV", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        startForeground(1, buildNotification())
        try { web = WebServer(this, 8080).also { it.start() } } catch (_: Exception) { }
        running = true
        tickThread = Thread({
            while (running) {
                try {
                    Thread.sleep(15000)
                    tick()
                } catch (_: InterruptedException) { break } catch (_: Exception) { }
            }
        }, "tvlock-tick").also { it.isDaemon = true; it.start() }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        store.tick(now)
        val pkg = store.lastPkg
        if (pkg.isNotEmpty() && !store.isExcluded(pkg) && pkg != packageName) {
            val reason = store.blockReason(pkg, now, store.appLabel(pkg))
            if (reason != null) {
                val i = Intent(this, LockActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                i.putExtra("reason", reason)
                i.putExtra("mode", "lock")
                try { startActivity(i) } catch (_: Exception) { }
            }
        }
        warnIfNeeded(now)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        try { nm.notify(1, buildNotification()) } catch (_: Exception) { }
    }

    /**
     * v3: gentle warning when 5 or 1 minutes remain before the daily limit
     * (including bonus), the current app's own limit, or the lock window
     * starts. Each threshold fires once per deadline; never launches the
     * lock screen and never counts as viewing (WarningActivity is vn.tvlock).
     */
    private fun warnIfNeeded(now: Long) {
        if (store.lastPkg == packageName) return // a tvlock screen is already showing
        if (now < store.unlockUntilMs || store.isLockedNow(now)) return
        val pkg = store.lastPkg
        // 1) Current app's own limit.
        if (pkg.isNotEmpty() && !store.isExcluded(pkg) && pkg != packageName) {
            val appRem = store.appLimitRemainingMin(pkg)
            if (appRem in 1..5) {
                val kind = "app:$pkg"
                if (!store.warned(kind, appRem)) {
                    store.markWarned(kind, appRem)
                    launchWarning(
                        "Còn $appRem phút nữa là hết giờ cho app ${store.appLabel(pkg)}",
                        "Hết giờ app này thì vẫn xem được app khác."
                    )
                    return
                }
            }
        }
        // 2) Daily limit (including bonus pool).
        val rem = store.remainingMin()
        if (rem in 1..5 && !store.warned("daily", rem)) {
            store.markWarned("daily", rem)
            val sub = if (store.bonusMin > 0) "Trong đó có ${store.bonusMin} phút thưởng."
                else "Bố có thể cộng thêm giờ từ điện thoại."
            launchWarning("Còn $rem phút nữa là hết giờ xem", sub)
            return
        }
        // 3) Next lock schedule start.
        val untilLock = store.minutesUntilNextLock(now)
        if (untilLock in 1..5 && !store.warned("lockwindow", untilLock)) {
            store.markWarned("lockwindow", untilLock)
            launchWarning(
                "Còn $untilLock phút nữa là đến giờ khóa TV",
                "Nhớ lưu lại việc đang xem nhé!"
            )
        }
    }

    private fun launchWarning(message: String, sub: String) {
        val i = Intent(this, WarningActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        i.putExtra("message", message)
        i.putExtra("sub", sub)
        try { startActivity(i) } catch (_: Exception) { }
    }

    private fun buildNotification(): Notification {
        store.resetUsedIfNewDay()
        val locked = store.isLockedNow(System.currentTimeMillis())
        val rem = store.remainingMin()
        val bonus = if (store.bonusMin > 0) " • thưởng còn ${store.bonusMin} phút" else ""
        val limit = store.limitForToday()
        val text = if (locked) "TV đang khóa" else if (limit > 0)
            "Đã xem ${store.usedTodayMin}/$limit phút — còn lại $rem phút$bonus"
        else "Đã xem ${store.usedTodayMin} phút hôm nay$bonus"
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, "guard") else Notification.Builder(this)
        return b.setContentTitle("Khóa TV").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi).setOngoing(true).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        tickThread?.interrupt()
        try { web?.stop() } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
