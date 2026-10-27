package vn.tvlock

/**
 * v1.7: cầu nối trong cùng tiến trình giữa GuardService (tính giờ, quyết định
 * khi nào nhắc) và GuardAccessibilityService (nơi duy nhất được vẽ overlay
 * TYPE_ACCESSIBILITY_OVERLAY mà không cần quyền "hiển thị trên app khác").
 * Trả false khi chưa có listener — GuardService sẽ dùng WarningActivity
 * (popup cũ) làm dự phòng.
 */
object WarningBus {
    interface Listener {
        fun showWarning(title: String, sub: String)
        fun hideWarning()
    }

    @Volatile
    var listener: Listener? = null

    /** true = overlay đã nhận và sẽ hiện; false = không có ai nghe. */
    fun show(title: String, sub: String): Boolean {
        val l = listener ?: return false
        l.showWarning(title, sub)
        return true
    }

    fun hide() {
        listener?.hideWarning()
    }
}
