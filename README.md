# DuckyMoMo Network

Network server Minecraft cho người chơi Việt Nam: **Java (PC) và Bedrock (điện thoại) chơi chung**,
vào **lobby** rồi chọn chế độ. Chạy toàn bộ bằng **Docker Compose**.

- Kế hoạch tổng quát: [`docs/minecraft-server-plan.md`](docs/minecraft-server-plan.md)
- Thiết kế kiến trúc: [`docs/network-architecture.md`](docs/network-architecture.md)
- **Bàn giao / tiếp tục ở session khác: [`docs/HANDOFF.md`](docs/HANDOFF.md)**

```
 Java (PC) ─┐                      ┌─► lobby       (Paper)
            ├─► proxy (Velocity) ──┼─► banghoi-1   (Paper)   Bang Hội Chiến
 Bedrock ───┘   + Geyser/Floodgate └─► pokemon-1   (Fabric)  Pokémon - chưa mở
                                         │
                          mariadb + redis (dùng chung, không mở ra ngoài)
```

## Yêu cầu
- Docker + Docker Compose v2.20 trở lên (cần tính năng `include`).
  Windows: Docker Desktop với WSL2, để thư mục dự án **bên trong WSL** cho nhanh.
- RAM: khoảng 8 GB trống cho proxy + lobby + Bang Hội Chiến (Pokémon cần thêm khoảng 10 GB).
- Bash để chạy script trong `ops/` (Linux, macOS, WSL hoặc Git Bash).

## Chạy lần đầu
```bash
./ops/init.sh                 # tạo .env với mật khẩu/secret ngẫu nhiên, tạo thư mục data/
./ops/build-core.sh           # build plugin DuckyMoMoCore (network-core) bằng Docker, copy vào lobby/plugins
docker compose up -d          # tải image, Paper, Velocity, plugin rồi khởi động
docker compose logs -f        # theo dõi log (Ctrl+C để thoát xem log, server vẫn chạy)
```
Vào game:
- **PC (Java)**: thêm server `localhost` (hoặc IP máy chạy Docker), cổng `25565`.
  Server chạy bản mới nhất, nhưng nhờ ViaVersion/ViaBackwards/ViaRewind, client **1.7 tới bản mới** đều vào được.
- **Điện thoại (Bedrock)**: thêm server IP máy chạy Docker, cổng `19132`.

Cho bạn bè ngoài mạng nhà vào: dùng **playit.gg**, không mở port trên modem.
1. Trên playit.gg tạo agent Docker, copy `SECRET_KEY` vào `PLAYIT_SECRET_KEY` trong `.env`.
2. `docker compose --profile tunnel up -d playit`
3. Trên dashboard playit tạo tunnel: Minecraft Java trỏ tới `127.0.0.1:25577`, Minecraft Bedrock trỏ tới `127.0.0.1:19132`
   (agent dùng chung mạng với proxy). Gửi bạn bè địa chỉ playit cấp cho mỗi tunnel.

## Checklist sau lần chạy đầu
- [ ] `docker compose ps`: tất cả service ở trạng thái `running` / `healthy`.
- [ ] Vào được lobby bằng Java. Server chạy **offline mode**: lần đầu gõ `/register <matkhau> <matkhau>`, các lần sau `/login <matkhau>`.
      Có bản quyền thì gõ thêm `/premium` để lần sau vào thẳng. **Đăng ký tên admin của mình trước tiên.**
- [ ] Gõ `/server banghoi-1` để sang Bang Hội Chiến (sau này sẽ có menu ở lobby). Chưa đăng nhập thì phải bị chặn.
- [ ] Kiểm tra `data/proxy/plugins/Geyser-Velocity/config.yml` có **auth-type: floodgate** (xem `proxy/plugins/README.md`).
- [ ] Vào được bằng Bedrock. Tên người chơi Bedrock có tiền tố của Floodgate (ví dụ `.TenNguoiChoi`).
- [ ] Cấp quyền admin cho mình: `docker compose exec lobby rcon-cli lp user <TenBan> permission set "*" true`
      (LuckPerms dùng chung MariaDB nên quyền có hiệu lực ở mọi server Paper).
- [ ] Ghim phiên bản: đổi `MC_VERSION=LATEST` trong `.env` thành phiên bản đang chạy.

## Lệnh thường dùng
```bash
docker compose ps                          # trạng thái
docker compose logs -f banghoi-1           # log của một service
docker compose restart proxy               # khởi động lại một service
docker compose exec lobby rcon-cli         # console của server (gõ lệnh như admin)
# Xem log/chat của mọi server qua trình duyệt: http://localhost:8888 (Dozzle, chỉ mở trên máy nhà)
# Trang Cài đặt (bấm Lưu là áp dụng): http://localhost:8889, đăng nhập bằng PANEL_USER/PANEL_PASSWORD trong .env
docker compose pull && docker compose up -d   # cập nhật image
docker compose down                        # tắt tất cả (dữ liệu trong data/ vẫn giữ)
docker compose --profile pokemon up -d     # bật thêm chế độ Pokémon
```

