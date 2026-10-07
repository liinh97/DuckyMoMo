# Bàn giao dự án DuckyMoMo Network

> Dùng file này để tiếp tục ở session hoặc Project Claude khác. Cập nhật: 07/10/2026.
> Đọc kèm: `README.md` (cách chạy, checklist), `docs/banghoi-design.md` (luật Bang Hội Chiến), `docs/backup.md`,
> `docs/minecraft-server-plan.md` (kế hoạch tổng), `docs/network-architecture.md` (kiến trúc).

---

## 0. Bắt đầu session mới ở đâu

- **Nhánh làm việc mới nhất: `claude/exciting-cray-lh5jgq`** (chưa gộp vào nhánh mặc định `claude/confident-thompson-q939lp`).
  Session mới nên tiếp tục trên nhánh này, hoặc gộp nó vào nhánh mặc định trước (người dùng chưa yêu cầu tạo PR).
- **Người dùng chưa chạy server trên máy nhà lần nào.** Mọi thứ về Minecraft (Paper, Velocity, plugin) chưa ai vào game thử.
- 👉 **Việc đầu tiên**: hỏi người dùng đã chạy theo mục 6 chưa, máy **Windows hay Linux**, rồi xin
  `docker compose ps` và `docker compose logs --tail 200 <service>` của phần lỗi để sửa.

## 1. Người làm dự án và cách làm việc

- Chủ quán đồ ăn vặt (nem chua rán, bánh rán…), đồng thời là **dev**, đang tự làm app quản lý bán hàng (đơn hàng, sản phẩm, khách hàng, báo cáo, thuế, kết nối bên thứ 3, đa cửa hàng).
- Từng chơi Minecraft khoảng 10 năm trước, thích **Factions**, sinh tồn, xây dựng, cày tiến độ, nhập vai. Có **hơn 15 giờ mỗi tuần**. Chưa tính đến làm content.
- **Trả lời bằng tiếng Việt**, thực tế, ngắn gọn, nói rõ chỗ nào chưa chắc hoặc cần kiểm tra lại. **Không tự chốt thay người dùng.**
  Khi người dùng trả lời mơ hồ (ví dụ "Đúng" cho câu hỏi chọn A hay B) thì hỏi lại.
- Người dùng muốn **build từng phần một**, chưa cần tối ưu cho đông người. Giai đoạn đầu **chạy trên máy nhà**.
- **Bắt buộc dùng Docker Compose** (không dùng Pterodactyl). Không commit mật khẩu.

## 2. Repo

- GitHub: `liinh97/DuckyMoMo`. Nhánh mặc định **`claude/confident-thompson-q939lp`**. **Không đổi sang `main`** (người dùng chốt).
  Có một nhánh `main` bị tạo nhầm (trùng commit `efa0f7e`, không có gì mới). Claude không xoá được (bị chặn quyền); người dùng tự xoá trên GitHub nếu muốn.
- Mỗi session làm trên một nhánh `claude/...` riêng.
- Kiểm tra trước khi push (theo `CLAUDE.md`): `./ops/init.sh && docker compose config --quiet && docker compose --profile pokemon config --quiet`, rồi xoá `.env`, `data/`, `backups/` vừa tạo.

## 3. Các quyết định đã chốt

