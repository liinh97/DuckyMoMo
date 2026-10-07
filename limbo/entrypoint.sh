#!/bin/sh
# Khởi động NanoLimbo: tải jar lần đầu, điền secret Velocity vào settings.yml rồi chạy.
set -eu
cd /data

if [ ! -f NanoLimbo.jar ]; then
  echo "Tải NanoLimbo từ ${NANOLIMBO_URL}"
  wget -q -O NanoLimbo.jar.tmp "${NANOLIMBO_URL}" || {
    echo "Không tải được NanoLimbo. Tải tay file .jar từ https://github.com/Nan1t/NanoLimbo/releases" >&2
    echo "rồi đặt vào data/limbo/NanoLimbo.jar" >&2
    rm -f NanoLimbo.jar.tmp
    exit 1
  }
  mv NanoLimbo.jar.tmp NanoLimbo.jar
fi

# Secret chỉ gồm chữ và số (ops/init.sh sinh), nên thay bằng sed là an toàn.
sed "s|\${CFG_VELOCITY_SECRET}|${VELOCITY_SECRET}|" /template/settings.yml > settings.yml

exec java -Xms64M -Xmx"${MEMORY:-256M}" -jar NanoLimbo.jar
