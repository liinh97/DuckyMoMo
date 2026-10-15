"""Đăng nhập trang web bằng tài khoản game: xác minh, quyền thật trong LuckPerms, hồ sơ người chơi.

Dùng bởi panel/app.py. Trang web công khai (site/) gọi panel qua mạng nội bộ Docker kèm khoá PANEL_SITE_KEY;
panel tự đọc quyền từ LuckPerms ở MỖI yêu cầu (không tin quyền do trang web gửi sang).

Chỉ một cách đăng nhập: mã một lần lấy trong game. Người chơi (đã /login AuthMe) gõ /web (DuckyMoMoCore WebLogin),
panel hỏi lại qua RCON "dmm webma <tên> <mã>". Trang web không nhận mật khẩu, nên không ai dò mật khẩu game qua web được.
"""
import os
import re
import time
import unicodedata
from pathlib import Path

from ruamel.yaml import YAML

DATA_DIR = Path(os.environ.get("DATA_DIR", "/data"))
MODES_DIR = Path(os.environ.get("MODES_DIR", "/repo/modes"))
QUYEN_WEB_FILE = MODES_DIR / "_network" / "web-quyen.yml"
SRV = "banghoi-1"
DMM = DATA_DIR / SRV / "plugins" / "DuckyMoMoCore"
USER_PRIO = 1_000_000  # quyền gán thẳng cho người chơi thắng quyền của nhóm (như LuckPerms)


def db(database):
    import pymysql
    return pymysql.connect(host="mariadb", user=os.environ["DB_USER"], password=os.environ["DB_PASSWORD"],
                           database=database, charset="utf8mb4", connect_timeout=5)


def safe_yaml(path):
    try:
        return YAML(typ="safe").load(Path(path).read_text(encoding="utf-8")) or {}
    except Exception:
        return {}


def slug(s):
    """'Thưởng & sự kiện' -> 'thuongsukien' (tên quyền duckymomo.web.caidat.<slug>)."""
    s = unicodedata.normalize("NFD", str(s)).replace("đ", "d").replace("Đ", "D")
    return re.sub(r"[^a-z0-9]", "", "".join(c for c in s if unicodedata.category(c) != "Mn").lower())


# ---------------------------------------------------------------- xác minh

# Lý do sai, ưu tiên báo cái cụ thể nhất (mã chỉ nằm ở MỘT server: server người chơi gõ /web)
CODE_REASONS = ["KHAC", "HET", "SAI", "KHONG"]


def verify_code(name, code, rcon, servers):
    """Mã /web: hỏi lần lượt các server. Trả (tên thật, None) hoặc (None, lý do: KHAC|HET|SAI|KHONG|TAT)."""
    if not re.fullmatch(r"\d{6}", code):
        return None, "SAI"
    found, reached = [], False
    for srv in servers:
        try:
            out = rcon(srv, [f"dmm webma {name} {code}"])[0][1]
        except Exception:
            continue
        reached = True
        m = re.match(r"WEBMA OK (\S+)", out)
        if m:
            return m.group(1), None
        m = re.match(r"WEBMA (\w+)", out)
        found.append(m.group(1) if m else "SAI")
    if not reached:
        return None, "TAT"
    return None, next((r for r in CODE_REASONS if r in found), "SAI")


# ---------------------------------------------------------------- quyền LuckPerms

def load_perms(name):
    """Quyền thật của người chơi: nhóm (trực tiếp + kế thừa) và mọi quyền, kèm server (global = toàn mạng)."""
    now = int(time.time())
    con = db("luckperms")
    try:
        cur = con.cursor()
        cur.execute("SELECT uuid, username FROM luckperms_players WHERE username = %s", (name.lower(),))
        row = cur.fetchone()
        if not row:
            return None
        uuid = row[0]
        cur.execute("SELECT permission, value, server, expiry FROM luckperms_user_permissions WHERE uuid = %s", (uuid,))
        user_rows = [r for r in cur.fetchall() if not r[3] or int(r[3]) > now]
        cur.execute("SELECT name, permission, value, server, expiry FROM luckperms_group_permissions")
        group_rows = [r for r in cur.fetchall() if not r[4] or int(r[4]) > now]
    finally:
        con.close()
    gperm, meta = {}, {}
    for g, perm, value, server, _ in group_rows:
        m = meta.setdefault(g, {"weight": 0, "prefix": "", "display": g})
        if perm.startswith("weight."):
            m["weight"] = int(perm.split(".", 1)[1] or 0)
        elif perm.startswith("prefix."):
            m["prefix"] = perm.split(".", 2)[2] if perm.count(".") >= 2 else ""
        elif perm.startswith("displayname."):
            m["display"] = perm.split(".", 1)[1]
        elif not perm.startswith(("suffix.", "meta.")):
            gperm.setdefault(g, []).append((perm.lower(), bool(value), server or "global"))
    direct = [{"group": p[6:], "server": s or "global", "expiry": int(e or 0)}
              for p, v, s, e in user_rows if p.startswith("group.") and v]
    # nhóm kế thừa (mod -> helper, congtuoc -> hautuoc ...); default luôn có
    groups = {}
    queue = [("default", "global")] + [(d["group"], d["server"]) for d in direct]
    while queue:
        g, srv = queue.pop()
        if g in groups and (groups[g] == "global" or srv != "global"):
            continue
        groups[g] = srv
        for perm, value, s in gperm.get(g, []):
            if perm.startswith("group.") and value:
                queue.append((perm[6:], s if s != "global" else srv))
    nodes = []
    for p, v, s, _ in user_rows:
        nodes.append((p.lower(), bool(v), s or "global", USER_PRIO))
    for g, gsrv in groups.items():
        w = meta.get(g, {}).get("weight", 0)
        nodes.append((f"group.{g}", True, gsrv, w))
        for perm, value, s in gperm.get(g, []):
            nodes.append((perm, value, s if s != "global" else gsrv, w))
    return {"uuid": uuid, "name": row[1], "direct": direct, "groups": groups, "meta": meta, "nodes": nodes}


