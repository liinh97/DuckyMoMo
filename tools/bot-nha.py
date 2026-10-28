"""Dựng nhà cho bot trong ô đất thị trấn Towny rồi cho bot đứng trong nhà (chỗ đứng mặc định) — 28/10, chủ server:
"xây vài cái nhà trong town rồi đứng trong nhà, đấy là điểm mà bot đứng luôn".

Cách dùng (từ thư mục gốc repo, trên máy chạy server):
  python3 tools/bot-nha.py <tên thị trấn> <bot thị trưởng> [bot thành viên ...]
Ô homeblock của thị trấn (16×16) chia lưới 2×2 chỗ nhà 5×5 (tối đa 4 bot). Mỗi nhà: đo mặt đất (marker trên heightmap
motion_blocking_no_leaves, vì /place của game làm nhà lơ lửng trên map này), san nền, sàn + tường gỗ ngẫu nhiên, cột gỗ
góc, mái 2 tầng, cửa, 3 cửa sổ, đèn lồng (quái không sinh trong nhà), đồ đạc ngẫu nhiên (không nhà nào giống hệt).
Bot được tp vào giữa nhà rồi botfarm "sethome" (chỗ mặc định = trong nhà). Lệnh RCON gửi từng lệnh một.
"""
import json
import random
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOWNY = ROOT / "data" / "banghoi-1" / "plugins" / "Towny" / "data" / "towns"
WOODS = ["oak", "spruce", "birch", "dark_oak", "acacia", "cherry"]
ROOFS = ["spruce", "dark_oak", "oak", "mangrove", "stone_brick", "brick"]
BEDS = ["red", "blue", "green", "yellow", "light_blue", "white", "orange", "purple"]


def rc(cmd):
    out = subprocess.run(["docker", "exec", "duckymomo-banghoi-1-1", "rcon-cli", cmd], capture_output=True, text=True).stdout
    return re.sub(r"\x1b\[[0-9;]*m", "", out).strip()


def bot_act(body):
    code = ("import urllib.request,json,sys;b=json.loads(sys.argv[1]);"
            "r=urllib.request.Request('http://botfarm:8090/act',data=json.dumps(b).encode(),headers={'Content-Type':'application/json'},method='POST');"
            "print(urllib.request.urlopen(r,timeout=30).read().decode())")
    return subprocess.run(["docker", "exec", "duckymomo-panel-1", "python3", "-c", code, json.dumps(body)], capture_output=True, text=True).stdout.strip()


def ground_y(x, z):
    rc(f"execute in minecraft:overworld positioned {x} 0 {z} positioned over motion_blocking_no_leaves run summon minecraft:marker ~ ~ ~ {{Tags:[\"dmmnha\"]}}")
    out = rc("data get entity @e[type=minecraft:marker,tag=dmmnha,limit=1] Pos[1]")
    rc("kill @e[type=minecraft:marker,tag=dmmnha]")
    m = re.search(r"(-?\d+(\.\d+)?)d", out)
    return int(float(m.group(1))) if m else None


