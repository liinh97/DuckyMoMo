// Bấm NPC Thợ Rèn gần nhất (TO="x y z", NAME regex), xem có mở menu không.
const h = require('./h');
const N = process.env.BOTN || 'ThuNpc';
(async () => {
  let t = await h.start(N);
  await t.rc.cmd(`minecraft:tp ${N} ${process.env.TO}`); await t.end(); await t.sleep(2500); t = await h.start(N); await t.sleep(2500);
  const re = new RegExp(process.env.NAME);
  const me = t.bot.entity.position;
  const npc = Object.values(t.bot.entities).filter(e => e.type === 'player' && re.test(e.username || '')).sort((a, b) => a.position.distanceTo(me) - b.position.distanceTo(me))[0];
  t.log('## thấy NPC:', npc ? npc.username + '@' + npc.position.floored() : 'KHÔNG');
  let title = null; t.bot.once('windowOpen', w => { title = w.title; });
  if (npc) { await t.bot.lookAt(npc.position.offset(0, 1.6, 0)); await t.use(npc); await t.sleep(2500); }
  t.log('## menu:', title ? JSON.stringify(title).slice(0, 120) : 'không mở');
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
