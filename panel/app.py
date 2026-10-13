"""Trang Cài đặt của DuckyMoMo Network.

Đọc modes/<id>/settings.schema.yml (định nghĩa) + settings.yml (giá trị). Khi bấm Lưu:
ghi giá trị vào file cấu hình plugin trong data/<server>/, lưu settings.yml, rồi gửi lệnh nạp lại
qua RCON để áp dụng ngay. Mật khẩu RCON đọc từ data/<server>/.rcon-cli.env (image itzg tự sinh).
"""

import base64
import hmac
import json
import os
import re
import socket
import struct
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from ruamel.yaml import YAML
from ruamel.yaml.scalarstring import DoubleQuotedScalarString, LiteralScalarString, SingleQuotedScalarString

MODES_DIR = Path(os.environ.get("MODES_DIR", "/repo/modes"))
DATA_DIR = Path(os.environ.get("DATA_DIR", "/data"))
PANEL_USER = os.environ.get("PANEL_USER", "admin")
PANEL_PASSWORD = os.environ["PANEL_PASSWORD"]
DOZZLE_URL = os.environ.get("DOZZLE_URL", "")
# Mở qua Trạm 6 (gateway cắt tiền tố /mc/caidat và gửi X-Forwarded-Prefix): link log trỏ vào đường dẫn của Trạm 6
DOZZLE_PROXY_URL = os.environ.get("DOZZLE_PROXY_URL", DOZZLE_URL)
RCON_PORT = 25575
INDEX_HTML = (Path(__file__).parent / "index.html").read_text(encoding="utf-8")
ACTIVITY_HTML = (Path(__file__).parent / "activity.html").read_text(encoding="utf-8")
CAMNANG_HTML = (Path(__file__).parent / "camnang.html").read_text(encoding="utf-8")
HUONGDAN_HTML = (Path(__file__).parent / "huongdan.html").read_text(encoding="utf-8")
ROLE_HTML = (Path(__file__).parent / "role.html").read_text(encoding="utf-8")
# Trang Role: role chức vụ (track staff + builder + default) và rank donate (track donate), lưu ở LuckPerms (MariaDB)
STAFF_GROUPS = ["admin", "mod", "helper", "builder", "default"]
DONATE_GROUPS = ["congtuoc", "hautuoc", "batuoc", "tutuoc", "namtuoc"]
PLAYER_RE = re.compile(r"^\.?[A-Za-z0-9_]{1,17}$")
# Cẩm nang trong game (/huongdan): bản gốc + các bản chép (repo của từng server, data/ để có hiệu lực ngay)
HUONGDAN_FILE = MODES_DIR / "_network" / "huongdan.yml"
HUONGDAN_COPIES = [MODES_DIR / "banghoi/server/plugins/DuckyMoMoCore/huongdan.yml",
                   MODES_DIR / "practice/server/plugins/DuckyMoMoCore/huongdan.yml",
                   Path("/repo/lobby-dmm/huongdan.yml")] + \
                  [DATA_DIR / srv / "plugins/DuckyMoMoCore/huongdan.yml" for srv in ("lobby", "banghoi-1", "practice-1")]
ID_RE = re.compile(r"^[a-z0-9_-]{1,32}$")
# Cẩm nang: nội dung ở modes/_network/camnang.yml (sửa không cần build lại); server quét plugin thật đang cài
CAMNANG_FILE = MODES_DIR / "_network" / "camnang.yml"
CAMNANG_SERVERS = ["proxy", "lobby", "banghoi-1", "practice-1", "pokemon-1"]
# Do service "activity" (dashboard/log-feed.sh, JSONL=…) ghi ra
ACTIVITY_FILE = DATA_DIR / "activity" / "activity.jsonl"
ACTIVITY_TAIL_BYTES = 4_000_000
TIMES_RE = re.compile(r"^$|^([01]\d|2[0-3]):[0-5]\d(,([01]\d|2[0-3]):[0-5]\d)*$")


