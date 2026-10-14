"""Trang giới thiệu công khai của DuckyMoMo Network (site/public) + API chỉ đọc cho số liệu sống.

Đọc (read-only) file của server Bang Hội Chiến: cấu hình DuckyMoMoCore, settings.yml của trang Cài đặt, dữ liệu Hội Bàn Tròn
(an.yml), Hội đồng Hoà Bình (hoidong.yml), bảng xếp hạng (bxh.yml), tổ chức (tochuc.yml), Towny (flatfile). Hỏi proxy Velocity
bằng Server List Ping để biết số người online. Không trả UUID, mật khẩu, IP; không ghi gì vào server.

API: /api/info  /api/status  /api/bantron  /api/bxh  /api/vote   (kết quả giữ 30 giây)
"""
import json
import os
import socket
import struct
import threading
import time
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from zoneinfo import ZoneInfo

import yaml

DATA = Path(os.environ.get("DATA_DIR", "/data"))
MODES = Path(os.environ.get("MODES_DIR", "/repo/modes"))
PUBLIC = Path(__file__).parent / "public"
SITE_CFG = Path(__file__).parent / "config" / "site.yml"
SRV = os.environ.get("BANGHOI_SERVER", "banghoi-1")
PROXY = (os.environ.get("PROXY_HOST", "proxy"), int(os.environ.get("PROXY_PORT", "25577")))
VN = ZoneInfo("Asia/Ho_Chi_Minh")
DAYS_VI = {"MONDAY": "Thứ 2", "TUESDAY": "Thứ 3", "WEDNESDAY": "Thứ 4", "THURSDAY": "Thứ 5", "FRIDAY": "Thứ 6",
           "SATURDAY": "Thứ 7", "SUNDAY": "Chủ nhật"}
OPTIONS_VI = {"giu": "Giữ mức sàn", "phatnang": "Phạt nặng", "truyna": "Truy nã", "tu": "Tù", "luuday": "Lưu đày",
              "camct1": "Cấm công thành 1 tuần", "camct2": "Cấm công thành 2 tuần", "camct3": "Cấm công thành 3 tuần"}

_cache = {}
_lock = threading.Lock()


def cached(key, ttl, fn):
    now = time.time()
    with _lock:
        hit = _cache.get(key)
        if hit and now - hit[0] < ttl:
            return hit[1]
    val = fn()
    with _lock:
        _cache[key] = (now, val)
    return val


def load_yaml(path):
    try:
        with open(path, encoding="utf-8") as f:
            return yaml.safe_load(f) or {}
    except (OSError, yaml.YAMLError):
        return {}


def plugin(*parts):
    return DATA / SRV / "plugins" / Path(*parts)


def strip_colors(s):
    if not isinstance(s, str):
        return s
    out, i = [], 0
    while i < len(s):
        if s[i] in "&§" and i + 1 < len(s):
            i += 2
            continue
        out.append(s[i])
        i += 1
    return "".join(out)


def get(d, path, default=None):
    for p in path.split("."):
        if not isinstance(d, dict) or p not in d:
            return default
        d = d[p]
    return d


def fmt_ms(ms):
    if not ms:
        return None
    t = datetime.fromtimestamp(int(ms) / 1000, VN)
    day = "Chủ nhật" if t.weekday() == 6 else f"Thứ {t.weekday() + 2}"
    return f"{t:%H:%M} {day} {t:%d/%m}"


# ---------------------------------------------------------------- Towny (flatfile)

def towny_kv(path):
    out = {}
    try:
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            if "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip()
    except OSError:
        pass
    return out


