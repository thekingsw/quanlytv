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
                method == "POST" && path == "/api/lock" -> action(out, params, "lock")
                method == "POST" && path == "/api/unlock" -> action(out, params, "unlock")
                method == "POST" && path == "/api/add" -> action(out, params, "add")
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

    private fun checkPin(params: Map<String, String>): Boolean {
        val store = Store(ctx)
        return params["pin"] == store.pin
    }

    private fun status(out: OutputStream, params: Map<String, String>) {
        if (!checkPin(params)) { sendJson(out, 403, """{"error":"Sai mã PIN"}"""); return }
        val store = Store(ctx)
        store.resetUsedIfNewDay()
        val now = System.currentTimeMillis()
        val locked = store.isLockedNow(now)
        val rem = store.remainingMin()
        val remStr = if (rem == Int.MAX_VALUE) -1 else rem
        val news = store.isInNewsSlot(now)
        val json = """{"locked":$locked,"usedTodayMin":${store.usedTodayMin},"dailyLimitMin":${store.dailyLimitMin},"remainingMin":$remStr,"newsSlot":$news,"ip":"${wifiIp()}","manualLocked":${store.manualLocked}}"""
        sendJson(out, 200, json)
    }

    private fun action(out: OutputStream, params: Map<String, String>, kind: String) {
        if (!checkPin(params)) {
            sendHtml(out, 403, homePage("Sai mã PIN, thử lại."))
            return
        }
        val store = Store(ctx)
        val msg = when (kind) {
            "lock" -> { store.manualLocked = true; store.unlockUntilMs = 0L; "Đã khóa TV ngay." }
            "unlock" -> { store.manualLocked = false; store.unlockUntilMs = System.currentTimeMillis() + 60 * 60000L; "Đã mở khóa TV trong 60 phút." }
            "add" -> {
                val min = params["minutes"]?.toIntOrNull() ?: 0
                if (min !in setOf(5, 10, 15, 20, 30)) "Số phút không hợp lệ."
                else { store.addMinutes(min); "Đã cho thêm $min phút." }
            }
            else -> "Không rõ lệnh."
        }
        sendHtml(out, 200, homePage(msg))
    }

    private fun wifiIp(): String {
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"
        } catch (_: Exception) { "" }
    }

    private fun homePage(msg: String): String {
        val store = Store(ctx)
        store.resetUsedIfNewDay()
        val now = System.currentTimeMillis()
        val locked = store.isLockedNow(now)
        val rem = store.remainingMin()
        val remTxt = if (rem == Int.MAX_VALUE) "không giới hạn" else "$rem phút"
        val status = if (locked) "ĐANG KHÓA" else "ĐANG MỞ"
        val newsTxt = if (store.newsPkg.isEmpty()) "chưa chọn app" else store.newsPkg
        val msgHtml = if (msg.isEmpty()) "" else "<p style='color:#4CAF50;font-size:28px'>$msg</p>"
        return """<!DOCTYPE html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Khóa TV</title><style>body{font-family:sans-serif;background:#111;color:#fff;padding:24px}h1{font-size:36px}input,button{font-size:26px;padding:14px;margin:6px;border-radius:10px}button{background:#1565C0;color:#fff;border:0;min-width:120px}button.red{background:#C62828}.pin{width:200px}</style></head><body>
<h1>Khóa TV</h1><p style="font-size:28px">Trạng thái: <b>$status</b></p>
<p style="font-size:24px">Đã xem hôm nay: ${store.usedTodayMin} / ${store.dailyLimitMin} phút — Còn lại: $remTxt</p>
<p style="font-size:22px">Giờ khóa: ${store.fmt(store.lockStartMin)} – ${store.fmt(store.lockEndMin)} &nbsp;|&nbsp; Giờ thời sự: ${store.fmt(store.newsStartMin)} – ${store.fmt(store.newsEndMin)} ($newsTxt)</p>
$msgHtml
<form method="post" action="/api/lock"><input class="pin" type="password" name="pin" placeholder="Nhập mã PIN" inputmode="numeric"><button class="red" type="submit">Khóa ngay</button></form>
<form method="post" action="/api/unlock"><input class="pin" type="password" name="pin" placeholder="Nhập mã PIN" inputmode="numeric"><button type="submit">Mở khóa 60 phút</button></form>
<form method="post" action="/api/add"><input class="pin" type="password" name="pin" placeholder="Nhập mã PIN" inputmode="numeric"><br>
<button type="submit" name="minutes" value="5">+5 phút</button>
<button type="submit" name="minutes" value="10">+10 phút</button>
<button type="submit" name="minutes" value="15">+15 phút</button>
<button type="submit" name="minutes" value="20">+20 phút</button>
<button type="submit" name="minutes" value="30">+30 phút</button></form>
<p><a style="color:#90CAF9;font-size:22px" href="/">Làm mới trạng thái</a></p>
</body></html>"""
    }

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