def yaml_rt():
    """YAML giữ nguyên comment, dấu nháy, thứ tự (để ghi lại file cấu hình plugin)."""
    y = YAML()
    y.preserve_quotes = True
    y.width = 4096
    y.indent(mapping=2, sequence=4, offset=2)
    return y


# ---------------------------------------------------------------- chế độ + cài đặt

def load_modes():
    modes = {}
    for schema_file in sorted(MODES_DIR.glob("*/settings.schema.yml")):
        mode_id = schema_file.parent.name
        schema = YAML(typ="safe").load(schema_file.read_text(encoding="utf-8"))
        values_file = schema_file.parent / "settings.yml"
        values = yaml_rt().load(values_file.read_text(encoding="utf-8")) if values_file.exists() else None
        modes[mode_id] = {"schema": schema, "values": values or {}, "values_file": values_file}
    return modes


def all_settings(schema):
    for group in schema.get("groups", []):
        for s in group.get("settings", []):
            yield group, s


def plain(v):
    """Chuyển giá trị ruamel về kiểu Python thường (để so sánh / trả JSON)."""
    if isinstance(v, (list, tuple)):
        return [plain(x) for x in v]
    if isinstance(v, bool) or v is None:
        return v
    if isinstance(v, int):
        return int(v)
    if isinstance(v, float):
        return float(v)
    return str(v)


def validate(s, v):
    t = s["type"]
    label = s["label"]
    if t == "times":
        v = str(v or "").replace(" ", "")
        if not TIMES_RE.match(v):
            raise ValueError(f"{label}: sai định dạng giờ (ví dụ 20:00,21:30)")
        return v
    if t in ("int", "number"):
        try:
            v = int(v) if t == "int" else float(v)
        except (TypeError, ValueError):
            raise ValueError(f"{label}: phải là số")
        if "min" in s and v < s["min"]:
            raise ValueError(f"{label}: tối thiểu {s['min']}")
        if "max" in s and v > s["max"]:
            raise ValueError(f"{label}: tối đa {s['max']}")
        return v
    if t == "select":
        allowed = [o["value"] for o in s["options"]]
        if v not in allowed:
            raise ValueError(f"{label}: lựa chọn không hợp lệ")
        return v
    if t == "text":
        return str(v or "")
    if t in ("list", "lines"):
        # list: các mục cách nhau dấu phẩy; lines: mỗi dòng một mục. items: int/number -> đổi sang số
        if isinstance(v, str):
            parts = v.split("\n") if t == "lines" else v.split(",")
        else:
            parts = list(v or [])
        out = [str(x).strip() for x in parts if str(x).strip()]
        if s.get("items") in ("int", "number"):
            try:
                out = [int(x) if s["items"] == "int" else float(x) for x in out]
            except ValueError:
                raise ValueError(f"{label}: mỗi mục phải là số")
        if "min_items" in s and len(out) < s["min_items"]:
            raise ValueError(f"{label}: cần ít nhất {s['min_items']} mục")
        return out
    raise ValueError(f"{label}: kiểu {t} không ghi được")


def set_path(doc, dotted, value):
    # Phần tử là số = chỉ số trong danh sách YAML (ví dụ donate-ranks.tiers.0.prices)
    keys = [int(k) if k.isdigit() else k for k in dotted.split(".")]
    node = doc
    for k in keys[:-1]:
        if isinstance(k, int):
            node = node[k]
            continue
        if k not in node or node[k] is None:
            node[k] = {}
        node = node[k]
    last = keys[-1]
    old = node[last] if isinstance(last, int) else (node.get(last) if hasattr(node, "get") else None)
    # Giữ kiểu ghi của file gốc: Towny/SiegeWar ghi số dạng chuỗi trong nháy đơn ('20.0')
    if isinstance(value, list):
        new = list(value)
    elif isinstance(old, SingleQuotedScalarString):
        new = SingleQuotedScalarString(str(value))
    elif isinstance(old, DoubleQuotedScalarString):
        new = DoubleQuotedScalarString(str(value))
    elif isinstance(old, str):
        new = str(value)
    else:
        new = value
    node[last] = new


# ---------------------------------------------------------------- RCON

