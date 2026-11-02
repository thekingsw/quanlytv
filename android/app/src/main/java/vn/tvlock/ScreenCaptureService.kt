package vn.tvlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream

/**
 * v2.9: chụp màn hình TV theo yêu cầu từ web (GET /api/screenshot).
 *
 * Android không cho app tự chụp màn hình lén — cần quyền MediaProjection
 * do người dùng đồng ý qua dialog hệ thống. Luồng cấp quyền: TV →
 * Cài đặt → Chụp màn hình → "Cấp quyền chụp màn hình" → dialog đồng ý
 * → ScreenCaptureActivity nhận resultCode + data → start service này.
 * Service giữ MediaProjection + VirtualDisplay + ImageReader sống để
 * WebServer chụp bất cứ lúc nào (không cần đồng ý lại mỗi lần chụp).
 *
 * GIỚI HẠN KHÔNG TRÁNH ĐƯỢC: sau khi khởi động lại TV, projection mất
 * (service không tự chạy lại được vì không có resultCode + data mới) —
 * phải cấp quyền lại bằng tay. Không có cách tự động. Trạng thái này
 * hiện trên màn hình TV (ScreenCaptureActivity) và trong hint lỗi 503
 * của /api/screenshot.
 */
class ScreenCaptureService : Service() {

    companion object {
        @Volatile private var projection: MediaProjection? = null
        @Volatile private var imageReader: ImageReader? = null
        @Volatile private var virtualDisplay: VirtualDisplay? = null

        /** Projection còn sống không (đã cấp quyền và chưa bị dừng). */
        fun isAlive(): Boolean = projection != null

        /** Chụp 1 khung hình mới nhất → JPEG (85%). Null khi chưa cấp
         *  quyền, projection đã chết, hoặc không lấy được khung hình.
         *  Chạy trên luồng HTTP của WebServer — vòng chờ tối đa ~1 giây
         *  (dưới timeout 5 giây của socket). */
        fun capture(): ByteArray? {
            val reader = imageReader ?: return null
            var image: Image? = null
            try {
                var tries = 0
                while (tries < 10) {
                    image = try { reader.acquireLatestImage() } catch (_: Exception) { null }
                    if (image != null) break
                    Thread.sleep(100)
                    tries++
                }
                val img = image ?: return null
                try {
                    val plane = img.planes[0]
                    val buf = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    // Hàng pixel có thể đệm thêm (rowStride > width*pixelStride)
                    // nên bitmap phải rộng hơn rồi cắt về đúng kích thước.
                    val rowPadding = rowStride - pixelStride * img.width
                    val bmp = Bitmap.createBitmap(
                        img.width + rowPadding / pixelStride, img.height,
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.copyPixelsFromBuffer(buf)
                    val cropped = if (bmp.width != img.width)
                        Bitmap.createBitmap(bmp, 0, 0, img.width, img.height) else bmp
                    if (cropped !== bmp) bmp.recycle()
                    val out = ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    cropped.recycle()
                    return out.toByteArray()
                } finally {
                    try { img.close() } catch (_: Exception) { }
                }
            } catch (_: Exception) {
                try { image?.close() } catch (_: Exception) { }
                return null
            }
        }
    }

    private var projectionCallback: MediaProjection.Callback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Lệnh dừng từ màn hình TV (nút "Tắt chụp màn hình").
        if (intent?.getBooleanExtra("stop", false) == true) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null) return START_STICKY // đã chạy rồi, giữ nguyên
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel("capture", "Chụp màn hình TV", NotificationManager.IMPORTANCE_LOW)
                nm.createNotificationChannel(ch)
            }
            startForeground(2, buildNotification())
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mgr.getMediaProjection(resultCode, data) ?: run {
                stopSelf()
                return START_NOT_STICKY
            }
            @Suppress("DEPRECATION")
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val point = android.graphics.Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(point)
            val dpi = resources.displayMetrics.densityDpi
            val reader = ImageReader.newInstance(point.x, point.y, PixelFormat.RGBA_8888, 2)
            val vd = proj.createVirtualDisplay(
                "KhoaTV-chup-man-hinh", point.x, point.y, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )
            val cb = object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }
            proj.registerCallback(cb, Handler(Looper.getMainLooper()))
            projectionCallback = cb
            projection = proj
            imageReader = reader
            virtualDisplay = vd
            Store(this).screenshotGrantedOnce = true
        } catch (e: Exception) {
            Log.e("ScreenCapture", "khong bat duoc MediaProjection: $e")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, "capture") else Notification.Builder(this)
        return b.setContentTitle("Khóa TV — đang bật chụp màn hình")
            .setContentText("Bố có thể chụp màn hình TV từ điện thoại.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi).setOngoing(true).build()
    }

    override fun onDestroy() {
        try { virtualDisplay?.release() } catch (_: Exception) { }
        try { imageReader?.close() } catch (_: Exception) { }
        try {
            val cb = projectionCallback
            if (cb != null) projection?.unregisterCallback(cb)
        } catch (_: Exception) { }
        try { projection?.stop() } catch (_: Exception) { }
        virtualDisplay = null
        imageReader = null
        projection = null
        projectionCallback = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
