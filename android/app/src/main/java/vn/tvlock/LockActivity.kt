package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class LockActivity : Activity() {
    private lateinit var store: Store
    private val pinBuf = StringBuilder()
    private lateinit var tvPin: TextView
    private lateinit var pinView: LinearLayout
    private lateinit var optionsView: LinearLayout
    private var mode = "lock"
    private var addMinutes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        store = Store(this)
        mode = intent.getStringExtra("mode") ?: "lock"
        addMinutes = intent.getIntExtra("minutes", 0)
        val reason = intent.getStringExtra("reason") ?: store.lockReason(System.currentTimeMillis())
        findViewById<TextView>(R.id.tvReason).text = reason
        findViewById<TextView>(R.id.tvClock).text = "Nhập mã PIN để mở khóa"
        tvPin = findViewById(R.id.tvPin)
        pinView = findViewById(R.id.pinView)
        optionsView = findViewById(R.id.optionsView)
        val digits = listOf(
            R.id.key0 to "0", R.id.key1 to "1", R.id.key2 to "2", R.id.key3 to "3",
            R.id.key4 to "4", R.id.key5 to "5", R.id.key6 to "6", R.id.key7 to "7",
            R.id.key8 to "8", R.id.key9 to "9"
        )
        for ((id, d) in digits) findViewById<Button>(id).setOnClickListener { appendDigit(d) }
        findViewById<Button>(R.id.keyDel).setOnClickListener { deleteDigit() }
        findViewById<Button>(R.id.keyOk).setOnClickListener { submitPin() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
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
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.key1).requestFocus()
        updatePinDisplay()
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
                if (pinView.visibility == View.VISIBLE) { submitPin(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun appendDigit(d: String) {
        if (pinBuf.length < 8) pinBuf.append(d)
        updatePinDisplay()
    }

    private fun deleteDigit() {
        if (pinBuf.isNotEmpty()) pinBuf.deleteCharAt(pinBuf.length - 1)
        updatePinDisplay()
    }

    private fun updatePinDisplay() {
        tvPin.text = "•".repeat(pinBuf.length).ifEmpty { "____" }
    }

    private fun submitPin() {
        if (pinBuf.toString() == store.pin) {
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
                else -> {
                    pinView.visibility = View.GONE
                    optionsView.visibility = View.VISIBLE
                    findViewById<Button>(R.id.optAdd5).requestFocus()
                }
            }
        } else {
            Toast.makeText(this, "Sai mã PIN", Toast.LENGTH_SHORT).show()
            pinBuf.clear()
            updatePinDisplay()
        }
    }

    override fun onBackPressed() {
        // Block BACK from dismissing the lock screen when it was triggered by a lock.
        if (mode == "lock") return
        super.onBackPressed()
    }
}
