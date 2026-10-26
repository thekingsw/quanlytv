# Khóa TV — README

App Android TV (Google TV) quản lý thời gian xem TV chung cho cả nhà, không dùng Family Link.

- Package: `vn.tvlock`, versionCode 1, versionName 1.0, minSdk 26, targetSdk 34.
- Ngôn ngữ: Kotlin, **View system (XML layout), không Compose, không AndroidX** — chỉ cần kotlin-stdlib + android.jar.
- APK: `~/workspace/your_files/tv-lock/khoa-tv.apk` (đã ký bằng debug keystore dùng chung trong `~/workspace/manual-build/debug.keystore`).
- Build thủ công: `~/workspace/manual-build/build_tvlock.sh` (không dùng Gradle — VM chặn Gradle daemon và Java HTTPS; compile bằng K2JVMCompiler trực tiếp, aapt2/d8/zipalign/apksigner từ build-tools 34.0.0).

## Kiến trúc

| File | Vai trò |
|---|---|
| `Store.kt` | SharedPreferences `tvlock`: PIN, giới hạn ngày, khung khóa, khung thời sự, app thời sự, khóa thủ công, unlockUntil, số phút đã xem hôm nay. Logic thuần: `isInLockWindow`, `isInNewsSlot`, `isLockedNow`, cộng dồn thời gian xem theo từng phút. |
| `GuardAccessibilityService.kt` | AccessibilityService (`typeWindowStateChanged`): theo dõi app foreground, cộng dồn thời gian cho app cũ, mở `LockActivity` khi app mới bị chặn. |
| `GuardService.kt` | Foreground service (START_STICKY, `foregroundServiceType="specialUse"`): tick mỗi 15 giây để cộng dồn thời gian khi xem lâu một app và kích hoạt khóa khi tới giờ/hết hạn; chạy WebServer; notification ongoing hiện trạng thái. |
| `WebServer.kt` | HTTP server tự viết bằng `java.net.ServerSocket` cổng 8080 (không dùng `com.sun.net.httpserver` vì Android không có): `GET /` trang điều khiển, `GET /api/status` JSON, `POST /api/lock`, `/api/unlock`, `/api/add` — tất cả yêu cầu PIN đúng. |
| `MainActivity.kt` | Màn hình chính TV: trạng thái, đã xem/còn lại, giờ khóa, giờ thời sự, địa chỉ web `http://IP:8080`, nút Khóa ngay / Mở khóa / +5…+30 / Cài đặt / Mở app thời sự / Bật Trợ năng. |
| `LockActivity.kt` | Màn hình khóa toàn màn hình: lý do khóa, bàn phím số trên màn hình (D-pad) + hỗ trợ phím số vật lý KEYCODE_0–9, nhập đúng PIN thì hiện lựa chọn +5…+30, mở đến hết khung khóa, hủy. Nút BACK bị chặn khi đang ở chế độ khóa. |
| `SettingsActivity.kt` | Đổi PIN, giới hạn ngày (0/30/60/90/120/180), giờ khóa và giờ thời sự (±30 phút), chọn app thời sự (Spinner liệt kê app launchable), khóa thủ công, reset phút hôm nay, trạng thái + nút mở cài đặt Accessibility. |
| `BootReceiver.kt` | `BOOT_COMPLETED` → chạy lại GuardService. |
| `DeviceOwnerReceiver.kt` | DeviceAdminReceiver cho bước chống gỡ tùy chọn bằng ADB (`dpm set-device-owner vn.tvlock/.DeviceOwnerReceiver`); không cần cho khóa/mở bình thường. |
| `res/xml/accessibility_service_config.xml` | Cấu hình AccessibilityService. |

## Build APK bằng GitHub Actions

Mỗi lần push code lên GitHub, GitHub Actions tự build file APK debug
(`.github/workflows/android.yml`, Gradle 8.7, JDK 17).
Vào tab **Actions** → chọn lần chạy mới nhất → tải file `khoa-tv-apk`
về rồi gửi sang TV cài đặt (cho phép cài từ nguồn không xác định).

Lưu ý: file workflow phải do anh tự thêm qua web GitHub (GitHub chặn app
tạo file workflow qua API). Các bản build CI đều ký bằng cùng một debug
keystore cố định (`android/app/debug.keystore.b64`) nên cài đè lên nhau được.

## Mặc định

PIN `1234` · giới hạn ngày 90 phút · khóa 21:00–07:00 · thời sự 19:00–20:00 (chưa chọn app) · thời gian xem app thời sự trong khung thời sự không tính vào giới hạn ngày.

## Giới hạn đã biết

- **Phải bật Accessibility** thì app mới chặn được app khác; nếu tắt, app chỉ còn đếm giờ/khóa qua dịch vụ nền.
- Thời gian xem được cộng dồn theo từng phút (phần lẻ dưới 1 phút được giữ lại, không mất).
- Web server chỉ nghe trong mạng WiFi nhà (không có HTTPS/auth ngoài PIN); không dùng cloud.
- D8 in cảnh báo "malformed kotlin.Metadata" (do Kotlin 2.0.20 mới hơn R8 trong build-tools 34) — chỉ là cảnh báo, APK vẫn build và ký thành công.
- Chưa kiểm thử thực tế trên TV thật (chỉ verify ký số và badging trên VM).
