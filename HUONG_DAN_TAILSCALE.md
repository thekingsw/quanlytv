# Điều khiển Khóa TV từ xa bằng Tailscale (5G / mạng công ty đều được)

App Khóa TV bản hiện tại chỉ mở được khi điện thoại cùng WiFi nhà. Thêm Tailscale thì điện thoại ở đâu cũng điều khiển được, không cần sửa app, không cần mở cổng router.

## Chuẩn bị

- Một tài khoản Tailscale miễn phí (cá nhân): đăng ký tại https://login.tailscale.com — đăng nhập bằng tài khoản Google / Microsoft / GitHub / Apple có sẵn, không cần tạo mật khẩu mới.
- Dùng **cùng một tài khoản** này cho cả điện thoại và TV.

## 1. Điện thoại: cài Tailscale

1. Cài app **Tailscale** từ App Store (iPhone) hoặc CH Play (Android).
2. Mở app, đăng nhập đúng tài khoản ở trên, bấm **Connect** và cho phép tạo cấu hình VPN khi điện thoại hỏi.
3. Ghi lại tên máy và địa chỉ IP của điện thoại trong app (dạng `100.x.x.x`) — chỉ để đối chiếu, không bắt buộc.

## 2. TV: cài Tailscale

Thử **Cách 1** trước, không được mới dùng Cách 2.

**Cách 1 — CH Play trên TV (nên thử trước):**
Trên TV mở CH Play, tìm `Tailscale`, cài đặt như app thường.

**Cách 2 — cài file APK có sẵn (nếu CH Play trên TV không có Tailscale):**
- File: `tailscale-android-1.104.2.apk` (bản chính thức từ pkgs.tailscale.com, package `com.tailscale.ipn`, 101 MB) — gửi sang TV bằng **Send Files to TV** hoặc USB, giống cách cài `khoa-tv.apk`.
- Khi TV hỏi, cho phép **Cài đặt từ nguồn không xác định** cho app vừa dùng để cài.

## 3. Đăng nhập Tailscale trên TV

1. Mở app **Tailscale** trên TV, bấm **Sign in / Log in**.
2. TV hiện một đường link (và mã). Mở link đó trên điện thoại hoặc máy tính, đăng nhập đúng tài khoản Tailscale rồi bấm duyệt cho TV.
3. TV hỏi tạo cấu hình VPN thì bấm **Cho phép / OK**.
4. Khi app Tailscale trên TV hiện **Connected** là xong. Ghi lại địa chỉ IP của TV trong app (dạng `100.x.x.x`) — đây là địa chỉ anh sẽ dùng từ xa.

## 4. Một cài đặt quan trọng: tắt hết hạn khóa trên TV

Mặc định Tailscale sẽ bắt đăng nhập lại sau 180 ngày. TV để lâu dễ quên nên tắt đi cho yên tâm:

1. Trên điện thoại hoặc máy tính, mở https://login.tailscale.com/admin/machines, đăng nhập.
2. Tìm máy TV trong danh sách → bấm `...` → **Disable key expiry**.
3. Kiểm tra cả điện thoại và TV đều hiện **Connected**.

## 5. Thử điều khiển từ xa

1. Trên điện thoại: **tắt WiFi, bật 5G**, mở app Tailscale và chắc chắn nó đang **Connected**.
2. Mở trình duyệt, vào `http://<IP-Tailscale-của-TV>:8080` — ví dụ `http://100.90.12.34:8080`.
3. Nhập **mã PIN** của app Khóa TV (mặc định `1234` nếu anh chưa đổi), rồi bấm **Khóa ngay** hoặc **+5 / +10 / +15 / +20 / +30 phút**.
4. Nếu vào được và bấm được là thành công. Lúc này TV vẫn ở nhà, điện thoại anh ở đâu cũng được.

Mẹo: thay vì nhớ dãy số `100.x.x.x`, anh có thể dùng tên máy: `http://<ten-tv>.tailxxxx.ts.net:8080` (xem tên đầy đủ trong app Tailscale hoặc trang admin).

## Nếu không vào được, kiểm tra theo thứ tự

1. Tailscale trên TV có đang **Connected** không? TV có đang bật WiFi nhà không?
2. Tailscale trên điện thoại có đang **Connected** không? (5G vẫn cần bật Tailscale.)
3. Anh có gõ đúng IP **Tailscale** của TV (`100.x.x.x`) không, hay đang gõ nhầm IP WiFi (`192.168.x.x`)? IP WiFi chỉ dùng được khi cùng mạng nhà.
4. App **Khóa TV** trên TV có đang chạy không? Mở app Khóa TV lên một lần để chắc chắn dịch vụ đang chạy.
5. Thử tắt Tailscale rồi bật lại trên cả hai máy, đợi 10 giây rồi thử lại.

## Lưu ý

- TV phải luôn bật WiFi nhà + Tailscale thì mới điều khiển từ xa được. Sau khi TV khởi động lại, kiểm tra lại Tailscale có tự chạy lại không.
- Chỉ các máy trong tài khoản Tailscale của anh mới thấy được TV, và vẫn phải nhập PIN mới điều khiển được.
- Không dùng chung VPN công ty (OpenVPN) cho việc này — Tailscale dùng tài khoản cá nhân riêng, tách hẳn khỏi mạng công ty.
