// Thử kiểu gói chuột phải vào NPC Citizens qua Via: node npc-probe.js <x> <y> <z>
const { start } = require('./h');
const [x, y, z] = process.argv.slice(2, 5).map(Number);
(async () => {
  let t = await start('ThuQ7', '10.9.0.71');
  await t.rc.cmd(`minecraft:tp ThuQ7 ${x + 2} ${y} ${z}`); await t.end(); await t.sleep(2500);
  t = await start('ThuQ7', '10.9.0.71'); await t.sleep(1500);
  const npc = t.bot.nearestEntity(e => e.type === 'player' && e.username !== 'ThuQ7');
  t.log('NPC:', npc && npc.username, npc && npc.position);
  await t.bot.lookAt(npc.position.offset(0, 1.6, 0), true);
  for (const [label, pk] of [['interact mouse 0', { target: npc.id, mouse: 0, hand: 0, sneaking: false }], ['interact_at + interact', null]]) {
    const n0 = t.chat.length;
    if (pk) t.bot._client.write('use_entity', pk);
    else { t.bot._client.write('use_entity', { target: npc.id, mouse: 2, x: 0, y: 1, z: 0, hand: 0, sneaking: false }); t.bot._client.write('use_entity', { target: npc.id, mouse: 0, hand: 0, sneaking: false }); }
    await t.sleep(2500);
    t.log(label, '->', t.chat.slice(n0).join(' | ').slice(0, 400));
  }
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
