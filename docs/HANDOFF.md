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
8. **Cho người chơi không bản quyền vào** (chốt 07/10/2026, vì phần lớn người chơi Việt dùng bản không bản quyền). Proxy chạy `online-mode = false`. **AuthMe** bảo vệ tên bằng `/register`, `/login` ở lobby. Người có bản quyền gõ `/premium` để lần sau vào thẳng. **SkinsRestorer** lo skin.

## 4. Trạng thái hiện tại

### Đã có trong repo
| Thành phần | File chính |
|---|---|
| File gốc | `compose.yaml` (danh sách include), `.env.example`, `.gitignore`, `.gitattributes` |
| MariaDB + Redis | `infra/compose.yaml`, `infra/db-init/01-databases.sh`, `02-network-schema.sql`, `03-mode-databases.sql` |
| Proxy | `proxy/compose.yaml`, `proxy/config/velocity.toml`, `proxy/config/forwarding.secret` (mẫu chứa biến), `proxy/config/plugins/authmevelocity/config.yml`, `proxy/plugins/Geyser-Velocity/config.yml` |
| Lobby (Paper) | `lobby/compose.yaml`, `lobby/config/paper-global.yml`, `lobby/plugins/LuckPerms/config.yml`, `lobby/plugins/AuthMe/config.yml` |
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

### Chạy lần đầu trên máy nhà (07/10/2026, WSL Ubuntu 22.04, Docker 29.5, Compose v5.1)
- `docker compose up -d`: cả 5 service `healthy`. Paper **26.2** (build 132), Velocity **4.2.0**, Geyser **2.11.3**, Floodgate 2.2.5, LuckPerms 5.5.71, WorldEdit 7.4.5, Chunky 1.5.3.
- Slug Modrinth `luckperms`, `worldedit`, `chunky` tải được. LuckPerms nối được MariaDB và Redis.
- Secret Velocity khớp giữa `.env`, `forwarding.secret` và `paper-global.yml` của lobby và banghoi-1.
- Ping được từ máy: Java `127.0.0.1:25565`, Bedrock `127.0.0.1:19132` (dùng `itzg/mc-monitor`).
- Velocity tự nâng `velocity.toml` từ `config-version` 2.7 lên 2.9, giữ đủ các key. `ping-passthrough = "DISABLED"` được đổi thành bảng `[ping-passthrough]`, mọi mục `false`.
- Geyser mặc định `auth-type: online`, **đã sửa** thành `floodgate` bằng file `proxy/plugins/Geyser-Velocity/config.yml`.
- RAM thực dùng: proxy ~0.7 GB, lobby ~2.2 GB, banghoi-1 ~3.8 GB.
- Lobby log `ERROR: No key layers in MapLike[{}]` vì `generator-settings={}` khi `LEVEL_TYPE=minecraft:flat`. Server vẫn chạy (Minecraft dùng preset phẳng mặc định), **chưa xác nhận bằng mắt** thế giới lobby trông thế nào.

### Chuyển sang offline mode + AuthMe (07/10/2026)
- Proxy: `online-mode = false`, `force-key-authentication = false`. Paper (lobby, banghoi-1, khuôn mẫu): `proxies.velocity.online-mode: false`.
- Plugin proxy (ghim bằng link Modrinth trong `PLUGINS`): **AuthMe-Velocity 6.0.1**, **SkinsRestorer 15.12.6**. Lobby: `authmereloaded` qua `MODRINTH_PROJECTS` (đang là 6.0.1).
- Không cần plugin AuthMeVelocity riêng: từ bản 6, AuthMeReloaded có sẵn plugin Velocity.
- AuthMe chỉ cài ở **lobby**. Proxy chặn người chưa đăng nhập sang server khác (`serverSwitch.requiresAuth`), nên banghoi-1 không cần AuthMe.
- Tài khoản lưu ở bảng `network.authme` (MariaDB), mật khẩu băm BCRYPT, tin nhắn tiếng Việt (`vn`).
- Secret giữa proxy và lobby: `AUTHME_SECRET` trong `.env` (`ops/init.sh` tự thêm vào `.env` cũ nếu còn thiếu).
- `maxRegPerIp: 0` (không giới hạn), vì trong mạng nhà và qua playit.gg mọi người có thể chung một IP. **Xem lại khi mở công khai.**
- `allowedNicknameCharacters: '\.?[a-zA-Z0-9_]*'` để tên Bedrock có dấu chấm ở đầu vẫn vào được.
- Đã kiểm chứng bằng log: AuthMe lobby nối được MariaDB, tạo bảng `authme`, hook LuckPerms. Proxy nhận `authServers: lobby`, premium verification, secret khớp với lobby.
- Cảnh báo bỏ qua được: `protectInventory` cần PacketEvents (lobby là adventure, chưa cần). GeoIP tắt vì không có tài khoản MaxMind.
- **Bẫy**: mc-proxy chỉ điền `${CFG_...}` cho file trong `/config`, không điền cho `/plugins`. Cấu hình plugin proxy nào có secret thì đặt ở `proxy/config/plugins/<plugin>/`.

