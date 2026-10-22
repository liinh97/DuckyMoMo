// n10 AuraSkills: đào quặng than ở đất hoang (lên kinh nghiệm Đào mỏ), ở vùng spawn WorldGuard và Thái Ấp (không lên),
// tự đặt rồi phá (không lên: check_block_replace). Kinh nghiệm đọc bằng papi %auraskills_xp_mining% lúc bot online.
const { start, moveTo } = require('./h');
const N = process.argv[2] || 'ThuA';   // ThuA có đất Thái Ấp (a-thaiap.js)
(async () => {
  let t = await start(N);
  const R = c => t.rc.cmd(c);
  await R(`lp user ${N} permission set grim.exempt true`); await R(`gamemode survival ${N}`);
  const xp = async () => (await R(`papi parse ${N} %auraskills_xp_mining% / cấp %auraskills_mining%`)).trim();
  const digHere = async (label, place = false, dim = 'minecraft:overworld') => {
    await R(`minecraft:item replace entity ${N} weapon.mainhand with minecraft:diamond_pickaxe`); t.bot.setQuickBarSlot(0); await t.sleep(400);
    const p = t.bot.entity.position.floored(); const bx = p.x + 1, by = p.y, bz = p.z;
    const x0 = await xp();
    for (let i = 0; i < 5; i++) {
      if (place) {
        await R(`execute in ${dim} run setblock ${bx} ${by} ${bz} minecraft:air`);
        await R(`minecraft:item replace entity ${N} hotbar.1 with minecraft:coal_ore 1`); t.bot.setQuickBarSlot(1); await t.sleep(300);
        const below = t.bot.blockAt(new (require('vec3'))(bx, by - 1, bz));
        t.bot._client.write('block_place', { location: below.position, direction: 1, hand: 0, cursorX: 0.5, cursorY: 1, cursorZ: 0.5, insideBlock: false, sequence: 10 + i, worldBorderHit: false });
        await t.sleep(600); t.bot.setQuickBarSlot(0); await t.sleep(300);
      } else await R(`execute in ${dim} run setblock ${bx} ${by} ${bz} minecraft:coal_ore`);
      await t.sleep(300);
      const loc = { x: bx, y: by, z: bz };
      t.bot._client.write('block_dig', { status: 0, location: loc, face: 1, sequence: 100 + i * 2 }); await t.sleep(150);
      t.bot._client.write('block_dig', { status: 2, location: loc, face: 1, sequence: 101 + i * 2 }); await t.sleep(700);
    }
    const gone = /passed/.test(await R(`execute in ${dim} if block ${bx} ${by} ${bz} minecraft:air`));
    t.log(`n10 ${label}: kinh nghiệm Đào mỏ ${x0} -> ${await xp()} | khối đã phá: ${gone}`);
  };
  t = await moveTo(t, 13200, -2900); await digHere('đất hoang (setblock = quặng tự nhiên, 5 lần)');
  await digHere('đất hoang, TỰ ĐẶT quặng rồi phá (5 lần)', true);
  t = await moveTo(t, 12690, -2520); await digHere('vùng spawn (WorldGuard spawn)');
  t.log('  /thaiap:', (await t.say('/thaiap', 2500)).join(' | ').slice(0, 150));
  await t.end(); await t.sleep(2500); t = await start(N); await t.sleep(2000);
  t.log('  đang ở:', (await R(`data get entity ${N} Dimension`)).replace(/.*data: /, ''), t.bot.entity.position.floored());
  await digHere('Thái Ấp (đất của mình)', false, 'minecraft:thaiap');
  await R(`lp user ${N} permission unset grim.exempt`); await R(`minecraft:clear ${N}`); await R(`spawn ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
