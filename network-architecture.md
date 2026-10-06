# Thiết kế khung network: thêm chế độ mới dễ dàng

> Bổ sung cho `minecraft-server-plan.md`. Mục tiêu: **thêm một chế độ mới (kể cả Pokémon/Cobblemon) mà không phải sửa lobby, proxy hay các chế độ cũ.**

---

## 1. Nguyên tắc cốt lõi

1. **Mỗi chế độ là một "module" độc lập**: có server riêng, thế giới riêng, dữ liệu riêng, kinh tế riêng. Gắn vào hay gỡ ra không ảnh hưởng chế độ khác.
2. **Danh sách chế độ khai báo bằng dữ liệu, không viết cứng trong code**: lobby và proxy **đọc danh sách chế độ (mode registry)** để hiện menu và chuyển người chơi. Thêm chế độ chỉ là thêm một dòng vào danh sách, không sửa code lobby.
3. **Dịch vụ chung nằm ở "lõi network"**: tài khoản, rank, đồ trang trí, party, chat, xử phạt, giao hàng từ web store. Các chế độ chỉ **dùng** những dịch vụ này.
4. **Không phụ thuộc nền tảng**: lõi được viết tách thành phần logic chung và các phần chuyển đổi (adapter) cho Paper, Fabric, Velocity. Nhờ vậy chế độ chạy Fabric (Pokémon) vẫn dùng chung được rank, đồ trang trí và đơn hàng.
5. **Giao tiếp giữa các server qua Redis và database**, không gọi trực tiếp vào nhau.
6. **Mọi thứ chạy bằng Docker Compose** (không dùng Pterodactyl). Mỗi thành phần và mỗi chế độ có file `compose.yaml` riêng, file gốc gom lại bằng `include`. Chi tiết ở mục 6.

---

## 2. Sơ đồ tổng thể

```
 Java vanilla      Java + modpack (Pokémon)      Bedrock (điện thoại)
      \                    |                        /
       \                   |              Geyser + Floodgate
        ▼                  ▼                       ▼
 ┌──────────────────────────────────────────────────────────┐
 │ PROXY: Velocity + network-core-velocity                  │
 │  - đọc danh sách chế độ, đăng ký server con động         │
 │  - kiểm tra loại client (Java / Bedrock / có modpack)    │
 │  - party, bạn bè, chat liên server, xử phạt              │
 └───────────┬──────────────────────────────────────────────┘
             │
   ┌─────────┼──────────────┬──────────────┬─────────────────┐
   ▼         ▼              ▼              ▼                 ▼
 LOBBY    BANG HỘI       SURVIVAL      ONEBLOCK          POKÉMON
 (Paper)  CHIẾN (Paper)  (Paper)       (Paper)           (Fabric + Cobblemon)
   │         │              │              │                 │
   └── network-core-paper ──┴──────────────┘     network-core-fabric
             │                                            │
             ▼                                            ▼
 ┌──────────────────────────────────────────────────────────┐
 │ MariaDB                                                  │
 │  - schema `network`: tài khoản, chế độ, server, đồ trang │
 │    trí, đơn hàng, giao hàng, xử phạt                     │
 │  - schema riêng từng chế độ: `mode_banghoi`,             │
 │    `mode_pokemon`…                                       │
 │ Redis: sự kiện liên server (pub/sub), cache, trạng thái  │
 │ online                                                   │
 └──────────────────────────────────────────────────────────┘
             ▲
             │ đơn hàng → hàng đợi giao hàng
 ┌───────────┴──────────────┐
 │ Web store (VietQR, thẻ   │  ← dùng lại module đơn hàng, sản phẩm,
 │ cào qua DotMan, …)       │    khách hàng, báo cáo của app bán hàng
 └──────────────────────────┘
```

---

## 3. Danh sách chế độ (mode registry)

Mỗi chế độ có một file `mode.yml` (lưu trong Git). Lõi network đồng bộ file này vào bảng `network.modes`.

```yaml
id: pokemon
display_name: "Pokémon"
icon: "cobblemon:poke_ball"        # icon trong menu lobby (vanilla thì dùng item thay thế)
description: "Bắt, nuôi và đấu Pokémon"
status: beta                       # open | beta | maintenance | closed
platform: fabric                   # paper | fabric
minecraft_version: "1.21.1"        # khóa theo phiên bản Cobblemon hỗ trợ
clients:                           # loại client được vào
  - java-modded
required_mods: [cobblemon]         # lobby kiểm tra trước khi cho vào
modpack_url: "https://modrinth.com/modpack/<ten-modpack>"
servers:
  - name: pokemon-1
    address: pokemon-1:25565       # tên service trong Docker Compose
visibility:
  beta_permission: "network.beta"  # chế độ beta chỉ hiện cho tester
economy: isolated                  # tiền, vật phẩm tách riêng
cosmetics: [chat_tag, name_color]  # đồ trang trí network áp dụng được ở chế độ này
```

