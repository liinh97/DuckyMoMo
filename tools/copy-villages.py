"""Chép làng Minecraft từ một world PHẲNG (nơi /place structure đặt làng đúng độ cao) sang map Trái Đất, ở chỗ đất bằng.

Vì sao: trên map Trái Đất, làng đặt bằng lệnh bị lơ lửng (game tính độ cao nhà theo bộ sinh địa hình gốc, không theo
địa hình thật). Nên dựng làng trên world phẳng tạm rồi chép block + rương (bảng loot) + dân làng/golem sang.

Bước:
  1. sites: tìm chỗ đất bằng trong vùng mùa (độ lệch mặt đất nhỏ trong bán kính 56 block), tránh nước, khu spawn,
     công trình, viền; mỗi chỗ cách nhau >= 280 block.
       python3 tools/copy-villages.py sites <region world> <x1> <z1> <x2> <z2> <n> <tránh.txt> > sites.txt
       tránh.txt: mỗi dòng "x z bán_kính" (khu spawn, công trình…)
  2. place: đọc làng i ở world phẳng (tâm x_i, mặt đất y -61), sinh lệnh đặt ở chỗ i.
       python3 tools/copy-villages.py place <region world phẳng> <entities world phẳng> villages.txt sites.txt > lệnh.txt
       villages.txt: "chỉ_số x z loại" (tâm làng trên world phẳng); sites.txt: "x z y_mặt_đất"
     Mỗi làng: forceload ±112, chờ, san nền (cột trong khu làng: đắp đất tới mặt đất, dọn mọi thứ phía trên tới +40),
     đặt block (strict), gán bảng loot cho rương/thùng, thả dân làng/golem/mèo.
"""
import importlib.util
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("rt", os.path.join(HERE, "render-top.py"))
rt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rt)

FLAT_GROUND = -61     # mặt cỏ của world phẳng mặc định
R = 104               # bán kính lấy làng (block)
UP = 40               # dọn phía trên mặt đất tới +UP
LEAFY = ("leaves", "_log", "vine", "bamboo", "cocoa")


def state_str(p):
    name = p["Name"]
    props = p.get("Properties")
    if props:
        return name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"
    return name


def ground(region, x, z, cache={}):
    """(y mặt đất bỏ cây, có nước phía trên không)."""
    key = (x >> 4, z >> 4)
    if key not in cache:
        ch = rt.read_chunk(region, *key)
        cache[key] = rt.chunk_top(ch) if ch else None
    top = cache[key]
    if top is None:
        return None
    v = top[z & 15][x & 15]
    return v


def column_ground(region, x, z):
    """Mặt đất thật bỏ qua cây: đi xuống qua lá/thân nếu cột trên cùng là cây (ước lượng từ chunk_top + đọc section)."""
    v = ground(region, x, z)
    if v is None:
        return None, True
    name, y, w = v
    if any(k in name for k in LEAFY):
        return None, False   # dưới tán cây: không đo được nhanh, bỏ qua mẫu này
    return y, w > 0


def cmd_sites(argv):
    region, x1, z1, x2, z2, n, avoid_file = argv[0], *map(int, argv[1:6]), argv[6]
    avoid = [tuple(map(int, l.split())) for l in open(avoid_file) if l.strip()]
    cands = []
    for cx in range(x1 + 120, x2 - 120, 32):
        for cz in range(z1 + 120, z2 - 120, 32):
            if any(math.hypot(cx - ax, cz - az) < ar for ax, az, ar in avoid):
                continue
            hs, wet = [], 0
            for dx in range(-56, 57, 8):
                for dz in range(-56, 57, 8):
                    y, w = column_ground(region, cx + dx, cz + dz)
                    if w:
                        wet += 1
                    elif y is not None:
                        hs.append(y)
            if wet > 3 or len(hs) < 120:
                continue
            hs = np.array(hs)
            g = int(np.median(hs))
            spread = float(np.percentile(hs, 90) - np.percentile(hs, 10))
            cands.append((spread, cx, cz, g))
    cands.sort()
    chosen = []
    for spread, cx, cz, g in cands:
        if all(math.hypot(cx - a, cz - b) >= 280 for a, b, _ in chosen):
            chosen.append((cx, cz, g))
            print(f"{cx} {cz} {g}   # độ lệch mặt đất {spread:.0f}", flush=True)
        if len(chosen) >= n:
            break
    print(f"{len(chosen)} chỗ / {len(cands)} ứng viên", file=sys.stderr)


