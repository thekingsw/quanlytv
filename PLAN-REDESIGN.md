# Kế hoạch redesign Khóa TV 2.2 (theo design-pack của anh)

Nguồn chân lý: `design-pack/DESIGN-SPEC.md` + `design-pack/design-tokens.json`.
Mục tiêu: chỉ đổi giao diện, KHÔNG đổi logic khóa/mở/PIN/lịch/giới hạn.

## Token (design-tokens.json)
- Màu: bg #0E1525, card #172035, card2 #202C45, text #F3F6FB, muted #9FB0CC,
  accent #FFB84D, onAccent #1A1405, open #3DDC97, onOpen #06261A,
  locked #FF6B6B, onLocked #2A0A0A, track #26324D.
- Bo: card 24dp, button 14dp, pill 20dp. Cao: button 52dp, button nhỏ 44dp,
  list row 72dp, phím keypad 56dp, rail 250dp. Lề an toàn 48dp ngang / 27dp dọc.
- Chữ: hero 64sp, lock message 48sp, title 28sp, body 20–22sp, caption 16–18sp, min 16sp.
- Focus: viền trắng 4dp + vòng sáng hổ phách rgba(255,184,77,.45) + phóng 1.06.
- Khung tham chiếu TV 960x540dp, kiểm tra thêm 1280x720dp. Chỉ dùng dp/sp.

## Ràng buộc bất biến
- KHÔNG sửa Store.kt, GuardService.kt, GuardAccessibilityService.kt, các
  Receiver, hay hành vi khóa/mở/PIN/lịch/giới hạn. Chỉ đổi giao diện.
- KHÔNG đổi đường dẫn/tham số endpoint web hiện có (xem reference/current-app.md).
  Được THÊM endpoint JSON mới.
- Giữ nguyên chuỗi tiếng Việt hiện có; giữ id view mà code đang tham chiếu
  (đổi tên id thì cập nhật MỌI chỗ dùng). Giữ HoldRepeat.kt.
- Mọi phần tử bấm được phải focusable, focus rõ, nextFocus rõ, focus ban đầu
  hợp lý, Back lùi một cấp.
- Version: AndroidManifest versionCode 13, versionName "2.2" (bump ở giai đoạn 1).
- Build: `bash ~/workspace/manual-build/build_tvlock.sh` (KHÔNG Gradle).
  Verify: apksigner verify + `aapt dump badging` (versionCode 13 / 2.2).
  Commit sau mỗi giai đoạn: `redesign 2.2 phase N: ...`. Giai đoạn sau chỉ
  bắt đầu khi giai đoạn trước SIGN_OK. KHÔNG push GitHub (không có remote).

## Lưu ý phát hiện ở Bước 0 (để các giai đoạn xử lý)
1. `styles.xml` tham chiếu `@color/btn_text` nhưng colors.xml KHÔNG định nghĩa
   màu này — giai đoạn 1 kiểm chứng build, định nghĩa thêm hoặc bỏ tham chiếu.
2. `build_tvlock.sh` KHÔNG đóng gói `assets/` (aapt2 link chỉ có res + manifest,
   rồi zip dex vào APK). Giai đoạn 5 phải thêm bước zip `assets/` vào APK
   trước zipalign/sign (sửa tối thiểu trong build script).
3. Không có emulator Android TV ở đây — KHÔNG kiểm chứng được D-pad/focus trên
   thiết bị thật; ghi rõ vào báo cáo cuối + hướng dẫn kiểm thử trên TV thật.

## Store API cần cho giao diện mới (đọc từ Store.kt, KHÔNG sửa Store)
- `isLockedNow(now)`, `remainingMin()` (Int.MAX_VALUE = không giới hạn),
  `limitForToday()`, `usedTodayMin`, `bonusMin`, `newsStartMin/newsEndMin`,
  `fmt(min)` → "HH:MM", `lockSchedules()`, `scheduleTimeText(s)`,
  `todayScheduleSummaries(now)`, `lockMessage`, `hasLockBg()`, `lockBgFile()`,
  `isInNewsSlot(now)`, `newsPkg`, `appLabel(pkg)`, `lockReason(now)`.
- "Khóa đêm" (trang chủ + web): trong lịch hôm nay, ưu tiên lịch qua đêm
  (startMin > endMin); nếu không có thì lịch đầu tiên hôm nay; nếu không có
  lịch nào thì "Không có lịch khóa". Hiển thị `fmt(start) + " – " + fmt(end)`.
- Số hero: còn lại ≥60 phút → "X giờ Y phút" (vd "1 giờ 12 phút"); <60 phút →
  "45 phút"; không giới hạn → "Không giới hạn", thanh tiến độ đầy.
  Tiến độ = đã xem / giới hạn (không giới hạn thì đầy).
