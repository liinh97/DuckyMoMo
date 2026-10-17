"""Dựng sảnh PvP (Đấu Trường) trong thế giới "dautruong": nền, tường barrier, 3 cổng vòm (mỗi cổng một sân).

Cách dùng (thư mục gốc repo, server banghoi-1 đang chạy, nên lúc 0 người online):
  python3 tools/build-pvp-lobby.py > /tmp/lobby.cmds
  docker exec -i duckymomo-banghoi-1-1 rcon-cli < /tmp/lobby.cmds
Sau đó: ghi sảnh/cổng/tên sân vào plugins/DuckyMoMoCore/dautruong.yml (in ra stderr dạng YAML), bảng chữ nổi
DecentHolograms (dt_cong_<sân>.yml) và warp Essentials "pvp" — xem docs/HANDOFF.md mục Đấu Trường.
Toạ độ: tâm sảnh (0, 64, -150), cổng ở phía bắc z = -172, x = -14 / 0 / 14; người chơi xuất hiện ở (0.5, 65, -130.5) nhìn về cổng.
"""
import sys

W = "execute in minecraft:dautruong run "
GATES = [  # (sân, x tâm, kính, thảm, đèn)
    ("san1", -14, "lime_stained_glass", "moss_carpet", "sea_lantern"),
    ("san2", 0, "orange_stained_glass", "orange_carpet", "shroomlight"),
    ("san3", 14, "purple_stained_glass", "purple_carpet", "glowstone"),
]
X1, X2, Z1, Z2, Y = -24, 24, -177, -127, 64


def fill(a, b, c, d, e, f, block):
    return f"{W}fill {a} {b} {c} {d} {e} {f} minecraft:{block}"


out = [f"{W}forceload add {X1 - 8} {Z1 - 8} {X2 + 8} {Z2 + 8}"]
# dọn khoảng không (lần dựng lại)
for za in range(Z1 - 1, Z2 + 2, 16):   # fill tối đa 32768 khối/lệnh: chia theo dải z
    out.append(fill(X1 - 1, Y - 1, za, X2 + 1, Y + 16, min(za + 15, Z2 + 1), "air"))
# nền: viền đá đen gạch, trong đá mịn, quảng trường giữa đá đen bóng + chữ thập đỏ
out.append(fill(X1, Y - 1, Z1, X2, Y - 1, Z2, "deepslate_tiles"))
out.append(fill(X1, Y, Z1, X2, Y, Z2, "polished_blackstone_bricks"))
out.append(fill(X1 + 2, Y, Z1 + 2, X2 - 2, Y, Z2 - 2, "smooth_stone"))
out.append(fill(-8, Y, -158, 8, Y, -142, "polished_blackstone"))
out.append(fill(-1, Y, -158, 1, Y, -142, "red_nether_bricks"))
out.append(fill(-8, Y, -151, 8, Y, -149, "red_nether_bricks"))
out.append(fill(-2, Y, -152, 2, Y, -148, "gilded_blackstone"))
# lối đi từ chỗ xuất hiện tới từng cổng
for _, gx, _, carpet, _ in GATES:
    out.append(fill(gx - 1, Y, -170, gx + 1, Y, -159, "polished_blackstone_bricks"))
out.append(fill(-1, Y, -141, 1, Y, -128, "polished_blackstone_bricks"))
# tường thấp quanh mép + cột đèn lồng ở góc và giữa các cạnh
for (a, c, d, f) in [(X1, Z1, X2, Z1), (X1, Z2, X2, Z2), (X1, Z1, X1, Z2), (X2, Z1, X2, Z2)]:
    out.append(fill(a, Y + 1, c, d, Y + 1, f, "polished_blackstone_brick_wall"))
for px, pz in [(X1, Z1), (X2, Z1), (X1, Z2), (X2, Z2), (X1, -152), (X2, -152), (-8, Z2), (8, Z2)]:
    out.append(fill(px, Y + 1, pz, px, Y + 3, pz, "polished_blackstone_bricks"))
    out.append(f"{W}setblock {px} {Y + 4} {pz} minecraft:lantern")
# lối vào phía nam (nơi xuất hiện) để trống tường 3 ô
out.append(fill(-1, Y + 1, Z2, 1, Y + 1, Z2, "air"))
# cổng vòm: 2 cột 2x2, lanh tô 2 lớp, trong vòm kính màu ở mặt sau, thảm màu dưới chân
for san, gx, glass, carpet, light in GATES:
    gz = -172
    for side in (-4, 3):
        out.append(fill(gx + side, Y + 1, gz - 1, gx + side + 1, Y + 8, gz, "polished_blackstone_bricks"))
        out.append(fill(gx + side, Y + 9, gz - 1, gx + side + 1, Y + 9, gz, "chiseled_polished_blackstone"))
    out.append(fill(gx - 4, Y + 8, gz - 1, gx + 4, Y + 9, gz, "polished_blackstone_bricks"))
    out.append(fill(gx - 2, Y + 8, gz, gx + 2, Y + 8, gz, light))
    out.append(fill(gx - 1, Y + 10, gz - 1, gx + 1, Y + 10, gz, "chiseled_polished_blackstone"))
    out.append(fill(gx - 2, Y + 1, gz - 2, gx + 2, Y + 7, gz - 2, glass))           # "mặt cổng" phát sáng phía sau
    out.append(fill(gx - 2, Y, gz - 1, gx + 2, Y, gz, "crying_obsidian"))
    out.append(fill(gx - 2, Y + 1, gz - 1, gx + 2, Y + 1, gz, carpet))
    out.append(fill(gx - 2, Y, gz + 1, gx + 2, Y, gz + 1, "gilded_blackstone"))
    out.append(fill(gx - 2, Y - 1, gz - 2, gx + 2, Y - 1, gz - 2, light))
# tường barrier vô hình bao sảnh (không rơi ra khoảng không) + trần
out.append(fill(X1 - 1, Y, Z1 - 1, X2 + 1, Y + 14, Z1 - 1, "barrier"))
out.append(fill(X1 - 1, Y, Z2 + 1, X2 + 1, Y + 14, Z2 + 1, "barrier"))
out.append(fill(X1 - 1, Y, Z1 - 1, X1 - 1, Y + 14, Z2 + 1, "barrier"))
out.append(fill(X2 + 1, Y, Z1 - 1, X2 + 1, Y + 14, Z2 + 1, "barrier"))
out.append(fill(X1 - 1, Y + 15, Z1 - 1, X2 + 1, Y + 15, Z2 + 1, "barrier"))
out.append(f"{W}forceload remove {X1 - 8} {Z1 - 8} {X2 + 8} {Z2 + 8}")
print("\n".join(out))

# phần ghi vào dautruong.yml (để người chạy chép, hoặc script khác ghép)
print("sanh: dautruong,0.5,65,-130.5,180.0,0.0", file=sys.stderr)
for san, gx, *_ in GATES:
    print(f"cong.{san}: {gx - 2},{Y + 1},-173,{gx + 2},{Y + 3},-171", file=sys.stderr)
