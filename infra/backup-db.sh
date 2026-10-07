#!/bin/bash
# Định kỳ dump toàn bộ MariaDB ra backups/mariadb/*.sql.gz, xoá bản cũ hơn KEEP_DAYS ngày.
set -euo pipefail
mkdir -p /backups/mariadb
export MYSQL_PWD="${MARIADB_ROOT_PASSWORD}"   # không để mật khẩu lộ trên dòng lệnh

until mariadb-admin -h mariadb -uroot ping --silent; do sleep 5; done

while true; do
  f="/backups/mariadb/all-$(date +%Y%m%d-%H%M).sql.gz"
  if mariadb-dump -h mariadb -uroot --all-databases --single-transaction --routines --events --triggers \
      | gzip > "$f.tmp"; then
    mv "$f.tmp" "$f"
    echo "$(date '+%F %T') Đã sao lưu $f ($(du -h "$f" | cut -f1))"
  else
    rm -f "$f.tmp"
    echo "$(date '+%F %T') LỖI: sao lưu MariaDB thất bại" >&2
  fi
  find /backups/mariadb -name 'all-*.sql.gz' -mtime +"${KEEP_DAYS:-14}" -delete
  sleep "${INTERVAL:-6h}"
done
