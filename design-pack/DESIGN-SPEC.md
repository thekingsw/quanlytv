# Đặc tả thiết kế Khóa TV
Nguồn chân lý: design-tokens.json. Ảnh minh họa: screenshots/*.png. HTML tham chiếu: design/*.html (mở bằng trình duyệt).

## Nguyên tắc
- Một ngôn ngữ chung cho TV và web: nền xanh đêm, thẻ bo tròn, một màu nhấn hổ phách; xanh lá = đang mở, đỏ san hô = khóa.
- Trang chủ chỉ trả lời: đang mở hay khóa, còn bao nhiêu phút, bấm gì nhanh nhất.
- Cài đặt = thanh bên trái + khung nội dung phải (TV); thanh tab dưới (điện thoại). Thêm tính năng = thêm một mục vào danh sách, không đổi bố cục.
- Màn khóa (bé nhìn thấy): lời nhắn lớn, nút "Mở thời sự"; PIN nằm trong thẻ riêng "Dành cho bố mẹ".

## Quy tắc TV 4K (Xiaomi A Pro 55")
- Chỉ dùng dp/sp. Khung tham chiếu 960x540dp, kiểm tra thêm 1280x720dp.
- Lề an toàn 48dp trái/phải, 27dp trên/dưới.
- Mọi phần tử bấm được phải focusable, có trạng thái focus rõ (viền trắng 4dp + vòng sáng hổ phách + phóng 1.06), nextFocus rõ ràng, focus ban đầu hợp lý, Back lùi một cấp.
- Chữ tối thiểu 16sp, nội dung 20sp trở lên.

## Kiến trúc mở rộng
- SettingsRegistry: danh sách (id, tiêu đề, icon, màn đích) cho thanh bên TV và danh sách/tab web.
- Component dùng lại: StatusPill, Card, ListRow (72dp), TvButton (primary/success/danger/ghost), TimeStepper (± giờ/phút, giữ để tăng liên tục, dùng HoldRepeat), SectionHeader.
- Theme tập trung: colors.xml, dimens.xml, styles.xml. Không để số cứng trong layout.
- Web: trang tĩnh assets/web/{index.html,app.css,app.js}, gọi API JSON, cập nhật tại chỗ; tab đọc từ một mảng cấu hình trong app.js; CSS variables lấy từ design-tokens.json.

## Màn hình trong design/
1. tv-home.html: trạng thái + số phút còn lại + thanh tiến độ, thẻ "Hôm nay", hàng nút nhanh (Khóa ngay, Mở khóa, +5 +10 +15 +30, Cài đặt). Nút phụ cũ (Mở app thời sự, Bật Trợ năng, +20) chuyển vào Cài đặt hoặc thẻ phù hợp.
2. tv-settings.html: rail + pane; ví dụ mục Lịch khóa dạng ListRow; dòng gợi ý phím ở cuối.
3. tv-lock.html: màn khóa + bàn phím PIN 3x4 (phím 56dp). Sau khi nhập đúng PIN: thẻ chọn thao tác cùng phong cách.
4. phone-home.html: web 390px, thẻ trạng thái, hai nút Khóa/Mở, hàng chip +5..+30, thẻ hôm nay, tab dưới.
Các màn còn lại (Sửa lịch, Giới hạn ngày, Giờ thời sự, Giới hạn app, Màn hình khóa, PIN, Phút thưởng, Trợ năng, Cảnh báo 5 phút) dựng bằng cùng component và token, không cần thiết kế riêng.
