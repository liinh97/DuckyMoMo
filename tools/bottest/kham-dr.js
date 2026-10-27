// Giảm sát thương khảm: so out_of_world 10 khi không mặc gì / quần trơn / quần khảm (Kim Cương + Netherite = 4%) ở đất hoang.
const { start, moveTo } = require('./h');
const N = 'ThuKhamDR';
(async () => {
  let t = await start(N);
  await t.rc.cmd(`minecraft:clear ${N}`); await t.rc.cmd(`gamemode survival ${N}`);
  t = await moveTo(t, 12280, -2236); await t.sleep(6000);
  const R = x => t.rc.cmd(x);
  const hp = async () => +((await R(`data get entity ${N} Health`)).match(/([\d.]+)f/) || [0, 0])[1];
  const test = async lb => { const l = []; for (let i = 0; i < 12; i++) { await R(`effect give ${N} minecraft:instant_health 1 5`); await t.sleep(600); const a = await hp(); await R(`damage ${N} 10 minecraft:out_of_world`); await t.sleep(400); l.push(a - (await hp())); } l.sort((x, y) => x - y); t.log(lb, 'mất (12 lần):', l.map(x => x.toFixed(2)).join(' '), '| trung vị', l[6].toFixed(2)); };
  await test('không mặc:');
  await R(`dmm laydo ht_quan_0 1 ${N}`); await t.sleep(600);
  let q = t.bot.inventory.items().find(i => /leggings/.test(i.name)); await t.bot.equip(q, 'legs'); await t.sleep(1500);
  await test('quần trơn:');
  await t.bot.unequip('legs'); await t.sleep(300);
  q = t.bot.inventory.items().find(i => /leggings/.test(i.name)); await t.bot.equip(q, 'hand'); await t.sleep(300);
  await R(`tinhluyen give kimcuong 1 ${N}`); await R(`tinhluyen give netherite 1 ${N}`); await t.sleep(500);
  const find = (w, re) => w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
  let w = await t.openMenu('/thoren kham');
  for (const g of [/^Kim Cương$/, /^Netherite$/]) { w = await t.click(find(w, /Ô trống/)); w = await t.click(find(w, g)); }
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await t.bot.equip(t.bot.heldItem, 'legs'); await t.sleep(2500);
  await test('quần khảm 4%:');
  await R(`minecraft:clear ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
