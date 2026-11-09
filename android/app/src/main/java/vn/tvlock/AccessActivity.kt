package vn.tvlock

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** v2.1: Trợ năng + chống tắt + hiển thị icon — tách ra màn hình riêng
 *  (trước đây nằm trong Cài đặt, làm trang Cài đặt dài). */
class AccessActivity : Activity() {
    private var settingsFrame: SettingsFrame.Handle? = null
    private lateinit var store: Store
    private lateinit var tvAccess: TextView
    private lateinit var tvAntiDisable: TextView
    private lateinit var tvIconState: TextView
    private lateinit var btnAntiDisableToggle: Button
    private lateinit var btnIconToggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_frame)
        store = Store(this)
        settingsFrame = SettingsFrame.bind(this, "access")
        layoutInflater.inflate(R.layout.pane_access, settingsFrame!!.pane, true)
        settingsFrame!!.onPaneReady()
        tvAccess = findViewById(R.id.tvAccess)
        tvAntiDisable = findViewById(R.id.tvAntiDisable)
        tvIconState = findViewById(R.id.tvIconState)
        btnAntiDisableToggle = findViewById(R.id.btnAntiDisableToggle)
        btnIconToggle = findViewById(R.id.btnIconToggle)

        btnAntiDisableToggle.setOnClickListener {
            store.antiDisable = !store.antiDisable
            if (store.antiDisable) store.ensureAccessEnabled()
            Toast.makeText(this,
                if (store.antiDisable) "Đã bật chống tắt Trợ năng" else "Đã tắt chống tắt — giờ tắt được Trợ năng",
                Toast.LENGTH_SHORT).show()
            refresh()
        }
        btnIconToggle.setOnClickListener { toggleLauncherIcon(); refresh() }
        findViewById<Button>(R.id.btnOpenMain).setOnClickListener {
            val i = Intent(this, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        tvAccess.text = "Trợ năng (Accessibility): " + if (isAccessEnabled()) "ĐÃ BẬT" else "CHƯA BẬT — bấm nút dưới để bật"
        tvAntiDisable.text = "Chống tắt: " + if (store.antiDisable) "ĐANG BẬT" else "ĐANG TẮT" +
            " — quyền ghi cài đặt: " + if (store.hasWriteSecureSettings()) "đã cấp" else "CHƯA cấp (cài lại bằng Cài TV 1.5)"
        btnAntiDisableToggle.text = if (store.antiDisable) "Tắt chống tắt" else "Bật chống tắt"
        val iconOn = launcherIconEnabled()
        tvIconState.text = if (iconOn) "Icon trên màn hình chính: ĐANG HIỆN" else "Icon trên màn hình chính: ĐANG ẨN"
        btnIconToggle.text = if (iconOn) "Ẩn icon" else "Hiện icon"
    }

    private fun launcherIconEnabled(): Boolean {
        // Manifest sets the alias enabled=false, so DEFAULT also means hidden.
        return packageManager.getComponentEnabledSetting(
            ComponentName(packageName, "$packageName.LauncherAlias")
        ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    private fun toggleLauncherIcon() {
        val enabled = launcherIconEnabled()
        packageManager.setComponentEnabledSetting(
            ComponentName(packageName, "$packageName.LauncherAlias"),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        Toast.makeText(
            this,
            if (enabled) "Đã ẩn icon khỏi màn hình chính" else "Đã hiện icon trên màn hình chính",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun isAccessEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            enabled.contains(packageName)
        } catch (_: Exception) { false }
    }

    /** Back phân cấp: pane → rail → tab → thoát. */
    override fun onBackPressed() {
        if (settingsFrame?.onBackPressed() == true) return
        super.onBackPressed()
    }
}
