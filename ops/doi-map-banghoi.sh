#!/usr/bin/env bash
# Đổi world của banghoi-1 sang một map khác (ví dụ map Trái Đất do tools/earthmap/build-earth.sh tạo).
# KHÔNG xoá gì: world cũ, warp, tiến độ trang trí/đặt công trình cũ đều được đổi tên để giữ lại.
#
# Dùng (từ thư mục gốc repo):
#   ./ops/doi-map-banghoi.sh tools/earthmap/work/out/earth_1-1000 [nhãn-world-cũ]
# Trước khi chạy: sửa border_x/border_z/spawn_x/spawn_z trong modes/banghoi/settings.yml và biên map trong
# modes/banghoi/server/plugins/DuckyMoMoCore/config.yml cho đúng map mới (vùng bảo vệ spawn tính theo spawn_x/spawn_z).
# Sau khi chạy: docker compose up -d banghoi-1 backup-banghoi-1, trang Cài đặt bấm "Áp dụng lại tất cả",
# rồi /dmm decorate start (trồng cây), xong thì /dmm structures start, rồi tạo lại warp (tools/earthmap/make-warps.py).
set -euo pipefail
cd "$(dirname "$0")/.."

SRC="${1:?Cần thư mục map mới, ví dụ tools/earthmap/work/out/earth_1-1000}"
OLD="${2:-world-cu-$(date +%Y%m%d-%H%M)}"
D=data/banghoi-1

[ -f "$SRC/level.dat" ] || { echo "Không thấy $SRC/level.dat" >&2; exit 1; }
[ -d "$D/world" ] || { echo "Không thấy $D/world" >&2; exit 1; }
[ ! -e "$D/$OLD" ] || { echo "$D/$OLD đã có, chọn nhãn khác" >&2; exit 1; }

need=$(du -sk "$SRC" | cut -f1); free=$(df -k --output=avail "$D" | tail -1)
[ "$free" -gt $((need + 10 * 1024 * 1024)) ] || { echo "Ổ đĩa không đủ chỗ (cần ~$((need / 1024 / 1024)) GB + 10 GB dư)" >&2; exit 1; }

echo "1/5 Dừng banghoi-1 (và container sao lưu của nó)"
docker compose stop banghoi-1 backup-banghoi-1

echo "2/5 Giữ world cũ: $D/world -> $D/$OLD"
mv "$D/world" "$D/$OLD"

echo "3/5 Chép map mới: $SRC -> $D/world"
cp -a "$SRC" "$D/world"
rm -f "$D/world/session.lock"

echo "4/5 Cất warp và tiến độ trang trí của map cũ (toạ độ không còn đúng)"
E="$D/plugins/Essentials/warps"
[ -d "$E" ] && mv "$E" "$E-$OLD" && mkdir -p "$E"
C="$D/plugins/DuckyMoMoCore"
for f in decorate-progress.yml structures-progress.yml structures-placed.csv; do
  [ -f "$C/$f" ] && mv "$C/$f" "$C/$f.$OLD"
done

echo "5/5 Dời vùng bảo vệ spawn (WorldGuard, 128 x 128 quanh spawn_x/spawn_z trong settings.yml)"
R="$D/plugins/WorldGuard/worlds/world/regions.yml"
if [ -f "$R" ]; then
  cp "$R" "$R.$OLD"
  python3 - "$R" modes/banghoi/settings.yml <<'PY'
import re, sys
regions, settings = sys.argv[1:]
v = dict(re.findall(r"^(spawn_[xz]):\s*(-?\d+)", open(settings, encoding="utf-8").read(), re.M))
x, z = int(v["spawn_x"]), int(v["spawn_z"])
s = open(regions, encoding="utf-8", newline="").read()  # giữ nguyên kiểu xuống dòng (WorldGuard ghi CRLF)
s, n1 = re.subn(r"(spawn:\s*\n\s*min: \{x: )-?\d+(, y: -?\d+, z: )-?\d+", rf"\g<1>{x - 64}\g<2>{z - 64}", s)
s, n2 = re.subn(r"(\n\s*max: \{x: )-?\d+(, y: -?\d+, z: )-?\d+", rf"\g<1>{x + 64}\g<2>{z + 64}", s, count=1)
assert n1 == 1 and n2 == 1, "không tìm thấy vùng spawn trong regions.yml"
open(regions, "w", encoding="utf-8", newline="").write(s)
print(f"   vùng spawn: x {x - 64}..{x + 64}, z {z - 64}..{z + 64}")
PY
fi

du -sh "$D/world" "$D/$OLD"
echo "Xong. Tiếp theo: docker compose up -d banghoi-1 backup-banghoi-1"
