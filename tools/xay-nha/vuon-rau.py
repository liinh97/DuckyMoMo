# Vườn rau tường đá đổ nát (lúa mì / cà rốt / khoai tây, đường đất vòng quanh) dựng bằng lệnh vanilla qua RCON.
# Chạy trong container panel: python3 vuon-rau.py <server> <x> <y> <z> [rộng] [dài]
#   x z = góc tây bắc, y = mặt đất + 1; rộng theo trục x (mặc định 13), dài theo trục z (mặc định 18).
# 31/10 đặt ở LetGoMine: banghoi-1 13858 63 -2607 (bên trái biệt thự nhìn từ vịnh).
import random, sys
sys.path.insert(0, '/app')
import app

SERVER = sys.argv[1] if len(sys.argv) > 1 else 'sangtao-1'
X0, Y0, Z0 = (int(v) for v in sys.argv[2:5]) if len(sys.argv) > 4 else (10060, -60, 10000)
W = int(sys.argv[5]) if len(sys.argv) > 5 else 13
L = int(sys.argv[6]) if len(sys.argv) > 6 else 18
random.seed(5)
cmds = []


def P(x, y, z):
    return f'{X0 + x} {Y0 + y} {Z0 + z}'


def fill(x1, y1, z1, x2, y2, z2, b, mode=''):
    cmds.append(f'minecraft:fill {P(x1, y1, z1)} {P(x2, y2, z2)} {b} {mode}'.strip())


def sb(x, y, z, b):
    cmds.append(f'minecraft:setblock {P(x, y, z)} {b}')


STONE = ['minecraft:stone_bricks'] * 4 + ['minecraft:mossy_stone_bricks'] * 2 + ['minecraft:cracked_stone_bricks', 'minecraft:andesite',
                                                                                 'minecraft:mossy_cobblestone']
TOP = ['minecraft:stone_brick_slab', 'minecraft:mossy_stone_brick_slab', 'minecraft:stone_brick_wall', None, None]

# 0. san nền: dọn phía trên, đất bên dưới, cỏ mặt
fill(0, 0, 0, W - 1, 14, L - 1, 'minecraft:air')
fill(0, -4, 0, W - 1, -2, L - 1, 'minecraft:dirt')
fill(0, -1, 0, W - 1, -1, L - 1, 'minecraft:grass_block')

# 1. đường đất vòng ngoài, lốm đốm đất thô
for x in range(W):
    for z in range(L):
        if x in (0, W - 1) or z in (0, L - 1):
            sb(x, -1, z, random.choice(['minecraft:dirt_path'] * 3 + ['minecraft:coarse_dirt', 'minecraft:rooted_dirt']))

# 2. tường đá đổ nát lx 1..W-2, lz 1..L-2; cổng tây và cổng đông
x1, x2, z1, z2 = 1, W - 2, 1, L - 2
mid = (z1 + z2) // 2
gates = {(x1, mid), (x1, mid + 1), (x2, z2 - 3), (x2, z2 - 4)}
H = {}
for x in range(x1, x2 + 1):
    for z in range(z1, z2 + 1):
        if not (x in (x1, x2) or z in (z1, z2)) or (x, z) in gates:
            continue
        corner = x in (x1, x2) and z in (z1, z2)
        h = 3 if corner else random.choice([1, 1, 2, 2, 2, 3])
        H[(x, z)] = h
        for y in range(h):
            sb(x, y, z, random.choice(STONE))
        t = random.choice(TOP)
        if t and not corner:
            sb(x, h, z, t)
        if corner:
            sb(x, 3, z, 'minecraft:oak_fence')
            sb(x, 4, z, 'minecraft:lantern')
# cổng: cửa rào gỗ, đèn hai bên
sb(x1, 0, mid, 'minecraft:oak_fence_gate[facing=east]'); sb(x1, 0, mid + 1, 'minecraft:oak_fence_gate[facing=east]')
sb(x2, 0, z2 - 3, 'minecraft:oak_fence_gate[facing=west]'); sb(x2, 0, z2 - 4, 'minecraft:oak_fence_gate[facing=west]')

