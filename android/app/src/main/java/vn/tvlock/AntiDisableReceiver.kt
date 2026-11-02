package vn.tvlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** v2.0: bật/tắt "chống tắt Trợ năng" từ xa qua ADB (app Cài TV gửi
 *  `am broadcast -a vn.tvlock.SET_ANTIDISABLE --ez enabled false -n
 *  vn.tvlock/.AntiDisableReceiver`). Khi tắt, app ngừng tự bật lại
 *  Trợ năng nên anh có thể tắt nó trong Cài đặt TV như bình thường;
 *  khi bật, app tự bật lại Trợ năng ngay nếu đang tắt (cần quyền
 *  WRITE_SECURE_SETTINGS đã được cấp qua `pm grant`). */
class AntiDisableReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != "vn.tvlock.SET_ANTIDISABLE") return
        val store = Store(ctx)
        store.antiDisable = intent.getBooleanExtra("enabled", true)
        if (store.antiDisable) store.ensureAccessEnabled()
    }
}
