// Sát thương lên quái tinh nhuệ EliteMobs (23/10): cùng zombie tinh nhuệ cấp LV, mỗi vũ khí chém 3 đòn, đo máu quái mất.
// So đồ EliteMobs rơi trong dungeon với đồ set Thợ Rèn (đồ trao đổi phải mạnh hơn đồ rơi).
const h = require('./h');
const N = process.env.BOTN || 'ThuDmg';
const LV = process.env.LV || '10';
const WEAPONS_ALL = [
  ['em', 'the_climb_sword_normal', 'Kiếm EliteMobs (Đỉnh Vách Đá)'],
  ['em', 'the_climb_undead_drop_sword_dps_normal', 'Kiếm trùm EliteMobs (Đỉnh Vách Đá)'],
  ['mc', 'diamond_sword', 'Kiếm kim cương thường'],
  ['lib', 'tt_kiem_0', 'Kiếm Tân Thủ +0'], ['lib', 'tt_kiem_9', 'Kiếm Tân Thủ +9'], ['lib', 'cb_kiem_7', 'Kiếm Chiến Binh +7'],
  ['lib', 'ta_kiem_5', 'Kiếm Tinh Anh +5'], ['lib', 'cc_kiem_5', 'Kiếm Cao Cấp +5'],
];
const WEAPONS = process.env.DEBUG ? WEAPONS_ALL.slice(2, 3) : WEAPONS_ALL;
(async () => {
  let t = await h.start(N); let R = c => t.rc.cmd(c);
  await R(`lp user ${N} parent add admin`); await t.sleep(2500);
  t = await h.moveTo(t, 13300, -2400); R = c => t.rc.cmd(c);   // bot qua Via không theo lệnh tp: thoát rồi vào lại đúng chỗ
  await R('forceload add 13296 -2404 13304 -2396');
  await R(`effect give ${N} minecraft:resistance 600 4 true`); await t.sleep(3000);
  const hp = async () => parseFloat((await R(`data get entity @e[tag=dmgt,limit=1] Health`)).replace(/.*data: /, '')) || NaN;
  for (const [kind, id, label] of WEAPONS) {
    await R(`minecraft:clear ${N}`); await t.sleep(400);
    if (kind === 'em') await R(`em loot give ${N} ${id}`); else if (kind === 'lib') await R(`dmm laydo ${id} 1 ${N}`); else await R(`minecraft:give ${N} minecraft:${id} 1`);
    await t.sleep(1500);
    const w = t.bot.inventory.items()[0]; if (!w) { t.log(label, ': không có vũ khí'); continue; }
    await t.bot.equip(w, 'hand'); await t.sleep(2500);   // đợi hết hồi đòn sau khi đổi đồ (hệ tốc đánh)
    const held = t.bot.heldItem ? t.bot.heldItem.name : 'tay không';
    const res = [];
    for (let k = 0; k < 3; k++) {
      await R(`execute at ${N} run minecraft:kill @e[type=!minecraft:player,type=!minecraft:item,distance=..60]`); await R('minecraft:kill @e[tag=dmgt]'); await t.sleep(400);
      t.bot.chat(`/em spawn elite ZOMBIE ${LV}`); await t.sleep(1500);
      await R(`execute at ${N} as @e[type=minecraft:zombie,distance=..60] if data entity @s CustomName run minecraft:tag @s add dmgt`);
      await R(`execute as @e[tag=dmgt] run data merge entity @s {NoAI:1b}`);
      await R(`execute as @e[tag=dmgt] at ${N} run minecraft:tp @s ^ ^ ^1.6`); await t.sleep(1500);
      const z = Object.values(t.bot.entities).filter(e => e.name === 'zombie').sort((a, b) => a.position.distanceTo(t.bot.entity.position) - b.position.distanceTo(t.bot.entity.position))[0];
      const h0 = await hp(); const maxhp = parseFloat((await R(`attribute @e[tag=dmgt,limit=1] minecraft:max_health get`)).replace(/.*is /, ''));
      if (!z || isNaN(h0)) { res.push(NaN); continue; }
      await t.bot.lookAt(z.position.offset(0, 1.4, 0)); t.bot.attack(z); await t.sleep(1200);
      const h1 = await hp();
      res.push(100 * (isNaN(h1) ? h0 : h0 - h1) / maxhp);
      await t.sleep(1500);
    }
    const sorted = res.filter(x => !isNaN(x)).sort((a, b) => a - b);
    t.log(`## cấp ${LV} · ${label.padEnd(34)} cầm ${held.padEnd(16)} 1 đòn: ${res.map(x => isNaN(x) ? '?' : x.toFixed(0) + '%').join(' / ')} → giữa ${sorted.length ? sorted[Math.floor(sorted.length / 2)].toFixed(0) : '?'}% máu`);
  }
  await R(`execute at ${N} run minecraft:kill @e[type=minecraft:zombie,distance=..20]`); await R('forceload remove 13296 -2404 13304 -2396');
  await R(`lp user ${N} parent remove admin`); await R(`minecraft:clear ${N}`); await R(`effect clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
