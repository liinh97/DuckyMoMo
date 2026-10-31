# Cây cổ thụ tán rộng (kiểu "Big Tree": thân gỗ sồi to, rễ toả, tán lá bông từng cụm) dựng bằng lệnh vanilla qua RCON.
# Chạy trong container panel: python3 cay-lon.py <server> <x> <y> <z> [seed]   (x y z = gốc cây, y = mặt đất + 1)
import math, random, sys
sys.path.insert(0, '/app')
import app

SERVER = sys.argv[1] if len(sys.argv) > 1 else 'sangtao-1'
X0, Y0, Z0 = (int(v) for v in sys.argv[2:5]) if len(sys.argv) > 4 else (10040, -60, 10000)
random.seed(int(sys.argv[5]) if len(sys.argv) > 5 else 11)
LOG = 'minecraft:oak_log'
WOOD = 'minecraft:oak_wood'
LEAF = ['minecraft:oak_leaves[persistent=true]'] * 5 + ['minecraft:azalea_leaves[persistent=true]'] * 2 + \
       ['minecraft:jungle_leaves[persistent=true]']
blocks = {}            # (x,y,z) -> block; gỗ ghi đè lá, lá không ghi đè gỗ


def put(x, y, z, b, wood=False):
    k = (x, y, z)
    if wood or k not in blocks:
        blocks[k] = b


def line(a, b, block, r=0.0):
    """Cành từ a tới b (toạ độ thực), dày r."""
    n = int(max(abs(b[i] - a[i]) for i in range(3)) * 2) + 1
    for i in range(n + 1):
        t = i / n
        p = [a[j] + (b[j] - a[j]) * t for j in range(3)]
        for dx in (-1, 0, 1) if r >= 1 else (0,):
            for dz in (-1, 0, 1) if r >= 1 else (0,):
                if abs(dx) + abs(dz) <= 1:
                    put(round(p[0]) + dx, round(p[1]), round(p[2]) + dz, block, True)


def blob(cx, cy, cz, r):
    """Cụm lá hơi dẹt, mép lởm chởm."""
    R = int(r) + 1
    for x in range(-R, R + 1):
        for y in range(-R, R + 1):
            for z in range(-R, R + 1):
                d = math.sqrt(x * x + (y * 1.35) ** 2 + z * z)
                if d <= r - 0.5 or (d <= r + 0.4 and random.random() < 0.45):
                    put(round(cx) + x, round(cy) + y, round(cz) + z, random.choice(LEAF))


# 1. thân 2x2 cao 7, gốc loe thành 4x4 bằng gỗ
for y in range(0, 8):
    for dx, dz in ((0, 0), (1, 0), (0, 1), (1, 1)):
        put(dx, y, dz, LOG, True)
for dx, dz in ((-1, 0), (-1, 1), (2, 0), (2, 1), (0, -1), (1, -1), (0, 2), (1, 2)):
    for y in range(0, random.randint(1, 3)):
        put(dx, y, dz, WOOD, True)

# 2. rễ bò trên mặt đất (gỗ tước + bậc thang gỗ sồi)
for ang in range(0, 360, 45):
    a = math.radians(ang + random.randint(-15, 15))
    L = random.randint(3, 5)
    for i in range(2, L + 1):
        x, z = round(0.5 + math.cos(a) * i), round(0.5 + math.sin(a) * i)
        put(x, 0, z, 'minecraft:stripped_oak_wood' if i < L else 'minecraft:oak_slab', True)

# 3. cành chính toả ra, cuối mỗi cành một cụm lá to
tips = []
for k in range(7):
    a = math.radians(k * (360 / 7) + random.randint(-20, 20))
    L = random.uniform(4.5, 6.5)
    y0 = random.randint(5, 7)
    end = (0.5 + math.cos(a) * L, y0 + random.uniform(3, 6), 0.5 + math.sin(a) * L)
    line((0.5, y0, 0.5), end, LOG, r=0.0)
    tips.append(end)
line((0.5, 7, 0.5), (0.5, 12, 0.5), LOG)
tips.append((0.5, 13, 0.5))

for (x, y, z) in tips:
    blob(x, y, z, random.uniform(3.2, 4.2))
    for _ in range(2):                                     # cụm phụ cho tán bông lồi lõm
        blob(x + random.uniform(-3, 3), y + random.uniform(-1.5, 1.5), z + random.uniform(-3, 3), random.uniform(2.0, 2.8))
blob(0.5, 11, 0.5, 4.5)                                    # lấp lõi tán

# 4. lá rủ + đèn treo dưới tán
under = sorted({(x, z) for (x, y, z), b in blocks.items() if 'leaves' in b})
for x, z in under:
    ys = [y for (bx, y, bz) in blocks if bx == x and bz == z]
    low = min(ys)
    if low > 4 and random.random() < 0.12:
        for y in range(low - 1, low - 1 - random.randint(1, 3), -1):
            if (x, y, z) not in blocks:
                blocks[(x, y, z)] = random.choice(LEAF)
for x, z in ((3, 0), (-2, 2), (1, -3)):
    ys = [y for (bx, y, bz), b in blocks.items() if bx == x and bz == z and 'leaves' in b]
    if ys and (x, min(ys) - 1, z) not in blocks:
        blocks[(x, min(ys) - 1, z)] = 'minecraft:lantern[hanging=true]'

cmds = [f'minecraft:setblock {X0 + x} {Y0 + y} {Z0 + z} {b}' for (x, y, z), b in sorted(blocks.items(), key=lambda kv: kv[0][1])]
print(len(cmds), 'khối')
errs = 0
for i in range(0, len(cmds), 150):
    for c, out in app.rcon(SERVER, cmds[i:i + 150]):
        o = app.COLOR_RE.sub('', out or '')
        if any(k in o for k in ('Unknown', 'Incorrect', 'Expected', 'Invalid', 'not loaded', 'Too many', 'error')):
            errs += 1
            if errs <= 8:
                print('LỖI:', c[:120], '->', o[:160])
print('xong, lỗi:', errs)
