"""(Dùng khi nâng AuraSkills) Dựng messages_vi.yml của AuraSkills theo cấu trúc messages_en.yml (2.4.0): câu Việt có sẵn ở đúng chỗ thì giữ, còn lại dùng T.
Chạy từ thư mục repo: python3 tools/auraskills-vi.py data/banghoi-1/plugins/AuraSkills/messages modes/banghoi/server/plugins/AuraSkills/messages/messages_vi.yml"""
import json
import re
import sys

import yaml

SRC, OUT = sys.argv[1], sys.argv[2]
X = "+{value}% XP"
T = {
    "abilities.farmer.info": X, "abilities.forager.info": X, "abilities.miner.info": X, "abilities.fisher.info": X,
    "abilities.excavator.info": X, "abilities.archer.info": X, "abilities.defender.info": X, "abilities.fighter.info": X,
    "abilities.runner.info": X, "abilities.jumper.info": X, "abilities.brewer.info": X, "abilities.enchanter.info": X,
    "abilities.healer.info": X, "abilities.forger.info": X,
    "abilities.lumberjack.name": "Tiều phu", "abilities.lucky_miner.name": "Thợ mỏ may mắn", "abilities.grappler.name": "Móc kéo",
    "abilities.no_debuff.name": "Miễn hiệu ứng xấu", "abilities.runner.name": "Người chạy", "abilities.golden_heal.name": "Hồi máu vàng",
    "abilities.healer.name": "Người chữa lành", "abilities.life_steal.name": "Hút máu",
    "commands.antiafk.failed": "<red>{player} không qua kiểm tra chống AFK {check} <gray>(Số lần: {count})",
    "commands.antiafk.logs.header": "<gray>Nhật ký chống AFK của <red>{player} <gray>({count} mục) <dark_gray>| <gray>Trang {page}/{total_pages}:",
    "commands.antiafk.logs.entry": "<gray>{timestamp} - {message}",
    "commands.antiafk.logs.desc": "Xem nhật ký chống AFK của một người chơi.",
    "commands.armor.modifier.add.desc": "Thêm chỉ số cho giáp đang cầm (mặc định kèm dòng mô tả).",
    "commands.armor.modifier.remove.desc": "Gỡ một chỉ số khỏi giáp đang cầm (mặc định gỡ cả dòng mô tả).",
    "commands.armor.modifier.list.desc": "Liệt kê chỉ số trên giáp đang cầm.",
    "commands.armor.modifier.removeall.desc": "Gỡ mọi chỉ số khỏi giáp đang cầm.",
    "commands.armor.trait.add.added": "Đã thêm đặc tính cho giáp: {trait}, giá trị: {value}, phép tính: {operation}",
    "commands.armor.trait.add.already_exists": "<yellow>Giáp đã có đặc tính {trait}<yellow>! Gỡ trước bằng /sk armor trait remove",
    "commands.armor.trait.add.desc": "Thêm đặc tính cho giáp đang cầm (mặc định kèm dòng mô tả).",
    "commands.armor.trait.remove.desc": "Gỡ một đặc tính khỏi giáp đang cầm.",
    "commands.armor.trait.list.desc": "Liệt kê đặc tính trên giáp đang cầm.",
    "commands.armor.trait.removeall.desc": "Gỡ mọi đặc tính khỏi giáp đang cầm.",
    "commands.armor.multiplier.add.desc": "Thêm hệ số XP cho giáp đang cầm (mọi kỹ năng hoặc một kỹ năng; giá trị = % XP thêm).",
    "commands.armor.multiplier.remove.desc": "Gỡ hệ số XP của một kỹ năng (hoặc hệ số chung) khỏi giáp đang cầm.",
    "commands.armor.multiplier.list.desc": "Liệt kê hệ số XP trên giáp đang cầm.",
    "commands.armor.multiplier.removeall.desc": "Gỡ mọi hệ số XP khỏi giáp đang cầm.",
    "commands.armor.requirement.add.desc": "Thêm yêu cầu cấp cho giáp đang cầm (mặc định kèm dòng mô tả).",
    "commands.armor.requirement.remove.desc": "Gỡ một yêu cầu cấp khỏi giáp đang cầm (mặc định gỡ cả dòng mô tả).",
    "commands.armor.requirement.list.desc": "Liệt kê yêu cầu cấp trên giáp đang cầm.",
    "commands.armor.requirement.removeall.desc": "Gỡ mọi yêu cầu cấp khỏi giáp đang cầm.",
    "commands.backup.load.desc": "Nạp bản sao lưu từ một file.",
    "commands.backup.save.desc": "Sao lưu dữ liệu hiện tại vào thư mục backups.",
    "commands.claimitems.desc": "Mở menu nhận các vật phẩm chưa nhận.",
    "commands.item.give.desc": "Đưa cho người chơi một vật phẩm đã đăng ký theo mã.",
    "commands.item.modifier.add.desc": "Thêm chỉ số cho vật phẩm đang cầm (mặc định kèm dòng mô tả).",
    "commands.item.modifier.remove.desc": "Gỡ một chỉ số khỏi vật phẩm đang cầm (mặc định gỡ cả dòng mô tả).",
    "commands.item.modifier.list.desc": "Liệt kê chỉ số trên vật phẩm đang cầm.",
    "commands.item.modifier.removeall.desc": "Gỡ mọi chỉ số khỏi vật phẩm đang cầm.",
    "commands.item.trait.add.added": "Đã thêm đặc tính cho vật phẩm: {trait}, giá trị: {value}, phép tính: {operation}",
    "commands.item.trait.add.already_exists": "<yellow>Vật phẩm đã có đặc tính {trait}<yellow>! Gỡ trước bằng /sk item trait remove",
    "commands.item.trait.add.desc": "Thêm đặc tính cho vật phẩm đang cầm (mặc định kèm dòng mô tả).",
    "commands.item.trait.remove.desc": "Gỡ một đặc tính khỏi vật phẩm đang cầm.",
    "commands.item.trait.list.desc": "Liệt kê đặc tính trên vật phẩm đang cầm.",
    "commands.item.trait.removeall.desc": "Gỡ mọi đặc tính khỏi vật phẩm đang cầm.",
    "commands.item.multiplier.add.desc": "Thêm hệ số XP cho vật phẩm đang cầm (mọi kỹ năng hoặc một kỹ năng; giá trị = % XP thêm).",
    "commands.item.multiplier.remove.desc": "Gỡ hệ số XP của một kỹ năng (hoặc hệ số chung) khỏi vật phẩm đang cầm.",
    "commands.item.multiplier.list.desc": "Liệt kê hệ số XP trên vật phẩm đang cầm.",
    "commands.item.multiplier.removeall.desc": "Gỡ mọi hệ số XP khỏi vật phẩm đang cầm.",
    "commands.item.register.desc": "Đăng ký vật phẩm vào danh sách theo mã để dùng trong cấu hình plugin.",
    "commands.item.requirement.add.desc": "Thêm yêu cầu cấp cho vật phẩm đang cầm (mặc định kèm dòng mô tả).",
    "commands.item.requirement.remove.desc": "Gỡ một yêu cầu cấp khỏi vật phẩm đang cầm (mặc định gỡ cả dòng mô tả).",
    "commands.item.requirement.list.desc": "Liệt kê yêu cầu cấp trên vật phẩm đang cầm.",
    "commands.item.requirement.removeall.desc": "Gỡ mọi yêu cầu cấp khỏi vật phẩm đang cầm.",
    "commands.item.unregister.desc": "Xoá vật phẩm khỏi danh sách đã đăng ký theo mã.",
    "commands.item.ignore.add.added": "<gray>Vật phẩm của bạn sẽ không kích hoạt khả năng đặc biệt nữa",
    "commands.item.ignore.add.desc": "Gắn nhãn cho vật phẩm đang cầm để không kích hoạt khả năng đặc biệt.",
    "commands.item.ignore.remove.removed": "<gray>Vật phẩm của bạn lại kích hoạt được khả năng đặc biệt",
    "commands.item.ignore.remove.desc": "Gỡ nhãn không kích hoạt khả năng đặc biệt khỏi vật phẩm đang cầm.",
    "commands.jobs.add.added": "<white>Đã thêm nghề {job} cho {player}",
    "commands.jobs.add.limited": "<yellow>Không thêm được: {player} đã đủ số nghề tối đa",
    "commands.jobs.add.existing": "<yellow>{player} đã có nghề {job}",
    "commands.jobs.add.desc": "Chọn một kỹ năng làm nghề cho mình hoặc người khác.",
    "commands.jobs.remove.removed": "<white>Đã bỏ nghề {job} của {player}",
    "commands.jobs.remove.unchanged": "<yellow>Không đổi gì: {player} không làm nghề {job}",
    "commands.jobs.remove.desc": "Bỏ một nghề của mình hoặc người khác.",
    "commands.jobs.removeall.removed": "<white>Đã bỏ mọi nghề của {player}",
    "commands.jobs.removeall.desc": "Bỏ mọi nghề của mình hoặc người khác.",
    "commands.lang.desc": "Đổi ngôn ngữ của bạn.",
    "commands.mana.desc": "Xem năng lượng hiện tại và tối đa của bạn hoặc người khác",
    "commands.mana.add.added": "<gray>Đã thêm <aqua>{amount} {mana_unit} <gray>cho <gold>{player}",
    "commands.mana.add.desc": "Thêm năng lượng cho người chơi",
    "commands.mana.remove.removed": "<gray>Đã bớt <aqua>{amount} {mana_unit} <gray>của <gold>{player}",
    "commands.mana.remove.desc": "Bớt năng lượng của người chơi",
    "commands.mana.set.set": "<gray>Đã đặt năng lượng của <gold>{player} <gray>thành <aqua>{amount} {mana_unit}",
    "commands.mana.set.desc": "Đặt năng lượng của người chơi",
    "commands.modifier.add.desc": "Thêm chỉ số cộng thêm cho người chơi.",
    "commands.modifier.addtemp.added": "Đã thêm chỉ số tạm thời cho {player}: chỉ số {stat}, tên {name}, giá trị {value}, thời hạn {duration}, phép tính {operation}",
    "commands.modifier.addtemp.desc": "Thêm chỉ số tạm thời cho người chơi.",
    "commands.modifier.remove.desc": "Gỡ một chỉ số cộng thêm của người chơi.",
    "commands.modifier.list.one_stat_entry": "  <white>{name} - {color}{stat} <white>Cấp <gold>{value}",
    "commands.modifier.list.desc": "Liệt kê chỉ số cộng thêm (tất cả hoặc một chỉ số) của người chơi.",
    "commands.modifier.removeall.desc": "Gỡ mọi chỉ số cộng thêm của người chơi.",
    "commands.trait.add.already_exists": "<yellow>Đã có đặc tính tên {name} trên <gold>{player}<yellow>! Gỡ bằng <gold>/sk trait remove {player} {name}",
    "commands.trait.add.desc": "Thêm đặc tính cho người chơi.",
    "commands.trait.addtemp.added": "Đã thêm đặc tính tạm thời cho {player}: đặc tính {trait}, tên {name}, giá trị {value}, thời hạn {duration}",
    "commands.trait.addtemp.desc": "Thêm đặc tính tạm thời cho người chơi.",
    "commands.trait.remove.desc": "Gỡ một đặc tính của người chơi.",
    "commands.trait.list.all_traits_header": "<gray>Đặc tính cộng thêm của <gold>{player}:",
    "commands.trait.list.one_trait_header": "<gray>Đặc tính cộng thêm ({color}{trait}<gray>) của <gold>{player}:",
    "commands.trait.list.desc": "Liệt kê đặc tính cộng thêm (tất cả hoặc một đặc tính) của người chơi.",
    "commands.trait.removeall.removed_all_traits": "<gray>Đã gỡ <aqua>{num} <gray>đặc tính cộng thêm của <gold>{player}",
    "commands.trait.removeall.desc": "Gỡ mọi đặc tính cộng thêm của người chơi.",
    "commands.multiplier.desc": "Xem hệ số XP hiện tại của người chơi (theo quyền).",
    "commands.openmenu.desc": "Mở một menu của plugin cho người chơi (kèm thuộc tính JSON và số trang nếu cần).",
    "commands.preset.load.desc": "Nạp một bộ cấu hình có sẵn trong thư mục presets.",
    "commands.profile.skills.header": "Kỹ năng của <gold>{name}:\n<gray>UUID: <white>{uuid}\n<gray>Kỹ năng:{skill_entries}",
    "commands.profile.skills.entry": "\n  <dark_aqua>{skill} <gray>- Cấp: <white>{level}<gray>, XP: <white>{xp}",
    "commands.profile.skills.desc": "Xem cấp và XP kỹ năng của bất kỳ người chơi nào (cả khi offline).",
    "commands.profile.stats.header": "Chỉ số của <gold>{name}:\n<gray>UUID: <white>{uuid}\n<gray>Chỉ số:{stat_entries}",
    "commands.profile.stats.entry": "\n  <dark_aqua>{stat} <gray>- Tổng: <white>{total_level}<gray>, Gốc: <white>{base_level}<gray>, Cộng thêm: <white>{modified_level}",
    "commands.profile.stats.desc": "Xem chỉ số của bất kỳ người chơi nào (cả khi offline).",
    "commands.rank.desc": "Xem thứ hạng kỹ năng của bạn.",
    "commands.reload.reloaded": "<green>Đã nạp lại cấu hình!",
    "commands.reload.desc": "Nạp lại cấu hình, lời nhắn, menu, bảng đồ rơi, thanh thông báo, và chỉ số máu, may mắn.",
    "commands.resethealth.desc": "Gỡ mọi thay đổi máu/chỉ số do AuraSkills thêm (để gỡ plugin). Chỉ dùng cho người chơi đang offline.",
    "commands.save.desc": "Lưu dữ liệu kỹ năng.",
    "commands.skill.addlevel.desc": "Cộng thêm số cấp cho một kỹ năng.",
    "commands.skill.setall.desc": "Đặt mọi kỹ năng của người chơi về một cấp.",
    "commands.skill.setlevel.desc": "Đặt cấp một kỹ năng cho người chơi.",
    "commands.skill.reset.desc": "Đưa mọi kỹ năng (hoặc một kỹ năng) của người chơi về cấp khởi đầu.",
    "commands.skills.desc": "Mở menu Kỹ năng: xem kỹ năng, tiến độ và khả năng.",
    "commands.stats.desc": "Mở menu Chỉ số: xem cấp và mô tả từng chỉ số.",
    "commands.sources.desc": "Mở menu Nguồn XP: làm gì thì được XP của một kỹ năng.",
    "commands.toggle.desc": "Bật/tắt thanh kinh nghiệm kỹ năng trên màn hình.",
    "commands.top.desc": "Xem người đứng đầu một kỹ năng",
    "commands.transfer.desc": "Chuyển dữ liệu từ người này sang người khác (theo UUID).",
    "commands.updateleaderboards.desc": "Cập nhật và sắp xếp lại bảng xếp hạng.",
    "commands.version.version": "<white>Đang dùng AuraSkills <aqua>{current_version}<white>. Bản mới nhất: <aqua>{latest_version}",
    "commands.version.new_update": "{prefix}<white>Có bản cập nhật! Đang dùng <aqua>{current_version}<white>, mới nhất <aqua>{latest_version}\n{prefix}<white>Tải trên Modrinth: <yellow><underlined>{link}",
    "commands.version.desc": "Xem phiên bản đang cài và bản mới nhất của plugin.",
    "commands.xp.add.added": "<gray>Đã cộng <aqua>{amount} XP {skill} <gray>cho <gray>{player}",
    "commands.xp.add.desc": "Cộng XP một kỹ năng cho người chơi.",
    "commands.xp.set.set": "<gray>Đã đặt XP <aqua>{skill} <gray>của <gray>{player} <gray>thành {amount}",
    "commands.xp.set.desc": "Đặt XP một kỹ năng của người chơi.",
    "commands.xp.remove.removed": "<gray>Đã bớt <aqua>{amount} XP {skill} <gray>của <gold>{player}",
    "commands.xp.remove.desc": "Bớt XP một kỹ năng của người chơi.",
    "commands.manaability.resetcooldown.cooldown_reset": "<gray>Đã làm mới thời gian hồi của <aqua>{ability} <gray>cho <gold>{player}",
    "commands.manaability.resetcooldown.desc": "Làm mới thời gian hồi của một khả năng đặc biệt (có thể nạp đầy năng lượng).",
    "commands.unknown_mana_ability": "<yellow>Không có khả năng đặc biệt này!",
    "commands.unknown_trait": "<yellow>Không có đặc tính này!",
    "menus.common.trait": "Đặc tính",
    "menus.common.trait_gain": "<1>Mỗi {color}1 {stat_name} <1>cho thêm {color}{level_modifier} {trait_name}<1>.",
    "menus.common.trait_level": "Cấp đặc tính",
    "menus.common.base": "Gốc",
    "menus.common.modifiers_additive": "Cộng thêm (cộng)",
    "menus.common.modifiers_multiplicative": "Cộng thêm (nhân)",
    "menus.skills.active_job": "Nghề đang làm",
    "menus.level_progression.job": "Nghề",
    "menus.level_progression.job_desc": "Chọn {skill} làm nghề thì vừa lên XP vừa kiếm được tiền.",
    "menus.level_progression.job_select": "Chuột trái để chọn {skill} làm nghề",
    "menus.level_progression.job_active": "Bạn đang làm nghề này",
    "menus.level_progression.job_quit": "Chuột phải để bỏ nghề này",
    "menus.level_progression.job_limit": "Bạn đã đủ số nghề tối đa. Bỏ một nghề trước rồi mới chọn được nghề này.",
    "menus.level_progression.job_cooldown": "Chưa đổi nghề được. Chọn lại sau {time}.",
    "menus.stats.stat_info_click": "Bấm để xem chi tiết chỉ số",
    "menus.stat_info.stat_details": "Chỉ số {stat_name}",
    "menus.stat_info.stat": "Chỉ số",
    "menus.stat_info.stat_level": "Cấp chỉ số",
    "menus.stat_info.traits_leveled": "Đặc tính được cộng",
    "menus.stat_info.stat_modifiers": "Chỉ số cộng thêm",
    "menus.stat_info.stat_reward_desc": "Nhờ cấp kỹ năng {skill}.",
    "menus.stat_info.stat_modifiers_click": "Bấm để xem chỉ số cộng thêm",
    "menus.stat_info.trait_modifiers_click": "Bấm để xem đặc tính cộng thêm",
    "menus.stat_info.showing_stat_modifiers": "Đang xem chỉ số cộng thêm",
    "menus.stat_info.showing_trait_modifiers": "Đang xem đặc tính cộng thêm",
    "menus.stat_info.base_level": "Cấp gốc",
    "menus.stat_info.base_level_desc": "Cấp gốc từ Minecraft và nguồn khác.",
    "menus.stat_info.linked_stat_desc": "Nhờ cấp chỉ số {stat}.",
    "menus.stat_info.ability_modifier_name": "Khả năng {name}",
    "menus.stat_info.ability_modifier_desc": "Nhờ khả năng {ability}.",
    "menus.stat_info.custom_modifier_desc": "Nhờ một chỉ số cộng thêm riêng.",
    "menus.stat_info.item_hand_desc": "Nhờ vật phẩm trên tay.",
    "menus.stat_info.item_off_hand_desc": "Nhờ vật phẩm tay trái.",
    "menus.stat_info.helmet_desc": "Nhờ mũ giáp.",
    "menus.stat_info.chestplate_desc": "Nhờ áo giáp.",
    "menus.stat_info.leggings_desc": "Nhờ quần giáp.",
    "menus.stat_info.boots_desc": "Nhờ giày giáp.",
    "menus.stat_info.effective_trait": "{trait_name} thực tế",
    "mana_abilities.terraform.name": "Xúc đất",
    "traits.hp.name": "Máu", "units.mana": "Năng lượng", "units.hp": "Máu", "units.xp": "XP",
}
NAMES = {"farming": "Trồng trọt", "foraging": "Đốn gỗ", "mining": "Đào mỏ", "fishing": "Câu cá", "excavation": "Đào đất", "alchemy": "Pha chế"}
DESC = {"farming": "Trồng và thu hoạch nông sản để nhận {xp_unit} Trồng trọt", "foraging": "Chặt cây để nhận {xp_unit} Đốn gỗ",
        "mining": "Đào đá và quặng để nhận {xp_unit} Đào mỏ", "fishing": "Câu cá để nhận {xp_unit} Câu cá",
        "excavation": "Đào bằng xẻng để nhận {xp_unit} Đào đất", "alchemy": "Pha và dùng thuốc để nhận {xp_unit} Pha chế"}
