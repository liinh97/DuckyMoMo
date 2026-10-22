// Mở các trang Chợ đen của ThuCA, in món + tên ô (dò món hết hạn nằm ở đâu).
const { start } = require('./h');
(async () => {
  const a = await start('ThuCA');
  for (const c of ['/choden hethan', '/choden cuatoi', '/ah expired', '/ah items']) {
    const w = await a.openMenu(c);
    a.log(c, '->', w ? (a.strip(JSON.stringify(w.title)).match(/"value":"([^"]*)"/g) || []).join('').slice(0, 80) : '(không mở)');
    if (w) a.log('   ', w.slots.slice(0, w.inventoryStart).map((it, i) => it && !/pane/.test(it.name) && `${i}:${it.name}:${a.itemText(it).name}`).filter(Boolean).join(' | ').slice(0, 400));
    if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow); await a.sleep(800);
  }
  a.log('chat:', a.chat.slice(-6).join(' / ').slice(0, 300));
  await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
