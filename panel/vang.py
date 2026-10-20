"""Vàng: tiền ủng hộ (20/10). Nạp tiền thật -> admin duyệt -> cộng Vàng; người chơi tự tiêu Vàng mua rank ủng hộ, đồ trang trí, tên.

- Ví lưu MariaDB database `network`: bảng vang_vi (số dư, tổng nạp) + vang_log (mọi giao dịch, ref duy nhất -> không cộng 2 lần).
- KHÔNG chuyển Vàng cho người khác, KHÔNG đổi ra xu / đồ chơi game (luật Mojang: tiền thật chỉ mua trang trí).
- Cấu hình: config DuckyMoMoCore ở Bang Hội Chiến (taikhoan.DMM), mục `vang` (tỉ giá, mốc thưởng nạp, món bán lẻ); rank lấy từ
  donate-ranks (giá tiền thật '1 tháng 20k' quy ra Vàng theo tỉ giá). Sửa tỉ giá / mốc thưởng: Cài đặt › Toàn mạng › Rank donate.
- Mua: trừ Vàng trước (trong giao dịch DB), rồi chạy lệnh cấp qua RCON sảnh; lệnh lỗi thì HOÀN Vàng.
"""
import math
import re
import threading
import time

import taikhoan

_lock = threading.Lock()
NAME_RE = re.compile(r"^\.?[A-Za-z0-9_]{1,17}$")


def _db():
    return taikhoan.db("network")


def ensure_tables():
    con = _db()
    try:
        with con.cursor() as c:
            c.execute("""CREATE TABLE IF NOT EXISTS vang_vi (
                ten VARCHAR(32) PRIMARY KEY, ten_that VARCHAR(32) NOT NULL, so_du BIGINT NOT NULL DEFAULT 0,
                tong_nap BIGINT NOT NULL DEFAULT 0, cap_nhat BIGINT NOT NULL DEFAULT 0) CHARACTER SET utf8mb4""")
            c.execute("""CREATE TABLE IF NOT EXISTS vang_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, luc BIGINT NOT NULL, ten VARCHAR(32) NOT NULL, thay_doi BIGINT NOT NULL,
                so_du_sau BIGINT NOT NULL, loai VARCHAR(16) NOT NULL, ly_do VARCHAR(255) NOT NULL, ref VARCHAR(64) NULL,
                boi VARCHAR(32) NOT NULL DEFAULT '', UNIQUE KEY ref_u (ref), KEY ten_k (ten, luc)) CHARACTER SET utf8mb4""")
        con.commit()
    finally:
        con.close()


# ---------------------------------------------------------------- cấu hình

def cfg():
    core = taikhoan.safe_yaml(taikhoan.DMM / "config.yml")
    v = core.get("vang") or {}
    rate = max(1, int(v.get("ty-gia", 1000) or 1000))            # bao nhiêu đồng = 1 Vàng
    bonus = []
    for line in v.get("thuong-nap") or []:                        # "200000:10" = nạp từ 200.000đ thưởng thêm 10%
        m = re.match(r"^\s*([\d.]+)\s*[:=]\s*(\d+)", str(line))
        if m:
            bonus.append((int(m.group(1).replace(".", "")), int(m.group(2))))
    bonus.sort()
    return {"ty_gia": rate, "thuong": bonus, "nap_toi_thieu": int(v.get("nap-toi-thieu", 10000) or 10000),
            "mon": v.get("mon") or [], "core": core}


def vang_for(tien, c=None):
    """Số Vàng nhận được khi nạp `tien` đồng (đã cộng thưởng mốc cao nhất đạt được)."""
    c = c or cfg()
    base = int(tien) // c["ty_gia"]
    pct = 0
    for muc, p in c["thuong"]:
        if tien >= muc:
            pct = p
    return base + base * pct // 100, pct


def shop(donate_tiers):
    """Danh sách món: rank (từ donate-ranks, giá quy theo tỉ giá) + món lẻ (vang.mon)."""
    c = cfg()
    out = []
    for t in donate_tiers():
        for g in t["goi"]:
            out.append({"ma": f"rank:{t['group']}:{g['thang']}", "loai": "rank", "ten": f"{t['ten']} {g['thang']} tháng",
                        "mo_ta": "Rank ủng hộ: tiền tố, trang trí, không cho sức mạnh. Mua thêm thì cộng dồn thời hạn.",
                        "gia": max(1, math.ceil(g["tien"] / c["ty_gia"])), "ngay": g["thang"] * 30,
                        "lenh": [f"rank give {{player}} {t['group']} {g['thang']}"], "nhom": t["ten"]})
    for m in c["mon"]:
        if not isinstance(m, dict) or not m.get("ma") or not m.get("lenh"):
            continue
        out.append({"ma": str(m["ma"]), "loai": str(m.get("loai", "trangtri")), "ten": str(m.get("ten", m["ma"])),
                    "mo_ta": str(m.get("mo-ta", "")), "gia": max(1, int(m.get("gia", 1))), "ngay": int(m.get("ngay", 0) or 0),
                    "lenh": [str(x) for x in m.get("lenh") or []], "nhom": str(m.get("nhom", "Trang trí"))})
    return out


