#!/usr/bin/env python3
"""Sửa dấu ngã của font pixel gốc Minecraft (30/10).

Font gốc vẽ dấu ngã thành răng cưa `..#.#` / `.#.#.`, nhìn như dấu mũ ("Đã" đọc thành "Đẫ"). Script lấy đúng các chữ có dấu ngã
từ accented.png của bản Minecraft (client jar của Mojang), xoá dấu cũ, vẽ dấu ngã mới `.#..#` / `#.##.` rồi ghi:
  tools/fontpack/src/vn-pixel.png   (ô 9x12 như accented.png)
  tools/fontpack/src/vn-pixel.json  (provider bitmap, đặt TRƯỚC include/default trong font/default.json của gói)
Chỉ chạy lại khi đổi bản Minecraft hoặc kiểu dấu:  python3 tools/fontpack/vn-pixel.py [đường_dẫn_client.jar] [phiên_bản]
"""
import io, json, sys, urllib.request, zipfile
from pathlib import Path
from PIL import Image

VERSION = sys.argv[2] if len(sys.argv) > 2 else "26.2"
TILDE = [".#..#", "#.##."]
CHARS = "ãẫẵẽễĩõỗỡũữỹÃẪẴẼỄĨÕỖỠŨỮỸ"
OUT = Path(__file__).resolve().parent / "src"


def client_jar():
    if len(sys.argv) > 1:
        return zipfile.ZipFile(sys.argv[1])
    get = lambda u: urllib.request.urlopen(u, timeout=60).read()
    man = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
    meta = json.loads(get(next(v["url"] for v in man["versions"] if v["id"] == VERSION)))
    return zipfile.ZipFile(io.BytesIO(get(meta["downloads"]["client"]["url"])))


jar = client_jar()
inc = json.loads(jar.read("assets/minecraft/font/include/default.json"))
prov = next(p for p in inc["providers"] if p.get("file") == "minecraft:font/accented.png")
img = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/font/accented.png"))).convert("RGBA")
rows = prov["chars"]
cw, chh = img.width // len(rows[0]), img.height // len(rows)
where = {c: (x, y) for y, row in enumerate(rows) for x, c in enumerate(row)}

cols = 12
out = Image.new("RGBA", (cols * cw, -(-len(CHARS) // cols) * chh), (0, 0, 0, 0))
grid = []
for i, c in enumerate(CHARS):
    x, y = where[c]
    cell = img.crop((x * cw, y * chh, (x + 1) * cw, (y + 1) * chh))
    px = cell.load()
    used = [r for r in range(chh) if any(px[i2, r][3] for i2 in range(cw))]
    top = used[0]
    marks = [(i2, r) for r in (top, top + 1) for i2 in range(cw) if px[i2, r][3]]
    color = px[marks[0]]
    mid = (min(m[0] for m in marks) + max(m[0] for m in marks)) / 2
    width = max([i2 for r in range(chh) for i2 in range(cw) if px[i2, r][3]]) + 1
    for i2, r in marks:
        px[i2, r] = (0, 0, 0, 0)
    tw = len(TILDE[0])
    x0 = round(mid - (tw - 1) / 2)
    x0 = max(0, min(x0, width - tw)) if width >= tw else 0
    for dy, row in enumerate(TILDE):
        for dx, ch in enumerate(row):
            if ch == "#" and x0 + dx < cw:
                px[x0 + dx, top + dy] = color
    out.paste(cell, ((i % cols) * cw, (i // cols) * chh))
for r in range(0, len(CHARS), cols):
    grid.append(CHARS[r:r + cols].ljust(cols, "\u0000"))

OUT.mkdir(exist_ok=True)
out.save(OUT / "vn-pixel.png")
(OUT / "vn-pixel.json").write_text(json.dumps({"type": "bitmap", "file": "minecraft:font/vn-pixel.png", "height": prov.get("height", 8),
                                               "ascent": prov["ascent"], "chars": grid}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Đã ghi", OUT / "vn-pixel.png", "và vn-pixel.json:", len(CHARS), "chữ (Minecraft", VERSION + ")")
