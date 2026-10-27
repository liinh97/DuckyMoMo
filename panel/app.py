"""Trang Cài đặt của DuckyMoMo Network.

Đọc modes/<id>/settings.schema.yml (định nghĩa) + settings.yml (giá trị). Khi bấm Lưu:
ghi giá trị vào file cấu hình plugin trong data/<server>/, lưu settings.yml, rồi gửi lệnh nạp lại
qua RCON để áp dụng ngay. Mật khẩu RCON đọc từ data/<server>/.rcon-cli.env (image itzg tự sinh).
"""

import base64
import secrets
import hmac
import json
import os
import re
import socket
import struct
import sys
import threading
import time
import urllib.parse
import urllib.request
import urllib.error
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from ruamel.yaml import YAML
from ruamel.yaml.scalarstring import DoubleQuotedScalarString, LiteralScalarString, SingleQuotedScalarString

import taikhoan
import vang

MODES_DIR = Path(os.environ.get("MODES_DIR", "/repo/modes"))
DATA_DIR = Path(os.environ.get("DATA_DIR", "/data"))
PANEL_USER = os.environ.get("PANEL_USER", "admin")
PANEL_PASSWORD = os.environ["PANEL_PASSWORD"]
DOZZLE_URL = os.environ.get("DOZZLE_URL", "")
# Mở qua Trạm 6 (gateway cắt tiền tố /mc/caidat và gửi X-Forwarded-Prefix): link log trỏ vào đường dẫn của Trạm 6
DOZZLE_PROXY_URL = os.environ.get("DOZZLE_PROXY_URL", DOZZLE_URL)
RCON_PORT = 25575
# Trang web công khai (site/) gọi panel kèm khoá này khi người chơi đăng nhập bằng tài khoản game. Trống = tắt.
PANEL_SITE_KEY = os.environ.get("PANEL_SITE_KEY", "")
# Nhật ký đăng nhập web + mọi thao tác của staff đăng nhập bằng tài khoản game
WEB_LOG_FILE = DATA_DIR / "panel" / "web-thaotac.jsonl"
GAME_SERVERS = ["lobby", "banghoi-1", "practice-1", "sangtao-1"]
INDEX_HTML = (Path(__file__).parent / "index.html").read_text(encoding="utf-8")
ACTIVITY_HTML = (Path(__file__).parent / "activity.html").read_text(encoding="utf-8")
CAMNANG_HTML = (Path(__file__).parent / "camnang.html").read_text(encoding="utf-8")
HUONGDAN_HTML = (Path(__file__).parent / "huongdan.html").read_text(encoding="utf-8")
ROLE_HTML = (Path(__file__).parent / "role.html").read_text(encoding="utf-8")
THUVIEN_HTML = (Path(__file__).parent / "thuvien.html").read_text(encoding="utf-8")
BOSS_HTML = (Path(__file__).parent / "boss.html").read_text(encoding="utf-8")
DONATE_HTML = (Path(__file__).parent / "donate.html").read_text(encoding="utf-8")
HETHONG_HTML = (Path(__file__).parent / "hethong.html").read_text(encoding="utf-8")
MEDIA_HTML = (Path(__file__).parent / "media.html").read_text(encoding="utf-8")
BOT_HTML = (Path(__file__).parent / "bot.html").read_text(encoding="utf-8")
# Khung chung mọi trang (thanh điều hướng trái, màu, nút): ui.css, shell.js
# Đăng nhập trang quản trị bằng form + cookie phiên (thay hộp thoại Basic Auth của trình duyệt). Basic vẫn nhận cho script/curl.
# Phiên giữ trong bộ nhớ: khởi động lại panel là phải đăng nhập lại. Sai 5 lần/10 phút trên một IP thì chặn tạm.
SESSIONS = {}                 # sha256(token) -> hết hạn (epoch giây); lưu ở data/panel/phien-quantri.json để dựng lại panel không bị đăng xuất
# Thời hạn đăng nhập trang quản trị: 12 giờ (23/10 mở server; giai đoạn test 21/10 từng để 720). Đổi: PANEL_SESSION_HOURS trong .env.
SESSION_HOURS = int(os.environ.get("PANEL_SESSION_HOURS", "12"))
SESSION_FILE = DATA_DIR / "panel" / "phien-quantri.json"


def _tok_hash(tok):
    return hashlib.sha256(tok.encode()).hexdigest()


def panel_hours():
    """Giờ giữ đăng nhập trang quản trị: Cài đặt › Toàn mạng › Đăng nhập lâu (dnl_panel_gio), không có thì SESSION_HOURS."""
    try:
        v = YAML(typ="safe").load((MODES_DIR / "_network" / "settings.yml").read_text(encoding="utf-8")) or {}
        return max(1, int(v.get("dnl_panel_gio") or SESSION_HOURS))
    except Exception:
        return SESSION_HOURS


def _load_sessions():
    try:
        now = time.time()
        # phiên lưu từ hồi hạn dài hơn (test 21/10: 30 ngày) bị cắt về tối đa SESSION_HOURS kể từ lúc dựng lại
        SESSIONS.update({k: min(v, now + panel_hours() * 3600) for k, v in json.loads(SESSION_FILE.read_text(encoding="utf-8")).items() if v > now})
    except (OSError, ValueError):
        pass


def _save_sessions():
    try:
        SESSION_FILE.parent.mkdir(parents=True, exist_ok=True)
        tmp = SESSION_FILE.with_suffix(".tmp")
        tmp.write_text(json.dumps(SESSIONS), encoding="utf-8")
        tmp.replace(SESSION_FILE)
    except OSError as e:
        sys.stdout.write(f"Không lưu được phiên quản trị: {e}\n")
LOGIN_FAILS = {}              # ip -> [thời điểm sai]
LOGIN_LOCK = threading.Lock()
LOGIN_HTML = """<!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>DuckyMoMo · Đăng nhập quản trị</title><meta name="robots" content="noindex">
<style>
:root{--bg:#f3f4f7;--card:#fff;--text:#1b1f29;--muted:#6b7385;--line:#e3e6ec;--input:#fff;--accent:#e8711c;--err:#d93025}
@media (prefers-color-scheme:dark){:root{--bg:#0f1115;--card:#171a21;--text:#e7e9ee;--muted:#98a2b3;--line:#2a2f3a;--input:#12151b;--err:#f28b82}}
*{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;background:var(--bg);color:var(--text);
font:15px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;padding:24px 16px}
form{width:100%;max-width:360px;background:var(--card);border:1px solid var(--line);border-radius:14px;padding:28px 24px;display:grid;gap:14px}
.brand{display:flex;align-items:center;gap:10px}.brand span{font-size:30px;line-height:1}.brand b{display:block;font-size:18px}.brand small{color:var(--muted)}
label{display:grid;gap:5px;font-size:13.5px;font-weight:600}
input{font:inherit;padding:10px 12px;border:1px solid var(--line);border-radius:9px;background:var(--input);color:var(--text)}
input:focus{outline:2px solid var(--accent);outline-offset:1px;border-color:var(--accent)}
button{font:inherit;font-weight:700;padding:11px;border:0;border-radius:9px;background:var(--accent);color:#fff;cursor:pointer}
button:disabled{opacity:.6;cursor:wait}.msg{min-height:1.2em;margin:0;color:var(--err);font-size:13.5px}.hint{margin:0;color:var(--muted);font-size:12.5px}
</style></head><body>
<form id="f" autocomplete="on">
  <div class="brand"><span>🦆</span><div><b>DuckyMoMo</b><small>Trang quản trị</small></div></div>
  <label>Tên đăng nhập<input id="ten" name="username" autocomplete="username" required autofocus></label>
  <label>Mật khẩu<input id="matkhau" name="password" type="password" autocomplete="current-password" required></label>
  <p class="msg" id="msg" role="alert"></p>
  <button id="nut">Đăng nhập</button>
  <p class="hint">Tài khoản quản trị trong file .env (PANEL_USER / PANEL_PASSWORD). Staff dùng mã /web trong game ở trang tài khoản.</p>
</form>
<script>
document.getElementById("f").onsubmit = async e => {
  e.preventDefault();
  const nut = document.getElementById("nut"), msg = document.getElementById("msg");
  nut.disabled = true; msg.textContent = "";
  try {
    const r = await fetch("dangnhap", {method: "POST", headers: {"Content-Type": "application/json"}, credentials: "same-origin",
      body: JSON.stringify({ten: document.getElementById("ten").value, matkhau: document.getElementById("matkhau").value})});
    const d = await r.json().catch(() => ({}));
    if (r.ok && d.ok) return location.reload();
    msg.textContent = d.message || "Không đăng nhập được.";
  } catch (err) { msg.textContent = "Không kết nối được máy chủ."; }
  nut.disabled = false;
};
</script></body></html>"""

STATIC = {"/ui.css": ("text/css; charset=utf-8", (Path(__file__).parent / "ui.css").read_bytes()),
          "/shell.js": ("application/javascript; charset=utf-8", (Path(__file__).parent / "shell.js").read_bytes())}
# Trang Role: role chức vụ (track staff + builder + default) và rank donate (track donate), lưu ở LuckPerms (MariaDB)
STAFF_GROUPS = ["admin", "quanly", "mod", "helper", "builder", "default"]
STAFF_CHAIN = ["admin", "quanly", "mod", "helper"]   # role chức vụ theo bậc: mỗi người một role (builder riêng, giữ được song song)
DONATE_GROUPS = ["congtuoc", "hautuoc", "batuoc", "tutuoc", "namtuoc"]
PLAY_GROUPS = ["haokiet", "luongdan", "dan"]   # Danh phận (lên bậc tự động bằng /danhphan): trang Role chỉ sửa QUYỀN, không thêm/gỡ người
PLAYER_RE = re.compile(r"^\.?[A-Za-z0-9_]{1,17}$")
PERM_RE = re.compile(r"^[A-Za-z0-9_.*-]{1,120}$")
SERVER_CTX = ["global", "lobby", "banghoi-1", "practice-1", "sangtao-1"]
QUYEN_FILE = MODES_DIR / "_network" / "quyen.yml"
# Cẩm nang trong game (/huongdan): bản gốc + các bản chép (repo của từng server, data/ để có hiệu lực ngay)
HUONGDAN_FILE = MODES_DIR / "_network" / "huongdan.yml"
HUONGDAN_COPIES = [MODES_DIR / "banghoi/server/plugins/DuckyMoMoCore/huongdan.yml",
                   MODES_DIR / "practice/server/plugins/DuckyMoMoCore/huongdan.yml",
                   MODES_DIR / "sangtao/server/plugins/DuckyMoMoCore/huongdan.yml",
                   Path("/repo/lobby-dmm/huongdan.yml")] + \
                  [DATA_DIR / srv / "plugins/DuckyMoMoCore/huongdan.yml" for srv in ("lobby", "banghoi-1", "practice-1", "sangtao-1")]
ID_RE = re.compile(r"^[a-z0-9_-]{1,32}$")
# Cẩm nang: nội dung ở modes/_network/camnang.yml (sửa không cần build lại); server quét plugin thật đang cài
CAMNANG_FILE = MODES_DIR / "_network" / "camnang.yml"
CAMNANG_SERVERS = ["proxy", "lobby", "banghoi-1", "practice-1", "sangtao-1", "pokemon-1"]
# Do service "activity" (dashboard/log-feed.sh, JSONL=…) ghi ra
ACTIVITY_FILE = DATA_DIR / "activity" / "activity.jsonl"
# Tin admin gửi từ trang Hoạt động: file riêng (activity.jsonl thuộc service activity, panel không ghi được), gộp khi đọc
ADMIN_FILE = DATA_DIR / "panel" / "admin.jsonl"
ACTIVITY_TAIL_BYTES = 4_000_000
# Quà (DuckyMoMoCore Rewards): xu <số> | do <mã thư viện> [số] | vat <VẬT_LIỆU> [số] | lenh <lệnh> | manh <loại> <số>
REWARD_RES = [re.compile(r"^xu \d{1,9}(\.\d{1,2})?$"), re.compile(r"^do [a-z0-9_]{1,32}( \d{1,2})?$"),
              re.compile(r"^vat [A-Za-z0-9_]{1,64}( \d{1,4})?$"), re.compile(r"^lenh \S.{0,199}$"),
              re.compile(r"^manh [a-z0-9_]{1,32} \d{1,4}$"), re.compile(r"^petexp \d{1,6}$"), re.compile(r"^danhhieu [a-z0-9_]{1,32}$")]
FISH_COST_RE = re.compile(r"^ca:(common|rare|epic|legendary)$")   # giá đổi bằng cá câu (EvenMoreFish) theo độ hiếm
# Hiệu ứng Xưởng chế tạo: mã -> có dùng "value" không (khớp Workshop.FEATURES trong DuckyMoMoCore)
WORKSHOP_FEATURES = {"": False, "dao_vung": True, "chat_cay": True, "tu_nung": False, "da_nang": False, "gat_lua": False, "hut_do": False,
                     "nam_cham": True, "ban_ruong": False, "tui_hu": False, "nuoc": False, "dung_nham": False, "bat_thu": False, "co_le": False,
                     "moc_bay": True, "hut_mau": True, "khien_than": True, "kiem_nhat": True, "riu_chien": True}
# tên + nghĩa của "value" từng kỹ năng (khớp Workshop.FEATURES), cho trang Thư viện đồ
WORKSHOP_FEATURES_VI = {"": ["Không (chỉ phù phép)", ""], "dao_vung": ["Đào vùng", "1 = 3×3, 2 = 5×5"], "chat_cay": ["Chặt cả cây", "số khối gỗ tối đa"],
                        "tu_nung": ["Tự nung (quặng ra thỏi)", ""], "da_nang": ["3 trong 1 (cuốc/rìu/xẻng)", ""], "gat_lua": ["Gặt 3×3 + gieo lại", ""],
                        "hut_do": ["Đồ đào/săn bay vào túi", ""], "nam_cham": ["Hút đồ quanh người", "bán kính (khối)"], "ban_ruong": ["Bán cả rương", ""],
                        "tui_hu": ["Rương ender từ xa", ""], "nuoc": ["Nước vô tận", ""], "dung_nham": ["Dung nham vô tận", ""],
                        "bat_thu": ["Bắt thú thành trứng", ""], "co_le": ["Lấy lồng quái", ""], "moc_bay": ["Móc câu bay", "lực kéo (1–3)"],
                        "hut_mau": ["Hút máu (cung/nỏ)", "máu hồi/phát (2 = 1 tim)"], "khien_than": ["Giảm sát thương + phản đòn (khiên)", "% giảm"],
                        "kiem_nhat": ["Lướt chém (chuột phải)", "sát thương"], "riu_chien": ["Chí mạng + chảy máu", "% chí mạng"]}
DISH_FOODS = {"BREAD", "COOKED_COD", "COOKED_SALMON", "BEETROOT_SOUP", "RABBIT_STEW", "MUSHROOM_STEW", "SUSPICIOUS_STEW", "BAKED_POTATO",
              "PUMPKIN_PIE", "COOKIE", "GOLDEN_CARROT", "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN", "COOKED_MUTTON", "COOKED_RABBIT",
              "DRIED_KELP", "HONEY_BOTTLE", "APPLE", "SWEET_BERRIES", "GLOW_BERRIES"}   # món ăn thư viện đồ phải là một trong số này
DISH_EFFECTS = {"REGENERATION", "RESISTANCE", "ABSORPTION", "STRENGTH", "SPEED", "FIRE_RESISTANCE", "NIGHT_VISION", "WATER_BREATHING",
                "JUMP_BOOST", "HASTE", "SATURATION", "HEALTH_BOOST"}


def check_rewards(label, v):
    out = [" ".join(str(x).split()) for x in (v or []) if str(x).strip()]
    lib = None
    for r in out:
        if not any(rx.match(r) for rx in REWARD_RES):
            raise ValueError(f"{label}: quà không hợp lệ \"{r}\"")
        if r.startswith("do "):
            lib = lib if lib is not None else lib_items()
            if r.split()[1] not in lib:
                raise ValueError(f"{label}: thư viện đồ không có \"{r.split()[1]}\"")
    return out


QUEST_TYPES = ("break", "place", "kill", "fish", "harvest")
QUEST_TARGET_RE = re.compile(r"^(\*|@HOSTILE|\*?[A-Z0-9_]{1,64}\*?)$")
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
    if isinstance(v, dict):
        return {str(k): plain(x) for k, x in v.items()}
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
    if t == "rewards":
        return check_rewards(label, v)
    if t == "milestones":
        out = {}
        for k, rw in (v or {}).items():
            try:
                day = int(k)
            except (TypeError, ValueError):
                raise ValueError(f"{label}: mốc phải là số ngày")
            if not 1 <= day <= 3650:
                raise ValueError(f"{label}: mốc 1–3650 ngày")
            lst = check_rewards(f"{label} (mốc {day})", rw)
            if lst:
                out[day] = lst
        return dict(sorted(out.items()))
    if t == "shopitems":
        out = []
        for d in (v or []):
            d = d or {}
            try:
                amount, price = int(d.get("amount") or 1), float(d.get("price"))
            except (TypeError, ValueError):
                raise ValueError(f"{label}: số lượng / giá phải là số")
            if not (1 <= amount <= 2304 and 0 < price <= 100000000):
                raise ValueError(f"{label}: số lượng 1–2304, giá lớn hơn 0")
            price = int(price) if price == int(price) else price
            cat = str(d.get("cat") or "").strip().lower()
            if cat and not re.match(r"^[a-z0-9_]{1,24}$", cat):
                raise ValueError(f"{label}: mã danh mục sai \"{cat}\"")
            if d.get("library"):
                lid = str(d["library"]).strip()
                if not LIB_ID_RE.match(lid):
                    raise ValueError(f"{label}: mã thư viện sai \"{lid}\"")
                out.append({"library": lid, "amount": amount, "price": price, **({"cat": cat} if cat else {})})
            else:
                mat = str(d.get("material", "")).strip().upper()
                meta = lib_meta() or {}
                if "*" in mat:   # mẫu: *_WOOL = mọi loại len (đồ đặc thù tự bị loại, xem DecorShop.DENY)
                    if not re.match(r"^\*?[A-Z0-9_]{1,64}\*?$", mat):
                        raise ValueError(f"{label}: mẫu vật liệu sai \"{mat}\" (vd *_WOOL, STRIPPED_*)")
                elif not MATERIAL_RE.match(mat) or (meta.get("materials") and mat not in meta["materials"]):
                    raise ValueError(f"{label}: vật liệu không có trong server \"{mat}\"")
                out.append({"material": mat, "amount": amount, "price": price, **({"cat": cat} if cat else {})})
        return out
    if t == "shopcats":
        # danh mục cửa hàng: [{id, name, icon, slot 0-44, desc, sell [VẬT_LIỆU | *_ĐUÔI | ĐẦU_*]}]
        out, seen = [], set()
        for d in (v or []):
            d = d or {}
            cid = str(d.get("id", "")).strip().lower()
            if not re.match(r"^[a-z0-9_]{1,24}$", cid) or cid in seen:
                raise ValueError(f"{label}: mã danh mục sai hoặc trùng \"{cid}\"")
            seen.add(cid)
            icon = str(d.get("icon", "CHEST")).strip().upper() or "CHEST"
            if not MATERIAL_RE.match(icon):
                raise ValueError(f"{label} ({cid}): vật phẩm biểu tượng sai")
            try:
                slot = int(d.get("slot", -1))
            except (TypeError, ValueError):
                raise ValueError(f"{label} ({cid}): ô phải là số")
            if not -1 <= slot <= 44:
                raise ValueError(f"{label} ({cid}): ô 0–44 (-1 = tự xếp)")
            sell = []
            for x in d.get("sell") or []:
                x = str(x).strip().upper()
                if not x:
                    continue
                if not re.match(r"^\*?[A-Z0-9_]{1,64}\*?$", x):
                    raise ValueError(f"{label} ({cid}): mẫu thu mua sai \"{x}\"")
                sell.append(x)
            items = validate({"type": "shopitems", "label": f"{label} ({d.get('name') or cid})"}, d.get("items") or []) if d.get("items") else []
            out.append({"id": cid, "name": DoubleQuotedScalarString(str(d.get("name", "")).strip()[:40] or cid), "icon": icon, "slot": slot,
                        "desc": DoubleQuotedScalarString(str(d.get("desc", "")).strip()[:80]), "sell": sell,
                        "items": [{k: v for k, v in it.items() if k != "cat"} for it in items]})
        if not out:
            raise ValueError(f"{label}: cần ít nhất một danh mục")
        return out
    if t == "shoptitles":
        out = []
        for d in (v or []):
            d = d or {}
            tid = str(d.get("id", "")).strip().lower()
            if not LIB_ID_RE.match(tid):
                raise ValueError(f"{label}: mã danh hiệu sai \"{tid}\"")
            try:
                price = float(d.get("price"))
            except (TypeError, ValueError):
                raise ValueError(f"{label}: giá phải là số")
            text = str(d.get("text", "")).strip()[:40]
            if not text or price <= 0:
                raise ValueError(f"{label} ({tid}): cần chữ hiển thị và giá")
            out.append({"id": tid, "text": DoubleQuotedScalarString(text), "price": int(price) if price == int(price) else price})
        return out
    if t == "fragtypes":
        # loại mảnh = món thư viện đồ công dụng "manh": {mã: {until}}
        out = {}
        for fid, d in (v or {}).items():
            fid = lib_need(label, str(fid).strip().lower(), "manh")
            until = str((d or {}).get("until") or "").strip()
            if until and not re.match(r"^\d{4}-\d{2}-\d{2}$", until):
                raise ValueError(f"{label} ({fid}): hạn đổi dạng năm-tháng-ngày")
            out[fid] = {"until": until}
        return out
    if t == "libitem":
        # một món thư viện đồ (use: công dụng bắt buộc, vd bua-bat-pet)
        return lib_need(label, str(v or "").strip().lower(), s.get("use"))
    if t == "fragshop":
        out = []
        for o in (v or []):
            o = o or {}
            cost = {}
            for k, n in (o.get("cost") or {}).items():
                k = str(k).strip().lower()
                try:
                    n = int(n)
                except (TypeError, ValueError):
                    raise ValueError(f"{label}: giá phải là số")
                if not (LIB_ID_RE.match(k) or FISH_COST_RE.match(k)) or not 1 <= n <= 100000:
                    raise ValueError(f"{label}: giá sai ({k} {n})")
                cost[k] = n
            rw = check_rewards(label, o.get("rewards"))
            if not cost or not rw:
                raise ValueError(f"{label}: mỗi món cần giá và quà")
            try:
                limit = int(o.get("limit") or 0)
            except (TypeError, ValueError):
                raise ValueError(f"{label}: giới hạn phải là số")
            out.append({"label": str(o.get("label", "")).strip()[:40] or rw[0], "cost": cost, "limit": max(0, limit), "rewards": rw})
        return out
    if t == "dishes":
        # món ăn ra trận: {mã thư viện (công dụng mon-an): {need {VẬT_LIỆU|ca:<độ hiếm>: số}}}; món + hiệu ứng ở thư viện đồ
        out = {}
        for did, d in (v or {}).items():
            did = lib_need(label, str(did).strip().lower(), "mon-an")
            need = {}
            for k, n in ((d or {}).get("need") or {}).items():
                k = str(k).strip()
                k = k.lower() if FISH_COST_RE.match(k.lower()) else k.upper()
                try:
                    n = int(n)
                except (TypeError, ValueError):
                    raise ValueError(f"{label} ({did}): số lượng nguyên liệu phải là số")
                if not (FISH_COST_RE.match(k) or MATERIAL_RE.match(k)) or not 1 <= n <= 640:
                    raise ValueError(f"{label} ({did}): nguyên liệu sai ({k} {n})")
                need[k] = n
            if not need:
                raise ValueError(f"{label} ({did}): mỗi món cần ít nhất 1 nguyên liệu")
            out[did] = {"need": need}
        if len(out) > 7:
            raise ValueError(f"{label}: tối đa 7 món (bếp chỉ có 7 ô)")
        return out
    if t == "workshop":
        # Xưởng: {mã thư viện (công dụng ky-nang): {cost, grid [9 ô "VẬT_LIỆU số"|""], tinh-luyen {mã: số}, enabled}}; món + kỹ năng ở thư viện đồ
        out = {}
        for wid, d in (v or {}).items():
            wid = lib_need(label, str(wid).strip().lower(), "ky-nang")
            d = d or {}
            try:
                cost = int(float(d.get("cost") or 0))
            except (TypeError, ValueError):
                raise ValueError(f"{label} ({wid}): giá phải là số")
            if not 0 <= cost <= 1_000_000_000:
                raise ValueError(f"{label} ({wid}): giá 0–1.000.000.000")
            grid = []
            for g in list(d.get("grid") or [])[:9]:
                g = " ".join(str(g or "").split()).upper()
                if g:
                    m = re.match(r"^([A-Z0-9_]+)(?:[ :X×]+(\d{1,3}))?$", g)
                    if not m or not 1 <= int(m.group(2) or 1) <= 640:
                        raise ValueError(f"{label} ({wid}): ô nguyên liệu sai \"{g}\" (dạng VẬT_LIỆU số)")
                    g = f"{m.group(1)} {int(m.group(2) or 1)}"
                grid.append(g)
            grid += [""] * (9 - len(grid))
            if not any(grid) and cost <= 0:
                raise ValueError(f"{label} ({wid}): cần ít nhất nguyên liệu hoặc giá xu")
            o = {}
            if d.get("enabled") is False:
                o["enabled"] = False
            o.update({"cost": cost, "grid": grid})
            tl = {}
            for k, n in (d.get("tinh-luyen") or {}).items():
                k = str(k).strip().lower()
                if not re.match(r"^[a-z0-9_]{1,32}$", k):
                    raise ValueError(f"{label} ({wid}): mã món tinh luyện sai \"{k}\" (xem /tinhluyen ds)")
                try:
                    n = int(n)
                except (TypeError, ValueError):
                    raise ValueError(f"{label} ({wid}): số món tinh luyện phải là số")
                if not 1 <= n <= 64:
                    raise ValueError(f"{label} ({wid}): số món tinh luyện 1–64")
                tl[k] = n
            if tl:
                o["tinh-luyen"] = tl
            out[wid] = o
        if len(out) > 28:
            raise ValueError(f"{label}: tối đa 28 món (menu có 28 ô)")
        return out
    if t == "crates":
        # Gacha: [{id, name, icon, key (món thư viện công dụng chia-khoa), source, prizes[spin]}]
        out, seen = [], set()
        for c in (v or []):
            c = c or {}
            cid = str(c.get("id") or "").strip().lower()
            if not LIB_ID_RE.match(cid):
                raise ValueError(f"{label}: mã máy gacha \"{cid}\" chỉ gồm chữ thường không dấu, số, _")
            if cid in seen:
                raise ValueError(f"{label}: trùng mã máy gacha \"{cid}\"")
            seen.add(cid)
            name = " ".join(str(c.get("name") or "").split())[:40] or cid
            icon = str(c.get("icon") or "CHEST").strip().upper()
            if not MATERIAL_RE.match(icon):
                raise ValueError(f"{label} ({cid}): biểu tượng sai \"{icon}\"")
            key = lib_need(f"{label} ({cid}) vé", str(c.get("key") or "").strip().lower(), "chia-khoa")
            prizes = validate({"type": "spin", "label": f"{label} ({cid})"}, c.get("prizes"))
            if len(prizes) > 36:
                raise ValueError(f"{label} ({cid}): tối đa 36 phần thưởng (menu 4 hàng)")
            out.append({"id": cid, "name": name, "icon": icon, "key": key,
                        "source": " ".join(str(c.get("source") or "").split())[:80], "prizes": prizes})
        if len(out) > 27:
            raise ValueError(f"{label}: tối đa 27 máy gacha")
        return out
    if t == "spin":
        out = []
        for pz in (v or []):
            pz = pz or {}
            try:
                w = int(pz.get("weight"))
            except (TypeError, ValueError):
                raise ValueError(f"{label}: trọng số phải là số")
            if not 1 <= w <= 100000:
                raise ValueError(f"{label}: trọng số 1–100000")
            rw = check_rewards(label, pz.get("rewards"))
            if not rw:
                raise ValueError(f"{label}: mỗi phần thưởng cần ít nhất một món quà")
            name = str(pz.get("label", "")).strip()[:40] or rw[0]
            out.append({"label": name, "weight": w, "rare": bool(pz.get("rare")), "rewards": rw})
        if not out:
            raise ValueError(f"{label}: cần ít nhất một phần thưởng")
        return out
    if t == "kits":
        # Gói quà /kit: {mã: {ten, icon, cho (giây, -1 = một lần), rank [nhóm LuckPerms], qua [Rewards]}}
        out = {}
        for kid, k in (v or {}).items():
            kid = str(kid).strip().lower()
            if not LIB_ID_RE.match(kid):
                raise ValueError(f"{label}: mã gói \"{kid}\" chỉ gồm chữ thường không dấu, số, _")
            k = k or {}
            icon = str(k.get("icon") or "CHEST").strip().upper()
            if not MATERIAL_RE.match(icon):
                raise ValueError(f"{label} ({kid}): biểu tượng sai \"{icon}\"")
            try:
                cho = int(k.get("cho", -1))
            except (TypeError, ValueError):
                raise ValueError(f"{label} ({kid}): thời gian chờ phải là số giây")
            if not -1 <= cho <= 366 * 86400:
                raise ValueError(f"{label} ({kid}): thời gian chờ -1 (một lần) đến 366 ngày")
            ranks = [str(r).strip().lower() for r in (k.get("rank") or []) if str(r).strip()]
            for r in ranks:
                if not re.match(r"^[a-z0-9_\-]{1,32}$", r):
                    raise ValueError(f"{label} ({kid}): tên rank sai \"{r}\"")
            qua = check_rewards(f"{label} ({kid})", k.get("qua"))
            if not qua:
                raise ValueError(f"{label} ({kid}): cần ít nhất một món quà")
            out[kid] = {"ten": str(k.get("ten") or kid).strip()[:40] or kid, "icon": icon, "cho": cho, "rank": ranks, "qua": qua}
        return out
    if t == "quests":
        out = {}
        for qid, q in (v or {}).items():
            qid = str(qid).strip()
            if not LIB_ID_RE.match(qid):
                raise ValueError(f"{label}: mã nhiệm vụ \"{qid}\" chỉ gồm chữ thường không dấu, số, _")
            q = q or {}
            qtype = str(q.get("type", "")).lower()
            if qtype not in QUEST_TYPES:
                raise ValueError(f"{label} ({qid}): loại không hợp lệ")
            targets = [str(x).strip().upper() for x in (q.get("targets") or []) if str(x).strip()] or ["*"]
            for tg in targets:
                if not QUEST_TARGET_RE.match(tg):
                    raise ValueError(f"{label} ({qid}): mục tiêu sai \"{tg}\"")
            try:
                amount = int(q.get("amount"))
            except (TypeError, ValueError):
                raise ValueError(f"{label} ({qid}): số lượng phải là số")
            if not 1 <= amount <= 100000:
                raise ValueError(f"{label} ({qid}): số lượng 1–100000")
            name = str(q.get("name", "")).strip()[:60] or qid
            out[qid] = {"name": name, "type": qtype, "targets": targets, "amount": amount,
                        "rewards": check_rewards(f"{label} ({qid})", q.get("rewards"))}
        if not out:
            raise ValueError(f"{label}: cần ít nhất một nhiệm vụ")
        return out
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


