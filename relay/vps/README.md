# Phía VPS của cổng chuyển tiếp (BKNS "mc-relay", Ubuntu 24.04, SSH cổng 26266, chỉ vào bằng khoá ~/.ssh/duckymomo_vps)

Cài lại từ đầu:

    apt-get install -y wireguard-tools
    umask 077; wg genkey > /etc/wireguard/vps.key; wg pubkey < /etc/wireguard/vps.key   # đổi khoá thì sửa RELAY_VPS_PUBLIC_KEY
    # chép wg0.conf, relay-up.sh, relay-down.sh vào /etc/wireguard/ (chmod 600 / 700)
    echo "net.ipv4.ip_forward = 1" > /etc/sysctl.d/99-duckymomo-relay.conf && sysctl -p /etc/sysctl.d/99-duckymomo-relay.conf
    systemctl enable --now wg-quick@wg0

SSH chỉ cho vào bằng khoá: `/etc/ssh/sshd_config.d/00-duckymomo.conf` (PasswordAuthentication no, PermitRootLogin prohibit-password).
Tường lửa ufw (bật 15/10): chặn mọi chiều vào trừ 26266/tcp (SSH), 51820/udp (WireGuard); chuyển tiếp chỉ cho
10.66.0.2:25577/tcp và 10.66.0.2:19132/udp (`ufw route allow ...`). Thêm cổng game mới thì thêm luật `ufw route allow` tương ứng.
Trạng thái: `wg show` (latest handshake), `iptables -t nat -S MC_RELAY`.
Đổi cổng game: sửa relay-up.sh rồi `systemctl restart wg-quick@wg0`.
