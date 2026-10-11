"""Tạo file warp Essentials cho banghoi-1 (map Trái Đất 1:2000). Tính toạ độ warp từ kinh/vĩ độ (map 1:2000), đọc độ cao mặt đất từ heightmap trong region file, tránh đứng trên nước."""
import os, sys, struct, zlib, math

# Cách dùng: python3 tools/earthmap/make-warps.py data/banghoi-1/world/dimensions/minecraft/overworld/region \\
#     modes/banghoi/server/plugins/Essentials/warps [signs.txt]
# signs.txt: lệnh setblock đặt biển tên (chạy qua RCON, xem docs/HANDOFF.md).
REGION = sys.argv[1]
OUT = sys.argv[2]
WORLD_UUID = "83762995-117c-4aba-9404-a7d9ba9b6dd8"
# Block mỗi độ = chiều rộng map / 360. Chiều rộng = 2 x border_x trong modes/banghoi/settings.yml (viền ChunkyBorder,
# trang Cài đặt). 1:2000: border_x 10752 -> 59,73; 1:1000: border_x 21504 -> 119,47. Ghi đè: biến môi trường BORDER_X.
_settings = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "modes", "banghoi", "settings.yml")
_border_x = int(os.environ.get("BORDER_X") or next(
    l.split(":", 1)[1] for l in open(_settings, encoding="utf-8") if l.startswith("border_x:")))
K = _border_x * 2 / 360

