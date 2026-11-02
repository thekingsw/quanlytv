# App hiện tại (rút ra từ APK khoa-tv, package vn.tvlock)
Chỉ có APK, nên đây là phân tích từ bản build. Agent hãy đối chiếu với mã nguồn thật.

## Activity (13), mọi màn dùng ScrollView/LinearLayout + Button, landscape
MainActivity (LAUNCHER + LEANBACK_LAUNCHER), SettingsActivity, SchedulesActivity, ScheduleEditActivity, DailyLimitsActivity, NewsSlotActivity, AppRulesActivity, LockScreenActivity (tuỳ biến màn khóa), LockActivity (màn khóa bé thấy + PIN), PinActivity, BonusActivity, AccessActivity (trợ năng), WarningActivity.
Khác: WarningOverlay, WarningBus, HoldRepeat (giữ nút ± tăng liên tục), Store, GuardService, GuardAccessibilityService, WebServer, các Receiver (Boot, Update, AntiDisable, DeviceOwner).
Quyền: INTERNET, SYSTEM_ALERT_WINDOW, WRITE_SECURE_SETTINGS, QUERY_ALL_PACKAGES, foreground service...

## Chữ trên màn hình hiện tại (giữ nguyên tiếng Việt)
- Trang chủ: "Khóa TV", "ĐANG MỞ", "Khóa ngay", "Mở khóa", "+5/+10/+15/+20/+30 phút", "Cài đặt", "Mở app thời sự", "Bật Trợ năng"
- Cài đặt: Lịch khóa, Giới hạn ngày, Giờ thời sự, Giới hạn từng app, Màn hình khóa, Mã PIN & khóa thủ công, Phút thưởng, Trợ năng & hiển thị
- Màn khóa: "Hết giờ xem TV rồi con nhé!", "Đã khóa", "Nhập mã PIN để mở khóa", "Mở thời sự", bàn phím 1-9, Xóa, 0, OK, Hủy; sau PIN đúng: +5/+10/+15/+20/+30, "Mở đến hết khung khóa", "Mở Cài đặt", Hủy
- Cảnh báo: "Còn 5 phút nữa là hết giờ xem", "Đã hiểu"
- Sửa lịch: chọn ngày áp dụng, Bắt đầu/Kết thúc (giờ, phút với nút − +), "Khóa cả ngày", "Lưu lịch", "Xóa lịch này"
- Phút thưởng khác +5 phút ở trang chủ: thưởng chỉ có tác dụng khi đã hết giờ ngày.

## Web server (trang HTML tự sinh trong Kotlin)
Endpoint: /api/status, /api/lock, /api/unlock, /api/add (minutes), /api/bonus (minutes), /api/change-pin (newPin), /api/settings-limits, /api/settings-news, /api/settings-schedule, /api/settings-schedule-delete, /api/settings-antidisable, /api/settings-lockmsg (message), /api/settings-lockbg (img base64), /api/settings-lockbg-remove, /api/lockbg, /api/report, /settings.
Hiện tại mỗi thao tác là form POST và tải lại cả trang. CSS cũ: nền #121212, thẻ #1E1E1E, nút đỏ #C62828, xanh #2E7D32, hổ phách #FF8F00, lưới 2 cột.

## Layout XML đã giải mã
Xem reference/current-layouts/ (id hiển thị dạng hex, đối chiếu với mã nguồn).
