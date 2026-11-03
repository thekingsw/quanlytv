package vn.tvlock

import android.app.Activity

/**
 * v2: Registry cài đặt 2 cấp — nguồn duy nhất cho khung Cài đặt TV.
 * Thêm một mục mới chỉ cần thêm 1 dòng vào [items] (có sẵn Activity đích).
 *
 * 3 nhóm (theo thứ tự tab): lock = Khóa TV, launcher = Màn hình chính,
 * system = Bảo mật & công cụ.
 */
object SettingsRegistry {

    data class Group(val id: String, val title: String)

    val groups: List<Group> = listOf(
        Group("lock", "Khóa TV"),
        Group("launcher", "Màn hình chính"),
        Group("system", "Bảo mật & công cụ")
    )

    data class Item(
        val id: String,
        val title: String,
        val activity: Class<out Activity>,
        val group: String,
        /** mode/section truyền kèm cho Activity đích (vd "webpin",
         *  hoặc section của HomeSettingsActivity). */
        val extraMode: String? = null
    )

    fun groupTitle(group: String): String =
        groups.firstOrNull { it.id == group }?.title ?: group

    fun itemsOf(group: String): List<Item> = items.filter { it.group == group }

    fun itemOf(id: String): Item? = items.firstOrNull { it.id == id }

    val items: List<Item> = listOf(
        // ---- Nhóm Khóa TV ----
        Item("schedules", "Lịch khóa", SchedulesActivity::class.java, "lock"),
        Item("daily_limits", "Giới hạn ngày", DailyLimitsActivity::class.java, "lock"),
        Item("news_slot", "Giờ thời sự", NewsSlotActivity::class.java, "lock"),
        Item("app_rules", "Giới hạn từng app", AppRulesActivity::class.java, "lock"),
        Item("lock_screen", "Màn hình khóa", LockScreenActivity::class.java, "lock"),
        Item("bonus", "Phút thưởng", BonusActivity::class.java, "lock"),
        // ---- Nhóm Màn hình chính (tách HomeSettingsActivity thành section) ----
        Item("home_wallpaper", "Ảnh nền", HomeSettingsActivity::class.java, "launcher", "wallpaper"),
        Item("home_widgets", "Widget", HomeSettingsActivity::class.java, "launcher", "widgets"),
        Item("home_countdown", "Đếm ngược", HomeSettingsActivity::class.java, "launcher", "countdown"),
        Item("home_push", "Thông báo đẩy", HomeSettingsActivity::class.java, "launcher", "push"),
        Item("home_style", "Kiểu icon & thời tiết", HomeSettingsActivity::class.java, "launcher", "style"),
        Item("home_hidden", "Ứng dụng đã ẩn", HomeSettingsActivity::class.java, "launcher", "hidden"),
        // ---- Nhóm Bảo mật & công cụ ----
        Item("pin", "Mã PIN TV", PinActivity::class.java, "system"),
        Item("webpin", "Mã PIN web", WebPinActivity::class.java, "system"),
        Item("access", "Trợ năng & chống tắt", AccessActivity::class.java, "system"),
        Item("screenshot", "Chụp màn hình", ScreenCaptureActivity::class.java, "system")
    )

    fun titleOf(id: String): String = itemOf(id)?.title ?: "Cài đặt"
}