- Ngày giờ trang chủ: SimpleDateFormat("EEEE · HH:mm", Locale("vi")) → "Thứ Tư · 19:42".

## Giai đoạn 1 — Theme + component
File: `res/values/colors.xml`, `res/values/dimens.xml` (mới), `res/values/styles.xml`,
`res/drawable/btn_*_selector.xml` (4 biến thể), `res/drawable/btn_*_focus.xml`
(layer-list: lớp ngoài amber 45% + lớp trong màu nền bo tròn + stroke trắng 4dp),
`res/drawable/btn_*_pressed.xml` (tối đi), `res/animator/focus_scale.xml`
(StateListAnimator phóng 1.06 khi focused), `res/drawable/pill_open.xml`,
`pill_locked.xml` (nền open/locked, radius 20dp), `res/drawable/card_bg.xml`
(#172035, radius 24dp), `res/drawable/row_bg.xml` (mới, #202C45, radius 16dp).
Styles: TvButtonPrimary (accent/onAccent), TvButtonSuccess (open/onOpen),
TvButtonDanger (locked/onLocked), TvButtonGhost (card2/text) — mỗi cái có
selector focus/pressed riêng + stateListAnimator. Component dùng lại:
StatusPill (pill_open/pill_locked), Card (card_bg), ListRow 72dp (xem giai đoạn 3),
TimeStepper (helper mới, xem giai đoạn 3 — giai đoạn 1 chỉ chuẩn bị style),
SectionHeader (style: tiêu đề 28sp bold + phụ đề 18sp muted).
Bump manifest 13 / 2.2 ở giai đoạn này. Commit cả `design-pack/` + file PLAN này.

## Giai đoạn 2 — Trang chủ (MainActivity) theo design/tv-home.html
File: `res/layout/activity_main.xml`, `MainActivity.kt` (chỉ phần UI).
Khung 960x540, padding 27dp trên/dưới, 48dp trái/phải, gap 16dp.
- Hàng đầu: "Khóa TV" 28sp bold + ngày giờ "Thứ Tư · 19:42" 20sp muted
  (id mới tvDateTime; cập nhật trong updateUi() mỗi lần refresh 5s).
- Hàng chính 2 thẻ (gap 20dp): thẻ trái flex 1.5 — pill trạng thái (tvStatus,
  ĐANG MỞ xanh / ĐÃ KHÓA đỏ), số lớn 64sp bold (id mới tvRemaining),
  "còn lại hôm nay" 22sp muted, thanh tiến độ (track #26324D, fill xanh theo
  tỷ lệ đã xem/giới hạn; không giới hạn thì đầy — dùng ProgressBar +
  progressDrawable layer-list, hoặc View fill tính width bằng code),
  dòng dưới "Đã xem X phút" / "Giới hạn Y giờ" 18sp muted (id mới tvUsedLine).
  Thẻ phải "Hôm nay" 20sp bold: 3 dòng (nhãn 16sp muted + giá trị 22sp
  semibold): "Giờ thời sự" (fmt(newsStartMin)–fmt(newsEndMin)), "Khóa đêm"
  (logic ở trên), "Phút thưởng" (bonusMin + " phút").
- Hàng nút dưới (cao 52dp, 20sp semibold): "Khóa ngay" danger, "Mở khóa"
  success, "+5" "+10" "+15" "+30" ghost, "Cài đặt" ghost. Focus ban đầu ở
  "Khóa ngay" (giữ onResume requestFocus btnLock).
- BỎ khỏi trang chủ: btnNews ("Mở app thời sự" → màn Giờ thời sự, giai đoạn 3),
  btnAccess ("Bật Trợ năng" → màn Trợ năng, đã có), btnAdd20 (+20 vẫn còn
  trong màn khóa sau PIN và web). Xóa listener tương ứng trong MainActivity.kt;
  giữ nguyên logic khóa/mở/thưởng (btnLock/btnUnlock/btnAdd5/10/15/30/btnSettings)
  và id view còn dùng (tvStatus, btnLock, btnUnlock, btnAdd5/10/15/30, btnSettings).
  Các id cũ tvUsed/tvTimes/tvNews có thể bỏ nếu layout mới không dùng — nhưng
  phải xóa MỌI findViewById tham chiếu chúng trong MainActivity.kt.
- Khuyến nghị: GIỮ dòng địa chỉ web tvWeb ("Điều khiển từ điện thoại (cùng WiFi):
  http://...:8080") dạng 16sp muted ở dưới hàng nút — design không vẽ nhưng đây
  là chỗ duy nhất cho anh biết địa chỉ web; nếu bỏ thì phải ghi rõ vào báo cáo.
- Kiểm tra tràn: tổng chiều cao (27+~34+16+~300+16+52+27) phải vừa 540dp.

## Giai đoạn 3 — Cài đặt rail+pane + các màn con
File mới: `SettingsRegistry.kt` (danh sách id, tiêu đề, Activity đích cho 8 mục:
Lịch khóa → SchedulesActivity, Giới hạn ngày → DailyLimitsActivity,
Giờ thời sự → NewsSlotActivity, Giới hạn từng app → AppRulesActivity,
Màn hình khóa → LockScreenActivity, Mã PIN → PinActivity,
Phút thưởng → BonusActivity, Trợ năng → AccessActivity),
`SettingsRail.kt` (helper: dựng rail 250dp vào một container — tiêu đề "Cài đặt"
28sp bold + các mục 44dp, radius 12dp, 20sp; mục đang chọn nền accent + chữ đậm
màu onAccent, mục khác chữ #C9D3E6; bấm mục khác → mở Activity đích + finish()
Activity hiện tại), `TimeStepper.kt` (helper mới: bind 4 nút giờ−/giờ+/phút−/phút+
với get/set lambda + refresh callback, dùng HoldRepeat.attach — thay cho
bindHourMin() đang trùng lặp trong ScheduleEditActivity và NewsSlotActivity),
`res/layout/settings_rail.xml` (khung rail dùng lại), `res/layout/list_row.xml`
(ListRow 72dp: nền row_bg.xml, padding 24dp, tiêu đề 22sp semibold + chi tiết
22sp muted + "›", focusable + focus selector rõ), `res/layout/activity_settings.xml`
(rail + pane tổng quan: trạng thái hiện tại, đã xem/giới hạn hôm nay, gợi ý chọn mục).
File sửa: `SettingsActivity.kt` (dùng rail + pane tổng quan), `SchedulesActivity.kt`,
`DailyLimitsActivity.kt`, `AppRulesActivity.kt` (bỏ ListView thô → dựng ListRow
bằng code trong LinearLayout, giữ nguyên onItemClick/showDialog logic),
`ScheduleEditActivity.kt`, `NewsSlotActivity.kt` (dùng TimeStepper; giữ nguyên
logic lưu/xóa, spinner app thời sự), `LockScreenActivity.kt`, `PinActivity.kt`,
`BonusActivity.kt`, `AccessActivity.kt` (chỉ đổi layout sang rail+pane, giữ nguyên
mọi logic — riêng LockScreenActivity giữ nguyên nháp→Xem trước→Áp dụng).
Mỗi màn con: rail trái + pane thẻ phải (radius 24dp, padding 28dp, tiêu đề 28sp
bold + phụ đề 18sp muted), chân trang gợi ý phím 16sp muted:
"◀ ▶ ▲ ▼ Di chuyển · OK Chọn · Back Quay lại". Back lùi một cấp (finish()).
NewsSlotActivity thêm nút "Mở app thời sự" (chuyển từ trang chủ — mở app
newsPkg bằng getLaunchIntentForPackage, toast khi chưa chọn/không mở được,
giữ đúng chuỗi toast cũ của MainActivity).
**Chứng minh mở rộng (bắt buộc, cuối giai đoạn 3):** thêm mục giả "Demo" 1 dòng
vào SettingsRegistry + 1 panel đơn giản, build thử (phải SIGN_OK), rồi XÓA ĐI
trước khi kết thúc giai đoạn. Ghi kết quả vào báo cáo giai đoạn.

## Giai đoạn 4 — Màn khóa + cảnh báo
File: `res/layout/activity_lock.xml`, `LockActivity.kt` (chỉ phần UI),
`res/drawable/ic_moon.xml` (mới, layer-list: hình tròn accent 72dp + hình tròn
màu nền lệch góc tạo lưỡi liềm), `res/layout/activity_warning.xml`,
`res/layout/view_warning_banner.xml` (token: nền thẻ #172035 + viền hổ phách,
chữ 22sp, trong vùng an toàn).
LockActivity theo design/tv-lock.html: khung 960x540, padding 27/48, 2 cột gap 40dp.
- Cột trái flex 1.2: nếu có ảnh nền riêng thì hiện ảnh full-bleed + lớp tối +
  lời nhắc đè lên (GIỮ hành vi v1.8 — ivLockBg + tvLockMessage, refreshLockLook());
  nếu không có ảnh thì hiện icon mặt trăng (ic_moon.xml) + lời nhắc 48sp bold
  (tvLockMessage) + dòng phụ là lý do khóa 22sp muted (tvReason — chuyển sang
  cột trái, bỏ style pill) + nút "Mở thời sự" 60dp success 22sp (btnNews, chỉ
  hiện khi đúng khung giờ thời sự — GIỮ logic refreshNewsButton()/openNewsApp()).
- Cột phải là thẻ (nền card, radius 24dp, padding 24dp): tiêu đề
  "Dành cho bố mẹ · Nhập mã PIN" 18sp muted (tvClock — giữ id, đổi text mặc định),
  hàng chấm PIN 36sp (tvPin — đổi updatePinDisplay(): hiển thị "●" cho số đã
  nhập và "○" cho số chưa nhập, tối đa 6 chấm), bàn phím 3x4 phím 56dp radius
  14dp nền card2 chữ 24sp bold (key0–key9), phím OK nền accent (keyOk),
  phím Xóa chữ 20sp (keyDel, text "Xóa").
- Thẻ chọn thao tác sau PIN đúng GIỮ NGUYÊN nội dung (optAdd5/10/15/20/30,
  optUnlockEnd "Mở đến hết khung khóa", btnOpenSettings "Mở Cài đặt", optCancel
  "Hủy", btnCancel) nhưng dùng style nút mới. GIỮ NGUYÊN mọi id view và logic
  PIN/chế độ (lock/unlock/add/bonus/preview, onKeyDown số từ remote, onBackPressed).
- Cảnh báo: WarningActivity + view_warning_banner giữ nguyên id
  (tvWarnTitle, tvWarnSub, btnWarnOk, tvBannerTitle, tvBannerSub) và logic;
  chỉ đổi màu/chữ theo token. WarningOverlay đặt banner y=27dp (lề an toàn trên)
  thay vì 24dp hiện tại.

## Giai đoạn 5 — Web
File mới: `android/app/src/main/assets/web/{index.html,app.css,app.js}`.
File sửa: `WebServer.kt`, `~/workspace/manual-build/build_tvlock.sh`
(thêm bước zip assets/ vào APK trước zipalign/sign — xem lưu ý 2).
- WebServer phục vụ "/" = index.html (và /index.html, /app.css, /app.js với đúng
  Content-Type: text/html, text/css, application/javascript); giữ trang cũ ở
  "/legacy" (homePage cũ) và "/legacy/settings" (settingsPage cũ).
- Mọi endpoint /api/* hiện có GIỮ NGUYÊN. Được THÊM endpoint JSON mới nếu cần
  (vd GET /api/schedules trả toàn bộ lịch, GET /api/limits trả giới hạn từng ngày,
  GET /api/settings trả giờ thời sự + lời nhắc + trạng thái chống tắt).
  /api/status đã trả JSON đầy đủ (locked, remainingMin, usedTodayMin,
  dailyLimitMin, todaySchedules, bonusMin, newsSlot, antiDisable, a11yEnabled...).
- app.js: tab dưới đọc từ một mảng cấu hình [Hôm nay, Lịch, Giới hạn, Cài đặt];
  mục cài đặt đọc từ một mảng cấu hình (8 mục như SettingsRegistry).
- Trang Hôm nay theo design/phone-home.html (390px): thẻ trạng thái pill +
  "1g 12p" 44sp + tiến độ, 2 nút Khóa ngay/Mở khóa 56px, nhãn "CỘNG THÊM GIỜ",
  chip +5/+10/+15/+20/+30 cao 48px, thẻ thông tin Giờ thời sự/Khóa đêm,
  thẻ Báo cáo từ /api/report, tab dưới.
- Dùng /api/status (JSON) fetch mỗi 5 giây để cập nhật tại chỗ; thao tác gọi
  fetch POST rồi cập nhật tại chỗ + toast thành công/lỗi, KHÔNG tải lại trang.
- GIỮ hành vi xem trước + nút "Áp dụng lên TV" của thẻ Màn hình khóa từ bản 2.1
  (dùng /api/lockbg, /api/settings-lockmsg, /api/settings-lockbg) trong tab Cài đặt.
- CSS variables lấy từ design-tokens.json (bg, card, card2, text, muted, accent,
  open, locked, track). Mobile-first 360–430px, không cuộn ngang, nút cao tối
  thiểu 48px. KHÔNG thêm xác thực (web hiện không yêu cầu PIN/token — chỉ ghi
  đề xuất này vào báo cáo cuối, đừng tự thêm vì làm đổi hành vi).

## Kiểm tra cuối (sau giai đoạn 5)
- Mọi id mà code Kotlin tham chiếu đều tồn tại trong layout; không còn hardcode
  màu/cỡ cũ trong các layout đã sửa; dex chứa đủ Activity mới/cũ
  (dexdump grep Lvn/tvlock/); apksigner verify OK; versionCode 13 / 2.2.
- Cập nhật HUONG_DAN.md + README.md (phiên bản 2.2, thay đổi giao diện, /legacy).
- Báo cáo cuối cho agent chính: file đã đổi theo từng giai đoạn; điều chưa chắc
  chắn (không có emulator/TV thật để kiểm D-pad/focus); cách kiểm thử trên TV
  thật (cài qua Cài TV, kiểm tra từng màn); đề xuất xác thực web (PIN/token) —
  chỉ đề xuất, KHÔNG triển khai; kết quả chứng minh mở rộng "Demo".