| # | Quyết định |
|---|---|
| 1 | Server **Java (Paper)**, điện thoại (**Bedrock**) vào qua **Geyser + Floodgate** (đăng nhập Xbox, không cần tài khoản Java, không cần mật khẩu) |
| 2 | **Network nhiều chế độ**: lobby rồi chọn chế độ. Build từng chế độ một |
| 3 | **Chế độ đầu tiên: Bang Hội Chiến** trên nền **Towny + SiegeWar**. Luật: `docs/banghoi-design.md` |
| 4 | **Pokémon (Cobblemon, Fabric)** làm sau. **Không bán gì liên quan Pokémon** |
| 5 | **Kiếm tiền đúng luật Mojang**: chỉ đồ trang trí, không pay-to-win, **không bán cape**. **Bán cả cho người chơi crack** |
| 6 | **Cho người chơi crack vào**: proxy `online-mode = false`, đăng nhập bằng **LibreLogin**, chờ ở **limbo (NanoLimbo)** |
| 7 | **ViaVersion + ViaBackwards** trên proxy: Java bản cũ hơn server vẫn vào được |
| 8 | Chạy máy nhà trước, bạn bè vào qua **playit.gg**. Thuê máy chủ khi mở công khai |
| 9 | **Tên: DuckyMoMo.** Tên miền chưa mua |
| 10 | Bang Hội Chiến: PvP hoang dã **bật**, Nether mở ngay, **End mở bằng sự kiện**, **mùa thử 4 tuần** rồi **mùa chính 8 tuần**; giờ trận và cấm vật phẩm (totem, netherite…) **quyết sau mùa thử** |

## 4. Trạng thái

### Thành phần trong repo
| Thành phần | File chính |
|---|---|
| File gốc | `compose.yaml` (include), `.env.example`, `.gitignore`, `.gitattributes` |
| MariaDB + Redis + sao lưu DB | `infra/compose.yaml`, `infra/db-init/*`, `infra/backup-db.sh` |
| Proxy (Velocity + Geyser + Floodgate + LibreLogin + ViaVersion + ViaBackwards) | `proxy/compose.yaml`, `proxy/config/velocity.toml`, `proxy/config/forwarding.secret`, `proxy/plugins/librelogin/config.conf`, `proxy/plugins/README.md` |
| Limbo (NanoLimbo, chờ đăng nhập) | `limbo/compose.yaml`, `limbo/entrypoint.sh`, `limbo/settings.yml`, `limbo/README.md` |
| Lobby (Paper) + sao lưu | `lobby/compose.yaml`, `lobby/config/`, `lobby/plugins/LuckPerms/` |
| Bang Hội Chiến (Paper) + sao lưu | `modes/banghoi/` (compose, mode.yml, README có danh sách plugin) |
| Pokémon (Fabric, profile `pokemon`) + sao lưu | `modes/pokemon/` |
| Khuôn mẫu chế độ Paper (có sẵn sao lưu) | `modes/_template-paper/` (placeholder `__MODE_ID__`) |
| Tunnel playit.gg (profile `tunnel`) | `tunnel/compose.yaml`, `tunnel/README.md` |
| Script | `ops/init.sh` (tạo `.env`, mật khẩu ngẫu nhiên kể cả `RESTIC_PASSWORD`), `ops/new-mode.sh <id>` |
| Tài liệu | `README.md`, `docs/banghoi-design.md`, `docs/backup.md`, `docs/minecraft-server-plan.md`, `docs/network-architecture.md` |

### Đã chạy thử thật (trong môi trường cloud)
- `docker compose config` hợp lệ: mặc định, `--profile pokemon`, `--profile tunnel`.
- MariaDB tạo đúng database `network`, `luckperms`, `librelogin`, `mode_banghoi`, `mode_pokemon`, bảng `players` (có cột `auth`), `modes`, `servers`.
- Redis có mật khẩu, healthcheck chạy.
- Điền biến `CFG_*` vào file cấu hình (bằng công cụ của image itzg).
- Script limbo: điền đúng secret vào `settings.yml`; báo lỗi rõ khi không tải được jar.
- `db-backup`: dump MariaDB, **xoá một database rồi khôi phục từ dump thành công**.
- Lệnh restic trong `docs/backup.md` (init, backup, snapshots, restore) bằng image `itzg/mc-backup`: chạy đúng với thế giới giả.
- `ops/init.sh`, `ops/new-mode.sh` (chế độ mới có sẵn service sao lưu cùng profile).

