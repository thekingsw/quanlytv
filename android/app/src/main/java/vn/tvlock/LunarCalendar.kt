package vn.tvlock

import kotlin.math.*

/**
 * Âm lịch Việt Nam 100% offline — thuật toán thiên văn Hồ Ngọc Đức.
 *
 * Đã kiểm chứng: prototype Python khớp 100% với thư viện độc lập
 * lunar-vn trên 2192 ngày (2023–2028), bao gồm các năm nhuận
 * 2023 (nhuận 2), 2025 (nhuận 6). Mốc chuẩn: 30/10/2026 = 21/9/2026
 * âm lịch, ngày Đinh Sửu, Hắc đạo.
 *
 * LƯU Ý khi sửa hàm getNewMoonDay: dấu của số hạng +0.0161*sin(2*Mpr)
 * trong C1 RẤT dễ gõ sai thành trừ (đã từng sai, lệch 45 phút!).
 */
object LunarCalendar {

    data class LunarDate(val day: Int, val month: Int, val year: Int, val leap: Boolean)

    private const val PI = Math.PI
    private const val TZ = 7.0 // Asia/Ho_Chi_Minh

    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    private val TIET_KHI = arrayOf(
        "Lập xuân", "Vũ thủy", "Kinh trập", "Xuân phân",
        "Thanh minh", "Cốc vũ", "Lập hạ", "Tiểu mãn",
        "Mang chủng", "Hạ chí", "Tiểu thử", "Đại thử",
        "Lập thu", "Xử thử", "Bạch lộ", "Thu phân",
        "Hàn lộ", "Sương giáng", "Lập đông", "Tiểu tuyết",
        "Đại tuyết", "Đông chí", "Tiểu hàn", "Đại hàn"
    )

    /** Offset hoàng đạo theo (chi ngày - chi tháng) mod 12. */
    private val HOANG_DAO_OFFSETS = setOf(0, 1, 4, 5, 7, 10)

    fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161)
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        return jd
    }

    fun getNewMoonDay(k: Int, timeZone: Double = TZ): Int {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = PI / 180
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        // CẨN THẬN DẤU: số hạng 0.0161*sin(2*Mpr) là CỘNG, không phải trừ!
        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c1 = c1 - 0.4068 * sin(mpr * dr) + 0.0161 * sin(dr * 2 * mpr)
        c1 = c1 - 0.0004 * sin(dr * 3 * mpr)
        c1 = c1 + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mpr))
        c1 = c1 - 0.0074 * sin(dr * (m - mpr)) + 0.0004 * sin(dr * (2 * f + m))
        c1 = c1 - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mpr))
        c1 = c1 + 0.0010 * sin(dr * (2 * f - mpr)) + 0.0005 * sin(dr * (2 * mpr + m))
        val deltat = if (t < -11)
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        else
            -0.000278 + 0.000265 * t + 0.000262 * t2
        val jdNew = jd1 + c1 - deltat
        return floor(jdNew + 0.5 + timeZone / 24).toInt()
    }

    fun getSunLongitude(jdn: Int, timeZone: Double = TZ): Int {
        val t = (jdn - 2451545.5 - timeZone / 24) / 36525
        val t2 = t * t
        val dr = PI / 180
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= PI * 2 * (l / (PI * 2)).toInt()
        return (l / PI * 6).toInt()
    }

    /** Kinh độ mặt trời (độ, 0–360) tại thời điểm JD_UTC chính xác. */
    fun sunLongitudeDeg(jdUtc: Double): Double {
        val t = (jdUtc - 2451545.0) / 36525
        val t2 = t * t
        val dr = PI / 180
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= PI * 2 * floor(l / (PI * 2))
        return l / PI * 180
    }

    fun getLunarMonth11(yy: Int, timeZone: Double = TZ): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = (off / 29.530588853).toInt()
        var nm = getNewMoonDay(k, timeZone)
        val sunLong = getSunLongitude(nm, timeZone)
        if (sunLong >= 9) nm = getNewMoonDay(k - 1, timeZone)
        return nm
    }

    fun getLeapMonthOffset(a11: Int, timeZone: Double = TZ): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last = 0
        var i = 1
        var arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
        while (true) {
            last = arc
            i++
            arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
            if (arc == last) break
            if (i >= 14) break
        }
        return i - 1
    }

    fun convertSolar2Lunar(dd: Int, mm: Int, yy: Int, timeZone: Double = TZ): LunarDate {
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var moonIndex = k + 1
        var monthStart = getNewMoonDay(moonIndex, timeZone)
        while (monthStart > dayNumber) {
            moonIndex--
            monthStart = getNewMoonDay(moonIndex, timeZone)
        }
        while (getNewMoonDay(moonIndex + 1, timeZone) <= dayNumber) {
            moonIndex++
            monthStart = getNewMoonDay(moonIndex, timeZone)
        }
        var a11 = getLunarMonth11(yy, timeZone)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1, timeZone)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1, timeZone)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var lunarLeap = false
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapMonthDiff = getLeapMonthOffset(a11, timeZone)
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
                if (diff == leapMonthDiff) lunarLeap = true
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1
        return LunarDate(lunarDay, lunarMonth, lunarYear, lunarLeap)
    }

    /** Can-chi của ngày (vd "Đinh Sửu"). */
    fun canChiDay(jd: Int): String = CAN[(jd + 9) % 10] + " " + CHI[(jd + 1) % 12]

    /** Can-chi của năm âm lịch (vd "Bính Ngọ"). */
    fun canChiYear(lunarYear: Int): String =
        CAN[(lunarYear + 6) % 10] + " " + CHI[(lunarYear + 8) % 12]

    /** Chi của tháng âm lịch (tháng 1 = Dần). Tháng nhuận dùng chung chi. */
    fun monthChi(lunarMonth: Int): Int = (lunarMonth + 1) % 12

    /** true = ngày Hoàng đạo, false = ngày Hắc đạo. */
    fun isHoangDao(lunarMonth: Int, dayJd: Int): Boolean {
        val dayChi = (dayJd + 1) % 12
        val mChi = monthChi(lunarMonth)
        val offset = ((dayChi - mChi) % 12 + 12) % 12
        return HOANG_DAO_OFFSETS.contains(offset)
    }

    /** Tiết khí hiện tại (vd "Lập đông"). */
    fun tietKhiNow(): String {
        val now = System.currentTimeMillis() / 86400000.0
        // đổi millis sang JD_UTC: JD 2440587.5 = 1/1/1970 00:00 UTC
        val jdUtc = now + 2440587.5
        val lon = sunLongitudeDeg(jdUtc)
        val idx = floor(((lon - 315 + 360) % 360) / 15).toInt() % 24
        return TIET_KHI[idx]
    }

    /** Chuỗi hiển thị cho widget: "Âm lịch 21/9 · Ngày Đinh Sửu · Hắc đạo". */
    fun widgetLine(): String {
        val cal = java.util.Calendar.getInstance()
        val dd = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val mm = cal.get(java.util.Calendar.MONTH) + 1
        val yy = cal.get(java.util.Calendar.YEAR)
        val lunar = convertSolar2Lunar(dd, mm, yy)
        val jd = jdFromDate(dd, mm, yy)
        val cc = canChiDay(jd)
        val hd = if (isHoangDao(lunar.month, jd)) "Hoàng đạo" else "Hắc đạo"
        val leap = if (lunar.leap) " (nhuận)" else ""
        return "Âm lịch ${lunar.day}/${lunar.month}$leap · Ngày $cc · $hd"
    }
}
