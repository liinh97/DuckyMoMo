#!/bin/bash
# Bedrock NetherNet (19/10): từ Bedrock 26.60 (27/10/2026) điện thoại KHÔNG vào bằng RakNet nữa, Geyser phải bật NetherNet.
#   Geyser (proxy/plugins/Geyser-Velocity/config.yml): transport both (nhận cả RakNet lẫn NetherNet), webrtc-port 19133,
#   signaling builtin trên TCP 19132, advertise-addresses = IP VPS. Người chơi vẫn nhập mc.goc6.io.vn cổng 19132.
#   ./ops/nethernet.sh vps   mở TCP 19132 + UDP 19133 trên VPS (DNAT + ufw), KHÔNG khởi động lại WireGuard (không ai bị văng)
# Sau đó (kiểm tra không ai online, khởi động lại proxy làm MỌI người văng):
#   docker compose up -d --force-recreate proxy && docker compose --profile tunnel up -d --build --force-recreate relay
set -euo pipefail
cd "$(dirname "$0")/.."
VPS=${VPS:-root@125.212.218.74}
KEY=${KEY:-$HOME/.ssh/duckymomo_vps}

if [ "${1:-}" = "vps" ]; then
  echo "Mở TCP 19132 + UDP 19133 trên $VPS…"
  scp -i "$KEY" relay/vps/relay-up.sh "$VPS:/etc/wireguard/relay-up.sh"
  ssh -i "$KEY" "$VPS" 'chmod 700 /etc/wireguard/relay-up.sh
    iptables -t nat -C MC_RELAY -p tcp --dport 19132 -j DNAT --to-destination 10.66.0.2:19132 2>/dev/null \
      || iptables -t nat -A MC_RELAY -p tcp --dport 19132 -j DNAT --to-destination 10.66.0.2:19132
    iptables -t nat -C MC_RELAY -p udp --dport 19133 -j DNAT --to-destination 10.66.0.2:19133 2>/dev/null \
      || iptables -t nat -A MC_RELAY -p udp --dport 19133 -j DNAT --to-destination 10.66.0.2:19133
    if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
      ufw route allow proto tcp to 10.66.0.2 port 19132 >/dev/null && echo "ufw: đã cho chuyển tiếp TCP 19132"
      ufw route allow proto udp to 10.66.0.2 port 19133 >/dev/null && echo "ufw: đã cho chuyển tiếp UDP 19133"
    fi
    iptables -t nat -S MC_RELAY'
  echo "Xong. relay-up.sh trên VPS đã có dòng 19132/tcp, 19133/udp (giữ được sau khi VPS khởi động lại)."
  exit 0
fi
sed -n 2,8p "$0"
