// w11 vũ khí nổ lên người chơi (ExplosionRules, Cài đặt boom_*): cùng khoảng cách 3 khối, đo máu mất (bot max_health 200, không giáp, không kháng):
// TNT thường (không đổi) · xe goòng TNT (×0,3) · end crystal (×0) · respawn anchor nổ ở Overworld (×0) · giường nổ ở Nether (×0).
const { start, moveTo } = require('./h');
const N = 'ThuW11';
(async () => {
  let t = await start(N);
  const R = c => t.rc.cmd(c);
  await R(`lp user ${N} permission set grim.exempt true`); await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`);
  await R(`effect clear ${N}`);
  const hp = async () => +((await R(`data get entity ${N} Health`)).match(/([\d.]+)f/) || [0, 0])[1];
  const full = async () => { await R(`attribute ${N} minecraft:max_health base set 200`); await R(`effect give ${N} minecraft:instant_health 1 28 true`); await t.sleep(1200); await R(`effect clear ${N}`); };
  const blast = async (label, setup, after) => {
    await full(); const h0 = await hp();
    await R(`execute at ${N} run ${setup}`); await t.sleep(300);
    if (after) await after(); await t.sleep(2500);
    const h1 = await hp();
    t.log(`w11 ${label}: máu ${h0} -> ${h1} (mất ${(h0 - h1).toFixed(1)})`);
    return h0 - h1;
  };
  t = await moveTo(t, 13250, -2950);
  const tnt = await blast('TNT thường (đối chứng)', 'summon tnt ~3 ~ ~ {fuse:1}');
  const cart = await blast('xe goòng TNT', 'summon tnt_minecart ~3 ~ ~ {fuse:1,Tags:["thuw11c"]}');
  t.log('  xe goòng còn (chưa nổ)?', (await R(`execute if entity @e[type=tnt_minecart,tag=thuw11c]`)).trim());
  await R('minecraft:kill @e[type=tnt_minecart,tag=thuw11c]');
  await R(`execute at ${N} run fill ~2 ~-1 ~-1 ~4 ~ ~1 minecraft:air replace minecraft:activator_rail`);
  t.log(`  tỉ lệ xe goòng / TNT = ${(cart / tnt).toFixed(2)} (Cài đặt 0,3; TNT và xe goòng sức nổ gần như nhau)`);
  await blast('end crystal (lệnh /damage)', 'summon end_crystal ~3 ~ ~ {Tags:["thuw11"]}', () => R(`execute at ${N} run damage @e[type=end_crystal,tag=thuw11,limit=1] 1`));
  await blast('end crystal (bot tự đánh)', 'summon end_crystal ~3 ~ ~ {Tags:["thuw11"],ShowBottom:0b}', async () => { await t.sleep(600); const c = t.bot.nearestEntity(e => e.name === 'end_crystal'); if (c) { await t.bot.lookAt(c.position.offset(0, 1, 0), true); t.bot.attack(c); } else t.log('  không thấy crystal'); });
  // respawn anchor đủ 4 sạc ở Overworld: bot chuột phải (tay không) -> nổ
  const p = t.bot.entity.position.floored();
  await R(`setblock ${p.x + 3} ${p.y} ${p.z} minecraft:respawn_anchor[charges=4]`); await t.sleep(600);
  await full(); const h0 = await hp();
  await t.bot.lookAt(p.offset(3.5, 0.5, 0.5), true);
  t.bot._client.write('block_place', { location: { x: p.x + 3, y: p.y, z: p.z }, direction: 4, hand: 0, cursorX: 0.1, cursorY: 0.5, cursorZ: 0.5, insideBlock: false, sequence: 50, worldBorderHit: false });
  await t.sleep(2500);
  const gone = /passed/.test(await R(`execute if block ${p.x + 3} ${p.y} ${p.z} minecraft:air`));
  t.log(`w11 respawn anchor (bot bấm): đã nổ ${gone} | máu ${h0} -> ${await hp()}`);
  await R(`setblock ${p.x + 3} ${p.y} ${p.z} minecraft:air`);
  // giường ở Nether
  t = await moveTo(t, 1650, -360, 'minecraft:the_nether');
  const q = t.bot.entity.position.floored();
  await R(`execute in minecraft:the_nether run setblock ${q.x + 2} ${q.y} ${q.z} minecraft:red_bed[facing=east,part=foot]`);
  await R(`execute in minecraft:the_nether run setblock ${q.x + 3} ${q.y} ${q.z} minecraft:red_bed[facing=east,part=head]`); await t.sleep(800);
  await full(); const g0 = await hp();
  await t.bot.lookAt(q.offset(2.5, 0.3, 0.5), true);
  t.bot._client.write('block_place', { location: { x: q.x + 2, y: q.y, z: q.z }, direction: 1, hand: 0, cursorX: 0.5, cursorY: 0.5, cursorZ: 0.5, insideBlock: false, sequence: 60, worldBorderHit: false });
  await t.sleep(2500);
  const bedGone = /passed/.test(await R(`execute in minecraft:the_nether if block ${q.x + 2} ${q.y} ${q.z} minecraft:air`));
  t.log(`w11 giường ở Nether (bot bấm): đã nổ ${bedGone} | máu ${g0} -> ${await hp()}`);
  await R(`attribute ${N} minecraft:max_health base set 20`); await R(`lp user ${N} permission unset grim.exempt`); await R(`spawn ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
