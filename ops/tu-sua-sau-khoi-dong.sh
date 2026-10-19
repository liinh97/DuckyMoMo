#!/usr/bin/env bash
# Tự sửa các lỗi hay gặp sau khi Windows / WSL / Docker Desktop khởi động lại (đã gặp thật, xem docs/HANDOFF.md):
#   1. Cổng publish "chết" (container vẫn Up nhưng Windows không kết nối được): khởi động lại đúng container đó.
#      Áp dụng cho MỌI dự án đang chạy (DuckyMoMo, tram6, be_goc6...): cổng 80 chết là goc6 (qua cloudflared) cũng chết.
#   2. Thư mục bind mount bị "treo" (container thấy thư mục cũ/rỗng): tạo lại đúng container đó bằng compose của dự án.
#   3. Proxy Minecraft vừa khởi động lại/tạo lại thì tạo lại relay (cổng VPS, network_mode: service:proxy). playit đã bỏ 19/10.
# Không khởi động lại server Minecraft đang có người chơi (chỉ ghi cảnh báo).
#
# Dùng:  ./ops/tu-sua-sau-khoi-dong.sh            sửa
#        ./ops/tu-sua-sau-khoi-dong.sh --kiem-tra  chỉ kiểm tra, không sửa gì
# Tự chạy sau khi đăng nhập Windows: ops/windows/dang-ky-tu-sua.ps1 (Task Scheduler).
# Nhật ký: data/ops/tu-sua.log
set -uo pipefail
cd "$(dirname "$0")/.."
REPO="$PWD"

CHECK_ONLY=0
[ "${1:-}" = "--kiem-tra" ] && CHECK_ONLY=1

LOG_DIR="$REPO/data/ops"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/tu-sua.log"
# Khoá bằng file PID (không dùng flock: tiến trình con như dbus-daemon thừa hưởng fd và giữ khoá mãi)
PIDF="$LOG_DIR/tu-sua.pid"
if [ -f "$PIDF" ] && kill -0 "$(cat "$PIDF" 2>/dev/null)" 2>/dev/null; then echo "Đang có một lần chạy khác, bỏ qua."; exit 0; fi
echo $$ > "$PIDF"
trap 'rm -f "$PIDF"' EXIT

log() { echo "$(date '+%F %T') $*" | tee -a "$LOG"; }
WIN_CURL=/mnt/c/Windows/System32/curl.exe
MC_SERVERS="lobby banghoi-1 practice-1 sangtao-1 pokemon-1"

log "===== Bắt đầu$([ $CHECK_ONLY = 1 ] && echo ' (chỉ kiểm tra)') ====="

# ---------------------------------------------------------------- 0. chờ Docker
for i in $(seq 1 60); do
  docker ps -q >/dev/null 2>&1 && break
  [ "$i" = 1 ] && log "Chờ Docker Desktop sẵn sàng…"
  sleep 5
done
docker ps -q >/dev/null 2>&1 || { log "LỖI: Docker chưa chạy sau 5 phút. Mở Docker Desktop rồi chạy lại."; exit 1; }
# Cho các container có restart policy kịp khởi động
if [ $CHECK_ONLY = 0 ]; then
  up=$(awk '{print int($1)}' /proc/uptime)
  [ "$up" -lt 240 ] && { log "Máy vừa khởi động, chờ thêm 60 giây cho container lên…"; sleep 60; }
fi

FIXED=()
PROXY_TOUCHED=0

# Service compose đang có người chơi không (return 0 = có). Proxy: có người ở bất kỳ server nào là bận
# (khởi động lại proxy là văng hết).
mc_busy() {
  local svc=$1 s out
  if [ "$svc" = proxy ]; then
    for s in $MC_SERVERS; do mc_busy "$s" && return 0; done
    return 1
  fi
  case " $MC_SERVERS " in *" $svc "*) ;; *) return 1 ;; esac
  out=$(docker exec "duckymomo-$svc-1" rcon-cli list 2>/dev/null | sed 's/\x1b\[[0-9;]*m//g')
  echo "$out" | grep -qE 'There are [1-9]' && return 0
  return 1
}

restart_ctr() {  # tên container, lý do
  local c=$1 why=$2 svc proj
  svc=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.service"}}' "$c" 2>/dev/null)
  proj=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$c" 2>/dev/null)
  if [ "$proj" = duckymomo ] && mc_busy "$svc"; then
    log "  BỎ QUA $c ($why): đang có người chơi, không khởi động lại."
    return
  fi
  if [ $CHECK_ONLY = 1 ]; then log "  [cần sửa] $c: $why → sẽ khởi động lại"; return; fi
  log "  Khởi động lại $c ($why)"
  docker restart "$c" >/dev/null && FIXED+=("$c: khởi động lại ($why)")
  [ "$proj" = duckymomo ] && [ "$svc" = proxy ] && PROXY_TOUCHED=1
}

