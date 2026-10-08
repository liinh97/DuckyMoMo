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

### Trang Cài đặt `panel/` (08/10/2026)
- Người dùng muốn chỉnh setting trên web, **bấm Lưu là áp dụng**. `panel/compose.yaml`: build `panel/Dockerfile` (python:3.13-alpine + `ruamel.yaml`), mở `127.0.0.1:${PANEL_PORT:-8889}`, đăng nhập HTTP Basic bằng `PANEL_USER`/`PANEL_PASSWORD` trong `.env` (`ops/init.sh` tự sinh).
- Mỗi chế độ: `modes/<id>/settings.schema.yml` (nhóm, nhãn tiếng Việt, kiểu, file + đường dẫn YAML trong `data/<server>/`, lệnh RCON) và `modes/<id>/settings.yml` (giá trị, có trong Git). Panel tự tìm mọi `modes/*/settings.schema.yml`.
- Bấm Lưu: kiểm tra giá trị, ghi vào file cấu hình plugin (ruamel giữ comment và kiểu nháy, ví dụ `'20.0'`), ghi `settings.yml`, rồi gửi lệnh qua RCON (`swa reload`, `ta reload config`, `ocm reload`, `worldborder …`). Mật khẩu RCON đọc từ `data/<server>/.rcon-cli.env` (itzg tự sinh), **không cần `docker.sock`**.
- "Áp dụng lại tất cả": ghi toàn bộ `settings.yml` vào plugin. Dùng khi server mới cài (plugin vừa tạo file cấu hình mặc định) hoặc khi sửa tay `settings.yml`.
- Đã thử: apply-all chỉ đổi đúng các dòng cần, comment giữ nguyên, các lệnh nạp lại đều thành công.
- Mục chỉ lưu (không có `file`/`commands`): loại map, link map, reset mỗi mùa. Dùng khi làm chức năng mở mùa mới.
- **Chưa làm**: bật/tắt plugin (TownyResources, jobs) từ trang này, vì cần thêm/bớt plugin và khởi động lại container; chức năng "mở mùa mới".

### Dashboard xem log: Dozzle (07/10/2026)
- `dashboard/compose.yaml`: `amir20/dozzle` (đang là v11.3.0), chạy mặc định, **chỉ mở trên `127.0.0.1:8888`** (`DASHBOARD_PORT`).
- `DOZZLE_FILTER=label=com.docker.compose.project=duckymomo`: chỉ hiện container của dự án này, không hiện tram6/goc6.
- Chỉ xem log (chat, vào/ra, lỗi), không gõ lệnh. Gõ lệnh: `docker compose exec <server> rcon-cli`.
- Mount `docker.sock` (`:ro` không giới hạn quyền gọi API), nên **không được mở ra ngoài** khi chưa có lớp đăng nhập. Muốn xem từ xa: Cloudflare Tunnel + Cloudflare Access.
- Cài đặt của Dozzle lưu ở `data/dozzle`.
- **Tab `activity`** (08/10/2026, người dùng muốn gộp chat + vào/ra + lệnh vào **một** tab, tách khỏi log khác): service `activity` trong `dashboard/compose.yaml` (mẫu `x-log-feed`, image `docker:29-cli`, `MODE: all`). Script `dashboard/log-feed.sh` đọc log các container của dự án qua `docker.sock`, lọc bằng awk, in `[server] giờ NHÃN nội dung`, nhãn `CHAT` / `VÀO/RA` / `LỆNH`.
  - CHAT: `<Tên> nội dung` từ các server Paper.
  - VÀO/RA: từ proxy (vào/rời network, `→ server`) và Paper (`mất kết nối: lý do`).
  - LỆNH: `issued server command` từ Paper. **Lệnh có mật khẩu bị che** (`/login ***`, `/register ***`, `/changepassword ***`…).
  - Script vẫn hỗ trợ `MODE: chat | join | commands` nếu sau này muốn tách tab.
  - Lần đầu hiện lại 6 giờ log gần nhất (`HISTORY`), sau đó chỉ log mới. Tự nhận server mới mỗi 30 giây (lọc theo label project + image `itzg/minecraft-server` / `itzg/mc-proxy`).
