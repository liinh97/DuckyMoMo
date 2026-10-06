# Chế độ: Bang Hội Chiến

Factions kiểu mới: lập bang, chiếm lãnh thổ, **công thành theo lịch**, chạy theo mùa, combat kiểu 1.8.
Thiết kế chi tiết: xem `docs/minecraft-server-plan.md` (mục 3).

## Plugin
| Plugin | Cách cài | Trạng thái |
|---|---|---|
| LuckPerms | Modrinth (tự động) | ✅ |
| WorldEdit | Modrinth (tự động) | ✅ |
| Chunky | Modrinth (tự động) | ✅ |
| Towny + SiegeWar | Kiểm tra trên Modrinth, nếu không có thì đặt `.jar` vào `server/plugins/` | ⏳ |
| WorldGuard | Như trên | ⏳ |
| CoreProtect | Như trên (**bắt buộc** trước khi mở cho người chơi) | ⏳ |
| EssentialsX | Như trên | ⏳ |
| OldCombatMechanics | Như trên | ⏳ |

Thêm plugin có trên Modrinth: thêm slug vào `MODRINTH_PROJECTS` trong `compose.yaml`.

## Dữ liệu
- Thế giới và plugin: `data/banghoi-1/`
- Database riêng: `mode_banghoi`
