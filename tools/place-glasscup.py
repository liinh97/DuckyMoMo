"""Đặt PHẦN CÔNG TRÌNH của một map tải về (.schem, ví dụ "Oriental Japanese Spring Lobby" của GlassCup) lên địa hình
thật của world, bỏ phần đất đảo của map; xung quanh để địa hình/cây cối tự nhiên.

Cách làm:
  - Phân loại block: đất/đá/cỏ của đảo (TERRAIN) và nguồn sáng ẩn "light" bị bỏ; còn lại là công trình; cây anh đào
    (TREE) giữ, đi theo công trình nếu nằm trong chân công trình, nếu không thì đi theo gốc cây gần nhất.
  - Tách cụm: mỗi công trình (chân công trình liền nhau trên mặt bằng) và mỗi cây anh đào là một cụm.
  - Mỗi cụm nâng/hạ cả khối (dy) cho mặt đất của đảo dưới cụm khớp mặt đất thật (trung vị); mặt nước tính là mặt đất.
  - Trong chân công trình: đào bỏ phần đất/cây tự nhiên chen vào khối công trình, dọn cây phía trên; cột nào công trình
    vốn đặt sát đất đảo thì đắp móng (FOUNDATION) xuống tới đất thật hoặc đáy biển.
Lệnh ra theo từng ô 192 x 192 block (forceload từng ô, tối đa 144 chunk), dùng "strict" như place-schem.py.

Cách dùng (từ thư mục gốc repo):
  python3 tools/place-glasscup.py <file.schem> <region map GỐC (đất tự nhiên)> <ox> <oz> <file lệnh> <file dy.npy> [x1 z1 x2 z2]
  ox oz: góc (x, z nhỏ nhất) của file trong world. x1 z1 x2 z2 (toạ độ trong file, tuỳ chọn): chỉ đặt phần giữa; công
  trình/cây có tâm nằm trong vùng thì giữ cả khối, trang trí mặt đất đi theo công trình gần nhất (spawn hiện tại: 120 160 320 470). dy.npy: độ nâng của từng cột (dùng cho spawn-signs.py, biển...).
Map gốc (tools/earthmap/work/out/earth_1-1000-populate) dùng để đo mặt đất thật (chưa có cây). Trước khi chạy lệnh nên
trả vùng về map gốc bằng tools/restore-chunks.py (xoá bản đặt cũ); chunk được Minecraft trang trí lại khi forceload.
"""
import collections
import importlib.util
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("ps", os.path.join(HERE, "place-schem.py"))
ps = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ps)

TERRAIN = {"stone", "dirt", "grass_block", "coarse_dirt", "rooted_dirt", "podzol", "mud", "moss_block", "moss_carpet", "gravel",
           "sand", "clay", "short_grass", "tall_grass", "fern", "large_fern", "bedrock", "tuff", "granite", "diorite", "andesite",
           "deepslate", "snow", "snow_block", "dirt_path"}
GROUND = TERRAIN - {"short_grass", "tall_grass", "fern", "large_fern", "moss_carpet", "snow"}
SKIP_NAMES = {"air", "cave_air", "void_air", "structure_void", "light"}
TREE = {"cherry_log", "cherry_leaves", "cherry_wood", "pink_petals"}
FOUNDATION = "minecraft:stone_bricks"
TILE = 192


def label(mask, conn8=True):
    """Gán nhãn thành phần liên thông trên lưới 2D (BFS)."""
    L, W = mask.shape
    lab = np.zeros((L, W), np.int32)
    n = 0
    nb = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)] if conn8 else [(-1, 0), (1, 0), (0, -1), (0, 1)]
    for z0, x0 in zip(*np.nonzero(mask)):
        if lab[z0, x0]:
            continue
        n += 1
        lab[z0, x0] = n
        q = collections.deque([(z0, x0)])
        while q:
            z, x = q.popleft()
            for dz, dx in nb:
                zz, xx = z + dz, x + dx
                if 0 <= zz < L and 0 <= xx < W and mask[zz, xx] and not lab[zz, xx]:
                    lab[zz, xx] = n
                    q.append((zz, xx))
    return lab, n