def rcon_password(server):
    env = DATA_DIR / server / ".rcon-cli.env"
    for line in env.read_text().splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1].strip()
    raise RuntimeError("không tìm thấy mật khẩu RCON")


def rcon(server, commands):
    """Gửi lệnh qua RCON. Trả về [(lệnh, kết quả)]. Lỗi kết nối thì ném ra (server đang tắt)."""
    def send(sock, req_id, kind, body):
        data = body.encode("utf-8") + b"\x00\x00"
        sock.sendall(struct.pack("<iii", len(data) + 8, req_id, kind) + data)

    def recv(sock):
        head = b""
        while len(head) < 4:
            chunk = sock.recv(4 - len(head))
            if not chunk:
                raise ConnectionError("RCON đóng kết nối")
            head += chunk
        (length,) = struct.unpack("<i", head)
        body = b""
        while len(body) < length:
            chunk = sock.recv(length - len(body))
            if not chunk:
                raise ConnectionError("RCON đóng kết nối")
            body += chunk
        req_id, _kind = struct.unpack("<ii", body[:8])
        return req_id, body[8:-2].decode("utf-8", "replace")

    out = []
    with socket.create_connection((server, RCON_PORT), timeout=10) as sock:
        send(sock, 1, 3, rcon_password(server))
        if recv(sock)[0] == -1:
            raise RuntimeError("sai mật khẩu RCON")
        for i, cmd in enumerate(commands, start=2):
            send(sock, i, 2, cmd)
            _, text = recv(sock)
            out.append((cmd, re.sub(r"§.", "", text).strip()))
    return out


# ---------------------------------------------------------------- lưu + áp dụng

def save_and_apply(mode_id, incoming, apply_all=False):
    modes = load_modes()
    if mode_id not in modes:
        raise ValueError("không có chế độ này")
    mode = modes[mode_id]
    schema, values = mode["schema"], mode["values"]
    server = schema["server"]

    changed = []  # (group, setting, value)
    for group, s in all_settings(schema):
        if s["type"] == "info":
            continue
        key = s["key"]
        if key in incoming:
            v = validate(s, incoming[key])
        elif apply_all and key in values:
            v = validate(s, plain(values[key]))
        else:
            continue
        if apply_all or plain(values.get(key)) != v:
            changed.append((group, s, v))

    if not changed:
        return {"ok": True, "message": "Không có gì thay đổi.", "results": []}

    # Mục có "servers" ghi vào file của nhiều server (vd lọc chat, rank donate: lobby + banghoi + practice)
    def servers_of(s):
        return s.get("servers") or [server]

    # 1) Ghi vào file cấu hình plugin (gom theo server + file)
    by_file = {}
    for _, s, v in changed:
        if s.get("file"):
            for srv in servers_of(s):
                by_file.setdefault((srv, s["file"]), []).append((s["path"], v))
    for (srv, rel), items in by_file.items():
        path = DATA_DIR / srv / rel
        if not path.exists():
            raise FileNotFoundError(f"chưa có {srv}/{rel}: server cần chạy ít nhất một lần để plugin tạo file")
        y = yaml_rt()
        doc = y.load(path.read_text(encoding="utf-8"))
        for dotted, v in items:
            set_path(doc, dotted, v)
        tmp = path.with_suffix(path.suffix + ".tmp")
        with tmp.open("w", encoding="utf-8") as f:
            y.dump(doc, f)
        tmp.replace(path)

    # 2) Lưu settings.yml (giữ comment đầu file)
    for _, s, v in changed:
        values[s["key"]] = v
    with mode["values_file"].open("w", encoding="utf-8") as f:
        yaml_rt().dump(values, f)

    # 3) Áp dụng qua RCON: lệnh riêng của từng mục, rồi lệnh nạp lại của từng nhóm (mỗi lệnh 1 lần)
    # Lệnh dùng được {value}, {double} (giá trị x2) và {tên_mục_khác} (giá trị hiện tại của mục đó)
    # Lệnh nhóm (apply) chạy trên mọi server có mục đổi trong nhóm đó.
    current = {k: plain(v) for k, v in values.items()}
    per_server = {}
    for group, s, v in changed:
        for c in s.get("commands", []):
            cmd = c.format(**current, value=v, double=int(v) * 2 if s["type"] in ("int", "number") else v)
            for srv in servers_of(s):
                lst = per_server.setdefault(srv, [])
                if cmd not in lst:
                    lst.append(cmd)
    for group in schema.get("groups", []):
        srvs = []
        for g, s, _ in changed:
            if g is group:
                srvs += [x for x in servers_of(s) if x not in srvs]
        for srv in srvs:
            for c in group.get("apply", []):
                lst = per_server.setdefault(srv, [])
                if c not in lst:
                    lst.append(c)

    stored_only = [s["label"] for _, s, _ in changed if not s.get("file") and not s.get("commands")]
    results, failed = [], []
    for srv, cmds in per_server.items():
        try:
            for c, o in rcon(srv, cmds):
                results.append((f"[{srv}] {c}" if len(per_server) > 1 else c, o))
        except Exception as e:  # server đang tắt / đang khởi động
            failed.append(f"{srv} ({e})")
    ok = not failed
    if ok:
        msg = "Đã lưu và áp dụng."
    else:
        msg = (f"Đã lưu, nhưng chưa áp dụng được trên: {', '.join(failed)}. Server có thể đang tắt; bật lên sẽ dùng giá trị mới "
               "(riêng viền thế giới phải bấm Áp dụng lại tất cả).")
    if stored_only:
        msg += " Chỉ ghi lại (dùng khi mở mùa mới): " + ", ".join(stored_only) + "."
    return {"ok": ok, "message": msg, "results": [{"command": c, "output": o} for c, o in results]}


