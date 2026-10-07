#!/usr/bin/env bash
# Chuẩn bị lần đầu: tạo .env với mật khẩu ngẫu nhiên và thư mục data/.
# Chạy từ thư mục gốc repo:  ./ops/init.sh
set -euo pipefail
cd "$(dirname "$0")/.."

rand() { LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c "${1:-32}"; }

if [ -f .env ]; then
  echo ".env đã tồn tại, giữ nguyên (xoá file nếu muốn tạo lại)."
else
  sed \
    -e "s/^DB_ROOT_PASSWORD=.*/DB_ROOT_PASSWORD=$(rand 32)/" \
    -e "s/^DB_PASSWORD=.*/DB_PASSWORD=$(rand 32)/" \
    -e "s/^REDIS_PASSWORD=.*/REDIS_PASSWORD=$(rand 32)/" \
    -e "s/^VELOCITY_SECRET=.*/VELOCITY_SECRET=$(rand 48)/" \
    -e "s/^RESTIC_PASSWORD=.*/RESTIC_PASSWORD=$(rand 40)/" \
    .env.example > .env
  chmod 600 .env
  echo "Đã tạo .env với mật khẩu ngẫu nhiên."
  echo "QUAN TRỌNG: chép RESTIC_PASSWORD trong .env ra chỗ an toàn, mất nó thì không khôi phục được bản sao lưu."
fi

mkdir -p data backups
echo "Xong. Chạy tiếp:  docker compose up -d   rồi   docker compose logs -f"
