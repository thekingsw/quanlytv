package vn.tvlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val si = Intent(context, GuardService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(si)
                else context.startService(si)
            } catch (_: Exception) { }
            // v4.1: tự mở Tailscale sau reboot để ADB qua tailnet luôn dùng
            // được (always-on VPN không tự start app sau boot).
            try {
                val ts = context.packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
                ts?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (ts != null) context.startActivity(ts)
            } catch (_: Exception) { }
        }
    }
}
