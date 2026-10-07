# DuckyMoMo Network

Network server Minecraft cho người chơi Việt Nam: **Java (PC) và Bedrock (điện thoại) chơi chung**,
**cho cả người chơi crack** (đăng nhập bằng mật khẩu qua LibreLogin), vào **lobby** rồi chọn chế độ.
Chạy toàn bộ bằng **Docker Compose**.

- Kế hoạch tổng quát: [`docs/minecraft-server-plan.md`](docs/minecraft-server-plan.md)
- Thiết kế kiến trúc: [`docs/network-architecture.md`](docs/network-architecture.md)
- **Bàn giao / tiếp tục ở session khác: [`docs/HANDOFF.md`](docs/HANDOFF.md)**

```
 Java (PC) ─┐                      ┌─► limbo       (NanoLimbo) chờ /login, /register (chỉ người chơi crack)
            ├─► proxy (Velocity) ──┼─► lobby       (Paper)
 Bedrock ───┘   + Geyser/Floodgate ├─► banghoi-1   (Paper)   Bang Hội Chiến (Towny + SiegeWar)
                + LibreLogin       └─► pokemon-1   (Fabric)  Pokémon - chưa mở
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
docker compose up -d          # tải image, Paper, Velocity, plugin rồi khởi động
docker compose logs -f        # theo dõi log (Ctrl+C để thoát xem log, server vẫn chạy)
```
Vào game:
- **PC (Java)**: thêm server `localhost` (hoặc IP máy chạy Docker), cổng `25565`.
- **Điện thoại (Bedrock)**: thêm server IP máy chạy Docker, cổng `19132`.

Cho bạn bè ngoài mạng nhà vào: dùng **playit.gg** (`docker compose --profile tunnel up -d`, hướng dẫn trong `tunnel/README.md`), không mở port trên modem.

Sao lưu chạy tự động ngay khi `up` (thế giới mỗi giờ, database mỗi 6 giờ, vào thư mục `backups/`). Xem `docs/backup.md`.
**Chép `RESTIC_PASSWORD` trong `.env` ra chỗ an toàn**, mất nó thì không khôi phục được.

## Checklist sau lần chạy đầu
- [ ] `docker compose ps`: tất cả service ở trạng thái `running` / `healthy`.
- [ ] Vào được lobby bằng Java. Gõ `/server banghoi-1` để sang Bang Hội Chiến (sau này sẽ có menu ở lobby).
- [ ] Kiểm tra `data/proxy/plugins/Geyser-Velocity/config.yml` có **auth-type: floodgate** (xem `proxy/plugins/README.md`).
- [ ] `docker compose logs limbo`: NanoLimbo tải và chạy được (xem `limbo/README.md`).
- [ ] LibreLogin: vào bằng Java crack phải bị giữ ở limbo đến khi `/register`; tài khoản bản quyền và Bedrock
      phải vào thẳng lobby (xem `proxy/plugins/README.md`).
- [ ] Vào được bằng Java bản cũ hơn server (ví dụ 1.20.4) nhờ ViaVersion + ViaBackwards.
- [ ] Vào được bằng Bedrock. Tên người chơi Bedrock có tiền tố của Floodgate (ví dụ `.TenNguoiChoi`).
- [ ] Cấp quyền admin cho mình: `docker compose exec lobby rcon-cli lp user <TenBan> permission set "*" true`
      (LuckPerms dùng chung MariaDB nên quyền có hiệu lực ở mọi server Paper).
- [ ] Ghim phiên bản: đổi `MC_VERSION=LATEST` trong `.env` thành phiên bản đang chạy.
- [ ] Sao lưu: `docker compose logs backup-banghoi-1 db-backup` không báo lỗi; `ls backups/mariadb` có file. Tập khôi phục một lần theo `docs/backup.md`.
- [ ] Tunnel (khi cần): bạn bè vào được qua địa chỉ playit, cả Java lẫn Bedrock (xem `tunnel/README.md`).

## Lệnh thường dùng
```bash
docker compose ps                          # trạng thái
docker compose logs -f banghoi-1           # log của một service
docker compose restart proxy               # khởi động lại một service
docker compose exec lobby rcon-cli         # console của server (gõ lệnh như admin)
docker compose pull && docker compose up -d   # cập nhật image
docker compose down                        # tắt tất cả (dữ liệu trong data/ vẫn giữ)
docker compose --profile pokemon up -d     # bật thêm chế độ Pokémon
docker compose --profile tunnel up -d      # bật tunnel playit.gg
docker compose exec backup-banghoi-1 backup now   # sao lưu ngay
```

## Cấu trúc thư mục
```
compose.yaml           File gốc: danh sách include
.env.example           Mẫu biến môi trường (.env thật không commit)
infra/                 MariaDB + Redis + db-backup; db-init/ tạo database và bảng lần đầu
proxy/                 Velocity + Geyser + Floodgate + LibreLogin; config/velocity.toml, plugins/librelogin/config.conf
limbo/                 Phòng chờ đăng nhập (NanoLimbo) cho người chơi crack
lobby/                 Lobby (Paper)
modes/
  _template-paper/     Khuôn mẫu chế độ Paper mới
  banghoi/             Bang Hội Chiến: compose.yaml, mode.yml, server/{config,plugins}
  pokemon/             Pokémon (Fabric + Cobblemon), mặc định không chạy
tunnel/                Agent playit.gg (profile tunnel)
ops/                   init.sh, new-mode.sh
docs/                  Kế hoạch và thiết kế
data/                  Dữ liệu chạy (KHÔNG commit)
backups/               Bản sao lưu restic + dump MariaDB (KHÔNG commit, nên chép ra ổ khác)
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
| Proxy, lobby, Bang Hội Chiến khởi động và vào game | ⏳ **Chưa chạy thử được**: môi trường dựng khung chặn tải Paper/Velocity/plugin. Cần chạy lần đầu trên máy nhà theo checklist ở trên |
| Geyser auth-type floodgate | ⏳ Kiểm tra ở lần chạy đầu |
| ViaVersion + ViaBackwards trên proxy (nhiều phiên bản Java) | ⏳ Đã thêm, chưa chạy thử (tải qua Spiget chưa kiểm tra) |
| Cho crack vào: LibreLogin + limbo (NanoLimbo) | ⏳ Đã viết cấu hình theo wiki, **chưa chạy thử** (link tải, tên thư mục cấu hình) |
| Plugin Bang Hội Chiến (Towny + SiegeWar…) | ⏳ Chưa cài, xem `modes/banghoi/README.md` |
| Pokémon (Fabric) | ⏳ Chưa mở; LuckPerms Fabric chưa cấu hình MariaDB |
| network-core (menu lobby tự sinh, đăng ký server động, giao hàng web store) | 🔜 Bước tiếp theo |
| Sao lưu: dump và khôi phục MariaDB | ✅ Đã chạy thử |
| Sao lưu: lệnh restic sao lưu và khôi phục (image `itzg/mc-backup`) | ✅ Đã chạy thử với thế giới giả; ⏳ sao lưu tự động qua RCON chưa thử với server thật |
| Tunnel playit.gg | ⏳ Chưa thử (môi trường dựng khung không vào được playit.gg) |