def has(perms, node, server=None):
    """Như LuckPerms: khớp đúng tên trước, rồi a.b.*, a.*, *. Cùng mức: quyền gán thẳng / nhóm nặng hơn thắng.
    server=None: tính quyền ở bất kỳ server nào (dùng cho trang web); server='banghoi-1': chỉ global + server đó."""
    if not perms:
        return False
    node = node.lower()
    parts = node.split(".")
    keys = [node] + [".".join(parts[:i]) + ".*" for i in range(len(parts) - 1, 0, -1)] + ["*"]
    for k in keys:
        hits = [n for n in perms["nodes"] if n[0] == k and (server is None or n[2] in ("global", server))]
        if hits:
            return max(hits, key=lambda n: (n[3], not n[1]))[1]
    return False


# ---------------------------------------------------------------- tính năng trang quản trị

def web_config():
    d = safe_yaml(QUYEN_WEB_FILE)
    return {"features": d.get("tinh-nang") or [], "caidat": d.get("cai-dat") or {}}


def full_admin(perms):
    """Toàn quyền: có '*' (admin) hoặc được cấp riêng duckymomo.web.toanquyen."""
    return has(perms, "duckymomo.web.toanquyen")


def access(perms, categories):
    """Tính năng được mở + loại cài đặt được sửa. categories: tên các loại trên trang Cài đặt."""
    cfg = web_config()
    feats = {}
    for f in cfg["features"]:
        hit = next((q for q in f.get("quyen") or [] if has(perms, q)), None)
        if hit:
            feats[f["id"]] = {"id": f["id"], "ten": f.get("ten", f["id"]), "mo_ta": f.get("mo-ta", ""), "trang": f.get("trang"), "nho": hit}
    base = cfg["caidat"].get("quyen-chung", "duckymomo.web.caidat")
    extra = cfg["caidat"].get("them") or {}
    cats = []
    for c in categories:
        if has(perms, f"{base}.{slug(c)}") or any(has(perms, q) for q in extra.get(c) or []):
            cats.append(c)
    if cats:
        feats["caidat"] = {"id": "caidat", "ten": "Cài đặt", "mo_ta": "Sửa cài đặt: " + ", ".join(cats), "trang": "", "nho": ""}
    return {"toan_quyen": full_admin(perms), "features": feats, "categories": cats}


def can_use_command(row, perms, srv):
    """Lệnh trong cẩm nang (app.all_commands) người này dùng được không. 'Từ helper' = có nhóm helper (kể cả kế thừa)."""
    who = row.get("who", "?")
    if who == "Mọi người":
        return True
    if row.get("perm"):
        return has(perms, row["perm"], srv)
    m = re.match(r"Từ (\S+)", who)
    if m:
        return has(perms, f"group.{m.group(1)}", srv)
    return full_admin(perms)


# ---------------------------------------------------------------- hồ sơ người chơi (đọc file của banghoi-1)

def towny_kv(path):
    out = {}
    try:
        for line in Path(path).read_text(encoding="utf-8", errors="replace").splitlines():
            if "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip()
    except OSError:
        pass
    return out


def towny(name):
    base = DATA_DIR / SRV / "plugins" / "Towny" / "data"
    f = base / "residents" / f"{name}.txt"
    if not f.exists():
        d = base / "residents"
        f = next((x for x in d.glob("*.txt") if x.stem.lower() == name.lower()), None) if d.is_dir() else None
    if not f:
        return None
    r = towny_kv(f)
    town = r.get("town", "")
    out = {"thitran": town, "quocgia": "", "chuc": []}
    if town:
        t = towny_kv(base / "towns" / f"{town}.txt")
        out["quocgia"] = t.get("nation", "")
        if t.get("mayor", "").lower() == name.lower():
            out["chuc"].append("Thị trưởng")
            if out["quocgia"] and towny_kv(base / "nations" / f"{out['quocgia']}.txt").get("capital", "") == town:
                out["chuc"].append("Vua")
    out["chuc"] += [x for x in r.get("town-ranks", "").split(",") + r.get("nation-ranks", "").split(",") if x.strip()]
    return out


