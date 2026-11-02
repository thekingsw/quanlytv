# Hướng dẫn cài đặt app Khóa TV

TV: Xiaomi A Pro 55 (Google TV) — quản lý chung cả TV, không tách profile từng bé.

## 1. Cài app lên TV

- Cách dễ nhất: trên điện thoại mở trình duyệt, tải file `khoa-tv.apk`, rồi dùng app **Send Files to TV** (cài cả trên điện thoại và TV) để gửi file sang TV, sau đó mở file trên TV để cài.
- Hoặc chép file APK vào USB, cắm vào TV, dùng trình quản lý file trên TV để mở và cài.
- Khi TV hỏi, cho phép **Cài đặt từ nguồn không xác định** cho app vừa dùng để cài (Send Files to TV / trình quản lý file).
- Mở app **Khóa TV** lần đầu: app tự chạy dịch vụ bảo vệ.

> **Bản v2 — icon đã ẩn khỏi màn hình chính:** sau khi cài đè bản v2, anh sẽ **không thấy icon Khóa TV** trên màn hình chính nữa — đó là đúng ý đồ, không phải lỗi cài đặt. Cách mở app lần đầu xem mục "Mở app khi icon đã ẩn" ngay dưới đây.

## 1b. Mở app khi icon đã ẩn (bản v2)

Chọn 1 trong 3 cách sau (đều không cần icon):

1. **Qua Trợ năng (dễ nhất):** trên TV vào **Cài đặt → Hệ thống → Trợ năng → Khóa TV** → bấm **biểu tượng bánh răng** ⚙️ bên cạnh tên dịch vụ — màn hình Cài đặt của app sẽ mở ra. Trong đó bấm **Mở màn hình chính** để vào app.
2. **Qua màn hình khóa:** khi TV đang khóa, nhập đúng **mã PIN** rồi bấm **Mở Cài đặt** → trong Cài đặt bấm **Mở màn hình chính**.
3. **Qua ADB (dự phòng, cần laptop cùng WiFi):**
   ```
   adb connect <IP-của-TV>:5555
   adb shell am start -n vn.tvlock/.MainActivity
   ```

**Hiện lại icon:** trong Cài đặt của app → mục **Hiển thị icon** → bấm **Hiện icon** (icon sẽ xuất hiện lại trên màn hình chính; có thể cần khởi động lại TV hoặc đợi launcher làm mới). Bấm **Ẩn icon** để ẩn lại.

> **Nói thật với anh:** ẩn icon chỉ làm app khó thấy hơn thôi — trẻ vẫn có thể gỡ app từ **Cài đặt TV → Ứng dụng → Khóa TV → Gỡ cài đặt** nếu biết cách. Chỉ bước ADB Device Owner ở mục 5 mới chặn được việc gỡ.

## 2. Bật Trợ năng — BẮT BUỘC

Không bật bước này thì app không chặn được app khác.

1. Trên TV, vào **Cài đặt TV → Hệ thống → Trợ năng** (bản v2 không còn icon nên không mở app bằng icon được — dùng mục 1b ở trên).
2. Tìm **Khóa TV** trong danh sách dịch vụ đã tải xuống → **Bật** → bấm **Cho phép** khi TV cảnh báo.
3. Bấm **biểu tượng bánh răng** ⚙️ cạnh **Khóa TV** để mở Cài đặt của app, kiểm tra dòng "Trợ năng" phải hiện **ĐÃ BẬT**.

## 3. Cài đặt theo ý anh (mặc định đã có sẵn)

Mở Cài đặt của app (xem mục 1b nếu icon đã ẩn):

- **Mã PIN mặc định: 1234** — đổi ngay trong Cài đặt (nhập PIN cũ + PIN mới, ít nhất 4 số).
- **Lịch khóa theo ngày** (bản v4, xem mục 3c): mỗi lịch chọn ngày trong tuần + giờ khóa từ–đến, gộp nhiều lịch được. Mặc định ban đầu: một lịch khóa 21:00 – 07:00 hằng ngày (giữ nguyên quy tắc cũ của bản v3).
- **Giới hạn ngày theo ngày** (bản v4, xem mục 3c): mỗi ngày trong tuần một mức riêng, mặc định 90 phút cho cả 7 ngày (giữ nguyên mức cũ).
- **Khung giờ thời sự: 19:00 – 20:00** — chọn **app thời sự** trong danh sách. Trong khung này app thời sự **không bao giờ bị chặn** (kể cả hết giờ ngày, lịch khóa, khóa tay) và thời gian xem app đó **không tính** vào giới hạn ngày. Các app khác trong khung này mở bình thường theo quy tắc chung: còn giờ xem và không vướng lịch khóa thì mở được, hết giờ hoặc vướng lịch thì vẫn bị khóa như ngoài khung. Nếu chưa chọn app nào thì khung thời sự không có tác dụng gì thêm. Khung thời sự KHÔNG nằm trong danh sách lịch khóa — nó là luật riêng.
- **Khóa thủ công**: bật/tắt khóa ngay.
- **Reset số phút đã xem hôm nay** nếu muốn cho xem lại từ đầu.

## 3b. Tính năng mới bản v3 (1.2)

- **Giới hạn từng app** (trong Cài đặt → mục **Giới hạn từng app** → bấm **Chọn app để đặt giới hạn**): chọn một app rồi chọn chế độ: **Bình thường** (tính vào giới hạn ngày như cũ) / **Giới hạn riêng 15 / 30 / 60 phút mỗi ngày** / **Không tính giờ** (app học — xem bao lâu cũng được, không cộng vào giới hạn ngày) / **Chặn hẳn** (mở lên là hiện màn hình khóa, trừ khi nhập PIN mở tạm). Lưu xong áp dụng ngay, không cần khởi động lại. Hết giờ của app nào thì chỉ app đó bị khóa (màn hình khóa ghi rõ lý do, ví dụ "Hết giờ cho app YouTube"), TV vẫn xem app khác được. Thứ tự ưu tiên: app thời sự trong khung thời sự được miễn trừ tuyệt đối (cao nhất); các app khác tuân quy tắc của chính nó, rồi đến khóa thủ công / khung khóa / giới hạn ngày như cũ. Lưu ý: app "Không tính giờ" vẫn bị khóa trong các lịch khóa và khi bố khóa thủ công. Đổi quy tắc chỉ làm trên TV trong bản này; trang web điện thoại chỉ xem số phút từng app.
- **Phút thưởng** (trang web điện thoại → mục **Thưởng phút**, hoặc trên TV: Cài đặt → mục **Phút thưởng**): bấm **Thưởng +5 / +10 / +15 / +30 phút** để cộng vào quỹ thưởng. Khi hết giờ ngày mà quỹ còn phút, TV vẫn mở và tự trừ dần quỹ, mỗi phút xem trừ 1 phút thưởng; hết cả phút thưởng mới khóa. Phút thưởng chưa dùng hết **dồn sang hôm sau** (khác với phút ngày chưa dùng thì không dồn). Số phút thưởng còn lại hiện trên màn hình chính của TV ("Còn lại hôm nay: X phút + thưởng Y phút"), trong Cài đặt, và trên trang web. Trên TV phải nhập PIN trước khi thưởng (bấm nút rồi nhập PIN); trên điện thoại thì không cần PIN. Khác với nút "+5 phút" thường (mở khóa ngay 5 phút): phút thưởng chỉ có tác dụng khi đã hết giờ ngày.
- **Nhắc nhẹ trước khi khóa** (bản v1.7 đổi kiểu hiện): khi còn đúng **5 phút** và **1 phút** nữa là hết giờ ngày (tính cả phút thưởng), hoặc hết giờ riêng của app đang xem, hoặc đến giờ bắt đầu khung khóa, TV hiện một **dải nhắc nhỏ kiểu notification ở phía trên màn hình** — video đang xem **không bị pause, không bị chắn**, điều khiển vẫn bấm bình thường, không có nút nào giành focus. Dải nhắc tự tắt sau 20 giây. **60 giây cuối thì đếm ngược từng giây**: "Còn 59 giây nữa là hết giờ xem", "Còn 58 giây..." cho tới lúc khóa. Mỗi mốc chỉ nhắc một lần; được cộng thêm giờ hoặc phút thưởng thì nhắc lại từ đầu cho lần tới hạn mới. (Kỹ thuật: dải nhắc vẽ bằng overlay của dịch vụ Trợ năng nên không cần thêm quyền gì; chỉ khi Trợ năng chưa bật thì mới rơi về popup cũ.)
- **Báo cáo** (trang web điện thoại → mục **Báo cáo**; trên TV: màn hình chính hiện dòng nhỏ **"Hôm qua đã xem X phút"**): trang web hiện bảng hôm nay — từng app xem bao nhiêu phút, tổng hôm nay so với giới hạn ngày — và 7 ngày gần nhất, mỗi ngày một hàng với thanh ngang + số phút. Lịch sử lưu 14 ngày gần nhất. Ngoài ra có endpoint `GET /api/report` trả JSON `{today:{date,total,limit,perApp:{pkg:min}}, days:[{date,total}]}` để sau này dùng.