# Container tạo từ phía Windows ghi nhãn đường dẫn \\wsl.localhost\<distro>\home\... → /home/...
unc2wsl() {
  local p=$1
  if [[ $p == '\\'* ]]; then p=${p//\\//}; p=${p#//}; p=${p#*/}; p=/${p#*/}; fi
  echo "$p"
}

recreate_ctr() {  # tạo lại bằng compose của dự án (bind mount treo)
  local c=$1 why=$2 proj wd files svc args=()
  proj=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$c")
  wd=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' "$c")
  files=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project.config_files"}}' "$c")
  svc=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.service"}}' "$c")
  wd=$(unc2wsl "$wd")
  if [ -z "$proj" ] || [ -z "$svc" ] || [ ! -d "$wd" ]; then
    restart_ctr "$c" "$why (không phải container compose, chỉ khởi động lại)"; return
  fi
  if [ "$proj" = duckymomo ] && mc_busy "$svc"; then
    log "  BỎ QUA $c ($why): đang có người chơi, không tạo lại."
    return
  fi
  if [ $CHECK_ONLY = 1 ]; then log "  [cần sửa] $c: $why → sẽ tạo lại ($proj/$svc)"; return; fi
  IFS=',' read -ra fl <<< "$files"
  for f in "${fl[@]}"; do args+=(-f "$(unc2wsl "$f")"); done
  log "  Tạo lại $c ($why)"
  if (cd "$wd" && docker compose -p "$proj" "${args[@]}" up -d --no-deps --force-recreate "$svc" >>"$LOG" 2>&1); then
    FIXED+=("$c: tạo lại ($why)")
  else
    log "  LỖI khi tạo lại $c, xem nhật ký"
  fi
  [ "$proj" = duckymomo ] && [ "$svc" = proxy ] && PROXY_TOUCHED=1
}

# ---------------------------------------------------------------- 1. cổng publish (thử từ phía Windows)
log "Kiểm tra cổng (từ Windows)…"
declare -A BAD_PORT=()
while read -r name ports; do
  [ -z "$ports" ] && continue
  # 0.0.0.0:25565->25577/tcp, 127.0.0.1:8889->8080/tcp (bỏ udp, bỏ dòng IPv6 trùng)
  for hp in $(echo "$ports" | grep -oE '(0\.0\.0\.0|127\.0\.0\.1):[0-9]+->[0-9]+/tcp' | sed -E 's/^[^:]+:([0-9]+)->.*/\1/' | sort -u); do
    alive=0
    if [ -x "$WIN_CURL" ]; then
      # telnet:// qua WSL không bao giờ tự đóng (luôn hết giờ), nên xem đã bắt tay TCP được chưa: time_connect > 0 là cổng sống
      tc=$("$WIN_CURL" -s -o NUL -m 3 -w '%{time_connect}' "telnet://127.0.0.1:$hp" </dev/null 2>/dev/null)
      awk -v t="${tc:-0}" 'BEGIN{exit !(t > 0)}' && alive=1
    else
      timeout 4 bash -c "</dev/tcp/127.0.0.1/$hp" 2>/dev/null && alive=1
    fi
    [ $alive = 0 ] && BAD_PORT[$name]+="$hp "
  done
done < <(docker ps --format '{{.Names}} {{.Ports}}')
if [ ${#BAD_PORT[@]} = 0 ]; then log "  Mọi cổng đều trả lời."; fi
for c in "${!BAD_PORT[@]}"; do restart_ctr "$c" "cổng chết: ${BAD_PORT[$c]}"; done

# ---------------------------------------------------------------- 2. bind mount treo
log "Kiểm tra thư mục gắn vào container…"
STALE=0; TESTED=0
for c in $(docker ps --format '{{.Names}}'); do
  # chỉ thư mục (không phải file lẻ) nằm trong WSL, bỏ socket/thiết bị
  while IFS='|' read -r src dst; do
    [ -d "$src" ] || continue
    case "$src" in /var/run*|/run*|/proc*|/sys*|/dev*|/mnt/wsl*) continue ;; esac
    mark=".tu-sua-$$-$RANDOM"
    if ! touch "$src/$mark" 2>/dev/null; then continue; fi    # thư mục chỉ đọc với user này: bỏ qua
    seen=$(docker exec "$c" sh -c "test -e '$dst/$mark' && echo co || echo khong" 2>/dev/null)
    rm -f "$src/$mark"
    [ -n "$seen" ] && TESTED=$((TESTED + 1))
    if [ "$seen" = khong ]; then
      log "  $c: không thấy thay đổi trong $dst (gắn từ $src)"
      recreate_ctr "$c" "thư mục gắn bị treo: $dst"
      STALE=$((STALE + 1))
      break
    fi
    # seen rỗng = container không có sh/test (vd. distroless): không kiểm được, bỏ qua
  done < <(docker inspect -f '{{range .Mounts}}{{if eq .Type "bind"}}{{.Source}}|{{.Destination}}{{"\n"}}{{end}}{{end}}' "$c")
done
[ $STALE = 0 ] && log "  Đã thử $TESTED thư mục gắn, không có thư mục nào bị treo."

# ---------------------------------------------------------------- 3. relay (cổng VPS) theo proxy
for t in relay; do
  docker ps -a --format '{{.Names}}' | grep -qx "duckymomo-$t-1" || continue
  state=$(docker inspect -f '{{.State.Running}}' "duckymomo-$t-1" 2>/dev/null)
  # relay: wg0 phải còn trong mạng proxy (proxy khởi động lại là mất)
  [ "$t" = relay ] && [ "$state" = true ] && ! docker exec duckymomo-relay-1 ip link show wg0 >/dev/null 2>&1 && state=mat-wg0
  if [ $PROXY_TOUCHED = 1 ] || [ "$state" != true ]; then
    if [ $CHECK_ONLY = 1 ]; then
      log "  [cần sửa] $t: proxy vừa đổi hoặc $t không chạy ($state) → sẽ tạo lại"
    else
      log "Tạo lại $t (proxy vừa khởi động lại hoặc $t không chạy: $state)"
      docker compose --profile tunnel up -d --no-deps --force-recreate "$t" >>"$LOG" 2>&1 && FIXED+=("$t: tạo lại")
    fi
  fi
done

# ---------------------------------------------------------------- tổng kết
if [ ${#FIXED[@]} = 0 ]; then
  log "Xong: không phải sửa gì."
else
  log "Xong: đã sửa ${#FIXED[@]} chỗ:"
  for f in "${FIXED[@]}"; do log "  - $f"; done
fi
