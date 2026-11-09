package vn.tvlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

class GuardService : Service() {
    private lateinit var store: Store
    private var web: WebServer? = null
    private var tickThread: Thread? = null
    @Volatile private var running = false

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        // v2.6: lastPkg được lưu bền (SharedPreferences) và chỉ cập nhật
        // khi đổi app (qua Trợ năng). Sau khi service khởi động lại (cập
        // nhật app), KHÔNG xóa lastPkg — xóa là đếm giờ đứng im vì không
        // có sự kiện đổi app nào để nhận lại app đang xem (vd đang ở
        // YouTube sẵn lúc cài bản mới, 2026-11-06: đã xem kẹt ở 89 phút).
        // Chỉ reset mốc thời gian về "bây giờ" để elapsed không phình to
        // từ phiên trước. Trợ năng tự nhận lại app foreground lúc kết nối.
        store.lastPkgStartMs = System.currentTimeMillis()
        // v2.4: nếu app đã là Device Owner (đặt từ bản cũ, hoặc vừa cập nhật
        // đè lên bản đã đặt), đảm bảo chặn gỡ được bật — onEnabled của
        // DeviceOwnerReceiver chỉ chạy lúc mới đặt, không chạy khi cập nhật.
        DeviceOwnerPolicy.ensureBlocked(this)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("guard", "Khóa TV", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        startForeground(1, buildNotification())
        try { web = WebServer(this, 8080).also { it.start() } } catch (_: Exception) { }
        running = true
        tickThread = Thread({
            while (running) {
                try {
                    // v1.7: tick mỗi 1 giây (trước đây 15 giây) để đếm ngược
                    // từng giây trong 60 giây cuối trước khi khóa.
                    Thread.sleep(1000)
                    tick()
                } catch (_: InterruptedException) { break } catch (_: Exception) { }
            }
        }, "tvlock-tick").also { it.isDaemon = true; it.start() }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        // v4.0: poll hộp thư mỗi 10 phút (600 tick).
        if (++pollTickCounter >= 600) {
            pollTickCounter = 0
            pollInbox()
        }
        // v4.1: watchdog Tailscale — mỗi 60s kiểm tra 1 lần, chết thì
        // dựng dậy. BootReceiver chỉ chạy khi reboot thật; còn ngủ/thức
        // hay app tự chết giữa chừng thì cần lớp này.
        if (++tailscaleTickCounter >= 60) {
            tailscaleTickCounter = 0
            ensureTailscaleRunning()
        }
        // v2.0: chống tắt Trợ năng — ai tắt Trợ năng trong Cài đặt TV
        // thì bật lại ngay trong tích tắc này. Chỉ làm được khi app đã
        // được cấp quyền WRITE_SECURE_SETTINGS (Cài TV cấp sau khi cài).
        if (store.antiDisable) store.ensureAccessEnabled()
        store.tick(now)
        if (now < store.unlockUntilMs) {
            // Đang mở tạm bằng mã thưởng / nút +giờ: không hiện màn khóa,
            // nhưng PHẢI nhắc 5'/1' trước khi hết hạn — hết là khóa luôn.
            // (Fix 2026-11-02 chỉ sửa warnIfNeeded mà quên tick() return
            // sớm ở đây nên cảnh báo không bao giờ chạy.)
            warnIfNeeded(now)
            updateCountdown(now)
            updateNotification()
            return
        }
        if (store.isLockedNow(now)) {
            // Đang khóa: không nhắc nữa, gỡ banner nếu còn.
            WarningBus.hide()
            countdownActive = false
            bannerShown = false
            updateNotification()
            // v2.6: khóa qua web phải hiện màn hình khóa NGAY — trước đây
            // tick return sớm nên màn hình khóa chỉ hiện khi đổi app (qua
            // Trợ năng). Chỉ hiện khi app khóa chưa ở foreground (tránh
            // mở lại liên tục); đang mở tạm (unlockUntilMs) thì không hiện.
            // v2.9: app thời sự trong khung giờ thời sự được miễn trừ
            // tuyệt đối — đang xem thời sự trong khung thì KHÔNG hiện màn
            // khóa dù đang khóa tay hay trong lịch khóa.
            // v4.1: không hiện khóa khi đang ở app hệ thống được miễn trừ
            // (vd popup cấp quyền MediaProjection của systemui) — tránh
            // đá người dùng về màn khóa giữa chừng khi đang cài đặt.
            if (store.isLockedNow(now) && store.lastPkg != packageName &&
                !store.isExcluded(store.lastPkg) &&
                !store.isNewsExempt(store.lastPkg, now)) {
                val i = Intent(this, LockActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                i.putExtra("reason", store.lockReason(now))
                i.putExtra("mode", "lock")
                // v2.9: màn khóa này là khóa toàn TV (không phải chặn
                // riêng 1 app) — để LockActivity tự tắt về Home khi TV
                // hết khóa (vd hết khung khóa, mở khóa trên web).
                i.putExtra("wholeLock", true)
                LockActivity.showLock(this, i)
            }
            return
        }
        val pkg = store.lastPkg
        if (pkg.isNotEmpty() && !store.isExcluded(pkg) && pkg != packageName) {
            val reason = store.blockReason(pkg, now, store.appLabel(pkg))
            if (reason != null) {
                WarningBus.hide()
                countdownActive = false
                bannerShown = false
                val i = Intent(this, LockActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                i.putExtra("reason", reason)
                i.putExtra("mode", "lock")
                LockActivity.showLock(this, i)
            }
        }
        warnIfNeeded(now)
        updateCountdown(now)
        updateNotification()
    }

    // v1.7: trạng thái banner nhắc (overlay) + đếm ngược 60 giây cuối.
    private var countdownActive = false
    private var bannerShown = false
    private var bannerShownAtMs = 0L
    // Số tick liên tiếp mà deadline nằm trong 1..10s — chống nhá 1 tick
    // sai lúc TV vừa bật (trạng thái chưa ổn định).
    private var countdownConfirm = 0
    private var lastNotifText = ""

    // ============ v4.0: poll hộp thư Tinker ============
    private var pollTickCounter = 590 // poll lần đầu sau ~10s chạy service

    // ============ v4.1: watchdog Tailscale ============
    private var tailscaleTickCounter = 55 // check lần đầu sau ~5s chạy service
    private var lastTailscaleLaunch = 0L // chống spam mở liên tục

    /** Kiểm tra Tailscale còn sống không; chết thì mở lại.
     *  v4.1.2: check interface tailscale0 trực tiếp (chính xác hơn
     *  ConnectivityManager — có lúc VPN đã lên nhưng capabilities chưa
     *  cập nhật kịp). Mở ngầm bằng service thay vì activity (không hiện
     *  màn hình Tailscale); chỉ mở activity khi service không chạy được.
     *  Cooldown 15 phút. */
    private fun ensureTailscaleRunning() {
        try {
            // Tailscale chạy hay không: kiểm tra IP CGNAT 100.64.0.0/10 trên
            // BẤT KỲ interface nào đang up. Trước đây kiểm tên "tailscale0"
            // nhưng TV này Tailscale dùng "tun1" (100.71.81.69) nên chó canh
            // cứ tưởng chết, 60s lại gọi mở service 1 lần và lôi UI lên.
            val hasTs = try {
                java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.any { ni ->
                    ni.isUp && ni.inetAddresses.toList().any { addr ->
                        val b = addr.address
                        b.size == 4 && (b[0].toInt() and 0xFF) == 100 &&
                            ((b[1].toInt() and 0xFF) and 0xC0) == 0x40
                    }
                } == true
            } catch (_: Exception) { false }
            if (hasTs) return
            val now = System.currentTimeMillis()
            if (now - lastTailscaleLaunch < 15 * 60 * 1000) return
            lastTailscaleLaunch = now
            // Mở ngầm: start service IPNService (không hiện UI).
            val svcStarted = try {
                val svc = Intent()
                svc.setClassName("com.tailscale.ipn", "com.tailscale.ipn.IPNService")
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(svc)
                else startService(svc)
                true
            } catch (_: Exception) { false }
            if (!svcStarted) {
                val ts = packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
                ts?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (ts != null) startActivity(ts)
            }
        } catch (_: Exception) { }
    }

    private fun pollInbox() {
        // Không poll khi đang khóa (tin nhắn chờ trong chuông).
        val now = System.currentTimeMillis()
        InboxClient.pollAndCache(store) { msgs ->
            if (msgs.isNullOrEmpty()) return@pollAndCache
            routeInboxPopup(msgs.maxByOrNull { it.createdAt } ?: return@pollAndCache, now)
        }
    }

    /** Quyết định có popup thẻ thông báo không (theo cài đặt của user). */
    private fun routeInboxPopup(msg: InboxClient.Message, now: Long) {
        // Đang khóa → chỉ chuông, không popup.
        if (store.isLockedNow(now)) return
        // Ngoài khung giờ → chỉ chuông.
        if (!store.inPushHours(now)) return
        when (store.popupMode) {
            "silent" -> return
            "home" -> {
                // Chỉ popup khi đang ở màn hình chính (app vn.tvlock).
                if (store.lastPkg != packageName) return
            }
        }
        val i = Intent(this, NotificationPopupActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        i.putExtra("title", msg.title)
        i.putExtra("body", msg.body)
        i.putExtra("type", msg.type)
        try { startActivity(i) } catch (_: Exception) { }
    }

    /** Hạn gần nhất còn chạy (số giây, nhãn) — hạn ngày, hạn app, giờ khóa. */
    private fun nearestDeadline(now: Long): Pair<Int, String>? {
        var best: Pair<Int, String>? = null
        fun consider(sec: Int, label: String) {
            if (sec in 1..3600) {
                val cur = best
                if (cur == null || sec < cur.first) best = sec to label
            }
        }
        // v2.11: chỉ xét các hạn tính theo giờ xem khi đang thực sự tính giờ
        // (isCountingNow) — nếu không, con số là dữ liệu cũ đóng băng (vd vừa
        // khởi động TV, lastPkg còn rỗng) và đếm ngược sẽ đứng yên một chỗ.
        // Hạn theo giờ giấc (lịch khóa) thì luôn xét vì không phụ thuộc đếm giờ.
        if (store.isCountingNow(now)) {
            consider(store.secondsUntilDailyLimit(now), "hết giờ xem")
            val pkg = store.lastPkg
            if (pkg.isNotEmpty() && !store.isExcluded(pkg) && pkg != packageName) {
                consider(store.secondsUntilAppLimit(pkg, now), "hết giờ cho app ${store.appLabel(pkg)}")
            }
        }
        consider(store.secondsUntilNextLock(now), "đến giờ khóa TV")
        // Mở tạm bằng mã thưởng: hết hạn là khóa luôn nên cũng phải đếm
        // ngược 10 giây cuối (user 2026-11-02).
        val unlockMs = store.unlockUntilMs
        if (unlockMs > now) consider(((unlockMs - now) / 1000).toInt(), "hết giờ mở tạm")
        return best
    }

    /** v1.7: 60 giây cuối đếm ngược từng giây ("Còn 42 giây nữa là...").
     * Tick chạy mỗi 1 giây nên số giây luôn tươi. Hết hạn / được cộng thêm
     * giờ thì banner tự gỡ. Banner nhắc 5'/1' tự tắt sau 20 giây. */
    private fun updateCountdown(now: Long) {
        val deadline = nearestDeadline(now)
        // v2.6: đếm ngược từng giây chỉ 10 giây cuối (trước đây 60 giây).
        if (deadline != null && deadline.first in 1..10) {
            // Debounce: chỉ hiện đếm ngược khi deadline đã ổn định 2 tick
            // liên tiếp — 1 tick sai lúc TV vừa bật (vd "5s nữa khóa" lúc
            // 19:30 trong khung thời sự) không làm banner nhá lên rồi mất.
            countdownConfirm++
            if (countdownConfirm >= 2) {
                val (sec, label) = deadline
                countdownActive = true
                val s = if (sec == 1) "1 giây" else "$sec giây"
                showBanner("Còn $s nữa là $label", "Nhớ lưu lại việc đang xem nhé!")
            }
            return
        }
        countdownConfirm = 0
        if (countdownActive) {
            countdownActive = false
            bannerShown = false
            WarningBus.hide()
            return
        }
        // v2.6: banner nhắc 5'/1' chỉ hiện 5 giây (trước đây 20 giây).
        if (bannerShown && System.currentTimeMillis() - bannerShownAtMs > 5000) {
            bannerShown = false
            WarningBus.hide()
        }
    }

    /** Chỉ cập nhật notification khi nội dung đổi (tick nay chạy mỗi giây). */
    private fun updateNotification() {
        val text = notificationText()
        if (text == lastNotifText) return
        lastNotifText = text
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        try { nm.notify(1, buildNotification()) } catch (_: Exception) { }
    }

    /**
     * v3: gentle warning when 5 or 1 minutes remain before the daily limit
     * (including bonus), the current app's own limit, or the lock window
     * starts. v1.7: warnings show as a non-blocking overlay banner
     * (WarningOverlay via GuardAccessibilityService), never as a popup that
     * pauses the app being watched. Each threshold fires once per deadline;
     * never launches the lock screen and never counts as viewing
     * (vn.tvlock is excluded from watch time).
     */
    private fun warnIfNeeded(now: Long) {
        // Mở tạm bằng mã thưởng / nút +giờ (unlockUntilMs): phải nhắc 5'..1'
        // trước khi hết hạn vì hết là khóa luôn — trước đây return sớm nên
        // không hề có cảnh báo, bụp một cái là khóa (user 2026-11-02).
        val unlockMs = store.unlockUntilMs
        if (unlockMs > now) {
            val remMin = ((unlockMs - now + 59999) / 60000).toInt()
            if (remMin in 1..5 && !store.warned("tempunlock", remMin)) {
                store.markWarned("tempunlock", remMin)
                showBanner(
                    "Còn $remMin phút nữa là hết giờ mở tạm",
                    "Nhập mã thưởng mới hoặc nhờ bố cộng giờ nhé!"
                )
            }
            return
        }
        if (store.isLockedNow(now)) return
        val pkg = store.lastPkg
        // 1) Current app's own limit.
        if (pkg.isNotEmpty() && !store.isExcluded(pkg) && pkg != packageName) {
            val appRem = store.appLimitRemainingMin(pkg)
            if (appRem in 1..5) {
                val kind = "app:$pkg"
                if (!store.warned(kind, appRem)) {
                    store.markWarned(kind, appRem)
                    showBanner(
                        "Còn $appRem phút nữa là hết giờ cho app ${store.appLabel(pkg)}",
                        "Hết giờ app này thì vẫn xem được app khác."
                    )
                    return
                }
            }
        }
        // 2) Daily limit (including bonus pool).
        val rem = store.remainingMin()
        if (rem in 1..5 && !store.warned("daily", rem)) {
            store.markWarned("daily", rem)
            val sub = if (store.bonusMin > 0) "Trong đó có ${store.bonusMin} phút thưởng."
                else "Bố có thể cộng thêm giờ từ điện thoại."
            showBanner("Còn $rem phút nữa là hết giờ xem", sub)
            return
        }
        // 3) Next lock schedule start.
        // v1.5: ngoài "cửa sổ" đếm ngược (còn >5') thì xóa cờ lockwindow, để
        // mỗi khung khóa đều được nhắc 5'/1' riêng — trước đây cờ chỉ reset
        // mỗi ngày nên khung khóa thứ 2 trong ngày mất luôn cảnh báo 5'.
        val untilLock = store.minutesUntilNextLock(now)
        if (untilLock > 5) store.clearWarningsKind("lockwindow")
        if (untilLock in 1..5 && !store.warned("lockwindow", untilLock)) {
            store.markWarned("lockwindow", untilLock)
            showBanner(
                "Còn $untilLock phút nữa là đến giờ khóa TV",
                "Nhớ lưu lại việc đang xem nhé!"
            )
        }
    }

    /** v1.7: nhắc bằng banner overlay (không chắn app đang xem, không cướp
     * focus); chỉ khi Trợ năng chưa chạy mới rơi về popup WarningActivity cũ. */
    private fun showBanner(message: String, sub: String) {
        bannerShown = true
        bannerShownAtMs = System.currentTimeMillis()
        if (!WarningBus.show(message, sub)) launchWarning(message, sub)
    }

    /** v2.7: gửi tin nhắn lên TV — mở MessageActivity (thẻ tin nhắn giữa
     *  màn hình, nền trong suốt để vẫn thấy video phía sau, tự tắt sau
     *  5 giây rồi quay lại app đang xem). Dùng Activity vì cả hai cơ chế
     *  không chắn video đều không hiển thị được trên TV này: overlay
     *  Trợ năng (TYPE_ACCESSIBILITY_OVERLAY) không tạo được cửa sổ,
     *  còn Toast hệ thống bị chặn (không ném lỗi nhưng cũng không hiện).
     *  Đánh đổi: video tạm dừng trong 5 giây hiện tin nhắn. Gọi từ
     *  WebServer (trang web trên điện thoại). */
    fun showMessage(text: String) {
        val i = Intent(this, MessageActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        i.putExtra("text", text)
        try { startActivity(i) } catch (e: Exception) {
            Log.e("GuardService", "showMessage: khong mo duoc MessageActivity: $e")
        }
    }

    private fun launchWarning(message: String, sub: String) {
        val i = Intent(this, WarningActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        i.putExtra("message", message)
        i.putExtra("sub", sub)
        try { startActivity(i) } catch (_: Exception) { }
    }

    private fun notificationText(): String {
        store.resetUsedIfNewDay()
        val locked = store.isLockedNow(System.currentTimeMillis())
        val rem = store.remainingMin()
        val bonus = if (store.bonusMin > 0) " • thưởng còn ${store.bonusMin} phút" else ""
        val limit = store.limitForToday()
        return if (locked) "TV đang khóa" else if (limit > 0)
            "Đã xem ${store.usedTodayMin}/$limit phút — còn lại $rem phút$bonus"
        else "Đã xem ${store.usedTodayMin} phút hôm nay$bonus"
    }

    private fun buildNotification(): Notification {
        val text = notificationText()
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, "guard") else Notification.Builder(this)
        return b.setContentTitle("Khóa TV").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi).setOngoing(true).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        tickThread?.interrupt()
        try { web?.stop() } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
