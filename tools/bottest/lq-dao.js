// Đối chứng: đào khối thường cạnh NPC (phải mất) và khối dưới chân NPC (phải còn).
const { start, moveTo } = require('./h');
const N = process.env.BOTN || 'ThuLamQuen';
(async () => {
  let t = await start(N);
  const R = x => t.rc.cmd(x);
  t = await moveTo(t, 12274, -2229);
  await R(`gamemode survival ${N}`); await R(`minecraft:give ${N} minecraft:diamond_pickaxe 1`); await t.sleep(800);
  t.bot.on('actionBar', m => t.log('ACTIONBAR', String(m)));
  const npc = Object.values(t.bot.entities).find(e => e.type === 'player' && /Cống/.test(e.username || ''));
  const under = npc.position.floored().offset(0, -1, 0);
  const other = under.offset(2, 0, 0);
  for (const [lb, pos] of [['khối thường', other], ['dưới chân NPC', under]]) {
    const b = t.bot.blockAt(pos);
    await t.bot.equip(t.bot.inventory.items().find(i => i.name === 'diamond_pickaxe'), 'hand').catch(() => {});
    try { await t.bot.dig(b, true); } catch (e) { t.log('dig err', e.message); }
    await t.sleep(1500);
    t.log(lb, pos, b.name, '->', (await R(`execute in minecraft:overworld run data get block ${pos.x} ${pos.y} ${pos.z}`)).slice(0, 80), '|', t.bot.blockAt(pos).name);
  }
  await R(`minecraft:clear ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
