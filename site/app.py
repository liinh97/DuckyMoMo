"""Trang giới thiệu công khai của DuckyMoMo Network (site/public) + API chỉ đọc cho số liệu sống.

Đọc (read-only) file của server Bang Hội Chiến: cấu hình DuckyMoMoCore, settings.yml của trang Cài đặt, dữ liệu Hội Bàn Tròn
(an.yml), Hội đồng Hoà Bình (hoidong.yml), bảng xếp hạng (bxh.yml), tổ chức (tochuc.yml), Towny (flatfile). Hỏi proxy Velocity
bằng Server List Ping để biết số người online. Không trả UUID, mật khẩu, IP; không ghi gì vào server.

API: /api/info  /api/status  /api/bantron  /api/bxh  /api/vote  /api/mua  /api/hangngay   (kết quả giữ 30 giây)

Đăng nhập bằng tài khoản game (khi có PANEL_SITE_KEY): /api/dangnhap, /api/dangxuat, /api/toi; trang quản trị cho staff ở
/quantri/ (chuyển tiếp sang panel, panel tự kiểm quyền LuckPerms). Chỉ đăng nhập bằng mã một lần lấy trong game (/web),
không có mật khẩu web; trang web không đọc database, mọi kiểm tra do panel làm. Phiên đăng nhập chỉ nằm trong bộ nhớ (khởi động lại = đăng nhập lại).
"""
import hashlib
import http.client
import json
import os
import re
import secrets
import socket
import struct
import threading
import time
from datetime import date, datetime, timedelta
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
PANEL_URL = os.environ.get("PANEL_URL", "http://panel:8080")
PANEL_SITE_KEY = os.environ.get("PANEL_SITE_KEY", "")
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

def _i32(v):
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v & 0x80000000 else v


def java_objects_hash(seed, minute):
    """Như Java Objects.hash(String seed, Long minute) trong PetCombat.allowedFor (bốc thăm phiên có pet)."""
    hs = 0
    b = seed.encode("utf-16-be")
    for i in range(0, len(b), 2):
        hs = _i32(31 * hs + int.from_bytes(b[i:i + 2], "big"))
    hl = _i32(minute ^ (minute >> 32))
    return _i32(31 * _i32(31 * 1 + hs) + hl)


