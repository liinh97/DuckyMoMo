"""Skyblock (31/10): nâng cấp đảo, bảng điều khiển /is, túi quà + hướng dẫn người mới. Sửa data/skyblock-1, chạy lúc server tắt."""
import glob, json, os, re

R = "/home/developer/works/DuckyMoMo/"
P = R + "data/skyblock-1/plugins/"
DB = P + "BentoBox/database/"

# ---- Nâng cấp đảo (đảo gốc bán kính 50 = 100x100): không bán bằng tiền thật, mọi thứ mua bằng xu trong game + cấp đảo
for x in ("cow", "diamond", "donor", "spawner"):
    for f in (f"UpgradeData/BSkyBlock_example_{x}.json", f"UpgradeTier/BSkyBlock_example_{x}_t1.json"):
        if os.path.exists(DB + f):
            os.remove(DB + f)
UP = {
    "range1": ("Mở Rộng Đảo I", ["Từ 100x100 lên 200x200", "+20 khối mỗi chiều mỗi cấp"], 1, 4,
               [{"class": "IslandLevelPriceDB", "parameters": {"levelNeededEquation": "5+5*[level]"}}, {"class": "MoneyPriceDB", "parameters": {"amountEquation": "500+500*[level]"}}],
               [{"class": "RangeRewardDB", "parameters": {"rangeUpgradeEquation": "10"}}]),
    "range2": ("Mở Rộng Đảo II", ["Từ 200x200 lên 300x300", "+20 khối mỗi chiều mỗi cấp"], 2, 4,
               [{"class": "IslandLevelPriceDB", "parameters": {"levelNeededEquation": "50+25*[level]"}}, {"class": "MoneyPriceDB", "parameters": {"amountEquation": "5000+2500*[level]"}}],
               [{"class": "RangeRewardDB", "parameters": {"rangeUpgradeEquation": "10"}}]),
    "hopper": ("Thêm Phễu", ["+10 phễu tối đa mỗi cấp"], 3, 4,
               [{"class": "MoneyPriceDB", "parameters": {"amountEquation": "2000+2000*[level]"}}],
               [{"class": "LimitsRewardDB", "parameters": {"limitType": "BLOCK", "target": "HOPPER", "amountEquation": "10"}}]),
    "cropgrowth": ("Cây Lớn Nhanh", ["Cây trồng trên đảo lớn nhanh hơn"], 4, 4,
                   [{"class": "IslandLevelPriceDB", "parameters": {"levelNeededEquation": "10+10*[level]"}}, {"class": "MoneyPriceDB", "parameters": {"amountEquation": "1500+1500*[level]"}}],
                   [{"class": "CropGrowthRewardDB", "parameters": {"growthBonusEquation": "0.5"}}]),
}
for k, (name, desc, order, end, prices, rewards) in UP.items():
    p = DB + f"UpgradeData/BSkyBlock_example_{k}.json"
    d = json.load(open(p, encoding="utf-8"))
    d.update(name=name, description=desc, order=order)
    json.dump(d, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    p = DB + f"UpgradeTier/BSkyBlock_example_{k}_t1.json"
    d = json.load(open(p, encoding="utf-8"))
    d.update(name=name, endLevel=end, prices=prices, rewards=rewards)
    json.dump(d, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("ok nâng cấp")

# ---- Bảng điều khiển: /is mở bảng này, /is go về đảo
BTN = [
    (10, "&a&lVề đảo", "GRASS_BLOCK", "Dịch chuyển về đảo của bạn", "[label] go"),
    (11, "&e&lThử thách", "WRITABLE_BOOK", "Làm thử thách nhận thưởng", "[label] challenges"),
    (12, "&6&lChợ", "EMERALD", "Mua bán đồ lấy xu", "shop"),
    (13, "&b&lNâng cấp đảo", "ANVIL", "Mở rộng đảo, thêm phễu", "[label] upgrade"),
    (14, "&d&lCấp đảo", "EXPERIENCE_BOTTLE", "Tính cấp đảo hiện tại", "[label] level"),
    (15, "&c&lBảng xếp hạng", "GOLD_INGOT", "Top đảo cấp cao nhất", "[label] top"),
    (16, "&f&lHướng dẫn", "BOOK", "Bắt đầu chơi thế nào", "huongdan"),
    (19, "&9&lNhóm", "PLAYER_HEAD", "Mời bạn vào đảo", "[label] team"),
    (20, "&3&lMáy tạo đá", "COBBLESTONE", "Xem, mở máy tạo đá", "[label] generator"),
    (21, "&2&lĐiểm dịch chuyển", "OAK_SIGN", "Đi thăm đảo người khác", "[label] warps"),
    (22, "&7&lCài đặt đảo", "COMPARATOR", "Ai được làm gì trên đảo", "[label] settings"),
]
o = ["panel-list:", "  default:", "    defaultPanel: true", "    panelName: '&0&lĐảo Của Bạn'", "    permission: 'default'", "    buttons:"]
for slot, name, mat, desc, cmd in BTN:
    o += [f"      {slot}:", f"        name: '{name}'", f"        material: {mat}", f"        icon: minecraft:{mat.lower()}", "        description: |-", f"          &7{desc}", f"        command: '{cmd}'"]
open(P + "BentoBox/addons/ControlPanel/controlPanelTemplate.yml", "w", encoding="utf-8").write("\n".join(o) + "\n")

p = P + "BentoBox/addons/BSkyBlock/config.yml"
s = open(p, encoding="utf-8").read()
s, n = re.subn(r"^(\s*default-action:) go\s*$", r"\1 cp", s, count=1, flags=re.M)
print("đổi default-action:", n)
open(p, "w", encoding="utf-8", newline="").write(s)

# ---- Essentials: tin chào, túi quà, hướng dẫn
p = P + "Essentials/config.yml"
s = open(p, encoding="utf-8").read()
s = re.sub(r"^(\s*announce-format:) .*$", r"\1 '&a✦ Chào mừng {DISPLAYNAME}&a tới Skyblock!'", s, count=1, flags=re.M)
s, n = re.subn(r"^(\s*)kit: tools\s*$", r"\1kit: daomoi", s, count=1, flags=re.M)
print("kit newbies:", n)
open(p, "w", encoding="utf-8", newline="").write(s)

p = P + "Essentials/kits.yml"
s = open(p, encoding="utf-8").read()
if "daomoi:" not in s:
    s = re.sub(r"^kits:\n", "kits:\n  daomoi:\n    delay: -1\n    items:\n    - bread 8\n    - torch 16\n    - bone_meal 4\n", s, count=1, flags=re.M)
open(p, "w", encoding="utf-8", newline="").write(s)

open(P + "Essentials/motd.txt", "w", encoding="utf-8").write(
    "&6✦ &fChào {PLAYER}&f! Đây là &aDuckyMoMo Skyblock&f.\n"
    "&7Gõ &e/is &7mở bảng điều khiển đảo · &e/huongdan &7xem cách chơi.\n")
open(P + "Essentials/info.txt", "w", encoding="utf-8").write("""&6&l✦ CÁCH CHƠI SKYBLOCK
&fBạn bắt đầu trên một hòn đảo nhỏ giữa trời, có cây, ít đất và rương đồ.
&c⚠ Đừng rơi khỏi đảo, rơi là chết về đảo.

&e1. Mở rương &fở trên đảo, lấy đồ khởi đầu.
&e2. Trồng cây &fđể có gỗ, luôn giữ lại cây non.
&e3. Làm máy tạo đá: &fđổ nước cạnh dung nham, đá cuội sẽ tự sinh ra mãi.
&e4. Làm thử thách &e/is challenges &fđể có đất, cát, đồ quý và mở cấp mới.
&e5. Bán đồ ở &e/shop&f lấy xu, mua thứ còn thiếu.
&e6. Xây thêm &fđể lên cấp đảo &e/is level&f, đua top &e/is top&f.
&e7. Mở rộng đảo &fở &e/is upgrade&f: 100x100 lên 300x300.

&6&l✦ LỆNH HAY DÙNG
&e/is &7bảng điều khiển · &e/is go &7về đảo
&e/is team invite <tên> &7mời bạn vào đảo (tối đa 8 người)
&e/is sethome &7đặt điểm về · &e/is settings &7chỉnh quyền trên đảo
&e/is warps &7thăm đảo khác · &e/bal &7xem xu · &e/pay <tên> <xu> &7chuyển xu
&e/is generator &7máy tạo đá xịn hơn
&fĐặt bảng ghi &e[Welcome] &fdòng đầu để người khác ghé thăm đảo bạn.
""")

p = R + "data/skyblock-1/commands.yml"
s = open(p, encoding="utf-8").read()
if "huongdan:" not in s:
    s = re.sub(r"^aliases:\n", "aliases:\n  huongdan:\n  - essentials:info $1-\n  hd:\n  - essentials:info $1-\n", s, count=1, flags=re.M)
    open(p, "w", encoding="utf-8", newline="").write(s)
print("ok hướng dẫn")
