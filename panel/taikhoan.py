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


def command_access(row, perms, srv, towny_roles=()):
    """Lệnh trong cẩm nang (app.all_commands) người này dùng được không, và nhờ đâu.
    Trả (được dùng, nhãn): nhãn rỗng = mọi người dùng được; "Thị trưởng"/"Vua" = theo chức trong Towny;
    "Từ helper" = role; "Admin" = chỉ admin. srv=None: lệnh proxy (tính quyền ở mọi server)."""
    who = row.get("who", "?")
    if who == "Console":
        return False, ""
    if who == "Mọi người":
        return True, ""
    if who in ("Thị trưởng", "Vua"):
        return who in towny_roles, who
    m = re.match(r"Từ (\S+)", who)
    if row.get("perm"):
        if has(perms, row["perm"], srv):
            return True, who if m else ("Admin" if who == "Chỉ admin" else "Quyền riêng")
        return False, ""
    if m:
        return has(perms, f"group.{m.group(1)}", srv), who
    return full_admin(perms), "Admin"


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


# ---------------------------------------------------------------- trang trí (đổi tên, hiệu ứng hạt, pet, danh hiệu)
# Trang tài khoản cho người có quyền trang trí chỉnh trực tiếp. Lệnh chạy THAY người chơi bằng /sudo của Essentials
# (chỉ có ở Bang Hội Chiến, Practice) nên chính plugin kiểm quyền như khi gõ trong game; web chỉ lọc trước cho gọn.

PP_EFFECTS = ["flame", "soul_fire_flame", "heart", "note", "enchant", "happy_villager", "angry_villager", "cloud", "snowflake",
              "end_rod", "portal", "witch", "totem_of_undying", "cherry_leaves", "glow", "electric_spark", "crit", "dripping_honey"]
PP_STYLES = ["normal", "halo", "orbit", "feet", "overhead", "point", "spin", "quadhelix", "wings", "rings", "sphere", "vortex",
             "beam", "whirl", "celebration", "twins", "pulse"]
# pet.type.passive (Công Tước) = mọi pet hiền dưới đây; các bậc dưới có quyền từng loài pet.type.<loài>.*
PASSIVE_PETS = ["cat", "wolf", "chicken", "pig", "rabbit", "parrot", "fox", "bee", "axolotl", "frog", "turtle", "sheep", "cow", "panda",
                "polarbear", "goat", "allay", "armadillo", "camel", "sniffer", "mooshroom", "ocelot", "llama", "tadpole", "copper_golem",
                "dolphin", "happy_ghast", "horse", "donkey", "mule", "squid", "glow_squid", "cod", "salmon", "tropical_fish",
                "trader_llama", "strider", "bat"]
COSMETIC_SERVERS = ["banghoi-1", "practice-1"]  # có Essentials (/sudo)


def active_particles(uuid, srv="banghoi-1"):
    """Hiệu ứng hạt đang bật của người chơi, đọc thẳng SQLite của PlayerParticles (chỉ đọc). Trả [{id, hieu_ung, kieu}] hoặc None
    nếu không đọc được (plugin dùng MySQL, chưa có file, cấu trúc bảng khác bản 8.x)."""
    import sqlite3
    d = DATA_DIR / srv / "plugins" / "PlayerParticles"
    db = next((f for f in [d / "playerparticles.db"] + sorted(d.glob("*.db")) if f.exists()), None)
    if not db:
        return None
    try:
        con = sqlite3.connect(f"file:{db}?mode=ro", uri=True, timeout=2)
        try:
            tables = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            pt = next((t for t in tables if t.endswith("particle") and not t.endswith("fixed_particle")), None)
            gt = next((t for t in tables if t.endswith("group")), None)
            if not pt or not gt:
                return None
            rows = con.execute(f"SELECT p.id, p.effect, p.style FROM {pt} p JOIN {gt} g ON p.group_uuid = g.uuid "
                               f"WHERE g.owner_uuid = ? AND g.name = 'active' ORDER BY p.id", (uuid,)).fetchall()
        finally:
            con.close()
    except sqlite3.Error:
        return None
    return [{"id": int(i), "hieu_ung": str(e).lower(), "kieu": str(s).lower()} for i, e, s in rows]


