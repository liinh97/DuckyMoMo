"""Sinh cây kỹ năng MyPet: HỆ (người chơi chọn) × NHÓM LOÀI (bản tính con vật) + đặc tính riêng từng loài.

Ý tưởng: lúc bắt mọi loài như nhau (20 máu). Lớn lên thì đi theo bản tính: thú lớn trâu nhưng đánh yếu (tank), thú săn đánh đau
nhưng mỏng, loài biết bay mới cưỡi bay được, cung thủ bắn xa... Người chơi vẫn chọn hệ (Chiến Binh, Hậu Cần, Đấu Sĩ, Thú Cưỡi,
Nông Trại); trong menu chỉ thấy 5 hệ của đúng loài mình.

Cách MyPet làm: 5 cây hệ gốc (tools/mypet/he.json) đặt MobTypes rỗng nên không ai chọn được; mỗi cây "<Hệ>_<mã>" kế thừa
(Inheritance) cây hệ gốc rồi cộng thêm tăng trưởng của nhóm + đặc tính loài, MobTypes = các loài đó.

Chạy (thư mục gốc repo):  python3 tools/mypet/sinh-cay-ky-nang.py
Ghi vào modes/banghoi/server/plugins/MyPet/skilltrees/ (image itzg chép sang data khi server khởi động). MyPet KHÔNG có lệnh nạp
lại: phải khởi động lại banghoi-1. Pet đang dùng cây đã đổi tên/xoá sẽ mất hệ (phải chọn lại).
"""
import json
import re
import sys
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "modes" / "banghoi" / "server" / "plugins" / "MyPet" / "skilltrees"
HE = json.loads((ROOT / "tools" / "mypet" / "he.json").read_text(encoding="utf-8"))
HE_TEN = {"Combat": "Chiến Binh", "Utility": "Hậu Cần", "PvP": "Đấu Sĩ", "Ride": "Thú Cưỡi", "Farm": "Nông Trại"}
LINE = "<dark_gray><st>                              </st></dark_gray>"


def khung(goc, nhom=None):
    """Mô tả trong menu chọn hệ theo khung tooltip chung (Tip.java, 20/10): ▍ mô tả · • Kỹ năng ▸ … · • Mốc cấp ▸ … · • Nhóm loài ▸ …
    goc: Description của hệ trong he.json (dòng đầu = mô tả, "- " = kỹ năng, "<gold>Cấp" = mốc cấp). nhom: dòng thêm của nhóm loài."""
    out, ky, moc = [], [], []
    for i, x in enumerate(goc):
        if i == 0:
            out.append("<gold>▍</gold> <white>" + re.sub(r"^<gray>", "", x).rstrip(":") + "</white>")
        elif x.startswith("- "):
            ky.append("<dark_gray>  ▸</dark_gray> <white>" + x[2:])
        else:
            moc.append("<dark_gray>  ▸</dark_gray> " + re.sub(r"^<gold>(Cấp [^:]+:)", r"<yellow>\1</yellow><white>", x))
    out.append(LINE)
    if ky:
        out += ["<dark_gray>•</dark_gray> <gold>Kỹ năng:</gold>"] + ky
    if moc:
        out += ["<dark_gray>•</dark_gray> <gold>Mốc cấp:</gold>"] + moc
    if nhom:
        out.append(LINE)
        out += nhom
    return out