# 3. vách ngăn thấp giữa hai luống (lz = mid), lối đi ở giữa
for x in range(x1 + 1, x2):
    if x == (x1 + x2) // 2:
        continue
    sb(x, 0, mid, random.choice(STONE))
    if random.random() < 0.4:
        sb(x, 1, mid, random.choice(['minecraft:stone_brick_slab', 'minecraft:mossy_stone_brick_slab']))

# 4. luống: đất cày ẩm + cây chín; rãnh nước giữa mỗi luống (tưới đủ 4 ô hai bên)
cx = (x1 + x2) // 2
beds = [(z1 + 1, mid - 1, {'all': 'minecraft:wheat[age=7]'}),
        (mid + 1, z2 - 1, {'w': 'minecraft:carrots[age=7]', 'e': 'minecraft:potatoes[age=7]'})]
for za, zb, crop in beds:
    fill(x1 + 1, -1, za, x2 - 1, -1, zb, 'minecraft:farmland[moisture=7]')
    fill(cx, -1, za, cx, -1, zb, 'minecraft:water')
    for x in range(x1 + 1, x2):
        if x == cx:
            continue
        c = crop.get('all') or (crop['w'] if x < cx else crop['e'])
        fill(x, 0, za, x, 0, zb, c)
    # lá súng trên rãnh nước cho đỡ trống
    for z in range(za, zb + 1, 2):
        sb(cx, 0, z, 'minecraft:lily_pad')

# lối đi giữa vách ngăn: đá lát thay cho nước ở hàng mid
sb(cx, -1, mid, 'minecraft:mossy_cobblestone')

# 5. bù nhìn trong luống lúa
sx, sz = x1 + 2, (z1 + 1 + mid - 1) // 2
sb(sx, -1, sz, 'minecraft:coarse_dirt')
sb(sx, 0, sz, 'minecraft:oak_fence'); sb(sx, 1, sz, 'minecraft:hay_block'); sb(sx, 2, sz, 'minecraft:carved_pumpkin[facing=west]')

# 6. bụi azalea và chậu hoa trên đỉnh tường (đặt ngay trên khối đá cao nhất, thay phiến/tường nhỏ nếu có)
for x, z in ((x1, z1 + 3), (x2, z1 + 5), (x1, z2 - 2), (x2, z1 + 2), (x1 + 4, z1), (x2 - 2, z2)):
    sb(x, H[(x, z)], z, random.choice(['minecraft:azalea_leaves[persistent=true]', 'minecraft:flowering_azalea_leaves[persistent=true]']))
for x, z in ((x1 + 2, z1), (x2 - 3, z2), (x1, mid + 4), (x2, z1 + 7)):
    sb(x, H[(x, z)], z, random.choice(['minecraft:potted_red_tulip', 'minecraft:potted_poppy', 'minecraft:potted_azalea_bush']))
for z in (mid - 1, mid + 2):
    sb(x1, 1, z, 'minecraft:oak_fence'); sb(x1, 2, z, 'minecraft:lantern')
sb(x2, 1, z2 - 2, 'minecraft:oak_fence'); sb(x2, 2, z2 - 2, 'minecraft:lantern')
sb(x2, 1, z2 - 5, 'minecraft:oak_fence'); sb(x2, 2, z2 - 5, 'minecraft:lantern')

print(len(cmds), 'lệnh')
errs = 0
for i in range(0, len(cmds), 150):
    for c, out in app.rcon(SERVER, cmds[i:i + 150]):
        o = app.COLOR_RE.sub('', out or '')
        if any(k in o for k in ('Unknown', 'Incorrect', 'Expected', 'Invalid', 'not loaded', 'Too many', 'error', 'Could not')):
            errs += 1
            if errs <= 8:
                print('LỖI:', c[:120], '->', o[:160])
print('xong, lỗi:', errs)
