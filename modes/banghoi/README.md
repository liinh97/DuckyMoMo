# Chế độ: Bang Hội Chiến

Lập thị trấn và quốc gia, chiếm lãnh thổ, **công thành theo lịch**, chạy theo mùa, combat kiểu 1.8.
Nền tảng: **Towny + SiegeWar** (chốt 08/10/2026). Thiết kế chi tiết: `docs/banghoi-design.md`.

## Plugin
| Plugin | Cách cài | Trạng thái |
|---|---|---|
| LuckPerms | Modrinth (tự động) | ✅ |
| WorldEdit | Modrinth (tự động) | ✅ |
| Chunky | Modrinth (tự động) | ✅ |
| Towny | Modrinth `towny` | ✅ 0.103.2.0 |
| SiegeWar | GitHub release (link ghim trong `PLUGINS`), đã chạy `/swa install` | ✅ 3.7.0 |
| VaultUnlocked | Modrinth `vaultunlocked` | ✅ |
| WorldGuard | Modrinth | ✅ 7.0.19 |
| CoreProtect | Modrinth, **bắt buộc** trước khi mở cho người chơi | ✅ 24.1 |
| EssentialsX (+ Chat, Spawn) | Bản dev #1832 từ ci.ender.zone (bản chính thức mới tới 26.1.2) | ✅ 2.22.1-dev |
| OldCombatMechanics | GitHub release (link ghim trong `PLUGINS`) | ✅ 2.7.0 |

## Cài đặt
Chỉnh trên trang Cài đặt **http://localhost:8889** (bấm Lưu là áp dụng): lịch công thành, luật công thành,
phí thị trấn/quốc gia, kiểu combat, viền thế giới, loại map. Định nghĩa: `settings.schema.yml`, giá trị: `settings.yml`.

Thêm plugin có trên Modrinth: thêm slug vào `MODRINTH_PROJECTS` trong `compose.yaml`.

## Dữ liệu
- Thế giới và plugin: `data/banghoi-1/`
- Database riêng: `mode_banghoi`