Ví dụ chế độ thường:

```yaml
id: banghoi
display_name: "Bang Hội Chiến"
status: open
platform: paper
minecraft_version: "1.21.x"
clients: [java, bedrock]
servers:
  - name: banghoi-1
    address: banghoi-1:25565
economy: isolated
cosmetics: [chat_tag, name_color, kill_effect, particle, guild_banner]
```

**Lobby** đọc danh sách này rồi tự sinh menu (menu dạng rương cho Java, Bedrock Forms cho điện thoại):
- Bedrock không có trong `clients` → ẩn chế độ đó, hoặc hiện "chỉ dành cho PC".
- Java chưa cài modpack → hiện "Cần cài modpack" kèm link.
- `status: beta` → chỉ người có quyền `network.beta` thấy.
- `status: maintenance` → hiện nhưng không cho vào.

---

## 4. Lõi network (code tự viết)

### Cấu trúc project (Gradle nhiều module)
```
network-core/
├── core-api/        # interface và model dùng chung (PlayerProfile, Mode, Order, Cosmetic…)
├── core-common/     # logic thuần: truy cập DB, Redis, xử lý giao hàng, danh sách chế độ
├── core-velocity/   # adapter cho proxy: chuyển server, kiểm tra client, party, chat
├── core-paper/      # adapter cho Paper: menu lobby, áp dụng đồ trang trí, giao hàng
└── core-fabric/     # adapter cho Fabric: dùng cho Pokémon và các chế độ mod sau này
```

### Dịch vụ dùng chung
| Dịch vụ | Cách làm |
|---|---|
| **Hồ sơ người chơi** | `network.players`: UUID, tên, nền tảng (java / bedrock / modded), lần đầu vào, lần cuối vào. Liên kết tài khoản Java với Bedrock (Floodgate có sẵn tính năng link) |
| **Rank và quyền** | **LuckPerms** (có bản cho Velocity, Paper, Fabric) dùng chung MySQL. Quyền riêng từng chế độ dùng **context `server=`** hoặc nhóm server |
| **Đồ trang trí** | `network.cosmetics` (danh mục), `network.player_cosmetics` (sở hữu). Mỗi chế độ khai báo loại nào áp dụng được |
| **Giao hàng từ web store** | Web store ghi vào `network.deliveries` (người nhận, nội dung, phạm vi network hoặc từng chế độ, trạng thái). Server tương ứng nhận qua Redis hoặc định kỳ đọc bảng, **chỉ giao khi người chơi online**, ghi lại kết quả. **Không dùng RCON trực tiếp**, vì như vậy giao được khi người chơi đang offline hoặc ở chế độ khác, có thử lại và có log |
| **Xử phạt** | Ban, mute ở mức network, nằm ở proxy. Hoặc dùng plugin có sẵn hỗ trợ Velocity |
| **Party, bạn bè, chat** | Ở proxy, đồng bộ qua Redis |
| **Sự kiện liên server** | Redis pub/sub: `player.join_mode`, `delivery.created`, `mode.status_changed`… |

### Database
```
network.players            network.modes             network.servers
network.player_links       network.cosmetics         network.player_cosmetics
network.orders             network.deliveries        network.punishments
mode_banghoi.*             mode_survival.*           mode_pokemon.*   (mỗi chế độ tự quản lý)
```

---

## 5. Cấu trúc repo cấu hình (Git)

