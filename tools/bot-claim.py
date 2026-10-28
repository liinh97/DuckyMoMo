"""Cho bot thị trưởng nhận thêm vài ô đất liền kề quanh homeblock, như người chơi mở rộng dần (28/10).
  python3 tools/bot-claim.py <thị trấn> <bot thị trưởng> <số ô thêm>
Mỗi ô: tp thị trưởng tới giữa ô (mặt đất), bot tự gõ /t claim; xong đưa về nhà (chỗ mặc định trong botfarm).
Ô chọn ngẫu nhiên dần ra ngoài từ các ô đã có (hình thị trấn mỗi nơi một khác). Tiền trả từ quỹ thị trấn."""
import importlib.util
import random
import sys
import time
from pathlib import Path

spec = importlib.util.spec_from_file_location("nha", Path(__file__).with_name("bot-nha.py"))
nha = importlib.util.module_from_spec(spec)
spec.loader.exec_module(nha)


def main():
    town, mayor, extra = sys.argv[1], sys.argv[2], int(sys.argv[3])
    hb = None
    for f in nha.TOWNY.glob("*.txt"):
        kv = dict(l.split("=", 1) for l in f.read_text(encoding="utf-8", errors="replace").splitlines() if "=" in l)
        if kv.get("name") == town:
            hb = kv.get("homeBlock")
    _, chx, chz = hb.split(",")
    have = {(int(chx), int(chz))}
    home = None
    for _ in range(extra):
        cand = sorted({(x + dx, z + dz) for x, z in have for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))} - have)
        cx, cz = random.choice(cand)
        bx, bz = cx * 16 + 8, cz * 16 + 8
        nha.rc(f"execute in minecraft:overworld run forceload add {bx} {bz}")
        time.sleep(2)
        y = nha.ground_y(bx, bz)
        nha.rc(f"execute in minecraft:overworld run minecraft:tp {mayor} {bx}.5 {y} {bz}.5")
        time.sleep(2)
        print(cx, cz, nha.bot_act({"bot": mayor, "action": "cmd", "text": "t claim"}))
        time.sleep(3)
        nha.rc(f"execute in minecraft:overworld run forceload remove {bx} {bz}")
        have.add((cx, cz))
        time.sleep(random.uniform(8, 25))
    # về nhà: chỗ mặc định của bot trong botfarm
    import json
    st = json.loads(nha.subprocess.run(["docker", "exec", "duckymomo-panel-1", "python3", "-c",
                                        "import urllib.request;print(urllib.request.urlopen('http://botfarm:8090/bots').read().decode())"],
                                       capture_output=True, text=True).stdout)
    h = next(b["home"] for b in st["bots"] if b["name"] == mayor)
    nha.rc(f"execute in minecraft:overworld run minecraft:tp {mayor} {h[0]}.5 {h[1]} {h[2]}.5")
    print(mayor, "về nhà", h)


if __name__ == "__main__":
    main()
