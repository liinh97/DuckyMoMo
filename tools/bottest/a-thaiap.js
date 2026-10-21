// a2–a4 Thái Ấp: ThuA mua đất; ThuF (người lạ) phá/đặt/mở rương/đánh thú/đánh A bị chặn; mời rồi ThuF xây được;
// ghé, thích, mở rộng; bếp: nấu Cơm nắm trong đất mình có dòng "Nấu ở bếp Thái Ấp". Cả 2 bot có grim.exempt (đặt khối).
const { start } = require('./h');
const A = 'ThuA', F = 'ThuF';
const rejoin = async (t, name) => { await t.end(); await t.sleep(2500); const t2 = await start(name); await t2.sleep(1500); return t2; };
(async () => {
  let a = await start(A);
  const R = c => a.rc.cmd(c);
  const msgs = {};
  const hook = (t, n) => { msgs[n] = []; t.bot.on('messagestr', m => msgs[n].push(m)); t.bot._client.on('action_bar', p => { const x = t.strip(JSON.stringify(p.text)); if (!x.includes('❤ [')) msgs[n].push('[bar] ' + x.replace(/"type":"[a-z]+",|"value":|[{}\[\]"]|text:|extra:/g, '').slice(0, 120)); }); };
  const since = (n, k) => msgs[n].slice(k).join(' | ');
  await R(`eco set ${A} 60000`); await R(`gamemode survival ${A}`); await R(`gamemode survival ${F}`);
  a.log('a1 mua:', (await a.say('/thaiap mua', 3000)).join(' | '));
  a = await rejoin(a, A); hook(a, A);
  const p = a.bot.entity.position.floored();
  a.log('A ở thaiap:', a.bot.game.dimension, p, 'dưới chân', (a.bot.blockAt(p.offset(0, -1, 0)) || {}).name);
  const X = p.x, Y = p.y, Z = p.z;
  await R(`execute in minecraft:thaiap run setblock ${X + 2} ${Y} ${Z} minecraft:chest`);
  await R(`execute in minecraft:thaiap run summon cow ${X} ${Y} ${Z + 2} {NoAI:1b,Tags:["thuta"]}`);
  await R(`execute in minecraft:thaiap run setblock ${X - 2} ${Y - 1} ${Z} minecraft:stone`);
  let f = await start(F); await R(`minecraft:tp ${F} ${A}`); f = await rejoin(f, F); hook(f, F);
  await R(`minecraft:clear ${F}`); await R(`minecraft:give ${F} minecraft:dirt 16`); await R(`minecraft:give ${F} minecraft:iron_pickaxe 1`);
  await f.sleep(1000);
  const blk = (dx, dy, dz) => f.bot.blockAt(f.bot.entity.position.floored().offset(dx, dy, dz));
  const isBlock = async (x, y, z, b) => /passed/.test(await R(`execute in minecraft:thaiap if block ${x} ${y} ${z} ${b}`));
  const tryAll = async (tag, t, n) => {
    const k = msgs[n].length;
    // phá đá
    const st = t.bot.blockAt(new (require('vec3'))(X - 2, Y - 1, Z));
    await t.bot.equip(t.bot.inventory.items().find(i => i.name === 'iron_pickaxe'), 'hand').catch(() => {});
    await t.bot.lookAt(st.position.offset(0.5, 0.5, 0.5), true);
    await t.bot.dig(st, true).catch(e => t.log('  dig lỗi', e.message.slice(0, 50)));
    await t.sleep(800);
    const broke = !(await isBlock(X - 2, Y - 1, Z, 'minecraft:stone'));
    // đặt đất lên đá/cỏ cạnh chân
    const base = t.bot.blockAt(new (require('vec3'))(X - 1, Y - 1, Z - 1));
    await t.bot.equip(t.bot.inventory.items().find(i => i.name === 'dirt'), 'hand').catch(() => {});
    await t.bot.placeBlock(base, new (require('vec3'))(0, 1, 0)).catch(e => {});
    await t.sleep(800);
    const placed = await isBlock(X - 1, Y, Z - 1, 'minecraft:dirt');
    // mở rương
    let opened = false;
    try { const ch = t.bot.blockAt(new (require('vec3'))(X + 2, Y, Z)); const w = await Promise.race([t.bot.openContainer(ch), t.sleep(2500).then(() => null)]); opened = !!w; if (w) w.close(); } catch (e) {}
    // đánh bò
    const cow = t.bot.nearestEntity(e => e.name === 'cow');
    const cow0 = (await R('data get entity @e[type=cow,tag=thuta,limit=1] Health')).replace(/.*data: /, '');
    if (cow) { await t.bot.lookAt(cow.position.offset(0, 1, 0), true); t.bot.attack(cow); await t.sleep(800); }
    const cow1 = (await R('data get entity @e[type=cow,tag=thuta,limit=1] Health')).replace(/.*data: /, '');
    t.log(`${tag}: phá đá=${broke} đặt đất=${placed} mở rương=${opened} bò ${cow0}->${cow1} | tin: ${since(n, k).slice(0, 300)}`);
    return { broke, placed, opened };
  };
  await tryAll('a2 NGƯỜI LẠ', f, F);
  // đánh A
  const h0 = (await R(`data get entity ${A} Health`)).replace(/.*data: /, '');
  const ea = f.bot.players[A] && f.bot.players[A].entity;
  if (ea) { await f.bot.lookAt(ea.position.offset(0, 1.5, 0), true); f.bot.attack(ea); await f.sleep(800); }
  a.log('a2 F đánh A: máu A', h0, '->', (await R(`data get entity ${A} Health`)).replace(/.*data: /, ''), ea ? '' : '(bot không thấy A)');
  // quái
  a.log('a2 quái trong thaiap:', await R('execute in minecraft:thaiap run execute if entity @e[type=#minecraft:undead]'), '| gamerule doMobSpawning:', await R('execute in minecraft:thaiap run gamerule doMobSpawning'));
  a.log('    difficulty/spawn thử: summon zombie ->', await R(`execute in minecraft:thaiap run summon zombie ${X + 3} ${Y} ${Z + 3} {Tags:["thuta"]}`), '| còn sau 1s:', (await (async () => { await a.sleep(1000); return R('execute in minecraft:thaiap run execute if entity @e[type=zombie,tag=thuta]'); })()));
  await R('execute in minecraft:thaiap run minecraft:kill @e[type=zombie,tag=thuta]');
  // a3: mời
  a.log('a3 moi:', (await a.say(`/thaiap moi ${F}`, 1500)).join(' | '));
  await R(`execute in minecraft:thaiap run setblock ${X - 2} ${Y - 1} ${Z} minecraft:stone`);
  await tryAll('a3 ĐÃ MỜI', f, F);
  a.log('a3 duoi:', (await a.say(`/thaiap duoi ${F}`, 1500)).join(' | '));
  f.log('a3 ghe:', (await f.say(`/thaiap ghe ${A}`, 4000)).join(' | '));
  f = await rejoin(f, F);
  f.log('a3 thich:', (await f.say('/thaiap thich', 1500)).join(' | '), '|| lần 2:', (await f.say('/thaiap thich', 1500)).join(' | '));
  a.log('a3 top:', (await a.say('/thaiap top', 1500)).join(' | ').slice(0, 300));
  a.log('a3 morong:', (await a.say('/thaiap morong', 2000)).join(' | '), '| tiền', await R(`bal ${A}`));
  a.log('a3 /thaiap:', (await a.say('/thaiap', 1500)).join(' | ').slice(0, 500));
  // a4 bếp
  await R(`minecraft:give ${A} minecraft:wheat 12`);
  let w = await a.openMenu('/nauan');
  const slot = w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && /Cơm nắm/i.test(a.itemText(it).name)) : -1;
  a.log('a4 /nauan:', w ? 'mở menu, ô cơm nắm = ' + slot : 'không mở');
  if (slot >= 0) { w = await a.click(slot); a.bot.closeWindow(a.bot.currentWindow || w); await a.sleep(800); }
  const dish = a.bot.inventory.items().find(i => !/wheat/.test(i.name) && a.itemText(i).lore.some(l => /bếp Thái Ấp|Thái Ấp/.test(l)));
  const any = a.bot.inventory.items().map(i => a.itemText(i).name).join(', ');
  a.log('a4 món:', dish ? a.itemText(dish).name + ' / ' + a.itemText(dish).lore.join(' / ').slice(0, 200) : 'không thấy dòng bếp Thái Ấp; túi: ' + any);
  await R('execute in minecraft:thaiap run minecraft:kill @e[type=cow,tag=thuta]');
  await R(`minecraft:clear ${A}`); await R(`minecraft:clear ${F}`);
  await f.end(); await a.end();
})().catch(e => { console.log('FAIL', e); process.exit(1); });
