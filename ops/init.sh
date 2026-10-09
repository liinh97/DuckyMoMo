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
    -e "s/^AUTHME_SECRET=.*/AUTHME_SECRET=$(rand 64)/" \
    -e "s/^PANEL_PASSWORD=.*/PANEL_PASSWORD=$(rand 20)/" \
    -e "s/^RCON_PASSWORD=.*/RCON_PASSWORD=$(rand 32)/" \
    .env.example > .env
  chmod 600 .env
  echo "Đã tạo .env với mật khẩu ngẫu nhiên."
fi

# Biến thêm sau này: bổ sung vào .env cũ nếu còn thiếu
if ! grep -q '^AUTHME_SECRET=' .env; then
  printf '\n# AuthMe (thêm bởi ops/init.sh)\nAUTHME_SECRET=%s\n' "$(rand 64)" >> .env
  echo "Đã thêm AUTHME_SECRET vào .env."
fi

if ! grep -q '^PANEL_PASSWORD=' .env; then
  printf '\n# Trang Cài đặt (thêm bởi ops/init.sh)\nPANEL_USER=admin\nPANEL_PASSWORD=%s\n' "$(rand 20)" >> .env
  echo "Đã thêm PANEL_USER/PANEL_PASSWORD vào .env (đăng nhập trang Cài đặt)."
fi

if ! grep -q '^RCON_PASSWORD=' .env; then
  printf '\n# RCON dùng chung (thêm bởi ops/init.sh)\nRCON_PASSWORD=%s\n' "$(rand 32)" >> .env
  echo "Đã thêm RCON_PASSWORD vào .env."
fi

mkdir -p data
echo "Xong. Chạy tiếp:  docker compose up -d   rồi   docker compose logs -f"
