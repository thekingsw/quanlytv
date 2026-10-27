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
        val w = wm ?: return
        if (view == null) {
            val c = ctx ?: return
            val v = LayoutInflater.from(c).inflate(R.layout.view_warning_banner, null)
            tvTitle = v.findViewById(R.id.tvBannerTitle)
            tvSub = v.findViewById(R.id.tvBannerSub)
            val density = v.resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = (24 * density).toInt()
            try {
                w.addView(v, params)
                view = v
            } catch (_: Exception) {
                view = null
                return
            }
        }
        tvTitle?.text = title
        val s = tvSub
        if (s != null) {
            s.text = sub
            s.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
        }
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
