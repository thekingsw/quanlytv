package vn.tvlock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.util.Log

/**
 * v1.7: banner nhắc kiểu notification — một dải nhỏ ở phía trên màn hình,
 * KHÔNG chắn app đang xem, KHÔNG cướp focus, KHÔNG nhận chạm.
 *
 * Dùng TYPE_ACCESSIBILITY_OVERLAY nên chỉ AccessibilityService mới vẽ được
 * (không cần quyền "hiển thị trên app khác" của hệ thống). Video đang xem
 * không bị pause, điều khiển vẫn bấm được như bình thường — khác với
 * WarningActivity cũ (mở Activity mới là app dưới bị pause + nút "Đã hiểu"
 * giành focus). Mọi thao tác WindowManager đều chạy trên main thread.
 */
object WarningOverlay {
    private var wm: WindowManager? = null
    private var ctx: Context? = null
    private var view: View? = null
    private var tvTitle: TextView? = null
    private var tvSub: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    fun attach(service: AccessibilityService) {
        handler.post {
            ctx = service.applicationContext
            wm = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
        }
    }

    fun show(title: String, sub: String) {
        handler.post { showOnMain(title, sub) }
    }

    fun hide() {
        handler.post { hideOnMain() }
    }

    private fun showOnMain(title: String, sub: String) {
        val w = wm
        if (w == null) { Log.e("WarningOverlay", "showOnMain: wm null, chua attach?"); return }
        val v = view
        if (v == null) {
            val c = ctx ?: return
            val nv = LayoutInflater.from(c).inflate(R.layout.view_warning_banner, null)
            tvTitle = nv.findViewById(R.id.tvBannerTitle)
            tvSub = nv.findViewById(R.id.tvBannerSub)
            // v2.7: đặt chữ TRƯỚC khi addView — nếu không, WRAP_CONTENT
            // sẽ đo được chiều rộng 0 (chữ đang rỗng) và banner tàng hình.
            tvTitle?.text = title
            tvSub?.let { s ->
                s.text = sub
                s.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
            }
            val density = nv.resources.displayMetrics.density
            // v2.6: banner chỉ rộng vừa khung chữ (WRAP_CONTENT) thay vì
            // full chiều ngang — gọn lại, vẫn thấy được nội dung hai bên.
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = (27 * density).toInt()
            try {
                w.addView(nv, params)
                view = nv
            } catch (e: Exception) {
                Log.e("WarningOverlay", "addView that bai: " + e.toString())
                view = null
            }
            return
        }
        // View đã có: cập nhật chữ rồi buộc đo lại (WRAP_CONTENT).
        tvTitle?.text = title
        tvSub?.let { s ->
            s.text = sub
            s.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
        }
        v.requestLayout()
    }

    private fun hideOnMain() {
        val v = view ?: return
        view = null
        tvTitle = null
        tvSub = null
        try { wm?.removeView(v) } catch (_: Exception) { }
    }

    /** Gỡ banner và quên WindowManager khi AccessibilityService chết. */
    fun detach() {
        handler.post {
            hideOnMain()
            wm = null
            ctx = null
        }
    }
}
