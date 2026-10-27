// Khảm (phần còn lại): bảng "Đang có" trong menu, đốt (kiếm Than, dưới mái kính
// cho khỏi mưa dập lửa), chuyển đồ khảm sang người khác (A ném, B nhặt).
const { start, moveTo } = require('./h');
const A = 'ThuKhamA', B = 'ThuKhamB';
const find = (t, w, re) => w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name)) : -1;
(async () => {
  let a = await start(A);
  let R = x => a.rc.cmd(x);
  await R(`minecraft:clear ${A}`); await R(`gamemode survival ${A}`);
  await R(`dmm laydo ht_kiem_0 1 ${A}`);
  await R(`tinhluyen give dong 3 ${A}`); await R(`tinhluyen give than 1 ${A}`); await a.sleep(800);
  const kham = async (re, gems) => {
    const it = a.bot.inventory.items().find(i => re.test(i.name) && !a.itemText(i).lore.join('').includes('◆'));
    await a.bot.equip(it, 'hand'); await a.sleep(300);
    let w = await a.openMenu('/thoren kham');
    for (const g of gems) { w = await a.click(find(a, w, /Ô trống/)); w = await a.click(find(a, w, g)); }
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
    await a.sleep(300);
  };
  await kham(/sword/, [/^Than$/]);
  await a.sleep(1500);   // kiếm vẫn trên tay
  let w = await a.openMenu('/thoren kham'); const top = w && w.slots[4];
  a.log('menu "Đang có":', top ? a.itemText(top).lore.filter(l => /Đang có|\+|chưa có/.test(l)).join(' / ') : '(không mở)');
  if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  a = await moveTo(a, 12280, -2236); R = x => a.rc.cmd(x); await a.sleep(6000);
  await a.bot.equip(a.bot.inventory.items().find(i => /sword/.test(i.name)), 'hand'); await a.sleep(300);
  // chuyển đồ: A ném kiếm khảm, B nhặt
  let b = await start(B); await b.rc.cmd(`minecraft:clear ${B}`);
  b = await moveTo(b, 12280, -2236); await b.sleep(2000);
  await R(`tp ${A} ${B}`); await a.sleep(1500);
  const kiem = a.bot.inventory.items().find(i => /sword/.test(i.name));
  await a.bot.tossStack(kiem); await a.sleep(300); await a.end();   // A thoát để chỉ B nhặt
  await b.sleep(4000);
  const got = b.bot.inventory.items().find(i => /sword/.test(i.name));
  b.log('B nhặt kiếm:', got ? b.itemText(got).name + ' | ' + b.itemText(got).lore.filter(l => /◆|KHẢM/.test(l)).join(' / ') : 'không nhặt được (xem B có ở gần không)');
  await b.rc.cmd(`minecraft:clear ${A}`); await b.rc.cmd(`minecraft:clear ${B}`);
  await b.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