def towny_world():
    base = plugin("Towny", "data")
    towns = {}
    for f in (base / "towns").glob("*.txt"):
        kv = towny_kv(f)
        towns[f.stem] = {"nation": kv.get("nation", ""), "neutral": kv.get("neutral", "false") == "true"}
    residents = 0
    for f in (base / "residents").glob("*.txt"):
        kv = towny_kv(f)
        if kv.get("town"):
            residents += 1
            t = towns.get(kv["town"])
            if t is not None:
                t["dan"] = t.get("dan", 0) + 1
    blocks = {}
    for f in (base / "townblocks").rglob("*.data"):
        kv = towny_kv(f)
        if kv.get("town"):
            blocks[kv["town"]] = blocks.get(kv["town"], 0) + 1
    for name, n in blocks.items():
        if name in towns:
            towns[name]["o"] = n
    nations = sorted({t["nation"] for t in towns.values() if t["nation"]})
    return towns, nations, residents


# ---------------------------------------------------------------- Server List Ping

def _varint(n):
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        out += struct.pack("B", b | (0x80 if n else 0))
        if not n:
            return out


def _read_varint(sock):
    n = shift = 0
    while True:
        b = sock.recv(1)
        if not b:
            raise ConnectionError
        b = b[0]
        n |= (b & 0x7F) << shift
        if not b & 0x80:
            return n
        shift += 7


def ping():
    host, port = PROXY
    try:
        with socket.create_connection((host, port), timeout=3) as s:
            hs = _varint(0) + _varint(767) + _varint(len(host)) + host.encode() + struct.pack(">H", port) + _varint(1)
            s.sendall(_varint(len(hs)) + hs)
            s.sendall(_varint(1) + _varint(0))
            _read_varint(s)
            _read_varint(s)
            length = _read_varint(s)
            data = b""
            while len(data) < length:
                chunk = s.recv(length - len(data))
                if not chunk:
                    break
                data += chunk
            js = json.loads(data.decode("utf-8", "replace"))
            pl = js.get("players", {})
            return {"online": True, "players": pl.get("online", 0), "max": pl.get("max", 0),
                    "version": strip_colors(js.get("version", {}).get("name", ""))}
    except (OSError, ValueError, ConnectionError):
        return {"online": False, "players": 0, "max": 0, "version": ""}


# ---------------------------------------------------------------- API

