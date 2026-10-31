# Cách chạy: docker cp tools/xay-nha/biet-thu-dia-trung-hai.py duckymomo-panel-1:/tmp/v.py && docker exec -w /app duckymomo-panel-1 python3 /tmp/v.py
# (dọn và dựng lại toàn bộ ở sangtao-1, toạ độ X0/Y0/Z0; đổi random.seed để ra cách bày cây khác)
# Biệt thự Địa Trung Hải, bản 2 (31/10): thiết kế riêng theo phong cách (không chép bản trả phí). Nét chính chủ server muốn giống:
# hai cửa vòm gạch men xanh ở tầng trệt khối chính + khối lá dày phía trên, viền xanh ngọc dày dưới lan can răng cưa, cánh phụ dạng
# bậc thang (sân thượng thấp trước, cao sau) có giàn gỗ phủ lá, cầu thang đá hai bên trên chân đế đá, rất nhiều lá rủ.
# Mặt trước nhìn về phía bắc (z giảm). Chạy trong container panel (dùng app.rcon).
import random, sys
sys.path.insert(0, '/app')
import app

# Tham số: python3 v.py [server x0 y0 z0]  (mặc định bản thử ở thế giới phẳng sangtao-1). y0 = khối KHÍ đầu tiên trên mặt đất.
# 31/10 đặt bản thật: banghoi-1 13874 63 -2575 W (thị trấn LetGoMine của Itrap, mặt tiền nhìn ra vịnh phía tây).
SERVER = sys.argv[1] if len(sys.argv) > 4 else 'sangtao-1'
X0, Y0, Z0 = (int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])) if len(sys.argv) > 4 else (10000, -60, 10000)
# Hướng mặt tiền (tham số thứ 5): N (mặc định), W, S, E. 31/10 LetGoMine: W (nhìn ra vịnh phía tây).
FACE = sys.argv[5].upper() if len(sys.argv) > 5 else 'N'
TURNS = {'N': 0, 'W': 1, 'S': 2, 'E': 3}[FACE]   # số lần xoay 90° ngược chiều kim đồng hồ (nhìn từ trên)
_ROT = {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}

def _rot_xz(x, z):
    for _ in range(TURNS):
        x, z = z, -x
    return x, z

import re as _re
def _rot_state(b):
    m = _re.match(r'^([^\[]+)\[(.*)\]$', b)
    if not m or TURNS == 0:
        return b
    props = []
    for kv in m.group(2).split(','):
        k, v = kv.split('=')
        for _ in range(TURNS):
            if k == 'facing' and v in _ROT:
                v = _ROT[v]
            elif k == 'axis' and v in ('x', 'z'):
                v = 'z' if v == 'x' else 'x'
            elif k in _ROT:
                k = _ROT[k]
        props.append(f'{k}={v}')
    return f"{m.group(1)}[{','.join(props)}]"
random.seed(11)
cmds = []

def P(x, y, z):
    x, z = _rot_xz(x, z)
    return f"{X0 + x} {Y0 + y} {Z0 + z}"

def fill(x1, y1, z1, x2, y2, z2, b, mode=''):
    cmds.append(f"minecraft:fill {P(x1, y1, z1)} {P(x2, y2, z2)} {_rot_state(b)} {mode}".strip())

def sb(x, y, z, b):
    cmds.append(f"minecraft:setblock {P(x, y, z)} {_rot_state(b)}")

LEAF = ['minecraft:azalea_leaves[persistent=true]', 'minecraft:flowering_azalea_leaves[persistent=true]',
        'minecraft:oak_leaves[persistent=true]', 'minecraft:azalea_leaves[persistent=true]', 'minecraft:jungle_leaves[persistent=true]']
WALL, TRIM, TEAL, TEAL2 = 'minecraft:white_concrete', 'minecraft:quartz_block', 'minecraft:cyan_concrete', 'minecraft:prismarine_bricks'
TILE = ['minecraft:terracotta', 'minecraft:brown_terracotta', 'minecraft:terracotta', 'minecraft:orange_terracotta']
BASE = ['minecraft:stone_bricks', 'minecraft:cobblestone', 'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:andesite']
POTS = ['minecraft:potted_red_tulip', 'minecraft:potted_azalea_bush', 'minecraft:potted_flowering_azalea_bush', 'minecraft:potted_fern', 'minecraft:decorated_pot']

