package vn.tvlock

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.OvershootInterpolator
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.AdapterView
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * v4.0: màn hình chính v2 — dày đặc theo mockup đã duyệt:
 * đồng hồ + ngày dương + âm lịch (offline) | thời tiết, chuông, cài đặt;
 * hàng widget (Tinker, vé máy bay, đếm ngược, gia đình, camera);
 * 7 yêu thích; lưới app 8 cột; slideshow ảnh nền.
 *
 * Tích hợp khóa: mỗi giây kiểm tra Store.isLockedNow() — nếu TV đang bị
 * khóa thì mở LockActivity (cùng cách GuardService.tick()). Chống mở
 * chồng bằng LockActivity.isShowing. Logic khóa KHÔNG đổi.
 */
class HomeActivity : Activity() {

    private lateinit var store: Store
    private lateinit var bgView: ImageView
    private lateinit var bgDimView: View
    private lateinit var tvDate: TextView
    private lateinit var tvLunar: TextView
    private lateinit var tvWeatherPill: TextView
    private lateinit var tvTimeChip: TextView
    private var timeChipTick = 0
    private lateinit var btnBell: ImageButton
    private lateinit var btnHomeSettings: ImageButton
    private lateinit var tvBellBadge: TextView
    private lateinit var widgetRow: LinearLayout
    private lateinit var favRow: LinearLayout
    private lateinit var favHint: TextView
    private lateinit var grid: GridView
    private lateinit var tvEmpty: TextView
    /** Khung chọn animated trượt theo hướng phím (user 2026-10-31). */
    private lateinit var focusFrame: View
    private var frameAnimator: ValueAnimator? = null
    private val gridAdapter = AppAdapter()

    /** Placeholder khi icon app chưa tải xong (tải nền). */
    private val appIconPlaceholder: Drawable by lazy {
        resources.getDrawable(android.R.drawable.sym_def_app_icon, theme)
    }

