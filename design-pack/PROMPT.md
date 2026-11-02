Bạn là kỹ sư Android giàu kinh nghiệm về Android TV. Hãy thiết kế lại toàn bộ giao diện app "Khóa TV" (package vn.tvlock, Kotlin, View/XML thuần, không Compose) cho TV Xiaomi A Pro 55" 4K và giao diện web trên điện thoại. KHÔNG đổi logic nghiệp vụ.

## Tài liệu đi kèm (đọc trước)
- DESIGN-SPEC.md và design-tokens.json: nguồn chân lý về màu, cỡ chữ, kích thước, focus, kiến trúc.
- design/*.html: bản dựng tham chiếu 4 màn (mở bằng trình duyệt); screenshots/*.png: ảnh của chúng.
- reference/current-app.md và reference/current-layouts/: phân tích app hiện tại (từ APK, id dạng hex).

## Bước 0: Đọc mã nguồn rồi mới sửa
Đọc MainActivity, SettingsActivity, SchedulesActivity, ScheduleEditActivity, DailyLimitsActivity, NewsSlotActivity, AppRulesActivity, LockScreenActivity, LockActivity, PinActivity, BonusActivity, AccessActivity, WarningActivity, WarningOverlay, WarningBus, HoldRepeat, Store, GuardService, WebServer và toàn bộ res/. Viết kế hoạch ngắn (file sẽ sửa, thứ tự làm) trước khi code.

## Ràng buộc bất biến
- Không đổi Store, GuardService, GuardAccessibilityService, các Receiver, hay hành vi khóa/mở khóa/PIN/lịch/giới hạn.
- Không đổi đường dẫn và tham số của endpoint web hiện có (danh sách trong reference/current-app.md). Được THÊM endpoint JSON mới.
- Giữ nguyên chuỗi tiếng Việt hiện có; giữ id view mà code đang tham chiếu (hoặc cập nhật mọi chỗ dùng). Giữ HoldRepeat.

## Việc cần làm
1. Theme tập trung từ design-tokens.json: colors.xml, dimens.xml, styles.xml. Nút TV 4 biến thể (primary, success, danger, ghost), trạng thái focus (viền trắng 4dp, vòng sáng hổ phách, phóng 1.06, StateListAnimator) và pressed riêng.
2. Component dùng lại: StatusPill, Card, ListRow, TvButton, TimeStepper, SectionHeader.
3. Trang chủ (MainActivity) theo design/tv-home.html. Nút phụ cũ chuyển vào Cài đặt hoặc thẻ phù hợp, không rải rác.
4. Cài đặt theo design/tv-settings.html (rail 250dp + pane). Xây SettingsRegistry (id, tiêu đề, icon, màn đích). Có thể dùng một Activity + Fragment hoặc giữ các Activity nhưng dùng chung khung rail+pane.
5. Các màn con (Lịch khóa, Sửa lịch, Giới hạn ngày, Giờ thời sự, Giới hạn từng app, Màn hình khóa, PIN, Phút thưởng, Trợ năng): dùng Card, ListRow (72dp), TimeStepper. Thay ListView thô bằng ListRow.
6. Màn khóa (LockActivity) theo design/tv-lock.html; sau PIN đúng hiện thẻ chọn thao tác cùng phong cách; giữ ảnh nền và lời nhắn tùy chỉnh.
7. Cảnh báo còn 5 phút (WarningActivity, WarningOverlay, view_warning_banner): banner trong vùng an toàn, nền thẻ + viền hổ phách, chữ 22sp.
8. Web: trang tĩnh assets/web/{index.html,app.css,app.js}, WebServer phục vụ tại "/"; giữ trang cũ ở /legacy tới khi xong. Dùng /api/status (JSON) cập nhật bằng fetch vài giây một lần; thao tác gọi fetch rồi cập nhật tại chỗ, có toast thành công/lỗi. Mobile-first 390px, tab dưới (Hôm nay, Lịch, Giới hạn, Cài đặt), nút cao tối thiểu 48px, CSS variables theo design-tokens.json, tab và mục cài đặt đọc từ một mảng cấu hình trong app.js.
9. Kiểm tra web server: nếu chưa yêu cầu xác thực, hãy ĐỀ XUẤT (đừng tự thêm nếu làm đổi hành vi) yêu cầu PIN hoặc token.

## Quy tắc TV 4K
Chỉ dùng dp/sp. Khung tham chiếu 960x540dp, kiểm tra thêm 1280x720dp. Lề an toàn 48dp trái/phải, 27dp trên/dưới. Mọi phần tử bấm được phải focusable, có nextFocus rõ, focus ban đầu hợp lý, Back lùi một cấp. Chữ tối thiểu 16sp.

## Tiêu chí nghiệm thu
- assembleDebug thành công, không cảnh báo thiếu id.
- Mọi màn TV điều hướng hoàn toàn bằng D-pad, focus nhìn rõ từ 3 m, không nội dung nằm ngoài vùng an toàn.
- Chạy trên emulator Android TV 1080p và 4K; chụp từng màn lưu vào docs/screenshots.
- Web dùng tốt ở 360 đến 430px, không cuộn ngang, thao tác không tải lại trang.
- Chứng minh khả năng mở rộng: thêm một mục cài đặt giả "Demo" chỉ bằng 1 dòng vào SettingsRegistry và 1 panel, rồi xóa đi.

## Cách làm việc
Làm theo giai đoạn, commit sau mỗi giai đoạn: (1) theme + component, (2) Trang chủ, (3) Cài đặt + màn con, (4) Màn khóa + cảnh báo, (5) Web. Cuối cùng tóm tắt: file đã đổi, điều chưa chắc chắn, cách kiểm thử trên TV thật.
