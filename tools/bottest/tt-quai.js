const h = require('./h');
const N = process.env.BOTN || 'TanThuX';
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  await t.sleep(3000);
  for (let i = 0; i < 3; i++) {
    t.log('summon', await R(`execute at ${N} run summon minecraft:zombie ^ ^ ^2 {NoAI:1b,Tags:["tt"],ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}`)); await t.sleep(1000);
    const z = t.bot.nearestEntity(e => e.name === 'zombie');
    t.log('thấy zombie:', !!z, z && z.position.distanceTo(t.bot.entity.position).toFixed(1));
    t.log('damage', await R(`damage @e[tag=tt,limit=1,sort=nearest] 100000 minecraft:player_attack by ${N}`)); await t.sleep(1500);
    t.log('còn', await R(`execute if entity @e[tag=tt]`));
  }
  t.log(await R(`data get entity ${N} Pos`));
  const n = []; t.bot._client.on('system_chat', p => n.push(JSON.stringify(p).slice(0, 200)));
  t.bot.chat('/tanthu'); await t.sleep(1500); t.log(n.join('\n').slice(0,1500));
  await R(`kill @e[tag=tt]`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
