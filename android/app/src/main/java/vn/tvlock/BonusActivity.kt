package vn.tvlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** v2.1: phút thưởng — tách ra màn hình riêng (trước đây nằm trong Cài
 *  đặt). Bấm nút rồi nhập PIN trên màn hình khóa (chế độ bonus). */
class BonusActivity : Activity() {
    private lateinit var store: Store
    private lateinit var tvBonus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bonus)
        store = Store(this)
        SettingsRail.build(this, findViewById<LinearLayout>(R.id.railContainer), "bonus")
        tvBonus = findViewById(R.id.tvBonus)
        val bonusBtns = listOf(
            R.id.btnBonus5 to 5, R.id.btnBonus10 to 10,
            R.id.btnBonus15 to 15, R.id.btnBonus30 to 30
        )
        for ((id, min) in bonusBtns) {
            findViewById<Button>(id).setOnClickListener {
                val i = Intent(this, LockActivity::class.java)
                i.putExtra("mode", "bonus"); i.putExtra("minutes", min)
                i.putExtra("reason", "Thưởng thêm $min phút")
                startActivity(i)
            }
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        tvBonus.text = "Phút thưởng còn lại: ${store.bonusMin} phút\n(Phút thưởng chỉ trừ khi đã hết giờ ngày, và không hết hạn khi sang ngày mới.)"
    }
}
