# Container nhỏ chỉ để dựng đường hầm WireGuard trong mạng của proxy (iproute2 đầy đủ: busybox ip thiếu "ipproto/sport").
FROM alpine:3.20
RUN apk add --no-cache iproute2 wireguard-tools-wg socat
COPY wg-relay.sh /wg-relay.sh
ENTRYPOINT ["sh", "/wg-relay.sh"]