### Tunnel playit.gg (07/10/2026)
- `tunnel/compose.yaml`: service `playit` (`ghcr.io/playit-cloud/playit-agent:1.0`, đang là 1.0.12), profile `tunnel`, key ở `PLAYIT_SECRET_KEY` trong `.env`.
- `network_mode: "service:proxy"`: dùng chung mạng với proxy, nên trên dashboard playit, tunnel trỏ tới `127.0.0.1:25577` (Java, TCP) và `127.0.0.1:19132` (Bedrock, UDP). `depends_on.restart: true` để proxy khởi động lại thì playit cũng khởi động lại.
- Bật: `docker compose --profile tunnel up -d playit`. Agent đã kết nối được (agent tên `duckymomo`).
- Docker Desktop + WSL: `docker pull` từ ghcr.io có thể lỗi `error getting credentials`. Image công khai thì tải bằng `DOCKER_CONFIG` trỏ tới thư mục có `config.json` rỗng (`{}`).
- Tunnel Java `duckymomo-java`: `pgsql-youths.tun.ply.gg` (SRV trỏ cổng 28438), máy chủ Singapore. **Đã vào game thật qua tunnel** (07/10, 23:11): Linh vào được lobby.
- Tunnel Bedrock `duckymomo-bedrock`: `pgsql-mississauga.tun.ply.gg` cổng **21185** (Bedrock không đọc SRV, phải nhập cả cổng). Ping Geyser qua tunnel có phản hồi (`mc-monitor status-bedrock`). Geyser để `transport: raknet`, đúng loại tunnel Bedrock (RakNet) của gói miễn phí.
- **Đừng kiểm tra tunnel bằng `mc-monitor status`**: nó luôn báo `EOF` qua playit, dù game vào bình thường. Phải thử bằng client thật và xem log `[connected player]` của proxy.
- Proxy thấy người chơi qua playit với IP dạng `127.x.x.x` (ví dụ `127.135.13.102`). Có vẻ playit gán mỗi người chơi một địa chỉ riêng, nhưng **chưa xác nhận** với hai người khác nhau. Chưa bật Proxy Protocol.
- Secret key đã lộ trong ảnh chụp ở session 07/10. **Nên tạo lại key** trên dashboard rồi sửa `.env`.

### Nhiều phiên bản client (07/10/2026)
- **ViaVersion 5.12.0 + ViaBackwards 5.12.0 + ViaRewind 4.2.0**, cài qua `MODRINTH_PROJECTS` trên **lobby, banghoi-1 và `_template-paper`**. Không cài ở proxy, vì Velocity tự nhận client từ 1.7 trở lên, và để server Pokémon (Fabric, cần đúng phiên bản) không bị ảnh hưởng.
- Server vẫn là 26.2. Về lý thuyết client 1.7 tới 26.x đều vào được. **Chưa thử** bằng client cũ thật.
- **Rủi ro chưa kiểm chứng**: hộp thoại đăng ký/đăng nhập của AuthMe (dialog) chỉ có trên client 1.21.6 trở lên. Client cũ có thể không thấy hộp thoại. Nếu vậy, thử gõ `/register`, `/login` trong chat, hoặc tắt `settings.registration.dialog.preJoin.enable`.
- **Bẫy**: dòng `#` **bên trong** khối `MODRINTH_PROJECTS: |` không phải comment YAML. Image itzg đọc nó thành tên plugin (`viabackwards: client…` thành project kèm phiên bản) và server khởi động lỗi liên tục. Comment phải đặt **trên** dòng `MODRINTH_PROJECTS:`.

