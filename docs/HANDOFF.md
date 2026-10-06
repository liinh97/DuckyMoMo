# Bàn giao dự án DuckyMoMo Network

> Dùng file này để tiếp tục ở session hoặc Project Claude khác. Cập nhật: 06/10/2026.
> Đọc kèm: `README.md`, `docs/minecraft-server-plan.md` (kế hoạch, quyết định), `docs/network-architecture.md` (kiến trúc).

---

## 1. Người làm dự án và cách làm việc

- Chủ quán đồ ăn vặt (nem chua rán, bánh rán…), đồng thời là **dev**, đang tự làm app quản lý bán hàng (đơn hàng, sản phẩm, khách hàng, báo cáo, thuế, kết nối bên thứ 3, đa cửa hàng).
- Từng chơi Minecraft khoảng 10 năm trước, thích **Factions**, sinh tồn, xây dựng, cày tiến độ, nhập vai. Có **hơn 15 giờ mỗi tuần** cho dự án. Chưa tính đến làm content.
- **Trả lời bằng tiếng Việt**, thực tế, nói rõ chỗ nào chưa chắc hoặc cần kiểm tra lại. Không tự chốt thay người dùng những thứ chưa được chốt.
- Người dùng muốn **build từng phần một**, chưa cần tối ưu cho đông người. Giai đoạn đầu **chạy trên máy nhà**.
- **Bắt buộc dùng Docker Compose** (không dùng Pterodactyl).

## 2. Repo

- GitHub: `liinh97/DuckyMoMo`, nhánh **`claude/confident-thompson-q939lp`**. Repo trước đó trống, nên nhánh này hiện là nhánh mặc định. Người dùng có thể muốn đổi sang `main` (chưa quyết).
- Claude GitHub App đã được cấp quyền cho repo này.

## 3. Các quyết định đã chốt (tóm tắt)

1. Server **Java (Paper)**, cho **Bedrock (điện thoại)** vào qua **Geyser + Floodgate**.
2. **Network nhiều chế độ** (lobby rồi chọn chế độ). **Build từng chế độ một**, dựng khung network ngay từ đầu.
3. **Chế độ đầu tiên: Bang Hội Chiến**, tức Factions kiểu mới. Chiến tranh theo lịch, chạy theo mùa, combat kiểu 1.8, không cướp lúc người chơi offline. *Luật chơi chi tiết chưa thiết kế.*
4. **Pokémon (Cobblemon, Fabric)** sẽ thêm sau. Chỉ người chơi Java có modpack vào được. **Không bán gì liên quan Pokémon.**
5. **Kiếm tiền đúng luật Mojang**: chỉ đồ trang trí và tiện ích nhỏ, không pay-to-win, **không bán cape**.
6. Chạy máy nhà trước, cho bạn bè vào qua **playit.gg**. Thuê máy chủ khi mở công khai.
7. **Docker Compose**: mỗi thành phần và mỗi chế độ một file `compose.yaml`, file gốc gom lại bằng `include`.

## 4. Trạng thái hiện tại

### Đã có trong repo
| Thành phần | File chính |
|---|---|
| File gốc | `compose.yaml` (danh sách include), `.env.example`, `.gitignore`, `.gitattributes` |
| MariaDB + Redis | `infra/compose.yaml`, `infra/db-init/01-databases.sh`, `02-network-schema.sql`, `03-mode-databases.sql` |
| Proxy | `proxy/compose.yaml`, `proxy/config/velocity.toml`, `proxy/config/forwarding.secret` (mẫu chứa biến) |
| Lobby (Paper) | `lobby/compose.yaml`, `lobby/config/paper-global.yml`, `lobby/plugins/LuckPerms/config.yml` |
| Bang Hội Chiến (Paper) | `modes/banghoi/` (compose, mode.yml, README, server/config, server/plugins) |
| Pokémon (Fabric, profile `pokemon`) | `modes/pokemon/` (compose, mode.yml, README, FabricProxy-Lite.toml) |
| Khuôn mẫu chế độ Paper | `modes/_template-paper/` (dùng placeholder `__MODE_ID__`) |
| Script | `ops/init.sh` (tạo `.env` với mật khẩu ngẫu nhiên), `ops/new-mode.sh <id>` |

