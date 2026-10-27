package vn.tvlock

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * Per-app limits: pick an installed launchable app, then choose
 * Normal / own limit (15/30/60 min) / Free (not counted) / Blocked.
 */
class AppRulesActivity : Activity() {
    private lateinit var store: Store
    private var pkgs = listOf<String>()
    private var labels = listOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    private val options = arrayOf(
        "Bình thường (tính vào giới hạn ngày)",
        "Giới hạn riêng 15 phút/ngày",
        "Giới hạn riêng 30 phút/ngày",
        "Giới hạn riêng 60 phút/ngày",
        "Không tính giờ (app học)",
        "Chặn hẳn"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_rules)
        store = Store(this)
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.sortedBy { it.second }
        pkgs = apps.map { it.first }
        labels = apps.map { it.second }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rowTexts())
        val list = findViewById<ListView>(R.id.listApps)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> showRuleDialog(pos) }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvHint).text =
            "Bấm vào một app để đặt quy tắc riêng. App đã đặt quy tắc sẽ áp dụng ngay, không cần khởi động lại."
    }

    private fun ruleText(pkg: String): String {
        val r = store.appRule(pkg)
        return when (r.mode) {
            "limit" -> "Giới hạn riêng ${r.minutes}' (hôm nay đã xem ${store.perAppMin(pkg)}')"
            "free" -> "Không tính giờ (hôm nay đã xem ${store.perAppMin(pkg)}')"
            "blocked" -> "Chặn hẳn"
            else -> "Bình thường (hôm nay đã xem ${store.perAppMin(pkg)}')"
        }
    }

    private fun rowTexts(): MutableList<String> {
        return labels.mapIndexed { i, label -> "$label — ${ruleText(pkgs[i])}" }.toMutableList()
    }

    private fun refresh() {
        adapter.clear()
        adapter.addAll(rowTexts())
        adapter.notifyDataSetChanged()
    }

    private fun showRuleDialog(pos: Int) {
        val pkg = pkgs[pos]
        val label = labels[pos]
        val current = store.appRule(pkg)
        val checked = when (current.mode) {
            "limit" -> when (current.minutes) {
                15 -> 1; 30 -> 2; 60 -> 3; else -> 2
            }
            "free" -> 4
            "blocked" -> 5
            else -> 0
        }
        var chosen = checked
        AlertDialog.Builder(this)
            .setTitle(label)
            .setSingleChoiceItems(options, checked) { _, which -> chosen = which }
            .setPositiveButton("Lưu") { _, _ ->
                when (chosen) {
                    0 -> store.setAppRule(pkg, "normal", 0)
                    1 -> store.setAppRule(pkg, "limit", 15)
                    2 -> store.setAppRule(pkg, "limit", 30)
                    3 -> store.setAppRule(pkg, "limit", 60)
                    4 -> store.setAppRule(pkg, "free", 0)
                    5 -> store.setAppRule(pkg, "blocked", 0)
                }
                Toast.makeText(this, "Đã lưu quy tắc cho $label", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
