#!/bin/bash
# Voice chat (Simple Voice Chat) cho DuckyMoMo. Chạy ở máy nhà, thư mục gốc repo.
#   ./ops/voicechat.sh          tải bản Velocity mới nhất từ Modrinth vào proxy/plugins/ (jar không commit)
#   ./ops/voicechat.sh vps      mở UDP 24454 trên VPS Vinahost (DNAT + ufw), KHÔNG khởi động lại WireGuard (người đang chơi không bị văng)
# Server con (lobby, banghoi-1, practice-1) tự tải plugin qua MODRINTH_PROJECTS: simple-voice-chat.
# Sau đó (kiểm tra không ai online, khởi động lại proxy làm MỌI người văng):
#   docker compose up -d --no-deps proxy && docker compose --profile tunnel up -d --build --force-recreate playit relay   (relay có luật mới cho UDP 24454)
#   docker compose up -d --no-deps lobby banghoi-1 practice-1
set -euo pipefail
cd "$(dirname "$0")/.."
VPS=${VPS:-root@125.212.218.74}
KEY=${KEY:-$HOME/.ssh/duckymomo_vps}

if [ "${1:-}" = "vps" ]; then
  echo "Mở UDP 24454 trên $VPS…"
  scp -i "$KEY" relay/vps/relay-up.sh "$VPS:/etc/wireguard/relay-up.sh"
  ssh -i "$KEY" "$VPS" 'chmod 700 /etc/wireguard/relay-up.sh
    iptables -t nat -C MC_RELAY -p udp --dport 24454 -j DNAT --to-destination 10.66.0.2:24454 2>/dev/null \
      || iptables -t nat -A MC_RELAY -p udp --dport 24454 -j DNAT --to-destination 10.66.0.2:24454
    if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
      ufw route allow proto udp to 10.66.0.2 port 24454 >/dev/null && echo "ufw: đã cho chuyển tiếp UDP 24454"
    fi
    iptables -t nat -S MC_RELAY'
  echo "Xong. relay-up.sh trên VPS đã có dòng 24454 (giữ được sau khi VPS khởi động lại)."
  exit 0
fi

echo "Tìm bản Simple Voice Chat cho Velocity trên Modrinth…"
URL=$(curl -fsS 'https://api.modrinth.com/v2/project/simple-voice-chat/version?loaders=%5B%22velocity%22%5D' | python3 -c '
import json, sys
v = json.load(sys.stdin)
if not v:
    sys.exit("Không thấy bản Velocity nào")
f = next((f for f in v[0]["files"] if f.get("primary")), v[0]["files"][0])
print(v[0]["version_number"], f["url"], file=sys.stderr)
print(f["url"])')
rm -f proxy/plugins/voicechat-velocity*.jar
curl -fsSL "$URL" -o proxy/plugins/voicechat-velocity.jar
echo "Đã tải: proxy/plugins/voicechat-velocity.jar ($(du -h proxy/plugins/voicechat-velocity.jar | cut -f1))"
echo "Tiếp theo: ./ops/voicechat.sh vps  rồi khởi động lại proxy + 3 server khi không ai online (xem đầu file)."
