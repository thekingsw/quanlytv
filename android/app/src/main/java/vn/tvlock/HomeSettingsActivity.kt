package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** v4.0: cài đặt màn hình chính — ảnh nền slideshow, widget, đếm ngược,
 *  khung giờ popup. Vào qua rail "Màn hình chính" (PIN gate v2.5). */
class HomeSettingsActivity : Activity() {
    private lateinit var store: Store
    private lateinit var tvBgState: TextView
    private lateinit var btnIconStyle: Button
    private lateinit var btnCity: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v4.0: mở trực tiếp từ bánh răng màn hình chính thì phải qua PIN
        // khóa TV (như SettingsActivity) — trẻ con không được mò vào đây.
        // Mở từ rail Cài đặt (đã qua PIN) thì kèm pinVerified=true.
        if (!intent.getBooleanExtra("pinVerified", false)) {
            val i = Intent(this, LockActivity::class.java)
            i.putExtra("mode", "settings")
            i.putExtra("target", "home")
            startActivity(i)
            finish()
            return
        }
        setContentView(R.layout.activity_home_settings)
        store = Store(this)
        SettingsRail.build(this, findViewById<LinearLayout>(R.id.railContainer), "home_screen")
        tvBgState = findViewById(R.id.tvHomeBgState)
        btnIconStyle = findViewById(R.id.btnIconStyle)
        btnCity = findViewById(R.id.btnCity)

