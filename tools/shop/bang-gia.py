"""Bảng giá Cửa hàng Kinh Đô + giá thu mua /sell (Essentials worth.yml), 20/10.

Người dùng: "bổ sung đầy đủ items phổ biến, giá mua, giá bán lại giống bên kia (CherryMC), có món không thể mua được, giá tăng theo độ
quý hiếm". Mọi giá tính theo TỪNG MÓN (lần bấm mua 1 cái). Nguyên tắc chống in tiền:
  - giá mua luôn >= 4 lần giá bán lại (mua rồi bán lại không bao giờ lời; DecorShop còn tự bỏ món có giá mua <= giá /sell);
  - món chế từ món khác có giá bán lại <= tổng giá bán lại nguyên liệu (vd 9 than = 1 khối than);
  - KHÔNG đặt giá /sell cho GOLD_NUGGET và *_POTTERY_SHERD: đó là vật liệu nền của Xu Quái / món Tinh Luyện, có giá là Essentials bán nhầm.
Món "không mua được" (quặng quý, kim loại, đá quý…) chỉ có giá bán lại, nằm trong "sell" của danh mục.

Chạy (thư mục gốc repo):  python3 tools/shop/bang-gia.py
  - ghi decor-shop.categories vào modes/banghoi/server/plugins/DuckyMoMoCore/config.yml và data/banghoi-1/... (nếu có),
  - ghi decor_categories vào modes/banghoi/settings.yml,
  - ghi mục worth: của data/banghoi-1/plugins/Essentials/worth.yml (file chỉ có ở data).
Sau đó: docker compose restart banghoi-1 (hoặc /dmm reload + /essentials reload).
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# (mã danh mục, tên, biểu tượng, ô, mô tả, [(mẫu vật liệu, giá mua | None = không mua được, giá bán lại)])
# Thứ tự dòng quan trọng: dòng trước "giành" vật phẩm, dòng sau khớp lại cũng bỏ qua (vd STRIPPED_* trước *_LOG).
CATS = [
    ("tunhien", "Khối tự nhiên", "GRASS_BLOCK", 10, "Đất, đá, cát, băng, khối Nether và End", [
        ("COBBLESTONE", 1, 0.02), ("COBBLED_DEEPSLATE", 2, 0.02), ("NETHERRACK", 1, 0.02), ("DIRT", 2, 0.05), ("SAND", 2, 0.05),
        ("GRAVEL", 2, 0.05), ("STONE", 2, 0.05), ("GRANITE", 2, 0.05), ("DIORITE", 2, 0.05), ("ANDESITE", 2, 0.05), ("DEEPSLATE", 3, 0.05),
        ("TUFF", 3, 0.05), ("COARSE_DIRT", 3, 0.05), ("RED_SAND", 3, 0.05), ("SANDSTONE", 3, 0.05), ("RED_SANDSTONE", 3, 0.05),
        ("BASALT", 3, 0.05), ("BLACKSTONE", 3, 0.05), ("MUD", 4, 0.05), ("ROOTED_DIRT", 4, 0.1), ("MOSSY_COBBLESTONE", 4, 0.1),
        ("CALCITE", 4, 0.1), ("DRIPSTONE_BLOCK", 4, 0.1), ("POINTED_DRIPSTONE", 4, 0.1), ("SOUL_SAND", 4, 0.1), ("SOUL_SOIL", 4, 0.1),
        ("SNOW_BLOCK", 4, 0.1), ("SNOWBALL", 1, 0.02), ("GRASS_BLOCK", 6, 0.1), ("PODZOL", 6, 0.1), ("CLAY", 6, 0.2), ("ICE", 6, 0.1),
        ("END_STONE", 6, 0.1), ("MYCELIUM", 8, 0.1), ("MOSS_BLOCK", 8, 0.2), ("PACKED_ICE", 12, 0.3), ("MAGMA_BLOCK", 12, 0.3),
        ("PRISMARINE", 12, 0.3), ("OBSIDIAN", 40, 1), ("BLUE_ICE", 40, 1)]),
    ("go", "Khối gỗ", "OAK_LOG", 12, "Khúc gỗ, gỗ bóc vỏ, ván các loại", [
        ("STRIPPED_*", 9, 2), ("*_LOG", 8, 2), ("*_WOOD", 8, 2), ("*_STEM", 10, 2), ("*_HYPHAE", 10, 2), ("BAMBOO_BLOCK", 8, 1.5),
        ("*_PLANKS", 2, 0.4), ("STICK", 1, 0.05)]),
    ("xaydung", "Vật liệu xây", "BRICKS", 14, "Gạch, bê tông, len, kính, cầu thang, hàng rào", [
        ("PRISMARINE_BRICKS", 15, 0.4), ("DARK_PRISMARINE", 18, 0.5), ("PURPUR_BLOCK", 6, 0.2), ("PURPUR_PILLAR", 6, 0.2),
        ("BRICKS", 6, 0.2), ("*_BRICKS", 6, 0.2), ("POLISHED_*", 4, 0.1), ("CHISELED_*", 5, 0.1), ("CUT_*", 4, 0.1), ("SMOOTH_*", 4, 0.1),
        ("*_STAIRS", 3, 0.05), ("*_SLAB", 1.5, 0.02), ("*_WALL", 3, 0.05), ("*_CONCRETE_POWDER", 4, 0.1), ("*_CONCRETE", 5, 0.1),
        ("*_GLAZED_TERRACOTTA", 8, 0.2), ("TERRACOTTA", 4, 0.1), ("*_TERRACOTTA", 4, 0.1), ("*_WOOL", 4, 0.5), ("*_CARPET", 2, 0.1),
        ("GLASS", 5, 0.15), ("TINTED_GLASS", 20, 0.5), ("*_STAINED_GLASS", 6, 0.2), ("GLASS_PANE", 2, 0.05), ("*_GLASS_PANE", 2, 0.05),
        ("*_FENCE_GATE", 6, 0.2), ("*_FENCE", 4, 0.1), ("*_DOOR", 6, 0.2), ("*_TRAPDOOR", 6, 0.2), ("*_HANGING_SIGN", 6, 0.2),
        ("*_SIGN", 4, 0.1), ("LADDER", 2, 0.05), ("SCAFFOLDING", 4, 0.1), ("TORCH", 1, 0.05), ("SOUL_TORCH", 2, 0.05)]),
    ("nongsan", "Nông nghiệp", "WHEAT", 28, "Hạt giống, cây non, nông sản, thịt, cá", [
        ("WHEAT_SEEDS", 2, 0.1), ("BEETROOT_SEEDS", 2, 0.1), ("MELON_SEEDS", 4, 0.1), ("PUMPKIN_SEEDS", 4, 0.1), ("WHEAT", 4, 0.75),
        ("CARROT", 4, 0.6), ("POTATO", 4, 0.6), ("BEETROOT", 4, 0.6), ("SUGAR_CANE", 4, 1), ("MELON_SLICE", 2, 0.2), ("MELON", 12, 1.5),
        ("PUMPKIN", 8, 2), ("BAMBOO", 2, 0.1), ("CACTUS", 4, 0.3), ("COCOA_BEANS", 4, 0.5), ("SWEET_BERRIES", 3, 0.2),
        ("GLOW_BERRIES", 6, 0.4), ("KELP", 2, 0.1), ("DRIED_KELP_BLOCK", 10, 0.5), ("HAY_BLOCK", 30, 6), ("*_SAPLING", 10, 0.5),
        ("MANGROVE_PROPAGULE", 10, 0.5), ("NETHER_WART", 10, 0.5), ("BROWN_MUSHROOM", 4, 0.2), ("RED_MUSHROOM", 4, 0.2),
        ("CRIMSON_FUNGUS", 6, 0.3), ("WARPED_FUNGUS", 6, 0.3), ("APPLE", 6, 0.5), ("EGG", 3, 0.3), ("BONE_MEAL", 3, 0.1),
        ("BEEF", 6, 0.5), ("PORKCHOP", 6, 0.5), ("CHICKEN", 5, 0.4), ("MUTTON", 5, 0.4), ("RABBIT", 6, 0.5), ("COD", 6, 0.5),
        ("SALMON", 7, 0.6), ("COOKED_*", 10, 0.8), ("BREAD", 8, 0.5), ("BAKED_POTATO", 8, 0.6)]),
    ("thuocnhuom", "Thuốc nhuộm & hoa", "RED_DYE", 30, "Thuốc nhuộm đủ màu, hoa các loại", [
        ("*_DYE", 8, 0.5), ("INK_SAC", 6, 0.3), ("GLOW_INK_SAC", 15, 0.5), ("DANDELION", 6, 0.5), ("POPPY", 6, 0.5),
        ("BLUE_ORCHID", 6, 0.5), ("ALLIUM", 6, 0.5), ("AZURE_BLUET", 6, 0.5), ("*_TULIP", 6, 0.5), ("OXEYE_DAISY", 6, 0.5),
        ("CORNFLOWER", 6, 0.5), ("LILY_OF_THE_VALLEY", 6, 0.5), ("SUNFLOWER", 8, 0.5), ("LILAC", 8, 0.5), ("ROSE_BUSH", 8, 0.5),
        ("PEONY", 8, 0.5), ("PINK_PETALS", 6, 0.3), ("TORCHFLOWER", 15, 0.5), ("PITCHER_PLANT", 15, 0.5), ("SPORE_BLOSSOM", 30, 1)]),
    ("trangtri", "Trang trí", "LANTERN", 32, "Đèn, nến, san hô, lá cây, giường, cờ", [
        ("*_LEAVES", 10, 0.5), ("*_CANDLE", 8, 0.5), ("CANDLE", 6, 0.5), ("*_CORAL_BLOCK", 15, 0.5), ("*_CORAL_FAN", 12, 0.5),
        ("*_CORAL", 12, 0.5), ("*_FROGLIGHT", 40, 2), ("LANTERN", 15, 1), ("SOUL_LANTERN", 18, 1), ("SEA_LANTERN", 30, 1.5),
        ("GLOWSTONE", 20, 0.75), ("CAMPFIRE", 15, 0.5), ("SOUL_CAMPFIRE", 18, 0.5), ("FLOWER_POT", 4, 0.1), ("DECORATED_POT", 10, 0.3),
        ("ITEM_FRAME", 8, 0.3), ("GLOW_ITEM_FRAME", 15, 0.5), ("PAINTING", 8, 0.3), ("ARMOR_STAND", 20, 1), ("*_BANNER", 15, 0.5),
        ("*_BED", 20, 1), ("BOOKSHELF", 30, 1), ("CHEST", 6, 0.2), ("BARREL", 6, 0.2), ("VINE", 4, 0.1), ("GLOW_LICHEN", 6, 0.2),
        ("LILY_PAD", 6, 0.2), ("MOSS_CARPET", 3, 0.1), ("AZALEA", 10, 0.3), ("FLOWERING_AZALEA", 12, 0.3), ("FERN", 3, 0.05),
        ("SHORT_GRASS", 2, 0.05), ("TALL_GRASS", 3, 0.05), ("SEA_PICKLE", 8, 0.3)]),
    ("khoangsan", "Khoáng sản", "DIAMOND", 34, "Than, đồng, đá đỏ mua được; sắt, vàng, kim cương chỉ bán", [
        ("COAL", 27, 3), ("CHARCOAL", 20, 2), ("COAL_BLOCK", 240, 27), ("COPPER_INGOT", 20, 2), ("REDSTONE", 15, 1),
        ("LAPIS_LAZULI", 25, 3), ("QUARTZ", 20, 2), ("AMETHYST_SHARD", 15, 1), ("FLINT", 4, 0.2), ("CLAY_BALL", 2, 0.1),
        ("GLOWSTONE_DUST", 8, 0.3),
        # không mua được, chỉ bán cho server
        ("RAW_COPPER", None, 1.5), ("RAW_IRON", None, 8), ("RAW_GOLD", None, 16), ("IRON_NUGGET", None, 1), ("IRON_INGOT", None, 10),
        ("GOLD_INGOT", None, 20), ("DIAMOND", None, 120), ("EMERALD", None, 40), ("ANCIENT_DEBRIS", None, 300),
        ("NETHERITE_SCRAP", None, 300), ("NETHERITE_INGOT", None, 1300), ("COPPER_BLOCK", None, 18), ("IRON_BLOCK", None, 90),
        ("GOLD_BLOCK", None, 180), ("DIAMOND_BLOCK", None, 1080), ("EMERALD_BLOCK", None, 360), ("LAPIS_BLOCK", None, 27),
        ("REDSTONE_BLOCK", None, 9), ("QUARTZ_BLOCK", None, 8), ("AMETHYST_BLOCK", None, 4)]),
    ("quai", "Vật phẩm quái vật", "BONE", 38, "Xương, tơ, thuốc súng, ngọc ender, que blaze…", [
        ("ROTTEN_FLESH", 2, 0.1), ("ARROW", 2, 0.1), ("FEATHER", 4, 0.3), ("RABBIT_HIDE", 6, 0.3), ("BONE", 8, 0.5), ("STRING", 8, 0.5),
        ("SPIDER_EYE", 10, 0.5), ("PRISMARINE_SHARD", 10, 0.5), ("PRISMARINE_CRYSTALS", 12, 0.5), ("LEATHER", 12, 1),
        ("FERMENTED_SPIDER_EYE", 20, 1), ("GUNPOWDER", 20, 1), ("SLIME_BALL", 25, 2), ("BLAZE_POWDER", 30, 2), ("MAGMA_CREAM", 50, 1),
        ("PHANTOM_MEMBRANE", 50, 3), ("RABBIT_FOOT", 50, 3), ("ENDER_PEARL", 60, 5), ("BLAZE_ROD", 60, 5), ("ARMADILLO_SCUTE", 15, 1),
        ("TURTLE_SCUTE", 30, 2), ("BREEZE_ROD", 40, 3), ("GHAST_TEAR", 150, 10),
        ("SHULKER_SHELL", None, 40), ("NAUTILUS_SHELL", None, 10)]),
    ("khac", "Khác", "SADDLE", 40, "Dụng cụ, đồ tiện ích", [
        ("PAPER", 2, 0.05), ("BOOK", 15, 0.5), ("WRITABLE_BOOK", 20, 0.5), ("FIREWORK_ROCKET", 10, 0.2), ("FISHING_ROD", 30, 0),
        ("CARROT_ON_A_STICK", 35, 0), ("WARPED_FUNGUS_ON_A_STICK", 40, 0), ("LEAD", 40, 0), ("BRUSH", 25, 0),
        ("EXPERIENCE_BOTTLE", 30, 0), ("BELL", 150, 0), ("ANVIL", 500, 0), ("ENDER_CHEST", 400, 0), ("SADDLE", 300, 0),
        ("NAME_TAG", 300, 0)]),
    ("danhhieu", "Danh hiệu", "NAME_TAG", 42, "Chữ hiện cạnh tên, đổi bằng /danhhieu", []),
]

# vật liệu nền của món có dấu riêng (Xu Quái, Tinh Luyện, bùa…): KHÔNG có giá /sell
NO_WORTH = ["GOLD_NUGGET", "*_POTTERY_SHERD"]
# đồ đặc thù (khớp DecorShop.DENY): mẫu * không bao giờ khớp các món này
DENY = ["*_ORE", "RAW_*", "*_INGOT", "*_NUGGET", "*DIAMOND*", "*EMERALD*", "*NETHERITE*", "*GOLD*", "*IRON*", "*COPPER*", "*LAPIS*",
        "*REDSTONE*", "*QUARTZ*", "*AMETHYST*", "ANCIENT_DEBRIS", "*_SPAWN_EGG", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "*COMMAND*",
        "BARRIER", "STRUCTURE_*", "JIGSAW", "LIGHT", "DEBUG_STICK", "BEDROCK", "END_PORTAL_FRAME", "REINFORCED_DEEPSLATE", "ELYTRA",
        "TOTEM_OF_UNDYING", "BEACON", "NETHER_STAR", "DRAGON_*", "*SHULKER*", "ENCHANTED_*", "*_SMITHING_TEMPLATE", "HEAVY_CORE",
        "*TRIAL_KEY", "OMINOUS_*", "CONDUIT", "HEART_OF_THE_SEA", "*_HEAD", "*_SKULL", "PETRIFIED_OAK_SLAB", "KNOWLEDGE_BOOK",
        "INFESTED_*", "*SCULK*", "CALIBRATED_*", "*_SWORD", "*_AXE", "*_PICKAXE", "*_SHOVEL", "*_HOE", "*_HELMET", "*_CHESTPLATE",
        "*_LEGGINGS", "*_BOOTS", "BOW", "CROSSBOW", "TRIDENT", "MACE", "SHIELD", "*POTION*", "*_BUCKET", "TNT*", "END_CRYSTAL",
        "RESPAWN_ANCHOR", "LODESTONE", "CRYING_OBSIDIAN", "CHORUS_*", "FROGSPAWN", "WITHER_*", "ECHO_SHARD", "RECOVERY_COMPASS",
        "*_BANNER_PATTERN", "MUSIC_DISC_*", "DISC_FRAGMENT_*", "GOAT_HORN", "SPYGLASS", "CLOCK", "COMPASS", "*_MINECART", "*RAIL",
        "BUDDING_AMETHYST", "*_POTTERY_SHERD"]


def match(pat, name):
    if pat.startswith("*") and pat.endswith("*") and len(pat) > 1:
        return pat[1:-1] in name
    if pat.startswith("*"):
        return name.endswith(pat[1:])
    if pat.endswith("*"):
        return name.startswith(pat[:-1])
    return name == pat


def materials():
    meta = ROOT / "data" / "banghoi-1" / "plugins" / "DuckyMoMoCore" / "thuvien-meta.json"
    return json.loads(meta.read_text(encoding="utf-8"))["materials"]


def num(x):
    return int(x) if float(x) == int(x) else x


def build():
    """-> (danh mục cho config, {vật liệu: giá bán lại})"""
    mats = materials()
    taken, worth, cats = set(), {}, []
    for cid, name, icon, slot, desc, rows in CATS:
        items, sell = [], []
        for pat, buy, sv in rows:
            hit = [m for m in mats if match(pat, m) and m not in taken and ("*" not in pat or not any(match(d, m) for d in DENY))]
            if not hit:
                continue
            for m in hit:
                taken.add(m)
                if sv and not any(match(n, m) for n in NO_WORTH):
                    worth[m] = sv
            if buy is None:
                sell += hit
            else:
                assert not sv or buy >= 4 * sv, f"{pat}: giá mua {buy} < 4 lần giá bán {sv}"
                items.append({"material": pat, "amount": 1, "price": num(buy)})
        cats.append({"id": cid, "name": name, "icon": icon, "slot": slot, "desc": desc, "sell": sell, "items": items})
    return cats, worth


def flow(v):
    if isinstance(v, dict):
        return "{" + ", ".join(f"{k}: {flow(x)}" for k, x in v.items()) + "}"
    if isinstance(v, list):
        return "[" + ", ".join(flow(x) for x in v) + "]"
    if isinstance(v, (int, float)):
        return str(num(v))
    s = str(v)
    return s if re.match(r"^[A-Za-z0-9_]+$", s) and s not in ("true", "false", "null") else json.dumps(s, ensure_ascii=False)


def cat_lines(cats, indent):
    return "".join(f"{indent}- {flow(c)}\n" for c in cats)


def replace_block(text, start_line, indent, body):
    """Thay khối bắt đầu bằng dòng start_line (vd '  categories:') tới dòng kế tiếp có thụt lề <= indent của khoá."""
    lines = text.split("\n")
    i = lines.index(start_line)
    key_ind = len(start_line) - len(start_line.lstrip())
    j = i + 1
    while j < len(lines) and (lines[j].strip() == "" or len(lines[j]) - len(lines[j].lstrip()) > key_ind
                              or (lines[j].lstrip().startswith("- ") and len(lines[j]) - len(lines[j].lstrip()) >= key_ind)):
        if lines[j].strip() == "" and j + 1 < len(lines) and len(lines[j + 1]) - len(lines[j + 1].lstrip()) <= key_ind \
                and not lines[j + 1].lstrip().startswith("- "):
            break
        j += 1
    return "\n".join(lines[:i + 1]) + "\n" + body + "\n".join(lines[j:])


def main():
    cats, worth = build()
    for cfg in [ROOT / "modes/banghoi/server/plugins/DuckyMoMoCore/config.yml", ROOT / "data/banghoi-1/plugins/DuckyMoMoCore/config.yml"]:
        if cfg.exists():
            cfg.write_text(replace_block(cfg.read_text(encoding="utf-8"), "  categories:", 2, cat_lines(cats, "    ")), encoding="utf-8")
    st = ROOT / "modes/banghoi/settings.yml"
    st.write_text(replace_block(st.read_text(encoding="utf-8"), "decor_categories:", 0, cat_lines(cats, "  ")), encoding="utf-8")
    w = ROOT / "data/banghoi-1/plugins/Essentials/worth.yml"
    if w.exists():
        head = w.read_text(encoding="utf-8").split("\nworth:", 1)[0]
        body = "".join(f"  {m.lower()}: {num(v)}\n" for m, v in sorted(worth.items()))
        w.write_text(head + "\nworth:\n" + body, encoding="utf-8")
    nbuy = sum(len(c["items"]) for c in cats)
    print(f"{len(cats)} danh mục, {nbuy} dòng món bán, {sum(len(c['sell']) for c in cats)} món chỉ thu mua, {len(worth)} giá /sell")


if __name__ == "__main__":
    main()
