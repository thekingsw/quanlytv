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
 * and start/end times in 15-minute steps. "Khóa cả ngày" sets 00:00–23:59.
 * End <= start means overnight (except equal, which means all day).
 */
class ScheduleEditActivity : Activity() {
    private lateinit var store: Store
    private var scheduleId: String? = null
    private val dayBoxes = mutableListOf<CheckBox>()
    private var startMin = 1140 // 19:00
    private var endMin = 600    // 10:00
    private lateinit var tvStart: TextView
    private lateinit var tvEnd: TextView
    private lateinit var tvRangeNote: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_edit)
        store = Store(this)
        scheduleId = intent.getStringExtra("id")
        tvStart = findViewById(R.id.tvStart)
        tvEnd = findViewById(R.id.tvEnd)
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
            cb.textSize = 22f
            cb.setTextColor(android.graphics.Color.WHITE)
            cb.isChecked = existing?.days?.contains(d) ?: (d < 5) // default: weekdays
            cb.isFocusable = true
            dayBoxes.add(cb)
            daysBox.addView(cb)
        }

        bindTimeButtons(R.id.btnStartMinus, R.id.btnStartPlus, { startMin }) { startMin = it }
        bindTimeButtons(R.id.btnEndMinus, R.id.btnEndPlus, { endMin }) { endMin = it }
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

    private fun bindTimeButtons(minusId: Int, plusId: Int, get: () -> Int, set: (Int) -> Unit) {
        findViewById<Button>(minusId).setOnClickListener { set((get() - 15 + 1440) % 1440); refresh() }
        findViewById<Button>(plusId).setOnClickListener { set((get() + 15) % 1440); refresh() }
    }

    private fun refresh() {
        tvStart.text = "Bắt đầu: ${store.fmt(startMin)}"
        tvEnd.text = "Kết thúc: ${store.fmt(endMin)}"
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
}
