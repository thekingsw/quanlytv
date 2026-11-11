package vn.tvlock

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class LockActivity : Activity() {
    // v3.0: cờ tĩnh cho HomeActivity biết màn khóa có đang hiện không —
    // chống mở chồng màn khóa (HomeActivity tick mỗi giây + GuardService
    // tick đều có thể mở). Chỉ thêm cờ, không sửa logic khóa nào khác.
    // v4.1: showLock() — sửa race: trước đây cờ chỉ bật ở onCreate nên
    // 2 tick cùng lọt qua khe hở và mở chồng 2-3 màn khóa. Giờ kiểm tra +
    // hạ cờ trong cùng khối synchronized, cờ bật TRƯỚC khi startActivity.
    companion object {
        @Volatile var isShowing = false
        private var lastLaunchMs = 0L

        /** Mở màn khóa (mode lock) chống chồng. Trả false nếu vừa mở
         *  trong 3 giây trước (chống race). Nếu đã hiện rồi (isShowing)
         *  thì đưa lên trước thay vì bỏ qua — vd bấm HOME khi đang khóa. */
        @Synchronized
        fun showLock(ctx: Context, i: Intent): Boolean {
            val now = System.currentTimeMillis()
            // Đã hiện rồi: đưa lên trước (không tạo mới).
            if (isShowing) {
                return try {
                    i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    ctx.startActivity(i)
                    true
                } catch (e: Exception) {
                    android.util.Log.e("LockActivity", "showLock(reorder) failed", e)
                    false
                }
            }
            if (now - lastLaunchMs < 3000) return false
            lastLaunchMs = now
            isShowing = true
            return try {
                ctx.startActivity(i)
                true
            } catch (e: Exception) {
                android.util.Log.e("LockActivity", "showLock(start) failed", e)
                isShowing = false
                false
            }
        }
    }

    private lateinit var store: Store
    private val pinBuf = StringBuilder()
    private lateinit var tvPin: TextView
    private lateinit var tvClock: TextView
    private lateinit var btnRewardCode: Button
    private lateinit var btnCancel: Button
    private var defaultClockText = "Nhập mã PIN để mở khóa"
    // v2.8: chế độ nhập mã thưởng (chỉ ở mode "lock") — bàn phím số dùng
    // để nhập mã thưởng 6 số thay vì mã PIN; hai luồng tách biệt.
    private var rewardMode = false
    private lateinit var pinView: LinearLayout
    private lateinit var optionsView: LinearLayout
    private lateinit var btnNews: Button
    private lateinit var ivLockBg: ImageView
    private lateinit var tvLockMessage: TextView
    // Giai đoạn 4: cột trái có 2 trạng thái — lockBgBar (thanh tối dưới cùng,
    // chỉ khi có ảnh nền riêng) và lockNoBg (mặt trăng + lời nhắc + lý do +
    // nút thời sự, chỉ khi không có ảnh). tvLockMessageNoBg là lời nhắc 48sp
    // của trạng thái không-ảnh (tvLockMessage chỉ có 1 id, dùng cho trạng
    // thái có-ảnh); refreshLockLook() set text cả 2 chỗ.
    private lateinit var lockBgBar: LinearLayout
    private lateinit var lockNoBg: LinearLayout
    private lateinit var tvLockMessageNoBg: TextView
    private var mode = "lock"
    private var addMinutes = 0
    // v2.9: màn khóa này có phải khóa toàn TV không (tick mở khi
    // isLockedNow) — khác với chặn riêng 1 app (Trợ năng/blockReason mở
    // khi app bị chặn nhưng TV chưa hẳn bị khóa). Chỉ màn khóa toàn TV
    // mới tự tắt về Home khi TV hết khóa (guard ở onResume); màn chặn
    // app không tự tắt theo isLockedNow vì isLockedNow có thể đã false
    // ngay lúc nó được mở (vd app bị chặn riêng trong khi TV đang mở).
    private var wholeLock = false
    // v2.9: nhận broadcast mở khóa từ web (WebServer gửi khi bố bấm
    // "Mở khóa") — chỉ có tác dụng ở mode "lock".
    private var receiverRegistered = false
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (mode == "lock") goHomeAndFinish()
        }
    }
    // v2.1: chế độ xem trước màn hình khóa — chỉ để xem giao diện sẽ
    // hiện thế nào, không khóa thật, Back thoát, PIN không có tác dụng.
    private var isPreview = false
    private var previewMessage: String? = null
    private var previewBgPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true
        setContentView(R.layout.activity_lock)
        store = Store(this)
        mode = intent.getStringExtra("mode") ?: "lock"
        wholeLock = intent.getBooleanExtra("wholeLock", false)
        isPreview = mode == "preview"
        previewMessage = intent.getStringExtra("previewMessage")
        previewBgPath = intent.getStringExtra("previewBgPath")
        addMinutes = intent.getIntExtra("minutes", 0)
        val reason = when {
            isPreview -> "Xem trước màn hình khóa"
            mode == "webpin" -> "Xem mã PIN web"
            mode == "settings" -> "Mở Cài đặt"
            else -> intent.getStringExtra("reason") ?: store.lockReason(System.currentTimeMillis())
        }
        findViewById<TextView>(R.id.tvReason).text = reason
        tvClock = findViewById(R.id.tvClock)
        tvClock.text = when {
            isPreview -> "Bấm Back để thoát — bàn phím chỉ để xem thử"
            mode == "webpin" -> "Nhập mã PIN khóa TV để tiếp tục"
            mode == "settings" -> "Nhập mã PIN khóa TV để tiếp tục"
            else -> "Nhập mã PIN để mở khóa"
        }
        defaultClockText = tvClock.text.toString()
        tvPin = findViewById(R.id.tvPin)
        pinView = findViewById(R.id.pinView)
        optionsView = findViewById(R.id.optionsView)
        btnNews = findViewById(R.id.btnNews)
        ivLockBg = findViewById(R.id.ivLockBg)
        tvLockMessage = findViewById(R.id.tvLockMessage)
        lockBgBar = findViewById(R.id.lockBgBar)
        lockNoBg = findViewById(R.id.lockNoBg)
        tvLockMessageNoBg = findViewById(R.id.tvLockMessageNoBg)
        btnCancel = findViewById(R.id.btnCancel)
        // v1.8: nút Mở thời sự — chỉ ở chế độ khóa và đang trong khung thời sự,
        // không cần PIN (trẻ được xem thời sự, app khác vẫn khóa).
        btnNews.setOnClickListener { openNewsApp() }
        refreshLockLook()
        refreshNewsButton()
        val digits = listOf(
            R.id.key0 to "0", R.id.key1 to "1", R.id.key2 to "2", R.id.key3 to "3",
            R.id.key4 to "4", R.id.key5 to "5", R.id.key6 to "6", R.id.key7 to "7",
            R.id.key8 to "8", R.id.key9 to "9"
        )
        for ((id, d) in digits) findViewById<Button>(id).setOnClickListener { appendDigit(d) }
        findViewById<Button>(R.id.keyDel).setOnClickListener { deleteDigit() }
        findViewById<Button>(R.id.keyOk).setOnClickListener { submitPin() }
        btnCancel.visibility = if (mode == "lock") View.GONE else View.VISIBLE
        btnCancel.setOnClickListener { if (mode != "lock") finish() }
        // v2.8: nút nhập mã thưởng — chỉ ở màn hình khóa (mode "lock").
        // ĐỔI 2026-10-31: hiện cả trong giờ thời sự (xem refreshRewardButton).
        btnRewardCode = findViewById(R.id.btnRewardCode)
        refreshRewardButton()
        // The cancel button is hidden in lock mode; keep D-pad focus on a
        // visible key instead of navigating to the hidden cancel button.
        if (mode == "lock") btnRewardCode.nextFocusDownId = R.id.key1
        btnRewardCode.setOnClickListener { setRewardMode(!rewardMode) }
        // Option buttons (shown after correct PIN, except in direct-add mode).
        val opts = listOf(R.id.optAdd5 to 5, R.id.optAdd10 to 10, R.id.optAdd15 to 15, R.id.optAdd20 to 20, R.id.optAdd30 to 30)
        for ((id, min) in opts) {
            findViewById<Button>(id).setOnClickListener {
                store.addMinutes(min)
                Toast.makeText(this, "Đã cho thêm $min phút", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        findViewById<Button>(R.id.optUnlockEnd).setOnClickListener {
            store.unlockUntilEndOfLockWindow(System.currentTimeMillis())
            Toast.makeText(this, "Đã mở khóa", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<Button>(R.id.optCancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            // v2.5: đã nhập đúng PIN ở màn khóa rồi nên mở Cài đặt kèm
            // pinVerified — SettingsActivity không hỏi PIN lần nữa.
            val i = Intent(this, SettingsActivity::class.java)
            i.putExtra("pinVerified", true)
            startActivity(i)
        }
        findViewById<Button>(R.id.key1).requestFocus()
        updatePinDisplay()
    }

    override fun onResume() {
        super.onResume()
        // v2.9: đăng ký nhận broadcast mở khóa từ web (chỉ mode "lock"
        // mới phản ứng — kiểm tra trong onReceive).
        if (!receiverRegistered) {
            try {
                registerReceiver(unlockReceiver, IntentFilter("vn.tvlock.UNLOCKED"))
                receiverRegistered = true
            } catch (_: Exception) { }
        }
        // v2.9: màn khóa còn sót lại sau khi TV đã hết khóa thì về Home
        // ngay, không để màn khóa cũ chắn màn hình. Hai trường hợp an
        // toàn, KHÔNG kích hoạt sai lúc mới tạo activity:
        // (a) màn khóa toàn TV mà TV đã hết khóa (hết khung khóa, bố mở
        // khóa bằng cách khác); (b) bố vừa mở khóa trên web (unlockUntilMs
        // đang chạy thì mọi lý do chặn đều đã hết — blockReason trả null).
        // Màn chặn riêng 1 app KHÔNG tự tắt theo isLockedNow vì lúc nó
        // được mở isLockedNow có thể đã false (app bị chặn riêng trong
        // khi TV đang mở) — trường hợp đó chỉ tắt bằng broadcast web.
        if (!isPreview && mode == "lock") {
            val now = System.currentTimeMillis()
            if ((wholeLock && !store.isLockedNow(now)) || now < store.unlockUntilMs) {
                goHomeAndFinish()
                return
            }
        }
        // Ảnh/lời nhắc có thể vừa đổi từ điện thoại — tải lại mỗi lần hiện.
        refreshLockLook()
        refreshNewsButton()
        refreshRewardButton()
        if (!isPreview && mode != "webpin" && mode != "settings") {
            findViewById<TextView>(R.id.tvReason).text =
                intent.getStringExtra("reason") ?: store.lockReason(System.currentTimeMillis())
        }
    }

    override fun onPause() {
        if (receiverRegistered) {
            try { unregisterReceiver(unlockReceiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
        super.onPause()
    }

    /** v2.9: về màn hình chính (launcher) rồi đóng màn khóa — dùng khi
     *  mở khóa trên web, khi màn khóa sót lại sau khi hết khóa, và khi
     *  nhập mã thưởng thành công. */
    private fun goHomeAndFinish() {
        val home = Intent(Intent.ACTION_MAIN)
        home.addCategory(Intent.CATEGORY_HOME)
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(home) } catch (_: Exception) { }
        finish()
    }

    /** v2.9: nút "Nhập mã thưởng" chỉ hiện ở màn hình khóa VÀ ngoài
     *  khung giờ thời sự.
     *  ĐỔI (user 2026-10-31): trong giờ thời sự VẪN hiện nút nhập mã thưởng —
     *  nhập đúng thì được xem tiếp trong giờ thời sự (đã coi thời sự xong
     *  vẫn có thể xem cái khác nếu còn thời gian). */
    private fun refreshRewardButton() {
        val show = mode == "lock"
        btnRewardCode.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** v1.8: ảnh nền + lời nhắc bên trái (ảnh riêng nếu có, không thì nền tối).
     *  v2.1: ở chế độ xem trước thì dùng lời nhắc/ảnh đang sửa (chưa Áp dụng)
     *  nếu có, không thì dùng ảnh/lời nhắc đã lưu.
     *  Giai đoạn 4: 2 trạng thái cột trái — có ảnh thì hiện thanh tối dưới
     *  cùng (lockBgBar) với lời nhắc; không ảnh (hoặc ảnh lỗi) thì hiện
     *  mặt trăng + lời nhắc 48sp + lý do + nút thời sự (lockNoBg).
     *  Lời nhắc set ở cả 2 TextView; nút thời sự nằm trong lockNoBg nên
     *  chỉ hiện khi không có ảnh nền riêng (logic refreshNewsButton giữ
     *  nguyên — ẩn/hiện trên chính nút). */
    private fun refreshLockLook() {
        tvLockMessage.text = previewMessage ?: store.lockMessage
        tvLockMessageNoBg.text = tvLockMessage.text
        val previewFile = previewBgPath?.takeIf { it.isNotEmpty() }?.let { java.io.File(it) }
        val bgFile = if (previewFile != null && previewFile.exists()) previewFile else store.lockBgFile()
        var hasBg = false
        if (bgFile.exists()) {
            try {
                val bmp = BitmapFactory.decodeFile(bgFile.absolutePath)
                if (bmp != null) {
                    ivLockBg.setImageBitmap(bmp)
                    hasBg = true
                } else ivLockBg.setImageDrawable(null)
            } catch (_: Exception) { ivLockBg.setImageDrawable(null) }
        } else {
            ivLockBg.setImageDrawable(null)
        }
        lockBgBar.visibility = if (hasBg) View.VISIBLE else View.GONE
        lockNoBg.visibility = if (hasBg) View.GONE else View.VISIBLE
    }

    /** v1.8: chỉ hiện nút Mở thời sự khi đang trong khung thời sự và đã
     * chọn app thời sự. Mở app rồi để màn hình khóa ở dưới — thoát app
     * thời sự là quay lại màn hình khóa. */
    private fun refreshNewsButton() {
        val show = !isPreview && mode == "lock" && store.newsPkg.isNotEmpty() &&
            store.isInNewsSlot(System.currentTimeMillis())
        btnNews.visibility = if (show) View.VISIBLE else View.GONE
        if (show) btnNews.text = "Mở thời sự: ${store.appLabel(store.newsPkg)}"
    }

    private fun openNewsApp() {
        if (isPreview) {
            Toast.makeText(this, "Bản xem trước — không mở app thật", Toast.LENGTH_SHORT).show()
            return
        }
        val pkg = store.newsPkg
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            Toast.makeText(this, "Không mở được app thời sự", Toast.LENGTH_SHORT).show()
            return
        }
        try { startActivity(launch) } catch (_: Exception) {
            Toast.makeText(this, "Không mở được app thời sự", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { appendDigit("0"); return true }
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> { appendDigit("1"); return true }
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { appendDigit("2"); return true }
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { appendDigit("3"); return true }
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { appendDigit("4"); return true }
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { appendDigit("5"); return true }
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { appendDigit("6"); return true }
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { appendDigit("7"); return true }
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { appendDigit("8"); return true }
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { appendDigit("9"); return true }
            KeyEvent.KEYCODE_DEL -> { deleteDigit(); return true }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                // v4.1: chặn triệt để — kể cả giữ lâu cũng không thoát được.
                // Nếu đang nhập PIN thì submit, còn không thì nuốt luôn.
                if (pinView.visibility == View.VISIBLE) submitPin()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // v4.1: chặn long-press OK — không cho thoát màn khóa bằng cách giữ nút.
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    private fun appendDigit(d: String) {
        // v2.8: mã thưởng chỉ có 6 số; mã PIN tối đa 8 số như cũ.
        val maxLen = if (rewardMode) 6 else 8
        if (pinBuf.length < maxLen) pinBuf.append(d)
        updatePinDisplay()
    }

    private fun deleteDigit() {
        if (pinBuf.isNotEmpty()) pinBuf.deleteCharAt(pinBuf.length - 1)
        updatePinDisplay()
    }

    /** Giai đoạn 4: "●" cho số đã nhập, "○" cho số chưa nhập — luôn đủ
     *  6 chấm (tối đa 6 chấm hiển thị dù PIN dài tới 8 ký tự); khi rỗng
     *  hiện "○○○○○○" thay cho "____" cũ. */
    private fun updatePinDisplay() {
        val filled = minOf(pinBuf.length, 6)
        tvPin.text = "●".repeat(filled) + "○".repeat(6 - filled)
    }

    private fun submitPin() {
        // v2.8: bản xem trước — PIN không có tác dụng, chỉ để xem giao diện.
        if (isPreview) {
            Toast.makeText(this, "Đây chỉ là bản xem trước — PIN không có tác dụng ở đây", Toast.LENGTH_SHORT).show()
            pinBuf.clear()
            updatePinDisplay()
            return
        }
        // v2.8: đang ở chế độ nhập mã thưởng thì phím OK đổi mã thưởng,
        // không kiểm tra mã PIN khóa TV.
        if (rewardMode && mode == "lock") {
            submitRewardCode()
            return
        }
        // (2026-11-09) Đang bị khóa nhập PIN do đoán mò thì từ chối ngay,
        // không kiểm tra mã (tránh dò tiếp trong thời gian khóa).
        val nowMs = System.currentTimeMillis()
        if (PinGuard.isLocked(store.pinGuardState(), nowMs)) {
            Toast.makeText(this, "Sai quá nhiều lần, thử lại sau 5 phút", Toast.LENGTH_SHORT).show()
            pinBuf.clear()
            updatePinDisplay()
            return
        }
        if (pinBuf.toString() == store.pin) {
            // Nhập đúng thì xóa đếm sai (PinGuard dùng chung cho cả 3 loại mã).
            store.savePinGuardState(PinGuard.recordSuccess(store.pinGuardState()))
            when (mode) {
                "add" -> {
                    if (addMinutes > 0) {
                        store.addMinutes(addMinutes)
                        Toast.makeText(this, "Đã cho thêm $addMinutes phút", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                "unlock" -> {
                    store.manualLocked = false
                    store.unlockUntilMs = System.currentTimeMillis() + 60 * 60000L
                    Toast.makeText(this, "Đã mở khóa 60 phút", Toast.LENGTH_SHORT).show()
                    finish()
                }
                "bonus" -> {
                    if (addMinutes > 0) {
                        store.addBonus(addMinutes)
                        Toast.makeText(this, "Đã thưởng thêm $addMinutes phút (quỹ còn ${store.bonusMin} phút)", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                // v2.3: đã nhập đúng mã PIN khóa TV thì mở màn hình
                // quản lý mã PIN web (xem/đổi/đặt lại).
                "webpin" -> {
                    store.startAdminSession(System.currentTimeMillis())
                    startActivity(Intent(this, WebPinActivity::class.java))
                    finish()
                }
                // v2.5: đã nhập đúng mã PIN khóa TV thì mở Cài đặt —
                // mở kèm pinVerified để SettingsActivity không hỏi lại.
                // v4.0: target "home" (từ bánh răng màn hình chính) thì mở
                // thẳng cài đặt màn hình chính cho dễ tìm chỗ đặt ảnh nền.
                // (2026-11-09) target "system": mở Cài đặt hệ thống của
                // Android sau khi nhập PIN (nút bánh răng trên remote) —
                // trước đây mở thẳng không cần PIN.
                "settings" -> {
                    // Phiên PIN có hạn: trong giờ khóa, khóa tạm nhường để chỉnh cài đặt
                    // (thay cho chốt "lastPkg == vn.tvlock" cũ — xem LockPolicy.evaluate).
                    store.startAdminSession(System.currentTimeMillis())
                    val target = intent.getStringExtra("target")
                    if (target == "system") {
                        val i = Intent(android.provider.Settings.ACTION_SETTINGS)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(i)
                        finish()
                    } else {
                        val i = if (target == "home")
                            Intent(this, HomeSettingsActivity::class.java)
                        else
                            Intent(this, SettingsActivity::class.java)
                        i.putExtra("pinVerified", true)
                        startActivity(i)
                        finish()
                    }
                }
                else -> {
                    pinView.visibility = View.GONE
                    optionsView.visibility = View.VISIBLE
                    findViewById<Button>(R.id.optAdd5).requestFocus()
                }
            }
        } else {
            // Sai mã PIN: đếm lỗi qua PinGuard dùng chung (sai 5 lần liên
            // tiếp thì khóa nhập 5 phút — trước đây PIN không có gì chặn,
            // dò hết 4 số chỉ là vấn đề thời gian).
            val now = System.currentTimeMillis()
            val st = PinGuard.recordFailure(store.pinGuardState(), now)
            store.savePinGuardState(st)
            if (PinGuard.isLocked(st, now)) {
                Toast.makeText(this, "Sai quá nhiều lần, thử lại sau 5 phút", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Sai mã PIN", Toast.LENGTH_SHORT).show()
            }
            pinBuf.clear()
            updatePinDisplay()
        }
    }

    /** v2.8: chuyển bàn phím giữa nhập mã PIN và nhập mã thưởng (chỉ ở
     *  màn hình khóa). Đổi chế độ thì xóa các số đã nhập. */
    private fun setRewardMode(on: Boolean) {
        rewardMode = on
        pinBuf.clear()
        updatePinDisplay()
        if (on) {
            tvClock.text = "Nhập mã thưởng — 6 số"
            btnRewardCode.text = "← Quay lại nhập mã PIN"
        } else {
            tvClock.text = defaultClockText
            btnRewardCode.text = "Nhập mã thưởng"
        }
    }

    /** v2.9: đổi mã thưởng 6 số. Thứ tự kiểm tra (cái nào cũng KHÔNG
     *  tiêu mã, KHÔNG trừ lượt đoán mò nếu không phải mã sai):
     *  1) (ĐÃ BỎ 2026-10-31) giờ thời sự vẫn dùng được mã thưởng;
     *  2) đang bị khóa nhập mã 5 phút do đoán mò → từ chối;
     *  3) hôm nay đã nhập đủ số mã cho phép (limit > 0) → từ chối với
     *  thông báo "Hôm nay đã nhập đủ X mã rồi, mai nhập tiếp nhé" —
     *  mã KHÔNG bị tiêu, hôm sau nhập lại vẫn được;
     *  4) đổi mã: sai thì đếm lỗi (đủ 5 lần thì khóa nhập mã 5 phút);
     *  đúng thì tăng đếm hôm nay lên 1 rồi cộng phút bằng addMinutes —
     *  GIỐNG nút +5'/+10' sau PIN (mở tạm qua unlockUntilMs, hiệu lực
     *  ngay lập tức; v2.8 dùng addBonus đã bị đảo ngược — xem ghi chú
     *  ở Store.kt, đừng sửa ngược lại). Đổi đúng + hết khóa thì về
     *  màn hình chính; vẫn khóa (trường hợp hiếm) thì ở lại màn khóa. */
    private fun submitRewardCode() {
        val code = pinBuf.toString()
        if (code.length < 6) {
            Toast.makeText(this, "Mã thưởng có 6 số", Toast.LENGTH_SHORT).show()
            return
        }
        val now = System.currentTimeMillis()
        // ĐỔI (user 2026-10-31): trong giờ thời sự VẪN dùng được mã thưởng —
        // nhập đúng thì cộng phút xem tiếp trong giờ thời sự.
        // (2026-11-09) Khóa nhập mã dùng chung qua PinGuard.
        if (PinGuard.isLocked(store.rewardGuardState(), now)) {
            Toast.makeText(this, "Sai quá nhiều lần, thử lại sau 5 phút", Toast.LENGTH_SHORT).show()
            pinBuf.clear()
            updatePinDisplay()
            return
        }
        // v2.9 (phần 6): quá số mã cho phép trong ngày thì từ chối nhưng
        // KHÔNG tiêu mã — kiểm tra TRƯỚC khi redeem.
        val limit = store.dailyRewardCodeLimit
        if (limit > 0 && store.rewardCodesUsedToday() >= limit) {
            Toast.makeText(this, "Hôm nay đã nhập đủ $limit mã rồi, mai nhập tiếp nhé", Toast.LENGTH_SHORT).show()
            pinBuf.clear()
            updatePinDisplay()
            return
        }
        val mins = store.redeemRewardCode(code)
        if (mins > 0) {
            store.saveRewardGuardState(PinGuard.recordSuccess(store.rewardGuardState()))
            store.incrementRewardCodesUsedToday()
            store.addMinutes(mins)
            pinBuf.clear()
            updatePinDisplay()
            if (!store.isLockedNow(System.currentTimeMillis())) {
                Toast.makeText(this, "Đã cộng $mins phút, chúc con xem vui vẻ!", Toast.LENGTH_SHORT).show()
                goHomeAndFinish()
            } else {
                Toast.makeText(this, "Đã cộng $mins phút (vẫn đang trong giờ khóa)", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Sai mã: đếm lỗi qua PinGuard dùng chung (đủ 5 lần thì khóa
            // nhập mã 5 phút).
            val st = PinGuard.recordFailure(store.rewardGuardState(), System.currentTimeMillis())
            store.saveRewardGuardState(st)
            pinBuf.clear()
            updatePinDisplay()
            if (PinGuard.isLocked(st, System.currentTimeMillis())) {
                Toast.makeText(this, "Sai quá nhiều lần, thử lại sau 5 phút", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Mã thưởng không đúng, thử lại nhé", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        // v2.1: bản xem trước thì Back thoát bình thường.
        if (isPreview) { super.onBackPressed(); return }
        // Block BACK from dismissing the lock screen when it was triggered by a lock.
        if (mode == "lock") return
        super.onBackPressed()
    }

    // v3.0: hạ cờ isShowing khi màn khóa đóng — xem companion object.
    override fun onDestroy() {
        isShowing = false
        super.onDestroy()
    }
}
