package vn.tvlock

/**
 * Luật "app nào đang ở foreground" — HÀM THUẦN, KHÔNG import android.*.
 *
 * Trước đây các luật này rải ở nhiều chỗ (onAccessibilityEvent, refreshForegroundApp,
 * GuardService.onCreate, Store.tick), mỗi chỗ một kiểu nên lastPkg bị kẹt ở app hệ thống
 * (06–08/11). Giờ chỉ còn một nơi định nghĩa luật; LockEngine là nơi duy nhất áp kết
 * quả vào Store (Store.lastPkg có `private set`, trình biên dịch chặn ghi tắt).
 *
 * Phân loại gói:
 *  - Cửa sổ THOÁNG QUA ([TRANSIENT_OVERLAYS]: thanh âm lượng, bảng thông báo...) nằm
 *    trên app đang xem. Sự kiện của chúng KHÔNG đổi foreground; nếu đổi thì lastPkg
 *    thành app hệ thống -> dừng đếm giờ dù trẻ vẫn xem video bên dưới.
 *  - Mọi gói khác — kể cả Cài đặt / launcher hệ thống / chính app mình — là màn hình
 *    THẬT: ghi đè lastPkg. Vào Cài đặt hay launcher là KHÔNG còn xem YouTube nữa nên
 *    phải dừng đếm giờ (isCountingNow false vì gói đó nằm trong LockPolicy.isExcluded).
 *    Việc khóa không phụ thuộc điều này: LockPolicy.evaluate vẫn khóa đè lên chúng.
 */
object ForegroundTracker {

    private val TRANSIENT_OVERLAYS = setOf("com.android.systemui")

    /** Sự kiện cửa sổ của [pkg] có nên bỏ qua (không đổi foreground) không? */
    fun shouldIgnoreEvent(pkg: String): Boolean =
        pkg.isEmpty() || pkg in TRANSIENT_OVERLAYS

    /** Một cửa sổ trên màn hình (đã rút gọn từ AccessibilityWindowInfo). */
    data class Win(val pkg: String, val focused: Boolean)

    sealed class Detected {
        /** Foreground là [pkg] (có thể là chính app mình = màn khóa/màn hình chính). */
        data class App(val pkg: String) : Detected()
        /** Không xác định được — giữ nguyên giá trị cũ. */
        object Unknown : Detected()
    }

    /**
     * Chọn foreground từ danh sách cửa sổ (dùng khi nhận lại app lúc service khởi động
     * lại, hoặc khi lastPkg đang rỗng / ở màn hình hệ thống).
     *  1. Ưu tiên cửa sổ đang focus: của chính mình -> [ownPkg] (để biết màn khóa đã
     *     hiện, đừng gọi lại); cửa sổ thoáng qua -> bỏ qua, xét cửa sổ focus kế;
     *     còn lại -> chính nó.
     *  2. Đã có cửa sổ focus mà không chọn được -> Unknown (KHÔNG quét cửa sổ nền:
     *     quét bừa làm lockPoll tưởng đang ở app khác, màn khóa nhấp nháy 88 lần).
     *  3. Không có cửa sổ focus nào -> lấy app thật đầu tiên (không phải mình, không
     *     phải app hệ thống) — vẫn hơn để lastPkg rỗng.
     */
    fun detect(wins: List<Win>, ownPkg: String): Detected {
        var hasFocused = false
        for (w in wins) {
            if (!w.focused) continue
            hasFocused = true
            if (w.pkg.isEmpty()) continue
            if (w.pkg == ownPkg) return Detected.App(ownPkg)
            if (w.pkg in TRANSIENT_OVERLAYS) continue
            return Detected.App(w.pkg)
        }
        if (hasFocused) return Detected.Unknown
        for (w in wins) {
            if (w.pkg.isEmpty() || w.pkg == ownPkg) continue
            if (LockPolicy.isExcluded(w.pkg)) continue
            return Detected.App(w.pkg)
        }
        return Detected.Unknown
    }
}
