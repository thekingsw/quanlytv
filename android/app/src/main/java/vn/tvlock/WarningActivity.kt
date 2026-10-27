package vn.tvlock

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView

/**
 * v1.7: chỉ còn là DỰ PHÒNG khi banner overlay không vẽ được (Trợ năng chưa
 * chạy). Bình thường GuardService nhắc bằng banner overlay của
 * GuardAccessibilityService — không chắn app, không cướp focus.
 * Popup này hiện tối đa 20 giây (hoặc tới khi bấm OK/Back) và không tính
 * vào giờ xem (vn.tvlock được loại khỏi tính giờ).
 */
class WarningActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val dismiss = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warning)
        findViewById<TextView>(R.id.tvWarnTitle).text =
            intent.getStringExtra("message") ?: "Sắp hết giờ xem"
        findViewById<TextView>(R.id.tvWarnSub).text =
            intent.getStringExtra("sub") ?: ""
        findViewById<Button>(R.id.btnWarnOk).setOnClickListener { finish() }
        // v1.7: dự phòng khi overlay không vẽ được — không giành focus để
        // điều khiển vẫn ở app đang xem; bấm Back là tắt bảng này.
        handler.postDelayed(dismiss, 20000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(dismiss)
        super.onDestroy()
    }
}
