// Bấm chuột phải NPC Citizens (BetonQuest): bot tới cạnh NPC (TO="x y z"), bấm NPC tên khớp NAME; đọc log debug BetonQuest bên ngoài.
const h = require('./h');
const N = process.env.BOTN || 'ThuNpc';
(async () => {
  let t = await h.start(N);
  await t.rc.cmd(`minecraft:tp ${N} ${process.env.TO}`); await t.end(); await t.sleep(2500); t = await h.start(N); await t.sleep(2500);
  const re = new RegExp(process.env.NAME);
  const npc = Object.values(t.bot.entities).filter(e => e.type === 'player' && re.test(e.username || ''))[0];
  t.log('## thấy NPC:', npc ? npc.username + '@' + npc.position.floored() : 'KHÔNG');
  if (npc) { await t.bot.lookAt(npc.position.offset(0, 1.6, 0)); await t.use(npc); await t.sleep(2500); }
  t.log('## cửa sổ hội thoại:', t.bot.currentWindow ? 'có' : 'không');
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
