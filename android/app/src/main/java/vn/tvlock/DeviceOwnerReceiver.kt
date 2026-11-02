package vn.tvlock

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Device admin receiver — only used for the optional one-time ADB Device Owner step
 * (`adb shell dpm set-device-owner vn.tvlock/.DeviceOwnerReceiver`) so the app
 * cannot be uninstalled from Settings. Not required for normal lock/unlock.
 *
 * v2.4: the receiver now actually enforces the block. Before v2.4 the app only
 * declared the receiver; uninstall blocking relied on the system's generic
 * "active admin" rule and `setUninstallBlocked` was never called.
 *
 * Removal path (important — read before setting Device Owner): `dpm
 * remove-active-admin` does NOT work for this app because it is not a test-only
 * app (Android refuses with "Attempt to remove non-test admin"; only a factory
 * reset would remove it that way). The supported way back is the app removing
 * itself: [DeviceOwnerPolicy.remove] calls `clearDeviceOwnerApp`, reachable
 * (a) from the phone web after entering the web PIN
 * (`POST /api/device-owner-remove`), and (b) via ADB broadcast
 * (`am broadcast -a vn.tvlock.REMOVE_DEVICE_OWNER -n
 * vn.tvlock/.DeviceOwnerRemoveReceiver`). `clearDeviceOwnerApp` is a deprecated
 * Android API ("for testing purposes only", best-effort policy cleanup), so it
 * is the escape hatch, not a guarantee — if it ever fails, factory reset
 * remains the last resort. "Clear data" in Settings still resets the PINs
 * (Device Owner does not block that); that limit is unchanged.
 */
object DeviceOwnerPolicy {
    fun admin(ctx: Context) = ComponentName(ctx, DeviceOwnerReceiver::class.java)

    /** If this app is the device owner, block its uninstall. Safe no-op otherwise. */
    fun ensureBlocked(ctx: Context) {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(ctx.packageName)) {
                dpm.setUninstallBlocked(admin(ctx), ctx.packageName, true)
            }
        } catch (_: Exception) { }
    }

    /** Remove this app as device owner (self-removal). Returns true on success. */
    @Suppress("DEPRECATION")
    fun remove(ctx: Context): Boolean {
        return try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!dpm.isDeviceOwnerApp(ctx.packageName)) return false
            dpm.setUninstallBlocked(admin(ctx), ctx.packageName, false)
            dpm.clearDeviceOwnerApp(ctx.packageName)
            !dpm.isDeviceOwnerApp(ctx.packageName)
        } catch (_: Exception) {
            false
        }
    }

    fun isDeviceOwner(ctx: Context): Boolean {
        return try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isDeviceOwnerApp(ctx.packageName)
        } catch (_: Exception) { false }
    }
}

class DeviceOwnerReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        DeviceOwnerPolicy.ensureBlocked(context)
    }
    override fun onDisabled(context: Context, intent: Intent) {}
}