def money(uuid):
    d = safe_yaml(DATA_DIR / SRV / "plugins" / "Essentials" / "userdata" / f"{uuid}.yml")
    try:
        return float(d.get("money")) if d.get("money") is not None else None
    except (TypeError, ValueError):
        return None


def profile(perms):
    """Số liệu Bang Hội Chiến của người chơi: giờ chơi, điểm danh, nhiệm vụ, vé quay, vote, xếp hạng, án."""
    uuid, name = perms["uuid"], perms["name"]
    from datetime import date, timedelta
    core = safe_yaml(DMM / "config.yml")
    out = {"tien": money(uuid), "towny": towny(name)}
    hd = safe_yaml(DMM / "hoatdong.yml").get(uuid) or {}
    days = hd.get("ngay") or {}
    week_from = (date.today() - timedelta(days=6)).isoformat()
    out["giochoi"] = {"tong": round((hd.get("tong") or 0) / 60, 1),
                      "tuan": round(sum(v for k, v in days.items() if str(k) >= week_from) / 60, 1),
                      "can_tuan": (core.get("active-play") or {}).get("active-hours-per-week", 3)}
    dd = (safe_yaml(DMM / "diemdanh.yml").get("players") or {}).get(uuid) or {}
    out["diemdanh"] = {"so_ngay": dd.get("count", 0), "lan_cuoi": dd.get("last"), "hom_nay": dd.get("last") == date.today().isoformat()}
    nv = (safe_yaml(DMM / "nhiemvu.yml").get("players") or {}).get(uuid) or {}
    pool = (core.get("daily-quests") or {}).get("pool") or {}
    quests = []
    if str(nv.get("date")) == date.today().isoformat():
        for q in nv.get("quests") or []:
            spec = pool.get(q) or {}
            quests.append({"ten": spec.get("name", q), "da": (nv.get("progress") or {}).get(q, 0), "can": spec.get("amount", 0)})
    out["nhiemvu"] = quests
    vq = safe_yaml(DMM / "vongquay.yml").get(uuid) or {}
    out["vongquay"] = {"ve": vq.get("ve", 0), "da_quay": vq.get("da-quay", 0),
                       "phut_moi_ve": (core.get("lucky-spin") or {}).get("minutes-per-key")}
    vt = (safe_yaml(DMM / "vote.yml").get("players") or {}).get(uuid) or {}
    month = date.today().strftime("%Y-%m")
    out["vote"] = {"tong": vt.get("total", 0), "chuoi": vt.get("streak", 0), "thang": vt.get("month-votes", 0) if vt.get("month") == month else 0}
    bxh = safe_yaml(DMM / "bxh.yml")
    ct = bxh.get("chienthan") or {}
    pts = (ct.get(uuid) or {}).get("diem", 0)
    out["chienthan"] = {"diem": pts, "hang": (1 + sum(1 for v in ct.values() if (v or {}).get("diem", 0) > pts)) if pts else None}
    an = safe_yaml(DMM / "an.yml")
    tn = (an.get("truyna") or {}).get(uuid)
    out["an"] = {"truyna": {"den": tn.get("den"), "tien": tn.get("tien")} if tn and (tn.get("den") or 0) > time.time() * 1000 else None,
                 "tu_phut": (an.get("tu") or {}).get(uuid) or 0}
    pr = core.get("player-reset") or {}
    if pr.get("enabled"):
        cl = safe_yaml(DMM / "choilai.yml").get(uuid) or {}
        season = str(pr.get("season", "1"))
        done = int(((cl.get("mua") or {}).get(season) or {}).get("so-lan", 0) or 0)
        last, cooldown, maxn = int(cl.get("lan-cuoi") or 0), int(pr.get("cooldown-days", 7)), int(pr.get("max-per-season", 3))
        wait_until = last + cooldown * 86_400_000 if last else 0
        t = out["towny"] or {}
        blockers = []
        if "Vua" in t.get("chuc", []):
            blockers.append("Bạn đang là vua: nhường ngôi trước (/n set king <tên>).")
        elif "Thị trưởng" in t.get("chuc", []):
            blockers.append("Bạn đang là thị trưởng: nhường chức trước (/t set mayor <tên>).")
        out["choilai"] = {"da_lam": done, "toi_da": maxn, "cach_ngay": cooldown, "lan_cuoi": last,
                          "cho_den": wait_until if wait_until > time.time() * 1000 else 0,
                          "co_qua": not pr.get("gift-first-only", True) or done == 0, "tien_dau": pr.get("starting-money", 200),
                          "vuong": blockers}
    tiers = (core.get("donate-ranks") or {}).get("tiers") or []
    out["donate"] = {"tiers": [{"group": t.get("group"), "ten": t.get("name"), "mau": t.get("color", "&f"), "quyen_loi": t.get("perks") or [],
                                "gia": t.get("prices") or []} for t in tiers],
                     "cach_mua": (core.get("donate-ranks") or {}).get("how-to-buy", "")}
    return out
