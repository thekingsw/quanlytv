package vn.tvlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** v2.3: đặt lại mã PIN web từ xa qua ADB (app Cài TV gửi
 *  `am broadcast -a vn.tvlock.RESET_WEBPIN -n
 *  vn.tvlock/.WebPinResetReceiver`). Mã PIN web được đặt lại = mã PIN
 *  khóa TV hiện tại — cách dự phòng khi quên mã PIN web mà không
 *  muốn (hoặc không mở được) màn hình TV. */
class WebPinResetReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != "vn.tvlock.RESET_WEBPIN") return
        val store = Store(ctx)
        store.webPin = store.pin
        Toast.makeText(ctx, "Đã đặt lại mã PIN web = mã PIN khóa TV", Toast.LENGTH_LONG).show()
    }
}