def blob(cx, cy, cz, rx, ry, rz, dens=0.85):
    """Khối lá hình bầu dục (bụi cây / tán lá phủ)."""
    for x in range(cx - rx, cx + rx + 1):
        for y in range(cy - ry, cy + ry + 1):
            for z in range(cz - rz, cz + rz + 1):
                d = ((x - cx) / (rx + .5)) ** 2 + ((y - cy) / (ry + .5)) ** 2 + ((z - cz) / (rz + .5)) ** 2
                if d <= 1 and random.random() < dens:
                    sb(x, y, z, random.choice(LEAF))

def vines(x, ytop, z, face, n):
    for y in range(ytop, ytop - n, -1):
        sb(x, y, z, f'minecraft:vine[{face}=true]')

# 0. dọn khu, nền cỏ
fill(-6, 0, -7, 27, 26, 15, 'minecraft:air')
fill(-6, -1, -7, 27, -1, 15, 'minecraft:grass_block')

# 1. KHỐI CHÍNH: x 0..12, z 0..10, cao 0..15 (3 tầng), viền xanh dày y13..14, mái y15, răng cưa y16
fill(0, 0, 0, 12, 14, 10, WALL)
fill(1, 0, 1, 11, 14, 9, 'minecraft:air')
fill(0, 0, 0, 12, 0, 10, 'minecraft:stone_bricks', 'outline')
fill(0, 5, 0, 12, 5, 10, TEAL, 'outline'); fill(1, 5, 1, 11, 5, 9, 'minecraft:oak_planks')
fill(1, 10, 1, 11, 10, 9, 'minecraft:oak_planks')
fill(0, 13, 0, 12, 14, 10, TEAL, 'outline')                        # dải xanh ngọc dày dưới mái
fill(0, 12, -1, 12, 12, -1, 'minecraft:quartz_stairs[facing=south,half=top]')   # gờ đỡ dải xanh mặt trước
fill(0, 15, 0, 12, 15, 10, TRIM)
for x in range(0, 13):                                               # răng cưa trắng xen ô trống
    for z in (0, 10):
        if x % 2 == 0:
            sb(x, 16, z, WALL)
for z in range(1, 10):
    for x in (0, 12):
        if z % 2 == 0:
            sb(x, 16, z, WALL)
fill(0, 1, 0, 0, 12, 0, 'minecraft:quartz_pillar'); fill(12, 1, 0, 12, 12, 0, 'minecraft:quartz_pillar')
fill(1, -1, 1, 11, -1, 9, 'minecraft:polished_diorite')

# 1a. hai cửa vòm gạch men (x 1..4 và 8..11, cao 0..4) + cửa xanh thật ở giữa đáy
def tile_door(x1, x2):
    for x in range(x1, x2 + 1):
        for y in range(0, 4):
            sb(x, y, 0, 'minecraft:light_blue_glazed_terracotta[facing=north]' if (x + y) % 2 == 0 else 'minecraft:blue_glazed_terracotta[facing=east]')
    sb(x1, 4, 0, 'minecraft:quartz_stairs[facing=east,half=top]'); sb(x2, 4, 0, 'minecraft:quartz_stairs[facing=west,half=top]')
    fill(x1 + 1, 4, 0, x2 - 1, 4, 0, 'minecraft:blue_glazed_terracotta[facing=north]')
    fill(x1 - 1, 0, -1, x1 - 1, 4, -1, 'minecraft:quartz_pillar'); fill(x2 + 1, 0, -1, x2 + 1, 4, -1, 'minecraft:quartz_pillar')
    mid = (x1 + x2) // 2
    sb(mid, 0, 0, 'minecraft:warped_door[half=lower,facing=north]'); sb(mid, 1, 0, 'minecraft:warped_door[half=upper,facing=north]')
    sb(mid, -1, -1, 'minecraft:smooth_quartz')
tile_door(2, 4)
tile_door(8, 10)
sb(6, 3, -1, 'minecraft:lantern'); sb(6, 0, -1, 'minecraft:decorated_pot')

# 1b. cửa sổ tầng 2 và 3 (3 ô, cửa chớp xanh), bậu gỗ có chậu
def window(x1, x2, y1, y2, z, out=-1):
    fill(x1, y1, z, x2, y2, z, 'minecraft:glass_pane')
    for x in (x1 - 1, x2 + 1):
        for y in range(y1, y2 + 1):
            sb(x, y, z + out, 'minecraft:warped_trapdoor[open=true,facing=north]')
    fill(x1, y1 - 1, z + out, x2, y1 - 1, z + out, 'minecraft:spruce_slab[type=top]')
    for x in range(x1, x2 + 1):
        if random.random() < 0.6:
            sb(x, y1, z + out, random.choice(POTS[:4]))
