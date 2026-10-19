"""Vẽ hình 16x16 cho món Tinh Luyện — DuckyMoMo TỰ VẼ theo phong cách "phiến đá khắc ký hiệu màu" (lấy ý tưởng, không chép hình server khác).
Chạy: python3 tools/itempack/gen.py <thư mục ra>  -> tl_<mã>.png + _xemtruoc.png (phóng to để duyệt).
Phiến đá: O viền, D tối, M vừa, L sáng, H rất sáng. Ký hiệu (6x7): # màu chính, + sáng, - tối, S lấp lánh."""
import os
import sys
from PIL import Image

TABLET = [
    "................",
    "...OOOOOOOOOO...",
    "..OHHHHLLLLLLO..",
    "..OHLLLLLLLLMO..",
    "..OHLLLLLLLLMO..",
    "..OLLLLLLLLLMO..",
    "..OLLLLLLLLLMO..",
    "..OLLLLLLLLLMO..",
    "..OLLLLLLLLLMO..",
    "..OLLLLLLLLLMO..",
    "..OLLLLLLLLMMO..",
    "..OLLLLLLLLMMO..",
    "..OLLLLLLLMMMO..",
    "..OMMMMMMMMMDO..",
    "...ODDDDDDDDO...",
    "....OOOOOOOO...."]
COIN = [
    "................",
    "......OOOO......",
    "....OOHHLLOO....",
    "...OHHLLLLMMO...",
    "..OHLLLLLLLMMO..",
    "..OHLLLLLLLLMO..",
    ".OHLLLLLLLLLMDO.",
    ".OLLLLLLLLLLMDO.",
    ".OLLLLLLLLLLMDO.",
    ".OLLLLLLLLLMMDO.",
    "..OLLLLLLLMMDO..",
    "..OMLLLLLMMDDO..",
    "...OMMMMMDDDO...",
    "....OODDDDOO....",
    "......OOOO......",
    "................"]
GLYPH = {  # 8 cột x 8 hàng, đặt ở x=4, y=4
    "gem":   ["...--...", "..-++-..", ".-+##+-.", "-+####+-", "-######-", ".-####-.", "..-##-..", "...--..."],
    "hex":   ["..----..", ".-++##-.", "-+####+-", "-#+##+#-", "-######-", "-######-", ".-####-.", "..----.."],
    "drop":  ["...-....", "..-+-...", "..-+#-..", ".-+###-.", "-+#####-", "-######-", ".-####-.", "..----.."],
    "dust":  ["-+....-+", ".-+..-+.", "..-++-..", "..-##-..", "..-##-..", ".-+..-+.", "-+....-+", "........"],
    "bar":   ["........", "..-----.", ".-++###-", "-+#####-", "-######-", "-------.", "........", "........"],
    "bars":  ["..-----.", ".-++###-", "-------.", "........", "..-----.", ".-++###-", "-######-", "-------."],
    "crown": ["-..--..-", "-+.--.+-", "-+#++#+-", "-######-", "-#+##+#-", "-######-", "--------", "........"],
    "skull": [".------.", "-######-", "-#####+-", "-+-##-+-", "-######-", ".-#--#-.", ".-#-.-#.", "..-...-."],
    "flame": ["....-...", "...-+-..", "..-+#-..", ".-+##+-.", "-+#+###-", "-##++##-", ".-####-.", "..----.."],
    "block": ["--------", "-++####-", "-+#####-", "-##+###-", "-###+##-", "-######-", "--------", "........"],
    "leaf":  [".....--.", "....-++-", "...-+#+-", "..-+##-.", ".-+##-..", ".-##-...", "-+--....", "-......."],
    "one":   ["...--...", "..-+#-..", ".-+##-..", "...##-..", "...##-..", "...##-..", "..-##--.", "..-----."],
    "two":   ["..----..", ".-+###-.", ".--..##-", "....-#-.", "...-#-..", "..-#-...", ".-####-.", ".------."],
}
#           phiến/xu (O, D, M, L, H)                                ký hiệu (tối, chính, sáng)            khuôn   ký hiệu
STONE = ("#2e2744", "#5e528a", "#8f80c4", "#b7a8ec", "#e2d9ff")
ITEMS = {
    "kimcuong":  (STONE, ("#0e6b66", "#3fd3c8", "#c8fffa"), "tablet", "gem"),
    "lucbao":    (STONE, ("#0d5a26", "#2fd36a", "#c6ffd8"), "tablet", "hex"),
    "lapis":     (STONE, ("#14286e", "#3a64d8", "#c9d6ff"), "tablet", "drop"),
    "dado":      (STONE, ("#6e0d0d", "#e03030", "#ffc4bc"), "tablet", "dust"),
    "dong":      (STONE, ("#6b3415", "#d77a46", "#ffd9bf"), "tablet", "bar"),
    "sat":       (STONE, ("#4a4a4a", "#c4c4c4", "#ffffff"), "tablet", "bars"),
    "vang":      (STONE, ("#7a5200", "#f2c230", "#fff6c4"), "tablet", "crown"),
    "netherite": (STONE, ("#1e1418", "#5e4a55", "#b7a2ad"), "tablet", "skull"),
    "than":      (STONE, ("#0a0a0a", "#2e2e2e", "#7a7a7a"), "tablet", "flame"),
    "da":        (STONE, ("#3a3a3a", "#8a8a8a", "#d6d6d6"), "tablet", "block"),
    "dialinh":   (STONE, ("#1f5a14", "#5fc23a", "#d8ffc4"), "tablet", "leaf"),
    "xuquai":    (("#3a2a00", "#9a6a00", "#e0a800", "#ffd84a", "#fff2b0"), ("#6a4400", "#b57d00", "#fff2b0"), "coin", "one"),
    "xuquai2":   (("#3a0010", "#8a0020", "#d0204a", "#ff6a86", "#ffd6de"), ("#5a0014", "#9a0a2e", "#ffd6de"), "coin", "two"),
}


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def draw(item):
    base, glyph, shape, sym = ITEMS[item]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    col = dict(zip("ODMLH", base))
    for y, row in enumerate(TABLET if shape == "tablet" else COIN):
        for x, ch in enumerate(row):
            if ch in col:
                img.putpixel((x, y), rgb(col[ch]))
    gc = {"-": glyph[0], "#": glyph[1], "+": glyph[2]}
    ox, oy = 4, 4
    for y, row in enumerate(GLYPH[sym]):
        for x, ch in enumerate(row):
            if ch in gc:
                img.putpixel((ox + x, oy + y), rgb(gc[ch]))
    return img


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for it in ITEMS:
        draw(it).save(f"{out}/tl_{it}.png")
    prev = Image.new("RGBA", (8 + len(ITEMS) * 136, 144), (139, 139, 139, 255))
    for i, it in enumerate(ITEMS):
        big = draw(it).resize((128, 128), Image.NEAREST)
        prev.paste(big, (8 + i * 136, 8), big)
    prev.save(f"{out}/_xemtruoc.png")
    print("Đã vẽ", len(ITEMS), "hình vào", out)
