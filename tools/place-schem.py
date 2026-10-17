"""Đặt một phần (hoặc toàn bộ) file WorldEdit .schem (Sponge v2/v3) vào world bằng lệnh console fill/setblock,
không cần người chơi trong game và không bị hết RAM như //paste với công trình lớn.

Cách dùng (từ thư mục gốc repo):
  python3 tools/place-schem.py <file.schem> <x1> <z1> <x2> <z2> <ox> <oy> <oz> [trần_không_khí] > /tmp/place.cmds
  docker exec -i duckymomo-banghoi-1-1 rcon-cli < /tmp/place.cmds
  - x1 z1 x2 z2: vùng cắt trong file (toạ độ trong schematic, tính cả hai đầu).
  - ox oy oz: block (0,0,0) của file nằm ở đâu trong world (world = o + toạ độ trong file).
  - Y_MIN=.. Y_MAX=.. (biến môi trường, tuỳ chọn): chỉ đặt các lớp này của file (vd bỏ phần đất/hang bên dưới sân).
  - trần_không_khí (mặc định 100): ô không khí của file chỉ được đặt (để dọn đất/cây cũ) khi world y <= trần này;
    phía trên đó world vốn đã trống, bỏ qua cho đỡ lệnh.
Quy tắc: mỗi cột chỉ đặt từ block thấp nhất của file trở lên, nên phần đất phía dưới đáy công trình giữ nguyên
(không khoét lỗ dưới đảo nổi). Gộp block giống nhau thành hình chữ nhật trên từng lớp y, dùng "strict" để không kích
hoạt cập nhật lân cận (cát không rơi, nước không chảy khi đặt dở). Không chép dữ liệu block entity (biển, rương…).
Cần numpy.
"""
import gzip
import importlib.util
import math
import os
import sys

import numpy as np

spec = importlib.util.spec_from_file_location("rt", os.path.join(os.path.dirname(os.path.abspath(__file__)), "render-top.py"))
rt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rt)

MAX_FILL = 32768
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void"}


def load(path):
    s = rt.read_nbt(gzip.decompress(open(path, "rb").read()))
    s = s.get("Schematic", s)
    blocks = s.get("Blocks", s)  # v3 để trong "Blocks"
    pal = blocks["Palette"]
    data = blocks["Data"] if "Data" in blocks else s["BlockData"]
    W, H, L = s["Width"], s["Height"], s["Length"]
    b = np.frombuffer(data, dtype=np.uint8)
    ends = np.nonzero((b & 0x80) == 0)[0]
    starts = np.concatenate(([0], ends[:-1] + 1))
    vals = np.zeros(len(ends), dtype=np.int64)
    for k in range(int((ends - starts).max()) + 1):  # varint nhiều byte
        m = (ends - starts) >= k
        vals[m] |= (b[starts[m] + k].astype(np.int64) & 0x7F) << (7 * k)
    assert len(vals) == W * H * L
    states = [None] * len(pal)
    for name, i in pal.items():
        states[i] = name
    return vals.reshape(H, L, W), states


def main():
    path = sys.argv[1]
    x1, z1, x2, z2, ox, oy, oz = map(int, sys.argv[2:9])
    air_max = int(sys.argv[9]) if len(sys.argv) > 9 else 100
    arr, states = load(path)
    # Cắt theo chiều cao (tuỳ chọn): biến môi trường Y_MIN / Y_MAX = lớp trong file (tính cả hai đầu); oy vẫn ứng với lớp 0 của file
    ymin, ymax = int(os.environ.get("Y_MIN", 0)), int(os.environ.get("Y_MAX", arr.shape[0] - 1))
    if ymin > 0 or ymax < arr.shape[0] - 1:
        arr = arr.copy()
        is_air0 = [i for i, st in enumerate(states) if st in AIR][0]
        arr[:ymin] = is_air0
        arr[ymax + 1:] = is_air0
    H = arr.shape[0]
    crop = arr[:, z1:z2 + 1, x1:x2 + 1]
    is_air = np.array([s in AIR for s in states])
    solid = ~is_air[crop]
    bottom = np.where(solid.any(axis=0), np.argmax(solid, axis=0), H)  # cột trống: bỏ cả cột
    AIR_ID = len(states)
    states_out = states + ["minecraft:air"]
    SKIP = -1
    out = []
    wx1, wz1, wx2, wz2 = ox + x1, oz + z1, ox + x2, oz + z2
    # forceload theo từng nhóm tối đa 256 chunk
    cx1, cx2, cz1, cz2 = wx1 >> 4, wx2 >> 4, wz1 >> 4, wz2 >> 4
    per = max(1, 256 // (cx2 - cx1 + 1))
    groups = [(cz, min(cz + per - 1, cz2)) for cz in range(cz1, cz2 + 1, per)]
    for a, b in groups:
        out.append(f"forceload add {cx1 * 16} {a * 16} {cx2 * 16 + 15} {b * 16 + 15}")
    n_fill = 0
    for y in range(H):
        wy = oy + y
        if wy < -64 or wy > 319:
            continue
        layer = crop[y].astype(np.int64)
        target = np.where(is_air[layer], AIR_ID, layer)
        target[y < bottom] = SKIP
        if wy > air_max:
            target[target == AIR_ID] = SKIP
        # chạy theo x trên từng hàng z, rồi gộp các hàng z liên tiếp có cùng đoạn
        open_rects = {}  # (xa, xb, id) -> z bắt đầu
        rects = []
        Lz, Wx = target.shape
        for z in range(Lz + 1):
            runs = set()
            if z < Lz:
                row = target[z]
                x = 0
                while x < Wx:
                    v = row[x]
                    xe = x
                    while xe + 1 < Wx and row[xe + 1] == v:
                        xe += 1
                    if v != SKIP:
                        runs.add((x, xe, int(v)))
                    x = xe + 1
            for key in list(open_rects):
                if key not in runs:
                    rects.append((key, open_rects.pop(key), z - 1))
            for key in runs:
                open_rects.setdefault(key, z)
        for (xa, xb, v), za, zb in rects:
            st = states_out[v]
            w = xb - xa + 1
            rows = max(1, MAX_FILL // w)
            for zs in range(za, zb + 1, rows):
                ze = min(zb, zs + rows - 1)
                X1, X2, Z1, Z2 = ox + x1 + xa, ox + x1 + xb, oz + z1 + zs, oz + z1 + ze
                if X1 == X2 and Z1 == Z2:
                    out.append(f"setblock {X1} {wy} {Z1} {st} strict")
                else:
                    out.append(f"fill {X1} {wy} {Z1} {X2} {wy} {Z2} {st} strict")
                n_fill += 1
    for a, b in groups:
        out.append(f"forceload remove {cx1 * 16} {a * 16} {cx2 * 16 + 15} {b * 16 + 15}")
    print("\n".join(out))
    print(f"{n_fill} lệnh đặt block, vùng world x {wx1}..{wx2}, z {wz1}..{wz2}, y {oy}..{oy + H - 1}", file=sys.stderr)


if __name__ == "__main__":
    main()
