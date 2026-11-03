package vn.tvlock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.ImageView

/** Icon app cho màn hình chính — chế độ "Bo tròn" thì cắt chữ nhật bo tròn
 *  (kiểu CleanTV), chế độ "Mặc định" thì vẽ icon gốc như ImageView thường.
 *  Thay thế CircleIconView (hình tròn chỉ đẹp với vài app). */
class RoundedIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {

    var roundMode: Boolean = false
        set(v) { field = v; invalidate() }

    private val clip = Path()
    private val rect = RectF()

    /** Bo góc ~25% cạnh icon (kiểu iOS/CleanTV). */
    private val radius: Float
        get() = minOf(width, height) * 0.25f

    override fun onDraw(canvas: Canvas) {
        if (!roundMode) {
            super.onDraw(canvas)
            return
        }
        clip.reset()
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val r = radius
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)
        super.onDraw(canvas)
        canvas.restoreToCount(save)
    }
}
