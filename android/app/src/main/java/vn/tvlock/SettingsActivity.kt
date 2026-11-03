package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Giai đoạn 3 (redesign 2.2): Cài đặt dùng khung rail+pane — rail trái
 * (SettingsFrame, dựng từ SettingsRegistry), pane phải là tổng quan.
 *
 * v2.4: pane Tổng quan thành dashboard trực quan — 3 thẻ số liệu
 * (Đã xem / Còn lại / Phút thưởng), thanh tiến độ hôm nay, biểu đồ
 * cột 7 ngày, dòng Khóa đêm + Giờ thời sự. Nội dung nằm trong ScrollView,
 * nút Quay lại ghim ở đáy pane.
 */
class SettingsActivity : Activity() {
    private var settingsFrame: SettingsFrame.Handle? = null
    private lateinit var store: Store
    private lateinit var tvStatus: TextView
    private lateinit var tvUsed: TextView
    private lateinit var progressUsed: ProgressBar
    private lateinit var tvStatUsed: TextView
    private lateinit var tvStatRemaining: TextView
    private lateinit var tvStatBonus: TextView
    private lateinit var chartContainer: LinearLayout
    private lateinit var tvChartEmpty: TextView
    private lateinit var tvNightLock: TextView
    private lateinit var tvNewsSlot: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v2.5: vào Cài đặt phải nhập mã PIN khóa TV — chặn ở cửa để bịt
        // mọi đường vào chưa qua xác thực (nút Cài đặt ở màn hình chính,
        // bánh răng Trợ năng của TV, ...). Đường vào hợp lệ duy nhất là
        // LockActivity mode "settings" mở lại kèm pinVerified=true.
        if (!intent.getBooleanExtra("pinVerified", false)) {
            val i = Intent(this, LockActivity::class.java)
            i.putExtra("mode", "settings")
            startActivity(i)
            finish()
            return
        }
        setContentView(R.layout.settings_frame)
        settingsFrame = SettingsFrame.bind(this, "")
        layoutInflater.inflate(R.layout.pane_settings_overview, settingsFrame!!.pane, true)
        settingsFrame!!.onPaneReady()
        store = Store(this)
        tvStatus = findViewById(R.id.tvStatus)
        tvUsed = findViewById(R.id.tvUsed)
        progressUsed = findViewById(R.id.progressUsed)
        tvStatUsed = findViewById(R.id.tvStatUsed)
        tvStatRemaining = findViewById(R.id.tvStatRemaining)
        tvStatBonus = findViewById(R.id.tvStatBonus)
        chartContainer = findViewById(R.id.chartContainer)
        tvChartEmpty = findViewById(R.id.tvChartEmpty)
        tvNightLock = findViewById(R.id.tvNightLock)
        tvNewsSlot = findViewById(R.id.tvNewsSlot)
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** "1 giờ 12 phút" / "45 phút" / "1 giờ" — như MainActivity. */
    private fun fmtDuration(min: Int): String {
        if (min <= 0) return "Không giới hạn"
        if (min < 60) return "$min phút"
        val h = min / 60
        val m = min % 60
        return if (m == 0) "$h giờ" else "$h giờ $m phút"
    }

    /** "Khóa đêm": trong lịch hôm nay, ưu tiên lịch qua đêm (startMin > endMin),
        nếu không có thì lịch đầu tiên hôm nay; không có lịch nào thì "Không có lịch khóa".
        Như MainActivity. */
    private fun nightLockText(): String {
        val day = store.dayIndexOf(Calendar.getInstance())
        val todays = store.schedulesForDay(day)
        if (todays.isEmpty()) return "Không có lịch khóa"
        val s = todays.firstOrNull { it.startMin > it.endMin } ?: todays[0]
        return "${store.fmt(s.startMin)} – ${store.fmt(s.endMin)}"
    }

