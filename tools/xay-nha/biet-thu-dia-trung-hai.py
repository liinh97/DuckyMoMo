# Cách chạy: docker cp tools/xay-nha/biet-thu-dia-trung-hai.py duckymomo-panel-1:/tmp/v.py && docker exec -w /app duckymomo-panel-1 python3 /tmp/v.py
# (dọn và dựng lại toàn bộ ở sangtao-1, toạ độ X0/Y0/Z0; đổi random.seed để ra cách bày cây khác)
# Biệt thự Địa Trung Hải (thiết kế riêng, không chép bản trả phí) dựng bằng lệnh vanilla qua RCON.
# Chạy trong container panel: python3 villa.py  (dùng app.rcon). Mặt trước nhìn về phía bắc (z giảm).
import random, sys
sys.path.insert(0, '/app')
import app

SERVER = 'sangtao-1'
X0, Y0, Z0 = 10000, -60, 10000
random.seed(7)
cmds = []

def P(x, y, z):
    return f"{X0 + x} {Y0 + y} {Z0 + z}"

def fill(x1, y1, z1, x2, y2, z2, b, mode=''):
    cmds.append(f"minecraft:fill {P(x1, y1, z1)} {P(x2, y2, z2)} {b} {mode}".strip())

def sb(x, y, z, b):
    cmds.append(f"minecraft:setblock {P(x, y, z)} {b}")

LEAF = ['minecraft:azalea_leaves[persistent=true]', 'minecraft:flowering_azalea_leaves[persistent=true]',
        'minecraft:oak_leaves[persistent=true]', 'minecraft:flowering_azalea_leaves[persistent=true]']
WALL = 'minecraft:white_concrete'
TRIM = 'minecraft:quartz_block'
BAND = 'minecraft:cyan_concrete'
TILE = ['minecraft:terracotta', 'minecraft:brown_terracotta', 'minecraft:terracotta', 'minecraft:orange_terracotta']

# 0. dọn khu, đặt nền
fill(-4, 0, -5, 25, 24, 14, 'minecraft:air')
fill(-4, -1, -5, 25, -1, 14, 'minecraft:grass_block')
fill(-1, -1, -3, 22, -1, -1, 'minecraft:smooth_stone')       # sân trước lát đá
fill(0, -1, 0, 20, -1, 10, 'minecraft:smooth_stone')

def box(x1, z1, x2, z2, h, roof_y):
    """Khối tường trắng rỗng: tường x1..x2, z1..z2, cao 0..h-1; sàn tầng mỗi 5; mái ở roof_y."""
    fill(x1, 0, z1, x2, h - 1, z2, WALL)
    fill(x1 + 1, 0, z1 + 1, x2 - 1, h - 1, z2 - 1, 'minecraft:air')
    fill(x1, 0, z1, x2, 0, z2, 'minecraft:stone_bricks', 'outline')      # chân tường đá
    fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, 'minecraft:air')
    for fy in range(5, roof_y, 5):
        fill(x1, fy, z1, x2, fy, z2, BAND, 'outline')                     # viền xanh giữa các tầng
        fill(x1 + 1, fy, z1 + 1, x2 - 1, fy, z2 - 1, 'minecraft:oak_planks')
    fill(x1, roof_y, z1, x2, roof_y, z2, TRIM)                            # mái
    for x, z in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        fill(x, 1, z, x, roof_y - 1, z, 'minecraft:quartz_pillar')        # cột góc

# 1. khối chính (3 tầng) và cánh phụ (2 tầng)
box(0, 0, 12, 10, 15, 15)
box(13, 2, 20, 10, 10, 10)
fill(0, 0, 0, 12, 0, 10, 'minecraft:air')
fill(1, -1, 1, 11, -1, 9, 'minecraft:polished_diorite')
fill(0, 0, 0, 12, 0, 10, 'minecraft:stone_bricks', 'outline')
fill(1, 0, 1, 11, 0, 9, 'minecraft:air')
fill(12, 1, 3, 12, 4, 9, 'minecraft:air')                                 # thông tầng 1 hai khối
fill(12, 6, 3, 12, 9, 9, 'minecraft:air')

# 2. lan can răng cưa trên mái
def battlement(x1, z1, x2, z2, y, skip=()):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if x in (x1, x2) or z in (z1, z2):
                if (x, z) in skip:
                    continue
                sb(x, y, z, WALL if (x + z) % 2 == 0 else 'minecraft:quartz_slab')
battlement(0, 0, 12, 10, 16)
battlement(13, 2, 20, 10, 11, skip={(20, 9), (20, 10), (13, 2), (13, 3), (13, 4), (13, 5), (13, 6), (13, 7), (13, 8), (13, 9), (13, 10)})

# 3. sàn sân thượng lát đất nung
for x in range(1, 12):
    for z in range(1, 10):
        sb(x, 15, z, random.choice(TILE))
for x in range(14, 20):
    for z in range(3, 10):
        sb(x, 10, z, random.choice(TILE))

# 4. cửa sổ + cửa chớp xanh (mặt trước z=0 của khối chính, z=2 của cánh)
def window(x1, x2, y1, y2, z, face='north', out=-1):
    fill(x1, y1, z, x2, y2, z, 'minecraft:glass_pane')
    for x in (x1 - 1, x2 + 1):
        for y in range(y1, y2 + 1):
            sb(x, y, z + out, f'minecraft:warped_trapdoor[open=true,facing={face}]')
    fill(x1, y1 - 1, z + out, x2, y1 - 1, z + out, 'minecraft:spruce_trapdoor[half=top,facing=' + face + ']')   # bậu cửa
    for x in range(x1, x2 + 1):
        if random.random() < 0.7:
            sb(x, y1 - 1 + 1, z + out, random.choice(['minecraft:potted_red_tulip', 'minecraft:potted_azalea_bush', 'minecraft:potted_fern', 'minecraft:potted_poppy']))

