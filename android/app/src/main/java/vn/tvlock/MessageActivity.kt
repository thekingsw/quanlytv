package vn.tvlock

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView

/**
 * v2.7: màn hình tin nhắn gửi lên TV — hiện câu nhắn của bố mẹ lên giữa
 * màn hình, tự tắt sau 5 giây rồi quay lại app đang xem.
 *
 * Dùng Activity vì cả hai cơ chế không chắn video đều không hiển thị được
 * trên TV này: overlay Trợ năng (TYPE_ACCESSIBILITY_OVERLAY) không tạo
 * được cửa sổ, còn Toast hệ thống bị chặn (không ném lỗi nhưng cũng không
 * hiện). Activity thì luôn hiển thị được. Đánh đổi: video sẽ tạm dừng
 * trong 5 giây hiện tin nhắn (Activity mới làm app dưới bị onPause), rồi
 * tự quay lại — đa số app sẽ resume khi quay lại foreground.
 */
class MessageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)
        val text = intent.getStringExtra("text") ?: ""
        findViewById<TextView>(R.id.tvMessage)?.text = text
        // Tự tắt sau 5 giây, quay lại app đang xem.
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 5000)
    }
}
