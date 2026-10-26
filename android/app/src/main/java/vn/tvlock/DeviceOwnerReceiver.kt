package vn.tvlock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Device admin receiver — only used for the optional one-time ADB Device Owner step
 *  (adb shell dpm set-device-owner vn.tvlock/.DeviceOwnerReceiver) so the app
 *  cannot be uninstalled from Settings. Not required for normal lock/unlock. */
class DeviceOwnerReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {}
    override fun onDisabled(context: Context, intent: Intent) {}
}