# Tăng trưởng mỗi 5 cấp sau cấp 10 (cấp 15, 20, ... 100 = 18 lần). Trước đây mọi loài: +2 máu, +0,5 sát thương.
# "bay": cưỡi bay được (chỉ ở hệ Thú Cưỡi). "them": kỹ năng thêm ở MỌI hệ của nhóm. "he": thêm chỉ ở một hệ.
NHOM = {
    "thulon": {
        "ten": "Thú lớn", "vai": "trâu, đỡ đòn, đánh yếu", "mau": 4, "dame": 0.25,
        "loai": ["PolarBear", "Panda", "Hoglin", "Zoglin", "Sniffer", "Turtle", "Armadillo", "IronGolem", "Ravager", "Warden",
                 "Giant", "CopperGolem", "Creaking"],
        "them": {"Life": {"5": {"health": "+3"}}, "Shield": {"20": {"chance": "+10", "redirect": "+15"}},
                 "Thorns": {"30": {"chance": "+10", "reflection": "+10"}}},
        "mo_ta": ["Cấp 5: +3 máu · cấp 20: Hộ chủ · cấp 30: Gai"],
    },
    "thusan": {
        "ten": "Thú săn", "vai": "đánh đau, nhanh, máu mỏng", "mau": 1.5, "dame": 0.75,
        "loai": ["Wolf", "Fox", "Cat", "Ocelot", "Spider", "CaveSpider", "Enderman", "Vindicator", "Silverfish", "Endermite",
                 "PiglinBrute"],
        "them": {"Sprint": {"5": {"active": True}}, "Bleed": {"20": {"chance": "+8", "damage": "+1", "duration": "+3"}}},
        "mo_ta": ["Cấp 5: Chạy nhanh · cấp 20: Chảy máu"],
    },
    "bay": {
        "ten": "Loài bay", "vai": "bay được, máu mỏng", "mau": 1.5, "dame": 0.5, "bay": True,
        "loai": ["Allay", "Bat", "Bee", "Blaze", "Breeze", "Parrot", "Phantom", "Vex", "Ghast", "Wither", "EnderDragon"],
        "them": {},
        "mo_ta": ["Hệ Thú Cưỡi: bay được từ cấp 5 (40 khối mỗi lần · cấp 20: 80 · 30: 150 · 50: 250)"],
    },
    "cuoi": {
        "ten": "Thú cưỡi", "vai": "chạy nhanh, nhảy cao, thồ hàng", "mau": 2.5, "dame": 0.4,
        "loai": ["Horse", "Donkey", "Mule", "Llama", "TraderLlama", "Camel", "SkeletonHorse", "ZombieHorse", "Strider", "Pig", "Goat"],
        "them": {"Backpack": {"15": {"rows": "+1", "drop": False}}},
        "he": {"Ride": {"Ride": {"10": {"speed": "+5"}, "20": {"speed": "+5", "jumpheight": "+0.5"}, "40": {"speed": "+5", "jumpheight": "+0.5"}}}},
        "mo_ta": ["Cấp 15: túi đồ +1 hàng · hệ Thú Cưỡi: chạy nhanh hơn (cấp 10, 20, 40), nhảy cao hơn (cấp 20, 40)"],
    },
    "xacsong": {
        "ten": "Xác sống", "vai": "lì đòn, đánh khá", "mau": 3, "dame": 0.5,
        "loai": ["Zombie", "Husk", "Drowned", "ZombieVillager", "ZombifiedPiglin", "PigZombie", "WitherSkeleton"],
        "them": {"Heal": {"20": {"health": "+1", "timer": "+30"}}},
        "mo_ta": ["Cấp 20: tự hồi máu"],
    },
    "cungthu": {
        "ten": "Cung thủ", "vai": "bắn xa, máu mỏng", "mau": 1.5, "dame": 0.25,
        "loai": ["Skeleton", "Stray", "Bogged", "Pillager", "Piglin"],
        "them": {"Ranged": {"5": {"projectile": "Arrow", "rate": "+30", "damage": "+1.5"}, "%5>10": {"damage": "+0.5"}}},
        "mo_ta": ["Cấp 5: bắn tên ở mọi hệ, mỗi 5 cấp sau cấp 10 tên +0,5 sát thương"],
    },
    "phuthuy": {
        "ten": "Phù thủy", "vai": "phép, hiệu ứng xấu lên địch", "mau": 2, "dame": 0.35,
        "loai": ["Witch", "Evoker", "Illusioner"],
        "them": {"Heal": {"10": {"health": "+1", "timer": "+40"}}},
        "mo_ta": ["Cấp 10: tự hồi máu"],
    },
    "thuytoc": {
        "ten": "Thủy tộc", "vai": "dưới nước, hào quang thở dưới nước", "mau": 2, "dame": 0.5,
        "loai": ["Axolotl", "Cod", "Salmon", "Pufferfish", "TropicalFish", "Squid", "GlowSquid", "Dolphin", "Guardian",
                 "ElderGuardian", "Tadpole", "Frog"],
        "them": {"Beacon": {"15": {"buffs": {"waterbreathing": True}, "count": "+1", "duration": "+8", "range": "+8"}}},
        "mo_ta": ["Cấp 15: hào quang thở dưới nước cho chủ"],
    },
    "giasuc": {
        "ten": "Gia súc", "vai": "hiền, trâu, mang đồ, đánh rất yếu", "mau": 3, "dame": 0.2,
        "loai": ["Cow", "Mooshroom", "Sheep", "Chicken", "Rabbit", "Villager", "WanderingTrader", "Snowman"],
        "them": {"Backpack": {"10": {"rows": "+1", "drop": False}}, "Pickup": {"15": {"range": "+1"}},
                 "Heal": {"20": {"health": "+1", "timer": "+40"}}},
        "mo_ta": ["Cấp 10: túi đồ +1 hàng · cấp 15: nhặt đồ xa hơn · cấp 20: tự hồi máu"],
    },
    "nhay": {
        "ten": "Quái nhầy & nổ", "vai": "dậm đất gây sát thương xung quanh", "mau": 2.5, "dame": 0.4,
        "loai": ["Creeper", "Slime", "MagmaCube"],
        "them": {"Stomp": {"10": {"chance": "+10", "damage": "+2"}, "30": {"chance": "+5", "damage": "+2"}}},
        "mo_ta": ["Cấp 10, 30: Dậm đất"],
    },
}

