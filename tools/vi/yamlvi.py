"""Công cụ Việt hoá file YAML của plugin, giữ nguyên chú thích/thứ tự (ruamel; chạy trong image panel).
  xuất câu cần dịch:  python3 yamlvi.py dump  <file.yml>                 -> in JSON [đường dẫn, câu] mỗi dòng
  ghi bản dịch:       python3 yamlvi.py apply <file.yml> <bandich.json>   -> sửa tại chỗ
  thay câu khớp nguyên văn (menu lặp nhiều câu giống nhau):
                      python3 yamlvi.py replace <file.yml> <cau.json> [regex khoá hiển thị] [regex khoá bỏ qua]
                      cau.json: {"câu gốc": "câu Việt"}; chỉ thay ở khoá khớp regex (mặc định: name/title/lore/display-name/description).
bandich.json: {"đường.dẫn": "câu Việt", "đường.dẫn.0": "dòng 1 của danh sách", ...}; đường dẫn nối bằng dấu chấm, số = chỉ số danh sách.
Lúc ghi: kiểm câu dịch giữ đủ biến ({x}, %x%, <x>, {0}), sai thì báo và bỏ qua câu đó.
  --no-angle: không coi <x> là biến (plugin dùng %x% làm biến, <x> chỉ là chữ trong cú pháp lệnh, vd Duels)."""
import json
import re
import sys

from ruamel.yaml import YAML

y = YAML()
y.preserve_quotes = True
y.width = 10000

VARS = re.compile(r"\{[^}\s]*\}|%[A-Za-z0-9_.\-]+%|%[sd]|<(?:player|amount|price|item|time|name|target|arena|kit|[a-z_]+)>")


def wordy(s):
    s = re.sub(r"<[^>]*>|\{[^}]*\}|%[^%\s]*%|[&§][0-9a-fk-orx#]", "", s)
    return re.search(r"[A-Za-z]{3,}", s) is not None


def walk(node, path, out):
    if hasattr(node, "items"):
        for k, v in node.items():
            walk(v, path + [str(k)], out)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            walk(v, path + [str(i)], out)
    elif isinstance(node, str) and wordy(node):
        out.append([".".join(path), str(node)])


def resolve(node, parts):
    """Tìm (cha, khoá) cho đường dẫn; khoá có thể chứa dấu chấm (thử ghép dài nhất trước). None nếu không thấy."""
    for n in range(len(parts), 0, -1):
        key = ".".join(parts[:n])
        if isinstance(node, list):
            if n != 1 or not key.isdigit() or int(key) >= len(node):
                continue
            k = int(key)
        elif hasattr(node, "items"):
            k = key if key in node else (int(key) if key.isdigit() and int(key) in node else None)
            if k is None:
                continue
        else:
            return None
        if n == len(parts):
            return node, k
        r = resolve(node[k], parts[n:])
        if r is not None:
            return r
    return None


def get_parent(doc, parts):
    node = doc
    for p in parts[:-1]:
        node = node[int(p)] if isinstance(node, list) else node[p if p in node else (int(p) if p.isdigit() and int(p) in node else p)]
    return node


def replace_exact(doc, f):
    tr = json.load(open(sys.argv[3], encoding="utf-8"))
    ok = re.compile(sys.argv[4] if len(sys.argv) > 4 and sys.argv[4] else r"(^|\.)(name|title|display-name|description)$|\.lore\.\d+$")
    skip = re.compile(sys.argv[5]) if len(sys.argv) > 5 and sys.argv[5] else None
    n, bad = 0, set()

    def walk2(node, path):
        nonlocal n
        items = node.items() if hasattr(node, "items") else enumerate(node) if isinstance(node, list) else []
        for k, v in list(items):
            p = ".".join(path + [str(k)])
            if isinstance(v, str) and v in tr and ok.search(p) and not (skip and skip.search(p)):
                a, b = VARS.findall(v), VARS.findall(tr[v])
                if sorted(a) != sorted(b):
                    bad.add(v)
                    continue
                node[k] = type(v)(tr[v])
                n += 1
            elif not isinstance(v, str):
                walk2(v, path + [str(k)])
    walk2(doc, [])
    with open(f, "w", encoding="utf-8") as fh:
        y.dump(doc, fh)
    print(f"{f}: thay {n} chỗ; lệch biến (bỏ qua): {sorted(bad)}")


def main():
    cmd, f = sys.argv[1], sys.argv[2]
    doc = y.load(open(f, encoding="utf-8").read())
    if cmd == "replace":
        replace_exact(doc, f)
        return
    if cmd == "dump":
        out = []
        walk(doc, [], out)
        for row in out:
            print(json.dumps(row, ensure_ascii=False))
        return
    tr = json.load(open(sys.argv[3], encoding="utf-8"))
    done, bad, miss = 0, [], []
    for path, vi in tr.items():
        found = resolve(doc, path.split("."))
        if found is None:
            miss.append(path)
            continue
        parent, last = found
        old = parent[last]
        va, vb = VARS.findall(str(old)), VARS.findall(vi)
        if "--no-angle" in sys.argv:
            va, vb = [x for x in va if not x.startswith("<")], [x for x in vb if not x.startswith("<")]
        if sorted(va) != sorted(vb):
            bad.append(path)
            continue
        parent[last] = type(old)(vi) if isinstance(old, str) else vi
        done += 1
    with open(f, "w", encoding="utf-8") as fh:
        y.dump(doc, fh)
    print(f"{f}: ghi {done} câu; lệch biến (bỏ qua): {bad}; không thấy khoá: {miss}")


main()
