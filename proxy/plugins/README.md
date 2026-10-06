# Plugin của proxy

- **Geyser** và **Floodgate** được tải tự động (biến `PLUGINS` trong `proxy/compose.yaml`).
- Muốn thêm plugin Velocity khác: thêm URL vào `PLUGINS`, hoặc đặt file `.jar` vào thư mục này
  (file `.jar` không được commit, xem `.gitignore`).
- Cấu hình plugin có thể đặt ở đây theo đúng thư mục của plugin, ví dụ `Geyser-Velocity/config.yml`.
  Các file cấu hình được copy vào `/server/plugins` khi khởi động.

## Việc cần kiểm tra ở lần chạy đầu
Sau lần chạy đầu, mở `data/proxy/plugins/Geyser-Velocity/config.yml` và kiểm tra
**auth-type** đang là `floodgate` (để người chơi Bedrock không cần tài khoản Java).
Nếu chưa, sửa thành `floodgate` rồi `docker compose restart proxy`.
Khi đã ổn, copy file cấu hình đó vào `proxy/plugins/Geyser-Velocity/config.yml` để lưu vào Git.
