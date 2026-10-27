// Làm quen tính năng: /quests = /nhiemvu, /sell lưu ly (việc "ban"), /diemdanh, rồi phá khối dưới chân lữ khách Thợ Cống Già.
const { start, moveTo } = require('./h');
const N = process.env.BOTN || 'ThuLamQuen';
(async () => {
  let t = await start(N);
  const R = x => t.rc.cmd(x);
  await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`); await R(`eco set ${N} 0`);
  t.log('--- /quests:\n' + (await t.say('/quests', 2000)).filter(l => !/^▏|Bang Hội Chiến\s*$/.test(l)).join('\n').slice(0, 2500));
  await R(`minecraft:give ${N} minecraft:lapis_lazuli 10`); await t.sleep(400);
  t.log('--- /sell lapis_lazuli:', (await t.say('/sell lapis_lazuli', 2500)).join(' | ').slice(0, 300));
  t.log('--- /diemdanh:', (await t.say('/diemdanh', 2500)).join(' | ').slice(0, 400));
  t.log('bal', await R(`bal ${N}`));
  t = await moveTo(t, 12274, -2230);
  const npc = Object.values(t.bot.entities).find(e => e.type === 'player' && /Cống/.test(e.username || ''));
  t.log('NPC', npc && npc.username, npc && npc.position);
  if (npc) {
    const below = t.bot.blockAt(npc.position.offset(0, -0.5, 0));
    t.log('khối dưới chân', below && below.name, below && below.position);
    await R(`minecraft:give ${N} minecraft:diamond_pickaxe 1`); await R(`minecraft:give ${N} minecraft:diamond_shovel 1`); await t.sleep(500);
    const n = t.chat.length;
    try { await t.bot.dig(below, true); } catch (e) { t.log('dig err', e.message); }
    await t.sleep(1500);
    const after = t.bot.blockAt(below.position);
    t.log('sau khi đào:', after && after.name, '| tin:', t.since(n).join(' | ').slice(0, 200));
  }
  await R(`minecraft:clear ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
