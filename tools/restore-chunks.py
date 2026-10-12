"""Trả một số chunk của world về như map gốc (WorldPainter), để Minecraft sinh lại cây cỏ/động vật khi nạp.

Dùng khi một vùng bị đặt hỏng (ví dụ làng lơ lửng). Chép dữ liệu chunk từ region của map gốc vào region của world,
và XOÁ dữ liệu chunk đó trong entities/ (thực thể: dân làng, động vật…) và poi/ (điểm làng: giường, chỗ làm việc).
PHẢI TẮT server trước (server đang chạy sẽ ghi đè).

Cách dùng (từ thư mục gốc repo):
  python3 tools/restore-chunks.py <thư mục world gốc> <thư mục world> <danh sách "x z bán_kính_chunk" (block)>
  ví dụ: python3 tools/restore-chunks.py tools/earthmap/work/out/earth_1-1000-populate data/banghoi-1/world villages.txt
  mỗi dòng của danh sách: <x> <z> <bán kính tính bằng chunk>  (vùng vuông quanh điểm)
"""
import os
import struct
import sys
import time
from collections import defaultdict

DIM = os.path.join("dimensions", "minecraft", "overworld")


def read_region(path):
    """dict (i 0..1023) -> (bytes dữ liệu chunk gồm 5 byte đầu, timestamp)."""
    out = {}
    if not os.path.exists(path):
        return out
    with open(path, "rb") as f:
        data = f.read()
    if len(data) < 8192:
        return out
    for i in range(1024):
        off = struct.unpack(">I", b"\0" + data[i * 4:i * 4 + 3])[0]
        if not off:
            continue
        p = off * 4096
        ln = struct.unpack(">I", data[p:p + 4])[0]
        ts = struct.unpack(">I", data[4096 + i * 4:4096 + i * 4 + 4])[0]
        out[i] = (data[p:p + 4 + ln], ts)
    return out


def write_region(path, chunks):
    loc = bytearray(4096)
    tss = bytearray(4096)
    body = bytearray()
    sector = 2
    for i in sorted(chunks):
        raw, ts = chunks[i]
        n = (len(raw) + 4095) // 4096
        if n > 255:
            raise ValueError(f"chunk {i} quá lớn ({len(raw)} byte)")
        loc[i * 4:i * 4 + 4] = struct.pack(">I", (sector << 8) | n)
        tss[i * 4:i * 4 + 4] = struct.pack(">I", ts)
        body += raw + b"\0" * (n * 4096 - len(raw))
        sector += n
    tmp = path + ".tmp"
    with open(tmp, "wb") as f:
        f.write(loc + tss + body)
    os.replace(tmp, path)


def main():
    src, dst, listfile = sys.argv[1:4]
    want = set()
    for line in open(listfile):
        p = line.split()
        if len(p) < 3:
            continue
        x, z, r = int(p[0]), int(p[1]), int(p[2])
        for cx in range((x >> 4) - r, (x >> 4) + r + 1):
            for cz in range((z >> 4) - r, (z >> 4) + r + 1):
                want.add((cx, cz))
    by_region = defaultdict(set)
    for cx, cz in want:
        by_region[(cx >> 5, cz >> 5)].add((cx, cz))
    now = int(time.time())
    restored = removed_e = removed_p = 0
    for (rx, rz), cs in sorted(by_region.items()):
        name = f"r.{rx}.{rz}.mca"
        src_chunks = read_region(os.path.join(src, DIM, "region", name))
        dst_path = os.path.join(dst, DIM, "region", name)
        dst_chunks = read_region(dst_path)
        for cx, cz in cs:
            i = (cx & 31) + (cz & 31) * 32
            if i in src_chunks:
                dst_chunks[i] = (src_chunks[i][0], now)
                restored += 1
            else:
                dst_chunks.pop(i, None)
        write_region(dst_path, dst_chunks)
        for sub in ("entities", "poi"):
            p = os.path.join(dst, DIM, sub, name)
            if not os.path.exists(p):
                continue
            ch = read_region(p)
            before = len(ch)
            for cx, cz in cs:
                ch.pop((cx & 31) + (cz & 31) * 32, None)
            if len(ch) != before:
                write_region(p, ch)
                if sub == "entities":
                    removed_e += before - len(ch)
                else:
                    removed_p += before - len(ch)
    print(f"Đã trả {restored} chunk về map gốc ({len(by_region)} file region); xoá dữ liệu entities {removed_e}, poi {removed_p} chunk")


if __name__ == "__main__":
    main()