        // v4.0: thêm ảnh vào slideshow (thư mục home_wallpapers).
        findViewById<Button>(R.id.btnPickHomeBg).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
            try { startActivityForResult(i, 3001) } catch (_: Exception) {
                Toast.makeText(this, "TV không có trình chọn ảnh", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnRemoveHomeBg).setOnClickListener {
            store.wallpaperFiles().forEach { try { it.delete() } catch (_: Exception) { } }
            store.deleteHomeBg()
            Toast.makeText(this, "Đã xóa hết ảnh nền", Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.btnWpInterval).setOnClickListener {
            // 0 (tắt) → 15 → 30 → 60 → 0...
            store.wallpaperIntervalMin = when (store.wallpaperIntervalMin) {
                0 -> 15
                15 -> 30
                30 -> 60
                else -> 0
            }
            refresh()
        }
        // Nguồn ảnh nền: ảnh mặc định kèm app <-> ảnh anh tải từ điện thoại.
        findViewById<Button>(R.id.btnWpSource).setOnClickListener {
            store.wallpaperSource = if (store.wallpaperSource == "default") "custom" else "default"
            Toast.makeText(this, "Nguồn ảnh nền: ${wpSourceName()}", Toast.LENGTH_SHORT).show()
            refresh()
        }
        // Widget bật/tắt.
        findViewById<Button>(R.id.btnWidgetTinker).setOnClickListener {
            store.widgetTinker = !store.widgetTinker; refresh()
        }
        findViewById<Button>(R.id.btnWidgetFare).setOnClickListener {
            store.widgetFare = !store.widgetFare; refresh()
        }
        findViewById<Button>(R.id.btnWidgetCountdown).setOnClickListener {
            store.widgetCountdown = !store.widgetCountdown; refresh()
        }
        findViewById<Button>(R.id.btnWidgetFamily).setOnClickListener {
            store.widgetFamily = !store.widgetFamily; refresh()
        }
        findViewById<Button>(R.id.btnWidgetCamera).setOnClickListener {
            store.widgetCamera = !store.widgetCamera; refresh()
        }
        // Đếm ngược: Đà Nẵng 1/6/2027 <-> Tắt.
        findViewById<Button>(R.id.btnCountdown).setOnClickListener {
            if (store.countdownDate == "2027-06-01") {
                store.countdownDate = ""
                store.countdownLabel = ""
                Toast.makeText(this, "Đã tắt đếm ngược", Toast.LENGTH_SHORT).show()
            } else {
                store.countdownDate = "2027-06-01"
                store.countdownLabel = "Đà Nẵng"
                Toast.makeText(this, "Đếm ngược: Đà Nẵng 1/6/2027", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
        // Khung giờ popup: 7–21 <-> 6–22 <-> 0–24.
        findViewById<Button>(R.id.btnPushHours).setOnClickListener {
            if (store.pushStartHour == 7) {
                store.pushStartHour = 6; store.pushEndHour = 22
            } else if (store.pushStartHour == 6) {
                store.pushStartHour = 0; store.pushEndHour = 24
            } else {
                store.pushStartHour = 7; store.pushEndHour = 21
            }
            refresh()
        }
        // Chế độ popup: luôn <-> chỉ màn hình chính <-> im lặng.
        findViewById<Button>(R.id.btnPopupMode).setOnClickListener {
            store.popupMode = when (store.popupMode) {
                "always" -> "home"
                "home" -> "silent"
                else -> "always"
            }
            refresh()
        }
        // Kiểu icon: Mặc định <-> Tròn.
        btnIconStyle.setOnClickListener {
            store.iconStyle = if (store.iconStyle == "circle") "default" else "circle"
            Toast.makeText(this, "Kiểu icon: ${store.iconStyleName()}", Toast.LENGTH_SHORT).show()
            refresh()
        }
        // Thành phố thời tiết.
        btnCity.setOnClickListener {
            store.weatherCity = when (store.weatherCity) {
                "hanoi" -> "danang"
                "danang" -> "hcmc"
                else -> "hanoi"
            }
            store.weatherCacheJson = ""
            Toast.makeText(this, "Thời tiết: ${store.weatherCityName()}", Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.btnHiddenApps).setOnClickListener {
            startActivity(Intent(this, HiddenAppsActivity::class.java))
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        refresh()
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
                // Downscale như ảnh nền khóa để nhẹ máy.
                if (store.saveHomeBgBytes(bytes)) {
                    val tmp = store.homeBgFile()
                    if (tmp.exists()) {
                        tmp.copyTo(f, overwrite = true)
                        tmp.delete()
                    }
                    Toast.makeText(this, "Đã thêm ảnh nền (${store.wallpaperFiles().size} ảnh)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Ảnh không đọc được", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val n = store.wallpaperFiles().size
        tvBgState.text = if (store.wallpaperSource == "default") "Nguồn: ảnh mặc định (10 ảnh phong cảnh)"
            else if (n > 0) "Nguồn: ảnh của tôi ($n ảnh)"
            else "Nguồn: ảnh của tôi (chưa có ảnh nào)"
        findViewById<Button>(R.id.btnWpSource).text = "Nguồn ảnh nền: ${wpSourceName()}"
        findViewById<Button>(R.id.btnWpInterval).text =
            if (store.wallpaperIntervalMin == 0) "Đổi ảnh: tắt"
            else "Đổi ảnh mỗi: ${store.wallpaperIntervalMin} phút"
        btnIconStyle.text = "Kiểu icon ứng dụng: ${store.iconStyleName()}"
        btnCity.text = "Thành phố thời tiết: ${store.weatherCityName()}"
        findViewById<Button>(R.id.btnWidgetTinker).text =
            "Widget Tinker: ${onOff(store.widgetTinker)}"
        findViewById<Button>(R.id.btnWidgetFare).text =
            "Widget vé máy bay: ${onOff(store.widgetFare)}"
        findViewById<Button>(R.id.btnWidgetCountdown).text =
            "Widget đếm ngược: ${onOff(store.widgetCountdown)}"
        findViewById<Button>(R.id.btnWidgetFamily).text =
            "Widget gia đình: ${onOff(store.widgetFamily)}"
        findViewById<Button>(R.id.btnWidgetCamera).text =
            "Widget camera: ${onOff(store.widgetCamera)}"
        findViewById<Button>(R.id.btnCountdown).text =
            if (store.countdownDate.isEmpty()) "Đếm ngược: tắt"
            else "Đếm ngược: ${store.countdownLabel} ${store.countdownDate}"
        findViewById<Button>(R.id.btnPushHours).text =
            "Khung giờ popup: ${store.pushStartHour}:00–${store.pushEndHour}:00"
        findViewById<Button>(R.id.btnPopupMode).text =
            "Chế độ popup: ${store.popupModeName()}"
    }

    private fun onOff(b: Boolean) = if (b) "Bật" else "Tắt"

    private fun wpSourceName() =
        if (store.wallpaperSource == "default") "Ảnh mặc định" else "Ảnh của tôi"
}
