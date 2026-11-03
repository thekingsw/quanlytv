package vn.tvlock

import android.animation.AnimatorInflater
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * v2: khung Cài đặt 2 cấp dùng chung cho mọi màn cài đặt.
 *
 * Cấu trúc: hàng trên (tiêu đề "Cài đặt" + tab nhóm dạng pill + gợi ý phím),
 * dưới: rail 200dp (mục của nhóm đang chọn) + pane (nội dung Activity).
 *
 * Điều hướng D-pad:
 * - Ở hàng tab: ◀ ▶ đổi nhóm (rail cập nhật theo), ▼ xuống rail.
 * - Ở rail: ▲ ▼ đổi mục, ▶ sang pane, OK mở mục (Activity đích),
 *   Back về hàng tab.
 * - Ở pane: Back về rail. (Nội dung pane tự xử lý phím riêng.)
 * - Ở hàng tab bấm Back: thoát Activity.
 *
 * Dùng: trong onCreate, sau setContentView(R.layout.settings_frame):
 *   val frame = SettingsFrame.bind(this, "schedules")
 *   layoutInflater.inflate(R.layout.pane_schedules, frame.pane, true)
 *   frame.onPaneReady()   // nối focus rail ▶ pane sau khi inflate
 *
 * Back phân cấp: Activity gọi frame.onBackPressed() trong onBackPressed(),
 * trả về true nếu frame đã xử lý (chuyển focus), false để Activity finish.
 */
object SettingsFrame {

    class Handle(
        val pane: LinearLayout,
        private val activity: Activity,
        private val tabViews: List<TextView>,
        private val railViews: List<TextView>,
        private val railScroll: ScrollView?
    ) {
        /** Gọi sau khi inflate xong nội dung pane: nối focus ▶ từ rail
         *  sang view focusable đầu tiên trong pane. */
        fun onPaneReady() {
            val first = findFirstFocusable(pane) ?: return
            if (first.id == View.NO_ID) first.id = View.generateViewId()
            for (rv in railViews) rv.nextFocusRightId = first.id
            // Từ pane bấm ◀ về rail: view đầu trong pane trỏ về mục đang chọn.
            first.nextFocusLeftId = railViews.firstOrNull { it.isSelected }?.id
                ?: railViews.firstOrNull()?.id ?: View.NO_ID
        }

        /** Focus vào view đầu tiên trong pane (dùng sau recreate để giữ
         *  con trỏ ở pane thay vì nhảy về rail). */
        fun focusPane() {
            findFirstFocusable(pane)?.requestFocus()
        }

        /** Back phân cấp: pane → rail → tab → thoát (false). */
        fun onBackPressed(): Boolean {
            val focused = activity.currentFocus ?: return false
            if (isInPane(focused)) {
                // Về mục đang chọn trong rail.
                railViews.firstOrNull { it.isSelected }?.requestFocus()
                    ?: railViews.firstOrNull()?.requestFocus()
                return true
            }
            if (railViews.contains(focused)) {
                // Về tab của nhóm hiện tại.
                tabViews.firstOrNull { it.isSelected }?.requestFocus()
                    ?: tabViews.firstOrNull()?.requestFocus()
                return true
            }
            return false // đang ở tab → Activity tự finish
        }

        private fun isInPane(v: View): Boolean {
            var p = v.parent
            while (p != null) {
                if (p === pane) return true
                p = p.parent
            }
            return false
        }

        private fun findFirstFocusable(vg: ViewGroup): View? {
            for (i in 0 until vg.childCount) {
                val c = vg.getChildAt(i)
                if (c.isFocusable && c.visibility == View.VISIBLE) return c
                if (c is ViewGroup) {
                    val f = findFirstFocusable(c)
                    if (f != null) return f
                }
            }
            return null
        }
    }