```
mc-network/
├── compose.yaml            # file gốc: chỉ gồm danh sách include
├── .env.example            # mẫu biến môi trường (commit). File .env thật KHÔNG commit
├── .gitignore              # bỏ qua .env, data/
├── data/                   # dữ liệu chạy (thế giới, DB…), KHÔNG commit, có sao lưu
├── infra/
│   ├── compose.yaml        # MariaDB, Redis (sau này thêm backup)
│   └── db-init/            # SQL tạo schema network và mode_* lần đầu
├── proxy/
│   ├── compose.yaml        # Velocity + Geyser + Floodgate
│   ├── config/             # velocity.toml, cấu hình plugin proxy
│   └── plugins/
├── shared/                 # cấu hình dùng chung (LuckPerms storage, kết nối DB/Redis) dạng template
├── lobby/
│   ├── compose.yaml
│   ├── config/
│   └── plugins/
├── modes/
│   ├── _template-paper/    # khuôn mẫu chế độ Paper mới (có sẵn compose.yaml)
│   ├── _template-fabric/   # khuôn mẫu chế độ Fabric (mod) mới
│   ├── banghoi/
│   │   ├── compose.yaml
│   │   ├── mode.yml
│   │   ├── server/         # config/, plugins/
│   │   └── db/             # migration SQL cho schema mode_banghoi
│   └── pokemon/
│       ├── compose.yaml
│       ├── mode.yml
│       ├── server/         # config/ (mod tải tự động qua Modrinth)
│       ├── modpack/        # định nghĩa modpack cho client (đăng lên Modrinth)
│       └── db/
├── network-core/           # source code lõi (mục 4)
├── web-store/              # hoặc repo riêng
└── ops/                    # script sao lưu, cập nhật, deploy
```

- **Mật khẩu** (DB, Redis, secret của Velocity): để trong `.env`. **Không commit.**

---

## 6. Docker Compose

### Nguyên tắc
- **Mỗi thành phần một file `compose.yaml`** (infra, proxy, lobby, từng chế độ). File gốc chỉ `include` các file đó. **Thêm chế độ = thêm 1 thư mục + 1 dòng include.**
- **Chỉ proxy mở cổng ra ngoài** (25565/tcp cho Java, 19132/udp cho Bedrock). Lobby, các chế độ, MariaDB, Redis chỉ nói chuyện với nhau trong mạng nội bộ của Compose, gọi nhau bằng **tên service** (`mariadb`, `redis`, `lobby`, `banghoi-1`…).
- **Cấu hình nằm trong Git** (`config/`, `plugins/`, mount chỉ đọc). **Dữ liệu chạy nằm trong `data/`** (không commit, có sao lưu).
- **Mật khẩu nằm trong `.env`** (không commit). Repo chỉ có `.env.example`.
- Chế độ chưa mở hoặc đang thử dùng **`profiles`** để mặc định không chạy.
- Đặt **giới hạn RAM** cho từng service (`MEMORY` cho JVM, `mem_limit` cho container) để một chế độ không ăn hết RAM của máy.
- Image dùng chung: **`itzg/minecraft-server`** (Paper, Fabric, tải plugin/mod từ Modrinth) và **`itzg/mc-proxy`** (Velocity). Khi chạy thật nên **ghim tag cụ thể** thay vì `latest`.

### File mẫu (đã kiểm tra cú pháp bằng `docker compose config`)

`compose.yaml` (gốc)
```yaml
name: mc-network

include:
  - infra/compose.yaml
  - proxy/compose.yaml
  - lobby/compose.yaml
  - modes/banghoi/compose.yaml
  - modes/pokemon/compose.yaml   # chỉ chạy khi bật profile "pokemon"
```

`infra/compose.yaml`
```yaml
services:
  mariadb:
    image: mariadb:11.4
    restart: unless-stopped
    environment:
      MARIADB_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
      MARIADB_DATABASE: network
      MARIADB_USER: ${DB_USER}
      MARIADB_PASSWORD: ${DB_PASSWORD}
    volumes:
      - ../data/mariadb:/var/lib/mysql
      - ./db-init:/docker-entrypoint-initdb.d:ro   # tạo schema network, mode_* lần đầu
    healthcheck:
      test: ["CMD", "healthcheck.sh", "--connect", "--innodb_initialized"]
      interval: 10s
      retries: 10
    # KHÔNG mở cổng ra ngoài; các container khác gọi bằng tên "mariadb"

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD}", "--appendonly", "yes"]
    volumes:
      - ../data/redis:/data
```

`proxy/compose.yaml`
```yaml
services:
  proxy:
    image: itzg/mc-proxy
    restart: unless-stopped
    environment:
      TYPE: VELOCITY
      MEMORY: 1G
    ports:
      - "25565:25577"       # Java
      - "19132:19132/udp"   # Bedrock qua Geyser
    volumes:
      - ./config:/config:ro     # velocity.toml, cấu hình Geyser/Floodgate/LuckPerms (trong Git)
      - ./plugins:/plugins:ro   # file .jar plugin proxy
      - ../data/proxy:/server
    depends_on:
      mariadb:
        condition: service_healthy
      redis:
        condition: service_started
```