def mirror_worth(doc):
    """Essentials tra giá /sell theo tên bỏ dấu _ (LAPIS_LAZULI -> worth.lapislazuli); bảng giá ghi tên có dấu _ (code DuckyMoMoCore
    đọc tên đó). Chép giá sang tên liền, thiếu thì món không bán được (tools/shop/worth-essentials.py làm cùng việc cho cả file)."""
    w = doc.get("worth") if hasattr(doc, "get") else None
    if not hasattr(w, "items"):
        return
    for k, v in list(w.items()):
        if "_" in str(k):
            w[str(k).replace("_", "")] = v


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
    # targets: [{file, path}] = ghi cùng giá trị vào nhiều chỗ (vd trần cấp cho mọi kỹ năng AuraSkills);
    # template: chuỗi có {value} để ghép giá trị (vd "eco give {player} {value}"; chỉ {value} được thay)
    by_file = {}
    for _, s, v in changed:
        out = s["template"].replace("{value}", str(v)) if s.get("template") else v
        spots = ([(s["file"], s["path"])] if s.get("file") else []) + [(t["file"], t["path"]) for t in s.get("targets", [])]
        for rel, dotted in spots:
            for srv in servers_of(s):
                by_file.setdefault((srv, rel), []).append((dotted, out))
    for (srv, rel), items in by_file.items():
        path = DATA_DIR / srv / rel
        if not path.exists():
            raise FileNotFoundError(f"chưa có {srv}/{rel}: server cần chạy ít nhất một lần để plugin tạo file")
        y = yaml_rt()
        doc = y.load(path.read_text(encoding="utf-8"))
        for dotted, v in items:
            set_path(doc, dotted, v)
        if rel.endswith("Essentials/worth.yml"):
            mirror_worth(doc)
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
    # Lệnh dùng được {value}, {double} (giá trị x2), {minus1} (giá trị - 1, tối thiểu 0) và {tên_mục_khác} (giá trị hiện tại của mục đó)
    # Lệnh nhóm (apply) chạy trên mọi server có mục đổi trong nhóm đó.
    current = {k: plain(v) for k, v in values.items()}
    per_server = {}
    for group, s, v in changed:
        for c in s.get("commands", []):
            num = s["type"] in ("int", "number")
            cmd = c.format(**current, value=v, double=int(v) * 2 if num else v, minus1=max(0, int(v) - 1) if num else v)
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

    stored_only = [s["label"] for _, s, _ in changed if not s.get("file") and not s.get("targets") and not s.get("commands")]
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
    return {"guide": doc.get("guide", []), "plugins": doc.get("plugins", []), "servers": list(jars), "unknown": unknown,
            "commands": all_commands(doc)}


# Lệnh vanilla mà người chơi thường dùng được (permission level 0)
VANILLA_PLAYER = {"help", "list", "me", "msg", "tell", "w", "teammsg", "tm", "trigger", "random"}


def all_commands(doc):
    """Mọi lệnh của lobby/banghoi/practice (DuckyMoMoCore xuất ra commands.json) + mô tả tiếng Việt + ai dùng được."""
    vi = {}
    f = MODES_DIR / "_network" / "lenh-vi.yml"
    if f.exists():
        vi = {str(k).lower(): str(v) for k, v in (YAML(typ="safe").load(f.read_text(encoding="utf-8")) or {}).items()}
    # mô tả trong cẩm nang tay: dòng lệnh "/ten ..." -> mô tả (không ghi đè lenh-vi.yml)
    for p in doc.get("plugins", []):
        for row in (p.get("player") or []) + (p.get("admin") or []):
            for part in str(row[0]).split("·"):
                m = re.match(r"\s*/([a-z0-9_:-]+)", part.strip())
                if m:
                    vi.setdefault(m.group(1).split(":")[-1], str(row[1]))
    # quyền của từng nhóm (để biết ai dùng được)
    holders = {}
    try:
        con = lp_db()
        try:
            cur = con.cursor()
            cur.execute("SELECT name, permission, value, server FROM luckperms_group_permissions")
            for name, perm, value, server in cur.fetchall():
                holders.setdefault(perm.lower(), []).append((name, bool(value), server))
        finally:
            con.close()
    except Exception:
        pass
    order = ["default", "namtuoc", "tutuoc", "batuoc", "hautuoc", "congtuoc", "builder", "helper", "mod", "quanly"]

    def who(perm, default, server):
        if not perm:
            return "?"
        hs = [h for h in holders.get(perm.lower(), []) if h[2] in ("global", server)]
        if any(n == "default" and not v for n, v, _ in hs):
            return "Chỉ admin"
        if any(n == "default" and v for n, v, _ in hs) or default in ("true",):
            return "Mọi người"
        grp = [n for n in order if any(h[0] == n and h[1] for h in hs)]
        if grp:
            return "Từ " + grp[0]
        return "Chỉ admin"

    out = {}
    # lệnh đang chạy thật của từng plugin ở từng server (tên + tên gọi khác), để lọc lệnh ghi tay bên dưới
    active = {}
    for srv in ("lobby", "banghoi-1", "practice-1", "sangtao-1"):
        f = DATA_DIR / srv / "plugins" / "DuckyMoMoCore" / "commands.json"
        if not f.exists():
            continue
        try:
            data = json.loads(f.read_text(encoding="utf-8"))
        except Exception:
            continue
        rows = []
        for c in data.get("commands", []):
            # lệnh DuckyMoMoCore có đăng ký nhưng module tắt ở server này (chỉ báo "chỉ có ở Bang Hội Chiến"): không liệt kê
            if c.get("off"):
                continue
            active.setdefault(srv, {}).setdefault(c["plugin"].lower(), set()).update(
                [c["name"].split(":")[-1].lower()] + [a.lower() for a in c.get("aliases", [])])
            # vài plugin (Paper bọc lệnh) tự đặt tên kèm "plugin:" -> bỏ đi
            name, plugin, perm, default = c["name"].split(":")[-1], c["plugin"], c.get("permission") or "", c.get("default") or ""
            c["aliases"] = [a for a in c.get("aliases", []) if ":" not in a and a != name]
            if not perm and plugin.lower().startswith("essentials"):
                perm, default = f"essentials.{name}", ""
            if c["ns"] == "minecraft":
                plugin = "Minecraft (gốc)"
                w = "Mọi người" if name in VANILLA_PLAYER else "Chỉ admin"
            else:
                w = who(perm, default, srv)
            rows.append({"plugin": plugin, "name": name, "aliases": c.get("aliases", []), "desc_vi": vi.get(f"{c['ns']}:{name}".lower()) or vi.get(name.lower(), ""),
                         "desc": c.get("description", ""), "usage": c.get("usage", ""), "perm": perm, "who": w})
        # bỏ dòng trùng: tên đã là tên gọi tắt của lệnh khác cùng plugin (uJobs đăng ký /job, /ujob… riêng)
        alias_of = {(r["plugin"], a) for r in rows for a in r["aliases"]}
        rows = [r for r in rows if (r["plugin"], r["name"]) not in alias_of or r["aliases"]]
        out[srv] = {"generated": data.get("generated"), "commands": rows}
    # lệnh ghi tay: proxy + lệnh con của plugin lớn
    f = MODES_DIR / "_network" / "lenh-con.yml"
    if f.exists():
        for g in (YAML(typ="safe").load(f.read_text(encoding="utf-8")) or {}).get("groups", []):
            for srv in g.get("servers", []):
                if srv != "proxy" and srv not in out:
                    continue
                d = out.setdefault(srv, {"generated": None, "commands": []})
                plug = active.get(srv, {}).get(str(g["plugin"]).lower())
                for row in g.get("commands", []):
                    # plugin có trong danh sách tự xuất của server này mà lệnh gốc (/diemdanh của "diemdanh xem") không chạy
                    # ở đây (module tắt, chỉ có ở Bang Hội Chiến) -> không liệt kê cho server này
                    if plug is not None and str(row[0]).split()[0].lower() not in plug:
                        continue
                    same = [r for r in d["commands"] if r["plugin"] == g["plugin"] and r["name"] == str(row[0])]
                    if same:  # trùng lệnh tự xuất: dùng mô tả + "ai dùng được" ghi tay
                        same[0].update(desc_vi=str(row[1]), who=str(row[2]) if len(row) > 2 else same[0]["who"])
                        continue
                    d["commands"].append({"plugin": g["plugin"], "name": str(row[0]), "aliases": [], "desc_vi": str(row[1]),
                                          "desc": "", "usage": "", "perm": "", "who": str(row[2]) if len(row) > 2 else "?",
                                          "manual": True})
    return out


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


def lp_player(name):
    """(UUID, tên đúng) trong LuckPerms theo tên, không phân biệt hoa thường; quên dấu chấm của Bedrock thì thử thêm "."
    None nếu chưa từng vào server."""
    con = lp_db()
    try:
        cur = con.cursor()
        for n in [name] + ([] if name.startswith(".") else ["." + name]):
            cur.execute("SELECT uuid, username FROM luckperms_players WHERE LOWER(username) = LOWER(%s) LIMIT 1", (n,))
            row = cur.fetchone()
            if row:
                return row[0], row[1]
        return None
    finally:
        con.close()


def group_weights():
    """weight của từng nhóm LuckPerms (meta weight.N; không có = 0)."""
    con = lp_db()
    try:
        cur = con.cursor()
        cur.execute("SELECT name, permission FROM luckperms_group_permissions WHERE permission LIKE 'weight.%%' AND value = 1")
        out = {}
        for name, perm in cur.fetchall():
            try:
                out[name] = max(out.get(name, 0), int(perm.split(".", 1)[1]))
            except ValueError:
                pass
        return out
    finally:
        con.close()


def staff_weight(perms):
    """weight của role chức vụ cao nhất người chơi đang có (kể cả kế thừa); 0 nếu chỉ là Dân."""
    w = group_weights()
    return max([w.get(g, 0) for g in perms["groups"] if g in STAFF_GROUPS and g != "default"] or [0])


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
                # nhiều prefix (sót từ lần đặt trước, khác priority): LuckPerms hiện cái priority cao nhất, trang cũng vậy
                _, w, text = perm.split(".", 2)
                w = int(w) if w.lstrip("-").isdigit() else 0
                if w >= m.get("prefix_w", -10**9):
                    m["prefix"], m["prefix_w"] = text, w
                m.setdefault("prefixes", []).append(text)
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
        cur.execute("SELECT name, permission, value, server FROM luckperms_group_permissions WHERE permission NOT LIKE 'prefix.%%' "
                    "AND permission NOT LIKE 'weight.%%' AND permission NOT LIKE 'displayname.%%' AND permission NOT LIKE 'suffix.%%'")
        perms, parents = {}, {}
        for name, perm, value, server in cur.fetchall():
            if perm.startswith("group."):
                parents.setdefault(name, []).append(perm[6:])
            else:
                perms.setdefault(name, []).append({"node": perm, "value": bool(value), "server": server})
        cur.execute("SELECT name FROM luckperms_groups")
        existing = {r[0] for r in cur.fetchall()}
        cur.execute("SELECT COUNT(*) FROM luckperms_players")
        total = cur.fetchone()[0]
    finally:
        con.close()
    out = []
    for g in STAFF_GROUPS + PLAY_GROUPS + DONATE_GROUPS:
        m = meta.get(g, {})
        out.append({"name": g, "kind": "default" if g == "default" else ("donate" if g in DONATE_GROUPS else "play" if g in PLAY_GROUPS else "staff"),
                    "prefix": m.get("prefix", ""), "prefixes": m.get("prefixes", []), "weight": m.get("weight", 0), "display": m.get("display", {"quanly": "Manager"}.get(g, g)),
                    "members": sorted(members.get(g, []), key=lambda x: x["name"].lower()),
                    "count": total if g == "default" else len(members.get(g, [])),
                    "perms": sorted(perms.get(g, []), key=lambda x: x["node"]), "parents": parents.get(g, []),
                    "exists": g in existing or g == "default", "preset": g in ROLE_PRESETS})
    catalog = (YAML(typ="safe").load(QUYEN_FILE.read_text(encoding="utf-8")) if QUYEN_FILE.exists() else {}) or {}
    groups = catalog.get("groups", [])
    try:
        groups = groups + command_catalog({p[0] for g in groups for p in g.get("perms", [])})
    except Exception as e:  # thiếu commands.json: chỉ có danh mục ghi tay
        print("command_catalog:", e)
    return {"groups": out, "players": total, "catalog": groups, "servers": SERVER_CTX}


SRV_VI = {"lobby": "Sảnh", "banghoi-1": "Bang Hội Chiến", "practice-1": "Practice", "sangtao-1": "Thái Ấp Sáng Tạo"}
USAGE_VI = {"player": "người", "playername": "người", "players": "người", "target": "người", "targets": "người", "amount": "số", "message": "tin",
            "msg": "tin", "reason": "lý do", "world": "thế giới", "name": "tên", "kit": "kit", "kitname": "kit", "item": "đồ", "itemname": "đồ",
            "datediff": "thời gian", "duration": "thời gian", "time": "thời gian", "delay": "giây", "price": "giá", "text": "chữ", "mob": "quái",
            "address": "IP", "jailname": "tên tù", "warp": "warp", "count": "số", "radius": "bán kính", "block": "khối", "pattern": "khối",
            "size": "cỡ", "command": "lệnh"}


def vi_usage(u, name):
    """Cách dùng của plugin (tiếng Anh) -> gọn, dịch tên tham số hay gặp. Bỏ cách dùng không có tham số."""
    u = str(u).strip().replace("<command>", name).replace("<alias>", name)
    if not u or u in ("/", "/" + name) or u.lower().startswith("usage: /") and len(u) < 10:
        return ""
    u = re.sub(r"^Usage:\s*", "", u)
    if not u.startswith("/"):
        u = f"/{name} {u}"
    return re.sub(r"(?<=[<\[|])([a-z]+)(?=[>\]|])", lambda m: USAGE_VI.get(m.group(1).lower(), m.group(1)), u)[:140]


def command_catalog(known):
    """Danh mục quyền TỰ ĐỘNG từ danh sách lệnh các server tự xuất (commands.json): mỗi quyền một dòng, gom theo plugin.
    Bỏ quyền đã có trong quyen.yml (known). Trang Role hiện các nhóm này thu gọn (auto: true)."""
    doc = YAML(typ="safe").load(CAMNANG_FILE.read_text(encoding="utf-8")) or {}
    by_perm = {}
    for srv, d in all_commands(doc).items():
        for c in d["commands"]:
            perm = (c.get("perm") or "").split(";")[0].strip()
            if c.get("manual") or not perm or perm in known or perm.startswith("group."):
                continue
            e = by_perm.setdefault(perm, {"plugin": c["plugin"], "cmds": [], "desc": "", "servers": []})
            if c["name"] not in e["cmds"]:
                e["cmds"].append(c["name"])
            for a in c.get("aliases") or []:
                if a not in e["cmds"] and len(e["cmds"]) < 12:
                    e["cmds"].append(a)
            e["desc"] = e["desc"] or c.get("desc_vi") or ""   # chỉ tiếng Việt (lenh-vi.yml), không lấy mô tả tiếng Anh của plugin
            e["usage"] = e.get("usage") or vi_usage(c.get("usage") or "", c["name"])
            if srv not in e["servers"]:
                e["servers"].append(srv)
    plugins = {}
    for perm, e in sorted(by_perm.items()):
        main, alias = e["cmds"][0], e["cmds"][1:]
        label = f"/{main}" + (f" (/{', /'.join(alias[:4])}{'…' if len(alias) > 4 else ''})" if alias else "") + (f" — {e['desc']}" if e["desc"] else "")
        lines = [f"Làm gì: {e['desc'] or 'chưa có mô tả (xem Cẩm nang admin)'}"]
        if e["usage"]:
            lines.append(f"Cách dùng: {e['usage']}")
        if alias:
            lines.append(f"Gọi tắt: /{', /'.join(alias)}")
        lines.append(f"Có ở: {', '.join(SRV_VI.get(x, x) for x in e['servers'])}")
        info = "\n".join(lines)
        plugins.setdefault(e["plugin"], []).append([perm, label[:160], info])
    order = sorted(plugins, key=lambda p: (p.startswith("Minecraft"), p.lower()))
    return [{"title": f"Lệnh {p} (tự động)", "auto": True, "perms": plugins[p]} for p in order]


# Bộ quyền khuyên dùng cho role (tools/<file>, mount ro vào /repo/tools): nút trên trang Role chạy từng dòng "lp ..." qua RCON ở lobby
ROLE_PRESETS = {"quanly": "quanly-role.cmds", "builder": "builder-role.cmds"}
TOOLS_DIR = Path(os.environ.get("TOOLS_DIR", "/repo/tools"))


def admin_override():
    """Admin luôn thắng: quyền nào role khác chặn (false) thì Admin ghi đích danh true (cùng server). Quyền false đích danh
    thắng '*' của Admin (Admin kế thừa Mod -> Helper -> Dân, hoặc kiêm role khác), còn cùng một quyền thì role nặng hơn thắng.
    Bỏ qua quyền Admin đã tự ghi (true hay false). Trả số quyền vừa thêm."""
    con = lp_db()
    try:
        cur = con.cursor()
        cur.execute("SELECT DISTINCT permission, server FROM luckperms_group_permissions WHERE value = 0 AND name <> 'admin'")
        denied = set(cur.fetchall())
        cur.execute("SELECT permission, server FROM luckperms_group_permissions WHERE name = 'admin'")
        own = set(cur.fetchall())
    finally:
        con.close()
    cmds = [f"lp group admin permission set {n} true" + ("" if srv == "global" else f" server={srv}")
            for n, srv in sorted(denied - own) if PERM_RE.match(n)]
    if cmds:
        rcon("lobby", cmds)
    return len(cmds)


def apply_role_preset(group):
    f = TOOLS_DIR / ROLE_PRESETS[group]
    if not f.exists():
        raise ValueError(f"Không thấy {f.name}: panel cần mount ../tools (git pull rồi docker compose up -d --build panel)")
    lines = [l.strip() for l in f.read_text(encoding="utf-8").splitlines() if l.strip().startswith("lp ")]
    # RCON gửi từng lệnh riêng (không qua shell): chỉ cần mỗi dòng đúng là lệnh LuckPerms quản lý nhóm/track
    if not lines or any(not re.fullmatch(r"lp (group|track|creategroup|createtrack) \S.*", l) for l in lines):
        raise ValueError(f"{f.name} có dòng không phải lệnh lp hợp lệ, không chạy.")
    out = rcon("lobby", lines)
    bad = [o for _, o in out if re.search(r"(?i)error|invalid|không hợp lệ", o) and "already" not in o.lower()]
    time.sleep(1.5)
    admin_override()
    msg = f"Đã chạy {len(lines)} lệnh trong {f.name}." + (f" {len(bad)} lệnh báo lỗi: {bad[0][:120]}" if bad else "")
    data = roles_json()
    # báo luôn prefix thật trong LuckPerms sau khi chạy, để biết lệnh đã ăn hay chưa
    g = next((x for x in data["groups"] if x["name"] == group), {})
    if g.get("prefixes"):
        msg += f" Prefix trong LuckPerms: {' | '.join(p.strip() for p in g['prefixes'])}."
    return {"ok": not bad, "message": msg, "data": data}


