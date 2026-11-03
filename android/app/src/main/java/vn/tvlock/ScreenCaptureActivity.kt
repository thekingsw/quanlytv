package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** v2.9: cấp quyền chụp màn hình TV (MediaProjection) để web trên điện
 *  thoại chụp được màn hình TV (GET /api/screenshot). Quyền này phải do
 *  người dùng đồng ý qua dialog hệ thống — app không tự lấy được.
 *  Sau khi khởi động lại TV, projection mất và phải cấp lại bằng tay
 *  (không có cách tự động) — trạng thái đó hiện rõ ở đây. */
class ScreenCaptureActivity : Activity() {
    private var settingsFrame: SettingsFrame.Handle? = null
    private lateinit var store: Store
    private lateinit var tvCaptureState: TextView

    companion object {
        private const val REQ_CAPTURE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_frame)
        store = Store(this)
        settingsFrame = SettingsFrame.bind(this, "screenshot")
        layoutInflater.inflate(R.layout.pane_screen_capture, settingsFrame!!.pane, true)
        settingsFrame!!.onPaneReady()
        tvCaptureState = findViewById(R.id.tvCaptureState)
        findViewById<Button>(R.id.btnGrantCapture).setOnClickListener {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            try {
                startActivityForResult(mgr.createScreenCaptureIntent(), REQ_CAPTURE)
            } catch (_: Exception) {
                Toast.makeText(this, "TV này không hỗ trợ chụp màn hình", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnStopCapture).setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            Toast.makeText(this, "Đã tắt chụp màn hình", Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @Deprecated("Dùng cho MediaProjection trên TV (minSdk 26)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return
        if (resultCode == RESULT_OK && data != null) {
            val svc = Intent(this, ScreenCaptureService::class.java)
            svc.putExtra("resultCode", resultCode)
            svc.putExtra("data", data)
            try {
                startForegroundService(svc)
                store.screenshotGrantedOnce = true
                Toast.makeText(this, "Đã cấp quyền chụp màn hình", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Không bật được dịch vụ chụp màn hình", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Chưa cấp quyền chụp màn hình", Toast.LENGTH_SHORT).show()
        }
        refresh()
    }

    private fun refresh() {
        tvCaptureState.text = when {
            ScreenCaptureService.isAlive() ->
                "Đã cấp quyền — chụp màn hình từ điện thoại đang hoạt động."
            store.screenshotGrantedOnce ->
                "Cần cấp lại sau khi khởi động lại TV — quyền chụp màn hình mất khi TV khởi động lại, bấm nút dưới để cấp lại."
            else ->
                "Chưa cấp quyền — bấm nút dưới để cho phép app chụp màn hình TV."
        }
    }

    /** Back phân cấp: pane → rail → tab → thoát. */
    override fun onBackPressed() {
        if (settingsFrame?.onBackPressed() == true) return
        super.onBackPressed()
    }
}
