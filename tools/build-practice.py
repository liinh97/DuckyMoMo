"""Dựng chế độ Practice (server practice-1, world hư không): sảnh, 6 đấu trường, bục Sumo, và kit/đấu trường/hàng chờ
cho plugin Duels Optimised.

Cách dùng (từ thư mục gốc repo):
  1. Xây (server đang chạy):
       python3 tools/build-practice.py build > /tmp/practice.cmds
       docker exec -i duckymomo-practice-1-1 rcon-cli < /tmp/practice.cmds
  2. Ghi cấu hình Duels (TẮT server trước, vì Duels ghi đè file khi tắt):
       docker compose stop practice-1
       python3 tools/build-practice.py duels data/practice-1/plugins/Duels
       docker compose start practice-1
Kit dùng "JSON cũ" của Duels ({"material": ..., "enchantments": {...}}); lần nạp đầu Duels tự đổi sang định dạng mới.
Kiểm tra lại kits.json sau khi khởi động để chắc phù phép/thuốc còn đủ.
"""
import json
import os
import sys

Y = 100           # mặt sàn
WORLD = "world"

LOBBY = (0, 0)
ARENAS = [  # (tên, tâm x, tâm z, sàn, viền)
    ("DauTruong1", 300, 0, "grass_block", "mossy_stone_bricks"),
    ("DauTruong2", 420, 0, "sand", "sandstone"),
    ("DauTruong3", 540, 0, "snow_block", "packed_ice"),
    ("DauTruong4", 300, 120, "polished_andesite", "stone_bricks"),
    ("DauTruong5", 420, 120, "deepslate_tiles", "polished_blackstone_bricks"),
    ("DauTruong6", 540, 120, "prismarine_bricks", "dark_prismarine"),
]
SUMO = ("Sumo", 300, 300)
R = 16            # nửa cạnh đấu trường (33 x 33)


