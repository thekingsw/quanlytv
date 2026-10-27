# Khóa TV — README

App Android TV (Google TV) quản lý thời gian xem TV chung cho cả nhà, không dùng Family Link.

- Package: `vn.tvlock`, versionCode 3, versionName 1.2, minSdk 26, targetSdk 34.
- Ngôn ngữ: Kotlin, **View system (XML layout), không Compose, không AndroidX** — chỉ cần kotlin-stdlib + android.jar.
- APK: `~/workspace/your_files/tv-lock/khoa-tv.apk` (đã ký bằng debug keystore dùng chung trong `~/workspace/manual-build/debug.keystore`).
- Build thủ công: `~/workspace/manual-build/build_tvlock.sh` (không dùng Gradle — VM chặn Gradle daemon và Java HTTPS; compile bằng K2JVMCompiler trực tiếp, aapt2/d8/zipalign/apksigner từ build-tools 34.0.0).

## Kiến trúc

| File | Vai trò |
|---|---|
| `Store.kt` | SharedPreferences `tvlock`: PIN, giới hạn ngày, khung khóa, khung thời sự, app thời sự, khóa thủ công, unlockUntil, số phút đã xem hôm nay. **v3:** phút thưởng `bonusMin` (không hết hạn khi sang ngày), quy tắc từng app (`appRules`: limit/free/blocked), số phút từng app hôm nay (`perAppToday`), lịch sử 14 ngày (`history`), mốc đã cảnh báo (`warned`). Logic thuần: `isInLockWindow`, `isInNewsSlot`, `isLockedNow`, `remainingMin` (giới hạn ngày + thưởng), `blockReason(pkg, now, label)` (thứ tự ưu tiên: thời sự > mở tạm bằng PIN > blocked/limit từng app > khóa thủ công/khung khóa/giới hạn ngày), `minutesUntilLockWindow`, cộng dồn thời gian xem theo từng phút (app "free" không cộng vào giới hạn ngày; phút vượt giới hạn ngày trừ dần từ quỹ thưởng). |
| `GuardAccessibilityService.kt` | AccessibilityService (`typeWindowStateChanged`): theo dõi app foreground, cộng dồn thời gian cho app cũ, mở `LockActivity` khi app mới bị chặn (lý do từ `Store.blockReason`). |
| `GuardService.kt` | Foreground service (START_STICKY, `foregroundServiceType="specialUse"`): tick mỗi 15 giây để cộng dồn thời gian khi xem lâu một app và kích hoạt khóa khi tới giờ/hết hạn; **v3:** kiểm tra ngưỡng cảnh báo 5'/1' (giới hạn ngày tính cả thưởng, giới hạn riêng của app đang xem, tới giờ khóa) và mở `WarningActivity`; chạy WebServer; notification ongoing hiện trạng thái (kèm số phút thưởng còn lại). |
| `WarningActivity.kt` | **v3:** bảng nhắc nhẹ phía trên màn hình (nền tối, chữ lớn), tự tắt sau 20 giây hoặc khi bấm Đã hiểu/Back; không chặn việc xem, không tính giờ. |
| `AppRulesActivity.kt` | **v3:** liệt kê app launchable đã cài, bấm vào một app để chọn: Bình thường / Giới hạn riêng 15/30/60' / Không tính giờ / Chặn hẳn; lưu xong áp dụng ngay. |
| `WebServer.kt` | HTTP server tự viết bằng `java.net.ServerSocket` cổng 8080 (không dùng `com.sun.net.httpserver` vì Android không có): `GET /` trang điều khiển mobile-first (nền tối, nút lớn), `GET /api/status` JSON, `POST /api/lock`, `/api/unlock`, `/api/add` — từ v2 KHÔNG yêu cầu PIN trên điện thoại (điện thoại do bố cầm); `POST /api/change-pin` đổi PIN mới (4–6 số, không cần PIN cũ). Màn hình khóa trên TV vẫn yêu cầu PIN như cũ. **v3:** `POST /api/bonus` (+5/+10/+15/+30 phút thưởng, không cần PIN), `GET /api/report` JSON `{today:{date,total,perApp}, days:[{date,total}]}`, mục **Thưởng phút** và **Báo cáo** (bảng hôm nay theo app + 7 ngày gần nhất dạng thanh ngang) trên trang chủ. |
| `MainActivity.kt` | Màn hình chính TV: trạng thái, đã xem/còn lại, giờ khóa, giờ thời sự, địa chỉ web `http://IP:8080`, nút Khóa ngay / Mở khóa / +5…+30 / Cài đặt / Mở app thời sự / Bật Trợ năng. **v3:** hiện "Còn lại hôm nay: X phút + thưởng Y phút" và dòng nhỏ "Hôm qua đã xem X phút". |
| `LockActivity.kt` | Màn hình khóa toàn màn hình: lý do khóa, bàn phím số trên màn hình (D-pad) + hỗ trợ phím số vật lý KEYCODE_0–9, nhập đúng PIN thì hiện lựa chọn +5…+30, mở đến hết khung khóa, hủy. Nút BACK bị chặn khi đang ở chế độ khóa. **v3:** chế độ `bonus` — nhập đúng PIN thì cộng phút thưởng (dùng từ Cài đặt trên TV). |
| `SettingsActivity.kt` | Đổi PIN, giới hạn ngày (0/30/60/90/120/180), giờ khóa và giờ thời sự (±30 phút), chọn app thời sự (Spinner liệt kê app launchable), khóa thủ công, reset phút hôm nay, trạng thái + nút mở cài đặt Accessibility. **v3:** mục **Giới hạn từng app** (mở AppRulesActivity, hiện tóm tắt các quy tắc đã đặt) và mục **Phút thưởng** (hiện quỹ còn lại + nút Thưởng +5/+10/+15/+30, bấm rồi nhập PIN). |
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

