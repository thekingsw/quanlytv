package vn.tvlock

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView

/**
 * Gentle pre-lock warning banner: shows a message at the top of the screen
 * for 20 seconds (or until the parent presses OK/Back). It never blocks
 * viewing and is not counted as watch time (vn.tvlock is excluded).
 */
class WarningActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val dismiss = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warning)
        findViewById<TextView>(R.id.tvWarnTitle).text =
            intent.getStringExtra("message") ?: "Sắp hết giờ xem"
        findViewById<TextView>(R.id.tvWarnSub).text =
            intent.getStringExtra("sub") ?: ""
        findViewById<Button>(R.id.btnWarnOk).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnWarnOk).requestFocus()
        handler.postDelayed(dismiss, 20000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(dismiss)
        super.onDestroy()
    }
}
