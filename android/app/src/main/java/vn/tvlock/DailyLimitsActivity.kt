package vn.tvlock

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * v4: daily limit per weekday (0=Mon..6=Sun). Each day picks one of
 * 30/60/90/120/180/240 minutes or "Không giới hạn" (-1).
 *
 * Giai đoạn 3 (redesign 2.2): rail+pane; 7 ngày dựng bằng ListRow trong
 * listContainer thay cho ListView thô. Dialog + logic giữ nguyên.
 */
class DailyLimitsActivity : Activity() {
    private var settingsFrame: SettingsFrame.Handle? = null
    private lateinit var store: Store
    private lateinit var listContainer: LinearLayout

    private val options = arrayOf(
        "5 phút", "10 phút", "15 phút", "30 phút", "60 phút", "90 phút", "120 phút",
        "180 phút", "240 phút", "Không giới hạn"
    )
    private val values = listOf(5, 10, 15, 30, 60, 90, 120, 180, 240, -1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_frame)
        store = Store(this)
        settingsFrame = SettingsFrame.bind(this, "daily_limits")
        layoutInflater.inflate(R.layout.pane_daily_limits, settingsFrame!!.pane, true)
        settingsFrame!!.onPaneReady()
        listContainer = findViewById(R.id.listContainer)
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvHint).text =
            "Bấm vào một ngày để đặt giới hạn xem của ngày đó. " +
            "Ngày \"Không giới hạn\" thì chỉ còn bị khóa bởi lịch khóa. " +
            "Ví dụ: ngày học 60 phút, cuối tuần 120 phút."
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        listContainer.removeAllViews()
        val btnBack = findViewById<Button>(R.id.btnBack)
        val rows = (0..6).map { day ->
            ListRows.add(this, listContainer,
                store.dayNames()[day], store.limitText(store.limitForDay(day)), onClick = {
                showLimitDialog(day)
            })
        }
        ListRows.linkFocus(rows, btnBack)
    }

    private fun showLimitDialog(day: Int) {
        val current = store.limitForDay(day)
        val checked = values.indexOf(current).let { if (it < 0) 5 else it } // mặc định 90 phút
        var chosen = checked
        AlertDialog.Builder(this)
            .setTitle("Giới hạn ngày ${store.dayNames()[day]}")
            .setSingleChoiceItems(options, checked) { _, which -> chosen = which }
            .setPositiveButton("Lưu") { _, _ ->
                store.setLimitForDay(day, values[chosen])
                Toast.makeText(this, "Đã lưu giới hạn ${store.dayNames()[day]}", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /** Back phân cấp: pane → rail → tab → thoát. */
    override fun onBackPressed() {
        if (settingsFrame?.onBackPressed() == true) return
        super.onBackPressed()
    }
}
