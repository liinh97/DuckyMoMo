// Tìm vì sao bí ngô đội bằng /hat biến mất (rank-hethan.js 22/10): đội rồi theo dõi đầu + túi qua /spawn và khi hết quyền.
const h = require('./h');
const N = 'ThuR1';
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c); await t.sleep(2500);
  const st = async l => t.log(l, '| đầu:', (await R(`data get entity ${N} equipment.head.id`)).replace(/.*data: /, ''), '| túi bí ngô:', (await R(`minecraft:clear ${N} minecraft:pumpkin 0`)).replace(/.*Found /, ''));
  await R(`lp user ${N} permission settemp essentials.hat true 45s`); await t.sleep(2500);
  await R(`minecraft:clear ${N}`); await R(`minecraft:give ${N} minecraft:pumpkin`); await t.sleep(800); await t.bot.equip(t.bot.inventory.items()[0], 'hand'); await t.sleep(300);
  t.log((await t.say('/hat')).join(' / ')); await st('sau /hat');
  await R(`minecraft:tp ${N} 12700 80 -2400`); await t.sleep(1500); await st('sau tp rcon');
  t.bot.chat('/spawn'); await t.sleep(9000); await st('sau /spawn');
  const n0 = t.chat.length;
  for (let i = 0; i < 9; i++) { await t.sleep(10000); await st(`+${(i + 1) * 10}s`); }
  t.log('chat:', t.chat.slice(n0).join(' / ').slice(0, 400));
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
