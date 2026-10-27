package vn.tvlock

import android.content.Context
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

    fun start() {
        server = ServerSocket(port)
        running = true
        acceptThread = Thread({
            while (running) {
                try {
                    val s = server?.accept() ?: break
                    Thread({ handle(s) }, "tvlock-http").also { it.isDaemon = true; it.start() }
                } catch (_: Exception) { if (!running) break }
            }
        }, "tvlock-accept").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) { }
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
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0 && line.substring(0, idx).trim().equals("Content-Length", true)) {
                    contentLength = line.substring(idx + 1).trim().toIntOrNull() ?: 0
                }
            }
            var body = ""
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
            val out = sock.getOutputStream()
            when {
                method == "GET" && path == "/" -> sendHtml(out, 200, homePage(""))
                method == "GET" && path == "/settings" -> sendHtml(out, 200, settingsPage(""))
                method == "GET" && path == "/api/status" -> status(out, params)
                method == "GET" && path == "/api/report" -> report(out, params)
                method == "POST" && path == "/api/lock" -> action(out, params, "lock")
                method == "POST" && path == "/api/unlock" -> action(out, params, "unlock")
                method == "POST" && path == "/api/add" -> action(out, params, "add")
                method == "POST" && path == "/api/bonus" -> bonus(out, params)
                method == "POST" && path == "/api/change-pin" -> changePin(out, params)
                method == "POST" && path == "/api/settings-news" -> settingsNews(out, params)
                method == "POST" && path == "/api/settings-limits" -> settingsLimits(out, params)
                method == "POST" && path == "/api/settings-schedule" -> settingsSchedule(out, params)
                method == "POST" && path == "/api/settings-schedule-delete" -> settingsScheduleDelete(out, params)
                method == "POST" && path == "/api/settings-lockmsg" -> settingsLockMsg(out, params)
                method == "POST" && path == "/api/settings-lockbg" -> settingsLockBg(out, params)
                method == "POST" && path == "/api/settings-lockbg-remove" -> settingsLockBgRemove(out, params)
                else -> sendHtml(out, 404, "<h1>Không tìm thấy</h1><a href='/'>Về trang chính</a>")
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
        // No PIN required: the phone is in the parent's hand (user's choice).
        // The TV lock screen itself still requires the PIN.
        val store = Store(ctx)
        store.resetUsedIfNewDay()
        val now = System.currentTimeMillis()
        val locked = store.isLockedNow(now)
        val rem = store.remainingMin()
        val remStr = if (rem == Int.MAX_VALUE) -1 else rem
        val news = store.isInNewsSlot(now)
        val perApp = store.perAppToday().entries.joinToString(",") { "\"${it.key}\":${it.value}" }
        val todaySched = store.todayScheduleSummaries(now).joinToString(",") { "\"${esc(it)}\"" }
        val json = """{"locked":$locked,"usedTodayMin":${store.usedTodayMin},"dailyLimitMin":${store.limitForToday()},"todayLimit":${store.limitForToday()},"todaySchedules":[$todaySched],"remainingMin":$remStr,"newsSlot":$news,"ip":"${wifiIp()}","manualLocked":${store.manualLocked},"bonusMin":${store.bonusMin},"perAppToday":{$perApp}}"""
        sendJson(out, 200, json)
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

    /** v3: grant bonus minutes from the phone (no PIN — the phone is in the parent's hand). */
    private fun bonus(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val min = params["minutes"]?.toIntOrNull() ?: 0
        if (min !in setOf(5, 10, 15, 30)) {
            sendHtml(out, 200, homePage("Số phút thưởng không hợp lệ.", true))
            return
        }
        store.addBonus(min)
        sendHtml(out, 200, homePage("Đã thưởng thêm $min phút. Quỹ thưởng còn ${store.bonusMin} phút."))
    }

    private fun action(out: OutputStream, params: Map<String, String>, kind: String) {
        // No PIN required on the phone: the phone is in the parent's hand
        // (user's choice). Any "pin" param sent by a client is ignored.
        // The TV lock screen itself still requires the PIN.
        val store = Store(ctx)
        var msg = ""
        var error = false
        when (kind) {
            "lock" -> { store.manualLocked = true; store.unlockUntilMs = 0L; msg = "Đã khóa TV ngay." }
            "unlock" -> { store.manualLocked = false; store.unlockUntilMs = System.currentTimeMillis() + 60 * 60000L; msg = "Đã mở khóa TV trong 60 phút." }
            "add" -> {
                val min = params["minutes"]?.toIntOrNull() ?: 0
                if (min !in setOf(5, 10, 15, 20, 30)) { msg = "Số phút không hợp lệ."; error = true }
                else { store.addMinutes(min); msg = "Đã cho thêm $min phút." }
            }
            else -> { msg = "Không rõ lệnh."; error = true }
        }
        sendHtml(out, 200, homePage(msg, error))
    }

    /** Change the TV PIN from the phone — no old PIN required, so a forgotten
        PIN can be reset from the phone. New PIN must be 4–6 digits only. */
    private fun changePin(out: OutputStream, params: Map<String, String>) {
        val newPin = params["newPin"]?.trim() ?: ""
        if (!newPin.matches(Regex("^\\d{4,6}$"))) {
            sendHtml(out, 200, homePage("Mã PIN mới phải là 4–6 chữ số.", true))
            return
        }
        Store(ctx).pin = newPin
        sendHtml(out, 200, homePage("Đã đổi mã PIN thành công."))
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
            sendHtml(out, 200, settingsPage("Giờ thời sự không hợp lệ.", true))
            return
        }
        store.newsStartMin = start
        store.newsEndMin = end
        store.newsPkg = params["newsPkg"]?.trim() ?: ""
        sendHtml(out, 200, settingsPage("Đã lưu giờ thời sự ${store.fmt(start)} – ${store.fmt(end)}."))
    }

    private fun settingsLimits(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val allowed = setOf(5, 10, 15, 30, 60, 90, 120, 180, 240, -1)
        for (d in 0..6) {
            val v = params["limit$d"]?.toIntOrNull() ?: continue
            if (v in allowed) store.setLimitForDay(d, v)
        }
        sendHtml(out, 200, settingsPage("Đã lưu giới hạn xem từng ngày."))
    }

    private fun settingsSchedule(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val days = (0..6).filter { params["d$it"] == "1" }
        val start = parseTimeParam(params["start"])
        val end = parseTimeParam(params["end"])
        if (days.isEmpty()) {
            sendHtml(out, 200, settingsPage("Chọn ít nhất một ngày trong tuần.", true))
            return
        }
        if (start == null || end == null) {
            sendHtml(out, 200, settingsPage("Giờ khóa không hợp lệ.", true))
            return
        }
        val id = params["id"]?.trim().orEmpty().ifEmpty { store.newScheduleId() }
        store.upsertLockSchedule(Store.LockSchedule(id, days, start, end))
        sendHtml(out, 200, settingsPage("Đã lưu lịch khóa ${store.fmt(start)} – ${store.fmt(end)}."))
    }

    private fun settingsScheduleDelete(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        val id = params["id"]?.trim() ?: ""
        if (id.isEmpty()) {
            sendHtml(out, 200, settingsPage("Không rõ lịch nào cần xóa.", true))
            return
        }
        store.deleteLockSchedule(id)
        sendHtml(out, 200, settingsPage("Đã xóa lịch khóa."))
    }

    // ---------- v1.8: màn hình khóa — lời nhắc + ảnh nền (tải từ điện thoại) ----------

    private fun settingsLockMsg(out: OutputStream, params: Map<String, String>) {
        val msg = params["message"]?.trim() ?: ""
        if (msg.isEmpty() || msg.length > 140) {
            sendHtml(out, 200, settingsPage("Lời nhắc từ 1–140 ký tự.", true))
            return
        }
        Store(ctx).lockMessage = msg
        sendHtml(out, 200, settingsPage("Đã lưu lời nhắc màn hình khóa."))
    }

    /** Ảnh gửi lên dạng base64 (data URL) trong field "img" — đọc dạng text
     * nên không cần parse multipart nhị phân. Chỉ nhận JPEG/PNG ≤ 5MB. */
    private fun settingsLockBg(out: OutputStream, params: Map<String, String>) {
        val img = params["img"] ?: ""
        val b64 = if (img.contains(",")) img.substringAfter(",") else img
        if (b64.length < 100 || b64.length > 7_000_000) {
            sendHtml(out, 200, settingsPage("Ảnh không hợp lệ hoặc quá lớn (tối đa ~5MB).", true))
            return
        }
        val bytes = try { java.util.Base64.getDecoder().decode(b64) } catch (_: Exception) {
            sendHtml(out, 200, settingsPage("Ảnh không đọc được.", true))
            return
        }
        if (bytes.size < 100 || bytes.size > 5 * 1024 * 1024) {
            sendHtml(out, 200, settingsPage("Ảnh quá lớn (tối đa ~5MB).", true))
            return
        }
        val isJpeg = bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val isPng = bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        if (!isJpeg && !isPng) {
            sendHtml(out, 200, settingsPage("Chỉ nhận ảnh JPEG hoặc PNG.", true))
            return
        }
        if (!Store(ctx).saveLockBgBytes(bytes)) {
            sendHtml(out, 200, settingsPage("Không lưu được ảnh.", true))
            return
        }
        sendHtml(out, 200, settingsPage("Đã lưu ảnh nền màn hình khóa."))
    }

    private fun settingsLockBgRemove(out: OutputStream, params: Map<String, String>) {
        Store(ctx).deleteLockBg()
        sendHtml(out, 200, settingsPage("Đã xóa ảnh nền riêng — màn hình khóa dùng nền mặc định."))
    }

    private fun shortDay(d: Int): String = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")[d]

    private fun limitOptions(selected: Int): String {
        val opts = listOf(5 to "5 phút", 10 to "10 phút", 15 to "15 phút", 30 to "30 phút",
            60 to "60 phút", 90 to "90 phút", 120 to "120 phút", 180 to "180 phút",
            240 to "240 phút", -1 to "Không giới hạn")
        return opts.joinToString("") { (v, label) ->
            "<option value=\"$v\"${if (v == selected) " selected" else ""}>$label</option>"
        }
    }

    private fun scheduleForm(store: Store, s: Store.LockSchedule?, isNew: Boolean): String {
        val id = s?.id ?: ""
        val days = s?.days ?: listOf(0, 1, 2, 3, 4)
        val start = s?.startMin ?: 1260
        val end = s?.endMin ?: 420
        val dayBoxes = (0..6).joinToString(" ") { d ->
            "<label style='display:inline-block;margin:4px 8px 4px 0;font-size:20px'><input type='checkbox' name='d$d' value='1' style='width:22px;height:22px'${if (d in days) " checked" else ""}> ${shortDay(d)}</label>"
        }
        val title = if (isNew) "Thêm lịch khóa mới" else store.scheduleSummary(s!!)
        val delBtn = if (isNew) "" else
            "<form method='post' action='/api/settings-schedule-delete' style='margin-top:8px'>" +
            "<input type='hidden' name='id' value='${esc(id)}'>" +
            "<button class='red' type='submit'>Xóa lịch này</button></form>"
        return "<div class='card'><h2>$title</h2>" +
            "<form method='post' action='/api/settings-schedule'>" +
            "<input type='hidden' name='id' value='${esc(id)}'>" +
            "<p class='lbl' style='margin-top:0'>Ngày áp dụng</p><div>$dayBoxes</div>" +
            "<p class='lbl'>Bắt đầu khóa</p><input type='time' name='start' value='${store.fmt(start)}' required>" +
            "<p class='lbl'>Kết thúc khóa</p><input type='time' name='end' value='${store.fmt(end)}' required>" +
            "<p class='lbl' style='font-size:18px'>Kết thúc sớm hơn bắt đầu = khóa qua đêm. Bắt đầu = kết thúc thì khóa cả ngày — đặt 00:00 và 23:59 cũng được.</p>" +
            "<button class='green' type='submit'>Lưu lịch này</button></form>$delBtn</div>"
    }

    private fun settingsPage(msg: String, error: Boolean = false): String {
        val store = Store(ctx)
        val newsApps = launcherApps()
        val newsOpts = "<option value=''>— Chưa chọn app thời sự —</option>" + newsApps.joinToString("") { (pkg, label) ->
            "<option value='${esc(pkg)}'${if (pkg == store.newsPkg) " selected" else ""}>${esc(label)}</option>"
        }
        val lockBgState = if (store.hasLockBg()) "Đã đặt ảnh nền riêng." else "Chưa có ảnh nền riêng — đang dùng nền mặc định."
        val lockCard = "<div class='card'><h2>Màn hình khóa</h2>" +
            "<p class='lbl' style='margin-top:0'>Lời nhắc hiện cùng ảnh nền bên trái màn hình khóa</p>" +
            "<form method='post' action='/api/settings-lockmsg'>" +
            "<input type='text' name='message' value='${esc(store.lockMessage)}' maxlength='140'>" +
            "<button class='green' type='submit'>Lưu lời nhắc</button></form>" +
            "<p class='lbl'>Ảnh nền màn hình khóa — $lockBgState</p>" +
            "<input type='file' id='bgFile' accept='image/*'>" +
            "<img id='bgPreview' style='max-width:100%;border-radius:12px;display:none;margin-bottom:12px'>" +
            "<button class='green' type='button' id='bgUploadBtn'>Tải ảnh này lên TV</button>" +
            "<p class='lbl' style='font-size:18px'>Ảnh trên điện thoại là tiện nhất — chọn ảnh rồi bấm tải lên. Ảnh JPEG/PNG tối đa ~5MB, tự thu nhỏ vừa màn hình TV.</p>" +
            "<form method='post' action='/api/settings-lockbg-remove'><button class='red' type='submit'>Xóa ảnh nền riêng</button></form></div>"
        val limitRows = (0..6).joinToString("") { d ->
            "<p class='lbl'>${store.dayNames()[d]}</p>" +
            "<select name='limit$d'>${limitOptions(store.limitForDay(d))}</select>"
        }
        val schedForms = store.lockSchedules().joinToString("") { scheduleForm(store, it, false) } +
            scheduleForm(store, null, true)
        val msgHtml = if (msg.isEmpty()) "" else
            if (error) "<p class='err'>$msg</p>" else "<p class='msg'>$msg</p>"
        return """<!DOCTYPE html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Cài đặt — Khóa TV</title><style>
body{font-family:sans-serif;background:#121212;color:#fff;margin:0;padding:16px}
h1{font-size:32px;margin:8px 0 16px}h2{font-size:24px;margin:0 0 12px}
.card{background:#1E1E1E;border:1px solid #333;border-radius:16px;padding:20px;margin-bottom:16px}
.lbl{font-size:20px;color:#B0B0B0;margin:14px 0 2px}.val{font-size:28px;font-weight:bold;margin:0}
button{font-size:22px;padding:18px 12px;border:0;border-radius:12px;color:#fff;width:100%}
button.red{background:#C62828}button.green{background:#2E7D32}button.amber{background:#FF8F00;color:#000}
input{font-size:22px;padding:16px;border-radius:12px;border:2px solid #444;background:#2A2A2A;color:#fff;width:100%;box-sizing:border-box;margin-bottom:12px}
input[type=time]{font-size:26px}
select{font-size:22px;padding:16px;border-radius:12px;border:2px solid #444;background:#2A2A2A;color:#fff;width:100%;box-sizing:border-box;margin-bottom:12px}
.msg{font-size:22px;color:#66BB6A}.err{font-size:22px;color:#EF5350}
a{color:#90CAF9;font-size:20px}
</style></head><body>
<h1>Cài đặt Khóa TV</h1>
$msgHtml
<div class="card"><h2>Giờ thời sự</h2>
<form method="post" action="/api/settings-news">
<p class="lbl" style="margin-top:0">Bắt đầu</p><input type="time" name="start" value="${store.fmt(store.newsStartMin)}" required>
<p class="lbl">Kết thúc</p><input type="time" name="end" value="${store.fmt(store.newsEndMin)}" required>
<p class="lbl">App thời sự (trong khung này TV chỉ mở được app này)</p>
<select name="newsPkg">$newsOpts</select>
<button class="green" type="submit">Lưu giờ thời sự</button></form></div>
$lockCard
<div class="card"><h2>Giới hạn xem từng ngày</h2>
<form method="post" action="/api/settings-limits">
$limitRows
<button class="green" type="submit">Lưu giới hạn ngày</button></form></div>
<div class="card"><h2>Lịch khóa</h2>
<p class="lbl" style="margin-top:0">Mỗi lịch chọn ngày + giờ khóa từ–đến, gộp được nhiều lịch, khóa qua đêm cũng được.</p></div>
$schedForms
<p><a href="/">← Về trang chính</a></p>
<script>
document.getElementById('bgFile').addEventListener('change', function(e){ var f=e.target.files[0]; if(!f) return; var r=new FileReader(); r.onload=function(){ var img=document.getElementById('bgPreview'); img.src=r.result; img.style.display='block'; }; r.readAsDataURL(f); });
document.getElementById('bgUploadBtn').addEventListener('click', function(){ var f=document.getElementById('bgFile').files[0]; if(!f){ alert('Chọn một ảnh trước đã.'); return; } var r=new FileReader(); r.onload=function(){ fetch('/api/settings-lockbg',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'img='+encodeURIComponent(r.result)}).then(function(resp){ return resp.text(); }).then(function(html){ document.open(); document.write(html); document.close(); }).catch(function(){ alert('Không tải được ảnh, thử lại nhé.'); }); }; r.readAsDataURL(f); });
</script>
</body></html>"""
    }

    private fun wifiIp(): String {
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"
        } catch (_: Exception) { "" }
    }

    private fun homePage(msg: String, error: Boolean = false): String {
        val store = Store(ctx)
        store.resetUsedIfNewDay()
        val now = System.currentTimeMillis()
        val locked = store.isLockedNow(now)
        val rem = store.remainingMin()
        val remTxt = if (rem == Int.MAX_VALUE) "không giới hạn" else "$rem phút"
        val status = if (locked) "ĐANG KHÓA" else "ĐANG MỞ"
        val pillClass = if (locked) "pill locked" else "pill open"
        val newsTxt = if (store.newsPkg.isEmpty()) "chưa chọn app" else store.newsPkg
        val limit = store.limitForToday()
        val limitTxt = if (limit <= 0) "không giới hạn" else "$limit phút"
        val todays = store.todayScheduleSummaries(now)
        val schedTxt = if (todays.isEmpty()) "Hôm nay không có lịch khóa" else todays.joinToString("<br>")
        val bonusTxt = if (store.bonusMin > 0) "<p class=\"lbl\">Phút thưởng còn</p><p class=\"val\">${store.bonusMin} phút</p>" else ""
        // v3: report — today per app + last 7 days as simple bars.
        val today = store.perAppToday()
        val todayRows = if (today.isEmpty()) "<p class='lbl'>Hôm nay chưa xem app nào.</p>"
        else today.entries.sortedByDescending { it.value }.joinToString("") {
            "<p class='lbl'>${esc(store.appLabel(it.key))}</p><p class='val' style='font-size:22px'>${it.value} phút</p>"
        }
        val days = store.historyDays().takeLast(7).reversed()
        val maxDay = (days.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
        val dayRows = if (days.isEmpty()) "" else days.joinToString("") {
            val w = (it.total * 100 / maxDay).coerceAtLeast(2)
            "<p class='lbl'>${it.date} — ${it.total} phút</p><div style='background:#333;border-radius:8px;height:16px'><div style='background:#FF8F00;width:$w%;height:16px;border-radius:8px'></div></div>"
        }
        val yday = store.yesterdayTotal()
        val reportHtml = "<div class='card'><h2>Báo cáo</h2>" +
            "<p class='lbl'>Hôm nay / Hôm qua</p><p class='val'>${store.usedTodayMin} / ${if (yday < 0) "?" else yday} phút</p>" +
            todayRows +
            "<p class='lbl' style='margin-top:16px'>7 ngày gần nhất</p>" + dayRows + "</div>"
        val msgHtml = if (msg.isEmpty()) "" else
            if (error) "<p class='err'>$msg</p>" else "<p class='msg'>$msg</p>"
        return """<!DOCTYPE html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Khóa TV</title><style>
body{font-family:sans-serif;background:#121212;color:#fff;margin:0;padding:16px}
h1{font-size:32px;margin:8px 0 16px}h2{font-size:24px;margin:0 0 12px}
.card{background:#1E1E1E;border:1px solid #333;border-radius:16px;padding:20px;margin-bottom:16px}
.pill{display:inline-block;padding:8px 20px;border-radius:24px;font-size:24px;font-weight:bold}
.pill.open{background:#2E7D32}.pill.locked{background:#C62828}
.lbl{font-size:20px;color:#B0B0B0;margin:14px 0 2px}.val{font-size:28px;font-weight:bold;margin:0}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:12px}
button{font-size:22px;padding:18px 12px;border:0;border-radius:12px;color:#fff;width:100%}
button.red{background:#C62828}button.green{background:#2E7D32}button.amber{background:#FF8F00;color:#000}
input{font-size:22px;padding:16px;border-radius:12px;border:2px solid #444;background:#2A2A2A;color:#fff;width:100%;box-sizing:border-box;margin-bottom:12px}
.msg{font-size:22px;color:#66BB6A}.err{font-size:22px;color:#EF5350}
a{color:#90CAF9;font-size:20px}
</style></head><body>
<h1>Khóa TV</h1>
<div class="card"><span class="$pillClass">$status</span>
<p class="lbl">Đã xem hôm nay</p><p class="val">${store.usedTodayMin} / $limitTxt</p>
<p class="lbl">Còn lại</p><p class="val">$remTxt</p>
$bonusTxt
<p class="lbl">Lịch khóa hôm nay</p><p class="val" style="font-size:22px">$schedTxt</p>
<p class="lbl">Giờ thời sự: ${store.fmt(store.newsStartMin)} – ${store.fmt(store.newsEndMin)} ($newsTxt)</p>
<p><a href="/settings">Mở Cài đặt: sửa lịch khóa, giới hạn ngày, giờ thời sự</a></p></div>
$msgHtml
<div class="grid">
<form method="post" action="/api/lock"><button class="red" type="submit">Khóa ngay</button></form>
<form method="post" action="/api/unlock"><button class="green" type="submit">Mở khóa 60 phút</button></form>
</div>
<div class="grid">
<form method="post" action="/api/add"><button class="amber" type="submit" name="minutes" value="5">+5 phút</button></form>
<form method="post" action="/api/add"><button class="amber" type="submit" name="minutes" value="10">+10 phút</button></form>
<form method="post" action="/api/add"><button class="amber" type="submit" name="minutes" value="15">+15 phút</button></form>
<form method="post" action="/api/add"><button class="amber" type="submit" name="minutes" value="20">+20 phút</button></form>
<form method="post" action="/api/add" style="grid-column:span 2"><button class="amber" type="submit" name="minutes" value="30">+30 phút</button></form>
</div>
<div class="card"><h2>Thưởng phút</h2>
<p class="lbl" style="margin-top:0">Cộng vào quỹ thưởng — chỉ trừ khi đã hết giờ ngày, phút chưa dùng hết dồn sang hôm sau.</p>
<div class="grid">
<form method="post" action="/api/bonus"><button class="green" type="submit" name="minutes" value="5">Thưởng +5 phút</button></form>
<form method="post" action="/api/bonus"><button class="green" type="submit" name="minutes" value="10">Thưởng +10 phút</button></form>
<form method="post" action="/api/bonus"><button class="green" type="submit" name="minutes" value="15">Thưởng +15 phút</button></form>
<form method="post" action="/api/bonus"><button class="green" type="submit" name="minutes" value="30">Thưởng +30 phút</button></form>
</div></div>
$reportHtml
<div class="card"><h2>Đổi mã PIN</h2>
<form method="post" action="/api/change-pin"><input type="password" name="newPin" placeholder="Nhập mã PIN mới (4–6 số)" inputmode="numeric" maxlength="6" autocomplete="off"><button class="amber" type="submit">Đổi PIN</button></form></div>
<p><a href="/">Làm mới trạng thái</a></p>
</body></html>"""
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun sendHtml(out: OutputStream, code: Int, body: String) {
        val b = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8)); out.write(b); out.flush()
    }

    private fun sendJson(out: OutputStream, code: Int, body: String) {
        val b = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8)); out.write(b); out.flush()
    }
}