### Đã kiểm chứng (chạy thật trong session trước)
- `docker compose config`: hợp lệ cả mặc định lẫn `--profile pokemon`.
- MariaDB tạo đúng database `network`, `luckperms`, `mode_banghoi`, `mode_pokemon`, các bảng `players`, `modes`, `servers` và dữ liệu khởi tạo. User có quyền tạo database `mode_%`.
- Redis bắt buộc mật khẩu, healthcheck chạy.
- Điền biến `CFG_*` vào `forwarding.secret`, `paper-global.yml`, LuckPerms `config.yml` (thử bằng `mc-image-helper` trong image).
- `ops/init.sh`, `ops/new-mode.sh` (kể cả kiểm tra id sai).

### Chưa kiểm chứng
Môi trường cloud chặn các host: `api.papermc.io`, `fill.papermc.io`, `download.geysermc.org`, `api.modrinth.com`, `piston-meta.mojang.com`. Vì vậy chưa kiểm tra được:
- Proxy, lobby, Bang Hội Chiến **khởi động hoàn chỉnh và vào được game** (Java và Bedrock).
- Geyser có dùng **`auth-type: floodgate`** không (xem `proxy/plugins/README.md`).
- Velocity có nhận hết các key trong `velocity.toml` không (viết theo mẫu `config-version = "2.7"`).
- Slug Modrinth `luckperms`, `worldedit`, `chunky` (Paper) và `fabric-api`, `cobblemon`, `fabricproxy-lite`, `luckperms` (Fabric) có tải được không.
- Các key trong `FabricProxy-Lite.toml`.

👉 **Việc đầu tiên của session mới**: hỏi người dùng đã chạy thử trên máy nhà chưa. Nếu rồi, xin log `docker compose logs <service>` để sửa.

## 5. Chi tiết kỹ thuật cần nhớ

### Image `itzg`
- `itzg/minecraft-server` (bản `latest` hiện dùng **Java 25**) và `itzg/mc-proxy`.
- **Đồng bộ cấu hình khi khởi động**:
  - minecraft-server: `/config` → `/data/config`, `/plugins` → `/data/plugins`.
  - mc-proxy: `/config` → `/server`, `/plugins` → `/server/plugins`.
- **Điền biến**: chuỗi `${CFG_TEN_BIEN}` trong các file đồng bộ được thay bằng biến môi trường `CFG_TEN_BIEN` (mặc định `REPLACE_ENV_DURING_SYNC=true`, tiền tố `CFG_`). Chỉ áp dụng cho các đuôi trong `REPLACE_ENV_SUFFIXES`. Proxy đã thêm đuôi `secret` để điền được `forwarding.secret`.
- Khi đồng bộ, **file trong `data/` mới hơn sẽ không bị ghi đè**.
- **Secret của Velocity**: mc-proxy chỉ tự sinh `/server/forwarding.secret` khi file chưa có. Ở đây file được tạo từ mẫu `proxy/config/forwarding.secret`, nên dùng đúng `VELOCITY_SECRET` trong `.env`. Paper nhận secret qua `paper-global.yml`, Fabric qua `FabricProxy-Lite.toml`.
- Trên mc-proxy, `MODRINTH_PROJECTS` **bắt buộc có `MINECRAFT_VERSION` cụ thể**. Vì vậy Geyser và Floodgate được tải qua `PLUGINS` bằng URL chính thức của download.geysermc.org.
- Trên minecraft-server, `MODRINTH_PROJECTS` dùng được với `VERSION=LATEST`.
- Tải tay plugin: đặt file `.jar` vào thư mục `plugins` của service (`.jar` đã nằm trong `.gitignore`).

### Network
- **Chỉ proxy mở cổng**: 25565/tcp sang 25577 trong container, 19132/udp cho Geyser. Các service khác gọi nhau bằng tên service.
- Mục `[servers]` trong `velocity.toml` **chưa khai báo `pokemon-1`**, vì service không chạy thì tên không phân giải được. Bật Pokémon thì bỏ comment dòng đó.
- **Floodgate hiện chỉ cài trên proxy.** Muốn dùng **Bedrock Forms** (menu cho điện thoại) trên lobby hoặc server con thì phải cài Floodgate trên các server đó, dùng **chung `key.pem`** với proxy. Đây là việc cần làm khi viết menu lobby.
- **LuckPerms**:
  - Paper: thư mục `plugins/LuckPerms/`, cấu hình dạng YAML rút gọn (`server: ${CFG_SERVER_NAME}`, MariaDB, Redis messaging).
  - Fabric: `config/luckperms/luckperms.conf` (HOCON), **chưa cấu hình**.
  - Proxy: **chưa cài** LuckPerms-Velocity, sẽ thêm cùng network-core.