## 3c. Tính năng mới bản v4 (1.3) — Lịch khóa linh hoạt

- **Lịch khóa theo ngày** (trong Cài đặt → mục **Lịch khóa theo ngày** → bấm **Quản lý lịch khóa**): danh sách các lịch hiện có, bấm vào một lịch để sửa, bấm **Thêm lịch mới** để tạo thêm. Mỗi lịch gồm: 7 ô tick chọn ngày trong tuần (Thứ 2 … Chủ nhật, bấm bằng điều khiển), giờ **bắt đầu** và giờ **kết thúc** (từ bản v1.6: giờ và phút chỉnh riêng — nút giờ ±1 giờ, nút phút ±1 phút, giữ nút là tự tăng/giảm liên tục như giữ phím volume), nút **Khóa cả ngày** (đặt 00:00–23:59), nút **Xóa lịch này**. Lưu xong áp dụng ngay, không cần khởi động lại.
  - **Khóa qua đêm:** nếu giờ kết thúc nhỏ hơn giờ bắt đầu thì app hiểu là khóa sang ngày hôm sau. Ví dụ lịch Thứ 4, Thứ 5 khóa 19:00–10:00 nghĩa là: Thứ 4 từ 19:00 đến Thứ 5 10:00 sáng, và Thứ 5 từ 19:00 đến Thứ 6 10:00 sáng.
  - **Gộp nhiều lịch:** lịch nào khớp giờ hiện tại thì TV khóa. Màn hình khóa ghi rõ lý do, ví dụ "Đến giờ khóa (Thứ 4, Thứ 5: 19:00–10:00 (qua đêm))".
  - **Ví dụ của anh:** lịch 1 = tick Thứ 2, Thứ 3 + bấm **Khóa cả ngày**; lịch 2 = tick Thứ 4, Thứ 5 + bắt đầu 19:00, kết thúc 10:00.
  - **Chuyển từ bản cũ:** lần đầu mở bản v4, khung khóa cũ (21:00–07:00 hằng ngày) tự chuyển thành một lịch trong danh sách — quy tắc hiện tại của anh không mất, anh chỉ việc sửa/xóa/thêm lịch theo ý.
  - Nhắc nhẹ 5 phút / 1 phút trước giờ khóa vẫn hoạt động theo lịch mới (tính tới giờ bắt đầu của lịch gần nhất).
- **Giới hạn ngày theo ngày** (trong Cài đặt → mục **Giới hạn ngày theo ngày** → bấm **Đặt giới hạn từng ngày**): bấm vào một ngày rồi chọn mức: 5 / 10 / 15 / 30 / 60 / 90 / 120 / 180 / 240 phút hoặc **Không giới hạn** (thêm 5 / 10 / 15 phút từ bản v1.6 để test khóa nhanh). Ngày "Không giới hạn" thì chỉ còn bị khóa bởi lịch khóa (và các quy tắc khác như giới hạn từng app). Ví dụ: ngày học 60 phút, cuối tuần 120 phút. Lần đầu mở bản v4, mức 90 phút cũ được gán cho cả 7 ngày.
- **Trang web điện thoại:** từ bản v1.6 sửa được cả cài đặt — xem mục 3d. Endpoint `GET /api/status` thêm `todayLimit` và `todaySchedules`.

## 3d. Tính năng mới bản v1.6 (1.6) — Chỉnh giờ/phút riêng + cài đặt trên điện thoại

- **Giờ và phút chỉnh riêng trên TV:** cả màn hình **Sửa lịch khóa** và **khung giờ thời sự** trong Cài đặt đều chia thành 2 cụm nút riêng — cụm **giờ** (bấm ±1 giờ, phút giữ nguyên) và cụm **phút** (bấm ±1 phút, tràn sang giờ bên cạnh, ví dụ 19:00 bấm phút − thành 18:59). **Giữ nút trên điều khiển** (giữ phím OK giữa) là số tự tăng/giảm liên tục như giữ phím volume, không phải bấm nhiều lần — giữ ~0,5 giây thì bắt đầu chạy, giữ lâu thì chạy nhanh dần. Bấm một cái vẫn chỉ đổi 1 đơn vị như cũ.
- **Cài đặt từ điện thoại:** trang chính của web có đường dẫn **"Mở Cài đặt"** (mục **Cài đặt** ở thanh tab dưới) — sửa được ngay trên điện thoại, không cần mở TV:
  - **Giờ thời sự:** chọn giờ bắt đầu / kết thúc (ô nhập giờ, bấm vào chỉnh giờ và phút riêng) + chọn app thời sự trong danh sách.
  - **Giới hạn xem từng ngày:** chọn mức cho từng ngày Thứ 2 … Chủ nhật (5 / 10 / 15 / 30 / 60 / 90 / 120 / 180 / 240 phút hoặc Không giới hạn).
  - **Lịch khóa:** sửa từng lịch có sẵn (tick ngày, giờ bắt đầu / kết thúc), xóa lịch, hoặc thêm lịch mới.
  - Lưu xong áp dụng ngay trên TV, không cần khởi động lại. Như các nút khác trên web, cài đặt từ điện thoại **không cần nhập PIN** (điện thoại do anh cầm).

## 3e. Tính năng mới bản v1.8 (1.8) — Màn hình khóa 2 cột + nút Mở thời sự

