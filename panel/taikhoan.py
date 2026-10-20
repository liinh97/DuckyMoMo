"""Đăng nhập trang web bằng tài khoản game: xác minh, quyền thật trong LuckPerms, hồ sơ người chơi.

Dùng bởi panel/app.py. Trang web công khai (site/) gọi panel qua mạng nội bộ Docker kèm khoá PANEL_SITE_KEY;
panel tự đọc quyền từ LuckPerms ở MỖI yêu cầu (không tin quyền do trang web gửi sang).

Chỉ một cách đăng nhập: mã một lần lấy trong game. Người chơi (đã /login AuthMe) gõ /web (DuckyMoMoCore WebLogin),
panel hỏi lại qua RCON "dmm webma <tên> <mã>". Trang web không nhận mật khẩu, nên không ai dò mật khẩu game qua web được.
"""
import os
import json
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


# Cài đặt cá nhân (/caidat trong game; khớp PlayerSettings.OPTS của DuckyMoMoCore): mã, tên, mặc định, mô tả, có ở đâu.
# Lưu: LuckPerms meta của người chơi "dmmcaidat.<mã>" = bat|tat (không có = mặc định).
CAIDAT = [
    ("chat", "Kênh chat chung", True, "Tắt: không thấy chat của người khác (vẫn thấy tin nhắn riêng, thông báo)", ""),
    ("msg", "Tin nhắn riêng (/msg)", True, "Tắt: không ai nhắn riêng cho bạn được", ""),
    ("goiten", "Kêu khi bị gọi tên", True, "Có tiếng \"ting\" khi ai đó nhắc tên bạn trong chat", ""),
    ("loctuc", "Lọc từ tục", True, "Tắt: bạn thấy chat nguyên văn (người khác vẫn thấy bản đã lọc)", ""),
    ("vaora", "Thông báo vào / rời", True, "Dòng \"… đã vào / đã rời server\"", ""),
    ("tuvong", "Thông báo tử vong", True, "Dòng \"… đã chết\" của người khác", ""),
    ("donrac", "Thông báo dọn rác", True, "Đếm ngược dọn đồ rơi trên đất", "Bang Hội Chiến"),
    ("sukien", "Thông báo sự kiện", True, "Lệnh săn, kho báu, tin đồn Cổng Hoang, kỷ lục dungeon…", "Bang Hội Chiến"),
    ("khungnhac", "Khung nhắc lúc vào game", True, "Khung gom hướng dẫn, mùa, công thành, điểm danh… lúc vào", ""),
    ("bangphai", "Bảng bên phải", True, "Bảng thông tin bên phải màn hình", ""),
    ("bossbar", "Thanh mẹo trên cùng", True, "Thanh chữ mẹo xoay vòng ở đầu màn hình", ""),
    ("hat", "Hiệu ứng hạt người khác", True, "Tắt: không thấy hạt trang trí của người khác (máy yếu đỡ giật)", ""),
    ("maumob", "Máu quái trên đầu", True, "Tên, máu, sát thương bay lên khi đánh quái", "Bang Hội Chiến"),
    ("tpa", "Nhận yêu cầu TPA", True, "Người khác xin dịch chuyển tới bạn (/tpa)", "Bang Hội Chiến, Practice"),
    ("tpahere", "Nhận yêu cầu TPAHere", True, "Người khác xin kéo bạn tới chỗ họ (/tpahere)", "Bang Hội Chiến, Practice"),
    ("loimoi", "Lời mời đấu / nhóm", True, "Lời mời đấu tay đôi (/duel) và vào nhóm (/party invite)", "Practice"),
    ("nhacvay", "Nhắc giờ công thành", True, "Báo trước 15 phút khi thị trấn / nước bạn có trận vây", "Bang Hội Chiến"),
    ("nhacngay", "Nhắc điểm danh, nhiệm vụ", True, "Mỗi giờ nhắc nếu chưa điểm danh / còn nhiệm vụ hằng ngày", "Bang Hội Chiến"),
    ("fontviet", "Font tiếng Việt", True, "Chữ có dấu rõ đẹp. Tắt: font gốc Minecraft, chữ căn giữa chuẩn hơn (áp dụng khi vào lại / đổi server)", ""),
    ("goipet", "Tự gọi pet khi vào", False, "Vào Bang Hội Chiến là pet chiến đấu tự ra đi theo", "Bang Hội Chiến"),
]
CAIDAT_IDS = {c[0]: c for c in CAIDAT}
_CAIDAT_RE = re.compile(r"^meta\.dmmcaidat\\?\.([a-z]+)\.(bat|tat)$")


