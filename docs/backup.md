# Sao lưu và khôi phục

Mọi bản sao lưu nằm trong thư mục **`backups/`** ở gốc repo (không commit, có trong `.gitignore`).

| Dữ liệu | Service | Cách | Tần suất | Giữ lại |
|---|---|---|---|---|
| Bang Hội Chiến (`data/banghoi-1`) | `backup-banghoi-1` | `itzg/mc-backup` + restic → `backups/restic` | Mỗi giờ khi có người chơi | 24 bản theo giờ, 14 theo ngày, 8 theo tuần |
| Lobby (`data/lobby`) | `backup-lobby` | như trên | Mỗi ngày | như trên |
| Pokémon (`data/pokemon-1`) | `backup-pokemon-1` (profile `pokemon`) | như trên | Mỗi giờ | như trên |
| Chế độ mới tạo bằng `ops/new-mode.sh` | `backup-<id>-1` | như trên (có sẵn trong khuôn mẫu) | Mỗi giờ | như trên |
| MariaDB (LuckPerms, LibreLogin, network, mode_*) | `db-backup` | `mariadb-dump` → `backups/mariadb/*.sql.gz` | 6 giờ | 14 ngày |

- `itzg/mc-backup` dùng RCON tạm dừng ghi thế giới (`save-off`, `save-all`) khi sao lưu rồi bật lại, nên không cần tắt server.
  Mật khẩu RCON do image server tự sinh, mc-backup tự đọc từ `data/<server>/.rcon-cli.env`.
- restic chỉ lưu phần thay đổi và **mã hoá** bằng `RESTIC_PASSWORD` trong `.env`.
  **Mất mật khẩu này thì không khôi phục được.** Chép ra chỗ khác ngoài máy chủ.
- Không lưu: file `.jar`, `logs`, `cache` (mặc định của mc-backup; tải lại được).

## ⚠️ Sao lưu trên cùng ổ đĩa chưa phải là sao lưu thật
Hỏng ổ, mất máy thì mất cả server lẫn bản sao lưu. Trước khi mở cho người ngoài, cần chép `backups/` ra chỗ khác:
- Đơn giản nhất: định kỳ chép `backups/` sang ổ cứng ngoài hoặc máy khác.
- Tự động: restic hỗ trợ đẩy thẳng lên cloud qua rclone (Google Drive, OneDrive, S3…), xem README của `itzg/mc-backup`, mục "Restic with rclone".
  Việc này làm sau, khi chọn được nơi lưu.

## Lệnh thường dùng
```bash
# Sao lưu ngay (ví dụ trước khi cập nhật plugin)
docker compose exec backup-banghoi-1 backup now

# Xem danh sách bản sao lưu restic
docker compose exec backup-banghoi-1 restic snapshots

# Xem các bản dump MariaDB
ls -lh backups/mariadb/
```

## Khôi phục thế giới (ví dụ banghoi-1)
```bash
docker compose stop banghoi-1 backup-banghoi-1
# Image dùng entrypoint "backup", nên chạy restic trực tiếp thì phải đổi entrypoint
docker compose run --rm --no-deps --entrypoint restic backup-banghoi-1 snapshots --tag banghoi-1   # chọn ID
mv data/banghoi-1 data/banghoi-1.hong                                # giữ lại bản đang hỏng, xoá sau
docker compose run --rm --no-deps --entrypoint restic -v "$PWD/data:/restore" backup-banghoi-1 \
  restore <ID> --target /restore/banghoi-1-restored
```
restic khôi phục cả đường dẫn gốc (`/data/...`), nên thư mục cần dùng nằm ở `data/banghoi-1-restored/data/`.
Chuyển nó về `data/banghoi-1`, rồi `docker compose up -d banghoi-1 backup-banghoi-1`.
**Lệnh khôi phục chưa chạy thử**, nên tập khôi phục một lần khi server còn ít dữ liệu.

## Khôi phục MariaDB
```bash
gunzip -c backups/mariadb/all-YYYYMMDD-HHMM.sql.gz | \
  docker compose exec -T mariadb sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"'
```
Bản dump chứa toàn bộ database. Khôi phục sẽ ghi đè dữ liệu hiện tại: tắt proxy và các server trước để không ai ghi thêm.
