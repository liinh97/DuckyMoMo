"""Sinh lệnh đặt dân làng NPC đổi đồ (bộ trao đổi cố định) ở khu spawn Bang Hội Chiến.

Cách dùng (từ thư mục gốc repo):
  python3 tools/villagers.py > /tmp/villagers.cmds
  docker exec -i duckymomo-banghoi-1-1 rcon-cli < /tmp/villagers.cmds
Chạy lại: script tự xoá dân làng cũ (tag dmm_trader) trước khi đặt mới. Sửa bộ đồ ở TRADERS.
Dân làng: không AI (đứng yên), bất tử, không biến mất, giá cố định (không tăng giá theo nhu cầu, không hết lượt).
Vùng WorldGuard "spawn" chặn sinh vật xuất hiện, nên script tạm mở mob-spawning rồi chặn lại.
Tiền tệ: ngọc lục bảo. Người chơi bán nông sản/cá/đồ quái lấy ngọc, dùng ngọc mua đồ.
"""
import math

# tâm quảng trường tròn phía nam khu spawn, vòng bậc đứng ở y 64, bán kính ~20
CX, CZ, Y, RADIUS = 12696.5, -2354.5, 64, 20
# góc (độ, 0 = Đông, 90 = Nam); bỏ phía Bắc (đại lộ vào quảng trường)
ANGLES = [0, 35, 70, 110, 145, 180, 215, 325]


def it(item, count=1, ench=None, stored=None):
    comp = []
    if ench:
        comp.append('"minecraft:enchantments":{' + ",".join(f'"minecraft:{k}":{v}' for k, v in ench.items()) + "}")
    if stored:
        comp.append('"minecraft:stored_enchantments":{' + ",".join(f'"minecraft:{k}":{v}' for k, v in stored.items()) + "}")
    c = f',components:{{{",".join(comp)}}}' if comp else ""
    return f'{{id:"minecraft:{item}",count:{count}{c}}}'


E = "emerald"
BOOK = it("book")

# (tên, nghề, kiểu làng, màu tên, [(mua, mua thêm hoặc None, bán)])
TRADERS = [
    ("Nông dân", "farmer", "plains", "green", [
        (it("wheat", 20), None, it(E)), (it("carrot", 18), None, it(E)), (it("potato", 18), None, it(E)),
        (it("pumpkin", 6), None, it(E)), (it(E), None, it("bread", 6)), (it(E, 8), None, it("golden_apple")),
    ]),
    ("Ngư dân", "fisherman", "swamp", "aqua", [
        (it("cod", 8), None, it(E)), (it("salmon", 6), None, it(E)), (it("string", 16), None, it(E)),
        (it(E), None, it("cooked_cod", 8)), (it(E, 3), None, it("fishing_rod", 1, ench={"lure": 2, "luck_of_the_sea": 1})),
    ]),
    ("Thợ rèn vũ khí", "weaponsmith", "plains", "gold", [
        (it("iron_ingot", 5), None, it(E)), (it("coal", 15), None, it(E)),
        (it(E, 3), None, it("iron_sword", 1, ench={"sharpness": 1})), (it(E, 4), None, it("iron_axe", 1, ench={"sharpness": 1})),
        (it(E, 12), None, it("diamond_sword", 1)),
    ]),
    ("Thợ rèn giáp", "armorer", "plains", "gold", [
        (it(E, 4), None, it("iron_helmet")), (it(E, 7), None, it("iron_chestplate")), (it(E, 6), None, it("iron_leggings")),
        (it(E, 4), None, it("iron_boots")), (it(E, 5), None, it("shield")),
        (it(E, 15), None, it("diamond_chestplate", 1)),
    ]),
    ("Thủ thư", "librarian", "plains", "light_purple", [
        (it("paper", 24), None, it(E)), (it("book", 4), None, it(E)),
        (it(E, 10), BOOK, it("enchanted_book", stored={"protection": 2})),
        (it(E, 12), BOOK, it("enchanted_book", stored={"sharpness": 2})),
        (it(E, 15), BOOK, it("enchanted_book", stored={"efficiency": 3})),
        (it(E, 20), BOOK, it("enchanted_book", stored={"unbreaking": 3})),
        (it(E, 6), None, it("bookshelf", 3)),
    ]),
    ("Thợ làm cung", "fletcher", "taiga", "yellow", [
        (it("stick", 32), None, it(E)), (it("feather", 16), None, it(E)), (it("flint", 16), None, it(E)),
        (it(E), None, it("arrow", 16)), (it(E, 3), None, it("bow")), (it(E, 6), None, it("crossbow")),
    ]),
    ("Thợ đá", "mason", "desert", "gray", [
        (it("clay_ball", 10), None, it(E)), (it(E), None, it("stone_bricks", 16)), (it(E), None, it("polished_andesite", 16)),
        (it(E), None, it("terracotta", 16)), (it(E, 2), None, it("quartz_block", 8)), (it(E), None, it("bricks", 12)),
    ]),
    ("Thầy tu", "cleric", "jungle", "dark_purple", [
        (it("rotten_flesh", 32), None, it(E)), (it("bone", 16), None, it(E)), (it("spider_eye", 12), None, it(E)),
        (it(E, 2), None, it("redstone", 8)), (it(E, 4), None, it("ender_pearl")), (it(E, 5), None, it("experience_bottle", 3)),
    ]),
]


def recipe(buy, buyb, sell):
    b = f",buyB:{buyb}" if buyb else ""
    return (f"{{buy:{buy}{b},sell:{sell},maxUses:2147483647,rewardExp:0b,xp:0,priceMultiplier:0f,demand:0,specialPrice:0}}")


def main():
    cmds = [f"forceload add {int(CX) - 32} {int(CZ) - 32} {int(CX) + 32} {int(CZ) + 32}",
            "rg flag -w world spawn mob-spawning allow",
            "minecraft:kill @e[type=minecraft:villager,tag=dmm_trader]"]
    for (name, prof, vtype, color, trades), ang in zip(TRADERS, ANGLES):
        a = math.radians(ang)
        x, z = CX + RADIUS * math.cos(a), CZ + RADIUS * math.sin(a)
        yaw = math.degrees(math.atan2(-(CX - x), CZ - z)) % 360  # quay mặt vào tâm (Minecraft: yaw 0 = Nam, 90 = Tây)
        recipes = ",".join(recipe(*t) for t in trades)
        cmds.append(
            f"summon minecraft:villager {x:.1f} {Y} {z:.1f} {{Tags:[\"dmm_trader\"],NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,"
            f"Silent:1b,CustomNameVisible:1b,CustomName:{{text:\"{name}\",color:\"{color}\",bold:true}},Rotation:[{yaw:.0f}f,0f],"
            f"VillagerData:{{profession:\"minecraft:{prof}\",level:5,type:\"minecraft:{vtype}\"}},Offers:{{Recipes:[{recipes}]}}}}")
    cmds.append("rg flag -w world spawn mob-spawning deny")
    cmds.append(f"forceload remove {int(CX) - 32} {int(CZ) - 32} {int(CX) + 32} {int(CZ) + 32}")
    print("\n".join(cmds))


if __name__ == "__main__":
    main()
