// Đồ khảm đổi chủ: chép nguyên món (mọi dữ liệu) từ tay A sang tay B như khi trade, xem ngọc trên món của B + B mở /thoren kham thấy ngọc.
const { start } = require('./h');
const A = 'ThuKhamA', B = 'ThuKhamB';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  const a = await start(A); const b = await start(B);
  const R = x => a.rc.cmd(x);
  await R(`minecraft:clear ${A}`); await R(`minecraft:clear ${B}`);
  await R(`dmm laydo ht_kiem_0 1 ${A}`); await R(`tinhluyen give netherite 1 ${A}`); await a.sleep(800);
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(300);
  let w = await a.openMenu('/thoren kham'); w = await a.click(find(a, w, /Ô trống/)); w = await a.click(find(a, w, /^Netherite$/));
  if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  await a.sleep(500);
  a.log('A cầm:', await R(`data get entity ${A} SelectedItem.components."minecraft:custom_data".PublicBukkitValues."duckymomocore:dmm_kham"`));
  a.log("chép:", await R(`minecraft:item replace entity ${B} weapon.mainhand from entity ${A} weapon.mainhand`));
  await R(`minecraft:item replace entity ${A} weapon.mainhand with minecraft:air`);
  await b.sleep(1500);
  b.log('B nhận:', await R(`data get entity ${B} SelectedItem.components."minecraft:custom_data".PublicBukkitValues."duckymomocore:dmm_kham"`));
  w = await b.openMenu('/thoren kham'); b.log('menu khảm của B:', w ? w.slots.slice(0, w.inventoryStart).filter(Boolean).map(i => b.itemText(i).name).filter(n => /◆|◇/.test(n)).join(' | ') : '(không mở)');
  if (b.bot.currentWindow) b.bot.closeWindow(b.bot.currentWindow);
  await R(`minecraft:clear ${A}`); await R(`minecraft:clear ${B}`);
  await b.end(); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