    fun bind(activity: Activity, currentItemId: String, startInPane: Boolean = false): Handle {
        val tabsBox = activity.findViewById<LinearLayout>(R.id.groupTabs)
        val railBox = activity.findViewById<LinearLayout>(R.id.railContainer)
        val railScroll = activity.findViewById<ScrollView>(R.id.railScroll)
        val pane = activity.findViewById<LinearLayout>(R.id.paneContainer)

        val currentItem = SettingsRegistry.itemOf(currentItemId)
        var currentGroup = currentItem?.group ?: SettingsRegistry.groups.first().id

        val density = activity.resources.displayMetrics.density
        val tabViews = mutableListOf<TextView>()
        val railViews = mutableListOf<TextView>()

        fun paintTabs() {
            for ((i, g) in SettingsRegistry.groups.withIndex()) {
                val tv = tabViews[i]
                val sel = g.id == currentGroup
                tv.isSelected = sel
                tv.setBackgroundResource(
                    if (sel) R.drawable.pill_tab_selected_focusable else R.drawable.pill_tab_focusable)
                tv.setTextColor(activity.resources.getColor(
                    if (sel) R.color.on_accent else R.color.rail_item_text, null))
                tv.setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        fun buildRail() {
            railBox.removeAllViews()
            railViews.clear()
            val items = SettingsRegistry.itemsOf(currentGroup)
            for ((idx, item) in items.withIndex()) {
                val tv = TextView(activity)
                tv.id = View.generateViewId()
                tv.text = item.title
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                tv.isFocusable = true
                tv.gravity = Gravity.CENTER_VERTICAL
                val padH = (14 * density).toInt()
                tv.setPadding(padH, 0, padH, 0)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (36 * density).toInt())
                if (idx > 0) lp.topMargin = (4 * density).toInt()
                tv.layoutParams = lp
                val selected = item.id == currentItemId
                tv.isSelected = selected
                tv.setBackgroundResource(R.drawable.rail_item_v2_selector)
                if (selected) {
                    tv.setTextColor(activity.resources.getColor(R.color.accent, null))
                    tv.setTypeface(null, Typeface.BOLD)
                } else {
                    tv.setTextColor(activity.resources.getColor(R.color.rail_item_text, null))
                    tv.setTypeface(null, Typeface.NORMAL)
                }
                tv.stateListAnimator = AnimatorInflater.loadStateListAnimator(
                    activity, R.animator.focus_scale_v2)
                tv.tag = item
                tv.setOnClickListener { openItem(activity, item) }
                railBox.addView(tv)
                railViews.add(tv)
            }
            for (i in railViews.indices) {
                if (i > 0) railViews[i].nextFocusUpId = railViews[i - 1].id
                if (i < railViews.size - 1) railViews[i].nextFocusDownId = railViews[i + 1].id
            }
            val selView = railViews.firstOrNull { it.isSelected }
            selView?.post { railScroll?.smoothScrollTo(0, selView.top) }
        }

        for ((idx, g) in SettingsRegistry.groups.withIndex()) {
            val tv = TextView(activity)
            tv.id = View.generateViewId()
            tv.text = g.title
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            tv.gravity = Gravity.CENTER
            tv.isFocusable = true
            val padH = (16 * density).toInt()
            tv.setPadding(padH, 0, padH, 0)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (34 * density).toInt())
            if (idx < SettingsRegistry.groups.size - 1) lp.rightMargin = (8 * density).toInt()
            tv.layoutParams = lp
            tv.tag = g
            tv.stateListAnimator = AnimatorInflater.loadStateListAnimator(
                activity, R.animator.focus_scale_v2)
            tv.setOnClickListener {
                currentGroup = g.id
                paintTabs()
                buildRail()
            }
            tabsBox.addView(tv)
            tabViews.add(tv)
        }
        for (i in tabViews.indices) {
            if (i > 0) tabViews[i].nextFocusLeftId = tabViews[i - 1].id
            if (i < tabViews.size - 1) tabViews[i].nextFocusRightId = tabViews[i + 1].id
        }

        paintTabs()
        buildRail()

        // Nối ▼ từ tab xuống mục đầu rail (sau khi buildRail).
        val firstRail = railViews.firstOrNull()
        if (firstRail != null) {
            for (tv in tabViews) tv.nextFocusDownId = firstRail.id
            // Mục đầu rail ▲ về tab đang chọn.
            val selTab = tabViews.firstOrNull { it.isSelected }
            if (selTab != null) firstRail.nextFocusUpId = selTab.id
        }

        val selView = railViews.firstOrNull { it.isSelected }
        // Nếu startInPane (recreate từ pane), không focus rail — activity
        // sẽ tự focus pane sau khi dựng xong nội dung.
        if (!startInPane) {
            selView?.post { selView.requestFocus() }
        }

        return Handle(pane, activity, tabViews, railViews, railScroll)
    }

    private fun openItem(activity: Activity, item: SettingsRegistry.Item) {
        val cur = activity.intent.getStringExtra("frameItemId")
        if (cur == item.id) return
        val i = Intent(activity, item.activity)
        if (item.extraMode != null) {
            if (item.activity == HomeSettingsActivity::class.java)
                i.putExtra("section", item.extraMode)
            else
                i.putExtra("mode", item.extraMode)
        }
        if (activity.intent.getBooleanExtra("pinVerified", false))
            i.putExtra("pinVerified", true)
        i.putExtra("frameItemId", item.id)
        activity.startActivity(i)
        activity.finish()
    }
}
