package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var store: Store
    private lateinit var tvLimit: TextView
    private lateinit var tvLockStart: TextView
    private lateinit var tvLockEnd: TextView
    private lateinit var tvNewsStart: TextView
    private lateinit var tvNewsEnd: TextView
    private lateinit var tvManual: TextView
    private lateinit var tvAccess: TextView
    private val limits = listOf(0, 30, 60, 90, 120, 180)
    private var newsPkgs = listOf<String>()
    private var newsLabels = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = Store(this)
        tvLimit = findViewById(R.id.tvLimit)
        tvLockStart = findViewById(R.id.tvLockStart)
        tvLockEnd = findViewById(R.id.tvLockEnd)
        tvNewsStart = findViewById(R.id.tvNewsStart)
        tvNewsEnd = findViewById(R.id.tvNewsEnd)
        tvManual = findViewById(R.id.tvManual)
        tvAccess = findViewById(R.id.tvAccess)

        findViewById<Button>(R.id.btnChangePin).setOnClickListener {
            val old = findViewById<EditText>(R.id.etOldPin).text.toString()
            val nw = findViewById<EditText>(R.id.etNewPin).text.toString()
            when {
                old != store.pin -> Toast.makeText(this, "PIN cũ không đúng", Toast.LENGTH_SHORT).show()
                nw.length < 4 -> Toast.makeText(this, "PIN mới ít nhất 4 số", Toast.LENGTH_SHORT).show()
                else -> { store.pin = nw; Toast.makeText(this, "Đã đổi PIN", Toast.LENGTH_SHORT).show() }
            }
        }
        findViewById<Button>(R.id.btnLimit).setOnClickListener {
            val i = limits.indexOf(store.dailyLimitMin)
            store.dailyLimitMin = limits[(i + 1) % limits.size]
            refresh()
        }
        bindTimeButtons(R.id.btnLockStartMinus, R.id.btnLockStartPlus, { store.lockStartMin }) { store.lockStartMin = it }
        bindTimeButtons(R.id.btnLockEndMinus, R.id.btnLockEndPlus, { store.lockEndMin }) { store.lockEndMin = it }
        bindTimeButtons(R.id.btnNewsStartMinus, R.id.btnNewsStartPlus, { store.newsStartMin }) { store.newsStartMin = it }
        bindTimeButtons(R.id.btnNewsEndMinus, R.id.btnNewsEndPlus, { store.newsEndMin }) { store.newsEndMin = it }

        findViewById<Button>(R.id.btnManual).setOnClickListener {
            store.manualLocked = !store.manualLocked
            if (store.manualLocked) store.unlockUntilMs = 0L
            refresh()
        }
        findViewById<Button>(R.id.btnResetUsed).setOnClickListener {
            store.usedTodayMin = 0
            Toast.makeText(this, "Đã reset số phút hôm nay", Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.btnAccessSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        setupNewsSpinner()
        refresh()
    }

    private fun bindTimeButtons(minusId: Int, plusId: Int, get: () -> Int, set: (Int) -> Unit) {
        findViewById<Button>(minusId).setOnClickListener { set((get() - 30 + 1440) % 1440); refresh() }
        findViewById<Button>(plusId).setOnClickListener { set((get() + 30) % 1440); refresh() }
    }

    private fun setupNewsSpinner() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.sortedBy { it.second }
        newsPkgs = listOf("") + apps.map { it.first }
        newsLabels = listOf("(Chưa chọn)") + apps.map { it.second }
        val sp = findViewById<Spinner>(R.id.spNews)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, newsLabels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val idx = newsPkgs.indexOf(store.newsPkg).coerceAtLeast(0)
        sp.setSelection(idx)
        var first = true
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (first) { first = false; return }
                store.newsPkg = newsPkgs[pos]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        tvLimit.text = if (store.dailyLimitMin == 0) "Giới hạn ngày: Tắt" else "Giới hạn ngày: ${store.dailyLimitMin} phút (đã xem ${store.usedTodayMin} phút)"
        tvLockStart.text = "Bắt đầu khóa: ${store.fmt(store.lockStartMin)}"
        tvLockEnd.text = "Kết thúc khóa: ${store.fmt(store.lockEndMin)}"
        tvNewsStart.text = "Bắt đầu thời sự: ${store.fmt(store.newsStartMin)}"
        tvNewsEnd.text = "Kết thúc thời sự: ${store.fmt(store.newsEndMin)}"
        tvManual.text = if (store.manualLocked) "Khóa thủ công: ĐANG KHÓA" else "Khóa thủ công: đang mở"
        tvAccess.text = "Trợ năng (Accessibility): " + if (isAccessEnabled()) "ĐÃ BẬT" else "CHƯA BẬT — bấm nút dưới để bật"
    }

    private fun isAccessEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            enabled.contains(packageName)
        } catch (_: Exception) { false }
    }
}
