#!/bin/sh
iptables -t nat -D PREROUTING -i eth0 -j MC_RELAY 2>/dev/null
iptables -t nat -F MC_RELAY 2>/dev/null; iptables -t nat -X MC_RELAY 2>/dev/null
iptables -t mangle -D FORWARD -o wg0 -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --clamp-mss-to-pmtu 2>/dev/null
true
