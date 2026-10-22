// cd19 phụ: bot ThuCD3 mở /capdo, bấm nâng Máu + Sức mạnh mỗi cái 1 lần (để trang tài khoản có dòng thuộc tính), in thông báo.
const h = require('./h');
(async () => {
  const t = await h.start('ThuCD3');
  for (const slot of (process.env.SLOTS || '23').split(',').map(Number)) {
    const w = await t.openMenu('/capdo'); if (!w) { t.log('không mở menu'); break; }
    t.log('ô', slot, ':', t.itemText(w.slots[slot]) && t.itemText(w.slots[slot]).name);
    const n = t.chat.length; await t.click(slot); await t.sleep(800); t.log('  ->', t.chat.slice(n).join(' / ').slice(0, 150));
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); await t.sleep(500);
  }
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
