// Hành quyết CE đã chỉnh yếu (CeExecuteTamer, 22/10): kiếm thu_execute (Execute V). Đánh husk đầy máu -> KHÔNG có Sức mạnh;
// husk còn 20% máu -> Sức mạnh I (amplifier 0) ~5 giây (CE gốc: Sức mạnh IV mọi đòn). Sàn kính tạm y 150 đất hoang.
const h = require('./h');
const X = 13200, Y = 150, Z = -2900;
(async () => {
  let A = await h.start('ThuA'); const R = c => A.rc.cmd(c);
  await R(`forceload add ${X - 6} ${Z - 6} ${X + 6} ${Z + 6}`); await A.sleep(1500);
  await R(`fill ${X - 3} ${Y} ${Z - 4} ${X + 3} ${Y} ${Z + 3} minecraft:glass`);
  await R(`minecraft:tp ThuA ${X + 0.5} ${Y + 1} ${Z + 0.5} 180 0`); await A.end(); await A.sleep(1500); A = await h.start('ThuA'); await A.sleep(1500);
  await R('minecraft:clear ThuA'); await R('effect clear ThuA'); await R('dmm laydo thu_execute ThuA 1'); await A.sleep(1000);
  await A.bot.equip(A.bot.inventory.items()[0], 'hand'); await A.sleep(1500);
  const eff = async () => (await R('data get entity ThuA active_effects')).replace(/.*data: /, '').slice(0, 160);
  for (const [label, hp] of [['husk đầy máu (100%)', 200], ['husk còn 20% máu', 40]]) {
    await R(`minecraft:kill @e[type=husk,x=${X},y=${Y},z=${Z},distance=..20]`); await R('effect clear ThuA');
    await R(`summon minecraft:husk ${X + 0.5} ${Y + 1} ${Z - 2.5} {NoAI:1b,Health:${hp}f,attributes:[{id:"minecraft:max_health",base:200}],PersistenceRequired:1b}`); await A.sleep(1000);
    const e = A.bot.nearestEntity(x => x.name === 'husk'); await A.bot.lookAt(e.position.offset(0, 1.5, 0), true); A.bot.attack(e); await A.sleep(400);
    A.log(label, '-> hiệu ứng của ThuA:', await eff());
  }
  await R(`minecraft:kill @e[type=husk,x=${X},y=${Y},z=${Z},distance=..20]`); await R('minecraft:clear ThuA'); await R('effect clear ThuA');
  await R(`minecraft:tp ThuA ${X} 70 ${Z}`); await R(`fill ${X - 3} ${Y} ${Z - 4} ${X + 3} ${Y} ${Z + 3} minecraft:air replace minecraft:glass`); await R(`forceload remove ${X - 6} ${Z - 6} ${X + 6} ${Z + 6}`);
  await A.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
