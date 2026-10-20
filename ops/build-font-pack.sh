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
# Hình món Tinh Luyện (DuckyMoMo tự vẽ, tools/itempack/gen.py) -> assets/duckymomo (item_model "duckymomo:tl_<mã>", Refining.java)
rm -rf "$OUT/items" && python3 tools/itempack/gen.py "$OUT/items" >/dev/null
python3 - <<'EOF'
import json, zipfile, time, glob, os
src = "tools/fontpack/src"
# 26.2: resource pack format 88. min/max_format cho bản mới, supported_formats cho 1.20.2-1.21.8, pack_format cho bản cũ hơn.
mcmeta = {"pack": {"description": "§6DuckyMoMo §f- font tiếng Việt + hình vật phẩm", "pack_format": 88,
                   "supported_formats": [15, 88], "min_format": 15, "max_format": 88}}
# Thứ tự: khoảng trắng gốc, font TTF (chữ), rồi font gốc (ký hiệu font TTF không có)
default = {"providers": [
    {"type": "reference", "id": "minecraft:include/space"},
    {"type": "ttf", "file": "minecraft:bevietnampro-medium.ttf", "shift": [0.0, 0.0], "size": 6.5, "oversample": 8.0},
    {"type": "reference", "id": "minecraft:include/default"},
    {"type": "reference", "id": "minecraft:include/unifont"}]}
fixed = (2026, 10, 12, 0, 0, 0)  # thời gian cố định -> cùng nội dung thì cùng SHA1 (máy người chơi không tải lại)
# 2 gói (20/10): duckymomo-font.zip = font tiếng Việt + hình vật phẩm (mặc định); duckymomo-items.zip = chỉ hình vật phẩm
# (người chơi tắt ô "Font tiếng Việt" trong /caidat thì nhận gói này, chữ dùng font gốc Minecraft).
for out, with_font in (("tools/fontpack/out/duckymomo-font.zip", True), ("tools/fontpack/out/duckymomo-items.zip", False)):
  meta = json.loads(json.dumps(mcmeta))
  if not with_font:
    meta["pack"]["description"] = "§6DuckyMoMo §f- hình vật phẩm (font gốc)"
  with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    def put(name, data):
        info = zipfile.ZipInfo(name, fixed); info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, data)
    put("pack.mcmeta", json.dumps(meta, ensure_ascii=False, indent=2))
    if with_font:
        put("assets/minecraft/font/default.json", json.dumps(default, indent=2))
        put("assets/minecraft/font/bevietnampro-medium.ttf", open(f"{src}/BeVietnamPro-Medium.ttf", "rb").read())
        put("LICENSE-font-OFL.txt", open(f"{src}/OFL.txt", "rb").read())
    # vật phẩm riêng (1.21.4+: item_model trỏ tới assets/duckymomo/items/<tên>.json); client cũ thấy vật phẩm gốc
    for png in sorted(glob.glob("tools/fontpack/out/items/tl_*.png")):  # (vẫn trong vòng lặp 2 gói)
        n = os.path.basename(png)[:-4]
        put(f"assets/duckymomo/textures/item/{n}.png", open(png, "rb").read())
        put(f"assets/duckymomo/models/item/{n}.json", json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"duckymomo:item/{n}"}}))
        put(f"assets/duckymomo/items/{n}.json", json.dumps({"model": {"type": "minecraft:model", "model": f"duckymomo:item/{n}"}}))
  print("Đã đóng gói", out)
EOF
set_env() { if grep -q "^$1=" .env; then sed -i "s#^$1=.*#$1=$2#" .env; else printf '%s=%s\n' "$1" "$2" >> .env; fi; }
mkdir -p "$TRAM6_PUBLIC"
for kind in font items; do
  SHA1=$(sha1sum "$OUT/duckymomo-$kind.zip" | cut -d' ' -f1)
  # tên file có mã ngắn: đổi nội dung -> đổi URL (tránh cache của Cloudflare/trình duyệt)
  NAME="duckymomo-$kind-${SHA1:0:8}.zip"
  cp "$OUT/duckymomo-$kind.zip" "$TRAM6_PUBLIC/$NAME"
  KEY=$( [ $kind = font ] && echo FONT || echo ITEM )
  set_env "${KEY}_PACK_URL" "$PUBLIC_BASE/$NAME"
  set_env "${KEY}_PACK_SHA1" "$SHA1"
  echo "$kind: $PUBLIC_BASE/$NAME ($SHA1)"
done
echo "Áp dụng (kiểm ai online trước): docker compose up -d lobby banghoi-1 practice-1 sangtao-1"