    /** Gắn icon cho ô app: có trong cache thì hiện ngay, chưa có thì hiện
     *  placeholder rồi tải nền; view tái chế thì bỏ kết quả cũ (so tag). */
    private fun bindAppIcon(iconView: RoundedIconView, app: TvApp) {
        iconView.roundMode = store.iconStyle == "rounded"
        val cached = AppIconCache.peek(app.pkg)
        if (cached != null) {
            iconView.tag = null
            iconView.setImageDrawable(cached)
            return
        }
        iconView.tag = app.pkg
        iconView.setImageDrawable(appIconPlaceholder)
        AppIconCache.load(packageManager, app) { d ->
            if (d != null && iconView.tag == app.pkg) {
                iconView.tag = null
                iconView.setImageDrawable(d)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wallpaperIdx = 0
    private var wallpaperLastSwitch = 0L

    private val tick = object : Runnable {
        override fun run() {
            checkLocked()
            rotateWallpaperIfDue()
            if (++timeChipTick >= 30) {
                timeChipTick = 0
                refreshTimeChip()
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        // v4.1: đang khóa thì hiện màn khóa ngay, không vẽ UI home —
        // bấm HOME khi khóa không được thấy trang chính dù chỉ 1 giây.
        val now = System.currentTimeMillis()
        if (store.isLockedNow(now)) {
            val i = Intent(this, LockActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            i.putExtra("reason", store.lockReason(now))
            i.putExtra("mode", "lock")
            i.putExtra("wholeLock", true)
            LockActivity.showLock(this, i)
            finish()
            return
        }
        setContentView(R.layout.activity_home)
        // v4.0: migrate ảnh nền đơn (v3.0) sang thư mục slideshow.
        migrateSingleBg()
        bgView = findViewById(R.id.bgView)
        bgDimView = findViewById(R.id.bgDimView)
        applyWallpaperDim()
        tvDate = findViewById(R.id.tvDate)
        tvLunar = findViewById(R.id.tvLunar)
        tvWeatherPill = findViewById(R.id.tvWeatherPill)
        tvTimeChip = findViewById(R.id.tvTimeChip)
        btnBell = findViewById(R.id.btnBell)
        btnHomeSettings = findViewById(R.id.btnHomeSettings)
        tvBellBadge = findViewById(R.id.tvBellBadge)
        widgetRow = findViewById(R.id.widgetRow)
        favRow = findViewById(R.id.favRow)
        favHint = findViewById(R.id.favHint)
        grid = findViewById(R.id.appGrid)
        tvEmpty = findViewById(R.id.tvEmpty)
        grid.numColumns = 8
        grid.adapter = gridAdapter
        // FIX 2026-11-01: ô app trong GridView KHÔNG được focusable riêng lẻ —
        // đó là anti-pattern trên TV: focus nhảy lung tung (lúc ôm cả vùng
        // lưới, lúc chui vào ô con) và phím OK không bao giờ tới được
        // onItemClick. Chặn focus đi vào ô con, để GridView tự quản selection:
        // LÊN/XUỐNG/TRÁI/PHẢI di chuyển ô chọn, OK mở app, giữ OK ra menu.
        grid.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        grid.selector = android.graphics.drawable.ColorDrawable(0)
        // FIX 2026-10-31: GridView chặn phím OK giữa (DPAD_CENTER) — view con
        // không nhận được onClick. Phải dùng onItemClick/onItemLongClick của
        // GridView thì remote mới mở được app / giữ để ghim yêu thích.
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val app = gridAdapter.items.getOrNull(pos) ?: return@OnItemClickListener
            if (!openApp(this, app.pkg)) toast("Không mở được ${app.label}")
        }
        grid.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val app = gridAdapter.items.getOrNull(pos) ?: return@OnItemLongClickListener true
            showAppMenu(app)
            true
        }
        // v4.0: bánh răng mở THẲNG cài đặt màn hình chính (qua cổng PIN),
        // để dễ tìm chỗ đặt ảnh nền — không đi vòng qua Cài đặt chung nữa.
        btnHomeSettings.setOnClickListener {
            val i = Intent(this, LockActivity::class.java)
            i.putExtra("mode", "settings")
            i.putExtra("target", "home")
            startActivity(i)
        }
        btnBell.setOnClickListener {
            startActivity(Intent(this, NotificationCenterActivity::class.java))
        }
        // Khung chọn animated: theo dõi mọi thay đổi focus, trượt khung tới
        // view đang focus. Trong suốt, viền trắng — thấy rõ nó chạy theo
        // hướng bấm phím lên/xuống/trái/phải.
        focusFrame = findViewById(R.id.focusFrame)
        val root = findViewById<View>(android.R.id.content)
        root.viewTreeObserver.addOnGlobalFocusChangeListener(
            ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
                // GridView giữ focus ở container — khung phải ôm ô đang chọn
                // thì mới thấy nó trượt theo hướng phím.
                if (newFocus === grid) moveFocusFrame(selectedGridChild(), animate = true)
                else moveFocusFrame(newFocus, animate = true)
                updateGridZoom()
            }
        )
        // Ô chọn trong lưới đổi (phím TRÁI/PHẢI/LÊN/XUỐNG) thì trượt khung theo.
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                if (grid.hasFocus() && v != null) moveFocusFrame(v, animate = true)
                updateGridZoom()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        // GridView cuộn: ô đang chọn di chuyển theo — cập nhật khung.
        grid.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(v: AbsListView?, s: Int) {}
            override fun onScroll(
                v: AbsListView?, first: Int, visible: Int, total: Int
            ) {
                if (currentFocus === grid) moveFocusFrame(selectedGridChild(), animate = false)
            }
        })
    }

    /** Ô đang được chọn trong lưới app (GridView giữ focus ở container,
     *  nên khung animated phải ôm ô này). */
    private fun selectedGridChild(): View? {
        val pos = grid.selectedItemPosition
        if (pos == AdapterView.INVALID_POSITION) return null
        return grid.getChildAt(pos - grid.firstVisiblePosition)
    }

    /** Di chuyển khung chọn tới view đang focus. animate=true thì trượt
     *  mượt (thấy hướng di chuyển), false thì nhảy ngay (dùng khi cuộn). */
    private var suppressFrameJump = false

    private fun moveFocusFrame(v: View?, animate: Boolean) {
        frameAnimator?.cancel()
        if (suppressFrameJump) return
        if (v == null || v === focusFrame || v.visibility != View.VISIBLE) {
            focusFrame.visibility = View.GONE
            return
        }
        // v4.1: view chưa qua layout (mới tạo trong lúc GridView relayout
        // sau notifyDataSetChanged khi di chuyển app) có tọa độ (0,0) —
        // bỏ qua, giữ khung ở chỗ cũ thay vì nhảy nhầm lên góc trên
        // (chỗ đồng hồ) rồi mới nhảy xuống.
        if (!v.isLaidOut) return
        // v4.1: thêm lớp chặn tọa độ (0,0) đáng ngờ — view đã layout từ
        // pass trước nhưng đang được rebind, tọa độ chưa cập nhật.
        val locChk = IntArray(2); v.getLocationOnScreen(locChk)
        if (locChk[0] == 0 && locChk[1] == 0) return
        // Chỉ hiện khung cho view trong màn hình chính (bỏ qua dialog...).
        var p: View? = v
        var inside = false
        while (p != null) {
            if (p.id == android.R.id.content) { inside = true; break }
            p = p.parent as? View
        }
        if (!inside) {
            focusFrame.visibility = View.GONE
            return
        }
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        val rootLoc = IntArray(2)
        findViewById<View>(android.R.id.content).getLocationOnScreen(rootLoc)
        val pad = (8 * resources.displayMetrics.density).toInt()
        val l = loc[0] - rootLoc[0] - pad
        val t = loc[1] - rootLoc[1] - pad
        val r = l + v.width + pad * 2
        val b = t + v.height + pad * 2
        if (focusFrame.visibility != View.VISIBLE || !animate) {
            focusFrame.layout(l, t, r, b)
            focusFrame.visibility = View.VISIBLE
            return
        }
        val sL = focusFrame.left; val sT = focusFrame.top
        val sR = focusFrame.right; val sB = focusFrame.bottom
        if (sL == l && sT == t && sR == r && sB == b) return
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 220
        // v4.0 (user 2026-11-02): giật lại 1 nhịp như CleanTV — vượt
        // nhẹ qua đích rồi nảy về (OvershootInterpolator).
        anim.interpolator = OvershootInterpolator(1.2f)
        anim.addUpdateListener {
            val f = it.animatedValue as Float
            focusFrame.layout(
                (sL + (l - sL) * f).toInt(),
                (sT + (t - sT) * f).toInt(),
                (sR + (r - sR) * f).toInt(),
                (sB + (b - sB) * f).toInt()
            )
        }
        frameAnimator = anim
        anim.start()
    }

    // ---------- Điều hướng remote theo vùng (v4.0, gọn lại 2026-11-01) ----------
    // Quy tắc cố định, dễ đoán: 4 vùng từ trên xuống — 0: thanh trên
    // (chuông + bánh răng), 1: hàng widget, 2: yêu thích, 3: lưới app.
    // Bấm XUỐNG thì xuống vùng CÓ NỘI DUNG gần nhất bên dưới; bấm LÊN thì
    // ngược lại. Vùng trống (tắt hết widget / chưa ghim yêu thích) thì bỏ
    // qua luôn. TRÁI/PHẢI giữ mặc định. Trong lưới app, chỉ khi đang ở hàng
    // đầu thì LÊN mới ra vùng trên — các hàng dưới để GridView tự chuyển ô.

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // v4.0: chế độ di chuyển app — phím điều hướng đổi chỗ,
            // OK lưu, Back hủy (khôi phục thứ tự cũ).
            if (moveModePkg != null) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { moveApp(-1); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { moveApp(1); return true }
                    KeyEvent.KEYCODE_DPAD_UP -> { moveApp(-8); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { moveApp(8); return true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> { endMoveMode(save = true); return true }
                    KeyEvent.KEYCODE_BACK -> { endMoveMode(save = false); return true }
                }
            }
            val f = currentFocus
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val r = regionOf(f)
                    if (r in 0..2) {
                        for (nr in r + 1..3) {
                            if (regionHasFocusable(nr)) { focusRegion(nr); break }
                        }
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    val r = regionOf(f)
                    if (r == 3) {
                        if (grid.selectedItemPosition in 0 until grid.numColumns) {
                            for (nr in 2 downTo 0) {
                                if (regionHasFocusable(nr)) { focusRegion(nr); break }
                            }
                            return true
                        }
                        // Các hàng dưới: để GridView tự chuyển ô chọn lên.
                    } else if (r in 1..2) {
                        for (nr in r - 1 downTo 0) {
                            if (regionHasFocusable(nr)) { focusRegion(nr); break }
                        }
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Vùng chứa view đang focus: 0=thanh trên, 1=widget, 2=yêu thích,
     *  3=lưới app, -1=không rõ (để FocusFinder mặc định xử lý). */
    private fun regionOf(f: View?): Int {
        if (f == btnBell || f == btnHomeSettings) return 0
        if (inRow(f, widgetRow)) return 1
        if (inRow(f, favRow)) return 2
        if (f === grid) return 3
        return -1
    }

    private fun regionHasFocusable(region: Int): Boolean = when (region) {
        0 -> true // chuông + bánh răng luôn có
        1 -> firstChildOf(widgetRow) != null
        2 -> firstChildOf(favRow) != null
        3 -> gridAdapter.count > 0
        else -> false
    }

    private fun focusRegion(region: Int) {
        when (region) {
            0 -> btnBell.requestFocus()
            1 -> focusFirstIn(widgetRow)
            2 -> focusFirstIn(favRow)
            3 -> focusGridAt(0)
        }
    }

    private fun inRow(f: View?, row: ViewGroup): Boolean {
        var v: View? = f
        while (v != null) {
            if (v === row) return true
            v = v.parent as? View
        }
        return false
    }

    private fun firstChildOf(row: ViewGroup): View? {
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i)
            if (c.isFocusable) return c
        }
        return null
    }

    private fun focusFirstIn(row: ViewGroup): Boolean {
        val c = firstChildOf(row) ?: return false
        c.requestFocus()
        return true
    }

    private fun focusGridAt(pos: Int) {
        if (gridAdapter.count == 0) return
        grid.setSelection(pos.coerceIn(0, gridAdapter.count - 1))
        grid.requestFocus()
        // setSelection trước khi layout xong thì ô con chưa có — hẹn một
        // nhịp để khung ôm đúng ô đang chọn.
        grid.post { if (grid.hasFocus()) moveFocusFrame(selectedGridChild(), animate = false) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // v4.1: bấm HOME liên tục khi đang khóa — system gọi onNewIntent
        // thay vì onCreate, phải check khóa ở đây nữa.
        val now = System.currentTimeMillis()
        if (store.isLockedNow(now)) {
            val i = Intent(this, LockActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            i.putExtra("reason", store.lockReason(now))
            i.putExtra("mode", "lock")
            i.putExtra("wholeLock", true)
            LockActivity.showLock(this, i)
            finish()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDate()
        refreshLunar()
        refreshWeather()
        refreshTimeChip()
        refreshBell()
        refreshWidgets()
        refreshApps()
        showWallpaper()
        applyWallpaperDim()
        checkLocked()
        // Lần đầu mở chưa có focus: về đúng ô app lần trước (nếu còn),
        // chưa từng mở thì đặt vào chuông — từ đó bấm XUỐNG đi đúng thứ tự.
        if (currentFocus == null) {
            val last = store.homeLastPkg
            val pos = gridAdapter.items.indexOfFirst { it.pkg == last }
            if (last.isNotEmpty() && pos >= 0) focusGridAt(pos)
            else if (last.isEmpty() || !focusFavCell(last)) btnBell.requestFocus()
        }
        handler.postDelayed(tick, 1000)
        // Làm mới tin nhắn từ cache (GuardService poll nền mỗi 10 phút).
        InboxClient.pollAndCache(store) { refreshWidgets(); refreshBell() }
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        // Nhớ ô app đang chọn — bấm Home từ app khác về thì về đúng chỗ.
        currentSelectedPkg()?.let { store.homeLastPkg = it }
        super.onPause()
    }

    private fun checkLocked() {
        val now = System.currentTimeMillis()
        if (store.isLockedNow(now)) {
            val i = Intent(this, LockActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            i.putExtra("reason", store.lockReason(now))
            i.putExtra("mode", "lock")
            i.putExtra("wholeLock", true)
            LockActivity.showLock(this, i)
        }
    }

    private fun refreshDate() {
        val s = SimpleDateFormat("EEEE, d/M/yyyy", Locale("vi")).format(Date())
        tvDate.text = s.replaceFirstChar { it.uppercase() }
    }

    private fun refreshLunar() {
        tvLunar.text = try { LunarCalendar.widgetLine() } catch (_: Exception) { "" }
    }

    private fun refreshWeather() {
        val show = { r: WeatherFetcher.Result? ->
            tvWeatherPill.text = if (r != null)
                "${r.tempC.roundToInt()}° ${r.desc}" else "--°"
        }
        show(WeatherFetcher.cached(store))
        if (WeatherFetcher.cacheAgeMs(store) > WeatherFetcher.STALE_MS) {
            WeatherFetcher.fetch(store) { res -> if (res != null) show(res) }
        }
    }

    private fun refreshBell() {
        val n = store.inboxUnread
        tvBellBadge.visibility = if (n > 0) View.VISIBLE else View.GONE
        if (n > 0) tvBellBadge.text = if (n > 99) "99+" else n.toString()
    }

    /** Chip giờ còn lại — bên trái pill thời tiết, cùng chiều cao.
     *  Xanh "Còn X giờ Y phút" khi đang mở, đỏ "Đã khóa" khi TV khóa.
     *  Ẩn khi tắt công tắc hoặc không có giới hạn giờ. */
    private fun showTimeChip(): Boolean =
        getSharedPreferences("tvlock", MODE_PRIVATE).getBoolean("showTimeChip", true)

    private fun refreshTimeChip() {
        if (!showTimeChip()) {
            tvTimeChip.visibility = View.GONE
            return
        }
        val now = System.currentTimeMillis()
        val bg = tvTimeChip.background as android.graphics.drawable.GradientDrawable
        if (store.isLockedNow(now)) {
            tvTimeChip.text = "Đã khóa"
            bg.setColor(getColor(R.color.locked))
            tvTimeChip.setTextColor(getColor(R.color.on_locked))
            tvTimeChip.visibility = View.VISIBLE
            return
        }
        val secs = store.secondsUntilDailyLimit(now)
        if (secs == Int.MAX_VALUE || secs < 0) {
            tvTimeChip.visibility = View.GONE
            return
        }
        // Làm tròn LÊN theo phút (vd còn 6 giây → "Còn 1 phút") cho khớp
        // với web (remainingMin). Trước đây chia nguyên nên còn dưới 1
        // phút thì hiện "Còn 0 phút" gây hiểu lầm là hết giờ.
        val totalMin = (secs + 59) / 60
        val h = totalMin / 60
        val m = totalMin % 60
        tvTimeChip.text = if (h > 0) "Còn $h giờ $m phút" else "Còn $m phút"
        bg.setColor(getColor(R.color.open))
        tvTimeChip.setTextColor(getColor(R.color.on_open))
        tvTimeChip.visibility = View.VISIBLE
    }

    // ---------- Widget ----------

    private fun refreshWidgets() {
        widgetRow.removeAllViews()
        if (store.widgetTinker) widgetRow.addView(makeWidget(
            "TINKER",
            tinkerMain(), tinkerSub()
        ) { startActivity(Intent(this, NotificationCenterActivity::class.java)) })
        if (store.widgetFare) widgetRow.addView(makeWidget(
            "VÉ MÁY BAY",
            fareMain(), fareSub()
        ) { startActivity(Intent(this, NotificationCenterActivity::class.java)) })
        if (store.widgetCountdown) widgetRow.addView(makeWidget(
            "ĐẾM NGƯỢC",
            "${store.countdownLabel} · ${countdownText()}",
            ""
        ) { })
        if (store.widgetFamily) widgetRow.addView(makeWidget(
            "GIA ĐÌNH",
            familyMain(), familySub()
        ) { })
        if (store.widgetCamera) widgetRow.addView(makeWidget(
            "CAMERA",
            "Sắp có",
            "Chờ mật khẩu camera"
        ) { toast("Camera sẽ có sau khi anh tìm lại mật khẩu") })
    }

    private fun makeWidget(title: String, main: String, sub: String, onClick: () -> Unit): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_widget, widgetRow, false)
        v.findViewById<TextView>(R.id.tvWidgetTitle).text = title
        v.findViewById<TextView>(R.id.tvWidgetMain).text = main
        val tvSub = v.findViewById<TextView>(R.id.tvWidgetSub)
        if (sub.isEmpty()) tvSub.visibility = View.GONE else tvSub.text = sub
        v.setOnClickListener { onClick() }
        return v
    }

    private fun tinkerMain(): String {
        val msgs = InboxClient.cachedMessages(store)
        return msgs.firstOrNull()?.title ?: "Chưa có thông báo"
    }

    private fun tinkerSub(): String {
        val msgs = InboxClient.cachedMessages(store)
        return msgs.firstOrNull()?.body?.take(60) ?: "Em sẽ báo vé, khuyến mãi, nhắc rằm ở đây"
    }

    private fun fareMain(): String {
        val fare = InboxClient.cachedMessages(store).firstOrNull { it.type == "fare" }
        return fare?.title ?: "HAN – DAD · đang săn"
    }

    private fun fareSub(): String {
        val fare = InboxClient.cachedMessages(store).firstOrNull { it.type == "fare" }
        return fare?.body?.take(60) ?: "Báo khi giá và giờ bay đều đẹp"
    }

    private fun countdownText(): String {
        val d = store.countdownDays()
        return when {
            d > 0 -> "$d ngày nữa"
            d == 0L -> "Hôm nay!"
            else -> "Đã qua ${-d} ngày"
        }
    }

    private fun familyMain(): String {
        val fam = InboxClient.cachedFamily(store)
        if (fam.isEmpty()) return "Chưa có trạng thái"
        return fam.take(2).joinToString(" · ") { "${it.who}: ${it.text}" }
    }

    private fun familySub(): String {
        val fam = InboxClient.cachedFamily(store)
        if (fam.isEmpty()) return "Bố/mẹ cập nhật từ điện thoại"
        return fam.take(2).joinToString(" · ") {
            if (it.agoMin < 0) "" else "${it.agoMin} phút trước"
        }.trim().trim('·').trim()
    }

    // ---------- App ----------

    private fun refreshApps() {
        // v4.1: nhớ ô đang chọn TRƯỚC khi dựng lại danh sách — để sau khi
        // ẩn/di chuyển app, bấm Home về, focus về đúng chỗ thay vì nhảy
        // về đầu hoặc mất hẳn.
        val gridWasFocused = grid.hasFocus()
        val favPkg = focusedFavPkg()
        val keepPkg = (if (gridWasFocused)
            gridAdapter.items.getOrNull(grid.selectedItemPosition)?.pkg else favPkg)
            ?: store.homeLastPkg.takeIf { it.isNotEmpty() }
        val keepPos = grid.selectedItemPosition.coerceAtLeast(0)

        val all = loadAllApps(packageManager)
        val hidden = store.homeHidden()
        // v4.0: thứ tự tùy chỉnh (di chuyển app) — app chưa có trong
        // danh sách thì xếp cuối theo tên.
        val orderIdx = store.homeAppOrder()
            .withIndex().associate { it.value to it.index }
        val visible = all.filter { !hidden.contains(it.pkg) }
            .sortedWith(compareBy({ orderIdx[it.pkg] ?: Int.MAX_VALUE }, { it.label }))
        val favPkgs = store.homeFavorites().filter { fp -> visible.any { it.pkg == fp } }.take(7)
        favRow.removeAllViews()
        favHint.visibility = if (favPkgs.isEmpty()) View.VISIBLE else View.GONE
        for (fp in favPkgs) favRow.addView(makeAppCell(visible.first { it.pkg == fp }))
        if (visible.isEmpty()) {
            grid.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            grid.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            gridAdapter.items = visible
            gridAdapter.notifyDataSetChanged()
        }
        // Khôi phục focus đã nhớ — CHỈ khi trước đó focus đang ở vùng app
        // (lưới/yêu thích), để không giật focus khi đang ở chuông/widget.
        if (gridWasFocused || favPkg != null) {
            var restored = false
            if (!gridWasFocused && keepPkg != null) restored = focusFavCell(keepPkg)
            if (!restored && keepPkg != null) {
                val pos = gridAdapter.items.indexOfFirst { it.pkg == keepPkg }
                if (pos >= 0) {
                    if (gridWasFocused) grid.setSelection(pos) else focusGridAt(pos)
                    restored = true
                }
            }
            if (!restored && gridAdapter.count > 0) {
                // App vừa bị ẩn/gỡ: đáp xuống lưới ở vị trí gần nhất.
                val pos = keepPos.coerceIn(0, gridAdapter.count - 1)
                if (gridWasFocused) grid.setSelection(pos) else focusGridAt(pos)
            }
        }
    }

    /** pkg của ô yêu thích đang focus, null = focus không ở hàng yêu thích. */
    private fun focusedFavPkg(): String? {
        for (i in 0 until favRow.childCount) {
            val c = favRow.getChildAt(i)
            if (c.hasFocus()) return c.tag as? String
        }
        return null
    }

    /** Đưa focus về ô yêu thích của pkg, false = không còn ô đó. */
    private fun focusFavCell(pkg: String): Boolean {
        for (i in 0 until favRow.childCount) {
            val c = favRow.getChildAt(i)
            if (c.tag == pkg) { c.requestFocus(); return true }
        }
        return false
    }

    /** pkg của ô app đang chọn/focus (lưới hoặc yêu thích) — để nhớ vị trí. */
    private fun currentSelectedPkg(): String? {
        if (grid.hasFocus()) return gridAdapter.items.getOrNull(grid.selectedItemPosition)?.pkg
        return focusedFavPkg()
    }

    private fun makeAppCell(app: TvApp): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_app, favRow, false)
        v.tag = app.pkg
        bindAppCell(v, app)
        return v
    }

    private fun bindAppCell(v: View, app: TvApp) {
        val icon = v.findViewById<RoundedIconView>(R.id.appIcon)
        bindAppIcon(icon, app)
        v.findViewById<TextView>(R.id.appLabel).text = app.label
        v.setOnClickListener {
            if (!openApp(this, app.pkg)) toast("Không mở được ${app.label}")
        }
        v.setOnLongClickListener {
            showAppMenu(app)
            true
        }
    }

    private fun showAppMenu(app: TvApp) {
        val items = mutableListOf<String>()
        if (store.isHomeFavorite(app.pkg)) items.add("Bỏ ghim khỏi yêu thích")
        else items.add("Ghim vào yêu thích")
        // v4.0 (user 2026-11-02): di chuyển tự do + thông tin app
        // (như launcher gốc).
        items.add("Di chuyển")
        items.add("Thông tin ứng dụng")
        items.add("Ẩn ứng dụng này")
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Ghim vào yêu thích" -> {
                        store.addHomeFavorite(app.pkg)
                        refreshApps()
                        toast("Đã ghim ${app.label} vào yêu thích")
                    }
                    "Bỏ ghim khỏi yêu thích" -> {
                        store.removeHomeFavorite(app.pkg)
                        refreshApps()
                        toast("Đã bỏ ghim ${app.label}")
                    }
                    "Di chuyển" -> startMoveMode(app)
                    "Thông tin ứng dụng" -> openAppInfo(app)
                    "Ẩn ứng dụng này" -> {
                        store.setHomeHidden(app.pkg, true)
                        refreshApps()
                        toast("Đã ẩn ${app.label} (xem lại trong Cài đặt → Màn hình chính → Ứng dụng đã ẩn)")
                    }
                }
            }
            .show()
    }

    /** v4.0: mở trang Thông tin ứng dụng của hệ thống (xem thông tin,
     *  gỡ cài đặt, xóa dữ liệu/bộ nhớ). */
    private fun openAppInfo(app: TvApp) {
        try {
            val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = android.net.Uri.parse("package:${app.pkg}")
            startActivity(i)
        } catch (_: Exception) {
            toast("Không mở được thông tin ${app.label}")
        }
    }

    // ---------- v4.0: chế độ di chuyển app (user 2026-11-02) ----------
    /** pkg đang di chuyển, null = không ở chế độ di chuyển. */
    private var moveModePkg: String? = null
    /** Thứ tự trước khi di chuyển — để hủy (Back) thì khôi phục. */
    private var moveModeOrigOrder: List<String> = emptyList()

    private fun startMoveMode(app: TvApp) {
        // Đảm bảo danh sách thứ tự đầy đủ trước khi di chuyển.
        val current = gridAdapter.items.map { it.pkg }
        val stored = store.homeAppOrder()
        if (stored.size != current.size || !stored.containsAll(current)) {
            store.setHomeAppOrder(current)
        }
        moveModeOrigOrder = store.homeAppOrder()
        moveModePkg = app.pkg
        // Đưa focus về lưới đúng ô app đang di chuyển.
        val pos = current.indexOf(app.pkg)
        if (pos >= 0) {
            grid.requestFocus()
            grid.setSelection(pos)
        }
        // Hiện lâu hơn một nhịp để đọc kịp hướng dẫn.
        Toast.makeText(this, "Di chuyển ${app.label}: ←→↑↓ để đặt vị trí, OK để xong, Back để hủy", Toast.LENGTH_LONG).show()
    }

    private fun endMoveMode(save: Boolean) {
        if (!save) {
            store.setHomeAppOrder(moveModeOrigOrder)
            refreshApps()
            // Trả selection về ô app vừa di chuyển.
            val pos = gridAdapter.items.indexOfFirst { it.pkg == moveModePkg }
            if (pos >= 0) grid.setSelection(pos)
        }
        moveModePkg = null
        toast(if (save) "Đã lưu vị trí" else "Đã hủy di chuyển")
    }

    /** Đổi chỗ app đang di chuyển với ô kề theo hướng delta
     *  (∓1 = trái/phải, ∓8 = lên/xuống). */
    private fun moveApp(delta: Int) {
        val pkg = moveModePkg ?: return
        val items = gridAdapter.items.toMutableList()
        val from = items.indexOfFirst { it.pkg == pkg }
        if (from == -1) return
        val cols = 8
        var to = from + delta
        // Không tràn hàng khi đi ngang.
        if (delta == -1 && from % cols == 0) return
        if (delta == 1 && from % cols == cols - 1) return
        if (to !in items.indices) return
        java.util.Collections.swap(items, from, to)
        store.setHomeAppOrder(items.map { it.pkg })
        gridAdapter.items = items
        // v4.1: chặn khung nhảy lung tung trong lúc GridView relayout —
        // chỉ đặt lại khung sau khi layout xong, với tọa độ đúng.
        suppressFrameJump = true
        gridAdapter.notifyDataSetChanged()
        grid.setSelection(to)
        grid.post {
            suppressFrameJump = false
            if (moveModePkg != null) {
                moveFocusFrame(selectedGridChild(), animate = true)
            }
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        var items: List<TvApp> = emptyList()
        override fun getCount(): Int = items.size
        override fun getItem(p: Int): Any = items[p]
        override fun getItemId(p: Int): Long = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val v = convert
                ?: LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            // Ô trong GridView KHÔNG được focusable/clickable riêng — để
            // GridView quản selection thì OK mới tới onItemClick (xem onCreate).
            // Hàng Yêu thích (makeAppCell) vẫn dùng bindAppCell như cũ.
            v.isFocusable = false
            v.isFocusableInTouchMode = false
            v.isClickable = false
            v.isLongClickable = false
            v.setOnClickListener(null)
            v.setOnLongClickListener(null)
            v.stateListAnimator = null
            val app = items[p]
            val icon = v.findViewById<RoundedIconView>(R.id.appIcon)
            bindAppIcon(icon, app)
            v.findViewById<TextView>(R.id.appLabel).text = app.label
            // v4.0: view tái chế thì trả scale về 1 — zoom chỉ áp cho ô
            // đang chọn (updateGridZoom).
            if (v.scaleX != 1f || v.scaleY != 1f) {
                v.scaleX = 1f; v.scaleY = 1f
            }
            return v
        }
    }

    // ---------- v4.0: zoom ô app đang chọn trong lưới ----------
    /** Ô lưới đang zoom, null = chưa có. */
    private var zoomedGridChild: View? = null

    /** Zoom ô đang chọn lên 1.06 như hàng yêu thích (user 2026-11-02)
     *  — chỉ áp cho app, không zoom widget/nút. */
    private fun updateGridZoom() {
        val sel = if (grid.hasFocus()) selectedGridChild() else null
        val old = zoomedGridChild
        if (old === sel) return
        old?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)?.start()
        zoomedGridChild = sel
        sel?.animate()?.scaleX(1.06f)?.scaleY(1.06f)?.setDuration(100)?.start()
    }

    // ---------- Ảnh nền slideshow ----------

    /** v3.0 dùng 1 file home_bg.jpg — chuyển vào thư mục slideshow. */
    private fun migrateSingleBg() {
        try {
            val single = java.io.File(filesDir, "home_bg.jpg")
            val dir = store.wallpaperDir()
            if (single.exists() && store.wallpaperFiles().isEmpty()) {
                dir.mkdirs()
                single.copyTo(java.io.File(dir, "home_bg.jpg"), overwrite = true)
                single.delete()
            }
        } catch (_: Exception) { }
    }

    private fun showWallpaper() {
        val n = wallpaperCount()
        if (n == 0) {
            bgView.visibility = View.GONE
            return
        }
        wallpaperIdx %= n
        val bmp = decodeWallpaper(wallpaperIdx)
        if (bmp != null) {
            bgView.setImageBitmap(bmp)
            bgView.visibility = View.VISIBLE
            wallpaperLastSwitch = System.currentTimeMillis()
        } else bgView.visibility = View.GONE
    }

    /** Độ tối ảnh nền 0-60% (mặc định 30%) — đọc từ SharedPreferences
     *  "tvlock" (chung cơ chế lưu cài đặt, không sửa Store). */
    private fun wallpaperDimPct(): Int =
        getSharedPreferences("tvlock", MODE_PRIVATE).getInt("wallpaperDimPct", 30)

    private fun applyWallpaperDim() {
        bgDimView.alpha = wallpaperDimPct().coerceIn(0, 60) / 100f
    }

    private fun rotateWallpaperIfDue() {
        val intervalMs = store.wallpaperIntervalMin * 60_000L
        if (intervalMs <= 0) return
        val n = wallpaperCount()
        if (n < 2) return
        if (System.currentTimeMillis() - wallpaperLastSwitch >= intervalMs) {
            wallpaperIdx = (wallpaperIdx + 1) % n
            showWallpaper()
        }
    }

    /** Ảnh nền mặc định kèm app (assets/wallpapers) — 10 ảnh phong cảnh. */
    private fun defaultWpNames(): List<String> = try {
        assets.list("wallpapers")?.filter { it.endsWith(".jpg") }?.sorted() ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun wallpaperCount(): Int =
        if (store.wallpaperSource == "default") defaultWpNames().size
        else store.wallpaperFiles().size

    private fun decodeWallpaper(idx: Int): Bitmap? {
        return try {
            if (store.wallpaperSource == "default") {
                val names = defaultWpNames()
                if (names.isEmpty()) return null
                assets.open("wallpapers/" + names[idx % names.size]).use {
                    BitmapFactory.decodeStream(it)
                }
            } else {
                val files = store.wallpaperFiles()
                if (files.isEmpty()) return null
                BitmapFactory.decodeFile(files[idx % files.size].absolutePath)
            }
        } catch (_: Exception) { null }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