def camnang_json():
    """Cẩm nang + plugin đang cài thật ở từng server (quét data/<server>/plugins/*.jar, mods/*.jar với Fabric)."""
    doc = YAML(typ="safe").load(CAMNANG_FILE.read_text(encoding="utf-8")) or {}
    jars = {}
    for srv in CAMNANG_SERVERS:
        found = []
        for sub in ("plugins", "mods"):
            d = DATA_DIR / srv / sub
            if d.is_dir():
                found += sorted(p.name[:-4] for p in d.glob("*.jar"))
        if found or (DATA_DIR / srv).is_dir():
            jars[srv] = found
    used = {srv: set() for srv in jars}
    for p in doc.get("plugins", []):
        key = str(p.get("jar", "")).lower()
        p["installed"] = []
        for srv, names in jars.items():
            if key == "-":
                if srv == "proxy":
                    p["installed"].append(srv)
                continue
            hit = [n for n in names if n.lower().startswith(key)]
            if hit:
                p["installed"].append(srv)
                used[srv].update(hit)
    # jar đi kèm (thư viện/phần phụ) của plugin đã có mục: không coi là "chưa có trong cẩm nang"
    companions = ("viabackwards", "viarewind", "essentialsxchat", "essentialsxspawn", "chunkyborder", "zmenu", "geyser")
    unknown = {srv: [n for n in names if n not in used[srv] and not n.lower().startswith(companions)] for srv, names in jars.items()}
    return {"guide": doc.get("guide", []), "plugins": doc.get("plugins", []), "servers": list(jars), "unknown": unknown}


def huongdan_json():
    doc = YAML(typ="safe").load(HUONGDAN_FILE.read_text(encoding="utf-8")) or {}
    return {k: doc.get(k, "") for k in ("title", "author", "toc-title", "toc-footer")} | {"chapters": doc.get("chapters", [])}