USER_SKIP = ("group.", "prefix.", "suffix.", "meta.", "weight.", "displayname.")


def user_perms_json(uid, name):
    """Quyền RIÊNG của một người (luckperms_user_permissions, bỏ role/prefix/meta) + role đang giữ."""
    con = lp_db()
    try:
        cur = con.cursor()
        cur.execute("SELECT permission, value, server, expiry FROM luckperms_user_permissions WHERE uuid = %s", (uid,))
        rows = cur.fetchall()
    finally:
        con.close()
    groups, perms = [], []
    for perm, value, server, expiry in rows:
        if perm.startswith("group."):
            if value:
                groups.append({"name": perm[6:], "server": server, "expiry": int(expiry or 0)})
        elif not perm.startswith(USER_SKIP):
            perms.append({"node": perm, "value": bool(value), "server": server, "expiry": int(expiry or 0)})
    return {"name": name, "groups": sorted(groups, key=lambda g: g["name"]), "perms": sorted(perms, key=lambda p: (p["node"], p["server"]))}


def user_perm_action(data):
    """Quyền riêng từng người (23/10): user_get | user_set (node, server, days 0 = vĩnh viễn, deny = cấm) | user_unset.
    Gửi lp user <UUID> … qua RCON ở sảnh (LuckPerms đồng bộ toàn mạng); tên Bedrock có dấu chấm nên dùng UUID."""
    act, player = data.get("action"), str(data.get("player", "")).strip()
    if not PLAYER_RE.match(player):
        raise ValueError("Tên người chơi không hợp lệ")
    found = lp_player(player)
    if not found:
        raise ValueError(f"Không thấy {player}: người chơi phải vào server ít nhất một lần (Bedrock có dấu chấm đầu, vd .griim7833).")
    uid, player = found
    if act == "user_get":
        return {"ok": True, "message": f"Quyền riêng của {player}.", "user": user_perms_json(uid, player)}
    node, server = str(data.get("node", "")).strip(), data.get("server") or "global"
    if not PERM_RE.match(node) or node.startswith(USER_SKIP):
        raise ValueError("Tên quyền không hợp lệ (vd essentials.workbench). Role cấp ở mục role, không gõ group.<tên> ở đây.")
    if server not in SERVER_CTX:
        raise ValueError("Server không hợp lệ")
    ctx = "" if server == "global" else f" server={server}"
    where = "toàn mạng" if server == "global" else server
    if act == "user_set":
        days = int(data.get("days") or 0)
        if not 0 <= days <= 3650:
            raise ValueError("Số ngày từ 0 (vĩnh viễn) đến 3650")
        val = "false" if data.get("deny") else "true"
        cmd = (f"lp user {uid} permission settemp {node} {val} {days}d accumulate{ctx}" if days
               else f"lp user {uid} permission set {node} {val}{ctx}")
        msg = f"Đã {'CẤM' if data.get('deny') else 'cấp'} {perm_label(node)} cho {player} ({where}{f', {days} ngày' if days else ', vĩnh viễn'})."
    elif act == "user_unset":
        cmd = f"lp user {uid} permission {'unsettemp' if data.get('temp') else 'unset'} {node}{ctx}"
        msg = f"Đã gỡ {perm_label(node)} của {player} ({where})."
    else:
        raise ValueError("Thao tác không hợp lệ")
    rcon("lobby", [cmd])
    time.sleep(1.5)   # LuckPerms ghi DB bất đồng bộ
    return {"ok": True, "message": msg, "user": user_perms_json(uid, player)}


def role_action(data, actor_weight=None):
    """Cấp / gỡ role qua RCON ở lobby (LuckPerms dùng chung DB + Redis nên áp dụng toàn mạng).
    actor_weight: staff không toàn quyền -> không đụng người đang giữ role chức vụ ngang / trên mình."""
    act, player, group = data.get("action"), str(data.get("player", "")).strip(), data.get("group")
    if act in ("user_get", "user_set", "user_unset"):
        return user_perm_action(data)
    if act == "preset":
        if group not in ROLE_PRESETS:
            raise ValueError("Role này không có bộ quyền khuyên dùng")
        return apply_role_preset(group)
    if act in ("perm_set", "perm_unset", "perm_deny"):
        node, server = str(data.get("node", "")).strip(), data.get("server") or "global"
        if group not in STAFF_GROUPS + DONATE_GROUPS + PLAY_GROUPS:
            raise ValueError("Role không hợp lệ")
        if not PERM_RE.match(node) or node.startswith("group."):
            raise ValueError("Tên quyền không hợp lệ (vd essentials.fly)")
        if server not in SERVER_CTX:
            raise ValueError("Server không hợp lệ")
        ctx = "" if server == "global" else f" server={server}"
        if act == "perm_unset":
            cmd = f"lp group {group} permission unset {node}{ctx}"
        else:
            cmd = f"lp group {group} permission set {node} {'false' if act == 'perm_deny' else 'true'}{ctx}"
        rcon("lobby", [cmd])
        time.sleep(1.5)
        if act == "perm_deny" and group != "admin":
            admin_override()
        threading.Thread(target=sync_perm_change, args=(group, node, act), daemon=True).start()
        return {"ok": True, "message": f"Đã gửi: /{cmd}. Người trong role đang online sẽ nhận tin (mất quyền bay/bất tử… thì tắt luôn).",
                "data": roles_json()}
    if not PLAYER_RE.match(player):
        raise ValueError("Tên người chơi không hợp lệ")
    if group not in STAFF_GROUPS + DONATE_GROUPS or group == "default":
        raise ValueError("Role không hợp lệ")
    found = lp_player(player)
    if not found:
        raise ValueError(f"Không thấy {player}: người chơi phải vào server ít nhất một lần (Bedrock có dấu chấm đầu, vd .griim7833).")
    uid, player = found
    if group in DONATE_GROUPS and act == "add":
        months = int(data.get("months") or 1)
        if not 1 <= months <= 24:
            raise ValueError("Số tháng từ 1 đến 24")
        cmds = [f"rank give {player} {group} {months}"]
    elif group in DONATE_GROUPS and act == "remove":
        cmds = [f"rank remove {player}"]
    elif act in ("add", "remove"):
        # LuckPerms từ chối tên có dấu chấm (người chơi Bedrock: .ten) trong "lp user <tên>" -> gửi theo UUID trong DB
        cmds = [f"lp user {uid} parent {act} {group}"]
        if actor_weight is not None and group in STAFF_CHAIN:
            held = taikhoan.load_perms(player) or {}
            if any(group_weights().get(g, 0) >= actor_weight for g in held.get("groups", []) if g in STAFF_CHAIN):
                raise ValueError(f"{player} đang giữ role chức vụ ngang hoặc trên bạn")
        if act == "add" and group in STAFF_CHAIN:
            # Chỉ giữ một role chức vụ: Manager chặn hẳn (false) duckymomo.admin, luckperms.*… nên Admin kiêm Manager mất quyền admin
            cmds += [f"lp user {uid} parent remove {g}" for g in STAFF_CHAIN if g != group]
    else:
        raise ValueError("Thao tác không hợp lệ")
    rcon("lobby", cmds)
    time.sleep(2)  # lệnh chạy bất đồng bộ (tải người chơi, ghi DB)
    threading.Thread(target=tell_role_change, args=(player, group, act, data.get("months")), daemon=True).start()
    shown = {"admin": "Admin", "quanly": "Manager", "mod": "Mod", "helper": "Helper", "builder": "Builder", "congtuoc": "Công Tước", "hautuoc": "Hầu Tước", "batuoc": "Bá Tước", "tutuoc": "Tử Tước", "namtuoc": "Nam Tước"}.get(group, group)
    if group in DONATE_GROUPS:
        msg = f"Đã cấp {shown} cho {player} ({data.get('months') or 1} tháng)." if act == "add" else f"Đã gỡ rank của {player}."
    else:
        msg = f"Đã thêm {player} vào {shown}." if act == "add" else f"Đã gỡ {player} khỏi {shown}."
    return {"ok": True, "message": msg + " Người chơi nhận tin trong game nếu đang online.", "data": roles_json()}


# Quyền kiểu "trạng thái": bỏ quyền chỉ chặn GÕ lệnh, trạng thái đang bật vẫn còn (vẫn bay…) nên phải tắt bằng lệnh.
# {p} = tên người chơi, {mode} = chế độ chơi mặc định của server đó.
STATE_REVOKE = {
    "essentials.fly": ["fly {p} off"],
    "essentials.god": ["god {p} off"],
    "essentials.vanish": ["vanish {p} off"],
    "essentials.speed": ["speed walk 1 {p}", "speed fly 1 {p}"],
    "essentials.gamemode": ["gamemode {mode} {p}"],
}
DEFAULT_MODE = {"lobby": "adventure", "banghoi-1": "survival", "practice-1": "adventure", "sangtao-1": "creative"}


def perm_label(node):
    try:
        cat = YAML(typ="safe").load(QUYEN_FILE.read_text(encoding="utf-8")) or {}
        for g in cat.get("groups", []):
            for n, label in g.get("perms", []):
                if n == node:
                    return f"{str(label).strip()} ({node})"
    except Exception:
        pass
    return node


def revoke_states(perms, srv, only=None):
    """Lệnh tắt các trạng thái mà người chơi không còn quyền (only: chỉ xét các quyền khớp tiền tố này)."""
    cmds = []
    for node, lst in STATE_REVOKE.items():
        if only and not (node == only or node.startswith(only.rstrip("*").rstrip(".") + ".") or only in ("*", node)):
            continue
        if node == "essentials.gamemode":
            # còn được đổi chế độ chơi thì thôi; mất hẳn thì đưa về chế độ mặc định của server
            if taikhoan.has(perms, node, srv) or any(taikhoan.has(perms, f"{node}.{m}", srv) for m in ("creative", "spectator")):
                continue
        elif taikhoan.has(perms, node, srv):
            continue
        cmds += [c.format(p=perms["name"], mode=DEFAULT_MODE.get(srv, "survival")) for c in lst]
    return cmds


def sync_perm_change(group, node, act):
    """Sau khi bật/tắt quyền của role: báo người trong role (kể cả role kế thừa) đang online; ai vừa mất quyền trạng thái thì tắt luôn."""
    shown = group.capitalize()
    label = perm_label(node)
    base = node.split(".gamemode.")[0] + ".gamemode" if ".gamemode." in node else node
    for name, srv in online_players(fresh=True).items():
        try:
            perms = taikhoan.load_perms(name)
            if not perms or group not in perms["groups"]:
                continue
            now = taikhoan.has(perms, node, srv)
            if act == "perm_set" and now:
                msg, cmds = f"Role {shown} vừa được thêm quyền: {label}.", []
            elif act != "perm_set" and not now:
                msg, cmds = f"Role {shown} vừa bị bỏ quyền: {label}.", revoke_states(perms, srv, base)
            else:
                continue  # vẫn có / vẫn không có quyền này nhờ role khác: không đổi gì với người này
            raw = ["", {"text": "[Role] ", "color": "gold", "bold": True}, {"text": msg, "color": "yellow"}]
            rcon(srv, cmds + [f"tellraw {perms['name']} " + json.dumps(raw, ensure_ascii=False)])
        except Exception:
            pass


def tell_role_change(player, group, act, months=None):
    """Báo vào game cho người vừa được cấp/gỡ role (nếu đang online). LuckPerms tự làm mới danh sách lệnh của client."""
    shown = {"admin": "Admin", "quanly": "Manager", "mod": "Mod", "helper": "Helper", "builder": "Builder"}.get(group, group)
    if group in DONATE_GROUPS:
        shown = {"namtuoc": "Nam Tước", "tutuoc": "Tử Tước", "batuoc": "Bá Tước", "hautuoc": "Hầu Tước", "congtuoc": "Công Tước"}[group]
    if act == "add":
        text = f"Bạn vừa được cấp {shown}" + (f" ({months} tháng)" if group in DONATE_GROUPS and months else "") + \
            ". Xem quyền và lệnh mới: gõ /web rồi mở trang tài khoản."
        if group not in DONATE_GROUPS:
            text += " Lệnh Essentials (/fly, /kick…) dùng ở Bang Hội Chiến và Practice."
    else:
        text = f"Role {shown} của bạn đã được gỡ."
    srv = online_players(fresh=True).get(player.lower())
    if srv:
        raw = ["", {"text": "[Role] ", "color": "gold", "bold": True}, {"text": text, "color": "yellow"}]
        cmds = []
        if act == "remove":
            try:
                perms = taikhoan.load_perms(player)
                cmds = revoke_states(perms, srv) if perms else []  # gỡ mod: tắt bay, tàng hình… nếu không còn quyền
            except Exception:
                pass
        try:
            rcon(srv, cmds + [f"tellraw {player} " + json.dumps(raw, ensure_ascii=False)])
        except Exception:
            pass


def modes_json(cats=None):
    """cats: chỉ trả các nhóm thuộc những loại này (staff đăng nhập bằng tài khoản game); None = tất cả."""
    out = []
    for mode_id, m in load_modes().items():
        schema = m["schema"]
        if cats is not None:
            schema = dict(schema, groups=[g for g in schema.get("groups", []) if (g.get("category") or "Khác") in cats])
            if not schema["groups"]:
                continue
        out.append({"id": mode_id, "schema": schema,
                    "values": {k: plain(v) for k, v in m["values"].items()}})
    return out


_cat_cache = {}


def all_categories():
    """Các loại trên trang Cài đặt. Đọc schema mất gần 1 giây nên giữ lại tới khi file schema đổi (mỗi yêu cầu của staff đều cần)."""
    stamp = tuple((str(f), f.stat().st_mtime) for f in sorted(MODES_DIR.glob("*/settings.schema.yml")))
    if _cat_cache.get("stamp") != stamp:
        cats = set()
        for f in sorted(MODES_DIR.glob("*/settings.schema.yml")):
            schema = YAML(typ="safe").load(f.read_text(encoding="utf-8")) or {}
            cats |= {g.get("category") or "Khác" for g in schema.get("groups", [])}
        _cat_cache.update(stamp=stamp, cats=sorted(cats))
    return _cat_cache["cats"]


def allowed_keys(mode_id, cats):
    m = load_modes().get(mode_id)
    if not m:
        return set()
    return {s["key"] for g, s in all_settings(m["schema"]) if (g.get("category") or "Khác") in cats}


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


_NICKS = {"t": 0.0, "map": {}}


def nick_map():
    """Biệt danh Essentials (/nick, bỏ mã màu, chữ thường) -> tên thật, đọc userdata Bang Hội Chiến + Practice, làm mới mỗi 60 giây.
    Chat ghi theo tên hiển thị nên ai đổi biệt danh thì phải đổi lại mới lọc được theo tên thật."""
    if time.time() - _NICKS["t"] < 60:
        return _NICKS["map"]
    out = {}
    for srv in ("banghoi-1", "practice-1"):
        for f in (DATA_DIR / srv / "plugins" / "Essentials" / "userdata").glob("*.yml"):
            try:
                txt = f.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            nick, name = re.search(r"^nickname: *'?(.+?)'?$", txt, re.M), re.search(r"^last-account-name: *(\S+)", txt, re.M)
            if nick and name:
                n = re.sub(r"[§&][0-9a-fk-orx]", "", nick.group(1), flags=re.I).lstrip("~").strip().lower()
                if n and n != name.group(1).lower():
                    out[n] = name.group(1)
    _NICKS.update(t=time.time(), map=out)
    return out


def read_activity(params):
    """Sự kiện mới nhất trước. Lọc: types (chat,join,command), mode, player, q (tìm trong nội dung),
    from / to (mốc UTC dạng 2026-10-10T17:00:00Z, trình duyệt tự đổi ngày giờ Việt Nam sang), limit."""
    types = set(filter(None, params.get("types", "").split(","))) or {"chat", "join", "command", "admin"}
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
        if ADMIN_FILE.exists():
            raw += ADMIN_FILE.read_text(encoding="utf-8", errors="replace").splitlines()[-2000:]
        seen = set()
        nicks = nick_map()
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
            real = nicks.get(e.get("player", "").lower()) if e.get("type") == "chat" else None
            if real:   # chat ghi theo biệt danh -> gắn tên thật để lọc theo người chơi
                e["text"] = e.get("text", "").replace(f"<{e['player']}>", f"<{e['player']} ({real})>", 1)
                e["player"] = real
            events.append(e)
    events.sort(key=lambda e: e.get("ts", ""))
    mark_switches(events)
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
    return {"modes": modes, "events": out, "has_file": ACTIVITY_FILE.exists(), "thiet_bi": devices_for({e.get("player", "") for e in out})}


def mark_switches(events):
    """Chuyển server (sảnh -> Bang Hội) làm server cũ ghi "X mất kết nối: Disconnected" như thoát thật.
    Proxy ghi "X → server mới" trong vòng 3 giây thì đổi chữ thành chuyển server."""
    import datetime

    def secs(ts):
        try:
            return datetime.datetime.fromisoformat(ts.replace("Z", "+00:00")).timestamp()
        except ValueError:
            return None
    moves = {}
    for e in events:
        if e.get("server") == "proxy" and e.get("type") == "join" and e.get("target"):
            t = secs(e.get("ts", ""))
            if t is not None:
                moves.setdefault(e.get("player", "").lower(), []).append((t, e["target"]))
    for e in events:
        if e.get("type") != "join" or e.get("server") == "proxy" or not e.get("text", "").endswith("mất kết nối: Disconnected"):
            continue
        t = secs(e.get("ts", ""))
        to = next((dst for mt, dst in moves.get(e.get("player", "").lower(), [])
                   if t is not None and abs(mt - t) <= 3 and dst != e.get("server")), None)
        if to:
            e["text"] = f"{e['player']} rời {e['server']} (chuyển sang {to})"


DEVICE_FILE = DATA_DIR / "proxy" / "plugins" / "duckymomoskin" / "thietbi.json"
_bot_names = {"at": 0.0, "names": set()}


def devices_for(names):
    """Thiết bị của người chơi (plugin proxy ghi thietbi.json): {tên: "PC" | "BE·Android" | "Bot"}; bot của trang Bot ghi "Bot"."""
    import time as _t
    if _t.time() - _bot_names["at"] > 60:
        _bot_names["at"] = _t.time()
        try:
            _bot_names["names"] = {b["name"].lower() for b in bot_json().get("bots", [])}
        except Exception:
            pass
    try:
        data = json.loads(DEVICE_FILE.read_text(encoding="utf-8")) if DEVICE_FILE.exists() else {}
    except (OSError, ValueError):
        data = {}
    os_vi = {"GOOGLE": "Android", "IOS": "iOS", "OSX": "macOS", "AMAZON": "Fire", "GEARVR": "VR", "HOLOLENS": "VR", "UWP": "Windows",
             "WIN32": "Windows", "DEDICATED": "Server", "TVOS": "Apple TV", "PS4": "PlayStation", "NX": "Switch", "XBOX": "Xbox",
             "WINDOWS_PHONE": "Windows Phone", "LINUX": "Linux"}
    out = {}
    for n in names:
        if not n:
            continue
        low = n.lower()
        if low in _bot_names["names"]:
            out[n] = {"nhan": "Bot", "loai": "bot", "chi_tiet": "Bot của trang quản trị › Bot"}
            continue
        d = data.get(low)
        if not d:
            continue
        if d.get("loai") == "be":
            os_ = os_vi.get(str(d.get("os", "")).upper(), str(d.get("os", "")).title())
            out[n] = {"nhan": "BE" + ("·" + os_ if os_ else ""), "loai": "be", "chi_tiet": f"Bedrock {os_} · bản {d.get('ban', '?')}".strip()}
        else:
            cl = str(d.get("client") or "")
            out[n] = {"nhan": "PC", "loai": "pc", "chi_tiet": f"Máy tính (Java) · bản {d.get('ban', '?')}" + (f" · client {cl}" if cl else "")}
    return out



# ---------------------------------------------------------------- gửi tin vào game (trang Hoạt động)

# Server Paper nhận tin (proxy Velocity không có RCON). Server đang tắt thì bỏ qua, báo lại.
SAY_SERVERS = {"lobby": "Sảnh", "banghoi-1": "Bang Hội Chiến", "practice-1": "Practice", "sangtao-1": "Thái Ấp Sáng Tạo", "pokemon-1": "Pokémon"}
SAY_KINDS = {"chat": "Chat admin", "broadcast": "Thông báo", "title": "Thông báo giữa màn hình"}


def say(payload, full=True, who=None):
    """Gửi tin vào game bằng tellraw/title qua RCON, rồi ghi vào dòng hoạt động (loại "admin").
    Nội dung bắt đầu bằng "/" = chạy lệnh console trên một server (chỉ tài khoản toàn quyền)."""
    text = re.sub(r"[\x00-\x1f\x7f§]", "", str(payload.get("text", ""))).strip()
    if text.startswith("/"):
        return say_command(text, payload.get("target", "all"), full, who)
    name = re.sub(r"[\x00-\x1f\x7f§]", "", str(payload.get("name", "") or "Admin")).strip()[:24] or "Admin"
    kind = payload.get("kind", "chat")
    target = payload.get("target", "all")
    if not text:
        raise ValueError("Chưa nhập nội dung")
    if len(text) > 200:
        raise ValueError("Tối đa 200 ký tự")
    if kind not in SAY_KINDS:
        raise ValueError("Kiểu gửi không hợp lệ")
    servers = list(SAY_SERVERS) if target == "all" else [target]
    if any(s not in SAY_SERVERS for s in servers):
        raise ValueError("Server không hợp lệ")
    if kind == "chat":
        raw = ["", {"text": "[Admin] ", "color": "red", "bold": True}, {"text": name + ": ", "color": "gold"}, {"text": text, "color": "white"}]
        cmds = ["tellraw @a " + json.dumps(raw, ensure_ascii=False)]
    else:
        raw = ["", {"text": "✦ THÔNG BÁO ✦ ", "color": "gold", "bold": True}, {"text": text, "color": "yellow"}]
        cmds = ["tellraw @a " + json.dumps(raw, ensure_ascii=False)]
        if kind == "title":
            cmds += ["title @a times 10 100 20",
                     "title @a subtitle " + json.dumps({"text": text, "color": "yellow"}, ensure_ascii=False),
                     "title @a title " + json.dumps({"text": "Thông báo", "color": "gold", "bold": True}, ensure_ascii=False)]
    sent, failed = [], []
    for srv in servers:
        try:
            rcon(srv, cmds)
            sent.append(SAY_SERVERS[srv])
        except Exception:
            failed.append(SAY_SERVERS[srv])
    if not sent:
        raise ValueError("Không gửi được: server đang tắt (" + ", ".join(failed) + ")")
    from datetime import datetime, timezone
    event = {"ts": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "server": "web" if target == "all" else target,
             "type": "admin", "player": name, "text": f"{SAY_KINDS[kind]} ({'cả mạng' if target == 'all' else SAY_SERVERS[target]}) · {name}: {text}",
             "target": "" if target == "all" else target}
    try:
        ADMIN_FILE.parent.mkdir(parents=True, exist_ok=True)
        with ADMIN_FILE.open("a", encoding="utf-8") as f:
            f.write(json.dumps(event, ensure_ascii=False) + "\n")
    except OSError as e:
        print(f"Không ghi được {ADMIN_FILE}: {e}", flush=True)
    msg = "Đã gửi tới " + ", ".join(sent)
    if failed:
        msg += " (bỏ qua server đang tắt: " + ", ".join(failed) + ")"
    return {"ok": True, "message": msg}

def say_command(text, target, full, who):
    """Lệnh console gõ từ ô gửi tin: chạy trên đúng một server, trả lại phản hồi của server."""
    if not full:
        raise ValueError("Chỉ tài khoản toàn quyền được chạy lệnh console")
    if target not in SAY_SERVERS:
        raise ValueError("Chọn một server cụ thể để chạy lệnh (không chạy cho cả mạng)")
    cmd = text[1:].strip()
    if not cmd:
        raise ValueError("Chưa nhập lệnh")
    try:
        out = COLOR_RE.sub("", rcon(target, [cmd])[0][1]).strip()
    except Exception:
        raise ValueError(f"Không chạy được: {SAY_SERVERS[target]} đang tắt")
    web_log({"viec": "/api/say", "nguoi": who or PANEL_USER, "hanh_dong": "/" + cmd, "doi_tuong": target, "ok": True})
    from datetime import datetime, timezone
    event = {"ts": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "server": target, "type": "admin",
             "player": who or "Admin", "text": f"Lệnh console ({SAY_SERVERS[target]}) · {who or 'Admin'}: /{cmd}", "target": target}
    try:
        ADMIN_FILE.parent.mkdir(parents=True, exist_ok=True)
        with ADMIN_FILE.open("a", encoding="utf-8") as f:
            f.write(json.dumps(event, ensure_ascii=False) + "\n")
    except OSError as e:
        print(f"Không ghi được {ADMIN_FILE}: {e}", flush=True)
    return {"ok": True, "message": f"/{cmd} ({SAY_SERVERS[target]}): " + (out[:600] or "đã chạy, server không trả lời gì")}


