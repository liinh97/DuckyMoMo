"""Ghi chữ lên các biển của khu spawn GlassCup (Bang Hội Chiến): quầy mua [Buy], thu mua [Sell], cổng [Warp], thông tin.

Công trình có 24 dãy x 5 biển gỗ sồi treo quanh vòng quảng trường (đặt bằng tools/place-schem.py). Script tự dò vị trí
biển từ file .schem + gốc đặt (ox, oy, oz), chia theo phía: tây = mua, đông = warp, nam = thu mua, bắc = thông tin.
Biển đầu mỗi dãy là tên quầy. Biển được khoá (is_waxed) để người chơi không sửa.

Cách dùng (từ thư mục gốc repo, server đang chạy):
  python3 tools/spawn-signs.py <file.schem> <ox> <oy> <oz> [dy.npy] > /tmp/signs.cmds
  dy.npy (từ place-glasscup.py): độ nâng từng cột; khi có thì y thế giới = y trong file + dy của cột (bỏ qua oy).
  docker exec -i duckymomo-banghoi-1-1 rcon-cli < /tmp/signs.cmds
Sửa giá/món/warp ở BUY, SELL, WARP, INFO rồi chạy lại.
"""
import gzip
import importlib.util
import os
import re
import sys

spec = importlib.util.spec_from_file_location("ps", os.path.join(os.path.dirname(os.path.abspath(__file__)), "place-schem.py"))
ps = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ps)

# Vùng cắt đã dùng khi đặt spawn (toạ độ trong file) và tâm quảng trường (để chia phía)
CROP = (120, 160, 320, 470)
PLAZA = (220, 257)

BUY = [("Lương thực", [(8, "bread", 20), (8, "cooked_beef", 30), (8, "apple", 15), (4, "golden_carrot", 40)]),
       ("Vật liệu", [(64, "cobblestone", 20), (16, "oak_log", 30), (64, "stone_bricks", 40), (16, "glass", 20)]),
       ("Công cụ", [(1, "iron_pickaxe", 60), (1, "iron_axe", 60), (1, "iron_shovel", 40), (16, "torch", 10)]),
       ("Nông nghiệp", [(16, "wheat_seeds", 10), (4, "sugar_cane", 20), (4, "oak_sapling", 10), (16, "bone_meal", 20)]),
       ("Chiến đấu", [(1, "bow", 40), (16, "arrow", 20), (1, "shield", 50), (1, "iron_sword", 60)]),
       ("Giáp sắt", [(1, "iron_helmet", 60), (1, "iron_chestplate", 100), (1, "iron_leggings", 90), (1, "iron_boots", 50)]),
       ("Trang trí", [(4, "lantern", 20), (4, "flower_pot", 10), (16, "white_wool", 20), (64, "oak_planks", 30)]),
       ("Tiện ích", [(1, "white_bed", 30), (1, "crafting_table", 5), (1, "furnace", 10), (1, "chest", 10)])]
# Giá thu mua khớp plugins/Essentials/worth.yml (giá /sell một món x số lượng). Không thu mua thứ farm vô hạn (đá cuội, cát, đất).
SELL = [("Nông sản", [(16, "wheat", 12), (16, "carrot", 10), (16, "potato", 10), (16, "sugar_cane", 16)]),
        ("Khoáng sản", [(16, "coal", 16), (8, "iron_ingot", 32), (4, "gold_ingot", 24), (1, "diamond", 60)]),
        ("Đồ từ quái", [(32, "rotten_flesh", 8), (16, "bone", 8), (16, "string", 12), (8, "gunpowder", 16)]),
        ("Gỗ", [(32, "oak_log", 19), (32, "spruce_log", 19), (32, "birch_log", 19), (32, "jungle_log", 19)])]
# Mùa 1 (Đông Nam Á lục địa + Nam Trung Quốc): chỉ warp nằm trong viền mùa
WARP = [("Tây Bắc - Việt Bắc", ["sapa", "hagiang", "dienbienphu", "caobang"]),
        ("Miền xa", ["luangnamtha", "condao", "naypyidaw", "phuket"]),  # 13/10: bỏ hanoi/haiphong/halong/ninhbinh (nằm trong khu spawn)
        ("Bắc Trung Bộ", ["vinh", "hue", "danang", "hoian"]),
        ("Nam Trung Bộ", ["quynhon", "nhatrang", "pleiku", "buonmathuot"]),
        ("Đông Nam Bộ", ["hcm", "dalat", "vungtau", "cantho"]),
        ("Tây Nam Bộ", ["camau", "phuquoc", "phnompenh", "angkor"]),
        ("Lào - Thái Lan", ["vientiane", "luangprabang", "bangkok", "chiangmai"]),
        ("Myanmar - Trung Quốc", ["yangon", "mandalay", "guangzhou", "hongkong"])]