- **Bố cục mới:** màn hình khóa chia 2 cột — **bên trái là ảnh nền + lời nhắc của bố** (chữ lớn đè lên ảnh, có nền mờ cho dễ đọc), **bên phải là cụm bàn phím PIN** + lý do khóa + các nút sau khi nhập đúng PIN. Trước đây màn hình khóa là một cột dọc, nay ảnh chiếm nửa trái màn hình.
- **Nút Mở thời sự:** khi đang trong khung giờ thời sự (và đã chọn app thời sự), màn hình khóa hiện nút **"Mở thời sự: <tên app>"** ngay trên bàn phím — bấm là mở thẳng app thời sự, **không cần nhập PIN** (trẻ chỉ xem được đúng app thời sự, app khác vẫn khóa). Thoát app thời sự là quay lại màn hình khóa. Ngoài khung thời sự thì nút này tự ẩn.
- **Tùy chỉnh ảnh nền + lời nhắc — làm được cả trên TV lẫn điện thoại:**
  - **Trên điện thoại** (trang Cài đặt của web → mục **Màn hình khóa**): sửa lời nhắc (tối đa 140 ký tự), chọn ảnh trên điện thoại rồi bấm **Tải ảnh này lên TV** (xem trước ảnh ngay trên trang). Ảnh JPEG/PNG tối đa ~5MB, app tự thu nhỏ vừa màn hình TV. Có nút **Xóa ảnh nền riêng** để về nền mặc định.
  - **Trên TV** (Cài đặt → mục **Màn hình khóa**): sửa lời nhắc, bấm **Chọn ảnh nền từ TV** (chọn ảnh qua trình chọn file của TV), hoặc **Xóa ảnh nền riêng**.
  - Lời nhắc mặc định: "Hết giờ xem TV rồi con nhé!". Ảnh/lời nhắc đổi từ điện thoại thì màn hình khóa tải lại ngay lần mở sau (không cần khởi động lại app).

## 3f. Tính năng mới bản v1.9 (1.9) — Cập nhật xong web tự chạy lại

- **Vấn đề cũ:** sau khi cài đè APK mới (qua app Cài TV), khóa trên TV vẫn chạy nhưng điện thoại không vào được web nữa — phải mở màn hình chính trên TV một lần thì web mới chạy lại. Nguyên nhân: web chạy trong GuardService, mà Android không tự chạy lại service khi app được cập nhật (process bị kill, hệ thống chỉ gửi thông báo cập nhật chứ không khởi động lại service). Dịch vụ Trợ năng thì hệ thống tự gắn lại nên khóa vẫn chạy — chỉ có web là chết.
- **Sửa 2 lớp:** (1) Khóa TV 1.9 nghe thông báo cập nhật (`MY_PACKAGE_REPLACED`) và tự chạy lại GuardService ngay khi cài xong — từ bản này trở đi, cập nhật xong là vào web được luôn, khỏi mở tay. (2) Cài TV 1.4 sau khi cài xong tự mở luôn màn hình chính Khóa TV trên TV (qua ADB) để web chạy lại ngay — log trong app Cài TV sẽ ghi `Đã mở màn hình chính Khóa TV trên TV`.
- **Lần chuyển tiếp này:** cài **Cài TV 1.4 lên điện thoại trước**, rồi dùng nó cài **Khóa TV 1.9** — màn hình chính sẽ tự mở sau khi cài, web chạy ngay. Nếu cài Khóa TV 1.9 bằng Cài TV cũ (1.3) thì vẫn phải mở màn hình chính một lần cuối, từ lần sau mới tự chạy lại.

## 4. Điều khiển từ điện thoại (cùng WiFi)

1. Màn hình chính của app trên TV hiện địa chỉ dạng `http://192.168.x.x:8080`.
2. Điện thoại **cùng WiFi nhà** mở trình duyệt tới địa chỉ đó.
3. Bấm một cái là xong, **không cần nhập mã PIN**: tab **Hôm nay** có **Khóa ngay**, **Mở khóa 60 phút**, chip **+5 / +10 / +15 / +20 / +30 phút** và thẻ **Báo cáo** (hôm nay xem app nào bao nhiêu phút + 7 ngày gần nhất); tab **Cài đặt** có mục **Phút thưởng** cộng phút vào quỹ thưởng (+5 / +10 / +15 / +30). Tab **Lịch** sửa/thêm/xóa lịch khóa, tab **Giới hạn** đổi giới hạn từng ngày. Chi tiết xem mục 3g.
4. Trang web hiện trạng thái: đang khóa hay mở, đã xem bao nhiêu phút, còn lại bao nhiêu (kể cả phút thưởng).
5. Không cần cài thêm app nào trên điện thoại, không cần tài khoản hay cloud.

> Ai vào được trang web này (cùng WiFi nhà hoặc cùng Tailscale) đều bấm được — đó là lựa chọn của anh vì điện thoại do anh cầm.

**Quên mã PIN thì đổi ngay trên điện thoại:** trên trang web, mục **Đổi mã PIN** chỉ có 1 ô duy nhất — nhập mã PIN mới (4–6 số) rồi bấm **Đổi PIN**, không cần nhập PIN cũ.

Trên TV thì khác: màn hình khóa trên TV vẫn yêu cầu nhập PIN như cũ, và các nút Khóa ngay / Mở khóa / +5…+30 trên TV vẫn phải nhập PIN trên bàn phím số hiện trên màn hình (điều khiển Xiaomi không có phím số nên dùng bàn phím trên màn hình; nếu remote có phím số thì bấm trực tiếp cũng được).

## 5. Chống gỡ app (tùy chọn, cần laptop cùng WiFi, làm 1 lần)

> **Nói thật với anh:** nếu không làm bước này, các bé vẫn có thể gỡ app từ **Cài đặt TV → Ứng dụng → Khóa TV → Gỡ cài đặt**. App không thể tự chặn việc gỡ của chính nó nếu chưa được đặt làm Device Owner. Đây là giới hạn của Android, không phải lỗi app.

Cách làm (không root máy, chỉ cần bật ADB trên TV):

1. Trên TV: **Cài đặt → Hệ thống → Giới thiệu → bấm 7 lần vào "Bản dựng"** để bật Tùy chọn nhà phát triển, rồi vào **Tùy chọn nhà phát triển → bật Gỡ lỗi ADB / ADB qua mạng**.
2. Trên laptop (cùng WiFi, đã cài Android SDK platform-tools): chạy
   ```
   adb connect <IP-của-TV>:5555
   adb shell dpm set-device-owner vn.tvlock/.DeviceOwnerReceiver
   ```
   **Cài Khóa TV 2.4 trước rồi mới chạy lệnh này** — bản 2.4 mới có đường gỡ (xem bước 3), chạy lệnh trên bản cũ thì sau này gỡ rất khó.
   **Lưu ý quan trọng:** lệnh này chỉ thành công khi trên TV **chưa có tài khoản nào** (tài khoản Google, tài khoản hãng...) — nếu còn tài khoản thì lệnh báo lỗi `Not allowed to set the device owner because there are already some accounts on the device`. Cách làm: vào **Cài đặt TV → Tài khoản**, gỡ hết tài khoản ra, chạy lệnh, rồi đăng nhập lại tài khoản sau. Kiểm tra còn tài khoản nào không bằng lệnh `dumpsys account | grep -A 3 Accounts:`. Nếu TV đã có Device Owner khác thì lệnh cũng thất bại — kiểm tra bằng `dumpsys device_policy | grep -i "device owner"`. Sau khi đặt xong, app không gỡ được từ Cài đặt nữa (từ bản 2.4 app tự bật `setUninstallBlocked` khi làm Device Owner). **Xóa dữ liệu** trong Cài đặt thì vẫn làm được, Device Owner chỉ chặn gỡ cài đặt chứ không chặn xóa dữ liệu.
