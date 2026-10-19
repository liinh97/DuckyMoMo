"""Tên + mô tả tiếng Việt cho 105 phù phép CrazyEnchantments (dòng trên món đồ, menu /ce), lấy từ modes/_network/ce-vi.yml.
CE lưu phù phép trên đồ bằng PDC (khoá "enchantments") nên đổi tên hiển thị không làm mất phù phép (đồ cũ còn dòng chữ cũ tới khi CE vẽ lại).
Chỉ sửa Name, Info.Name, Info.Description; giữ Enabled… (trang Cài đặt sửa). Chạy trong image panel (ruamel):
  docker run --rm -v "$PWD":/w -w /w --entrypoint python3 duckymomo-panel tools/vi/crazyenchantments.py <Enchantments.yml> modes/_network/ce-vi.yml"""
import re
import sys
import textwrap

from ruamel.yaml import YAML

y = YAML()
y.preserve_quotes = True
y.width = 10000
f, vi_file = sys.argv[1], sys.argv[2]
doc = y.load(open(f, encoding="utf-8").read())
vi = YAML(typ="safe").load(open(vi_file, encoding="utf-8").read())

TAIL = {
    "&6Found in:": "&6Có ở:",
    "&7 - &9&l&nRare": "&7 - &9&l&nHiếm", "&7 - &4&l&nLegendary": "&7 - &4&l&nHuyền thoại",
    "&7 - &a&l&nCommon": "&7 - &a&l&nThường", "&7 - &5&l&nMythical": "&7 - &5&l&nThần thoại",
}
ITEMS = {"Armor": "Giáp", "Swords": "Kiếm", "Sword": "Kiếm", "Bows": "Cung", "Bow": "Cung", "Axes": "Rìu", "Axe": "Rìu",
         "Boots": "Giày", "Pickaxes": "Cuốc chim", "Helmets": "Mũ", "Hoe": "Cuốc", "Tools": "Công cụ",
         "Armor, Weapons, Tools, Fishing Rods, and Shears": "Giáp, vũ khí, công cụ, cần câu và kéo"}
n = 0
for key, e in doc["Enchantments"].items():
    if key not in vi:
        continue
    name, desc = vi[key][0], vi[key][1]
    m = re.match(r"^((?:&[0-9a-fk-or])*)", str(e["Name"]))
    e["Name"] = type(e["Name"])(m.group(1) + name)
    info = e["Info"]
    t = re.match(r"^((?:&[0-9a-fk-or])*)(.*?)(\s*&7\(.*)?$", str(info["Name"]))
    info["Name"] = type(info["Name"])(t.group(1) + name + (t.group(3) or ""))
    old = list(info["Description"])
    cut = old.index("") if "" in old else len(old)
    tail = []
    for line in old[cut:]:
        s = str(line)
        if s in TAIL:
            s = TAIL[s]
        elif s.startswith("&6Enchantable Items: &a"):
            s = "&6Dùng cho: &a" + ITEMS.get(s[len("&6Enchantable Items: &a"):], s[len("&6Enchantable Items: &a"):])
        tail.append(s)
    info["Description"] = ["&7" + w for w in textwrap.wrap(desc, 30)] + tail
    n += 1
with open(f, "w", encoding="utf-8") as fh:
    y.dump(doc, fh)
print(f"{f}: dịch {n} phù phép")