def player_settings(perms):
    """Trạng thái cài đặt cá nhân: [{ma, ten, bat, mo_ta, o}] (đọc meta LuckPerms của chính người chơi)."""
    got = {}
    for p, v, _s, prio in (perms or {}).get("nodes", []):
        m = _CAIDAT_RE.match(p)
        if m and v and prio == USER_PRIO:
            got[m.group(1)] = m.group(2) == "bat"
    return [{"ma": i, "ten": n, "bat": got.get(i, d), "mo_ta": desc, "o": where} for i, n, d, desc, where in CAIDAT]


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
    out["chuc"] += [ROLE_VI.get(x.strip().lower(), x.strip()) for x in r.get("town-ranks", "").split(",") + r.get("nation-ranks", "").split(",") if x.strip()]
    # kho lương thị trấn + cân bằng chiến tranh của nước (DuckyMoMoCore ghi kholuong.json, canbang.json)
    dmm = DATA_DIR / SRV / "plugins" / "DuckyMoMoCore"
    if town:
        kl = core_json(dmm / "kholuong.json")
        k = next((x for x in kl.get("kho", []) if str(x.get("ten", "")).lower() == town.lower()), None)
        cost = float(kl.get("cost_per_minute") or 0)
        out["kholuong"] = {"diem": int(k.get("diem", 0)) if k else 0, "max": int(kl.get("max") or 0),
                           "phut_nguoi": int((k.get("diem", 0) if k else 0) / cost) if cost > 0 else None} if kl else None
    if out["quocgia"]:
        cb = core_json(dmm / "canbang.json")
        n = next((x for x in cb.get("nuoc", []) if str(x.get("ten", "")).lower() == out["quocgia"].lower()), None)
        wb = (safe_yaml(dmm / "config.yml").get("war-balance") or {})
        out["canbang"] = {"thi_tran": n.get("thi_tran"), "thuoc_dia": n.get("thuoc_dia"), "toi_da": wb.get("max-colonies"),
                          "ba_chu": bool(n.get("ba_chu")), "no": n.get("no", 0),
                          "quy_them": float(wb.get("war-chest-extra-percent-per-town") or 0) * max(0, int(n.get("thi_tran") or 1) - 1)} if n else None
    return out


# Chức vụ Towny (rank) -> tiếng Việt, khớp HallOfFameSnapshot.ROLE_VI
ROLE_VI = {"assistant": "Phó thị trưởng", "helper": "Trợ lý", "sheriff": "Cảnh sát trưởng", "guard": "Vệ binh", "private": "Binh nhì",
           "sergeant": "Trung sĩ", "lieutenant": "Trung uý", "captain": "Đại uý", "major": "Thiếu tá", "colonel": "Đại tá", "general": "Tướng",
           "co-king": "Phó vương", "diplomat": "Ngoại giao", "recruiter": "Tuyển quân"}


