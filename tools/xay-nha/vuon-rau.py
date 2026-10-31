# Vườn rau bậc thang 3 tầng, tường gạch đá răng cưa, đường đất vòng ngoài, dựng bằng lệnh vanilla qua RCON.
# Chạy trong container panel: python3 vuon-rau.py <server> <x> <y> <z>
#   x z = góc tây bắc của cả khu (kể cả đường đất), y = mặt đất + 1. Khu rộng 13 (trục x) x 15 (trục z).
#   Mặt trước (luống thấp nhất) quay về phía tây; phía sau: luống cao nhất bên bắc, luống giữa bên nam.
# 31/10 đặt ở LetGoMine: banghoi-1 13858 63 -2606 (bên trái biệt thự nhìn từ vịnh).
import random, sys
sys.path.insert(0, '/app')
import app

SERVER = sys.argv[1] if len(sys.argv) > 1 else 'sangtao-1'
X0, Y0, Z0 = (int(v) for v in sys.argv[2:5]) if len(sys.argv) > 4 else (10060, -60, 10000)
random.seed(5)
cmds = []
DEPTH, WIDTH = 13, 15          # u: trước -> sau (x tăng), v: trái/bắc -> phải/nam (z tăng)
CU, CV = 1, 2                  # góc lõi (tường) trong khu


def P(u, y, v):
    return f'{X0 + u} {Y0 + y} {Z0 + v}'


def fill(u1, y1, v1, u2, y2, v2, b, mode=''):
    cmds.append(f'minecraft:fill {P(u1, y1, v1)} {P(u2, y2, v2)} {b} {mode}'.strip())


def sb(u, y, v, b):
    cmds.append(f'minecraft:setblock {P(u, y, v)} {b}')


STONE = ['minecraft:stone_bricks'] * 5 + ['minecraft:mossy_stone_bricks'] * 2 + ['minecraft:cracked_stone_bricks']
SLAB = ['minecraft:stone_brick_slab', 'minecraft:stone_brick_slab', 'minecraft:mossy_stone_brick_slab']

# 0. san nền
fill(0, 0, 0, DEPTH - 1, 12, WIDTH - 1, 'minecraft:air')
fill(0, -4, 0, DEPTH - 1, -2, WIDTH - 1, 'minecraft:dirt')
fill(0, -1, 0, DEPTH - 1, -1, WIDTH - 1, 'minecraft:grass_block')

# 1. đường đất: hàng trước/sau sát mép, hai bên cách tường một dải cỏ; lốm đốm đất thô
for u in range(DEPTH):
    for v in range(WIDTH):
        if u in (0, DEPTH - 1) or v in (0, WIDTH - 1):
            sb(u, -1, v, 'minecraft:coarse_dirt' if random.random() < 0.3 else 'minecraft:dirt_path')

# 2. ba luống: (u1, v1, u2, v2) theo toạ độ lõi, độ cao luống (mặt đất cày = y lvl-1), cây trồng
beds = [((0, 2, 5, 8), 0, 'minecraft:potatoes[age=7]'),       # trước, thấp nhất
        ((5, 5, 10, 10), 1, 'minecraft:wheat[age=7]'),         # sau-phải (nam), tầng giữa
        ((5, 0, 10, 5), 2, 'minecraft:carrots[age=7]')]        # sau-trái (bắc), cao nhất
for (a1, b1, a2, b2), lvl, crop in beds:
    u1, v1, u2, v2 = a1 + CU, b1 + CV, a2 + CU, b2 + CV
    if lvl:
        fill(u1, 0, v1, u2, lvl - 1, v2, 'minecraft:dirt')
    top = lvl                                                   # tường cao tới ngang mặt cây
    for u in range(u1, u2 + 1):
        for v in range(v1, v2 + 1):
            if u in (u1, u2) or v in (v1, v2):
                for y in range(0, top + 1):
                    sb(u, y, v, random.choice(STONE))
                if (u + v) % 2 == 0:                            # răng cưa
                    sb(u, top + 1, v, random.choice(STONE + SLAB))
    fill(u1 + 1, lvl - 1, v1 + 1, u2 - 1, lvl - 1, v2 - 1, 'minecraft:farmland[moisture=7]')
    fill(u1 + 1, lvl, v1 + 1, u2 - 1, lvl, v2 - 1, crop)
    fill(u1 + 1, lvl + 1, v1 + 1, u2 - 1, lvl + 2, v2 - 1, 'minecraft:air')
    # nước giấu trong tường: phiến đá ngập nước ngang mặt đất cày, giữa cạnh trước của luống (tưới đủ 4 ô)
    sb(u1, lvl - 1, (v1 + v2) // 2, 'minecraft:stone_brick_slab[type=top,waterlogged=true]')
    sb(u2, lvl - 1, (v1 + v2) // 2, 'minecraft:stone_brick_slab[type=top,waterlogged=true]')

# 3. cột rào + đèn ở các góc ngoài, bụi azalea + hoa hồng trên dải cỏ hai bên
for u, v, y in ((CU, CV + 2, 1), (CU, CV + 8, 1), (CU + 10, CV, 3), (CU + 10, CV + 10, 2), (CU + 5, CV, 3), (CU + 5, CV + 10, 2)):
    sb(u, y, v, 'minecraft:oak_fence'); sb(u, y + 1, v, 'minecraft:lantern')
for u, v in ((CU + 1, CV + 1), (CU + 3, CV - 1), (CU + 2, CV + 9), (CU + 8, CV + 11), (CU + 6, CV - 1)):
    sb(u, 0, v, random.choice(['minecraft:azalea_leaves[persistent=true]', 'minecraft:flowering_azalea_leaves[persistent=true]']))
for u, v in ((CU, CV + 1), (CU + 4, CV + 9), (CU + 9, CV - 1), (CU + 3, CV + 11)):
    sb(u, 0, v, 'minecraft:rose_bush[half=lower]'); sb(u, 1, v, 'minecraft:rose_bush[half=upper]')

# 4. bù nhìn nhỏ trong luống trước
sb(CU + 2, 0, CV + 5, 'minecraft:oak_fence'); sb(CU + 2, 1, CV + 5, 'minecraft:carved_pumpkin[facing=west]')

print(len(cmds), 'lệnh')
errs = 0
for i in range(0, len(cmds), 150):
    for c, out in app.rcon(SERVER, cmds[i:i + 150]):
        o = app.COLOR_RE.sub('', out or '')
        if any(k in o for k in ('Unknown', 'Incorrect', 'Expected', 'Invalid', 'not loaded', 'Too many', 'error'))   # 'Could not set' = khối đã giống, bỏ qua:
            errs += 1
            if errs <= 8:
                print('LỖI:', c[:120], '->', o[:160])
print('xong, lỗi:', errs)
