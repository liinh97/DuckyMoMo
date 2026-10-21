// o5 /choilai (bot ThuS): rút hết hàng Chợ đen trước (luật: phải rút mới chơi lại được), rồi /choilai + /choilai xacnhan.
// Mất tiền, đồ, balo; tiền về mức khởi đầu.
const { start } = require('./h');
const N = 'ThuS';
(async () => {
  const t = await start(N);
  const R = c => t.rc.cmd(c);
  const bal = async () => +((await R(`bal ${N}`)).replace(/[^\d.]/g, '') || 0);
  const names = w => w ? w.slots.slice(0, w.inventoryStart).map((it, i) => it && t.itemText(it).name.trim() && `${i}:${t.itemText(it).name}`).filter(Boolean).join(', ') : '(không mở)';
  for (const c of ['/choden cuatoi', '/choden hethan']) { const m = await t.openMenu(c); t.log(c, names(m)); if (m) t.bot.closeWindow(m); await t.sleep(500); }
  let w = await t.openMenu('/choden cuatoi');
  if (w) { const i = w.slots.slice(0, w.inventoryStart).findIndex(it => it && /Rút hết/.test(t.itemText(it).name)); if (i >= 0) { const n0 = t.chat.length; await t.click(i); await t.sleep(1000); t.log('bấm Rút hết:', t.chat.slice(n0).join(' | ').slice(0, 200)); } if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); }
  await t.sleep(1000);
  w = await t.openMenu('/choden hethan'); if (w) { const i = w.slots.slice(0, w.inventoryStart).findIndex(it => it && /Rút hết|Lấy hết/.test(t.itemText(it).name)); if (i >= 0) await t.click(i); if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); }
  // đồ đã mua chưa nhận: trang mua -> nút "✔ Đồ đã mua" -> lấy hết
  w = await t.openMenu('/choden mua');
  if (w) { const i = w.slots.slice(0, w.inventoryStart).findIndex(it => it && /Đồ đã mua/.test(t.itemText(it).name)); if (i >= 0) { w = await t.click(i); t.log('Đồ đã mua:', names(w)); const j = w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && /Lấy hết|Rút hết|Nhận hết/.test(t.itemText(it).name)) : -1; if (j >= 0) await t.click(j); } if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); }
  await t.sleep(1000);
  t.log('rút hàng chợ:', t.chat.slice(-3).join(' | ').slice(0, 200));
  await R(`minecraft:clear ${N}`); await R(`minecraft:give ${N} minecraft:diamond 5`); await R(`eco set ${N} 3000`);
  t.log('o5 trước: tiền', await bal());
  t.log('o5 /choilai:', (await t.say('/choilai', 1500)).join(' | ').slice(0, 400));
  t.log('o5 /choilai xacnhan:', (await t.say('/choilai xacnhan', 4000)).join(' | ').slice(0, 300));
  await t.sleep(2000);
  t.log('o5 sau: tiền', await bal(), '| kim cương', (await R(`minecraft:clear ${N} minecraft:diamond 0`)).slice(0, 60), '| balo', (await t.say('/balo', 1200)).join(' ').slice(0, 80));
  t.log('o5 /choilai lần 2:', (await t.say('/choilai', 1500)).join(' | ').slice(0, 250));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
