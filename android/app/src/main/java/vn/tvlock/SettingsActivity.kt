package vn.tvlock

import android.app.Activity
import android.content.ComponentName
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
    private lateinit var tvSchedules: TextView
    private lateinit var tvDailyLimits: TextView
    private lateinit var tvNewsStart: TextView
    private lateinit var tvNewsEnd: TextView
    private lateinit var tvManual: TextView
    private lateinit var tvAccess: TextView
    private lateinit var tvIconState: TextView
    private lateinit var tvBonus: TextView
    private lateinit var tvAppRules: TextView
    private lateinit var btnIconToggle: Button
    private var newsPkgs = listOf<String>()
    private var newsLabels = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = Store(this)
        tvSchedules = findViewById(R.id.tvSchedules)
        tvDailyLimits = findViewById(R.id.tvDailyLimits)
        tvNewsStart = findViewById(R.id.tvNewsStart)
        tvNewsEnd = findViewById(R.id.tvNewsEnd)
        tvManual = findViewById(R.id.tvManual)
        tvAccess = findViewById(R.id.tvAccess)
        tvIconState = findViewById(R.id.tvIconState)
        tvBonus = findViewById(R.id.tvBonus)
        tvAppRules = findViewById(R.id.tvAppRules)
        btnIconToggle = findViewById(R.id.btnIconToggle)

        findViewById<Button>(R.id.btnChangePin).setOnClickListener {
            val old = findViewById<EditText>(R.id.etOldPin).text.toString()
            val nw = findViewById<EditText>(R.id.etNewPin).text.toString()
            when {
                old != store.pin -> Toast.makeText(this, "PIN cũ không đúng", Toast.LENGTH_SHORT).show()
                nw.length < 4 -> Toast.makeText(this, "PIN mới ít nhất 4 số", Toast.LENGTH_SHORT).show()
                else -> { store.pin = nw; Toast.makeText(this, "Đã đổi PIN", Toast.LENGTH_SHORT).show() }
            }
        }
        findViewById<Button>(R.id.btnSchedules).setOnClickListener {
            startActivity(Intent(this, SchedulesActivity::class.java))
        }
        findViewById<Button>(R.id.btnDailyLimits).setOnClickListener {
            startActivity(Intent(this, DailyLimitsActivity::class.java))
        }
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
        btnIconToggle.setOnClickListener { toggleLauncherIcon(); refresh() }
        findViewById<Button>(R.id.btnAppRules).setOnClickListener {
            startActivity(Intent(this, AppRulesActivity::class.java))
        }
        // v3: bonus minutes on TV require the PIN first (LockActivity mode "bonus").
        val bonusBtns = listOf(
            R.id.btnBonus5 to 5, R.id.btnBonus10 to 10,
            R.id.btnBonus15 to 15, R.id.btnBonus30 to 30
        )
        for ((id, min) in bonusBtns) {
            findViewById<Button>(id).setOnClickListener {
                val i = Intent(this, LockActivity::class.java)
                i.putExtra("mode", "bonus"); i.putExtra("minutes", min)
                i.putExtra("reason", "Thưởng thêm $min phút")
                startActivity(i)
            }
        }
        findViewById<Button>(R.id.btnOpenMain).setOnClickListener {
            val i = Intent(this, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
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
        // v4: flexible schedules + per-weekday daily limits.
        val scheds = store.lockSchedules()
        tvSchedules.text = if (scheds.isEmpty()) "Chưa có lịch khóa nào — TV không bị khóa theo giờ."
        else scheds.joinToString("\n") { store.scheduleSummary(it) }
        tvDailyLimits.text = store.dailyLimitsSummary() +
            "\n(Hôm nay đã xem ${store.usedTodayMin} phút, giới hạn ${store.limitText(store.limitForToday())}.)"
        tvNewsStart.text = "Bắt đầu thời sự: ${store.fmt(store.newsStartMin)}"
        tvNewsEnd.text = "Kết thúc thời sự: ${store.fmt(store.newsEndMin)}"
        tvManual.text = if (store.manualLocked) "Khóa thủ công: ĐANG KHÓA" else "Khóa thủ công: đang mở"
        tvAccess.text = "Trợ năng (Accessibility): " + if (isAccessEnabled()) "ĐÃ BẬT" else "CHƯA BẬT — bấm nút dưới để bật"
        val iconOn = launcherIconEnabled()
        tvIconState.text = if (iconOn) "Icon trên màn hình chính: ĐANG HIỆN" else "Icon trên màn hình chính: ĐANG ẨN"
        btnIconToggle.text = if (iconOn) "Ẩn icon" else "Hiện icon"
        // v3: bonus pool + per-app rules summary.
        tvBonus.text = "Phút thưởng còn lại: ${store.bonusMin} phút\n(Phút thưởng chỉ trừ khi đã hết giờ ngày, và không hết hạn khi sang ngày mới.)"
        val rules = store.appRules()
        tvAppRules.text = if (rules.isEmpty()) "Chưa đặt giới hạn riêng cho app nào."
        else rules.entries.joinToString("\n") { (pkg, r) ->
            val label = store.appLabel(pkg)
            when (r.mode) {
                "limit" -> "$label: giới hạn riêng ${r.minutes} phút/ngày (hôm nay đã xem ${store.perAppMin(pkg)} phút)"
                "free" -> "$label: không tính giờ"
                "blocked" -> "$label: chặn hẳn"
                else -> "$label: bình thường"
            }
        }
    }

    private fun launcherIconEnabled(): Boolean {
        // Manifest sets the alias enabled=false, so DEFAULT also means hidden.
        return packageManager.getComponentEnabledSetting(
            ComponentName(packageName, "$packageName.LauncherAlias")
        ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    private fun toggleLauncherIcon() {
        val enabled = launcherIconEnabled()
        packageManager.setComponentEnabledSetting(
            ComponentName(packageName, "$packageName.LauncherAlias"),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        Toast.makeText(
            this,
            if (enabled) "Đã ẩn icon khỏi màn hình chính" else "Đã hiện icon trên màn hình chính",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun isAccessEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            enabled.contains(packageName)
        } catch (_: Exception) { false }
    }
}
