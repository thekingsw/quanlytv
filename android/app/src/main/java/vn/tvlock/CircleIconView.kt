package vn.tvlock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import android.widget.ImageView

/** v3.0: icon app cho màn hình chính — chế độ "Tròn" thì cắt tròn trong
 *  onDraw, chế độ "Mặc định" thì vẽ icon gốc như ImageView thường. */
class CircleIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {

    var circleMode: Boolean = false
        set(v) { field = v; invalidate() }

    private val clip = Path()

    override fun onDraw(canvas: Canvas) {
        if (!circleMode) {
            super.onDraw(canvas)
            return
        }
        clip.reset()
        val r = minOf(width, height) / 2f
        clip.addCircle(width / 2f, height / 2f, r, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)
        super.onDraw(canvas)
        canvas.restoreToCount(save)
    }
}