# ---------------------------------------------------------------- Bot (dịch vụ botfarm, chỉ trong mạng Docker)

BOTFARM_URL = os.environ.get("BOTFARM_URL", "http://botfarm:8090")
BOT_ACTIONS = {"join": "vào", "leave": "thoát", "server": "đổi server", "chat": "nói", "cmd": "gõ lệnh", "move": "đi tới", "home": "về chỗ", "sethome": "đặt chỗ", "random": "ra khu ngẫu nhiên",
               "create": "tạo bot", "remove": "xoá bot"}


def known_player_names():
    """Tên mọi tài khoản từng vào một server con (usercache.json của Paper), chữ thường: tạo bot không được trùng người thật."""
    out = set()
    for f in DATA_DIR.glob("*/usercache.json"):
        try:
            out |= {str(x.get("name", "")).lower() for x in json.loads(f.read_text(encoding="utf-8"))}
        except Exception:
            pass
    return out


def bot_json():
    """Danh sách bot + sự kiện bot nhận (tin nhắn riêng, TPA, bị gọi tên) từ botfarm."""
    try:
        with urllib.request.urlopen(BOTFARM_URL + "/bots", timeout=5) as r:
            return json.loads(r.read().decode("utf-8")) | {"ok": True}
    except Exception as e:
        return {"ok": False, "message": f"Dịch vụ bot không trả lời ({e}). Kiểm tra: docker compose ps botfarm", "bots": [], "events": [], "servers": {}}