def api_info():
    core = load_yaml(plugin("DuckyMoMoCore", "config.yml"))
    st = load_yaml(MODES / "banghoi" / "settings.yml")
    sw = load_yaml(plugin("SiegeWar", "config.yml"))
    towny = load_yaml(plugin("Towny", "settings", "config.yml"))
    site = load_yaml(SITE_CFG)
    sched = get(sw, "battle_session_scheduler.start_times", {}) or {}
    siege_days = [{"day": DAYS_VI.get(k.upper(), k), "times": [t.strip() for t in str(v).split(",") if t.strip()]}
                  for k, v in sched.items() if str(v).strip()]
    order = list(DAYS_VI.values())
    siege_days.sort(key=lambda d: order.index(d["day"]) if d["day"] in order else 9)
    zones = [{"name": strip_colors(z.get("name", "")), "desc": z.get("description", ""), "id": z.get("id")}
             for z in get(core, "rtp.zones", []) or []]
    tiers = [{"name": t.get("name"), "color": t.get("color", "&f"), "perks": t.get("perks", []), "prices": t.get("prices", [])}
             for t in get(core, "donate-ranks.tiers", []) or []]
    titles = {k: strip_colors(v) for k, v in (get(core, "leaderboard.titles", {}) or {}).items()}
    return {
        "site": site,
        "season": {"id": get(core, "leaderboard.season", "1"), "start": get(core, "leaderboard.season-start", "") or None,
                   "weeks": 10, "double_week": get(core, "leaderboard.double-week", 10)},
        "siege": {
            "days": siege_days,
            "session_minutes": int(get(sw, "battle_session_scheduler.session_duration_minutes", 60)),
            "sessions": int(get(sw, "war.siege.times.duration_battle_sessions", 4)),
            "start_day": DAYS_VI.get(str(get(sw, "siege_start_day_limiter.allowed_siege_start_days", "thursday")).upper(), "Thứ 5"),
            "zone_radius": int(get(sw, "war.siege.distances.siegezone_radius_blocks", 200)),
            "warchest_per_plot": float(get(sw, "war.siege.money.warchest_cost_per_plot", 20)),
            "plunder_per_plot": float(get(sw, "war.siege.money.plunder.amount_per_plot", 40)),
            "immunity_hours": int(get(sw, "war.siege.times.siege_immunity_post_siege_hours", 168)),
            "enabled": str(get(sw, "war.siege.switches.enabled", "true")) == "true",
            "max_attacks": int(get(sw, "war.siege.quantities.max_active_siege_attacks_per_nation", 2)),
            "min_towns": get(core, "town-rules.siege-min-towns", 2),
        },
        "town": {
            "price": float(get(towny, "economy.new_expand.price_new_town", 500)),
            "nation_price": float(get(towny, "economy.new_expand.price_new_nation", 1000)),
            "claim_price": float(get(towny, "economy.new_expand.price_claim_townblock", 25)),
            "upkeep": float(get(towny, "economy.daily_taxes.price_town_upkeep", 10)),
            "plots_per_resident": int(get(towny, "claiming.town_block_ratio", 8) or 8),
            "min_hours": get(core, "town-rules.town-min-hours", 3),
            "nation_min_active": get(core, "town-rules.nation-min-active-residents", 3),
            "active_hours": get(core, "active-play.active-hours-per-week", 3),
            "join_cooldown": get(core, "town-rules.join-cooldown-days", 3),
            "homeblock_distance": 10,
            "spawn_ring": get(core, "town-rules.reserved-spawn-radius", 300),
        },
        "player": {
            "start_money": st.get("eco_starting_balance", 200),
            "pvp_wild": bool(get(core, "pvp-rules.wild-pvp", True)),
            "newbie_minutes": int(st.get("pvp_newbie_seconds", 900)) // 60,
            "death_keep_minutes": get(core, "item-cleaner.death-protect-minutes", 10),
            "rank_hours": get(core, "pvp-rules.min-rank-hours", 5),
            "max_accounts": get(core, "pvp-rules.max-accounts", 2),
            "tpr_cooldown": int(get(core, "rtp.cooldown-seconds", 300)) // 60,
            "tpr_zones": zones,
            "reset_days": get(core, "player-reset.cooldown-days", 7),
            "reset_max": get(core, "player-reset.max-per-season", 3),
        },
        "peace": {
            "join_wait": get(core, "peace-alliance.join-wait-days", 2),
            "rejoin": get(core, "peace-alliance.rejoin-cooldown-days", 14),
            "plot_base": get(core, "peace-alliance.plot-base-price", 25),
            "plot_step": get(core, "peace-alliance.plot-price-per-plot", 0.5),
            "fee": get(core, "peace-alliance.protection-fee-per-plot", 0.5),
            "sell_bonus": get(core, "peace-alliance.sell-bonus-percent", 20),
            "tax_outside": float(get(core, "peace-alliance.shop-tax-outside", 0.05)) * 100,
            "auction_only": bool(get(core, "peace-alliance.auction-only-in-peace", True)),
            "license_fee": get(core, "organizations.weekly-fee", 100),
            "slots": get(core, "organizations.slots", {}),
        },
        "justice": {
            "floor": get(core, "justice.floor-fine", 100),
            "buffer": get(core, "justice.buffer-blocks", 100),
            "self_defense": get(core, "justice.self-defense-seconds", 30),
            "bounty_days": get(core, "justice.bounty-days", 3),
            "jail_minutes": get(core, "justice.jail-minutes", 45),
            "exile_days": get(core, "justice.exile-days", 14),
            "seats": get(core, "justice.roundtable.seats", 5),
            "min_seats": get(core, "justice.roundtable.min-seats", 3),
            "day": DAYS_VI.get(str(get(core, "justice.roundtable.day", "WEDNESDAY")).upper(), "Thứ 4"),
            "time": get(core, "justice.roundtable.time", "21:00"),
            "radius": get(core, "justice.roundtable.table-radius", 16),
            "no_tp": get(core, "justice.roundtable.no-teleport-radius", 150),
        },
        "titles": titles,
        "donate": {"tiers": tiers, "how": strip_colors(get(core, "donate-ranks.how-to-buy", ""))},
    }


