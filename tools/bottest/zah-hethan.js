// Chợ đen (zAuctionHouse) hết hạn (22/10): tạm default-expiration 60 giây (sao lưu, trả lại sau), treo 1 kim cương, chờ quá hạn:
// món phải rời trang bán, nằm ở /choden hethan, bấm lấy lại được đúng 1 kim cương.
const { start } = require('./h');
const A = 'ThuCA', B = 'ThuCB';
(async () => {
  const a = await start(A); const R = x => a.rc.cmd(x);
  const names = w => w ? w.slots.slice(0, w.inventoryStart).map((it, i) => it && `${i}:${it.name}`).filter(Boolean).join(', ') : '(không mở)';
  const kc = async () => (await R(`minecraft:clear ${A} minecraft:diamond 0`)).replace(/.*Found |No items.*/, m => m.startsWith('No') ? '0' : '');
  await R(`minecraft:clear ${A}`); await R(`minecraft:item replace entity ${A} weapon.mainhand with minecraft:diamond 1`); await a.sleep(500);
  a.log('treo:', (await a.say('/choden ban 100', 2000)).join(' | ').slice(0, 200), '| kc trong túi:', await kc());
  let w = await a.openMenu('/choden mua'); a.log('trang mua có kc:', w && w.slots.slice(0, w.inventoryStart).some(it => it && it.name === 'diamond')); if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  await a.sleep(90000);
  w = await a.openMenu('/choden mua'); a.log('sau 90 giây, trang mua còn kc:', w && w.slots.slice(0, w.inventoryStart).some(it => it && it.name === 'diamond')); if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow); await a.sleep(500);
  w = await a.openMenu('/choden hethan'); a.log('/choden hethan:', names(w));
  const i = w ? w.slots.slice(0, w.inventoryStart).findIndex(it => it && it.name === 'diamond') : -1;
  if (i >= 0) { await a.click(i); await a.sleep(1500); if (a.bot.currentWindow) { a.log('sau bấm:', names(a.bot.currentWindow)); a.bot.closeWindow(a.bot.currentWindow); } }
  await a.sleep(1000); a.log('kc trong túi sau khi lấy lại:', await kc(), '| tin:', a.chat.slice(-4).join(' / ').slice(0, 300));
  await R(`minecraft:clear ${A}`); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
