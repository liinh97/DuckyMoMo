#!/bin/sh
# Dựng wg0 trong mạng của proxy (network_mode: service:proxy) để người chơi vào qua VPS giữ nguyên IP thật.
#   VPS DNAT cổng game → 10.66.0.2 (wg0 ở đây), không SNAT.
#   Chiều về: chỉ gói trả lời của Velocity (tcp sport 25577) và Geyser (udp sport 19132) gửi tới IP công khai
#   mới đi bảng 66 (default dev wg0). playit (127.0.0.1), mạng Docker/LAN và mọi kết nối khác giữ đường cũ.
#   Vote (Votifier): socat 8192 → banghoi-1:8192, chiều về theo luật tcp sport 8192.
#   Luật theo cổng nguồn (không dùng connmark) để UDP của Geyser chọn đúng IP nguồn 10.66.0.2.
set -u
ADDR=${RELAY_WG_ADDRESS:-10.66.0.2/32}
TABLE=66
PREFS="100 101 102 103 104 300 301 302"
VOTIFIER=${RELAY_VOTIFIER_TARGET:-banghoi-1:8192}

cleanup() {
  [ -n "${SOCAT_PID:-}" ] && kill "$SOCAT_PID" 2>/dev/null
  ip link del wg0 2>/dev/null
  for p in $PREFS; do while ip rule del pref "$p" 2>/dev/null; do :; done; done
  ip route flush table $TABLE 2>/dev/null
}

if [ -z "${RELAY_WG_PRIVATE_KEY:-}" ] || [ -z "${RELAY_VPS_PUBLIC_KEY:-}" ] || [ -z "${RELAY_VPS_ENDPOINT:-}" ]; then
  echo "Chưa cấu hình cổng VPS (RELAY_WG_PRIVATE_KEY trong .env), relay không làm gì."
  cleanup
  exec sleep infinity
fi

cleanup
set -e
umask 077
printf '%s\n' "$RELAY_WG_PRIVATE_KEY" > /tmp/wg.key
ip link add wg0 type wireguard
wg set wg0 private-key /tmp/wg.key peer "$RELAY_VPS_PUBLIC_KEY" endpoint "$RELAY_VPS_ENDPOINT" \
  allowed-ips 0.0.0.0/0 persistent-keepalive 25
rm -f /tmp/wg.key
ip addr add "$ADDR" dev wg0
ip link set wg0 mtu 1380 up
ip route add default dev wg0 table $TABLE
# Đích nội bộ luôn đi bảng chính
ip rule add to 127.0.0.0/8 lookup main pref 100
ip rule add to 10.0.0.0/8 lookup main pref 101
ip rule add to 172.16.0.0/12 lookup main pref 102
ip rule add to 192.168.0.0/16 lookup main pref 103
ip rule add to 100.64.0.0/10 lookup main pref 104
ip rule add ipproto tcp sport 25577 lookup $TABLE pref 300
ip rule add ipproto udp sport 19132 lookup $TABLE pref 301
ip rule add ipproto tcp sport 8192 lookup $TABLE pref 302
set +e
echo "Đã dựng wg0 $ADDR → $RELAY_VPS_ENDPOINT"
# Votifier: trang vote gửi tới VPS:8192 → đây → banghoi-1:8192 (không cần IP người gửi)
socat TCP-LISTEN:8192,fork,reuseaddr TCP:"$VOTIFIER" &
SOCAT_PID=$!
echo "Chuyển vote 8192 → $VOTIFIER"

trap 'cleanup; echo "Đã gỡ wg0."; exit 0' TERM INT
# Kiểm tra mỗi phút, chỉ ghi khi trạng thái đổi (xem: docker compose logs relay)
last=""
while :; do
  hs=$(wg show wg0 latest-handshakes 2>/dev/null | awk '{print $2}')
  now=$(date +%s)
  if [ -z "$hs" ] || [ "$hs" = 0 ]; then st="chưa bắt tay được với VPS"
  elif [ $((now - hs)) -gt 180 ]; then st="MẤT kết nối VPS ($((now - hs)) giây chưa bắt tay)"
  else st="đang kết nối VPS"; fi
  [ "$st" != "$last" ] && echo "$(date '+%F %T') $st" && last=$st
  sleep 60 & wait $!
done
