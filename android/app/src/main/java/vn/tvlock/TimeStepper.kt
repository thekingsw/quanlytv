package vn.tvlock

import android.app.Activity
import android.widget.Button

/**
 * Giai đoạn 3 (redesign 2.2): bind chung cho 4 nút giờ−/giờ+/phút−/phút+
 * dùng HoldRepeat.attach — thay cho bindHourMin() từng trùng lặp trong
 * ScheduleEditActivity và NewsSlotActivity.
 *
 * Giờ ±60 (mod 1440, giữ nguyên phút), phút ±1 (mod 1440, tràn sang giờ).
 * Sau mỗi bước gọi [refresh] để cập nhật TextView hiển thị.
 */
object TimeStepper {

    fun bind(
        activity: Activity,
        hourMinusId: Int, hourPlusId: Int,
        minMinusId: Int, minPlusId: Int,
        get: () -> Int, set: (Int) -> Unit,
        refresh: () -> Unit
    ) {
        HoldRepeat.attach(activity.findViewById<Button>(hourMinusId)) {
            set((get() - 60 + 1440) % 1440); refresh()
        }
        HoldRepeat.attach(activity.findViewById<Button>(hourPlusId)) {
            set((get() + 60) % 1440); refresh()
        }
        HoldRepeat.attach(activity.findViewById<Button>(minMinusId)) {
            set((get() - 1 + 1440) % 1440); refresh()
        }
        HoldRepeat.attach(activity.findViewById<Button>(minPlusId)) {
            set((get() + 1) % 1440); refresh()
        }
    }
}
