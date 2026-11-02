package vn.tvlock

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v4.0: client cho hộp thư Tinker (tinker-inbox trên server Ubuntu).
 * Poll /api/poll?since= để lấy thông báo mới, /api/family cho bảng
 * trạng thái gia đình. Chạy trên thread nền, không bao giờ main thread.
 *
 * Token nằm trong InboxConfig (file sinh lúc build, KHÔNG commit git).
 */
object InboxClient {

    data class Message(
        val id: Long,
        val type: String,
        val title: String,
        val body: String,
        val target: String,
        val createdAt: Long
    )

    data class FamilyStatus(val who: String, val text: String, val agoMin: Long)

    private fun base(): String = "http://${InboxConfig.HOST}:${InboxConfig.PORT}"

    private fun get(path: String): String? {
        return try {
            val conn = URL(base() + path).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Authorization", "Bearer ${InboxConfig.TOKEN}")
            val code = conn.responseCode
            if (code != 200) {
                android.util.Log.e("InboxDbg", "GET $path -> HTTP $code (khong phai 200)")
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            body
        } catch (e: Exception) {
            android.util.Log.e("InboxDbg", "GET $path FAILED: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Poll tin mới từ mốc since (unix giây). Trả null nếu lỗi mạng. */
    fun poll(sinceSec: Long): List<Message>? {
        val body = get("/api/poll?since=$sinceSec") ?: return null
        return try {
            val arr = JSONObject(body).optJSONArray("messages") ?: JSONArray()
            val out = mutableListOf<Message>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                // Chỉ nhận tin cho TV (bỏ qua tin target=phone).
                if (o.optString("target", "tv") != "tv") continue
                out.add(Message(
                    id = o.optLong("id"),
                    type = o.optString("type"),
                    title = o.optString("title"),
                    body = o.optString("body"),
                    target = o.optString("target", "tv"),
                    createdAt = o.optLong("created_at")
                ))
            }
            out
        } catch (_: Exception) { null }
    }

    fun family(): List<FamilyStatus>? {
        val body = get("/api/family") ?: return null
        return try {
            val arr = JSONObject(body).optJSONArray("status") ?: JSONArray()
            val out = mutableListOf<FamilyStatus>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(FamilyStatus(
                    who = o.optString("who"),
                    text = o.optString("text"),
                    agoMin = o.optLong("ago_min", -1)
                ))
            }
            out
        } catch (_: Exception) { null }
    }

    /**
     * Poll đầy đủ: tin mới + family. Lưu cache vào Store, cập nhật
     * unread/lastSeen. onDone(newMessages) chạy trên main thread.
     * Trả null nếu lỗi mạng (giữ nguyên cache cũ).
     */
    fun pollAndCache(store: Store, onDone: (List<Message>?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread({
            var fresh: List<Message>? = null
            try {
                val since = store.inboxLastSeenTs
                val msgs = poll(since)
                if (msgs != null) {
                    fresh = msgs
                    if (msgs.isNotEmpty()) {
                        // Gộp vào cache (giữ tối đa 50 tin mới nhất).
                        val cache = try {
                            JSONArray(store.inboxCacheJson)
                        } catch (_: Exception) { JSONArray() }
                        val seen = mutableSetOf<Long>()
                        for (i in 0 until cache.length())
                            seen.add(cache.optJSONObject(i)?.optLong("id") ?: -1)
                        for (m in msgs.sortedBy { it.createdAt }) {
                            if (seen.add(m.id)) {
                                val o = JSONObject()
                                o.put("id", m.id)
                                o.put("type", m.type)
                                o.put("title", m.title)
                                o.put("body", m.body)
                                o.put("created_at", m.createdAt)
                                cache.put(o)
                            }
                        }
                        while (cache.length() > 50) cache.remove(0)
                        store.inboxCacheJson = cache.toString()
                        val maxTs = msgs.maxOf { it.createdAt }
                        if (maxTs > store.inboxLastSeenTs) {
                            store.inboxLastSeenTs = maxTs
                            store.inboxUnread = store.inboxUnread + msgs.size
                        }
                    }
                    store.inboxLastPoll = System.currentTimeMillis()
                }
                // Family status (không critical nếu lỗi).
                family()?.let { fam ->
                    val a = JSONArray()
                    for (f in fam) {
                        val o = JSONObject()
                        o.put("who", f.who)
                        o.put("text", f.text)
                        o.put("ago_min", f.agoMin)
                        a.put(o)
                    }
                    store.familyCacheJson = a.toString()
                }
            } catch (_: Exception) { }
            val r = fresh
            main.post { onDone(r) }
        }, "inbox-poll").also { it.isDaemon = true; it.start() }
    }

    /** Đọc cache tin nhắn từ Store (mới nhất trước). */
    fun cachedMessages(store: Store): List<Message> {
        return try {
            val arr = JSONArray(store.inboxCacheJson)
            val out = mutableListOf<Message>()
            for (i in arr.length() - 1 downTo 0) {
                val o = arr.getJSONObject(i)
                out.add(Message(
                    o.optLong("id"), o.optString("type"),
                    o.optString("title"), o.optString("body"),
                    "tv", o.optLong("created_at")
                ))
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    fun cachedFamily(store: Store): List<FamilyStatus> {
        return try {
            val arr = JSONArray(store.familyCacheJson)
            val out = mutableListOf<FamilyStatus>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(FamilyStatus(o.optString("who"), o.optString("text"), o.optLong("ago_min", -1)))
            }
            out
        } catch (_: Exception) { emptyList() }
    }
}
