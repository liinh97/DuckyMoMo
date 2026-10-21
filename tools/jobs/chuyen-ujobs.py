"""Chuyển nghề từ uJobs sang Jobs Reborn (21/10/2026). Người dùng: "chỉ cho chọn 1 trong các nghề thôi, nếu không member quá lợi".

uJobs tự tính mọi nghề, không có chọn nghề và không có API để chặn -> đổi sang Jobs Reborn (Zrips, cần CMILib; 5.2.6.7 hỗ trợ 26.2).
Chạy ở thư mục gốc repo:  python3 tools/jobs/chuyen-ujobs.py
  - đọc giá đang dùng ở modes/banghoi/server/plugins/UJobs/jobs.yml (đã định giá lại 21/10) + nhãn tiếng Việt ở settings.schema.yml
  - ghi modes/banghoi/server/plugins/Jobs/: jobs/<nghề>.yml, generalConfig.yml, titleConfig.yml, locale/messages_vi.yml
    (generalConfig / messages_en lấy từ bản mặc định của Jobs: tools/jobs/goc/)
  - thay phần "Nghề" trong modes/banghoi/settings.schema.yml + giá trị trong settings.yml (mục Cài đặt trỏ vào file Jobs mới)
8 nghề, mỗi người 1 nghề (max-jobs 1): Thợ mỏ, Tiều phu, Thợ đào đất, Nông dân (gộp Chăn nuôi), Thợ săn, Ngư dân,
Phù thuỷ (phù phép + pha thuốc), Thợ chế tạo. Bỏ: Đấu sĩ (0 xu), Hộ vệ làng, Thương nhân (farm dân làng), Nhà thám hiểm
(Jobs chỉ có "Explore" theo chunk: bay elytra là ra tiền vô hạn).
Tiền theo cấp: +0,5% mỗi cấp, tối đa cấp 50 (+24,5%) để giữ mô hình giờ công 1.400 xu (docs/dinh-gia-xu.html).
"""
import re
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
SRV = ROOT / "modes/banghoi/server/plugins"
GOC = Path(__file__).resolve().parent / "goc"
OUT = SRV / "Jobs"
SCHEMA = ROOT / "modes/banghoi/settings.schema.yml"
VALUES = ROOT / "modes/banghoi/settings.yml"

# mã uJobs -> (file Jobs, tên khoá trong file, tên hiện, chữ tắt, màu chat, biểu tượng, mô tả)
JOBS = {
    "miner": ("miner", "Miner", "Thợ mỏ", "Mo", "GRAY", "iron_pickaxe", ["Đào đá, quặng ra tiền.", "Hợp đào mỏ, làm hầm."]),
    "lumberjack": ("woodcutter", "Woodcutter", "Tiều phu", "Ti", "GOLD", "iron_axe", ["Chặt cây ra tiền.", "Gỗ bán cho server cũng được giá."]),
    "digger": ("digger", "Digger", "Thợ đào đất", "Da", "YELLOW", "iron_shovel", ["Đào đất, cát, sỏi, đất sét ra tiền.", "Hợp san đất, làm đường."]),
    "farmer": ("farmer", "Farmer", "Nông dân", "No", "GREEN", "iron_hoe", ["Gặt cây chín, hái quả, cho thú đẻ,", "thuần hoá thú ra tiền."]),
    "hunter": ("hunter", "Hunter", "Thợ săn", "Sa", "RED", "iron_sword", ["Hạ quái ra tiền.", "Quái từ lồng sinh quái không tính."]),
    "fisher": ("fisherman", "Fisherman", "Ngư dân", "Ng", "AQUA", "fishing_rod", ["Câu cá ra tiền (cộng với tiền bán cá).", "Cá hiếm ra nhiều hơn."]),
    "witcher": ("enchanter", "Enchanter", "Phù thuỷ", "Ph", "LIGHT_PURPLE", "enchanting_table", ["Phù phép đồ và pha thuốc ra tiền."]),
    "producer": ("crafter", "Crafter", "Thợ chế tạo", "Ch", "DARK_AQUA", "crafting_table", ["Chế đồ đá đỏ, máy móc ra tiền."]),
}
GOP = {"husbandry": "farmer"}           # gộp nghề: Chăn nuôi -> Nông dân
BO = {"gladiator", "guardian", "trader", "traveler"}
ACT = {"break": "Break", "kill": "Kill", "fish": "Fish", "breed": "Breed", "tame": "Tame", "harvest": "Collect", "craft": "Craft"}

