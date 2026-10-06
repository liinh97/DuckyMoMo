#!/usr/bin/env bash
# Tạo chế độ Paper mới từ khuôn mẫu.
#   ./ops/new-mode.sh <id>        ví dụ: ./ops/new-mode.sh survival
# id: chữ thường, số, gạch dưới; bắt đầu bằng chữ.
set -euo pipefail
cd "$(dirname "$0")/.."

id="${1:-}"
if ! [[ "$id" =~ ^[a-z][a-z0-9_]{1,30}$ ]]; then
  echo "Cách dùng: $0 <id>   (id: chữ thường/số/_, bắt đầu bằng chữ, 2-31 ký tự)" >&2
  exit 1
fi
if [ -e "modes/$id" ]; then
  echo "modes/$id đã tồn tại." >&2
  exit 1
fi

cp -r modes/_template-paper "modes/$id"
rm -f "modes/$id/README.md"
find "modes/$id" -type f \( -name '*.yml' -o -name '*.yaml' \) \
  -exec sed -i.bak "s/__MODE_ID__/$id/g" {} \; -exec rm -f {}.bak \;

echo "  - modes/$id/compose.yaml" >> compose.yaml

cat <<MSG
Đã tạo modes/$id và thêm vào compose.yaml (đang ở chế độ thử: profile "$id").

Việc còn lại:
  1. Sửa modes/$id/mode.yml (tên hiển thị, mô tả, icon) và RAM trong modes/$id/compose.yaml.
  2. Thêm plugin vào MODRINTH_PROJECTS trong modes/$id/compose.yaml.
  3. Khai báo server trong proxy/config/velocity.toml, mục [servers]:
       $id-1 = "$id-1:25565"
  4. Tạo database riêng (nếu chế độ cần):
       docker compose exec mariadb sh -c 'mariadb -uroot -p"\$MARIADB_ROOT_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS mode_$id CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"'
     và thêm cùng dòng CREATE DATABASE vào infra/db-init/03-mode-databases.sql.
  5. Chạy thử:  docker compose --profile $id up -d $id-1
  6. Khi mở chính thức: xoá dòng "profiles" trong modes/$id/compose.yaml, đổi status trong mode.yml.
MSG