def cosmetics(perms, srv="banghoi-1"):
    h = lambda n: has(perms, n, srv)
    ess = safe_yaml(DATA_DIR / srv / "plugins" / "Essentials" / "userdata" / f"{perms['uuid']}.yml")
    esscfg = safe_yaml(DATA_DIR / srv / "plugins" / "Essentials" / "config.yml")
    max_pp = max([n for n in range(1, 11) if h(f"playerparticles.particles.max.{n}")] or [0])
    pets = [t for t in PASSIVE_PETS if h(f"pet.type.{t}.spawn") or h("pet.type.passive")]
    core = safe_yaml(DMM / "config.yml")
    titles = (core.get("leaderboard") or {}).get("titles") or {}
    season = str((core.get("leaderboard") or {}).get("season", "1"))
    dh = (safe_yaml(DMM / "bxh.yml").get("danhhieu") or {}).get(perms["uuid"]) or {}

    def title_text(t):
        base = re.sub(r"\d+$", "", t)
        return str(titles.get(t, titles.get(base, f"&7[{t}]"))).replace("{mua}", season)
    return {
        "nick": {"duoc": h("essentials.nick"), "mau": h("essentials.nick.color"), "dinhdang": h("essentials.nick.format"),
                 "rgb": h("essentials.nick.rgb"), "hien_tai": ess.get("nickname") or "",
                 # giới hạn của Essentials: mặc định 15 ký tự và TÍNH CẢ mã màu (&6 = 2 ký tự)
                 "toi_da": int(esscfg.get("max-nick-length", 15) or 15), "bo_mau": bool(esscfg.get("ignore-colors-in-max-nick-length", False)),
                 "dau": str(esscfg.get("nickname-prefix", "~") if esscfg.get("nickname-prefix") is not None else "~")},
        "hat": h("essentials.hat"),
        "pp": {"toi_da": max_pp, "dang": active_particles(perms["uuid"], srv) if max_pp else [], "hieu_ung": [e for e in PP_EFFECTS if h(f"playerparticles.effect.{e}")] if max_pp else [],
               "kieu": [s for s in PP_STYLES if h(f"playerparticles.style.{s}")] if max_pp else []},
        "pet": {"loai": pets},
        "danhhieu": {"co": [{"id": t, "chu": title_text(t)} for t in dh.get("co") or []], "dung": dh.get("dung")},
    }


NICK_RE = re.compile(r"^(?:&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]|[A-Za-z0-9_])+$")


def cosmetic_command(perms, data, srv="banghoi-1"):
    """Kiểm tra yêu cầu từ web, trả lệnh (không có /) để chạy thay người chơi. ValueError nếu không hợp lệ."""
    c = cosmetics(perms, srv)
    viec = data.get("viec")
    if viec == "nick":
        v = str(data.get("gia_tri", "")).strip()
        if not NICK_RE.match(v):
            raise ValueError("Tên chỉ gồm chữ không dấu, số, dấu _ và mã màu (&a, &l, &#ff8800).")
        plain = re.sub(r"&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]", "", v)
        n, limit = (len(plain) if c["nick"]["bo_mau"] else len(v)), c["nick"]["toi_da"]
        if len(plain) < 3:
            raise ValueError("Tên hiển thị cần ít nhất 3 chữ.")
        if n > limit:
            raise ValueError(f"Tên quá dài: {n}/{limit} ký tự" + ("" if c["nick"]["bo_mau"] else " (server đang tính cả mã màu, vd &6 = 2 ký tự)")
                             + ". Bớt chữ hoặc bớt mã màu.")
        if not c["nick"]["duoc"]:
            raise ValueError("Rank của bạn chưa có quyền đổi tên.")
        if re.search(r"&[0-9a-fA-F]", v) and not c["nick"]["mau"]:
            raise ValueError("Rank của bạn chưa được tô màu tên.")
        if re.search(r"&[k-oK-O]", v) and not c["nick"]["dinhdang"]:
            raise ValueError("Rank của bạn chưa được chữ đậm/nghiêng.")
        if "&#" in v and not c["nick"]["rgb"]:
            raise ValueError("Rank của bạn chưa được màu tuỳ chọn (#hex).")
        return f"nick {v}"
    if viec == "nick_off":
        return "nick off"
    if viec == "pp_add":
        e, s = str(data.get("hieu_ung", "")), str(data.get("kieu", ""))
        if e not in c["pp"]["hieu_ung"] or s not in c["pp"]["kieu"]:
            raise ValueError("Rank của bạn chưa có hiệu ứng hoặc kiểu này.")
        return f"pp add {e} {s}"
    if viec == "pp_reset":
        return "pp reset"
    if viec == "pp_remove":
        pid = int(data.get("id") or 0)
        if pid not in [x["id"] for x in (c["pp"]["dang"] or [])]:
            raise ValueError("Không có hiệu ứng này (tải lại trang).")
        return f"pp remove {pid}"
    if viec == "pet":
        t = str(data.get("loai", ""))
        if t not in c["pet"]["loai"]:
            raise ValueError("Rank của bạn chưa có pet này.")
        return f"pet summon {t}"
    if viec == "pet_off":
        return "pet remove"
    if viec == "danhhieu":
        t = str(data.get("ma", ""))
        if t not in [x["id"] for x in c["danhhieu"]["co"]]:
            raise ValueError("Bạn chưa có danh hiệu này.")
        return f"danhhieu chon {t}"
    if viec == "danhhieu_off":
        return "danhhieu tat"
    raise ValueError("Thao tác không hợp lệ")
