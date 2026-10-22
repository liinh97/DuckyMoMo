"""So giá trị trang Cài đặt (modes/<chế độ>/settings.yml) với file cấu hình ĐANG CHẠY trong data/<server>/ (22/10).
Lệch = trang web đang hiện số cũ; bấm Lưu (hoặc "áp lại tất cả") sẽ ghi đè server về số cũ.
Chạy trong container panel:  docker compose exec -T panel python /repo/tools/cai-dat-lech.py [--sua-web]
  --sua-web: chép giá trị đang chạy vào settings.yml (tin server là đúng); không đụng file server."""
import sys
sys.path.insert(0, "/app")
import app  # noqa: E402

fix = "--sua-web" in sys.argv


def get_path(doc, dotted):
    node = doc
    for k in dotted.split("."):
        k = int(k) if k.isdigit() else k
        if isinstance(k, int):
            node = node[k]
        elif hasattr(node, "get") and k in node:
            node = node[k]
        else:
            raise KeyError(dotted)
    return node


def norm(s, v):
    v = app.plain(v)
    if s["type"] in ("int", "number"):
        try:
            return float(v)
        except (TypeError, ValueError):
            return v
    if s["type"] == "select" and isinstance(v, str) and v.lower() in ("true", "false"):
        return v.lower() == "true"
    return v


tong = 0
for mode_id, mode in app.load_modes().items():
    schema, values = mode["schema"], mode["values"]
    server = schema.get("server")
    cache, lech = {}, {}
    for _, s in app.all_settings(schema):
        if s["type"] == "info" or not s.get("file") or s.get("template") or s["key"] not in values:
            continue
        srv = (s.get("servers") or [server])[0]
        path = app.DATA_DIR / srv / s["file"]
        if not path.exists() or path.suffix not in (".yml", ".yaml"):
            continue
        if path not in cache:
            try:
                cache[path] = app.yaml_rt().load(path.read_text(encoding="utf-8"))
            except Exception as e:  # noqa: BLE001
                cache[path] = e
        doc = cache[path]
        if isinstance(doc, Exception):
            continue
        try:
            live = get_path(doc, s["path"])
        except (KeyError, IndexError, TypeError):
            continue
        a, b = norm(s, values[s["key"]]), norm(s, live)
        if a != b:
            lech[s["key"]] = app.plain(live)
            tong += 1
            print(f"[{mode_id}] {s['key']} ({srv}/{s['file']} › {s['path']})\n    web:    {str(a)[:200]}\n    server: {str(b)[:200]}")
    if fix and lech:
        for k, v in lech.items():
            values[k] = v
        with mode["values_file"].open("w", encoding="utf-8") as f:
            app.yaml_rt().dump(values, f)
        print(f"[{mode_id}] đã chép {len(lech)} giá trị đang chạy vào {mode['values_file']}")
print("tổng lệch:", tong)