### network-core: bắt đầu với `core-paper` (08/10/2026)
- `network-core/` (Gradle Kotlin DSL, Java 25). Module đầu tiên `core-paper` tạo plugin **DuckyMoMoCore**.
- Build: `./ops/build-core.sh`. Script chạy `gradle:9.6-jdk25` trong Docker (cache ở volume `duckymomo-gradle-cache`) rồi copy `DuckyMoMoCore.jar` vào `lobby/plugins/`. File `.jar` không commit, nên máy mới phải build trước khi `up`.
- Phụ thuộc: `paper-api:26.2.build.132-stable` và `fr.xephi:authme-core:6.0.1` (`compileOnly`, không lấy phụ thuộc kéo theo). Nâng Paper thì sửa phiên bản trong `core-paper/build.gradle.kts`.
- **Tính năng 1: Bedrock auto-login** (`BedrockAuthListener`). Người chơi Bedrock (UUID Floodgate có nửa đầu bằng 0 và tên bắt đầu bằng `.`) được tự đăng ký AuthMe bằng mật khẩu ngẫu nhiên, rồi `forceLoginFromProxy` ở pha configuration, trước hộp thoại pre-join của AuthMe. Dự phòng: `forceLogin` 1 giây sau khi vào. Lý do: Bedrock đã xác thực qua Xbox, và app điện thoại bị văng mỗi khi chuyển app nên gõ lại mật khẩu rất phiền.
- **Chưa kiểm chứng bằng người thật**: đã build và nạp được trên lobby, chưa thấy Bedrock vào lại mà không hiện hộp thoại.
- Kế hoạch network-core cũ (mục 6 phần "Việc tiếp theo") vẫn giữ: thêm `core-api`, `core-common`, `core-velocity` khi cần.

### Bang Hội Chiến: đã cài plugin (08/10/2026)
- `modes/banghoi/compose.yaml`. Modrinth: `towny` (0.103.2.0), `vaultunlocked` (hiện tên Vault 2.20.3), `coreprotect` (24.1), `worldguard` (7.0.19). `PLUGINS` (ghim link): SiegeWar 3.7.0 (GitHub), OldCombatMechanics 2.7.0 (GitHub), EssentialsX + Chat + Spawn bản dev #1832 `2.22.1-dev+27` (ci.ender.zone, vì bản chính thức 2.22.0 mới tới 26.1.2; dấu `+` trong link phải viết `%2B`).
- Đã chạy `/swa install`, `/ta reload all`. Công thành: bật ở `world`, tắt ở `world_nether`, `world_the_end` (`/townyworld <world> toggle warallowed on|off`).
- Towny đang lưu **flatfile** (chưa chuyển sang MariaDB `mode_banghoi`). CoreProtect đang dùng SQLite mặc định. Cân nhắc chuyển trước khi mở công khai.
- Towny không có bản dịch tiếng Việt.
- OldCombatMechanics dùng "modeset": `old` (1.8), `new`. `worlds.__default__` quyết định ai được dùng kiểu nào; người chơi tự đổi bằng `/ocm mode` nếu danh sách có nhiều kiểu.

### Sao lưu tự động (08/10/2026)
- Mỗi server có thế giới có service sao lưu đi kèm **trong chính compose của nó**: `backup-lobby` (lobby/), `backup-banghoi-1` (modes/banghoi/), `backup-pokemon-1` (profile `pokemon`), `backup-__MODE_ID__-1` trong `_template-paper` (cùng profile với chế độ). Image `itzg/mc-backup`, tar.gz, `PAUSE_IF_NO_PLAYERS=true`, bỏ `*.jar,cache,logs`.
- Database: `backup-db` trong `infra/compose.yaml`, script `infra/backup-db.sh` (`mariadb-dump --all-databases --single-transaction`, gzip, xoá bản cũ hơn `BACKUP_KEEP_DAYS`). Mật khẩu root qua `MYSQL_PWD`.
- `.env`: `BACKUP_DIR` (bỏ trống = `backups/` trong repo, có trong `.gitignore`; đặt thì phải là đường dẫn **tuyệt đối**, vì đường dẫn tương đối tính theo từng file compose con), `BACKUP_INTERVAL` (6h), `BACKUP_KEEP_DAYS` (7).
- **RCON dùng chung**: `RCON_PASSWORD` trong `.env` (`ops/init.sh` tự sinh), đặt cho mọi server Paper/Fabric. itzg ghi mật khẩu này ra `data/<server>/.rcon-cli.env`, nên trang Cài đặt vẫn đọc được.
- Đã kiểm chứng: `backup now` tạo `.tar.gz` có `world/level.dat` và cấu hình plugin; dump database có đủ 5 database, có bảng `authme`. **Chưa diễn tập khôi phục thật** (lệnh khôi phục ở README mục "Sao lưu và khôi phục").
- Bản sao lưu vẫn nằm trên **cùng ổ C** với server. Nên đẩy thêm một bản ra ngoài (ổ F, cloud bằng rclone/restic) khi mở công khai.

