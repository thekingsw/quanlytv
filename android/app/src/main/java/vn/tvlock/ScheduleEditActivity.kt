package vn.tvlock

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * v4: edit one lock schedule — pick weekdays (7 checkboxes, D-pad friendly)
 * and start/end times. v1.6: hour and minute are separate steppers
 * (±1 hour / ±1 minute, hold to repeat). "Khóa cả ngày" sets 00:00–23:59.
 * End <= start means overnight (except equal, which means all day).
 */
class ScheduleEditActivity : Activity() {
    private var settingsFrame: SettingsFrame.Handle? = null
    private lateinit var store: Store
    private var scheduleId: String? = null
    private val dayBoxes = mutableListOf<CheckBox>()
    private var startMin = 1140 // 19:00
    private var endMin = 600    // 10:00
    private lateinit var tvStartHour: TextView
    private lateinit var tvStartMin: TextView
    private lateinit var tvEndHour: TextView
    private lateinit var tvEndMin: TextView
    private lateinit var tvRangeNote: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_frame)
        store = Store(this)
        settingsFrame = SettingsFrame.bind(this, "schedules")
        layoutInflater.inflate(R.layout.pane_schedule_edit, settingsFrame!!.pane, true)
        settingsFrame!!.onPaneReady()
        scheduleId = intent.getStringExtra("id")
        tvStartHour = findViewById(R.id.tvStartHour)
        tvStartMin = findViewById(R.id.tvStartMin)
        tvEndHour = findViewById(R.id.tvEndHour)
        tvEndMin = findViewById(R.id.tvEndMin)
        tvRangeNote = findViewById(R.id.tvRangeNote)

        val daysBox = findViewById<LinearLayout>(R.id.daysBox)
        val names = store.dayNames()
        val existing = scheduleId?.let { id -> store.lockSchedules().firstOrNull { it.id == id } }
        if (existing != null) {
            startMin = existing.startMin
            endMin = existing.endMin
        }
        for (d in 0..6) {
            val cb = CheckBox(this)
            cb.text = names[d]
            cb.textSize = 19f
            cb.setTextColor(android.graphics.Color.WHITE)
            cb.isChecked = existing?.days?.contains(d) ?: (d < 5) // default: weekdays
            cb.isFocusable = true
            dayBoxes.add(cb)
            daysBox.addView(cb)
        }

        // v1.6: giờ và phút chỉnh riêng — nút giờ ±1 giờ, nút phút ±1 phút,
        // giữ nút là tự tăng/giảm liên tục (HoldRepeat, như giữ phím volume).
        // Giai đoạn 3: bind chung qua TimeStepper (thay bindHourMin trùng lặp).
        TimeStepper.bind(this, R.id.btnStartHourMinus, R.id.btnStartHourPlus, R.id.btnStartMinMinus, R.id.btnStartMinPlus,
            { startMin }, { startMin = it }) { refresh() }
        TimeStepper.bind(this, R.id.btnEndHourMinus, R.id.btnEndHourPlus, R.id.btnEndMinMinus, R.id.btnEndMinPlus,
            { endMin }, { endMin = it }) { refresh() }
        findViewById<Button>(R.id.btnAllDay).setOnClickListener {
            startMin = 0; endMin = 1439; refresh()
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        val btnDelete = findViewById<Button>(R.id.btnDelete)
        if (existing == null) {
            btnDelete.visibility = android.view.View.GONE
        } else {
            btnDelete.setOnClickListener {
                store.deleteLockSchedule(existing.id)
                Toast.makeText(this, "Đã xóa lịch", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        refresh()
    }

    private fun refresh() {
        tvStartHour.text = String.format("%02d", startMin / 60)
        tvStartMin.text = String.format("%02d", startMin % 60)
        tvEndHour.text = String.format("%02d", endMin / 60)
        tvEndMin.text = String.format("%02d", endMin % 60)
        tvRangeNote.text = when {
            startMin == endMin -> "Khóa cả ngày vào các ngày đã chọn."
            startMin > endMin -> "Khóa qua đêm: từ ${store.fmt(startMin)} đến ${store.fmt(endMin)} sáng hôm sau."
            else -> "Khóa trong ngày: từ ${store.fmt(startMin)} đến ${store.fmt(endMin)}."
        }
    }

    private fun save() {
        val days = dayBoxes.mapIndexedNotNull { i, cb -> if (cb.isChecked) i else null }
        if (days.isEmpty()) {
            Toast.makeText(this, "Chọn ít nhất một ngày trong tuần", Toast.LENGTH_LONG).show()
            return
        }
        val id = scheduleId ?: store.newScheduleId()
        store.upsertLockSchedule(Store.LockSchedule(id, days, startMin, endMin))
        Toast.makeText(this, "Đã lưu lịch khóa", Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Back phân cấp: pane → rail → tab → thoát. */
    override fun onBackPressed() {
        if (settingsFrame?.onBackPressed() == true) return
        super.onBackPressed()
    }
}
