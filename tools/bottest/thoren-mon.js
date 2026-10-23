// Độ mòn theo cấp (23/10): Mũ Tân Thủ +0 (wear 100%) và Giày +9 (45%) cùng nhận 80 đòn -> giày mòn khoảng một nửa.
const h = require('./h');
const N = process.env.BOTN || 'ThuRen';
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  await R(`minecraft:clear ${N}`); await R(`dmm laydo tt_mu_0 1 ${N}`); await R(`dmm laydo tt_giay_9 1 ${N}`); await t.sleep(1500);
  for (const [re, d] of [[/^Mũ/, 'head'], [/^Giày/, 'feet']]) { const it = t.bot.inventory.items().find(i => re.test(t.itemText(i).name)); if (it) await t.bot.equip(it, d); await t.sleep(400); }
  await R(`effect give ${N} minecraft:instant_health 100 10 true`);
  for (let i = 0; i < 80; i++) { await R(`damage ${N} 2 minecraft:mob_attack`); if (i % 10 === 9) await R(`effect give ${N} minecraft:instant_health 1 5 true`); await t.sleep(550); }
  const dmg = async s => (await R(`data get entity ${N} equipment.${s}.components."minecraft:damage"`)).replace(/.*data: /, '');
  t.log('## sau 80 đòn: Mũ +0 mất', await dmg('head'), '| Giày +9 mất', await dmg('feet'));
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