for y1, y2 in ((7, 8), (11, 12)):
    window(2, 4, y1, y2, 0); window(8, 10, y1, y2, 0)
for y1, y2 in ((2, 3), (7, 8), (11, 12)):                            # hông tây + mặt sau
    fill(0, y1, 3, 0, y2, 4, 'minecraft:glass_pane'); fill(0, y1, 7, 0, y2, 8, 'minecraft:glass_pane')
    fill(3, y1, 10, 4, y2, 10, 'minecraft:glass_pane'); fill(8, y1, 10, 9, y2, 10, 'minecraft:glass_pane')

# 1c. khối lá dày trên hai cửa vòm và giữa tầng 1–2
blob(3, 6, -1, 3, 1, 1, 0.9); blob(9, 6, -1, 3, 1, 1, 0.9); blob(6, 6, -1, 1, 1, 1, 0.7)
for x in (1, 2, 5, 6, 7, 10, 11):
    if random.random() < 0.6:
        vines(x, 5, -2, 'south', random.randint(1, 3))

# 2. CÁNH PHỤ BẬC THANG: thấp trước x 13..21 z 1..10 cao 0..5 (sân thượng y5), cao sau z 6..10 tới y10 (sân thượng y10)
fill(13, 0, 1, 21, 4, 10, WALL); fill(14, 0, 2, 20, 4, 9, 'minecraft:air')
fill(13, 0, 1, 21, 0, 10, 'minecraft:stone_bricks', 'outline')
fill(13, 5, 1, 21, 5, 10, TEAL)                                      # mái tầng thấp = sân thượng thấp (viền xanh)
fill(14, 5, 2, 20, 5, 9, 'minecraft:oak_planks')
fill(13, 6, 6, 21, 9, 10, WALL); fill(14, 6, 7, 20, 9, 9, 'minecraft:air')
fill(13, 10, 6, 21, 10, 10, TRIM)
fill(12, 1, 2, 12, 4, 9, 'minecraft:air')                            # thông với khối chính
fill(12, 6, 7, 12, 9, 9, 'minecraft:air')
for x in range(14, 21):                                              # lát đất nung hai sân thượng
    for z in range(2, 6):
        sb(x, 5, z, random.choice(TILE))
    for z in range(7, 10):
        sb(x, 10, z, random.choice(TILE))
for x in range(13, 22):                                              # răng cưa / lan can
    if x % 2 == 1:
        sb(x, 6, 1, WALL)
    if x % 2 == 0:
        sb(x, 11, 6, WALL); sb(x, 11, 10, WALL)
for z in range(1, 6):
    if z % 2 == 1:
        sb(21, 6, z, WALL)
fill(13, 6, 1, 13, 6, 5, 'minecraft:air')
# cửa vòm xanh ngọc của cánh + cửa sổ
sb(17, 0, 1, 'minecraft:warped_door[half=lower,facing=north]'); sb(17, 1, 1, 'minecraft:warped_door[half=upper,facing=north]')
sb(16, 2, 1, 'minecraft:prismarine_brick_stairs[facing=east,half=top]'); sb(18, 2, 1, 'minecraft:prismarine_brick_stairs[facing=west,half=top]')
sb(17, 2, 1, TEAL2); fill(16, 0, 1, 16, 1, 1, TEAL2); fill(18, 0, 1, 18, 1, 1, TEAL2)
window(14, 14, 2, 3, 1); window(20, 20, 2, 3, 1)
window(15, 16, 7, 8, 6); window(18, 19, 7, 8, 6)
# bồn hoa ban công trước sân thượng thấp
fill(14, 6, 0, 20, 6, 0, 'minecraft:spruce_trapdoor[open=true,facing=north]')
for x in range(14, 21):
    sb(x, 6, 1, random.choice(LEAF) if random.random() < 0.7 else 'minecraft:potted_red_tulip')