def api_status():
    towns, nations, residents = towny_world()
    peace_nation = get(load_yaml(plugin("DuckyMoMoCore", "config.yml")), "peace-alliance.nation", "LienMinhHoaBinh")
    peace_towns = [{"name": n, "dan": t.get("dan", 0), "o": t.get("o", 0)} for n, t in towns.items() if t["nation"] == peace_nation]
    return {"ping": ping(), "towns": len(towns), "nations": len([n for n in nations if n != peace_nation]),
            "residents": residents, "peace_towns": peace_towns, "time": datetime.now(VN).strftime("%H:%M %d/%m")}


def api_bantron():
    an = load_yaml(plugin("DuckyMoMoCore", "an.yml"))
    hd = load_yaml(plugin("DuckyMoMoCore", "hoidong.yml"))
    tc = load_yaml(plugin("DuckyMoMoCore", "tochuc.yml"))

    def votes(item):
        out = []
        for v in (item.get("phieu") or {}).values():
            parts = str(v).split(":", 1)
            out.append({"nguoi": parts[1] if len(parts) > 1 else "?", "chon": OPTIONS_VI.get(parts[0], parts[0])})
        return out

    cases = []
    for cid, v in (an.get("vu") or {}).items():
        cases.append({"id": cid, "ke": v.get("ke"), "nan": v.get("nan"), "luc": fmt_ms(v.get("luc")), "ms": v.get("luc", 0),
                      "trangthai": "Đang chờ xử" if v.get("trangthai") == "MO" else "Đã xử",
                      "san": v.get("san"), "phanquyet": v.get("ketqua-chu"), "phieu": votes(v), "molai": bool(v.get("mo-lai"))})
    motions = []
    kinds = {"TRUDIEM": "Trừ điểm Bá Chủ", "THUHOI": "Thu hồi giấy phép", "MOLAI": "Mở lại vụ án"}
    for mid, v in (an.get("kien") or {}).items():
        res = v.get("ketqua")
        motions.append({"id": mid, "loai": kinds.get(v.get("loai"), v.get("loai")), "muc": v.get("muc"), "giatri": v.get("giatri"),
                        "ly": v.get("ly"), "nguoi": v.get("nguoi"), "luc": fmt_ms(v.get("luc")), "ms": v.get("luc", 0),
                        "ketqua": None if res is None else ("Thông qua" if res else "Không thông qua"), "phieu": votes(v)})
    cases.sort(key=lambda x: x["ms"], reverse=True)
    motions.sort(key=lambda x: x["ms"], reverse=True)
    hop = an.get("hop") or {}
    rep = (hd.get("daidien") or {})
    bau = hd.get("bau") or {}
    orgs = []
    for oid, o in (tc.get("tc") or {}).items():
        if o.get("giayphep") not in ("GIAITAN",):
            orgs.append({"ten": o.get("ten"), "loai": "Thương Hội" if o.get("loai") == "thuonghoi" else "Tổ chức Sự kiện",
                         "giayphep": {"CO": "Có giấy phép", "CHO": "Chờ duyệt", "TREO": "Bị treo", "CHUA": "Chưa xin",
                                      "KHONG": "Không được duyệt", "THUHOI": "Bị thu hồi", "HETHAN": "Hết hạn"}.get(o.get("giayphep"), o.get("giayphep")),
                         "thanhvien": len(o.get("thanhvien") or [])})
    return {
        "hop": {"luc": fmt_ms(hop.get("luc")), "noi": hop.get("ban-thitran"), "dang_hop": hop.get("trangthai") == "MO"},
        "cases": cases[:30], "motions": motions[:20],
        "open_cases": len([c for c in cases if c["trangthai"] == "Đang chờ xử"]),
        "daidien": {"ten": rep.get("ten"), "den": fmt_ms(rep.get("den")), "tam": bool(rep.get("tam"))},
        "bau": {"mo": bool(bau.get("mo")), "dong": fmt_ms(bau.get("dong")), "ungvien": bau.get("ungvien") or []},
        "orgs": orgs,
    }


