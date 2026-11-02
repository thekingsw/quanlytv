package vn.tvlock

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** v3.0: thời tiết cho màn hình chính — Open-Meteo (miễn phí, không cần
 *  key). Chạy trên thread riêng, KHÔNG bao giờ chạy trên main thread.
 *  Kết quả được cache vào prefs để mở màn hình lên có ngay. */
object WeatherFetcher {

    data class Result(val tempC: Double, val desc: String, val cityName: String)

    const val STALE_MS = 30 * 60 * 1000L // refresh mỗi 30 phút

    fun weatherDesc(code: Int): String = when (code) {
        0 -> "Quang đãng"
        1 -> "Chủ yếu quang đãng"
        2 -> "Ít mây"
        3 -> "Âm u"
        45, 48 -> "Sương mù"
        51, 53, 55 -> "Mưa phùn"
        56, 57 -> "Mưa phùn đóng băng"
        61 -> "Mưa nhẹ"
        63 -> "Mưa"
        65 -> "Mưa to"
        66, 67 -> "Mưa đóng băng"
        71, 73, 75, 77 -> "Tuyết"
        80 -> "Mưa rào nhẹ"
        81 -> "Mưa rào"
        82 -> "Mưa rào to"
        95 -> "Dông"
        96, 99 -> "Dông có mưa đá"
        else -> ""
    }

    /** Đọc cache từ prefs. Trả null nếu chưa có. */
    fun cached(store: Store): Result? {
        return try {
            val raw = store.weatherCacheJson
            if (raw.isEmpty()) return null
            val o = JSONObject(raw)
            val t = o.optDouble("t", Double.NaN)
            if (t.isNaN()) return null
            Result(t, o.optString("d", ""), o.optString("c", ""))
        } catch (_: Exception) { null }
    }

    fun cacheAgeMs(store: Store): Long {
        return try {
            val o = JSONObject(store.weatherCacheJson)
            System.currentTimeMillis() - o.optLong("ts", 0)
        } catch (_: Exception) { Long.MAX_VALUE }
    }

    /** Tải mới trên thread nền; onDone luôn chạy trên main thread.
     *  Thành công thì lưu cache, thất bại thì trả null (giữ cache cũ). */
    fun fetch(store: Store, onDone: (Result?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread({
            val res = try {
                val (lat, lon) = store.weatherCityCoords()
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code&timezone=Asia%2FBangkok"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val cur = JSONObject(body).optJSONObject("current")
                    ?: throw Exception("thieu current")
                val temp = cur.optDouble("temperature_2m", Double.NaN)
                if (temp.isNaN()) throw Exception("thieu nhiet do")
                Result(temp, weatherDesc(cur.optInt("weather_code", -1)), store.weatherCityName())
            } catch (_: Exception) { null }
            main.post {
                if (res != null) {
                    val o = JSONObject()
                    o.put("t", res.tempC)
                    o.put("d", res.desc)
                    o.put("c", res.cityName)
                    o.put("ts", System.currentTimeMillis())
                    store.weatherCacheJson = o.toString()
                }
                onDone(res)
            }
        }, "weather-fetch").also { it.isDaemon = true; it.start() }
    }
}
