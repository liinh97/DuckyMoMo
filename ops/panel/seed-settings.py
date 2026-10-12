# Điền giá trị đang dùng (đọc từ file plugin trong /data) cho các mục chưa có trong settings.yml của một trang.
import sys
from ruamel.yaml import YAML
safe=YAML(typ="safe")
mode, server = sys.argv[1], sys.argv[2]
def get(doc, dotted):
    node=doc
    for k in dotted.split("."):
        node=node[int(k) if k.isdigit() and not isinstance(node, dict) else (int(k) if k.isdigit() and int(k) in node else k)]
    return node
schema=safe.load(open(f"/repo/modes/{mode}/settings.schema.yml",encoding="utf-8"))
vf=f"/repo/modes/{mode}/settings.yml"
y=YAML(); y.preserve_quotes=True; y.width=4096; y.indent(mapping=2, sequence=4, offset=2)
values=y.load(open(vf,encoding="utf-8")); cache={}; added=0; bad=[]
for g in schema["groups"]:
    for s in g["settings"]:
        if s["key"] in values or not s.get("file"): continue
        srv=(s.get("servers") or [server])[0]; path=f"/data/{srv}/{s['file']}"
        if path not in cache: cache[path]=safe.load(open(path,encoding="utf-8"))
        try: v=get(cache[path], s["path"])
        except Exception as e: bad.append(s["key"]+" (thiếu "+s["path"]+")"); continue
        t=s["type"]
        if t=="int": v=int(v)
        elif t=="number": v=float(v)
        elif t in ("list","lines"): v=[str(x) for x in v]
        elif t=="text": v=str(v)
        elif t=="select" and v not in [o["value"] for o in s["options"]]: bad.append(s["key"]+"="+repr(v))
        values[s["key"]]=v; added+=1
with open(vf,"w",encoding="utf-8") as f: y.dump(values,f)
print(mode,"thêm",added,"| lỗi:",bad)
