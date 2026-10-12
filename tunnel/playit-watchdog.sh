#!/bin/sh
# Tự khởi động lại agent playit khi bị kẹt: playit vẫn nhận kết nối ("New TCP Client") nhưng không chuyển vào proxy,
# người chơi bị "Connection reset" (hay gặp sau nhiều giờ chạy). Cách nhận biết: bình thường kết nối qua playit để lại
# dòng log ở proxy với địa chỉ /127.x trong vài giây (playit dùng chung mạng với proxy); thỉnh thoảng một lần ping lẻ
# không để lại dòng nào, nên cần >= MIN_CLIENTS kết nối liên tiếp.
# Quy tắc: đếm kết nối playit SAU dòng /127 cuối cùng của proxy (trong LOOKBACK giây, bỏ GRACE giây gần nhất để proxy
# kịp ghi log); >= MIN_CLIENTS thì khởi động lại playit. Không giới hạn khoảng cách giữa các lần kết nối
# (12/10: hai lần vào cách nhau 16 phút, quy tắc cũ "2 lần trong 3 phút" không bắt được).
PROJECT="${PROJECT:-duckymomo}"
LOOKBACK="${LOOKBACK:-7200}"
GRACE="${GRACE:-20}"
MIN_CLIENTS="${MIN_CLIENTS:-2}"
INTERVAL="${INTERVAL:-30}"
COOLDOWN="${COOLDOWN:-120}"

cid() { docker ps -aq --filter "label=com.docker.compose.project=$PROJECT" --filter "label=com.docker.compose.service=$1" | head -1; }
log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*"; }

log "Theo dõi playit: ngưỡng ${MIN_CLIENTS} kết nối không tới proxy (xét ${LOOKBACK}s gần nhất), kiểm tra mỗi ${INTERVAL}s"
last_restart=0
while :; do
  sleep "$INTERVAL"
  playit=$(cid playit); proxy=$(cid proxy)
  [ -n "$playit" ] && [ -n "$proxy" ] || continue
  if [ "$(docker inspect -f '{{.State.Running}}' "$playit")" != "true" ]; then
    continue  # playit đang tắt (có chủ đích hoặc đang khởi động lại): không can thiệp
  fi
  now=$(date +%s)
  [ $((now - last_restart)) -ge "$COOLDOWN" ] || continue
  # mốc thời gian dạng 2026-10-12T10:50:02 (UTC, do docker ghi) so sánh được theo chuỗi
  since=$(docker inspect -f '{{.State.StartedAt}}' "$playit" | cut -c1-19)
  floor=$(date -u -d "@$((now - LOOKBACK))" '+%Y-%m-%dT%H:%M:%S')
  [ "$since" \> "$floor" ] || since=$floor
  cutoff=$(date -u -d "@$((now - GRACE))" '+%Y-%m-%dT%H:%M:%S')
  last_ok=$(docker logs -t --since "$since" "$proxy" 2>&1 | grep "/127\." | tail -1 | cut -c1-19)
  [ -n "$last_ok" ] || last_ok=$since
  clients=$(docker logs -t --since "$since" "$playit" 2>&1 | grep "New TCP Client" | cut -c1-19 |
    awk -v a="$last_ok" -v b="$cutoff" '$0 > a && $0 < b' | wc -l)
  if [ "$clients" -ge "$MIN_CLIENTS" ]; then
    log "playit nhận $clients kết nối từ $last_ok (UTC) mà proxy không thấy: khởi động lại playit"
    docker restart "$playit" >/dev/null && log "Đã khởi động lại playit"
    last_restart=$now
  fi
done
