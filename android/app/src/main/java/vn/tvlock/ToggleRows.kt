package vn.tvlock

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/**
 * v2: dựng ToggleRow (toggle_row.xml, 52dp) bằng code — hàng có công tắc
 * 44x24dp bên phải. Row focusable, bấm OK thì đảo trạng thái switch
 * rồi gọi [onToggle].
 */
object ToggleRows {

    fun add(
        activity: Activity,
        container: LinearLayout,
        title: String,
        detail: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ): View {
        val row = activity.layoutInflater.inflate(R.layout.toggle_row, container, false)
        row.id = View.generateViewId()
        row.findViewById<TextView>(R.id.toggleTitle).text = title
        row.findViewById<TextView>(R.id.toggleDetail).text = detail
        val sw = row.findViewById<Switch>(R.id.toggleSwitch)
        sw.isChecked = checked
        val gap = (4 * activity.resources.displayMetrics.density).toInt()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.resources.getDimensionPixelSize(R.dimen.list_row_height)
        )
        params.topMargin = (2 * activity.resources.displayMetrics.density).toInt()
        params.bottomMargin = gap
        row.layoutParams = params
        row.setOnClickListener {
            val nv = !sw.isChecked
            sw.isChecked = nv
            onToggle(nv)
        }
        container.addView(row)
        return row
    }
}