def huongdan_save(data):
    """Ghi cẩm nang (giữ chú thích đầu file), chép sang các server. Trả về thông báo."""
    chapters = data.get("chapters") or []
    if not chapters:
        raise ValueError("cần ít nhất một chương")
    ids = set()
    clean = []
    for c in chapters:
        cid = str(c.get("id", "")).strip().lower()
        name = str(c.get("name", "")).strip()
        if not ID_RE.match(cid):
            raise ValueError(f"mã chương '{cid}' chỉ gồm chữ thường không dấu, số, _ hoặc - (vd banghoi)")
        if cid in ids:
            raise ValueError(f"mã chương '{cid}' bị trùng")
        if not name:
            raise ValueError(f"chương '{cid}' chưa có tên")
        ids.add(cid)
        pages = [str(p).replace("\r", "").rstrip() for p in c.get("pages") or [] if str(p).strip()]
        if not pages:
            raise ValueError(f"chương '{name}' chưa có trang nào")
        clean.append((cid, name, pages))
    y = yaml_rt()
    doc = y.load(HUONGDAN_FILE.read_text(encoding="utf-8"))
    for k in ("title", "author", "toc-title", "toc-footer"):
        if k in data:
            doc[k] = DoubleQuotedScalarString(str(data[k]))
    doc["chapters"] = [{"id": cid, "name": name, "pages": [LiteralScalarString(p) for p in pages]}
                       for cid, name, pages in clean]
    tmp = HUONGDAN_FILE.with_suffix(".yml.tmp")
    with tmp.open("w", encoding="utf-8") as f:
        y.dump(doc, f)
    tmp.replace(HUONGDAN_FILE)
    text = HUONGDAN_FILE.read_bytes()
    done = []
    for dst in HUONGDAN_COPIES:
        if dst.parent.is_dir():
            dst.write_bytes(text)
            done.append(str(dst).replace(str(DATA_DIR), "data").replace(str(MODES_DIR), "modes"))
    n_pages = sum(len(p) for _, _, p in clean)
    return f"Đã lưu {len(clean)} chương, {n_pages} trang và chép sang {len(done)} chỗ. Người chơi mở /huongdan là thấy ngay."


def lp_db():
    import pymysql
    return pymysql.connect(host="mariadb", user=os.environ["DB_USER"], password=os.environ["DB_PASSWORD"],
                           database="luckperms", charset="utf8mb4", connect_timeout=5)


def roles_json():
    """Mỗi nhóm: tên, kiểu (staff/donate/default), prefix, weight, thành viên (tên, hạn, server)."""
    con = lp_db()
    try:
        cur = con.cursor()
        cur.execute("SELECT name, permission FROM luckperms_group_permissions WHERE permission LIKE 'prefix.%%' "
                    "OR permission LIKE 'weight.%%' OR permission LIKE 'displayname.%%'")
        meta = {}
        for name, perm in cur.fetchall():
            m = meta.setdefault(name, {})
            if perm.startswith("prefix."):
                _, w, text = perm.split(".", 2)
                m["prefix"] = text
            elif perm.startswith("weight."):
                m["weight"] = int(perm.split(".", 1)[1])
            else:
                m["display"] = perm.split(".", 1)[1]
        cur.execute("SELECT p.username, up.permission, up.expiry, up.server FROM luckperms_user_permissions up "
                    "JOIN luckperms_players p ON p.uuid = up.uuid WHERE up.permission LIKE 'group.%%' AND up.value = 1")
        members = {}
        for user, perm, expiry, server in cur.fetchall():
            g = perm[6:]
            if g == "default":
                continue
            members.setdefault(g, []).append({"name": user, "expiry": int(expiry or 0), "server": server})
        cur.execute("SELECT COUNT(*) FROM luckperms_players")
        total = cur.fetchone()[0]
    finally:
        con.close()
    out = []
    for g in STAFF_GROUPS + DONATE_GROUPS:
        m = meta.get(g, {})
        out.append({"name": g, "kind": "default" if g == "default" else ("donate" if g in DONATE_GROUPS else "staff"),
                    "prefix": m.get("prefix", ""), "weight": m.get("weight", 0), "display": m.get("display", g),
                    "members": sorted(members.get(g, []), key=lambda x: x["name"].lower()),
                    "count": total if g == "default" else len(members.get(g, []))})
    return {"groups": out, "players": total}


