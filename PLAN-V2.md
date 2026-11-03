# PLAN-V2 — Redesign Cài đặt TV + Web (theo DESIGN-SPEC-v2.md)

## Nguyên tắc
- KHÔNG đổi: HomeActivity, LockActivity layout; Store.kt, GuardService, GuardAccessibilityService, Receivers; endpoint web hiện có; chữ tiếng Việt; id view đang được code dùng.
- KHÔNG commit: keystore, token, secret.

## Giai đoạn 1 — Token + component dùng chung
1. `res/values/dimens.xml`: lề an toàn 24/16dp; title 22sp / body 16sp / caption 13sp (styles); rail 200dp; rail item 36dp gap 4dp bo 10dp; ListRow 52dp bo 12dp; nút 40dp; toggle 44x24dp; pane bo 18dp padding 20dp; tab pill 34dp bo 17dp; focus viền trắng 3dp + glow accent 45% + scale 1.03.
2. `res/values/styles.xml`: TvTitle 22sp, TvBody 16sp, TvCaption 13sp, TvButton height 40dp text 16sp, focus_scale 1.03.
3. `ListRows.kt`: row 52dp theo dimen mới (đang 72dp).
4. `TimeStepper.kt`: giữ nguyên logic, kiểm tra nút 40dp.
5. ToggleRow mới (dùng chung): hàng 52dp + Switch 44x24dp — viết `ToggleRows.kt` tương tự ListRows.
6. Commit.

## Giai đoạn 2 — Khung Cài đặt TV 2 cấp + Registry
1. `SettingsRegistry.kt`: thêm `groups` (lock/launcher/system + tiêu đề), mỗi Item có `group`; tách HomeSettings thành 6 section (anh_nen, widget, dem_nguoc, thong_bao_day, kieu_icon_thoi_tiet, ung_dung_an) dùng chung HomeSettingsActivity + extra `section`.
2. `SettingsFrame` mới (thay SettingsRail từng Activity tự dựng): layout `settings_frame.xml` — hàng trên (tiêu đề "Cài đặt" 22sp + pill tabs + gợi ý phím 13sp), dưới: rail 200dp + pane container. Class `SettingsFrame.kt` dựng tab/rail, xử lý D-pad (◀▶ đổi nhóm ở tab, ▼ vào rail, ▶ vào pane, Back lùi cấp).
3. Mỗi Activity cài đặt: đổi layout — thay `railContainer` bằng `<include layout="@layout/settings_frame"/>`, pane cũ giữ nguyên id. Sửa code: `SettingsFrame.bind(this, "<item_id>")` thay `SettingsRail.build(...)`.
4. `SettingsActivity` (tổng quan): giữ dashboard, dùng frame mới, không chọn mục nào.
5. Giữ `SettingsRail.kt` cũ cho tới khi mọi màn chuyển xong rồi xóa.
6. Test mở rộng: thêm mục "Demo" giả vào nhóm lock (1 dòng Registry + pane trống), kiểm tra hiện đúng, rồi xóa.
7. Commit.

## Giai đoạn 3 — Các màn con khớp component mới
- Schedules, ScheduleEdit, DailyLimits, NewsSlot, AppRules: ListRow 52dp + nút 40dp.
- LockScreen, Pin, WebPin, Bonus, Access, ScreenCapture: nút 40dp, chữ 16/13sp.
- HomeSettings 6 section: mỗi section là pane riêng (Ảnh nền / Widget / Đếm ngược / Thông báo đẩy / Kiểu icon & thời tiết / Ứng dụng đã ẩn).
- HiddenApps: thêm vào frame (trước đây không có rail).
- Kiểm tra 960x540dp: đủ mục không cuộn, không chữ <13sp, không cắt chữ.
- Commit.

## Giai đoạn 4 — Web điện thoại
1. `index.html`: 4 tab (Hôm nay, Quy tắc, Màn hình chính, Cài đặt) + icon SVG inline, bỏ emoji.
2. `app.css`: biến màu theo token, mobile-first 360–430px, nút ≥48px, không cuộn ngang.
3. `app.js`:
   - Tab Hôm nay: thẻ trạng thái ("1 giờ 12 phút"), Khóa ngay/Mở khóa, chip +5..+30, gửi tin 1 dòng, mã thưởng gọn, thẻ thời sự/khóa đêm/phút thưởng, báo cáo thu gọn + "Xem 7 ngày".
   - Tab Quy tắc (mới): gộp Lịch + Giới hạn cũ + Giờ thời sự + Giới hạn app + Phút thưởng + Màn hình khóa + Mã thưởng; dòng 56px + giá trị hiện tại; hash route #rules/<id> + nút quay lại.
   - Tab Màn hình chính: các panel launcher hiện có (ảnh nền, widget, đếm ngược, thông báo đẩy, kiểu icon & thời tiết, app ẩn).
   - Tab Cài đặt: nhóm Bảo mật & công cụ (PIN TV, PIN web, trợ năng, chụp màn hình) + đăng xuất.
   - Bỏ: nhãn "Phòng khách" → "Đã kết nối TV"; bỏ link /legacy; bỏ "1g 12p".
   - SETTING_GROUPS đồng bộ 3 nhóm TV.
4. WebServer.kt: KHÔNG đổi endpoint cũ; thêm endpoint JSON mới nếu cần (giá trị hiện tại cho dòng Quy tắc).
5. Commit.

## Nghiệm thu
- Build thành công, không warning thiếu id.
- Chụp màn Cài đặt TV (2 nhóm) + web, lưu docs/screenshots-v2.
- Báo cáo cuối: file đã đổi, điều chưa chắc, cách thử trên TV thật.
