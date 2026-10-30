#!/usr/bin/env python3
"""Vẽ bảng hướng dẫn người mới (30/10) để treo ở spawn bằng bản đồ trong khung (DuckyMoMoCore BangTranh, /dmm bangtranh).

Ảnh ra đúng bội số 128 px (mỗi ô = 1 bản đồ): mặc định 7 x 4 ô = 896 x 512.
  python3 tools/bangtranh/ve-bang.py <client.jar> [ảnh_nền] [ra.png]
client.jar: jar client Minecraft (lấy icon vật phẩm 16x16, phóng to giữ nét pixel).
"""
import sys, zipfile, io
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance

HERE = Path(__file__).resolve().parent
JAR = zipfile.ZipFile(sys.argv[1])
BG = Path(sys.argv[2]) if len(sys.argv) > 2 else HERE / "nen-lang.jpg"
OUT = Path(sys.argv[3]) if len(sys.argv) > 3 else HERE / "huongdan.png"
COLS, ROWS = 7, 4
W, H = COLS * 128, ROWS * 128
B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
R = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
f = lambda p, s: ImageFont.truetype(p, s)

STEPS = [
    ("ender_pearl", "Ra thế giới sinh tồn", "Chọn vùng đất trống để bắt đầu", "/tpr"),
    ("writable_book", "Nhận nhiệm vụ mỗi ngày", "Làm xong nhận xu và quà", "/nhiemvu"),
    ("iron_pickaxe", "Chọn nghề kiếm tiền", "Đào, chặt, câu, săn đều ra tiền", "/jobs"),
    ("bell", "Vào hoặc lập thị trấn", "Có đồng đội, đất được bảo vệ", "/t"),
    ("oak_door", "Đặt nhà đầu tiên", "Lúc nào cũng có chỗ để về", "/sethome"),
    ("compass_00", "Khám phá khu vực", "Chợ, Thợ Rèn, Đấu Trường, dungeon", "/warp"),
]

# nền: phủ kín khung, giữ ảnh rõ (30/10 chủ server: "chữ mờ đi để ảnh nổi lên, kiểu chữ viết mờ lên ảnh")
bg = Image.open(BG).convert("RGB")
s = max(W / bg.width, H / bg.height)
bg = bg.resize((round(bg.width * s), round(bg.height * s)), Image.LANCZOS)
bg = bg.crop(((bg.width - W) // 2, (bg.height - H) // 2, (bg.width - W) // 2 + W, (bg.height - H) // 2 + H))
img = bg.convert("RGBA")

# chữ: vẽ lên lớp riêng, bóng mờ (blur) bên dưới cho đọc được mà không cần khung
txt = Image.new("RGBA", (W, H), (0, 0, 0, 0))
sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
dt, ds = ImageDraw.Draw(txt), ImageDraw.Draw(sh)
WHITE, SOFT, GOLD = (255, 255, 255, 190), (255, 255, 255, 150), (255, 214, 110, 205)


def put(x, y, s_, font, fill, center=False):
    if center:
        x -= dt.textlength(s_, font=font) / 2
    ds.text((x + 1, y + 2), s_, font=font, fill=(0, 0, 0, 150))
    dt.text((x, y), s_, font=font, fill=fill)


put(W / 2, 14, "CHÀO MỪNG ĐẾN", f(B, 18), SOFT, True)
put(W / 2, 34, "DUCKYMOMO", f(B, 46), (255, 210, 90, 215), True)

cw, rh, top = W // 2, 118, 120
for i, (icon, title, sub, cmd) in enumerate(STEPS):
    col, row = i // 3, i % 3
    x0, y0 = 40 + col * cw, top + row * rh
    put(x0, y0 + 14, str(i + 1), f(B, 40), (255, 210, 90, 170))
    ic = Image.open(io.BytesIO(JAR.read(f"assets/minecraft/textures/item/{icon}.png"))).convert("RGBA").resize((40, 40), Image.NEAREST)
    ic.putalpha(ic.getchannel("A").point(lambda a: a * 200 // 255))
    txt.alpha_composite(ic, (int(x0 + 40), int(y0 + 18)))
    tx = x0 + 94
    put(tx, y0 + 10, title, f(B, 21), WHITE)
    put(tx, y0 + 38, sub, f(R, 15), SOFT)
    put(tx, y0 + 60, cmd, f(B, 26), GOLD)

sh = sh.filter(ImageFilter.GaussianBlur(3))
img.alpha_composite(sh)
img.alpha_composite(txt)
img.convert("RGB").save(OUT)
print("Đã vẽ", OUT, f"{W}x{H} = {COLS}x{ROWS} bản đồ")
