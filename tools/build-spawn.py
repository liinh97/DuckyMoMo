"""Sinh lệnh Minecraft (fill/setblock) xây khu spawn Bang Hội Chiến: quảng trường Hà Nội, Hồ Gươm + Tháp Rùa,
cầu Thê Húc + đền Ngọc Sơn, cổng kiểu Khuê Văn Các, chợ (biển [Buy]/[Sell] EssentialsX), tường biển [Warp].

Cách dùng (từ thư mục gốc repo):
  python3 tools/build-spawn.py <x giữa> <y mặt đất> <z giữa> > /tmp/spawn.cmds
  docker exec -i duckymomo-banghoi-1-1 rcon-cli < /tmp/spawn.cmds
Script tự thêm forceload đầu/cuối để chunk được nạp. Hướng: x tăng = Đông, z tăng = Nam. Khu rộng 81 x 81.
Giá trong chợ là tạm, admin sửa trực tiếp trên biển (hoặc sửa SHOPS rồi chạy lại).
Biển EssentialsX chỉ hoạt động khi đã bật trong plugins/Essentials/config.yml (enabledSigns: buy, sell, warp).
"""
import math
import sys

CX, G, CZ = int(sys.argv[1]), int(sys.argv[2]), int(sys.argv[3])
R = 40  # nửa cạnh quảng trường
cmds = []


def a(dx, dz):
    return CX + dx, CZ + dz


