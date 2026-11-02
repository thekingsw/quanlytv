package vn.tvlock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var tvStatus: TextView
    private lateinit var tvDateTime: TextView
    private lateinit var tvRemaining: TextView
    private lateinit var tvUsedLeft: TextView
    private lateinit var tvUsedRight: TextView
    private lateinit var tvNewsSlot: TextView
    private lateinit var tvNightLock: TextView
    private lateinit var tvBonus: TextView
    private lateinit var progressUsed: ProgressBar
    private lateinit var tvWeb: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { updateUi(); handler.postDelayed(this, 5000) }
    }
    private val dateFmt = SimpleDateFormat("EEEE · HH:mm", Locale("vi"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = Store(this)
        tvStatus = findViewById(R.id.tvStatus)
        tvDateTime = findViewById(R.id.tvDateTime)
        tvRemaining = findViewById(R.id.tvRemaining)
        tvUsedLeft = findViewById(R.id.tvUsedLeft)
        tvUsedRight = findViewById(R.id.tvUsedRight)
        tvNewsSlot = findViewById(R.id.tvNewsSlot)
        tvNightLock = findViewById(R.id.tvNightLock)
        tvBonus = findViewById(R.id.tvBonus)
        progressUsed = findViewById(R.id.progressUsed)
        tvWeb = findViewById(R.id.tvWeb)
        findViewById<Button>(R.id.btnLock).setOnClickListener {
            store.manualLocked = true; store.unlockUntilMs = 0L
            Toast.makeText(this, "Đã khóa TV", Toast.LENGTH_SHORT).show(); updateUi()
        }
        findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            val i = Intent(this, LockActivity::class.java)
            i.putExtra("mode", "unlock"); i.putExtra("reason", "Mở khóa TV")
            startActivity(i)
        }
        val adds = listOf(R.id.btnAdd5 to 5, R.id.btnAdd10 to 10, R.id.btnAdd15 to 15, R.id.btnAdd30 to 30)
        for ((id, min) in adds) {
            findViewById<Button>(id).setOnClickListener {
                val i = Intent(this, LockActivity::class.java)
                i.putExtra("mode", "add"); i.putExtra("minutes", min)
                i.putExtra("reason", "Cho thêm $min phút")
                startActivity(i)
            }
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            // v2.5: vào Cài đặt phải nhập mã PIN khóa TV trước.
            val i = Intent(this, LockActivity::class.java)
            i.putExtra("mode", "settings")
            startActivity(i)
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

    /** "1 giờ 12 phút" / "45 phút" / "1 giờ" / "Không giới hạn" (min <= 0). */
    private fun fmtDuration(min: Int): String {
        if (min <= 0) return "Không giới hạn"
        if (min < 60) return "$min phút"
        val h = min / 60
        val m = min % 60
        return if (m == 0) "$h giờ" else "$h giờ $m phút"
    }

    /** "Khóa đêm": trong lịch hôm nay, ưu tiên lịch qua đêm (startMin > endMin),
        nếu không có thì lịch đầu tiên hôm nay; không có lịch nào thì "Không có lịch khóa". */
    private fun nightLockText(): String {
        val day = store.dayIndexOf(Calendar.getInstance())
        val todays = store.schedulesForDay(day)
        if (todays.isEmpty()) return "Không có lịch khóa"
        val s = todays.firstOrNull { it.startMin > it.endMin } ?: todays[0]
        return "${store.fmt(s.startMin)} – ${store.fmt(s.endMin)}"
    }

    private fun updateUi() {
        store.resetUsedIfNewDay()
        val now = System.currentTimeMillis()
        val locked = store.isLockedNow(now)
        tvStatus.text = if (locked) "ĐÃ KHÓA" else "ĐANG MỞ"
        tvStatus.setBackgroundResource(if (locked) R.drawable.pill_locked else R.drawable.pill_open)
        tvStatus.setTextColor(getColor(if (locked) R.color.on_locked else R.color.on_open))
        tvDateTime.text = dateFmt.format(Date(now))
        val rem = store.remainingMin()
        tvRemaining.text = if (rem == Int.MAX_VALUE) "Không giới hạn" else fmtDuration(rem)
        val limit = store.limitForToday()
        val used = store.usedTodayMin
        progressUsed.progress = if (limit <= 0) 100 else (used * 100 / limit).coerceIn(0, 100)
        tvUsedLeft.text = "Đã xem $used phút"
        tvUsedRight.text = if (limit <= 0) "Không giới hạn" else "Giới hạn ${fmtDuration(limit)}"
        tvNewsSlot.text = "${store.fmt(store.newsStartMin)} – ${store.fmt(store.newsEndMin)}"
        tvNightLock.text = nightLockText()
        tvBonus.text = "${store.bonusMin} phút"
        tvWeb.text = "Điều khiển từ điện thoại (cùng WiFi):\nhttp://${wifiIp()}:8080"
    }
}
