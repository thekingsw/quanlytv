package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * v4: flexible lock schedules. Each schedule picks weekdays + a from-to
 * time range; several schedules combine (any match locks the TV).
 * Overnight ranges (e.g. 19:00 -> 10:00) span to the next day.
 *
 * Giai đoạn 3 (redesign 2.2): rail+pane; danh sách lịch dựng bằng ListRow
 * trong listContainer thay cho ListView thô. Logic giữ nguyên.
 */
class SchedulesActivity : Activity() {
    private var settingsFrame: SettingsFrame.Handle? = null
    private lateinit var store: Store
    private var schedules = listOf<Store.LockSchedule>()
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_frame)
        store = Store(this)
        settingsFrame = SettingsFrame.bind(this, "schedules")
        layoutInflater.inflate(R.layout.pane_schedules, settingsFrame!!.pane, true)
        settingsFrame!!.onPaneReady()
        listContainer = findViewById(R.id.listContainer)
        findViewById<Button>(R.id.btnAddSchedule).setOnClickListener {
            startActivity(Intent(this, ScheduleEditActivity::class.java))
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvHint).text =
            "Bấm vào một lịch để sửa, hoặc thêm lịch mới. Ví dụ: Thứ 2, Thứ 3 khóa cả ngày; " +
            "Thứ 4, Thứ 5 khóa 19:00 đến 10:00 sáng hôm sau. Nhiều lịch gộp lại — lịch nào khớp giờ là TV khóa."
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        schedules = store.lockSchedules()
        listContainer.removeAllViews()
        val btnAdd = findViewById<Button>(R.id.btnAddSchedule)
        if (schedules.isEmpty()) {
            ListRows.add(this, listContainer,
                "Chưa có lịch khóa nào — TV không bị khóa theo giờ.", "", null)
            return
        }
        val rows = schedules.map { s ->
            val daysTxt = if (s.days.size == 7) "Hằng ngày"
                else s.days.sorted().joinToString(", ") { store.dayNames()[it] }
            ListRows.add(this, listContainer, daysTxt, store.scheduleTimeText(s), onClick = {
                val i = Intent(this, ScheduleEditActivity::class.java)
                i.putExtra("id", s.id)
                startActivity(i)
            })
        }
        ListRows.linkFocus(rows, btnAdd)
    }

    /** Back phân cấp: pane → rail → tab → thoát. */
    override fun onBackPressed() {
        if (settingsFrame?.onBackPressed() == true) return
        super.onBackPressed()
    }
}
