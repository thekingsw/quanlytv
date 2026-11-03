package vn.tvlock

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** v2.1: mã PIN + khóa thủ công + reset phút hôm nay — tách ra màn hình
 *  riêng (trước đây nằm trong Cài đặt, làm trang Cài đặt dài). */
class PinActivity : Activity() {
    private var settingsFrame: SettingsFrame.Handle? = null
    private lateinit var store: Store
    private lateinit var tvManual: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_frame)
        store = Store(this)
        settingsFrame = SettingsFrame.bind(this, "pin")
        layoutInflater.inflate(R.layout.pane_pin, settingsFrame!!.pane, true)
        settingsFrame!!.onPaneReady()
        tvManual = findViewById(R.id.tvManual)

        findViewById<Button>(R.id.btnChangePin).setOnClickListener {
            val old = findViewById<EditText>(R.id.etOldPin).text.toString()
            val nw = findViewById<EditText>(R.id.etNewPin).text.toString()
            when {
                old != store.pin -> Toast.makeText(this, "PIN cũ không đúng", Toast.LENGTH_SHORT).show()
                nw.length < 4 -> Toast.makeText(this, "PIN mới ít nhất 4 số", Toast.LENGTH_SHORT).show()
                else -> { store.pin = nw; Toast.makeText(this, "Đã đổi PIN", Toast.LENGTH_SHORT).show() }
            }
        }
        findViewById<Button>(R.id.btnManual).setOnClickListener {
            store.manualLocked = !store.manualLocked
            if (store.manualLocked) store.unlockUntilMs = 0L
            refresh()
        }
        findViewById<Button>(R.id.btnResetUsed).setOnClickListener {
            store.usedTodayMin = 0
            Toast.makeText(this, "Đã reset số phút hôm nay", Toast.LENGTH_SHORT).show()
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
        tvManual.text = if (store.manualLocked) "Khóa thủ công: ĐANG KHÓA" else "Khóa thủ công: đang mở"
        findViewById<TextView>(R.id.tvUsedToday).text =
            "Hôm nay đã xem ${store.usedTodayMin} phút (giới hạn ${store.limitText(store.limitForToday())})."
    }

    /** Back phân cấp: pane → rail → tab → thoát. */
    override fun onBackPressed() {
        if (settingsFrame?.onBackPressed() == true) return
        super.onBackPressed()
    }
}
