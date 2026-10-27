package vn.tvlock

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * v4: daily limit per weekday (0=Mon..6=Sun). Each day picks one of
 * 30/60/90/120/180/240 minutes or "Không giới hạn" (-1).
 */
class DailyLimitsActivity : Activity() {
    private lateinit var store: Store
    private lateinit var adapter: ArrayAdapter<String>

    private val options = arrayOf(
        "30 phút", "60 phút", "90 phút", "120 phút",
        "180 phút", "240 phút", "Không giới hạn"
    )
    private val values = listOf(30, 60, 90, 120, 180, 240, -1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_limits)
        store = Store(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        val list = findViewById<ListView>(R.id.listDays)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> showLimitDialog(pos) }
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
        adapter.clear()
        adapter.addAll((0..6).map { "${store.dayNames()[it]}: ${store.limitText(store.limitForDay(it))}" })
        adapter.notifyDataSetChanged()
    }

    private fun showLimitDialog(day: Int) {
        val current = store.limitForDay(day)
        val checked = values.indexOf(current).coerceAtLeast(2)
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
}
