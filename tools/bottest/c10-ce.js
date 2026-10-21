// c10 shop phù phép /ce (CrazyEnchantments): giá ghi trên menu = tiền bị trừ; sách tên tiếng Việt; đập sách vào kiếm (thành công / thất bại / vỡ).
// Đứng ở spawn (kinh đô: mua bán với server được). Bot cần grim.exempt? không (chỉ bấm menu).
const { start } = require('./h');
const N = 'ThuCE';
(async () => {
  const t = await start(N);
  const R = c => t.rc.cmd(c);
  const bal = async () => +((await R(`bal ${N}`)).replace(/[^\d.]/g, '') || 0);
  await R(`gamemode survival ${N}`); await R(`minecraft:clear ${N}`); await R(`eco set ${N} 50000`);
  let w = await t.openMenu('/ce');
  if (!w) { t.log('không mở /ce'); process.exit(1); }
  const list = w.slots.slice(0, w.inventoryStart).map((it, i) => it && { i, ten: t.itemText(it).name, gia: t.itemText(it).lore.find(l => /Giá|xu/i.test(l)) || '' }).filter(x => x && x.ten.trim());
  for (const x of list) t.log(`  ô ${x.i}: ${x.ten} | ${x.gia}`);
  const pick = list.find(x => /Thường|Common/i.test(x.ten) && /Giá/.test(x.gia)) || list.find(x => /Giá/.test(x.gia));
  const books = [];
  for (let k = 0; k < 6 && pick; k++) {
    const b0 = await bal();
    w = await t.click(pick.i); await t.sleep(800);
    const b1 = await bal();
    t.log(`c10 mua "${pick.ten}" (${pick.gia}): tiền ${b0} -> ${b1} (trừ ${b0 - b1})`);
    if (t.bot.currentWindow && t.bot.currentWindow.inventoryStart && k === 0) { /* vẫn ở menu */ }
  }
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await t.sleep(800);
  const inv = t.bot.inventory.items();
  t.log('c10 túi:', inv.map(i => t.itemText(i).name.replace(/\s+/g, ' ') + 'x' + i.count).join(', ').slice(0, 300));
  // sách tìm được (bấm chuột phải mở sách bí ẩn nếu có)
  const bk = inv.filter(i => /book/.test(i.name));
  if (bk[0]) t.log('c10 mô tả sách:', t.itemText(bk[0]).lore.join(' / ').slice(0, 300));
  // đập sách: 12 kiếm kim cương + 12 sách Hút máu I (/ce book, sách cho kiếm), mỗi kiếm 1 sách
  await R(`minecraft:clear ${N}`);
  for (let k = 0; k < 12; k++) await R(`minecraft:give ${N} minecraft:diamond_sword 1`);
  t.log('/ce book:', await R(`ce book LifeSteal 1 12 ${N}`));
  await t.sleep(1500);
  const b0 = t.bot.inventory.items().find(i => /book/.test(i.name));
  if (b0) t.log('c10 sách Hút máu:', t.itemText(b0).name, '|', t.itemText(b0).lore.filter(l => /Thành công|vỡ/i.test(l)).join(' / '));
  const kq = { thanh_cong: 0, that_bai: 0, vo: 0, khac: 0 };
  const swordsBefore = () => t.bot.inventory.items().filter(i => i.name === 'diamond_sword');
  for (let k = 0; k < 12; k++) {
    const book = t.bot.inventory.items().find(i => /book/.test(i.name));
    const sword = swordsBefore().find(i => !t.itemText(i).lore.some(l => /Hút máu/.test(l)));
    if (!book || !sword) break;
    const nS = swordsBefore().length, n0 = t.chat.length;
    await t.bot.clickWindow(book.slot, 0, 0).catch(() => {}); await t.sleep(300);
    await t.bot.clickWindow(sword.slot, 0, 0).catch(() => {}); await t.sleep(900);
    const msg = t.chat.slice(n0).join(' | ');
    const nS2 = swordsBefore().length;
    if (nS2 < nS) kq.vo++; else if (/đã nhận phù phép/.test(msg)) kq.thanh_cong++; else if (/không nhận/.test(msg)) kq.that_bai++; else kq.khac++;
    if (k < 4 || nS2 < nS) t.log(`  lần ${k + 1}: ${msg.slice(0, 120) || '(không tin)'} | kiếm ${nS} -> ${nS2}`);
    // còn đồ trên con trỏ thì đặt vào ô trống
    const empty = t.bot.inventory.firstEmptyInventorySlot(); if (t.bot.inventory.selectedItem && empty != null) await t.bot.clickWindow(empty, 0, 0).catch(() => {});
  }
  const enchanted = swordsBefore().filter(i => t.itemText(i).lore.some(l => /Hút máu/.test(l))).length;
  t.log('c10 đập 12 sách (Thành công 15%+…):', JSON.stringify(kq), '| kiếm có Hút máu:', enchanted, '| kiếm còn:', swordsBefore().length);
  let co = 0, khong = 0;
  for (let k = 0; k < 36; k++) { const r = await R(`data get entity ${N} Inventory[{Slot:${k}b}]`); if (!/diamond_sword/.test(r)) continue; if (/Hút máu|LifeSteal|lifesteal/i.test(r)) co++; else khong++; if (co + khong === 1) t.log('  mẫu kiếm:', r.replace(/.*data: /, '').slice(0, 300)); }
  t.log('c10 phía server: kiếm có Hút máu', co, '| kiếm trơn', khong, '| (12 sách đã dùng; vỡ = 12 - tổng kiếm)');
  t.log('c10 phía server: kiếm', (await R(`minecraft:clear ${N} minecraft:diamond_sword 0`)).slice(0, 40), '| sách', (await R(`minecraft:clear ${N} minecraft:enchanted_book 0`)).slice(0, 40), '| kiếm có lore:', (await R(`execute if data entity ${N} Inventory[{id:"minecraft:diamond_sword",components:{}}].components."minecraft:lore"`)).slice(-20));
  await R(`minecraft:clear ${N}`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
