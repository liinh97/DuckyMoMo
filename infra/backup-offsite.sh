#!/bin/sh
# Sao lưu NGOÀI MÁY (19/10): đẩy thư mục sao lưu (BACKUP_DIR: kho restic của từng server + bản dump MariaDB) lên đám mây bằng rclone.
# Kho restic đã MÃ HOÁ bằng RESTIC_PASSWORD → nhà cung cấp đám mây không đọc được. Bản dump db-*.sql.gz thì CHƯA mã hoá:
# chỉ đẩy khi OFFSITE_DB=1 (mặc định 1, nằm trong thư mục riêng tư của tài khoản đám mây của bạn).
# Chống phá hoại: dùng "sync --backup-dir": file bị xoá/ghi đè ở máy nhà KHÔNG mất ngay trên mây mà chuyển vào _da-xoa/<ngày>,
# giữ OFFSITE_KEEP_DAYS ngày mới xoá hẳn → kẻ phá xoá sạch máy nhà thì bản trên mây vẫn còn.
# Cấu hình tài khoản: ./ops/saoluu-ngoai.sh (tạo data/rclone/rclone.conf, KHÔNG commit).
set -u
REMOTE=${OFFSITE_REMOTE:-}
INTERVAL=${OFFSITE_INTERVAL:-24h}
KEEP=${OFFSITE_KEEP_DAYS:-30}
CONF=/config/rclone/rclone.conf
secs() { case "$1" in *h) echo $(( ${1%h} * 3600 ));; *m) echo $(( ${1%m} * 60 ));; *) echo "$1";; esac; }
sleep "${OFFSITE_DELAY:-600}"
while :; do
  if [ -z "$REMOTE" ] || [ ! -s "$CONF" ]; then
    echo "$(date '+%F %T') Chưa cấu hình sao lưu ngoài máy (OFFSITE_REMOTE trong .env + data/rclone/rclone.conf). Chạy ./ops/saoluu-ngoai.sh"
  else
    day=$(date +%F)
    echo "$(date '+%F %T') Bắt đầu đẩy lên $REMOTE"
    ok=1
    for d in /backups/*/; do
      n=$(basename "$d")
      [ "$n" = db ] && [ "${OFFSITE_DB:-1}" != 1 ] && continue
      rclone sync "$d" "$REMOTE/$n" --backup-dir "$REMOTE/_da-xoa/$day/$n" \
        --exclude ".mc-backup-lock" --exclude "*.tar.gz" --transfers 4 --checkers 8 \
        --retries 5 --low-level-retries 20 --stats-one-line --stats 0 2>&1 | tail -n 3 || ok=0
    done
    rclone delete "$REMOTE/_da-xoa" --min-age "${KEEP}d" --rmdirs 2>/dev/null
    used=$(rclone size "$REMOTE" --json 2>/dev/null | sed -n 's/.*"bytes":\([0-9]*\).*/\1/p')
    echo "$(date '+%F %T') Xong (ok=$ok), trên mây: $(( ${used:-0} / 1048576 )) MB. Lần sau sau $INTERVAL"
    date +%s > /backups/.offsite-last 2>/dev/null
  fi
  sleep "$(secs "$INTERVAL")"
done