# 3. giàn gỗ phủ lá: sân thượng thấp, sân thượng cao, mái khối chính
def pergola(x1, z1, x2, z2, yb, dens=0.6):
    for x, z in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        fill(x, yb, z, x, yb + 2, z, 'minecraft:stripped_oak_log')
    for z in (z1, z2):
        fill(x1, yb + 3, z, x2, yb + 3, z, 'minecraft:stripped_spruce_log[axis=x]')
    for x in range(x1, x2 + 1, 2):
        fill(x, yb + 3, z1, x, yb + 3, z2, 'minecraft:spruce_fence')
    for x in range(x1 - 1, x2 + 2):
        for z in range(z1 - 1, z2 + 2):
            if random.random() < dens:
                sb(x, yb + 4, z, random.choice(LEAF))
            if random.random() < 0.15 and (x in (x1 - 1, x2 + 1) or z in (z1 - 1, z2 + 1)):
                sb(x, yb + 3, z, random.choice(LEAF))
    sb((x1 + x2) // 2, yb + 2, (z1 + z2) // 2, 'minecraft:lantern[hanging=true]')
pergola(15, 2, 19, 5, 6, 0.65)
pergola(15, 7, 19, 9, 11, 0.7)
pergola(7, 5, 11, 9, 16, 0.75)
pergola(1, 1, 4, 4, 16, 0.6)

# 4. sân thượng khối chính: lát đất nung, bồn cây, cây bụi lớn
for x in range(1, 12):
    for z in range(1, 10):
        sb(x, 15, z, random.choice(TILE))
blob(6, 17, 2, 2, 1, 1, 0.8)
for x, z in ((2, 8), (5, 8), (10, 2), (11, 6)):
    sb(x, 16, z, random.choice(POTS))
# lá tràn qua lan can răng cưa + dây leo rủ mặt tiền
for x in range(0, 13):
    if random.random() < 0.55:
        sb(x, 17, 0, random.choice(LEAF))
    if random.random() < 0.45:
        sb(x, 16, -1, random.choice(LEAF))
    if random.random() < 0.3:
        vines(x, 15, -2, 'south', random.randint(3, 7))
blob(0, 9, -1, 1, 3, 1, 0.75)                                        # mảng lá leo góc tây
blob(12, 8, -1, 1, 2, 1, 0.7)

# 5. cầu thang đá hai bên trên chân đế đá
# bên phải: lên sân thượng thấp của cánh (x 22, đi về nam)
for i in range(0, 6):
    if i > 0:
        for y in range(0, i):
            sb(22, y, 1 + i, random.choice(BASE))
    sb(22, i, 1 + i, 'minecraft:stone_brick_stairs[facing=south]')
    sb(23, i + 1, 1 + i, 'minecraft:diorite_wall')
fill(22, 0, 7, 22, 4, 10, 'minecraft:stone_bricks')
sb(21, 6, 6, 'minecraft:air')
# từ sân thượng thấp lên sân thượng cao (trong, dọc x 20, đi về nam)
for i in range(0, 5):
    sb(20, 6 + i, 2 + i, 'minecraft:quartz_stairs[facing=south]')
    if i > 0:
        fill(20, 6, 2 + i, 20, 5 + i, 2 + i, 'minecraft:quartz_block')
sb(20, 11, 6, 'minecraft:air')
# bên trái: bậc đá lên bậu tầng 2 khối chính (trang trí + chậu)
for i in range(0, 5):
    if i > 0:
        for y in range(0, i):
            sb(-1, y, 7 - i, random.choice(BASE))
    sb(-1, i, 7 - i, 'minecraft:stone_brick_stairs[facing=north]')
    sb(-2, i + 1, 7 - i, 'minecraft:diorite_wall')
fill(-1, 0, 0, -1, 4, 2, 'minecraft:stone_bricks')
sb(-1, 5, 1, 'minecraft:decorated_pot'); sb(-1, 5, 0, random.choice(LEAF))

# 6. chậu gốm, bụi cây, đèn sân trước
for x, z in ((1, -1), (5, -1), (7, -1), (11, -1), (13, 0), (21, 0), (-1, -1), (22, 0)):
    sb(x, 0, z, random.choice(POTS))
for x in range(-3, 25):
    if random.random() < 0.35 and x not in (3, 9, 17):
        sb(x, 0, -3, random.choice(['minecraft:azalea', 'minecraft:flowering_azalea', 'minecraft:fern', 'minecraft:sweet_berry_bush[age=3]']))
fill(-1, -1, -2, 22, -1, -1, 'minecraft:smooth_stone')
for x, z, y in ((3, 4, 9), (9, 4, 9), (6, 5, 4), (17, 5, 4), (17, 8, 9), (3, 6, 14), (9, 6, 14)):
    sb(x, y, z, 'minecraft:lantern[hanging=true]')

print(len(cmds), 'lệnh')
errs = 0
for i in range(0, len(cmds), 150):
    for c, out in app.rcon(SERVER, cmds[i:i + 150]):
        o = app.COLOR_RE.sub('', out or '')
        if any(k in o for k in ('Unknown', 'Incorrect', 'Expected', 'Invalid', 'not loaded', 'Too many', 'error')):
            errs += 1
            if errs <= 12:
                print('LỖI:', c[:120], '->', o[:160])
print('xong, lỗi:', errs)
