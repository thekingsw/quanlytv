package vn.tvlock

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** v2.3: quản lý mã PIN web — màn hình này chỉ mở được sau khi đã nhập
 *  đúng mã PIN khóa TV ở LockActivity (mode "webpin"), nên mã PIN web
 *  hiện công khai ở đây. Dùng để xem/đổi mã khi quên mã PIN web. */
class WebPinActivity : Activity() {
    private var settingsFrame: SettingsFrame.Handle? = null
    private lateinit var store: Store
    private lateinit var tvWebPin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_frame)
        store = Store(this)
        settingsFrame = SettingsFrame.bind(this, "webpin")
        layoutInflater.inflate(R.layout.pane_webpin, settingsFrame!!.pane, true)
        settingsFrame!!.onPaneReady()
        tvWebPin = findViewById(R.id.tvWebPin)

        findViewById<Button>(R.id.btnChangeWebPin).setOnClickListener {
            val nw = findViewById<EditText>(R.id.etNewWebPin).text.toString().trim()
            if (!nw.matches(Regex("^\\d{4,6}$"))) {
                Toast.makeText(this, "Mã PIN web mới phải là 4–6 chữ số", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            store.webPin = nw
            findViewById<EditText>(R.id.etNewWebPin).setText("")
            Toast.makeText(this, "Đã đổi mã PIN web", Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.btnResetWebPin).setOnClickListener {
            store.webPin = store.pin
            Toast.makeText(this, "Đã đặt lại mã PIN web = mã PIN khóa TV", Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        tvWebPin.text = store.webPinOrDefault()
    }

    /** Back phân cấp: pane → rail → tab → thoát. */
    override fun onBackPressed() {
        if (settingsFrame?.onBackPressed() == true) return
        super.onBackPressed()
    }
}
