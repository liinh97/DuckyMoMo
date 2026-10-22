"""Ghi kết quả test mới vào docs/lo-trinh-test-ketqua.json + sinh file nạp DB (scratchpad/load2/<id>.json, _ids.txt).
Dùng: python3 tools/bottest/kq_add.py <file kết quả .json {id: [tt, ghi_chu]}> [thư mục ra, mặc định thư mục của file kết quả/load2]"""
import json, os, sys, time
R = "/home/developer/works/DuckyMoMo/"
new = json.load(open(sys.argv[1], encoding="utf-8"))
p = R + "docs/lo-trinh-test-ketqua.json"
repo = json.load(open(p, encoding="utf-8"))
out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(sys.argv[1])), "load2") + "/"
os.makedirs(out, exist_ok=True)
now = int(time.time() * 1000)
for k, (tt, note) in new.items():
    old = repo.get(k)
    v = {"tt": tt, "ghi_chu": note, "luc": now}
    if old and old.get("sua"):
        v["sua"] = old["sua"]
    repo[k] = v
    json.dump(v, open(out + k + ".json", "w", encoding="utf-8"), ensure_ascii=False)
    print(k, "cũ" if old else "mới")
json.dump(repo, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
print("tổng", len(repo))
