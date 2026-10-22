// Sau khi có PerkCleanup: cấp quyền tạm 40 giây, dùng nick màu + /hat + /pp, ĐỨNG ONLINE chờ hết hạn + 40 giây, xem đã được dọn chưa.
const h = require('./h');
const N = 'ThuVang';
const PERMS = ['essentials.hat', 'essentials.chat.color', 'essentials.nick', 'essentials.nick.color', 'playerparticles.particles.max.1', 'playerparticles.effect.*', 'playerparticles.style.*'];
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c); await t.sleep(2500);
  const nick = async () => ((await R(`whois ${N}`)).match(/Biệt danh[^\n]*/i) || ['(không)'])[0].trim();
  const head = async () => (await R(`data get entity ${N} equipment.head.id`)).replace(/.*data: /, '');
  const pumpkins = async () => (await R(`minecraft:clear ${N} minecraft:pumpkin 0`)).replace(/.*Found /, '');
  for (const p of PERMS) await R(`lp user ${N} permission settemp ${p} true 40s`);
  await t.sleep(2500);
  t.bot.chat('/nick &aNickThu'); await t.sleep(1000);
  await R(`minecraft:clear ${N}`); await R(`minecraft:give ${N} minecraft:pumpkin`); await t.sleep(800); await t.bot.equip(t.bot.inventory.items()[0], 'hand'); await t.sleep(300);
  t.bot.chat('/hat'); await t.sleep(1000); t.bot.chat('/pp add flame normal'); await t.sleep(1500);
  t.log('lúc còn quyền: nick', await nick(), '| đầu', await head());
  const n0 = t.chat.length; await t.sleep(80000);
  t.log('sau hết hạn ~40 giây: nick', await nick(), '| đầu', await head(), '| bí ngô trong túi:', await pumpkins());
  t.log('báo:', t.chat.slice(n0).filter(x => /hết hạn/.test(x)).join(' / '));
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
