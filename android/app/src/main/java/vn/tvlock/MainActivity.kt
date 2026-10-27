package vn.tvlock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var tvStatus: TextView
    private lateinit var tvUsed: TextView
    private lateinit var tvTimes: TextView
    private lateinit var tvWeb: TextView
    private lateinit var tvNews: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { updateUi(); handler.postDelayed(this, 5000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = Store(this)
        tvStatus = findViewById(R.id.tvStatus)
        tvUsed = findViewById(R.id.tvUsed)
        tvTimes = findViewById(R.id.tvTimes)
        tvWeb = findViewById(R.id.tvWeb)
        tvNews = findViewById(R.id.tvNews)
        findViewById<Button>(R.id.btnLock).setOnClickListener {
            store.manualLocked = true; store.unlockUntilMs = 0L
            Toast.makeText(this, "Đã khóa TV", Toast.LENGTH_SHORT).show(); updateUi()
        }
        findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            val i = Intent(this, LockActivity::class.java)
            i.putExtra("mode", "unlock"); i.putExtra("reason", "Mở khóa TV")
            startActivity(i)
        }
        val adds = listOf(R.id.btnAdd5 to 5, R.id.btnAdd10 to 10, R.id.btnAdd15 to 15, R.id.btnAdd20 to 20, R.id.btnAdd30 to 30)
        for ((id, min) in adds) {
            findViewById<Button>(id).setOnClickListener {
                val i = Intent(this, LockActivity::class.java)
                i.putExtra("mode", "add"); i.putExtra("minutes", min)
                i.putExtra("reason", "Cho thêm $min phút")
                startActivity(i)
            }
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnNews).setOnClickListener {
            val pkg = store.newsPkg
            if (pkg.isEmpty()) { Toast.makeText(this, "Chưa chọn app thời sự trong Cài đặt", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            val li = packageManager.getLaunchIntentForPackage(pkg)
            if (li != null) startActivity(li) else Toast.makeText(this, "Không mở được app thời sự", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        // Start the guard service (foreground) so ticking + web server run.
        try {
            val si = Intent(this, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(si) else startService(si)
        } catch (_: Exception) { }
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
        findViewById<Button>(R.id.btnLock).requestFocus()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    private fun wifiIp(): String {
        return try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"
        } catch (_: Exception) { "?" }
    }

    private fun updateUi() {
        store.resetUsedIfNewDay()
        val now = System.currentTimeMillis()
        val locked = store.isLockedNow(now)
        tvStatus.text = if (locked) "ĐANG KHÓA" else "ĐANG MỞ"
        tvStatus.setBackgroundResource(if (locked) R.drawable.pill_locked else R.drawable.pill_open)
        val rem = store.remainingMin()
        val remTxt = if (rem == Int.MAX_VALUE) "không giới hạn" else "$rem phút"
        tvUsed.text = "Đã xem hôm nay: ${store.usedTodayMin} / ${store.dailyLimitMin} phút\nCòn lại: $remTxt"
        tvTimes.text = "Giờ khóa: ${store.fmt(store.lockStartMin)} – ${store.fmt(store.lockEndMin)}\nGiờ thời sự: ${store.fmt(store.newsStartMin)} – ${store.fmt(store.newsEndMin)}"
        tvNews.text = if (store.newsPkg.isEmpty()) "App thời sự: chưa chọn" else "App thời sự: ${appLabel(store.newsPkg)}"
        tvWeb.text = "Điều khiển từ điện thoại (cùng WiFi):\nhttp://${wifiIp()}:8080"
    }

    private fun appLabel(pkg: String): String {
        return try { packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString() } catch (_: Exception) { pkg }
    }
}