def fill(dx1, y1, dz1, dx2, y2, dz2, block, mode=""):
    x1, z1 = a(dx1, dz1)
    x2, z2 = a(dx2, dz2)
    cmds.append(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {block}{' ' + mode if mode else ''}")


def setb(dx, y, dz, block):
    x, z = a(dx, dz)
    cmds.append(f"setblock {x} {y} {z} {block}")


def text(t, color=None, bold=False):
    parts = [f'text:"{t}"']
    if color:
        parts.append(f'color:"{color}"')
    if bold:
        parts.append("bold:true")
    return "{" + ",".join(parts) + "}"


def sign_nbt(lines):
    lines = (lines + ['""'] * 4)[:4]
    return "{front_text:{messages:[" + ",".join(lines) + "]}}"


def wall_sign(dx, y, dz, facing, lines, wood="oak"):
    setb(dx, y, dz, f"minecraft:{wood}_wall_sign[facing={facing}]{sign_nbt(lines)}")


def ess_sign(kind, l2, l3, l4):
    """Biển EssentialsX: dòng đầu [Buy]/[Sell]/[Warp] màu xanh đậm (Essentials nhận biển theo đúng định dạng này)."""
    return [text(f"[{kind}]", "dark_blue"), text(l2), text(l3), text(l4)]


# ------------------------------------------------------------------ nền
x1, z1 = a(-R - 8, -R - 8)
x2, z2 = a(R + 8, R + 8)
cmds.append(f"forceload add {x1} {z1} {x2} {z2}")
for y in range(G + 1, G + 34, 4):  # dọn sạch phía trên (cây, cỏ, đồi nhỏ); mỗi lệnh fill tối đa 32768 block
    fill(-R, y, -R, R, min(y + 3, G + 33), R, "air")
fill(-R, G - 3, -R, R, G - 1, R, "dirt", "replace air")
fill(-R, G - 3, -R, R, G - 1, R, "dirt", "replace water")
fill(-R, G, -R, R, G, R, "polished_andesite")
# viền và trục đường chính
for d in (R - 1, R):
    fill(-R, G, d, R, G, d, "stone_bricks")
    fill(-R, G, -d, R, G, -d, "stone_bricks")
    fill(d, G, -R, d, G, R, "stone_bricks")
    fill(-d, G, -R, -d, G, R, "stone_bricks")
fill(-2, G, -R + 2, 2, G, R - 2, "smooth_stone")
fill(-R + 2, G, -2, R - 2, G, 2, "smooth_stone")

# ------------------------------------------------------------------ Hồ Gươm
LX, LZ, RX, RZ = 0, -21, 14, 9


def lake(dx, dz, grow=0.0):
    return ((dx - LX) / (RX + grow)) ** 2 + ((dz - LZ) / (RZ + grow)) ** 2 <= 1


for dx in range(LX - RX - 3, LX + RX + 4):
    for dz in range(LZ - RZ - 3, LZ + RZ + 4):
        if lake(dx, dz):
            setb(dx, G - 3, dz, "clay")
            fill(dx, G - 2, dz, dx, G, dz, "water")
        elif lake(dx, dz, 1.6):
            setb(dx, G, dz, "stone_bricks")
            if not (-10 <= dx <= -6 and dz > LZ):  # chừa lối lên cầu Thê Húc
                setb(dx, G + 1, dz, "mossy_stone_brick_wall")
# đèn quanh hồ
for i in range(12):
    t = 2 * math.pi * i / 12
    dx, dz = round(LX + (RX + 1.3) * math.cos(t)), round(LZ + (RZ + 1.3) * math.sin(t))
    if not (-10 <= dx <= -6 and dz > LZ):
        setb(dx, G + 2, dz, "lantern")

# Tháp Rùa (đảo nhỏ giữa hồ)
TX, TZ = 4, -22
fill(TX - 1, G - 2, TZ - 1, TX + 1, G, TZ + 1, "mossy_stone_bricks")
fill(TX - 1, G + 1, TZ - 1, TX + 1, G + 3, TZ + 1, "mossy_stone_bricks")
for ddx, ddz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
    setb(TX + ddx, G + 2, TZ + ddz, "air")  # cửa vòm mỗi mặt
fill(TX - 1, G + 4, TZ - 1, TX + 1, G + 4, TZ + 1, "mossy_stone_brick_slab")
fill(TX, G + 4, TZ, TX, G + 5, TZ, "mossy_stone_bricks")
setb(TX, G + 6, TZ, "mossy_stone_brick_wall")
setb(TX, G + 7, TZ, "lantern")

# đền Ngọc Sơn (đảo phía tây bắc) + cầu Thê Húc đỏ
NX, NZ = -8, -24
fill(NX - 2, G - 2, NZ - 2, NX + 2, G, NZ + 2, "stone_bricks")
for ddx in (-2, 2):
    for ddz in (-2, 2):
        fill(NX + ddx, G + 1, NZ + ddz, NX + ddx, G + 3, NZ + ddz, "red_terracotta")
fill(NX - 2, G + 4, NZ - 2, NX + 2, G + 4, NZ + 2, "red_nether_bricks")
fill(NX - 1, G + 5, NZ - 1, NX + 1, G + 5, NZ + 1, "red_nether_bricks")
setb(NX, G + 6, NZ, "red_nether_brick_wall")
setb(NX, G + 7, NZ, "lantern")
setb(NX, G + 1, NZ, "lantern")
bz1, bz2 = NZ + 3, LZ + RZ + 1  # từ đảo tới bờ nam
for dz in range(bz1, bz2 + 1):
    rise = 1 if bz1 + 2 <= dz <= bz2 - 2 else 0  # cầu vồng lên giữa
    fill(NX - 1, G + rise, dz, NX + 1, G + rise, dz, "crimson_planks")
    setb(NX - 2, G + rise + 1, dz, "crimson_fence")
    setb(NX + 2, G + rise + 1, dz, "crimson_fence")

# ------------------------------------------------------------------ điểm spawn (quảng trường giữa)
fill(-1, G, 11, 1, G, 13, "chiseled_stone_bricks")
fill(0, G + 1, 12, 0, G + 3, 12, "chiseled_stone_bricks")
setb(0, G + 4, 12, "lantern")
wall_sign(0, G + 2, 13, "south", [text("Bang Hội Chiến", "dark_red", True), text("Spawn Hà Nội"), text("/warp, /spawn"), text("Chợ: phía Tây", "dark_gray")])
wall_sign(0, G + 2, 11, "north", [text("Hồ Gươm", "dark_green", True), text("Tháp Rùa"), text("Cầu Thê Húc"), text("Cổng warp: phía Đông", "dark_gray")])

# ------------------------------------------------------------------ cổng phía nam (kiểu Khuê Văn Các)
for px in (-5, 4):
    fill(px, G + 1, 37, px + 1, G + 6, 38, "bricks")
fill(-6, G + 7, 36, 6, G + 7, 39, "dark_oak_planks")
fill(-4, G + 8, 37, 4, G + 10, 38, "red_terracotta")
fill(-1, G + 9, 37, 1, G + 9, 38, "yellow_stained_glass")  # "cửa sổ mặt trời"
fill(-7, G + 11, 35, 7, G + 11, 40, "red_nether_bricks")
fill(-5, G + 12, 36, 5, G + 12, 39, "red_nether_bricks")
fill(-2, G + 13, 37, 2, G + 13, 38, "red_nether_brick_slab")
for lx in (-7, 7):
    for lz in (35, 40):
        setb(lx, G + 10, lz, "lantern[hanging=true]")
wall_sign(0, G + 8, 39, "south", [text("HÀ NỘI", "dark_red", True), text("Bang Hội Chiến"), text(""), text("Map Trái Đất 1:1000", "dark_gray")], "dark_oak")

# ------------------------------------------------------------------ chợ (phía tây)
fill(-26, G, 3, -24, G, 33, "smooth_stone")  # lối đi giữa chợ
WOOL = ["red_wool", "yellow_wool", "orange_wool", "white_wool"]
# (tên sạp, biển 1, biển 2): biển = (loại, số lượng, vật phẩm, giá)
SHOPS = [
    ("Lương thực", ("Buy", 8, "bread", 20), ("Buy", 8, "cooked_beef", 30)),
    ("Vật liệu", ("Buy", 64, "cobblestone", 20), ("Buy", 16, "oak_log", 30)),
    ("Công cụ", ("Buy", 1, "iron_pickaxe", 60), ("Buy", 16, "torch", 10)),
    ("Nông nghiệp", ("Buy", 16, "wheat_seeds", 10), ("Buy", 4, "sugar_cane", 20)),
    ("Cung tên", ("Buy", 1, "bow", 40), ("Buy", 16, "arrow", 20)),
    ("Thu mua nông sản", ("Sell", 16, "wheat", 12), ("Sell", 16, "carrot", 10)),
    ("Thu mua khoáng", ("Sell", 8, "iron_ingot", 40), ("Sell", 1, "diamond", 60)),
    ("Thu mua khác", ("Sell", 16, "coal", 15), ("Sell", 16, "rotten_flesh", 4)),
]
stalls = []
for i, z0 in enumerate((5, 12, 19, 26)):
    stalls.append((-34, -30, z0, "east", -29))   # dãy tây, mặt quay sang lối đi (đông)
    stalls.append((-20, -16, z0, "west", -21))   # dãy đông, mặt quay sang lối đi (tây)
for n, ((xa, xb, z0, facing, sx), shop) in enumerate(zip(stalls, SHOPS)):
    front = xb if facing == "east" else xa
    for cx_ in (xa, xb):
        for cz_ in (z0, z0 + 4):
            fill(cx_, G + 1, cz_, cx_, G + 3, cz_, "oak_fence")
    fill(xa, G + 4, z0, xb, G + 4, z0 + 4, WOOL[n % 4])
    fill(front, G + 1, z0 + 1, front, G + 1, z0 + 3, "barrel")
    setb(front, G + 3, z0 + 2, "oak_planks")
    wall_sign(sx, G + 3, z0 + 2, facing, [text(""), text(shop[0], "dark_red", True), text("")])
    for (kind, qty, item, price), dz in ((shop[1], z0 + 1), (shop[2], z0 + 3)):
        wall_sign(sx, G + 1, dz, facing, ess_sign(kind, str(qty), item, f"${price}"))
wall_sign(-25, G + 1, 34, "north", [text("CHỢ", "dark_red", True), text("Bấm biển để mua/bán"), text("Bán nhanh: /sell hand"), text("Xem giá: /worth", "dark_gray")])
setb(-25, G, 34, "stone_bricks")

# ------------------------------------------------------------------ cổng dịch chuyển (phía đông): tường biển [Warp]
fill(34, G + 1, 5, 34, G + 5, 31, "stone_bricks")
fill(28, G + 6, 4, 35, G + 6, 32, "red_nether_brick_slab")
for pz in (4, 10, 16, 22, 28, 32):
    fill(28, G + 1, pz, 28, G + 5, pz, "dark_oak_log")
WARPS = [
    ["hanoi", "hcm", "haiphong", "halong", "sapa", "hue", "danang", "hoian", "nhatrang", "dalat", "cantho", "phuquoc"],
    ["bangkok", "angkor", "singapore", "kualalumpur", "jakarta", "manila", "beijing", "tokyo", "seoul", "newdelhi", "dubai", "everest"],
    ["paris", "london", "rome", "moscow", "cairo", "giza", "newyork", "losangeles", "rio", "sydney", "fuji", "grandcanyon"],
]
for row, (names, y) in enumerate(zip(WARPS, (G + 4, G + 3, G + 2))):
    for i, name in enumerate(names):
        wall_sign(33, y, 7 + i * 2, "west", ess_sign("Warp", name, "", ""))
wall_sign(33, G + 5, 18, "west", [text("CỔNG DỊCH CHUYỂN", "dark_purple", True), text("Bấm biển [Warp]"), text("Trên: Việt Nam"), text("Giữa: châu Á · Dưới: thế giới", "dark_gray")])

# ------------------------------------------------------------------ đèn dọc đường
for d in range(-R + 6, R - 5, 8):
    for side in (-4, 4):
        if not (LZ - RZ - 3 <= d <= LZ + RZ + 3) and abs(d - 12) > 2:
            setb(side, G + 1, d, "stone_brick_wall")
            setb(side, G + 2, d, "lantern")
        if abs(d) > 4:
            setb(d, G + 1, side, "stone_brick_wall")
            setb(d, G + 2, side, "lantern")

cmds.append(f"forceload remove {x1} {z1} {x2} {z2}")
print("\n".join(cmds))
