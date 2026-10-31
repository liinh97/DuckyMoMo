"""Skyblock (31/10): vá kẽ hở giá Chợ, chỉ hạ giá BÁN LẠI (rà bằng tools/skyblock-doi-gia.py + rà tay các biến đổi không phải công thức).
Kính màu: 8 kính + 1 nhuộm; bê tông: bột bê tông gặp nước; hoa cao: bột xương nhân đôi; dropper: gần như toàn đá cuội. Chạy lại an toàn."""
import glob
import re

S = "/home/developer/works/DuckyMoMo/data/skyblock-1/plugins/UltimateShop/shops/"
TAP = re.compile(r"^(RED|ORANGE|YELLOW|LIME|GREEN|LIGHT_BLUE|CYAN|BLUE|PURPLE|MAGENTA|PINK|WHITE|LIGHT_GRAY|GRAY|BLACK|BROWN)_(STAINED_GLASS|CONCRETE)$")
FIX = {"SUNFLOWER": "0.5", "PEONY": "0.5", "LILAC": "0.5", "ROSE_BUSH": "0.5", "DROPPER": "3.5"}
n = 0
for f in glob.glob(S + "*.yml"):
    s = open(f, encoding="utf-8").read()

    def sua(m):
        global n
        mat = m.group(2)
        ta = TAP.match(mat)
        gia = FIX.get(mat) or (None if not ta else "0.5" if ta.group(2) == "STAINED_GLASS" else "0.6")
        if gia is None:
            return m.group(0)
        n += 1
        return m.group(1) + mat + m.group(3) + gia + m.group(4)

    s2 = re.sub(r"(material: )([A-Z_]+)(\n(?:(?!material:)[\s\S])*?sell-prices:\n\s+1:\n\s+economy-plugin: Vault\n\s+amount: ')[0-9.]+(')", sua, s)
    if s2 != s:
        open(f, "w", encoding="utf-8", newline="").write(s2)
print("đã sửa", n, "món")
