package vn.tvlock

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/** Minimal hand-rolled HTTP server on port 8080 (no com.sun.net.httpserver on Android). */
class WebServer(private val ctx: Context, private val port: Int) {
    private var server: ServerSocket? = null
    @Volatile private var running = false
    private var acceptThread: Thread? = null

    // ---------- v2.3: mã PIN web + phiên đăng nhập ----------
    // Phiên: token UUID ngẫu nhiên, lưu trong bộ nhớ (mất khi app/TV
    // khởi động lại), hạn 30 ngày, cookie tvlock_session.
    private val sessions = mutableMapOf<String, Long>()
    private var failedLogins = 0
    private var lockUntilMs = 0L

    companion object {
        private const val SESSION_MS = 30L * 24 * 3600 * 1000 // 30 ngày
        private const val SESSION_COOKIE = "tvlock_session"
        /** Max request body (8MB — ảnh nền lớn nhất cho phép). Vượt quá thì
         *  413, không cấp phát CharArray theo Content-Length kẻo OOM. */
        private const val MAX_BODY_BYTES = 8 * 1024 * 1024
    }

    /** Bounded thread pool for HTTP handlers (max 8 concurrent). */
    private val pool: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newFixedThreadPool(8) { r ->
            Thread(r, "tvlock-http").also { it.isDaemon = true }
        }

