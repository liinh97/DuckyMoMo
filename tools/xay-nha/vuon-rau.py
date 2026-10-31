# Vườn rau bậc thang 3 tầng, tường gỗ răng cưa, đường đất vòng ngoài, dựng bằng lệnh vanilla qua RCON.
# Chạy trong container panel: python3 vuon-rau.py <server> <x> <y> <z>
#   x z = góc tây bắc của cả khu (kể cả đường đất), y = mặt đất + 1. Khu rộng 15 (trục x) x 17 (trục z). Ruộng trong 5x5 (lẻ) để ô nước nằm đúng giữa.
#   Không dùng khối ngập nước trong tường: nước chảy ra ngoài làm ngập luống thấp (31/10).
#   Mặt trước (luống thấp nhất) quay về phía tây; phía sau: luống cao nhất bên bắc, luống giữa bên nam.
# 31/10 đặt ở LetGoMine: banghoi-1 13856 63 -2607 (lấn 2 cột mép vịnh) (bên trái biệt thự nhìn từ vịnh).
import random, sys
sys.path.insert(0, '/app')
import app

SERVER = sys.argv[1] if len(sys.argv) > 1 else 'sangtao-1'
X0, Y0, Z0 = (int(v) for v in sys.argv[2:5]) if len(sys.argv) > 4 else (10060, -60, 10000)
random.seed(5)
cmds = []
DEPTH, WIDTH = 15, 17          # u: trước -> sau (x tăng), v: trái/bắc -> phải/nam (z tăng)
CU, CV = 1, 2                  # góc lõi (tường) trong khu


def P(u, y, v):
    return f'{X0 + u} {Y0 + y} {Z0 + v}'


def fill(u1, y1, v1, u2, y2, v2, b, mode=''):
    cmds.append(f'minecraft:fill {P(u1, y1, v1)} {P(u2, y2, v2)} {b} {mode}'.strip())


def sb(u, y, v, b):
    cmds.append(f'minecraft:setblock {P(u, y, v)} {b}')


STONE = ['minecraft:spruce_planks'] * 5 + ['minecraft:stripped_spruce_wood'] * 2 + ['minecraft:oak_planks']   # 31/10: tường gỗ (trước là gạch đá)
SLAB = ['minecraft:spruce_slab', 'minecraft:spruce_slab', 'minecraft:oak_slab']

# 0. san nền
fill(0, 0, 0, DEPTH - 1, 12, WIDTH - 1, 'minecraft:air')
fill(0, -12, 0, DEPTH - 1, -2, WIDTH - 1, 'minecraft:dirt')        # sâu để lấp được mép nước
fill(0, -1, 0, DEPTH - 1, -1, WIDTH - 1, 'minecraft:grass_block')

# 1. đường đất: hàng trước/sau sát mép, hai bên cách tường một dải cỏ; lốm đốm đất thô
for u in range(DEPTH):
    for v in range(WIDTH):
        if u in (0, DEPTH - 1) or v in (0, WIDTH - 1):
            sb(u, -1, v, 'minecraft:coarse_dirt' if random.random() < 0.3 else 'minecraft:dirt_path')

# 2. ba luống vuông (ruộng trong 5x5): (u1, v1, u2, v2) theo toạ độ lõi, độ cao luống (mặt đất cày = y lvl-1), cây trồng
beds = [((0, 3, 6, 9), 0, 'minecraft:potatoes[age=7]'),       # trước, thấp nhất
        ((6, 6, 12, 12), 1, 'minecraft:wheat[age=7]'),         # sau-phải (nam), tầng giữa
        ((6, 0, 12, 6), 2, 'minecraft:carrots[age=7]')]        # sau-trái (bắc), cao nhất
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
    # một ô nước giữa ruộng, ngang mặt đất cày (xung quanh là đất cày nên nước không chảy, tưới đủ 4 ô)
    cu, cv = (u1 + u2) // 2, (v1 + v2) // 2
    sb(cu, lvl, cv, 'minecraft:air'); sb(cu, lvl - 1, cv, 'minecraft:water')

# 3. cột rào + đèn ở các góc ngoài, bụi azalea + hoa hồng trên dải cỏ hai bên
for u, v, y in ((CU, CV + 3, 1), (CU, CV + 9, 1), (CU + 12, CV, 3), (CU + 12, CV + 12, 2), (CU + 6, CV, 3), (CU + 6, CV + 12, 2)):
    sb(u, y, v, 'minecraft:oak_fence'); sb(u, y + 1, v, 'minecraft:lantern')
for u, v in ((CU + 1, CV + 1), (CU + 3, CV - 1), (CU + 2, CV + 11), (CU + 10, CV + 13), (CU + 8, CV - 1)):
    sb(u, 0, v, random.choice(['minecraft:azalea_leaves[persistent=true]', 'minecraft:flowering_azalea_leaves[persistent=true]']))
for u, v in ((CU, CV + 1), (CU + 4, CV + 11), (CU + 11, CV - 1), (CU + 3, CV + 13)):
    sb(u, 0, v, 'minecraft:rose_bush[half=lower]'); sb(u, 1, v, 'minecraft:rose_bush[half=upper]')

# 4. bù nhìn nhỏ trong luống trước
sb(CU + 5, 0, CV + 8, 'minecraft:oak_fence'); sb(CU + 5, 1, CV + 8, 'minecraft:carved_pumpkin[facing=west]')

print(len(cmds), 'lệnh')
errs = 0
for i in range(0, len(cmds), 150):
    for c, out in app.rcon(SERVER, cmds[i:i + 150]):
        o = app.COLOR_RE.sub('', out or '')
        if any(k in o for k in ('Unknown', 'Incorrect', 'Expected', 'Invalid', 'not loaded', 'Too many', 'error')):   # 'Could not set' = khối đã giống, bỏ qua
            errs += 1
            if errs <= 8:
                print('LỖI:', c[:120], '->', o[:160])
print('xong, lỗi:', errs)
