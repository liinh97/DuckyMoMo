// Chỉnh theo % của Thư viện đồ (22/10, ItemPercent): món thử thu_pct_kiem (Sát thương -50%, Tốc đánh -50%), thu_pct_ao (Máu +50%,
// Kháng đẩy lùi +100%), thu_pct_giay (Kháng đẩy lùi -100%). Sàn kính tạm y 150 ở đất hoang 13200,-2900, xong xoá sàn.
const h = require('./h');
const X = 13200, Y = 150, Z = -2900;
(async () => {
  let A = await h.start('ThuA'); const R = c => A.rc.cmd(c);
  await R(`forceload add ${X - 8} ${Z - 30} ${X + 8} ${Z + 8}`); await A.sleep(1500);
  await R(`fill ${X - 4} ${Y} ${Z - 25} ${X + 4} ${Y} ${Z + 4} minecraft:glass`);
  const place = async (t, n, x, z, face) => { for (let i = 0; i < 4; i++) { await t.rc.cmd(`minecraft:tp ${n} ${x} ${Y + 1} ${z} ${face} 0`); await t.end(); await A.sleep(1500); t = await h.start(n); await t.sleep(1500); if (Math.abs(t.bot.entity.position.x - x) < 3 && Math.abs(t.bot.entity.position.z - z) < 3) break; } return t; };
  A = await place(A, 'ThuA', X + 0.5, Z + 0.5, 180);
  const P = async n => (await R(`data get entity ${n} Pos`)).replace(/.*data: /, '').replace(/d/g, '').split(',').map(x => parseFloat(x.replace(/[\[\]]/g, '')));
  const give = async id => { await R('minecraft:clear ThuA'); await R(`dmm laydo ${id} ThuA 1`); await A.sleep(1000); return A.bot.inventory.items()[0]; };
  // 1) Máu +50%: áo giáp
  let it = await give('thu_pct_ao'); await A.bot.equip(it, 'torso'); await A.sleep(1000);
  A.log('1) máu tối đa khi mặc áo Máu +50%:', (await R('attribute ThuA minecraft:max_health get')).replace(/.*is /, ''));
  A.log('   mô tả áo:', JSON.stringify((await R('data get entity ThuA equipment.chest.components."minecraft:lore"')).replace(/.*data: /, '')).slice(0, 200));
  await R('minecraft:item replace entity ThuA armor.chest with minecraft:air');
  // 2) Sát thương -50%, 3) tốc đánh -50% (kiếm kim cương gốc 1,6 đòn/giây -> 0,8)
  const husk = async () => { await R(`minecraft:kill @e[type=husk,x=${X},y=${Y},z=${Z},distance=..40]`); await R(`summon minecraft:husk ${X + 0.5} ${Y + 1} ${Z - 2.5} {NoAI:1b,Health:200f,attributes:[{id:"minecraft:max_health",base:200}],PersistenceRequired:1b}`); await A.sleep(800); };
  const hp = async () => parseFloat((await R(`data get entity @e[type=husk,limit=1,sort=nearest,x=${X},y=${Y},z=${Z}] Health`)).replace(/.*data: /, ''));
  const hit = async () => { const e = A.bot.nearestEntity(x => x.name === 'husk'); if (!e) return; await A.bot.lookAt(e.position.offset(0, 1.5, 0), true); A.bot.attack(e); };
  for (const [label, id] of [['kiếm kim cương thường', null], ['kiếm Sát thương -50% / Tốc đánh -50%', 'thu_pct_kiem']]) {
    await R('minecraft:clear ThuA');
    if (id) { it = await give(id); } else { await R('minecraft:give ThuA minecraft:diamond_sword'); await A.sleep(800); it = A.bot.inventory.items()[0]; }
    await A.bot.equip(it, 'hand'); await A.sleep(2500);
    await husk(); await A.sleep(1500);
    const d = []; for (const gap of [2000, 2000, 600, 600]) { const h0 = await hp(); await hit(); await A.sleep(250); d.push((h0 - await hp()).toFixed(2)); await A.sleep(gap - 250); }
    A.log(`2-3) ${label}: mất máu từng đòn (cách 2 giây, 2 giây, rồi 0,6 giây, 0,6 giây):`, d.join(' | '));
  }
  await R(`minecraft:kill @e[type=husk,x=${X},y=${Y},z=${Z},distance=..40]`);
  // 4) Đẩy lùi: /damage 1 từ con husk đứng im phía trước (game đẩy lùi như bị quái đánh), so 3 trường hợp
  await R(`summon minecraft:husk ${X + 0.5} ${Y + 1} ${Z - 2.5} {NoAI:1b,Silent:1b,PersistenceRequired:1b}`); await A.sleep(800);
  await R('effect give ThuA minecraft:health_boost 300 4 true'); await R('effect give ThuA minecraft:instant_health 1 4 true');
  for (const [label, id, slot] of [['không đồ', null], ['áo Kháng đẩy lùi +100%', 'thu_pct_ao', 'torso'], ['giày Kháng đẩy lùi -100%', 'thu_pct_giay', 'feet'], ['không đồ (lần 2)', null]]) {
    await R('minecraft:clear ThuA'); for (const s of ['chest', 'feet']) await R(`minecraft:item replace entity ThuA armor.${s} with minecraft:air`);
    if (id) { it = await give(id); await A.bot.equip(it, slot); await A.sleep(800); }
    await R(`minecraft:tp ThuA ${X + 0.5} ${Y + 1} ${Z + 0.5} 180 0`); await A.sleep(1500);
    const a0 = await P('ThuA'); A.log('   ', (await R(`damage ThuA 1 minecraft:mob_attack by @e[type=husk,limit=1,sort=nearest,x=${X},y=${Y},z=${Z}]`)).trim());
    await A.sleep(2000); const a1 = await P('ThuA');
    A.log(`4) ${label}: bị đẩy`, Math.hypot(a1[0] - a0[0], a1[2] - a0[2]).toFixed(2), 'khối');
  }
  await R(`minecraft:kill @e[type=husk,x=${X},y=${Y},z=${Z},distance=..40]`);
  const B = { end: async () => {} };
  await R('minecraft:clear ThuA'); await R('minecraft:clear ThuX1'); await R('effect clear ThuA'); await R('effect clear ThuX1');
  for (const s of ['chest', 'feet']) await R(`minecraft:item replace entity ThuA armor.${s} with minecraft:air`);
  await R(`minecraft:tp ThuA ${X} 70 ${Z}`); await R(`minecraft:tp ThuX1 ${X} 70 ${Z}`);
  await R(`fill ${X - 4} ${Y} ${Z - 25} ${X + 4} ${Y} ${Z + 4} minecraft:air replace minecraft:glass`); await R(`forceload remove ${X - 8} ${Z - 30} ${X + 8} ${Z + 8}`);
  await B.end(); await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
