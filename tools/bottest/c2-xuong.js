// c2 đồ Xưởng còn lại ở đất hoang: Đào Hầm (dao_vung 5×5), Rìu Tiều (chat_cay tối đa 150 gỗ), Gặt Lúa (gat_lua 3×3 + gieo lại).
// Dựng khối bằng lệnh, bot phá 1 khối, đếm khối còn lại phía server + độ bền trên lore.
const { start, moveTo } = require('./h');
const N = 'ThuC2';
(async () => {
  let t = await start(N, '10.9.4.2');
  const R = c => t.rc.cmd(c);
  await R(`lp user ${N} permission set grim.exempt true`); await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`);
  t = await moveTo(t, 13250, -2950, 'minecraft:overworld', '10.9.4.2');
  const pos = (await R(`data get entity ${N} Pos`)).match(/\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]/).slice(1).map(Number);
  const px = Math.floor(pos[0]), py = Math.floor(pos[1]), pz = Math.floor(pos[2]);
  // đếm khối: đổi tạm sang bọt biển (fill báo số khối đổi) rồi đổi lại (fill cùng loại thì báo 0 vì không đổi gì)
  const count = async (x1, y1, z1, x2, y2, z2, block) => {
    const n = +(((await R(`fill ${x1} ${y1} ${z1} ${x2} ${y2} ${z2} minecraft:sponge replace ${block}`)).match(/(\d+) block/) || [0, 0])[1]);
    await R(`fill ${x1} ${y1} ${z1} ${x2} ${y2} ${z2} ${block} replace minecraft:sponge`); return n;
  };
  const durab = () => t.bot.heldItem ? t.itemText(t.bot.heldItem).lore.filter(l => /bền/i.test(l)).join(' ') : '(trống tay)';
  const dig = async (x, y, z, face) => {
    await t.bot.lookAt(new (require('vec3'))(x + 0.5, y + 0.5, z + 0.5), true);
    t.bot._client.write('block_dig', { status: 0, location: { x, y, z }, face, sequence: 300 }); await t.sleep(150);
    t.bot._client.write('block_dig', { status: 2, location: { x, y, z }, face, sequence: 301 }); await t.sleep(1500);
  };
  const give = async id => { await R(`minecraft:clear ${N}`); t.log('  ', (await R(`xuong tang ${id} ${N}`)).trim()); t.bot.setQuickBarSlot(0); await t.sleep(800); };
  if (!process.argv.includes('lua')) {
  // Đào Hầm: tấm đá 5×5 dựng đứng phía đông, cách 2 khối
  await R(`fill ${px + 1} ${py} ${pz - 3} ${px + 3} ${py + 5} ${pz + 3} minecraft:air`);
  await R(`fill ${px + 2} ${py - 1} ${pz - 2} ${px + 2} ${py + 3} ${pz + 2} minecraft:stone`);
  await give('dao_ham'); const d0 = durab();
  await dig(px + 2, py + 1, pz, 4);
  t.log(`c2 Đào Hầm (5×5): đá còn ${await count(px + 2, py - 1, pz - 2, px + 2, py + 3, pz + 2, 'minecraft:stone')}/25 | độ bền ${d0} -> ${durab()}`);
  await R(`fill ${px + 2} ${py - 1} ${pz - 2} ${px + 2} ${py + 3} ${pz + 2} minecraft:air`); await R(`setblock ${px + 2} ${py - 1} ${pz} minecraft:grass_block`);
  // Rìu Tiều: cây sồi 6 khối gỗ + tán lá
  const tx = px + 2, tz = pz + 3;
  await R(`fill ${tx} ${py} ${tz} ${tx} ${py + 5} ${tz} minecraft:oak_log`);
  await R(`fill ${tx - 2} ${py + 4} ${tz - 2} ${tx + 2} ${py + 6} ${tz + 2} minecraft:oak_leaves[persistent=false] replace minecraft:air`);
  await give('riu_tieu'); const r0 = durab();
  await dig(tx, py, tz, 4);
  t.log(`c2 Rìu Tiều: gỗ còn ${await count(tx, py, tz, tx, py + 5, tz, 'minecraft:oak_log')}/6 | độ bền ${r0} -> ${durab()}`);
  await R(`fill ${tx - 2} ${py} ${tz - 2} ${tx + 2} ${py + 6} ${tz + 2} minecraft:air`);
  }
  // Gặt Lúa: ruộng 3×3 lúa chín, 9 hạt trong túi
  const fx = px + 3, fz = pz - 3;
  await R(`fill ${fx - 1} ${py - 1} ${fz - 1} ${fx + 1} ${py - 1} ${fz + 1} minecraft:farmland[moisture=7]`);
  await R(`fill ${fx - 1} ${py} ${fz - 1} ${fx + 1} ${py} ${fz + 1} minecraft:wheat[age=7]`);
  await give('gat_lua'); await R(`minecraft:give ${N} minecraft:wheat_seeds 9`); await t.sleep(500); const g0 = durab();
  await dig(fx, py, fz, 1);
  await t.sleep(1500);
  t.log('  ô giữa / góc sau khi gặt:', (await R(`execute if block ${fx} ${py} ${fz} minecraft:wheat`)).trim(), '/', (await R(`execute if block ${fx - 1} ${py} ${fz - 1} minecraft:wheat`)).trim(), '| ruộng:', (await R(`execute if block ${fx} ${py - 1} ${fz} minecraft:farmland`)).trim(), '| đồ rơi quanh:', (await R(`execute positioned ${fx} ${py} ${fz} if entity @e[type=item,distance=..4]`)).trim());
  const chin = await count(fx - 1, py, fz - 1, fx + 1, py, fz + 1, 'minecraft:wheat[age=7]');
  const moi = await count(fx - 1, py, fz - 1, fx + 1, py, fz + 1, 'minecraft:wheat[age=0]');
  const seeds = ((await R(`minecraft:clear ${N} minecraft:wheat_seeds 0`)).match(/Found (\d+)/) || [0, 0])[1];
  const wheat = ((await R(`minecraft:clear ${N} minecraft:wheat 0`)).match(/Found (\d+)/) || [0, 0])[1];
  t.log(`c2 Gặt Lúa (3×3): lúa chín còn ${chin}/9, cây mới gieo ${moi}/9 | hạt trong túi ${seeds} (lúc đầu 9) | lúa thu ${wheat} | độ bền ${g0} -> ${durab()}`);
  await R(`fill ${fx - 1} ${py} ${fz - 1} ${fx + 1} ${py} ${fz + 1} minecraft:air`); await R(`fill ${fx - 1} ${py - 1} ${fz - 1} ${fx + 1} ${py - 1} ${fz + 1} minecraft:grass_block`);
  await R(`execute at ${N} run minecraft:kill @e[type=item,distance=..12]`); await R(`minecraft:clear ${N}`); await R(`lp user ${N} permission unset grim.exempt`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e.message); process.exit(1); });