# (warp, tên thành phố/địa danh, nước, vĩ độ, kinh độ). Tên ngắn (biển tên ~15 ký tự mỗi dòng).
PLACES = [
    # Việt Nam
    ("hanoi", "Hà Nội", "Việt Nam", 21.03, 105.85), ("hcm", "TP. Hồ Chí Minh", "Việt Nam", 10.78, 106.70),
    ("haiphong", "Hải Phòng", "Việt Nam", 20.86, 106.68), ("halong", "Vịnh Hạ Long", "Việt Nam", 20.95, 107.08),
    ("sapa", "Sa Pa", "Việt Nam", 22.34, 103.84), ("hagiang", "Hà Giang", "Việt Nam", 22.82, 104.98),
    ("dienbienphu", "Điện Biên Phủ", "Việt Nam", 21.39, 103.02), ("caobang", "Cao Bằng", "Việt Nam", 22.67, 106.26),
    ("ninhbinh", "Ninh Bình", "Việt Nam", 20.25, 105.97), ("vinh", "Vinh", "Việt Nam", 18.68, 105.68),
    ("hue", "Huế", "Việt Nam", 16.46, 107.59), ("danang", "Đà Nẵng", "Việt Nam", 16.05, 108.20),
    ("hoian", "Hội An", "Việt Nam", 15.88, 108.33), ("quynhon", "Quy Nhơn", "Việt Nam", 13.78, 109.22),
    ("nhatrang", "Nha Trang", "Việt Nam", 12.24, 109.19), ("dalat", "Đà Lạt", "Việt Nam", 11.94, 108.44),
    ("buonmathuot", "Buôn Ma Thuột", "Việt Nam", 12.67, 108.04), ("pleiku", "Pleiku", "Việt Nam", 13.98, 108.00),
    ("vungtau", "Vũng Tàu", "Việt Nam", 10.35, 107.08), ("cantho", "Cần Thơ", "Việt Nam", 10.03, 105.79),
    ("camau", "Cà Mau", "Việt Nam", 9.18, 105.15), ("phuquoc", "Phú Quốc", "Việt Nam", 10.23, 103.96),
    # Đông Nam Á
    ("vientiane", "Viêng Chăn", "Lào", 17.97, 102.61), ("luangprabang", "Luông Pha Băng", "Lào", 19.89, 102.13),
    ("phnompenh", "Phnôm Pênh", "Campuchia", 11.56, 104.92), ("angkor", "Angkor Wat", "Campuchia", 13.41, 103.87),
    ("bangkok", "Bangkok", "Thái Lan", 13.76, 100.50), ("chiangmai", "Chiang Mai", "Thái Lan", 18.79, 98.98),
    ("phuket", "Phuket", "Thái Lan", 7.88, 98.39), ("yangon", "Yangon", "Myanmar", 16.87, 96.20),
    ("mandalay", "Mandalay", "Myanmar", 21.97, 96.08), ("naypyidaw", "Naypyidaw", "Myanmar", 19.76, 96.08),
    ("kualalumpur", "Kuala Lumpur", "Malaysia", 3.14, 101.69), ("singapore", "Singapore", "Singapore", 1.35, 103.82),
    ("jakarta", "Jakarta", "Indonesia", -6.21, 106.85), ("bali", "Bali", "Indonesia", -8.41, 115.19),
    ("manila", "Manila", "Philippines", 14.60, 120.98), ("cebu", "Cebu", "Philippines", 10.32, 123.89),
    ("brunei", "Bandar S. Begawan", "Brunei", 4.90, 114.94), ("dili", "Dili", "Đông Timor", -8.56, 125.57),
    # Đông Á, Nam Á, Trung Á
    ("beijing", "Bắc Kinh", "Trung Quốc", 39.90, 116.40), ("shanghai", "Thượng Hải", "Trung Quốc", 31.23, 121.47),
    ("guangzhou", "Quảng Châu", "Trung Quốc", 23.13, 113.26), ("hongkong", "Hồng Kông", "Trung Quốc", 22.32, 114.17),
    ("chengdu", "Thành Đô", "Trung Quốc", 30.57, 104.07), ("lhasa", "Lhasa", "Trung Quốc", 29.65, 91.11),
    ("taipei", "Đài Bắc", "Đài Loan", 25.03, 121.57), ("tokyo", "Tokyo", "Nhật Bản", 35.68, 139.69),
    ("osaka", "Osaka", "Nhật Bản", 34.69, 135.50), ("seoul", "Seoul", "Hàn Quốc", 37.57, 126.98),
    ("pyongyang", "Bình Nhưỡng", "Triều Tiên", 39.04, 125.76), ("ulaanbaatar", "Ulaanbaatar", "Mông Cổ", 47.89, 106.91),
    ("newdelhi", "New Delhi", "Ấn Độ", 28.61, 77.21), ("mumbai", "Mumbai", "Ấn Độ", 19.08, 72.88),
    ("kolkata", "Kolkata", "Ấn Độ", 22.57, 88.36), ("kathmandu", "Kathmandu", "Nepal", 27.72, 85.32),
    ("dhaka", "Dhaka", "Bangladesh", 23.81, 90.41), ("colombo", "Colombo", "Sri Lanka", 6.93, 79.86),
    ("islamabad", "Islamabad", "Pakistan", 33.68, 73.05), ("kabul", "Kabul", "Afghanistan", 34.56, 69.21),
    ("tashkent", "Tashkent", "Uzbekistan", 41.30, 69.24), ("astana", "Astana", "Kazakhstan", 51.17, 71.45),
    # Trung Đông
    ("tehran", "Tehran", "Iran", 35.69, 51.39), ("baghdad", "Baghdad", "Iraq", 33.31, 44.36),
    ("riyadh", "Riyadh", "Ả Rập Xê Út", 24.71, 46.68), ("mecca", "Mecca", "Ả Rập Xê Út", 21.39, 39.86),
    ("dubai", "Dubai", "UAE", 25.20, 55.27), ("jerusalem", "Jerusalem", "Trung Đông", 31.77, 35.21),
    ("istanbul", "Istanbul", "Thổ Nhĩ Kỳ", 41.01, 28.98), ("ankara", "Ankara", "Thổ Nhĩ Kỳ", 39.93, 32.86),
    # Châu Âu
    ("moscow", "Moskva", "Nga", 55.76, 37.62), ("stpetersburg", "St. Petersburg", "Nga", 59.93, 30.36),
    ("kyiv", "Kyiv", "Ukraina", 50.45, 30.52), ("warsaw", "Warszawa", "Ba Lan", 52.23, 21.01),
    ("berlin", "Berlin", "Đức", 52.52, 13.40), ("munich", "München", "Đức", 48.14, 11.58),
    ("paris", "Paris", "Pháp", 48.86, 2.35), ("london", "London", "Anh", 51.51, -0.13),
    ("dublin", "Dublin", "Ireland", 53.35, -6.26), ("madrid", "Madrid", "Tây Ban Nha", 40.42, -3.70),
    ("barcelona", "Barcelona", "Tây Ban Nha", 41.39, 2.17), ("lisbon", "Lisboa", "Bồ Đào Nha", 38.72, -9.14),
    ("rome", "Roma", "Ý", 41.90, 12.50), ("athens", "Athens", "Hy Lạp", 37.98, 23.73),
    ("amsterdam", "Amsterdam", "Hà Lan", 52.37, 4.90), ("brussels", "Bruxelles", "Bỉ", 50.85, 4.35),
    ("vienna", "Wien", "Áo", 48.21, 16.37), ("prague", "Praha", "Séc", 50.08, 14.44),
    ("budapest", "Budapest", "Hungary", 47.50, 19.04), ("bucharest", "Bucharest", "Romania", 44.43, 26.10),
    ("zurich", "Zürich", "Thụy Sĩ", 47.38, 8.54), ("stockholm", "Stockholm", "Thụy Điển", 59.33, 18.07),
    ("oslo", "Oslo", "Na Uy", 59.91, 10.75), ("copenhagen", "København", "Đan Mạch", 55.68, 12.57),
    ("helsinki", "Helsinki", "Phần Lan", 60.17, 24.94), ("reykjavik", "Reykjavík", "Iceland", 64.15, -21.94),
    # Châu Phi
    ("cairo", "Cairo", "Ai Cập", 30.04, 31.24), ("giza", "Kim tự tháp Giza", "Ai Cập", 29.98, 31.13),
    ("marrakech", "Marrakech", "Maroc", 31.63, -8.01), ("lagos", "Lagos", "Nigeria", 6.52, 3.38),
    ("addisababa", "Addis Ababa", "Ethiopia", 9.03, 38.74), ("nairobi", "Nairobi", "Kenya", -1.29, 36.82),
    ("kinshasa", "Kinshasa", "CHDC Congo", -4.44, 15.27), ("johannesburg", "Johannesburg", "Nam Phi", -26.20, 28.05),
    ("capetown", "Cape Town", "Nam Phi", -33.92, 18.42), ("madagascar", "Antananarivo", "Madagascar", -18.88, 47.51),
    # Châu Mỹ
    ("newyork", "New York", "Hoa Kỳ", 40.71, -74.01), ("washington", "Washington", "Hoa Kỳ", 38.91, -77.04),
    ("chicago", "Chicago", "Hoa Kỳ", 41.88, -87.63), ("losangeles", "Los Angeles", "Hoa Kỳ", 34.05, -118.24),
    ("sanfrancisco", "San Francisco", "Hoa Kỳ", 37.77, -122.42), ("miami", "Miami", "Hoa Kỳ", 25.76, -80.19),
    ("toronto", "Toronto", "Canada", 43.65, -79.38), ("vancouver", "Vancouver", "Canada", 49.28, -123.12),
    ("mexicocity", "TP. Mexico", "Mexico", 19.43, -99.13), ("havana", "La Habana", "Cuba", 23.11, -82.37),
    ("bogota", "Bogotá", "Colombia", 4.71, -74.07), ("lima", "Lima", "Peru", -12.05, -77.04),
    ("rio", "Rio de Janeiro", "Brazil", -22.91, -43.17), ("saopaulo", "São Paulo", "Brazil", -23.55, -46.63),
    ("buenosaires", "Buenos Aires", "Argentina", -34.60, -58.38), ("santiago", "Santiago", "Chile", -33.45, -70.67),
    # Châu Đại Dương
    ("sydney", "Sydney", "Úc", -33.87, 151.21), ("melbourne", "Melbourne", "Úc", -37.81, 144.96),
    ("perth", "Perth", "Úc", -31.95, 115.86), ("auckland", "Auckland", "New Zealand", -36.85, 174.76),
    # Địa danh thiên nhiên
    ("everest", "Đỉnh Everest", "Nepal/Tây Tạng", 27.99, 86.93), ("fuji", "Núi Phú Sĩ", "Nhật Bản", 35.36, 138.73),
    ("uluru", "Uluru", "Úc", -25.34, 131.04), ("grandcanyon", "Grand Canyon", "Hoa Kỳ", 36.11, -112.11),
    ("kilimanjaro", "Kilimanjaro", "Tanzania", -3.07, 37.35), ("sahara", "Sa mạc Sahara", "Algeria", 23.42, 5.0),
    ("amazon", "Rừng Amazon", "Brazil", -3.47, -62.37), ("niagara", "Thác Niagara", "Hoa Kỳ/Canada", 43.08, -79.07),
    ("andes", "Machu Picchu", "Peru", -13.16, -72.55), ("baikal", "Hồ Baikal", "Nga", 53.5, 108.0),
]

