"""Thêm tên liền cho Essentials vào worth.yml (26/10).

EssentialsX tra giá /sell, /worth theo tên vật liệu BỎ dấu _ (LAPIS_LAZULI -> worth.lapislazuli), còn bảng giá của mình
(tools/shop/bang-gia.py, trang Cài đặt, DuckyMoMoCore: DecorShop, SellLimit, Workshop) ghi và đọc tên có dấu _ (lapis_lazuli).
Thiếu tên liền thì món có dấu _ KHÔNG bán được. Script này giữ nguyên các dòng có dấu _, xoá khối tên liền cũ rồi sinh lại
ở cuối mục worth: (tên liền = giá của tên có dấu _). Chạy lại bao nhiêu lần cũng được.
  python3 tools/shop/worth-essentials.py [đường dẫn worth.yml]   (mặc định data/banghoi-1/plugins/Essentials/worth.yml)
Sau đó: rcon banghoi-1 "essentials reload". Trang Cài đặt tự làm việc này khi lưu giá (panel/app.py, mirror_worth).
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MARK = "  # --- Tên liền cho Essentials (tự sinh bằng tools/shop/worth-essentials.py, đừng sửa tay: sửa dòng có dấu _ ở trên) ---"


def mirror(text):
    head, sep, body = text.partition("\nworth:\n")
    if not sep:
        raise SystemExit("không thấy mục worth:")
    body = body.split("\n" + MARK + "\n", 1)[0].rstrip("\n") + "\n"
    if body.startswith(MARK + "\n"):
        body = ""
    keys = {}
    for line in body.splitlines():
        m = re.match(r"  ([a-z0-9_]+):\s*([0-9.]+)\s*$", line)
        if m:
            keys[m.group(1)] = m.group(2)
    extra = []
    for k, v in keys.items():
        if "_" in k:
            flat = k.replace("_", "")
            if flat in keys and keys[flat] != v:
                print(f"bỏ qua {k}: đã có dòng {flat} giá khác ({keys[flat]} / {v})")
                continue
            if flat not in keys:
                extra.append(f"  {flat}: {v}")
    return head + sep + body + MARK + "\n" + "\n".join(extra) + "\n", len(extra)


if __name__ == "__main__":
    p = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "data/banghoi-1/plugins/Essentials/worth.yml"
    out, n = mirror(p.read_text(encoding="utf-8"))
    p.write_text(out, encoding="utf-8")
    print(f"{p}: {n} tên liền")
