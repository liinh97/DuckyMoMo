// Đưa bot ra khỏi dungeon EliteMobs: thử /em quit, rồi /spawn; in thế giới.
const h = require('./h');
(async () => {
  const t = await h.start(process.env.BOTN || 'ThuR1'); const R = c => t.rc.cmd(c); const N = t.bot.username;
  const dim = async () => (await R(`data get entity ${N} Dimension`)).replace(/.*data: /, '');
  t.log('đang ở', await dim());
  for (const c of ['/em quit', '/em leave', '/spawn']) { if (!/em_/.test(await dim())) break; t.log(c, '->', (await t.say(c, 7000)).join(' / ').slice(0, 150), '|', await dim()); }
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
