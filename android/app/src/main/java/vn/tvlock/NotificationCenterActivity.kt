package vn.tvlock

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v4.0: trung tâm thông báo — liệt kê tin từ hộp thư (mới nhất trước).
 * Mở từ chuông trên màn hình chính hoặc từ popup (OK). Vào đây thì
 * xóa số chưa đọc. Tin đã xem thì tự xóa khỏi máy cho khỏi rác
 * (user 2026-11-02) — chỉ xóa những tin đã hiện, tin mới đến trong
 * lúc xem thì giữ lại.
 */
class NotificationCenterActivity : Activity() {

    private lateinit var store: Store
    private val adapter = NotifAdapter()
    private var shownIds = setOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_center)
        store = Store(this)
        val list = findViewById<ListView>(R.id.notifList)
        list.adapter = adapter
        findViewById<Button>(R.id.btnNotifBack)?.setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvNotifTitle)?.text = "Thông báo"
    }

    override fun onResume() {
        super.onResume()
        val msgs = InboxClient.cachedMessages(store)
        adapter.items = msgs
        shownIds = msgs.map { it.id }.toSet()
        adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.tvNotifEmpty)?.visibility =
            if (adapter.items.isEmpty()) View.VISIBLE else View.GONE
        // Đã xem hết → xóa badge.
        store.inboxUnread = 0
    }

    override fun onPause() {
        super.onPause()
        // Tin đã xem thì tự xóa cho khỏi rác.
        if (shownIds.isNotEmpty()) {
            try {
                val arr = org.json.JSONArray(store.inboxCacheJson)
                val kept = org.json.JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optLong("id") !in shownIds) kept.put(o)
                }
                store.inboxCacheJson = kept.toString()
            } catch (_: Exception) { }
            shownIds = emptySet()
        }
    }

    private inner class NotifAdapter : BaseAdapter() {
        var items: List<InboxClient.Message> = emptyList()
        private val fmt = SimpleDateFormat("HH:mm dd/MM", Locale("vi"))
        override fun getCount(): Int = items.size
        override fun getItem(p: Int): Any = items[p]
        override fun getItemId(p: Int): Long = items[p].id
        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val v = convert ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notification, parent, false)
            val m = items[p]
            v.findViewById<TextView>(R.id.tvNotifItemTitle)?.text = m.title
            v.findViewById<TextView>(R.id.tvNotifItemBody)?.text = m.body
            v.findViewById<TextView>(R.id.tvNotifItemTime)?.text =
                fmt.format(Date(m.createdAt * 1000))
            return v
        }
    }
}
