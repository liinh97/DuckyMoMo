"""Set Chuyên Cần (29/10): 4 món giáp x +0..+5, đổi bằng Phiếu Chuyên Cần (điểm danh + online). Ghi vào thư viện (data) + config (repo + data)."""
import shutil, yaml, re
R = "/home/developer/works/DuckyMoMo/"
LIB = R + "data/banghoi-1/plugins/DuckyMoMoCore/thuvien-do.yml"

PIECES = [("mu", "Mũ", "HELMET"), ("ao", "Giáp", "CHESTPLATE"), ("quan", "Quần", "LEGGINGS"), ("giay", "Giày", "BOOTS")]
RAR = ["&aCommon", "&bUncommon", "&bUncommon", "&9Rare", "&9Rare", "&5Epic"]
PROT = [2, 3, 3, 3, 4, 4]
UNB = [2, 3, 3, 3, 4, 4]
PROJ = [0, 0, 2, 2, 3, 3]
MAU = [0, 2, 3, 4, 5, 6]
QUOTE = ["Ngày đầu đến làng, áo còn thơm mùi vải mới…", "Sáng nào cũng thấy mặt ở quảng trường…", "Già Làng bắt đầu nhớ tên bạn rồi đấy…",
         "Bền như người chẳng bỏ buổi nào…", "Cả làng đã quen tiếng bước chân này…", "Ngày công dệt thành sợi chỉ vàng trên áo…"]

items = {}
for key, vn, slot in PIECES:
    for lv in range(6):
        mat = ("IRON_" if lv <= 2 else "DIAMOND_") + slot
        ench = {"protection": PROT[lv], "unbreaking": UNB[lv]}
        if PROJ[lv]:
            ench["projectile_protection"] = PROJ[lv]
        perks = []
        if key == "mu":
            r = 1 if lv >= 1 else 0
            r = 2 if lv >= 3 else r
            r = 3 if lv >= 5 else r
            if r:
                ench["respiration"] = r
                perks.append(f"&7• Thở dưới nước lâu &8(Thở dưới nước {'I' * r})")
            if lv >= 4:
                ench["aqua_affinity"] = 1
                perks.append("&7• Đào dưới nước nhanh &8(Thợ lặn)")
        if key == "ao":
            perks.append("&7• Áo dày: thêm &c+1% &7sinh lực so với món khác")
        if key == "quan" and lv >= 2:
            ss = 1 if lv < 4 else 2
            ench["swift_sneak"] = ss
            perks.append(f"&7• Ngồi vẫn đi nhanh &8(Lén nhanh {'I' * ss})")
        if key == "giay" and lv >= 1:
            ff = 1 if lv < 3 else 2 if lv < 5 else 4
            ench["feather_falling"] = ff
            perks.append(f"&7• Ngã nhẹ hơn &8(Rơi nhẹ {['', 'I', 'II', 'III', 'IV'][ff]})")
            if lv >= 4:
                ench["depth_strider"] = 1
                perks.append("&7• Lội nước nhanh &8(Sải chân I)")
        if lv >= 5:
            ench["mending"] = 1
            perks.append("&a&l• Tự vá: &akinh nghiệm nhặt được sửa đồ &8(Tu sửa)")
        if not perks:
            perks.append("&8• Lên +1 để mở nếp chăm đầu tiên")
        mau = MAU[lv] + (1 if key == "ao" and lv >= 1 else 0)
        dots = "".join("&8[&e☀&8]" if j <= lv else "&8[&7·&8]" for j in range(6))
        lore = [
            "&6━━━ &e☀ &6&lCHUYÊN CẦN &e☀ &6━━━",
            f"&e▪ &7Món: &f{vn}   &7Phẩm: {RAR[lv]}",
            f"&e▪ &7Ngày công: {dots}",
            "&b&l▸ SỨC BỀN",
            f"&7• +{mau}% sinh lực tối đa" if mau else "&7• Giáp sắt vững chãi cho người mới",
            "",
            "&a&l▸ NẾP CHĂM",
            *perks,
            "&7• Đủ 4 món: &c+máu&7, &e+kinh nghiệm &8(/thoren)",
            "&6━━━━━━━━━━━━━━━━━━━",
            "",
            f'&e&o"{QUOTE[lv]}"',
        ]
        it = {
            "nguon": "web", "nhom": "giap", "ten-goi": f"{vn} Chuyên Cần +{lv}", "material": mat, "amount": 1,
            "name": f"&e☀ &6&l{vn} Chuyên Cần &e☀ &f+{lv}", "lore": lore, "enchants": ench,
        }
        if mau:
            it["attributes"] = {"pct_mau": mau}
        it.update({"compact-enchants": True, "hide-stats": True, "bo": ["Chuyên Cần", "Đồ set"], "nguoi-luu": "Claude (Thợ Rèn)"})
        items[f"cn_{key}_{lv}"] = it
items["phieu_dd"] = {
    "nguon": "web", "nhom": "nguyen-lieu", "ten-goi": "Phiếu Chuyên Cần", "material": "SUNFLOWER", "amount": 1,
    "name": "&e☀ &6Phiếu Chuyên Cần",
    "lore": ["&7Nhận khi &f/diemdanh &7mỗi ngày, ở mốc điểm danh,", "&7và mỗi giờ online hoạt động &8(tối đa 2/ngày)&7.",
             "&a⚑ Đổi: &fset Chuyên Cần ở Thợ Rèn &8(/thoren)"],
    "glint": True, "cong-dung": "tinh-luyen",
}