def build():
    c = []
    # nạp chunk riêng từng công trình (mỗi lệnh forceload tối đa 256 chunk)
    centers = [LOBBY] + [(a[1], a[2]) for a in ARENAS] + [(SUMO[1], SUMO[2])]
    for x, z in centers:
        c.append(f"forceload add {x-24} {z-24} {x+24} {z+24}")

    # ---- sảnh 41x41
    lx, lz = LOBBY
    c.append(f"fill {lx-20} {Y-1} {lz-20} {lx+20} {Y-1} {lz+20} minecraft:stone_bricks")
    c.append(f"fill {lx-20} {Y} {lz-20} {lx+20} {Y} {lz+20} minecraft:polished_deepslate")
    c.append(f"fill {lx-18} {Y} {lz-18} {lx+18} {Y} {lz+18} minecraft:polished_andesite")
    c.append(f"fill {lx-3} {Y} {lz-3} {lx+3} {Y} {lz+3} minecraft:chiseled_stone_bricks")
    for d in (-20, 20):
        c.append(f"fill {lx-20} {Y+1} {lz+d} {lx+20} {Y+1} {lz+d} minecraft:polished_deepslate_wall")
        c.append(f"fill {lx+d} {Y+1} {lz-20} {lx+d} {Y+1} {lz+20} minecraft:polished_deepslate_wall")
    for px in (-20, -10, 0, 10, 20):
        for pz in (-20, 20):
            c.append(f"fill {lx+px} {Y+1} {lz+pz} {lx+px} {Y+3} {lz+pz} minecraft:polished_deepslate_wall")
            c.append(f"setblock {lx+px} {Y+4} {lz+pz} minecraft:lantern")
            c.append(f"fill {lx+pz} {Y+1} {lz+px} {lx+pz} {Y+3} {lz+px} minecraft:polished_deepslate_wall")
            c.append(f"setblock {lx+pz} {Y+4} {lz+px} minecraft:lantern")
    # tượng kiếm giữa sảnh
    c.append(f"fill {lx} {Y+1} {lz} {lx} {Y+5} {lz} minecraft:iron_block")
    c.append(f"fill {lx-2} {Y+5} {lz} {lx+2} {Y+5} {lz} minecraft:iron_block")
    c.append(f"fill {lx} {Y+6} {lz} {lx} {Y+9} {lz} minecraft:light_gray_stained_glass")
    c.append(f"setblock {lx} {Y+10} {lz} minecraft:sea_lantern")

    def t(s, color=None, bold=False):
        p = [f'text:"{s}"'] + ([f'color:"{color}"'] if color else []) + (["bold:true"] if bold else [])
        return "{" + ",".join(p) + "}"

    def sign(x, z, rot, lines):
        msgs = ",".join((lines + [t("")] * 4)[:4])
        c.append(f"setblock {x} {Y+1} {z} minecraft:dark_oak_sign[rotation={rot}]{{front_text:{{messages:[{msgs}]}},is_waxed:1b}}")

    sign(lx, lz + 6, 0, [t("PRACTICE", "dark_red", True), t("Luyện PvP"), t("Combat 1.8"), t("DuckyMoMo", "dark_gray")])
    sign(lx - 6, lz, 4, [t("Đấu xếp hạng", "dark_green", True), t("/queue"), t("hoặc /q"), t("chọn kit")])
    sign(lx + 6, lz, 12, [t("Thách đấu", "dark_green", True), t("/duel <tên>"), t("chọn kit,"), t("đấu trường")])
    sign(lx, lz - 6, 8, [t("Khác", "dark_green", True), t("/party"), t("/duels stats"), t("/spawn")])

    # ---- đấu trường 33x33, tường cao 6
    for name, cx, cz, floor, border in ARENAS:
        c.append(f"fill {cx-R} {Y-1} {cz-R} {cx+R} {Y-1} {cz+R} minecraft:stone")
        c.append(f"fill {cx-R} {Y} {cz-R} {cx+R} {Y} {cz+R} minecraft:{floor}")
        for d in (-R, R):
            c.append(f"fill {cx-R} {Y+1} {cz+d} {cx+R} {Y+6} {cz+d} minecraft:{border}")
            c.append(f"fill {cx+d} {Y+1} {cz-R} {cx+d} {Y+6} {cz+R} minecraft:{border}")
        for qx in (-R, R):
            for qz in (-R, R):
                c.append(f"setblock {cx+qx} {Y+7} {cz+qz} minecraft:lantern")
        # vài khối che chắn giữa sân
        for ox, oz in ((-5, -5), (5, 5), (-5, 5), (5, -5)):
            c.append(f"fill {cx+ox} {Y+1} {cz+oz} {cx+ox} {Y+2} {cz+oz} minecraft:{border}")

    # ---- bục Sumo: tròn bán kính 6, nước bên dưới (Duels: chạm nước là thua)
    _, sx, sz = SUMO
    c.append(f"fill {sx-14} {Y-5} {sz-14} {sx+14} {Y-5} {sz+14} minecraft:stone_bricks")
    c.append(f"fill {sx-14} {Y-4} {sz-14} {sx+14} {Y-3} {sz+14} minecraft:water")
    for d in (-15, 15):
        c.append(f"fill {sx-15} {Y-5} {sz+d} {sx+15} {Y-2} {sz+d} minecraft:stone_bricks")
        c.append(f"fill {sx+d} {Y-5} {sz-15} {sx+d} {Y-2} {sz+15} minecraft:stone_bricks")
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            if dx * dx + dz * dz <= 40:
                c.append(f"setblock {sx+dx} {Y} {sz+dz} minecraft:smooth_stone")
    c.append(f"fill {sx} {Y-2} {sz} {sx} {Y-1} {sz} minecraft:stone_brick_wall")  # cột đỡ (trang trí)

    c.append(f"setworldspawn {lx} {Y+1} {lz+2}")
    c.append("gamerule advance_time false")
    c.append("gamerule advance_weather false")
    c.append("gamerule fall_damage true")
    for x, z in centers:
        c.append(f"forceload remove {x-24} {z-24} {x+24} {z+24}")
    print("\n".join(c))


# ------------------------------------------------------------------ Duels JSON

def item(material, amount=1, ench=None, unbreakable=False, potion=None, name=None):
    d = {"material": material, "amount": amount}
    if ench:
        d["enchantments"] = ench
    if unbreakable:
        d["unbreakable"] = True
    if potion:
        d["itemData"] = potion   # "<loại>[-strong|-extended]", ví dụ "HEALING-strong" (Duels không nhận STRONG_HEALING)
    if name:
        d["displayName"] = name
    return d


def armor(kind, prot=0, unb=0):
    e = {}
    if prot:
        e["PROTECTION"] = prot
    if unb:
        e["UNBREAKING"] = unb
    return {"1": item(f"{kind}_HELMET", ench=e or None), "2": item(f"{kind}_CHESTPLATE", ench=e or None),
            "3": item(f"{kind}_LEGGINGS", ench=e or None), "4": item(f"{kind}_BOOTS", ench=e or None)}


