"""Skyblock: dò kẽ hở giá Chợ: giá vốn rẻ nhất (mua ở Chợ, chế / nung theo công thức thật của game) so với giá bán lại. Chỉ đọc, in báo cáo."""
import glob, json, os, re, sys, yaml, zipfile, io

R = "/home/developer/works/DuckyMoMo/data/skyblock-1/"
S = R + "plugins/UltimateShop/shops/"

buy, sell, where = {}, {}, {}
for f in sorted(glob.glob(S + "*.yml")):
    y = yaml.safe_load(open(f, encoding="utf-8"))
    for k, it in (y.get("items") or {}).items():
        p = (it.get("products") or {}).get(1) or {}
        m = str(p.get("material", "")).lower()
        a = float(p.get("amount", 1) or 1)
        b = ((it.get("buy-prices") or {}).get(1) or {}).get("amount")
        s = ((it.get("sell-prices") or {}).get(1) or {}).get("amount")
        if not m:
            continue
        where[m] = os.path.basename(f)
        if b is not None:
            buy[m] = min(buy.get(m, 1e18), float(b) / a)
        if s is not None:
            sell[m] = max(sell.get(m, 0), float(s) / a)
print("món:", len(where), "mua:", len(buy), "bán:", len(sell))

# công thức trong jar server
jars = glob.glob(R + "versions/**/*.jar", recursive=True) + glob.glob(R + "cache/**/*.jar", recursive=True) + glob.glob(R + "libraries/**/server-*.jar", recursive=True)
files, tags = {}, {}
for j in jars:
    try:
        z = zipfile.ZipFile(j)
    except Exception:
        continue
    names = [n for n in z.namelist() if n.startswith("data/minecraft/recipe/") or n.startswith("data/minecraft/tags/item/")]
    if not names:
        continue
    for n in names:
        if n.endswith(".json"):
            d = json.loads(z.read(n))
            if "/recipe/" in n:
                files[n] = d
            else:
                tags[n.split("/tags/item/")[1][:-5]] = d.get("values", [])
    print("jar:", os.path.basename(j), len(files), "công thức,", len(tags), "tag")
    break
if not files:
    sys.exit("không thấy công thức")


def expand(x, seen=()):
    if isinstance(x, dict):
        x = x.get("item") or ("#" + x["tag"] if x.get("tag") else "")
    if isinstance(x, list):
        out = []
        for i in x:
            out += expand(i, seen)
        return out
    x = str(x).replace("minecraft:", "")
    if x.startswith("#"):
        t = x[1:]
        out = []
        for v in tags.get(t, []):
            v = v if isinstance(v, str) else v.get("id", "")
            out += expand(v, seen + (t,)) if t not in seen else []
        return out
    return [x] if x else []


recipes = []
for n, d in files.items():
    t = d.get("type", "").replace("minecraft:", "")
    r = d.get("result")
    if not r:
        continue
    out = str(r.get("id") if isinstance(r, dict) else r).replace("minecraft:", "")
    cnt = (r.get("count", 1) if isinstance(r, dict) else 1) or 1
    ing = []
    if t == "crafting_shaped":
        pat = "".join(d.get("pattern", []))
        for c in pat:
            if c != " ":
                ing.append(expand(d["key"][c]))
    elif t == "crafting_shapeless":
        ing = [expand(i) for i in d.get("ingredients", [])]
    elif t in ("smelting", "blasting", "smoking", "campfire_cooking", "stonecutting"):
        ing = [expand(d.get("ingredient"))]
    else:
        continue
    if all(ing) and ing:
        recipes.append((out, cnt, ing, n.split("/")[-1][:-5], t))
print("công thức dùng được:", len(recipes))

INF = 1e18
cost = dict(buy)
how = {k: "mua" for k in buy}
for _ in range(6):
    ch = False
    for out, cnt, ing, name, t in recipes:
        tot = sum(min((cost.get(x, INF) for x in slot), default=INF) for slot in ing)
        if tot < INF and tot / cnt < cost.get(out, INF) - 1e-9:
            cost[out] = tot / cnt
            how[out] = f"{name} ({t})"
            ch = True
    if not ch:
        break

bad = []
for m, s in sell.items():
    c = cost.get(m)
    if c is not None and s > c + 1e-9:
        bad.append((s / c, m, s, c, how.get(m), where.get(m)))
print("\n== KẼ HỞ: bán lại > giá vốn:", len(bad))
for r, m, s, c, h, w in sorted(bad, reverse=True)[:45]:
    print(f"{r:6.2f}x {m:26} bán {s:7.2f}  vốn {c:7.2f}  {h}  [{w}]")

# món không mua được nhưng bán được: nguồn vô hạn đáng chú ý
print("\n== chỉ bán, không mua:", sorted(k for k in sell if k not in buy)[:30])
print("\n== giá bán các món cày vô hạn:")
for m in "cobblestone stone cobbled_deepslate gravel sand dirt oak_log wheat sugar_cane cactus pumpkin melon_slice bone rotten_flesh string gunpowder iron_ingot gold_ingot diamond emerald".split():
    print(f"  {m:18} mua {buy.get(m, '-')!s:7} bán {sell.get(m, '-')!s:7}")

# ---- lượt 2: máy tạo đá ra đá cuội vô hạn: nguyên liệu tự cày tính bằng giá BÁN (giá trị thật người chơi bỏ ra)
free = {m: sell.get(m, 0) for m in ("cobblestone", "stone", "gravel", "sand", "dirt", "ice", "water_bucket")}
cost2 = dict(free)
how2 = {k: "tự cày" for k in free}
for k, v in buy.items():
    cost2.setdefault(k, v)
    how2.setdefault(k, "mua")
for _ in range(6):
    for out, cnt, ing, name, t in recipes:
        tot = sum(min((cost2.get(x, INF) for x in slot), default=INF) for slot in ing)
        if tot < INF and tot / cnt < cost2.get(out, INF) - 1e-9:
            cost2[out] = tot / cnt
            how2[out] = f"{name} ({t})"
rows = [(sell[m] / cost2[m], m, sell[m], cost2[m], how2.get(m)) for m in sell if m in cost2 and how2.get(m) not in ("mua", "tự cày") and cost2[m] > 0 and sell[m] > cost2[m] * 1.5]
print("\n== LƯỢT 2 (đá, cát, sỏi, đất tự cày): lời > 1,5 lần:", len(rows))
for r, m, se, c, h in sorted(rows, reverse=True)[:40]:
    print(f"{r:6.2f}x {m:26} bán {se:7.2f}  vốn {c:7.3f}  {h}")
