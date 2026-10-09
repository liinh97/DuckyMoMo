#!/bin/sh
# Sao lưu toàn bộ MariaDB (tài khoản AuthMe, quyền LuckPerms, dữ liệu network, dữ liệu các chế độ) theo chu kỳ.
# Chạy trong service backup-db (infra/compose.yaml). File: /backups/db-YYYYmmdd-HHMMSS.sql.gz
# Khôi phục: xem README.md, mục "Sao lưu và khôi phục".
set -eu
INTERVAL="${BACKUP_INTERVAL:-6h}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"
mkdir -p /backups
echo "Sao lưu database mỗi $INTERVAL, giữ $KEEP_DAYS ngày"
while :; do
  f="/backups/db-$(date +%Y%m%d-%H%M%S).sql"
  # MYSQL_PWD (đặt trong compose) là mật khẩu root, không hiện trên dòng lệnh
  if mariadb-dump -h mariadb -uroot --all-databases --single-transaction --routines --events > "$f.tmp" \
     && gzip -c "$f.tmp" > "$f.gz.tmp" && mv "$f.gz.tmp" "$f.gz"; then
    echo "$(date '+%F %T') Đã sao lưu: $(basename "$f.gz") ($(du -h "$f.gz" | cut -f1))"
  else
    echo "$(date '+%F %T') LỖI: sao lưu database thất bại" >&2
    rm -f "$f.gz.tmp"
  fi
  rm -f "$f.tmp"
  find /backups -name 'db-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
  sleep "$INTERVAL"
done