`lobby/compose.yaml`
```yaml
services:
  lobby:
    image: itzg/minecraft-server   # nên ghim tag cụ thể khi chạy thật
    restart: unless-stopped
    environment:
      EULA: "TRUE"
      TYPE: PAPER
      VERSION: ${MC_VERSION}
      MEMORY: 2G
      ONLINE_MODE: "FALSE"      # proxy lo xác thực; server con không mở cổng ra ngoài
    mem_limit: 3g
    volumes:
      - ../data/lobby:/data
      - ./config:/config:ro
      - ./plugins:/plugins:ro
    depends_on:
      mariadb:
        condition: service_healthy
```

`modes/banghoi/compose.yaml`
```yaml
services:
  banghoi-1:
    image: itzg/minecraft-server
    restart: unless-stopped
    environment:
      EULA: "TRUE"
      TYPE: PAPER
      VERSION: ${MC_VERSION}
      MEMORY: 4G
      ONLINE_MODE: "FALSE"
    mem_limit: 5g
    volumes:
      - ../../data/banghoi-1:/data
      - ./server/config:/config:ro
      - ./server/plugins:/plugins:ro
    depends_on:
      mariadb:
        condition: service_healthy
```

`modes/pokemon/compose.yaml`
```yaml
services:
  pokemon-1:
    image: itzg/minecraft-server
    profiles: ["pokemon"]       # mặc định KHÔNG chạy; bật bằng --profile pokemon
    restart: unless-stopped
    environment:
      EULA: "TRUE"
      TYPE: FABRIC
      VERSION: ${POKEMON_MC_VERSION}   # khóa theo phiên bản Cobblemon hỗ trợ
      MEMORY: 8G
      ONLINE_MODE: "FALSE"
      MODRINTH_PROJECTS: |
        fabric-api
        cobblemon
        fabricproxy-lite
        luckperms
    mem_limit: 10g
    volumes:
      - ../../data/pokemon-1:/data
      - ./server/config:/config:ro
    depends_on:
      mariadb:
        condition: service_healthy
```

`.env.example`
```bash
# Sao chép thành .env rồi đổi mật khẩu. KHÔNG commit file .env
DB_ROOT_PASSWORD=doi-mat-khau
DB_USER=mc
DB_PASSWORD=doi-mat-khau
REDIS_PASSWORD=doi-mat-khau
MC_VERSION=1.21.4
POKEMON_MC_VERSION=1.21.1
```

> Đây là **bản khung**. Khi dựng thật còn phải: cấu hình Velocity modern forwarding (secret dùng chung giữa proxy và các server con), khai báo server con trong `velocity.toml` (hoặc để `network-core` đăng ký động), cấu hình Geyser/Floodgate, và kiểm tra lại tên biến môi trường theo tài liệu của image `itzg`.

### Lệnh thường dùng
```bash
cp .env.example .env              # lần đầu, rồi sửa mật khẩu
docker compose up -d              # chạy mọi thứ (trừ service có profile)
docker compose --profile pokemon up -d   # bật thêm chế độ Pokémon
docker compose ps                 # xem trạng thái
docker compose logs -f banghoi-1  # xem log một chế độ
docker compose restart lobby      # khởi động lại một service
docker compose pull && docker compose up -d   # cập nhật image
docker compose down               # tắt toàn bộ (dữ liệu trong data/ vẫn còn)
```

