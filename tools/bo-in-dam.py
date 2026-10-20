#!/usr/bin/env python3
"""Bỏ kiểu chữ IN ĐẬM (&l, §l, <bold>, <b>) trong chữ hiện cho người chơi.

Vì sao: gói tài nguyên thay chữ bằng font Be Vietnam Pro (TTF, mịn). Minecraft vẽ chữ đậm bằng cách vẽ 2 lần lệch nhau,
với font mịn thành chữ có bóng ma, nhìn như 3D và khó đọc (20/10). Nhấn mạnh bằng màu thay cho in đậm.

Cách dùng (từ thư mục gốc repo):
    python3 tools/bo-in-dam.py            # xem trước: file nào, bao nhiêu chỗ
    python3 tools/bo-in-dam.py --ghi      # sửa thật
    python3 tools/bo-in-dam.py --ghi data/banghoi-1/plugins/DecentHolograms   # thêm thư mục/file khác (vd dữ liệu chỉ có ở data/)

Mặc định quét: lobby/, modes/, proxy/plugins/, network-core/core-paper/src, tools/mypet. Bỏ qua .html/.js/.py/.css/.md
(HTML dùng <b> thật) và TownyMapLayer.java (popup HTML của bản đồ web). Java: chỉ sửa trong chuỗi "...".
"""
import re
import sys
from pathlib import Path

ROOTS = ["lobby", "modes", "proxy/plugins", "network-core/core-paper/src", "tools/mypet", "tools/vi", "tools"]
EXTS_EXTRA = {".cmds"}
EXTS = {".yml", ".yaml", ".json", ".txt", ".properties", ".java", ".conf", ".toml", ".lang", ".csv"}
SKIP_NAMES = {"TownyMapLayer.java"}
LEGACY = re.compile(r"[&§][lL]")
MINI = re.compile(r"</?(?:bold|b)>")
JAVA_STR = re.compile(r'"(?:[^"\\\n]|\\.)*"')


def fix_text(s, java):
    if java:
        # chuỗi đúng "&l" là mã dùng trong logic (vd Guide.java đo độ rộng chữ đậm): giữ nguyên
        return JAVA_STR.sub(lambda m: m.group(0) if m.group(0) in ('"&l"', '"§l"') else LEGACY.sub("", m.group(0)), s)
    return MINI.sub("", LEGACY.sub("", s))


def files(paths):
    for p in paths:
        p = Path(p)
        if p.is_file():
            yield p
            continue
        for f in p.rglob("*"):
            if f.is_file() and (f.suffix.lower() in EXTS or f.suffix in EXTS_EXTRA) and f.name not in SKIP_NAMES and "/build/" not in str(f):
                yield f


def main():
    write = "--ghi" in sys.argv
    extra = [a for a in sys.argv[1:] if not a.startswith("--")]
    total = 0
    for f in files(ROOTS + extra):
        try:
            s = f.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        java = f.suffix == ".java"
        n = fix_text(s, java)
        if n != s:
            cnt = len(LEGACY.findall(s)) + (0 if java else len(MINI.findall(s))) - len(LEGACY.findall(n)) - (0 if java else len(MINI.findall(n)))
            total += cnt
            print(f"{cnt:5d}  {f}")
            if write:
                f.write_text(n, encoding="utf-8")
    print(f"Tổng: {total} chỗ {'đã sửa' if write else '(chưa sửa, thêm --ghi để sửa)'}")


if __name__ == "__main__":
    main()
