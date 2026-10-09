#!/bin/sh
# Lọc log hoạt động của người chơi từ các server của dự án và in ra, để Dozzle hiện thành tab riêng.
#   MODE=chat      [lobby] 23:36:55 <.Griim7833> alo
#   MODE=join      [proxy] 19:49:07 .Griim7833 vào network / → lobby / rời network, [lobby] … mất kết nối: lý do
#   MODE=commands  [lobby] 23:42:09 .Griim7833: /setblock …   (lệnh có mật khẩu bị che: /login ***)
#   MODE=all       cả ba loại trong một luồng, có nhãn: [lobby] 23:36:55 CHAT <.Griim7833> alo
# Tự tìm server mới (kể cả chế độ thêm sau) mỗi 30 giây, theo label của Docker Compose.
PROJECT="${PROJECT:-duckymomo}"
MODE="${MODE:?Cần đặt MODE=chat|join|commands|all}"

case "$MODE" in
  chat|commands) IMAGES="itzg/minecraft-server" ;;
  join|all)      IMAGES="itzg/mc-proxy itzg/minecraft-server" ;;
  *) echo "MODE không hợp lệ: $MODE" >&2; exit 1 ;;
esac

# Lọc + định dạng. Biến: s = tên service, m = MODE
AWK_PROG='
{ gsub(/\033\[[0-9;]*m/, "") }                                    # bỏ mã màu
!/^\[[0-9:]+ INFO\]: / { next }
{ t = substr($0, 2, 8); line = $0; sub(/^\[[0-9:]+ INFO\]: /, "", line); out = ""; tag = "" }

(m == "chat" || m == "all") && line ~ /^(\[Not Secure\] )?<[^>]+> / {
  sub(/^\[Not Secure\] /, "", line); out = line; tag = "CHAT"
}

(m == "commands" || m == "all") && line ~ /^[^ ]+ issued server command: / {
  name = line; sub(/ issued server command: .*/, "", name)
  cmd = line;  sub(/^[^ ]+ issued server command: /, "", cmd)
  first = cmd; sub(/ .*/, "", first)
  # Không bao giờ hiện mật khẩu
  if (tolower(first) ~ /^\/(login|l|log|register|reg|changepassword|changepass|cp|unregister|unreg|email|2fa|totp|premium)$/ && cmd != first)
    cmd = first " ***"
  out = name ": " cmd; tag = "LỆNH"
}

(m == "join" || m == "all") && out == "" {
  if (line ~ /^\[connected player\] [^ ]+ .* has connected$/)         { split(line, a, " "); out = a[3] " vào network" }
  else if (line ~ /^\[connected player\] [^ ]+ .* has disconnected$/) { split(line, a, " "); out = a[3] " rời network" }
  else if (line ~ /^\[server connection\] [^ ]+ -> [^ ]+ has connected$/) { split(line, a, " "); out = a[3] " → " a[5] }
  else if (line ~ /^[^ ]+ lost connection: /) { out = line; sub(/ lost connection: /, " mất kết nối: ", out) }
  if (out != "") tag = "VÀO/RA"
}

out != "" { print "[" s "] " t " " (m == "all" ? tag " " : "") out; fflush() }
'

# Theo dõi một container. $1 = id container, $2 = tên service (proxy, lobby, banghoi-1…)
follow() {
  since="${HISTORY:-6h}"           # lần đầu: hiện lại lịch sử gần đây; các lần nối lại sau: chỉ log mới
  while :; do
    docker logs -f --since "$since" "$1" 2>&1 | awk -v s="$2" -v m="$MODE" "$AWK_PROG"
    since=1s
    # Container bị xoá (tạo lại) thì dừng; vòng quét bên dưới sẽ tìm container mới
    docker inspect "$1" >/dev/null 2>&1 || return
    sleep 5
  done
}

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