REPL = [("XP Thu hoạch", "XP Trồng trọt"), ("XP Khai thác", "XP Đào mỏ"), ("XP Đào bới", "XP Đào đất"), ("XP Điều chế", "XP Pha chế"),
        ("XP Chặt gỗ", "XP Đốn gỗ"), ("May mắn đào bới", "May mắn đào đất"), ("khi đào bới bằng xẻng", "khi đào đất bằng xẻng"),
        ("Kĩ năng", "Kỹ năng"), ("<aqua>Khả năng<dark_gray>", "<aqua>Kỹ năng<dark_gray>"), ("kĩ năng", "kỹ năng"), ("Chuột phải vào cuốc và và phá", "Chuột phải cầm cuốc rồi phá"),
        ("Chuột phải vào rìu và chặt", "Chuột phải cầm rìu rồi chặt"), ("Chuột phải vào cúp và đào", "Chuột phải cầm cuốc chim rồi đào")]

en = yaml.safe_load(open(SRC + "/messages_en.yml", encoding="utf-8"))
vi = yaml.safe_load(open(SRC + "/messages_vi.yml", encoding="utf-8"))
stats = {"vi": 0, "dich": 0, "en": 0}


def lookup(d, path):
    for p in path:
        if not isinstance(d, dict) or p not in d:
            return None
        d = d[p]
    return d


