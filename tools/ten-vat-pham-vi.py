#!/usr/bin/env python3
"""Tạo bảng tên tiếng Việt của vật phẩm/khối cho DuckyMoMoCore (Rewards.vi): VẬT_LIỆU=Tên Việt.

Nguồn: file ngôn ngữ gốc của Minecraft (vi_vn.json) mà MyPet lưu sẵn ở data/banghoi-1/plugins/MyPet/cache/vanilla-lang/<bản>/.
Chạy lại khi nâng bản Minecraft:  python3 tools/ten-vat-pham-vi.py [đường_dẫn_vi_vn.json]
Ghi: network-core/core-paper/src/main/resources/ten-vat-pham-vi.properties (UTF-8, build-core.sh đóng vào jar).
"""
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = sys.argv[1] if len(sys.argv) > 1 else sorted(glob.glob(os.path.join(ROOT, "data/banghoi-1/plugins/MyPet/cache/vanilla-lang/*/vi_vn.json")))[-1]
d = json.load(open(src, encoding="utf-8"))
names = {}
# khối trước, vật phẩm sau (vật phẩm ghi đè: vd giường, cửa có cả hai thì tên vật phẩm là tên người chơi thấy khi cầm)
for prefix in ("block.minecraft.", "item.minecraft."):
    for k, v in d.items():
        if k.startswith(prefix):
            mat = k[len(prefix):]
            if "." not in mat and v.strip():
                names[mat.upper()] = v.strip()


def esc(s):
    return s.replace("\\", "\\\\").replace("\n", "\\n").replace("=", "\\=").replace(":", "\\:")


out = os.path.join(ROOT, "network-core/core-paper/src/main/resources/ten-vat-pham-vi.properties")
with open(out, "w", encoding="utf-8") as f:
    f.write("# Tên tiếng Việt của vật phẩm/khối (từ vi_vn.json của Minecraft). Tạo bằng tools/ten-vat-pham-vi.py, đừng sửa tay.\n")
    for k in sorted(names):
        f.write(f"{k}={esc(names[k])}\n")
print(f"{len(names)} tên -> {out} (nguồn {src})")