## Bản v3 (1.2) — giới hạn từng app, phút thưởng, nhắc nhẹ, báo cáo

- **#2 Giới hạn từng app:** quy tắc theo app (`appRules` trong Store): Bình thường / Giới hạn riêng 15/30/60 phút/ngày / Không tính giờ (app học) / Chặn hẳn. Đặt trên TV (Cài đặt → Giới hạn từng app); áp dụng ngay. Hết hạn nào trước thì khóa theo lý do đó; khung thời sự 19:00–20:00 vẫn ưu tiên cao nhất.
- **#4 Phút thưởng:** quỹ `bonusMin` không hết hạn khi sang ngày; hết giờ ngày thì trừ dần quỹ, hết cả quỹ mới khóa. Cấp từ web điện thoại (Thưởng +5/+10/+15/+30, không PIN) hoặc trên TV (Cài đặt → Phút thưởng, phải nhập PIN).
- **#5 Nhắc nhẹ trước khi khóa:** còn đúng 5' và 1' nữa là hết giờ ngày (tính cả thưởng), hết giờ riêng của app đang xem, hoặc đến giờ khóa thì hiện `WarningActivity` (banner trên, tự tắt sau 20 giây, bấm Đã hiểu/Back để tắt); mỗi mốc nhắc một lần cho mỗi lần tới hạn; không chặn việc xem.
- **#6 Báo cáo:** lịch sử 14 ngày (tổng phút + phút từng app) trong Store; web điện thoại có mục Báo cáo (bảng hôm nay theo app + 7 ngày gần nhất dạng thanh ngang) và endpoint `GET /api/report`; màn hình chính TV hiện "Hôm qua đã xem X phút".
- Version: versionCode 3, versionName 1.2, cùng keystore ký cố định nên cài đè lên bản v2 được.

## Bản v2 (1.1) — UI mới + ẩn icon khỏi launcher

- UI làm lại: nền tối, nút căn đều theo hàng (layout_weight), nút đang chọn (focus) nền vàng #FFB300 + viền trắng 3dp + chữ đen, nhìn rõ ngay từ xa; nút Mở khóa tông xanh lá.
- Icon launcher **ẩn mặc định**: `MainActivity` không còn intent-filter LAUNCHER/LEANBACK_LAUNCHER; icon chỉ hiện qua activity-alias `.LauncherAlias` với `enabled=false`. Bố mở lại app bằng 1 trong 3 cách: (a) Cài đặt TV → Hệ thống → Trợ năng → Khóa TV → bánh răng (mở SettingsActivity qua `settingsActivity` của AccessibilityService); (b) nhập đúng PIN ở màn hình khóa → **Mở Cài đặt** → **Mở màn hình chính**; (c) `adb shell am start -n vn.tvlock/.MainActivity`. Trong Cài đặt có nút **Hiện/Ẩn icon** để bật lại alias.
- Version: versionCode 2, versionName 1.1, cùng keystore ký cố định nên cài đè lên bản v1 được.

## Mặc định

PIN `1234` · giới hạn ngày 90 phút · khóa 21:00–07:00 · thời sự 19:00–20:00 (chưa chọn app) · thời gian xem app thời sự trong khung thời sự không tính vào giới hạn ngày.

## Giới hạn đã biết

- **Phải bật Accessibility** thì app mới chặn được app khác; nếu tắt, app chỉ còn đếm giờ/khóa qua dịch vụ nền.
- **Ẩn icon không chặn được việc gỡ app** — trẻ vẫn gỡ được từ Cài đặt → Ứng dụng nếu biết cách; chỉ bước ADB Device Owner mới chặn gỡ được.
- Thời gian xem được cộng dồn theo từng phút (phần lẻ dưới 1 phút được giữ lại, không mất).
- Web server chỉ nghe trong mạng WiFi nhà (không có HTTPS/auth ngoài PIN); không dùng cloud.
- D8 in cảnh báo "malformed kotlin.Metadata" (do Kotlin 2.0.20 mới hơn R8 trong build-tools 34) — chỉ là cảnh báo, APK vẫn build và ký thành công.
- Chưa kiểm thử thực tế trên TV thật (chỉ verify ký số và badging trên VM).
