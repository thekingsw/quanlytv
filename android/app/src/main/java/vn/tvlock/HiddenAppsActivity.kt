package vn.tvlock

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.ListView

/** v3.0: danh sách app đã ẩn khỏi màn hình chính — bấm để ẩn/hiện lại.
 *  Port từ TV Home v1 (vn.tvhome.HiddenAppsActivity). */
class HiddenAppsActivity : Activity() {
    private var settingsFrame: SettingsFrame.Handle? = null
    private lateinit var store: Store
    private val adapter = HiddenAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_frame)
        settingsFrame = SettingsFrame.bind(this, "home_hidden")
        layoutInflater.inflate(R.layout.pane_hidden_apps, settingsFrame!!.pane, true)
        settingsFrame!!.onPaneReady()
        store = Store(this)
        val list = findViewById<ListView>(R.id.hiddenList)
        list.adapter = adapter
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        adapter.items = loadAllApps(packageManager)
        adapter.notifyDataSetChanged()
    }

    private inner class HiddenAdapter : BaseAdapter() {
        var items: List<TvApp> = emptyList()
        override fun getCount(): Int = items.size
        override fun getItem(p: Int): Any = items[p]
        override fun getItemId(p: Int): Long = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val v = convert ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_hidden_app, parent, false)
            val app = items[p]
            val ctv = v.findViewById<CheckedTextView>(R.id.hiddenLabel)
            ctv.text = app.label
            ctv.isChecked = store.homeHidden().contains(app.pkg)
            v.setOnClickListener {
                val hidden = store.homeHidden()
                store.setHomeHidden(app.pkg, !hidden.contains(app.pkg))
                notifyDataSetChanged()
            }
            return v
        }
    }

    /** Back phân cấp: pane → rail → tab → thoát. */
    override fun onBackPressed() {
        if (settingsFrame?.onBackPressed() == true) return
        super.onBackPressed()
    }
}
