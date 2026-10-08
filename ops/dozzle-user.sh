#!/usr/bin/env bash
# Tạo (hoặc đổi mật khẩu) tài khoản đăng nhập Dozzle, dashboard xem log ở http://localhost:8888
# Chạy từ thư mục gốc repo:  ./ops/dozzle-user.sh <ten-dang-nhap>
# Mật khẩu gõ ẩn, không nằm trong lịch sử lệnh; chỉ lưu dạng băm trong data/dozzle/users.yml.
set -euo pipefail
cd "$(dirname "$0")/.."

user="${1:?Cách dùng: ./ops/dozzle-user.sh <ten-dang-nhap>}"
read -rsp "Mật khẩu cho $user (ít nhất 8 ký tự): " p1; echo
read -rsp "Nhập lại mật khẩu: " p2; echo
[ "$p1" = "$p2" ] || { echo "Hai lần nhập không khớp." >&2; exit 1; }
[ "${#p1}" -ge 8 ] || { echo "Mật khẩu phải có ít nhất 8 ký tự." >&2; exit 1; }

mkdir -p data/dozzle
# data/dozzle do container (root) tạo, nên ghi file qua một container nhỏ
docker run --rm amir20/dozzle:latest generate "$user" --password "$p1" \
  | docker run --rm -i -v "$PWD/data/dozzle:/data" alpine sh -c 'cat > /data/users.yml && chmod 600 /data/users.yml'
unset p1 p2

docker compose up -d dozzle
echo "Xong. Mở http://localhost:8888 và đăng nhập bằng tài khoản $user."