    private fun refresh() {
        store.resetUsedIfNewDay()
        val now = System.currentTimeMillis()
        val locked = store.isLockedNow(now)
        tvStatus.text = if (locked) "ĐÃ KHÓA" else "ĐANG MỞ"
        tvStatus.setBackgroundResource(
            if (locked) R.drawable.pill_locked else R.drawable.pill_open)
        tvStatus.setTextColor(resources.getColor(
            if (locked) R.color.on_locked else R.color.on_open, null))

        val limit = store.limitForToday()
        val used = store.usedTodayMin
        val rem = store.remainingMin()
        tvStatUsed.text = "$used phút"
        tvStatRemaining.text =
            if (rem == Int.MAX_VALUE) "Không giới hạn" else fmtDuration(rem)
        tvStatBonus.text =
            if (store.bonusMin > 0) "+${store.bonusMin} phút" else "0 phút"
        progressUsed.progress =
            if (limit <= 0) 100 else (used * 100 / limit).coerceIn(0, 100)
        tvUsed.text = "Đã xem $used phút · Giới hạn " +
            if (limit <= 0) "không giới hạn" else fmtDuration(limit)
        tvNightLock.text = "Khóa đêm: ${nightLockText()}"
        tvNewsSlot.text = "Giờ thời sự: ${store.fmt(store.newsStartMin)} – ${store.fmt(store.newsEndMin)}"
        buildChart()
    }

    /**
     * Biểu đồ cột 7 ngày: 6 ngày gần nhất + hôm nay.
     * historyDays() chỉ chứa ngày đã chốt — hôm nay chưa chốt nên số hôm
     * nay lấy từ usedTodayMin, không lấy từ history (xem finalizeDay() /
     * resetUsedIfNewDay() trong Store.kt). Ngày nào không có dữ liệu thì
     * cột cao tối thiểu + nhãn "0". Chưa có dữ liệu nào (chưa chốt ngày
     * nào, hôm nay cũng chưa xem) thì hiện dòng "Chưa đủ dữ liệu 7 ngày qua"
     * thay cho biểu đồ.
     */
    private fun buildChart() {
        val history = store.historyDays().associateBy { it.date }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val days = (6 downTo 0).map { offset ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -offset)
            val dateStr = sdf.format(c.time)
            val isToday = offset == 0
            val total = if (isToday) store.usedTodayMin
                else history[dateStr]?.total ?: 0
            val label = if (isToday) "Nay" else when (c.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "T2"
                Calendar.TUESDAY -> "T3"
                Calendar.WEDNESDAY -> "T4"
                Calendar.THURSDAY -> "T5"
                Calendar.FRIDAY -> "T6"
                Calendar.SATURDAY -> "T7"
                else -> "CN"
            }
            Triple(total, label, isToday)
        }
        val hasAnyData = days.any { it.first > 0 } || history.isNotEmpty()
        if (!hasAnyData) {
            chartContainer.visibility = View.GONE
            tvChartEmpty.visibility = View.VISIBLE
            return
        }
        chartContainer.visibility = View.VISIBLE
        tvChartEmpty.visibility = View.GONE
        chartContainer.removeAllViews()
        val max = days.maxOf { it.first }.coerceAtLeast(1)
        val density = resources.displayMetrics.density
        val maxBarPx = (100 * density).toInt()
        val minBarPx = (4 * density).toInt()
        val barWpx = (32 * density).toInt()
        val gapPx = (6 * density).toInt()
        for ((total, label, isToday) in days) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val tvVal = TextView(this)
            tvVal.text = "$total"
            tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            tvVal.setTextColor(getColor(
                if (isToday) R.color.accent else R.color.text_muted))
            col.addView(tvVal)
            val bar = View(this)
            val h = (total.toFloat() / max * maxBarPx).toInt()
                .coerceIn(minBarPx, maxBarPx)
            val barParams = LinearLayout.LayoutParams(barWpx, h)
            barParams.topMargin = gapPx
            bar.layoutParams = barParams
            val bg = GradientDrawable()
            bg.cornerRadius = 4 * density
            bg.setColor(getColor(if (isToday) R.color.accent else R.color.open))
            bar.background = bg
            col.addView(bar)
            val tvDay = TextView(this)
            tvDay.text = label
            tvDay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            tvDay.setTextColor(getColor(R.color.text_muted))
            val dayParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            dayParams.topMargin = gapPx
            tvDay.layoutParams = dayParams
            col.addView(tvDay)
            chartContainer.addView(col)
        }
    }

    /** Back phân cấp: pane → rail → tab → thoát. */
    override fun onBackPressed() {
        if (settingsFrame?.onBackPressed() == true) return
        super.onBackPressed()
    }
}