def house(cx, cz, y):
    """Nhà 5×5 (trong 3×3, mái chìa 1): 4 nhà vừa ô 16×16 không đè nhau, không lấn ra ngoài đất thị trấn."""
    w, roof = random.choice(WOODS), random.choice(ROOFS)
    slab = f"{roof}_slab"
    c = [
        f"fill {cx-3} {y} {cz-3} {cx+3} {y+6} {cz+3} minecraft:air",
        f"fill {cx-3} {y-4} {cz-3} {cx+3} {y-2} {cz+3} minecraft:dirt replace minecraft:air",
        f"fill {cx-3} {y-4} {cz-3} {cx+3} {y-2} {cz+3} minecraft:dirt replace minecraft:water",
        f"fill {cx-3} {y-1} {cz-3} {cx+3} {y-1} {cz+3} minecraft:grass_block",
        f"fill {cx-2} {y-1} {cz-2} {cx+2} {y+3} {cz+2} minecraft:{w}_planks hollow",
    ]
    for dx in (-2, 2):
        for dz in (-2, 2):
            c.append(f"fill {cx+dx} {y-1} {cz+dz} {cx+dx} {y+3} {cz+dz} minecraft:{w}_log")
    c += [
        f"fill {cx-3} {y+4} {cz-3} {cx+3} {y+4} {cz+3} minecraft:{slab}",
        f"fill {cx-1} {y+5} {cz-1} {cx+1} {y+5} {cz+1} minecraft:{slab}",
        f"setblock {cx} {y} {cz-2} minecraft:{w}_door[facing=north,half=lower]",
        f"setblock {cx} {y+1} {cz-2} minecraft:{w}_door[facing=north,half=upper]",
        f"setblock {cx-2} {y+1} {cz} minecraft:glass_pane",
        f"setblock {cx+2} {y+1} {cz} minecraft:glass_pane",
        f"setblock {cx} {y+1} {cz+2} minecraft:glass_pane",
        f"setblock {cx} {y+2} {cz} minecraft:lantern[hanging=true]",
    ]
    # trong 3×3: bot đứng giữa (cx, cz), ô ngay trong cửa (cx, cz-1) để trống; đồ đạc ngẫu nhiên ở các ô còn lại
    spots = [(cx-1, cz+1), (cx, cz+1), (cx+1, cz+1), (cx+1, cz-1)]
    random.shuffle(spots)
    furn = random.sample(["crafting_table", "chest[facing=north]", "furnace[facing=north]", "barrel[facing=up]", "bookshelf",
                          "smoker[facing=north]", "potted_poppy", "potted_dandelion", "composter"], k=random.randint(2, 3))
    used = set()
    for (x, z), b in zip(spots, furn):
        c.append(f"setblock {x} {y} {z} minecraft:{b}")
        used.add((x, z))
    if random.random() < 0.75:   # giường dọc tường tây
        bed = random.choice(BEDS)
        c += [f"setblock {cx-1} {y} {cz-1} minecraft:{bed}_bed[facing=south,part=foot]",
              f"setblock {cx-1} {y} {cz} minecraft:{bed}_bed[facing=south,part=head]"]
    for cmd in c:
        rc("execute in minecraft:overworld run " + cmd)
    return w


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    town, bots = sys.argv[1], sys.argv[2:6]
    hb = None
    for f in TOWNY.glob("*.txt"):
        kv = dict(l.split("=", 1) for l in f.read_text(encoding="utf-8", errors="replace").splitlines() if "=" in l)
        if kv.get("name") == town:
            hb = kv.get("homeBlock")
    if not hb:
        sys.exit(f"Không thấy thị trấn {town}")
    _, chx, chz = hb.split(",")
    x0, z0 = int(chx) * 16, int(chz) * 16
    centers = [(x0 + 4, z0 + 4), (x0 + 11, z0 + 4), (x0 + 4, z0 + 11), (x0 + 11, z0 + 11)]   # nhà ±3 (kể cả mái) nằm gọn x0+1..x0+14
    random.shuffle(centers)
    rc(f"execute in minecraft:overworld run forceload add {x0} {z0} {x0+15} {z0+15}")
    time.sleep(3)
    for bot, (cx, cz) in zip(bots, centers):
        y = ground_y(cx, cz)
        if y is None:
            print(bot, "không đo được mặt đất, bỏ qua")
            continue
        w = house(cx, cz, y)
        rc(f"execute in minecraft:overworld run minecraft:tp {bot} {cx}.5 {y} {cz}.5 180 0")
        time.sleep(2)
        print(bot, f"nhà gỗ {w} ở {cx} {y} {cz} ·", bot_act({"bot": bot, "action": "sethome"}))
        time.sleep(random.uniform(20, 60))   # không dời nhiều bot cùng lúc
    rc(f"execute in minecraft:overworld run forceload remove {x0} {z0} {x0+15} {z0+15}")


if __name__ == "__main__":
    main()
