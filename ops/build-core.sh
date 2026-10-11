#!/usr/bin/env bash
# Build network-core bằng Gradle trong Docker (không cần cài Java), rồi copy plugin vào server.
# Chạy từ thư mục gốc repo:  ./ops/build-core.sh   rồi   docker compose restart lobby
set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE_IMAGE="gradle:9.6-jdk25"

docker run --rm \
  -u "$(id -u):$(id -g)" \
  -e GRADLE_USER_HOME=/cache \
  -v duckymomo-gradle-cache:/cache \
  -v "$PWD/network-core:/src" \
  -w /src \
  "$GRADLE_IMAGE" gradle --no-daemon -q :core-paper:build

# core-paper: lobby (menu chọn chế độ, Bedrock auto-login) và banghoi-1 (trang trí map). File .jar không commit (.gitignore).
cp network-core/core-paper/build/libs/DuckyMoMoCore.jar lobby/plugins/DuckyMoMoCore.jar
cp network-core/core-paper/build/libs/DuckyMoMoCore.jar modes/banghoi/server/plugins/DuckyMoMoCore.jar
echo "Xong: lobby/plugins + modes/banghoi/server/plugins/DuckyMoMoCore.jar. Áp dụng:  docker compose restart lobby banghoi-1"
