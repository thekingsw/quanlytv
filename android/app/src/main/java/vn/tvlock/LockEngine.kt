package vn.tvlock

import android.content.Context
import android.content.Intent

/**
 * CỬA DUY NHẤT cho mọi quyết định khóa.
 *
 * VẤN ĐỀ CŨ: câu hỏi "có hiện màn khóa không?" được trả lời ở ~8 chỗ (GuardService x2,
 * Trợ năng x3, HomeActivity x3) — chỗ có chốt "đang ở app mình thì đừng mở", chỗ không;
 * lastPkg bị ghi từ nhiều nơi; hai luồng (tick của GuardService + lockPoll của Trợ
 * năng) cùng đọc-sửa-ghi SharedPreferences không khóa. Mỗi lần vá một chỗ là lệch chỗ khác.
 *
 * QUY TẮC (ArchitectureTest bảo vệ):
 *  1. Chỉ file này được tạo Intent mở LockActivity với mode "lock".
 *  2. Chỉ file này ghi lastPkg (qua Store.markForeground; setter của lastPkg là private).
 *  3. Luật nằm trong các object THUẦN: LockPolicy (khóa / đếm giờ), SchedulePolicy (lịch),
 *     ForegroundTracker (foreground). File này chỉ NỐI: đọc state -> gọi luật thuần ->
 *     áp kết quả. KHÔNG thêm điều kiện nghiệp vụ vào đây.
 *  4. Mọi hàm public chạy dưới cùng một khóa [mutex] — an toàn khi gọi từ nhiều luồng.
 */
object LockEngine {
    private val mutex = Any()

    /** Kết quả một lần tick, để GuardService quyết định banner / thông báo. */
    data class TickResult(val wholeLocked: Boolean, val verdict: LockPolicy.Verdict)

    /**
     * Gọi mỗi giây từ BẤT KỲ nguồn nào (service nền, lockPoll của Trợ năng).
     * Cộng giờ cho app foreground rồi quyết định và (nếu cần) hiện màn khóa.
     * Gọi dư thì vô hại: cộng giờ theo mốc thời gian thật, không theo số lần gọi.
     */
    fun tick(ctx: Context, store: Store, now: Long): TickResult = synchronized(mutex) {
        store.tick(now)
        decideAndShow(ctx, store, now)
    }

    /**
     * Gọi khi Trợ năng báo cửa sổ đổi sang [pkg]: cộng giờ cho app trước đó, ghi app
     * mới, rồi quyết định (app mới có bị chặn không?). Trả null nếu sự kiện bị bỏ qua
     * (cửa sổ thoáng qua như thanh âm lượng).
     */
    fun onWindowChanged(ctx: Context, store: Store, pkg: String, now: Long): TickResult? =
        synchronized(mutex) {
            if (ForegroundTracker.shouldIgnoreEvent(pkg)) return null
            val prev = store.lastPkg
            // Cộng giờ app cũ rồi mới đổi mốc: mốc luôn đi cùng lần cộng, không đếm đôi.
            if (prev.isNotEmpty()) store.addElapsed(prev, now)
            store.markForeground(pkg, now)
            decideAndShow(ctx, store, now)
        }

    /** Áp kết quả quét cửa sổ (Trợ năng) vào Store theo luật của ForegroundTracker. */
    fun applyDetected(store: Store, detected: ForegroundTracker.Detected, now: Long) =
        synchronized(mutex) {
            if (detected is ForegroundTracker.Detected.App && detected.pkg != store.lastPkg) {
                store.markForeground(detected.pkg, now)
            }
        }

    /** Service khởi động lại: giữ lastPkg, chỉ đặt lại mốc để khoảng ngừng không bị tính. */
    fun onServiceRestart(store: Store, now: Long) = synchronized(mutex) { store.restartClock(now) }

    /**
     * Dùng cho màn hình chính (HomeActivity — chính nó là launcher nên lastPkg là app
     * mình): TV đang khóa thì hiện màn khóa. Trả true nếu đã hiện.
     */
    fun lockIfLockedAtHome(ctx: Context, store: Store, now: Long): Boolean = synchronized(mutex) {
        if (!store.isLockedNow(now)) return false
        show(ctx, store.lockReason(now), wholeTv = true)
        true
    }

    /** Khóa nhanh do người dùng bấm (nút remote đã gán). Không đổi trạng thái khóa. */
    fun showManualLock(ctx: Context, reason: String) = synchronized(mutex) {
        show(ctx, reason, wholeTv = false)
    }

    // ---------------- nội bộ ----------------

    private fun decideAndShow(ctx: Context, store: Store, now: Long): TickResult {
        val d = store.evaluateLock(now)
        val v = d.verdict
        if (v is LockPolicy.Verdict.Lock) show(ctx, v.reason, v.wholeTv)
        return TickResult(d.wholeLocked, v)
    }

    /** Nơi DUY NHẤT dựng Intent mở màn khóa. */
    private fun show(ctx: Context, reason: String, wholeTv: Boolean) {
        val i = Intent(ctx, LockActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        i.putExtra("reason", reason)
        i.putExtra("mode", "lock")
        i.putExtra("wholeLock", wholeTv)
        LockActivity.showLock(ctx, i)
    }
}
