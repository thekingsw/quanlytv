package vn.tvlock

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button

/**
 * Bấm-giữ để tăng/giảm liên tục, giống giữ phím volume trên điều khiển.
 *
 * Bấm một cái: chạy [onStep] đúng 1 lần (qua click thường).
 * Giữ nút (điều khiển hoặc chạm): sau khi giữ ~0,5 giây (long-press),
 * [onStep] tự chạy lặp lại mỗi ~120ms, nhanh dần lên ~70ms khi giữ lâu.
 * Nhả nút ra là dừng ngay. Dùng chung cho mọi nút −/+ giờ và phút.
 */
object HoldRepeat {
    fun attach(button: Button, onStep: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var repeating = false
        var suppressClick = false
        var ticks = 0
        val runnable = object : Runnable {
            override fun run() {
                if (!repeating) return
                ticks++
                onStep()
                val delay = if (ticks > 12) 70L else 120L
                handler.postDelayed(this, delay)
            }
        }
        fun start() {
            if (repeating) return
            repeating = true
            ticks = 0
            suppressClick = true
            handler.postDelayed(runnable, 150)
        }
        fun stop() {
            repeating = false
            handler.removeCallbacks(runnable)
        }
        button.setOnClickListener {
            if (suppressClick) suppressClick = false else onStep()
        }
        button.setOnLongClickListener { start(); true }
        button.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) stop()
            false
        }
        button.setOnKeyListener { _, keyCode, ev ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (ev.action == KeyEvent.ACTION_UP) stop()
            }
            false
        }
        button.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) stop()
        }
    }
}
