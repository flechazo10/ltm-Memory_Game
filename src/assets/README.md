# Hướng dẫn thay ảnh / nhạc

Mọi file ảnh, nhạc đặt trong thư mục `src/assets/`. **Giữ đúng tên file và thư mục** như dưới đây thì không phải sửa code.

Cách nhanh nhất: chép nguyên thư mục `assets` của base cũ (`D:\ltMang\BTL\LTM\src\assets`) sang đây, rồi thay dần từng file.

---

## 1. Ảnh mặt trước lá bài — BẮT BUỘC

Server đọc 12 ảnh cho mỗi chủ đề. **Thiếu 1 ảnh là trận không bắt đầu được** (server báo lỗi "Lỗi server, không thể bắt đầu trận đấu").

```
assets/images/<chủ đề>/img01.jpg
assets/images/<chủ đề>/img02.jpg
...
assets/images/<chủ đề>/img12.jpg
```

- `<chủ đề>` là 1 trong 5 thư mục: `badminton`, `footballer`, `kimesu`, `onepunchman`, `sakamoto`
- Tổng cộng 5 × 12 = **60 ảnh**
- Bắt buộc đuôi **.jpg**, tên đúng 2 chữ số: `img01.jpg` … `img12.jpg`
- Gợi ý: ảnh vuông, khoảng **300×300 px**, mỗi ảnh < 100 KB (ảnh được gửi qua mạng mỗi trận)

## 2. Ảnh mặt sau lá bài — tuỳ chọn

Thiếu ảnh thì lá bài hiện ô xám có dấu **?** (vẫn chơi được).

| Chủ đề | File |
|---|---|
| kimesu, sakamoto, mặc định | `assets/images/background/back_card.jpg` |
| onepunchman | `assets/images/background/opm.jpg` |
| footballer | `assets/images/background/ball.jpg` |
| badminton | `assets/images/background/qua.jpg` |

Gợi ý: ảnh vuông **300×300 px**.

## 3. Ảnh nền màn chơi theo chủ đề — tuỳ chọn

Thiếu ảnh thì màn chơi dùng nền xám nhạt mặc định.

| Chủ đề | File |
|---|---|
| kimesu (và mặc định) | `assets/images/background/Kimetsu.jpg` |
| onepunchman | `assets/images/background/back_ground_opm.jpg` |
| footballer | `assets/images/background/sta.jpg` |
| badminton | `assets/images/background/caulong.jpg` |
| sakamoto | `assets/images/background/sakamoto.jpg` |

Gợi ý: **1280×800 px** trở lên, ảnh tối/nhạt vừa phải vì bàn bài và thẻ người chơi nằm đè lên.

## 4. Ảnh nền chung cho các màn khác — tuỳ chọn

Mặc định các màn (đăng nhập, sảnh, xếp hạng...) dùng nền xám nhạt. Muốn dùng ảnh:

1. Đặt ảnh vào `assets/images/background/app_bg.jpg`
2. Mở `src/client/view/style.css`, tìm khối `.app-root`, bỏ comment 2 dòng `-fx-background-image` và `-fx-background-size`

## 5. Nhạc nền — tuỳ chọn

```
assets/sound/ifinitycastle.mp3
```

- Định dạng **MP3**. Thiếu file thì game chạy không có nhạc.
- Muốn đổi tên file: sửa `MUSIC_PATH` trong `src/client/service/ClientService.java`

## 6. Không còn dùng

`btn_on_music.png`, `btn_off_music.png`: nút nhạc giờ là nút chữ ("Nhạc: Bật / Tắt"), không cần 2 ảnh này nữa.

---

## Muốn đổi / thêm chủ đề

1. Tạo thư mục `assets/images/<tên mới>/` với đủ `img01.jpg` … `img12.jpg`
2. Thêm tên vào mảng `categories` trong `src/client/controller/MainController.java` (hàm `showInviteDialog`)
3. (Tuỳ chọn) Thêm ảnh nền / mặt sau trong 2 bảng `THEME_BACKGROUNDS`, `THEME_BACK_CARDS` ở đầu `src/client/controller/GameRoomController.java`

## Đổi màu giao diện

Mở `src/client/view/style.css`, sửa các biến `-c-...` trong khối `.root` ở đầu file (ví dụ `-c-accent` là màu nhấn xanh dương).
