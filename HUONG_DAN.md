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
- **Khung giờ thời sự: 19:00 – 20:00** — chọn **app thời sự** trong danh sách. Trong khung này TV chỉ mở được đúng app đó, các app khác mở lên sẽ hiện màn hình khóa. Thời gian xem app thời sự trong khung này **không tính** vào giới hạn ngày. Nếu chưa chọn app nào thì khung thời sự không có tác dụng gì thêm. Khung thời sự KHÔNG nằm trong danh sách lịch khóa — nó là luật riêng, luôn giữ nguyên như cũ.
- **Khóa thủ công**: bật/tắt khóa ngay.
- **Reset số phút đã xem hôm nay** nếu muốn cho xem lại từ đầu.

## 3b. Tính năng mới bản v3 (1.2)

- **Giới hạn từng app** (trong Cài đặt → mục **Giới hạn từng app** → bấm **Chọn app để đặt giới hạn**): chọn một app rồi chọn chế độ: **Bình thường** (tính vào giới hạn ngày như cũ) / **Giới hạn riêng 15 / 30 / 60 phút mỗi ngày** / **Không tính giờ** (app học — xem bao lâu cũng được, không cộng vào giới hạn ngày) / **Chặn hẳn** (mở lên là hiện màn hình khóa, trừ khi nhập PIN mở tạm). Lưu xong áp dụng ngay, không cần khởi động lại. Hết giờ của app nào thì chỉ app đó bị khóa (màn hình khóa ghi rõ lý do, ví dụ "Hết giờ cho app YouTube"), TV vẫn xem app khác được. Thứ tự ưu tiên: khung thời sự 19:00–20:00 vẫn cao nhất; ngoài khung đó thì quy tắc của app được áp dụng; khóa thủ công / khung khóa / giới hạn ngày như cũ. Lưu ý: app "Không tính giờ" vẫn bị khóa trong các lịch khóa và khi bố khóa thủ công. Đổi quy tắc chỉ làm trên TV trong bản này; trang web điện thoại chỉ xem số phút từng app.
- **Phút thưởng** (trang web điện thoại → mục **Thưởng phút**, hoặc trên TV: Cài đặt → mục **Phút thưởng**): bấm **Thưởng +5 / +10 / +15 / +30 phút** để cộng vào quỹ thưởng. Khi hết giờ ngày mà quỹ còn phút, TV vẫn mở và tự trừ dần quỹ, mỗi phút xem trừ 1 phút thưởng; hết cả phút thưởng mới khóa. Phút thưởng chưa dùng hết **dồn sang hôm sau** (khác với phút ngày chưa dùng thì không dồn). Số phút thưởng còn lại hiện trên màn hình chính của TV ("Còn lại hôm nay: X phút + thưởng Y phút"), trong Cài đặt, và trên trang web. Trên TV phải nhập PIN trước khi thưởng (bấm nút rồi nhập PIN); trên điện thoại thì không cần PIN. Khác với nút "+5 phút" thường (mở khóa ngay 5 phút): phút thưởng chỉ có tác dụng khi đã hết giờ ngày.
- **Nhắc nhẹ trước khi khóa**: khi còn đúng **5 phút** và **1 phút** nữa là hết giờ ngày (tính cả phút thưởng), hoặc hết giờ riêng của app đang xem, hoặc đến giờ bắt đầu khung khóa, TV hiện một bảng nhắc nhỏ ở phía trên màn hình ("Còn 5 phút nữa là hết giờ xem"), tự tắt sau 20 giây hoặc khi bấm **Đã hiểu** / Back trên điều khiển. Bảng nhắc không chặn việc đang xem — trẻ vẫn xem tiếp được đến khi khóa thật. Mỗi mốc chỉ nhắc một lần; được cộng thêm giờ hoặc phút thưởng thì nhắc lại từ đầu cho lần tới hạn mới.
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
- **Cài đặt từ điện thoại:** trang chính của web có đường dẫn **"Mở Cài đặt"** (hoặc vào thẳng `http://<IP-TV>:8080/settings`) — sửa được ngay trên điện thoại, không cần mở TV:
  - **Giờ thời sự:** chọn giờ bắt đầu / kết thúc (ô nhập giờ, bấm vào chỉnh giờ và phút riêng) + chọn app thời sự trong danh sách.
  - **Giới hạn xem từng ngày:** chọn mức cho từng ngày Thứ 2 … Chủ nhật (5 / 10 / 15 / 30 / 60 / 90 / 120 / 180 / 240 phút hoặc Không giới hạn).
  - **Lịch khóa:** sửa từng lịch có sẵn (tick ngày, giờ bắt đầu / kết thúc), xóa lịch, hoặc thêm lịch mới.
  - Lưu xong áp dụng ngay trên TV, không cần khởi động lại. Như các nút khác trên web, cài đặt từ điện thoại **không cần nhập PIN** (điện thoại do anh cầm).

## 4. Điều khiển từ điện thoại (cùng WiFi)

1. Màn hình chính của app trên TV hiện địa chỉ dạng `http://192.168.x.x:8080`.
2. Điện thoại **cùng WiFi nhà** mở trình duyệt tới địa chỉ đó.
3. Bấm một cái là xong, **không cần nhập mã PIN**: **Khóa ngay**, **Mở khóa 60 phút**, hoặc **+5 / +10 / +15 / +20 / +30 phút**. Mục **Thưởng phút** cộng phút vào quỹ thưởng (+5 / +10 / +15 / +30), mục **Báo cáo** xem hôm nay xem app nào bao nhiêu phút và 7 ngày gần nhất.
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
   **Lưu ý:** lệnh này chỉ thành công khi trên TV chưa có tài khoản Google nào đặt làm quản trị thiết bị khác — nếu báo lỗi, anh cứ dùng app bình thường, chỉ là bước chống gỡ không áp dụng được.
3. Sau khi đặt Device Owner thành công, app không thể bị gỡ từ Cài đặt nữa (chỉ gỡ được bằng lệnh `adb shell dpm remove-active-admin vn.tvlock/.DeviceOwnerReceiver` rồi gỡ).

## 6. Kiểm tra sau khi cài

1. Khởi động lại TV, mở YouTube xem thử vài phút — màn hình chính app phải đếm số phút đã xem tăng lên.
2. Bấm **Khóa ngay** trên app (hoặc từ điện thoại), rồi thử mở YouTube — phải hiện ngay màn hình khóa đỏ, nhập PIN mới mở được.
3. Thêm tạm một lịch khóa vào giờ hiện tại để thử, rồi xóa lịch đó đi.
4. Vào khung 19:00–20:00 thử mở app khác ngoài app thời sự đã chọn — phải bị chặn.

## Gỡ app (khi không cần nữa)

Cài đặt TV → Ứng dụng → Khóa TV → Gỡ cài đặt. Nhớ tắt dịch vụ Trợ năng của app trước khi gỡ cho sạch.
