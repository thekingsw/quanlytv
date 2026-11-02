package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.TextView

/**
 * v4.0: thẻ popup thông báo từ hộp thư Tinker — hiện góc phải trên,
 * nền trong suốt để vẫn thấy nội dung phía sau (cùng pattern đã chứng
 * minh với MessageActivity trên YouTube). Tự tắt sau 15 giây.
 * OK = mở chi tiết (trung tâm thông báo), Back = bỏ qua.
 */
class NotificationPopupActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val autoDismiss = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_popup)
        val title = intent.getStringExtra("title") ?: "Thông báo"
        val body = intent.getStringExtra("body") ?: ""
        val type = intent.getStringExtra("type") ?: ""
        findViewById<TextView>(R.id.tvPopupType)?.text = typeLabel(type)
        findViewById<TextView>(R.id.tvPopupTitle)?.text = title
        findViewById<TextView>(R.id.tvPopupBody)?.text = body
        // Đánh dấu đã đọc khi popup hiện (trừ khi người dùng mở chi tiết).
        handler.postDelayed(autoDismiss, 15000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoDismiss)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                handler.removeCallbacks(autoDismiss)
                val i = Intent(this, NotificationCenterActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                finish()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                handler.removeCallbacks(autoDismiss)
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun typeLabel(t: String): String = when (t) {
        "fare" -> "VÉ MÁY BAY"
        "promo" -> "KHUYẾN MÃI"
        "lunar" -> "ÂM LỊCH"
        "trip" -> "CHUYẾN ĐI"
        "family" -> "GIA ĐÌNH"
        "test" -> "THỬ"
        else -> "TINKER"
    }
}
