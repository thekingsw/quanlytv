package vn.tvlock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class GuardAccessibilityService : AccessibilityService() {
    private lateinit var store: Store

    private val warningListener = object : WarningBus.Listener {
        override fun showWarning(title: String, sub: String) = WarningOverlay.show(title, sub)
        override fun hideWarning() = WarningOverlay.hide()
    }

    // v4.2: poll khóa mỗi giây ngay trong Trợ năng — không phụ thuộc
    // GuardService nữa. Trợ năng do hệ thống quản lý, khó bị giết hơn
    // foreground service thường. Bắt trường hợp bấm khóa web lúc đang
    // xem dở (không có sự kiện đổi app).
    private val handler = Handler(Looper.getMainLooper())
    private val lockPoll = object : Runnable {
        override fun run() {
            try {
                if (::store.isInitialized) {
                    val now = System.currentTimeMillis()
                    // Chưa biết app nào đang xem (rỗng) hoặc đang ở màn hình hệ thống /
                    // chính app mình: quét lại cửa sổ để nhận đúng foreground
                    // (2026-11-07: lastPkg kẹt ở vn.tvlock sau khi mở thời sự từ màn khóa).
                    if (store.lastPkg.isEmpty() || store.isExcluded(store.lastPkg)) refreshForegroundApp()
                    // Cộng giờ + quyết định khóa + hiện màn khóa: một cửa LockEngine, cùng
                    // cửa với tick của GuardService (hai nơi gọi, một luật).
                    LockEngine.tick(this@GuardAccessibilityService, store, now)
                }
            } catch (e: Exception) {
                // Fail-CLOSED: lỗi trong lockPoll không được để TV mở khóa.
                android.util.Log.e("GuardA11y", "lockPoll failed", e)
                try {
                    if (::store.isInitialized) {
                        val now = System.currentTimeMillis()
                        if (store.lastPkg != packageName && store.isLockedNow(now)) {
                            LockEngine.showManualLock(this@GuardAccessibilityService, store.lockReason(now))
                        }
                    }
                } catch (_: Exception) { }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = Store(this)
        // Nhận lại app đang xem lúc kết nối — service có thể khởi động
        // lại giữa chừng (cập nhật app) trong khi người dùng đã ở sẵn
        // trên 1 app, sẽ không có sự kiện đổi app nào để nhận lại
        // lastPkg (đếm giờ đứng im, 2026-11-06).
        refreshForegroundApp()
        // v1.7: nhận banner nhắc từ GuardService để vẽ overlay — chỉ service
        // trợ năng mới vẽ được cửa sổ TYPE_ACCESSIBILITY_OVERLAY.
        WarningOverlay.attach(this)
        WarningBus.listener = warningListener
        // v4.2: bắt đầu poll khóa.
        handler.post(lockPoll)
    }

    /** Đọc app foreground hiện tại qua danh sách cửa sổ. Luật chọn nằm ở
     * ForegroundTracker.detect (thuần, có test); việc ghi vào Store do LockEngine làm. */
    private fun refreshForegroundApp() {
        try {
            val now = System.currentTimeMillis()
            val wins = windows ?: return
            fun pkgOf(w: android.view.accessibility.AccessibilityWindowInfo): String =
                w.root?.packageName?.toString() ?: ""
            // Chỉ đọc cửa sổ nền khi KHÔNG có cửa sổ focus nào (đỡ tốn).
            val focused = wins.filter { it.isFocused }.map { ForegroundTracker.Win(pkgOf(it), true) }
            val list = if (focused.isNotEmpty()) focused
                else wins.map { ForegroundTracker.Win(pkgOf(it), false) }
            LockEngine.applyDetected(store, ForegroundTracker.detect(list, packageName), now)
        } catch (e: Exception) {
            android.util.Log.e("GuardA11y", "refreshForegroundApp failed", e)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(lockPoll)
        WarningBus.listener = null
        WarningOverlay.detach()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (!::store.isInitialized) store = Store(this)
        // Cộng giờ app cũ, ghi app mới, quyết định chặn — tất cả trong LockEngine.
        LockEngine.onWindowChanged(this, store, pkg, System.currentTimeMillis())
    }

    override fun onInterrupt() {}

    // ---------- v4.3: gán nút remote ----------
    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action != android.view.KeyEvent.ACTION_DOWN) return false
        if (!::store.isInitialized) store = Store(this)
        val code = event.keyCode
        // Không bao giờ chặn các phím điều hướng hệ thống — tránh kẹt TV.
        if (code in KeyRemap.SYSTEM_KEYS) return false
        // Chế độ học: bắt keycode rồi báo cho web.
        if (KeyRemap.learnActive) {
            KeyRemap.capturedKey = code
            KeyRemap.learnActive = false
            return true
        }
        // Phím đã gán tay: thực hiện hành động (ưu tiên hơn mặc định).
        val entry = store.keyMapAction(code)
        if (entry != null) {
            val action = entry.optString("action", "")
            return handleRemapAction(action)
        }
        // v4.3: phím hệ thống có sẵn — launcher gốc Xiaomi xử lý, launcher
        // mình phải tự xử lý (gán tay chỉ gán được user app).
        return handleSystemKey(code)
    }

    /** Xử lý sẵn các nút hệ thống: Settings, Source/HDMI, Audio, TV. */
    private fun handleSystemKey(code: Int): Boolean {
        return try {
            when (code) {
                android.view.KeyEvent.KEYCODE_SETTINGS -> {
                    // (2026-11-09) Mở Cài đặt hệ thống PHẢI qua PIN — trước
                    // đây mở thẳng ACTION_SETTINGS, trẻ bấm nút bánh răng
                    // là vào được, từ đó Clear data (reset PIN), tắt Trợ
                    // năng, đổi giờ. Dùng luôn màn khóa mode "settings"
                    // (đã có PIN từ 04/11), target "system" để mở Cài đặt
                    // hệ thống sau khi nhập đúng PIN.
                    openSystemSettingsWithPin()
                }
                android.view.KeyEvent.KEYCODE_TV_INPUT -> {
                    // Mở app Live TV (có chọn nguồn) nếu có, không thì vào Cài đặt.
                    val li = packageManager.getLaunchIntentForPackage("com.google.android.tv")
                        ?: packageManager.getLaunchIntentForPackage("com.android.tv")
                    if (li != null) {
                        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(li)
                        true
                    } else {
                        openSystemSettingsWithPin()
                    }
                }
                android.view.KeyEvent.KEYCODE_TV,
                android.view.KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> {
                    val li = packageManager.getLaunchIntentForPackage("com.google.android.tv")
                        ?: packageManager.getLaunchIntentForPackage("com.android.tv")
                    if (li != null) {
                        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(li)
                        true
                    } else false
                }
                else -> false
            }
        } catch (e: Exception) {
            android.util.Log.e("GuardA11y", "handleSystemKey failed", e)
            false
        }
    }

    /** Mở Cài đặt hệ thống sau khi nhập đúng mã PIN khóa TV. */
    private fun openSystemSettingsWithPin(): Boolean {
        val i = Intent(this, LockActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        i.putExtra("mode", "settings")
        i.putExtra("target", "system")
        return try {
            LockActivity.showLock(this, i)
        } catch (_: Exception) { false }
    }

    private fun handleRemapAction(action: String): Boolean {
        return try {
            // Khi TV đang khóa: chặn mở app bằng nút đã gán (kẻo lọt qua
            // màn khóa), nhưng vẫn cho gửi tin nhắn và khóa ngay.
            // (2026-11-07: nút bind mở app vẫn mở được lúc khóa.)
            if (action.startsWith("app:")) {
                val now = System.currentTimeMillis()
                if (store.isLockedNow(now)) {
                    android.widget.Toast.makeText(this, "TV đang khóa",
                        android.widget.Toast.LENGTH_SHORT).show()
                    return true
                }
            }
            when {
                action == "lock" -> {
                    LockEngine.showManualLock(this, "Khóa nhanh bằng nút remote")
                    true
                }
                action.startsWith("app:") -> {
                    val pkg = action.removePrefix("app:")
                    val li = packageManager.getLaunchIntentForPackage(pkg)
                    if (li != null) {
                        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(li)
                        true
                    } else false
                }
                action.startsWith("msg:") -> {
                    // Gửi tin nhắn preset về điện thoại qua inbox (chạy nền).
                    val text = action.removePrefix("msg:")
                    Thread {
                        try {
                            KeyRemap.sendInboxMessage(this, text)
                        } catch (_: Exception) {}
                    }.start()
                    // Hiện toast xác nhận trên TV.
                    android.widget.Toast.makeText(this, "Đã gửi: $text",
                        android.widget.Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        } catch (_: Exception) { false }
    }
}

/** Trạng thái gán nút remote dùng chung giữa Service và WebServer. */
object KeyRemap {
    @Volatile var learnActive: Boolean = false
    @Volatile var capturedKey: Int = -1
    @Volatile var learnExpiresAt: Long = 0

    // Phím hệ thống không bao giờ gán lại: HOME, BACK, DPAD 4 hướng + OK.
    val SYSTEM_KEYS = setOf(3, 4, 19, 20, 21, 22, 23)

    fun startLearn(timeoutSec: Int = 15) {
        capturedKey = -1
        learnActive = true
        learnExpiresAt = System.currentTimeMillis() + timeoutSec * 1000L
    }

    fun pollLearn(): Int {
        if (!learnActive && capturedKey == -1) return -1
        if (System.currentTimeMillis() > learnExpiresAt) {
            learnActive = false
            return -1
        }
        return capturedKey
    }

    fun sendInboxMessage(ctx: android.content.Context, text: String) {
        // Gửi tin nhắn preset về điện thoại qua inbox server.
        // Token có sẵn trong InboxConfig (sinh lúc build).
        val url = "http://${InboxConfig.HOST}:${InboxConfig.PORT}"
        val body = org.json.JSONObject()
            .put("title", "Tin nhắn từ TV")
            .put("body", text)
            .put("target", "phone")
            .toString()
        val conn = java.net.URL("$url/api/notify").openConnection()
            as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${InboxConfig.TOKEN}")
        conn.connectTimeout = 8000
        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.inputStream.use { it.readBytes() }
        conn.disconnect()
    }
}
