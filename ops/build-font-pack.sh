#!/bin/bash
# Gói tài nguyên "font tiếng Việt" cho người chơi (Java). Font pixel mặc định của Minecraft vẽ chữ có dấu khó đọc
# (dấu chồng, dính chữ) -> thay chữ bằng Be Vietnam Pro (SIL OFL 1.1, tools/fontpack/src/OFL.txt); ký hiệu (✦ ⚔ ❤...)
# không có trong font vẫn lấy từ font gốc của Minecraft.
#
# Làm gì: đóng gói tools/fontpack -> zip, tính SHA1, chép lên Trạm 6 (apps/mc/public/pack/, route công khai /mc/pack/),
# ghi FONT_PACK_URL + FONT_PACK_SHA1 vào .env. Sau đó: docker compose up -d lobby banghoi-1 practice-1
# (server gửi gói khi người chơi vào; không bắt buộc, từ chối vẫn chơi được).
# Chạy từ thư mục gốc repo:  ./ops/build-font-pack.sh
set -euo pipefail
cd "$(dirname "$0")/.."
TRAM6_PUBLIC="${TRAM6_PUBLIC:-$HOME/works/tram6/apps/mc/public/pack}"
PUBLIC_BASE="${PUBLIC_BASE:-https://app.goc6.io.vn/mc/pack}"
OUT=tools/fontpack/out
mkdir -p "$OUT"
python3 - <<'EOF'
import json, zipfile, time
src = "tools/fontpack/src"
# 26.2: resource pack format 88. min/max_format cho bản mới, supported_formats cho 1.20.2-1.21.8, pack_format cho bản cũ hơn.
mcmeta = {"pack": {"description": "§6DuckyMoMo §f- font tiếng Việt dễ đọc", "pack_format": 88,
                   "supported_formats": [15, 88], "min_format": 15, "max_format": 88}}
# Thứ tự: khoảng trắng gốc, font TTF (chữ), rồi font gốc (ký hiệu font TTF không có)
default = {"providers": [
    {"type": "reference", "id": "minecraft:include/space"},
    {"type": "ttf", "file": "minecraft:bevietnampro-medium.ttf", "shift": [0.0, 1.5], "size": 7.5, "oversample": 8.0},
    {"type": "reference", "id": "minecraft:include/default"},
    {"type": "reference", "id": "minecraft:include/unifont"}]}
out = "tools/fontpack/out/duckymomo-font.zip"
fixed = (2026, 10, 12, 0, 0, 0)  # thời gian cố định -> cùng nội dung thì cùng SHA1 (máy người chơi không tải lại)
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    def put(name, data):
        info = zipfile.ZipInfo(name, fixed); info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, data)
    put("pack.mcmeta", json.dumps(mcmeta, ensure_ascii=False, indent=2))
    put("assets/minecraft/font/default.json", json.dumps(default, indent=2))
    put("assets/minecraft/font/bevietnampro-medium.ttf", open(f"{src}/BeVietnamPro-Medium.ttf", "rb").read())
    put("LICENSE-font-OFL.txt", open(f"{src}/OFL.txt", "rb").read())
print("Đã đóng gói", out)
EOF
SHA1=$(sha1sum "$OUT/duckymomo-font.zip" | cut -d' ' -f1)
# tên file có mã ngắn: đổi nội dung -> đổi URL (tránh cache của Cloudflare/trình duyệt)
NAME="duckymomo-font-${SHA1:0:8}.zip"
mkdir -p "$TRAM6_PUBLIC"
cp "$OUT/duckymomo-font.zip" "$TRAM6_PUBLIC/$NAME"
URL="$PUBLIC_BASE/$NAME"
set_env() { if grep -q "^$1=" .env; then sed -i "s#^$1=.*#$1=$2#" .env; else printf '%s=%s\n' "$1" "$2" >> .env; fi; }
set_env FONT_PACK_URL "$URL"
set_env FONT_PACK_SHA1 "$SHA1"
echo "URL:  $URL"
echo "SHA1: $SHA1"
echo "Áp dụng: docker compose up -d lobby banghoi-1 practice-1"
