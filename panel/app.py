"""Trang Cài đặt của DuckyMoMo Network.

Đọc modes/<id>/settings.schema.yml (định nghĩa) + settings.yml (giá trị). Khi bấm Lưu:
ghi giá trị vào file cấu hình plugin trong data/<server>/, lưu settings.yml, rồi gửi lệnh nạp lại
qua RCON để áp dụng ngay. Mật khẩu RCON đọc từ data/<server>/.rcon-cli.env (image itzg tự sinh).
"""

import base64
import hmac
import json
import os
import re
import socket
import struct
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from ruamel.yaml import YAML
from ruamel.yaml.scalarstring import DoubleQuotedScalarString, SingleQuotedScalarString

MODES_DIR = Path(os.environ.get("MODES_DIR", "/repo/modes"))
DATA_DIR = Path(os.environ.get("DATA_DIR", "/data"))
PANEL_USER = os.environ.get("PANEL_USER", "admin")
PANEL_PASSWORD = os.environ["PANEL_PASSWORD"]
DOZZLE_URL = os.environ.get("DOZZLE_URL", "")
RCON_PORT = 25575
INDEX_HTML = (Path(__file__).parent / "index.html").read_text(encoding="utf-8")
TIMES_RE = re.compile(r"^$|^([01]\d|2[0-3]):[0-5]\d(,([01]\d|2[0-3]):[0-5]\d)*$")


def yaml_rt():
    """YAML giữ nguyên comment, dấu nháy, thứ tự (để ghi lại file cấu hình plugin)."""
    y = YAML()
    y.preserve_quotes = True
    y.width = 4096
    y.indent(mapping=2, sequence=4, offset=2)
    return y


# ---------------------------------------------------------------- chế độ + cài đặt

def load_modes():
    modes = {}
    for schema_file in sorted(MODES_DIR.glob("*/settings.schema.yml")):
        mode_id = schema_file.parent.name
        schema = YAML(typ="safe").load(schema_file.read_text(encoding="utf-8"))
        values_file = schema_file.parent / "settings.yml"
        values = yaml_rt().load(values_file.read_text(encoding="utf-8")) if values_file.exists() else None
        modes[mode_id] = {"schema": schema, "values": values or {}, "values_file": values_file}
    return modes


def all_settings(schema):
    for group in schema.get("groups", []):
        for s in group.get("settings", []):
            yield group, s


def plain(v):
    """Chuyển giá trị ruamel về kiểu Python thường (để so sánh / trả JSON)."""
    if isinstance(v, (list, tuple)):
        return [plain(x) for x in v]
    if isinstance(v, bool) or v is None:
        return v
    if isinstance(v, int):
        return int(v)
    if isinstance(v, float):
        return float(v)
    return str(v)


def validate(s, v):
    t = s["type"]
    label = s["label"]
    if t == "times":
        v = str(v or "").replace(" ", "")
        if not TIMES_RE.match(v):
            raise ValueError(f"{label}: sai định dạng giờ (ví dụ 20:00,21:30)")
        return v
    if t in ("int", "number"):
        try:
            v = int(v) if t == "int" else float(v)
        except (TypeError, ValueError):
            raise ValueError(f"{label}: phải là số")
        if "min" in s and v < s["min"]:
            raise ValueError(f"{label}: tối thiểu {s['min']}")
        if "max" in s and v > s["max"]:
            raise ValueError(f"{label}: tối đa {s['max']}")
        return v
    if t == "select":
        allowed = [o["value"] for o in s["options"]]
        if v not in allowed:
            raise ValueError(f"{label}: lựa chọn không hợp lệ")
        return v
    if t == "text":
        return str(v or "")
    raise ValueError(f"{label}: kiểu {t} không ghi được")


def set_path(doc, dotted, value):
    keys = dotted.split(".")
    node = doc
    for k in keys[:-1]:
        if k not in node or node[k] is None:
            node[k] = {}
        node = node[k]
    last = keys[-1]
    old = node.get(last) if hasattr(node, "get") else None
    # Giữ kiểu ghi của file gốc: Towny/SiegeWar ghi số dạng chuỗi trong nháy đơn ('20.0')
    if isinstance(value, list):
        new = list(value)
    elif isinstance(old, SingleQuotedScalarString):
        new = SingleQuotedScalarString(str(value))
    elif isinstance(old, DoubleQuotedScalarString):
        new = DoubleQuotedScalarString(str(value))
    elif isinstance(old, str):
        new = str(value)
    else:
        new = value
    node[last] = new


# ---------------------------------------------------------------- RCON

def rcon_password(server):
    env = DATA_DIR / server / ".rcon-cli.env"
    for line in env.read_text().splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1].strip()
    raise RuntimeError("không tìm thấy mật khẩu RCON")


def rcon(server, commands):
    """Gửi lệnh qua RCON. Trả về [(lệnh, kết quả)]. Lỗi kết nối thì ném ra (server đang tắt)."""
    def send(sock, req_id, kind, body):
        data = body.encode("utf-8") + b"\x00\x00"
        sock.sendall(struct.pack("<iii", len(data) + 8, req_id, kind) + data)

    def recv(sock):
        head = b""
        while len(head) < 4:
            chunk = sock.recv(4 - len(head))
            if not chunk:
                raise ConnectionError("RCON đóng kết nối")
            head += chunk
        (length,) = struct.unpack("<i", head)
        body = b""
        while len(body) < length:
            chunk = sock.recv(length - len(body))
            if not chunk:
                raise ConnectionError("RCON đóng kết nối")
            body += chunk
        req_id, _kind = struct.unpack("<ii", body[:8])
        return req_id, body[8:-2].decode("utf-8", "replace")

    out = []
    with socket.create_connection((server, RCON_PORT), timeout=10) as sock:
        send(sock, 1, 3, rcon_password(server))
        if recv(sock)[0] == -1:
            raise RuntimeError("sai mật khẩu RCON")
        for i, cmd in enumerate(commands, start=2):
            send(sock, i, 2, cmd)
            _, text = recv(sock)
            out.append((cmd, re.sub(r"§.", "", text).strip()))
    return out


