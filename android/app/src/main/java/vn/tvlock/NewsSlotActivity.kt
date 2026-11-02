package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** v2.1: giờ thời sự tách ra màn hình riêng (trước đây nằm trong Cài đặt,
 *  làm trang Cài đặt dài). Giờ và phút chỉnh riêng — nút giờ ±1 giờ,
 *  nút phút ±1 phút, giữ nút là tự tăng/giảm liên tục (HoldRepeat). */
class NewsSlotActivity : Activity() {
    private lateinit var store: Store
    private lateinit var tvNewsStartHour: TextView
    private lateinit var tvNewsStartMin: TextView
    private lateinit var tvNewsEndHour: TextView
    private lateinit var tvNewsEndMin: TextView
    private var newsPkgs = listOf<String>()
    private var newsLabels = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news_slot)
        store = Store(this)
        SettingsRail.build(this, findViewById<LinearLayout>(R.id.railContainer), "news_slot")
        tvNewsStartHour = findViewById(R.id.tvNewsStartHour)
        tvNewsStartMin = findViewById(R.id.tvNewsStartMin)
        tvNewsEndHour = findViewById(R.id.tvNewsEndHour)
        tvNewsEndMin = findViewById(R.id.tvNewsEndMin)

        // Giai đoạn 3: bind chung qua TimeStepper (thay bindHourMin trùng lặp).
        TimeStepper.bind(this, R.id.btnNewsStartHourMinus, R.id.btnNewsStartHourPlus,
            R.id.btnNewsStartMinMinus, R.id.btnNewsStartMinPlus,
            { store.newsStartMin }, { store.newsStartMin = it }) { refresh() }
        TimeStepper.bind(this, R.id.btnNewsEndHourMinus, R.id.btnNewsEndHourPlus,
            R.id.btnNewsEndMinMinus, R.id.btnNewsEndMinPlus,
            { store.newsEndMin }, { store.newsEndMin = it }) { refresh() }

        // Giai đoạn 3: nút "Mở app thời sự" chuyển từ trang chủ về đây.
        findViewById<Button>(R.id.btnOpenNews).setOnClickListener { openNewsApp() }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        setupNewsSpinner()
        refresh()
    }

    /** Mở app thời sự đã chọn; giữ đúng 2 chuỗi toast cũ của MainActivity. */
    private fun openNewsApp() {
        val pkg = store.newsPkg
        if (pkg.isEmpty()) {
            Toast.makeText(this, "Chưa chọn app thời sự trong Cài đặt", Toast.LENGTH_SHORT).show()
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            Toast.makeText(this, "Không mở được app thời sự", Toast.LENGTH_SHORT).show()
            return
        }
        try { startActivity(launch) } catch (_: Exception) {
            Toast.makeText(this, "Không mở được app thời sự", Toast.LENGTH_SHORT).show()
        }
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

    private fun refresh() {
        tvNewsStartHour.text = String.format("%02d", store.newsStartMin / 60)
        tvNewsStartMin.text = String.format("%02d", store.newsStartMin % 60)
        tvNewsEndHour.text = String.format("%02d", store.newsEndMin / 60)
        tvNewsEndMin.text = String.format("%02d", store.newsEndMin % 60)
    }
}