def read_area(region, ents_dir, x0, z0):
    """Block (dict (dx,y,dz) -> state), block entity (dict -> nbt), thực thể của làng quanh (x0, 0, z0) ở world phẳng."""
    blocks, bents, ents = {}, {}, []
    for cx in range((x0 - R) >> 4, ((x0 + R) >> 4) + 1):
        for cz in range((-R) >> 4, (R >> 4) + 1):
            ch = rt.read_chunk(region, cx, cz)
            if not ch:
                continue
            for sec in ch.get("sections", []):
                bs = sec.get("block_states")
                if not bs:
                    continue
                pal = bs["palette"]
                names = [p["Name"] for p in pal]
                if all(nm in ("minecraft:air", "minecraft:bedrock", "minecraft:dirt") for nm in names):
                    continue
                b = rt.section_blocks(sec)
                pal_s = [state_str(p) for p in pal]
                for i in range(4096):
                    v = 0 if b[1] is None else b[1][i]
                    st = pal_s[v]
                    y = sec["Y"] * 16 + i // 256
                    if y < FLAT_GROUND or st == "minecraft:air":
                        continue
                    if y == FLAT_GROUND and st == "minecraft:grass_block[snowy=false]":
                        continue
                    x, z = cx * 16 + i % 16, cz * 16 + (i // 16) % 16
                    if abs(x - x0) <= R and abs(z) <= R:
                        blocks[(x - x0, y, z)] = st
            for be in ch.get("block_entities", []):
                x, y, z = be["x"], be["y"], be["z"]
                if "LootTable" in be and abs(x - x0) <= R and abs(z) <= R:
                    bents[(x - x0, y, z)] = be["LootTable"]
            e = rt.read_chunk(ents_dir, cx, cz)
            for en in (e or {}).get("Entities", []):
                pos = en.get("Pos")
                if pos and en.get("id") in ("minecraft:villager", "minecraft:iron_golem", "minecraft:cat"):
                    if abs(pos[0] - x0) <= R and abs(pos[2]) <= R:
                        ents.append((en["id"], pos[0] - x0, pos[1], pos[2], en.get("VillagerData")))
    return blocks, bents, ents


def rects(cells):
    """Gộp {(x,z): state} trên một lớp thành các hình chữ nhật (x1,z1,x2,z2,state)."""
    out = []
    by_z = {}
    for (x, z), st in cells.items():
        by_z.setdefault(z, {})[x] = st
    open_r = {}
    for z in sorted(set(by_z) | {max(by_z, default=0) + 1}):
        row = by_z.get(z, {})
        runs = set()
        xs = sorted(row)
        i = 0
        while i < len(xs):
            j = i
            while j + 1 < len(xs) and xs[j + 1] == xs[j] + 1 and row[xs[j + 1]] == row[xs[i]]:
                j += 1
            runs.add((xs[i], xs[j], row[xs[i]]))
            i = j + 1
        for k in list(open_r):
            if k not in runs or open_r[k][1] != z - 1:
                za, zb = open_r.pop(k)
                out.append((k[0], za, k[1], zb, k[2]))
        for k in runs:
            if k in open_r:
                open_r[k] = (open_r[k][0], z)
            else:
                open_r[k] = (z, z)
    for k, (za, zb) in open_r.items():
        out.append((k[0], za, k[1], zb, k[2]))
    return out


def cmd_place(argv):
    region, ents_dir, vfile, sfile = argv
    villages = [l.split()[:4] for l in open(vfile) if l.strip()]
    sites = [l.split("#")[0].split() for l in open(sfile) if l.strip() and not l.startswith("#")]
    for (idx, vx, vz, vtype), (sx, sz, sy) in zip(villages, sites):
        vx, sx, sz, sy = int(vx), int(sx), int(sz), int(sy)
        blocks, bents, ents = read_area(region, ents_dir, vx, 0)
        if not blocks:
            print(f"# làng {idx}: không đọc được block", file=sys.stderr)
            continue
        dy = sy - FLAT_GROUND
        foot = {(x, z) for (x, y, z) in blocks}
        # khu san nền: chân làng nở 2 ô
        pad = {(x + a, z + b) for x, z in foot for a in range(-2, 3) for b in range(-2, 3)}
        print(f"forceload add {sx - 112} {sz - 112} {sx + 112} {sz + 112}")
        print("#WAIT")
        # 1) dọn phía trên mặt đất (cây, gò) trong khu làng
        layers = {}
        for x, z in pad:
            for y in range(sy + 1, sy + UP):
                layers.setdefault(y, {})[(x, z)] = "minecraft:air"
        # 2) đắp nền: cột trong khu làng, từ (mặt đất thật - 12) tới mặt đất làng: đất, trên cùng cỏ
        for x, z in pad:
            for y in range(sy - 12, sy):
                layers.setdefault(y, {})[(x, z)] = "minecraft:dirt"
            layers.setdefault(sy, {})[(x, z)] = "minecraft:grass_block"
        # 3) block của làng
        for (x, y, z), st in blocks.items():
            layers.setdefault(y + dy, {})[(x, z)] = st
        n = 0
        for y in sorted(layers):
            for x1, z1, x2, z2, st in rects(layers[y]):
                X1, X2, Z1, Z2 = sx + x1, sx + x2, sz + z1, sz + z2
                if X1 == X2 and Z1 == Z2:
                    print(f"setblock {X1} {y} {Z1} {st} strict")
                else:
                    print(f"fill {X1} {y} {Z1} {X2} {y} {Z2} {st} strict")
                n += 1
        for (x, y, z), lt in bents.items():
            print(f'data merge block {sx + x} {y + dy} {sz + z} {{LootTable:"{lt}"}}')
        for eid, ex, ey, ez, vd in ents:
            nbt = "PersistenceRequired:1b"
            if eid == "minecraft:villager" and vd:
                nbt += (f',VillagerData:{{profession:"{vd.get("profession", "minecraft:none")}",'
                        f'level:{vd.get("level", 1)},type:"{vd.get("type", "minecraft:plains")}"}}')
            print(f"summon {eid} {sx + ex:.2f} {ey + dy:.2f} {sz + ez:.2f} {{{nbt}}}")
        print(f"forceload remove {sx - 112} {sz - 112} {sx + 112} {sz + 112}")
        print(f"# làng {idx} ({vtype}) -> {sx} {sy} {sz}: {len(blocks)} block, {n} lệnh, {len(bents)} rương, {len(ents)} sinh vật",
              file=sys.stderr)


if __name__ == "__main__":
    {"sites": cmd_sites, "place": cmd_place}[sys.argv[1]](sys.argv[2:])