def pet_allowed(start_ms, seed, chance):
    if chance >= 100:
        return True
    if chance <= 0:
        return False
    return java_objects_hash(seed, start_ms // 60000) % 100 < chance


def siege_upcoming(sw, core, days=14, limit=12):
    """Các phiên công thành sắp tới theo lịch SiegeWar + có cho pet tham chiến không (cùng cách bốc thăm với plugin)."""
    sched = get(sw, "battle_session_scheduler.start_times", {}) or {}
    pc = get(core, "pet-combat", {}) or {}
    seed, chance = str(pc.get("seed", "mua1")), int(pc.get("siege-pet-chance", 30))
    names = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
    now = datetime.now(VN)
    out = []
    for d in range(days + 1):
        day = (now + timedelta(days=d)).date()
        key = names[day.weekday()]
        times = next((str(v) for k, v in sched.items() if k.upper() == key), "")
        for t in [x.strip() for x in times.split(",") if x.strip()]:
            try:
                hh, mm = (int(x) for x in t.split(":")[:2])
            except ValueError:
                continue
            start = datetime(day.year, day.month, day.day, hh, mm, tzinfo=VN)
            if start < now:
                continue
            ms = int(start.timestamp() * 1000)
            out.append({"time": start.strftime("%H:%M"), "day": DAYS_VI.get(key, key), "date": start.strftime("%d/%m"),
                        "ts": ms // 1000, "pet": pet_allowed(ms, seed, chance) if pc.get("enabled", False) else False})
    out.sort(key=lambda x: x["ts"])
    return out[:limit]


def pet_trees():
    """5 hệ gốc (MobTypes rỗng, không kế thừa) + nhóm loài (cây "<Hệ>_<nhóm>[_<loài>]" có khoá DmmNhom, xem tools/mypet/)."""
    out, groups = [], {}
    for f in sorted(plugin("MyPet", "skilltrees").glob("*.st.json")):
        try:
            d = json.loads(f.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            continue
        desc = [re.sub(r"<[^>]+>", "", str(x)).strip() for x in d.get("Description") or []]
        g = d.get("DmmNhom")
        if g:
            e = groups.setdefault(g["ma"], {"ma": g["ma"], "ten": g["ten"], "vai": g["vai"], "tang": g.get("tang", ""),
                                            "them": g.get("mo_ta") or [], "loai": set(), "dac_tinh": set()})
            e["loai"].update(g.get("loai_vi") or d.get("MobTypes") or [])
            if g.get("dac_tinh"):
                e["dac_tinh"].add(g["dac_tinh"])
            continue
        if d.get("Inheritance"):
            continue
        out.append({"id": d.get("ID"), "ten": d.get("Display") or d.get("Name"), "thu_tu": d.get("Order", 9), "mo_ta": [x for x in desc if x]})
    nhom = [{**e, "loai": sorted(e["loai"]), "dac_tinh": sorted(e["dac_tinh"])} for e in groups.values()]
    return sorted(out, key=lambda t: t["thu_tu"]), sorted(nhom, key=lambda e: e["ten"])


def pet_capture(core):
    """Bắt pet theo xác suất (DuckyMoMoCore pet-capture): % mặc định, số lần thử, % từng nhóm loài (tên nhóm lấy từ cây kỹ năng)."""
    if not get(core, "pet-capture.enabled", False):
        return {"enabled": False}
    names = {g["ma"]: g["ten"] for g in pet_trees()[1]}
    gc = get(core, "pet-capture.group-chance", {}) or {}
    now = season_id(core)
    bonus = (get(core, "pet-capture.season-bonus", {}) or {}).get(now or "", {}) or {}
    groups = sorted(({"ten": names.get(k, k), "tyle": int(v), "mua_nay": int(bonus.get(k, 0))} for k, v in gc.items()),
                    key=lambda g: -(g["tyle"] + g["mua_nay"]))
    sname = strip_colors(get(core, f"seasons.list.{now}.name", "") or "") if now else ""
    return {"enabled": True, "default": get(core, "pet-capture.chance-percent", 30), "attempts": get(core, "pet-capture.attempts-per-mob", 3),
            "cooldown": get(core, "pet-capture.cooldown-seconds", 2), "groups": groups, "season": sname.strip()}


def season_id(core):
    """Mã mùa hiện tại (cùng cách tính với plugin, season_now), mùa tắt thì None."""
    cfg = core.get("seasons") or {}
    st = season_now(cfg) if cfg.get("enabled") else None
    return st["cur"] if st else None


def mypet_level(exp):
    """Cấp MyPet từ kinh nghiệm (công thức mặc định, giống panel/taikhoan.py pet_level và PetTop.java)."""
    def need(lv):
        if lv <= 1:
            return 0
        if lv <= 17:
            return (lv - 1) * 17
        if lv <= 31:
            return 272 + (lv - 17) * (3 * lv - 14) / 2
        return 887 + (lv - 32) * (7 * lv - 93) / 2
    lv = 1
    while lv < 1000 and need(lv + 1) <= (exp or 0):
        lv += 1
    return lv


HE_TEN = {"Combat": "Chiến Binh", "Utility": "Hậu Cần", "PvP": "Đấu Sĩ", "Ride": "Thú Cưỡi", "Farm": "Nông Trại"}


def pet_top(limit=10):
    """Top pet theo kinh nghiệm (đọc pets.db chỉ đọc). Tên loài/nhóm lấy từ cây kỹ năng (DmmNhom.loai_vi)."""
    import sqlite3
    vi, nhom = {}, {}
    for f in plugin("MyPet", "skilltrees").glob("*_*.st.json"):
        try:
            d = json.loads(f.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            continue
        g = d.get("DmmNhom") or {}
        for t, v in zip(d.get("MobTypes") or [], g.get("loai_vi") or []):
            vi[t.lower()], nhom[t.lower()] = v, g.get("ten")
    try:
        con = sqlite3.connect(f"file:{plugin('MyPet', 'pets.db')}?mode=ro", uri=True, timeout=2)
        try:
            rows = con.execute("SELECT p.name, p.type, p.exp, p.skilltree, pl.name FROM pets p LEFT JOIN players pl "
                               "ON pl.internal_uuid = p.owner_uuid ORDER BY p.exp DESC LIMIT ?", (limit,)).fetchall()
        finally:
            con.close()
    except Exception:
        return []
    out = []
    for name, typ, exp, tree, owner in rows:
        t = str(typ or "").lower()
        out.append({"ten": re.sub(r"<[^>]+>|[&§][0-9a-fk-or]", "", str(name or "")) or vi.get(t, typ), "loai": vi.get(t, typ),
                    "nhom": nhom.get(t), "cap": mypet_level(exp), "he": HE_TEN.get(str(tree or "").split("_")[0]), "chu": owner})
    return out


def api_dautruong():
    """Đấu Trường trực tiếp: plugin ghi dautruong-live.json mỗi 5 giây; cũ hơn 60 giây = server đang tắt."""
    try:
        d = json.loads(plugin("DuckyMoMoCore", "dautruong-live.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {"ok": False, "san": [], "top": []}
    d["ok"] = time.time() - d.get("updated", 0) < 60
    return d


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
            "upcoming": siege_upcoming(sw, core),
            "pet_chance": int(get(core, "pet-combat.siege-pet-chance", 30)) if get(core, "pet-combat.enabled", False) else 0,
        },
        "pet": {
            "enabled": bool(get(core, "pet-combat.enabled", False)),
            "reduce": get(core, "pet-combat.player-damage-reduce-percent", 50),
            "announce": get(core, "pet-combat.announce-minutes", [60, 10]),
            "slots": {k: st.get(f"petc_slots_{g}") for k, g in [("Dân", "default"), ("Nam Tước", "namtuoc"), ("Tử Tước", "tutuoc"),
                      ("Bá Tước", "batuoc"), ("Hầu Tước", "hautuoc"), ("Công Tước", "congtuoc")] if st.get(f"petc_slots_{g}")},
            **dict(zip(("trees", "groups"), pet_trees())),
            "level_cap": get(load_yaml(plugin("MyPet", "config.yml")), "MyPet.Exp.LevelCap", 100),
            "respawn_per_level": get(load_yaml(plugin("MyPet", "config.yml")), "MyPet.Respawn.Time.Default.Factor", 5),
            "no_catch": ["Warden", "Wither", "Rồng Ender", "Elder Guardian", "Ravager", "Golem sắt", "Ghast"],
            "capture": pet_capture(core),
            "charm_bonus": get(core, "pet-capture.charm-bonus", 25),
            "fish_cap": get(core, "pet-fish.daily-exp-cap", 300),
            "weather": {k: get(core, f"pet-combat.siege-weather.{p}", d) for k, p, d in [
                ("enabled", "enabled", True), ("night_bonus", "night-pet-bonus", 20), ("water_bonus", "rain-water-bonus", 20),
                ("fire_malus", "rain-fire-malus", 25), ("heat_bonus", "heat-bonus", 15), ("eyes", "night-eyes", [])]},
            "top": pet_top(),
        },
        "supply": {"enabled": bool(get(core, "siege-supply.enabled", False)), "max": get(core, "siege-supply.max-per-town", 5000),
                   "cost": get(core, "siege-supply.cost-per-player-minute", 2),
                   "top": sorted(core_json("kholuong.json").get("kho", []), key=lambda k: -k.get("diem", 0))[:10]},
        "dishes": {"enabled": bool(get(core, "dishes.enabled", False)), "cooldown": get(core, "dishes.cooldown-minutes", 20),
                   "list": [{"ten": strip_colors(d.get("name", k)).strip(), "can": d.get("need", {}), "hieu_ung": d.get("effects", [])}
                            for k, d in (get(core, "dishes.list", {}) or {}).items()]},
        "specialty": core_json("dacsan.json").get("vung", []) if get(core, "region-specialty.enabled", False) else [],
        "arena": {
            "enabled": bool(get(core, "arena.enabled", False)),
            "fight": get(core, "arena.fight-seconds", 180),
            "countdown": get(core, "arena.countdown-seconds", 5),
            "pet_reduce": get(core, "arena.pet-damage-reduce-percent", 50),
            "fee": get(core, "arena.fee-percent", 5),
            "max_stake": get(core, "arena.max-stake", 10000),
            "reward": get(core, "arena.win-reward", 50),
            "cap": get(core, "arena.daily-reward-cap", 5),
            "pet_exp": get(core, "arena.pet-exp-win", 30),
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
            "tpr_warmup": get(core, "rtp.warmup-seconds", 3),
            "tp_delay": get(load_yaml(plugin("Essentials", "config.yml")), "teleport-delay", 0),
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
    tt = []
    now_ms = time.time() * 1000
    for v in ((load_yaml(plugin("DuckyMoMoCore", "treothuong.yml")).get("muc")) or {}).values():
        if not isinstance(v, dict) or float(v.get("het", 0) or 0) <= now_ms:
            continue
        nguoi = [p.get("ten") for p in (v.get("nguoi") or {}).values() if isinstance(p, dict)]
        tong = float(v.get("he-thong", 0) or 0) + sum(float(p.get("xu", 0) or 0) for p in (v.get("nguoi") or {}).values() if isinstance(p, dict))
        tt.append({"ten": v.get("ten"), "tong": tong, "boi": (["Hệ thống"] if float(v.get("he-thong", 0) or 0) > 0 else []) + nguoi,
                   "het": fmt_ms(v.get("het"))})
    tt.sort(key=lambda x: -x["tong"])
    return {
        "treothuong": tt[:10],
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


# ---------------------------------------------------------------- mùa mưa/khô, câu cá (EvenMoreFish), hoạt động hằng ngày

CROP_VI = {"WHEAT": "Lúa mì", "CARROTS": "Cà rốt", "POTATOES": "Khoai tây", "BEETROOTS": "Củ dền", "COCOA": "Ca cao", "SUGAR_CANE": "Mía",
           "BAMBOO": "Tre", "CACTUS": "Xương rồng", "MELON": "Dưa hấu", "MELON_STEM": "Dây dưa hấu", "PUMPKIN": "Bí ngô",
           "PUMPKIN_STEM": "Dây bí ngô", "SWEET_BERRY_BUSH": "Dâu rừng", "KELP": "Tảo bẹ"}
MAT_VI = {"GOLDEN_APPLE": "táo vàng", "ENCHANTED_GOLDEN_APPLE": "táo vàng phù phép", "DIAMOND": "kim cương", "DIAMOND_BLOCK": "khối kim cương",
          "EMERALD": "ngọc lục bảo", "IRON_INGOT": "thỏi sắt", "GOLD_INGOT": "thỏi vàng", "BREAD": "bánh mì", "EXPERIENCE_BOTTLE": "bình kinh nghiệm",
          "ENDER_PEARL": "ngọc ender", "NETHERITE_SCRAP": "mảnh vụn netherite", "TOTEM_OF_UNDYING": "vật tổ bất tử", "NAME_TAG": "thẻ tên",
          "COOKED_BEEF": "bít tết", "ARROW": "mũi tên", "SADDLE": "yên ngựa", "DIRT": "đất", "COBBLESTONE": "đá cuội", "STONE": "đá",
          "SAND": "cát", "GRAVEL": "sỏi", "GLASS": "kính", "OAK_LOG": "gỗ sồi", "SPRUCE_LOG": "gỗ vân sam", "BIRCH_LOG": "gỗ bạch dương",
          "JUNGLE_LOG": "gỗ rừng", "OAK_PLANKS": "ván gỗ sồi", "WHEAT": "lúa mì", "CARROT": "cà rốt", "POTATO": "khoai tây", "SUGAR_CANE": "mía",
          "COAL": "than", "IRON_ORE": "quặng sắt", "RAW_IRON": "sắt thô", "COPPER_INGOT": "thỏi đồng", "REDSTONE": "đá đỏ", "LAPIS_LAZULI": "lưu ly",
          "QUARTZ": "thạch anh", "OBSIDIAN": "hắc diện thạch", "STRING": "tơ nhện", "LEATHER": "da", "FEATHER": "lông", "BONE": "xương",
          "GUNPOWDER": "thuốc súng", "SLIME_BALL": "chất nhờn", "WOOL": "len", "WHITE_WOOL": "len trắng", "BRICKS": "gạch", "CLAY_BALL": "đất sét",
          "COOKED_COD": "cá tuyết nướng", "COD": "cá tuyết", "SALMON": "cá hồi", "MELON_SLICE": "lát dưa hấu", "PUMPKIN": "bí ngô",
          "BAMBOO": "tre", "OBSIDIAN": "hắc diện thạch", "ENDER_EYE": "mắt ender", "GHAST_TEAR": "nước mắt ghast", "BREEZE_ROD": "que breeze",
          "IRON_BLOCK": "khối sắt", "CACTUS": "xương rồng", "KELP": "tảo bẹ", "STONE_BRICKS": "gạch đá", "DEEPSLATE": "đá phiến sâu", "NETHERRACK": "đá Nether"}
RARITY_COLOR = {"gray": "#adb5bd", "white": "#f8f9fa", "aqua": "#66d9e8", "light_purple": "#f783ac", "gold": "#f2b84b",
                "green": "#69db7c", "yellow": "#ffe066", "red": "#ff6b6b", "blue": "#5c7cfa", "dark_purple": "#9c36b5"}
DAYS_EN = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
EMF_SRC = MODES / "banghoi" / "server" / "plugins" / "EvenMoreFish"


def mm_strip(s):
    """Bỏ thẻ MiniMessage (<gold>, <bold>…) và mã màu &x."""
    return strip_colors(re.sub(r"<[^>]*>", "", str(s or ""))).strip()


def reward_text(spec, library=None):
    a = str(spec).split()
    if not a:
        return None
    k = a[0].lower()
    try:
        if k == "xu":
            return f"{float(a[1]):,.0f}".replace(",", ".") + " xu"
        if k == "vat":
            n = a[2] if len(a) > 2 else "1"
            return f"{n} {MAT_VI.get(a[1].upper(), a[1].lower().replace('_', ' '))}"
        if k == "manh":
            return f"{a[2]} mảnh sự kiện"
        if k == "do":
            name = (library or {}).get(a[1]) or "đồ đặc biệt"
            return f"{name}" + (f" x{a[2]}" if len(a) > 2 else "")
    except (IndexError, ValueError):
        return None
    return None


def season_now(cfg):
    """Giống Seasons.java: mùa theo ngày thật giờ VN, ghi đè của admin ở mua.yml."""
    lst = cfg.get("list") or {}
    order = [o for o in (cfg.get("order") or []) if o in lst]
    if not order:
        return None
    sd = cfg.get("start-date", "2026-10-12")
    try:
        start = sd if isinstance(sd, date) else date.fromisoformat(str(sd).strip())
    except ValueError:
        start = date(2026, 10, 12)
    per = max(1, int(cfg.get("days-per-season", 7) or 7))
    since = (datetime.now(VN).date() - start).days
    idx = (since // per) % len(order)
    forced = str(load_yaml(plugin("DuckyMoMoCore", "mua.yml")).get("ghi-de") or "")
    cur = forced if forced in order else order[idx]
    left = per - since % per
    return {"order": order, "cur": cur, "next": order[(order.index(cur) + 1) % len(order)], "per": per,
            "left": None if forced in order else left, "day": per - left + 1, "forced": forced in order}


def season_event(cfg, st):
    """Sự kiện mùa đang diễn ra (giống Seasons.event()): seasons.events.<id> theo ngày thứ mấy của mùa; ghi đè ở mua.yml."""
    evs = cfg.get("events") or {}
    forced = str(load_yaml(plugin("DuckyMoMoCore", "mua.yml")).get("ghi-de-sukien") or "")
    pick = None
    if forced and forced in evs:
        pick = forced
    elif st:
        per = st["per"]
        for eid, e in evs.items():
            if (e or {}).get("season") != st["cur"]:
                continue
            a = int(e.get("from-day", 1))
            b = int(e.get("to-day", a))
            a = per + a + 1 if a < 0 else a
            b = per + b + 1 if b < 0 else b
            if a <= st["day"] <= b:
                pick = eid
                break
    if not pick:
        return None
    e = evs[pick] or {}
    return {"id": pick, "ten": strip_colors(e.get("name", pick)).strip(), "mo_ta": strip_colors(e.get("subtitle", "")).strip()}


def core_json(name):
    """File JSON DuckyMoMoCore ghi cho web (kholuong.json, dacsan.json); lỗi thì {}."""
    try:
        return json.loads(plugin("DuckyMoMoCore", name).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def season_view(sid, sc):
    up, down = [], []
    for k, v in (sc.get("growth") or {}).items():
        try:
            v = float(v)
        except (TypeError, ValueError):
            continue
        item = {"ten": CROP_VI.get(str(k).upper(), str(k).lower()), "pct": round((v - 1) * 100)}
        (up if v > 1 else down if v < 1 else []).append(item)
    bonus = [{"ten": CROP_VI.get(str(k).upper(), str(k).lower()), "pct": round(float(v))}
             for k, v in (sc.get("harvest-bonus") or {}).items() if float(v or 0) > 0]
    return {"id": sid, "ten": strip_colors(sc.get("name", sid)), "mo_ta": strip_colors(sc.get("subtitle", "")),
            "mua_bat": float(sc.get("rain-start-chance", 0) or 0), "mua_tanh": float(sc.get("rain-stop-chance", 0) or 0),
            "nhanh": sorted(up, key=lambda x: -x["pct"]), "cham": sorted(down, key=lambda x: x["pct"]), "duoc_mua": bonus}


def emf_dir(*parts):
    live = plugin("EvenMoreFish", *parts)
    return live if live.exists() else EMF_SRC.joinpath(*parts)


def fish_catalog():
    rar = []
    for f in sorted(emf_dir("rarities").glob("*.yml")):
        if f.name.startswith("_"):
            continue
        r = load_yaml(f)
        if not r or r.get("disabled"):
            continue
        m = re.match(r"<(\w+)>", str(r.get("format", "")))
        size = r.get("size") or {}
        mult = float(r.get("worth-multiplier", 0) or 0)
        fish = []
        for fid, fc in (r.get("fish") or {}).items():
            fc = fc if isinstance(fc, dict) else {}
            if fc.get("disabled"):
                continue
            req = fc.get("requirements") or {}
            mua = None
            for ph in req.get("placeholder") or []:
                mm = re.search(r"dmmmua_id%\s*==?\s*(\w+)", str(ph))
                if mm:
                    mua = mm.group(1)
            fsize = fc.get("size") or size
            lo, hi = int(fsize.get("minSize", 0) or 0), int(fsize.get("maxSize", 0) or 0)
            lore = (fc.get("item") or {}).get("lore") or []
            fish.append({"ten": mm_strip(fc.get("displayname") or (fc.get("item") or {}).get("displayname") or fid),
                         "mo_ta": mm_strip(lore[0]) if lore else "", "mua": mua, "bien": "oceans" in (req.get("biome-set") or []),
                         "dai": [lo, hi], "gia": [round(lo * mult, 1), round(hi * mult, 1)] if mult else None})
        rar.append({"id": r.get("id"), "ten": mm_strip(r.get("displayname") or r.get("id")), "mau": RARITY_COLOR.get(m.group(1) if m else "", "#e8eef5"),
                    "w": float(r.get("weight", 0) or 0), "ca": fish})
    total = sum(x["w"] for x in rar) or 1
    for x in rar:
        x["tile"] = round(x["w"] * 100 / total, 1)
    rar.sort(key=lambda x: -x["w"])
    return rar


def next_competitions():
    now = datetime.now(VN)
    out = []
    for f in sorted(emf_dir("competitions").glob("*.yml")):
        if f.name.startswith("_"):
            continue
        c = load_yaml(f)
        if not c or c.get("disabled"):
            continue
        slots = []
        for day, times in (c.get("days") or {}).items():
            if str(day).upper() in DAYS_EN:
                slots += [(DAYS_EN.index(str(day).upper()), str(t)) for t in times or []]
        blacklist = [str(d).upper() for d in c.get("blacklisted-days") or []]
        for t in c.get("times") or []:
            slots += [(i, str(t)) for i, d in enumerate(DAYS_EN) if d not in blacklist]
        nxt = None
        for add in range(0, 8):
            d = now + timedelta(days=add)
            for wd, t in sorted(slots, key=lambda x: x[1]):
                if wd != d.weekday():
                    continue
                try:
                    hh, mi = map(int, t.split(":"))
                except ValueError:
                    continue
                at = d.replace(hour=hh, minute=mi, second=0, microsecond=0)
                if at > now and (nxt is None or at < nxt):
                    nxt = at
            if nxt:
                break
        rewards = {}
        for k, v in (c.get("rewards") or {}).items():
            money = [x.split(":", 1)[1] for x in v or [] if str(x).upper().startswith("MONEY:")]
            if money:
                rewards[str(k)] = money[0]
        kinds = {"LARGEST_FISH": "Con dài nhất", "MOST_FISH": "Câu nhiều nhất", "LARGEST_TOTAL": "Tổng chiều dài lớn nhất",
                 "SPECIFIC_FISH": "Câu đúng loại cá", "SPECIFIC_RARITY": "Câu đúng độ hiếm", "SHORTEST_FISH": "Con ngắn nhất", "RANDOM": "Ngẫu nhiên"}
        lich = sorted({(wd, t) for wd, t in slots})
        out.append({"ten": mm_strip(c.get("bossbar-prefix", "")).rstrip(": ") or c.get("id"), "kieu": kinds.get(str(c.get("type")), c.get("type")),
                    "phut": c.get("duration", 10), "it_nhat": c.get("minimum-players", 1),
                    "lich": [("Chủ nhật" if wd == 6 else f"Thứ {wd + 2}") + " " + t for wd, t in lich],
                    "tiep": int(nxt.timestamp() * 1000) if nxt else None, "thuong": rewards})
    return out


def api_mua():
    core = load_yaml(plugin("DuckyMoMoCore", "config.yml"))
    cfg = core.get("seasons") or {}
    out = {"bat": False, "ca": fish_catalog(), "thicau": next_competitions(), "emf": plugin("EvenMoreFish").exists()}
    st = season_now(cfg) if cfg.get("enabled") else None
    if st:
        lst = cfg.get("list") or {}
        out["su_kien"] = season_event(cfg, st)
        out.update({"bat": True, "hien_tai": season_view(st["cur"], lst[st["cur"]]), "tiep": season_view(st["next"], lst[st["next"]]),
                    "con": st["left"], "ngay_thu": st["day"], "moi_mua": st["per"], "ghi_de": st["forced"],
                    "cac_mua": [season_view(o, lst[o]) for o in st["order"]]})
    return out


def khaihoang(core):
    fc = get(core, "frontier", {}) or {}
    d = load_yaml(plugin("DuckyMoMoCore", "khaihoang.yml"))
    quy = float(d.get("quy", 0) or 0)
    dat = d.get("dat") or []
    goals = [{"ten": strip_colors(g.get("name", g.get("id"))), "can": float(g.get("need", 0) or 0), "mo": g.get("id") in dat}
             for g in fc.get("goals") or [] if isinstance(g, dict) and g.get("id")]
    top = sorted(((v.get("ten"), float(v.get("xu", 0) or 0)) for v in (d.get("gop") or {}).values() if isinstance(v, dict)),
                 key=lambda x: -x[1])[:8]
    return {"bat": bool(fc.get("enabled")), "quy": quy, "ban": fc.get("sell-percent", 100), "moc": goals,
            "top": [{"ten": n, "xu": x} for n, x in top]}


FEATURE_VI = {"tui_hu": "Rương ender mở ở mọi nơi", "nuoc": "Nguồn nước vô tận", "hut_do": "Đồ đào/săn quái bay thẳng vào túi",
              "hut_mau": "Hút máu mỗi phát bắn trúng"}


def xuong(core):
    w = get(core, "workshop", {}) or {}
    items = []
    for k, r in (w.get("items") or {}).items():
        if not isinstance(r, dict) or r.get("enabled") is False:
            continue
        ing = [f"{n} {MAT_VI.get(str(m).upper(), str(m).lower().replace('_', ' '))}" for m, n in (r.get("ingredients") or {}).items()]
        items.append({"ten": strip_colors(r.get("name", k)), "tinh_nang": FEATURE_VI.get(r.get("feature", ""), "Phù phép đặc biệt"),
                      "luot": r.get("uses", -1), "ngay": r.get("days", 7), "gia": r.get("cost", 0), "nguyen_lieu": ing,
                      "pvp": bool(r.get("pvp")), "mo_ta": " ".join(r.get("description") or [])})
    return {"bat": bool(w.get("enabled")), "mon": items}


def api_hangngay():
    core = load_yaml(plugin("DuckyMoMoCore", "config.yml"))
    # tên món trong Thư viện đồ (quà "do <mã>"): tên hiện trong game, không có thì tên gợi nhớ
    lib = {}
    for k, it in (load_yaml(plugin("DuckyMoMoCore", "thuvien-do.yml")).get("items") or {}).items():
        if isinstance(it, dict):
            lib[k] = strip_colors(get(it, "preview.name") or it.get("ten") or it.get("ten-goi") or k)

    def rw(lst):
        return [x for x in (reward_text(r, lib) for r in lst or []) if x]

    dc, dq, ls, ef = (get(core, k, {}) or {} for k in ("daily-checkin", "daily-quests", "lucky-spin", "event-fragments"))
    oc = get(core, "orders", {}) or {}
    days = int(oc.get("days", 7) or 7)
    now_ms = time.time() * 1000
    don = []
    for k, o in (load_yaml(plugin("DuckyMoMoCore", "donhang.yml")).get("don") or {}).items():
        if not isinstance(o, dict) or o.get("dong") or int(o.get("da", 0)) >= int(o.get("can", 0)):
            continue
        if now_ms > float(o.get("tao", 0)) + days * 86400000:
            continue
        m = str(o.get("vat", ""))
        don.append({"id": int(k), "ten": o.get("ten"), "vat": MAT_VI.get(m, m.lower().replace("_", " ")), "can": int(o.get("can", 0)),
                    "da": int(o.get("da", 0)), "gia": float(o.get("gia", 0))})
    don.sort(key=lambda x: -x["id"])
    prizes = [p for p in ls.get("prizes") or [] if isinstance(p, dict)]
    tw = sum(float(p.get("weight", 0) or 0) for p in prizes) or 1
    return {
        "diemdanh": {"bat": bool(dc.get("enabled")), "phut": dc.get("min-online-minutes", 5), "ngay": rw(dc.get("daily-rewards")),
                     "moc": [{"ngay": int(k), "qua": rw(v)} for k, v in sorted((dc.get("milestones") or {}).items(), key=lambda x: int(x[0]))]},
        "nhiemvu": {"bat": bool(dq.get("enabled")), "moi_ngay": dq.get("per-day", 3), "xong_het": rw(dq.get("complete-all-rewards")),
                    "ds": [{"ten": q.get("name", k), "qua": rw(q.get("rewards"))} for k, q in (dq.get("pool") or {}).items() if isinstance(q, dict)]},
        "vongquay": {"bat": bool(ls.get("enabled")), "phut": ls.get("minutes-per-key", 60), "toi_da": ls.get("max-keys", 5),
                     "qua": [{"ten": strip_colors(p.get("label", "")), "tile": round(float(p.get("weight", 0) or 0) * 100 / tw, 1),
                              "hiem": bool(p.get("rare"))} for p in prizes]},
        "khaihoang": khaihoang(core),
        "xuong": xuong(core),
        "donhang": {"bat": bool(oc.get("enabled")), "phi": oc.get("fee-percent", 2), "ngay": days, "ds": don[:12], "tong": len(don)},
        "manh": {"bat": bool(ef.get("enabled")), "toi_da_ngay": ef.get("daily-cap", 20),
                 "doi": [{"ten": strip_colors(x.get("label", "")), "gia": sum(int(v) for v in (x.get("cost") or {}).values())}
                         for x in ef.get("shop") or [] if isinstance(x, dict)]},
    }


# ---------------------------------------------------------------- đăng nhập bằng tài khoản game

COOKIE = "dmm_phien"
SESSION_DAYS = 7          # phiên xem tài khoản
ADMIN_HOURS = 12          # staff mở trang quản trị trong 12 giờ sau khi nhập mã; hết thì gõ /web lấy mã mới (vẫn xem được tài khoản)
NAME_RE = re.compile(r"^\.?[A-Za-z0-9_]{1,17}$")
FAIL_WINDOW = 15 * 60
FAIL_PER_NAME = 5         # sai 5 lần / 15 phút: khoá tên đó 15 phút (chống dò mã)
FAIL_PER_IP = 20
_sessions = {}            # sha256(token) -> {ten, tao, het, game_het, ip}
_fails = {}               # ("ten"|"ip", giá trị) -> [mốc thời gian sai]
_slock = threading.Lock()


def panel(method, path, body=None, player=None, ip=""):
    """Gọi panel qua mạng nội bộ Docker. Trả (status, headers, bytes)."""
    u = PANEL_URL.split("://", 1)[1]
    host, _, port = u.partition(":")
    con = http.client.HTTPConnection(host, int(port or 80), timeout=30)
    headers = {"X-DMM-Key": PANEL_SITE_KEY, "X-DMM-Ip": ip}
    if player:
        headers["X-DMM-Player"] = player
    if body is not None:
        headers["Content-Type"] = "application/json"
    try:
        con.request(method, path, body=body, headers=headers)
        r = con.getresponse()
        return r.status, dict(r.getheaders()), r.read()
    finally:
        con.close()


def _blocked(key, limit):
    now = time.time()
    with _slock:
        lst = [t for t in _fails.get(key, []) if now - t < FAIL_WINDOW]
        _fails[key] = lst
        return len(lst) >= limit


def _fail(*keys):
    with _slock:
        for k in keys:
            _fails.setdefault(k, []).append(time.time())


def session_of(token):
    if not token:
        return None
    h = hashlib.sha256(token.encode()).hexdigest()
    with _slock:
        s = _sessions.get(h)
        if s and s["het"] < time.time():
            _sessions.pop(h, None)
            s = None
    return s


def login(data, ip):
    name = str(data.get("ten", "")).strip()
    if not PANEL_SITE_KEY:
        return 503, {"ok": False, "message": "Đăng nhập chưa bật trên server."}, None
    if not NAME_RE.match(name):
        return 400, {"ok": False, "message": "Tên nhân vật không hợp lệ."}, None
    if _blocked(("ten", name.lower()), FAIL_PER_NAME) or _blocked(("ip", ip), FAIL_PER_IP):
        return 429, {"ok": False, "message": "Sai quá nhiều lần. Đợi 15 phút rồi thử lại, hoặc vào game gõ /web để lấy mã."}, None
    # Chỉ đăng nhập bằng mã /web (chứng minh đang đăng nhập trong game); trang web không nhận mật khẩu
    ma = re.sub(r"\D", "", str(data.get("ma", "")))
    if len(ma) != 6:
        return 400, {"ok": False, "message": "Nhập mã 6 số: vào game gõ /web để lấy mã."}, None
    body = json.dumps({"ten": name, "ma": ma}).encode()
    status, _, raw = panel("POST", "/api/web/xacminh", body, ip=ip)
    res = json.loads(raw or b"{}") if status == 200 else {}
    if not res.get("ok"):
        _fail(("ten", name.lower()), ("ip", ip))
        if status != 200:
            return 502, {"ok": False, "message": "Máy chủ đang bận, thử lại sau."}, None
        msg = {
            "KHAC": f"Mã này được tạo cho tài khoản khác, không phải {name}. Nhập đúng tên nhân vật bạn đã gõ /web trong game.",
            "HET": "Mã đã hết hạn (5 phút). Vào game gõ /web để lấy mã mới.",
            "KHONG": f"Tài khoản {name} chưa có mã (hoặc mã đã dùng rồi). Vào game bằng nhân vật này, gõ /web để lấy mã.",
            "TAT": "Server game đang tắt hoặc khởi động lại, thử lại sau ít phút.",
        }.get(res.get("ly_do"), "Sai mã. Kiểm tra lại 6 số trong chat game (mã dùng 1 lần).")
        return 401, {"ok": False, "message": msg}, None
    token = secrets.token_urlsafe(32)
    now = time.time()
    with _slock:
        for k in [k for k, v in _sessions.items() if v["het"] < now]:
            _sessions.pop(k, None)
        for k in [k for k, v in _fails.items() if not v or now - v[-1] > FAIL_WINDOW]:
            _fails.pop(k, None)
        _sessions[hashlib.sha256(token.encode()).hexdigest()] = {
            "ten": res["ten"], "tao": now, "het": now + SESSION_DAYS * 86400, "game_het": now + ADMIN_HOURS * 3600, "ip": ip}
    return 200, {"ok": True, "ten": res["ten"]}, token


def logout(token, everywhere=False):
    s = session_of(token)
    with _slock:
        if s and everywhere:
            for k in [k for k, v in _sessions.items() if v["ten"].lower() == s["ten"].lower()]:
                _sessions.pop(k, None)
        elif token:
            _sessions.pop(hashlib.sha256(token.encode()).hexdigest(), None)


def me(token, ip):
    s = session_of(token)
    if not s:
        return {"dang_nhap": False, "bat": bool(PANEL_SITE_KEY)}
    qua_game = s["game_het"] > time.time()
    status, _, raw = panel("GET", f"/api/web/hoso?ten={s['ten']}&qua_game={1 if qua_game else 0}", ip=ip)
    data = json.loads(raw or b"{}") if status == 200 else {"ok": False, "message": "Không tải được thông tin, thử lại sau."}
    data.update(dang_nhap=True, bat=True, phien={"het": int(s["het"] * 1000), "quan_tri_het": int(s["game_het"] * 1000) if qua_game else 0})
    return data


ROUTES = {
    "/api/info": lambda: cached("info", 30, api_info),
    "/api/status": lambda: cached("status", 20, api_status),
    "/api/bantron": lambda: cached("bantron", 20, api_bantron),
    "/api/bxh": lambda: cached("bxh", 30, api_bxh),
    "/api/vote": lambda: cached("vote", 30, api_vote),
    "/api/mua": lambda: cached("mua", 60, api_mua),
    "/api/dautruong": lambda: cached("dautruong", 5, api_dautruong),
    "/api/hangngay": lambda: cached("hangngay", 60, api_hangngay),
}
TYPES = {".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".js": "application/javascript; charset=utf-8",
         ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp", ".svg": "image/svg+xml", ".ico": "image/x-icon",
         ".json": "application/json"}


class Handler(BaseHTTPRequestHandler):
    # Không lộ phần mềm/phiên bản máy chủ (trang công khai, tránh bị lần ngược về hạ tầng)
    server_version = "web"
    sys_version = ""

    def _send(self, code, body, ctype, cache="no-cache", extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", cache)
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Robots-Tag", "noindex, nofollow")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Frame-Options", "DENY")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _json(self, code, obj, extra=None):
        self._send(code, json.dumps(obj, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8", "no-store", extra)

    def client_ip(self):
        # Trạm 6 (Traefik) ghi X-Real-Ip; chạy thẳng thì lấy địa chỉ kết nối
        return (self.headers.get("X-Real-Ip") or self.client_address[0])[:64]

    def token(self):
        for part in self.headers.get("Cookie", "").split(";"):
            k, _, v = part.strip().partition("=")
            if k == COOKIE:
                return v
        return ""

    def cookie(self, token, max_age):
        prefix = self.headers.get("X-Forwarded-Prefix", "").rstrip("/") + "/"
        secure = "; Secure" if self.headers.get("X-Forwarded-Proto") == "https" else ""
        return f"{COOKIE}={token}; Path={prefix}; Max-Age={max_age}; HttpOnly; SameSite=Lax{secure}"

    def same_origin(self):
        """Chặn trang khác gửi yêu cầu thay người dùng (CSRF): trình duyệt luôn gửi Origin / Sec-Fetch-Site với fetch POST."""
        site = self.headers.get("Sec-Fetch-Site")
        if site and site not in ("same-origin", "none"):
            return False
        origin = self.headers.get("Origin")
        if origin:
            host = self.headers.get("X-Forwarded-Host") or self.headers.get("Host", "")
            return origin.split("://", 1)[-1].rstrip("/") == host
        return True

    def body_json(self, limit=8192):
        length = int(self.headers.get("Content-Length", "0") or 0)
        if length > limit:
            raise ValueError("Dữ liệu quá lớn")
        return json.loads(self.rfile.read(length) or b"{}")

    def admin(self, method):
        """/quantri/… -> panel. Chỉ phiên đã nhập mã từ game (còn hạn); panel kiểm quyền từng trang/thao tác."""
        path = self.path[len("/quantri"):] or "/"
        s = session_of(self.token())
        if self.path.split("?", 1)[0] == "/quantri":
            return self._send(302, b"", "text/plain", "no-store", {"Location": "quantri/"})
        if not s or s["game_het"] < time.time():
            if path.startswith("/api/"):
                return self._json(401, {"ok": False, "message": "Phiên quản trị hết hạn: vào game gõ /web rồi đăng nhập lại bằng mã."})
            return self._send(302, b"", "text/plain", "no-store", {"Location": "../taikhoan.html?can=ma"})
        if method == "POST" and not self.same_origin():
            return self._json(403, {"ok": False, "message": "Yêu cầu không hợp lệ"})
        body = None
        if method == "POST":
            length = int(self.headers.get("Content-Length", "0") or 0)
            if length > 4_000_000:
                return self._json(413, {"ok": False, "message": "Dữ liệu quá lớn"})
            body = self.rfile.read(length)
        try:
            status, headers, raw = panel(method, path, body, player=s["ten"], ip=self.client_ip())
        except OSError:
            return self._json(502, {"ok": False, "message": "Trang quản trị đang tắt, thử lại sau."})
        extra = {"Location": headers["Location"]} if "Location" in headers else None
        self._send(status, raw, headers.get("Content-Type", "text/plain"), "no-store", extra)

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        if path.startswith("/quantri"):
            return self.admin("POST")
        if not self.same_origin():
            return self._json(403, {"ok": False, "message": "Yêu cầu không hợp lệ"})
        try:
            if path == "/api/dangnhap":
                code, obj, token = login(self.body_json(), self.client_ip())
                extra = {"Set-Cookie": self.cookie(token, SESSION_DAYS * 86400)} if token else None
                return self._json(code, obj, extra)
            if path == "/api/donate":
                s = session_of(self.token())
                if not s:
                    return self._json(401, {"ok": False, "message": "Phiên đăng nhập đã hết, đăng nhập lại."})
                d = self.body_json()
                body = {"ten": s["ten"], "action": d.get("action"), "bac": d.get("bac"), "goi": d.get("goi"), "id": d.get("id")}
                st, _, raw = panel("POST", "/api/web/donate", json.dumps(body).encode(), ip=self.client_ip())
                return self._json(st if st in (200, 400) else 502, json.loads(raw or b"{}") if st in (200, 400) else
                                  {"ok": False, "message": "Máy chủ đang bận, thử lại sau."})
            if path == "/api/trangtri":
                s = session_of(self.token())
                if not s:
                    return self._json(401, {"ok": False, "message": "Phiên đăng nhập đã hết, đăng nhập lại."})
                d = self.body_json()
                body = {k: d.get(k) for k in ("viec", "gia_tri", "hieu_ung", "kieu", "loai", "ma", "id")} | {"ten": s["ten"]}
                st, _, raw = panel("POST", "/api/web/trangtri", json.dumps(body).encode(), ip=self.client_ip())
                return self._json(st if st in (200, 400) else 502, json.loads(raw or b"{}") if st in (200, 400) else
                                  {"ok": False, "message": "Máy chủ đang bận, thử lại sau."})
            if path == "/api/choilai":
                s = session_of(self.token())
                if not s:
                    return self._json(401, {"ok": False, "message": "Phiên đăng nhập đã hết, đăng nhập lại."})
                st, _, raw = panel("POST", "/api/web/choilai", json.dumps({"ten": s["ten"]}).encode(), ip=self.client_ip())
                return self._json(200, json.loads(raw or b"{}") if st == 200 else {"ok": False, "message": "Máy chủ đang bận, thử lại sau."})
            if path in ("/api/dangxuat", "/api/dangxuat-het"):
                logout(self.token(), everywhere=path.endswith("-het"))
                return self._json(200, {"ok": True}, {"Set-Cookie": self.cookie("", 0)})
        except (ValueError, OSError) as e:
            return self._json(400 if isinstance(e, ValueError) else 502, {"ok": False, "message": "Không xử lý được, thử lại sau."})
        self._json(404, {"ok": False})

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path.startswith("/quantri"):
            return self.admin("GET")
        if path == "/api/toi":
            try:
                return self._json(200, me(self.token(), self.client_ip()))
            except OSError:
                return self._json(200, {"dang_nhap": True, "ok": False, "message": "Không tải được thông tin, thử lại sau."})
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
