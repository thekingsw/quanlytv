package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** v2.1: sửa màn hình khóa theo kiểu "soạn rồi mới Áp dụng".
 *  Sửa lời nhắc / chọn ảnh mới chỉ đổi bản nháp: bấm "Xem trước" để mở
 *  đúng màn hình khóa sẽ hiện trên TV (LockActivity chế độ preview),
 *  bấm "Áp dụng" mới lưu lời nhắc + ảnh vào máy. Bấm "Hủy thay đổi"
 *  thì quay về bản đang dùng. */
class LockScreenActivity : Activity() {
    private var settingsFrame: SettingsFrame.Handle? = null
    private lateinit var store: Store
    private lateinit var etMessage: EditText
    private lateinit var tvBgState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_frame)
        store = Store(this)
        settingsFrame = SettingsFrame.bind(this, "lock_screen")
        layoutInflater.inflate(R.layout.pane_lock_screen, settingsFrame!!.pane, true)
        settingsFrame!!.onPaneReady()
        etMessage = findViewById(R.id.etLockMessage)
        tvBgState = findViewById(R.id.tvLockBgState)
        etMessage.setText(store.lockMessage)

        findViewById<Button>(R.id.btnPickLockBg).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
            try { startActivityForResult(i, 1001) } catch (_: Exception) {
                Toast.makeText(this, "TV không có trình chọn ảnh", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnPreview).setOnClickListener {
            val i = Intent(this, LockActivity::class.java)
            i.putExtra("mode", "preview")
            i.putExtra("previewMessage", etMessage.text.toString().trim())
            val tmp = store.lockBgTempFile()
            if (tmp.exists()) i.putExtra("previewBgPath", tmp.absolutePath)
            startActivity(i)
        }
        findViewById<Button>(R.id.btnApply).setOnClickListener {
            val msg = etMessage.text.toString().trim()
            if (msg.isEmpty() || msg.length > 140) {
                Toast.makeText(this, "Lời nhắc từ 1–140 ký tự", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            store.lockMessage = msg
            val tmp = store.lockBgTempFile()
            if (tmp.exists()) {
                val bytes = try { tmp.readBytes() } catch (_: Exception) { null }
                if (bytes == null || !store.saveLockBgBytes(bytes)) {
                    Toast.makeText(this, "Lời nhắc đã lưu, nhưng ảnh mới không lưu được", Toast.LENGTH_LONG).show()
                    refresh()
                    return@setOnClickListener
                }
                try { tmp.delete() } catch (_: Exception) { }
            }
            Toast.makeText(this, "Đã áp dụng lên màn hình khóa", Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.btnDiscard).setOnClickListener {
            etMessage.setText(store.lockMessage)
            try { store.lockBgTempFile().delete() } catch (_: Exception) { }
            Toast.makeText(this, "Đã hủy thay đổi — quay về bản đang dùng", Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.btnRemoveLockBg).setOnClickListener {
            store.deleteLockBg()
            try { store.lockBgTempFile().delete() } catch (_: Exception) { }
            Toast.makeText(this, "Đã xóa ảnh nền riêng", Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        refresh()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
                if (bytes.size > 8 * 1024 * 1024) {
                    Toast.makeText(this, "Ảnh quá lớn (tối đa 8MB)", Toast.LENGTH_SHORT).show()
                    return
                }
                // Chỉ lưu vào file tạm — chưa đổi ảnh thật cho tới khi Áp dụng.
                if (store.saveLockBgBytesTo(bytes, store.lockBgTempFile())) {
                    Toast.makeText(this, "Đã chọn ảnh — bấm Xem trước để xem, Áp dụng để lưu", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Ảnh không đọc được", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val tmp = store.lockBgTempFile()
        tvBgState.text = when {
            tmp.exists() -> "Ảnh nền: đã chọn ảnh mới (chưa áp dụng — bấm Xem trước rồi Áp dụng)"
            store.hasLockBg() -> "Ảnh nền: đã đặt ảnh riêng"
            else -> "Ảnh nền: chưa có — đang dùng nền mặc định"
        }
    }

    /** Back phân cấp: pane → rail → tab → thoát. */
    override fun onBackPressed() {
        if (settingsFrame?.onBackPressed() == true) return
        super.onBackPressed()
    }
}