### Đổi sang map Trái Đất 1:1000 (11/10/2026)
- Tạo bằng `XMX=30g MEM_LIMIT=33g ./tools/earthmap/build-earth.sh 40`: **3 giờ 32 phút** (tạo dữ liệu 80 phút, ghi file ~2 giờ), container lên tới **31 GB** (19g heap không đủ: lần đầu 18,5 GB sau 10 phút). Lúc tạo đã tạm tắt DuckyMoMo và phần Trạm 6 không liên quan quán (giữ goc6 + gateway/auth/portal/ShopeeFood/Grab/Zalo/SePay). Kết quả `tools/earthmap/work/out/earth_1-1000`: 17 GB, 3.528 region, 43.008 × 21.504 block. Chunk WorldPainter **không có heightmap** (server tính khi nạp), nên script warp chỉ chạy được sau lượt trồng cây.
- Lắp bằng `./ops/doi-map-banghoi.sh <map> <nhãn>` (người dùng tự chạy; Claude bị chặn quyền khi đổi tên thư mục data). Script không xoá gì: world cũ `data/banghoi-1/world-1-2000-20261011`, warp cũ `plugins/Essentials/warps-world-1-2000-20261011`, tiến độ trang trí/công trình cũ `*.world-1-2000-20261011`; dời vùng WorldGuard `spawn` theo `spawn_x/spawn_z`.
- Thông số mới: viền 21504 × 10752, spawn Hà Nội (12646, 63, -2512), vùng spawn x 12582..12710, z -2576..-2448; 1 độ ≈ 119,47 block (X = kinh độ × 119,47; Z = −vĩ độ × 119,47). `make-warps.py` tự lấy tỉ lệ từ `border_x` trong `settings.yml`.
- Trồng cây chạy lại từ 19:03 11/10: 3.612.672 chunk, ~138 chunk/s, dự kiến ~7 giờ, TPS 20. banghoi-1 dùng ~7,5 GB khi trang trí → `mem_limit` 8g → **10g**. Còn lại sau khi xong: `/dmm structures start` (đặt công trình), rồi tạo lại warp + biển tên (warp cũ đã gỡ khỏi repo).

### Sao lưu chuyển sang restic + dọn ổ (11/10/2026)
- Map 1:1000 làm mỗi bản `.tar.gz` nặng 17 GB (gần như không nén được; 6 giờ/lần × 7 ngày sẽ ~480 GB). `backup-banghoi-1`, `backup-lobby`, pokemon, mẫu `_template-paper`: `BACKUP_METHOD=restic`, kho ở `backups/<server>/restic` (thư mục root-only, xem bằng container), `RESTIC_HOSTNAME`=tên server, giữ `--keep-within ${BACKUP_KEEP_DAYS}d --keep-daily 30`, `EXCLUDES` thêm `world-cu-*,world-1-2000-*,world-tu-nhien-*`. `RESTIC_PASSWORD` trong `.env` (`ops/init.sh` tự sinh) — **mất là không khôi phục được**; người dùng đã cất thêm một bản ngoài máy (11/10).
- Đo: lần đầu banghoi-1 17,0 GB → 12,1 GB lưu, 49 giây; lần hai 107 KB; khôi phục thử `level.dat` khớp file hiện tại. Lệnh khôi phục: README "Sao lưu và khôi phục". `backup-db` vẫn là `mariadb-dump` `.sql.gz`.
- Lưu ý: khi `backup now` server `save-off` vài giây–1 phút; trong lúc trang trí map nên `/dmm decorate pause` trước (chưa chắc chunk bị gỡ lúc `save-off` có được lưu).
- Đã dọn: 12 bản tar.gz 1:2000 cũ của banghoi-1 (giữ `banghoi-1-20261011-150128.tar.gz`, bản 1:2000 cuối), tar.gz lobby cũ (giữ bản cuối), map gốc 1:2000/1:4000, image Chrome. Ổ: 112 → 150 GB trống. Còn giữ: world 1:2000 `data/banghoi-1/world-1-2000-20261011` (4,8 GB, xoá khi map mới ổn), map gốc 1:1000 `tools/earthmap/work/out/earth_1-1000` (17 GB, để reset mùa).