# Đặc tính riêng từng loài (mọi hệ). Mỗi mục: (dòng mô tả, {kỹ năng: {cấp: nâng cấp}}).
LOAI = {
    "Bee": ("Ong: đốt có độc (cấp 10)", {"Poison": {"10": {"chance": "+15", "duration": "+3"}}}),
    "Spider": ("Nhện: độc (cấp 15)", {"Poison": {"15": {"chance": "+10", "duration": "+2"}}}),
    "CaveSpider": ("Nhện hang: độc mạnh (cấp 10)", {"Poison": {"10": {"chance": "+15", "duration": "+4"}}}),
    "Blaze": ("Blaze: đốt lửa (cấp 10), kháng lửa cho chủ (cấp 20)",
              {"Fire": {"10": {"chance": "+15", "duration": "+3"}},
               "Beacon": {"20": {"buffs": {"fireresistance": True}, "count": "+1", "duration": "+8", "range": "+8"}}}),
    "MagmaCube": ("Khối magma: đốt lửa (cấp 15)", {"Fire": {"15": {"chance": "+15", "duration": "+3"}}}),
    "Strider": ("Strider: kháng lửa cho chủ (cấp 15)",
                {"Beacon": {"15": {"buffs": {"fireresistance": True}, "count": "+1", "duration": "+8", "range": "+8"}}}),
    "WitherSkeleton": ("Bộ xương Wither: héo mòn (cấp 10)", {"Wither": {"10": {"chance": "+10", "duration": "+3"}}}),
    "Wither": ("Wither: héo mòn (cấp 10)", {"Wither": {"10": {"chance": "+15", "duration": "+3"}}}),
    "Stray": ("Stray: tên làm chậm (cấp 10)", {"Slow": {"10": {"chance": "+15", "duration": "+2"}}}),
    "Bogged": ("Bogged: tên có độc (cấp 10)", {"Poison": {"10": {"chance": "+15", "duration": "+2"}}}),
    "Snowman": ("Người tuyết: ném cầu tuyết làm chậm (cấp 5, 15)",
                {"Ranged": {"5": {"projectile": "Snowball", "rate": "+30", "damage": "+1"}},
                 "Slow": {"15": {"chance": "+15", "duration": "+2"}}}),
    "Llama": ("Lạc đà không bướu: phun nước bọt (cấp 10)", {"Ranged": {"10": {"projectile": "LlamaSpit", "rate": "+25", "damage": "+1.5"}}}),
    "TraderLlama": ("Lạc đà thương nhân: phun nước bọt (cấp 10)", {"Ranged": {"10": {"projectile": "LlamaSpit", "rate": "+25", "damage": "+1.5"}}}),
    "Pufferfish": ("Cá nóc: độc + gai (cấp 5, 15)", {"Poison": {"5": {"chance": "+20", "duration": "+3"}},
                                                    "Thorns": {"15": {"chance": "+15", "reflection": "+10"}}}),
    "Guardian": ("Guardian: gai (cấp 10)", {"Thorns": {"10": {"chance": "+15", "reflection": "+15"}}}),
    "ElderGuardian": ("Elder Guardian: gai (cấp 10)", {"Thorns": {"10": {"chance": "+20", "reflection": "+15"}}}),
    "Witch": ("Phù thủy: ném độc (cấp 10), hồi máu mạnh (cấp 20)",
              {"Poison": {"10": {"chance": "+20", "duration": "+3"}}, "Heal": {"20": {"health": "+1"}}}),
    "Evoker": ("Evoker: sét (cấp 15)", {"Lightning": {"15": {"chance": "+8", "damage": "+3"}}}),
    "Illusioner": ("Illusioner: sét (cấp 15)", {"Lightning": {"15": {"chance": "+8", "damage": "+3"}}}),
    "Goat": ("Dê: húc văng (cấp 10)", {"Knockback": {"10": {"chance": "+20"}}}),
    "Hoglin": ("Hoglin: húc văng (cấp 10)", {"Knockback": {"10": {"chance": "+20"}}}),
    "Zoglin": ("Zoglin: húc văng (cấp 10)", {"Knockback": {"10": {"chance": "+20"}}}),
    "IronGolem": ("Golem sắt: hất văng (cấp 10)", {"Knockback": {"10": {"chance": "+25"}}}),
    "Ravager": ("Ravager: húc văng (cấp 10)", {"Knockback": {"10": {"chance": "+25"}}}),
    "Breeze": ("Breeze: thổi văng (cấp 10)", {"Knockback": {"10": {"chance": "+20"}}}),
    "Turtle": ("Rùa: mai cứng, Hộ chủ mạnh hơn (cấp 10)", {"Shield": {"10": {"chance": "+10", "redirect": "+10"}}}),
    "Armadillo": ("Tatu: cuộn tròn, Hộ chủ mạnh hơn (cấp 10)", {"Shield": {"10": {"chance": "+10", "redirect": "+10"}}}),
    "Axolotl": ("Kỳ giông: tự hồi máu (cấp 10)", {"Heal": {"10": {"health": "+1", "timer": "+30"}}}),
    "Dolphin": ("Cá heo: hào quang chạy nhanh (cấp 20)",
                {"Beacon": {"20": {"buffs": {"speed": "+1"}, "count": "+1", "duration": "+8", "range": "+8"}}}),
    "Bat": ("Dơi: hào quang nhìn đêm (cấp 10)",
            {"Beacon": {"10": {"buffs": {"nightvision": True}, "count": "+1", "duration": "+8", "range": "+8"}}}),
    "Allay": ("Allay: nhặt đồ xa + kinh nghiệm (cấp 10)", {"Pickup": {"10": {"range": "+2", "exp": True}}}),
    "Fox": ("Cáo: nhặt đồ (cấp 10)", {"Pickup": {"10": {"range": "+1"}}}),
    "Rabbit": ("Thỏ: chạy nhanh (cấp 5)", {"Sprint": {"5": {"active": True}}}),
    "Cow": ("Bò: hồi máu (cấp 10)", {"Heal": {"10": {"health": "+1", "timer": "+30"}}}),
    "Mooshroom": ("Bò nấm: hồi máu (cấp 10)", {"Heal": {"10": {"health": "+1", "timer": "+30"}}}),
    "Creeper": ("Creeper: dậm đất mạnh hơn (cấp 20)", {"Stomp": {"20": {"chance": "+5", "damage": "+3"}}}),
}

