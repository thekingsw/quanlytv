package vn.tvlock

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Giai đoạn 3 (redesign 2.2): dựng ListRow (list_row.xml, 72dp) bằng code
 * vào một LinearLayout — thay cho ListView + ArrayAdapter thô ở các màn
 * Schedules / DailyLimits / AppRules.
 *
 * Row focusable + focus rõ (row_selector: nền card2, focus thì viền trắng
 * 4dp + vòng sáng hổ phách, phóng 1.06 như nút). [linkFocus] nối
 * nextFocusUp/Down giữa các row và nút nằm dưới danh sách, vì LinearLayout
 * không tự xử lý D-pad như ListView.
 */
object ListRows {

    /** Thêm một row vào [container]; [onClick] null → row chỉ để xem
     *  (không bấm được, không focusable, ẩn "›"). Trả về row view. */
    fun add(
        activity: Activity,
        container: LinearLayout,
        title: String,
        detail: String,
        onClick: (() -> Unit)?
    ): View {
        val row = activity.layoutInflater.inflate(R.layout.list_row, container, false)
        row.id = View.generateViewId()
        row.findViewById<TextView>(R.id.rowTitle).text = title
        row.findViewById<TextView>(R.id.rowDetail).text = detail
        val gap = (4 * activity.resources.displayMetrics.density).toInt()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.resources.getDimensionPixelSize(R.dimen.list_row_height)
        )
        // v2.4: khe dọc 2dp trên + 4dp dưới — row 72dp phóng 1.06 khi focus
        // (~76,3dp, nhô ~2,2dp mỗi đầu) vẫn nằm trong khe, không đè sát row kề.
        params.topMargin = (2 * activity.resources.displayMetrics.density).toInt()
        params.bottomMargin = gap
        row.layoutParams = params
        if (onClick != null) {
            row.setOnClickListener { onClick() }
        } else {
            row.isFocusable = false
            row.findViewById<TextView>(R.id.rowChevron).visibility = View.GONE
        }
        container.addView(row)
        return row
    }

    /** Nối nextFocusUp/Down: row[i] ↑ row[i-1], ↓ row[i+1] (row cuối ↓ nút
     *  [below]); nút [below] ↑ row cuối. Row đầu giữ nextFocusUp mặc định
     *  (đi lên rail / ra ngoài pane theo focus search của hệ thống). */
    fun linkFocus(rows: List<View>, below: View?) {
        for (i in rows.indices) {
            if (i > 0) rows[i].nextFocusUpId = rows[i - 1].id
            if (i < rows.size - 1) rows[i].nextFocusDownId = rows[i + 1].id
            else if (below != null) rows[i].nextFocusDownId = below.id
        }
        if (below != null && rows.isNotEmpty()) below.nextFocusUpId = rows.last().id
    }
}