- **Đăng nhập**: `DOZZLE_AUTH_PROVIDER: simple`. Tạo hoặc đổi tài khoản bằng `./ops/dozzle-user.sh <ten>`: mật khẩu gõ ẩn, ghi `data/dozzle/users.yml` (dạng băm), rồi tạo lại container. Trang thiết lập trên web của Dozzle chỉ cho tạo tài khoản trong 15 phút đầu sau khi cài, quá hạn thì báo lỗi, nên phải dùng script.
- Nhóm LuckPerms `admin` (quyền `*`, weight 100, prefix `&c[Admin] `). `.Griim7833` (Bedrock, UUID `00000000-0000-0000-0009-01f6ea9a66df`) thuộc nhóm này. Tên Bedrock có dấu chấm thì LuckPerms không nhận, phải dùng UUID.
- Hướng tiếp theo đã bàn: DiscordSRV (chat ↔ Discord, console), Plan (thống kê người chơi), web RCON (cần kiểm tra công cụ còn được cập nhật).

### Chưa kiểm chứng
- Vào bằng client cũ (ví dụ 1.8.9, 1.12.2, 1.20.1): có vào được không, AuthMe đăng ký/đăng nhập thế nào.
- Tạo tunnel trên dashboard và vào game qua địa chỉ playit từ ngoài mạng nhà.
- **Vào game thật** bằng Java (launcher không bản quyền) và Bedrock: `/register`, `/login`, chặn `/server banghoi-1` khi chưa đăng nhập, `/premium`, skin, tên Bedrock có tiền tố Floodgate.
- Bedrock có phải `/register` không, và hộp thoại đăng nhập (dialog) của AuthMe hiện thế nào trên điện thoại qua Geyser.
- Người dùng app Bedrock bẻ khóa (không đăng nhập Xbox) có vào được qua Floodgate không.
- Pokémon (Fabric): slug `fabric-api`, `cobblemon`, `fabricproxy-lite`, `luckperms` và các key trong `FabricProxy-Lite.toml`.

👉 **Việc đầu tiên của session mới**: hỏi người dùng đã vào game thử chưa. Nếu lỗi, xin log `docker compose logs <service>` để sửa. Sau đó ghim `MC_VERSION` và tag image.

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
3. **Thiết kế luật chơi Bang Hội Chiến**: đã có bản nháp `docs/banghoi-design.md` (Towny + SiegeWar). Còn chốt số liệu ở mục 12 của file đó.
4. **Sao lưu**: thêm `itzg/mc-backup` (restic) cho các server có thế giới, và `mariadb-dump` định kỳ.
5. ~~**Tunnel playit.gg**~~: đã thêm (`tunnel/compose.yaml`). Còn lại: tạo 2 tunnel trên dashboard, thử từ ngoài mạng nhà, tạo lại secret key.
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
- ~~Bang Hội Chiến dùng Towny + SiegeWar hay Factions?~~ Đã chốt 08/10/2026: **Towny + SiegeWar**. Bản thiết kế nháp: `docs/banghoi-design.md` (còn các câu hỏi ở mục 12).
- ~~Có cho người chơi crack (offline mode) vào không?~~ Đã chốt: **có** (mục 3.8). Còn mở: điều khoản thương mại của Mojang có nói gì riêng về server offline không. Cần đọc kỹ trước khi mở web store.
- Tên server, thương hiệu, tên miền.
- Máy nhà chạy Windows hay Linux, cấu hình bao nhiêu?

## 8. Prompt gợi ý để mở session mới

> Tôi đang làm dự án DuckyMoMo Network (server Minecraft, Docker Compose) trong repo `liinh97/DuckyMoMo`, nhánh `claude/confident-thompson-q939lp`. Hãy đọc `docs/HANDOFF.md`, `README.md` và các file trong `docs/` trước, rồi tiếp tục từ mục "Việc tiếp theo". Trả lời bằng tiếng Việt, nói rõ khi thông tin cần kiểm tra lại.