# Tên tiếng Việt (web hiện danh sách loài theo nhóm).
TEN = {"Axolotl": "Kỳ giông", "Allay": "Allay", "Armadillo": "Tatu", "Bat": "Dơi", "Bee": "Ong", "Blaze": "Blaze", "Bogged": "Bogged",
       "Breeze": "Breeze", "Camel": "Lạc đà", "Cat": "Mèo", "CaveSpider": "Nhện hang", "Chicken": "Gà", "Cod": "Cá tuyết",
       "CopperGolem": "Người đồng", "Cow": "Bò", "Creeper": "Creeper", "Creaking": "Creaking", "Dolphin": "Cá heo", "Donkey": "Lừa",
       "Drowned": "Zombie đuối nước", "ElderGuardian": "Elder Guardian", "EnderDragon": "Rồng Ender", "Enderman": "Enderman",
       "Endermite": "Endermite", "Evoker": "Evoker", "Fox": "Cáo", "Frog": "Ếch", "Ghast": "Ghast", "Goat": "Dê", "Giant": "Khổng lồ",
       "GlowSquid": "Mực phát sáng", "Guardian": "Guardian", "Horse": "Ngựa", "Hoglin": "Hoglin", "Husk": "Husk",
       "Illusioner": "Illusioner", "IronGolem": "Golem sắt", "Llama": "Lạc đà không bướu", "MagmaCube": "Khối magma",
       "Mooshroom": "Bò nấm", "Mule": "La", "Ocelot": "Mèo rừng", "Panda": "Gấu trúc", "Parrot": "Vẹt", "Phantom": "Phantom",
       "Pig": "Lợn", "Piglin": "Piglin", "PiglinBrute": "Piglin hung bạo", "Pillager": "Pillager", "PolarBear": "Gấu Bắc Cực",
       "Pufferfish": "Cá nóc", "Rabbit": "Thỏ", "Ravager": "Ravager", "Salmon": "Cá hồi", "Sheep": "Cừu", "Silverfish": "Silverfish",
       "Skeleton": "Bộ xương", "SkeletonHorse": "Ngựa xương", "Slime": "Slime", "Sniffer": "Sniffer", "Snowman": "Người tuyết",
       "Spider": "Nhện", "Squid": "Mực", "Stray": "Stray", "Strider": "Strider", "Tadpole": "Nòng nọc", "TraderLlama": "Lạc đà thương nhân",
       "TropicalFish": "Cá nhiệt đới", "Turtle": "Rùa", "Vex": "Vex", "Villager": "Dân làng", "Vindicator": "Vindicator",
       "Warden": "Warden", "WanderingTrader": "Thương nhân lang thang", "Witch": "Phù thủy", "Wither": "Wither",
       "WitherSkeleton": "Bộ xương Wither", "Wolf": "Sói", "Zoglin": "Zoglin", "Zombie": "Zombie", "ZombieHorse": "Ngựa zombie",
       "ZombifiedPiglin": "Piglin zombie", "PigZombie": "Piglin zombie (cũ)", "ZombieVillager": "Dân làng zombie"}

