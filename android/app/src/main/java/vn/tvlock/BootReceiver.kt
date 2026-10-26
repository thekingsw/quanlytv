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
        }
    }
}
