# Khóa TV

App Android TV quản lý thời gian xem TV cho cả nhà, không dùng Family Link. Kiêm luôn màn hình chính (launcher) của TV.

Package `vn.tvlock` · Kotlin, View system (không Compose, không AndroidX) · minSdk 26, targetSdk 34.

## App làm gì

- **Khóa TV bằng mã PIN:** hết giờ xem trong ngày, tới lịch khóa, hoặc bố khóa tay thì hiện màn khóa.
- **Giới hạn xem:** giới hạn phút/ngày (đặt riêng từng ngày trong tuần), giới hạn riêng từng app, chặn hẳn app.
- **Khung thời sự 19:00–20:00:** app thời sự được xem thoải mái, không tính giờ.
- **Mã thưởng:** bố tạo mã trên web, con nhập trên màn khóa để xem thêm vài phút.
- **Điều khiển từ điện thoại:** mở web `http://<IP-TV>:8080` (cần mã PIN web) để khóa/mở, cho thêm giờ, sửa lịch, tạo mã thưởng, gửi tin nhắn lên TV.
- **Màn hình chính:** đồng hồ, ngày dương + âm lịch, thời tiết, app yêu thích, ảnh nền.
- **Gán nút remote:** gán 1 nút trên điều khiển để mở app / khóa TV ngay / gửi tin nhắn.

## Cài đặt

- Build: `~/workspace/manual-build/build_tvlock.sh` (build tay, không Gradle). APK: `~/workspace/your_files/tv-lock/khoa-tv.apk`.
- Cài qua app Cài TV, hoặc `adb install -r khoa-tv.apk`.
- Đặt làm màn hình chính: `cmd package set-home-activity vn.tvlock/.HomeActivity`.
- Phải bật Trợ năng (Accessibility) thì app mới chặn được app khác.
- Mỗi lần push lên GitHub, GitHub Actions tự build APK debug (file workflow phải tự thêm qua web GitHub).

Lưu ý: đẩy code lên GitHub bằng snapshot-push chỉ nhận file text (UTF-8) — ảnh nền (`android/app/src/main/assets/wallpapers/*.jpg`) và ảnh chụp màn hình (`design-pack/screenshots/`, `docs/screenshots-launcher/`) là file nhị phân nên KHÔNG có trên GitHub, chỉ nằm ở máy local.

## Mặc định

PIN `1234` · giới hạn ngày 90 phút · lịch khóa 21:00–07:00 hằng ngày · thời sự 19:00–20:00.

## Giới hạn đã biết

- Phải bật Accessibility thì app mới chặn được app khác; nếu tắt, app chỉ còn đếm giờ/khóa qua dịch vụ nền.
- Ẩn icon không chặn được việc gỡ app — chỉ bước ADB Device Owner mới chặn gỡ được.
- Chụp màn hình TV phải cấp lại quyền sau mỗi lần khởi động lại TV.

## Changelog

### 2026-11-09
- README viết lại cho gọn.

### 2026-11-08
- Tách logic khóa/đếm giờ ra `LockPolicy.kt` (thuần Kotlin) + 36 unit test; Store chỉ đọc prefs rồi ủy quyền cho LockPolicy.
- Sửa màn khóa refresh liên tục 88 lần (chỉ quét cửa sổ nền khi không có cửa sổ nào focus).
- Nhập mã thưởng không còn bị reset về nhập PIN (LockActivity `singleTop`).

### 2026-11-07
- Sửa đếm giờ đứng im sau khi Trợ năng khởi động lại; web hết báo khóa sai lúc xem thời sự.
- Chặn nút remote đã gán mở app khi TV đang khóa.

### 2026-11-06
- Đếm ngược hết nhá 1 tick khi TV vừa bật (chỉ hiện khi deadline ổn định 2 tick).
- Mã thưởng / nút +phút giờ cộng dồn vào thời gian mở tạm đang chạy, không ghi đè.
- Sửa đếm giờ đứng sau khi TV ngủ dậy; cảnh báo mã thưởng hết bị mất.

### 2026-11-05
- Chuyển poll khóa sang Trợ năng (không phụ thuộc GuardService nữa).
- Gán nút remote (mở app / khóa ngay / gửi tin nhắn).
- Sửa thời sự tự khóa; sửa báo cáo web bị trống.

### 2026-11-04
- Chống tắt Trợ năng (tự ghi lại dịch vụ vào danh sách enabled).
- Vào Cài đặt trên TV phải nhập mã PIN.

### 2026-11-03
- Chặn bypass màn khóa: giữ OK, bấm HOME liên tục, popup cấp quyền hệ thống.

### 2026-10-30
- Gộp launcher thành app 2-trong-1: màn hình chính kiểu Apple TV (đồng hồ, thời tiết, yêu thích, ảnh nền).

### 2026-10-28
- Web điều khiển yêu cầu mã PIN web riêng (khác PIN khóa TV).
- Mã thưởng dùng một lần (bố tạo mã, con nhập trên màn khóa).
- Gửi tin nhắn từ web lên TV.

### 2026-10-27
- v4: lịch khóa theo ngày + giới hạn ngày theo ngày.
- v3: giới hạn từng app, phút thưởng, nhắc nhẹ 5'/1', báo cáo.
- v2: giao diện mới, ẩn icon launcher, web điện thoại mới.

### 2026-10-26
- v1: PIN lock, giới hạn ngày, khung thời sự 19:00–20:00, điều khiển qua WiFi.