3. Muốn gỡ quyền Device Owner sau này (để gỡ app), dùng 1 trong 2 cách — **không dùng `dpm remove-active-admin`**, lệnh đó báo lỗi `Attempt to remove non-test admin` vì app không đánh dấu testOnly, chỉ còn cách khôi phục cài đặt gốc:
   - Trên điện thoại, mở trang web của TV, đăng nhập mã PIN web rồi gọi `POST /api/device-owner-remove` (cần phiên đăng nhập hợp lệ).
   - Hoặc trong tab **Lệnh ADB** của app Cài TV, gõ: `am broadcast -a vn.tvlock.REMOVE_DEVICE_OWNER -n vn.tvlock/.DeviceOwnerRemoveReceiver`
   Cách này dùng API `clearDeviceOwnerApp` của Android — API này Google đã gắn cờ cũ (deprecated, "chỉ dùng để thử nghiệm", dọn chính sách theo kiểu best-effort), nên nó là đường thoát đã chuẩn bị sẵn chứ không phải bảo đảm tuyệt đối; nếu nó thất bại thì chỉ còn khôi phục cài đặt gốc. Sau khi gỡ quyền xong thì gỡ app như app thường.

## 6. Kiểm tra sau khi cài

1. Khởi động lại TV, mở YouTube xem thử vài phút — màn hình chính app phải đếm số phút đã xem tăng lên.
2. Bấm **Khóa ngay** trên app (hoặc từ điện thoại), rồi thử mở YouTube — phải hiện ngay màn hình khóa đỏ, nhập PIN mới mở được.
3. Thêm tạm một lịch khóa vào giờ hiện tại để thử, rồi xóa lịch đó đi.
4. Vào khung 19:00–20:00 thử mở app khác ngoài app thời sự đã chọn — mở được bình thường nếu còn giờ xem và không vướng lịch khóa; mở app thời sự thì không bao giờ bị chặn.

## Gỡ app (khi không cần nữa)

Cài đặt TV → Ứng dụng → Khóa TV → Gỡ cài đặt. Nhớ tắt dịch vụ Trợ năng của app trước khi gỡ cho sạch.

## 3d. Tính năng mới bản v2.0 (2.0) — Chống tắt Trợ năng + Báo cáo lên đầu trang web

### Chống tắt Trợ năng (học từ app CleanTV)

Trước đây, ai vào **Cài đặt TV → Trợ năng** tắt dịch vụ Khóa TV là khóa hết tác dụng. Từ bản 2.0, app tự bật lại:

- **Cách hoạt động:** sau khi cài Khóa TV 2.0 bằng **app Cài TV 1.5**, Cài TV tự chạy lệnh `pm grant vn.tvlock android.permission.WRITE_SECURE_SETTINGS` để cấp cho Khóa TV quyền tự ghi cài đặt hệ thống. Từ đó, cứ mỗi giây GuardService kiểm tra một lần: thấy Trợ năng bị tắt thì ghi lại dịch vụ của mình vào danh sách và bật lên ngay — không cần điện thoại, không cần ADB nữa.
- **Mặc định BẬT.** Trạng thái hiện ở: trang web điện thoại → **Cài đặt** → mục **Chống tắt Trợ năng** (ghi rõ Trợ năng đang bật/tắt và quyền đã cấp hay chưa); trên TV → **Cài đặt** → mục **Trợ năng** → dòng **Chống tắt**.
- **Khi anh cần tắt Trợ năng thật** (ví dụ sửa TV, cho con xem thoải mái một hôm): tắt **Chống tắt** trước, rồi mới tắt Trợ năng trong Cài đặt TV như bình thường. Tắt Chống tắt bằng 1 trong 3 cách:
  1. Điện thoại: mở web `http://<IP-TV>:8080/` → tab **Cài đặt** → mục **Chống tắt Trợ năng** → bấm **Tắt chống tắt**.
  2. Trên TV: **Cài đặt** của app → mục **Trợ năng** → bấm **Tắt chống tắt**.
  3. App **Cài TV** (điện thoại, đã kết nối TV) → mục **Chống tắt Trợ năng** → bấm **Tắt chống tắt**.
- **Nếu cài Khóa TV 2.0 bằng cách khác** (không qua Cài TV 1.5 — ví dụ Send Files to TV): quyền chưa được cấp, trang web sẽ báo quyền **CHƯA cấp** và chống tắt không chạy. Khắc phục: cài lại Khóa TV bằng app Cài TV 1.5, hoặc chạy trên laptop: `adb connect <IP-TV>:5555` rồi `adb shell pm grant vn.tvlock android.permission.WRITE_SECURE_SETTINGS`.
- **Lưu ý:** tắt "Gỡ lỗi USB" trên TV cũng không chặn được chống tắt, vì quyền đã cấp rồi thì ở lại trên app. Muốn dừng hẳn: tắt Chống tắt trước rồi tắt Trợ năng, hoặc gỡ app Khóa TV.

### Báo cáo dời lên đầu trang web

Thẻ **Báo cáo** trên trang chính của web điện thoại (`http://<IP-TV>:8080/`) đã dời lên ngay dưới thẻ trạng thái (trước đây nằm dưới mục Thưởng phút, phải kéo xuống mới thấy). Nội dung như cũ: hôm nay / hôm qua bao nhiêu phút, từng app hôm nay xem bao nhiêu phút, 7 ngày gần nhất dạng thanh ngang.

## 3e. Tính năng mới bản v2.1 (2.1) — Cài đặt thành menu + Xem trước + nút Áp dụng

### Cài đặt giờ là menu, không còn cuộn dài

Màn hình **Cài đặt** giờ chỉ là một danh sách 8 mục, mỗi mục mở một màn hình riêng vừa một trang:

- **Lịch khóa** — danh sách + sửa lịch khóa theo ngày.
- **Giới hạn ngày** — giới hạn xem từng ngày trong tuần.
- **Giờ thời sự** — giờ bắt đầu/kết thúc + chọn app thời sự.
- **Giới hạn từng app** — giới hạn riêng / không tính giờ / chặn hẳn từng app.
- **Màn hình khóa** — sửa lời nhắc + ảnh nền (xem dưới).
- **Mã PIN & khóa thủ công** — đổi PIN, bật/tắt khóa thủ công, reset số phút hôm nay.
- **Phút thưởng** — thưởng +5/+10/+15/+30 phút (nhập PIN).
- **Trợ năng & hiển thị** — trạng thái Trợ năng, bật/tắt chống tắt, ẩn/hiện icon, mở màn hình chính.

Màn hình chính cũng gọn lại: dòng "Hôm qua đã xem" gộp vào dòng "Đã xem hôm nay", bỏ dòng tiêu đề "Cho thêm giờ".

