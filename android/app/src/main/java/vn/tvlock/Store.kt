package vn.tvlock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Store(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("tvlock", Context.MODE_PRIVATE)

    var pin: String
        get() = prefs.getString("pin", "1234") ?: "1234"
        set(v) = prefs.edit().putString("pin", v).apply()

    // ---------- v2.3: mã PIN riêng cho trang web điều khiển ----------
    // Khác mã PIN khóa TV. Lần đầu chưa đặt thì mặc định = mã PIN khóa TV
    // hiện tại (vào web được ngay sau khi cập nhật, không phải đặt mã
    // mới); đổi mã PIN khóa TV sau này KHÔNG làm đổi mã PIN web.
    var webPin: String
        get() = prefs.getString("webPin", "") ?: ""
        set(v) = prefs.edit().putString("webPin", v).apply()

    /** Mã PIN web đang dùng: mã đã đặt, hoặc mã PIN khóa TV nếu chưa đặt
     *  (lưu lại để lần sau dùng luôn giá trị này). */
    fun webPinOrDefault(): String {
        val w = webPin
        if (w.isNotEmpty()) return w
        val d = pin
        webPin = d
        return d
    }

    // ---------- v4: flexible lock schedules ----------
    // Days: 0 = Thứ 2 ... 6 = Chủ nhật. A schedule locks the TV on its days
    // between startMin and endMin. startMin == endMin means "locked all day"
    // on those days; startMin > endMin means overnight (e.g. 19:00 -> 10:00
    // next day). Multiple schedules combine by union: any match locks.
    // (2026-11-09) Logic lịch sống trong SchedulePolicy thuần (test được);
    // Store chỉ đọc/ghi JSON và ủy quyền.

    fun dayNames(): List<String> = SchedulePolicy.dayNames()

    /** Calendar.DAY_OF_WEEK (1=Sunday..7=Saturday) -> 0=Mon..6=Sun. */
    fun dayIndexOf(cal: Calendar): Int = SchedulePolicy.dayIndexOf(cal)

    private fun lockSchedulesJson(): JSONArray {
        // One-time migration from the old single lock window (v3): keep the
        // user's current rule as one schedule covering all days.
        if (!prefs.contains("lockSchedules")) {
            val oldStart = prefs.getInt("lockStartMin", 1260)
            val oldEnd = prefs.getInt("lockEndMin", 420)
            val arr = JSONArray()
            val o = JSONObject()
            o.put("id", "migrated")
            o.put("days", JSONArray(listOf(0, 1, 2, 3, 4, 5, 6)))
            o.put("startMin", oldStart)
            o.put("endMin", oldEnd)
            arr.put(o)
            prefs.edit().putString("lockSchedules", arr.toString())
                .remove("lockStartMin").remove("lockEndMin").apply()
            return arr
        }
        return try { JSONArray(prefs.getString("lockSchedules", "[]") ?: "[]") }
        catch (_: Exception) { JSONArray() }
    }

    fun lockSchedules(): List<SchedulePolicy.Schedule> {
        val out = mutableListOf<SchedulePolicy.Schedule>()
        val arr = lockSchedulesJson()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val days = mutableListOf<Int>()
            val dj = o.optJSONArray("days")
            if (dj != null) for (j in 0 until dj.length()) days.add(dj.optInt(j))
            out.add(SchedulePolicy.Schedule(o.optString("id", "s$i"), days, o.optInt("startMin", 1260), o.optInt("endMin", 420)))
        }
        return out
    }

    fun saveLockSchedules(list: List<SchedulePolicy.Schedule>) {
        val arr = JSONArray()
        for (s in list) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("days", JSONArray(s.days))
            o.put("startMin", s.startMin)
            o.put("endMin", s.endMin)
            arr.put(o)
        }
        prefs.edit().putString("lockSchedules", arr.toString()).apply()
    }

    fun upsertLockSchedule(s: SchedulePolicy.Schedule) {
        val list = lockSchedules().toMutableList()
        val i = list.indexOfFirst { it.id == s.id }
        if (i >= 0) list[i] = s else list.add(s)
        saveLockSchedules(list)
    }

    fun deleteLockSchedule(id: String) = saveLockSchedules(lockSchedules().filter { it.id != id })

    fun newScheduleId(): String = "s" + System.currentTimeMillis()

    fun scheduleTimeText(s: SchedulePolicy.Schedule): String = SchedulePolicy.scheduleTimeText(s)

    fun scheduleSummary(s: SchedulePolicy.Schedule): String = SchedulePolicy.scheduleSummary(s)

    fun schedulesForDay(day: Int): List<SchedulePolicy.Schedule> = lockSchedules().filter { day in it.days }

    fun todayScheduleSummaries(nowMs: Long): List<String> {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        return schedulesForDay(dayIndexOf(c)).map { scheduleSummary(it) }
    }

    /** Schedules matching right now (union: any match locks the TV). */
    fun matchingSchedules(nowMs: Long): List<SchedulePolicy.Schedule> =
        SchedulePolicy.matchingSchedules(lockSchedules(), nowMs)

    fun isLockedBySchedule(nowMs: Long): Boolean =
        SchedulePolicy.isLockedBySchedule(lockSchedules(), nowMs)

    /** Minutes until the next schedule starts (0 when inside one, MAX when none). */
    fun minutesUntilNextLock(nowMs: Long): Int =
        SchedulePolicy.minutesUntilNextLock(lockSchedules(), nowMs)

    /** v1.7: seconds until the next schedule starts — same logic as
     * [minutesUntilNextLock] but exact to the second, for the 60s countdown. */
    fun secondsUntilNextLock(nowMs: Long): Int =
        SchedulePolicy.secondsUntilNextLock(lockSchedules(), nowMs)

    // ---------- v4: daily limit per weekday (0=Mon..6=Sun; <=0 = unlimited) ----------
    private fun dailyLimitsJson(): JSONObject {
        // One-time migration from the old single daily limit (v3).
        if (!prefs.contains("dailyLimits")) {
            val old = prefs.getInt("dailyLimitMin", 90)
            val j = JSONObject()
            for (d in 0..6) j.put(d.toString(), old)
            prefs.edit().putString("dailyLimits", j.toString()).remove("dailyLimitMin").apply()
            return j
        }
        return try { JSONObject(prefs.getString("dailyLimits", "{}") ?: "{}") }
        catch (_: Exception) { JSONObject() }
    }

    fun limitForDay(day: Int): Int = dailyLimitsJson().optInt(day.toString(), 90)

    fun limitForToday(): Int {
        val c = Calendar.getInstance()
        return limitForDay(dayIndexOf(c))
    }

    fun setLimitForDay(day: Int, minutes: Int) {
        val j = dailyLimitsJson()
        j.put(day.toString(), minutes)
        prefs.edit().putString("dailyLimits", j.toString()).apply()
    }

    fun limitText(min: Int): String = if (min <= 0) "không giới hạn" else "$min phút"

    fun dailyLimitsSummary(): String {
        return (0..6).joinToString("\n") { "${dayNames()[it]}: ${limitText(limitForDay(it))}" }
    }

    var newsStartMin: Int
        get() = prefs.getInt("newsStartMin", 1140)
        set(v) = prefs.edit().putInt("newsStartMin", v).apply()

    var newsEndMin: Int
        get() = prefs.getInt("newsEndMin", 1200)
        set(v) = prefs.edit().putInt("newsEndMin", v).apply()

    var newsPkg: String
        get() = prefs.getString("newsPkg", "") ?: ""
        set(v) = prefs.edit().putString("newsPkg", v).apply()

    // ---------- v1.8: tùy chỉnh màn hình khóa (ảnh nền + lời nhắc) ----------
    var lockMessage: String
        get() = prefs.getString("lockMessage", "Hết giờ xem TV rồi con nhé!") ?: "Hết giờ xem TV rồi con nhé!"
        set(v) = prefs.edit().putString("lockMessage", v).apply()

    fun lockBgFile(): java.io.File = java.io.File(ctx.filesDir, "lock_bg.jpg")

    /** v2.1: file tạm cho ảnh nền đang sửa nhưng chưa Áp dụng. */
    fun lockBgTempFile(): java.io.File = java.io.File(ctx.filesDir, "preview_bg.jpg")

    fun hasLockBg(): Boolean = lockBgFile().exists()

    fun deleteLockBg() {
        try { lockBgFile().delete() } catch (_: Exception) { }
    }

    /** Giải mã ảnh, thu nhỏ về tối đa 1920px rồi lưu JPEG (85%) làm ảnh nền
     * màn hình khóa. Trả false khi dữ liệu không phải ảnh hợp lệ. */
    fun saveLockBgBytes(bytes: ByteArray): Boolean = saveLockBgBytesTo(bytes, lockBgFile())

    /** v2.1: lưu ảnh nền đã thu nhỏ vào một file chỉ định (file tạm khi
     *  đang sửa, file chính khi Áp dụng). */
    fun saveLockBgBytesTo(bytes: ByteArray, file: java.io.File): Boolean {
        return try {
            if (bytes.size > 8 * 1024 * 1024) return false
            val bounds = android.graphics.BitmapFactory.Options().also { it.inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1920) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().also { it.inSampleSize = sample }
            var bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return false
            if (bmp.width > 1920 || bmp.height > 1920) {
                val scale = minOf(1920f / bmp.width, 1920f / bmp.height)
                val scaled = android.graphics.Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
                )
                if (scaled != bmp) bmp.recycle()
                bmp = scaled
            }
            java.io.FileOutputStream(file).use {
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
            }
            bmp.recycle()
            true
        } catch (_: Exception) { false }
    }

    /**
     * Khóa tay HIỆU LỰC hôm nay. Trả đúng cái LockPolicy dùng để khóa, nên màn PIN
     * và web không còn hiện "ĐANG KHÓA" ở hôm sau khi TV đã tự mở (lỗi UI lệch engine).
     * Dữ liệu cũ (bật trước bản có ngày, chưa ghi ngày): coi là bật HÔM NAY — không
     * nhả khóa đang bật lúc cập nhật; sang hôm sau tự hết như mọi khóa tay khác.
     */
    var manualLocked: Boolean
        get() {
            if (!prefs.getBoolean("manualLocked", false)) return false
            var d = prefs.getString("manualLockedDate", "") ?: ""
            if (d.isEmpty()) {
                d = todayStr()
                prefs.edit().putString("manualLockedDate", d).apply()
            }
            return d == todayStr()
        }
        set(v) {
            val e = prefs.edit().putBoolean("manualLocked", v)
            // Khóa tay chỉ có tác dụng trong ngày bật nó (2026-11-09):
            // ghi lại ngày bật; sang hôm sau LockPolicy tự coi như mở.
            if (v) e.putString("manualLockedDate", todayStr()) else e.putString("manualLockedDate", "")
            e.apply()
        }

    /** Ngày khóa tay được bật ("yyyy-MM-dd", "" nếu chưa từng bật). */
    val manualLockedDate: String
        get() = prefs.getString("manualLockedDate", "") ?: ""

    // ---------- v2.0: chống tắt Trợ năng (như CleanTV) ----------
    // Quyền WRITE_SECURE_SETTINGS (cấp một lần qua ADB bằng
    // `pm grant vn.tvlock android.permission.WRITE_SECURE_SETTINGS` —
    // Cài TV 1.5 cấp tự động sau khi cài) cho app tự ghi Settings.Secure.
    // Khi Trợ năng bị tắt trong Cài đặt TV, GuardService (tick 1 giây)
    // tự ghi lại dịch vụ của mình vào danh sách và bật lên.
    var antiDisable: Boolean
        get() = prefs.getBoolean("antiDisable", true)
        set(v) = prefs.edit().putBoolean("antiDisable", v).apply()

    fun accessServiceComponent(): String =
        android.content.ComponentName(ctx, GuardAccessibilityService::class.java).flattenToString()

    fun hasWriteSecureSettings(): Boolean =
        ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun isAccessEnabled(): Boolean = try {
        val cur = android.provider.Settings.Secure.getString(
            ctx.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        cur.split(":").contains(accessServiceComponent())
    } catch (_: Exception) { false }

    /** Tự bật lại Trợ năng nếu đang tắt và app có quyền ghi cài đặt hệ
     *  thống. Trả về true nếu sau lệnh này Trợ năng đang bật. Không ném lỗi. */
    fun ensureAccessEnabled(): Boolean {
        if (isAccessEnabled()) return true
        if (!hasWriteSecureSettings()) return false
        return try {
            val cr = ctx.contentResolver
            val cur = android.provider.Settings.Secure.getString(
                cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val comp = accessServiceComponent()
            val next = when {
                cur.isBlank() || cur == "null" -> comp
                cur.split(":").contains(comp) -> cur
                else -> "$cur:$comp"
            }
            android.provider.Settings.Secure.putString(
                cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            android.provider.Settings.Secure.putString(
                cr, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            isAccessEnabled()
        } catch (_: Exception) { false }
    }

    var unlockUntilMs: Long
        get() = prefs.getLong("unlockUntilMs", 0L)
        set(v) = prefs.edit().putLong("unlockUntilMs", v).apply()

    var usedTodayMin: Int
        get() = prefs.getInt("usedTodayMin", 0)
        set(v) = prefs.edit().putInt("usedTodayMin", v).apply()

    var usedDate: String
        get() = prefs.getString("usedDate", "") ?: ""
        set(v) = prefs.edit().putString("usedDate", v).apply()

    // ---------- Foreground app: CHỈ LockEngine được ghi (qua markForeground) ----------
    // `private set` để trình biên dịch chặn mọi chỗ khác ghi tắt — lỗi "lastPkg kẹt ở
    // app hệ thống" (06–08/11) sinh ra vì nhiều nơi cùng ghi với luật khác nhau.
    var lastPkg: String
        get() = prefs.getString("lastPkg", "") ?: ""
        private set(v) = prefs.edit().putString("lastPkg", v).apply()

    var lastPkgStartMs: Long
        get() = prefs.getLong("lastPkgStartMs", 0L)
        private set(v) = prefs.edit().putLong("lastPkgStartMs", v).apply()

    /** Ghi app foreground mới và mốc bắt đầu. Chỉ LockEngine gọi. */
    fun markForeground(pkg: String, nowMs: Long) {
        lastPkg = pkg
        lastPkgStartMs = nowMs
    }

    /** Đặt lại mốc thời gian về [nowMs] mà giữ nguyên lastPkg (service khởi động lại). */
    fun restartClock(nowMs: Long) {
        lastPkgStartMs = nowMs
    }

    private var remainderMs: Long
        get() = prefs.getLong("usedRemainderMs", 0L)
        set(v) = prefs.edit().putLong("usedRemainderMs", v).apply()

    // ---------- v3: bonus minutes (reward pool, never expires at midnight) ----------
    var bonusMin: Int
        get() = prefs.getInt("bonusMin", 0)
        set(v) = prefs.edit().putInt("bonusMin", v.coerceAtLeast(0)).apply()

    fun addBonus(minutes: Int) {
        if (minutes > 0) {
            bonusMin = bonusMin + minutes
            clearWarnings()
        }
    }

    // ---------- v2.8: mã thưởng dùng một lần ----------
    // Bố tạo mã 6 số trên web, đưa cho con; con nhập mã trên màn hình khóa
    // thì được cộng phút.
    // v2.9 ĐẢO QUYẾT ĐỊNH v2.8: mã thưởng KHÔNG cộng vào quỹ thưởng
    // (addBonus) nữa mà cộng bằng addMinutes — giống nút +5'/+10' sau PIN
    // (mở tạm qua unlockUntilMs, hiệu lực ngay lập tức). Lý do: mã thưởng
    // chỉ nhập được khi màn hình đang khóa nên hiệu lực ngay mới đúng.
    // ĐỪNG "sửa ngược" lại thành addBonus nếu không có yêu cầu mới.
    // Mã dùng xong xóa ngay, chưa dùng thì không hết hạn.
    data class RewardCode(val code: String, val minutes: Int, val createdAt: Long)

    private fun rewardCodesJson(): JSONArray {
        return try { JSONArray(prefs.getString("rewardCodes", "[]") ?: "[]") }
        catch (_: Exception) { JSONArray() }
    }

    private fun saveRewardCodes(list: List<RewardCode>) {
        val arr = JSONArray()
        for (c in list) {
            val o = JSONObject()
            o.put("code", c.code)
            o.put("minutes", c.minutes)
            o.put("createdAt", c.createdAt)
            arr.put(o)
        }
        prefs.edit().putString("rewardCodes", arr.toString()).apply()
    }

    fun activeRewardCodes(): List<RewardCode> {
        val out = mutableListOf<RewardCode>()
        val arr = rewardCodesJson()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(RewardCode(o.optString("code", ""), o.optInt("minutes", 0), o.optLong("createdAt", 0L)))
        }
        return out.filter { it.code.length == 6 }.sortedBy { it.createdAt }
    }

    /** Mã dễ đoán thì không sinh: một chữ số lặp (000000, 111111...),
     *  dãy tăng/giảm liên tục (123456, 654321, 012345, 987654...). */
    private fun isGuessableCode(code: String): Boolean {
        if (code.length != 6 || !code.all { it.isDigit() }) return true
        if (code.toSet().size == 1) return true
        val d = code.map { it - '0' }
        if (d.zipWithNext().all { (a, b) -> b == a + 1 }) return true
        if (d.zipWithNext().all { (a, b) -> b == a - 1 }) return true
        return false
    }

    /** Sinh mã 6 số mới (5 hoặc 10 phút): không trùng mã đang còn hiệu
     *  lực, không trùng mã PIN khóa TV, không dễ đoán. */
    fun generateRewardCode(minutes: Int): RewardCode {
        val min = if (minutes == 5) 5 else 10
        val existing = activeRewardCodes().map { it.code }.toSet()
        val rnd = java.security.SecureRandom()
        var code = ""
        var tries = 0
        do {
            code = (1..6).joinToString("") { rnd.nextInt(10).toString() }
            tries++
        } while (tries < 200 && (code in existing || code == pin || isGuessableCode(code)))
        val rc = RewardCode(code, min, System.currentTimeMillis())
        saveRewardCodes(activeRewardCodes() + rc)
        return rc
    }

    /** Đổi mã: đúng thì XÓA mã ngay khỏi danh sách và trả số phút của mã;
     *  sai thì trả 0. Mã không hết hạn nên không kiểm tra thời gian. */
    fun redeemRewardCode(code: String): Int {
        val list = activeRewardCodes()
        val found = list.firstOrNull { it.code == code } ?: return 0
        saveRewardCodes(list.filter { it.code != code })
        return found.minutes
    }

    /** Bố thu hồi một mã trên web (con không dùng được nữa). */
    fun deleteRewardCode(code: String) {
        saveRewardCodes(activeRewardCodes().filter { it.code != code })
    }

    // ---------- v2.8: chống đoán mò mã thưởng ----------
    // Mã thưởng đưa cho trẻ nên trẻ biết định dạng và có thể ngồi đoán —
    // sai 5 lần liên tiếp thì khóa nhập mã 5 phút. (2026-11-09) Cơ chế
    // này giờ dùng chung qua PinGuard cho cả 3 loại mã (PIN khóa TV,
    // PIN web, mã thưởng) — xem pinGuardState()/rewardGuardState().
    // Giữ nguyên tên key prefs cũ để không mất trạng thái đang khóa dở.
    var rewardCodeFails: Int
        get() = prefs.getInt("rewardCodeFails", 0)
        set(v) = prefs.edit().putInt("rewardCodeFails", v).apply()

    var rewardCodeLockedUntilMs: Long
        get() = prefs.getLong("rewardCodeLockedUntilMs", 0L)
        set(v) = prefs.edit().putLong("rewardCodeLockedUntilMs", v).apply()

    /** PinGuard state cho mã thưởng (đọc/ghi qua key prefs cũ). */
    fun rewardGuardState(): PinGuard.State =
        PinGuard.State(rewardCodeFails, rewardCodeLockedUntilMs)

    fun saveRewardGuardState(s: PinGuard.State) {
        rewardCodeFails = s.fails
        rewardCodeLockedUntilMs = s.lockedUntilMs
    }

    // ---------- 2026-11-09: chống đoán mò mã PIN khóa TV ----------
    // PIN mở khóa vô hạn nên cũng cần khóa tạm như mã thưởng (trước đây
    // không có gì chặn — dò hết 4 số chỉ là vấn đề thời gian).
    var pinFails: Int
        get() = prefs.getInt("pinFails", 0)
        set(v) = prefs.edit().putInt("pinFails", v).apply()

    var pinLockedUntilMs: Long
        get() = prefs.getLong("pinLockedUntilMs", 0L)
        set(v) = prefs.edit().putLong("pinLockedUntilMs", v).apply()

    /** PinGuard state cho mã PIN khóa TV. */
    fun pinGuardState(): PinGuard.State = PinGuard.State(pinFails, pinLockedUntilMs)

    fun savePinGuardState(s: PinGuard.State) {
        pinFails = s.fails
        pinLockedUntilMs = s.lockedUntilMs
    }

    // ---------- v2.9: số mã thưởng được nhập mỗi ngày ----------
    // 0 = không giới hạn. Đếm reset khi sang ngày mới — cùng cơ chế
    // lazy-reset theo ngày như usedTodayMin (resetUsedIfNewDay), nhưng
    // dùng trường ngày riêng để không phụ thuộc vào lần reset nào khác:
    // lần đọc đầu tiên của ngày mới tự đưa đếm về 0.
    var dailyRewardCodeLimit: Int
        get() = prefs.getInt("dailyRewardCodeLimit", 0)
        set(v) = prefs.edit().putInt("dailyRewardCodeLimit", v.coerceAtLeast(0)).apply()

    private var rewardCodesUsedDate: String
        get() = prefs.getString("rewardCodesUsedDate", "") ?: ""
        set(v) = prefs.edit().putString("rewardCodesUsedDate", v).apply()

    private var rewardCodesUsedTodayRaw: Int
        get() = prefs.getInt("rewardCodesUsedToday", 0)
        set(v) = prefs.edit().putInt("rewardCodesUsedToday", v).apply()

    /** Số mã đã nhập hôm nay — tự reset về 0 khi sang ngày mới. Mọi
     *  chỗ đọc/tăng đếm đều qua hàm này, không đọc trực tiếp field. */
    fun rewardCodesUsedToday(): Int {
        if (rewardCodesUsedDate != todayStr()) {
            rewardCodesUsedDate = todayStr()
            rewardCodesUsedTodayRaw = 0
        }
        return rewardCodesUsedTodayRaw
    }

    /** Ghi nhận 1 mã đã đổi thành công hôm nay (chỉ gọi sau khi redeem
     *  thành công — từ chối vì quá limit thì KHÔNG gọi, mã vẫn còn). */
    fun incrementRewardCodesUsedToday() {
        rewardCodesUsedTodayRaw = rewardCodesUsedToday() + 1
    }

    // ---------- v2.9: đã từng cấp quyền chụp màn hình (MediaProjection) ----------
    // Chỉ để hiển thị trạng thái "Cần cấp lại sau khi khởi động lại TV"
    // (projection thật thì mất khi khởi động lại, không tự có lại được).
    var screenshotGrantedOnce: Boolean
        get() = prefs.getBoolean("screenshotGrantedOnce", false)
        set(v) = prefs.edit().putBoolean("screenshotGrantedOnce", v).apply()

    // ---------- v3: per-app rules ----------
    data class AppRule(val mode: String, val minutes: Int)

    private fun appRulesJson(): JSONObject {
        return try { JSONObject(prefs.getString("appRules", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    }

    fun appRules(): Map<String, AppRule> {
        val out = mutableMapOf<String, AppRule>()
        val j = appRulesJson()
        val keys = j.keys()
        while (keys.hasNext()) {
            val pkg = keys.next()
            val r = j.optJSONObject(pkg) ?: continue
            out[pkg] = AppRule(r.optString("mode", "normal"), r.optInt("minutes", 0))
        }
        return out
    }

    fun appRule(pkg: String): AppRule = appRules()[pkg] ?: AppRule("normal", 0)

    fun setAppRule(pkg: String, mode: String, minutes: Int) {
        val j = appRulesJson()
        if (mode == "normal") j.remove(pkg)
        else {
            val r = JSONObject()
            r.put("mode", mode)
            r.put("minutes", minutes)
            j.put(pkg, r)
        }
        prefs.edit().putString("appRules", j.toString()).apply()
    }

    fun appRulesCount(): Int = appRules().size

    /** Minutes left for this app's own limit, or Int.MAX_VALUE when it has no limit rule. */
    fun appLimitRemainingMin(pkg: String): Int {
        val r = appRule(pkg)
        if (r.mode != "limit") return Int.MAX_VALUE
        return (r.minutes - perAppMin(pkg)).coerceAtLeast(0)
    }

    // ---------- v3: per-app minutes today ----------
    private fun perAppJson(): JSONObject {
        return try { JSONObject(prefs.getString("perAppToday", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    }

    fun perAppToday(): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        val j = perAppJson()
        val keys = j.keys()
        while (keys.hasNext()) {
            val pkg = keys.next()
            out[pkg] = j.optInt(pkg, 0)
        }
        return out
    }

    fun perAppMin(pkg: String): Int = perAppToday()[pkg] ?: 0

    private fun addPerAppMin(pkg: String, minutes: Int) {
        if (minutes <= 0) return
        val j = perAppJson()
        j.put(pkg, j.optInt(pkg, 0) + minutes)
        prefs.edit().putString("perAppToday", j.toString()).apply()
    }

    // ---------- v3: 14-day history ----------
    data class DayEntry(val date: String, val total: Int, val perApp: Map<String, Int>)

    private fun historyJson(): JSONObject {
        return try { JSONObject(prefs.getString("history", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    }

    fun historyDays(): List<DayEntry> {
        val j = historyJson()
        val out = mutableListOf<DayEntry>()
        val keys = j.keys()
        while (keys.hasNext()) {
            val date = keys.next()
            val d = j.optJSONObject(date) ?: continue
            val perApp = mutableMapOf<String, Int>()
            val pa = d.optJSONObject("perApp")
            if (pa != null) {
                val pk = pa.keys()
                while (pk.hasNext()) {
                    val pkg = pk.next()
                    perApp[pkg] = pa.optInt(pkg, 0)
                }
            }
            out.add(DayEntry(date, d.optInt("total", 0), perApp))
        }
        return out.sortedBy { it.date }.takeLast(14)
    }

    fun historyDayTotal(date: String): Int {
        return historyDays().firstOrNull { it.date == date }?.total ?: -1
    }

    fun yesterdayStr(): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    fun yesterdayTotal(): Int = historyDayTotal(yesterdayStr())

    private fun finalizeDay() {
        val date = usedDate
        if (date.isEmpty() || date == todayStr()) return
        val perApp = perAppToday()
        if (usedTodayMin <= 0 && perApp.isEmpty()) return
        val j = historyJson()
        val d = JSONObject()
        d.put("total", usedTodayMin)
        val pa = JSONObject()
        for ((pkg, min) in perApp) pa.put(pkg, min)
        d.put("perApp", pa)
        j.put(date, d)
        // Trim to the 14 most recent days.
        val dates = mutableListOf<String>()
        val keys = j.keys()
        while (keys.hasNext()) dates.add(keys.next())
        dates.sorted().dropLast(14).forEach { j.remove(it) }
        prefs.edit().putString("history", j.toString()).apply()
    }

    // ---------- v3: warned milestones (reset daily / when extra time is granted) ----------
    private fun warnedJson(): JSONObject {
        return try { JSONObject(prefs.getString("warned", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    }

    fun warned(kind: String, threshold: Int): Boolean {
        return warnedJson().optBoolean("$kind:$threshold", false)
    }

    fun markWarned(kind: String, threshold: Int) {
        val j = warnedJson()
        j.put("$kind:$threshold", true)
        prefs.edit().putString("warned", j.toString()).apply()
    }

    fun clearWarnings() {
        prefs.edit().putString("warned", "{}").apply()
    }

    /** v1.5: chỉ xóa cờ cảnh báo của một loại (ví dụ "lockwindow"),
     * để mỗi khung khóa đều được nhắc 5'/1' riêng, không phải 1 lần/ngày. */
    fun clearWarningsKind(kind: String) {
        val j = warnedJson()
        val keys = j.keys()
        val toRemove = mutableListOf<String>()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.startsWith("$kind:")) toRemove.add(k)
        }
        toRemove.forEach { j.remove(it) }
        prefs.edit().putString("warned", j.toString()).apply()
    }

    fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun resetUsedIfNewDay() {
        val t = todayStr()
        if (usedDate != t) {
            finalizeDay()
            usedDate = t
            usedTodayMin = 0
            remainderMs = 0L
            prefs.edit().putString("perAppToday", "{}").apply()
            clearWarnings()
        }
    }

    /** Reset today's counters (used by Settings "Reset số phút đã xem hôm nay"). */
    fun resetTodayUsage() {
        usedTodayMin = 0
        remainderMs = 0L
        prefs.edit().putString("perAppToday", "{}").apply()
        clearWarnings()
    }

    fun minutesOfDay(nowMs: Long): Int = LockPolicy.minutesOfDay(nowMs)

    fun isInWindow(nowMin: Int, start: Int, end: Int): Boolean =
        LockPolicy.isInWindow(nowMin, start, end)

    fun isInNewsSlot(nowMs: Long): Boolean = isInWindow(minutesOfDay(nowMs), newsStartMin, newsEndMin)

    /**
     * Snapshot current state into the pure LockPolicy input. Only reads;
     * callers that mutate (resetUsedIfNewDay) do so before building it.
     * (2026-11-08: loose coupling — quyết định khóa sống trong LockPolicy,
     * Store chỉ đọc prefs thành Input rồi áp kết quả.)
     */
    private fun lockInput(nowMs: Long, pkg: String = lastPkg): LockPolicy.Input {
        val rule = appRule(pkg)
        val match = matchingSchedules(nowMs)
        return LockPolicy.Input(
            nowMs = nowMs,
            lastPkg = lastPkg,
            newsPkg = newsPkg,
            newsStartMin = newsStartMin,
            newsEndMin = newsEndMin,
            manualLocked = manualLocked,
            unlockUntilMs = unlockUntilMs,
            usedTodayMin = usedTodayMin,
            dailyLimitMin = limitForToday(),
            bonusMin = bonusMin,
            lockedBySchedule = match.isNotEmpty(),
            scheduleReason = match.joinToString("; ") { scheduleSummary(it) },
            appRule = LockPolicy.AppRule(rule.mode, rule.minutes),
            perAppUsedMin = perAppMin(pkg),
            manualLockedDate = manualLockedDate,
            todayDate = todayStr()
        )
    }

    /**
     * QUYẾT ĐỊNH DUY NHẤT cho app foreground hiện tại — LockEngine gọi hàm này.
     * Đừng ghép điều kiện khóa ở nơi khác (xem LockPolicy.evaluate).
     */
    fun evaluateLock(nowMs: Long): LockPolicy.Decision {
        resetUsedIfNewDay()
        return LockPolicy.evaluate(lockInput(nowMs)) { appLabel(it) }
    }

    /** True when the TV should be locked right now (whole-TV rules; per-app rules are separate). */
    fun isLockedNow(nowMs: Long): Boolean {
        resetUsedIfNewDay()
        return LockPolicy.isLockedNow(lockInput(nowMs))
    }

    /** Minutes of daily limit left (excluding bonus). */
    fun dailyRemainingMin(): Int {
        resetUsedIfNewDay()
        val limit = limitForToday()
        if (limit <= 0) return Int.MAX_VALUE
        return (limit - usedTodayMin).coerceAtLeast(0)
    }

    /** Effective minutes left: daily limit + bonus pool. */
    fun remainingMin(): Int {
        val d = dailyRemainingMin()
        if (d == Int.MAX_VALUE) return Int.MAX_VALUE
        return d + bonusMin
    }

    /** v1.7: seconds until the daily limit (+ bonus pool) runs out (MAX when
     * unlimited). Counts full minutes already watched + the sub-minute
     * remainder + time on the current app since the last tick, so the
     * last-minute countdown is accurate to the second. */
    fun secondsUntilDailyLimit(nowMs: Long): Int {
        resetUsedIfNewDay()
        val limit = limitForToday()
        if (limit <= 0) return Int.MAX_VALUE
        val watchedSec = usedTodayMin * 60 + (remainderMs / 1000).toInt() + elapsedSec(nowMs)
        return (limit + bonusMin) * 60 - watchedSec
    }

    /** v1.7: seconds until this app's own limit runs out (MAX when it has none). */
    fun secondsUntilAppLimit(pkg: String, nowMs: Long): Int {
        val r = appRule(pkg)
        if (r.mode != "limit") return Int.MAX_VALUE
        val watchedSec = perAppMin(pkg) * 60 + (remainderMs / 1000).toInt() +
            if (pkg == lastPkg) elapsedSec(nowMs) else 0
        return r.minutes * 60 - watchedSec
    }

    /** Seconds of countable watch time since the last tick for the current
     * foreground app (0 when it is exempt or not counted). */
    private fun elapsedSec(nowMs: Long): Int {
        val pkg = lastPkg
        if (pkg.isEmpty() || isExcluded(pkg)) return 0
        if (isNewsExempt(pkg, nowMs)) return 0
        if (appRule(pkg).mode == "free") return 0
        val start = lastPkgStartMs
        if (start <= 0L || nowMs <= start) return 0
        if (LockPolicy.isSuspendGap(start, nowMs)) return 0 // hệ thống vừa treo, đừng hiện số khổng lồ
        return ((nowMs - start) / 1000).toInt()
    }

    fun lockReason(nowMs: Long): String {
        resetUsedIfNewDay()
        return LockPolicy.lockReason(lockInput(nowMs))
    }

    /**
     * Per-app block check. Priority: v2.9 news exemption > PIN
     * temp-unlock > per-app blocked/limit > manual lock / lock window /
     * daily limit (bonus exhausted). Returns null when the app may run.
     *
     * v2.9: trong khung giờ thời sự, app thời sự đã chọn được MIỄN TRỪ
     * TUYỆT ĐỐI — không bị chặn vì bất kỳ lý do gì (giới hạn ngày, giới
     * hạn từng app, lịch khóa, khóa tay). Kiểm tra này phải đứng TRƯỚC
     * mọi lý do chặn khác, kể cả quy tắc per-app của chính app đó.
     *
     * v2.10: khung giờ thời sự KHÔNG còn độc quyền — các app khác ngoài
     * app thời sự tuân theo quy tắc bình thường (hết giờ ngày, lịch khóa,
     * khóa tay, giới hạn từng app) thay vì bị chặn chỉ vì đang trong khung
     * thời sự. Tức là trong khung thời sự mà còn giờ xem và không vướng
     * lịch khóa thì mở app khác bình thường.
     */
    fun blockReason(pkg: String, nowMs: Long, label: String): String? {
        resetUsedIfNewDay()
        return LockPolicy.blockReason(pkg, label, lockInput(nowMs, pkg))
    }

    fun isExcluded(pkg: String): Boolean = LockPolicy.isExcluded(pkg)

    // Luật foreground nằm ở ForegroundTracker (thuần); LockEngine áp vào Store qua
    // markForeground. Store không còn tự quyết "app nào hợp lệ".

    /** Is this package exempt from counting right now (news app inside the news slot)? */
    fun isNewsExempt(pkg: String, nowMs: Long): Boolean =
        LockPolicy.isNewsExempt(pkg, newsPkg, nowMs, newsStartMin, newsEndMin)

    /** Display label for a package (fallback: the package name). */
    fun appLabel(pkg: String): String {
        return try {
            ctx.packageManager.getApplicationInfo(pkg, 0).loadLabel(ctx.packageManager).toString()
        } catch (_: Exception) { pkg }
    }

    /**
     * Add elapsed viewing time for a package; updates lastPkgStartMs to now.
     * Per-app minutes are always recorded. "free" apps do not consume the daily
     * limit. Minutes past the daily limit are spent from the bonus pool
     * (minute by minute); only when the pool is empty does usedTodayMin grow
     * past the limit and the lock engage.
     */
    fun addElapsed(pkg: String, nowMs: Long) {
        if (pkg.isEmpty() || LockPolicy.isExcluded(pkg)) return
        resetUsedIfNewDay()
        val r = LockPolicy.addElapsedGuarded(
            LockPolicy.CountInput(
                pkg = pkg, nowMs = nowMs, lastPkgStartMs = lastPkgStartMs,
                remainderMs = remainderMs, usedTodayMin = usedTodayMin,
                bonusMin = bonusMin, dailyLimitMin = limitForToday(),
                appRuleMode = appRule(pkg).mode,
                newsPkg = newsPkg, newsStartMin = newsStartMin, newsEndMin = newsEndMin
            )
        )
        remainderMs = r.remainderMs
        if (r.perAppAddMin > 0) addPerAppMin(pkg, r.perAppAddMin)
        usedTodayMin = r.usedTodayMin
        bonusMin = r.bonusMin
    }

    /** Accumulate time for the currently-tracked package up to now (called on a tick). */
    fun tick(nowMs: Long) {
        val pkg = lastPkg
        if (pkg.isEmpty()) return
        addElapsed(pkg, nowMs)
        lastPkgStartMs = nowMs
    }

    /**
     * v2.11: có đang tính giờ xem ngay lúc này không — chỉ khi biết rõ app
     * đang mở và app đó bị tính giờ (không loại trừ, không miễn trừ thời sự,
     * không phải app "free"). Khi false (vd vừa khởi động TV, Trợ năng chưa
     * kịp báo app nào thì lastPkg còn rỗng), các hạn tính theo giờ xem
     * (giới hạn ngày / giới hạn từng app) là số liệu cũ đóng băng — tick
     * không đếm tiếp nên không được hiện đếm ngược cho chúng (sẽ ra con số
     * đứng yên như lỗi "còn 9s" lúc 18:58 ngày 29/10).
     */
    fun isCountingNow(nowMs: Long): Boolean {
        return LockPolicy.isCountingNow(lockInput(nowMs, lastPkg))
    }

    fun fmt(min: Int): String = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

    fun addMinutes(minutes: Int) {
        // Cộng thêm vào mở tạm đang chạy (nếu còn) thay vì ghi đè —
        // nút "CỘNG THÊM GIỜ" phải cộng, không được rút ngắn giờ đang có
        // (vd con còn 7' mã 10', bấm +5 phải thành 12', không phải 5').
        // KHÔNG xóa manualLocked: mở tạm chỉ đè lên khóa tay (isLockedNow
        // đã ưu tiên unlockUntilMs), hết hạn thì khóa tay quay lại —
        // trước đây xóa luôn nên bấm "Khóa ngay" rồi con nhập mã xong
        // hết giờ TV cứ mở (2026-11-06). Nút "Mở khóa" trên web tự xóa
        // cờ khóa tay riêng, không dùng hàm này.
        val now = System.currentTimeMillis()
        unlockUntilMs = maxOf(now, unlockUntilMs) + minutes * 60000L
        clearWarnings()
    }

    /** Unlock until the end of the current lock schedule (or +60 min if not in one).
     *  Overlapping schedules unlock until the LATEST-ending one. */
    fun unlockUntilEndOfLockWindow(nowMs: Long) {
        val endMs = SchedulePolicy.unlockUntilEndOfLockWindowMs(lockSchedules(), nowMs)
        unlockUntilMs = endMs ?: (nowMs + 60 * 60000L)
        manualLocked = false
        clearWarnings()
    }

    // ---------- v3.0: màn hình chính 2-trong-1 ----------
    // CHỈ thêm key mới — không đổi tên prefs file "tvlock", không đổi key cũ,
    // để cài đè lên bản 2.11 giữ nguyên mọi cài đặt khóa.

    // --- Ảnh nền màn hình chính (home_bg.jpg, tái dùng bộ downscale của ảnh khóa) ---
    fun homeBgFile(): java.io.File = java.io.File(ctx.filesDir, "home_bg.jpg")
    fun hasHomeBg(): Boolean = homeBgFile().exists()
    fun deleteHomeBg() {
        try { homeBgFile().delete() } catch (_: Exception) { }
    }
    fun saveHomeBgBytes(bytes: ByteArray): Boolean = saveLockBgBytesTo(bytes, homeBgFile())

    /** v4.0: lưu ảnh nền màn hình chính gửi từ web điện thoại — thu nhỏ
     *  rồi lưu vào thư mục slideshow, trả về tên file (null nếu lỗi). */
    fun saveWallpaperBytes(bytes: ByteArray): String? {
        return try {
            val dir = wallpaperDir()
            dir.mkdirs()
            val f = java.io.File(dir, "wp_${System.currentTimeMillis()}.jpg")
            if (saveLockBgBytesTo(bytes, f)) f.name else null
        } catch (_: Exception) { null }
    }

    // --- Kiểu icon ứng dụng: "default" (icon gốc) | "circle" (cắt tròn) ---
    var iconStyle: String
        get() = prefs.getString("iconStyle", "default") ?: "default"
        set(v) = prefs.edit().putString("iconStyle", v).apply()
    fun iconStyleName(): String = if (iconStyle == "circle") "Tròn" else "Mặc định"

    // --- Thành phố cho thời tiết (Open-Meteo) ---
    var weatherCity: String
        get() = prefs.getString("weatherCity", "hanoi") ?: "hanoi"
        set(v) = prefs.edit().putString("weatherCity", v).apply()
    fun weatherCityName(): String = when (weatherCity) {
        "danang" -> "Đà Nẵng"
        "hcmc" -> "TP.HCM"
        else -> "Hà Nội"
    }
    fun weatherCityCoords(): Pair<Double, Double> = when (weatherCity) {
        "danang" -> 16.0544 to 108.2022
        "hcmc" -> 10.8231 to 106.6297
        else -> 21.0278 to 105.8342
    }

    // --- Cache thời tiết: JSON {"t":nhiệt độ,"d":mô tả,"c":thành phố,"ts":ms} ---
    var weatherCacheJson: String
        get() = prefs.getString("weatherCache", "") ?: ""
        set(v) = prefs.edit().putString("weatherCache", v).apply()

    // --- App yêu thích trên màn hình chính: danh sách package theo thứ tự ghim ---
    // v4.1: nhớ ô app chọn lần cuối — bấm Home từ app khác về / sau khi
    // ẩn-di chuyển app thì focus về đúng chỗ.
    var homeLastPkg: String
        get() = prefs.getString("homeLastPkg", "") ?: ""
        set(v) = prefs.edit().putString("homeLastPkg", v).apply()

    fun homeFavorites(): MutableList<String> {
        val out = mutableListOf<String>()
        try {
            val a = JSONArray(prefs.getString("homeFavorites", "[]"))
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (_: Exception) { }
        return out
    }
    private fun saveHomeFavorites(list: List<String>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        prefs.edit().putString("homeFavorites", a.toString()).apply()
    }
    fun addHomeFavorite(pkg: String) {
        val l = homeFavorites()
        if (!l.contains(pkg)) { l.add(pkg); saveHomeFavorites(l) }
    }
    fun removeHomeFavorite(pkg: String) {
        val l = homeFavorites()
        if (l.remove(pkg)) saveHomeFavorites(l)
    }
    fun isHomeFavorite(pkg: String): Boolean = homeFavorites().contains(pkg)

    // --- App bị ẩn khỏi màn hình chính: tập package ---
    fun homeHidden(): MutableSet<String> {
        return try {
            val out = mutableSetOf<String>()
            val a = JSONArray(prefs.getString("homeHidden", "[]"))
            for (i in 0 until a.length()) out.add(a.getString(i))
            out
        } catch (_: Exception) { mutableSetOf() }
    }
    fun setHomeHidden(pkg: String, hide: Boolean) {
        val s = homeHidden()
        if (hide) s.add(pkg) else s.remove(pkg)
        val a = JSONArray()
        s.forEach { a.put(it) }
        prefs.edit().putString("homeHidden", a.toString()).apply()
    }

    /** v4.0: thứ tự app tùy chỉnh trong lưới launcher (user 2026-11-02 —
     *  di chuyển app tự do, không theo mặc định A-Z). */
    fun homeAppOrder(): List<String> {
        return try {
            val a = JSONArray(prefs.getString("homeAppOrder", "[]") ?: "[]")
            List(a.length()) { a.optString(it) }.filter { it.isNotEmpty() }
        } catch (_: Exception) { emptyList() }
    }
    fun setHomeAppOrder(order: List<String>) {
        val a = JSONArray()
        order.forEach { a.put(it) }
        prefs.edit().putString("homeAppOrder", a.toString()).apply()
    }

    // ============ v4.0: launcher v2 — widget, countdown, push ============

    // Widget bật/tắt (mặc định bật hết; camera là placeholder).
    var widgetTinker: Boolean
        get() = prefs.getBoolean("wTinker", true)
        set(v) = prefs.edit().putBoolean("wTinker", v).apply()
    var widgetFare: Boolean
        get() = prefs.getBoolean("wFare", true)
        set(v) = prefs.edit().putBoolean("wFare", v).apply()
    var widgetCountdown: Boolean
        get() = prefs.getBoolean("wCountdown", true)
        set(v) = prefs.edit().putBoolean("wCountdown", v).apply()
    var widgetFamily: Boolean
        get() = prefs.getBoolean("wFamily", true)
        set(v) = prefs.edit().putBoolean("wFamily", v).apply()
    var widgetCamera: Boolean
        get() = prefs.getBoolean("wCamera", true)
        set(v) = prefs.edit().putBoolean("wCamera", v).apply()

    // Đếm ngược (mặc định: Đà Nẵng 1/6/2027).
    var countdownLabel: String
        get() = prefs.getString("cdLabel", "Đà Nẵng") ?: "Đà Nẵng"
        set(v) = prefs.edit().putString("cdLabel", v).apply()
    var countdownDate: String // yyyy-MM-dd
        get() = prefs.getString("cdDate", "2027-06-01") ?: "2027-06-01"
        set(v) = prefs.edit().putString("cdDate", v).apply()

    /** Số ngày còn lại tới mốc đếm ngược (âm = đã qua). */
    fun countdownDays(): Long {
        return try {
            val p = countdownDate.split("-").map { it.toInt() }
            val cal = java.util.Calendar.getInstance()
            val today = java.util.Calendar.getInstance()
            today.set(java.util.Calendar.HOUR_OF_DAY, 0)
            today.set(java.util.Calendar.MINUTE, 0)
            today.set(java.util.Calendar.SECOND, 0)
            today.set(java.util.Calendar.MILLISECOND, 0)
            cal.set(p[0], p[1] - 1, p[2], 0, 0, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            (cal.timeInMillis - today.timeInMillis) / 86400000L
        } catch (_: Exception) { 0L }
    }

    // Khung giờ cho phép popup (mặc định 7:00–21:00).
    var pushStartHour: Int
        get() = prefs.getInt("pushStart", 7)
        set(v) = prefs.edit().putInt("pushStart", v).apply()
    var pushStartMinute: Int
        get() = prefs.getInt("pushStartMin", 0)
        set(v) = prefs.edit().putInt("pushStartMin", v).apply()
    var pushEndHour: Int
        get() = prefs.getInt("pushEnd", 21)
        set(v) = prefs.edit().putInt("pushEnd", v).apply()
    var pushEndMinute: Int
        get() = prefs.getInt("pushEndMin", 0)
        set(v) = prefs.edit().putInt("pushEndMin", v).apply()

    fun inPushHours(nowMs: Long = System.currentTimeMillis()): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
            cal.get(java.util.Calendar.MINUTE)
        val s = pushStartHour * 60 + pushStartMinute
        val e = pushEndHour * 60 + pushEndMinute
        return if (s <= e) nowMin in s until e else (nowMin >= s || nowMin < e)
    }

    // Chế độ popup: "always" | "home" | "silent".
    var popupMode: String
        get() = prefs.getString("popupMode", "always") ?: "always"
        set(v) = prefs.edit().putString("popupMode", v).apply()
    fun popupModeName(): String = when (popupMode) {
        "home" -> "Chỉ ở màn hình chính"
        "silent" -> "Im lặng (chỉ chuông)"
        else -> "Luôn bật"
    }

    // Inbox: cache tin nhắn + số chưa đọc + mốc poll.
    var inboxLastPoll: Long
        get() = prefs.getLong("inboxLastPoll", 0L)
        set(v) = prefs.edit().putLong("inboxLastPoll", v).apply()
    var inboxLastSeenTs: Long
        get() = prefs.getLong("inboxLastSeen", 0L)
        set(v) = prefs.edit().putLong("inboxLastSeen", v).apply()
    var inboxUnread: Int
        get() = prefs.getInt("inboxUnread", 0)
        set(v) = prefs.edit().putInt("inboxUnread", v).apply()
    var inboxCacheJson: String
        get() = prefs.getString("inboxCache", "[]") ?: "[]"
        set(v) = prefs.edit().putString("inboxCache", v).apply()
    var familyCacheJson: String
        get() = prefs.getString("familyCache", "[]") ?: "[]"
        set(v) = prefs.edit().putString("familyCache", v).apply()

    // Slideshow ảnh nền (phút; 0 = tắt).
    var wallpaperIntervalMin: Int
        get() = prefs.getInt("wpInterval", 30)
        set(v) = prefs.edit().putInt("wpInterval", v).apply()
    // Nguồn ảnh nền màn hình chính: "default" (10 ảnh phong cảnh kèm app)
    // hoặc "custom" (ảnh anh tải từ điện thoại). Mặc định dùng ảnh kèm app
    // cho đẹp ngay, không còn nền đen sì.
    var wallpaperSource: String
        get() = prefs.getString("wpSource", "default") ?: "default"
        set(v) = prefs.edit().putString("wpSource", if (v == "custom") "custom" else "default").apply()
    fun wallpaperDir(): java.io.File = java.io.File(ctx.filesDir, "home_wallpapers")
    fun wallpaperFiles(): List<java.io.File> {
        val d = wallpaperDir()
        if (!d.isDirectory) return emptyList()
        return d.listFiles { f -> f.isFile &&
            (f.name.endsWith(".jpg", true) || f.name.endsWith(".jpeg", true) ||
             f.name.endsWith(".png", true) || f.name.endsWith(".webp", true)) }
            ?.sortedBy { it.name } ?: emptyList()
    }

    // ---------- v4.3: gán nút remote (keycode -> hành động) ----------
    // Hành động: "app:<package>" mở app, "lock" khóa ngay,
    // "msg:<nội dung>" gửi tin nhắn về điện thoại qua inbox.
    // Lưu dạng JSON: {"123":{"action":"app:...","label":"Netflix"}, ...}
    private fun keyMapJson(): JSONObject = try {
        JSONObject(prefs.getString("keymap", "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }

    fun keyMap(): Map<Int, JSONObject> {
        val out = mutableMapOf<Int, JSONObject>()
        val j = keyMapJson()
        val keys = j.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val code = k.toIntOrNull() ?: continue
            val o = j.optJSONObject(k) ?: continue
            out[code] = o
        }
        return out
    }

    fun setKeyMapEntry(keycode: Int, action: String, label: String) {
        val j = keyMapJson()
        j.put(keycode.toString(), JSONObject().put("action", action).put("label", label))
        prefs.edit().putString("keymap", j.toString()).apply()
    }

    fun removeKeyMapEntry(keycode: Int) {
        val j = keyMapJson()
        j.remove(keycode.toString())
        prefs.edit().putString("keymap", j.toString()).apply()
    }

    fun keyMapAction(keycode: Int): JSONObject? = keyMap()[keycode]
}