_cache = {}


def heightmaps(cx, cz):
    """(MOTION_BLOCKING_NO_LEAVES, OCEAN_FLOOR) của chunk, mỗi cái 256 giá trị y khối trên cùng."""
    if (cx, cz) in _cache:
        return _cache[(cx, cz)]
    res = None
    path = os.path.join(REGION, f"r.{cx >> 5}.{cz >> 5}.mca")
    if os.path.exists(path):
        with open(path, "rb") as f:
            i = (cx & 31) + (cz & 31) * 32
            f.seek(i * 4)
            off = struct.unpack(">I", b"\0" + f.read(3))[0]
            if off:
                f.seek(off * 4096)
                ln, comp = struct.unpack(">IB", f.read(5))
                raw = zlib.decompress(f.read(ln - 1))
                res = tuple(read_hm(raw, n) for n in ("MOTION_BLOCKING_NO_LEAVES", "OCEAN_FLOOR"))
    _cache[(cx, cz)] = res
    return res


def read_hm(raw, name):
    key = b"\x0c" + struct.pack(">H", len(name)) + name.encode()
    p = raw.index(key) + len(key)
    n = struct.unpack(">i", raw[p:p + 4])[0]
    longs = struct.unpack(f">{n}q", raw[p + 4:p + 4 + 8 * n])
    vals = []
    for l in longs:
        u = l & ((1 << 64) - 1)
        for k in range(7):
            vals.append((u >> (9 * k)) & 0x1FF)
    return [v - 65 for v in vals[:256]]  # giá trị lưu = y + 1 - minY(-64)


