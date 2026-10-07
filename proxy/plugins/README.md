# Plugin của proxy

- **Geyser**, **Floodgate** và **LibreLogin** được tải tự động (biến `PLUGINS` trong `proxy/compose.yaml`).
- **ViaVersion** và **ViaBackwards** được tải tự động từ SpigotMC (biến `SPIGET_PLUGINS`).
- Muốn thêm plugin Velocity khác: thêm URL vào `PLUGINS`, hoặc đặt file `.jar` vào thư mục này
  (file `.jar` không được commit, xem `.gitignore`).
- Cấu hình plugin có thể đặt ở đây theo đúng thư mục của plugin, ví dụ `Geyser-Velocity/config.yml`.
  Các file cấu hình được copy vào `/server/plugins` khi khởi động.

## LibreLogin: đăng nhập cho người chơi crack
Proxy để `online-mode = false` nên ai cũng vào được. LibreLogin chặn ở cửa:

| Người chơi | Lần đầu | Lần sau |
|---|---|---|
| Crack | Vào phòng chờ `limbo`, gõ `/register <mật khẩu> <mật khẩu>` | `/login <mật khẩu>` mỗi lần vào (phiên đăng nhập theo IP đang tắt, xem `tunnel/README.md`) |
| Bản quyền | Tự đăng ký và tự đăng nhập (`auto-register=true`) | Tự đăng nhập |
| Bedrock (Floodgate) | Không cần đăng nhập | Không cần đăng nhập |

Cấu hình: `librelogin/config.conf` (MariaDB database `librelogin`, limbo, lobby, UUID kiểu `MOJANG`).
Các chỗ đã đổi so với mặc định có ghi `DuckyMoMo:`.

Quyết định cần biết:
- `new-uuid-creator=MOJANG`: tên trùng tài khoản bản quyền dùng UUID thật của Mojang, còn lại dùng UUID kiểu offline.
  **Chỉ áp dụng cho người chơi mới, nên đừng đổi sau khi đã có người chơi.**
- `auto-register=true`: người chơi crack **không dùng được tên trùng tài khoản bản quyền**. Bảo vệ chủ tài khoản thật
  (kể cả tên admin), nhưng người chơi crack có tên phổ biến sẽ phải chọn tên khác.
- Admin: vào bằng tài khoản bản quyền là được bảo vệ sẵn. Nếu admin dùng crack thì đặt mật khẩu mạnh.

## ViaVersion + ViaBackwards: cho nhiều phiên bản Java vào chung
Server chạy bản Minecraft mới nhất. Không có hai plugin này thì người chơi Java phải dùng **đúng** bản đó.
- **ViaBackwards**: client **cũ hơn** server vẫn vào được (phần quan trọng, vì launcher crack hay để bản cũ).
- **ViaVersion**: cần cho ViaBackwards chạy; đồng thời cho client **mới hơn** server vào được khi Mojang vừa ra bản mới mà Paper chưa kịp cập nhật.
- Cài ở proxy nên có tác dụng cho mọi server phía sau (lobby, Bang Hội Chiến…). Không ảnh hưởng người chơi Bedrock (Geyser lo phần đó).
- Bản cũ nhất vào được: theo ViaBackwards hỗ trợ (thường về tới 1.10). Muốn hỗ trợ 1.8 thì thêm **ViaRewind** (SpigotMC 52109), **chưa thêm**.
- Người chơi bản cũ có thể thấy sai một số khối, vật phẩm mới (hiển thị bằng khối gần giống). Cần để ý trong trận SiegeWar có lỗi gì không.

## Việc cần kiểm tra ở lần chạy đầu
1. **Geyser**: mở `data/proxy/plugins/Geyser-Velocity/config.yml`, kiểm tra **auth-type** đang là `floodgate`
   (để người chơi Bedrock không cần tài khoản Java). Nếu chưa, sửa thành `floodgate` rồi `docker compose restart proxy`.
   Khi đã ổn, copy file đó vào `proxy/plugins/Geyser-Velocity/config.yml` để lưu vào Git.
2. **LibreLogin**:
   - Link tải mặc định (`LIBRELOGIN_URL` trong `.env`) là đoán theo tên file thường gặp, **chưa kiểm tra**.
     Lỗi thì xem tên file đúng trên https://github.com/kyngs/LibreLogin/releases rồi sửa `.env`.
   - Log proxy phải kết nối được MariaDB và **không** báo "A new configuration was generated".
     Nếu có, nghĩa là thư mục cấu hình không phải `plugins/librelogin/`: xem tên thư mục trong `data/proxy/plugins/`
     rồi đổi tên thư mục `proxy/plugins/librelogin/` cho khớp.
   - `config.conf` lấy theo wiki (revision 8). Bản mới hơn có thể tự thêm mục; so sánh với
     `data/proxy/plugins/librelogin/config.conf` rồi chép về repo nếu cần.
   - Thử: vào bằng Java crack (TLauncher…) phải bị giữ ở limbo đến khi `/register`; vào bằng tài khoản bản quyền
     phải sang thẳng lobby; vào bằng Bedrock phải sang thẳng lobby.
3. **ViaVersion, ViaBackwards**:
   - Log proxy phải có dòng nạp `ViaVersion` và `ViaBackwards`. Nếu báo không tải được qua Spiget (một số plugin trên SpigotMC
     chặn tải tự động), bỏ dòng `SPIGET_PLUGINS` và tải tay 2 file `.jar` bản **Velocity** từ https://hangar.papermc.io/ViaVersion
     và https://hangar.papermc.io/ViaVersion/ViaBackwards vào `proxy/plugins/`.
   - Thử vào bằng một bản Java cũ hơn (ví dụ 1.20.4) và một bản đúng phiên bản server, đi qua limbo, lobby, Bang Hội Chiến.