### Xem trước + nút Áp dụng (sửa màn hình khóa)

**Trên TV** (Cài đặt → **Màn hình khóa**): sửa lời nhắc, chọn ảnh mới — các thay đổi chỉ là **bản nháp**, chưa đổi gì trên TV. Bấm **Xem trước** để mở đúng màn hình khóa sẽ hiện trên TV (ảnh + lời nhắc mới, bàn phím chỉ để xem thử, bấm Back để thoát). Ưng rồi bấm **Áp dụng** mới lưu lời nhắc + ảnh vào máy. Bấm **Hủy thay đổi** để quay về bản đang dùng. Nút **Xóa ảnh nền** thì xóa ngay ảnh riêng (không cần Áp dụng).

**Trên điện thoại** (web `http://<IP-TV>:8080/` → tab **Cài đặt** → mục **Màn hình khóa**): phía trên là **bản xem trước trực quan** — vẽ lại màn hình khóa 2 cột giống trên TV (trái: ảnh nền + lời nhắc, phải: bàn phím PIN). Gõ lời nhắc thì chữ trong bản xem trước đổi theo ngay; chọn ảnh thì ảnh trong bản xem trước đổi theo ngay. Khi ưng rồi bấm **Áp dụng lên TV** — lúc đó lời nhắc + ảnh mới được đẩy lên TV cùng một lần. Trước khi bấm Áp dụng thì TV không đổi gì cả.

## 3g. Tính năng mới bản 2.2 (2.2) — Giao diện mới toàn bộ + web mới có tab

Bản 2.2 chỉ đổi giao diện, không đổi luật khóa/mở/PIN/lịch/giới hạn nào.

### Giao diện TV mới (theo bộ thiết kế của anh)

- **Màu mới:** nền xanh đậm `#0E1525`, thẻ `#172035`, chữ `#F3F6FB`, nút chính màu hổ phách `#FFB84D`, đang mở xanh `#3DDC97`, đã khóa đỏ `#FF6B6B`. Thẻ bo tròn 24dp, nút bo 14dp.
- **Trang chủ:** số phút còn lại to 64sp, thẻ trạng thái pill (ĐANG MỞ xanh / ĐÃ KHÓA đỏ), thanh tiến độ, 3 dòng "Hôm nay": Giờ thời sự / Khóa đêm / Phút thưởng, dòng địa chỉ web ở dưới hàng nút.
- **Cài đặt:** vẫn menu 8 mục như bản 2.1, nhưng có thêm **rail bên trái** (250dp) để chuyển nhanh giữa các mục, màn hình nào cũng vừa một trang, chân trang ghi gợi ý phím điều khiển.
- **Màn khóa:** 2 cột — trái là ảnh nền + lời nhắc 48sp (hoặc icon mặt trăng khi không có ảnh riêng), phải là thẻ bàn phím PIN (phím 56dp, chấm PIN "●/○"); nút "Mở thời sự" chỉ hiện trong khung thời sự.
- **Cảnh báo:** dải nhắc phía trên đổi theo màu mới, vẫn không pause video, không giành focus.

### Web điện thoại mới — mở `http://<IP-TV>:8080/`

Web mới là trang tĩnh (không tải lại trang): trạng thái tự cập nhật mỗi 5 giây, thao tác xong hiện **toast** xanh/đỏ ở cuối màn hình. Có **4 tab dưới cùng**:

