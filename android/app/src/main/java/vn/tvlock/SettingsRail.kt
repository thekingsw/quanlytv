package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView

/**
 * Giai đoạn 3 (redesign 2.2): dựng rail Cài đặt 250dp vào một container
 * (theo design-pack/design/tv-settings.html).
 *
 * - Tiêu đề "Cài đặt" 28sp bold + các mục 40dp, radius 12dp, 18sp,
 *   chừa 8dp hai bên + 2dp trên/4dp dưới (v2.4: phóng 1.06 khi focus
 *   vẫn nằm trong rail 250dp, không mất góc). Rail nằm trong ScrollView
 *   ở layout (v2.4) nên thêm mục không mất mục dưới — ScrollView tự
 *   cuộn theo mục đang focus.
 * - Mục đang chọn: nền accent + chữ đậm màu onAccent.
 * - Mục khác: chữ #C9D3E6 (rail_item_text), focus thì viền trắng + vòng
 *   sáng hổ phách như nút.
 * - Bấm mục khác → mở Activity đích + finish() màn hình hiện tại;
 *   bấm đúng mục đang chọn thì không làm gì.
 * - Focus ban đầu: mục đang chọn (ở màn Tổng quan — không có mục nào
 *   đang chọn — thì focus mục đầu tiên "Lịch khóa"). Focus luôn bắt đầu
 *   ở rail trên mọi màn cài đặt; bấm ▶ để sang nội dung pane.
 */
object SettingsRail {

    fun build(activity: Activity, container: ViewGroup, selectedId: String) {
        container.removeAllViews()
        val density = activity.resources.displayMetrics.density
        val padH = (16 * density).toInt()

        val title = TextView(activity)
        title.text = "Cài đặt"
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.setTextColor(activity.resources.getColor(R.color.text, null))
        val titleParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        titleParams.leftMargin = (8 * density).toInt()
        titleParams.rightMargin = (8 * density).toInt()
        titleParams.bottomMargin = (10 * density).toInt()
        title.layoutParams = titleParams
        container.addView(title)

        var selectedView: TextView? = null
        var firstView: TextView? = null
        var lastGroup = ""
        for (item in SettingsRegistry.items) {
            // v4.0: tiêu đề nhóm (Khóa TV / Màn hình chính) — không focus được.
            if (item.group != lastGroup) {
                lastGroup = item.group
                val gh = TextView(activity)
                gh.text = SettingsRegistry.groupTitle(item.group)
                gh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gh.setTypeface(null, android.graphics.Typeface.BOLD)
                gh.setTextColor(activity.resources.getColor(R.color.rail_item_text, null))
                gh.setPadding(padH, (10 * density).toInt(), padH, (4 * density).toInt())
                gh.isFocusable = false
                container.addView(gh)
            }
            val selected = item.id == selectedId
            val tv = TextView(activity)
            tv.text = item.title
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            tv.setTypeface(null, if (selected) android.graphics.Typeface.BOLD
                else android.graphics.Typeface.NORMAL)
            tv.setTextColor(activity.resources.getColor(
                if (selected) R.color.on_accent else R.color.rail_item_text, null))
            tv.setBackgroundResource(
                if (selected) R.drawable.rail_item_selected_selector
                else R.drawable.rail_item_selector)
            tv.stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(
                activity, R.animator.focus_scale)
            tv.isFocusable = true
            tv.gravity = android.view.Gravity.CENTER_VERTICAL
            tv.setPadding(padH, 0, padH, 0)
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (40 * density).toInt()
            )
            // v2.4: mục nhỏ lại (44→40dp, chữ 20→18sp) + chừa 8dp hai bên
            // và 2dp trên/4dp dưới — phóng 1.06 khi focus: rộng 234dp →
            // ~248dp vẫn nằm trong rail 250dp, cao 40dp → ~42,4dp vẫn
            // nằm trong khe dọc 6dp, không mất góc như bản 2.3.
            params.leftMargin = (8 * density).toInt()
            params.rightMargin = (8 * density).toInt()
            params.topMargin = (2 * density).toInt()
            params.bottomMargin = (4 * density).toInt()
            tv.layoutParams = params
            if (selected) {
                selectedView = tv
            } else {
                tv.setOnClickListener {
                    val intent = Intent(activity, item.activity)
                    // v2.3: mục có mode (vd "webpin") thì truyền kèm.
                    if (item.extraMode != null) intent.putExtra("mode", item.extraMode)
                    // v4.0: mở từ rail Cài đặt (đã qua PIN) thì báo cho
                    // màn hình con biết để khỏi hỏi PIN lại.
                    if (item.activity == HomeSettingsActivity::class.java) {
                        intent.putExtra("pinVerified", true)
                    }
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
            if (firstView == null) firstView = tv
            container.addView(tv)
        }
        // Focus ban đầu ở rail (mục đang chọn, hoặc mục đầu ở màn Tổng quan).
        (selectedView ?: firstView)?.post { (selectedView ?: firstView)?.requestFocus() }
    }
}