def ground_map(region, ox, oz, W, L):
    """Mặt đất thật (mặt nước nếu có nước) và đáy (block dưới nước) từ map gốc."""
    wg = np.full((L, W), -999, np.int32)
    bed = np.full((L, W), -999, np.int32)
    for cx in range(ox >> 4, ((ox + W - 1) >> 4) + 1):
        for cz in range(oz >> 4, ((oz + L - 1) >> 4) + 1):
            ch = ps.rt.read_chunk(region, cx, cz)
            if not ch:
                continue
            top = ps.rt.chunk_top(ch)
            for lz in range(16):
                for lx in range(16):
                    x, z = cx * 16 + lx - ox, cz * 16 + lz - oz
                    if 0 <= x < W and 0 <= z < L and top[lz][lx]:
                        _, y, w = top[lz][lx]
                        bed[z, x] = y
                        wg[z, x] = y + w
    return wg, bed


def main():
    path, region, ox, oz, out_cmds, out_dy = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5], sys.argv[6]
    crop = tuple(map(int, sys.argv[7:11])) if len(sys.argv) > 10 else None
    arr, states = ps.load(path)
    H, L, W = arr.shape
    names = np.array([s.split("[")[0].removeprefix("minecraft:") for s in states])
    is_skip = np.isin(names, list(SKIP_NAMES))
    is_terr = np.isin(names, list(TERRAIN))
    is_ground = np.isin(names, list(GROUND))
    is_tree = np.isin(names, list(TREE))
    struct = ~(is_skip | is_terr | is_tree)[arr]
    tree = is_tree[arr]
    keep = struct | tree
    print(f"giữ {int(keep.sum()):,} block (công trình {int(struct.sum()):,}, cây anh đào {int(tree.sum()):,})", file=sys.stderr)

    # mặt đất của đảo trong file: block đất cao nhất của cột
    g3 = is_ground[arr]
    sg = np.where(g3.any(axis=0), H - 1 - np.argmax(g3[::-1], axis=0), -1)
    fp_s = struct.any(axis=0)
    fp_t = tree.any(axis=0) & ~fp_s
    low = np.where(keep.any(axis=0), np.argmax(keep, axis=0), -1)     # block giữ lại thấp nhất của cột

    # cụm công trình: chỉ từ cột "cao" (công trình cao hơn mặt đảo > 2 block, hoặc không có đất đảo bên dưới);
    # đường lát, nước, đá rải sát mặt đảo KHÔNG nối các công trình lại với nhau.
    top_s = np.where(fp_s, H - 1 - np.argmax(struct[::-1], axis=0), -1)
    tall = fp_s & ((sg < 0) | (top_s - sg > 2))
    dil = tall.copy()
    for dz in (-1, 0, 1):
        for dx in (-1, 0, 1):
            dil |= np.roll(np.roll(tall, dz, 0), dx, 1)
    labS, nS = label(dil)
    labS[~tall] = 0
    # cột thấp sát công trình (<= 3 ô) đi theo công trình đó; còn lại là "trang trí mặt đất", bám mặt đất từng cột
    q = collections.deque((z, x, 0) for z, x in zip(*np.nonzero(labS)))
    while q:
        z, x, d = q.popleft()
        if d >= 3:
            continue
        for dz, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            zz, xx = z + dz, x + dx
            if 0 <= zz < L and 0 <= xx < W and fp_s[zz, xx] and not labS[zz, xx]:
                labS[zz, xx] = labS[z, x]
                q.append((zz, xx, d + 1))
    decor = fp_s & (labS == 0)
    # cây: gốc (cột có thân cây anh đào ngoài chân công trình), lá gán cho gốc gần nhất (BFS tối đa 8 ô)
    trunk = (names[arr] == "cherry_log").any(axis=0) & fp_t
    labT, nT = label(trunk)
    lab = labS.copy()
    lab[labT > 0] = labT[labT > 0] + nS
    DECOR = nS + nT + 1
    lab[decor] = DECOR
    q = collections.deque((z, x, 0) for z, x in zip(*np.nonzero(labT)))
    while q:
        z, x, d = q.popleft()
        if d >= 8:
            continue
        for dz, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            zz, xx = z + dz, x + dx
            if 0 <= zz < L and 0 <= xx < W and fp_t[zz, xx] and not lab[zz, xx]:
                lab[zz, xx] = lab[z, x]
                q.append((zz, xx, d + 1))
    print(f"{nS} cụm công trình, {nT} cây anh đào, {int(decor.sum()):,} cột trang trí mặt đất; cột không thuộc cụm nào (bỏ): {int(((fp_s | fp_t) & (lab == 0)).sum())}", file=sys.stderr)

    if crop:
        x1, z1, x2, z2 = crop
        zz, xx = np.indices((L, W))
        inside = (xx >= x1) & (xx <= x2) & (zz >= z1) & (zz <= z2)
        for l in range(1, nS + nT + 1):
            src = (labS == l) if l <= nS else (labT == l - nS)
            cz, cx = np.nonzero(src)
            if len(cx) and not (x1 <= cx.mean() <= x2 and z1 <= cz.mean() <= z2):
                lab[lab == l] = 0
        # trang trí mặt đất: trong vùng VÀ công trình gần nhất (BFS từ mọi cụm) được giữ
        kept = set(np.unique(lab[(lab > 0) & (lab <= nS)]).tolist())
        near = np.zeros((L, W), np.int32)
        near[labS > 0] = labS[labS > 0]
        q = collections.deque((z, x, 0) for z, x in zip(*np.nonzero(labS)))
        while q:
            z, x, d = q.popleft()
            if d >= 40:
                continue
            for dz, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                zz, xx = z + dz, x + dx
                if 0 <= zz < L and 0 <= xx < W and not near[zz, xx]:
                    near[zz, xx] = near[z, x]
                    q.append((zz, xx, d + 1))
        keep_d = inside & np.where(near > 0, np.isin(near, list(kept)), True)
        lab[(lab == DECOR) & ~keep_d] = 0
        decor &= keep_d
        print(f"cắt vùng {crop}: giữ {len(set(np.unique(lab)) - {0, DECOR})} cụm", file=sys.stderr)

    wg, bed = ground_map(region, ox, oz, W, L)
    nlab = nS + nT
    dy = np.zeros(nlab + 2, np.int32)
    for l in range(1, nlab + 1):
        cols = lab == l
        ok = cols & (wg > -999)
        if not ok.any():
            continue
        base = np.where(sg >= 0, sg, low - 1)
        if l > nS:  # cây: gốc cây ngồi trên mặt đất thật
            base = low - 1
        v = (wg - base)[ok]
        dy[l] = int(np.median(v))
    base_col = np.where(sg >= 0, sg, low - 1)
    dygrid = np.where(lab > 0, dy[lab], -10000).astype(np.int32)
    dygrid[decor] = np.where(wg[decor] > -999, wg[decor] - base_col[decor], -10000)
    np.save(out_dy, dygrid)
    big = sorted(((int((labS == l).sum()), l) for l in range(1, nS + 1)), reverse=True)[:6]
    print("cụm lớn nhất (số cột, dy):", [(c, int(dy[l])) for c, l in big], file=sys.stderr)

    # dựng các lớp theo y thế giới
    AIR = len(states)
    FOUND = AIR + 1
    out_states = states + ["minecraft:air", FOUNDATION, "minecraft:dirt"]
    DIRT = AIR + 2
    SKIP = -1
    layers = {}

    def put(wy, z, x, v):
        if -64 <= wy <= 319:
            a = layers.get(wy)
            if a is None:
                a = layers[wy] = np.full((L, W), SKIP, np.int32)
            a[z, x] = v

    for z, x in zip(*np.nonzero(lab)):
        l = lab[z, x]
        d = int(dygrid[z, x])
        if d <= -10000:
            continue
        col = arr[:, z, x]
        kcol = tree[:, z, x] if nS < l < DECOR else keep[:, z, x]
        ys = np.nonzero(kcol)[0]
        if not len(ys):
            continue
        floor, top = int(ys[0]), int(ys[-1])
        for y in range(floor, top + 1):
            put(y + d, z, x, int(col[y]) if kcol[y] else AIR)
        g = int(wg[z, x])
        if l <= nS or l == DECOR:
            # dọn cây tự nhiên phía trên công trình / mặt đất (tới mặt đất + 45)
            for wy in range(max(top + d, g) + 1, g + 46):
                put(wy, z, x, AIR)
            # móng: cột vốn đặt sát đất đảo, nay hở chân
            base_gap = floor - sg[z, x] if sg[z, x] >= 0 else 0
            if base_gap <= 2 and g > -999:
                lo = int(bed[z, x]) + 1 if wg[z, x] > bed[z, x] else g + 1
                for wy in range(lo, floor + d):
                    put(wy, z, x, FOUND)
        else:
            if trunk[z, x] and g > -999:
                for wy in range(g + 1, floor + d):
                    put(wy, z, x, DIRT)

    # gộp thành hình chữ nhật trên từng lớp, chia theo ô TILE x TILE để forceload
    tiles = collections.defaultdict(list)
    for wy in sorted(layers):
        a = layers[wy]
        open_r, rects = {}, []
        for z in range(L + 1):
            runs = set()
            if z < L:
                row = a[z]
                x = 0
                while x < W:
                    v = row[x]
                    xe = x
                    while xe + 1 < W and row[xe + 1] == v:
                        xe += 1
                    if v != SKIP:
                        runs.add((x, xe, int(v)))
                    x = xe + 1
            for k in list(open_r):
                if k not in runs:
                    rects.append((k, open_r.pop(k), z - 1))
            for k in runs:
                open_r.setdefault(k, z)
        for (xa, xb, v), za, zb in rects:
            for tx in range((ox + xa) // TILE, (ox + xb) // TILE + 1):
                for tz in range((oz + za) // TILE, (oz + zb) // TILE + 1):
                    X1, X2 = max(ox + xa, tx * TILE), min(ox + xb, tx * TILE + TILE - 1)
                    Z1, Z2 = max(oz + za, tz * TILE), min(oz + zb, tz * TILE + TILE - 1)
                    w = X2 - X1 + 1
                    rows = max(1, ps.MAX_FILL // w)
                    for zs in range(Z1, Z2 + 1, rows):
                        ze = min(Z2, zs + rows - 1)
                        st = out_states[v]
                        if X1 == X2 and zs == ze:
                            tiles[(tx, tz)].append(f"setblock {X1} {wy} {zs} {st} strict")
                        else:
                            tiles[(tx, tz)].append(f"fill {X1} {wy} {zs} {X2} {wy} {ze} {st} strict")
    n = 0
    with open(out_cmds, "w") as f:
        for (tx, tz), cmds in sorted(tiles.items()):
            area = f"{tx * TILE} {tz * TILE} {tx * TILE + TILE - 1} {tz * TILE + TILE - 1}"
            f.write(f"forceload add {area}\n#WAIT\n")
            f.write("\n".join(cmds) + "\n")
            f.write(f"forceload remove {area}\n")
            n += len(cmds)
    print(f"{n:,} lệnh trong {len(tiles)} ô; world x {ox}..{ox + W - 1}, z {oz}..{oz + L - 1}", file=sys.stderr)


if __name__ == "__main__":
    main()
