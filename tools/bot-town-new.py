"""Tạo thị trấn "1 bot" để /t list trông đông (29/10, chủ server: "bổ sung thêm town vì đã tăng số lượng member, có thể 1 town chỉ có 1 bot,
tạo fake để khi member /t list thôi").

Cách dùng (thư mục gốc repo, máy chạy server):  python3 tools/bot-town-new.py <Tên>:<mã vùng rtp>:<bot> [...]
  vd: python3 tools/bot-town-new.py BenSuong:taybac:Minh_Tuan XomTre:lao:Duc_Anh
Mỗi thị trấn: chọn chỗ đất liền ngẫu nhiên trong vùng /tpr (rtp.zones, cách tâm ≤ 70% bán kính, mặt đất không phải nước / dung nham,
cách homeblock thị trấn khác ≥ 200 khối), dời bot tới đó, /ta town new <Tên> <bot> (thị trấn tạo đúng chỗ thị trưởng đứng), nạp quỹ
DEPOSIT xu (phí duy trì 100 xu/ngày), rồi botfarm "home" đưa bot về phòng ẩn. Towny mặc định không công khai, không mở (default_public /
default_open false). Bot phải đang online; nên tắt thay phiên online trong lúc chạy (script tự tắt rồi bật lại).
"""
import json
import math
import random
import re
import subprocess
import sys
import time
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data" / "banghoi-1" / "plugins"
DEPOSIT = 15000


def rc(cmd):
    out = subprocess.run(["docker", "exec", "duckymomo-banghoi-1-1", "rcon-cli", cmd], capture_output=True, text=True).stdout
    return re.sub(r"\x1b\[[0-9;]*m", "", out).strip()


def act(body):
    code = ("import urllib.request,json,sys;b=json.loads(sys.argv[1]);"
            "r=urllib.request.Request('http://botfarm:8090/act',data=json.dumps(b).encode(),headers={'Content-Type':'application/json'},method='POST');"
            "print(urllib.request.urlopen(r,timeout=60).read().decode())")
    return subprocess.run(["docker", "exec", "duckymomo-panel-1", "python3", "-c", code, json.dumps(body)], capture_output=True, text=True).stdout.strip()


def homeblocks():
    out = []
    for f in (DATA / "Towny" / "data" / "towns").glob("*.txt"):
        kv = dict(l.split("=", 1) for l in f.read_text(encoding="utf-8", errors="replace").splitlines() if "=" in l)
        if kv.get("homeBlock"):
            _, cx, cz = kv["homeBlock"].split(",")
            out.append((int(cx) * 16 + 8, int(cz) * 16 + 8, kv.get("name")))
    return out


def town_exists(name):
    for f in (DATA / "Towny" / "data" / "towns").glob("*.txt"):
        if re.search(rf"^name={re.escape(name)}$", f.read_text(encoding="utf-8", errors="replace"), re.M):
            return True
    return False


def ground(x, z):
    rc(f"execute in minecraft:overworld run forceload add {x} {z}")
    m = None
    for _ in range(8):   # vùng xa chưa sinh / chưa nạp: đợi tới khi đo được (29/10: chờ 2 giây làm trượt cả vùng)
        time.sleep(2)
        rc(f"execute in minecraft:overworld positioned {x} 0 {z} positioned over motion_blocking_no_leaves run summon minecraft:marker ~ ~ ~ {{Tags:[\"dmmtown\"]}}")
        out = rc("data get entity @e[type=minecraft:marker,tag=dmmtown,limit=1] Pos[1]")
        rc("kill @e[type=minecraft:marker,tag=dmmtown]")
        m = re.search(r"(-?\d+(\.\d+)?)d", out)
        if m:
            break
    if not m:
        return None
    y = int(float(m.group(1)))
    bad = rc(f"execute in minecraft:overworld if block {x} {y - 1} {z} #minecraft:replaceable")   # nước, cỏ, tuyết mỏng…
    wet = rc(f"execute in minecraft:overworld if block {x} {y - 1} {z} minecraft:water") + rc(f"execute in minecraft:overworld if block {x} {y - 1} {z} minecraft:lava")
    if "passed" in wet.lower() or "passed" in bad.lower():
        return None
    return y


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    zones = {z["id"]: z for z in yaml.safe_load((DATA / "DuckyMoMoCore" / "config.yml").read_text(encoding="utf-8"))["rtp"]["zones"]}
    print(act({"action": "rotcfg", "on": False}))
    try:
        for arg in sys.argv[1:]:
            name, zid, bot = arg.split(":")
            if town_exists(name):
                print(name, "đã có, bỏ qua")
                continue
            z = zones[zid]
            spot = None
            for _ in range(25):
                a, d = random.random() * math.tau, random.random() * z["radius"] * 0.7
                x, zz = int(z["center-x"] + math.cos(a) * d), int(z["center-z"] + math.sin(a) * d)
                if any(math.hypot(x - hx, zz - hz) < 200 for hx, hz, _ in homeblocks()):
                    continue
                y = ground(x, zz)
                rc(f"execute in minecraft:overworld run forceload remove {x} {zz}")
                if y is not None:
                    spot = (x, y, zz)
                    break
            if not spot:
                print(name, "không tìm được chỗ đất liền trong vùng", zid)
                continue
            x, y, zz = spot
            rc(f"execute in minecraft:overworld run minecraft:tp {bot} {x}.5 {y} {zz}.5")
            time.sleep(4)
            rc(f"townyadmin town new {name} {bot}")
            ok = False
            for _ in range(10):
                time.sleep(2)
                if town_exists(name):
                    ok = True
                    break
            if ok:
                rc(f"townyadmin town {name} deposit {DEPOSIT}")
                print(name, f"tạo ở {x} {y} {zz} ({z['name']}), thị trưởng {bot}")
            else:
                print(name, "KHÔNG tạo được (xem log server)")
            time.sleep(2)
            print("  ", act({"bot": bot, "action": "home"}))
            time.sleep(random.uniform(3, 8))
    finally:
        print(act({"action": "rotcfg", "on": True}))


if __name__ == "__main__":
    main()