# ---- thư viện (data): chèn ngay sau "items:" (giữ nguyên phần còn lại của tệp)
src = open(LIB, encoding="utf-8").read()
if "\n  cn_mu_0:" in src or src.startswith("items:\n  cn_mu_0:"):
    print("thư viện đã có cn_*")
else:
    shutil.copy(LIB, LIB + ".bak-29-10-chuyencan")
    block = yaml.safe_dump(items, allow_unicode=True, sort_keys=False, width=1000)
    block = "".join("  " + l + "\n" if l else "\n" for l in block.splitlines())
    assert src.startswith("items:\n"), src[:20]
    src = "items:\n" + block + src[len("items:\n"):]
    y = yaml.safe_load(src)
    assert all(k in y["items"] for k in items), "thiếu món"
    open(LIB, "w", encoding="utf-8", newline="").write(src)
    print("thư viện: thêm", len(items), "món; tổng", len(y["items"]))

# ---- config (repo + data)
SET = '''    chuyencan:
      name: Chuyên Cần
      icon: SUNFLOWER
      desc: 'Set điểm danh: đổi bằng Phiếu Chuyên Cần (điểm danh mỗi ngày, mốc điểm danh, mỗi giờ online). Đủ bộ thêm máu và kinh nghiệm, +5 tự vá đồ.'
      cost:
      - {xu: 200, phieu_dd: 3}
      - {xu: 300, phieu_dd: 3}
      - {xu: 500, phieu_dd: 4}
      - {xu: 800, phieu_dd: 5}
      - {xu: 1200, phieu_dd: 7}
      - {xu: 1800, phieu_dd: 9}
      wear: [80, 72, 64, 56, 48, 40]
      bonus:
      - {from: 0, pieces: 4, mau: 4, kn: 10}
      - {from: 3, pieces: 4, mau: 8, day-lui: 5, kn: 20}
      - {from: 5, pieces: 4, mau: 12, day-lui: 10, giam-st: 3, kn: 30}
      pieces:
''' + "".join(f"        {k}: {{name: {vn}, ids: [{', '.join(f'cn_{k}_{i}' for i in range(6))}]}}\n" for k, vn, _ in PIECES)

for f in ("modes/banghoi/server/plugins/DuckyMoMoCore/config.yml", "data/banghoi-1/plugins/DuckyMoMoCore/config.yml"):
    p = R + f
    t = open(p, encoding="utf-8").read()
    if "\n    chuyencan:\n" not in t:
        a = "\n    nonggia:\n"
        assert t.count(a) == 1, (f, t.count(a))
        t = t.replace(a, "\n" + SET.rstrip("\n") + a, 1)
    t = re.sub(r"(\n  guard: \[[^\]]*?)(\])", lambda m: m.group(1) + ("" if "phieu_dd" in m.group(1) else ", phieu_dd") + m.group(2), t, count=1)
    if "do phieu_dd" not in t:
        t = t.replace('''  daily-rewards:
    - xu 150
    - do chia_khoa_diemdanh 1
''', '''  daily-rewards:
    - xu 150
    - do chia_khoa_diemdanh 1
    - do phieu_dd 1           # 29/10: Phiếu Chuyên Cần, đổi set Chuyên Cần ở Thợ Rèn
''')
        t = t.replace('''    7:
      - xu 750
      - vat GOLDEN_APPLE 2
''', '''    7:
      - xu 750
      - vat GOLDEN_APPLE 2
      - do phieu_dd 2
''')
        t = t.replace('''    14:
      - xu 1500
      - vat DIAMOND 3
''', '''    14:
      - xu 1500
      - vat DIAMOND 3
      - do phieu_dd 3
''')
        t = t.replace('''    30:
      - xu 4500
      - vat DIAMOND 8
daily-quests:''', '''    30:
      - xu 4500
      - vat DIAMOND 8
      - do phieu_dd 5
  # Phiếu online (29/10, set Chuyên Cần): mỗi every-minutes phút ĐANG HOẠT ĐỘNG trong ngày (đứng im quá idle-minutes không tính, bot không tính)
  # nhận 1 item, tối đa max-per-day. Một tháng chăm: ~30 (điểm danh) + 10 (mốc) + ~60 (online) phiếu; cả bộ +5 cần 124 phiếu.
  online-ticket:
    enabled: true
    item: phieu_dd
    every-minutes: 60
    max-per-day: 2
    idle-minutes: 5
    set: chuyencan            # nút [Đổi set Chuyên Cần] ở /diemdanh mở /thoren set <mã>
daily-quests:''')
    y = yaml.safe_load(t)
    assert "chuyencan" in y["tho-ren"]["sets"] and "phieu_dd" in y["tho-ren"]["guard"]
    assert y["daily-checkin"]["online-ticket"]["item"] == "phieu_dd", f
    assert "do phieu_dd 2" in y["daily-checkin"]["milestones"][7]
    open(p, "w", encoding="utf-8", newline="").write(t)
    print("config ok", f, len(y["tho-ren"]["sets"]), "set")
