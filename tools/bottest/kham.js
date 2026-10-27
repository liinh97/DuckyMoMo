// Khảm: bot cầm Áo Cao Cấp +5 (2 ô), khảm Sắt + Kim Cương qua /thoren kham, mặc vào xem máu tối đa, tháo 1 ngọc (2.000 xu, 50% vỡ).
// Đơn nhà buôn: /tinhluyen don, giao đơn cần 3 Đồng + 1 Than.
const { start } = require('./h');
const N = process.env.BOTN || 'ThuKham';
const names = (t, w) => w ? w.slots.slice(0, w.inventoryStart).map((it, i) => it && `${i}:${t.itemText(it).name}`).filter(Boolean).filter(s => !/^\d+: ?$/.test(s)).join(' | ') : '(không mở)';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  const t = await start(N);
  const R = x => t.rc.cmd(x);
  await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`); await R(`eco set ${N} 5000`);
  t.log('laydo:', await R(`dmm laydo cc_ao_5 1 ${N}`));
  await R(`tinhluyen give sat 1 ${N}`); await R(`tinhluyen give kimcuong 1 ${N}`);
  await t.sleep(800);
  const slot = t.bot.inventory.items().find(i => i.name === 'diamond_chestplate' || i.name === 'netherite_chestplate' || /chestplate/.test(i.name));
  t.log('áo trong túi:', slot && slot.name, slot && slot.slot);
  await t.bot.equip(slot, 'hand'); await t.sleep(500);
  let w = await t.openMenu('/thoren kham'); t.log('menu khảm:', names(t, w));
  for (const gem of [/Sắt/, /Kim Cương/]) {
    let i = find(t, w, /Ô trống/); if (i < 0) { t.log('không thấy ô trống'); break; }
    w = await t.click(i); t.log('chọn ngọc:', names(t, w));
    const j = find(t, w, gem); if (j < 0) { t.log('không thấy ngọc', gem); break; }
    w = await t.click(j); t.log('sau khảm:', names(t, w));
  }
  t.log('chat:', t.chat.slice(-3).join(' | '));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  const held = t.bot.heldItem; t.log('lore áo:', JSON.stringify(t.itemText(held).lore.slice(-5)));
  t.log('máu trước mặc:', await R(`attribute ${N} minecraft:max_health get`));
  await t.bot.equip(held, 'torso'); await t.sleep(2500);
  t.log('máu sau mặc:', await R(`attribute ${N} minecraft:max_health get`));
  // tháo: cầm lại áo
  await t.bot.unequip('torso'); await t.sleep(500);
  const ao = t.bot.inventory.items().find(i => /chestplate/.test(i.name)); await t.bot.equip(ao, 'hand'); await t.sleep(500);
  w = await t.openMenu('/thoren kham');
  let k = find(t, w, /Sắt/); w = await t.click(k); t.log('xác nhận:', names(t, w));
  k = find(t, w, /Tháo ngọc/); w = await t.click(k);
  t.log('sau tháo:', names(t, w), '| chat:', t.chat.slice(-2).join(' | '));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  t.log('bal:', await R(`bal ${N}`));
  // đơn nhà buôn
  await R(`tinhluyen give dong 3 ${N}`); await R(`tinhluyen give than 1 ${N}`); await t.sleep(600);
  w = await t.openMenu('/tinhluyen don'); t.log('đơn:', names(t, w));
  for (let i = 0; w && i < w.inventoryStart; i++) { const it = w.slots[i]; if (it && /Đơn 3/.test(t.itemText(it).name)) t.log('Đơn 3 lore:', t.itemText(it).lore.join(' / ')); }
  k = find(t, w, /Đơn 3/); if (k >= 0) { w = await t.click(k); }
  t.log('sau giao:', t.chat.slice(-2).join(' | '));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  t.log('bal:', await R(`bal ${N}`));
  await R(`minecraft:clear ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
