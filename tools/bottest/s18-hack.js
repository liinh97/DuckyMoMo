// s18 hack client cơ bản (GrimAC): bot KHÔNG có grim.exempt, ở đất hoang. Bay (đẩy toạ độ lên 0,6 khối/tick), đi nhanh (2 khối/tick),
// đánh xa (zombie cách 7 khối). Đo toạ độ phía server + máu zombie; wrapper đọc log Grim.
const { start, moveTo } = require('./h');
const N = 'ThuS18';
(async () => {
  let t = await start(N, '10.9.4.18');
  const R = c => t.rc.cmd(c);
  await R(`lp user ${N} permission unset grim.exempt`); await R(`gamemode survival ${N}`); await t.sleep(3000);
  t = await moveTo(t, 13200, -2900, 'minecraft:overworld', '10.9.4.18');
  await t.sleep(3000);
  const spos = async () => (await R(`data get entity ${N} Pos`)).replace(/.*data: /, '').replace(/(\.\d)\d+d/g, '$1');
  const cheat = async (label, dx, dy) => {
    const before = await spos();
    for (let i = 0; i < 20; i++) { t.bot.entity.position.x += dx; t.bot.entity.position.y += dy; t.bot.entity.velocity.y = 0; await t.sleep(50); }
    await t.sleep(1500);
    t.log(`s18 ${label}: server ${before} -> ${await spos()} | bot tự nghĩ ở ${t.bot.entity.position.floored()}`);
  };
  await cheat('BAY (lên 12 khối trong 1 giây)', 0, 0.6);
  await t.sleep(2000);
  await cheat('ĐI NHANH (40 khối trong 1 giây)', 2, 0);
  await t.sleep(2000);
  await R(`execute at ${N} run summon zombie ~7 ~ ~ {NoAI:1b,Tags:["thus18"],PersistenceRequired:1b}`); await t.sleep(1500);
  const z = t.bot.nearestEntity(e => e.name === 'zombie');
  const h0 = (await R('data get entity @e[tag=thus18,limit=1] Health')).replace(/.*data: /, '');
  if (z) { for (let i = 0; i < 3; i++) { t.bot._client.write('use_entity', { target: z.id, mouse: 1, sneaking: false }); t.bot.swingArm('right'); await t.sleep(600); } }
  t.log(`s18 ĐÁNH XA 7 khối: zombie ${z ? 'thấy' : 'KHÔNG thấy'} | máu ${h0} -> ${(await R('data get entity @e[tag=thus18,limit=1] Health')).replace(/.*data: /, '')}`);
  await R('minecraft:kill @e[tag=thus18]');
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e.message); process.exit(1); });
