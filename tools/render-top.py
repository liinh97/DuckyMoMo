"""Vẽ ảnh nhìn từ trên xuống (bản đồ màu) của một vùng world Minecraft, đọc thẳng region file (không cần client).

Cách dùng:
  python3 tools/render-top.py <thư mục region> <x giữa> <z giữa> <bán kính> <file .png> [phóng to]
  ví dụ: python3 tools/render-top.py data/banghoi-1/world/dimensions/minecraft/overworld/region 12646 -2512 128 spawn.png 4

Màu theo tên block (bảng COLORS, block lạ thì đoán theo tên), đậm/nhạt theo độ cao để thấy địa hình, nước pha màu theo độ sâu.
Cần Pillow (pip install pillow). Đọc được khi server đang chạy (chỉ đọc; chunk chưa lưu xuống ổ thì là dữ liệu cũ).
"""
import io
import math
import os
import struct
import sys
import zlib

from PIL import Image

# ------------------------------------------------------------------ NBT tối giản (chỉ đọc)

def read_nbt(buf):
    f = io.BytesIO(buf)
    tag = f.read(1)[0]
    f.read(struct.unpack(">H", f.read(2))[0])
    return _payload(f, tag)


def _payload(f, t):
    if t == 1: return struct.unpack(">b", f.read(1))[0]
    if t == 2: return struct.unpack(">h", f.read(2))[0]
    if t == 3: return struct.unpack(">i", f.read(4))[0]
    if t == 4: return struct.unpack(">q", f.read(8))[0]
    if t == 5: return struct.unpack(">f", f.read(4))[0]
    if t == 6: return struct.unpack(">d", f.read(8))[0]
    if t == 7: n = struct.unpack(">i", f.read(4))[0]; return f.read(n)
    if t == 8: n = struct.unpack(">H", f.read(2))[0]; return f.read(n).decode("utf-8", "replace")
    if t == 9:
        et = f.read(1)[0]; n = struct.unpack(">i", f.read(4))[0]
        return [_payload(f, et) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            ct = f.read(1)[0]
            if ct == 0: return d
            name = f.read(struct.unpack(">H", f.read(2))[0]).decode("utf-8", "replace")
            d[name] = _payload(f, ct)
    if t == 11: n = struct.unpack(">i", f.read(4))[0]; return struct.unpack(f">{n}i", f.read(4 * n))
    if t == 12: n = struct.unpack(">i", f.read(4))[0]; return struct.unpack(f">{n}q", f.read(8 * n))
    raise ValueError(f"tag NBT lạ {t}")


def read_chunk(region_dir, cx, cz):
    path = os.path.join(region_dir, f"r.{cx >> 5}.{cz >> 5}.mca")
    if not os.path.exists(path):
        return None
    with open(path, "rb") as f:
        f.seek(((cx & 31) + (cz & 31) * 32) * 4)
        off = struct.unpack(">I", b"\0" + f.read(3))[0]
        if not off:
            return None
        f.seek(off * 4096)
        ln, comp = struct.unpack(">IB", f.read(5))
        raw = f.read(ln - 1)
    return read_nbt(zlib.decompress(raw) if comp == 2 else raw)


# ------------------------------------------------------------------ cột block

AIR = {"air", "cave_air", "void_air", "light", "barrier", "structure_void"}


def section_blocks(sec):
    bs = sec.get("block_states")
    if not bs:
        return None
    pal = [p["Name"].removeprefix("minecraft:") for p in bs["palette"]]
    if len(pal) == 1:
        return pal, None
    bits = max(4, math.ceil(math.log2(len(pal))))
    per = 64 // bits
    mask = (1 << bits) - 1
    idx = []
    for l in bs["data"]:
        u = l & 0xFFFFFFFFFFFFFFFF
        for k in range(per):
            idx.append((u >> (k * bits)) & mask)
    return pal, idx[:4096]


def chunk_top(chunk):
    """Mỗi cột (16x16): (tên block trên cùng không phải không khí, y, độ sâu nước phía trên nó)."""
    secs = []
    for sec in chunk.get("sections", []):
        b = section_blocks(sec)
        if b:
            secs.append((sec["Y"], b))
    secs.sort(key=lambda s: -s[0])
    out = [[None] * 16 for _ in range(16)]
    for z in range(16):
        for x in range(16):
            water = 0
            done = False
            for sy, (pal, idx) in secs:
                for ly in range(15, -1, -1):
                    name = pal[0] if idx is None else pal[idx[ly * 256 + z * 16 + x]]
                    if name in AIR:
                        continue
                    if name in ("water", "bubble_column", "seagrass", "tall_seagrass", "kelp", "kelp_plant"):
                        water += 1
                        continue
                    out[z][x] = (name, sy * 16 + ly, water)
                    done = True
                    break
                if done:
                    break
    return out


# ------------------------------------------------------------------ màu

COLORS = {
    "grass_block": (95, 159, 53), "short_grass": (95, 159, 53), "tall_grass": (95, 159, 53), "fern": (80, 140, 50),
    "dirt": (134, 96, 67), "coarse_dirt": (119, 85, 59), "podzol": (91, 63, 24), "mud": (60, 57, 60), "rooted_dirt": (144, 103, 76),
    "sand": (219, 207, 163), "red_sand": (190, 102, 33), "gravel": (136, 126, 126), "clay": (160, 166, 179),
    "stone": (125, 125, 125), "deepslate": (80, 80, 82), "andesite": (136, 136, 137), "diorite": (188, 188, 188),
    "granite": (149, 103, 85), "cobblestone": (122, 122, 122), "mossy_cobblestone": (110, 118, 94),
    "snow": (249, 254, 254), "snow_block": (249, 254, 254), "ice": (145, 183, 253), "packed_ice": (141, 180, 250),
    "blue_ice": (116, 167, 253), "terracotta": (152, 94, 67), "sandstone": (216, 203, 155),
    "oak_leaves": (60, 120, 30), "birch_leaves": (110, 150, 60), "spruce_leaves": (55, 90, 55), "jungle_leaves": (40, 130, 20),
    "acacia_leaves": (90, 130, 30), "dark_oak_leaves": (40, 90, 20), "mangrove_leaves": (60, 120, 40), "cherry_leaves": (230, 170, 200),
    "azalea_leaves": (90, 120, 50), "oak_log": (109, 85, 50), "spruce_log": (58, 37, 16), "birch_log": (216, 215, 210),
    "jungle_log": (85, 67, 25), "acacia_log": (103, 96, 86), "dark_oak_log": (60, 46, 26),
    "lava": (207, 92, 20), "magma_block": (142, 63, 31), "obsidian": (20, 18, 30),
    "stone_bricks": (122, 121, 122), "polished_andesite": (132, 135, 134), "smooth_stone": (158, 158, 158),
    "bricks": (150, 97, 83), "red_terracotta": (143, 61, 46), "oak_planks": (162, 130, 78), "spruce_planks": (114, 84, 48),
    "dark_oak_planks": (66, 43, 20), "lantern": (230, 200, 120), "glowstone": (230, 200, 120),
    "iron_bars": (110, 110, 120),
    "chiseled_stone_bricks": (119, 118, 119), "red_nether_bricks": (69, 7, 9), "red_nether_brick_slab": (69, 7, 9),
    "red_wool": (161, 39, 34), "yellow_wool": (249, 198, 39), "orange_wool": (240, 118, 19), "white_wool": (233, 236, 236),
    "crying_obsidian": (32, 10, 60), "polished_blackstone_bricks": (48, 42, 49), "polished_blackstone": (53, 48, 56),
    "cracked_polished_blackstone_bricks": (44, 38, 44), "deepslate_tiles": (54, 54, 55), "moss_block": (89, 109, 45),
    "cracked_deepslate_bricks": (64, 64, 65), "deepslate_bricks": (70, 70, 70), "polished_deepslate": (72, 72, 73),
    "lapis_block": (30, 67, 140), "redstone_block": (175, 24, 5), "dirt_path": (148, 122, 65), "coal_block": (16, 15, 15),
    "cracked_nether_bricks": (40, 20, 23), "tuff": (108, 109, 102), "stripped_birch_wood": (196, 176, 118),
    "honeycomb_block": (229, 148, 29), "gold_block": (246, 208, 61), "cherry_planks": (226, 178, 172),
    "cherry_log": (54, 33, 44), "melon": (111, 145, 30), "horn_coral_block": (216, 200, 66), "brain_coral_block": (207, 91, 159),
    "tube_coral_block": (49, 87, 207), "bubble_coral_block": (165, 26, 162), "fire_coral_block": (163, 35, 46), "cocoa": (40, 120, 20), "bamboo": (93, 144, 19), "pink_petals": (247, 181, 219), "bamboo_planks": (194, 173, 80),
    "crimson_planks": (101, 48, 70), "crimson_fence": (101, 48, 70), "mossy_stone_bricks": (115, 121, 105), "barrel": (134, 100, 58),  # WorldPainter vẽ biên giới quốc gia bằng song sắt
}


def color_of(name):
    if name in COLORS:
        return COLORS[name]
    for key, c in (("leaves", (60, 120, 30)), ("log", (100, 80, 50)), ("planks", (150, 120, 75)), ("wool", (230, 230, 230)),
                   ("flower", (220, 80, 80)), ("tulip", (220, 80, 80)), ("concrete", (180, 180, 180)), ("stairs", (130, 130, 130)),
                   ("slab", (150, 150, 150)), ("ore", (125, 125, 125)), ("terracotta", (150, 90, 70)), ("glass", (200, 220, 230)),
                   ("fence", (140, 110, 70)), ("wall", (120, 120, 120)), ("sign", (160, 130, 80)), ("carpet", (200, 60, 60))):
        if key in name:
            return c
    return (170, 0, 170)  # block chưa có màu: tím để dễ thấy


# ------------------------------------------------------------------ vẽ

def render(region_dir, cx0, cz0, radius, out_png, scale=2):
    x0, z0 = cx0 - radius, cz0 - radius
    size = radius * 2
    img = Image.new("RGB", (size, size), (0, 0, 0))
    px = img.load()
    heights = {}
    cols = {}
    for ccx in range(x0 >> 4, (x0 + size - 1 >> 4) + 1):
        for ccz in range(z0 >> 4, (z0 + size - 1 >> 4) + 1):
            ch = read_chunk(region_dir, ccx, ccz)
            if ch is None:
                continue
            top = chunk_top(ch)
            for lz in range(16):
                for lx in range(16):
                    x, z = ccx * 16 + lx, ccz * 16 + lz
                    if x0 <= x < x0 + size and z0 <= z < z0 + size and top[lz][lx]:
                        cols[(x, z)] = top[lz][lx]
                        heights[(x, z)] = top[lz][lx][1] + top[lz][lx][2]
    for (x, z), (name, y, water) in cols.items():
        r, g, b = color_of(name)
        if water:
            depth = min(water, 12) / 12
            r, g, b = (int(r * (0.35 - 0.25 * depth) + 40 * (1 - depth)), int(g * (0.35 - 0.25 * depth) + 90 * (1 - depth) + 20),
                       int(b * (0.3 - 0.2 * depth) + 200 - 60 * depth))
        else:
            north = heights.get((x, z - 1), heights[(x, z)])
            shade = 1.0 + max(-0.25, min(0.25, (heights[(x, z)] - north) * 0.08))
            r, g, b = (min(255, int(r * shade)), min(255, int(g * shade)), min(255, int(b * shade)))
        px[x - x0, z - z0] = (r, g, b)
    if scale > 1:
        img = img.resize((size * scale, size * scale), Image.NEAREST)
    img.save(out_png)
    unknown = sorted({n for n, _, _ in cols.values() if color_of(n) == (170, 0, 170)})
    return len(cols), unknown


if __name__ == "__main__":
    region, x, z, r, out = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), sys.argv[5]
    n, unknown = render(region, x, z, r, out, int(sys.argv[6]) if len(sys.argv) > 6 else 2)
    print(f"{n} cột, ảnh: {out}" + (f" | block chưa có màu (tím): {', '.join(unknown[:20])}" if unknown else ""))
