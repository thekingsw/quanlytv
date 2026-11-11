# Kế hoạch sửa Khóa TV — chống hồi quy

## TRẠNG THÁI (cập nhật 2026-11-09, sau đợt review lại — ĐÃ KIỂM CHỨNG BẰNG BIÊN DỊCH + TEST)

Đã xong và kiểm chứng (biên dịch toàn project với android.jar thật, 0 lỗi; 110 test
JVM xanh, gồm test "phá thử" để chắc test thật sự bắt được hồi quy):

- `SchedulePolicy` thuần + test (lỗi 23:59 sửa; lịch chồng nhau lấy kết thúc muộn nhất;
  "khóa cả ngày" chỉ định nghĩa ở `SchedulePolicy.isAllDay`).
- `PinGuard` dùng chung cho PIN khóa TV, PIN web, mã thưởng (sai 5 lần khóa 5 phút).
- `ForegroundTracker` (thuần): chỉ BỎ QUA cửa sổ thoáng qua (`systemui`). Settings /
  launcher / chính app mình là màn hình thật nên GHI ĐÈ lastPkg (dừng đếm giờ; việc khóa
  vẫn đè lên chúng nhờ `LockPolicy.evaluate`).
- **`LockPolicy.evaluate` = quyết định khóa DUY NHẤT** (Verdict.Allow / Verdict.Lock) và
  **`LockEngine` = cửa duy nhất** (có `synchronized`) cho GuardService, Trợ năng, HomeActivity.
  `putExtra("mode","lock")` chỉ còn trong LockEngine. Chốt "đang ở app mình thì không mở
  màn khóa đè" nằm trong `evaluate` (thiếu nó thì phụ huynh không dùng được Cài đặt của app
  trong giờ khóa).
- `Store.lastPkg` / `lastPkgStartMs` có `private set`; chỉ `Store.markForeground` /
  `restartClock` ghi (LockEngine gọi). Trình biên dịch chặn ghi tắt.
- Khoảng treo (TV ngủ) > 60 giây giữa 2 lần cộng giờ thì BỎ QUA hẳn
  (`LockPolicy.addElapsedGuarded`) — không kẹp 5 phút nữa vì kẹp vẫn tính oan 5 phút mỗi lần.
- Khóa tay theo ngày (anh yêu cầu thêm 09/11): giữ. `Store.manualLocked` trả giá trị HIỆU
  LỰC (khớp quyết định của LockPolicy) nên UI/web không còn hiện "ĐANG KHÓA" ở hôm sau;
  khóa tay cũ chưa có ngày được coi là bật HÔM NAY (không nhả khóa lúc cập nhật).
- 3 receiver có `permission=DUMP`; `allowBackup=false`; `SecureRandom`; versionCode 1 nguồn.
- WebServer: 413 theo Content-Length (8MB khi đã đăng nhập, 4KB khi CHƯA đăng nhập),
  `jsonEsc`, thread pool 8, cookie `SameSite=Lax`, login dùng PinGuard.
- Nút bánh răng remote mở Cài đặt hệ thống phải qua PIN.
- `lockPoll` fail-closed (lỗi thì khóa nếu TV đang khóa).
- CI: sinh `InboxConfig.kt` từ Secrets, khôi phục keystore từ Secret, chạy test.
  `build.gradle.kts` đã có `testImplementation("junit:junit:4.13.2")` (trước đó THIẾU nên
  bước test trong CI không biên dịch được).
- `ArchitectureTest` (đọc mã nguồn) giữ kiến trúc một cửa — xem ARCHITECTURE.md.

Đã sửa thêm 2026-11-10 (kết quả review chéo, có test hồi quy):
- Nút "Hủy" trên màn khóa: ẩn ở mode "lock" (trước đây đóng được màn khóa); focus remote
  không rơi vào nút bị ẩn. Cần thử tay trên TV (UI không test được trên JVM).
- Phần dư thời gian (remainder): tách phần dư của giới hạn NGÀY (chỉ app tính giờ góp, app
  `free` không) khỏi phần dư riêng TỪNG APP (lưu theo gói trong prefs `appRemainders`).
  Trước đây dùng chung nên 59s app free bị tính vào giới hạn ngày, đổi app đúng nhịp
  (59s tính giờ + 1s free) né được giới hạn ngày, và phút bị gán sai app. KHÔNG được "xóa
  phần dư khi đổi app" (đổi app mỗi 59s sẽ không bao giờ đếm được). Test: RemainderScenarioTest.
