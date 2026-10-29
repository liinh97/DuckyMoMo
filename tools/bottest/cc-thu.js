// Set Chuyên Cần (29/10): /diemdanh ra Phiếu Chuyên Cần + nút [Đổi set Chuyên Cần]; /thoren set chuyencan mở menu set; rèn 1 món +0.
const h = require('./h');
const N = process.env.BOTN || 'ThuCC';
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const phieu = () => t.bot.inventory.items().filter(i => /Phiếu Chuyên Cần/.test(t.itemText(i).name)).reduce((a, i) => a + i.count, 0);
  const sl = (w, re) => w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
  await R(`minecraft:clear ${N}`); await R(`eco set ${N} 5000`); await t.sleep(1000);
  t.log('## /diemdanh:', (await t.say('/diemdanh', 3000)).join(' | ').slice(0, 600));
  await t.sleep(1000);
  t.log('## túi sau điểm danh:', t.bot.inventory.items().map(i => t.itemText(i).name + ' x' + i.count).join(', '), '| phiếu =', phieu());
  if (phieu() < 8) { await R(`dmm laydo phieu_dd ${8 - phieu()} ${N}`); await t.sleep(1000); t.log('## bù phiếu cho đủ 3 ->', phieu()); }
  let w = await t.openMenu('/thoren set chuyencan');
  t.log('## menu set:', w.slots.slice(0, w.inventoryStart).filter(it => it && !/pane/.test(it.name)).map(it => t.itemText(it).name).join(' | '));
  const b = sl(w, /Rèn Mũ/); t.log('## lore mũ +0:', t.itemText(w.slots[sl(w, /Mũ Chuyên Cần/)]).lore.join(' ¦ '));
  t.log('## nút rèn mũ:', b, b >= 0 ? t.itemText(w.slots[b]).lore.join(' ¦ ') : '(không thấy)');
  if (b >= 0) {
    const out = t.say ? null : null;
    await t.click(b); await t.sleep(2000);
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); await t.sleep(500);
  }
  t.log('## túi sau rèn:', t.bot.inventory.items().map(i => t.itemText(i).name + ' [' + i.name + '] x' + i.count).join(', '), '| phiếu =', phieu(), '| xu', await R(`balance ${N}`));
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
