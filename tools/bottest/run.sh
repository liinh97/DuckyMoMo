#!/bin/bash
# Chạy kịch bản test bằng bot (mineflayer) vào THẲNG banghoi-1 qua Velocity modern forwarding (không qua proxy/Sonar/AuthMe).
# Chạy trong container node cùng mạng docker (Docker Desktop không vào được IP container từ máy).
#   ./tools/bottest/run.sh vidu-capdo.js            (BOTN=TenBot ./tools/bottest/run.sh ... để đổi tên bot)
# Kịch bản dùng h.js: start(tên) -> {bot, rc (rcon), say, openMenu, click, itemText, attr, end}; moveTo(t, x, z) dịch chuyển
# phía server rồi bot vào lại (qua Via bot không theo được lệnh tp). Lưu ý: bot 1.21.4 qua ViaBackwards; đọc tin chat người chơi
# có thể lỗi (bỏ qua). Xong thì dọn: /choilai admin <tên bot> moi.
set -euo pipefail
D=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$D/../.." && pwd)
V=$(grep -m1 "secret:" "$ROOT/data/banghoi-1/config/paper-global.yml" | awk '{print $2}')
P=$(grep -m1 "^RCON_PASSWORD=" "$ROOT/.env" | cut -d= -f2-)
[ -d "$D/node_modules" ] || docker run --rm -v "$D":/bot -w /bot node:22-alpine npm install --silent
docker run --rm --network duckymomo_default -e VSECRET="$V" -e RCONPASS="$P" -e BOTN -e HOST -e KIEU -e CHI_DUNGEON -e DEBUG -e LV -e CHI_EM -e CHI_LIB -e CHI -e MOB -e LOAD -v "$D":/bot -w /bot node:22-alpine timeout 600 node "$@"