# ---------------------------------------------------------------- lưu + áp dụng

def save_and_apply(mode_id, incoming, apply_all=False):
    modes = load_modes()
    if mode_id not in modes:
        raise ValueError("không có chế độ này")
    mode = modes[mode_id]
    schema, values = mode["schema"], mode["values"]
    server = schema["server"]

    changed = []  # (group, setting, value)
    for group, s in all_settings(schema):
        if s["type"] == "info":
            continue
        key = s["key"]
        if key in incoming:
            v = validate(s, incoming[key])
        elif apply_all and key in values:
            v = validate(s, plain(values[key]))
        else:
            continue
        if apply_all or plain(values.get(key)) != v:
            changed.append((group, s, v))

    if not changed:
        return {"ok": True, "message": "Không có gì thay đổi.", "results": []}

    # 1) Ghi vào file cấu hình plugin (gom theo file)
    by_file = {}
    for _, s, v in changed:
        if s.get("file"):
            by_file.setdefault(s["file"], []).append((s["path"], v))
    for rel, items in by_file.items():
        path = DATA_DIR / server / rel
        if not path.exists():
            raise FileNotFoundError(f"chưa có {rel}: server cần chạy ít nhất một lần để plugin tạo file")
        y = yaml_rt()
        doc = y.load(path.read_text(encoding="utf-8"))
        for dotted, v in items:
            set_path(doc, dotted, v)
        tmp = path.with_suffix(path.suffix + ".tmp")
        with tmp.open("w", encoding="utf-8") as f:
            y.dump(doc, f)
        tmp.replace(path)

    # 2) Lưu settings.yml (giữ comment đầu file)
    for _, s, v in changed:
        values[s["key"]] = v
    with mode["values_file"].open("w", encoding="utf-8") as f:
        yaml_rt().dump(values, f)

    # 3) Áp dụng qua RCON: lệnh riêng của từng mục, rồi lệnh nạp lại của từng nhóm (mỗi lệnh 1 lần)
    # Lệnh dùng được {value}, {double} (giá trị x2) và {tên_mục_khác} (giá trị hiện tại của mục đó)
    current = {k: plain(v) for k, v in values.items()}
    commands = []
    for group, s, v in changed:
        for c in s.get("commands", []):
            cmd = c.format(**current, value=v, double=int(v) * 2 if s["type"] in ("int", "number") else v)
            if cmd not in commands:
                commands.append(cmd)
    for group in schema.get("groups", []):
        if any(g is group for g, _, _ in changed):
            for c in group.get("apply", []):
                if c not in commands:
                    commands.append(c)

    stored_only = [s["label"] for _, s, _ in changed if not s.get("file") and not s.get("commands")]
    try:
        results = rcon(server, commands) if commands else []
        msg = "Đã lưu và áp dụng."
        ok = True
    except Exception as e:  # server đang tắt / đang khởi động
        results = []
        msg = f"Đã lưu, nhưng chưa áp dụng được ({e}). Server {server} có thể đang tắt; bật lên sẽ dùng giá trị mới (riêng viền thế giới phải bấm Áp dụng lại tất cả)."
        ok = False
    if stored_only:
        msg += " Chỉ ghi lại (dùng khi mở mùa mới): " + ", ".join(stored_only) + "."
    return {"ok": ok, "message": msg, "results": [{"command": c, "output": o} for c, o in results]}


def modes_json():
    out = []
    for mode_id, m in load_modes().items():
        out.append({"id": mode_id, "schema": m["schema"],
                    "values": {k: plain(v) for k, v in m["values"].items()}})
    return out


# ---------------------------------------------------------------- HTTP

class Handler(BaseHTTPRequestHandler):
    server_version = "DuckyMoMoPanel"

    def log_message(self, fmt, *args):
        sys.stdout.write("%s %s\n" % (self.address_string(), fmt % args))

    def authorized(self):
        header = self.headers.get("Authorization", "")
        if header.startswith("Basic "):
            try:
                user, _, pw = base64.b64decode(header[6:]).decode("utf-8").partition(":")
            except Exception:
                return False
            return hmac.compare_digest(user, PANEL_USER) and hmac.compare_digest(pw, PANEL_PASSWORD)
        return False

    def send_json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def guard(self):
        if self.authorized():
            return True
        self.send_response(401)
        self.send_header("WWW-Authenticate", 'Basic realm="DuckyMoMo", charset="UTF-8"')
        self.end_headers()
        return False

    def do_GET(self):
        if not self.guard():
            return
        if self.path == "/":
            body = INDEX_HTML.replace("__DOZZLE_URL__", DOZZLE_URL).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif self.path == "/api/modes":
            self.send_json(200, modes_json())
        else:
            self.send_json(404, {"error": "không có"})

    def do_POST(self):
        if not self.guard():
            return
        m = re.fullmatch(r"/api/modes/([a-z0-9_-]+)/(save|apply-all)", self.path)
        if not m:
            self.send_json(404, {"error": "không có"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length) or b"{}")
            result = save_and_apply(m.group(1), payload.get("values", {}), apply_all=m.group(2) == "apply-all")
            self.send_json(200, result)
        except ValueError as e:
            self.send_json(400, {"ok": False, "message": str(e)})
        except Exception as e:
            self.send_json(500, {"ok": False, "message": f"Lỗi: {e}"})


if __name__ == "__main__":
    print("Trang Cài đặt chạy ở cổng 8080", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