### Chưa kiểm chứng (môi trường cloud chặn papermc, geysermc, modrinth, mojang, hangar, spigot, github releases, playit)
- **Khởi động hoàn chỉnh và vào game**: proxy, limbo, lobby, Bang Hội Chiến; cả Java (bản quyền, crack, bản cũ) lẫn Bedrock.
- Geyser có `auth-type: floodgate` chưa (xem `proxy/plugins/README.md`).
- Velocity nhận đủ key trong `velocity.toml` không (viết theo `config-version = "2.7"`).
- **Link tải đoán theo tên file**: `LIBRELOGIN_URL`, `NANOLIMBO_URL` (trong `.env`). Tên thư mục cấu hình `plugins/librelogin/` chưa chắc đúng.
- `limbo/settings.yml` lấy từ nhánh `main` của NanoLimbo, có thể mới hơn bản release.
- `config.conf` của LibreLogin lấy theo wiki (revision 8).
- **ViaVersion/ViaBackwards qua Spiget** (`SPIGET_PLUGINS: "19254,27448"`): SpigotMC có thể chặn tải tự động; dự phòng là tải tay bản Velocity từ Hangar.
- Slug Modrinth: `luckperms`, `worldedit`, `chunky` (Paper); `fabric-api`, `cobblemon`, `fabricproxy-lite`, `luckperms` (Fabric). Towny, SiegeWar và các plugin Bang Hội Chiến khác **chưa thêm**.
- Sao lưu tự động qua RCON với server thật (`backup-<server>`).
- Tunnel playit: trang playit có nhận địa chỉ local `proxy` không; UDP cho Bedrock có miễn phí không; PROXY protocol.
- SiegeWar có dùng đúng múi giờ `Asia/Ho_Chi_Minh` không; lệnh bật PvP hoang dã của Towny (`/tw toggle pvp`).

## 5. Chi tiết kỹ thuật cần nhớ

### Image `itzg`
- `itzg/minecraft-server` (bản `latest` dùng **Java 25**) và `itzg/mc-proxy`.
- Đồng bộ cấu hình khi khởi động: minecraft-server `/config` → `/data/config`, `/plugins` → `/data/plugins`; mc-proxy `/config` → `/server`, `/plugins` → `/server/plugins`.
- `${CFG_TEN_BIEN}` trong file đồng bộ được thay bằng biến môi trường `CFG_TEN_BIEN`, chỉ với đuôi trong `REPLACE_ENV_SUFFIXES` (proxy đã thêm `secret`; `conf` có sẵn nên `librelogin/config.conf` cũng được điền).
- **File trong `data/` mới hơn sẽ không bị ghi đè** khi đồng bộ. Sửa cấu hình sau lần chạy đầu thì sửa trong `data/` rồi chép ngược về repo.
- Secret Velocity: tạo từ mẫu `proxy/config/forwarding.secret` = `VELOCITY_SECRET`. Paper nhận qua `paper-global.yml`, Fabric qua `FabricProxy-Lite.toml`, NanoLimbo qua `limbo/entrypoint.sh`.
- mc-proxy: `MODRINTH_PROJECTS` **bắt buộc `MINECRAFT_VERSION` cụ thể**, nên dùng `PLUGINS` (URL) cho Geyser, Floodgate, LibreLogin và `SPIGET_PLUGINS` cho Via.
- minecraft-server: `MODRINTH_PROJECTS` dùng được với `VERSION=LATEST`.
- Tải tay plugin: đặt `.jar` vào thư mục `plugins` của service (`.jar` có trong `.gitignore`).

