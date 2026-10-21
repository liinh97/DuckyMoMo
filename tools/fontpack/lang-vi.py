"""Sinh tools/fontpack/src/lang-vi.json: các dòng chữ trong khung mô tả đồ mà MÁY NGƯỜI CHƠI tự vẽ theo ngôn ngữ game
(tên phù phép "Protection II", "When on Legs: +2 Armor", hiệu ứng thuốc, độ bền…), lấy bản tiếng Việt chính thức của Minecraft.
build-font-pack.sh ghi file này đè lên en_us / en_gb trong gói tài nguyên -> người để game tiếng Anh cũng thấy tiếng Việt.
Nguồn: vi_vn.json MyPet lưu sẵn (data/banghoi-1/plugins/MyPet/cache/vanilla-lang/<bản>/). Chạy lại khi nâng bản Minecraft:
  python3 tools/fontpack/lang-vi.py [đường_dẫn_vi_vn.json]"""
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
src = sys.argv[1] if len(sys.argv) > 1 else sorted(glob.glob(os.path.join(ROOT, "data/banghoi-1/plugins/MyPet/cache/vanilla-lang/*/vi_vn.json")))[-1]
vi = json.load(open(src, encoding="utf-8"))
PREFIX = ("enchantment.", "attribute.", "item.modifiers.", "effect.minecraft.", "effect.duration.", "effect.none", "potion.")
SINGLE = ("item.durability", "item.unbreakable", "item.dyed", "item.color", "item.disabled", "item.canBreak", "item.canPlace",
          "item.nbt_tags", "item.components")
out = {k: v for k, v in sorted(vi.items()) if k.startswith(PREFIX) or k in SINGLE}
dst = os.path.join(ROOT, "tools/fontpack/src/lang-vi.json")
json.dump(out, open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(out)} dòng -> {dst} (từ {src})")