# Bay của hệ Thú Cưỡi cho loài bay: flylimit = quãng đường bay mỗi lần (khối), flyregenrate = hồi mỗi tick khi chạm đất.
BAY = {"5": {"canfly": True, "flylimit": "+40", "flyregenrate": "+0.5"}, "20": {"flylimit": "+40", "flyregenrate": "+0.25"},
       "30": {"flylimit": "+70"}, "50": {"flylimit": "+100", "flyregenrate": "+0.25"}}
BAY_BAO = {"5": "Bay được (giữ phím nhảy khi đang ở trên không), 40 khối mỗi lần", "20": "Bay xa hơn (80 khối)",
           "30": "Bay xa hơn (150 khối)", "50": "Bay xa hơn (250 khối)"}


def cong(a, b):
    """Gộp 2 nâng cấp cùng kỹ năng cùng cấp: số "+x" cộng dồn, còn lại lấy cái sau."""
    out = dict(a)
    for k, v in b.items():
        if k in out and isinstance(v, str) and isinstance(out[k], str) and re.fullmatch(r"\+[\d.]+", v) and re.fullmatch(r"\+[\d.]+", out[k]):
            out[k] = "+" + format(Decimal(out[k][1:]) + Decimal(v[1:]), "f")
        elif k in out and isinstance(v, dict) and isinstance(out[k], dict):
            out[k] = {**out[k], **v}
        else:
            out[k] = v
    return out


def them(skills, more):
    for sk, ups in more.items():
        dst = skills.setdefault(sk, {}).setdefault("Upgrades", {})
        for lv, up in ups.items():
            dst[lv] = cong(dst[lv], up) if lv in dst else dict(up)


