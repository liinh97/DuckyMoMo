// (1) hết hạn lúc OFFLINE rồi vào lại; (2) chỉ hết quyền nick màu (còn quyền nick) -> bỏ màu, giữ chữ.
const h = require('./h');
const N = 'ThuVang', step = process.argv[2];
const PERMS = ['essentials.hat', 'essentials.nick', 'essentials.nick.color', 'playerparticles.particles.max.1', 'playerparticles.effect.*', 'playerparticles.style.*'];
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c); await t.sleep(2500);
  const nick = async () => ((await R(`whois ${N}`)).match(/Biệt danh[^\n]*/i) || ['(không)'])[0].trim();
  const head = async () => (await R(`data get entity ${N} equipment.head.id`)).replace(/.*data: /, '');
  if (step === 'offline1') {
    for (const p of PERMS) await R(`lp user ${N} permission settemp ${p} true 25s`);
    await t.sleep(2500); t.bot.chat('/nick &aNickThu'); await t.sleep(1000);
    await R(`minecraft:clear ${N}`); await R(`minecraft:give ${N} minecraft:pumpkin`); await t.sleep(800); await t.bot.equip(t.bot.inventory.items()[0], 'hand'); await t.sleep(300);
    t.bot.chat('/hat'); await t.sleep(1000); t.bot.chat('/pp add flame normal'); await t.sleep(1500);
    t.log('[offline1] còn quyền, thoát game: nick', await nick(), '| đầu', await head());
  } else if (step === 'offline2') {
    await t.sleep(5000); t.log('[offline2] vào lại sau khi hết hạn: nick', await nick(), '| đầu', await head(), '| báo:', t.chat.filter(x => /hết hạn/.test(x)).join(' / '));
    await R(`minecraft:clear ${N}`);
  } else if (step === 'mau') {
    await R(`lp user ${N} permission settemp essentials.nick true 120s`); await R(`lp user ${N} permission settemp essentials.nick.color true 20s`);
    await t.sleep(2500); t.bot.chat('/nick &aXanhLa'); await t.sleep(1200); t.log('[mau] còn quyền màu:', await nick());
    await t.sleep(55000); t.log('[mau] hết quyền màu, còn quyền nick:', await nick(), '| báo:', t.chat.filter(x => /màu đã hết hạn/.test(x)).join(' / '));
    t.log('[mau] (giữ nick để đọc file)');
  }
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
