const { start } = require('./h');
const N = 'ThuCapDo';
(async () => {
  const t = await start(N);
  const R = c => t.rc.cmd(c);
  const free = async () => { const w = await t.openMenu('/capdo'); const x = t.itemText(w.slots[4]); t.bot.closeWindow(w); return x.lore.find(l => l.includes('Điểm thuộc tính')); };
  const raise = async (slot, n) => { let w = await t.openMenu('/capdo'); const n0 = t.chat.length; for (let i = 0; i < n; i++) w = await t.click(slot); t.bot.closeWindow(w); await t.sleep(300); return t.chat.slice(n0); };
  const hp = async () => (await t.attr('minecraft:max_health')).replace(/.*is /, '');
  const sp = async () => (await t.attr('minecraft:movement_speed')).replace(/.*is /, '');
  // ra đất hoang (spawn là khu an toàn, không nhận sát thương)
  t.log('spread', await R(`spreadplayers 14200 -2532 0 1 false ${N}`)); await t.sleep(2000);
  t.log('BASE hp', await hp(), 'speed', await sp());
  // cd4
  t.log('dat20', await R(`capdo dat ${N} 20`));
  t.log('raise4', (await raise(19, 4)).join(' | '));
  t.log('cd4 hp', await hp(), await free());
  // cd12
  t.log('dat5', await R(`capdo dat ${N} 5`)); await t.sleep(500);
  t.log('cd12 hp', await hp(), await free());
  // cd8
  await R(`capdo dat ${N} 100`);
  await raise(19, 10); await raise(21, 10);
  t.log('cd8 free', await free(), 'hp', await hp());
  t.log('cd8 ben', (await raise(23, 1)).join(' | '));
  // cd11 tẩy điểm
  await R(`eco set ${N} 15000`);
  let w = await t.openMenu('/capdo'); let n0 = t.chat.length; w = await t.click(38); t.log('cd11 normal', t.chat.slice(n0).join(' | '));
  n0 = t.chat.length; w = await t.click(38, true); t.log('cd11 shift', t.chat.slice(n0).join(' | ')); t.bot.closeWindow(w);
  t.log('cd11 bal', await R(`bal ${N}`), 'hp', await hp(), await free());
  // cd6 Bền bỉ + cd7 Nhanh nhẹn
  await raise(23, 10); await raise(25, 10);
  t.log('cd7 speed', await sp());
  await R(`effect give ${N} minecraft:instant_health 1 10`); await t.sleep(800);
  const h0 = t.bot.health; t.log('dmg', await R(`damage ${N} 10 minecraft:mob_attack`)); await t.sleep(800);
  t.log('cd6 ben: máu trước', h0, 'sau', t.bot.health, '(không Bền bỉ thì mất 10)');
  // cd6 Sức mạnh: tẩy rồi Sức mạnh 10, đánh heo không AI máu 100
  await R(`eco set ${N} 15000`); w = await t.openMenu('/capdo'); w = await t.click(38, true); t.bot.closeWindow(w);
  await raise(21, 10);
  const p = t.bot.entity.position;
  for (const label of ['co', 'khong']) {
    if (label === 'khong') { await R(`eco set ${N} 15000`); w = await t.openMenu('/capdo'); w = await t.click(38, true); t.bot.closeWindow(w); }
    await R(`kill @e[type=pig,tag=thucd]`);
    await R(`execute at ${N} run summon pig ^ ^ ^2 {NoAI:1b,Tags:["thucd"],attributes:[{id:"minecraft:max_health",base:100}],Health:100f}`); await t.sleep(800);
    const pig = t.bot.nearestEntity(e => e.name === 'pig'); if (!pig) { t.log('không thấy heo'); continue; }
    await t.bot.lookAt(pig.position.offset(0, 0.5, 0)); await t.sleep(1000);
    t.bot.attack(pig); await t.sleep(1000);
    t.log('cd6 suc', label, await R(`data get entity @e[type=pig,tag=thucd,limit=1] Health`));
  }
  await R(`kill @e[type=pig,tag=thucd]`);
  await t.end();
})().catch(e => { console.log('FAIL', e); process.exit(1); });
