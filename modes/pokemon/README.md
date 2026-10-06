# Chế độ: Pokémon (Cobblemon) - CHƯA MỞ

- Chạy **Fabric** + Cobblemon, kết nối Velocity bằng **FabricProxy-Lite**.
- Người chơi **phải cài modpack** (sẽ đăng trên Modrinth). Người chơi điện thoại (Bedrock) **không vào được**.
- Mặc định không chạy. Bật thử:
  1. Bỏ comment dòng `pokemon-1 = "pokemon-1:25565"` trong `proxy/config/velocity.toml`.
  2. `docker compose --profile pokemon up -d`
- **Cần thử kỹ** việc chuyển người chơi từ lobby (Paper) sang server này. Nếu lỗi, dùng tên miền riêng
  (`[forced-hosts]` trong `velocity.toml`) để vào thẳng.
- LuckPerms bản Fabric dùng file cấu hình khác (`config/luckperms/luckperms.conf`), **chưa cấu hình** kết nối MariaDB.
- ⚠️ Bản quyền Pokémon: **không bán** bất cứ thứ gì liên quan Pokémon. Xem `docs/network-architecture.md` mục 8.
