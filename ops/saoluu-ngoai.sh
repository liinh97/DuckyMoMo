#!/bin/bash
# Sao lưu NGOÀI MÁY (19/10). Chạy ở thư mục gốc repo.
#   ./ops/saoluu-ngoai.sh                 1) cấu hình tài khoản đám mây (hỏi đáp của rclone, lưu data/rclone/rclone.conf — KHÔNG commit)
#   ./ops/saoluu-ngoai.sh dat <remote>    2) chọn nơi lưu, vd: ./ops/saoluu-ngoai.sh dat gdrive:DuckyMoMo-saoluu  (ghi OFFSITE_REMOTE vào .env)
#   ./ops/saoluu-ngoai.sh kiemtra         3) thử ghi/đọc/xoá 1 file nhỏ trên mây
#   ./ops/saoluu-ngoai.sh chay            4) bật dịch vụ backup-offsite (đẩy lần đầu sau 10 phút, sau đó mỗi 24 giờ)
#   ./ops/saoluu-ngoai.sh xem             xem log + dung lượng trên mây
# Google Drive / OneDrive: khi rclone hỏi "Use web browser to automatically authenticate?" chọn y, mở link hiện ra bằng trình duyệt
# Windows (localhost:53682), đăng nhập, cho phép. Backblaze B2 / MEGA: nhập key / tài khoản theo hỏi đáp.
# NHỚ: cất RESTIC_PASSWORD (trong .env) ở nơi khác (trình quản lý mật khẩu, giấy) — mất máy mà không có nó thì bản trên mây vô dụng.
set -euo pipefail
cd "$(dirname "$0")/.."
IMG=rclone/rclone:1.68.2
mkdir -p data/rclone
R() { docker run --rm -it --network host -u "$(id -u):$(id -g)" -v "$PWD/data/rclone:/config/rclone" "$IMG" "$@"; }
case "${1:-}" in
  "") R config ;;
  dat)
    [ -n "${2:-}" ] || { echo "Dùng: $0 dat <tên-remote>:<thư-mục>  (tên remote xem: $0 ds)"; exit 1; }
    if grep -q '^OFFSITE_REMOTE=' .env; then sed -i "s|^OFFSITE_REMOTE=.*|OFFSITE_REMOTE=$2|" .env; else echo "OFFSITE_REMOTE=$2" >> .env; fi
    echo "Đã ghi OFFSITE_REMOTE=$2 vào .env" ;;
  ds) R listremotes ;;
  kiemtra)
    REM=$(sed -n 's/^OFFSITE_REMOTE=//p' .env); [ -n "$REM" ] || { echo "Chưa đặt: $0 dat <remote>"; exit 1; }
    echo "thu $(date)" > data/rclone/.thu.txt
    R copyto /config/rclone/.thu.txt "$REM/_kiemtra.txt" && R cat "$REM/_kiemtra.txt" && R deletefile "$REM/_kiemtra.txt" && echo "OK: ghi/đọc/xoá được trên $REM"
    rm -f data/rclone/.thu.txt ;;
  chay) docker compose --profile offsite up -d backup-offsite && echo "Đã bật. Xem: $0 xem" ;;
  xem)
    docker compose --profile offsite logs --tail 20 backup-offsite
    REM=$(sed -n 's/^OFFSITE_REMOTE=//p' .env); [ -n "$REM" ] && R size "$REM" || true ;;
  *) sed -n 2,11p "$0" ;;
esac