### Map Trái Đất 1:2000 cho banghoi-1 (09/10/2026)
- Người dùng không muốn mua map → **tự tạo** bằng WorldPainter 2.27.1 + script DerMattinger/MinecraftEarthMap (MIT). Công cụ và hướng dẫn: `tools/earthmap/` (`build-earth.sh`, `README.md`); file tải về và map tạo ra ở `tools/earthmap/work/` (không commit).
- Đã lắp vào `data/banghoi-1/world` (21.504 × 10.752 block, 4,2 GB). Thế giới tự nhiên cũ giữ ở `data/banghoi-1/world-tu-nhien-20261009`.
- Viền: **ChunkyBorder** (Modrinth `chunkyborder`) hình chữ nhật 10752 × 5376, viền vanilla nới tối đa. Spawn: Hà Nội (6323, 63, -1256). Đều là setting trên trang Cài đặt (`border_x`, `border_z`, `spawn_x`, `spawn_z`).
- Tạo 1:2000 cần ~8 GB heap: lần này đã tắt cả network DuckyMoMo ~1 giờ. **Người dùng yêu cầu (09/10): DuckyMoMo, tram6, goc6 phải chạy liên tục, không tắt cái nào.** Lần sau muốn tạo lại map phải có đủ RAM trước (nâng WSL/nâng RAM máy), không tắt dịch vụ.
- **Giảm RAM (09/10)**: lobby `INIT_MEMORY 512M / MAX_MEMORY 1G` (mem_limit 2g), banghoi-1 `1G / 3G` (mem_limit 4g), proxy `MEMORY 512M`. **Không dùng `USE_AIKAR_FLAGS`**: nó có `AlwaysPreTouch`, chiếm sẵn toàn bộ heap (banghoi-1 lên 3,6/4 GB, sát mem_limit). Kết quả: lobby 1,1 GB, banghoi-1 1,8 GB, WSL trống từ ~2 lên ~4 GB. Đông người thì tăng MAX_MEMORY và mem_limit.
- **Nâng RAM (11/10)**: máy lên **64 GB** (lắp thêm 2×16 GB, mã `UD848 CL18`; cả 4 thanh chạy 3466 MHz). `.wslconfig` (`C:\Users\linh_\.wslconfig`) đổi `memory=16GB` → `40GB` (bản cũ: `.wslconfig.bak-16GB-20261011`). banghoi-1: `MEMORY 6G` + `USE_AIKAR_FLAGS` (giữ sẵn cả heap), `mem_limit 8g`; lobby `MAX_MEMORY 2G`, `mem_limit 3g`; proxy `MEMORY 1G`; mẫu `_template-paper` `MAX_MEMORY 4G`, `mem_limit 6g`. **Chỉ có hiệu lực sau `wsl --shutdown` rồi `docker compose --profile tunnel up -d`** (không áp dụng khi WSL còn 16 GB: banghoi-1 giữ sẵn 6 GB sẽ làm WSL thiếu RAM). Sau khi WSL khởi động lại phải kiểm tra bind mount (xem "Sự cố 11/10"). **Đã áp dụng 11/10 ~15:00**: WSL 39 GB, banghoi-1 chạy `-Xms6G -Xmx6G` + AlwaysPreTouch (dùng ~6,8/8 GB), TPS 20, Windows còn trống ~27 GB; bind mount world và thư mục sao lưu đều ghi đúng ra ổ cứng.
