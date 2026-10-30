#!/usr/bin/env bash
# Build network-core bằng Gradle trong Docker (không cần cài Java), rồi copy plugin vào server.
# Chạy từ thư mục gốc repo:  ./ops/build-core.sh   rồi   docker compose restart lobby
set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE_IMAGE="gradle:9.6-jdk25"

# core-velocity (Tủ skin) biên dịch dựa trên đúng jar đang chạy ở proxy: chép vào network-core/libs (không commit)
mkdir -p network-core/libs
if [ ! -f network-core/libs/velocity.jar ]; then
  v=$(docker exec duckymomo-proxy-1 sh -c 'ls /server/velocity-*.jar 2>/dev/null | head -1' || true)
  [ -n "$v" ] && docker cp "duckymomo-proxy-1:$v" network-core/libs/velocity.jar
fi
[ -f data/proxy/plugins/SkinsRestorer.jar ] && cp data/proxy/plugins/SkinsRestorer.jar network-core/libs/SkinsRestorer.jar
# PacketEvents (ẩn giáp sửa gói tin) biên dịch theo đúng jar ở banghoi
pe=$(ls data/banghoi-1/plugins/packetevents-spigot-*.jar 2>/dev/null | head -1 || true)
[ -n "$pe" ] && cp "$pe" network-core/libs/packetevents.jar
# zAuctionHouse (bot mua đồ người chơi treo /ah, 30/10) biên dịch theo đúng jar ở banghoi
za=$(ls data/banghoi-1/plugins/zAuctionHouse-*.jar 2>/dev/null | head -1 || true)
[ -n "$za" ] && cp "$za" network-core/libs/zauctionhouse.jar

docker run --rm \
  -u "$(id -u):$(id -g)" \
  -e GRADLE_USER_HOME=/cache \
  -v duckymomo-gradle-cache:/cache \
  -v "$PWD/network-core:/src" \
  -w /src \
  "$GRADLE_IMAGE" gradle --no-daemon -q :core-paper:build $( [ -f network-core/libs/velocity.jar ] && echo :core-velocity:build )

# core-paper: lobby (menu chọn chế độ, Bedrock auto-login), banghoi-1 (trang trí map, dọn rác, chống đặt-phá) và practice-1 (lọc chat). File .jar không commit (.gitignore).
cp network-core/core-paper/build/libs/DuckyMoMoCore.jar lobby/plugins/DuckyMoMoCore.jar
cp network-core/core-paper/build/libs/DuckyMoMoCore.jar modes/banghoi/server/plugins/DuckyMoMoCore.jar
cp network-core/core-paper/build/libs/DuckyMoMoCore.jar modes/practice/server/plugins/DuckyMoMoCore.jar
cp network-core/core-paper/build/libs/DuckyMoMoCore.jar modes/sangtao/server/plugins/DuckyMoMoCore.jar
if [ -f network-core/core-velocity/build/libs/DuckyMoMoSkin.jar ]; then
  cp network-core/core-velocity/build/libs/DuckyMoMoSkin.jar proxy/plugins/DuckyMoMoSkin.jar
  echo "Tủ skin (proxy): proxy/plugins/DuckyMoMoSkin.jar. Áp dụng: docker compose up -d proxy && docker compose --profile tunnel up -d --force-recreate relay"
fi
echo "Xong: lobby, banghoi, practice, sangtao. Áp dụng (kiểm ai online trước):  docker compose restart lobby banghoi-1 practice-1 sangtao-1"
