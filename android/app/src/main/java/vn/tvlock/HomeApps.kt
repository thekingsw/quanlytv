package vn.tvlock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.text.Collator
import java.util.Locale
import java.util.concurrent.Executors

// v3.0: quét app cho màn hình chính 2-trong-1 — port từ TV Home v1
// (vn.tvhome.Apps), đổi SELF_PKG thành vn.tvlock.
const val SELF_PKG = "vn.tvlock"

/** v4.1: TvApp KHÔNG ôm Drawable nữa — icon nặng, tải nền + cache riêng
 *  (xem AppIconCache). loadAllApps() vì thế chỉ còn query + đọc label,
 *  mở launcher không còn khựng khi máy cài nhiều app. */
data class TvApp(val label: String, val pkg: String, internal val ri: ResolveInfo)

/** Cache icon trong RAM + tải nền trên pool riêng.
 *  Lần đầu mở launcher: hiện placeholder ngay, icon về dần — không chặn
 *  main thread. Lần sau: lấy từ cache, hiện tức thì. */
object AppIconCache {
    private const val MAX_ENTRIES = 96
    private val cache = LruCache<String, Drawable>(MAX_ENTRIES)
    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun peek(pkg: String): Drawable? = synchronized(cache) { cache.get(pkg) }

    /** Lấy icon: có trong cache thì gọi cb ngay trên main thread;
     *  chưa có thì tải nền rồi gọi cb trên main thread. */
    fun load(pm: PackageManager, app: TvApp, cb: (Drawable?) -> Unit) {
        val hit = synchronized(cache) { cache.get(app.pkg) }
        if (hit != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) cb(hit)
            else main.post { cb(hit) }
            return
        }
        io.execute {
            val d = try { app.ri.loadIcon(pm) } catch (_: Exception) { null }
            if (d != null) synchronized(cache) { cache.put(app.pkg, d) }
            main.post { cb(d) }
        }
    }

    fun evict(pkg: String) { synchronized(cache) { cache.remove(pkg) } }
}

/** Toàn bộ app mở được: LEANBACK_LAUNCHER trước, fallback LAUNCHER;
 *  sắp xếp theo tên tiếng Việt; bỏ qua chính Khóa TV.
 *  NHẸ: không tải icon ở đây — icon do AppIconCache tải nền khi bind. */
fun loadAllApps(pm: PackageManager): List<TvApp> {
    val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    var ris = pm.queryIntentActivities(leanback, 0)
    if (ris.isEmpty()) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        ris = pm.queryIntentActivities(launcher, 0)
    }
    val coll = Collator.getInstance(Locale("vi"))
    return ris
        .mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == SELF_PKG) return@mapNotNull null
            val label = ri.loadLabel(pm)?.toString() ?: pkg
            TvApp(label, pkg, ri)
        }
        .distinctBy { it.pkg }
        .sortedWith { a, b -> coll.compare(a.label, b.label) }
}

/** Mở app theo package. Trả false nếu không mở được. */
fun openApp(ctx: Context, pkg: String): Boolean {
    val pm = ctx.packageManager
    val i = pm.getLeanbackLaunchIntentForPackage(pkg)
        ?: pm.getLaunchIntentForPackage(pkg)
        ?: return false
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        ctx.startActivity(i)
        true
    } catch (_: Exception) {
        false
    }
}
