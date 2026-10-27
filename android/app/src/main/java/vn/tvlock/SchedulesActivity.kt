package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView

/**
 * v4: flexible lock schedules. Each schedule picks weekdays + a from-to
 * time range; several schedules combine (any match locks the TV).
 * Overnight ranges (e.g. 19:00 -> 10:00) span to the next day.
 */
class SchedulesActivity : Activity() {
    private lateinit var store: Store
    private lateinit var adapter: ArrayAdapter<String>
    private var schedules = listOf<Store.LockSchedule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedules)
        store = Store(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        val list = findViewById<ListView>(R.id.listSchedules)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val i = Intent(this, ScheduleEditActivity::class.java)
            i.putExtra("id", schedules[pos].id)
            startActivity(i)
        }
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
        adapter.clear()
        if (schedules.isEmpty()) {
            adapter.add("Chưa có lịch khóa nào — TV không bị khóa theo giờ.")
        } else {
            adapter.addAll(schedules.map { store.scheduleSummary(it) })
        }
        adapter.notifyDataSetChanged()
    }
}
