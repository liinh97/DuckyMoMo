"""Thông báo đẩy về điện thoại (Web Push, PWA) — 29/10, chủ server: "thông báo về đâu đó khi có member vào sv, thoát khỏi server",
"PWA giống app goc6", "những mục này đều có thể tự cài đặt có nhận thông báo hay không".

Cách chạy giống goc6 (fe_goc6/src/services/pushService.ts + be_goc6 PushService): trang /thongbao đăng ký service worker (sw.js),
xin quyền, subscribe bằng khoá VAPID công khai, gửi subscription lên panel; panel lưu (data/panel/push-subs.json) và đẩy khi có sự kiện.
iPhone (iOS 16.4+): phải "Thêm vào Màn hình chính" rồi mở từ biểu tượng mới bật được (giới hạn của Apple).

Ba loại, mỗi máy tự bật / tắt:
  vaora — người THẬT vào / ra mạng (bỏ bot botfarm, bỏ staff; lần đầu vào thì ghi rõ; ra kèm số phút đã chơi)
  chat  — tin chat của người thật (bỏ bot, bỏ staff)
  suco  — server không phản hồi / hoạt động lại, TPS Bang Hội dưới 15 kéo dài, botfarm rớt nhiều bot cùng lúc
Khoá VAPID tự tạo lần đầu: data/panel/push-vapid.pem (không commit).
"""

import base64
import json
import re
import threading
import time
from pathlib import Path

KINDS = {"vaora": "Người chơi vào / ra", "chat": "Chat của người chơi", "suco": "Sự cố server"}