# ---------------------------------------------------------------- ví

def balance(name):
    con = _db()
    try:
        with con.cursor() as c:
            c.execute("SELECT so_du, tong_nap FROM vang_vi WHERE ten=%s", (name.lower(),))
            r = c.fetchone()
        return {"so_du": int(r[0]) if r else 0, "tong_nap": int(r[1]) if r else 0}
    finally:
        con.close()


def balances(names):
    """Số dư của nhiều người (bảng bên phải trong game hỏi mỗi phút): {tên thường: số dư}."""
    names = [n.lower() for n in names if NAME_RE.match(n)][:200]
    if not names:
        return {}
    con = _db()
    try:
        with con.cursor() as c:
            c.execute("SELECT ten, so_du FROM vang_vi WHERE ten IN (" + ",".join(["%s"] * len(names)) + ")", names)
            got = {r[0]: int(r[1]) for r in c.fetchall()}
        return {n: got.get(n, 0) for n in names}
    finally:
        con.close()


def history(name, limit=20):
    con = _db()
    try:
        with con.cursor() as c:
            c.execute("SELECT luc, thay_doi, so_du_sau, loai, ly_do FROM vang_log WHERE ten=%s ORDER BY id DESC LIMIT %s",
                      (name.lower(), int(limit)))
            return [{"luc": r[0], "thay_doi": r[1], "so_du_sau": r[2], "loai": r[3], "ly_do": r[4]} for r in c.fetchall()]
    finally:
        con.close()


def change(name, delta, loai, ly_do, ref=None, boi="", nap=0):
    """Cộng/trừ Vàng trong 1 giao dịch. ref trùng (đã cộng rồi) -> không làm gì, trả None. Không đủ Vàng -> ValueError."""
    if not NAME_RE.match(name):
        raise ValueError("Tên không hợp lệ")
    now = int(time.time() * 1000)
    with _lock:
        con = _db()
        try:
            con.begin()
            with con.cursor() as c:
                if ref:
                    c.execute("SELECT 1 FROM vang_log WHERE ref=%s", (ref,))
                    if c.fetchone():
                        con.rollback()
                        return None
                c.execute("SELECT so_du FROM vang_vi WHERE ten=%s FOR UPDATE", (name.lower(),))
                r = c.fetchone()
                cur = int(r[0]) if r else 0
                after = cur + int(delta)
                if after < 0:
                    con.rollback()
                    raise ValueError(f"Không đủ Vàng (có {cur}, cần {-int(delta)}).")
                if r:
                    c.execute("UPDATE vang_vi SET so_du=%s, tong_nap=tong_nap+%s, cap_nhat=%s, ten_that=%s WHERE ten=%s",
                              (after, int(nap), now, name, name.lower()))
                else:
                    c.execute("INSERT INTO vang_vi (ten, ten_that, so_du, tong_nap, cap_nhat) VALUES (%s,%s,%s,%s,%s)",
                              (name.lower(), name, after, int(nap), now))
                c.execute("INSERT INTO vang_log (luc, ten, thay_doi, so_du_sau, loai, ly_do, ref, boi) VALUES (%s,%s,%s,%s,%s,%s,%s,%s)",
                          (now, name.lower(), int(delta), after, loai, ly_do[:255], ref, boi[:32]))
            con.commit()
            return after
        except Exception:
            try:
                con.rollback()
            except Exception:
                pass
            raise
        finally:
            con.close()


def known_player(name):
    """Người chơi đã từng vào server (có trong LuckPerms). Chưa có thì lệnh lp bỏ qua KHÔNG báo lỗi (RCON trả rỗng vì LuckPerms
    trả lời bất đồng bộ) -> phải kiểm trước khi trừ Vàng."""
    con = taikhoan.db("luckperms")
    try:
        with con.cursor() as c:
            c.execute("SELECT 1 FROM luckperms_players WHERE username=%s", (name.lower(),))
            return c.fetchone() is not None
    finally:
        con.close()


def buy(name, ma, donate_tiers, run_cmds):
    """Mua 1 món: trừ Vàng, chạy lệnh cấp (run_cmds(list) -> None, ném lỗi nếu hỏng); lỗi thì hoàn Vàng."""
    item = next((m for m in shop(donate_tiers) if m["ma"] == ma), None)
    if not item:
        raise ValueError("Không có món này.")
    if not known_player(name):
        raise ValueError("Tài khoản này chưa vào server lần nào. Vào game một lần rồi mua.")
    after = change(name, -item["gia"], "mua", f"Mua {item['ten']}")
    try:
        run_cmds([x.replace("{player}", name) for x in item["lenh"]])
    except Exception as e:
        change(name, item["gia"], "hoan", f"Hoàn: cấp {item['ten']} lỗi ({str(e)[:80]})")
        raise ValueError("Server đang bận, chưa cấp được. Đã hoàn Vàng, thử lại sau ít phút.")
    return {"ok": True, "so_du": after, "message": f"Đã mua {item['ten']} (-{item['gia']} Vàng). Còn {after} Vàng."}