def bot_action(body, who):
    act = str(body.get("action", ""))
    if act not in BOT_ACTIONS:
        raise ValueError("Thao tác không hợp lệ")
    if act == "create":
        name = str(body.get("bot", "")).strip()
        if not re.fullmatch(r"[A-Za-z0-9_]{3,16}", name):
            raise ValueError("Tên bot 3–16 ký tự, chỉ chữ không dấu, số, gạch dưới")
        if name.lower() in known_player_names():
            raise ValueError(f"Tên {name} đã có tài khoản trong game (người chơi hoặc bot cũ), chọn tên khác")
    payload = {k: body.get(k) for k in ("bot", "action", "server", "text", "x", "z") if body.get(k) is not None}
    req = urllib.request.Request(BOTFARM_URL + "/act", data=json.dumps(payload).encode("utf-8"), headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            res = json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        res = json.loads(e.read().decode("utf-8") or "{}") or {"ok": False, "message": str(e)}
    except Exception as e:
        raise ValueError(f"Dịch vụ bot không trả lời ({e})")
    web_log({"viec": "/api/bot", "nguoi": who or PANEL_USER, "hanh_dong": f"{payload.get('bot')} {BOT_ACTIONS[act]} {payload.get('text') or payload.get('server') or ''}".strip(),
             "doi_tuong": payload.get("bot", ""), "ok": bool(res.get("ok"))})
    return res


# ---------------------------------------------------------------- Thư viện đồ (DuckyMoMoCore ItemLibrary, banghoi-1)

LIB_SERVER = "banghoi-1"
LIB_DIR = DATA_DIR / LIB_SERVER / "plugins" / "DuckyMoMoCore"
LIB_FILE = LIB_DIR / "thuvien-do.yml"
LIB_META = LIB_DIR / "thuvien-meta.json"
LIB_ID_RE = re.compile(r"^[a-z0-9_]{1,32}$")
ENCH_KEY_RE = re.compile(r"^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$")
MATERIAL_RE = re.compile(r"^[A-Z0-9_]{1,64}$")


def lib_load():
    y = yaml_rt()
    doc = y.load(LIB_FILE.read_text(encoding="utf-8")) if LIB_FILE.exists() else None
    return y, (doc if doc is not None else {})


# Đầu mục (nhom) và công dụng (cong-dung) của món: khớp ItemLibrary.NHOM / STAMPERS (DuckyMoMoCore) và thuvien.html
LIB_NHOM = {"vu-khi": "Vũ khí", "cong-cu": "Công cụ", "giap": "Giáp & khiên", "do-an": "Món ăn", "bua": "Bùa & tiêu hao",
            "nguyen-lieu": "Nguyên liệu", "tien": "Tiền vật phẩm", "manh": "Mảnh sự kiện", "chia-khoa": "Vé gacha", "khac": "Khác"}
LIB_USES = {"": "Đồ thường", "ky-nang": "Kỹ năng đặc biệt (Xưởng)", "mon-an": "Món ăn (hiệu ứng khi ăn)", "bua-bat-pet": "Bùa bắt pet",
            "tinh-luyen": "Nguyên liệu Tinh Luyện", "manh": "Mảnh sự kiện",
            "chia-khoa": "Vé gacha"}
# Thư viện đồ › Chỉ số gốc: mã -> (tên, (nhỏ nhất, lớn nhất)); khớp ItemLibrary.ATTRS và ATTRS trong thuvien.html
LIB_DMM_ENCH = {"luot": "Lướt", "hoi_mau": "Hồi máu", "cau_lua": "Cầu lửa", "bay": "Bay lên", "dich_chuyen": "Dịch chuyển",
                "dam_dat": "Dậm đất", "cuong_no": "Huyết chiến", "phong_bay": "Phóng bay",
                "khong_lo": "Khổng lồ", "thu_nho": "Thu nhỏ", "cam_lang": "Câm lặng", "troi": "Trói", "moc_keo": "Móc kéo",
                "tua_thoi_gian": "Tua thời gian", "khien_phep": "Khiên phép", "hoi_mau_dong_doi": "Hồi máu đồng đội",
                "chan_dan": "Chắn đạn", "soi_sang": "Soi sáng",
                "hoang_so": "Hoảng sợ", "hoa_cuu": "Hoá cừu", "hoa_da": "Ánh mắt hoá đá", "khieu_khich": "Khiêu khích", "danh_dau": "Đánh dấu tử thần", "ho_den": "Hố đen", "day_vang": "Đẩy văng", "ho_gia": "Hộ giá", "dong_bang": "Đóng băng thời gian", "bat_tu": "Bất tử", "la_chan": "Lá chắn phép", "giai_khong_che": "Giải khống chế", "loc_xoay": "Lốc xoáy", "tia_sang": "Tia sáng", "phao_kich": "Pháo kích", "set_nay": "Sét nảy", "lien_hoan": "Liên hoàn đao", "rut_bai": "Rút bài", "tang_toc": "Tăng tốc"}   # khớp SkillEnchants.NAMES
LIB_ATTRS = {"attack_damage": ("Sát thương", (0, 200)), "attack_speed": ("Tốc đánh", (0.1, 20)), "armor": ("Giáp", (0, 30)),
             "armor_toughness": ("Độ cứng giáp", (0, 20)), "knockback_resistance": ("Kháng đẩy lùi", (0, 1)),
             "max_health": ("Máu tối đa", (-10, 40)), "movement_speed": ("Tốc chạy %", (-50, 100)),
             # chỉnh theo % (22/10, âm = giảm): máu dùng chỉ số % của game, 3 cái sau server tự tính (ItemPercent.java)
             "pct_mau": ("Máu %", (-90, 300)), "pct_sat_thuong": ("Sát thương %", (-90, 300)),
             "pct_toc_danh": ("Tốc đánh %", (-90, 300)), "pct_day_lui": ("Kháng đẩy lùi %", (-300, 100))}
# Sách cấm (BanBook.java): hoạt động -> (tên, giải thích cho admin); cách xử lý từng món. Khớp BanBook.ACTIVITIES / Mode
LIB_ACTIVITIES = {"cong-thanh": ("Công thành", "Trong vùng vây lúc đang có phiên đánh (SiegeWar)."),
                  "dau-truong": ("Đấu trường", "Thế giới Đấu Trường /dautruong (vào hàng chờ, thách đấu)."),
                  "dungeon": ("Hầm ngục", "Dungeon EliteMobs, kể cả vào qua Cổng Hoang (trừ sảnh Hội Phiêu Lưu).")}
LIB_BAN_MODES = {"vo-hieu": "Không dùng được", "chan-vao": "Không cho mang vào"}   # "tat-hieu-ung" (tên cũ) đọc thành vo-hieu
LIB_OLD_SIEGE_ENCHANTS = ["bay", "dich_chuyen", "phong_bay", "thu_nho"]   # luật cũ trước Sách cấm (SkillEnchants)
LIB_MODEL_RE = re.compile(r"^[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,64}$")
BOSS_DROPS_FILE = LIB_DIR / "boss-roi.yml"
BOSS_DROP_TO = {"top1": "Người đánh nhiều nhất", "top3": "3 người đánh nhiều nhất (mỗi người tung riêng)",
                "tat-ca": "Mọi người đã đánh (mỗi người tung riêng)", "ngau-nhien": "Một người đã đánh (ngẫu nhiên)"}


def lib_default_book(doc):
    """Thư viện chưa có mục sach-cam: trang Công thành theo luật cũ (ky-nang.pvp + phù phép di chuyển). Khớp BanBook.defaults."""
    do = {str(k): "vo-hieu" for k, v in (doc.get("items") or {}).items()
          if (v or {}).get("cong-dung") == "ky-nang" and ((v or {}).get("ky-nang") or {}).get("pvp")}
    return {"cong-thanh": {"mo-ta": "Trong vùng vây lúc đang có phiên đánh", "do": do, "phu-phep": list(LIB_OLD_SIEGE_ENCHANTS)}}


def lib_book_json(doc):
    book = doc.get("sach-cam")
    if book is None:
        book = lib_default_book(doc)
    out = {}
    for act in LIB_ACTIVITIES:
        pg = (book or {}).get(act) or {}
        out[act] = {"mo_ta": str(pg.get("mo-ta") or ""), "do": {str(k): ("vo-hieu" if str(v) == "tat-hieu-ung" else str(v)) for k, v in (pg.get("do") or {}).items()
                           if str(v) in LIB_BAN_MODES or str(v) == "tat-hieu-ung"},
                    "phu_phep": [str(x) for x in (pg.get("phu-phep") or []) if str(x) in LIB_DMM_ENCH]}
    return out


def lib_book_save(doc, raw):
    """Trang Sách cấm gửi lên -> mục sach-cam (kiểm mã món, cách xử lý, phù phép riêng)."""
    items = doc.get("items") or {}
    book = {}
    for act, pg in (raw or {}).items():
        if act not in LIB_ACTIVITIES:
            raise ValueError(f"Hoạt động lạ: {act}")
        pg = pg or {}
        do = {}
        for iid, mode in (pg.get("do") or {}).items():
            iid, mode = str(iid), str(mode)
            if iid not in items:
                raise ValueError(f"{LIB_ACTIVITIES[act][0]}: không có món \"{iid}\" trong thư viện")
            if mode not in LIB_BAN_MODES:
                raise ValueError(f"{LIB_ACTIVITIES[act][0]}: cách xử lý lạ \"{mode}\"")
            do[iid] = mode
        ench = []
        for k in pg.get("phu_phep") or []:
            if str(k) not in LIB_DMM_ENCH:
                raise ValueError(f"Không có phù phép riêng: {k}")
            if str(k) not in ench:
                ench.append(str(k))
        desc = " ".join(str(pg.get("mo_ta") or "").replace("&", "").split())[:120]
        entry = {}
        if desc:
            entry["mo-ta"] = DoubleQuotedScalarString(desc)
        entry["do"] = do
        entry["phu-phep"] = ench
        book[act] = entry
    return book


def lib_items():
    """{mã: mục} của thư viện đồ (đọc nhanh, không giữ comment)."""
    try:
        return (YAML(typ="safe").load(LIB_FILE.read_text(encoding="utf-8")) or {}).get("items") or {}
    except (OSError, ValueError):
        return {}


def lib_need(label, iid, use=None):
    """Mã phải có trong thư viện đồ (và đúng công dụng nếu use). Trả mã."""
    it = lib_items().get(iid)
    if not LIB_ID_RE.match(iid) or it is None:
        raise ValueError(f"{label}: thư viện đồ không có \"{iid}\" (tạo ở trang Thư viện đồ trước)")
    if use and (it or {}).get("cong-dung", "") != use:
        raise ValueError(f"{label}: \"{iid}\" không phải món công dụng {LIB_USES.get(use, use)} (đặt công dụng ở trang Thư viện đồ)")
    return iid


def lib_usage():
    """{mã: [chỗ dùng]} để trang Thư viện đồ biết món nào đang được dùng (chặn xoá nhầm)."""
    out = {}
    add = lambda iid, where: out.setdefault(str(iid), []).append(where) if where not in out.get(str(iid), []) else None
    try:
        core = YAML(typ="safe").load((LIB_DIR / "config.yml").read_text(encoding="utf-8")) or {}
    except (OSError, ValueError):
        core = {}
    for k in ((core.get("workshop") or {}).get("items") or {}):
        add(k, "Xưởng chế tạo")
    for k in ((core.get("dishes") or {}).get("list") or {}):
        add(k, "Bếp /nauan")
    for k in ((core.get("tinh-luyen") or {}).get("items") or {}):
        add(k, "Tinh Luyện")
    for k in ((core.get("event-fragments") or {}).get("types") or {}):
        add(k, "Mảnh sự kiện")
    add((core.get("pet-capture") or {}).get("charm-item") or "bua_bat_pet", "Bùa bắt pet")
    hom = core.get("crates") or {}
    for c in hom.get("list") or []:
        if isinstance(c, dict) and c.get("key"):
            add(c["key"], f"Vé gacha {c.get('id', '')}")
    if (hom.get("online-key") or {}).get("item"):
        add(hom["online-key"]["item"], "Vé gacha chơi online")
    add((core.get("tinh-luyen") or {}).get("xu-quai", {}).get("item") or "xuquai", "Xu Quái rơi từ quái")
    add(((core.get("tinh-luyen") or {}).get("loi-quai") or {}).get("item") or "loiquai", "Lõi Quái rơi từ quái tinh nhuệ")
    tr = core.get("tho-ren") or {}   # Thợ Rèn (23/10): món set, mảnh ấn, nguyên liệu bảng giá, quầy đổi, rơi dungeon / câu cá
    for sid, st in (tr.get("sets") or {}).items():
        for pc in ((st or {}).get("pieces") or {}).values():
            for i in (pc or {}).get("ids") or []:
                add(i, f"Thợ Rèn: set {(st or {}).get('name', sid)}")
            if (pc or {}).get("an"):
                add(pc["an"], f"Thợ Rèn: mảnh ấn set {(st or {}).get('name', sid)}")
        for c in (st or {}).get("cost") or []:
            for k in (c or {}):
                if k not in ("xu", "an") and not str(k).isupper():
                    add(k, f"Thợ Rèn: nguyên liệu set {(st or {}).get('name', sid)}")
    for q in (tr.get("quay") or {}).values():
        for r in (q or {}).get("recipes") or []:
            add((r or {}).get("out"), "Thợ Rèn: quầy đổi")
            for k in ((r or {}).get("cost") or {}):
                if k != "xu" and not str(k).isupper():
                    add(k, "Thợ Rèn: nguyên liệu quầy đổi")
    for d in ((tr.get("dungeon-drops") or {}).get("dungeons") or {}).values():
        for i in (d or {}).get("items") or []:
            add(i, "Thợ Rèn: rơi trong dungeon")
    if (tr.get("fish-drop") or {}).get("item"):
        add(tr["fish-drop"]["item"], "Thợ Rèn: rơi khi câu cá")

    def walk(node, top):
        if isinstance(node, dict):
            if node.get("library"):
                add(node["library"], "Cửa hàng Kinh Đô")
            for v in node.values():
                walk(v, top)
        elif isinstance(node, list):
            for v in node:
                walk(v, top)
        elif isinstance(node, str):
            m = re.match(r"^do ([a-z0-9_]{1,32})\b", node)
            if m:
                add(m.group(1), f"Phần thưởng ({top})")
    for top, v in core.items():
        walk(v, top)
    try:
        drops = YAML(typ="safe").load(BOSS_DROPS_FILE.read_text(encoding="utf-8")) or {}
    except (OSError, ValueError):
        drops = {}
    for f, lst in drops.items():
        for d in lst or []:
            if isinstance(d, dict) and d.get("do"):
                add(d["do"], f"Boss {f}")
    return out


def lib_meta():
    try:
        return json.loads(LIB_META.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def ce_vi():
    """Tên + mô tả tiếng Việt phù phép CrazyEnchantments: {Tên: [tên Việt, mô tả]} (modes/_network/ce-vi.yml)."""
    try:
        d = YAML(typ="safe").load((MODES_DIR / "_network" / "ce-vi.yml").read_text(encoding="utf-8")) or {}
        return {str(k).lower(): v for k, v in d.items() if isinstance(v, list) and len(v) >= 2}
    except (OSError, ValueError):
        return {}


def ce_chances():
    """Tỉ lệ kích hoạt từng cấp phù phép CrazyEnchantments (22/10, người dùng: "né đòn không biết né được bao nhiêu %"):
    {tên thường: [% cấp 1, % cấp 2, …]} = Chance-System Base + Increase × (cấp − 1), tối đa 100. Phù phép không có Chance-System
    (luôn có tác dụng) không có trong kết quả. Đọc thẳng Enchantments.yml của Bang Hội Chiến (Cài đặt › Phù phép sửa được)."""
    try:
        d = YAML(typ="safe").load((DATA_DIR / LIB_SERVER / "plugins" / "CrazyEnchantments" / "Enchantments.yml").read_text(encoding="utf-8")) or {}
    except (OSError, ValueError):
        return {}
    out = {}
    for name, e in (d.get("Enchantments") or {}).items():
        cs = e.get("Chance-System") if isinstance(e, dict) else None
        if not isinstance(cs, dict):
            continue
        base, inc, mx = float(cs.get("Base", 0) or 0), float(cs.get("Increase", 0) or 0), int(e.get("MaxPower", 1) or 1)
        out[str(name).lower()] = [round(min(100.0, base + inc * i), 2) for i in range(max(1, mx))]
    return out


def thuvien_json():
    _, doc = lib_load()
    items = []
    for iid, s in (doc.get("items") or {}).items():
        s = s or {}
        it = {"id": str(iid), "nguon": s.get("nguon", "web"), "ten_goi": s.get("ten-goi", "")}
        src = (s.get("preview") or {}) if it["nguon"] == "game" else s
        it.update({"material": src.get("material", ""), "amount": src.get("amount", 1), "name": src.get("name", ""),
                   "lore": list(src.get("lore") or []), "enchants": {str(k): int(v) for k, v in (src.get("enchants") or {}).items()},
                   "ce_enchants": {str(k): int(v) for k, v in (s.get("ce-enchants") or {}).items()},
                   "dmm_enchants": {str(k): int(v) for k, v in (s.get("dmm-enchants") or {}).items()},
                   "unbreakable": bool(src.get("unbreakable", False)), "hide_enchants": bool(s.get("hide-enchants", False)),
                   "glint": s.get("glint"), "nguoi_luu": s.get("nguoi-luu", ""), "model": s.get("model", "") or "",
                   "compact_enchants": bool(s.get("compact-enchants", False)), "hide_stats": bool(s.get("hide-stats", False)),
                   "attributes": {str(k): float(v) for k, v in (s.get("attributes") or {}).items() if isinstance(v, (int, float))},
                   "nhom": s.get("nhom", "") or "", "cong_dung": s.get("cong-dung", "") or "", "bo": [str(x) for x in (s.get("bo") or [])],
                   "ky_nang": dict(s.get("ky-nang") or {}), "hieu_ung": [str(x) for x in (s.get("hieu-ung") or [])]})
        items.append(it)
    online = []
    try:
        out = rcon(LIB_SERVER, ["minecraft:list"])[0][1]
        names = out.split(":", 1)[1] if ":" in out else ""
        online = [n.strip().split(" ")[-1] for n in re.split(r"[,\n]", names) if n.strip()]
    except Exception:
        pass
    meta = lib_meta()
    if meta and meta.get("ce"):
        vi, ty = ce_vi(), ce_chances()
        for c in meta["ce"]:
            v = vi.get(str(c.get("name", "")).lower())
            if v:
                c["vi"], c["vi_desc"] = str(v[0]), str(v[1])
                if len(v) > 2:
                    c["tac_dung"] = str(v[2])   # tác dụng từng cấp của phù phép luôn có tác dụng (ce-vi.yml phần tử 3)
            if str(c.get("name", "")).lower() in ty:
                c["ty_le"] = ty[str(c.get("name", "")).lower()]
    return {"items": items, "meta": meta, "online": [n for n in online if PLAYER_RE.match(n)], "dung_o": lib_usage(),
            "sach_cam": lib_book_json(doc), "hoat_dong": {k: list(v) for k, v in LIB_ACTIVITIES.items()}, "che_do": LIB_BAN_MODES,
            "nhom": LIB_NHOM, "cong_dung": LIB_USES, "features": WORKSHOP_FEATURES_VI, "effects": sorted(DISH_EFFECTS), "foods": sorted(DISH_FOODS)}


def lib_extra(it):
    """Đầu mục + công dụng của món gửi từ trang Thư viện đồ -> mục YAML (nhom, cong-dung, ky-nang, hieu-ung)."""
    out = {}
    nhom = str(it.get("nhom") or "khac")
    if nhom not in LIB_NHOM:
        raise ValueError("Đầu mục không hợp lệ")
    out["nhom"] = nhom
    # bộ (set) để lọc/chọn nhanh: vd "Tân thủ", "Hào Kiệt"; một món thuộc được nhiều bộ
    bo = []
    for x in it.get("bo") or []:
        t = " ".join(str(x).replace("\n", " ").split())[:30]
        if t and not any(ch in t for ch in "&§<>\"") and t.lower() not in [b.lower() for b in bo]:
            bo.append(t)
    if len(bo) > 8:
        raise ValueError("Một món tối đa 8 bộ")
    if bo:
        out["bo"] = [DoubleQuotedScalarString(b) for b in bo]
    use = str(it.get("cong_dung") or "")
    if use not in LIB_USES:
        raise ValueError("Công dụng không hợp lệ")
    if not use:
        return out
    out["cong-dung"] = use
    if use == "ky-nang":
        k = it.get("ky_nang") or {}
        feat = str(k.get("feature") or "")
        if feat not in WORKSHOP_FEATURES:
            raise ValueError(f"Kỹ năng lạ \"{feat}\"")

        def num(key, lo, hi, dflt, integer=True):
            raw = k.get(key, dflt)
            try:
                x = (int if integer else float)(dflt if raw in ("", None) else raw)
            except (TypeError, ValueError):
                raise ValueError(f"Kỹ năng: {key} phải là số")
            if not lo <= x <= hi:
                raise ValueError(f"Kỹ năng: {key} phải từ {lo} đến {hi}")
            return x
        kn = {"feature": feat}
        if WORKSHOP_FEATURES[feat]:
            v = num("value", 0, 1000, 0, integer=False)
            kn["value"] = int(v) if v == int(v) else v
        text = str(k.get("text") or "").strip()[:80]
        if text:
            kn["text"] = DoubleQuotedScalarString(text)
        kn["uses"] = num("uses", -1, 10_000_000, -1)
        kn["days"] = num("days", 1, 365, 7)
        if k.get("pvp"):
            kn["pvp"] = True
        out["ky-nang"] = kn
    elif use == "mon-an":
        effects = []
        for e in it.get("hieu_ung") or []:
            m = re.match(r"^([A-Z_]+) ([0-3]) (\d{1,4})$", " ".join(str(e).upper().split()))
            if not m or m.group(1) not in DISH_EFFECTS or not 1 <= int(m.group(3)) <= 3600:
                raise ValueError(f"Hiệu ứng sai \"{e}\" (loại, cấp 0–3, 1–3600 giây)")
            effects.append(f"{m.group(1)} {m.group(2)} {int(m.group(3))}")
        if not effects:
            raise ValueError("Món ăn cần ít nhất 1 hiệu ứng khi ăn")
        out["hieu-ung"] = effects
    return out


LIB_LOCK = threading.Lock()   # 23/10: 2 lần Lưu chồng nhau (bấm 2 lần trên điện thoại) làm thuvien-do.yml có 88 KB ký tự rỗng giữa file


def lib_write(y, doc):
    """Ghi thư viện an toàn: ra file tạm rồi đổi tên (không bao giờ để file dở dang / lỗ ký tự rỗng)."""
    LIB_DIR.mkdir(parents=True, exist_ok=True)
    tmp = LIB_FILE.with_suffix(".yml.tmp")
    with tmp.open("w", encoding="utf-8") as f:
        y.dump(doc, f)
    os.replace(tmp, LIB_FILE)


def thuvien_action(data, actor=None):
    with LIB_LOCK:
        return _thuvien_action(data, actor)


def _thuvien_action(data, actor=None):
    action = data.get("action")
    if action == "sachcam":
        y, doc = lib_load()
        doc["sach-cam"] = lib_book_save(doc, data.get("book"))
        lib_write(y, doc)
        try:
            res = rcon(LIB_SERVER, ["dmm thuvien nap"])[0][1]
        except Exception as e:
            res = f"Đã lưu file nhưng chưa nạp được vào server ({e}); server bật lên sẽ tự đọc."
        return {"ok": "lỗi:" not in res, "message": "Đã lưu Sách cấm: đồ của người chơi tự cập nhật dòng ⛔ trong vài giây. " + res,
                "data": thuvien_json()}
    iid = str(data.get("id", ""))
    if not LIB_ID_RE.match(iid):
        raise ValueError("Mã không hợp lệ: chỉ chữ thường không dấu, số, dấu _ (tối đa 32), vd kiem_rong")
    if action == "give":
        player = str(data.get("player", "")).strip()
        amount = int(data.get("amount") or 0)
        if not PLAYER_RE.match(player) or not 0 <= amount <= 64:
            raise ValueError("Tên người chơi hoặc số lượng không hợp lệ")
        res = rcon(LIB_SERVER, [f"dmm laydo {iid} {amount} {player}"])[0][1]
        return {"ok": res.startswith("Đã"), "message": res or "Đã gửi lệnh"}
    y, doc = lib_load()
    items = doc.setdefault("items", {})
    if "sach-cam" not in doc:
        doc["sach-cam"] = lib_default_book(doc)   # ghi hẳn luật cũ trước khi sửa món (ô "pvp" của món đã bỏ, luật nằm ở Sách cấm)
    if action == "rename":
        new = str(data.get("new_id", "")).strip()
        if iid not in items:
            raise ValueError("Không có món này")
        if not LIB_ID_RE.match(new):
            raise ValueError("Mã mới không hợp lệ: chỉ chữ thường không dấu, số, dấu _ (tối đa 32)")
        if new in items:
            raise ValueError(f"Đã có món mã \"{new}\"")
        used = lib_usage().get(iid)
        if used:
            raise ValueError(f"\"{iid}\" đang dùng ở: {', '.join(used)}. Đổi mã ở đó không tự cập nhật nên chưa cho đổi.")
        keys = list(items.keys())
        val = items.pop(iid)
        items.insert(keys.index(iid), new, val)   # giữ đúng vị trí trong danh sách
        for pg in (doc.get("sach-cam") or {}).values():
            do = (pg or {}).get("do") or {}
            if iid in do:
                do[new] = do.pop(iid)
        lib_write(y, doc)
        try:
            res = rcon(LIB_SERVER, ["dmm thuvien nap"])[0][1]
        except Exception as e:
            res = f"Đã lưu file nhưng chưa nạp được vào server ({e})."
        return {"ok": True, "message": f"Đã đổi mã \"{iid}\" thành \"{new}\". {res}", "data": thuvien_json()}
    if action == "delete":
        if iid not in items:
            raise ValueError("Không có món này")
        used = lib_usage().get(iid)
        if used:
            raise ValueError("Món đang được dùng ở: " + ", ".join(used) + ". Bỏ chọn món ở những chỗ đó trước rồi mới xoá.")
        del items[iid]
        for pg in (doc.get("sach-cam") or {}).values():
            if isinstance(pg, dict) and iid in (pg.get("do") or {}):
                del pg["do"][iid]   # bỏ món đã xoá khỏi Sách cấm
    elif action == "save":
        it = data.get("item") or {}
        old = items.get(iid)
        if data.get("is_new") and old is not None:
            raise ValueError("Mã đã có trong thư viện, chọn mã khác")
        extra = lib_extra(it)
        need = {"Xưởng chế tạo": "ky-nang", "Bếp /nauan": "mon-an", "Tinh Luyện": "tinh-luyen", "Mảnh sự kiện": "manh", "Bùa bắt pet": "bua-bat-pet"}
        for where in (lib_usage().get(iid) or []) if old is not None else []:
            if where in need and extra.get("cong-dung", "") != need[where]:
                raise ValueError(f"Món đang dùng ở {where} nên phải giữ công dụng {LIB_USES[need[where]]}. Bỏ chọn món ở đó trước nếu muốn đổi.")
        if old is not None and old.get("nguon") == "game":
            # đồ lấy từ game: hình dạng giữ nguyên vẹn, chỉ sửa đầu mục / công dụng / tên gợi nhớ
            if extra.get("cong-dung") == "mon-an" and str((old.get("preview") or {}).get("material", "")).upper() not in DISH_FOODS:
                raise ValueError("Món ăn phải là đồ ăn được")
            for k in ("nhom", "cong-dung", "ky-nang", "hieu-ung", "bo"):
                old.pop(k, None)
            for k, v in extra.items():
                old[k] = v
            if it.get("ten_goi"):
                old["ten-goi"] = DoubleQuotedScalarString(str(it["ten_goi"])[:60])
            extra = None
    if action == "save" and extra is not None:
        meta = lib_meta() or {}
        mat = str(it.get("material", "")).upper().strip()
        if not MATERIAL_RE.match(mat) or (meta.get("materials") and mat not in meta["materials"]):
            raise ValueError(f"Vật liệu không có trong server: {mat or '(trống)'}")
        name = str(it.get("name", ""))[:150]
        lore = [str(x)[:150] for x in (it.get("lore") or [])][:20]
        ench = {}
        for k, v in (it.get("enchants") or {}).items():
            k = str(k).lower().strip()
            if k.startswith("minecraft:"):
                k = k[len("minecraft:"):]
            lvl = int(v)
            if not ENCH_KEY_RE.match(k) or not 1 <= lvl <= 255:
                raise ValueError(f"Phù phép không hợp lệ: {k} {v} (cấp 1–255)")
            ench[k] = lvl
        # phù phép CrazyEnchantments: tên phải có trong danh sách CE server xuất ra (meta.ce)
        ce_names = {str(c.get("name", "")).lower(): str(c.get("name", "")) for c in (meta.get("ce") or [])}
        cee = {}
        for k, v in (it.get("ce_enchants") or {}).items():
            real, lvl = ce_names.get(str(k).lower()), int(v)
            if not real or not 1 <= lvl <= 255:
                raise ValueError(f"Phù phép CrazyEnchantments không có trên server: {k} (cấp 1–255)")
            cee[real] = lvl
        amount = int(it.get("amount") or 1)
        if not 1 <= amount <= 64:
            raise ValueError("Số lượng 1–64")
        entry = {"nguon": "web", "ten-goi": DoubleQuotedScalarString(str(it.get("ten_goi", ""))[:60] or iid), "material": mat, "amount": amount}
        if name:
            entry["name"] = DoubleQuotedScalarString(name)
        if lore:
            entry["lore"] = [DoubleQuotedScalarString(x) for x in lore]
        if ench:
            entry["enchants"] = ench
        if cee:
            entry["ce-enchants"] = cee
        dmm = {}   # phù phép riêng (SkillEnchants.java): Lướt, Hồi máu, Cầu lửa
        for k, v in (it.get("dmm_enchants") or {}).items():
            if k not in LIB_DMM_ENCH:
                raise ValueError(f"Không có phù phép riêng: {k}")
            lv = int(v)
            if not 1 <= lv <= 5:
                raise ValueError(f"{LIB_DMM_ENCH[k]}: cấp 1 đến 5")
            dmm[k] = lv
        if dmm:
            entry["dmm-enchants"] = dmm
        if it.get("unbreakable"):
            entry["unbreakable"] = True
        if it.get("hide_enchants"):
            entry["hide-enchants"] = True
        if it.get("glint") in (True, False):
            entry["glint"] = it["glint"]
        if it.get("compact_enchants"):
            entry["compact-enchants"] = True   # 3 phù phép một dòng (tooltip dài bị đẩy ra ngoài màn hình)
        if it.get("hide_stats"):
            entry["hide-stats"] = True   # ẩn khối "Khi ở ngực: …" của game, thay bằng 1 dòng gọn
        # chỉ số gốc ghi đè (số như dòng "+8 Sát thương" trong game; ô trống = giữ gốc của loại đồ). ItemLibrary.ATTRS
        attrs = {}
        for k, v in (it.get("attributes") or {}).items():
            if k not in LIB_ATTRS:
                raise ValueError(f"Chỉ số lạ: {k}")
            try:
                v = float(v)
            except (TypeError, ValueError):
                raise ValueError(f"Chỉ số {LIB_ATTRS[k][0]} phải là số")
            lo, hi = LIB_ATTRS[k][1]
            if not lo <= v <= hi:
                raise ValueError(f"Chỉ số {LIB_ATTRS[k][0]} phải trong khoảng {lo} đến {hi}")
            attrs[k] = int(v) if v == int(v) else round(v, 3)
        if attrs:
            entry["attributes"] = attrs
        model = str(it.get("model") or "").strip().lower()
        if model and ":" not in model:
            model = "duckymomo:" + model   # chỉ ghi tên hình thì hiểu là hình trong gói DuckyMoMo
        if model:
            if not LIB_MODEL_RE.match(model):
                raise ValueError(f'Ô "Hình riêng" đang ghi "{model}": phải dạng tên_gói:tên_hình (vd duckymomo:tl_sat), chữ thường không dấu, '
                                 "không dấu cách. Không dùng hình riêng thì xoá trống ô đó.")
            entry["model"] = model
        if extra.get("cong-dung") == "mon-an" and mat not in DISH_FOODS:
            raise ValueError("Món ăn phải là đồ ăn được (bánh mì, cá chín, súp…)")
        entry.update(extra)
        if actor:
            entry["nguoi-luu"] = actor
        items[iid] = entry
    elif action not in ("save", "delete"):   # (trước 21/10 ghi != "save": nút Xoá luôn báo lỗi, không xoá được)
        raise ValueError("Thao tác không hợp lệ")
    lib_write(y, doc)
    try:
        res = rcon(LIB_SERVER, ["dmm thuvien nap"])[0][1]
    except Exception as e:
        res = f"Đã lưu file nhưng chưa nạp được vào server ({e}); server bật lên sẽ tự đọc."
    verb = "Đã xoá" if action == "delete" else "Đã lưu"
    return {"ok": "lỗi:" not in res, "message": f"{verb} \"{iid}\". {res}", "data": thuvien_json()}

# ---------------------------------------------------------------- Boss (EliteMobs custombosses, banghoi-1)
# Boss tạo trên web: custombosses/dmm_<mã>.yml (sửa được). Boss khác (EliteMobs, dungeon): chỉ xem.

BOSS_SERVER = "banghoi-1"
EM_DIR = DATA_DIR / BOSS_SERVER / "plugins" / "EliteMobs"
BOSS_DIR = EM_DIR / "custombosses"
BOSS_ENTITIES = ["ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "SKELETON", "STRAY", "BOGGED", "WITHER_SKELETON", "SPIDER", "CAVE_SPIDER",
                 "CREEPER", "ENDERMAN", "BLAZE", "WITCH", "VINDICATOR", "PILLAGER", "EVOKER", "ILLUSIONER", "RAVAGER", "PIGLIN", "PIGLIN_BRUTE",
                 "ZOMBIFIED_PIGLIN", "HOGLIN", "ZOGLIN", "IRON_GOLEM", "SLIME", "MAGMA_CUBE", "PHANTOM", "GUARDIAN", "ELDER_GUARDIAN",
                 "BREEZE", "WOLF", "POLAR_BEAR", "SILVERFISH", "ENDERMITE", "VEX", "GHAST", "SHULKER"]
BOSS_SLOTS = ["helmet", "chestplate", "leggings", "boots", "mainHand", "offHand"]
BOSS_ID_RE = re.compile(r"^[a-z0-9_]{1,32}$")
WORLD_RE = re.compile(r"^[A-Za-z0-9_\-]{1,64}$")
BOSS_FILE_RE = re.compile(r"^[A-Za-z0-9_\-]{1,64}\.yml$")
# bản gốc boss có sẵn của EliteMobs, chép ra trước lần sửa đầu (để khôi phục); ngoài thư mục EliteMobs để plugin không đọc nhầm
BOSS_BACKUP_DIR = DATA_DIR / "panel" / "boss-goc"
# các mục trang Boss quản lý; mục khác trong file (vệt hạt, câu nói, loot riêng…) luôn được giữ nguyên
BOSS_KEYS = ["isEnabled", "entityType", "name", "level", "healthMultiplier", "damageMultiplier", "powers", "spawnMessage",
             "deathMessage", "deathMessages", "onDeathCommands", "announcementPriority", "timeout", "dropsEliteMobsLoot"]


def boss_powers():
    pdir = EM_DIR / "powers"
    return sorted(p.name for p in pdir.iterdir() if p.suffix in (".lua", ".yml")) if pdir.exists() else []


def boss_json():
    safe = YAML(typ="safe")
    bosses = []
    if BOSS_DIR.exists():
        for f in sorted(BOSS_DIR.glob("*.yml")):
            try:
                d = safe.load(f.read_text(encoding="utf-8")) or {}
            except Exception:
                d = {}
            web = f.name.startswith("dmm_")
            extra = [k for k in d if k not in BOSS_KEYS + BOSS_SLOTS + ["bossType", "classLoot", "onSpawnBlockStates", "onRemoveBlockStates"]]
            bosses.append({"file": f.name, "id": f.stem[4:] if web else f.stem, "web": web,
                           "backup": (BOSS_BACKUP_DIR / f.name).exists(), "extra": extra,
                           "data": {k: d.get(k) for k in BOSS_KEYS + BOSS_SLOTS if k in d}})
    try:
        drops = YAML(typ="safe").load(BOSS_DROPS_FILE.read_text(encoding="utf-8")) or {}
    except (OSError, ValueError):
        drops = {}
    for b in bosses:
        b["drops"] = [d for d in (drops.get(b["file"]) or []) if isinstance(d, dict)]
    lib = [{"id": str(k), "ten": str((v or {}).get("ten-goi") or k), "nhom": (v or {}).get("nhom") or "khac",
            "name": str(((v or {}).get("preview") or {}).get("name", "") if (v or {}).get("nguon") == "game" else (v or {}).get("name", "") or "")}
           for k, v in lib_items().items()]
    return {"bosses": bosses, "powers": boss_powers(), "entities": BOSS_ENTITIES, "slots": BOSS_SLOTS, "world": "world",
            "lib": lib, "nhom": LIB_NHOM, "drop_to": BOSS_DROP_TO}


def boss_drops_save(fname, drops):
    """Đồ rơi (chọn từ thư viện đồ) của một boss -> plugins/DuckyMoMoCore/boss-roi.yml; DuckyMoMoCore đọc lại khi file đổi."""
    if not BOSS_FILE_RE.match(fname):
        raise ValueError("Tên file boss không hợp lệ")
    lib = lib_items()
    out = []
    for d in (drops or [])[:20]:
        d = d or {}
        iid = str(d.get("do") or "").strip().lower()
        if iid not in lib:
            raise ValueError(f"Đồ rơi: thư viện đồ không có \"{iid}\"")
        try:
            rate = float(d.get("ti-le"))
            n = int(d.get("so") or 1)
        except (TypeError, ValueError):
            raise ValueError("Đồ rơi: tỉ lệ và số lượng phải là số")
        if not 0.01 <= rate <= 100 or not 1 <= n <= 64:
            raise ValueError("Đồ rơi: tỉ lệ 0,01–100%, số lượng 1–64")
        to = str(d.get("cho") or "top1")
        if to not in BOSS_DROP_TO:
            raise ValueError("Đồ rơi: chọn người nhận không hợp lệ")
        out.append({"do": iid, "ti-le": int(rate) if rate == int(rate) else rate, "so": n, "cho": to})
    y = yaml_rt()
    doc = y.load(BOSS_DROPS_FILE.read_text(encoding="utf-8")) if BOSS_DROPS_FILE.exists() else None
    if doc is None:
        doc = {}
    if out:
        doc[fname] = out
    else:
        doc.pop(fname, None)
    LIB_DIR.mkdir(parents=True, exist_ok=True)
    tmp = BOSS_DROPS_FILE.with_suffix(".yml.tmp")
    with tmp.open("w", encoding="utf-8") as f:
        f.write("# Đồ rơi boss chọn từ thư viện đồ (trang quản trị › Boss › Đồ rơi). Khoá = file boss EliteMobs.\n"
                "# cho: top1 | top3 | tat-ca | ngau-nhien. DuckyMoMoCore đọc lại khi file đổi, không cần nạp lại.\n")
        y.dump(doc, f)
    tmp.replace(BOSS_DROPS_FILE)
    return len(out)


def boss_clean(b, partial, old_etype=None):
    """Kiểm tra và chuẩn hoá các mục boss gửi từ web. partial: chỉ các mục có trong b. None = xoá mục khỏi file."""
    out = {}
    has = (lambda k: k in b) if partial else (lambda k: True)
    if has("entityType"):
        etype = str(b.get("entityType", "")).upper()
        if etype not in BOSS_ENTITIES and etype != str(old_etype or "").upper():
            raise ValueError("Loại quái không hợp lệ")
        out["entityType"] = etype
    if has("isEnabled"):
        out["isEnabled"] = bool(b.get("isEnabled", True))
    if has("name"):
        out["name"] = DoubleQuotedScalarString(str(b.get("name", ""))[:160] or "Boss")
    if has("level"):
        level = str(b.get("level", "dynamic")).strip().lower()
        if level != "dynamic" and not (level.isdigit() and 1 <= int(level) <= 200):
            raise ValueError("Cấp: 1–200 hoặc dynamic")
        out["level"] = "dynamic" if level == "dynamic" else int(level)
    for k, lo, hi in (("healthMultiplier", 0.01, 1000), ("damageMultiplier", 0.01, 100)):
        if has(k):
            try:
                v = float(b.get(k, 1))
            except (TypeError, ValueError):
                raise ValueError("Hệ số máu / sát thương phải là số")
            if not lo <= v <= hi:
                raise ValueError("Hệ số máu 0,01–1000; sát thương 0,01–100")
            out[k] = v
    if has("powers"):
        known = set(boss_powers())
        out["powers"] = [str(p) for p in (b.get("powers") or []) if str(p) in known]
    if has("dropsEliteMobsLoot"):
        out["dropsEliteMobsLoot"] = bool(b.get("dropsEliteMobsLoot", True))
    if has("announcementPriority"):
        out["announcementPriority"] = max(0, min(3, int(b.get("announcementPriority", 2) or 0)))
    if has("timeout"):
        out["timeout"] = max(0, min(1440, int(b.get("timeout", 0) or 0)))
    for k in ("spawnMessage", "deathMessage"):
        if has(k):
            v = str(b.get(k) or "").strip()
            out[k] = DoubleQuotedScalarString(v[:200]) if v else None
    for k in ("deathMessages", "onDeathCommands"):
        if has(k):
            lines = [str(x).strip()[:200] for x in (b.get(k) or []) if str(x).strip()][:20]
            if k == "onDeathCommands":
                lines = [x.lstrip("/") for x in lines]
            out[k] = [DoubleQuotedScalarString(x) for x in lines] if lines else None
    for slot in BOSS_SLOTS:
        if has(slot):
            m = str(b.get(slot) or "").strip().upper()
            if m and not MATERIAL_RE.match(m):
                raise ValueError(f"Trang bị {slot} sai: {m}")
            out[slot] = m or None
    return out


def boss_action(data):
    action = data.get("action")
    if action == "spawn":
        fname = str(data.get("file", ""))
        if not re.match(r"^[A-Za-z0-9_\-]{1,64}\.yml$", fname) or not (BOSS_DIR / fname).exists():
            raise ValueError("Không có boss này")
        level = str(data.get("level") or "").strip()
        if level and not re.match(r"^\d{1,3}$", level):
            raise ValueError("Cấp phải là số")
        player = str(data.get("player") or "").strip()
        if player:
            if not PLAYER_RE.match(player):
                raise ValueError("Tên người chơi không hợp lệ")
            pos = rcon(BOSS_SERVER, [f"minecraft:data get entity {player} Pos", f"minecraft:data get entity {player} Dimension"])
            nums = re.findall(r"-?\d+(?:\.\d+)?d", pos[0][1])
            if len(nums) < 3 or "overworld" not in pos[1][1]:
                raise ValueError(f"{player} không online ở thế giới chính của Bang Hội Chiến")
            x, y, z = (float(n[:-1]) for n in nums[:3])
            world = "world"
            x += 4  # đặt lệch vài khối, không đè lên người chơi
        else:
            world = str(data.get("world") or "world")
            if not WORLD_RE.match(world):
                raise ValueError("Tên world không hợp lệ")
            try:
                x, y, z = (float(data.get(k)) for k in ("x", "y", "z"))
            except (TypeError, ValueError):
                raise ValueError("Toạ độ x, y, z phải là số")
        cmd = f"em spawn bossAt {fname} {world} {x:.1f} {y:.1f} {z:.1f}" + (f" {level}" if level else "")
        out = rcon(BOSS_SERVER, [cmd])[0][1]
        return {"ok": True, "message": f"Đã gọi {fname} tại {world} {x:.0f} {y:.0f} {z:.0f}. {out}".strip()}
    if action == "drops":
        fname = str(data.get("file") or "")
        if not BOSS_FILE_RE.match(fname) or not (BOSS_DIR / fname).exists():
            raise ValueError("Lưu boss trước rồi mới đặt đồ rơi")
        n = boss_drops_save(fname, data.get("drops"))
        return {"ok": True, "message": f"Đã lưu {n} món đồ rơi cho {fname} (có hiệu lực ngay, không cần nạp lại).", "data": boss_json()}
    fname = str(data.get("file") or "")
    shipped = bool(fname) and not fname.startswith("dmm_")
    if shipped:
        # boss có sẵn của EliteMobs / dungeon: sửa thẳng file gốc (chỉ các mục đã đổi), khôi phục, hoặc chép thành boss mới
        if not BOSS_FILE_RE.match(fname) or not (BOSS_DIR / fname).exists():
            raise ValueError("Không có boss này")
        path, bid = BOSS_DIR / fname, fname[:-4]
    else:
        bid = str(data.get("id", ""))
        if not BOSS_ID_RE.match(bid):
            raise ValueError("Mã boss chỉ gồm chữ thường không dấu, số, _ (vd rong_lua)")
        path = BOSS_DIR / f"dmm_{bid}.yml"
    if action == "delete":
        if shipped:
            raise ValueError("Boss có sẵn không xoá được trên web (tắt bằng công tắc Bật boss)")
        if not path.exists():
            raise ValueError("Không có boss này")
        path.unlink()
        if BOSS_DROPS_FILE.exists():
            boss_drops_save(path.name, [])
    elif action == "restore":
        bak = BOSS_BACKUP_DIR / fname
        if not shipped or not bak.exists():
            raise ValueError("Không có bản gốc để khôi phục")
        path.write_bytes(bak.read_bytes())
        bak.unlink()
    elif action == "copy":
        new_id = str(data.get("new_id", ""))
        if not BOSS_ID_RE.match(new_id):
            raise ValueError("Mã boss mới chỉ gồm chữ thường không dấu, số, _ (vd snoopy_lua)")
        dst = BOSS_DIR / f"dmm_{new_id}.yml"
        if dst.exists():
            raise ValueError("Mã đã có, chọn mã khác")
        dst.write_text(f"# Chép từ {path.name} trên trang quản trị DuckyMoMo (Boss).\n" + path.read_text(encoding="utf-8"), encoding="utf-8")
        bid = new_id
    elif action == "save":
        b = data.get("boss") or {}
        if not shipped and data.get("is_new") and path.exists():
            raise ValueError("Mã đã có, chọn mã khác")
        y = yaml_rt()
        doc = y.load(path.read_text(encoding="utf-8")) if path.exists() else None
        if doc is None:
            doc = {}
        # boss có sẵn: chỉ ghi mục được gửi (đã đổi); boss web mới: đủ mục + mặc định
        partial = shipped or bool(doc)
        upd = boss_clean(b, partial, doc.get("entityType"))
        if shipped and not upd:
            raise ValueError("Chưa đổi gì")
        if "powers" in upd:
            # giữ kỹ năng trang web không quản (có tham số, dạng bảng, hoặc không có trong thư mục powers)
            known = set(boss_powers())
            keep = [p for p in (doc.get("powers") or []) if not isinstance(p, str) or p not in known]
            upd["powers"] = keep + upd["powers"]
        for k, v in upd.items():
            if v is None:
                doc.pop(k, None)
            else:
                doc[k] = v
        if not partial:
            doc.setdefault("bossType", "NORMAL")
            doc.setdefault("classLoot", True)
        if shipped:
            BOSS_BACKUP_DIR.mkdir(parents=True, exist_ok=True)
            bak = BOSS_BACKUP_DIR / fname
            if not bak.exists():
                bak.write_bytes(path.read_bytes())
        BOSS_DIR.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".yml.tmp")
        with tmp.open("w", encoding="utf-8") as f:
            if not shipped and not path.exists():
                f.write("# Tạo trên trang quản trị DuckyMoMo (Boss). Sửa trên web, đừng sửa tay (sẽ bị ghi đè).\n")
            y.dump(doc, f)
        tmp.replace(path)
    else:
        raise ValueError("Thao tác không hợp lệ")
    try:
        rcon(BOSS_SERVER, ["em reload"])
        res = "EliteMobs đã nạp lại."
    except Exception as e:
        res = f"Đã lưu file nhưng chưa nạp được ({e}); bật server lên sẽ đọc."
    verb = {"delete": "Đã xoá", "restore": "Đã khôi phục bản gốc", "copy": "Đã chép thành"}.get(action, "Đã lưu")
    return {"ok": True, "message": f"{verb} boss \"{bid}\". {res}", "data": boss_json()}

# ---------------------------------------------------------------- đăng nhập bằng tài khoản game (trang web công khai)

_web_cache = {}
_web_lock = threading.Lock()


def web_cached(key, ttl, fn):
    now = time.time()
    with _web_lock:
        hit = _web_cache.get(key)
        if hit and now - hit[0] < ttl:
            return hit[1]
    val = fn()
    with _web_lock:
        _web_cache[key] = (now, val)
    return val


# ---------------------------------------------------------------- Hệ thống: trạng thái, lệnh bảo trì, khởi động lại server
# Khởi động lại = lưu thế giới rồi "stop" qua RCON; container có restart: unless-stopped nên Docker tự bật lại (~1 phút).
# Proxy (Velocity) không có RCON nên không điều khiển từ đây.

HETHONG_ACTIONS = {
    "luu": (["save-all flush"], "Lưu thế giới"),
    "donrac": (["dmm clean"], "Dọn rác (đồ rơi, mũi tên; giữ đồ quanh chỗ chết, đồ có tên, đất thị trấn)"),
    "napdmm": (["dmm reload"], "Nạp lại DuckyMoMoCore"),
    "naptab": (["tab reload"], "Nạp lại bảng TAB/scoreboard"),
    "napem": (["em reload"], "Nạp lại EliteMobs"),
}
RESTART_PENDING = {}   # server -> {"luc": epoch giây sẽ tắt, "nguoi": ai bấm, "huy": threading.Event}
COLOR_RE = re.compile(r"§.|\x1b\[[0-9;]*m")


def _num(x):
    try:
        return float(x.replace(",", "").replace("*", ""))
    except ValueError:
        return None


def hethong_status():
    out = []
    for srv in GAME_SERVERS:
        d = {"server": srv, "online": False}
        try:
            res = dict(rcon(srv, ["minecraft:list", "tps", "mspt", "essentials:gc"]))
            d["online"] = True
        except Exception as e:
            d["loi"] = str(e)
            out.append(d)
            continue
        lst = COLOR_RE.sub("", res.get("minecraft:list", ""))
        m = re.search(r"There are (\d+) of a max of (\d+) players online:?\s*(.*)", lst)
        if m:
            d["nguoi_so"], d["nguoi_max"] = int(m.group(1)), int(m.group(2))
            d["nguoi"] = [x.strip() for x in m.group(3).split(",") if x.strip()]
        m = re.search(r":\s*([\d.*]+),\s*([\d.*]+),\s*([\d.*]+)", COLOR_RE.sub("", res.get("tps", "")))
        if m:
            d["tps"] = [_num(m.group(i)) for i in (1, 2, 3)]
        m = re.search(r"([\d.]+)/([\d.]+)/([\d.]+)", COLOR_RE.sub("", res.get("mspt", "")))
        if m:
            d["mspt"] = _num(m.group(1))
        gc = COLOR_RE.sub("", res.get("essentials:gc", ""))
        mx, fr = re.search(r"(?:Maximum memory|Bộ nhớ tối đa): ([\d,]+)", gc), re.search(r"(?:Free memory|Bộ nhớ trống): ([\d,]+)", gc)   # Essentials tiếng Anh hoặc tiếng Việt
        al = re.search(r"(?:Allocated memory|Bộ nhớ có sẵn): ([\d,]+)", gc)
        if mx and fr and al:
            d["ram_max"], d["ram_dung"] = int(_num(mx.group(1))), int(_num(al.group(1)) - _num(fr.group(1)))
        up = re.search(r"(?:Uptime|Thời gian hoạt động): (.+)", gc)
        if up:
            d["uptime"] = up.group(1).strip()
        worlds = re.findall(r'"([^"]+)": ([\d,]+) (?:chunks|mảnh), ([\d,]+) (?:entities|thực thể)', gc)
        if worlds:
            d["chunks"] = sum(int(_num(w[1])) for w in worlds)
            d["entities"] = sum(int(_num(w[2])) for w in worlds)
        p = RESTART_PENDING.get(srv)
        if p:
            d["restart_con"] = max(0, int(p["luc"] - time.time()))
            d["restart_nguoi"] = p["nguoi"]
        out.append(d)
    return {"servers": out, "lenh": {k: v[1] for k, v in HETHONG_ACTIONS.items()}}


def _restart_worker(srv, ev):
    p = RESTART_PENDING.get(srv)
    marks = {300, 120, 60, 30, 10, 5, 4, 3, 2, 1}
    while p and not ev.is_set():
        left = int(round(p["luc"] - time.time()))
        if left <= 0:
            break
        if left in marks:
            msg = f"⚠ Server khởi động lại sau {left} giây" if left < 60 else f"⚠ Server khởi động lại sau {left // 60} phút"
            raw = {"text": msg + ". Tìm chỗ an toàn và đừng giao dịch lúc này!", "color": "gold", "bold": left <= 10}
            try:
                rcon(srv, ["tellraw @a " + json.dumps(raw, ensure_ascii=False)]
                     + ([f'title @a title {json.dumps({"text": str(left), "color": "red"})}'] if left <= 5 else []))
            except Exception:
                pass
        ev.wait(1)
    if ev.is_set():
        try:
            rcon(srv, ["tellraw @a " + json.dumps({"text": "✔ Đã huỷ khởi động lại server.", "color": "green"}, ensure_ascii=False)])
        except Exception:
            pass
        RESTART_PENDING.pop(srv, None)
        return
    try:
        rcon(srv, ["save-all flush", "stop"])
    except Exception:
        pass   # "stop" đóng kết nối RCON giữa chừng là bình thường
    RESTART_PENDING.pop(srv, None)


def hethong_action(body, who):
    srv, act = str(body.get("server", "")), str(body.get("action", ""))
    if srv not in GAME_SERVERS:
        raise ValueError("Server không hợp lệ")
    if act in HETHONG_ACTIONS:
        cmds, label = HETHONG_ACTIONS[act]
        res = rcon(srv, cmds)
        text = "\n".join(COLOR_RE.sub("", o).strip() for _, o in res if o.strip()) or "(không có phản hồi)"
        web_log({"viec": "/api/hethong", "nguoi": who or PANEL_USER, "hanh_dong": act, "doi_tuong": srv, "ok": True})
        return {"ok": True, "message": f"{label} ({srv}): {text[:600]}"}
    if act == "reset":
        # Reset người chơi (DuckyMoMoCore PlayerReset.adminReset): "choilai" = như /choilai do admin làm; "moi" = như người mới hoàn toàn
        ten = str(body.get("ten", "")).strip()
        if not PLAYER_RE.match(ten):
            raise ValueError("Tên người chơi không hợp lệ")
        cmd = "choilai admin " + ten + (" moi" if body.get("moi") else "") + (" giurank" if body.get("giurank") else "")
        out = COLOR_RE.sub("", rcon(srv, [cmd])[0][1]).strip()
        ok = out.startswith("RESET OK")
        web_log({"viec": "/api/hethong", "nguoi": who or PANEL_USER, "hanh_dong": cmd, "doi_tuong": srv, "ok": ok})
        return {"ok": ok, "message": out.replace("RESET OK: ", "").replace("RESET LOI: ", "") or "Server không trả lời (server có module Chơi lại không?)"}
    if act == "huy":
        p = RESTART_PENDING.get(srv)
        if not p:
            raise ValueError("Không có lần khởi động lại nào đang chờ")
        p["huy"].set()
        web_log({"viec": "/api/hethong", "nguoi": who or PANEL_USER, "hanh_dong": "huy-restart", "doi_tuong": srv, "ok": True})
        return {"ok": True, "message": f"Đã huỷ khởi động lại {srv}"}
    if act == "restart":
        if srv in RESTART_PENDING:
            raise ValueError("Server này đang đếm ngược khởi động lại")
        giay = max(0, min(600, int(body.get("giay", 60) or 0)))
        lst = COLOR_RE.sub("", rcon(srv, ["minecraft:list"])[0][1])
        m = re.search(r"There are (\d+) of a max of \d+ players online:?\s*(.*)", lst)
        so = int(m.group(1)) if m else 0
        if so > 0 and not body.get("ep"):
            return {"ok": False, "can_xac_nhan": True, "nguoi": [x.strip() for x in (m.group(2) if m else "").split(",") if x.strip()],
                    "message": f"Đang có {so} người online ở {srv}. Xác nhận để vẫn khởi động lại (họ được báo trước {giay} giây)."}
        ev = threading.Event()
        RESTART_PENDING[srv] = {"luc": time.time() + giay, "nguoi": who or PANEL_USER, "huy": ev}
        threading.Thread(target=_restart_worker, args=(srv, ev), daemon=True).start()
        web_log({"viec": "/api/hethong", "nguoi": who or PANEL_USER, "hanh_dong": f"restart {giay}s", "doi_tuong": srv, "ok": True})
        return {"ok": True, "message": f"{srv}: khởi động lại sau {giay} giây (có báo trong game). Docker tự bật lại sau khoảng 1–2 phút."}
    raise ValueError("Lệnh không hợp lệ")


def web_log(event):
    from datetime import datetime, timezone
    event = {"ts": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")} | event
    try:
        WEB_LOG_FILE.parent.mkdir(parents=True, exist_ok=True)
        with WEB_LOG_FILE.open("a", encoding="utf-8") as f:
            f.write(json.dumps(event, ensure_ascii=False) + "\n")
    except OSError as e:
        print(f"Không ghi được {WEB_LOG_FILE}: {e}", flush=True)


def online_players(fresh=False):
    """{tên thường: server} của người đang online ở lobby/banghoi/practice (giữ 15 giây; fresh=True: hỏi lại ngay)."""
    if fresh:
        with _web_lock:
            _web_cache.pop("online", None)
    def load():
        out = {}
        for srv in GAME_SERVERS:
            # list gốc của Minecraft: luôn tên thật (list của Essentials hiện tên /nick, vd ~b0ybn2013)
            try:
                text = rcon(srv, ["minecraft:list"])[0][1]
            except Exception:
                continue
            names = text.split(":", 1)[1] if ":" in text else ""
            for n in re.split(r"[,\n]", names):
                n = n.strip().split(" ")[-1]
                if PLAYER_RE.match(n):
                    out[n.lower()] = srv
        return out
    return web_cached("online", 15, load)


def warn_login(name, how, ip):
    """Báo vào game cho chủ tài khoản (nếu đang online) mỗi lần tài khoản đăng nhập web."""
    raw = ["", {"text": "[Web] ", "color": "gold", "bold": True},
           {"text": f"Tài khoản của bạn vừa đăng nhập trang web ({how}) lúc {time.strftime('%H:%M')}. ", "color": "yellow"},
           {"text": "Không phải bạn? Đổi mật khẩu bằng /changepassword và báo admin ngay.", "color": "red"}]
    srv = online_players().get(name.lower())
    if srv:
        try:
            rcon(srv, [f"tellraw {name} " + json.dumps(raw, ensure_ascii=False)])
        except Exception:
            pass


def web_login(data, ip):
    """Trang web hỏi: tên + mã /web (chỉ cách này, không nhận mật khẩu). Trả tên thật nếu đúng."""
    name = str(data.get("ten", "")).strip()
    if not PLAYER_RE.match(name):
        return {"ok": False, "message": "Tên nhân vật không hợp lệ"}
    (real, reason), how = taikhoan.verify_code(name, str(data.get("ma", "")).strip(), rcon, GAME_SERVERS), "mã /web"
    web_log({"viec": "dang-nhap", "nguoi": name, "cach": how, "ok": bool(real), "ly_do": reason, "ip": ip})
    if not real:
        return {"ok": False, "ly_do": reason}
    threading.Thread(target=warn_login, args=(real, how, ip), daemon=True).start()
    return {"ok": True, "ten": real}


def web_reset(data, ip):
    """Nút "Chơi lại từ đầu": mở bước xác nhận trong game (người chơi phải đang ở Bang Hội Chiến). Web không tự xoá gì."""
    name = str(data.get("ten", "")).strip()
    if not PLAYER_RE.match(name):
        raise ValueError("Tên không hợp lệ")
    try:
        out = rcon("banghoi-1", [f"dmm choilaiweb {name}"])[0][1]
    except Exception:
        out = "LOI"
    web_log({"viec": "choilai-web", "nguoi": name, "kq": out[:200], "ip": ip})
    if out.startswith("CHOILAI OK"):
        return {"ok": True, "message": "Đã mở bước xác nhận trong game. Xem chat trong game và gõ /choilai xacnhan trong 30 giây (Bedrock: bấm nút)."}
    if out.startswith("CHOILAI CHUA"):
        return {"ok": False, "message": "Chưa chơi lại được: " + out[len("CHOILAI CHUA"):].strip()}
    if out.startswith("CHOILAI OFFLINE"):
        return {"ok": False, "message": "Bạn cần đang online ở Bang Hội Chiến (vì đồ trên người được xoá ngay lúc đó). Vào game rồi bấm lại."}
    if out.startswith("CHOILAI TAT"):
        return {"ok": False, "message": "Chơi lại từ đầu đang tắt trên server."}
    return {"ok": False, "message": "Server Bang Hội Chiến đang tắt hoặc chưa cập nhật, thử lại sau."}


# ---------------------------------------------------------------- donate (chuyển khoản QR, admin duyệt tay)
# Người chơi chọn gói trên trang tài khoản, quét QR, bấm "Tôi đã chuyển khoản" -> ghi data/panel/donate.json + báo admin
# (tin trong game cho admin đang online, dòng "admin" ở trang Hoạt động, trang /donate). Chưa tự cấp rank: admin kiểm tra
# tiền đã về rồi bấm "Cấp rank" (chạy /rank give) hoặc "Huỷ".

DONATE_FILE = DATA_DIR / "panel" / "donate.json"
_donate_lock = threading.Lock()
PRICE_RE = re.compile(r"(\d+)\s*(tháng|thang|năm|nam)\D*?([\d.,]+)\s*(k|nghìn|ngàn|tr|triệu|đ|vnd)?", re.I)


def donate_tiers():
    """Các bậc + gói từ cấu hình DuckyMoMoCore (giá ghi kiểu '3 tháng 50k', '1 năm 1.200k')."""
    core = taikhoan.safe_yaml(taikhoan.DMM / "config.yml")
    out = []
    for t in (core.get("donate-ranks") or {}).get("tiers") or []:
        goi = []
        for i, txt in enumerate(t.get("prices") or []):
            m = PRICE_RE.search(str(txt))
            if not m:
                continue
            n = int(m.group(1)) * (12 if m.group(2).lower() in ("năm", "nam") else 1)
            num = float(m.group(3).replace(".", "").replace(",", "."))
            unit = (m.group(4) or "").lower()
            vnd = int(num * (1000 if unit in ("k", "nghìn", "ngàn") else 1_000_000 if unit in ("tr", "triệu") else 1))
            goi.append({"id": i, "ten": str(txt), "thang": n, "tien": vnd})
        out.append({"group": t.get("group"), "ten": t.get("name"), "mau": t.get("color", "&f"), "goi": goi})
    return out


def donate_load():
    try:
        return json.loads(DONATE_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return []


def donate_save(rows):
    DONATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    tmp = DONATE_FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")
    tmp.replace(DONATE_FILE)


def donate_code(name, tier_idx, months, rid):
    """Nội dung chuyển khoản: chỉ chữ + số (ngân hàng bỏ ký tự lạ), vd 'DMM B0YBN R1 3T K7Q2'."""
    return f"DMM {re.sub(r'[^A-Za-z0-9]', '', name).upper()} R{tier_idx + 1} {months}T {rid}"


def notify_admins(text):
    """Báo admin đang online (có quyền duckymomo.rank.admin) bằng tin trong game + ghi dòng trang Hoạt động."""
    raw = ["", {"text": "[Donate] ", "color": "gold", "bold": True}, {"text": text, "color": "yellow"}]
    for name, srv in online_players().items():
        try:
            perms = taikhoan.load_perms(name)
            if perms and taikhoan.has(perms, "duckymomo.rank.admin"):
                rcon(srv, [f"tellraw {perms['name']} " + json.dumps(raw, ensure_ascii=False)])
        except Exception:
            pass
    from datetime import datetime, timezone
    event = {"ts": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "server": "web", "type": "admin", "player": "Donate",
             "text": "Donate · " + text, "target": ""}
    try:
        ADMIN_FILE.parent.mkdir(parents=True, exist_ok=True)
        with ADMIN_FILE.open("a", encoding="utf-8") as f:
            f.write(json.dumps(event, ensure_ascii=False) + "\n")
    except OSError:
        pass


SITE_CFG = Path(os.environ.get("SITE_CFG", "/repo/site-config/site.yml"))


# Mã BIN ngân hàng (chuẩn VietQR/NAPAS 247). Ngân hàng khác: ghi "bin: <6 số>" trong site/config/site.yml › donate.
BANK_BIN = {"VCB": "970436", "VIETCOMBANK": "970436", "TCB": "970407", "TECHCOMBANK": "970407", "MB": "970422", "MBBANK": "970422",
            "ACB": "970416", "VPB": "970432", "VPBANK": "970432", "BIDV": "970418", "VTB": "970415", "ICB": "970415", "VIETINBANK": "970415",
            "TPB": "970423", "TPBANK": "970423", "VBA": "970405", "AGRIBANK": "970405", "STB": "970403", "SACOMBANK": "970403",
            "VIB": "970441", "HDB": "970437", "HDBANK": "970437", "SHB": "970443", "OCB": "970448", "MSB": "970426", "SEAB": "970440",
            "LPB": "970449", "EIB": "970431", "EXIMBANK": "970431", "NAB": "970428", "BAB": "970409", "CAKE": "546034", "UBANK": "546035",
            "TIMO": "963388", "SCB": "970429", "PVCB": "970412", "KLB": "970452", "VCCB": "970454", "ABB": "970425", "BVB": "970438"}


def _crc16(data):
    crc = 0xFFFF
    for b in data:
        crc ^= b << 8
        for _ in range(8):
            crc = ((crc << 1) ^ 0x1021) if crc & 0x8000 else crc << 1
            crc &= 0xFFFF
    return crc


def vietqr_png(bin_, acct, amount, info):
    """Ảnh QR PNG (data URI) theo chuẩn VietQR NAPAS 247, tự tạo (20/10: img.vietqr.io đổi sang SVG, Java trong game không đọc được)."""
    import segno, io
    f = lambda i, v: f"{i}{len(v):02d}{v}"
    info = re.sub(r"[^A-Za-z0-9 ]", "", str(info))[:50]
    mai = f("00", "A000000727") + f("01", f("00", bin_) + f("01", acct)) + f("02", "QRIBFTTA")
    s = f("00", "01") + f("01", "12") + f("38", mai) + f("53", "704") + (f("54", str(int(amount))) if amount else "") \
        + f("58", "VN") + (f("62", f("08", info)) if info else "") + "6304"
    s += f"{_crc16(s.encode()):04X}"
    buf = io.BytesIO()
    segno.make(s, error="m").save(buf, kind="png", scale=8, border=2)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def donate_qr(don):
    """Link ảnh VietQR (chỉ mã, cho tấm bản đồ trong game) + thông tin ngân hàng từ site/config/site.yml (mount ro). Thiếu = không có QR."""
    try:
        bank = (taikhoan.safe_yaml(SITE_CFG).get("donate") or {})
    except Exception:
        bank = {}
    nh, stk = str(bank.get("ngan-hang") or "").strip(), str(bank.get("so-tai-khoan") or "").strip()
    if not nh or not stk:
        return {"qr": "", "bank": {}}
    bin_ = str(bank.get("bin") or BANK_BIN.get(nh.upper().replace(" ", ""), "")).strip()
    if bin_.isdigit() and re.fullmatch(r"[0-9A-Za-z]{4,19}", stk):
        qr = vietqr_png(bin_, stk, don["tien"], don["noi_dung"])
    else:   # ngân hàng chưa có mã BIN: dùng ảnh của VietQR (web xem được, trong game có thể không đọc được)
        q = urllib.parse.urlencode({"amount": don["tien"], "addInfo": don["noi_dung"], "accountName": bank.get("chu-tai-khoan") or ""}, quote_via=urllib.parse.quote)
        qr = f"https://img.vietqr.io/image/{urllib.parse.quote(nh)}-{urllib.parse.quote(stk)}-qr_only.png?{q}"
    return {"qr": qr,
            "bank": {"ngan-hang": nh, "so-tai-khoan": stk, "chu-tai-khoan": bank.get("chu-tai-khoan") or ""}}


def web_donate(data, ip):
    """Trang web: tạo mã chuyển khoản (action=tao) hoặc báo đã chuyển (action=bao); xem yêu cầu của mình (action=ds)."""
    import secrets
    name = str(data.get("ten", "")).strip()
    if not PLAYER_RE.match(name):
        raise ValueError("Tên không hợp lệ")
    act = data.get("action")
    with _donate_lock:
        rows = donate_load()
        mine = [r for r in rows if r["ten"].lower() == name.lower()]
        if act == "ds":
            return {"ok": True, "yeu_cau": mine[-10:][::-1], "bac": donate_tiers()}
        if act == "tao" and data.get("tien") is not None:
            # 20/10: nạp VÀNG theo số tiền (không chọn gói rank nữa); rank và đồ trang trí mua bằng Vàng (/vang, trang tài khoản)
            vc = vang.cfg()
            try:
                tien = int(str(data.get("tien")).replace(".", "").replace(",", ""))
            except ValueError:
                raise ValueError("Số tiền không hợp lệ")
            if tien < vc["nap_toi_thieu"] or tien > 20_000_000:
                raise ValueError(f"Nạp từ {vc['nap_toi_thieu']:,}đ tới 20.000.000đ.".replace(",", "."))
            tien = tien // 1000 * 1000
            old = next((r for r in mine if r["trang_thai"] == "tao" and r.get("loai") == "vang" and r["tien"] == tien), None)
            if old:
                return {"ok": True, "don": old, **donate_qr(old)}
            if sum(1 for r in mine if r["trang_thai"] in ("tao", "cho")) >= 5:
                raise ValueError("Bạn đang có 5 yêu cầu chưa xong. Chờ admin duyệt hoặc nhắn admin.")
            rid = "".join(secrets.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(4))
            v, pct = vang.vang_for(tien, vc)
            don = {"id": rid, "ten": name, "loai": "vang", "bac": "Nạp Vàng", "goi": f"{v} Vàng" + (f" (thưởng {pct}%)" if pct else ""),
                   "thang": 0, "tien": tien, "vang": v, "noi_dung": f"DMM {re.sub(r'[^A-Za-z0-9]', '', name).upper()} V {rid}",
                   "trang_thai": "tao", "tao_luc": int(time.time() * 1000), "ip": ip}
            rows.append(don)
            donate_save(rows)
            return {"ok": True, "don": don, **donate_qr(don)}
        if act == "tao":
            tiers = donate_tiers()
            ti, gi = int(data.get("bac", -1)), int(data.get("goi", -1))
            if not (0 <= ti < len(tiers)) or not any(g["id"] == gi for g in tiers[ti]["goi"]):
                raise ValueError("Gói không hợp lệ")
            g = next(g for g in tiers[ti]["goi"] if g["id"] == gi)
            # dùng lại mã chưa báo của đúng gói này (bấm lại không sinh mã mới mỗi lần)
            old = next((r for r in mine if r["trang_thai"] == "tao" and r.get("group") == tiers[ti]["group"] and r["thang"] == g["thang"]), None)   # đơn nạp Vàng không có "group"
            if old:
                return {"ok": True, "don": old, **donate_qr(old)}
            if sum(1 for r in mine if r["trang_thai"] in ("tao", "cho")) >= 5:
                raise ValueError("Bạn đang có 5 yêu cầu chưa xong. Chờ admin duyệt hoặc nhắn admin.")
            rid = "".join(secrets.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(4))
            don = {"id": rid, "ten": name, "group": tiers[ti]["group"], "bac": tiers[ti]["ten"], "goi": g["ten"], "thang": g["thang"],
                   "tien": g["tien"], "noi_dung": donate_code(name, ti, g["thang"], rid), "trang_thai": "tao",
                   "tao_luc": int(time.time() * 1000), "ip": ip}
            rows.append(don)
            donate_save(rows)
            return {"ok": True, "don": don, **donate_qr(don)}
        if act == "bao":
            don = next((r for r in mine if r["id"] == str(data.get("id", ""))), None)
            if not don or don["trang_thai"] != "tao":
                raise ValueError("Không có yêu cầu này, hoặc đã báo rồi.")
            don.update(trang_thai="cho", bao_luc=int(time.time() * 1000))
            donate_save(rows)
    if act != "bao":
        raise ValueError("Thao tác không hợp lệ")
    msg = f"{name} báo đã chuyển {don['tien']:,}đ cho {don['bac']} {don['goi']} (nội dung: {don['noi_dung']}). Kiểm tra tài khoản rồi duyệt (cộng Vàng) ở trang quản trị > Donate.".replace(",", ".")
    threading.Thread(target=notify_admins, args=(msg,), daemon=True).start()
    web_log({"viec": "donate-bao", "nguoi": name, "don": don["id"], "ip": ip})
    return {"ok": True, "don": don, "message": "Đã báo admin. Admin kiểm tra tiền đã về rồi cộng Vàng (thường trong ngày)."}


def media_json():
    """Trang /media: đọc media.json do DuckyMoMoCore (MediaProgram) ghi ở Bang Hội Chiến."""
    f = DATA_DIR / "banghoi-1" / "plugins" / "DuckyMoMoCore" / "media.json"
    try:
        return json.loads(f.read_text(encoding="utf-8"))
    except Exception:
        return {"nguoi": [], "bao_cao": [], "chi_tieu": {}, "loi": "Chưa có dữ liệu (Bang Hội Chiến chưa bật Media hoặc đang tắt)."}


def media_admin(data, actor):
    """duyet | giahan | go | tuchoi <tên>: chạy /media ... ở Bang Hội Chiến qua RCON (plugin cấp/gỡ rank LuckPerms có hạn)."""
    act, ten = str(data.get("action", "")), str(data.get("ten", "")).strip()
    if act not in ("duyet", "giahan", "go", "tuchoi"):
        raise ValueError("Thao tác không hợp lệ")
    if not PLAYER_RE.match(ten):
        raise ValueError("Tên không hợp lệ")
    res = rcon("banghoi-1", [f"media {act} {ten}"])[0][1]
    web_log({"viec": "/api/media", "nguoi": actor or PANEL_USER, "hanh_dong": act, "doi_tuong": ten, "ok": True})
    return {"ok": True, "message": re.sub(r"§.", "", res).strip() or "Đã gửi lệnh."}


def vang_notify(name, text):
    """Báo người chơi (nếu đang online) một dòng về Vàng."""
    try:
        srv = online_players().get(name.lower())
        if srv:
            raw = ["", {"text": "[Vàng] ", "color": "gold"}, {"text": text, "color": "yellow"}]
            rcon(srv, [f"tellraw {name} " + json.dumps(raw, ensure_ascii=False)])
    except Exception:
        pass


def vang_run(cmds):
    """Chạy lệnh cấp đồ mua bằng Vàng ở sảnh (LuckPerms dùng chung cả mạng). Lỗi RCON -> ném lỗi để hoàn Vàng."""
    res = rcon("lobby", cmds)
    bad = [r for r in res if re.search(r"(?i)unknown command|không tìm thấy|could not|error", str(r[1]))]
    if bad:
        raise RuntimeError(re.sub(r"§.", "", str(bad[0][1]))[:120])


_vang_last = {}


def web_vang(data, ip):
    """Mua bằng Vàng (trang tài khoản / lệnh /vang trong game, tên lấy từ phiên hoặc từ plugin)."""
    name = str(data.get("ten", "")).strip()
    if not PLAYER_RE.match(name):
        raise ValueError("Tên không hợp lệ")
    if data.get("viec") != "mua":
        raise ValueError("Thao tác không hợp lệ")
    now = time.time()
    if now - _vang_last.get(name.lower(), 0) < 2:
        raise ValueError("Bấm chậm lại một chút.")
    _vang_last[name.lower()] = now
    res = vang.buy(name, str(data.get("ma", "")), donate_tiers, vang_run)
    web_log({"viec": "vang-mua", "nguoi": name, "ma": data.get("ma"), "ip": ip, "ok": True})
    threading.Thread(target=vang_notify, args=(name, res["message"]), daemon=True).start()
    return res


def vang_info(name):
    c = vang.cfg()
    return {"ok": True, **vang.balance(name), "lich_su": vang.history(name, 15), "mon": vang.shop(donate_tiers),
            "ty_gia": c["ty_gia"], "thuong": [{"tu": a, "pct": b} for a, b in c["thuong"]], "nap_toi_thieu": c["nap_toi_thieu"]}


VANG_PERM_RE = re.compile(r"^lp user \{player\} permission (?:settemp (\S+) true \d+d accumulate|set (\S+) true)$")
VANG_MA_RE = re.compile(r"^[a-z0-9_]{1,32}$")
VANG_FILES = [DATA_DIR / "banghoi-1" / "plugins" / "DuckyMoMoCore" / "config.yml",
              MODES_DIR / "banghoi" / "server" / "plugins" / "DuckyMoMoCore" / "config.yml"]


def vang_mon_list():
    """Món bán lẻ cho trình sửa: tách lệnh về dạng quyền (lệnh khớp mẫu lp ... permission) + lệnh tuỳ chỉnh."""
    out = []
    for m in vang.cfg()["mon"]:
        if not isinstance(m, dict):
            continue
        quyen, them = [], []
        for c in m.get("lenh") or []:
            mm = VANG_PERM_RE.match(str(c).strip())
            if mm:
                quyen.append(mm.group(1) or mm.group(2))
            else:
                them.append(str(c))
        out.append({"ma": str(m.get("ma", "")), "nhom": str(m.get("nhom", "Trang trí")), "ten": str(m.get("ten", "")),
                    "mo_ta": str(m.get("mo-ta", "")), "gia": int(m.get("gia", 1) or 1), "ngay": int(m.get("ngay", 0) or 0),
                    "quyen": quyen, "lenh_them": them})
    return out


def vang_mon_save(items, full):
    """Kiểm từng món rồi ghi vang.mon vào config Bang Hội Chiến (data + repo, giữ comment). Lệnh tuỳ chỉnh chạy bằng console
    -> chỉ tài khoản toàn quyền được đổi; quyền nguy hiểm (*, luckperms, web, admin) bị chặn."""
    if not isinstance(items, list) or len(items) > 200:
        raise ValueError("Danh sách món không hợp lệ")
    old_extra = {m["ma"]: m["lenh_them"] for m in vang_mon_list()}
    seen, out = set(), []
    for i, m in enumerate(items, 1):
        ma = str(m.get("ma", "")).strip().lower()
        if not VANG_MA_RE.match(ma) or ma in seen:
            raise ValueError(f"Món {i}: mã chỉ gồm chữ thường, số, _ và không trùng ({ma or 'trống'}).")
        seen.add(ma)
        ten = str(m.get("ten", "")).strip()[:60]
        if not ten:
            raise ValueError(f"Món {ma}: thiếu tên.")
        try:
            gia, ngay = int(m.get("gia", 0)), int(m.get("ngay", 0))
        except (TypeError, ValueError):
            raise ValueError(f"Món {ma}: giá / số ngày phải là số.")
        if not 1 <= gia <= 1_000_000 or not 0 <= ngay <= 3650:
            raise ValueError(f"Món {ma}: giá 1..1.000.000 Vàng, số ngày 0..3650 (0 = vĩnh viễn).")
        quyen = [str(q).strip() for q in m.get("quyen") or [] if str(q).strip()]
        for q in quyen:
            if not PERM_RE.match(q) or PROTECTED_PERM_RE.match(q) or q.lower().startswith(("duckymomo.rank.admin", "minecraft.command.op")):
                raise ValueError(f"Món {ma}: quyền không cho phép bán: {q}")
        them = [str(c).strip() for c in m.get("lenh_them") or [] if str(c).strip()]
        if them != old_extra.get(ma, []) and not full:
            raise ValueError(f"Món {ma}: chỉ tài khoản toàn quyền được sửa lệnh tuỳ chỉnh (chạy bằng quyền console).")
        if any(len(c) > 300 or "\n" in c for c in them):
            raise ValueError(f"Món {ma}: lệnh tuỳ chỉnh quá dài.")
        if not quyen and not them:
            raise ValueError(f"Món {ma}: cần ít nhất một quyền hoặc lệnh để cấp.")
        lenh = [f"lp user {{player}} permission settemp {q} true {ngay}d accumulate" if ngay else f"lp user {{player}} permission set {q} true"
                for q in quyen] + them
        out.append({"ma": ma, "nhom": str(m.get("nhom", "")).strip()[:30] or "Trang trí", "ten": ten,
                    "mo-ta": str(m.get("mo_ta", "")).strip()[:120], "gia": gia, "ngay": ngay, "lenh": lenh})
    # Chỉ thay khối `  mon:` trong mục `vang:` bằng văn bản (giữ nguyên từng ký tự phần còn lại của file, không để ruamel định dạng lại)
    q = lambda v: json.dumps(v, ensure_ascii=False)
    block = "  mon:\n" + "".join(
        f"    - {{ma: {m['ma']}, nhom: {q(m['nhom'])}, ten: {q(m['ten'])}, mo-ta: {q(m['mo-ta'])}, gia: {m['gia']}, ngay: {m['ngay']},\n"
        f"       lenh: [{', '.join(q(c) for c in m['lenh'])}]}}\n" for m in out)
    for f in VANG_FILES:
        if not f.exists():
            continue
        txt = f.read_text(encoding="utf-8")
        mv = re.search(r"(?m)^vang:\n", txt)
        if not mv:
            raise ValueError(f"Không thấy mục vang: trong {f}")
        mm = re.compile(r"(?m)^  mon:[^\n]*\n(?:(?:    |\s*#|\s*$)[^\n]*\n)*").search(txt, mv.end())
        new = txt[:mm.start()] + block + txt[mm.end():] if mm else txt[:mv.end()] + block + txt[mv.end():]
        from ruamel.yaml import YAML
        if (YAML(typ="safe").load(new) or {}).get("vang", {}).get("mon") != out:
            raise ValueError("Ghi cửa hàng lỗi (kiểm tra lại file cấu hình), chưa lưu gì.")
        tmp = f.with_suffix(".yml.tmp")
        tmp.write_text(new, encoding="utf-8")
        tmp.replace(f)
    return len(out)


def vang_admin(data, actor, full=True):
    """Trang /donate: tra ví (xem) | cộng/trừ Vàng tay (dieuchinh) | mon_ds / mon_luu: sửa món bán lẻ."""
    if data.get("action") == "mon_ds":
        return {"ok": True, "mon": vang_mon_list(), "toan_quyen": full}
    if data.get("action") == "mon_luu":
        n = vang_mon_save(data.get("mon"), full)
        web_log({"viec": "vang-mon", "nguoi": actor or PANEL_USER, "so_mon": n, "ok": True})
        return {"ok": True, "message": f"Đã lưu {n} món. Cửa hàng Vàng (web, /vang) đổi ngay.", "mon": vang_mon_list(), "toan_quyen": full}
    name = str(data.get("ten", "")).strip()
    if not PLAYER_RE.match(name):
        raise ValueError("Tên không hợp lệ")
    if data.get("action") == "dieuchinh":
        so = int(data.get("so", 0))
        ly_do = str(data.get("ly_do", "")).strip()
        if not so or not ly_do:
            raise ValueError("Nhập số Vàng (âm = trừ) và lý do.")
        after = vang.change(name, so, "admin", "Admin: " + ly_do, boi=actor or PANEL_USER)
        web_log({"viec": "vang-dieuchinh", "nguoi": actor or PANEL_USER, "doi_tuong": name, "so": so, "ly_do": ly_do, "ok": True})
        threading.Thread(target=vang_notify, args=(name, f"Admin {'cộng' if so > 0 else 'trừ'} {abs(so)} Vàng: {ly_do}"), daemon=True).start()
        return {"ok": True, "message": f"Đã {'cộng' if so > 0 else 'trừ'} {abs(so)} Vàng cho {name}. Số dư: {after}.", **vang_info(name)}
    return vang_info(name)


def donate_admin(data, actor):
    """Trang /donate: cap (chạy /rank give rồi đánh dấu đã cấp) | xong (đã cấp tay) | huy."""
    act, rid = data.get("action"), str(data.get("id", ""))
    with _donate_lock:
        rows = donate_load()
        don = next((r for r in rows if r["id"] == rid), None)
        if not don:
            raise ValueError("Không có yêu cầu này")
        if act == "cap":
            # 20/10: duyệt = CỘNG VÀNG theo số tiền (cả yêu cầu kiểu cũ chọn gói rank); người chơi tự mua rank bằng Vàng
            if don["trang_thai"] == "da_cap":
                raise ValueError("Yêu cầu này đã duyệt rồi")
            v, pct = vang.vang_for(int(don["tien"]))
            after = vang.change(don["ten"], v, "nap", f"Nạp {int(don['tien']):,}đ".replace(",", ".") + (f" (thưởng {pct}%)" if pct else ""),
                                ref=f"nap:{rid}", boi=actor or PANEL_USER, nap=int(don["tien"]))
            don.update(trang_thai="da_cap", vang=v, ket_qua=(f"+{v} Vàng, số dư {after}" if after is not None else "đã cộng trước đó"))
            threading.Thread(target=vang_notify, args=(don["ten"], f"Đã cộng {v} Vàng vào tài khoản (nạp {int(don['tien']):,}đ). Gõ /vang để tiêu.".replace(",", ".")),
                             daemon=True).start()
        elif act == "xong":
            don["trang_thai"] = "da_cap"
        elif act == "huy":
            don["trang_thai"] = "huy"
        else:
            raise ValueError("Thao tác không hợp lệ")
        don.update(xu_ly_boi=actor or PANEL_USER, xu_ly_luc=int(time.time() * 1000))
        donate_save(rows)
    return {"ok": True, "message": {"cap": "Đã duyệt: " + don.get("ket_qua", ""), "xong": "Đã đánh dấu xong (không cộng Vàng).", "huy": "Đã huỷ yêu cầu."}[act],
            "data": donate_load()[::-1]}


_cos_last = {}
_set_last = {}


def web_settings(data, ip):
    """Cài đặt cá nhân từ web: lp user <tên> meta set/unset dmmcaidat.<mã> qua RCON ở sảnh (LuckPerms đồng bộ mọi server + proxy)."""
    name = str(data.get("ten", "")).strip()
    if not PLAYER_RE.match(name):
        raise ValueError("Tên không hợp lệ")
    ma = str(data.get("ma", ""))
    if ma not in taikhoan.CAIDAT_IDS:
        raise ValueError("Cài đặt không có")
    now = time.time()
    if now - _set_last.get(name.lower(), 0) < 1:
        raise ValueError("Bấm chậm lại một chút.")
    _set_last[name.lower()] = now
    bat = bool(data.get("bat"))
    default = taikhoan.CAIDAT_IDS[ma][2]
    cmd = f"lp user {name} meta unset dmmcaidat.{ma}" if bat == default else f"lp user {name} meta set dmmcaidat.{ma} {'bat' if bat else 'tat'}"
    try:
        rcon("lobby", [cmd])
    except Exception:
        raise ValueError("Sảnh đang tắt, thử lại sau ít phút.")
    web_log({"viec": "caidat", "nguoi": name, "lenh": cmd, "ok": True, "ip": ip})
    return {"ok": True, "ma": ma, "bat": bat, "message": f"{taikhoan.CAIDAT_IDS[ma][1]}: {'Bật' if bat else 'Tắt'} (áp dụng ở mọi server)."}


# Tủ skin: plugin DuckyMoMoSkin trên proxy (Velocity) giữ tủ và gọi SkinsRestorer; panel chỉ chuyển tiếp + nhận ảnh PNG tải lên
# (lưu data/shared/skins/<sha1>.png, trang web công khai phục vụ ở /mc/web/skin/<sha1>.png để MineSkin tải về tạo skin).
SKIN_BRIDGE_URL = os.environ.get("SKIN_BRIDGE_URL", "http://proxy:8095")
SKIN_PUBLIC_BASE = os.environ.get("SKIN_PUBLIC_BASE", "https://app.goc6.io.vn/mc/web").rstrip("/")
SKIN_DIR = DATA_DIR / "shared" / "skins"
_skin_last = {}


def skin_bridge(method, body=None, name=None):
    url = SKIN_BRIDGE_URL + "/tuskin" + (f"?ten={urllib.parse.quote(name)}" if name else "")
    req = urllib.request.Request(url, data=json.dumps(body).encode("utf-8") if body is not None else None, method=method,
                                 headers={"X-DMM-Key": PANEL_SITE_KEY, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=28) as r:   # trang web chờ panel 30 giây
            return json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read() or b"{}")
        except ValueError:
            raise ValueError("Máy chủ skin trả lỗi, thử lại sau.")
    except (urllib.error.URLError, OSError, TimeoutError):
        raise ValueError("Máy chủ skin (proxy) đang khởi động hoặc tắt, thử lại sau ít phút.")


def skin_png(b64):
    """Ảnh skin tải lên: PNG thật, đúng cỡ skin Minecraft (64x64 hoặc 64x32), tối đa 48 KB. Trả đường dẫn công khai."""
    try:
        raw = base64.b64decode(str(b64).split(",", 1)[-1], validate=True)
    except Exception:
        raise ValueError("Ảnh không đọc được.")
    if len(raw) > 48 * 1024:
        raise ValueError("Ảnh quá lớn (tối đa 48 KB). Skin chuẩn chỉ vài KB.")
    if raw[:8] != b"\x89PNG\r\n\x1a\n" or raw[12:16] != b"IHDR":
        raise ValueError("Chỉ nhận ảnh PNG.")
    w, h = struct.unpack(">II", raw[16:24])
    if (w, h) not in ((64, 64), (64, 32)):
        raise ValueError(f"Ảnh skin phải 64x64 (hoặc 64x32 kiểu cũ), ảnh này {w}x{h}.")
    sha = hashlib.sha1(raw).hexdigest()
    SKIN_DIR.mkdir(parents=True, exist_ok=True)
    f = SKIN_DIR / f"{sha}.png"
    if not f.exists():
        f.write_bytes(raw)
    return f"{SKIN_PUBLIC_BASE}/skin/{sha}.png"


def web_skin(data, ip):
    name = str(data.get("ten", "")).strip()
    if not PLAYER_RE.match(name):
        raise ValueError("Tên không hợp lệ")
    viec = str(data.get("viec", ""))
    if viec not in ("them", "mac", "xoa"):
        raise ValueError("Việc không hợp lệ")
    now = time.time()
    if now - _skin_last.get(name.lower(), 0) < 3:
        raise ValueError("Bấm chậm lại một chút.")
    _skin_last[name.lower()] = now
    body = {"ten": name, "viec": viec, "so": int(data.get("so") or -1), "nhan": str(data.get("nhan", ""))[:24]}
    if viec == "them":
        body["nguon"] = skin_png(data["anh"]) if data.get("anh") else str(data.get("nguon", "")).strip()[:300]
    res = skin_bridge("POST", body)
    web_log({"viec": "skin-" + viec, "nguoi": name, "nguon": body.get("nguon", ""), "ok": bool(res.get("ok")), "ip": ip})
    return res


def web_cosmetic(data, ip):
    """Trang trí từ web: chạy lệnh THAY người chơi (/sudo của Essentials) ở server họ đang online (Bang Hội Chiến/Practice)."""
    name = str(data.get("ten", "")).strip()
    if not PLAYER_RE.match(name):
        raise ValueError("Tên không hợp lệ")
    now = time.time()
    if now - _cos_last.get(name.lower(), 0) < 2:
        raise ValueError("Bấm chậm lại một chút.")
    _cos_last[name.lower()] = now
    perms = taikhoan.load_perms(name)
    if not perms:
        raise ValueError("Chưa có dữ liệu tài khoản.")
    srv = online_players(fresh=True).get(name.lower())
    cmd = taikhoan.cosmetic_command(perms, data, srv if srv in taikhoan.COSMETIC_SERVERS else "banghoi-1")
    # hạt (PlayerParticles), danh hiệu: chỉ Bang Hội Chiến (Practice không cài PlayerParticles); đổi tên: cả Practice
    need = ["banghoi-1"] if data.get("viec", "").startswith(("danhhieu", "pp")) else taikhoan.COSMETIC_SERVERS
    if srv not in need:
        where = "Bang Hội Chiến" if need == ["banghoi-1"] else "Bang Hội Chiến hoặc Practice"
        return {"ok": False, "message": f"Bạn cần đang online ở {where} để áp dụng (sảnh không hỗ trợ). Vào game rồi bấm lại."}
    out = rcon(srv, [f"sudo {perms['name']} {cmd}"])[0][1]
    where = "Bang Hội Chiến" if srv == "banghoi-1" else "Practice"
    if "not found" in out.lower() or "không tìm" in out.lower():
        res = {"ok": False, "message": "Server chưa thấy bạn online, thử lại sau vài giây."}
    else:
        res = cosmetic_result(perms, srv, where, cmd, data)
    web_log({"viec": "trangtri", "nguoi": name, "lenh": cmd, "server": srv, "ok": res["ok"], "kq": res["message"][:150], "ip": ip})
    if cmd.startswith("pp"):
        time.sleep(0.8)  # PlayerParticles ghi SQLite bất đồng bộ
    res["trangtri"] = taikhoan.cosmetics(perms, srv)
    if cmd.startswith("pp") and res["trangtri"]["pp"]["dang"] is not None:
        # đọc lại được danh sách hiệu ứng: báo chắc chắn thay vì "đã gửi"
        have = {(x["hieu_ung"], x["kieu"]) for x in res["trangtri"]["pp"]["dang"]}
        if cmd.startswith("pp add"):
            want = (data.get("hieu_ung"), data.get("kieu"))
            res.update(ok=want in have, pending=False, message=f"Đã thêm hiệu ứng ở {where} ({len(have)}/{res['trangtri']['pp']['toi_da']})." if want in have
                       else f"Server KHÔNG thêm hiệu ứng (đang có {len(have)}/{res['trangtri']['pp']['toi_da']}). Đủ tối đa thì gỡ bớt trước; xem thêm dòng báo lỗi trong chat game.")
        elif cmd == "pp reset":
            res.update(ok=not have, pending=False, message="Đã tắt mọi hiệu ứng." if not have else "Server chưa tắt hiệu ứng, xem chat trong game.")
        elif cmd.startswith("pp remove"):
            gone = all(x["id"] != int(data.get("id") or 0) for x in res["trangtri"]["pp"]["dang"])
            res.update(ok=gone, pending=False, message="Đã gỡ hiệu ứng." if gone else "Server chưa gỡ hiệu ứng, xem chat trong game.")
    return res


def cosmetic_result(perms, srv, where, cmd, data):
    """Kiểm tra kết quả thật sau khi chạy lệnh thay người chơi (plugin chỉ báo lỗi trong chat game, RCON không thấy).
    Đổi tên: hỏi /whois. Danh hiệu: đọc bxh.yml. Hạt: không đọc được kết quả -> nói rõ là đã gửi."""
    plain = lambda s: re.sub(r"(?i)[&§]#[0-9a-f]{6}|[&§][0-9a-fk-or]", "", s or "")
    if cmd.startswith("nick"):
        time.sleep(0.8)
        try:
            who = rcon(srv, [f"whois {perms['name']}"])[0][1]
        except Exception:
            who = ""
        line = next((l for l in who.splitlines() if "nick" in l.lower() or "biệt danh" in l.lower()), "")   # Essentials tiếng Anh hoặc tiếng Việt
        now = plain(line.split(":", 1)[1]).strip() if ":" in line else ""
        if cmd == "nick off":
            if not now or now.lstrip("~").lower() == perms["name"].lower():
                return {"ok": True, "message": f"Đã trả về tên gốc ở {where}."}
            return {"ok": False, "message": f"Server chưa bỏ tên đổi (đang là {now}). Xem dòng báo lỗi trong chat game."}
        want = plain(str(data.get("gia_tri", ""))).strip()
        if want and now.endswith(want):
            return {"ok": True, "message": f"Đã đổi tên thành {now} ở {where}. Tên lưu riêng từng server."}
        if not line:
            return {"ok": False, "message": "Đã gửi nhưng không kiểm tra được kết quả (server không trả /whois). Xem chat trong game."}
        return {"ok": False, "message": f"Server KHÔNG nhận tên mới (tên hiện tại: {now or perms['name']}). Lý do hay gặp: tên quá dài "
                                         "(Essentials tính cả mã màu), trùng tên người khác, hoặc ký tự không cho phép. Xem dòng báo lỗi trong chat game."}
    if cmd.startswith("danhhieu"):
        time.sleep(0.6)
        dh = (taikhoan.safe_yaml(taikhoan.DMM / "bxh.yml").get("danhhieu") or {}).get(perms["uuid"]) or {}
        if cmd == "danhhieu tat":
            ok = not dh.get("dung")
            return {"ok": ok, "message": "Đã ẩn danh hiệu." if ok else "Server chưa ẩn danh hiệu, xem chat trong game."}
        ok = dh.get("dung") == data.get("ma")
        return {"ok": ok, "message": "Đã đổi danh hiệu, hiện sau tên trong chat." if ok else "Server chưa đổi danh hiệu, xem dòng báo lỗi trong chat game."}
    what = {"pp add": "thêm hiệu ứng", "pp reset": "tắt mọi hiệu ứng", "pp remove": "gỡ hiệu ứng"}
    act = next((v for k, v in what.items() if cmd.startswith(k)), "gửi lệnh")
    return {"ok": True, "pending": True,
            "message": f"Đã gửi lệnh {act} ở {where}. Plugin không báo kết quả về web: nếu không thấy thay đổi, xem dòng báo lỗi trong chat game "
                       + ("(vd đã đủ số hiệu ứng tối đa thì tắt bớt trước)." if cmd.startswith("pp add") else "(vd đang đánh nhau).")}


def account_times(name):
    try:
        con = taikhoan.db("network")
        try:
            cur = con.cursor()
            cur.execute("SELECT regdate, lastlogin FROM authme WHERE username = %s", (name.lower(),))
            row = cur.fetchone()
        finally:
            con.close()
        return {"dangky": int(row[0] or 0), "lancuoi": int(row[1] or 0)} if row else {}
    except Exception:
        return {}


def cmd_key(name):
    """'tpr [khu]' -> 'tpr', 'co lookup u:<người> t:<thời gian>' -> 'co lookup', '/co i' -> 'co i' (bỏ tham số, chữ thường)."""
    words = []
    for w in str(name).split("·")[0].split():
        if w[:1] in "<[(" or ":" in w or w.startswith("-"):
            break
        words.append(w)
    return " ".join(words).lstrip("/").lower()


def common_commands():
    """modes/_network/lenh-hay-dung.yml -> {tên lệnh: [tên nhóm, thứ tự]}"""
    try:
        doc = YAML(typ="safe").load((MODES_DIR / "_network" / "lenh-hay-dung.yml").read_text(encoding="utf-8")) or {}
    except Exception:
        return {}
    out, i = {}, 0
    for g in doc.get("nhom") or []:
        for c in g.get("lenh") or []:
            out.setdefault(str(c).lower().strip(), [str(g.get("ten", "")), i])
            i += 1
    return out


def play_rank(perms, roles):
    """Danh phận (play-ranks ở Bang Hội Chiến): bậc cao nhất người chơi đang có + bậc kế tiếp; Media (nếu có) kèm hạn."""
    core = taikhoan.safe_yaml(DATA_DIR / "banghoi-1" / "plugins" / "DuckyMoMoCore" / "config.yml")
    pr = core.get("play-ranks") or {}
    tiers = [(k, t) for k, t in (pr.get("tiers") or {}).items() if isinstance(t, dict)]
    if not pr.get("enabled") or not tiers:
        return None
    groups = set(perms.get("groups") or [])
    cur = 0
    for i, (k, t) in enumerate(tiers):
        if i > 0 and str(t.get("group", k)).lower() in groups:
            cur = i
    t = tiers[cur][1]
    nxt = tiers[cur + 1][1] if cur + 1 < len(tiers) else None
    md = next((r for r in roles if r["id"] == "media"), None)
    return {"ten": t.get("name", tiers[cur][0]), "nha": t.get("homes", 0), "balo": t.get("balo", 0), "o": t.get("balo-slots", 0),
            "cho": t.get("ah", 0), "don": t.get("orders", 0), "skin": t.get("tu-skin", 0),
            "tiep": {"ten": nxt.get("name"), "gio": nxt.get("hours", 0), "cap": nxt.get("level", 0),
                     "them": nxt.get("perm-text", "") if nxt.get("perm") else ""} if nxt else None,
            "media": {"het": md["het"]} if md else None}


def web_profile(name, by_code):
    """Hồ sơ cho trang "Tài khoản của tôi": role, rank, số liệu chơi, lệnh dùng được, quyền quản trị."""
    perms = taikhoan.load_perms(name)
    if not perms:
        return {"ok": False, "message": "Chưa có dữ liệu của tài khoản này: vào server ít nhất một lần."}
    acc = taikhoan.access(perms, all_categories())
    roles = []
    for d in perms["direct"]:
        g = d["group"]
        if g == "default":
            continue
        m = perms["meta"].get(g, {})
        roles.append({"id": g, "ten": m.get("display", g), "prefix": m.get("prefix", ""), "het": d["expiry"],
                      "server": d["server"], "loai": "donate" if g in DONATE_GROUPS else ("staff" if g in STAFF_GROUPS else "khac")})
    cmds = web_cached("lenh", 60, lambda: all_commands(YAML(typ="safe").load(CAMNANG_FILE.read_text(encoding="utf-8")) or {}))
    common = web_cached("hay-dung", 30, common_commands)
    hoso = taikhoan.profile(perms)
    roles_towny = set((hoso.get("towny") or {}).get("chuc", []))
    lenh = {}
    for srv in GAME_SERVERS + ["proxy"]:
        rows, seen = [], set()
        for r in (cmds.get(srv) or {}).get("commands", []):
            ok, tag = taikhoan.command_access(r, perms, None if srv == "proxy" else srv, roles_towny)
            if not ok or (r["plugin"], r["name"]) in seen:
                continue
            seen.add((r["plugin"], r["name"]))
            row = {"plugin": r["plugin"], "ten": r["name"], "goi_tat": r.get("aliases", [])[:4],
                   "mo_ta": r.get("desc_vi") or "", "nhan": tag}
            hit = common.get(cmd_key(r["name"])) or next((common[a.lower()] for a in r.get("aliases", []) if a.lower() in common), None)
            if hit:
                row["hay"] = hit  # [nhóm, thứ tự]
            rows.append(row)
        lenh[srv] = rows
    is_staff = bool(acc["features"]) or acc["toan_quyen"]
    return {"ok": True, "ten": perms["name"] if perms["name"].lower() == name.lower() else name, "bedrock": name.startswith("."),
            "taikhoan": account_times(name), "online": online_players().get(name.lower()),
            "roles": roles, "nhom": sorted(g for g in perms["groups"] if g != "default"), "danh_phan": play_rank(perms, roles),
            "hoso": hoso, "lenh": lenh,
            "trangtri": taikhoan.cosmetics(perms, online_players().get(name.lower()) if online_players().get(name.lower()) in taikhoan.COSMETIC_SERVERS else "banghoi-1"),
            "caidat": taikhoan.player_settings(perms),
            "quantri": {"la_staff": is_staff, "toan_quyen": acc["toan_quyen"], "qua_game": by_code,
                        "tinh_nang": list(acc["features"].values()), "loai_caidat": acc["categories"]}}


# Trang/API -> tính năng cần có (staff đăng nhập bằng tài khoản game)
PAGE_FEATURES = {"/": ("caidat",), "/thuvien": ("thuvien", "thuvien-dua"), "/boss": ("boss",), "/role": ("role", "role-quyen", "rank", "rank-quyen"),
                 "/hoatdong": ("hoatdong",), "/camnang": ("camnang",), "/huongdan": ("huongdan",),
                 "/donate": ("donate",), "/hethong": ("hethong",), "/media": ("media",), "/bot": ("bot",)}
API_GET_FEATURES = {"/api/modes": ("caidat",), "/api/camnang": ("camnang",), "/api/huongdan": ("huongdan",), "/api/roles": ("role", "role-quyen", "rank", "rank-quyen"),
                    "/api/boss": ("boss",), "/api/activity": ("hoatdong",), "/api/donate": ("donate",), "/api/hethong": ("hethong",), "/api/media": ("media",), "/api/bot": ("bot",), "/api/thuvien": None, "/api/toi": None}
# Staff đăng nhập bằng tài khoản game chỉ thao tác được CẤP DƯỚI: role chức vụ có weight nhỏ hơn role chức vụ cao nhất của mình
# (Manager 90: Mod, Helper, Builder, Dân; Mod 70: Helper, Builder, Dân…). Rank donate theo quyền riêng (web.rank / web.rank.quyen).
# Không ai ngoài toàn quyền cấp được * hay quyền web/admin/LuckPerms, và chỉ cấp được quyền chính mình đang có.
PROTECTED_PERM_RE = re.compile(r"^(\*|duckymomo\.web.*|duckymomo\.admin.*|luckperms.*)$")


# ---------------------------------------------------------------- HTTP

class Handler(BaseHTTPRequestHandler):
    server_version = "DuckyMoMoPanel"

    def log_message(self, fmt, *args):
        sys.stdout.write("%s %s\n" % (self.address_string(), fmt % args))

    def client_ip(self):
        return (self.headers.get("CF-Connecting-IP") or self.headers.get("X-Forwarded-For", "").split(",")[0].strip()
                or self.client_address[0])

    def session_token(self):
        for part in self.headers.get("Cookie", "").split(";"):
            k, _, v = part.strip().partition("=")
            if k == "dmm_panel":
                return v
        return ""

    def cookie_path(self):
        p = self.headers.get("X-Forwarded-Prefix", "") or "/"
        return p if re.fullmatch(r"/[A-Za-z0-9/_-]*", p) else "/"

    def login(self):
        """POST /dangnhap {ten, matkhau}: đúng PANEL_USER/PANEL_PASSWORD thì cấp cookie phiên (HttpOnly, SameSite=Strict)."""
        ip, now = self.client_ip(), time.time()
        with LOGIN_LOCK:
            fails = [t for t in LOGIN_FAILS.get(ip, []) if now - t < 600]
            LOGIN_FAILS[ip] = fails
        if len(fails) >= 5:
            return self.send_json(429, {"ok": False, "message": "Sai quá nhiều lần. Thử lại sau 10 phút."})
        try:
            d = self.read_body(4096)
        except ValueError:
            d = {}
        user, pw = str(d.get("ten", "")), str(d.get("matkhau", ""))
        if not (hmac.compare_digest(user.encode(), PANEL_USER.encode()) and hmac.compare_digest(pw.encode(), PANEL_PASSWORD.encode())):
            with LOGIN_LOCK:
                LOGIN_FAILS.setdefault(ip, []).append(now)
            sys.stdout.write(f"Đăng nhập quản trị SAI từ {ip}\n")
            time.sleep(1)
            return self.send_json(401, {"ok": False, "message": "Sai tên đăng nhập hoặc mật khẩu."})
        tok = secrets.token_urlsafe(32)
        with LOGIN_LOCK:
            for k in [k for k, exp in SESSIONS.items() if exp < now]:
                del SESSIONS[k]
            SESSIONS[_tok_hash(tok)] = now + panel_hours() * 3600
            LOGIN_FAILS.pop(ip, None)
            _save_sessions()
        secure = "; Secure" if self.headers.get("X-Forwarded-Proto", "") == "https" else ""
        body = json.dumps({"ok": True}).encode("utf-8")
        self.send_response(200)
        self.send_header("Set-Cookie", f"dmm_panel={tok}; Path={self.cookie_path()}; Max-Age={panel_hours() * 3600}; HttpOnly; SameSite=Strict{secure}")
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def logout(self):
        with LOGIN_LOCK:
            SESSIONS.pop(_tok_hash(self.session_token()), None)
            _save_sessions()
        body = json.dumps({"ok": True}).encode("utf-8")
        self.send_response(200)
        self.send_header("Set-Cookie", f"dmm_panel=; Path={self.cookie_path()}; Max-Age=0; HttpOnly; SameSite=Strict")
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        tok = self.session_token()
        if tok:
            with LOGIN_LOCK:
                exp = SESSIONS.get(_tok_hash(tok), 0)
            if exp > time.time():
                return True
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
        """Hai kiểu: PANEL_USER (Basic auth, toàn quyền) hoặc trang web công khai (khoá PANEL_SITE_KEY):
        không kèm người chơi = chỉ gọi /api/web/*; kèm X-DMM-Player = staff, quyền tính lại từ LuckPerms mỗi lần."""
        self.who, self.site = None, False
        key = self.headers.get("X-DMM-Key", "")
        if key:
            if not PANEL_SITE_KEY or not hmac.compare_digest(key.encode(), PANEL_SITE_KEY.encode()):
                self.send_json(403, {"ok": False, "message": "Sai khoá"})
                return False
            self.site = True
            name = self.headers.get("X-DMM-Player", "")
            if not name:
                return True
            try:
                perms = taikhoan.load_perms(name) if PLAYER_RE.match(name) else None
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Không đọc được quyền: {e}"})
                return False
            acc = taikhoan.access(perms, all_categories()) if perms else None
            if not acc or not (acc["features"] or acc["toan_quyen"]):
                self.send_json(403, {"ok": False, "message": "Tài khoản này không có quyền quản trị."})
                return False
            self.who = {"name": name, "acc": acc, "perms": perms, "weight": staff_weight(perms)}
            return True
        if self.authorized():
            return True
        # Chưa đăng nhập: trang HTML -> form đăng nhập; API -> 401 JSON. KHÔNG gửi WWW-Authenticate (trình duyệt sẽ bật hộp thoại).
        path = self.path.split("?", 1)[0]
        if self.command == "GET" and not path.startswith("/api/"):
            body = LOGIN_HTML.encode("utf-8")
            self.send_response(401)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_json(401, {"ok": False, "dang_nhap": True, "message": "Phiên đăng nhập đã hết. Tải lại trang để đăng nhập."})
        return False

    def role_rights(self, group):
        """(cấp/gỡ người, sửa quyền) với một role/rank. Staff: chỉ role có weight nhỏ hơn mình; rank donate: quyền riêng."""
        if self.who is None or self.who["acc"]["toan_quyen"]:
            return True, True
        if group in DONATE_GROUPS:
            return self.can("rank"), self.can("rank-quyen")
        if group in PLAY_GROUPS:
            return False, self.can("role-quyen")
        if group not in STAFF_GROUPS or group_weights().get(group, 0) >= self.who["weight"]:
            return False, False
        return self.can("role") and group != "default", self.can("role-quyen")

    def can(self, *features):
        if self.who is None:
            return True
        acc = self.who["acc"]
        return acc["toan_quyen"] or any(f in acc["features"] for f in features)

    def deny(self, msg="Tài khoản của bạn không có quyền làm việc này."):
        self.send_json(403, {"ok": False, "message": msg})

    def read_body(self, limit=65536):
        length = int(self.headers.get("Content-Length", "0"))
        if length > limit:
            raise ValueError("Dữ liệu quá lớn")
        return json.loads(self.rfile.read(length) or b"{}")

    def web_internal(self, method, path, query):
        """/api/web/*: chỉ trang web công khai gọi (khoá, không kèm người chơi)."""
        if not self.site or self.who is not None:
            self.send_json(404, {"error": "không có"})
            return
        try:
            if method == "POST" and path == "/api/web/xacminh":
                self.send_json(200, web_login(self.read_body(4096), self.headers.get("X-DMM-Ip", "")))
            elif method == "POST" and path == "/api/web/donate":
                self.send_json(200, web_donate(self.read_body(4096), self.headers.get("X-DMM-Ip", "")))
            elif method == "POST" and path == "/api/web/caidat":
                self.send_json(200, web_settings(self.read_body(4096), self.headers.get("X-DMM-Ip", "")))
            elif method == "POST" and path == "/api/web/trangtri":
                self.send_json(200, web_cosmetic(self.read_body(4096), self.headers.get("X-DMM-Ip", "")))
            elif method == "POST" and path == "/api/web/vang":
                self.send_json(200, web_vang(self.read_body(4096), self.headers.get("X-DMM-Ip", "")))
            elif method == "GET" and path == "/api/web/vang-ds":
                q = {k: v[-1] for k, v in urllib.parse.parse_qs(query).items()}
                self.send_json(200, {"ok": True, "so_du": vang.balances([x for x in q.get("ten", "").split(",") if x])})
            elif method == "GET" and path == "/api/web/vang":
                q = {k: v[-1] for k, v in urllib.parse.parse_qs(query).items()}
                if not PLAYER_RE.match(q.get("ten", "")):
                    raise ValueError("Tên không hợp lệ")
                self.send_json(200, vang_info(q["ten"]))
            elif method == "POST" and path == "/api/web/skin":
                self.send_json(200, web_skin(self.read_body(80_000), self.headers.get("X-DMM-Ip", "")))
            elif method == "GET" and path == "/api/web/skin":
                q = {k: v[-1] for k, v in urllib.parse.parse_qs(query).items()}
                if not PLAYER_RE.match(q.get("ten", "")):
                    raise ValueError("Tên không hợp lệ")
                self.send_json(200, skin_bridge("GET", name=q["ten"]))
            elif method == "POST" and path == "/api/web/choilai":
                self.send_json(200, web_reset(self.read_body(4096), self.headers.get("X-DMM-Ip", "")))
            elif method == "GET" and path == "/api/web/hoso":
                q = {k: v[-1] for k, v in urllib.parse.parse_qs(query).items()}
                name = q.get("ten", "")
                if not PLAYER_RE.match(name):
                    raise ValueError("Tên không hợp lệ")
                self.send_json(200, web_profile(name, q.get("qua_game") == "1"))
            else:
                self.send_json(404, {"error": "không có"})
        except ValueError as e:
            self.send_json(400, {"ok": False, "message": str(e)})
        except Exception as e:
            self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})

    def do_GET(self):
        if not self.guard():
            return
        path, _, query = self.path.partition("?")
        if path.startswith("/api/web/") or (self.site and self.who is None):
            return self.web_internal("GET", path, query)
        if self.who is not None:
            need = PAGE_FEATURES.get(path) or API_GET_FEATURES.get(path, ())
            if path == "/" and not self.can("caidat"):
                first = next((f["trang"] for f in self.who["acc"]["features"].values() if f.get("trang")), None)
                if first:
                    self.send_response(302)
                    self.send_header("Location", first)
                    self.end_headers()
                    return
            if need and not self.can(*need):
                return self.deny()
        if path == "/api/toi":
            acc = self.who["acc"] if self.who else None
            return self.send_json(200, {"ten": self.who["name"] if self.who else PANEL_USER, "game": self.who is not None,
                                        "toan_quyen": acc["toan_quyen"] if acc else True,
                                        "tinh_nang": list(acc["features"]) if acc else None,
                                        # role không được cấp/gỡ/sửa quyền (trang Role ẩn nút, máy chủ vẫn chặn ở scoped_post_ok)
                                        # role/rank nào được cấp/gỡ người, sửa quyền (None = tất cả); trang Role ẩn phần còn lại
                                        "role_quyen": None if not acc or acc["toan_quyen"] else
                                        {g: {"nguoi": self.role_rights(g)[0], "quyen": self.role_rights(g)[1]}
                                         for g in STAFF_GROUPS + PLAY_GROUPS + DONATE_GROUPS}})
        if path in ("/", "/hoatdong", "/camnang", "/huongdan", "/role", "/thuvien", "/boss", "/donate", "/hethong", "/media", "/bot"):
            dozzle = DOZZLE_PROXY_URL if self.headers.get("X-Forwarded-Prefix") else DOZZLE_URL
            page = {"/": INDEX_HTML, "/hoatdong": ACTIVITY_HTML, "/camnang": CAMNANG_HTML, "/huongdan": HUONGDAN_HTML, "/role": ROLE_HTML,
                    "/thuvien": THUVIEN_HTML, "/boss": BOSS_HTML, "/donate": DONATE_HTML, "/hethong": HETHONG_HTML, "/media": MEDIA_HTML, "/bot": BOT_HTML}[path]
            body = page.replace("__DOZZLE_URL__", "" if self.who else dozzle).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif path in STATIC:
            ctype, body = STATIC[path]
            self.send_response(200)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write(body)
        elif path == "/api/modes":
            scoped = self.who is not None and not self.who["acc"]["toan_quyen"]
            self.send_json(200, modes_json(self.who["acc"]["categories"] if scoped else None))
        elif path == "/api/camnang":
            self.send_json(200, camnang_json())
        elif path == "/api/huongdan":
            self.send_json(200, huongdan_json())
        elif path == "/api/roles":
            try:
                self.send_json(200, roles_json())
            except Exception as e:
                self.send_json(500, {"error": f"Không đọc được LuckPerms: {e}"})
        elif path == "/api/thuvien":
            self.send_json(200, thuvien_json())
        elif path == "/api/boss":
            self.send_json(200, boss_json())
        elif path == "/api/donate":
            self.send_json(200, {"rows": donate_load()[::-1]})
        elif path == "/api/hethong":
            self.send_json(200, hethong_status())
        elif path == "/api/media":
            self.send_json(200, media_json())
        elif path == "/api/bot":
            self.send_json(200, bot_json())
        elif path == "/api/activity":
            params = {k: v[-1] for k, v in urllib.parse.parse_qs(query).items()}
            self.send_json(200, read_activity(params))
        else:
            self.send_json(404, {"error": "không có"})

    def scoped_post_ok(self):
        """Staff đăng nhập bằng tài khoản game: kiểm tra quyền từng thao tác ghi, ghi nhật ký web-thaotac.jsonl."""
        acc, p, b = self.who["acc"], self.path, self.body
        full = acc["toan_quyen"]
        if p == "/api/roles":
            grp = str(b.get("group", ""))
            nguoi, quyen = self.role_rights(grp)
            if b.get("action") in ("preset", "user_get", "user_set", "user_unset"):
                ok = full   # quyền riêng từng người: chỉ tài khoản toàn quyền
            elif b.get("action") in ("perm_set", "perm_unset", "perm_deny"):
                node = str(b.get("node", "")).strip().lower()
                # bật quyền: chỉ quyền chính mình có (không tự nâng cấp dưới hơn mình)
                ok = full or (quyen and not PROTECTED_PERM_RE.match(node) and
                              (b.get("action") != "perm_set" or taikhoan.has(self.who["perms"], node)))
            else:
                ok = full or nguoi
        elif p == "/api/say":
            ok = self.can("hoatdong")
        elif p == "/api/boss":
            ok = self.can("boss")
        elif p == "/api/thuvien":
            ok = self.can("thuvien-dua" if b.get("action") == "give" else "thuvien")
        elif p == "/api/huongdan":
            ok = self.can("huongdan")
        elif p in ("/api/donate", "/api/vang"):
            ok = self.can("donate")
        elif p == "/api/hethong":
            ok = self.can("hethong")
        elif p == "/api/media":
            ok = self.can("media")
        elif p == "/api/bot":
            ok = self.can("bot")
        else:
            m = re.fullmatch(r"/api/modes/([a-z0-9_-]+)/(save|apply-all)", p)
            if m and m.group(2) == "save":
                keys = set((b.get("values") or {}).keys())
                ok = self.can("caidat") and (full or keys <= allowed_keys(m.group(1), acc["categories"]))
            else:
                ok = bool(m) and full
        web_log({"viec": p, "nguoi": self.who["name"], "hanh_dong": b.get("action"), "doi_tuong": b.get("id") or b.get("group") or b.get("player"),
                 "ok": ok})
        if not ok:
            self.deny()
        return ok

    def do_POST(self):
        if self.path == "/dangnhap" and not self.headers.get("X-DMM-Key"):
            return self.login()
        if self.path == "/dangxuat":
            return self.logout()
        if not self.guard():
            return
        if self.path.startswith("/api/web/") or (self.site and self.who is None):
            return self.web_internal("POST", self.path, "")
        try:
            self.body = self.read_body(4_000_000)
        except ValueError as e:
            return self.send_json(400, {"ok": False, "message": str(e) or "Dữ liệu không hợp lệ"})
        if self.who is not None and not self.scoped_post_ok():
            return
        if self.path == "/api/roles":
            try:
                self.send_json(200, role_action(self.body, None if self.who is None or self.who["acc"]["toan_quyen"] else self.who["weight"]))
            except ValueError as e:
                self.send_json(400, {"ok": False, "message": str(e)})
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})
            return
        if self.path == "/api/say":
            try:
                self.send_json(200, say(self.body, full=self.who is None or self.who["acc"]["toan_quyen"],
                                        who=self.who["name"] if self.who else None))
            except ValueError as e:
                self.send_json(400, {"ok": False, "message": str(e)})
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})
            return
        if self.path == "/api/boss":
            try:
                self.send_json(200, boss_action(self.body))
            except ValueError as e:
                self.send_json(400, {"ok": False, "message": str(e)})
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})
            return
        if self.path == "/api/thuvien":
            try:
                self.send_json(200, thuvien_action(self.body, self.who["name"] if self.who else None))
            except ValueError as e:
                self.send_json(400, {"ok": False, "message": str(e)})
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})
            return
        if self.path == "/api/vang":
            try:
                self.send_json(200, vang_admin(self.body, self.who["name"] if self.who else None,
                                               full=self.who is None or self.who["acc"]["toan_quyen"]))
            except ValueError as e:
                self.send_json(400, {"ok": False, "message": str(e)})
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})
            return
        if self.path == "/api/donate":
            try:
                self.send_json(200, donate_admin(self.body, self.who["name"] if self.who else None))
            except ValueError as e:
                self.send_json(400, {"ok": False, "message": str(e)})
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})
            return
        if self.path == "/api/bot":
            try:
                self.send_json(200, bot_action(self.body, self.who["name"] if self.who else None))
            except ValueError as e:
                self.send_json(400, {"ok": False, "message": str(e)})
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})
            return
        if self.path == "/api/media":
            try:
                self.send_json(200, media_admin(self.body, self.who["name"] if self.who else None))
            except ValueError as e:
                self.send_json(400, {"ok": False, "message": str(e)})
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})
            return
        if self.path == "/api/hethong":
            try:
                self.send_json(200, hethong_action(self.body, self.who["name"] if self.who else None))
            except ValueError as e:
                self.send_json(400, {"ok": False, "message": str(e)})
            except Exception as e:
                self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})
            return
        if self.path == "/api/huongdan":
            try:
                self.send_json(200, {"ok": True, "message": huongdan_save(self.body)})
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
            payload = self.body
            result = save_and_apply(m.group(1), payload.get("values", {}), apply_all=m.group(2) == "apply-all")
            self.send_json(200, result)
        except ValueError as e:
            self.send_json(400, {"ok": False, "message": str(e)})
        except Exception as e:
            self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})


if __name__ == "__main__":
    print("Trang Cài đặt chạy ở cổng 8080", flush=True)
    _load_sessions()
    try:
        vang.ensure_tables()
    except Exception as e:
        print(f"Vàng: không tạo được bảng ({e})", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