### Bổ sung sau
- **Sao lưu**: thêm service `itzg/mc-backup` (tạm dừng lưu thế giới qua RCON rồi sao lưu, hỗ trợ restic) cho mỗi server có thế giới cần giữ, cộng với `mariadb-dump` định kỳ.
- **Tunnel playit.gg** (giai đoạn chạy máy nhà): chạy agent playit dưới dạng một service trong Compose, trỏ tới `proxy:25577` (Java) và `proxy:19132` (Bedrock). Khi đó có thể bỏ phần `ports` của proxy để không mở cổng trên máy nhà. Kiểm tra image chính thức trong tài liệu playit.
- **Máy nhà chạy Windows**: dùng Docker Desktop với WSL2. Nên để thư mục dự án **bên trong WSL** (không phải ổ `C:\`) để đọc ghi file nhanh hơn.
- **Chuyển lên máy thuê**: cài Docker, `git clone`, chép `.env` và `data/` (hoặc khôi phục từ bản sao lưu), rồi `docker compose up -d`.

---

## 7. Checklist thêm một chế độ mới

1. Copy `_template-paper` hoặc `_template-fabric` thành `modes/<id>/`.
2. Sửa `modes/<id>/compose.yaml`: tên service, RAM, đường dẫn `data/`. Có thể gắn `profiles: ["<id>"]` để chạy thử riêng.
3. Thêm một dòng `- modes/<id>/compose.yaml` vào `include` của `compose.yaml` gốc.
4. Điền `mode.yml` với `status: beta`.
5. Cài plugin hoặc mod của chế độ, cấu hình kết nối DB/Redis từ template `shared/`.
6. Viết migration SQL cho schema `mode_<id>` nếu cần.
7. Chạy `docker compose up -d <service>` (hoặc `docker compose --profile <id> up -d`).
8. Cấu hình LuckPerms context cho server mới, khai báo đồ trang trí áp dụng được.
9. Lõi tự đăng ký server vào proxy và lobby tự hiện chế độ cho tester.
10. Thử kín (beta) cho đến khi ổn định.
11. Đổi `status: open` rồi thông báo ra mắt.

**Không cần sửa lobby, proxy hay chế độ khác.**

---

## 8. Chế độ Pokémon (Cobblemon): lưu ý riêng

| Vấn đề | Chi tiết và cách xử lý |
|---|---|
| **Nền tảng** | Cobblemon là **mod Fabric** (có thêm bản NeoForge), **không chạy trên Paper**. Server chế độ này chạy **Fabric** kèm Cobblemon, cùng **FabricProxy-Lite** để kết nối với Velocity |
| **Client** | Người chơi **phải cài mod**. Hãy làm **modpack đăng trên Modrinth** để cài một lần là xong (gồm Cobblemon và các mod hỗ trợ cần thiết) |
| **Bedrock (điện thoại)** | **Không vào được** chế độ này (Geyser không hiển thị được nội dung mod). Danh sách chế độ đặt `clients: [java-modded]` để ẩn với Bedrock |
| **Docker** | Dùng image `itzg/minecraft-server` với `TYPE: FABRIC`. Mod có thể tải tự động qua biến `MODRINTH_PROJECTS`. Service gắn `profiles: ["pokemon"]` để mặc định không chạy (xem mục 6) |
| **Phiên bản** | Cobblemon chỉ hỗ trợ một số phiên bản Minecraft cụ thể, nên chế độ này **bị khóa phiên bản** riêng. Proxy và lobby dùng ViaVersion để nhận nhiều phiên bản client |
| **Chuyển server** | Chuyển client có mod từ lobby Paper sang server Fabric qua Velocity **cần thử kỹ** (đồng bộ dữ liệu mod khi chuyển server). **Phương án dự phòng**: tạo địa chỉ vào riêng (ví dụ `pokemon.tenserver.vn`) đi thẳng vào server Pokémon qua proxy, vẫn dùng chung tài khoản, rank và đồ trang trí |
| **Plugin tương đương trên Fabric** | LuckPerms (bản Fabric), **Ledger** (ghi log và khôi phục, thay CoreProtect), mod quản lý vùng đất và kinh tế cho Fabric. Phần giao hàng và đồ trang trí dùng `core-fabric` |
| **Tài nguyên** | Server có mod nặng hơn, nên dành khoảng **6–10 GB RAM** riêng cho chế độ này |
| **⚠️ Bản quyền** | Pokémon là thương hiệu của Nintendo và The Pokémon Company. **Không bán gì liên quan Pokémon** (Pokémon, bóng bắt, shiny, hộp quà). Rank và đồ trang trí network vẫn dùng chung, nhưng **không quảng bá kiếm tiền bằng Pokémon**. Nếu server lớn, rủi ro bị yêu cầu gỡ bỏ là có thật |

---

## 9. Thứ tự triển khai khung

1. **Hạ tầng tối thiểu bằng Docker Compose**: `infra` (MariaDB, Redis), `proxy` (Velocity + Geyser/Floodgate), `lobby` (Paper), LuckPerms chế độ MySQL.
2. **network-core phiên bản 1**: `core-api`, `core-common`, `core-velocity`, `core-paper` với danh sách chế độ, hồ sơ người chơi, menu lobby tự sinh.
3. **Chế độ 1: Bang hội chiến**, khai báo qua `mode.yml`.
4. **Giao hàng và đồ trang trí** (khi bắt đầu làm web store).
5. **`_template-paper`** được rút ra từ chế độ Bang hội chiến.
6. Khi làm Pokémon: viết `core-fabric`, tạo `_template-fabric`, modpack, rồi thử chuyển server. Nếu không ổn thì dùng địa chỉ vào riêng.

> Nên thử sớm một server Fabric "trống" gắn vào proxy, ngay từ giai đoạn dựng khung, để chắc chắn kiến trúc chạy được với cả Paper lẫn Fabric trước khi viết nhiều code.
