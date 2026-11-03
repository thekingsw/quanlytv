package vn.tvlock

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * v2: cài đặt màn hình chính — tách thành 6 section (mỗi section là một
 * mục trong nhóm "Màn hình chính"), dùng chung Activity với extra "section".
 * Mỗi section dựng pane riêng bằng ListRows/ToggleRows.
 */
class HomeSettingsActivity : Activity() {
    private var settingsFrame: SettingsFrame.Handle? = null
    private lateinit var store: Store
    private var section: String = "wallpaper"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!intent.getBooleanExtra("pinVerified", false)) {
            val i = Intent(this, LockActivity::class.java)
            i.putExtra("mode", "settings")
            i.putExtra("target", "home")
            startActivity(i)
            finish()
            return
        }
        section = intent.getStringExtra("section") ?: "wallpaper"
        setContentView(R.layout.settings_frame)
        store = Store(this)
        val itemId = when (section) {
            "widgets" -> "home_widgets"
            "countdown" -> "home_countdown"
            "push" -> "home_push"
            "style" -> "home_style"
            "hidden" -> "home_hidden"
            else -> "home_wallpaper"
        }
        // recreate() luôn xuất phát từ pane (bấm đổi setting) → giữ focus ở pane.
        val startInPane = savedInstanceState?.getBoolean("startInPane", false) ?: false
        settingsFrame = SettingsFrame.bind(this, itemId, startInPane)
        when (section) {
            "widgets" -> buildWidgets(settingsFrame!!)
            "countdown" -> buildCountdown(settingsFrame!!)
            "push" -> buildPush(settingsFrame!!)
            "style" -> buildStyle(settingsFrame!!)
            "hidden" -> buildHidden(settingsFrame!!)
            else -> buildWallpaper(settingsFrame!!)
        }
        if (startInPane) settingsFrame?.focusPane()
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("startInPane", true)
    }

    /** Dialog chọn 1 trong nhiều lựa chọn — bấm vào item là áp dụng ngay,
     *  không cần bấm Lưu. Dùng cho mọi setting trừ switcher (bật/tắt). */
    private fun showOptionsDialog(
        title: String,
        options: List<String>,
        current: Int,
        row: View,
        onPick: (Int) -> String
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), current) { dialog, which ->
                val detail = onPick(which)
                ListRows.setDetail(row, detail)
                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private var needRefresh = false

    /** "7:05" — hiển thị giờ:phút. */
    private fun fmtTime(h: Int, m: Int): String =
        "%d:%02d".format(h, m)

    /** Dialog chọn khung giờ popup: NumberPicker cho giờ bắt đầu + kết thúc.
     *  Mũi tên lên/xuống bấm để +/−1, giữ để chạy nhanh, bấm vào số để gõ. */
    private fun showPopupHoursDialog(row: View) {
        val v = layoutInflater.inflate(R.layout.dialog_popup_hours, null)
        val npSH = v.findViewById<android.widget.NumberPicker>(R.id.npStartHour)
        val npSM = v.findViewById<android.widget.NumberPicker>(R.id.npStartMin)
        val npEH = v.findViewById<android.widget.NumberPicker>(R.id.npEndHour)
        val npEM = v.findViewById<android.widget.NumberPicker>(R.id.npEndMin)
        for ((np, max) in listOf(npSH to 23, npSM to 59, npEH to 23, npEM to 59)) {
            np.minValue = 0
            np.maxValue = max
            np.wrapSelectorWheel = true
        }
        npSH.value = store.pushStartHour
        npSM.value = store.pushStartMinute
        npEH.value = store.pushEndHour.coerceIn(0, 23)
        npEM.value = store.pushEndMinute
        AlertDialog.Builder(this)
            .setTitle("Khung giờ popup")
            .setView(v)
            .setPositiveButton("Lưu") { _, _ ->
                store.pushStartHour = npSH.value
                store.pushStartMinute = npSM.value
                store.pushEndHour = npEH.value
                store.pushEndMinute = npEM.value
                ListRows.setDetail(row,
                    "${fmtTime(npSH.value, npSM.value)} – ${fmtTime(npEH.value, npEM.value)}")
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Chỉ dựng lại khi quay về từ màn khác (vd HiddenApps) và có flag.
        if (needRefresh) {
            needRefresh = false
            recreate()
        }
    }

    // ---------- Ảnh nền ----------
    private fun buildWallpaper(frame: SettingsFrame.Handle) {
        layoutInflater.inflate(R.layout.pane_home_wallpaper, settingsFrame!!.pane, true)
        refreshWallpaper()
        val rows = findViewById<LinearLayout>(R.id.homeBgRows)
        val r1 = ListRows.add(this, rows, "Thêm ảnh", "từ điện thoại qua Cài TV", onClick = {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
            try { startActivityForResult(i, 3001) } catch (_: Exception) {
                Toast.makeText(this, "TV không có trình chọn ảnh", Toast.LENGTH_SHORT).show()
            }
        })
        lateinit var r2: View
        r2 = ListRows.add(this, rows, "Nguồn ảnh nền", wpSourceName(), onClick = {
            val opts = listOf("Ảnh mặc định", "Ảnh của tôi")
            val cur = if (store.wallpaperSource == "default") 0 else 1
            showOptionsDialog("Nguồn ảnh nền", opts, cur, r2) { which ->
                store.wallpaperSource = if (which == 0) "default" else "custom"
                refreshWallpaper()
                wpSourceName()
            }
        })
        lateinit var r3: View
        r3 = ListRows.add(this, rows, "Đổi ảnh mỗi", wpIntervalName(), onClick = {
            val vals = listOf(0, 15, 30, 60)
            val opts = listOf("Không tự đổi", "15 phút", "30 phút", "1 giờ")
            val cur = vals.indexOf(store.wallpaperIntervalMin).let { if (it < 0) 0 else it }
            showOptionsDialog("Đổi ảnh nền mỗi", opts, cur, r3) { which ->
                store.wallpaperIntervalMin = vals[which]
                wpIntervalName()
            }
        })
        val r4 = ListRows.add(this, rows, "Xóa hết ảnh", "ảnh của tôi", onClick = {
            AlertDialog.Builder(this)
                .setTitle("Xóa hết ảnh nền?")
                .setMessage("Xóa ${store.wallpaperFiles().size} ảnh của tôi?")
                .setPositiveButton("Xóa") { _, _ ->
                    store.wallpaperFiles().forEach { try { it.delete() } catch (_: Exception) { } }
                    store.deleteHomeBg()
                    Toast.makeText(this, "Đã xóa hết ảnh nền", Toast.LENGTH_SHORT).show()
                    refreshWallpaper()
                }
                .setNegativeButton("Hủy", null)
                .show()
        })
        lateinit var r5: View
        r5 = ListRows.add(this, rows, "Độ tối ảnh nền", "${wallpaperDimPct()}%", onClick = {
            val vals = (0..6).map { it * 10 }
            val opts = vals.map { "$it%" }
            val cur = vals.indexOf(wallpaperDimPct()).let { if (it < 0) 3 else it }
            showOptionsDialog("Độ tối ảnh nền", opts, cur, r5) { which ->
                val nv = vals[which]
                getSharedPreferences("tvlock", MODE_PRIVATE).edit()
                    .putInt("wallpaperDimPct", nv).apply()
                "$nv%"
            }
        })
        ListRows.linkFocus(listOf(r1, r2, r3, r4, r5), null)
        settingsFrame!!.onPaneReady()
    }

    private fun wallpaperDimPct(): Int =
        getSharedPreferences("tvlock", MODE_PRIVATE).getInt("wallpaperDimPct", 30)

    private fun refreshWallpaper() {
        val n = store.wallpaperFiles().size
        findViewById<TextView>(R.id.tvHomeBgState).text =
            if (store.wallpaperSource == "default") "Nguồn: ảnh mặc định (10 ảnh phong cảnh)"
            else if (n > 0) "Nguồn: ảnh của tôi ($n ảnh)"
            else "Nguồn: ảnh của tôi (chưa có ảnh nào)"
    }

    private fun wpSourceName() =
        if (store.wallpaperSource == "default") "Ảnh mặc định" else "Ảnh của tôi"

    private fun wpIntervalName() =
        if (store.wallpaperIntervalMin == 0) "Tắt"
        else "${store.wallpaperIntervalMin} phút"

    /** Tên kiểu icon — "circle" cũ được coi như "rounded" (di chuyển). */
    private fun iconStyleName(): String =
        if (store.iconStyle == "rounded" || store.iconStyle == "circle") "Bo tròn" else "Mặc định"

    // ---------- Widget ----------
    private fun buildWidgets(frame: SettingsFrame.Handle) {
        layoutInflater.inflate(R.layout.pane_home_widgets, settingsFrame!!.pane, true)
        val rows = findViewById<LinearLayout>(R.id.homeWidgetRows)
        val defs = listOf(
            "Tinker" to store.widgetTinker,
            "Vé máy bay" to store.widgetFare,
            "Đếm ngược" to store.widgetCountdown,
            "Gia đình" to store.widgetFamily,
            "Camera" to store.widgetCamera
        )
        val views = defs.map { (name, on) ->
            ToggleRows.add(this, rows, "Widget $name", "", on) { nv ->
                when (name) {
                    "Tinker" -> store.widgetTinker = nv
                    "Vé máy bay" -> store.widgetFare = nv
                    "Đếm ngược" -> store.widgetCountdown = nv
                    "Gia đình" -> store.widgetFamily = nv
                    "Camera" -> store.widgetCamera = nv
                }
            }
        }
        ListRows.linkFocus(views, null)
        settingsFrame!!.onPaneReady()
    }

    // ---------- Đếm ngược ----------
    private fun buildCountdown(frame: SettingsFrame.Handle) {
        layoutInflater.inflate(R.layout.pane_home_countdown, settingsFrame!!.pane, true)
        val rows = findViewById<LinearLayout>(R.id.homeCountdownRows)
        val cur = if (store.countdownDate.isEmpty()) "Tắt"
            else "${store.countdownLabel} ${store.countdownDate}"
        lateinit var r: View
        r = ListRows.add(this, rows, "Mốc đếm ngược", cur, onClick = {
            val opts = listOf("Tắt", "Đà Nẵng 1/6/2027")
            val curIdx = if (store.countdownDate == "2027-06-01") 1 else 0
            showOptionsDialog("Mốc đếm ngược", opts, curIdx, r) { which ->
                if (which == 1) {
                    store.countdownDate = "2027-06-01"; store.countdownLabel = "Đà Nẵng"
                } else {
                    store.countdownDate = ""; store.countdownLabel = ""
                }
                if (store.countdownDate.isEmpty()) "Tắt"
                else "${store.countdownLabel} ${store.countdownDate}"
            }
        })
        ListRows.linkFocus(listOf(r), null)
        settingsFrame!!.onPaneReady()
    }

    // ---------- Thông báo đẩy ----------
    private fun buildPush(frame: SettingsFrame.Handle) {
        layoutInflater.inflate(R.layout.pane_home_push, settingsFrame!!.pane, true)
        val rows = findViewById<LinearLayout>(R.id.homePushRows)
        lateinit var r1: View
        r1 = ListRows.add(this, rows, "Khung giờ popup",
            "${fmtTime(store.pushStartHour, store.pushStartMinute)} – ${fmtTime(store.pushEndHour, store.pushEndMinute)}",
            onClick = { showPopupHoursDialog(r1) })
        lateinit var r2: View
        r2 = ListRows.add(this, rows, "Chế độ popup", store.popupModeName(), onClick = {
            val vals = listOf("always", "home", "silent")
            val opts = listOf("Luôn hiện", "Chỉ ở màn hình chính", "Im lặng")
            val cur = vals.indexOf(store.popupMode).let { if (it < 0) 0 else it }
            showOptionsDialog("Chế độ popup", opts, cur, r2) { which ->
                store.popupMode = vals[which]
                store.popupModeName()
            }
        })
        ListRows.linkFocus(listOf(r1, r2), null)
        settingsFrame!!.onPaneReady()
    }

    // ---------- Kiểu icon & thời tiết ----------
    private fun buildStyle(frame: SettingsFrame.Handle) {
        layoutInflater.inflate(R.layout.pane_home_style, settingsFrame!!.pane, true)
        val rows = findViewById<LinearLayout>(R.id.homeStyleRows)
        lateinit var r1: View
        r1 = ListRows.add(this, rows, "Kiểu icon ứng dụng", iconStyleName(), onClick = {
            val vals = listOf("rounded", "default")
            val opts = listOf("Bo tròn", "Mặc định")
            val cur = vals.indexOf(store.iconStyle).let { if (it < 0) 0 else it }
            showOptionsDialog("Kiểu icon ứng dụng", opts, cur, r1) { which ->
                store.iconStyle = vals[which]
                iconStyleName()
            }
        })
        lateinit var r2: View
        r2 = ListRows.add(this, rows, "Thành phố thời tiết", store.weatherCityName(), onClick = {
            val vals = listOf("hanoi", "danang", "hcmc")
            val opts = listOf("Hà Nội", "Đà Nẵng", "TP.HCM")
            val cur = vals.indexOf(store.weatherCity).let { if (it < 0) 0 else it }
            showOptionsDialog("Thành phố thời tiết", opts, cur, r2) { which ->
                store.weatherCity = vals[which]
                store.weatherCacheJson = ""
                store.weatherCityName()
            }
        })
        val prefs = getSharedPreferences("tvlock", MODE_PRIVATE)
        val r3 = ToggleRows.add(
            this, rows, "Hiện giờ còn lại", "chip bên trái pill thời tiết",
            prefs.getBoolean("showTimeChip", true)
        ) { nv ->
            prefs.edit().putBoolean("showTimeChip", nv).apply()
        }
        ListRows.linkFocus(listOf(r1, r2, r3), null)
        settingsFrame!!.onPaneReady()
    }

    // ---------- Ứng dụng đã ẩn ----------
    private fun buildHidden(frame: SettingsFrame.Handle) {
        layoutInflater.inflate(R.layout.pane_home_hidden, settingsFrame!!.pane, true)
        val rows = findViewById<LinearLayout>(R.id.homeHiddenRows)
        val r = ListRows.add(this, rows, "Xem / hiện lại app đã ẩn", "", onClick = {
            needRefresh = true
            val i = Intent(this, HiddenAppsActivity::class.java)
            i.putExtra("pinVerified", true)
            startActivity(i)
        })
        ListRows.linkFocus(listOf(r), null)
        settingsFrame!!.onPaneReady()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 3001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
                val dir = store.wallpaperDir()
                dir.mkdirs()
                val f = java.io.File(dir, "wp_${System.currentTimeMillis()}.jpg")
                if (store.saveHomeBgBytes(bytes)) {
                    val tmp = store.homeBgFile()
                    if (tmp.exists()) { tmp.copyTo(f, overwrite = true); tmp.delete() }
                    Toast.makeText(this, "Đã thêm ảnh nền (${store.wallpaperFiles().size} ảnh)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Ảnh không đọc được", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_SHORT).show()
            }
            refreshWallpaper()
        }
    }

    /** Back phân cấp: pane → rail → tab → thoát. */
    override fun onBackPressed() {
        if (settingsFrame?.onBackPressed() == true) return
        super.onBackPressed()
    }
}