def core_json(f):
    try:
        return json.loads(f.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def money(uuid):
    d = safe_yaml(DATA_DIR / SRV / "plugins" / "Essentials" / "userdata" / f"{uuid}.yml")
    try:
        return float(d.get("money")) if d.get("money") is not None else None
    except (TypeError, ValueError):
        return None


def season_now(cfg):
    """Mùa hiện tại giống Seasons.java (ngày thật giờ VN, ghi đè ở mua.yml): {ten, con} hoặc None."""
    from datetime import date, datetime
    from zoneinfo import ZoneInfo
    lst = cfg.get("list") or {}
    order = [o for o in (cfg.get("order") or []) if o in lst]
    if not cfg.get("enabled") or not order:
        return None
    sd = cfg.get("start-date", "2026-10-12")
    try:
        start = sd if isinstance(sd, date) else date.fromisoformat(str(sd).strip())
    except ValueError:
        start = date(2026, 10, 12)
    per = max(1, int(cfg.get("days-per-season", 7) or 7))
    since = (datetime.now(ZoneInfo("Asia/Ho_Chi_Minh")).date() - start).days
    forced = str(safe_yaml(DMM / "mua.yml").get("ghi-de") or "")
    cur = forced if forced in order else order[(since // per) % len(order)]
    name = re.sub(r"[&§][0-9a-fk-orA-FK-OR]", "", str((lst.get(cur) or {}).get("name", cur)))
    return {"id": cur, "ten": name, "con": None if forced in order else per - since % per}


def pet_level(exp):
    """Cấp MyPet từ kinh nghiệm (công thức mặc định, giống exp.js: 17 XP/cấp tới 17, +3 tới 31, +7 từ 32)."""
    def need(lv):
        if lv <= 1:
            return 0
        if lv <= 17:
            return (lv - 1) * 17
        if lv <= 31:
            n = lv - 17
            return 272 + n * (3 * lv - 14) / 2
        n = lv - 32
        return 887 + n * (7 * lv - 93) / 2
    lv = 1
    while lv < 1000 and need(lv + 1) <= (exp or 0):
        lv += 1
    return lv


def combat_pets(uuid, perms):
    """Pet chiến đấu (MyPet, Bang Hội Chiến) của người chơi: đọc pets.db (chỉ đọc) + số pet được giữ theo quyền."""
    import sqlite3
    db = DATA_DIR / SRV / "plugins" / "MyPet" / "pets.db"
    trees, kinds = {}, {}   # kinds: loài -> (tên Việt, nhóm loài)
    for f in (DATA_DIR / SRV / "plugins" / "MyPet" / "skilltrees").glob("*.st.json"):
        try:
            d = json.loads(f.read_text(encoding="utf-8"))
            g = d.get("DmmNhom")   # cây theo nhóm loài (tools/mypet/): "Chiến Binh · Thú săn"
            trees[d.get("ID")] = (d.get("Display") or d.get("Name")) + (f" · {g['ten']}" if g else "")
            if g:
                for t, vi in zip(d.get("MobTypes") or [], g.get("loai_vi") or []):
                    kinds[t.lower()] = (vi, f"{g['ten']} ({g['vai']})")
        except (OSError, ValueError):
            pass
    pets = []
    try:
        con = sqlite3.connect(f"file:{db}?mode=ro", uri=True, timeout=2)
        try:
            rows = con.execute("SELECT p.name, p.type, p.exp, p.skilltree, p.wants_to_spawn, p.respawn_time FROM pets p "
                               "JOIN players pl ON pl.internal_uuid = p.owner_uuid WHERE pl.mojang_uuid = ? ORDER BY p.last_used DESC",
                               (uuid,)).fetchall()
            # hạng trên bảng xếp hạng pet (theo kinh nghiệm, như /bxhpet)
            ranks = [con.execute("SELECT COUNT(*) + 1 FROM pets WHERE exp > ?", (r[2] or 0,)).fetchone()[0] for r in rows]
        finally:
            con.close()
        for (name, typ, exp, tree, spawn, respawn), hang in zip(rows, ranks):
            vi, nhom = kinds.get(str(typ).lower(), (typ, None))
            pets.append({"ten": re.sub(r"<[^>]+>|[&§][0-9a-fk-or]", "", str(name or "")), "loai": typ, "loai_vi": vi, "nhom": nhom, "cap": pet_level(exp), "hang": hang,
                         "huong": trees.get(tree, tree) if tree else None, "dang_dan": bool(spawn), "dang_hoi_sinh": int(respawn or 0) > 0})
    except Exception:
        pets = None
    limit = 0
    for n in range(45, 0, -1):
        if has(perms, f"mypet.petstorage.limit.{n}", SRV):
            limit = n
            break
    return {"pets": pets, "giu_toi_da": limit + 1}


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
    # Giết / chết: thống kê gốc của Minecraft ở Bang Hội Chiến (world/stats/<uuid>.json)
    try:
        st = json.loads((DATA_DIR / SRV / "world" / "stats" / f"{uuid}.json").read_text(encoding="utf-8")).get("stats", {})
        custom = st.get("minecraft:custom", {})
        k, d = int(custom.get("minecraft:player_kills", 0)), int(custom.get("minecraft:deaths", 0))
        out["pvp"] = {"giet": k, "chet": d, "kda": round(k / max(1, d), 1)}
    except (OSError, ValueError, AttributeError):
        out["pvp"] = {"giet": 0, "chet": 0, "kda": 0}
    out["mua"] = season_now(core.get("seasons") or {})
    # Chợ đen (thu mua): đơn của mình đang mở, hàng chờ nhận
    don = [v for v in (safe_yaml(DMM / "donhang.yml").get("don") or {}).values() if isinstance(v, dict) and v.get("owner") == uuid]
    out["donhang"] = {"mo": sum(1 for v in don if not v.get("dong")), "cho_nhan": sum(int(v.get("kho", 0) or 0) for v in don)}
    kh = safe_yaml(DMM / "khaihoang.yml")
    out["khaihoang"] = {"gop": float(((kh.get("gop") or {}).get(uuid) or {}).get("xu", 0) or 0), "quy": float(kh.get("quy", 0) or 0)}
    tt = (safe_yaml(DMM / "treothuong.yml").get("muc") or {})
    me = tt.get(uuid) or {}
    on_me = 0.0
    if me and float(me.get("het", 0) or 0) > time.time() * 1000:
        on_me = float(me.get("he-thong", 0) or 0) + sum(float((p or {}).get("xu", 0) or 0) for p in (me.get("nguoi") or {}).values())
    placed = [{"ten": v.get("ten"), "xu": float(((v.get("nguoi") or {}).get(uuid) or {}).get("xu", 0) or 0)}
              for v in tt.values() if isinstance(v, dict) and uuid in (v.get("nguoi") or {}) and float(v.get("het", 0) or 0) > time.time() * 1000]
    out["treothuong"] = {"tren_ban": on_me, "da_treo": placed}
    out["mypet"] = combat_pets(uuid, perms)
    st = (safe_yaml(DMM / "dautruong.yml").get("stats") or {}).get(uuid) or {}
    ar = core.get("arena") or {}
    out["dautruong"] = {"elo": int(st.get("elo", 1000)), "thang": int(st.get("thang", 0)), "thua": int(st.get("thua", 0)),
                        "thuong_hom_nay": int((st.get("ngay") or {}).get(date.today().isoformat(), 0)), "toi_da": int(ar.get("daily-reward-cap", 5)),
                        "bat": bool(ar.get("enabled", False))}
    tiers = (core.get("donate-ranks") or {}).get("tiers") or []
    out["donate"] = {"tiers": [{"group": t.get("group"), "ten": t.get("name"), "mau": t.get("color", "&f"), "quyen_loi": t.get("perks") or [],
                                "gia": t.get("prices") or []} for t in tiers],
                     "cach_mua": (core.get("donate-ranks") or {}).get("how-to-buy", "")}
    # Sổ thành tích (achievements, thanhtich.yml) + hợp đồng lính đánh thuê (linhdanhthue.yml)
    ach = core.get("achievements") or {}
    if ach.get("enabled"):
        tt = safe_yaml(DMM / "thanhtich.yml")
        dem = (tt.get("dem") or {}).get(uuid) or {}
        xong = set((tt.get("xong") or {}).get(uuid) or [])
        out["thanhtich"] = [{"ten": g.get("name", k), "mo_ta": g.get("desc", ""), "co": float(dem.get(g.get("type"), 0) or 0),
                             "can": float(g.get("target", 1) or 1), "xong": k in xong, "danh_hieu": any(str(r).startswith("danhhieu") for r in g.get("rewards") or [])}
                            for k, g in (ach.get("list") or {}).items()]
    ta = ((safe_yaml(DMM / "thaiap.yml").get("dat") or {}).get(uuid)) if (core.get("thai-ap") or {}).get("enabled") else None
    if ta:
        sizes = (core.get("thai-ap") or {}).get("sizes") or [50, 70, 90]
        lv = int(ta.get("cap", 0) or 0)
        out["thaiap"] = {"ten": ta.get("ten"), "canh": sizes[min(lv, len(sizes) - 1)], "thich": len(ta.get("thich") or []),
                         "moi": len(ta.get("tin") or []), "to_nhat": lv + 1 >= len(sizes)}
    luc = core.get("level-up") or {}
    if luc.get("enabled"):
        lv = (safe_yaml(DMM / "capdo.yml").get("nguoi") or {}).get(uuid) or {}
        out["capdo"] = {"cap": int(lv.get("cap", 0) or 0), "toi_da": int(luc.get("max-level", 100) or 100), "diem": int(lv.get("diem", 0) or 0),
                        "ha": int(lv.get("ha", 0) or 0), "da_mua": len(lv.get("mua") or [])}
    dgc = core.get("dungeon-gate") or {}
    if dgc.get("enabled"):
        dg = safe_yaml(DMM / "dungeon.yml")
        names = {k: (v or {}).get("name", k) for k, v in (dgc.get("dungeons") or {}).items()}
        qua = (dg.get("qua") or {}).get(uuid) or []
        ve = (dg.get("ve") or {}).get(uuid) or {}
        out["dungeon"] = {"da_vuot": [names.get(x, x) for x in qua], "tim_an": len((dg.get("tim") or {}).get(uuid) or []),
                          "an_tong": sum(1 for v in (dgc.get("dungeons") or {}).values() if (v or {}).get("type") == "hidden"),
                          "chuoi": [names[k] for k, v in (dgc.get("dungeons") or {}).items() if (v or {}).get("type") == "chain"],
                          "ve": {names.get(k, k): n for k, n in ve.items() if n},
                          # tầng chuỗi đã mở: tầng đầu (không requires) + tầng mở bằng Chìa khoá (mo.<uuid>)
                          "da_mo": [names[k] for k, v in (dgc.get("dungeons") or {}).items() if (v or {}).get("type") == "chain"
                                    and (not (v or {}).get("requires") or k in ((dg.get("mo") or {}).get(uuid) or []))]}
    wgc = core.get("wild-gates") or {}
    mytown = (out.get("towny") or {}).get("thitran")
    if wgc.get("enabled") and mytown:
        try:
            gates = json.loads((DMM / "conghoang.json").read_text(encoding="utf-8")).get("cong") or []
        except (OSError, ValueError):
            gates = []
        mine = [g for g in gates if g.get("tim") and str(g.get("chu") or "").lower() == mytown.lower()]
        if mine:
            out["conghoang"] = [{"ma": g.get("ma"), "dungeon": g.get("dungeon"), "phi": g.get("phi", 0), "dong": g.get("dong", False),
                                 "luot": g.get("luot", 0)} for g in mine]
    ct = story(uuid)
    if ct:
        out["cotruyen"] = ct
    kn = skill_levels(uuid)
    if kn:
        out["kynang"] = kn
    lk = wanderers(uuid)
    if lk:
        out["lukhach"] = lk
    ldt = safe_yaml(DMM / "linhdanhthue.yml")
    reg = (ldt.get("dangky") or {}).get(uuid)
    hd = (ldt.get("hopdong") or {}).get(uuid)
    if reg or hd:
        out["linhdanhthue"] = {"gia": (reg or {}).get("gia"), "xong": (reg or {}).get("xong", 0),
                               "nuoc": (hd or {}).get("ten-nuoc") if hd and (hd.get("het") or 0) / 1000 > time.time() else None,
                               "het": int((hd or {}).get("het", 0) / 1000) if hd else None}
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


def story_tags(uuid):
    """Tag BetonQuest của người chơi (hồ sơ đang dùng), bỏ tiền tố gói ("cotruyen." / "cotruyen>"). None nếu chưa có dữ liệu."""
    import sqlite3
    f = DATA_DIR / SRV / "plugins" / "BetonQuest" / "database.db"
    if not f.exists():
        return None
    try:
        con = sqlite3.connect(f"file:{f}?mode=ro", uri=True, timeout=2)
        try:
            rows = con.execute("SELECT t.tag FROM betonquest_tags t JOIN betonquest_player p ON t.profileID = p.active_profile "
                               "WHERE p.playerID = ?", (uuid,)).fetchall()
        finally:
            con.close()
    except sqlite3.Error:
        return None
    return {re.split(r"[.>]", str(r[0]))[-1] for r in rows}


def story(uuid):
    """Cốt truyện (BetonQuest) trên trang tài khoản: mỗi chương đang ở bước nào. Mô tả chương: modes/banghoi/cotruyen.yml."""
    spec = safe_yaml(MODES_DIR / "banghoi" / "cotruyen.yml").get("chuong") or []
    tags = story_tags(uuid)
    if not spec or tags is None:
        return None
    pkg = DATA_DIR / SRV / "plugins" / "BetonQuest" / "QuestPackages" / "cotruyen" / "package.yml"
    consts = safe_yaml(pkg).get("constants") or safe_yaml(MODES_DIR / "banghoi" / "server" / "plugins" / "BetonQuest" / "QuestPackages"
                                                           / "cotruyen" / "package.yml").get("constants") or {}
    fill = lambda t: re.sub(r"\{(\w+)\}", lambda m: str(consts.get(m.group(1), m.group(0))), str(t or ""))
    out = []
    for c in spec:
        steps = c.get("buoc") or []
        if c.get("xong") in tags:
            out.append({"ten": c.get("ten"), "xong": True, "buoc": len(steps), "tong": len(steps), "viec": ""})
            continue
        cur, viec = 0, fill(c.get("bat_dau"))
        for i, b in enumerate(steps):
            if b.get("tag") in tags and b.get("xong") not in tags:
                cur, viec = i + 1, fill(b.get("viec"))
            elif b.get("xong") in tags:
                cur, viec = i + 1, "Quay lại gặp NPC để nhận việc tiếp theo."
        out.append({"ten": c.get("ten"), "xong": False, "buoc": cur, "tong": len(steps), "viec": viec})
    return out


SKILL_VI = {"farming": "Trồng trọt", "foraging": "Đốn gỗ", "mining": "Đào mỏ", "fishing": "Câu cá", "excavation": "Đào đất", "alchemy": "Pha chế"}


def skill_levels(uuid):
    """Cấp kỹ năng nghề AuraSkills (plugins/AuraSkills/userdata/<uuid>.yml) của các kỹ năng đang bật, kèm trần cấp."""
    base = DATA_DIR / SRV / "plugins" / "AuraSkills"
    sk = safe_yaml(base / "skills.yml").get("skills") or {}
    on = {k.split("/")[-1]: (v.get("options") or {}).get("max_level", 100) for k, v in sk.items() if (v.get("options") or {}).get("enabled", True)}
    if not on:
        return None
    mine = {k.split("/")[-1]: (v or {}).get("level", 0) for k, v in (safe_yaml(base / "userdata" / f"{uuid}.yml").get("skills") or {}).items()}
    return [{"ten": SKILL_VI.get(k, k), "cap": int(mine.get(k, 0) or 0), "tran": int(t)} for k, t in on.items()]


def wanderers(uuid):
    """Lữ khách (DuckyMoMoCore wander-npcs + BetonQuest): với từng lữ khách, người chơi đã nghe chuyện / đã qua cổng / đã kể chưa."""
    try:
        lk = json.loads((DMM / "lukhach.json").read_text(encoding="utf-8")).get("lu_khach") or []
    except (OSError, ValueError):
        return None
    tags = story_tags(uuid)
    if not lk or tags is None:
        return None
    def state(m):
        return "xong" if f"lk_{m}_xong" in tags else "qua" if f"lk_{m}_qua" in tags else "nghe" if f"lk_{m}_nghe" in tags else ""
    return [{"ten": v.get("ten"), "noi": v.get("noi"), "trang_thai": state(v.get("ma"))} for v in lk]


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


def amp_codes(text):
    """Mã màu Essentials lưu bằng § (hex: §x§r§r§g§g§b§b) -> dạng & mà ô nhập và xem trước trên web dùng (&6, &#rrggbb)."""
    text = re.sub(r"§x((?:§[0-9a-fA-F]){6})", lambda m: "&#" + m.group(1).replace("§", ""), str(text))
    return text.replace("§", "&")


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
                 "rgb": h("essentials.nick.rgb"), "hien_tai": amp_codes(ess.get("nickname") or ""),
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
    if viec == "danhhieu":
        t = str(data.get("ma", ""))
        if t not in [x["id"] for x in c["danhhieu"]["co"]]:
            raise ValueError("Bạn chưa có danh hiệu này.")
        return f"danhhieu chon {t}"
    if viec == "danhhieu_off":
        return "danhhieu tat"
    raise ValueError("Thao tác không hợp lệ")