def api_bxh():
    b = load_yaml(plugin("DuckyMoMoCore", "bxh.yml"))
    nations = sorted(((v.get("ten"), v.get("diem", 0), v.get("tran", 0)) for v in (b.get("bachu") or {}).values()),
                     key=lambda x: (x[1], x[2]), reverse=True)
    warriors = sorted(((v.get("ten"), v.get("diem", 0)) for v in (b.get("chienthan") or {}).values()), key=lambda x: x[1], reverse=True)
    return {"mua": b.get("mua", "1"), "khoa": bool(b.get("chot")),
            "bachu": [{"ten": n, "diem": d, "tran": t} for n, d, t in nations[:10]],
            "chienthan": [{"ten": n, "diem": d} for n, d in warriors[:10]],
            "kientruc": len(get(b, "kts.dangky", []) or [])}


def api_vote():
    v = get(load_yaml(plugin("DuckyMoMoCore", "config.yml")), "vote", {}) or {}
    d = load_yaml(plugin("DuckyMoMoCore", "vote.yml"))
    month = datetime.now(VN).strftime("%Y-%m")
    top = sorted(((p.get("name"), p.get("month-votes", 0)) for p in (d.get("players") or {}).values()
                  if p.get("month") == month and p.get("month-votes", 0) > 0), key=lambda x: x[1], reverse=True)
    sites = [{"ten": s.get("name", k), "url": s["url"]} for k, s in (v.get("sites") or {}).items()
             if isinstance(s, dict) and str(s.get("url", "")).startswith("https://")]
    return {"bat": bool(v.get("enabled")), "xu": v.get("money-per-vote", 100), "chuoi_ngay": v.get("streak-days", 7),
            "chuoi_thuong": v.get("streak-bonus", 500), "party_moi": v.get("party-every", 30), "party_xu": v.get("party-money", 200),
            "party_dem": get(d, "party.count", 0) or 0, "trang": sites, "thang": datetime.now(VN).strftime("%m/%Y"),
            "top": [{"ten": n, "luot": c} for n, c in top[:10]]}


ROUTES = {
    "/api/info": lambda: cached("info", 30, api_info),
    "/api/status": lambda: cached("status", 20, api_status),
    "/api/bantron": lambda: cached("bantron", 20, api_bantron),
    "/api/bxh": lambda: cached("bxh", 30, api_bxh),
    "/api/vote": lambda: cached("vote", 30, api_vote),
}
TYPES = {".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".js": "application/javascript; charset=utf-8",
         ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp", ".svg": "image/svg+xml", ".ico": "image/x-icon",
         ".json": "application/json"}


class Handler(BaseHTTPRequestHandler):
    # Không lộ phần mềm/phiên bản máy chủ (trang công khai, tránh bị lần ngược về hạ tầng)
    server_version = "web"
    sys_version = ""

    def _send(self, code, body, ctype, cache="no-cache"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", cache)
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Robots-Tag", "noindex, nofollow")
        self.send_header("Referrer-Policy", "no-referrer")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path in ROUTES:
            try:
                body = json.dumps(ROUTES[path](), ensure_ascii=False).encode("utf-8")
                self._send(200, body, "application/json; charset=utf-8")
            except Exception as e:  # noqa: BLE001 - trang vẫn chạy khi một nguồn dữ liệu lỗi
                self._send(500, json.dumps({"error": str(e)}).encode(), "application/json")
            return
        rel = "index.html" if path in ("", "/") else path.lstrip("/")
        target = (PUBLIC / rel).resolve()
        if PUBLIC.resolve() not in target.parents and target != PUBLIC.resolve() or not target.is_file():
            self._send(404, "Không tìm thấy".encode(), "text/plain; charset=utf-8")
            return
        self._send(200, target.read_bytes(), TYPES.get(target.suffix, "application/octet-stream"), "public, max-age=300")

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    print("Trang giới thiệu DuckyMoMo: cổng 8080", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
