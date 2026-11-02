package vn.tvlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** v2.4: gỡ quyền Device Owner của chính app (đường thoát khi đã đặt Device
 *  Owner, xem ghi chú trong DeviceOwnerReceiver.kt). Gửi từ tab Lệnh ADB
 *  của app Cài TV (cần quyền ADB — trẻ con không làm được):
 *  `am broadcast -a vn.tvlock.REMOVE_DEVICE_OWNER -n
 *  vn.tvlock/.DeviceOwnerRemoveReceiver`
 *  Sau khi gỡ xong thì gỡ cài đặt app như app thường. Không dùng
 *  `dpm remove-active-admin` — lệnh đó thất bại với app không phải testOnly. */
class DeviceOwnerRemoveReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != "vn.tvlock.REMOVE_DEVICE_OWNER") return
        val ok = DeviceOwnerPolicy.remove(ctx)
        Toast.makeText(
            ctx,
            if (ok) "Đã gỡ quyền Device Owner — giờ gỡ app như bình thường"
            else "Chưa gỡ được quyền Device Owner (app chưa phải Device Owner, hoặc cần khôi phục cài đặt gốc)",
            Toast.LENGTH_LONG
        ).show()
    }
}
