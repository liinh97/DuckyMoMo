#!/usr/bin/env bash
# Tắt an toàn mọi container trước khi tắt máy (25/10: chủ server tắt Windows bằng Win+X → U → U, WSL bị đóng ngang ->
# server Minecraft không kịp lưu, mất 9 NPC Citizens đặt sau lần lưu cuối). Gọi từ ops/windows/tat-may-an-toan.ps1.
#
# Dùng:  ./ops/tat-an-toan.sh --kiem-tra   chỉ in ai đang online (thoát 2 nếu có người)
#        ./ops/tat-an-toan.sh               báo trong game, lưu world, tắt server Minecraft (chờ tối đa 90 giây mỗi cái), rồi tắt
#                                           mọi container còn lại (DB, web, tram6...) cho gọn
# "docker stop" làm restart: unless-stopped KHÔNG tự bật lại khi mở máy -> ghi danh sách vào data/ops/tat-an-toan.list,
# lần đăng nhập Windows sau tác vụ "DuckyMoMo tu sua" (ops/tu-sua-sau-khoi-dong.sh) docker start lại đúng danh sách đó.
set -uo pipefail
cd "$(dirname "$0")/.."

MC="duckymomo-banghoi-1-1 duckymomo-lobby-1 duckymomo-practice-1-1 duckymomo-sangtao-1-1"
online() {
  local tong=0
  for c in $MC; do
    docker ps --format '{{.Names}}' | grep -qx "$c" || continue
    local out n
    out=$(docker exec "$c" rcon-cli list 2>/dev/null | sed 's/\x1b\[[0-9;]*m//g' | tr -s '\n' ' ')
    n=$(echo "$out" | grep -oE '[0-9]+' | head -1)
    n=${n:-0}
    if [ "$n" -gt 0 ]; then
      echo "  ${c#duckymomo-}: $out"
      tong=$((tong + n))
    fi
  done
  return $([ "$tong" -gt 0 ] && echo 2 || echo 0)
}

if [ "${1:-}" = "--kiem-tra" ]; then
  online && echo "Không ai online."
  exit $?
fi

mkdir -p data/ops
docker ps --format '{{.Names}}' > data/ops/tat-an-toan.list
for c in $MC; do
  docker ps --format '{{.Names}}' | grep -qx "$c" || continue
  docker exec "$c" rcon-cli "say Server tắt để bảo trì máy, vào lại sau ít phút." >/dev/null 2>&1
  docker exec "$c" rcon-cli "save-all flush" >/dev/null 2>&1
  docker exec "$c" rcon-cli "citizens save" >/dev/null 2>&1
done
sleep 3
echo "Đang tắt server Minecraft (lưu world, NPC)..."
# shellcheck disable=SC2086
docker stop -t 90 $MC >/dev/null 2>&1
echo "Đang tắt các container còn lại..."
docker ps -q | xargs -r docker stop -t 30 >/dev/null 2>&1
echo "Xong: $(docker ps -q | wc -l) container còn chạy."
