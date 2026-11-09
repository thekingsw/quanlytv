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
                    // Repair a lastPkg stuck at an excluded app (written by
                    // older buggy versions), then re-read the foreground app
                    // when we don't have a reliable one: empty (unknown),
                    // or excluded (lock screen / system app — re-check
                    // what's really in front).
                    // (2026-11-07 18:55: lastPkg kẹt ở vn.tvlock sau khi mở
                    // thời sự từ màn khóa — sự kiện cửa sổ của màn khóa tới
                    // sau, ghi đè lastPkg; web tưởng không phải thời sự nên
                    // báo khóa trong khi TV đang cho xem thời sự đúng khung.)
                    store.repairStuckForeground(now)
                    if (store.lastPkg.isEmpty() || store.isExcluded(store.lastPkg)) refreshForegroundApp()
                    // (2026-11-09) Chỉ 1 hàm duy nhất quyết định khóa hay
                    // mở: LockPolicy.isLockedNow. Hiện màn khóa thì gọi
                    // thẳng launchLock → showLock tự chống hiện lại bằng
                    // cờ isShowing + kẹp 3 giây nên không lặp (refresh
                    // 88 lần, 2026-11-08). Không còn shouldShowLock riêng lẻ.
                    if (store.isLockedNow(now)) {
                        val reason = store.lockReason(now)
                        launchLock(reason)
                    }
                }
            } catch (e: Exception) {
                // Fail-CLOSED (2026-11-09): lỗi trong lockPoll không được
                // để TV mở khóa — thử hiện màn khóa nếu TV đang khóa.
                // (Trước đây catch rỗng = fail-open: lỗi thì không khóa.)
                android.util.Log.e("GuardA11y", "lockPoll failed", e)
                try {
                    if (::store.isInitialized && store.isLockedNow(System.currentTimeMillis())) {
                        launchLock(store.lockReason(System.currentTimeMillis()))
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

    /** Đọc app foreground hiện tại qua cửa sổ đang focus. Chỉ nhận khi là
     * app người dùng thật — mọi lần ghi lastPkg đều đi qua
     * ForegroundTracker (app hệ thống không ghi đè app đang theo dõi).
     * (2026-11-06: nhận bừa cửa sổ hệ thống làm lastPkg thành app miễn trừ,
     * chặn cả đếm giờ lẫn hiện màn khóa — web báo khóa mà TV vẫn chạy.) */
    private fun refreshForegroundApp() {
        try {
            val now = System.currentTimeMillis()
            val wins = windows ?: return
            var hasFocused = false
            // Ưu tiên cửa sổ đang focus.
            for (w in wins) {
                if (!w.isFocused) continue
                hasFocused = true
                val pkg = w.root?.packageName?.toString() ?: continue
                if (pkg.isEmpty()) continue
                // Mọi lần ghi đều qua ForegroundTracker: app hệ thống
                // không ghi đè app đang theo dõi; màn khóa (vn.tvlock)
                // thì ghi đè để lockPoll biết màn khóa đang hiện.
                // (2026-11-08: bỏ qua màn khóa làm lastPkg rỗng,
                // lockPoll fail-safe gọi lại màn khóa liên tục.)
                store.noteForeground(pkg, now)
                // Cửa sổ focus là app thật (hoặc màn khóa) thì foreground
                // đã rõ — dừng lại. Cửa sổ focus là app hệ thống (vd
                // popup cấp quyền của systemui) thì tìm tiếp — có thể
                // còn cửa sổ focus khác là app thật.
                if (!store.isExcluded(pkg) || pkg == packageName) return
            }
            // Có cửa sổ đang focus (vd chính màn khóa) thì foreground đã
            // rõ — đừng quét cửa sổ nền rồi nhận bừa app sau lưng làm
            // foreground. (2026-11-08: làm vậy khiến lockPoll tưởng đang
            // ở app khác, gọi lại màn khóa mỗi giây — màn khóa nhấp nháy
            // liên tục, 88 lần trong log.)
            if (hasFocused) return
            // Không có cửa sổ focus nào (vd vừa khởi động lại): quét tất cả,
            // lấy app hợp lệ đầu tiên — vẫn hơn để lastPkg rỗng.
            for (w in wins) {
                val pkg = w.root?.packageName?.toString() ?: continue
                if (pkg.isEmpty() || pkg == packageName) continue
                if (store.isExcluded(pkg)) continue
                store.noteForeground(pkg, now)
                return
            }
            // Không xác định được app thật thì giữ nguyên lastPkg cũ —
            // sự kiện đổi app tiếp theo sẽ sửa lại, đừng ghi đè bừa.
            // Ngoại lệ: nếu lastPkg đang kẹt ở app hệ thống (do bản lỗi
            // trước ghi vào) thì xóa nó — giá trị miễn trừ chặn cả đếm
            // giờ lẫn hiện màn khóa, để rỗng thì khóa vẫn hiện được.
            store.repairStuckForeground(now)
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
        val now = System.currentTimeMillis()
        val prev = store.lastPkg
        // Accumulate time for the previous foreground app.
        if (prev.isNotEmpty() && prev != pkg) {
            store.addElapsed(prev, now)
        }
        // Single rule via ForegroundTracker: excluded system apps do NOT
        // overwrite the tracked app (old code assigned first, checked
        // excluded after — lastPkg got stuck at systemui/settings,
        // blocking both counting and the lock screen).
        store.noteForeground(pkg, now)
        if (store.isExcluded(pkg) || pkg == packageName) return
        val reason = store.blockReason(pkg, now, store.appLabel(pkg))
        if (reason != null) launchLock(reason)
    }

    private fun launchLock(reason: String) {
        // Don't relaunch if LockActivity is already foreground.
        // (2026-11-08: LockActivity đã là singleTop nên gọi lại cũng
        // không tạo mới nữa — chế độ nhập mã thưởng không bị reset.)
        if (store.lastPkg == packageName) return
        val i = Intent(this, LockActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        i.putExtra("reason", reason)
        i.putExtra("mode", "lock")
        LockActivity.showLock(this, i)
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
                    val i = Intent(this, LockActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    i.putExtra("reason", "Khóa nhanh bằng nút remote")
                    i.putExtra("mode", "lock")
                    LockActivity.showLock(this, i)
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