def so(x):
    return f"{x:g}".replace(".", ",")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for f in OUT.glob("*.st.json"):
        f.unlink()
    seen = set()
    n = 0
    for he, goc in HE.items():
        base = dict(goc)
        base.update({"ID": he, "Name": he, "Display": HE_TEN[he], "MobTypes": [], "Description": khung(goc["Description"])})   # cây gốc: không loài nào chọn trực tiếp
        (OUT / f"{he}.st.json").write_text(json.dumps(base, ensure_ascii=False, indent=2), encoding="utf-8")
        for ma, g in NHOM.items():
            # loài có đặc tính riêng -> cây riêng; loài không có -> chung một cây của nhóm
            parts = [(None, [l for l in g["loai"] if l not in LOAI])] + [(l, [l]) for l in g["loai"] if l in LOAI]
            for loai, mobs in parts:
                if not mobs:
                    continue
                seen.update(mobs)
                skills = {}
                them(skills, {"Life": {"%5>10": {"health": f"+{g['mau']}"}}, "Damage": {"%5>10": {"damage": f"+{g['dame']}"}}})
                them(skills, g.get("them", {}))
                them(skills, g.get("he", {}).get(he, {}))
                desc = [f"<dark_gray>•</dark_gray> <gold>Nhóm loài:</gold> <white>{g['ten']}</white> <gray>({g['vai']})</gray>"]
                notes = dict(goc.get("Notifications", {}))
                desc += [f"<dark_gray>  ▸</dark_gray> <white>{x}" for x in g["mo_ta"] if "Thú Cưỡi" not in x or he == "Ride"]
                if g.get("bay") and he == "Ride":
                    them(skills, {"Ride": BAY})
                    for lv, t in BAY_BAO.items():
                        notes[lv] = f"<gold>⬆ Pet đạt cấp {lv}: <yellow>{t}" + (" · " + notes[lv].split("<yellow>", 1)[1] if lv in notes else "")
                if loai:
                    them(skills, LOAI[loai][1])
                    desc.append(f"<dark_gray>  ▸</dark_gray> <white>{LOAI[loai][0]}")
                desc.append(f"<dark_gray>Mỗi 5 cấp sau cấp 10: +{so(g['mau'])} máu, +{so(g['dame'])} sát thương")
                desc += [LINE, "<green>➜ Bấm để chọn hệ này</green>"]
                tid = f"{he}_{ma}" + (f"_{loai}" if loai else "")
                tree = {
                    "ID": tid, "Name": tid, "Display": HE_TEN[he], "Order": goc.get("Order", 0),
                    "Icon": goc.get("Icon"), "MobTypes": mobs, "Inheritance": {"Skilltree": he},
                    "Description": khung(goc["Description"], desc), "Skills": skills, "Notifications": notes,
                    "DmmNhom": {"ma": ma, "ten": g["ten"], "vai": g["vai"], "he": he, "mo_ta": g["mo_ta"],
                                "tang": f"+{so(g['mau'])} máu, +{so(g['dame'])} sát thương mỗi 5 cấp sau cấp 10",
                                "dac_tinh": LOAI[loai][0] if loai else None, "loai_vi": [TEN.get(m, m) for m in mobs]},   # cho web (site/app.py pet_trees), MyPet bỏ qua
                }
                (OUT / f"{tid}.st.json").write_text(json.dumps(tree, ensure_ascii=False, indent=2), encoding="utf-8")
                n += 1
    print(f"Đã ghi {n} cây (+5 cây hệ gốc) vào {OUT.relative_to(ROOT)}")
    lack = set(LOAI) - seen
    if lack:
        sys.exit(f"Loài có đặc tính nhưng không thuộc nhóm nào: {sorted(lack)}")
    cfg = ROOT / "data" / "banghoi-1" / "plugins" / "MyPet" / "pet-config.yml"
    if cfg.exists():   # mọi loài MyPet phải thuộc một nhóm, không thì loài đó không chọn được hệ nào
        all_types = set(re.findall(r"^    ([A-Z][A-Za-z]+):$", cfg.read_text(encoding="utf-8"), re.M))
        if all_types - seen:
            sys.exit(f"Loài chưa thuộc nhóm nào: {sorted(all_types - seen)}")


if __name__ == "__main__":
    main()