INFO = [("HƯỚNG DẪN", [["Thị trấn", "/t new <tên>", "/t claim", "/t add <tên>"], ["Quốc gia", "/n new <tên>", "/n add <tên>", "/n ally <tên>"],
                       ["Công thành", "/sw", "Thứ 7, Chủ nhật", "20:00 và 21:00"], ["Nhà", "/sethome", "/home", "/spawn"]]),
        ("KINH TẾ", [["Tiền", "/bal  /baltop", "/pay <tên> <tiền>", "/sell hand"], ["Chợ đen", "/ah", "/ah sell <giá>", "(cầm món đồ)"],
                     ["Đổi đồ", "/trade <tên>", "hai bên cùng", "bấm đồng ý"], ["Cửa hàng rương", "cầm đồ, bấm", "chuột trái vào", "rương, nhập giá"]]),
        ("LUẬT", [["Không hack", "x-ray, fly,", "auto-click", ""], ["Lịch sự", "không chửi bới,", "không quấy rối", ""],
                  ["Không lợi dụng", "lỗi game,", "báo admin", ""], ["Đánh nhau", "theo luật", "công thành", ""]]),
        ("GHI CÔNG", [["Khu spawn", "Oriental Japanese", "Spring Lobby", "by GlassCup"], ["Map Trái Đất", "WorldPainter +", "MinecraftEarthMap", "(DerMattinger)"],
                      ["Dữ liệu", "NASA, ESA,", "USGS, Köppen-", "Geiger, Nat.Earth"], ["", "© OpenStreet-", "Map contributors", ""]])]


def t(s, color=None, bold=False):
    p = [f'text:"{s}"'] + ([f'color:"{color}"'] if color else []) + (["bold:true"] if bold else [])
    return "{" + ",".join(p) + "}"


def strips(path):
    """Các dãy 5 biển gỗ sồi treo trong vùng cắt, sắp theo thứ tự đọc trái -> phải."""
    s = ps.rt.read_nbt(gzip.decompress(open(path, "rb").read()))
    s = s.get("Schematic", s)
    arr, states = ps.load(path)
    x1, z1, x2, z2 = CROP
    signs = []
    for e in s["BlockEntities"]:
        x, y, z = e["Pos"]
        st = states[arr[y, z, x]]
        if e["Id"] == "minecraft:sign" and x1 <= x <= x2 and z1 <= z <= z2 and "oak_wall_sign" in st:
            signs.append(dict(x=x, y=y, z=z, f=re.search(r"facing=(\w+)", st).group(1)))
    pos = {(o["x"], o["y"], o["z"]): i for i, o in enumerate(signs)}
    left, groups = set(range(len(signs))), []
    while left:
        i = left.pop()
        g, q = [i], [i]
        while q:
            o = signs[q.pop()]
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                k = pos.get((o["x"] + dx, o["y"], o["z"] + dz))
                if k in left and signs[k]["f"] == o["f"]:
                    left.remove(k)
                    g.append(k)
                    q.append(k)
        groups.append(g)
    order = {"south": lambda o: o["x"], "north": lambda o: -o["x"], "east": lambda o: -o["z"], "west": lambda o: o["z"]}
    out = {"west": [], "east": [], "north": [], "south": []}
    for g in groups:
        os_ = sorted((signs[i] for i in g), key=order[signs[g[0]]["f"]])
        cx = sum(o["x"] for o in os_) / len(os_)
        cz = sum(o["z"] for o in os_) / len(os_)
        side = "west" if cx < PLAZA[0] - 30 else "east" if cx > PLAZA[0] + 30 else ("north" if cz < PLAZA[1] else "south")
        out[side].append((cz, cx, os_))
    return {k: [v[2] for v in sorted(vals, key=lambda v: (v[0], v[1]))] for k, vals in out.items()}


def main():
    path, ox, oy, oz = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
    dyg = None
    if len(sys.argv) > 5:
        import numpy as np
        dyg = np.load(sys.argv[5])
    st = strips(path)
    cmds = []

    def put(o, lines):
        wy = oy + o["y"] if dyg is None else o["y"] + int(dyg[o["z"], o["x"]])
        cmds.append(f"data merge block {ox + o['x']} {wy} {oz + o['z']} "
                    "{front_text:{messages:[" + ",".join(lines) + "]},is_waxed:1b}")

    def title(kind, name):
        return [t(""), t(kind, "dark_gray"), t(name, "dark_red", True), t("")]

    def ess(k, a, b, c):
        return [t(f"[{k}]", "dark_blue"), t(a), t(b), t(c)]

    for row, (name, items) in zip(st["west"], BUY):
        put(row[0], title("MUA", name))
        for o, (q, it, pr) in zip(row[1:], items):
            put(o, ess("Buy", str(q), it, f"${pr}"))
    for row, (name, items) in zip(st["south"], SELL):
        put(row[0], title("THU MUA", name))
        for o, (q, it, pr) in zip(row[1:], items):
            put(o, ess("Sell", str(q), it, f"${pr}"))
    for row, (name, ws) in zip(st["east"], WARP):
        put(row[0], title("DỊCH CHUYỂN", name))
        for o, w in zip(row[1:], ws):
            put(o, ess("Warp", w, "", ""))
    for row, (name, boards) in zip(st["north"], INFO):
        put(row[0], title("THÔNG TIN", name))
        for o, b in zip(row[1:], boards):
            put(o, [t(b[0], "dark_green", True)] + [t(x) for x in b[1:]])
    x1, z1, x2, z2 = CROP
    area = f"{ox + 140} {oz + 190} {ox + 300} {oz + 330}"
    print(f"forceload add {area}")
    print("\n".join(cmds))
    print(f"forceload remove {area}")
    print(f"{len(cmds)} biển (tây {len(st['west'])}, đông {len(st['east'])}, nam {len(st['south'])}, bắc {len(st['north'])} dãy)", file=sys.stderr)


if __name__ == "__main__":
    main()
