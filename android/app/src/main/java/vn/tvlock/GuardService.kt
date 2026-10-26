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
            val blocked = (store.isInNewsSlot(now) && store.newsPkg.isNotEmpty() && pkg != store.newsPkg) ||
                (now >= store.unlockUntilMs && store.isLockedNow(now))
            if (blocked) {
                val i = Intent(this, LockActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                i.putExtra("reason", store.lockReason(now))
                i.putExtra("mode", "lock")
                try { startActivity(i) } catch (_: Exception) { }
            }
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        try { nm.notify(1, buildNotification()) } catch (_: Exception) { }
    }

    private fun buildNotification(): Notification {
        store.resetUsedIfNewDay()
        val locked = store.isLockedNow(System.currentTimeMillis())
        val rem = store.remainingMin()
        val text = if (locked) "TV đang khóa" else if (store.dailyLimitMin > 0)
            "Đã xem ${store.usedTodayMin}/${store.dailyLimitMin} phút — còn lại $rem phút"
        else "Đã xem ${store.usedTodayMin} phút hôm nay"
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
