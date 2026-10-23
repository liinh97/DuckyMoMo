// /web ở sảnh: mở menu, in các ô, bấm ô mã -> chat có mã (23/10).
const h = require('./h');
(async () => {
  const t = await h.start('ThuWeb', undefined, process.env.HOST || 'lobby'); await t.sleep(2500);
  const w = await t.openMenu('/web');
  if (!w) { t.log('KHÔNG MỞ MENU | ' + t.chat.slice(-3).join(' / ')); await t.end(); process.exit(0); }
  let codeSlot = -1;
  w.slots.slice(0, w.inventoryStart).forEach((it, i) => { if (!it || /pane$/.test(it.name)) return; const x = t.itemText(it); if (/Mã của bạn/.test(x.name)) codeSlot = i; t.log(`  [${i}] ${it.name} | ${x.name} | ${x.lore.join(' ¦ ').slice(0, 170)}`); });
  const n = t.chat.length; await t.bot.clickWindow(codeSlot, 0, 0); await t.sleep(1500);
  t.log('## bấm ô mã -> chat: ' + t.chat.slice(n).join(' / '));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
