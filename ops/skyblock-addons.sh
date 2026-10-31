#!/usr/bin/env bash
# Tải addon BentoBox cho Skyblock vào data/skyblock-1/plugins/BentoBox/addons (ghim phiên bản; chạy lại an toàn, có rồi thì bỏ qua).
set -euo pipefail
cd "$(dirname "$0")/.."
d=data/skyblock-1/plugins/BentoBox/addons
mkdir -p "$d"
get() { [ -s "$d/$2" ] || curl -fsSL --retry 3 -o "$d/$2" "$1"; }
get "https://cdn.modrinth.com/data/ASGn77Qd/versions/3kLVOQCM/BSkyBlock-1.20.0.jar" "BSkyBlock-1.20.0.jar"
get "https://cdn.modrinth.com/data/OWzL9XSJ/versions/1uTlje4k/Level-2.30.0.jar" "Level-2.30.0.jar"
get "https://cdn.modrinth.com/data/M9ms3AD8/versions/npCIBxXV/Limits-1.31.0.jar" "Limits-1.31.0.jar"
get "https://cdn.modrinth.com/data/wvEhk1hC/versions/uldOepWg/Border-4.8.5.jar" "Border-4.8.5.jar"
get "https://github.com/BentoBoxWorld/Challenges/releases/download/1.8.1/Challenges-1.8.1.jar" "Challenges-1.8.1.jar"
get "https://github.com/BentoBoxWorld/Warps/releases/download/1.19.1/Warps-1.19.1.jar" "Warps-1.19.1.jar"
get "https://github.com/BentoBoxWorld/MagicCobblestoneGenerator/releases/download/2.10.0/MagicCobblestoneGenerator-2.10.0.jar" "MagicCobblestoneGenerator-2.10.0.jar"
get "https://github.com/BentoBoxWorld/Upgrades/releases/download/1.0.5/Upgrades-1.0.5.jar" "Upgrades-1.0.5.jar"
get "https://github.com/BentoBoxWorld/ControlPanel/releases/download/1.16.1/ControlPanel-1.16.1.jar" "ControlPanel-1.16.1.jar"
ls -la "$d"
