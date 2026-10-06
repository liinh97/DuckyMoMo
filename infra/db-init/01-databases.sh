#!/bin/bash
# Chạy tự động 1 lần bởi image MariaDB khi thư mục data/mariadb còn trống.
# Tạo các database dùng chung và cấp quyền cho user của network.
set -euo pipefail

mariadb -uroot -p"${MARIADB_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS \`network\`   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`luckperms\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON \`network\`.*   TO '${MARIADB_USER}'@'%';
GRANT ALL PRIVILEGES ON \`luckperms\`.* TO '${MARIADB_USER}'@'%';
-- Mỗi chế độ có database riêng tên mode_<id> (tạo trong 02-modes.sql hoặc khi thêm chế độ)
GRANT ALL PRIVILEGES ON \`mode\_%\`.* TO '${MARIADB_USER}'@'%';
FLUSH PRIVILEGES;
SQL