    fun start() {
        server = ServerSocket(port)
        running = true
        acceptThread = Thread({
            while (running) {
                try {
                    val s = server?.accept() ?: break
                    // Bounded pool (2026-11-09): trước đây mỗi kết nối sinh
                    // 1 thread mới — tấn công mở nhiều kết nối là cạn thread.
                    pool.execute { handle(s) }
                } catch (_: Exception) { if (!running) break }
            }
        }, "tvlock-accept").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) { }
        pool.shutdownNow()
    }

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), "UTF-8"))
            val reqLine = reader.readLine() ?: return
            val parts = reqLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val rawPath = parts[1]
            var contentLength = 0
            var cookieHeader = ""
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val name = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (name.equals("Content-Length", true)) {
                        contentLength = value.toIntOrNull() ?: 0
                    } else if (name.equals("Cookie", true)) {
                        cookieHeader = value
                    }
                }
            }
            val out = sock.getOutputStream()
            var body = ""
            if (contentLength > MAX_BODY_BYTES) {
                // (2026-11-09) Kẹp Content-Length: trước đây cấp phát
                // CharArray(contentLength) theo header kẻo OOM với
                // Content-Length giả. Ảnh nền lớn nhất cho phép là 8MB.
                sendJson(out, 413, """{"ok":false,"error":"Nội dung quá lớn"}""")
                return
            }
            if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                body = String(buf, 0, read)
            }
            val path = rawPath.substringBefore("?")
            val query = if (rawPath.contains("?")) rawPath.substringAfter("?") else ""
            val params = parseParams(query)
            if (method == "POST") params.putAll(parseParams(body))
            // v2.3: mã PIN web — mọi route đều cần phiên hợp lệ, trừ file
            // tĩnh giao diện (app.css/app.js, vô hại) và login/logout.
            // Trang chưa đăng nhập thì trả về trang chính để app.js tự
            // hiện màn hình nhập PIN; /api/* chưa đăng nhập thì 401.
            val authed = isAuthed(cookieHeader)
            val publicStatic = method == "GET" && (path == "/app.css" || path == "/app.js")
            val publicAuth = method == "POST" &&
                (path == "/api/webpin-login" || path == "/api/webpin-logout")
            if (!authed && !publicStatic && !publicAuth) {
                if (path.startsWith("/api/")) {
                    sendJson(out, 401, """{"error":"unauthorized"}""")
                } else {
                    serveStatic(out, "web/index.html", "text/html; charset=utf-8")
                }
                return
            }
            when {
                // Web mới (tĩnh) ở "/" — web cũ đã xóa hẳn từ bản này, chỉ còn JSON API.
                method == "GET" && path == "/" -> serveStatic(out, "web/index.html", "text/html; charset=utf-8")
                method == "GET" && path == "/index.html" -> serveStatic(out, "web/index.html", "text/html; charset=utf-8")
                method == "GET" && path == "/app.css" -> serveStatic(out, "web/app.css", "text/css; charset=utf-8")
                method == "GET" && path == "/app.js" -> serveStatic(out, "web/app.js", "application/javascript; charset=utf-8")
                method == "GET" && path == "/api/status" -> status(out, params)
                method == "GET" && path == "/api/report" -> report(out, params)
                method == "GET" && path == "/api/lockbg" -> lockBg(out)
                method == "GET" && path == "/api/schedules" -> schedules(out)
                method == "GET" && path == "/api/limits" -> limits(out)
                method == "GET" && path == "/api/settings" -> settingsJson(out)
                method == "GET" && path == "/api/newsapps" -> newsApps(out)
                method == "GET" && path == "/api/apprules" -> appRulesJson(out)
                method == "GET" && path == "/api/screenshot" -> screenshot(out)
                method == "POST" && path == "/api/webpin-login" -> webpinLogin(out, params)
                method == "POST" && path == "/api/webpin-change" -> webpinChange(out, params)
                method == "POST" && path == "/api/webpin-logout" -> webpinLogout(out, params, cookieHeader)
                method == "POST" && path == "/api/lock" -> action(out, params, "lock")
                method == "POST" && path == "/api/unlock" -> action(out, params, "unlock")
                method == "POST" && path == "/api/add" -> action(out, params, "add")
                method == "POST" && path == "/api/bonus" -> bonus(out, params)
                method == "GET" && path == "/api/reward-codes" -> rewardCodes(out)
                method == "POST" && path == "/api/reward-code-generate" -> rewardCodeGenerate(out, params)
                method == "POST" && path == "/api/reward-code-delete" -> rewardCodeDelete(out, params)
                method == "POST" && path == "/api/settings-reward-limit" -> settingsRewardLimit(out, params)
                method == "POST" && path == "/api/message" -> message(out, params)
                method == "POST" && path == "/api/change-pin" -> changePin(out, params)
                method == "POST" && path == "/api/settings-news" -> settingsNews(out, params)
                method == "POST" && path == "/api/settings-limits" -> settingsLimits(out, params)
                method == "POST" && path == "/api/settings-schedule" -> settingsSchedule(out, params)
                method == "POST" && path == "/api/settings-schedule-delete" -> settingsScheduleDelete(out, params)
                method == "POST" && path == "/api/settings-lockmsg" -> settingsLockMsg(out, params)
                method == "POST" && path == "/api/settings-lockbg" -> settingsLockBg(out, params)
                method == "POST" && path == "/api/settings-lockbg-remove" -> settingsLockBgRemove(out, params)
                // v4.0: ảnh nền màn hình chính (slideshow) — tải từ web điện thoại.
                method == "POST" && path == "/api/settings-homebg" -> settingsHomeBg(out, params)
                method == "GET" && path == "/api/homebg-list" -> homeBgList(out)
                method == "GET" && path == "/api/homebg-thumb" -> homeBgThumb(out, params)
                method == "GET" && path == "/api/homebg-default-thumb" -> homeBgDefaultThumb(out, params)
                method == "POST" && path == "/api/settings-wpsource" -> settingsWpSource(out, params)
                method == "POST" && path == "/api/settings-homebg-delete" -> settingsHomeBgDelete(out, params)
                method == "POST" && path == "/api/settings-homebg-clear" -> settingsHomeBgClear(out)
                method == "POST" && path == "/api/settings-wpinterval" -> settingsWpInterval(out, params)
                method == "GET" && path == "/api/widgets" -> widgetsJson(out)
                method == "POST" && path == "/api/settings-widgets" -> settingsWidgets(out, params)
                // v4.0: đồng bộ launcher lên web (user 2026-11-02 — trước
                // đây các mục này chỉ đổi được trên TV).
                method == "GET" && path == "/api/launcher" -> launcherJson(out)
                method == "POST" && path == "/api/settings-countdown" -> settingsCountdown(out, params)
                method == "POST" && path == "/api/settings-push" -> settingsPush(out, params)
                method == "POST" && path == "/api/settings-iconstyle" -> settingsIconStyle(out, params)
                method == "POST" && path == "/api/settings-weathercity" -> settingsWeatherCity(out, params)
                method == "GET" && path == "/api/hidden-apps" -> hiddenAppsJson(out)
                method == "POST" && path == "/api/settings-hiddenapp" -> settingsHiddenApp(out, params)
                method == "POST" && path == "/api/settings-antidisable" -> settingsAntiDisable(out, params)
                method == "POST" && path == "/api/device-owner-remove" -> deviceOwnerRemove(out)
                method == "POST" && path == "/api/settings-apprule" -> settingsAppRule(out, params)
                // v4.3: gán nút remote (keycode -> hành động)
                method == "GET" && path == "/api/keymap" -> keyMapJson(out)
                method == "POST" && path == "/api/keymap" -> keyMapSave(out, params)
                method == "POST" && path == "/api/keymap-delete" -> keyMapDelete(out, params)
                method == "POST" && path == "/api/keymap-learn" -> keyMapLearn(out)
                method == "GET" && path == "/api/keymap-learn" -> keyMapLearnPoll(out)
                else -> sendJson(out, 404, """{"ok":false,"error":"<h1>Không tìm thấy</h1><a href='/'>Về trang chính</a>"}""")
            }
        } catch (_: Exception) {
        } finally {
            try { sock.close() } catch (_: Exception) { }
        }
    }

    private fun parseParams(s: String): MutableMap<String, String> {
        val m = mutableMapOf<String, String>()
        if (s.isEmpty()) return m
        for (pair in s.split("&")) {
            val i = pair.indexOf('=')
            if (i > 0) {
                try {
                    m[URLDecoder.decode(pair.substring(0, i), "UTF-8")] =
                        URLDecoder.decode(pair.substring(i + 1), "UTF-8")
                } catch (_: Exception) { }
            }
        }
        return m
    }

    private fun status(out: OutputStream, params: Map<String, String>) {
        // v2.3: route này đã qua cổng kiểm tra phiên (mã PIN web) ở handle().
        val store = Store(ctx)
        store.resetUsedIfNewDay()
        val now = System.currentTimeMillis()
        val locked = store.isLockedNow(now)
        val rem = store.remainingMin()
        val remStr = if (rem == Int.MAX_VALUE) -1 else rem
        val news = store.isInNewsSlot(now)
        val perApp = store.perAppToday().entries.joinToString(",") { "\"${it.key}\":${it.value}" }
        val todaySched = store.todayScheduleSummaries(now).joinToString(",") { "\"${jsonEsc(it)}\"" }
        // Giai đoạn 5: "Khóa đêm" cho web mới — trong lịch hôm nay ưu tiên
        // lịch qua đêm, không có thì lịch đầu tiên, không có lịch nào thì null.
        val cal = java.util.Calendar.getInstance()
        val todaysSched = store.schedulesForDay(store.dayIndexOf(cal))
        val night = todaysSched.firstOrNull { it.startMin > it.endMin } ?: todaysSched.firstOrNull()
        val nightLock = night?.let { "\"${store.fmt(it.startMin)} – ${store.fmt(it.endMin)}\"" } ?: "null"
        val json = """{"locked":$locked,"usedTodayMin":${store.usedTodayMin},"dailyLimitMin":${store.limitForToday()},"todayLimit":${store.limitForToday()},"todaySchedules":[$todaySched],"remainingMin":$remStr,"newsSlot":$news,"ip":"${wifiIp()}","manualLocked":${store.manualLocked},"bonusMin":${store.bonusMin},"perAppToday":{$perApp},"antiDisable":${store.antiDisable},"a11yEnabled":${store.isAccessEnabled()},"hasWss":${store.hasWriteSecureSettings()},"nightLock":$nightLock}"""
        sendJson(out, 200, json)
    }

    /** v2.1: trả ảnh nền màn hình khóa đang lưu (JPEG) để trang Cài đặt
     *  trên điện thoại vẽ được bản xem trước giống trên TV. */
    private fun lockBg(out: OutputStream) {
        val f = Store(ctx).lockBgFile()
        if (!f.exists()) {
            sendJson(out, 404, """{"ok":false,"error":"Chưa có ảnh nền riêng."}""")
            return
        }
        val bytes = try { f.readBytes() } catch (_: Exception) {
            sendJson(out, 500, """{"ok":false,"error":"Không đọc được ảnh."}""")
            return
        }
        val head = "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    /** v3: report as JSON — today (total + per-app minutes) and the last 14 days. */
    private fun report(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        store.resetUsedIfNewDay()
        val perApp = store.perAppToday().entries.joinToString(",") { "\"${it.key}\":${it.value}" }
        val days = store.historyDays().joinToString(",") { "{\"date\":\"${it.date}\",\"total\":${it.total}}" }
        val json = """{"today":{"date":"${store.todayStr()}","total":${store.usedTodayMin},"limit":${store.limitForToday()},"perApp":{$perApp}},"days":[$days]}"""
        sendJson(out, 200, json)
    }

    /** v3: grant bonus minutes from the phone (cần phiên mã PIN web — cổng kiểm tra ở handle). */
    private fun bonus(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val min = params["minutes"]?.toIntOrNull() ?: 0
        if (min !in setOf(5, 10, 15, 30)) {
            sendJson(out, 400, """{"ok":false,"error":"Số phút thưởng không hợp lệ."}""")
            return
        }
        store.addBonus(min)
        sendJson(out, 200, """{"ok":true}""")
    }

    private fun action(out: OutputStream, params: Map<String, String>, kind: String) {
        // v2.3: cần phiên mã PIN web (cổng kiểm tra ở handle).
        // Any "pin" param sent by a client is ignored.
        // The TV lock screen itself still requires the PIN.
        val store = Store(ctx)
        var msg = ""
        var error = false
        when (kind) {
            "lock" -> { store.manualLocked = true; store.unlockUntilMs = 0L; msg = "Đã khóa TV ngay." }
            "unlock" -> {
                store.manualLocked = false
                store.unlockUntilMs = System.currentTimeMillis() + 60 * 60000L
                msg = "Đã mở khóa TV trong 60 phút."
                // v2.9: báo cho màn hình khóa (nếu đang hiện) về màn hình
                // chính ngay, không cần bấm Home. LockActivity đăng ký
                // receiver động nên nhận được broadcast này; setPackage
                // để thành broadcast tường minh trong app.
                try {
                    val b = Intent("vn.tvlock.UNLOCKED")
                    b.setPackage(ctx.packageName)
                    ctx.sendBroadcast(b)
                } catch (_: Exception) { }
            }
            "add" -> {
                val min = params["minutes"]?.toIntOrNull() ?: 0
                if (min !in setOf(5, 10, 15, 20, 30)) { msg = "Số phút không hợp lệ."; error = true }
                else { store.addMinutes(min); msg = "Đã cho thêm $min phút." }
            }
            else -> { msg = "Không rõ lệnh."; error = true }
        }
        if (error) sendJson(out, 400, """{"ok":false,"error":"${jsonEsc(msg)}"}""")
        else sendJson(out, 200, """{"ok":true,"message":"${jsonEsc(msg)}"}""")
    }

    /** v2.8: mã thưởng dùng một lần. GET /api/reward-codes — danh sách mã
     *  đang còn hiệu lực (để bố xem/thu hồi). Cần phiên mã PIN web (cổng
     *  kiểm tra ở handle, như mọi route /api/ khác).
     *  v2.9: trả thêm limit (0 = không giới hạn) + usedToday để tab Mã
     *  thưởng hiển thị "Hôm nay đã nhập X / Y mã". */
    private fun rewardCodes(out: OutputStream) {
        val store = Store(ctx)
        val items = store.activeRewardCodes().joinToString(",") {
            """{"code":"${jsonEsc(it.code)}","minutes":${it.minutes},"createdAt":${it.createdAt}}"""
        }
        sendJson(out, 200, """{"codes":[$items],"limit":${store.dailyRewardCodeLimit},"usedToday":${store.rewardCodesUsedToday()}}""")
    }

    /** v2.9: POST /api/settings-reward-limit {limit} — số mã được nhập
     *  mỗi ngày (0 = không giới hạn, số âm thì về 0). */
    private fun settingsRewardLimit(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val limit = (params["limit"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
        store.dailyRewardCodeLimit = limit
        sendJson(out, 200, """{"ok":true,"limit":$limit}""")
    }

    /** v2.8: POST /api/reward-code-generate {minutes: 5|10} — tạo mã mới. */
    private fun rewardCodeGenerate(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val minutes = params["minutes"]?.toIntOrNull() ?: 0
        if (minutes !in setOf(5, 10)) {
            sendJson(out, 200, """{"ok":false,"error":"Chỉ tạo được mã +5 hoặc +10 phút."}""")
            return
        }
        val rc = store.generateRewardCode(minutes)
        sendJson(out, 200, """{"ok":true,"code":"${jsonEsc(rc.code)}","minutes":${rc.minutes}}""")
    }

    /** v2.8: POST /api/reward-code-delete {code} — thu hồi một mã. */
    private fun rewardCodeDelete(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        store.deleteRewardCode(params["code"]?.trim() ?: "")
        sendJson(out, 200, """{"ok":true}""")
    }

    /** v2.7: POST /api/message {text} — gửi tin nhắn lên TV (banner overlay,
     *  tự tắt sau 5 giây, không pause video). Cần phiên mã PIN web (cổng
     *  kiểm tra ở handle). Giới hạn 100 ký tự để banner không quá dài. */
    private fun message(out: OutputStream, params: Map<String, String>) {
        val text = params["text"]?.trim() ?: ""
        if (text.isEmpty()) {
            sendJson(out, 400, """{"ok":false,"error":"Chưa nhập tin nhắn"}""")
            return
        }
        val msg = if (text.length > 100) text.substring(0, 100) else text
        (ctx as? GuardService)?.showMessage(msg)
        sendJson(out, 200, """{"ok":true}""")
    }

    /** Change the TV PIN from the phone — no old PIN required, so a forgotten
        PIN can be reset from the phone. New PIN must be 4–6 digits only. */
    private fun changePin(out: OutputStream, params: Map<String, String>) {
        val newPin = params["newPin"]?.trim() ?: ""
        if (!newPin.matches(Regex("^\\d{4,6}$"))) {
            sendJson(out, 400, """{"ok":false,"error":"Mã PIN mới phải là 4–6 chữ số."}""")
            return
        }
        Store(ctx).pin = newPin
        sendJson(out, 200, """{"ok":true}""")
    }

    // ---------- v1.6: cài đặt từ điện thoại (không cần PIN — điện thoại nằm trong tay bố) ----------

    /** "HH:MM" (từ input type=time) hoặc số phút thuần -> phút từ 0h, null nếu sai. */
    private fun parseTimeParam(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        if (t.contains(":")) {
            val p = t.split(":")
            if (p.size != 2) return null
            val h = p[0].toIntOrNull() ?: return null
            val m = p[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }
        val v = t.toIntOrNull() ?: return null
        return if (v in 0..1439) v else null
    }

    private fun launcherApps(): List<Pair<String, String>> {
        return try {
            val pm = ctx.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }.sortedBy { it.second }
        } catch (_: Exception) { emptyList() }
    }

    private fun settingsNews(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val start = parseTimeParam(params["start"])
        val end = parseTimeParam(params["end"])
        if (start == null || end == null) {
            sendJson(out, 400, """{"ok":false,"error":"Giờ thời sự không hợp lệ."}""")
            return
        }
        store.newsStartMin = start
        store.newsEndMin = end
        store.newsPkg = params["newsPkg"]?.trim() ?: ""
        sendJson(out, 200, """{"ok":true}""")
    }

    private fun settingsLimits(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val allowed = setOf(5, 10, 15, 30, 60, 90, 120, 180, 240, -1)
        for (d in 0..6) {
            val v = params["limit$d"]?.toIntOrNull() ?: continue
            if (v in allowed) store.setLimitForDay(d, v)
        }
        sendJson(out, 200, """{"ok":true}""")
    }

    private fun settingsSchedule(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val days = (0..6).filter { params["d$it"] == "1" }
        val start = parseTimeParam(params["start"])
        val end = parseTimeParam(params["end"])
        if (days.isEmpty()) {
            sendJson(out, 400, """{"ok":false,"error":"Chọn ít nhất một ngày trong tuần."}""")
            return
        }
        if (start == null || end == null) {
            sendJson(out, 400, """{"ok":false,"error":"Giờ khóa không hợp lệ."}""")
            return
        }
        val id = params["id"]?.trim().orEmpty().ifEmpty { store.newScheduleId() }
        store.upsertLockSchedule(SchedulePolicy.Schedule(id, days, start, end))
        sendJson(out, 200, """{"ok":true}""")
    }

    private fun settingsScheduleDelete(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val id = params["id"]?.trim() ?: ""
        if (id.isEmpty()) {
            sendJson(out, 400, """{"ok":false,"error":"Không rõ lịch nào cần xóa."}""")
            return
        }
        store.deleteLockSchedule(id)
        sendJson(out, 200, """{"ok":true}""")
    }

    // ---------- v1.8: màn hình khóa — lời nhắc + ảnh nền (tải từ điện thoại) ----------

    private fun settingsLockMsg(out: OutputStream, params: Map<String, String>) {
        val msg = params["message"]?.trim() ?: ""
        if (msg.isEmpty() || msg.length > 140) {
            sendJson(out, 400, """{"ok":false,"error":"Lời nhắc từ 1–140 ký tự."}""")
            return
        }
        Store(ctx).lockMessage = msg
        sendJson(out, 200, """{"ok":true}""")
    }

    /** Ảnh gửi lên dạng base64 (data URL) trong field "img" — đọc dạng text
     * nên không cần parse multipart nhị phân. Nhận JPEG/PNG/HEIC/WEBP… ≤ 5MB,
     * decode rồi chuẩn hóa về JPEG trước khi lưu. */
    private fun settingsLockBg(out: OutputStream, params: Map<String, String>) {
        val img = params["img"] ?: ""
        val b64 = if (img.contains(",")) img.substringAfter(",") else img
        if (b64.length < 100 || b64.length > 7_000_000) {
            sendJson(out, 400, """{"ok":false,"error":"Ảnh không hợp lệ hoặc quá lớn (tối đa ~5MB)."}""")
            return
        }
        val bytes = try { java.util.Base64.getDecoder().decode(b64) } catch (_: Exception) {
            sendJson(out, 400, """{"ok":false,"error":"Ảnh không đọc được."}""")
            return
        }
        // Ảnh trong máy anh toàn HEIC — decode rồi chuẩn hóa về JPEG
        // (TV có bộ giải mã HEIC, CleanTV chứng minh được — lỗi decode là do code).
        val (jpg, imgErr) = normalizeUploadImage(bytes)
        if (jpg == null) {
            sendJson(out, 400, """{"ok":false,"error":"Không xử lý được ảnh này (${jsonEsc(imgErr ?: "không rõ")}), thử ảnh khác nhé."}""")
            return
        }
        if (!Store(ctx).saveLockBgBytes(jpg)) {
            sendJson(out, 400, """{"ok":false,"error":"Không lưu được ảnh."}""")
            return
        }
        sendJson(out, 200, """{"ok":true}""")
    }

    /** Decode ảnh tải từ điện thoại (JPEG/PNG/HEIC/WEBP...) rồi chuẩn hóa
     *  về JPEG, cạnh dài nhất ≤1920px cho nhẹ RAM TV. Trả về cặp
     *  (bytes JPEG, null) khi thành công, (null, lý do) khi thất bại —
     *  lý do để hiện lên web cho dễ bắt bệnh. Thử ImageDecoder trước
     *  (API 28+), hỏng thì thử BitmapFactory — đường nào được thì lấy. */
    private fun normalizeUploadImage(bytes: ByteArray): Pair<ByteArray?, String?> {
        val brand = detectBrand(bytes)
        fun fail(why: String): Pair<ByteArray?, String?> =
            null to "$brand, ${bytes.size / 1024}KB: $why"
        try {
            var lastErr = ""
            var bmp: android.graphics.Bitmap? = null
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                bmp = try {
                    val src = android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
                    android.graphics.ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                        val m = maxOf(info.size.width, info.size.height)
                        if (m > 1920) {
                            val r = 1920.0 / m
                            dec.setTargetSize(
                                (info.size.width * r).toInt().coerceAtLeast(1),
                                (info.size.height * r).toInt().coerceAtLeast(1)
                            )
                        }
                    }
                } catch (e: Exception) {
                    lastErr = "ImageDecoder: ${e.javaClass.simpleName}"
                    null
                }
            }
            if (bmp == null) {
                bmp = try {
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    lastErr += (if (lastErr.isNotEmpty()) "; " else "") +
                        "BitmapFactory: ${e.javaClass.simpleName}"
                    null
                }
            }
            if (bmp == null) return fail("không decode được ($lastErr)")
            // Ảnh quá to thì thu nhỏ trước khi nén cho nhẹ RAM.
            val m = maxOf(bmp.width, bmp.height)
            val small = if (m > 1920) {
                val r = 1920.0 / m
                android.graphics.Bitmap.createScaledBitmap(
                    bmp, (bmp.width * r).toInt().coerceAtLeast(1),
                    (bmp.height * r).toInt().coerceAtLeast(1), true
                ).also { bmp.recycle() }
            } else bmp
            val out = java.io.ByteArrayOutputStream()
            small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            small.recycle()
            return out.toByteArray() to null
        } catch (e: Exception) {
            return fail("${e.javaClass.simpleName}: ${e.message ?: ""}")
        }
    }

    /** Đoán định dạng file từ magic bytes để ghi vào lỗi cho dễ bắt bệnh:
     *  HEIC thường là "ftypheic/heix/mif1", JPEG là "FFD8", PNG là "89504E47". */
    private fun detectBrand(bytes: ByteArray): String {
        return try {
            if (bytes.size > 11 && bytes[4] == 'f'.code.toByte() &&
                bytes[5] == 't'.code.toByte() && bytes[6] == 'y'.code.toByte() &&
                bytes[7] == 'p'.code.toByte()
            ) {
                "ftyp/" + String(bytes, 8, 4, Charsets.US_ASCII).filter { it.isLetterOrDigit() }
            } else if (bytes.size > 2) {
                "magic=" + bytes.take(4).joinToString("") { "%02X".format(it) }
            } else "rỗng"
        } catch (_: Exception) { "?" }
    }

    private fun settingsLockBgRemove(out: OutputStream, params: Map<String, String>) {
        Store(ctx).deleteLockBg()
        sendJson(out, 200, """{"ok":true}""")
    }

    // ---------- v4.0: ảnh nền màn hình chính (slideshow) qua web ----------

    /** Ảnh gửi lên dạng base64 (data URL) trong field "img" — như ảnh nền
     *  màn hình khóa. Lưu vào thư mục slideshow của màn hình chính. */
    private fun settingsHomeBg(out: OutputStream, params: Map<String, String>) {
        val img = params["img"] ?: ""
        val b64 = if (img.contains(",")) img.substringAfter(",") else img
        if (b64.length < 100 || b64.length > 7_000_000) {
            sendJson(out, 400, """{"ok":false,"error":"Ảnh không hợp lệ hoặc quá lớn (tối đa ~5MB)."}""")
            return
        }
        val bytes = try { java.util.Base64.getDecoder().decode(b64) } catch (_: Exception) {
            sendJson(out, 400, """{"ok":false,"error":"Ảnh không đọc được."}""")
            return
        }
        // Ảnh trong máy anh toàn HEIC — decode rồi chuẩn hóa về JPEG.
        val (jpg, imgErr) = normalizeUploadImage(bytes)
        if (jpg == null) {
            sendJson(out, 400, """{"ok":false,"error":"Không xử lý được ảnh này (${jsonEsc(imgErr ?: "không rõ")}), thử ảnh khác nhé."}""")
            return
        }
        val name = Store(ctx).saveWallpaperBytes(jpg)
        if (name == null) sendJson(out, 400, """{"ok":false,"error":"Không lưu được ảnh."}""")
        else sendJson(out, 200, """{"ok":true}""")
    }

    /** GET /api/homebg-list — danh sách ảnh + thời gian đổi + nguồn (JSON). */
    private fun homeBgList(out: OutputStream) {
        val store = Store(ctx)
        val items = store.wallpaperFiles().joinToString(",") {
            """{"name":"${jsonEsc(it.name)}","size":${it.length()}}"""
        }
        val defs = defaultWallpaperNames().joinToString(",") { """"${jsonEsc(it)}"""" }
        sendJson(out, 200, """{"wallpapers":[$items],"interval":${store.wallpaperIntervalMin},"source":"${store.wallpaperSource}","defaults":[$defs]}""")
    }

    /** Ảnh nền mặc định kèm app (assets/wallpapers) — 10 ảnh phong cảnh. */
    private fun defaultWallpaperNames(): List<String> = try {
        ctx.assets.list("wallpapers")?.filter { it.endsWith(".jpg") }?.sorted() ?: emptyList()
    } catch (_: Exception) { emptyList() }

    /** GET /api/homebg-default-thumb?name= — xem trước ảnh mặc định kèm app
     *  (chỉ cho phép tên trong danh sách cố định, chặn path traversal). */
    private fun homeBgDefaultThumb(out: OutputStream, params: Map<String, String>) {
        val name = params["name"] ?: ""
        if (name !in defaultWallpaperNames()) {
            sendJson(out, 404, """{"ok":false,"error":"not_found"}""")
            return
        }
        val bytes = try {
            ctx.assets.open("wallpapers/$name").use { it.readBytes() }
        } catch (_: Exception) {
            sendJson(out, 500, """{"ok":false,"error":"Không đọc được ảnh."}""")
            return
        }
        val head = "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    /** POST /api/settings-wpsource {source: default|custom} — chọn nguồn
     *  ảnh nền màn hình chính: ảnh mặc định kèm app hay ảnh anh tải lên. */
    private fun settingsWpSource(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        store.wallpaperSource = params["source"]?.trim() ?: "default"
        sendJson(out, 200, """{"ok":true,"source":"${store.wallpaperSource}"}""")
    }

    /** GET /api/homebg-thumb?name= — xem trước một ảnh (chặn path traversal). */
    private fun homeBgThumb(out: OutputStream, params: Map<String, String>) {
        val name = params["name"] ?: ""
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            sendJson(out, 400, """{"ok":false,"error":"Tên file không hợp lệ."}""")
            return
        }
        val f = java.io.File(Store(ctx).wallpaperDir(), name)
        if (!f.exists() || !f.isFile) {
            sendJson(out, 404, """{"ok":false,"error":"Không tìm thấy ảnh."}""")
            return
        }
        val bytes = try { f.readBytes() } catch (_: Exception) {
            sendJson(out, 500, """{"ok":false,"error":"Không đọc được ảnh."}""")
            return
        }
        val head = "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    /** POST /api/settings-homebg-delete {name} — xóa một ảnh. */
    private fun settingsHomeBgDelete(out: OutputStream, params: Map<String, String>) {
        val name = params["name"] ?: ""
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            sendJson(out, 400, """{"ok":false,"error":"Tên file không hợp lệ."}""")
            return
        }
        try { java.io.File(Store(ctx).wallpaperDir(), name).delete() } catch (_: Exception) { }
        sendJson(out, 200, """{"ok":true}""")
    }

    /** POST /api/settings-homebg-clear — xóa hết ảnh nền màn hình chính. */
    private fun settingsHomeBgClear(out: OutputStream) {
        Store(ctx).wallpaperFiles().forEach { try { it.delete() } catch (_: Exception) { } }
        sendJson(out, 200, """{"ok":true}""")
    }

    /** POST /api/settings-wpinterval {minutes} — thời gian tự đổi ảnh. */
    private fun settingsWpInterval(out: OutputStream, params: Map<String, String>) {
        val m = params["minutes"]?.toIntOrNull() ?: 30
        Store(ctx).wallpaperIntervalMin = when (m) { 0, 15, 30, 60 -> m else -> 30 }
        sendJson(out, 200, """{"ok":true}""")
    }

    /** GET /api/widgets — trạng thái bật/tắt 5 widget màn hình chính. */
    private fun widgetsJson(out: OutputStream) {
        val s = Store(ctx)
        sendJson(out, 200, """{"tinker":${s.widgetTinker},"fare":${s.widgetFare},""" +
            """"countdown":${s.widgetCountdown},"family":${s.widgetFamily},"camera":${s.widgetCamera}}""")
    }

    /** POST /api/settings-widgets {tinker,fare,countdown,family,camera: true|false}
     *  — bật/tắt widget màn hình chính từ web (đồng bộ với Cài đặt trên TV). */
    private fun settingsWidgets(out: OutputStream, params: Map<String, String>) {
        val s = Store(ctx)
        fun b(k: String, cur: Boolean) = params[k]?.let { it == "true" || it == "1" } ?: cur
        s.widgetTinker = b("tinker", s.widgetTinker)
        s.widgetFare = b("fare", s.widgetFare)
        s.widgetCountdown = b("countdown", s.widgetCountdown)
        s.widgetFamily = b("family", s.widgetFamily)
        s.widgetCamera = b("camera", s.widgetCamera)
        sendJson(out, 200, """{"ok":true}""")
    }

    /** GET /api/launcher — toàn bộ cài đặt launcher (đồng bộ web/TV). */
    private fun launcherJson(out: OutputStream) {
        val s = Store(ctx)
        sendJson(out, 200, """{"iconStyle":"${s.iconStyle}","weatherCity":"${s.weatherCity}",""" +
            """"cdLabel":"${jsonEsc(s.countdownLabel)}","cdDate":"${s.countdownDate}",""" +
            """"pushStart":${s.pushStartHour},"pushEnd":${s.pushEndHour},"popupMode":"${s.popupMode}"}""")
    }

    /** POST /api/settings-countdown {label, date: yyyy-MM-dd}. */
    private fun settingsCountdown(out: OutputStream, params: Map<String, String>) {
        val s = Store(ctx)
        params["label"]?.trim()?.takeIf { it.isNotEmpty() }?.let { s.countdownLabel = it.take(24) }
        params["date"]?.trim()?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }?.let { s.countdownDate = it }
        sendJson(out, 200, """{"ok":true}""")
    }

    /** POST /api/settings-push {start: 0-23, end: 0-23, mode: always|home|silent}. */
    private fun settingsPush(out: OutputStream, params: Map<String, String>) {
        val s = Store(ctx)
        params["start"]?.toIntOrNull()?.let { if (it in 0..23) s.pushStartHour = it }
        params["end"]?.toIntOrNull()?.let { if (it in 0..23) s.pushEndHour = it }
        params["mode"]?.trim()?.takeIf { it in setOf("always", "home", "silent") }?.let { s.popupMode = it }
        sendJson(out, 200, """{"ok":true}""")
    }

    /** POST /api/settings-iconstyle {style: default|circle}. */
    private fun settingsIconStyle(out: OutputStream, params: Map<String, String>) {
        val st = params["style"]?.trim() ?: ""
        if (st in setOf("default", "rounded", "circle")) Store(ctx).iconStyle =
            if (st == "circle") "rounded" else st
        sendJson(out, 200, """{"ok":true}""")
    }

    /** POST /api/settings-weathercity {city: hanoi|danang|hcmc}. */
    private fun settingsWeatherCity(out: OutputStream, params: Map<String, String>) {
        val c = params["city"]?.trim() ?: ""
        if (c in setOf("hanoi", "danang", "hcmc")) Store(ctx).weatherCity = c
        sendJson(out, 200, """{"ok":true}""")
    }

    /** GET /api/hidden-apps — app đã ẩn trên màn hình chính. */
    private fun hiddenAppsJson(out: OutputStream) {
        val store = Store(ctx)
        val hidden = store.homeHidden()
        val items = launcherApps().joinToString(",") { (pkg, label) ->
            """{"pkg":"${jsonEsc(pkg)}","label":"${jsonEsc(label)}","hidden":${hidden.contains(pkg)}}"""
        }
        sendJson(out, 200, """{"apps":[$items]}""")
    }

    /** POST /api/settings-hiddenapp {pkg, hidden: 1|0}. */
    private fun settingsHiddenApp(out: OutputStream, params: Map<String, String>) {
        val pkg = params["pkg"]?.trim() ?: ""
        if (pkg.isNotEmpty()) {
            Store(ctx).setHomeHidden(pkg, params["hidden"] == "1" || params["hidden"] == "true")
        }
        sendJson(out, 200, """{"ok":true}""")
    }


    /** v2.0: bật/tắt chống tắt Trợ năng từ điện thoại. Tắt xong thì anh
     *  mới tắt được Trợ năng trong Cài đặt TV như bình thường; bật lại
     *  thì app tự bật lại Trợ năng ngay nếu đang tắt. */
    // v2.4: POST /api/device-owner-remove — gỡ quyền Device Owner của chính
    // app. Các route /api/ đều bắt buộc phiên đăng nhập mã PIN web, nên chỉ
    // anh (người có mã PIN web) mới gọi được. Gọi xong thì gỡ app như thường.
    private fun deviceOwnerRemove(out: OutputStream) {
        val ok = DeviceOwnerPolicy.remove(ctx)
        sendJson(out, 200, """{"ok":$ok,"deviceOwner":${DeviceOwnerPolicy.isDeviceOwner(ctx)}}""")
    }

    private fun settingsAntiDisable(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        store.antiDisable = params["enabled"] == "on"
        if (store.antiDisable) store.ensureAccessEnabled()
        sendJson(out, 200, """{"ok":true,"antiDisable":${store.antiDisable}}""")
    }

    // ---------- Giai đoạn 5: web tĩnh mới + endpoint JSON cho app.js ----------

    /** Phục vụ file tĩnh từ assets/web/ (index.html, app.css, app.js). */
    private fun serveStatic(out: OutputStream, assetPath: String, contentType: String) {
        val bytes = try {
            ctx.assets.open(assetPath).use { it.readBytes() }
        } catch (_: Exception) {
            sendJson(out, 404, """{"ok":false,"error":"not_found"}""")
            return
        }
        // no-store: web điều khiển trên điện thoại luôn lấy bản mới nhất,
        // tránh kẹt cache cũ gây lệch ID với app.js (đã từng gây lỗi null).
        sendBytes(out, 200, contentType, bytes, noCache = true)
    }

    private fun sendBytes(out: OutputStream, code: Int, contentType: String, bytes: ByteArray, noCache: Boolean = false) {
        val cacheHdr = if (noCache) "Cache-Control: no-store\r\n" else ""
        val head = "HTTP/1.1 $code ${reasonFor(code)}\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\n${cacheHdr}Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    // ---------- v2.3: phiên đăng nhập web (mã PIN web) ----------

    /** Token phiên từ cookie tvlock_session, còn hạn thì hợp lệ. */
    private fun isAuthed(cookieHeader: String): Boolean {
        val token = parseCookie(cookieHeader, SESSION_COOKIE) ?: return false
        val now = System.currentTimeMillis()
        synchronized(sessions) {
            val exp = sessions[token] ?: return false
            if (exp <= now) {
                sessions.remove(token)
                return false
            }
            return true
        }
    }

    private fun parseCookie(header: String, name: String): String? {
        for (part in header.split(";")) {
            val p = part.trim()
            if (p.startsWith("$name=")) return p.substring(name.length + 1).trim()
        }
        return null
    }

    private fun newSession(): String {
        val token = java.util.UUID.randomUUID().toString()
        synchronized(sessions) {
            // Dọn phiên đã hết hạn cho gọn.
            val now = System.currentTimeMillis()
            sessions.entries.removeIf { it.value <= now }
            sessions[token] = now + SESSION_MS
        }
        return token
    }

    /** POST /api/webpin-login {pin} — đúng mã thì tạo phiên 30 ngày
     *  (cookie), sai 5 lần liên tiếp thì khóa 5 phút. (2026-11-09) Dùng
     *  PinGuard chung với PIN khóa TV và mã thưởng; khóa đọc/ghi bằng
     *  synchronized vì nhiều luồng handler cùng chạm vào. */
    private fun webpinLogin(out: OutputStream, params: Map<String, String>) {
        val now = System.currentTimeMillis()
        val st = synchronized(this) { PinGuard.State(failedLogins, lockUntilMs) }
        if (PinGuard.isLocked(st, now)) {
            sendJson(out, 429, """{"ok":false,"error":"Sai quá 5 lần, thử lại sau 5 phút."}""")
            return
        }
        val pin = params["pin"]?.trim() ?: ""
        if (pin.isNotEmpty() && pin == Store(ctx).webPinOrDefault()) {
            synchronized(this) {
                failedLogins = 0
                lockUntilMs = 0L
            }
            val token = newSession()
            sendJsonWithHeaders(out, 200, """{"ok":true}""",
                listOf("Set-Cookie: $SESSION_COOKIE=$token; Path=/; Max-Age=${SESSION_MS / 1000}; HttpOnly; SameSite=Lax"))
            return
        }
        val next = synchronized(this) {
            val s = PinGuard.recordFailure(PinGuard.State(failedLogins, lockUntilMs), now)
            failedLogins = s.fails
            lockUntilMs = s.lockedUntilMs
            s
        }
        if (PinGuard.isLocked(next, now)) {
            sendJson(out, 429, """{"ok":false,"error":"Sai quá 5 lần, thử lại sau 5 phút."}""")
            return
        }
        sendJson(out, 401, """{"ok":false,"error":"Sai mã PIN"}""")
    }

    /** POST /api/webpin-change {newPin} — cần phiên hợp lệ (đã qua cổng
     *  kiểm tra ở handle). Mã mới 4–6 chữ số. */
    private fun webpinChange(out: OutputStream, params: Map<String, String>) {
        val newPin = params["newPin"]?.trim() ?: ""
        if (!newPin.matches(Regex("^\\d{4,6}$"))) {
            sendJson(out, 200, """{"ok":false,"error":"Mã PIN web mới phải là 4–6 chữ số."}""")
            return
        }
        Store(ctx).webPin = newPin
        sendJson(out, 200, """{"ok":true}""")
    }

    /** POST /api/webpin-logout — xóa phiên (kể cả khi phiên đã hết hạn). */
    private fun webpinLogout(out: OutputStream, params: Map<String, String>, cookieHeader: String) {
        val token = parseCookie(cookieHeader, SESSION_COOKIE)
        if (token != null) {
            synchronized(sessions) { sessions.remove(token) }
        }
        sendJsonWithHeaders(out, 200, """{"ok":true}""",
            listOf("Set-Cookie: $SESSION_COOKIE=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"))
    }

    /** v2.9: GET /api/screenshot — chụp 1 khung hình TV mới nhất, trả
     *  JPEG. Cần phiên mã PIN web (cổng kiểm tra ở handle). Chưa cấp
     *  quyền (hoặc quyền đã mất sau khi khởi động lại TV) thì 503 kèm
     *  hướng dẫn cấp quyền lại trên TV. */
    private fun screenshot(out: OutputStream) {
        val bytes = try { ScreenCaptureService.capture() } catch (_: Exception) { null }
        if (bytes != null && bytes.isNotEmpty()) {
            sendBytes(out, 200, "image/jpeg", bytes)
            return
        }
        sendJson(out, 503, """{"error":"no_permission","hint":"Mở app Khóa TV trên TV → Cài đặt → Chụp màn hình → Cấp quyền chụp màn hình (cần cấp lại sau mỗi lần khởi động lại TV)"}""")
    }

    /** GET /api/schedules — toàn bộ lịch khóa (tab Lịch trên web mới). */
    private fun schedules(out: OutputStream) {
        val store = Store(ctx)
        val items = store.lockSchedules().joinToString(",") { s ->
            val days = s.days.joinToString(",")
            """{"id":"${jsonEsc(s.id)}","days":[$days],"startMin":${s.startMin},"endMin":${s.endMin},"summary":"${jsonEsc(store.scheduleSummary(s))}"}"""
        }
        sendJson(out, 200, """{"schedules":[$items]}""")
    }

    /** GET /api/limits — giới hạn xem từng ngày 0..6 (tab Giới hạn). */
    private fun limits(out: OutputStream) {
        val store = Store(ctx)
        val items = (0..6).joinToString(",") { "\"$it\":${store.limitForDay(it)}" }
        sendJson(out, 200, """{"limits":{$items}}""")
    }

    /** GET /api/settings — giờ thời sự, lời nhắc, trạng thái chống tắt... */
    private fun settingsJson(out: OutputStream) {
        val store = Store(ctx)
        val newsLabel = if (store.newsPkg.isEmpty()) "" else store.appLabel(store.newsPkg)
        val json = """{"newsStartMin":${store.newsStartMin},"newsEndMin":${store.newsEndMin},"newsPkg":"${jsonEsc(store.newsPkg)}","newsLabel":"${jsonEsc(newsLabel)}","lockMessage":"${jsonEsc(store.lockMessage)}","hasLockBg":${store.hasLockBg()},"antiDisable":${store.antiDisable},"a11yEnabled":${store.isAccessEnabled()},"hasWss":${store.hasWriteSecureSettings()}}"""
        sendJson(out, 200, json)
    }

    /** GET /api/newsapps — các app có launcher (để chọn app thời sự). */
    private fun newsApps(out: OutputStream) {
        val items = launcherApps().joinToString(",") { (pkg, label) ->
            """{"pkg":"${jsonEsc(pkg)}","label":"${jsonEsc(label)}"}"""
        }
        sendJson(out, 200, """{"apps":[$items]}""")
    }

    /** GET /api/apprules — app + quy tắc riêng + phút đã xem hôm nay. */
    private fun appRulesJson(out: OutputStream) {
        val store = Store(ctx)
        val perApp = store.perAppToday()
        val items = launcherApps().joinToString(",") { (pkg, label) ->
            val r = store.appRule(pkg)
            """{"pkg":"${jsonEsc(pkg)}","label":"${jsonEsc(label)}","mode":"${r.mode}","minutes":${r.minutes},"todayMin":${perApp[pkg] ?: 0}}"""
        }
        sendJson(out, 200, """{"apps":[$items]}""")
    }

    /** POST /api/settings-apprule (pkg, mode, minutes) — trả JSON, không HTML.
     *  mode=normal thì minutes=0; limit thì minutes ∈ {15,30,60};
     *  free/blocked thì minutes=0. */
    private fun settingsAppRule(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val pkg = params["pkg"]?.trim() ?: ""
        val mode = params["mode"]?.trim() ?: ""
        val minutes = params["minutes"]?.toIntOrNull() ?: 0
        if (pkg.isEmpty() || mode !in setOf("normal", "limit", "free", "blocked")) {
            sendJson(out, 200, """{"ok":false,"error":"Quy tắc không hợp lệ."}""")
            return
        }
        val min = when (mode) {
            "limit" -> if (minutes in setOf(15, 30, 60)) minutes else 30
            else -> 0
        }
        store.setAppRule(pkg, mode, min)
        sendJson(out, 200, """{"ok":true}""")
    }

    // ---------- v4.3: gán nút remote ----------
    private fun keyMapJson(out: OutputStream) {
        val store = Store(ctx)
        val arr = org.json.JSONArray()
        for ((code, o) in store.keyMap()) {
            arr.put(org.json.JSONObject()
                .put("keycode", code)
                .put("action", o.optString("action", ""))
                .put("label", o.optString("label", "")))
        }
        sendJson(out, 200, org.json.JSONObject().put("ok", true).put("items", arr).toString())
    }

    private fun keyMapSave(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val code = params["keycode"]?.toIntOrNull()
        val action = params["action"]?.trim() ?: ""
        val label = params["label"]?.trim() ?: ""
        if (code == null || code <= 0 || action.isEmpty()) {
            sendJson(out, 200, """{"ok":false,"error":"Thiếu keycode hoặc hành động."}""")
            return
        }
        // Không cho gán phím hệ thống (HOME/BACK/DPAD) — tránh kẹt TV.
        if (code in KeyRemap.SYSTEM_KEYS) {
            sendJson(out, 200, """{"ok":false,"error":"Không gán được phím điều hướng hệ thống."}""")
            return
        }
        // Chỉ cho phép: app:<pkg>, lock, msg:<text>
        val valid = action == "lock" || action.startsWith("app:") || action.startsWith("msg:")
        if (!valid) {
            sendJson(out, 200, """{"ok":false,"error":"Hành động không hợp lệ."}""")
            return
        }
        store.setKeyMapEntry(code, action, label.ifEmpty { "Nút $code" })
        sendJson(out, 200, """{"ok":true}""")
    }

    private fun keyMapDelete(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val code = params["keycode"]?.toIntOrNull()
        if (code == null) {
            sendJson(out, 200, """{"ok":false,"error":"Thiếu keycode."}""")
            return
        }
        store.removeKeyMapEntry(code)
        sendJson(out, 200, """{"ok":true}""")
    }

    private fun keyMapLearn(out: OutputStream) {
        KeyRemap.startLearn(15)
        sendJson(out, 200, """{"ok":true,"timeout":15}""")
    }

    private fun keyMapLearnPoll(out: OutputStream) {
        val code = KeyRemap.pollLearn()
        val o = org.json.JSONObject().put("ok", true)
        if (code > 0) o.put("keycode", code) else o.put("keycode", org.json.JSONObject.NULL)
        sendJson(out, 200, o.toString())
    }




    private fun wifiIp(): String {
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"
        } catch (_: Exception) { "" }
    }


    /** Escape a string for use inside a JSON string value. (2026-11-09)
     *  The old esc() was HTML-escaping (& -> &amp;) which is WRONG inside
     *  JSON — the web UI received literal "&amp;" text. */
    private fun jsonEsc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch.code < 0x20) sb.append(String.format("\\u%04x", ch.code))
                else sb.append(ch)
            }
        }
        return sb.toString()
    }


    private fun reasonFor(code: Int): String = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        413 -> "Content Too Large"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        503 -> "Service Unavailable"
        else -> "OK"
    }

    private fun sendJson(out: OutputStream, code: Int, body: String) =
        sendJsonWithHeaders(out, code, body, emptyList())

    /** Gửi JSON, kèm thêm header (dùng cho Set-Cookie khi đăng nhập/xuất). */
    private fun sendJsonWithHeaders(out: OutputStream, code: Int, body: String, extraHeaders: List<String>) {
        val b = body.toByteArray(Charsets.UTF_8)
        val extra = if (extraHeaders.isEmpty()) "" else extraHeaders.joinToString("") { "$it\r\n" }
        val head = "HTTP/1.1 $code ${reasonFor(code)}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${b.size}\r\n${extra}Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8)); out.write(b); out.flush()
    }
}