- Lệnh `include` của Compose: đường dẫn tương đối tính theo thư mục của từng file compose con.

### Database
- `network`: dữ liệu dùng chung. `luckperms`: dữ liệu LuckPerms. `mode_<id>`: dữ liệu riêng từng chế độ.
- Script trong `infra/db-init/` **chỉ chạy một lần khi `data/mariadb` còn trống**. Thay đổi schema về sau sẽ do network-core quản lý bằng migration (dự kiến Flyway).

### Chạy Docker trong session cloud của Claude Code
- Docker daemon không tự chạy, phải bật bằng `dockerd > <scratchpad>/dockerd.log 2>&1 &`.
- Docker Hub đôi khi trả **429** (giới hạn lượt tải), thử lại sau.
- Container không tải được Paper hay plugin vì các host bị chặn (mục 4). Muốn chạy thử trọn vẹn thì người dùng phải thêm các host đó vào Network access của môi trường.

## 6. Việc tiếp theo (theo thứ tự đề xuất)

1. **Chạy lần đầu trên máy nhà** theo checklist trong `README.md`, sửa lỗi từ log. Sau đó ghim `MC_VERSION` và tag của image.
2. **Hoàn thiện plugin cho Bang Hội Chiến**: Towny + SiegeWar (hoặc Factions kèm phần tự viết), CoreProtect (bắt buộc), WorldGuard, EssentialsX, OldCombatMechanics. Kiểm tra slug trên Modrinth, plugin nào không có thì tải `.jar` bằng tay. Danh sách nằm trong `modes/banghoi/README.md`.
3. **Thiết kế luật chơi Bang Hội Chiến**: lập bang, chiếm đất, lịch công thành, phần thưởng mùa, chống lạm dụng, cái gì bán được mà không vi phạm luật. Viết thành `docs/banghoi-design.md`.
4. **Sao lưu**: thêm `itzg/mc-backup` (restic) cho các server có thế giới, và `mariadb-dump` định kỳ.
5. **Tunnel playit.gg**: thêm service agent với profile `tunnel`, trỏ tới `proxy:25577` và `proxy:19132/udp`. Kiểm tra image chính thức của playit trước.
6. **network-core phiên bản 1** (Gradle nhiều module, Java hoặc Kotlin): `core-api`, `core-common`, `core-velocity`, `core-paper`.
   - Đồng bộ `modes/*/mode.yml` vào bảng `network.modes`.
   - Velocity **đăng ký server động** từ bảng `network.servers`.
   - Ghi hồ sơ người chơi vào `network.players` (phân biệt Java, Bedrock, Java có modpack).
   - **Menu lobby tự sinh** từ danh sách chế độ: menu dạng rương cho Java, Bedrock Forms cho điện thoại (cần Floodgate trên lobby và chung `key.pem`).
   - Chế độ beta chỉ hiện với người có quyền `network.beta`; chế độ maintenance hiện nhưng không cho vào.
7. Về sau:
   - Web store (VietQR, thẻ cào qua DotMan) với bảng `network.deliveries`, giao hàng khi người chơi online, không dùng RCON trực tiếp.
   - Đồ trang trí dùng chung toàn network.
   - Chế độ Pokémon: viết `core-fabric`, tạo `_template-fabric`, làm modpack, thử chuyển server từ lobby Paper sang Fabric.

## 7. Câu hỏi còn mở
- Đổi nhánh mặc định sang `main`?
- Bang Hội Chiến dùng **Towny + SiegeWar** hay **Factions kèm phần tự viết**?
- Có cho người chơi crack (offline mode) vào không? Hiện proxy để `online-mode = true`, tức chỉ nhận tài khoản bản quyền và Bedrock.
- Tên server, thương hiệu, tên miền.
- Máy nhà chạy Windows hay Linux, cấu hình bao nhiêu?

## 8. Prompt gợi ý để mở session mới

> Tôi đang làm dự án DuckyMoMo Network (server Minecraft, Docker Compose) trong repo `liinh97/DuckyMoMo`, nhánh `claude/confident-thompson-q939lp`. Hãy đọc `docs/HANDOFF.md`, `README.md` và các file trong `docs/` trước, rồi tiếp tục từ mục "Việc tiếp theo". Trả lời bằng tiếng Việt, nói rõ khi thông tin cần kiểm tra lại.