def kits():
    k = {}

    def add(name, icon, inv, arm=None, chars=(), specific=False):
        items = {"INVENTORY": {str(s): v for s, v in inv.items()}}
        if arm:
            items["ARMOR"] = arm
        k[name] = {"name": name, "displayed": item(icon, name="&e" + name), "usePermission": False,
                   "arenaSpecific": specific, "characteristics": list(chars), "items": items}

    sword_d = item("DIAMOND_SWORD", ench={"SHARPNESS": 2, "UNBREAKING": 3})
    nodebuff = {0: sword_d, 1: item("ENDER_PEARL", 16), 2: item("POTION", potion="SWIFTNESS-strong"),
                3: item("POTION", potion="FIRE_RESISTANCE-extended"), 8: item("COOKED_BEEF", 64)}
    for s in list(range(4, 8)) + list(range(9, 36)):
        nodebuff[s] = item("SPLASH_POTION", potion="HEALING-strong")
    add("NoDebuff", "SPLASH_POTION", nodebuff, armor("DIAMOND", 2, 3))
    add("Gapple", "GOLDEN_APPLE", {0: item("DIAMOND_SWORD", ench={"SHARPNESS": 3, "UNBREAKING": 3}), 1: item("GOLDEN_APPLE", 16),
                                     8: item("COOKED_BEEF", 32)}, armor("DIAMOND", 3, 3))
    add("Classic", "IRON_SWORD", {0: item("IRON_SWORD"), 1: item("BOW"), 2: item("FISHING_ROD"), 3: item("GOLDEN_APPLE", 2),
                                   8: item("COOKED_BEEF", 16), 9: item("ARROW", 16)}, armor("IRON"))
    add("Archer", "BOW", {0: item("BOW", ench={"POWER": 2, "INFINITY": 1}), 1: item("WOODEN_SWORD"),
                          8: item("COOKED_BEEF", 16), 9: item("ARROW", 1)}, armor("LEATHER", 1))
    add("Boxing", "DIAMOND_SWORD", {0: item("DIAMOND_SWORD", unbreakable=True)}, chars=["BOXING"])
    add("Combo", "PUFFERFISH", {0: item("DIAMOND_SWORD", ench={"SHARPNESS": 2, "UNBREAKING": 3}), 1: item("GOLDEN_APPLE", 32),
                                8: item("COOKED_BEEF", 32)}, armor("DIAMOND", 4, 3), chars=["COMBO"])
    add("Sumo", "SLIME_BALL", {}, chars=["SUMO"], specific=True)
    return k


def arenas():
    out = []
    for name, cx, cz, floor, _ in ARENAS:
        out.append({"name": name, "disabled": False, "displayed": item(floor.upper(), name="&a" + name), "kits": [],
                    "positions": {"1": {"world": WORLD, "x": cx - 12 + 0.5, "y": Y + 1, "z": cz + 0.5, "pitch": 0, "yaw": -90},
                                  "2": {"world": WORLD, "x": cx + 12 + 0.5, "y": Y + 1, "z": cz + 0.5, "pitch": 0, "yaw": 90}}})
    name, sx, sz = SUMO
    out.append({"name": name, "disabled": False, "displayed": item("SLIME_BLOCK", name="&aSumo"), "kits": ["Sumo"],
                "positions": {"1": {"world": WORLD, "x": sx - 3 + 0.5, "y": Y + 1, "z": sz + 0.5, "pitch": 0, "yaw": -90},
                              "2": {"world": WORLD, "x": sx + 3 + 0.5, "y": Y + 1, "z": sz + 0.5, "pitch": 0, "yaw": 90}}})
    return out


def queues(k):
    return [{"name": name, "kit": name, "bet": 0, "teamSize": 1, "rated": True, "maxDifference": None} for name in k]


if __name__ == "__main__":
    if sys.argv[1] == "build":
        build()
    elif sys.argv[1] == "duels":
        d = sys.argv[2]
        k = kits()
        for fname, data in (("kits.json", k), ("arenas.json", arenas()), ("queues.json", queues(k))):
            with open(os.path.join(d, fname), "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"Đã ghi {len(k)} kit, {len(ARENAS) + 1} đấu trường, {len(k)} hàng chờ vào {d}")