def build(node, path):
    if isinstance(node, dict):
        return {k: build(v, path + [k]) for k, v in node.items()}
    key = ".".join(map(str, path))
    if key in T:
        stats["dich"] += 1
        return T[key]
    old = lookup(vi, path)
    if old is not None and not isinstance(old, dict):
        stats["vi"] += 1
        return old
    stats["en"] += 1
    return node


out = build(en, [])
for k, n in NAMES.items():
    out["skills"][k]["name"] = n
    out["skills"][k]["desc"] = DESC[k]
# kiểm placeholder của câu dịch khớp bản gốc
for k, v in T.items():
    a, b = set(re.findall(r"\{\w+\}", str(lookup(en, k.split("."))))), set(re.findall(r"\{\w+\}", v))
    if a != b:
        print("LỆCH placeholder", k, a, b)
txt = yaml.dump(out, allow_unicode=True, sort_keys=False, width=10000)
for a, b in REPL:
    txt = txt.replace(a, b)
with open(OUT, "w", encoding="utf-8") as f:
    f.write("# DuckyMoMo: tiếng Việt cho AuraSkills 2.4.0, dựng theo cấu trúc messages_en.yml: câu Việt có sẵn của plugin + dịch nốt phần còn\n"
            "# thiếu/tiếng Anh; tên kỹ năng thống nhất với cẩm nang (Trồng trọt, Đốn gỗ, Đào mỏ, Câu cá, Đào đất, Pha chế). Áp dụng: /skills reload.\n")
    f.write(txt)
print(stats)
