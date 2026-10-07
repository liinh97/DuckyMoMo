# Limbo: phòng chờ đăng nhập

Server Minecraft siêu nhẹ (NanoLimbo, khoảng 5 MB) để giữ người chơi crack cho tới khi `/login` hoặc `/register`.
LibreLogin trên proxy điều khiển việc đưa vào và đưa ra. Không có thế giới, không lưu dữ liệu.

- `compose.yaml`: chạy bằng image Java (`eclipse-temurin:21-jre-alpine`), **không mở cổng ra ngoài**.
- `entrypoint.sh`: lần đầu tải `NanoLimbo.jar` vào `data/limbo/`, điền `VELOCITY_SECRET` vào `settings.yml`, rồi chạy.
- `settings.yml`: lấy từ bản gốc của NanoLimbo, đổi cổng `25565`, forwarding `MODERN`, chữ hiển thị tiếng Việt.

Lý do không dùng lobby làm chỗ đăng nhập: người chơi chưa đăng nhập sẽ đi lại, nhìn thấy và tương tác được trong lobby.
LibreLogin cũng khuyên dùng NanoLimbo.

## Cần kiểm tra ở lần chạy đầu
- Link `NANOLIMBO_URL` trong `.env` **chưa kiểm tra** tên file. Lỗi thì tải tay `.jar` từ
  https://github.com/Nan1t/NanoLimbo/releases và đặt vào `data/limbo/NanoLimbo.jar`.
- NanoLimbo phải hỗ trợ đúng phiên bản Minecraft đang chạy (xem README của NanoLimbo). Nâng Minecraft thì nâng cả NanoLimbo:
  xoá `data/limbo/NanoLimbo.jar` rồi `docker compose restart limbo`.
- `settings.yml` lấy từ nhánh `main` của NanoLimbo, có thể mới hơn bản release. Nếu log báo lỗi cấu hình,
  xoá các mục bị báo rồi chạy lại.