def column(x, z):
    hm = heightmaps(x >> 4, z >> 4)
    if hm is None:
        return None
    i = (x & 15) + (z & 15) * 16
    return hm[0][i], hm[1][i]


def find_land(x0, z0, maxr=20):
    for r in range(0, maxr + 1):
        best = None
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) != r:
                    continue
                c = column(x0 + dx, z0 + dz)
                if c and c[0] == c[1]:  # không có nước phía trên
                    d = dx * dx + dz * dz
                    if best is None or d < best[0]:
                        best = (d, x0 + dx, z0 + dz, c[0])
        if best:
            return best[1:]
    return None


SIGNS = sys.argv[3] if len(sys.argv) > 3 else None


# (dx, dz, rotation của biển để mặt chữ quay về điểm warp, yaw của người chơi để nhìn vào biển)
DIRECTIONS = [(0, 2, 8, 0.0), (0, -2, 0, 180.0), (2, 0, 4, -90.0), (-2, 0, 12, 90.0),
              (0, 3, 8, 0.0), (0, -3, 0, 180.0), (3, 0, 4, -90.0), (-3, 0, 12, 90.0)]


def sign_cmd(warp, city, country, x, z):
    """Lệnh đặt biển tên cạnh điểm warp, ở ô đất liền đầu tiên trong DIRECTIONS. Trả về (x, z, lệnh, yaw) hoặc None."""
    lines = ", ".join([f'{{text:"{city}",bold:true,color:"dark_green"}}', f'"{country}"', '""', f'{{text:"/warp {warp}",color:"dark_gray"}}'])
    for dx, dz, rot, yaw in DIRECTIONS:
        sx, sz = x + dx, z + dz
        c = column(sx, sz)
        if c is not None and c[0] == c[1]:
            return sx, sz, f"setblock {sx} {c[0] + 1} {sz} minecraft:oak_sign[rotation={rot}]{{front_text:{{messages:[{lines}]}}}} replace", yaw
    return None


os.makedirs(OUT, exist_ok=True)
signs = []
for warp, city, country, lat, lon in PLACES:
    name = f"{city} ({country})"
    x0, z0 = round(lon * K), round(-lat * K)
    found = find_land(x0, z0)
    if not found:
        print(f"!! {warp}: không tìm thấy đất liền gần {x0},{z0}")
        continue
    x, z, y = found
    moved = math.hypot(x - x0, z - z0)
    print(f"{warp:14s} {name:36s} {x:6d} {y + 1:4d} {z:6d}" + (f"  (dời {moved:.0f} block khỏi biển)" if moved else ""))
    s = sign_cmd(warp, city, country, x, z)
    if s:
        signs.append(s[:3])
    else:
        print(f"   {warp}: không đặt được biển tên (xung quanh là nước)")
    with open(os.path.join(OUT, f"{warp}.yml"), "w", encoding="utf-8") as f:
        f.write(f"# {name}: vĩ độ {lat}, kinh độ {lon}\n")
        f.write(f"world: {WORLD_UUID}\nworld-name: world\nx: {x + 0.5}\ny: {float(y + 1)}\nz: {z + 0.5}\n"
                f"yaw: {s[3] if s else 0.0}\npitch: 0.0\nname: {warp}\n")

if SIGNS:
    # mỗi dòng: chunkX chunkZ <tab> lệnh setblock (để nạp chunk bằng forceload trước khi đặt)
    with open(SIGNS, "w", encoding="utf-8") as f:
        for sx, sz, cmd in signs:
            f.write(f"{sx} {sz}\t{cmd}\n")
