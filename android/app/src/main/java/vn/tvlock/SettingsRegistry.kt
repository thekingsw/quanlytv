package vn.tvlock

import android.app.Activity

/**
 * Giai đoạn 3 (redesign 2.2): danh sách các mục Cài đặt — nguồn duy nhất
 * cho rail bên TV. Thêm một mục mới chỉ cần thêm 1 dòng vào [items].
 * Giai đoạn 5 (web) dùng chung cấu trúc này cho tab Cài đặt trên điện thoại.
 */
object SettingsRegistry {

    data class Item(
        val id: String,
        val title: String,
        val activity: Class<out Activity>,
        /** v2.3: mode truyền kèm cho Activity đích (vd "webpin" cho
         *  LockActivity) — null thì mở Activity như thường. */
        val extraMode: String? = null,
        /** v4.0: nhóm hiển thị trên rail (user 2026-11-02 — gom nhóm
         *  Khóa TV / Màn hình chính, đồng bộ với web). */
        val group: String = "lock"
    )

    /** Tên nhóm hiển thị trên rail. */
    fun groupTitle(group: String): String = when (group) {
        "launcher" -> "Màn hình chính"
        else -> "Khóa TV"
    }

    val items: List<Item> = listOf(
        Item("schedules", "Lịch khóa", SchedulesActivity::class.java),
        Item("daily_limits", "Giới hạn ngày", DailyLimitsActivity::class.java),
        Item("news_slot", "Giờ thời sự", NewsSlotActivity::class.java),
        Item("app_rules", "Giới hạn từng app", AppRulesActivity::class.java),
        Item("lock_screen", "Màn hình khóa", LockScreenActivity::class.java),
        Item("pin", "Mã PIN", PinActivity::class.java),
        Item("bonus", "Phút thưởng", BonusActivity::class.java),
        Item("access", "Trợ năng", AccessActivity::class.java),
        // v2.3: mã PIN web — qua LockActivity mode "webpin" (nhập đúng
        // mã PIN khóa TV mới mở được màn xem/đổi mã PIN web).
        Item("webpin", "Mã PIN web", LockActivity::class.java, "webpin"),
        // v3.0: màn hình chính 2-trong-1 — ảnh nền, kiểu icon, thời
        // tiết, ứng dụng đã ẩn.
        Item("home_screen", "Màn hình chính", HomeSettingsActivity::class.java,
            group = "launcher"),
        // v2.9: chụp màn hình TV (MediaProjection) — cấp quyền 1 lần
        // trên TV để web chụp được màn hình TV.
        Item("screenshot", "Chụp màn hình", ScreenCaptureActivity::class.java,
            group = "launcher")
    )

    fun titleOf(id: String): String = items.firstOrNull { it.id == id }?.title ?: "Cài đặt"
}
