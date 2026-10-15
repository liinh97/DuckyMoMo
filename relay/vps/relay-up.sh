#!/bin/sh
# Chuyển người chơi về proxy ở nhà qua WireGuard, KHÔNG đổi IP nguồn (server thấy IP thật).
#   Java    TCP 25565 (+ 25570 cũ thời BKNS) → 10.66.0.2:25577 (Velocity)
#   Bedrock UDP 19132 (+ 19130 cũ thời BKNS) → 10.66.0.2:19132 (Geyser)
#   Vote    TCP 8192 → 10.66.0.2:8192 (relay socat → banghoi-1 Votifier)
H=10.66.0.2
iptables -t nat -N MC_RELAY 2>/dev/null; iptables -t nat -F MC_RELAY
iptables -t nat -A MC_RELAY -p tcp -m multiport --dports 25565,25570 -j DNAT --to-destination $H:25577
iptables -t nat -A MC_RELAY -p udp -m multiport --dports 19132,19130 -j DNAT --to-destination $H:19132
iptables -t nat -A MC_RELAY -p tcp --dport 8192 -j DNAT --to-destination $H:8192
iptables -t nat -C PREROUTING -i eth0 -j MC_RELAY 2>/dev/null || iptables -t nat -A PREROUTING -i eth0 -j MC_RELAY
iptables -t mangle -C FORWARD -o wg0 -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --clamp-mss-to-pmtu 2>/dev/null \
  || iptables -t mangle -A FORWARD -o wg0 -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --clamp-mss-to-pmtu
