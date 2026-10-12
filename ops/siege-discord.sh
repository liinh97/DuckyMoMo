#!/bin/bash
# Bật thông báo công thành của SiegeWar lên Discord (webhook), lấy URL từ .env (DISCORD_SIEGE_WEBHOOK_URL).
# URL webhook là bí mật (ai có cũng đăng được vào kênh) nên không ghi vào Git: script sửa thẳng
# data/banghoi-1/plugins/SiegeWar/config.yml rồi tải lại SiegeWar. Để trống URL thì tắt.
# Chạy từ thư mục gốc repo:  ./ops/siege-discord.sh
set -euo pipefail
cd "$(dirname "$0")/.."
URL=$(grep -E '^DISCORD_SIEGE_WEBHOOK_URL=' .env | cut -d= -f2- || true)
CFG=data/banghoi-1/plugins/SiegeWar/config.yml
[ -f "$CFG" ] || { echo "Chưa có $CFG (chạy banghoi-1 trước)"; exit 1; }
if [ -n "$URL" ]; then ENABLED=true; else ENABLED=false; URL='https://discord.com/api/webhooks/SECRET'; fi
python3 - "$CFG" "$ENABLED" "$URL" <<'EOF'
import re, sys
path, enabled, url = sys.argv[1:4]
s = open(path, encoding="utf-8").read()
i = s.index("\ndiscord_webhook:")
head, tail = s[:i], s[i:]
tail = re.sub(r"(\n  enabled: )'[a-z]+'", lambda m: f"{m.group(1)}'{enabled}'", tail, count=1)
tail = re.sub(r"(\n  url: )\S+", lambda m: m.group(1) + url, tail, count=1)
open(path, "w", encoding="utf-8").write(head + tail)
EOF
docker exec duckymomo-banghoi-1-1 rcon-cli "siegewar reload" >/dev/null || docker exec duckymomo-banghoi-1-1 rcon-cli "swa reload" >/dev/null || true
echo "SiegeWar Discord webhook: $ENABLED"