- **Hôm nay:** giống màn tham chiếu của anh — pill trạng thái + số lớn "1g 12p" còn lại + thanh tiến độ, 2 nút **Khóa ngay** / **Mở khóa** (56px), nhãn "CỘNG THÊM GIỜ" với chip **+5/+10/+15/+20/+30** (48px), thẻ Giờ thời sự / Khóa đêm / Phút thưởng, thẻ **Báo cáo** (hôm nay + 7 ngày gần nhất).
- **Lịch:** danh sách lịch khóa — bấm **Sửa** để sửa ngày/giờ, **Xóa** để xóa, form dưới cùng để thêm lịch mới (tick T2–CN + giờ bắt đầu/kết thúc).
- **Giới hạn:** 7 ngày, mỗi ngày một hàng — chọn mức giới hạn trong ô chọn là lưu ngay.
- **Cài đặt:** danh sách 8 mục như trên TV. Bấm một mục thì mở panel ngay trong tab:
  - **Lịch khóa** → chuyển sang tab Lịch; **Giới hạn ngày** → chuyển sang tab Giới hạn.
  - **Giờ thời sự:** sửa giờ bắt đầu/kết thúc + chọn app thời sự.
  - **Giới hạn từng app:** danh sách app — chọn quy tắc (Bình thường / Giới hạn riêng 15/30/60' / Không tính giờ / Chặn hẳn), đổi là lưu ngay.
  - **Màn hình khóa:** giữ nguyên hành vi bản 2.1 — bản xem trước 2 cột cập nhật live khi gõ lời nhắc/chọn ảnh, bấm **Áp dụng lên TV** mới đẩy lên TV.
  - **Mã PIN:** nhập PIN mới 4–6 số → Đổi PIN (không cần PIN cũ).
  - **Phút thưởng:** hiện quỹ thưởng còn lại + 4 nút +5/+10/+15/+30.
  - **Trợ năng:** hiện trạng thái Trợ năng + chống tắt, nút bật/tắt chống tắt. Bật Trợ năng vẫn phải làm trên TV.

### Trang cũ đã xóa hẳn

Từ bản này, web cũ (dạng form tải lại trang) đã xóa hẳn — chỉ còn web mới ở `http://<IP-TV>:8080/`. Các đường `/legacy`, `/legacy/settings`, `/settings` không còn nữa. Mọi endpoint `/api/*` giữ nguyên đường dẫn và tham số, nhưng các POST cài đặt (`/api/settings-*`, `/api/add`, `/api/bonus`, `/api/lock`, `/api/unlock`, `/api/change-pin`) giờ trả JSON `{"ok":true}` thay vì trang HTML.

> **Từ bản 2.3, web đã có mã PIN** (xem mục 3h dưới đây) — ai cùng WiFi mà không biết mã PIN web thì không mở được trang điều khiển nữa.

## 3h. Mã PIN web (bản 2.3) — mở trang điều khiển phải nhập mã, quên mã vẫn đặt lại được

Từ bản 2.3, mở trang điều khiển trên điện thoại (`http://<IP-TV>:8080/`) phải nhập **mã PIN web** mới vào được. Mã này RIÊNG, khác mã PIN khóa TV, 4–6 chữ số.

### Lần đầu sau khi cập nhật
- Mã PIN web mặc định **bằng mã PIN khóa TV hiện tại** — anh cứ nhập mã PIN khóa TV là vào web được ngay, không phải đặt mã mới.
- Đổi mã PIN khóa TV sau này **không** làm đổi mã PIN web.

### Nhập sai nhiều lần
- Nhập sai 5 lần liên tiếp thì web khóa 5 phút (báo "Sai quá 5 lần, thử lại sau 5 phút"), hết 5 phút nhập lại bình thường.

### Phiên đăng nhập
- Đăng nhập một lần thì điện thoại nhớ 30 ngày (cookie). Nút **Đăng xuất** ở góc trên trang web và trong tab Cài đặt → Mã PIN web.
- TV hoặc app khởi động lại là phiên mất — đăng nhập lại một lần là xong.

### Đổi mã PIN web
- Trên web: tab Cài đặt → **Mã PIN web** → nhập mã mới → "Đổi mã PIN web".
- Trên TV: Cài đặt → **Mã PIN web** → nhập mã PIN khóa TV → màn hình hiện mã PIN web hiện tại, đổi mã mới ở đó.

### Quên mã PIN web thì đặt lại bằng 2 cách (đặt lại = mã PIN khóa TV)
1. **Trên TV (cách chính):** Cài đặt → **Mã PIN web** → nhập mã PIN khóa TV → bấm **Đặt lại = mã PIN khóa TV**. Xong thì mở web bằng mã PIN khóa TV.
2. **Qua app Cài TV trên điện thoại (dự phòng):** mở app Cài TV → kết nối TV → tab **Chống tắt** → bấm **Đặt lại mã PIN web**. Xong thì mở web bằng mã PIN khóa TV. Cần Khóa TV 2.3 trở lên trên TV và Cài TV 1.7 trở lên trên điện thoại.

## 3i. Tính năng mới bản 2.4 (2.4) — Menu nhỏ lại + thanh cuộn, dashboard Tổng quan

### Menu Cài đặt nhỏ lại, không mất góc khi focus
Các mục trong thanh bên trái giờ nhỏ hơn một chút (cao 40dp thay vì 44dp, chữ 18sp thay vì 20sp) và chừa 8dp mỗi bên — khi anh di chuyển tới mục nào, mục đó phóng to lên 1.06 như trước nhưng vẫn nằm gọn trong thanh 250dp, không bị mất góc nữa. Các dòng trong danh sách (lịch khóa, giới hạn ngày, quy tắc app) cũng được chừa khe dọc nhỏ nên khi phóng lên không đè sát dòng kề.

### Thanh cuộn ở menu Cài đặt
Thanh bên trái giờ có **thanh cuộn luôn hiện** (vạch mờ bên phải thanh). Di chuyển xuống mục cuối (Mã PIN web) thì menu tự cuộn theo. Sau này thêm mục mới cũng không lo mất mục dưới.

### Màn Tổng quan thành dashboard
Mở Cài đặt là thấy ngay một bảng tổng quan trực quan, không còn toàn chữ nữa:
- **3 thẻ số liệu:** Đã xem / Còn lại / Phút thưởng hôm nay (số lớn).
- **Thanh tiến độ** hôm nay + dòng "Đã xem X phút · Giới hạn Y".
- **Biểu đồ cột 7 ngày:** 6 ngày gần nhất + hôm nay (cột hôm nay màu hổ phách, ngày trước màu xanh). Ngày nào chưa có dữ liệu thì cột thấp + số 0. Mới dùng chưa có dữ liệu thì hiện dòng "Chưa đủ dữ liệu 7 ngày qua".
- **Khóa đêm** hôm nay và **Giờ thời sự** ở dưới cùng. Nút **Quay lại** vẫn ghim ở đáy màn hình.

## 3j. Tính năng mới bản 2.5 (2.5) — Vào Cài đặt trên TV phải nhập mã PIN

Trước đây nút Cài đặt trên màn hình chính mở thẳng Cài đặt, không cần PIN — lúc TV đang mở (giờ cho phép), trẻ cầm điều khiển là vào sửa được hết lịch khóa/giới hạn. Từ bản 2.5, **mở Cài đặt trên TV luôn phải nhập mã PIN khóa TV trước** (màn hình khóa hiện lý do "Mở Cài đặt").

- Nút **Cài đặt** ở màn hình chính → hiện màn hình nhập PIN → đúng PIN mới vào Cài đặt. Nhập sai thì báo sai, bấm Hủy/Back thì về màn hình chính, không vào được Cài đặt.
- Bấm **bánh răng** trong Cài đặt Trợ năng của TV (mở Cài đặt của app) → cũng hiện màn hình nhập PIN như trên, vì Cài đặt tự chặn ở cửa.
- Đã nhập đúng PIN một lần (ví dụ từ màn khóa bấm "Mở Cài đặt") thì vào thẳng, không hỏi lại lần nữa.
- Các màn con trong Cài đặt chuyển qua lại bằng thanh menu bên trái thì không hỏi PIN lại — đã qua cửa rồi. Mỗi lần mở Cài đặt mới đều hỏi PIN lại (không có "miễn hỏi 5 phút").

Web điều khiển trên điện thoại vẫn dùng mã PIN web riêng như bản 2.3 (xem mục 3h).

## 3k. Tính năng mới bản 2.8 (2.8) — Mã thưởng dùng một lần cho con

> **Từ bản 2.9, mã thưởng đổi sang hiệu lực ngay như nút +5'/+10' (xem mục 3l)** — mục này còn đúng về cách tạo mã, cách nhập mã, chống đoán mò và mã không hết hạn; riêng phần "cộng vào quỹ thưởng / không mở khóa lịch" thì bản 2.9 đã đảo ngược.

Bố tạo mã trên web, đưa cho con, con tự nhập mã trên màn hình khóa của TV để được cộng ngay 5 hoặc 10 phút thưởng. Mỗi mã do bố tạo và đưa từng mã một nên bố vẫn kiểm soát được (mã nào chưa đưa thì con không biết).

### Tạo mã trên điện thoại

Mở trang điều khiển `http://<IP-TV>:8080/` (đăng nhập mã PIN web như bình thường) → tab **Hôm nay** → thẻ **"Mã thưởng cho con"**:

- Bấm **"Tạo mã +5'"** hoặc **"Tạo mã +10'"** — mã 6 số hiện to rõ ngay trên trang, đọc cho con nghe hoặc giơ điện thoại cho con xem.
- Danh sách **mã đang còn hiệu lực** hiện dưới nút tạo (mỗi mã: dãy số + số phút + nút **Xóa**). Bấm Xóa là thu hồi mã đó — con nhập mã đó sẽ báo sai.
- Ghi chú trên trang: "Mã chỉ dùng 1 lần, nhập trên màn hình khóa của TV; chưa dùng thì không hết hạn." — mã không có hạn dùng, cứ để đó bao lâu cũng được.

### Con nhập mã trên TV

Khi TV đang khóa, màn hình khóa có thêm nút **"Nhập mã thưởng"** (dưới bàn phím, trên nút Hủy):

1. Con bấm **"Nhập mã thưởng"** — bàn phím chuyển sang chế độ nhập mã (tiêu đề đổi thành "Nhập mã thưởng — 6 số").
2. Con nhập 6 số rồi bấm **OK**. Đúng mã thì được cộng ngay số phút của mã vào quỹ thưởng, mã hết hiệu lực luôn (nhập lại mã cũ sẽ báo sai).
3. Muốn quay lại nhập mã PIN thì bấm **"← Quay lại nhập mã PIN"**.

### Lưu ý quan trọng

- **Mã thưởng chỉ cộng phút thưởng, không mở khóa trong khung giờ khóa:** nếu con nhập mã lúc TV đang khóa vì hết giờ ngày thì màn khóa tắt luôn, con xem tiếp ("Đã cộng X phút, chúc con xem vui vẻ!"). Nhưng nếu đang trong khung giờ khóa/lịch khóa (ví dụ khóa đêm, khóa cả ngày) thì màn khóa vẫn ở đó và báo "Đã cộng X phút (vẫn đang trong giờ khóa)" — phút thưởng cộng vào sẽ dùng khi hết giờ khóa. Con không thể dùng mã thưởng để "phá" lịch khóa.
- **Chống đoán mò:** con nhập sai 5 lần liên tiếp thì không nhập mã được nữa trong 5 phút ("Sai quá nhiều lần, thử lại sau 5 phút"). Nhập đúng một lần thì số lần sai reset về 0.
- Mã sinh ra không bao giờ là số dễ đoán (không có `000000`, `111111`, `123456`, `654321`...), không trùng mã PIN khóa TV, không trùng mã đang còn hiệu lực.

## 3l. Tính năng mới bản 2.9 (2.9) — Thời sự miễn trừ tuyệt đối, mở khóa về Home, mã thưởng hiệu lực ngay, tab Mã thưởng riêng, giới hạn mã/ngày, chụp màn hình TV

### 1. Khung giờ thời sự: app thời sự được miễn trừ tuyệt đối

Trong khung giờ thời sự, app thời sự đã chọn **không bị chặn vì bất kỳ lý do gì** — không bị chặn bởi giới hạn ngày, giới hạn từng app, lịch khóa, hay khóa tay. Và thời gian xem app đó trong khung đó **không được tính vào thời gian xem** (cả giới hạn ngày lẫn giới hạn từng app).

- Nghĩa là: nếu anh đặt một ngày chỉ được xem 1 phút thì trong khung thời sự vẫn xem đủ app thời sự (không bị khóa giữa chừng vì hết 1 phút đó).
- Nếu anh đặt lịch chặn xem trùng khung thời sự thì vẫn xem được app thời sự trong khung đó.
- Các app khác trong khung giờ thời sự tuân theo quy tắc bình thường (từ v2.10: khung thời sự không còn độc quyền — còn giờ xem và không vướng lịch khóa thì mở được, hết giờ hoặc vướng lịch thì vẫn bị khóa).
- Ngoài khung giờ thời sự, app thời sự tính như app thường.
- Nếu chưa chọn app thời sự thì không có miễn trừ nào. Nút "Mở thời sự" trên màn khóa giữ nguyên.

### 2. Mở khóa trên web thì TV về màn hình chính ngay

Bấm **"Mở khóa"** trên web thì TV tắt màn hình khóa và **về màn hình chính (launcher) ngay**, không cần bấm Home nữa. Kể cả trường hợp lúc bấm mở khóa đang có tin nhắn đè lên trên màn khóa (màn khóa bị lỡ tín hiệu) thì khi tin nhắn tắt, màn khóa cũng tự về Home chứ không sót lại chắn màn hình.

### 3. Nhập mã thưởng xong thì về màn hình chính

Sau khi con nhập mã thưởng thành công và TV hết khóa thì TV **về màn hình chính**, thay vì chỉ đóng màn khóa rồi trả về app đang mở dở trước đó.

### 4. Trong giờ thời sự thì không nhập được mã thưởng

Khi đang trong khung giờ thời sự, nút **"Nhập mã thưởng"** trên màn khóa **ẩn đi**. Nếu cố nhập (ví dụ khung thời sự vừa bắt đầu lúc đang nhập) thì bị từ chối với thông báo "Trong giờ thời sự không dùng mã thưởng" — **không trừ lượt đoán mò, không tiêu mã**.

### 5. Web: menu "Mã thưởng" riêng

Trang điều khiển có thêm tab **"Mã thưởng"** ngay sau tab "Hôm nay". Toàn bộ thẻ "Mã thưởng cho con" (2 nút tạo mã, ô hiện mã mới, danh sách mã còn hiệu lực + nút Xóa, ghi chú) đã dời từ tab Hôm nay sang tab này — tab Hôm nay không còn thẻ mã thưởng nữa.

### 6. Số mã thưởng cho phép trong ngày

Trong tab **Mã thưởng** có thêm ô **"Số mã được nhập mỗi ngày"** (nhập số, 0 = không giới hạn) + nút Lưu, và dòng "Hôm nay đã nhập X / Y mã" (Y là giới hạn, hoặc "không giới hạn" khi đặt 0).

- Ví dụ đặt là 3 thì trong 1 ngày con chỉ được nhập 3 mã. Nhập mã thứ 4 thì bị từ chối: "Hôm nay đã nhập đủ 3 mã rồi, mai nhập tiếp nhé".
- **Quan trọng: mã nhập sau đó không bị tiêu** — mã vẫn còn hiệu lực trong danh sách, hôm sau nhập lại vẫn được. Cũng không cộng thêm giờ, không tăng đếm.
- Đếm tự reset khi sang ngày mới. Mặc định là 0 = không giới hạn (như các bản trước).

### 7. Mã thưởng có hiệu lực NGAY — đảo quyết định bản 2.8

**Bản 2.8 cộng mã thưởng vào quỹ thưởng (chỉ trừ khi đã hết giờ ngày, không mở khóa lịch). Bản 2.9 đảo ngược:** mã thưởng giờ giống nút +5'/+10' sau PIN — **cộng bằng mở tạm, hiệu lực ngay lập tức**. Lý do: mã thưởng chỉ nhập được khi màn hình đang khóa nên hiệu lực ngay mới đúng.

- Đổi mã đúng + hết khóa thì về màn hình chính (mục 3). Vẫn khóa (trường hợp hiếm) thì ở lại màn khóa, báo đã cộng phút.
- Ghi chú này để lần sau không ai "sửa ngược" lại thành quỹ thưởng nếu không có yêu cầu mới.

### 8. Web: nút chụp ảnh màn hình TV

Tab **Hôm nay** có thêm nút **"Chụp màn hình TV"**. Bấm vào thì ảnh JPEG khung hình TV đang hiện hiện ngay dưới nút, kèm thời gian chụp và nút "Chụp lại". Chưa cấp quyền thì hiện thông báo lỗi từ server.

**Cần cấp quyền 1 lần trên TV trước** (Android không cho app tự chụp màn hình lén):

1. Mở app Khóa TV trên TV → Cài đặt → **Chụp màn hình** → bấm **"Cấp quyền chụp màn hình"** → đồng ý ở hộp thoại hệ thống.
2. Màn hình đó hiện trạng thái: Đã cấp quyền / Chưa cấp quyền / Cần cấp lại sau khi khởi động lại TV. Nút "Tắt chụp màn hình" để dừng hẳn.

**Lưu ý quan trọng: sau khi khởi động lại TV thì quyền chụp màn hình mất, phải cấp lại bằng tay — không có cách tự động.** (Android bắt dialog đồng ý mỗi lần khởi động lại; app không tự lấy lại được.)

## 3m. Tính năng mới bản 2.10 (2.10) — Khung thời sự không còn độc quyền

Từ bản này, khung giờ thời sự **không còn chặn các app khác**. Trong khung thời sự:
- App thời sự đã chọn vẫn được miễn trừ tuyệt đối như bản 2.9 (không bị chặn vì bất kỳ lý do gì, thời gian xem trong khung không tính vào giới hạn).
- Các app khác tuân theo quy tắc bình thường: còn giờ xem và không vướng lịch khóa/khóa tay thì mở được; hết giờ ngày, hết giờ riêng của app, vướng lịch khóa hoặc đang khóa tay thì vẫn bị khóa như ngoài khung.

Nói ngắn gọn: khung thời sự giờ chỉ còn là "quyền lợi riêng" cho app thời sự, không còn là "lệnh cấm" cho các app khác.

## 3n. Sửa lỗi bản 2.11 (2.11) — Đếm ngược bị đứng yên sau khi bật TV

Lỗi anh gặp lúc 18:58 ngày 29/10: TV vừa bật lên thì hiện "còn 9s nữa" nhưng con số đứng yên mãi, đến khi mở một app thì nó mới đếm về 0 và khóa lại.

Nguyên nhân: sau khi khởi động, app khóa chạy lại ngay nhưng dịch vụ Trợ năng cần một lúc mới báo cho app biết đang mở app gì. Trong khoảng trống đó app không tính giờ xem (không biết đang xem gì thì không tính), nên con số "còn 9 giây" là dữ liệu cũ đóng băng — mà bảng đếm ngược cứ hiện đi hiện lại đúng con số đó. Bảng dự phòng còn có lỗi riêng: không tự cập nhật chữ khi được gọi lại.

Bản 2.11 sửa hai chỗ:
- Khi chưa biết đang xem app gì (chưa tính giờ) thì không hiện đếm ngược cho các hạn tính theo giờ xem (giới hạn ngày, giới hạn từng app) nữa — chỉ hiện đếm ngược cho hạn theo giờ giấc (lịch khóa) vì hạn đó không phụ thuộc việc đếm giờ.
- Bảng cảnh báo dự phòng giờ tự cập nhật chữ mỗi lần được gọi lại (và đếm lại 20 giây tự tắt), nên không bao giờ hiện con số cũ đứng yên nữa.

## 3o. Tính năng mới bản 3.0 (3.0) — Màn hình chính 2-trong-1 (gộp launcher vào Khóa TV)

App Khóa TV giờ kiêm luôn màn hình chính của TV — một app duy nhất, kiểu Apple TV đơn giản: đồng hồ to + ngày tháng, thời tiết, mấy dòng app. Khóa thì vẫn như cũ, không đổi gì.

**Cài đặt:** file APK mới (`khoa-tv.apk` bản 3.0) cài đè lên bản 2.11 đang chạy trên TV qua app Cài TV như mọi khi — **mọi cài đặt khóa của anh (lịch khóa, giới hạn ngày, mã PIN, mã thưởng...) giữ nguyên hết**, vì app mới dùng chung package và chung bộ nhớ cài đặt với bản cũ.

**Đặt làm màn hình chính mặc định:** mở app Cài TV → tab Lệnh ADB → gõ:
```
cmd package set-home-activity vn.tvlock/.HomeActivity
```
Từ đó bấm nút Home trên remote là về màn hình chính mới. Muốn về lại launcher cũ (CleanTV) thì đặt lại bằng lệnh tương tự với launcher cũ, hoặc gỡ Khóa TV (không nên — gỡ là mất luôn khóa).

**Những gì có trên màn hình chính:**
- Đồng hồ to + ngày tháng tiếng Việt; thời tiết (nhiệt độ + mô tả, ví dụ "28° • Ít mây • Hà Nội") — mở lên có ngay, 30 phút tải mới một lần, không tốn mạng.
- Hàng "⭐ Yêu thích": app nào hay dùng thì **nhấn giữ nút OK** trên app đó → "Ghim vào yêu thích". Bỏ ghim cũng nhấn giữ → "Bỏ ghim".
- Lưới "Tất cả ứng dụng": mọi app trên TV. App nào không thích (app rác của Xiaomi...) thì nhấn giữ → "Ẩn ứng dụng này" cho gọn; muốn hiện lại thì vào Cài đặt → Màn hình chính → "Xem / hiện lại app đã ẩn".
- Di chuyển bằng remote như bình thường, ô nào đang chọn sẽ sáng viền lên rõ ràng.

**Tùy chỉnh trong Cài đặt → Màn hình chính** (vào Cài đặt trên TV vẫn phải nhập mã PIN như cũ):
- **Ảnh nền:** chọn ảnh của anh làm nền màn hình chính, hoặc xóa để về nền mặc định.
- **Kiểu icon ứng dụng:** "Mặc định" (icon gốc của app) hoặc "Tròn" (cắt tròn, kiểu Apple TV) — anh chọn kiểu nào thì cả màn hình đổi theo.
- **Thành phố thời tiết:** Hà Nội / Đà Nẵng / TP.HCM.

**Lúc TV đang bị khóa thì sao:** bấm Home vẫn ra màn hình khóa như cũ (nhập PIN, nút "Mở thời sự", nhập mã thưởng... đầy đủ). Mở khóa xong thì về lại màn hình chính mới. Trẻ con không mở được app nào khi TV đang khóa — y như trước.

**Lưu ý:** camera chưa có trong bản này — để khi nào anh tìm lại được mật khẩu camera thì làm tiếp.

## 3p. Đường lui khẩn cấp (đọc trước khi đặt làm màn hình chính)

TV không thể "chết" vì app này — Android luôn có đường lui, và anh có sẵn trong tay:

**1. Xem thử trước khi đặt mặc định** (không cần bấm Home): mở app Cài TV → tab Lệnh ADB → gõ:
```
am start -n vn.tvlock/.HomeActivity
```
Màn hình chính mới hiện lên để anh vọc thử; bấm Home là về lại launcher cũ như chưa có gì.

**2. Về lại launcher cũ** (nếu màn hình mới có lỗi): tab Lệnh ADB →
```
cmd package set-home-activity com.ts.launcher/com.gathin.tvhome.MainActivity
```
Bấm Home là về lại CleanTV như cũ.

**3. Trường hợp xấu nhất** (màn hình chính lỗi liên tục): tab Lệnh ADB →
```
pm uninstall -k --user 0 vn.tvlock
```
Gỡ app nhưng **giữ nguyên mọi cài đặt** (nhờ `-k`); TV tự về lại launcher cũ. Cài lại bản mới sau là mọi thứ còn nguyên. Hoặc dừng khẩn app đang chạy: `am force-stop vn.tvlock` (khóa tắt theo, TV dùng bình thường).

**4. Mấy lớp an toàn có sẵn:** nếu launcher mặc định crash liên tục, Android tự hiện lại bảng chọn launcher; nút mic trên remote vẫn gọi được "mở Cài đặt" bằng giọng nói kể cả khi màn hình chính lỗi; trang web điều khiển (`http://IP-TV:8080`) vẫn mở khóa từ xa được bằng mã PIN web.

Phần khóa trong bản 3.0 là code cũ của bản 2.11 đã chạy ổn định trên TV mấy ngày nay — em không đụng vào một dòng logic nào, chỉ thêm màn hình chính chạy độc lập bên cạnh. Nên rủi ro thực tế chỉ nằm ở màn hình mới, mà màn hình mới thì luôn có 4 đường lui ở trên.
