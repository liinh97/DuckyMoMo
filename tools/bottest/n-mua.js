// n3 được mùa: gặt khoai chín (mùa khô +25% rơi thêm 1 củ) và khoai chưa chín (không thêm); n4 dưa hấu mọc từ dây (hệ số dưa 1,3
// = 30% rơi thêm 3-7 lát) và dưa tự đặt (không thêm); n8 nấu ăn: thiếu nguyên liệu báo rõ, ăn 2 món liền bị chặn.
// Cách đếm: mỗi cú phá, vanilla sinh 1 "gói đồ rơi" mỗi loại; phần thưởng mùa là 1 gói riêng -> đếm gói (sự kiện itemDrop của bot).
const { start, moveTo } = require('./h');
const N = 'ThuNa';
(async () => {
  let t = await start(N);
  t = await moveTo(t, 13200, -2900);
  const R = c => t.rc.cmd(c);
  await R(`gamemode survival ${N}`); await R(`effect give ${N} minecraft:resistance 900 4 true`); await R(`minecraft:clear ${N}`);
  const V = require('vec3');
  const p0 = t.bot.entity.position.floored(); const X = p0.x, Y = p0.y, Z = p0.z;
  t.log('mùa:', (await t.say('/mua', 1500)).slice(0, 3).join(' | ').slice(0, 200));
  const spots = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]];
  // đếm theo tổng số món: mỗi vòng phá 8 ô, kéo hết đồ rơi về bot, đếm trong túi rồi xoá
  const cnt = async item => { const m = (await R(`minecraft:clear ${N} minecraft:${item} 0`)).match(/Found (\d+)/); return m ? +m[1] : 0; };
  const harvest = async (label, rounds, place, item, base) => {
    let pha = 0, tong = 0;
    for (let r = 0; r < rounds; r++) {
      await R(`minecraft:clear ${N}`);
      for (const [dx, dz] of spots) await place(X + dx, Y, Z + dz);
      await t.sleep(500);
      for (const [dx, dz] of spots) {
        const b = t.bot.blockAt(new V(X + dx, Y, Z + dz));
        if (!b || b.name === 'air') continue;
        await t.bot.dig(b, true).catch(() => {}); pha++;
        await t.sleep(150);
      }
      await t.sleep(700);
      await R(`execute at ${N} run minecraft:tp @e[type=item,distance=..8] ${N}`);
      await t.sleep(1500);
      tong += await cnt(item);
    }
    t.log(`${label}: phá ${pha}, được ${tong} ${item} = trung bình ${(tong / Math.max(1, pha)).toFixed(2)}/ô (vanilla ${base})`);
  };
  const SB = (x, y, z, b) => R(`setblock ${x} ${y} ${z} ${b}`);
  await harvest('n3 khoai CHÍN', 15, async (x, y, z) => { await SB(x, y - 1, z, 'minecraft:farmland[moisture=7]'); await SB(x, y, z, 'minecraft:potatoes[age=7]'); }, 'potato', '2,71; +25% được mùa = 2,96');
  await harvest('n3 khoai CHƯA chín', 3, async (x, y, z) => { await SB(x, y - 1, z, 'minecraft:farmland[moisture=7]'); await SB(x, y, z, 'minecraft:potatoes[age=3]'); }, 'potato', '1,00 đúng');
  // n4: dưa mọc từ dây = ô dưa + dây gắn (attached_melon_stem quay về phía dưa) ở ô xa hơn
  const face = (dx, dz) => Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 'west' : 'east') : (dz > 0 ? 'north' : 'south');
  await harvest('n4 dưa MỌC TỪ DÂY', 6, async (x, y, z) => {
    const dx = Math.sign(x - X), dz = Math.sign(z - Z);
    await SB(x, y - 1, z, 'minecraft:grass_block');
    await SB(x, y, z, 'minecraft:melon');
    const sx = x + (Math.abs(x - X) >= Math.abs(z - Z) ? dx : 0), sz = z + (Math.abs(x - X) >= Math.abs(z - Z) ? 0 : dz);
    await SB(sx, y - 1, sz, 'minecraft:farmland[moisture=7]');
    await SB(sx, y, sz, `minecraft:attached_melon_stem[facing=${face(sx - x, sz - z)}]`);
  }, 'melon_slice', '5,00; +30% dây = 6,50');
  await harvest('n4 dưa TỰ ĐẶT (không dây)', 4, async (x, y, z) => { await SB(x, y - 1, z, 'minecraft:grass_block'); await SB(x, y, z, 'minecraft:melon'); }, 'melon_slice', '5,00');
  // dọn dây dưa
  for (const [dx, dz] of spots) for (const s of [1, 2]) await SB(X + dx + Math.sign(dx) * (s - 1), Y, Z + dz + Math.sign(dz) * (s - 1), 'minecraft:air');
  // n8 nấu ăn
  await R(`minecraft:clear ${N}`); await R(`minecraft:give ${N} minecraft:wheat 12`);
  let w = await t.openMenu('/nauan');
  const find = re => w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
  t.log('n8 menu:', w ? w.slots.slice(0, w.inventoryStart).filter(Boolean).map(i => t.itemText(i).name).join(', ').slice(0, 200) : 'không mở');
  let n0 = t.chat.length; w = await t.click(find(/Canh chua/i)); t.log('n8 nấu Canh chua (thiếu cá):', t.chat.slice(n0).join(' | ').slice(0, 200));
  n0 = t.chat.length; w = await t.click(find(/Cơm nắm/i)); w = await t.click(find(/Cơm nắm/i)); t.log('n8 nấu 2 Cơm nắm:', t.chat.slice(n0).join(' | ').slice(0, 200));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await R(`minecraft:effect give ${N} minecraft:hunger 3 255`); await t.sleep(3500);
  const eat = async () => { const it = t.bot.inventory.items().find(i => /Cơm nắm/.test(t.itemText(i).name)); if (!it) return 'không còn món'; await t.bot.equip(it, 'hand'); const n = t.chat.length; await t.bot.consume().catch(e => t.log('  consume:', e.message.slice(0, 60))); await t.sleep(1000); return t.chat.slice(n).join(' | ').slice(0, 200) + ' | còn ' + t.bot.inventory.items().filter(i => /Cơm nắm/.test(t.itemText(i).name)).reduce((a, i) => a + i.count, 0); };
  t.log('n8 ăn món 1:', await eat(), '| hiệu ứng:', (await R(`data get entity ${N} active_effects`)).replace(/.*data: /, '').slice(0, 160));
  t.log('n8 ăn món 2 ngay:', await eat());
  await R(`minecraft:clear ${N}`);
  await t.end();
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