for y1, y2 in ((1, 2), (11, 12)):
    window(2, 3, y1, y2, 0); window(9, 10, y1, y2, 0)
window(2, 3, 6, 8, 0); window(9, 10, 6, 8, 0)
for y1, y2 in ((1, 2), (6, 7)):
    window(15, 16, y1, y2, 2); window(18, 19, y1, y2, 2)
# mặt sau (z=10) và hông tây (x=0)
for y1, y2 in ((1, 2), (6, 7), (11, 12)):
    fill(3, y1, 10, 4, y2, 10, 'minecraft:glass_pane'); fill(8, y1, 10, 9, y2, 10, 'minecraft:glass_pane')
    fill(0, y1, 3, 0, y2, 4, 'minecraft:glass_pane'); fill(0, y1, 7, 0, y2, 8, 'minecraft:glass_pane')

# 5. cửa chính: cửa xanh ngọc giữa hai tấm gạch men, ô vòm phía trên
for y in range(0, 3):
    sb(5, y, 0, 'minecraft:light_blue_glazed_terracotta[facing=north]')
    sb(7, y, 0, 'minecraft:light_blue_glazed_terracotta[facing=south]')
sb(6, 0, 0, 'minecraft:warped_door[half=lower,facing=north]')
sb(6, 1, 0, 'minecraft:warped_door[half=upper,facing=north]')
sb(6, 2, 0, 'minecraft:blue_glazed_terracotta')
fill(4, 3, -1, 8, 3, -1, 'minecraft:quartz_stairs[facing=south,half=top]')
sb(6, -1, -1, 'minecraft:smooth_quartz'); sb(6, -1, -2, 'minecraft:smooth_quartz')

# 6. ban công tầng 2 trước khối chính (cửa kính 3 ô)
fill(5, 6, 0, 7, 8, 0, 'minecraft:glass_pane')
fill(4, 5, -1, 8, 5, -2, 'minecraft:smooth_quartz_slab[type=top]')
fill(4, 6, -2, 8, 6, -2, 'minecraft:diorite_wall')
sb(4, 6, -1, 'minecraft:diorite_wall'); sb(8, 6, -1, 'minecraft:diorite_wall')
sb(4, 7, -2, 'minecraft:potted_azalea_bush'); sb(8, 7, -2, 'minecraft:potted_flowering_azalea_bush')

# 7. cầu thang ngoài lên sân thượng cánh phụ (sườn đông)
for i in range(0, 11):
    if i > 0:
        fill(21, 0, i, 21, i - 1, i, 'minecraft:stone_bricks')
    sb(21, i, i, 'minecraft:stone_brick_stairs[facing=south]')
    sb(22, i + 1, i, 'minecraft:diorite_wall')

# 8. giàn gỗ + tán lá trên hai sân thượng
def pergola(x1, z1, x2, z2, yb):
    for x, z in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        fill(x, yb, z, x, yb + 2, z, 'minecraft:stripped_oak_log')
    for z in range(z1, z2 + 1, 3):
        fill(x1, yb + 3, z, x2, yb + 3, z, 'minecraft:stripped_spruce_log[axis=x]')
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if random.random() < 0.55:
                sb(x, yb + 4, z, random.choice(LEAF))
    sb((x1 + x2) // 2, yb + 2, (z1 + z2) // 2, 'minecraft:lantern[hanging=true]')
pergola(14, 3, 19, 9, 11)
pergola(7, 5, 11, 9, 16)

# 9. cây xanh tràn xuống mặt tiền: bụi lá trên lan can + dây leo
for x in range(0, 13):
    if random.random() < 0.5:
        sb(x, 17, 0, random.choice(LEAF))
    if random.random() < 0.35:
        for y in range(16, 16 - random.randint(3, 8), -1):
            sb(x, y, -1, 'minecraft:vine[south=true]')
for x in range(13, 21):
    if random.random() < 0.5:
        sb(x, 12, 2, random.choice(LEAF))
    if random.random() < 0.3:
        for y in range(11, 11 - random.randint(2, 6), -1):
            sb(x, y, 1, 'minecraft:vine[south=true]')
for x in (1, 4, 8, 11):                                                     # bụi cây quanh cửa sổ tầng 2
    for dy in (9, 10):
        if random.random() < 0.6:
            sb(x, dy, -1, random.choice(LEAF))

# 10. vườn trước: bụi azalea, chậu, đèn
for x in range(-1, 22):
    if random.random() < 0.4 and not 4 <= x <= 8:
        sb(x, 0, -3, random.choice(['minecraft:azalea', 'minecraft:flowering_azalea', 'minecraft:fern', 'minecraft:rose_bush[half=lower]']))
for x, z in ((4, -1), (8, -1), (13, 1), (20, 1)):
    sb(x, 0, z, 'minecraft:decorated_pot')
for x in (3, 9):
    sb(x, 0, -2, 'minecraft:lantern')
for x, z, y in ((3, 3, 14), (9, 3, 14), (6, 7, 14), (3, 5, 9), (9, 5, 9), (6, 5, 4), (16, 6, 9), (16, 6, 4)):
    sb(x, y, z, 'minecraft:lantern[hanging=true]')

# 11. chậu cây trên sân thượng
for x, z in ((2, 2), (2, 8), (5, 2), (10, 2), (15, 4), (18, 8)):
    sb(x, 16 if x < 13 else 11, z, random.choice(['minecraft:potted_flowering_azalea_bush', 'minecraft:potted_red_tulip', 'minecraft:decorated_pot']))

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