class Notifier:
    def __init__(self, data_dir, activity_file, bot_names, staff_names, server_status, botfarm_online, log=print):
        """bot_names() / staff_names() -> set tên (chữ thường); server_status() -> {server: (ok, tps|None)}; botfarm_online() -> số bot online."""
        self.dir = Path(data_dir) / "panel"
        self.subs_file = self.dir / "push-subs.json"
        self.key_file = self.dir / "push-vapid.pem"
        self.log_file = self.dir / "push-log.json"
        self.activity_file = Path(activity_file)
        self.bot_names, self.staff_names = bot_names, staff_names
        self.server_status, self.botfarm_online = server_status, botfarm_online
        self.log = log
        self.lock = threading.Lock()
        self._vapid = None
        self._public = None
        self.sent = []   # 50 thông báo gần nhất (hiện trên trang)
        try:
            self.sent = json.loads(self.log_file.read_text(encoding="utf-8"))
        except Exception:
            self.sent = []

    # ------------------------------------------------------------ khoá VAPID
    def _keys(self):
        if self._vapid:
            return self._vapid, self._public
        from py_vapid import Vapid01
        from cryptography.hazmat.primitives import serialization
        self.dir.mkdir(parents=True, exist_ok=True)
        if self.key_file.exists():
            v = Vapid01.from_file(str(self.key_file))
        else:
            v = Vapid01()
            v.generate_keys()
            v.save_key(str(self.key_file))
            self.key_file.chmod(0o600)
        raw = v.public_key.public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)
        self._vapid, self._public = v, base64.urlsafe_b64encode(raw).decode().rstrip("=")
        return self._vapid, self._public

    def public_key(self):
        return self._keys()[1]

    # ------------------------------------------------------------ đăng ký
    def _load(self):
        try:
            return json.loads(self.subs_file.read_text(encoding="utf-8"))
        except Exception:
            return {}

    def _save(self, subs):
        self.dir.mkdir(parents=True, exist_ok=True)
        tmp = self.subs_file.with_suffix(".tmp")
        tmp.write_text(json.dumps(subs, ensure_ascii=False), encoding="utf-8")
        tmp.replace(self.subs_file)

    def info(self, endpoint=""):
        subs = self._load()
        me = subs.get(endpoint) if endpoint else None
        return {"ok": True, "key": self.public_key(), "kinds": KINDS, "so_may": len(subs),
                "cua_toi": me and {"prefs": me.get("prefs", {}), "ten": me.get("ten", "")}, "gan_day": self.sent[-30:][::-1]}

    def action(self, body, who):
        act = body.get("action")
        sub = body.get("sub") or {}
        ep = str(sub.get("endpoint") or body.get("endpoint") or "")
        if act in ("subscribe", "prefs", "unsubscribe", "test") and not ep.startswith("https://"):
            raise ValueError("Thiếu đăng ký thông báo của máy này")
        with self.lock:
            subs = self._load()
            if act == "subscribe":
                if not (sub.get("keys") or {}).get("p256dh"):
                    raise ValueError("Đăng ký thiếu khoá")
                old = subs.get(ep, {})
                prefs = {k: bool((body.get("prefs") or old.get("prefs") or {}).get(k, True)) for k in KINDS}
                subs[ep] = {"sub": {"endpoint": ep, "keys": sub["keys"]}, "prefs": prefs, "ten": str(body.get("ten") or who)[:40],
                            "t": int(time.time())}
                self._save(subs)
                return {"ok": True, "message": "Đã bật thông báo cho máy này"}
            if act == "prefs":
                if ep not in subs:
                    raise ValueError("Máy này chưa bật thông báo")
                subs[ep]["prefs"] = {k: bool((body.get("prefs") or {}).get(k, False)) for k in KINDS}
                self._save(subs)
                return {"ok": True, "message": "Đã lưu"}
            if act == "unsubscribe":
                subs.pop(ep, None)
                self._save(subs)
                return {"ok": True, "message": "Đã tắt thông báo cho máy này"}
            if act == "test":
                if ep not in subs:
                    raise ValueError("Máy này chưa bật thông báo")
                ok = self._send_one(ep, subs[ep], {"title": "🦆 DuckyMoMo", "body": "Thông báo thử: máy này nhận được rồi.", "tag": "thu", "url": "thongbao"})
                return {"ok": ok, "message": "Đã gửi thông báo thử" if ok else "Gửi không được (máy đã tắt quyền? bật lại)"}
        raise ValueError("Thao tác lạ")

    # ------------------------------------------------------------ gửi
    def _send_one(self, ep, s, payload):
        from pywebpush import webpush, WebPushException
        v, _ = self._keys()
        try:
            webpush(subscription_info=s["sub"], data=json.dumps(payload, ensure_ascii=False), vapid_private_key=v,
                    vapid_claims={"sub": "mailto:admin@duckymomo.vn"}, ttl=600, timeout=10)
            return True
        except WebPushException as e:
            code = getattr(e.response, "status_code", 0) if getattr(e, "response", None) is not None else 0
            if code in (404, 410):   # máy đã gỡ đăng ký / app đã xoá
                subs = self._load()
                subs.pop(ep, None)
                self._save(subs)
            self.log(f"Thông báo: gửi lỗi {code} {str(e)[:120]}")
            return False
        except Exception as e:
            self.log(f"Thông báo: gửi lỗi {e}")
            return False

    def push(self, kind, title, body, tag="", url="hoatdong"):
        payload = {"title": title, "body": body, "tag": tag or kind, "url": url}
        with self.lock:
            self.sent.append({"t": int(time.time()), "loai": kind, "tieu_de": title, "noi_dung": body})
            del self.sent[:-50]
            try:
                self.log_file.write_text(json.dumps(self.sent, ensure_ascii=False), encoding="utf-8")
            except OSError:
                pass
            subs = self._load()
        for ep, s in subs.items():
            if (s.get("prefs") or {}).get(kind, True):
                self._send_one(ep, s, payload)

    # ------------------------------------------------------------ theo dõi
    def start(self):
        threading.Thread(target=self._watch_activity, daemon=True).start()
        threading.Thread(target=self._watch_servers, daemon=True).start()

    def _skip(self, name):
        n = (name or "").lower()
        try:
            return n in self.bot_names() or n in self.staff_names()
        except Exception:
            return False

    def _watch_activity(self):
        seen, joined = set(), {}
        pos = 0
        try:   # ai đã từng vào (để biết "lần đầu"), rồi bắt đầu đọc từ cuối file
            with self.activity_file.open("rb") as f:
                for line in f:
                    try:
                        e = json.loads(line)
                    except ValueError:
                        continue
                    if e.get("server") == "proxy" and str(e.get("text", "")).endswith(" vào network"):
                        seen.add(str(e.get("player", "")).lower())
                pos = f.tell()
        except OSError:
            pass
        while True:
            time.sleep(3)
            try:
                size = self.activity_file.stat().st_size
                if size < pos:
                    pos = 0
                if size == pos:
                    continue
                with self.activity_file.open("rb") as f:
                    f.seek(pos)
                    chunk = f.read()
                    pos = f.tell() if chunk.endswith(b"\n") else pos + chunk.rfind(b"\n") + 1
                for line in chunk.split(b"\n"):
                    try:
                        e = json.loads(line)
                    except ValueError:
                        continue
                    self._event(e, seen, joined)
            except Exception as ex:
                self.log(f"Thông báo: lỗi đọc hoạt động {ex}")

    def _event(self, e, seen, joined):
        name = str(e.get("player", ""))
        text = str(e.get("text", ""))
        if not name or self._skip(name):
            return
        low = name.lower()
        if e.get("server") == "proxy" and text.endswith(" vào network"):
            first = low not in seen
            seen.add(low)
            joined[low] = time.time()
            self.push("vaora", f"🟢 {name} vào server", "Lần đầu vào DuckyMoMo" if first else "Đã từng chơi", tag="vao-" + low)
        elif e.get("server") == "proxy" and text.endswith(" rời network"):
            t0 = joined.pop(low, None)
            mins = f"chơi {max(1, round((time.time() - t0) / 60))} phút" if t0 else "rời mạng"
            self.push("vaora", f"🔴 {name} thoát", mins, tag="ra-" + low)
        elif e.get("type") == "chat" and e.get("server") != "proxy":
            msg = re.sub(r"^<[^>]+>\s*", "", text)[:180]
            self.push("chat", f"💬 {name}", msg, tag="chat-" + low)

    def _watch_servers(self):
        down, low_tps, last_tps_alert, last_bots = {}, 0, 0, None
        NAMES = {"lobby": "Sảnh", "banghoi-1": "Bang Hội Chiến", "practice-1": "Practice", "sangtao-1": "Thái Ấp"}
        time.sleep(60)
        while True:
            try:
                st = self.server_status()
                for srv, (ok, tps) in st.items():
                    c = down.get(srv, 0)
                    if not ok:
                        down[srv] = c + 1
                        if c + 1 == 2:   # 2 lần liền (~2 phút) mới báo, khỏi báo nhầm lúc khởi động lại nhanh
                            self.push("suco", f"⚠️ {NAMES.get(srv, srv)} không phản hồi", "Server tắt hoặc đang khởi động lại", tag="tat-" + srv)
                    else:
                        if c >= 2:
                            self.push("suco", f"✅ {NAMES.get(srv, srv)} hoạt động lại", "Server đã phản hồi", tag="tat-" + srv)
                        down[srv] = 0
                    if srv == "banghoi-1" and ok and tps is not None:
                        low_tps = low_tps + 1 if tps < 15 else 0
                        if low_tps >= 3 and time.time() - last_tps_alert > 1800:
                            last_tps_alert = time.time()
                            self.push("suco", "🐢 Bang Hội Chiến đang lag", f"TPS {tps:.1f} (dưới 15 suốt ~3 phút)", tag="tps")
                n = self.botfarm_online()
                if n is not None and last_bots is not None and last_bots - n >= 8:   # thay phiên lúc vắng có thể cho 3–6 bot thoát / phút
                    self.push("suco", "🤖 Bot rớt hàng loạt", f"Online {last_bots} → {n} bot trong 1 phút", tag="bot")
                last_bots = n
            except Exception as ex:
                self.log(f"Thông báo: lỗi theo dõi server {ex}")
            time.sleep(60)