- "Mở khóa đến hết khung" với lịch cả ngày: hết lúc 00:00 hôm sau (trước là 23:59:00 nên khóa
  lại sớm 1 phút).

Sự cố 2026-11-10 ~21:02 (TV không khóa sau khi hết giờ cộng tạm; web báo "đóng"): nguyên nhân là
chốt `lastPkg == ownPkg -> Allow` trong `LockPolicy.evaluate` (thêm ở lượt refactor "một cửa").
Anh ấn Settings -> lastPkg = vn.tvlock; quay lại TVMate mà không có sự kiện đổi cửa sổ nào nên
lastPkg kẹt; evaluate trả Allow mãi. Web dùng `isLockedNow` (không có chốt này) nên vẫn báo đóng.
Đã sửa: bỏ chốt, thay bằng phiên PIN có hạn 10 phút (`Store.startAdminSession`, gọi sau khi nhập
đúng PIN ở mode "settings"/"webpin"). Test: `LockDecisionTest.*` (replay sự cố + bất biến "khóa
toàn TV không phụ thuộc lastPkg").
Phát hiện kèm theo (CHƯA sửa — cần thử trên TV): `accessibility_service_config.xml` khai báo
`canRetrieveWindowContent="false"` và không có `flagRetrieveInteractiveWindows` nên
`AccessibilityService.windows` luôn rỗng -> `refreshForegroundApp()` không bao giờ cập nhật được
lastPkg (đoạn "tự nhận lại foreground" là mã chết). Hậu quả còn lại: lastPkg có thể cũ -> đếm giờ /
chặn từng app có thể sai, nhưng KHÓA TOÀN TV không còn phụ thuộc. Đã thêm log
`adb logcat -s GuardA11y` ("windows rong ...") để xác nhận trên TV.

ĐÃ SỬA 2026-11-11 (đếm giờ sai: xem TVMate 16' mà web báo 3'): bật `canRetrieveWindowContent="true"`
+ `flagRetrieveInteractiveWindows` trong `accessibility_service_config.xml` để `windows` có dữ liệu,
`refreshForegroundApp()` quét được foreground thật và `lastPkg` cập nhật đúng -> đếm giờ chạy lại.
Đổi capability của service nên sau khi cài phải bật lại Trợ năng 1 lần (qua ADB hoặc reboot).
Đã xác nhận trên TV 11/11: log "windows rong" hết hẳn; TV khóa đúng khi hết giới hạn (web báo đã xem
11'/5' lúc 12:34 — đếm khá đúng). Lưu ý nhỏ anh chấp nhận: số "còn X phút" trên web chỉ tính
giới hạn ngày (limit - used), không cộng thêm giờ mở tạm (+30' qua web vẫn mở TV nhưng số còn lại
vẫn hiện 0).

CHƯA kiểm chứng trên TV thật (không chạy được trong JVM) — cần checklist 4 mục trước khi cài:
- popup âm lượng / thanh thông báo khi đang xem có làm dừng đếm giờ không (kỳ vọng: không);
- trong giờ khóa: nhập PIN vào Cài đặt của app, màn khóa KHÔNG đè lên;
- trong giờ khóa: vào Cài đặt hệ thống / launcher Google TV, màn khóa đè lên;
- TV ngủ rồi bật lại: số phút đã xem không nhảy.

CHƯA làm (biết rõ, để sau):
- Cài đặt hệ thống mới được chặn bằng PIN ở nút bánh răng; các đường khác (menu launcher
  Google TV) chưa chặn bằng PIN khi TV đang MỞ khóa. Hướng sửa: chặn theo sự kiện cửa sổ
  `com.android.settings` khi chưa có phiên PIN.
- Chẻ Store / WebServer thành module nhỏ; tách integrations (watchdog, inbox, IP cứng,
  widget Đà Nẵng); test cho Store / WebServer / service (cần Android).

Mục tiêu: sửa hết các lỗi trong 2 comment review (09/11) mà không lặp lại kiểu
"sửa A lòi B" của đợt 05–08/11 (046f12c → màn khóa refresh 88 lần;
393bac0 → vòng lặp gọi khóa; sửa warnIfNeeded mà quên tick() return sớm).

## Vấn đề gốc (đã xác nhận trong code)

- Cùng 1 quyết định nằm ở nhiều nơi: "có hiện màn khóa không" ở 4+ chỗ
  (GuardService.tick() 2 chỗ, lockPoll, onAccessibilityEvent, handleRemapAction);
  lastPkg bị ghi từ 4+ chỗ (onAccessibilityEvent, refreshForegroundApp,
  Store.tick, GuardService.onCreate), mỗi chỗ một quy tắc "app nào hợp lệ".
- Store.kt 1171 dòng, WebServer.kt 1051 dòng, ôm tất cả, nhiều luồng
  đọc-sửa-ghi SharedPreferences không có khóa.
- Test chỉ phủ LockPolicy (36 test); lịch khóa, Store, hai service, WebServer
  không có test; CI không chạy test. Lỗi xuất hiện đúng ở chỗ không có test.

## Nguyên tắc chống hồi quy (áp dụng cho MỌI bước)

1. **Test trước, sửa sau.** Viết test tái hiện lỗi → test đỏ → sửa → test xanh.
   Không có test thì không sửa. (Quy tắc anh đặt 08/11, giữ nguyên.)
2. **Một bước chỉ đổi một thứ.** Xong một bước: chạy test + cài lên TV kiểm tra
   đúng kịch bản của bước đó → rồi mới sang bước sau.
3. **Khi tách code: không đổi hành vi.** Test chứng minh hành vi giống hệt
   trước khi tách. Chỉ đổi hành vi sau khi tách xong và test đã xanh.
4. **Không gộp nhiều fix vào một bản.** Mỗi bản cài lên TV phải qua checklist
   4 mục (màn nhấp nháy, nút remote bấm không được, bypass màn khóa,
   menu/focus không nhìn thấy rõ).
5. **Nếu một bước làm test cũ đỏ hoặc hành vi cũ đổi → dừng lại**, quay về
   bước trước, không sửa tiếp.

## Giai đoạn 0 — Lưới an toàn (không đổi logic)

Mục tiêu: CI build được và chạy test; có hạ tầng test để các giai đoạn sau
dựa vào.

- Tạo `android/app/src/main/java/vn/tvlock/InboxConfig.kt.example`
  (cấu trúc đủ HOST/PORT/TOKEN, không chứa giá trị thật). CI sinh
  `InboxConfig.kt` thật từ GitHub Secrets.
- Keystore: CI sinh debug keystore mới (hoặc lấy từ Secrets); sửa
  `build.gradle.kts` để không fail khi thiếu file b64/keystore.
- Thêm bước `./gradlew test` vào `.github/workflows/android.yml`; CI fail
  nếu test đỏ.
- Kiểm chứng: CI chạy xanh với 36 test cũ (chưa thêm test mới ở giai đoạn này).

## Giai đoạn 1 — Vá lỗ bảo mật khẩn (mỗi fix một bước, kiểm chứng ngay)

Thứ tự trong giai đoạn này là thứ tự ưu tiên. Mỗi fix: viết test → sửa →
test xanh → cài TV → kiểm tra đúng kịch bản → mới sang fix sau.

### 1a. Chặn nút bánh răng mở Cài đặt hệ thống khi khóa
- `handleSystemKey` (KEYCODE_SETTINGS): kiểm tra `isLockedNow` trước — nếu
  TV đang khóa thì không mở Settings, hiện màn khóa thay vào đó.
- **Cần anh chốt:** lúc TV KHÔNG khóa, bấm nút bánh răng có cần nhập PIN
  không? Đề xuất: có — mở Cài đặt hệ thống luôn yêu cầu PIN, giống như
  Cài đặt của app (đã có PIN từ 04/11). Như vậy trẻ không bao giờ vào được
  Cài đặt hệ thống mà không có PIN.
- Test: bấm gear lúc khóa → Settings không mở; bấm gear lúc mở → hỏi PIN
  (nếu anh chốt như đề xuất).

### 1b. Tách "miễn trừ đếm giờ" khỏi "miễn trừ khóa"
- `com.android.settings` nằm trong `isExcluded()` nên không tính giờ (đúng). Việc KHÔNG
  còn miễn khóa được làm bằng cách `LockPolicy.evaluate` kiểm tra "TV đang khóa" TRƯỚC
  `isExcluded` (không có hàm `isLockExempt` riêng) — TV khóa mà đang ở Settings thì màn
  khóa vẫn hiện.
- Test: đặt TV vào trạng thái khóa rồi mở Settings → màn khóa hiện.

### 1c. Khóa dò PIN khóa TV
- Áp dụng chung cơ chế mã thưởng (sai 5 lần liên tiếp → khóa nhập 5 phút)
  cho PIN khóa TV. (PinGuard đầy đủ, dùng chung cho cả 3 loại mã, làm ở
  Giai đoạn 2d; ở đây chỉ áp dụng cho PIN trước để vá lỗ.)
- Test: nhập sai PIN 5 lần → khóa nhập 5 phút; nhập đúng thì reset đếm.

### 1d. Gắn permission cho 3 receiver exported
- Thêm `android:permission="android.permission.DUMP"` cho
  `AntiDisableReceiver` (SET_ANTIDISABLE), `WebPinResetReceiver`
  (RESET_WEBPIN), `DeviceOwnerRemoveReceiver` (REMOVE_DEVICE_OWNER).
  Shell ADB có quyền DUMP nên Cài TV vẫn gọi được; app thường thì không.
- Test: gửi broadcast từ app thường → không có tác dụng; gửi từ shell →
  vẫn chạy.

### 1e. `allowBackup=false`
- PIN lưu plaintext trong SharedPreferences; `allowBackup=true` cho phép
  `adb backup` lấy ra. Đổi thành `false`.
- Test: build xong kiểm tra manifest; không ảnh hưởng cài đè (backup cũ
  không dùng nữa).

## Giai đoạn 2 — Refactor cấu trúc (gốc rễ)

Mỗi bước tách: KHÔNG đổi hành vi → test xanh → cài TV → kiểm tra hành vi
giống hệt → mới sang bước sau. Chỉ đổi hành vi sau khi tách xong.

### 2a. `SchedulePolicy` thuần (kéo lịch ra khỏi Store)
- Kéo `matchingSchedules`, `minutesUntilNextLock`, `unlockUntilEndOfLockWindow`,
  `scheduleTimeText` từ Store.kt sang `SchedulePolicy.kt` thuần Kotlin
  (không Android, không SharedPreferences), giống cách đã làm với LockPolicy.
- Test mới: lịch 00:00–23:59 (hiện "khóa cả ngày" thì 23:59 phải khóa);
  lịch chồng nhau (lấy lịch kết thúc muộn nhất, không lấy lịch đầu tiên);
  lịch qua đêm; start==end = cả ngày (thống nhất 1 ngữ nghĩa, bỏ lệch với
  `isInWindow`).
- Sửa lỗi 23:59 ở bước này (sau khi tách xong và test đã xanh).

### 2b. `ForegroundTracker` — một chỗ duy nhất quyết định lastPkg
- Gom mọi chỗ ghi lastPkg/lastPkgStartMs (onAccessibilityEvent,
  refreshForegroundApp, Store.tick, GuardService.onCreate) vào một class
  duy nhất, chạy trên một thread/handler duy nhất.
- Quy tắc ĐÃ CHỐT (thay bản đầu của kế hoạch): chỉ cửa sổ thoáng qua (systemui) không
  ghi đè lastPkg; settings / launcher / app mình là màn hình thật nên ghi đè (nếu không,
  vào Cài đặt vẫn bị tính giờ YouTube). Khoảng treo > 60 giây thì BỎ QUA, không kẹp 5
  phút; lastPkg rỗng thì tự nhận lại app foreground.
- Test mới: lastPkg=systemui (không ghi đè, đếm giờ không dừng, khóa vẫn
  hiện); gap 3 tiếng giữa 2 tick (chỉ cộng tối đa 5'); đổi app liên tục
  (không đếm đôi, không mất phút).
- Đây là bước khó nhất — làm sau 2a, làm một mình, không gộp với bước khác.

### 2c. Gom "có hiện màn khóa không" vào một chỗ duy nhất
- ĐÃ LÀM: `LockPolicy.evaluate` + `LockEngine` (tên `LockDecider` trong kế hoạch ban đầu
  không dùng). Mô tả gốc: một hàm duy nhất; cả
  GuardService.tick() và lockPoll cùng gọi nó. Xóa các bản copy điều kiện
  ở GuardService.tick() (2 chỗ), onAccessibilityEvent, handleRemapAction.
- Test: các ca đã gặp — khóa tay, hết giờ ngày, tới lịch khóa, mở tạm,
  thời sự miễn trừ, đang ở app excluded.

### 2d. `PinGuard` dùng chung
- Một class đếm sai + khóa tạm, dùng chung cho PIN khóa TV, PIN web, mã
  thưởng — thay 3 bản copy hiện tại (mã thưởng đã có, PIN chưa có, PIN web
  chưa rõ — kiểm tra rồi gom chung).
- Test: sai 5 lần → khóa 5 phút; nhập đúng → reset; hết thời gian khóa →
  nhập lại được.

## Giai đoạn 3 — Sửa các lỗi còn lại (trên cấu trúc sạch)

- WebServer: kẹp `contentLength` (≤16KB cho form, ≤8MB cho ảnh) trước khi
  cấp `CharArray`; dùng `org.json` thay nối chuỗi JSON (hết lỗi esc() là
  HTML-escape mà dùng trong JSON); sửa `reasonFor(400)` trả "Bad Request"
  thay vì "OK"; giới hạn thread bằng ThreadPoolExecutor thay cho
  thread-per-connection không giới hạn.
- versionCode: một nguồn duy nhất. Gradle thắng (hiện 4/1.3) — xóa số ở
  manifest, hoặc chốt manifest (27/4.0) làm chuẩn và sửa Gradle theo.
  **Cần anh chốt:** giữ số nào? Đề xuất giữ manifest 27/4.0 (bản local
  đang cài trên TV là 27, đổi về 4 có thể làm Android coi là downgrade).
- Mã thưởng: `java.util.Random` → `SecureRandom`.
- Dọn 40 khối `catch {}` rỗng: thêm log; riêng lockPoll thì đổi fail-open
  thành fail-closed (lỗi thì hiện khóa thay vì không khóa).
  **Cần anh chốt:** fail-closed an toàn hơn (lỗi thì khóa) nhưng có thể
  khóa nhầm nếu Trợ năng lỗi tạm thời. Đề xuất: fail-closed, vì app khóa
  thì khóa nhầm vẫn hơn là không khóa.
- Chia Store.kt (1171 dòng) và WebServer.kt (1051 dòng) thành module nhỏ:
  làm sau cùng, chỉ khi cần — đây là việc lớn nhất, không bắt buộc để hết
  lỗi. Ưu tiên thấp hơn 3 giai đoạn trên.

## Giai đoạn 4 — Kiểm chứng tổng + đẩy GitHub

- Chạy toàn bộ test (mới + cũ) xanh. CI xanh.
- Cài lên TV, kiểm tra tất cả kịch bản đã sửa: khóa hiện đúng (khóa tay,
  hết giờ, tới lịch), đếm giờ chạy đều, mã thưởng nhập được, nút remote
  (gán + gear) đúng, Settings cần PIN, hết mở tạm thì khóa lại, thời sự
  miễn trừ đúng.
- Đối chiếu UsageStats như anh dặn (số liệu app vs hệ thống).
- Push GitHub (cần anh duyệt thủ công như mọi lần).

## Thứ tự ưu tiên nếu anh muốn rút ngắn

1. Giai đoạn 1 (bảo mật) — không để lỗ bypass qua đêm.
2. Giai đoạn 2a + 2b (SchedulePolicy + ForegroundTracker) — gốc rễ, hết
   kiểu lỗi "sửa A lòi B".
3. Còn lại (2c, 2d, Giai đoạn 3) làm dần, mỗi bước một bản.

## Rủi ro và cách tránh

- Refactor chạm vùng nguy hiểm nhất → mỗi bước nhỏ, test trước, cài TV
  kiểm tra sau mỗi bước, không gộp bước.
- Nếu bước nào làm test cũ đỏ → dừng, quay lại, không sửa tiếp.
- Nếu TV tắt lúc cần cài bản kiểm tra → đợi anh bật TV, không cài vội
  qua đêm rồi báo xong.
