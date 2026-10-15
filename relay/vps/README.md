# Phía VPS của cổng chuyển tiếp (Vinahost CheapSSD 1 "60880.vpsvinahost.vn", 125.212.218.74, Viettel TP.HCM, Ubuntu 24.04)

SSH: `ssh -i ~/.ssh/duckymomo_vps root@125.212.218.74` (cổng 22: Vinahost dặn giữ SSH mặc định và không đổi cấu hình mạng).
Panel: Virtualizor trong trang khách hàng Vinahost (Start/Stop, VNC, SSH Keys, Firewall). Trước đó (15/10 sáng) dùng BKNS 42.96.4.244 —
BKNS cấm chạy game, chặn 25565/19132; đã bỏ.

Cài lại từ đầu:

    apt-get install -y wireguard-tools
    umask 077; wg genkey > /etc/wireguard/vps.key; wg pubkey < /etc/wireguard/vps.key   # đổi khoá thì sửa RELAY_VPS_PUBLIC_KEY
    # chép wg0.conf, relay-up.sh, relay-down.sh vào /etc/wireguard/ (chmod 600 / 700)
    echo "net.ipv4.ip_forward = 1" > /etc/sysctl.d/99-duckymomo-relay.conf && sysctl -p /etc/sysctl.d/99-duckymomo-relay.conf
    systemctl enable --now wg-quick@wg0

SSH chỉ cho vào bằng khoá: `/etc/ssh/sshd_config.d/00-duckymomo.conf` (PasswordAuthentication no, PermitRootLogin prohibit-password).
Tường lửa ufw (bật 15/10): chặn mọi chiều vào trừ 26266/tcp (SSH), 51820/udp (WireGuard); chuyển tiếp chỉ cho
10.66.0.2:25577/tcp, 10.66.0.2:19132/udp, 10.66.0.2:8192/tcp (vote) và 10.66.0.2:24454/udp (voice, thêm bằng ./ops/voicechat.sh vps) (`ufw route allow ...`). Thêm cổng game mới thì thêm luật `ufw route allow` tương ứng.
Trạng thái: `wg show` (latest handshake), `iptables -t nat -S MC_RELAY`.
Đổi cổng game: sửa relay-up.sh rồi `systemctl restart wg-quick@wg0`.
