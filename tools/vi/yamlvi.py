"""Công cụ Việt hoá file YAML của plugin, giữ nguyên chú thích/thứ tự (ruamel; chạy trong image panel).
  xuất câu cần dịch:  python3 yamlvi.py dump  <file.yml>                 -> in JSON [đường dẫn, câu] mỗi dòng
  ghi bản dịch:       python3 yamlvi.py apply <file.yml> <bandich.json>   -> sửa tại chỗ
bandich.json: {"đường.dẫn": "câu Việt", "đường.dẫn.0": "dòng 1 của danh sách", ...}; đường dẫn nối bằng dấu chấm, số = chỉ số danh sách.
Lúc ghi: kiểm câu dịch giữ đủ biến ({x}, %x%, <x>, {0}), sai thì báo và bỏ qua câu đó."""
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


def get_parent(doc, parts):
    node = doc
    for p in parts[:-1]:
        node = node[int(p)] if isinstance(node, list) else node[p if p in node else (int(p) if p.isdigit() and int(p) in node else p)]
    return node


def main():
    cmd, f = sys.argv[1], sys.argv[2]
    doc = y.load(open(f, encoding="utf-8").read())
    if cmd == "dump":
        out = []
        walk(doc, [], out)
        for row in out:
            print(json.dumps(row, ensure_ascii=False))
        return
    tr = json.load(open(sys.argv[3], encoding="utf-8"))
    done, bad, miss = 0, [], []
    for path, vi in tr.items():
        parts = path.split(".")
        try:
            parent = get_parent(doc, parts)
            last = parts[-1]
            if isinstance(parent, list):
                last = int(last)
            elif last not in parent and last.isdigit() and int(last) in parent:
                last = int(last)
            old = parent[last]
        except (KeyError, IndexError, ValueError, TypeError):
            miss.append(path)
            continue
        if sorted(VARS.findall(str(old))) != sorted(VARS.findall(vi)):
            bad.append(path)
            continue
        parent[last] = type(old)(vi) if isinstance(old, str) else vi
        done += 1
    with open(f, "w", encoding="utf-8") as fh:
        y.dump(doc, fh)
    print(f"{f}: ghi {done} câu; lệch biến (bỏ qua): {bad}; không thấy khoá: {miss}")


main()