def role_action(data):
    """Cấp / gỡ role qua RCON ở lobby (LuckPerms dùng chung DB + Redis nên áp dụng toàn mạng)."""
    act, player, group = data.get("action"), str(data.get("player", "")).strip(), data.get("group")
    if not PLAYER_RE.match(player):
        raise ValueError("Tên người chơi không hợp lệ")
    if group not in STAFF_GROUPS + DONATE_GROUPS or group == "default":
        raise ValueError("Role không hợp lệ")
    if group in DONATE_GROUPS and act == "add":
        months = int(data.get("months") or 1)
        if not 1 <= months <= 24:
            raise ValueError("Số tháng từ 1 đến 24")
        cmds = [f"rank give {player} {group} {months}"]
    elif group in DONATE_GROUPS and act == "remove":
        cmds = [f"rank remove {player}"]
    elif act == "add":
        cmds = [f"lp user {player} parent add {group}"]
    elif act == "remove":
        cmds = [f"lp user {player} parent remove {group}"]
    else:
        raise ValueError("Thao tác không hợp lệ")
    rcon("lobby", cmds)
    import time
    time.sleep(2)  # lệnh chạy bất đồng bộ (tải người chơi, ghi DB)
    return {"ok": True, "message": f"Đã gửi: /{cmds[0]}. Người chơi phải từng vào server.", "data": roles_json()}


def modes_json():
    out = []
    for mode_id, m in load_modes().items():
        out.append({"id": mode_id, "schema": m["schema"],
                    "values": {k: plain(v) for k, v in m["values"].items()}})
    return out


# ---------------------------------------------------------------- hoạt động người chơi

NETWORK_MODE = {"id": "network", "name": "Chung (sảnh, proxy)"}


def mode_index():
    """Danh sách chế độ (modes/*/mode.yml, bỏ thư mục bắt đầu bằng _) và bảng server -> chế độ."""
    modes, by_server = [], {}
    for f in sorted(MODES_DIR.glob("*/mode.yml")):
        if f.parent.name.startswith("_"):
            continue
        try:
            m = YAML(typ="safe").load(f.read_text(encoding="utf-8")) or {}
        except Exception:
            continue
        mode_id = m.get("id") or f.parent.name
        modes.append({"id": mode_id, "name": m.get("display_name") or mode_id})
        for srv in m.get("servers") or []:
            if isinstance(srv, dict) and srv.get("name"):
                by_server[srv["name"]] = mode_id
    return [NETWORK_MODE] + modes, by_server


def read_activity(params):
    """Sự kiện mới nhất trước. Lọc: types (chat,join,command), mode, player, q (tìm trong nội dung),
    from / to (mốc UTC dạng 2026-10-10T17:00:00Z, trình duyệt tự đổi ngày giờ Việt Nam sang), limit."""
    types = set(filter(None, params.get("types", "").split(","))) or {"chat", "join", "command"}
    mode = params.get("mode", "")
    player = params.get("player", "").strip().lower()
    q = params.get("q", "").strip().lower()
    ts_from, ts_to = params.get("from", ""), params.get("to", "")
    try:
        limit = max(1, min(2000, int(params.get("limit", "300"))))
    except ValueError:
        limit = 300
    modes, by_server = mode_index()
    events = []
    if ACTIVITY_FILE.exists():
        with ACTIVITY_FILE.open("rb") as f:
            f.seek(0, 2)
            size = f.tell()
            # lọc theo ngày thì đọc cả file (tối đa ~20 MB) để thấy được ngày cũ; không thì chỉ đọc phần cuối
            tail = size if ts_from else ACTIVITY_TAIL_BYTES
            f.seek(max(0, size - tail))
            raw = f.read().decode("utf-8", "replace").splitlines()
        if size > tail:
            raw = raw[1:]  # dòng đầu có thể bị cắt giữa chừng
        seen = set()
        for line in raw:
            try:
                e = json.loads(line)
            except ValueError:
                continue
            key = (e.get("ts"), e.get("server"), e.get("text"))
            if key in seen:  # lịch sử nạp lại khi log-feed khởi động lại
                continue
            seen.add(key)
            # chuyển server (proxy → banghoi-1) tính cho chế độ của server đích
            e["mode"] = by_server.get(e.get("target") or e.get("server"), "network")
            events.append(e)
    events.sort(key=lambda e: e.get("ts", ""))
    out = []
    for e in reversed(events):
        if e.get("type") not in types or (mode and e["mode"] != mode):
            continue
        ts = e.get("ts", "")
        if (ts_from and ts < ts_from) or (ts_to and ts >= ts_to):
            continue
        if player and player not in e.get("player", "").lower():
            continue
        if q and q not in e.get("text", "").lower():
            continue
        out.append(e)
        if len(out) >= limit:
            break
    return {"modes": modes, "events": out, "has_file": ACTIVITY_FILE.exists()}


