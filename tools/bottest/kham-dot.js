// Đốt: kiếm khảm Than (tạm 100% trong config) đánh husk dưới mái kính; xem husk có cháy, có phải quái EliteMobs không.
const { start, moveTo } = require('./h');
const A = 'ThuKhamDot';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  let a = await start(A); let R = x => a.rc.cmd(x);
  await R(`minecraft:clear ${A}`); await R(`gamemode survival ${A}`);
  await R(`dmm laydo ht_kiem_0 1 ${A}`); await R(`tinhluyen give than 1 ${A}`); await a.sleep(800);
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(300);
  let w = await a.openMenu('/thoren kham'); w = await a.click(find(a, w, /Ô trống/)); w = await a.click(find(a, w, /^Than$/));
  if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  a = await moveTo(a, 12280, -2236); R = x => a.rc.cmd(x); await a.sleep(6000);
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(2000);
  await R(`execute at ${A} run fill ~-3 ~4 ~-3 ~3 ~4 ~5 minecraft:glass replace minecraft:air`);
  await R(`execute at ${A} run summon minecraft:husk ~ ~ ~2 {NoAI:1b,Silent:1b,Health:1000f,attributes:[{id:"minecraft:max_health",base:1000}],Tags:["thukham"]}`);
  await a.sleep(1500);
  a.log('husk tên:', await R(`data get entity @e[tag=thukham,limit=1] CustomName`));
  const mob = Object.values(a.bot.entities).find(e => e.name === 'husk');
  for (let i = 0; i < 6 && mob; i++) {
    const h0 = (await R(`data get entity @e[tag=thukham,limit=1] Health`)).replace(/.*data: /, '');
    await a.bot.lookAt(mob.position.offset(0, 1, 0)); a.bot.attack(mob); await a.sleep(150);
    a.log('nhát', i + 1, 'máu', h0, '->', (await R(`data get entity @e[tag=thukham,limit=1] Health`)).replace(/.*data: /, ''), '| Fire', (await R(`data get entity @e[tag=thukham,limit=1] Fire`)).replace(/.*data: /, ''));
    await a.sleep(600);
  }
  await R(`kill @e[tag=thukham]`); await R(`execute at ${A} run fill ~-3 ~4 ~-3 ~3 ~4 ~5 minecraft:air replace minecraft:glass`);
  await R(`minecraft:clear ${A}`); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