### Network và đăng nhập
- **Chỉ proxy mở cổng**: 25565/tcp → 25577 trong container, 19132/udp cho Geyser. Service khác gọi nhau bằng tên service.
- `[servers]` trong `velocity.toml`: `lobby`, `limbo`, `banghoi-1`; `pokemon-1` đang comment (bật Pokémon thì bỏ comment).
- **LibreLogin**: database `librelogin` (MariaDB); `new-uuid-creator=MOJANG` (**không đổi sau khi đã có người chơi**); `auto-register=true` (crack không dùng được tên trùng tài khoản bản quyền); `limbo=[limbo]`, `lobby.root=[lobby]`; kick sau 120 giây chưa đăng nhập, sai 5 lần; TOTP tắt (cần Protocolize).
- **Qua playit mọi người chơi chung một IP** (proxy thấy IP agent), nên **`session-timeout=0`** (tắt nhớ đăng nhập theo IP, nếu bật thì người khác mạo danh được). Lấy IP thật cần PROXY protocol của playit + `haproxy-protocol = true` trong Velocity (chưa kiểm tra; sau đó vào thẳng bằng localhost sẽ không được nữa). `login-ratelimit = 3000` tính theo IP nên có thể chặn nhiều người vào cùng lúc qua playit.
- **Floodgate chỉ cài trên proxy.** Muốn Bedrock Forms (menu điện thoại) ở lobby thì cài Floodgate trên lobby, dùng **chung `key.pem`** với proxy.
- **LuckPerms**: Paper dùng `plugins/LuckPerms/config.yml` (MariaDB + Redis messaging). Fabric **chưa cấu hình**. Proxy **chưa cài** LuckPerms-Velocity.
- `include` của Compose: đường dẫn tương đối tính theo thư mục của từng file con. Biến dùng `:?` sẽ bị đòi **kể cả khi service đang tắt theo profile**, nên `PLAYIT_SECRET_KEY` dùng `:-`.

### Database
- `network` (dùng chung), `luckperms`, `librelogin`, `mode_<id>` (riêng từng chế độ).
- `infra/db-init/` **chỉ chạy một lần khi `data/mariadb` còn trống**. Schema về sau do network-core quản lý (dự kiến Flyway).

### Sao lưu (`docs/backup.md`)
- `backup-<server>` (`itzg/mc-backup` + restic) cạnh mỗi server có thế giới, kho chung `backups/restic`, mã hoá bằng `RESTIC_PASSWORD`.
  Mỗi giờ (lobby mỗi ngày), chỉ khi có người chơi. Giữ 24 bản theo giờ, 14 theo ngày, 8 theo tuần.
- mc-backup tự đọc mật khẩu RCON từ `data/<server>/.rcon-cli.env`, tự `restic init`. Mỗi service backup cần `hostname` cố định.
  Entrypoint của image là `backup`: chạy restic tay thì `docker compose run --rm --no-deps --entrypoint restic backup-<server> ...`.
- `db-backup`: `mariadb-dump --all-databases` mỗi 6 giờ ra `backups/mariadb/`, giữ 14 ngày.
- **Chưa có bản sao ngoài máy** (ổ ngoài hoặc cloud qua rclone).

### Chạy Docker trong session cloud của Claude Code
- Docker daemon không tự chạy: `dockerd > <scratchpad>/dockerd.log 2>&1 &`. Container có thể khởi động lại giữa chừng làm daemon tắt, khi đó bật lại.
- Docker Hub đôi khi trả **429**, thử lại sau.
- `raw.githubusercontent.com` đọc được (dùng để đọc README, wiki: `raw.githubusercontent.com/wiki/<owner>/<repo>/<Trang>.md`). `api.github.com`, `github.com` releases, Hangar, SpigotMC, Modrinth, PaperMC, GeyserMC, playit bị chặn.

## 6. Người dùng cần làm (chạy lần đầu trên máy nhà)

1. Cài Docker. Windows: Docker Desktop + WSL2 + Ubuntu, để thư mục dự án **trong Ubuntu**. Cần khoảng 8 GB RAM trống.
2. Tải code và tạo mật khẩu:
   ```bash
   git clone -b claude/exciting-cray-lh5jgq https://github.com/liinh97/DuckyMoMo.git
   cd DuckyMoMo
   ./ops/init.sh      # chép RESTIC_PASSWORD trong .env ra chỗ an toàn
   ```
