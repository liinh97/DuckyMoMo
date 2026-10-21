# DuckyMoMo Network: hướng dẫn cho Claude

- Đọc `docs/HANDOFF.md` trước tiên (mục 000000000 = mới nhất 21/10 tối muộn: test bot tiếp + sửa vào Liên minh Hoà Bình; 00000000 = kết quả tự test bằng bot, dữ liệu test cần dọn, câu hỏi chờ; rồi 0000000, 000000, 00000, 0000, 000): trạng thái hiện tại, chi tiết kỹ thuật, việc tiếp theo. Việc cần test trong game: `docs/TEST-CHECKLIST.md`; kết quả lộ trình test: `docs/lo-trinh-test-ketqua.json`; test bằng bot: `tools/bottest/`.
- Trả lời người dùng bằng **tiếng Việt**, thực tế, nói rõ chỗ nào chưa chắc hoặc cần kiểm tra lại.
- Hạ tầng **chỉ dùng Docker Compose**: mỗi thành phần hoặc chế độ một `compose.yaml`, file gốc dùng `include`.
- **Không commit mật khẩu.** Mật khẩu để trong `.env`; file cấu hình dùng `${CFG_TEN_BIEN}` (image itzg tự điền).
- Kiểm tra trước khi push: `./ops/init.sh && docker compose config --quiet && docker compose --profile pokemon config --quiet`, sau đó xoá `.env` và `data/` vừa tạo khi thử. Trên máy chạy server thật thì làm trong worktree tạm (`git worktree add <thư mục tạm> HEAD`), KHÔNG chạy trong repo (`.env` và `data/` thật).
- Kiếm tiền phải đúng luật Mojang: không pay-to-win, không bán cape, không bán gì liên quan Pokémon.
- Cài plugin hoặc thêm lệnh mới thì ghi ngay vào cẩm nang `modes/_network/camnang.yml` (lệnh con vào `lenh-con.yml`; người chơi cần biết thì thêm `huongdan.yml` + `./ops/huongdan.sh`).
- Kiểm tra có ai online trước khi khởi động lại server. DuckyMoMo, tram6, goc6 phải luôn chạy. Chủ server vào game qua cổng VPS (playit đã bỏ 19/10), đừng khuyên dùng localhost. Tạo lại proxy thì phải tạo lại relay: `docker compose --profile tunnel up -d --force-recreate relay`.
- **Bài học vận hành đã gây sự cố thật** (kiểm online tách riêng, proxy/relay, sảnh không có PlaceholderAPI, trùng khoá Cài đặt, kiểm JS…): đọc `docs/HANDOFF.md` mục 000 trước khi build / khởi động lại / sửa web.
