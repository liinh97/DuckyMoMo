# DuckyMoMo Network: hướng dẫn cho Claude

- Đọc `docs/HANDOFF.md` trước tiên: trạng thái hiện tại, chi tiết kỹ thuật, việc tiếp theo.
- Trả lời người dùng bằng **tiếng Việt**, thực tế, nói rõ chỗ nào chưa chắc hoặc cần kiểm tra lại.
- Hạ tầng **chỉ dùng Docker Compose**: mỗi thành phần hoặc chế độ một `compose.yaml`, file gốc dùng `include`.
- **Không commit mật khẩu.** Mật khẩu để trong `.env`; file cấu hình dùng `${CFG_TEN_BIEN}` (image itzg tự điền).
- Kiểm tra trước khi push: `./ops/init.sh && docker compose config --quiet && docker compose --profile pokemon config --quiet`, sau đó xoá `.env` và `data/` vừa tạo khi thử.
- Kiếm tiền phải đúng luật Mojang: không pay-to-win, không bán cape, không bán gì liên quan Pokémon.
