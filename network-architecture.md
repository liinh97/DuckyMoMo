# Thiết kế khung network: thêm chế độ mới dễ dàng

> Bổ sung cho `minecraft-server-plan.md`. Mục tiêu: **thêm một chế độ mới (kể cả Pokémon/Cobblemon) mà không phải sửa lobby, proxy hay các chế độ cũ.**

---

## 1. Nguyên tắc cốt lõi

1. **Mỗi chế độ là một "module" độc lập**: có server riêng, thế giới riêng, dữ liệu riêng, kinh tế riêng. Gắn vào hay gỡ ra không ảnh hưởng chế độ khác.
2. **Danh sách chế độ khai báo bằng dữ liệu, không viết cứng trong code**: lobby và proxy **đọc danh sách chế độ (mode registry)** để hiện menu và chuyển người chơi. Thêm chế độ chỉ là thêm một dòng vào danh sách, không sửa code lobby.
3. **Dịch vụ chung nằm ở "lõi network"**: tài khoản, rank, đồ trang trí, party, chat, xử phạt, giao hàng từ web store. Các chế độ chỉ **dùng** những dịch vụ này.
4. **Không phụ thuộc nền tảng**: lõi được viết tách thành phần logic chung và các phần chuyển đổi (adapter) cho Paper, Fabric, Velocity. Nhờ vậy chế độ chạy Fabric (Pokémon) vẫn dùng chung được rank, đồ trang trí và đơn hàng.
5. **Giao tiếp giữa các server qua Redis và database**, không gọi trực tiếp vào nhau.

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
    address: 10.0.0.12:25570
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
    address: 10.0.0.11:25566
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
├── proxy/                  # velocity.toml, plugin proxy, cấu hình Geyser/Floodgate
├── shared/                 # cấu hình dùng chung (LuckPerms storage, kết nối DB/Redis) dạng template
├── lobby/
├── modes/
│   ├── _template-paper/    # khuôn mẫu cho chế độ Paper mới
│   ├── _template-fabric/   # khuôn mẫu cho chế độ Fabric (mod) mới
│   ├── banghoi/
│   │   ├── mode.yml
│   │   ├── server/         # server.properties, paper config, danh sách plugin + cấu hình
│   │   └── db/             # migration SQL cho schema mode_banghoi
│   └── pokemon/
│       ├── mode.yml
│       ├── server/         # fabric config, danh sách mod
│       ├── modpack/        # định nghĩa modpack cho client (đăng lên Modrinth)
│       └── db/
├── network-core/           # source code lõi (mục 4)
├── web-store/              # hoặc repo riêng
└── ops/                    # docker-compose / Pterodactyl eggs, script sao lưu, script deploy
```

- **Mật khẩu** (DB, Redis, token): để trong `.env` hoặc biến môi trường. **Không commit.**
- Mỗi chế độ có **Pterodactyl egg** tương ứng (Paper hoặc Fabric), nên tạo server mới chỉ mất vài click.

---

## 6. Checklist thêm một chế độ mới

1. Copy `_template-paper` hoặc `_template-fabric` thành `modes/<id>/`.
2. Điền `mode.yml` với `status: beta`.
3. Cài plugin hoặc mod của chế độ, cấu hình kết nối DB/Redis từ template `shared/`.
4. Viết migration SQL cho schema `mode_<id>` nếu cần.
5. Tạo server trên Pterodactyl bằng egg tương ứng.
6. Cấu hình LuckPerms context cho server mới, khai báo đồ trang trí áp dụng được.
7. Lõi tự đăng ký server vào proxy và lobby tự hiện chế độ cho tester.
8. Thử kín (beta) cho đến khi ổn định.
9. Đổi `status: open` rồi thông báo ra mắt.

**Không cần sửa lobby, proxy hay chế độ khác.**

---

## 7. Chế độ Pokémon (Cobblemon): lưu ý riêng

| Vấn đề | Chi tiết và cách xử lý |
|---|---|
| **Nền tảng** | Cobblemon là **mod Fabric** (có thêm bản NeoForge), **không chạy trên Paper**. Server chế độ này chạy **Fabric** kèm Cobblemon, cùng **FabricProxy-Lite** để kết nối với Velocity |
| **Client** | Người chơi **phải cài mod**. Hãy làm **modpack đăng trên Modrinth** để cài một lần là xong (gồm Cobblemon và các mod hỗ trợ cần thiết) |
| **Bedrock (điện thoại)** | **Không vào được** chế độ này (Geyser không hiển thị được nội dung mod). Danh sách chế độ đặt `clients: [java-modded]` để ẩn với Bedrock |
| **Phiên bản** | Cobblemon chỉ hỗ trợ một số phiên bản Minecraft cụ thể, nên chế độ này **bị khóa phiên bản** riêng. Proxy và lobby dùng ViaVersion để nhận nhiều phiên bản client |
| **Chuyển server** | Chuyển client có mod từ lobby Paper sang server Fabric qua Velocity **cần thử kỹ** (đồng bộ dữ liệu mod khi chuyển server). **Phương án dự phòng**: tạo địa chỉ vào riêng (ví dụ `pokemon.tenserver.vn`) đi thẳng vào server Pokémon qua proxy, vẫn dùng chung tài khoản, rank và đồ trang trí |
| **Plugin tương đương trên Fabric** | LuckPerms (bản Fabric), **Ledger** (ghi log và khôi phục, thay CoreProtect), mod quản lý vùng đất và kinh tế cho Fabric. Phần giao hàng và đồ trang trí dùng `core-fabric` |
| **Tài nguyên** | Server có mod nặng hơn, nên dành khoảng **6–10 GB RAM** riêng cho chế độ này |
| **⚠️ Bản quyền** | Pokémon là thương hiệu của Nintendo và The Pokémon Company. **Không bán gì liên quan Pokémon** (Pokémon, bóng bắt, shiny, hộp quà). Rank và đồ trang trí network vẫn dùng chung, nhưng **không quảng bá kiếm tiền bằng Pokémon**. Nếu server lớn, rủi ro bị yêu cầu gỡ bỏ là có thật |

---

## 8. Thứ tự triển khai khung

1. **Hạ tầng tối thiểu**: Velocity + Geyser/Floodgate, MariaDB, Redis, LuckPerms (chế độ MySQL), lobby Paper.
2. **network-core phiên bản 1**: `core-api`, `core-common`, `core-velocity`, `core-paper` với danh sách chế độ, hồ sơ người chơi, menu lobby tự sinh.
3. **Chế độ 1: Bang hội chiến**, khai báo qua `mode.yml`.
4. **Giao hàng và đồ trang trí** (khi bắt đầu làm web store).
5. **`_template-paper`** được rút ra từ chế độ Bang hội chiến.
6. Khi làm Pokémon: viết `core-fabric`, tạo `_template-fabric`, modpack, rồi thử chuyển server. Nếu không ổn thì dùng địa chỉ vào riêng.

> Nên thử sớm một server Fabric "trống" gắn vào proxy, ngay từ giai đoạn dựng khung, để chắc chắn kiến trúc chạy được với cả Paper lẫn Fabric trước khi viết nhiều code.
