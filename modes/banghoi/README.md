# Chế độ: Bang Hội Chiến

Factions kiểu mới: lập bang, chiếm lãnh thổ, **công thành theo lịch**, chạy theo mùa, combat kiểu 1.8.
Dùng **Towny + SiegeWar**.
Luật chơi (bản nháp): **`docs/banghoi-design.md`**. Định hướng chung: `docs/minecraft-server-plan.md` (mục 3).

## Nền tảng: Towny + SiegeWar (đã chốt 07/10/2026)
- **Towny**: lập thị trấn (town), gộp thành quốc gia (nation) = **bang**, chiếm đất theo ô (plot), thuế và phí duy trì.
- **SiegeWar**: công thành. Bang tấn công dựng cờ, hai bên giành điểm trong vùng quanh cờ, thắng thì chiếm hoặc cướp thị trấn.
  Chỉ diễn ra trong **khung giờ chiến** (cấu hình được), nên không ai bị đánh lúc offline. Khớp với ý tưởng "công thành theo lịch".
- Luật chi tiết (giờ trận, mùa giải, chống lạm dụng, cấu hình SiegeWar đề xuất): `docs/banghoi-design.md`.

## Plugin
| Plugin | Vai trò | Cách cài | Trạng thái |
|---|---|---|---|
| LuckPerms | Phân quyền | Modrinth (tự động) | ✅ |
| WorldEdit | Dựng công trình | Modrinth (tự động) | ✅ |
| Chunky | Tạo trước bản đồ | Modrinth (tự động) | ✅ |
| **Towny** | Thị trấn, bang, đất | Xem bên dưới | ⏳ |
| **SiegeWar** | Công thành theo giờ | Xem bên dưới. Cần **Towny 0.101.2.5 trở lên**; xem trang release của SiegeWar để chọn bản khớp. Cài xong chạy `/swa install` | ⏳ |
| Vault (hoặc VaultUnlocked) | Cầu nối tiền tệ, Towny cần để thu thuế, phí | Xem bên dưới | ⏳ |
| EssentialsX | Lệnh cơ bản + **tiền tệ** (Towny dùng qua Vault) | Xem bên dưới | ⏳ |
| CoreProtect | Ghi lại và khôi phục phá hoại (**bắt buộc** trước khi mở) | Xem bên dưới | ⏳ |
| WorldGuard | Bảo vệ khu spawn | Xem bên dưới | ⏳ |
| OldCombatMechanics | Combat kiểu 1.8 | Xem bên dưới | ⏳ |

### Cách cài các plugin ⏳
Môi trường dựng khung không vào được Modrinth nên **chưa kiểm tra được slug**. Trên máy nhà:
1. Tìm plugin trên https://modrinth.com/plugins. Nếu có bản cho Paper, lấy slug trong URL
   (ví dụ `modrinth.com/plugin/towny` thì slug là `towny`) rồi thêm vào `MODRINTH_PROJECTS` trong `compose.yaml`.
   Slug sai thì container sẽ báo lỗi khi khởi động, xem `docker compose logs banghoi-1`.
2. Không có trên Modrinth: tải `.jar` (GitHub releases, SpigotMC, Hangar) và đặt vào `server/plugins/`.
   File `.jar` không được commit (xem `.gitignore`), nên ghi lại nguồn và phiên bản vào bảng trên.
- Towny: https://github.com/TownyAdvanced/Towny/releases
- SiegeWar: https://github.com/TownyAdvanced/SiegeWar/releases

Sau lần chạy đầu, chép các file cấu hình cần đổi (ví dụ `data/banghoi-1/plugins/Towny/settings/config.yml`,
`data/banghoi-1/plugins/SiegeWar/config.yml`) về `server/plugins/` để lưu vào Git. Mật khẩu thì thay bằng `${CFG_...}`.

### Lưu ý khi cho crack vào
- UUID người chơi do LibreLogin trên proxy quyết định (xem `proxy/plugins/README.md`), Towny lưu theo UUID đó.
- Người chơi đổi tên với tài khoản crack sẽ thành người mới. Cần hướng dẫn người chơi không đổi tên.

## Dữ liệu
- Thế giới và plugin: `data/banghoi-1/`
- Database riêng: `mode_banghoi`
