#!/bin/sh
# Lọc log hoạt động của người chơi từ các server của dự án và in ra, để Dozzle hiện thành tab riêng.
#   MODE=chat      [lobby] 23:36:55 <.Griim7833> alo
#   MODE=join      [proxy] 19:49:07 .Griim7833 vào network / → lobby / rời network, [lobby] … mất kết nối: lý do
#   MODE=commands  [lobby] 23:42:09 .Griim7833: /setblock …   (lệnh có mật khẩu bị che: /login ***)
#   MODE=all       cả ba loại trong một luồng, có nhãn: [lobby] 23:36:55 CHAT <.Griim7833> alo
# Tự tìm server mới (kể cả chế độ thêm sau) mỗi 30 giây, theo label của Docker Compose.
# JSONL=<file>: ghi thêm mỗi sự kiện thành một dòng JSON (ts, server, type chat|join|command, player, text, target)
#   cho trang "Hoạt động người chơi" của panel (lọc theo loại, chế độ). Lịch sử nạp lại khi khởi động có thể bị
#   ghi trùng, panel tự bỏ dòng trùng. File quá 20 MB thì giữ lại nửa sau khi khởi động.
PROJECT="${PROJECT:-duckymomo}"
MODE="${MODE:?Cần đặt MODE=chat|join|commands|all}"

case "$MODE" in
  chat|commands) IMAGES="itzg/minecraft-server" ;;
  join|all)      IMAGES="itzg/mc-proxy itzg/minecraft-server" ;;
  *) echo "MODE không hợp lệ: $MODE" >&2; exit 1 ;;
esac

# Lọc + định dạng. Biến: s = tên service, m = MODE, jf = file JSONL (rỗng = không ghi)
# Mỗi dòng vào có mốc thời gian của Docker ở đầu (docker logs -t): 2026-10-10T08:47:30.123456789Z <log>
AWK_PROG='
function js(v) { gsub(/\\/, "\\\\\\\\", v); gsub(/"/, "\\\"", v); gsub(/[\001-\037]/, " ", v); return "\"" v "\"" }
{ ts = substr($1, 1, 19) "Z"; sub(/^[^ ]+ /, "") }
{ gsub(/\033\[[0-9;]*m/, "") }                                    # bỏ mã màu
!/^\[[0-9:]+ INFO\]: / { next }
{ t = substr($0, 2, 8); line = $0; sub(/^\[[0-9:]+ INFO\]: /, "", line); out = ""; tag = ""; type = ""; player = ""; target = "" }

(m == "chat" || m == "all") && line ~ /^(\[Not Secure\] )?<[^>]+> / {
  sub(/^\[Not Secure\] /, "", line); out = line; tag = "CHAT"; type = "chat"
  player = line; sub(/^</, "", player); sub(/> .*/, "", player)
}

# Chat có định dạng riêng (Paper ghi [Not Secure] vì plugin đổi khung chat): [Not Secure] [Admin] .Griim7833: alo
(m == "chat" || m == "all") && out == "" && line ~ /^\[Not Secure\] / {
  sub(/^\[Not Secure\] /, "", line); body = line
  while (body ~ /^\[[^]]*\] /) sub(/^\[[^]]*\] /, "", body)        # bỏ tiền tố [Admin] [Bang]…
  if (body ~ /^[^ :]+: /) {
    player = body; sub(/: .*/, "", player)
    out = "<" player "> " substr(body, length(player) + 3); tag = "CHAT"; type = "chat"
  }
}

(m == "commands" || m == "all") && line ~ /^[^ ]+ issued server command: / {
  name = line; sub(/ issued server command: .*/, "", name)
  cmd = line;  sub(/^[^ ]+ issued server command: /, "", cmd)
  first = cmd; sub(/ .*/, "", first)
  # Không bao giờ hiện mật khẩu
  if (tolower(first) ~ /^\/(login|l|log|register|reg|changepassword|changepass|cp|unregister|unreg|email|2fa|totp|premium)$/ && cmd != first)
    cmd = first " ***"
  out = name ": " cmd; tag = "LỆNH"; type = "command"; player = name
}

(m == "join" || m == "all") && out == "" {
  if (line ~ /^\[connected player\] [^ ]+ .* has connected$/)         { split(line, a, " "); out = a[3] " vào network"; player = a[3] }
  else if (line ~ /^\[connected player\] [^ ]+ .* has disconnected$/) { split(line, a, " "); out = a[3] " rời network"; player = a[3] }
  else if (line ~ /^\[server connection\] [^ ]+ -> [^ ]+ has connected$/) { split(line, a, " "); out = a[3] " → " a[5]; player = a[3]; target = a[5] }
  else if (line ~ /^[^ ]+ lost connection: /) { out = line; sub(/ lost connection: /, " mất kết nối: ", out); player = line; sub(/ .*/, "", player) }
  if (out != "") { tag = "VÀO/RA"; type = "join" }
}

out != "" {
  print "[" s "] " t " " (m == "all" ? tag " " : "") out; fflush()
  if (jf != "") {
    printf "{\"ts\":%s,\"server\":%s,\"type\":%s,\"player\":%s,\"text\":%s,\"target\":%s}\n", js(ts), js(s), js(type), js(player), js(out), js(target) >> jf
    fflush(jf)
  }
}
'

# Theo dõi một container. $1 = id container, $2 = tên service (proxy, lobby, banghoi-1…)
follow() {
  since="${HISTORY:-6h}"           # lần đầu: hiện lại lịch sử gần đây; các lần nối lại sau: chỉ log mới
  while :; do
    docker logs -f -t --since "$since" "$1" 2>&1 | awk -v s="$2" -v m="$MODE" -v jf="${JSONL:-}" "$AWK_PROG"
    since=1s
    # Container bị xoá (tạo lại) thì dừng; vòng quét bên dưới sẽ tìm container mới
    docker inspect "$1" >/dev/null 2>&1 || return
    sleep 5
  done
}

if [ -n "${JSONL:-}" ]; then
  mkdir -p "$(dirname "$JSONL")"
  if [ -f "$JSONL" ] && [ "$(wc -c < "$JSONL")" -gt 20000000 ]; then
    tail -c 10000000 "$JSONL" | sed 1d > "$JSONL.tmp" && mv "$JSONL.tmp" "$JSONL"
  fi
fi

watched=""
echo "Bắt đầu theo dõi: $MODE (dự án: $PROJECT)"
while :; do
  for image in $IMAGES; do
    for id in $(docker ps -q --filter "label=com.docker.compose.project=$PROJECT" --filter "ancestor=$image"); do
      case " $watched " in *" $id "*) continue ;; esac
      name=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.service"}}' "$id")
      watched="$watched $id"
      echo "== Theo dõi: $name"
      follow "$id" "$name" &
    done
  done
  sleep 30
done