# ---------------------------------------------------------------- HTTP

class Handler(BaseHTTPRequestHandler):
    server_version = "DuckyMoMoPanel"

    def log_message(self, fmt, *args):
        sys.stdout.write("%s %s\n" % (self.address_string(), fmt % args))

    def authorized(self):
        header = self.headers.get("Authorization", "")
        if header.startswith("Basic "):
            try:
                user, _, pw = base64.b64decode(header[6:]).decode("utf-8").partition(":")
            except Exception:
                return False
            return hmac.compare_digest(user, PANEL_USER) and hmac.compare_digest(pw, PANEL_PASSWORD)
        return False

    def send_json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def guard(self):
        if self.authorized():
            return True
        self.send_response(401)
        self.send_header("WWW-Authenticate", 'Basic realm="DuckyMoMo", charset="UTF-8"')
        self.end_headers()
        return False

    def do_GET(self):
        if not self.guard():
            return
        path, _, query = self.path.partition("?")
        if path in ("/", "/hoatdong", "/camnang", "/huongdan", "/role"):
            dozzle = DOZZLE_PROXY_URL if self.headers.get("X-Forwarded-Prefix") else DOZZLE_URL
            page = {"/": INDEX_HTML, "/hoatdong": ACTIVITY_HTML, "/camnang": CAMNANG_HTML, "/huongdan": HUONGDAN_HTML, "/role": ROLE_HTML}[path]
            body = page.replace("__DOZZLE_URL__", dozzle).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif path == "/api/modes":
            self.send_json(200, modes_json())
        elif path == "/api/camnang":
            self.send_json(200, camnang_json())
        elif path == "/api/huongdan":
            self.send_json(200, huongdan_json())
        elif path == "/api/roles":
            try:
                self.send_json(200, roles_json())
            except Exception as e:
                self.send_json(500, {"error": f"Không đọc được LuckPerms: {e}"})
        elif path == "/api/activity":
            params = {k: v[-1] for k, v in urllib.parse.parse_qs(query).items()}
            self.send_json(200, read_activity(params))
        else:
            self.send_json(404, {"error": "không có"})

    def do_POST(self):
        if not self.guard():
            return
        if self.path == "/api/roles":
            try:
                length = int(self.headers.get("Content-Length", "0"))
                self.send_json(200, role_action(json.loads(self.rfile.read(length) or b"{}")))
            except ValueError as e:
                self.send_json(400, {"ok": False, "message": str(e)})
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})
            return
        if self.path == "/api/huongdan":
            try:
                length = int(self.headers.get("Content-Length", "0"))
                self.send_json(200, {"ok": True, "message": huongdan_save(json.loads(self.rfile.read(length) or b"{}"))})
            except ValueError as e:
                self.send_json(400, {"ok": False, "message": str(e)})
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})
            return
        m = re.fullmatch(r"/api/modes/([a-z0-9_-]+)/(save|apply-all)", self.path)
        if not m:
            self.send_json(404, {"error": "không có"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length) or b"{}")
            result = save_and_apply(m.group(1), payload.get("values", {}), apply_all=m.group(2) == "apply-all")
            self.send_json(200, result)
        except ValueError as e:
            self.send_json(400, {"ok": False, "message": str(e)})
        except Exception as e:
            self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})


if __name__ == "__main__":
    print("Trang Cài đặt chạy ở cổng 8080", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
