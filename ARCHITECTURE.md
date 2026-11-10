# Kiến trúc Khóa TV — "một cửa" cho quyết định khóa

Mục tiêu: sửa một chỗ không làm hỏng chỗ khác. Các quy tắc dưới đây được
`ArchitectureTest` (android/app/src/test) kiểm bằng cách đọc mã nguồn — test đỏ nghĩa là
có ai đó rải logic khóa ra nhiều chỗ. Đừng sửa test cho xanh; hãy đưa logic về đúng chỗ.

## Ai làm gì

| Lớp | Việc | Android? | Test |
|---|---|---|---|
| `LockPolicy` | Luật khóa + đếm giờ. `evaluate()` là QUYẾT ĐỊNH DUY NHẤT hiện màn khóa hay không | không | LockPolicyTest, LockDecisionTest |
| `SchedulePolicy` | Luật lịch khóa (qua đêm, cả ngày, chồng nhau) | không | SchedulePolicyTest |
| `ForegroundTracker` | Luật "app nào đang foreground" | không | ForegroundTrackerTest |
| `PinGuard` | Đếm sai PIN / khóa tạm, dùng chung cho mọi loại mã | không | PinGuardTest |
| `LockEngine` | CỬA DUY NHẤT: đọc state → gọi luật thuần → áp kết quả + mở màn khóa | có (chỉ nối) | ArchitectureTest |
| `Store` | Lưu/đọc SharedPreferences, ủy quyền cho các Policy | có | — |
| `GuardService`, `GuardAccessibilityService`, `HomeActivity` | Chỉ GỌI LockEngine | có | — |

## 4 quy tắc cứng

1. Chỉ `LockEngine` dựng Intent mở `LockActivity` với `mode = "lock"`.
2. Chỉ `Store.markForeground` / `restartClock` ghi `lastPkg` (setter là `private`); chỉ
   `LockEngine` gọi chúng.
3. Muốn đổi luật khóa → sửa `LockPolicy.evaluate` (hoặc `SchedulePolicy` / `ForegroundTracker`)
   và thêm test trước. Không thêm điều kiện khóa ở Service / Activity.
4. Mọi hàm public của `LockEngine` chạy dưới một khóa `synchronized` — tick của GuardService
   và lockPoll của Trợ năng gọi song song nhưng không giẫm lên nhau.

## Thứ tự ưu tiên trong `evaluate` (đừng đổi nếu không có test mới)

1. đang mở tạm (PIN / mã thưởng / +phút) → Allow
2. chính app mình (màn khóa, màn hình chính, Cài đặt của app) → Allow
3. TV đang khóa toàn bộ → Lock(whole) — kể cả đang ở Cài đặt / launcher hệ thống
4. app hệ thống → Allow · 5. app thời sự trong khung giờ → Allow
6. quy tắc riêng của app / hết giờ app → Lock(per-app) · 7. còn lại → Allow

## Chạy test

Gradle: `gradle :app:testDebugUnitTest` (CI chạy bước này). Test thuần JVM, không cần thiết bị.

## Cần thử trên TV thật (JVM không kiểm được)

Popup âm lượng khi đang xem; giờ khóa + Cài đặt của app; giờ khóa + Cài đặt hệ thống;
TV ngủ rồi bật lại (số phút đã xem không nhảy).
