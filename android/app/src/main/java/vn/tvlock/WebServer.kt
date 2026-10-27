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
                method == "GET" && path == "/api/status" -> status(out, params)
                method == "GET" && path == "/api/report" -> report(out, params)
                method == "POST" && path == "/api/lock" -> action(out, params, "lock")
                method == "POST" && path == "/api/unlock" -> action(out, params, "unlock")
                method == "POST" && path == "/api/add" -> action(out, params, "add")
                method == "POST" && path == "/api/bonus" -> bonus(out, params)
                method == "POST" && path == "/api/change-pin" -> changePin(out, params)
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
        val json = """{"locked":$locked,"usedTodayMin":${store.usedTodayMin},"dailyLimitMin":${store.dailyLimitMin},"remainingMin":$remStr,"newsSlot":$news,"ip":"${wifiIp()}","manualLocked":${store.manualLocked},"bonusMin":${store.bonusMin},"perAppToday":{$perApp}}"""
        sendJson(out, 200, json)
    }

    /** v3: report as JSON — today (total + per-app minutes) and the last 14 days. */
    private fun report(out: OutputStream, params: Map<String, String>) {
        val store = Store(ctx)
        store.resetUsedIfNewDay()
        val perApp = store.perAppToday().entries.joinToString(",") { "\"${it.key}\":${it.value}" }
        val days = store.historyDays().joinToString(",") { "{\"date\":\"${it.date}\",\"total\":${it.total}}" }
        val json = """{"today":{"date":"${store.todayStr()}","total":${store.usedTodayMin},"perApp":{$perApp}},"days":[$days]}"""
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
<p class="lbl">Đã xem hôm nay</p><p class="val">${store.usedTodayMin} / ${store.dailyLimitMin} phút</p>
<p class="lbl">Còn lại</p><p class="val">$remTxt</p>
$bonusTxt
<p class="lbl">Giờ khóa: ${store.fmt(store.lockStartMin)} – ${store.fmt(store.lockEndMin)} &nbsp;|&nbsp; Giờ thời sự: ${store.fmt(store.newsStartMin)} – ${store.fmt(store.newsEndMin)} ($newsTxt)</p></div>
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
