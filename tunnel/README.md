# Tunnel playit.gg

Cho bạn bè ngoài mạng nhà vào server mà **không mở port trên modem** và không lộ IP nhà.
Agent chạy trong Docker, chung mạng với proxy. Mặc định **không chạy**, bật bằng profile `tunnel`.

## Cài lần đầu
1. Tạo tài khoản ở https://playit.gg.
2. Lấy **secret key cho Docker**: https://playit.gg/account/setup/wizard/new-account/docker/docker-name
   (link trong README chính thức của playit agent). Dán vào `.env`:
   ```
   PLAYIT_SECRET_KEY=<key>
   ```
   Key này cho phép điều khiển tunnel của bạn. **Không commit, không gửi ai.**
3. Bật agent:
   ```bash
   docker compose --profile tunnel up -d
   docker compose logs -f playit
   ```
4. Trên trang playit.gg, tạo 2 tunnel cho agent vừa kết nối:
   | Tunnel | Loại | Địa chỉ local |
   |---|---|---|
   | Java | Minecraft Java (TCP) | `proxy` cổng `25577` |
   | Bedrock | Minecraft Bedrock (UDP) | `proxy` cổng `19132` |
5. playit.gg cho một địa chỉ dạng `ten-ngau-nhien.gl.joinmc.link` (Java) và một địa chỉ/cổng cho Bedrock. Gửi cho bạn bè.
   Khi mua tên miền thì trỏ tên miền vào đó (trang playit có hướng dẫn).

## Chưa kiểm tra được (môi trường dựng khung chặn playit.gg)
- Trang playit có nhận **tên service `proxy`** làm địa chỉ local không. Nếu chỉ nhận IP:
  - Linux: thêm `network_mode: host` vào service `playit` trong `tunnel/compose.yaml`, bỏ `depends_on`,
    rồi đặt địa chỉ local là `127.0.0.1:25565` (Java) và `127.0.0.1:19132` (Bedrock), tức cổng proxy mở ra máy.
  - Windows/macOS (Docker Desktop): `network_mode: host` có thể không chạy. Khi đó cài playit trực tiếp trên máy
    (https://playit.gg/download) và trỏ vào `127.0.0.1:25565` / `127.0.0.1:19132`.
- Tunnel Bedrock (UDP) có cần gói trả phí không.

## Lưu ý: mọi người chơi đi qua playit sẽ có **chung một IP**
Proxy thấy IP của agent playit, không thấy IP thật của người chơi. Hệ quả:
- **Phiên đăng nhập theo IP của LibreLogin đã tắt** (`session-timeout=0` trong `proxy/plugins/librelogin/config.conf`).
  Nếu bật, người khác gõ tên của bạn sẽ vào được mà không cần mật khẩu. Người chơi crack phải `/login` mỗi lần vào.
- Không đối chiếu IP để bắt acc phụ được (luật chống lạm dụng trong `docs/banghoi-design.md`, mục 9).
- Velocity giới hạn đăng nhập theo IP (`login-ratelimit = 3000`, tức 1 lần mỗi 3 giây cho mỗi IP).
  Nhiều người vào cùng lúc có thể bị báo vào quá nhanh, thử lại là được. Nếu phiền thì giảm giá trị này trong `velocity.toml`.

### Cách lấy lại IP thật: PROXY protocol (làm sau, cần kiểm tra)
playit.gg có tuỳ chọn gửi IP thật qua **PROXY protocol** cho tunnel (cần kiểm tra trên trang playit, có thể chỉ ở gói trả phí).
Nếu dùng:
1. Bật PROXY protocol cho tunnel Java trên trang playit.
2. Đặt `haproxy-protocol = true` trong `proxy/config/velocity.toml`.
   Sau đó **mọi** kết nối Java phải đi qua playit; vào thẳng bằng `localhost` trong mạng nhà sẽ không được nữa.
3. Kiểm tra log proxy thấy IP thật, rồi mới bật lại `session-timeout` của LibreLogin.
