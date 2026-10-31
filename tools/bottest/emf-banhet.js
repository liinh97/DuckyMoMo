// Thử nút "Bán hết" (sell-inventory) ở /emf shop: phát 2 cá vào túi, mở shop, bấm Bán hết, bấm Xác nhận, so tiền + số cá còn lại.
const { start } = require('./h');
const A = process.env.BOTN || 'ThuN7';
(async () => {
  const a = await start(A, '10.9.0.81');
  const R = c => a.rc.cmd(c);
  const bal = async n => +((await R(`bal ${n}`)).replace(/.*:\s*/, '').replace(/[^\d.]/g, '') || 0);
  await R(`gamemode survival ${A}`); await R(`minecraft:clear ${A}`);
  for (const f of ['CaRoDong', 'CaChep']) { await R(`emf admin fish Common ${f} 1 ${A}`); await a.sleep(600); }
  const fishN = () => a.bot.inventory.items().filter(it => /Dài/.test(a.itemText(it).lore.join(' '))).length;
  const b0 = await bal(A), n0 = fishN();
  const c0 = a.chat.length;
  const w = await a.openMenu('/emf shop');
  a.log('mở shop:', w ? 'ok' : 'KHÔNG', '| tin:', a.chat.slice(c0).join(' | ').slice(0, 200));
  if (w) {
    const btn = w.slots.slice(0, w.inventoryStart).findIndex(x => x && /Bán hết/.test(a.itemText(x).name));
    a.log('nút Bán hết ở ô', btn);
    await a.click(btn); await a.sleep(1500);
    const w2 = a.bot.currentWindow;
    const cf = w2 ? w2.slots.slice(0, w2.inventoryStart).findIndex(x => x && /Xác nhận bán hết/.test(a.itemText(x).name)) : -1;
    a.log('màn xác nhận:', w2 ? 'mở' : 'không', '| nút xác nhận ô', cf, '| tin:', a.chat.slice(c0).join(' | ').slice(0, 300));
    if (cf >= 0) { await a.click(cf); await a.sleep(1500); }
    a.log('tin sau bấm:', a.chat.slice(c0).join(' | ').slice(0, 400));
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  }
  a.log(`KQ: tiền ${b0} -> ${await bal(A)} | cá ${n0} -> ${fishN()}`);
  await R(`minecraft:clear ${A}`);
  await a.end(); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