# Phù thuỷ: uJobs trả theo số cấp phù phép (0,2 xu/cấp sau ×2) -> Jobs trả theo món được phù phép (≈ phù phép cấp 15–20)
ENCHANT = {"book": 3}
for m in ["leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "golden_sword", "golden_pickaxe", "golden_axe",
          "golden_shovel", "golden_hoe", "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots", "stone_sword",
          "stone_pickaxe", "stone_axe", "stone_shovel", "stone_hoe", "chainmail_helmet", "chainmail_chestplate",
          "chainmail_leggings", "chainmail_boots", "iron_sword", "iron_pickaxe", "iron_axe", "iron_shovel", "iron_hoe",
          "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "bow", "crossbow", "fishing_rod", "shears", "shield"]:
    ENCHANT[m] = 2.5
for m in ["diamond_sword", "diamond_pickaxe", "diamond_axe", "diamond_shovel", "diamond_hoe", "diamond_helmet", "diamond_chestplate",
          "diamond_leggings", "diamond_boots", "netherite_sword", "netherite_pickaxe", "netherite_axe", "netherite_shovel",
          "netherite_hoe", "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots", "trident", "mace"]:
    ENCHANT[m] = 4
# pha thuốc: mỗi lần nguyên liệu tan trong lò (tối đa 3 bình)
BREW = {"nether_wart": 1.0, "redstone": 1.0, "glowstone_dust": 1.2, "fermented_spider_eye": 1.5, "gunpowder": 1.0, "dragon_breath": 3.0,
        "sugar": 1.0, "rabbit_foot": 2.5, "glistering_melon_slice": 1.5, "spider_eye": 1.2, "blaze_powder": 1.5, "golden_carrot": 1.5,
        "magma_cream": 1.5, "pufferfish": 1.5, "ghast_tear": 3.0, "phantom_membrane": 2.0, "turtle_helmet": 2.0}

VI_ITEM = {}
for line in (ROOT / "network-core/core-paper/src/main/resources/ten-vat-pham-vi.properties").read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.startswith("#"):
        k, v = line.split("=", 1)
        VI_ITEM[k.strip().lower()] = v.strip()


def vi(m):
    return VI_ITEM.get(m.lower(), m.replace("_", " "))


def key_of(act, k):
    """khoá trong file Jobs: cây trồng 'wheat7' -> 'wheat-7', sinh vật viết HOA."""
    k = str(k)
    if act in ("kill", "breed", "tame"):
        return k.upper()
    m = re.fullmatch(r"([a-z_]+?)(\d+)", k)
    if act == "break" and m and k not in ("melon",):
        return f"{m.group(1)}-{m.group(2)}"
    return k


def num(v):
    v = round(float(v), 4)
    return int(v) if v.is_integer() else v


def main():
    uj = yaml.safe_load((SRV / "UJobs/jobs.yml").read_text(encoding="utf-8"))["jobs"]
    # nhãn cũ trên trang Cài đặt
    labels = {}
    for m in re.finditer(r'key: (job_[a-z0-9_]+), label: "([^"]+)"', SCHEMA.read_text(encoding="utf-8")):
        labels[m.group(1)] = m.group(2)

    files = {}          # mã nghề -> {Action: {khoá: {income, points, experience}}}
    rows = {}           # mã nghề -> [(key cài đặt, nhãn, [đường dẫn ...], giá)]
    for jid, job in uj.items():
        if jid in BO:
            continue
        dst = GOP.get(jid, jid)
        acts = files.setdefault(dst, {})
        for act, items in (job.get("actions") or {}).items():
            if act not in ACT or not isinstance(items, dict):
                continue
            for k, v in items.items():
                if not isinstance(v, dict) or not v.get("money"):
                    continue
                ja = ACT[act]
                keys = [key_of(act, k)]
                if act == "harvest" and str(k) == "sweet_berries":
                    keys = ["sweet_berries-2", "sweet_berries-3"]
                for jk in keys:
                    acts.setdefault(ja, {})[jk] = {"income": num(v["money"]), "points": 0, "experience": num(v.get("exp", 1))}
                skey = f"job_{jid}_{act}_{k}"
                fname, jname = JOBS[dst][0], JOBS[dst][1]
                paths = [f"{jname}.{ja}.{jk}.income" for jk in keys]
                rows.setdefault(dst, []).append((skey, labels.get(skey, f"{ja} {k}"), fname, paths, num(v["money"])))
    acts = files.setdefault("witcher", {})
    rows.setdefault("witcher", [])
    for m, inc in ENCHANT.items():
        acts.setdefault("Enchant", {})[m] = {"income": inc, "points": 0, "experience": inc * 2}
        rows["witcher"].append((f"job_witcher_enchant_{m}", f"Phù phép {vi(m)}", "enchanter", [f"Enchanter.Enchant.{m}.income"], inc))
    for m, inc in BREW.items():
        acts.setdefault("Brew", {})[m] = {"income": inc, "points": 0, "experience": inc * 2}
        rows["witcher"].append((f"job_witcher_brew_{m}", f"Pha thuốc với {vi(m)}", "enchanter", [f"Enchanter.Brew.{m}.income"], inc))
    rows["witcher"] = [r for r in rows["witcher"] if not re.match(r"job_witcher_(enchant_\d+|anvil_merge)", r[0])]

    (OUT / "jobs").mkdir(parents=True, exist_ok=True)
    for jid, (fname, jname, ten, short, color, icon, desc) in JOBS.items():
        doc = {jname: {
            "fullname": ten, "shortname": short, "FullDescription": desc, "ChatColour": color, "BossBarColour": "WHITE",
            "chat-display": "none", "max-level": 50,
            "leveling-progression-equation": "10*(joblevel)+(joblevel*joblevel*4)",
            "income-progression-equation": "baseincome+(baseincome*(joblevel-1)*0.005)",
            "points-progression-equation": "0",
            "experience-progression-equation": "baseexperience",
            "rejoinCooldown": 86400,
            "Gui": {"ItemStack": f"{icon};hideenchants"},
            **files.get(jid, {})}}
        head = (f"# Nghề {ten} (Jobs Reborn). SINH TỰ ĐỘNG bằng tools/jobs/chuyen-ujobs.py (21/10) từ bảng giá uJobs; sửa giá trên trang\n"
                f"# Cài đặt › Nghề (ghi thẳng vào file này) hoặc sửa tay rồi /jobs reload. income = xu mỗi lần, +0,5%/cấp, tối đa cấp 50.\n")
        (OUT / "jobs" / f"{fname}.yml").write_text(head + yaml.safe_dump(doc, allow_unicode=True, sort_keys=False, width=200), encoding="utf-8")

    # generalConfig: bản mặc định của Jobs + vài chỗ đổi
    g = (GOC / "generalConfig.yml").read_text(encoding="utf-8")
    for a, b in [(r"^locale-language: en$", "locale-language: vi"),
                 (r"^max-jobs: \d+$", "max-jobs: 1"),
                 (r"^jobs-shop-enabled: true$", "jobs-shop-enabled: false"),
                 (r"^enable-pay-near-spawner: true$", "enable-pay-near-spawner: false"),
                 (r"^enable-pay-creative: true$", "enable-pay-creative: false"),
                 (r"^  ShowChatBrowse: true$", "  ShowChatBrowse: false"),
                 (r"^(  PaymentMethods:\n    Money: true\n    Exp: true\n    )Points: true$", r"\1Points: false"),
                 (r"^(DailyQuests:\n(?:  #.*\n)*  Enabled: )true$", r"\1false")]:
        g2 = re.sub(a, b, g, count=1, flags=re.M)
        if g2 == g:
            raise SystemExit(f"generalConfig: không thấy {a}")
        g = g2
    (OUT / "generalConfig.yml").write_text("# DuckyMoMo 21/10: bản mặc định Jobs 5.2.6.7, đổi: tiếng Việt, 1 nghề/người, tắt điểm nghề + shop điểm,\n"
                                           "# không trả tiền gần lồng quái / chế độ sáng tạo, tắt nhiệm vụ nghề (đã có /nhiemvu), /jobs browse chỉ mở menu (không in chat).\n" + g, encoding="utf-8")

    # danh hiệu nghề theo cấp (tối đa 50)
    titles = {"Titles": {}}
    for i, (k, ten, short, color) in enumerate([("TapSu", "Tập sự", "TS", "GRAY"), ("HocViec", "Học việc", "HV", "WHITE"),
                                                ("ThaoViec", "Thạo việc", "TV", "YELLOW"), ("LanhNghe", "Lành nghề", "LN", "GREEN"),
                                                ("BacThay", "Bậc thầy", "BT", "AQUA"), ("DaiSu", "Đại sư", "ĐS", "GOLD")]):
        titles["Titles"][k] = {"Name": ten, "ShortName": short, "ChatColour": color, "levelReq": i * 10}
    (OUT / "titleConfig.yml").write_text("# DuckyMoMo 21/10: danh hiệu nghề theo cấp (tối đa cấp 50).\n" + yaml.safe_dump(titles, allow_unicode=True, sort_keys=False), encoding="utf-8")

    # tiếng Việt: bản tiếng Anh của Jobs + thay các dòng người chơi thấy
    msg = yaml.safe_load((GOC / "messages_en.yml").read_text(encoding="utf-8"))
    for path, text in VI_MSG.items():
        node = msg
        ks = path.split(".")
        for k in ks[:-1]:
            node = node[k]
        if ks[-1] not in node:
            raise SystemExit(f"messages_en không có {path}")
        node[ks[-1]] = text
    # tên nghề: [jobname] = mã nghề (Miner…) -> dùng [jobfullname] (Thợ mỏ…), trừ phần mô tả cú pháp lệnh (help.args)
    def full(node, path=""):
        if isinstance(node, dict):
            for k in node:
                node[k] = full(node[k], f"{path}.{k}")
            return node
        if isinstance(node, list):
            return [full(x, path) for x in node]
        if isinstance(node, str) and ".help.args" not in path:
            return re.sub(r"(?i)([\[%])jobname([\]%])", r"\1jobfullname\2", node)
        return node
    full(msg)
    (OUT / "locale").mkdir(exist_ok=True)
    (OUT / "locale/messages_vi.yml").write_text(yaml.safe_dump(msg, allow_unicode=True, sort_keys=False, width=200), encoding="utf-8")

    # tên vật phẩm / sinh vật / phù phép / thuốc / màu tiếng Việt (TranslatableWords/Words_vi.yml) từ file ngôn ngữ vi_vn của game
    lang_file = ROOT / "data/banghoi-1/plugins/MyPet/cache/vanilla-lang/26.2/vi_vn.json"
    if lang_file.exists():
        import json
        lang = json.loads(lang_file.read_text(encoding="utf-8"))
        words = yaml.safe_load((GOC / "Words_en.yml").read_text(encoding="utf-8"))
        cap = lambda t: t[:1].upper() + t[1:]
        n = 0
        for k in list(words.get("ItemList") or {}):
            t = lang.get(f"block.minecraft.{k.lower()}") or lang.get(f"item.minecraft.{k.lower()}") or VI_ITEM.get(k.lower())
            if t:
                words["ItemList"][k] = cap(t); n += 1
        for k in list(words.get("EntityList") or {}):
            t = lang.get("entity.minecraft." + k.split("-", 1)[-1].lower())
            if t:
                words["EntityList"][k] = cap(t); n += 1
        for k in list(words.get("EnchantList") or {}):
            t = lang.get("enchantment.minecraft." + k.lower().replace(" ", "_"))
            if t:
                words["EnchantList"][k] = t; n += 1
        for k in list(words.get("PotionEffects") or {}):
            base, suf = k.lower(), ""
            if base.startswith("long "):
                base, suf = base[5:], " (lâu)"
            elif base.startswith("strong "):
                base, suf = base[7:], " II"
            base = base.replace(" ", "_")
            t = lang.get(f"item.minecraft.potion.effect.{base}") or lang.get(f"effect.minecraft.{base}")
            if t:
                words["PotionEffects"][k] = t + suf; n += 1
        for k, v in list((words.get("ColorList") or {}).items()):
            t = lang.get("color.minecraft." + k.split("-", 1)[-1])
            if t:
                code = re.match(r"^(&[0-9a-fk-or])*", str(v)).group(0)
                words["ColorList"][k] = code + cap(re.sub(r"^Màu ", "", t)); n += 1
        (OUT / "TranslatableWords").mkdir(exist_ok=True)
        (OUT / "TranslatableWords/Words_vi.yml").write_text(
            "# DuckyMoMo 21/10: tên tiếng Việt lấy từ file ngôn ngữ vi_vn của game (tools/jobs/chuyen-ujobs.py).\n"
            + yaml.safe_dump(words, allow_unicode=True, sort_keys=False, width=200), encoding="utf-8")
        print("Words_vi:", n, "tên")

    # trang Cài đặt: thay khối "Nghề"
    s = SCHEMA.read_text(encoding="utf-8")
    a = s.index("  # ---- Nghề (")
    b = s.index("  # ---- Chống hack")
    out = ["  # ---- Nghề (Jobs Reborn, 21/10): tiền mỗi lần làm, sinh bằng tools/jobs/chuyen-ujobs.py. Mỗi người chỉ 1 nghề (/jobs).\n"]
    vals = {}
    for jid, (fname, jname, ten, *_rest) in JOBS.items():
        out.append(f'\n  - title: "Nghề: {ten}"\n    category: Nghề\n'
                   f'    description: "Tiền mỗi lần làm (xu), cộng thêm 0,5% mỗi cấp nghề (tối đa cấp 50). Mỗi người chỉ làm 1 nghề, đổi nghề phải chờ 1 ngày. '
                   f'Phá lại block tự đặt không được tiền."\n    apply: [jobs reload]\n    settings:\n')
        for skey, label, fn, paths, val in rows.get(jid, []):
            first, rest = paths[0], paths[1:]
            tg = "" if not rest else ", targets: [ " + ", ".join(f"{{ file: plugins/Jobs/jobs/{fn}.yml, path: {p} }}" for p in rest) + " ]"
            out.append(f'      - {{ key: {skey}, label: "{label}", type: number, min: 0, max: 100000, file: plugins/Jobs/jobs/{fn}.yml, path: {first}{tg} }}\n')
            vals[skey] = val
    out.append("\n")
    SCHEMA.write_text(s[:a] + "".join(out) + s[b:], encoding="utf-8")

    v = VALUES.read_text(encoding="utf-8").split("\n")
    v = [line for line in v if not re.match(r"^job_[a-z0-9_]+:", line)]
    while v and v[-1] == "":
        v.pop()
    v += [f"{k}: {val}" for k, val in vals.items()]
    VALUES.write_text("\n".join(v) + "\n", encoding="utf-8")
    print("Xong:", {j: sum(len(x) for x in files.get(j, {}).values()) for j in JOBS}, "mục Cài đặt:", len(vals))


VI_MSG = {
    "limitedItem.error.levelup": "&cCần lên cấp nghề [jobname] mới dùng được món này!",
    "command.help.output.title": "&e------- &6Nghề &e-------",
    "command.help.output.cmdUsage": "&2Cách dùng: &7[command]",
    "command.help.output.label": "Nghề",
    "command.stats.help.info": "Xem cấp nghề của bạn.",
    "command.stats.error.nojob": "Bạn chưa có nghề. Gõ /jobs để chọn.",
    "command.stats.output.Level": "&7Cấp &f%joblevel% &7nghề &f%jobname%&7: &f%jobxp%&7/&f%jobmaxxp%&7 kinh nghiệm",
    "command.stats.output.maxLevel": "    &2Tối đa    &7Cấp &f%joblevel% &7nghề &f%jobname%",
    "command.stats.bossBarOutput": "Nghề · Cấp %joblevel% %jobname%: %jobxp%/%jobmaxxp%%gain% +%money% xu",
    "command.info.help.title": "&2*** &eNghề&2 ***",
    "command.info.help.info": "Xem nghề trả tiền cho việc gì, bao nhiêu.",
    "command.info.help.penalty": "&eNghề này đang bị giảm &c[penalty]% &evì quá đông người làm.",
    "command.info.help.bonus": "&eNghề này đang được thêm &2[bonus]% &evì ít người làm.",
    "command.info.help.actions": "&eViệc được trả tiền: &f%actions%",
    "command.info.help.max": " - &ecấp tối đa:&f ",
    "command.info.help.newMax": " &eCấp tối đa: &f[max]",
    "command.info.help.levelRange": " &a(cấp &e%levelFrom% &a- &e%levelUntil%&a)",
    "command.info.help.levelFrom": " &a(từ cấp &e%levelFrom%&a)",
    "command.info.help.levelUntil": " &a(tới cấp &e%levelUntil%&a)",
    "command.info.help.money": " &2%money% xu",
    "command.info.help.exp": " &e%exp% kn",
    "command.info.gui.pickjob": "&0Chọn nghề của bạn &8(chỉ 1 nghề)",
    "command.info.gui.jobinfo": "&0Nghề [jobname]",
    "command.info.gui.actions": "&eViệc được trả tiền:",
    "command.info.gui.leftClick": "&eChuột trái&7: xem chi tiết",
    "command.info.gui.middleClick": "&eChuột giữa&7: rời nghề này",
    "command.info.gui.qClick": "&ePhím Q&7: rời nghề này",
    "command.info.gui.rightClick": "&aChuột phải&7: tham gia nghề",
    "command.info.gui.leftSlots": "&eCòn chỗ:&f ",
    "command.info.gui.working": "&2Bạn đang làm nghề này",
    "command.info.gui.cantJoin": "&cBạn không vào được nghề này.",
    "command.info.gui.max": "&eCấp tối đa:&f ",
    "command.info.gui.infoLore": ["&7Đóng"],
    "command.info.output.break.info": "&eĐào / phá",
    "command.info.output.kill.info": "&eHạ quái",
    "command.info.output.fish.info": "&eCâu cá",
    "command.info.output.craft.info": "&eChế tạo",
    "command.info.output.brew.info": "&ePha thuốc",
    "command.info.output.enchant.info": "&ePhù phép",
    "command.info.output.breed.info": "&eCho thú đẻ",
    "command.info.output.tame.info": "&eThuần hoá",
    "command.info.output.collect.info": "&eHái quả",
    "command.join.help.info": "Tham gia nghề.",
    "command.join.help.args": "[tên nghề]",
    "command.join.error.alreadyin": "Bạn đang làm nghề %jobname% rồi.",
    "command.join.error.fullslots": "Nghề %jobname% đã đủ người.",
    "command.join.error.maxjobs": "Mỗi người chỉ được 1 nghề. Rời nghề đang làm trước (/jobs leave <nghề>).",
    "command.join.error.rejoin": "&cChưa vào lại nghề này được. Chờ [time]",
    "command.join.rejoin": "&aBấm để vào lại nghề: ",
    "command.join.success": "Bạn đã vào nghề %jobname%.",
    "command.join.confirm": "&2Bấm lần nữa để xác nhận vào nghề &7[jobname]&2.",
    "command.leave.help.info": "Rời nghề.",
    "command.leave.help.args": "[nghề đang làm]",
    "command.leave.success": "&2Bạn đã rời nghề &7[jobname]&2.",
    "command.leave.confirmationNeed": "&cRời nghề&e [jobname]&c? Vào lại phải chờ 1 ngày và mất bớt cấp. Gõ lại lệnh trong&6 [time] giây &cđể xác nhận!",
    "command.leaveall.help.info": "Rời mọi nghề.",
    "command.leaveall.error.nojobs": "Bạn chưa có nghề nào!",
    "command.leaveall.success": "Bạn đã rời mọi nghề.",
    "command.leaveall.confirmationNeed": "&cRời mọi nghề? Gõ lại lệnh trong&6 [time] giây &cđể xác nhận!",
    "command.browse.help.info": "Xem các nghề có thể vào.",
    "command.browse.error.nojobs": "Không có nghề nào bạn vào được.",
    "command.browse.output.header": "Các nghề bạn có thể vào:",
    "command.browse.output.footer": "Xem thêm: /jobs info <nghề>",
    "command.browse.output.totalWorkers": " &7Người làm: &e[amount]",
    "command.browse.output.penalty": " &4Giảm: &c[amount]%",
    "command.browse.output.bonus": " &2Thêm: &a[amount]%",
    "command.browse.output.newHeader": "&2========== [amount] nghề ==========",
    "command.browse.output.click": "&bBấm vào nghề để xem chi tiết!",
    "command.browse.output.detailed": "&bBấm để xem đủ việc được trả tiền",
    "command.browse.output.jobHeader": "&2========== [jobname] ==========",
    "command.browse.output.chooseJob": "&7&nChọn nghề này",
    "command.browse.output.chooseJobHover": "&7Bấm để vào nghề này",
    "command.fire.output.target": "Bạn đã bị cho thôi nghề %jobname%.",
    "command.fireall.output.target": "Bạn đã bị cho thôi mọi nghề.",
    "command.employ.output.target": "Bạn được nhận vào nghề %jobname%.",
    "command.top.help.info": "Top người chơi theo nghề.",
    "command.top.output.topline": "&aTop&e %amount% &angười chơi nghề &e%jobname%",
    "command.top.output.list": "&e%number%&a. &e%playerdisplayname% &acấp &e%level% &a(&e%exp% &akn)",
    "command.gtop.output.topline": "&aTop&e %amount% &angười chơi theo tổng cấp nghề",
    "command.gtop.output.list": "&e%number%&a. &e%playerdisplayname% &acấp &e%level% &a(&e%exp% &akn)",
    "command.toggle.help.info": "Bật/tắt dòng báo tiền nghề trên thanh hành động hoặc thanh trên cùng.",
    "command.toggle.output.paid.main": "&aBạn nhận được:",
    "command.toggle.output.paid.money": "&e[amount] xu",
    "command.toggle.output.paid.exp": "&7[exp] kinh nghiệm nghề",
    "command.toggle.output.paid.ACmoney": "&e+[amount] xu ",
    "command.toggle.output.paid.ACexp": "&7+[exp] kn ",
    "command.toggle.output.paid.ACpoints": "",
    "command.limit.output.reachedmoneylimit": "{gcw}Bạn đã chạm giới hạn tiền nghề trong khoảng này!",
    "general.info.join": "{gcp}Bấm để vào nghề",
    "general.info.leave": "{gcw}Bấm để rời nghề",
    "general.info.invalidPage": "{gcw}Trang không hợp lệ",
    "general.error.job": "{gcw}Không có nghề này!",
    "general.error.jobname": "{gcw}Không tìm thấy nghề tên này!",
    "general.error.worldisdisabled": "{gcw}Không dùng lệnh nghề ở thế giới này!",
    "general.error.noRegistration": "{gcw}Bạn đã đặt tối đa số [block]!",
    "general.error.newRegistration": "{gcp}Đã ghi chủ cho {gcn}[block] {gcs}[current]{gcp}/{gcs}[max]",
    "message.skillup.broadcast": "[playerdisplayname] lên danh hiệu [titlename] nghề [jobname].",
    "message.skillup.nobroadcast": "Chúc mừng! Bạn lên danh hiệu [titlename] nghề [jobname].",
    "message.levelup.broadcast": "[playerdisplayname] lên cấp [joblevel] nghề [jobname].",
    "message.levelup.nobroadcast": "Bạn lên cấp [joblevel] nghề [jobname].",
    "message.blocktimer": "{gcp}Chờ thêm {gcs}[time] {gcp}giây mới được trả tiền cho việc này!",
    "message.crafting.fullinventory": "{gcw}Túi đồ đầy!",
}

if __name__ == "__main__":
    main()