3. `docker compose up -d`, chờ 5–10 phút, `docker compose ps`.
4. Vào game thử:
   - Java bản quyền `localhost`: vào thẳng lobby.
   - Java crack: bị giữ ở limbo, `/register matkhau matkhau` rồi sang lobby.
   - Java bản cũ (ví dụ 1.20.4): vẫn vào được (ViaBackwards).
   - Điện thoại: IP máy tính (`192.168.x.x`), cổng `19132`: vào thẳng lobby.
   - Ở lobby `/server banghoi-1`.
5. Gửi cho Claude: hệ điều hành, `docker compose ps`, log phần lỗi.

## 7. Việc tiếp theo (thứ tự đề xuất)

1. **Sửa lỗi từ lần chạy đầu** (mục 6). Sau đó ghim `MC_VERSION` và tag image.
2. **Cài plugin Bang Hội Chiến**: Towny (cần 0.101.2.5 trở lên), SiegeWar (`/swa install` sau khi cài), Vault, EssentialsX (tiền tệ), CoreProtect (bắt buộc), WorldGuard, OldCombatMechanics. Kiểm tra slug Modrinth, không có thì tải `.jar` tay. Danh sách: `modes/banghoi/README.md`.
3. **Áp cấu hình SiegeWar** theo `docs/banghoi-design.md` mục 4 (giờ trận T6 20:30, T7/CN 15:00 và 20:30; 5 trận; 1 cuộc vây/bang; vùng chiến 150 ô; bật Siege Assembly), khoá End (`settings.allow-end: false` trong `data/banghoi-1/bukkit.yml`), bật PvP hoang dã. Chép cấu hình về `modes/banghoi/server/plugins/`.
4. **Bật tunnel playit** (`tunnel/README.md`), cho bạn bè vào **mùa thử 4 tuần**.
5. **Chép sao lưu ra ngoài máy** (ổ ngoài hoặc rclone lên cloud).
6. **network-core v1** (Gradle nhiều module: `core-api`, `core-common`, `core-velocity`, `core-paper`): đồng bộ `modes/*/mode.yml` vào `network.modes`; đăng ký server động từ `network.servers`; ghi `network.players` (platform + auth); menu lobby tự sinh (rương cho Java, Bedrock Forms cho điện thoại); chế độ beta chỉ hiện với quyền `network.beta`; tự tính điểm mùa Bang Hội Chiến; acc mới phải chơi đủ vài giờ mới nhận quân hàm.
7. Về sau: web store (VietQR, thẻ cào qua DotMan, bảng `network.deliveries`, giao khi người chơi online, không dùng RCON trực tiếp); đồ trang trí dùng chung network; chế độ Pokémon (`core-fabric`, `_template-fabric`, modpack); có thể thêm ViaRewind (1.8), Dynmap + Dynmap-Towny, TownyResources.

## 8. Câu hỏi còn mở
- Tên miền.
- Đọc kỹ EULA và Minecraft Usage Guidelines về server offline-mode **trước khi mở web store** (đã chốt bán đồ trang trí cho cả người chơi crack).
- Máy nhà chạy Windows hay Linux, cấu hình bao nhiêu.
- Sau mùa thử: giờ trận chính thức, cấm vật phẩm nào, có đổi cách rơi đồ ở hoang dã không, số tiền (phí thành, giá chợ).

## 9. Prompt gợi ý để mở session mới

> Tôi đang làm dự án DuckyMoMo Network (server Minecraft, Docker Compose) trong repo `liinh97/DuckyMoMo`, nhánh `claude/exciting-cray-lh5jgq`. Hãy đọc `docs/HANDOFF.md` trước (bắt đầu từ mục 0), rồi `README.md` và các file trong `docs/`. Trả lời bằng tiếng Việt, nói rõ khi thông tin cần kiểm tra lại.
