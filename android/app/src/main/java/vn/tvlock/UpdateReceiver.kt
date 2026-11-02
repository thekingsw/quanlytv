package vn.tvlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * v1.9: Android KHÔNG tự chạy lại service khi app được cập nhật (cài đè
 * APK) — process bị kill, hệ thống chỉ gửi MY_PACKAGE_REPLACED, không
 * khởi động lại GuardService. Trước đây web 8080 chết theo, phải mở
 * màn hình chính một lần thì MainActivity mới start lại service.
 * Receiver này khởi động lại GuardService (web + tick) ngay khi cập
 * nhật xong, khỏi cần mở tay. Dịch vụ Trợ năng thì hệ thống tự gắn
 * lại nên khóa vẫn chạy — chỉ có web + tick là cần receiver này.
 * (Dự phòng: Cài TV v1.4 mở màn hình chính Khóa TV sau khi cài xong.)
 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val si = Intent(context, GuardService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(si)
            else context.startService(si)
        } catch (_: Exception) { }
    }
}
