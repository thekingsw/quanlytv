package vn.tvlock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.text.Collator
import java.util.Locale

// v3.0: quét app cho màn hình chính 2-trong-1 — port từ TV Home v1
// (vn.tvhome.Apps), đổi SELF_PKG thành vn.tvlock.
const val SELF_PKG = "vn.tvlock"

data class TvApp(val label: String, val pkg: String, val icon: Drawable)

/** Toàn bộ app mở được: LEANBACK_LAUNCHER trước, fallback LAUNCHER;
 *  sắp xếp theo tên tiếng Việt; bỏ qua chính Khóa TV. */
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
            val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                ?: return@mapNotNull null
            TvApp(label, pkg, icon)
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
