// Khảm + nâng cấp: Áo Cao Cấp +5 có 2 ngọc -> rèn Giáp Huyền Thoại +0 (3 ô): ngọc phải theo sang, ô 3 trống.
const { start } = require('./h');
const N = process.env.BOTN || 'ThuKham';
const names = (t, w) => w ? w.slots.slice(0, w.inventoryStart).map((it, i) => it && `${i}:${t.itemText(it).name}`).filter(Boolean).filter(s => !/Pane/.test(s)).join(' | ') : '(không mở)';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  const t = await start(N);
  const R = x => t.rc.cmd(x);
  await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`); await R(`eco set ${N} 50000`);
  await R(`dmm laydo cc_ao_5 1 ${N}`); await R(`tinhluyen give sat 1 ${N}`); await R(`tinhluyen give kimcuong 2 ${N}`); await R(`tinhluyen give lucbao 1 ${N}`);
  await R(`dmm laydo ht_an_than 3 ${N}`); await R(`dmm laydo long_hon 1 ${N}`); await R(`minecraft:give ${N} minecraft:netherite_ingot 2`);
  await t.sleep(800);
  const ao = t.bot.inventory.items().find(i => /chestplate/.test(i.name)); await t.bot.equip(ao, 'hand'); await t.sleep(400);
  let w = await t.openMenu('/thoren kham');
  for (const gem of [/Sắt/, /Kim Cương/]) { w = await t.click(find(t, w, /Ô trống/)); w = await t.click(find(t, w, gem)); }
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  t.log('trước nâng:', JSON.stringify(t.itemText(t.bot.heldItem).lore.slice(-3)));
  w = await t.openMenu('/thoren'); t.log('thoren:', names(t, w).slice(0, 300));
  w = await t.click(find(t, w, /Huyền Thoại/)); t.log('set:', names(t, w).slice(0, 400));
  const k = find(t, w, /Rèn Giáp/); t.log('ô giáp', k);
  if (k >= 0) { w = await t.click(k); }
  t.log('chat:', t.chat.slice(-3).join(' | '));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await t.sleep(800);
  for (const it of t.bot.inventory.items().filter(i => /chestplate/.test(i.name))) t.log('áo:', t.itemText(it).name, JSON.stringify(t.itemText(it).lore.slice(-4)));
  await R(`minecraft:clear ${N}`); await R(`eco set ${N} 0`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