## Sao lưu và khôi phục
Tự động chạy cùng `docker compose up -d`, mặc định **6 giờ một lần, giữ 7 ngày**. Chỉnh trong `.env`:
`BACKUP_INTERVAL`, `BACKUP_KEEP_DAYS`, `BACKUP_DIR` (bỏ trống = `backups/` trong repo; muốn sang ổ F: `BACKUP_DIR=/mnt/f/DuckyMoMo-backups`).

| Thư mục | Nội dung | Service |
|---|---|---|
| `backups/lobby/`, `backups/banghoi-1/` | Thế giới + cấu hình plugin (`.tar.gz`, không kèm file `.jar`) | `backup-lobby`, `backup-banghoi-1` |
| `backups/db/` | Toàn bộ MariaDB: tài khoản AuthMe, quyền LuckPerms, dữ liệu network (`.sql.gz`) | `backup-db` |

Server không có ai vào kể từ lần sao lưu trước thì bỏ qua lần đó (đỡ tốn ổ).

```bash
docker compose exec backup-banghoi-1 backup now      # sao lưu ngay một server
```

**Khôi phục thế giới** (ví dụ banghoi-1):
```bash
docker compose stop banghoi-1
mv data/banghoi-1 data/banghoi-1.truoc-khi-khoi-phuc       # giữ lại bản hiện tại phòng khi cần
mkdir data/banghoi-1
tar -xzf backups/banghoi-1/banghoi-1-YYYYMMDD-HHMMSS.tar.gz -C data/banghoi-1
docker compose up -d banghoi-1                              # plugin (.jar) tự tải lại khi khởi động
```

**Khôi phục database** (ghi đè dữ liệu hiện tại, nên tắt các server trước):
```bash
docker compose stop lobby banghoi-1 proxy
zcat backups/db/db-YYYYMMDD-HHMMSS.sql.gz | docker compose exec -T mariadb sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"'
docker compose up -d
```

## Cấu trúc thư mục
```
compose.yaml           File gốc: danh sách include
.env.example           Mẫu biến môi trường (.env thật không commit)
infra/                 MariaDB + Redis; db-init/ tạo database và bảng lần đầu
proxy/                 Velocity + Geyser + Floodgate; config/velocity.toml
lobby/                 Lobby (Paper)
modes/
  _template-paper/     Khuôn mẫu chế độ Paper mới
  banghoi/             Bang Hội Chiến: compose.yaml, mode.yml, server/{config,plugins}
  pokemon/             Pokémon (Fabric + Cobblemon), mặc định không chạy
ops/                   init.sh, new-mode.sh
docs/                  Kế hoạch và thiết kế
data/                  Dữ liệu chạy (KHÔNG commit, cần sao lưu)
```

### Cấu hình và mật khẩu
- File trong `*/config/` và `*/plugins/` được copy vào container khi khởi động.
  Các chỗ ghi `${CFG_...}` sẽ được điền từ biến `CFG_...` trong `compose.yaml`, mà biến đó lấy từ `.env`.
  Nhờ vậy **mật khẩu và secret không nằm trong Git**.
- Chỉ ghi những mục cần đổi; plugin tự điền phần còn lại.
- Sửa cấu hình xong: `docker compose restart <service>`. Nếu file trong `data/` mới hơn bản trong Git
  thì sẽ không bị ghi đè; khi cần, sửa thẳng trong `data/` rồi chép ngược về repo.

## Thêm chế độ mới
```bash
./ops/new-mode.sh survival
```
Script copy khuôn mẫu sang `modes/survival/`, thêm vào `compose.yaml` và in ra các bước còn lại
(khai báo server trong `velocity.toml`, tạo database `mode_survival`, chạy thử bằng profile).
Chi tiết: `docs/network-architecture.md`, mục 7.

## Trạng thái
| Thành phần | Trạng thái |
|---|---|
| Docker Compose (cú pháp, include, profile) | ✅ Đã kiểm tra bằng `docker compose config` |
| MariaDB: tạo database, bảng, quyền `mode_%` | ✅ Đã chạy thử |
| Redis có mật khẩu | ✅ Đã chạy thử |
| Điền secret/mật khẩu vào cấu hình (`CFG_*`) | ✅ Đã chạy thử với công cụ của image |
| `ops/init.sh`, `ops/new-mode.sh` | ✅ Đã chạy thử |
| Proxy, lobby, Bang Hội Chiến khởi động | ✅ Chạy trên máy nhà (WSL, 07/10/2026): Paper 26.2, Velocity 4.2.0, Geyser 2.11.3, cả 5 service `healthy`, ping được cổng 25565 và 19132 |
| Vào game bằng Java / Bedrock | ⏳ Cần người thật vào thử |
| Geyser auth-type floodgate | ✅ Đã đặt trong `proxy/plugins/Geyser-Velocity/config.yml` |
| Pokémon (Fabric) | ⏳ Chưa mở; LuckPerms Fabric chưa cấu hình MariaDB |
| network-core (menu lobby tự sinh, đăng ký server động, giao hàng web store) | 🔜 Bước tiếp theo |
| Sao lưu tự động (`itzg/mc-backup` + `mariadb-dump`) | ✅ Đã chạy thử, xem mục "Sao lưu và khôi phục" |
