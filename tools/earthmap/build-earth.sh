#!/usr/bin/env bash
# Tạo map Trái Đất cho Bang Hội Chiến bằng WorldPainter + script DerMattinger/MinecraftEarthMap (MIT).
# Chạy WorldPainter không giao diện trong Docker (eclipse-temurin), không cần cài Java.
#
# Dùng:  ./tools/earthmap/build-earth.sh <scale>
#   scale 10 = 1:4000 (thử nhanh), 20 = 1:2000 (dùng cho server), 40 = 1:1000 (cần ~19 GB RAM, máy hiện tại không đủ)
# Kết quả: tools/earthmap/work/out/earth_1-<tỉ lệ>/  (thế giới Minecraft 26.x)
# Chuẩn bị công cụ: xem tools/earthmap/README.md
set -euo pipefail
cd "$(dirname "$0")"

SCALE="${1:-10}"
case "$SCALE" in 10|20|40) ;; *) echo "scale phải là 10, 20 hoặc 40" >&2; exit 1 ;; esac
RATIO=$((40000 / SCALE))
case "$SCALE" in 10) DEF_XMX=4g ;; 20) DEF_XMX=9g ;; 40) DEF_XMX=20g ;; esac
XMX="${XMX:-$DEF_XMX}"
NAME="earth_1-$RATIO"
W="$PWD/work"

[ -d "$W/worldpainter/lib" ] && [ -f "$W/MinecraftEarthMap/world.js" ] \
  || { echo "Chưa có WorldPainter hoặc MinecraftEarthMap trong $W (xem README.md)" >&2; exit 1; }

# Bản sao world.js đã chỉnh cho chạy tự động:
#  - đường dẫn dữ liệu trong container, block theo bản mới nhất script hỗ trợ ("1-14"), tỉ lệ
#  - định dạng Minecraft 26.x (JAVA_ANVIL_26_1), giới hạn độ cao -64..320 như Minecraft hiện đại
#  - xuất thẳng ra thư mục thế giới Minecraft (script gốc chỉ lưu file .world để xuất bằng tay)
python3 - "$W/MinecraftEarthMap/world.js" "$W/world-generated.js" "$SCALE" "$NAME" <<'PY'
import re, sys
src, dst, scale, name = sys.argv[1:]
s = open(src, encoding="utf-8").read()
s = re.sub(r'^var path = .*$', 'var path = "/src/MinecraftEarthMap/";', s, count=1, flags=re.M)
s = re.sub(r'^var version = .*$', 'var version = "1-14";', s, count=1, flags=re.M)
s = re.sub(r'^var scale = .*$', 'var scale = %s;' % scale, s, count=1, flags=re.M)
# Script gốc dùng mã 189 cho bamboo_jungle; WorldPainter (bảng Minecraft1_14Biomes) dùng 168, 189 làm hỏng bước xuất
assert s.count('.toLevel(189)') == 1
s = s.replace('.toLevel(189)', '.toLevel(168)')
fmt = ('.toLevels(0, 255)'
       '.withMapFormat(Java.type("org.pepsoft.worldpainter.DefaultPlugin").JAVA_ANVIL_26_1)'
       '.withLowerBuildLimit(-64).withUpperBuildLimit(320)')
assert s.count('.toLevels(0, 255)') >= 1
s = s.replace('.toLevels(0, 255)', fmt, 1)
export = ('world.setName("%s");\n'
          'print("Đang xuất thế giới Minecraft 26.x: /src/out/%s");\n'
          'wp.exportWorld(world).toDirectory("/src/out").go();\n'
          'print("Xong.");\n') % (name, name)
i = s.rfind('world = null;')
assert i >= 0
s = s[:i] + export + s[i:]
open(dst, "w", encoding="utf-8").write(s)
PY

mkdir -p "$W/out" "$W/home"
rm -rf "${W:?}/out/$NAME"
echo "Tạo $NAME (scale $SCALE, RAM tối đa $XMX). Có thể mất từ vài phút đến hơn một giờ…"
time docker run --rm -u "$(id -u):$(id -g)" \
  -e HOME=/src/home \
  -v "$W:/src" -w /src \
  eclipse-temurin:25-jdk \
  java "-Xmx$XMX" -Djava.awt.headless=true \
       -cp "/src/worldpainter/lib/*" org.pepsoft.worldpainter.tools.ScriptingTool /src/world-generated.js

du -sh "$W/out/$NAME"
echo "Kết quả: $W/out/$NAME"
