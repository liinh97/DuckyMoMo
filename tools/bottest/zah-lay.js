// Lấy lại món hết hạn ở /choden hethan (ô 9), đếm kim cương.
const { start } = require('./h');
(async () => {
  const a = await start('ThuCA'); const R = x => a.rc.cmd(x);
  await R('minecraft:clear ThuCA');
  const w = await a.openMenu('/choden hethan'); const i = w.slots.slice(0, w.inventoryStart).findIndex(it => it && it.name === 'diamond');
  a.log('ô kc:', i); const w2 = await a.click(i); await a.sleep(1500);
  if (a.bot.currentWindow) { a.log('sau bấm:', a.bot.currentWindow.slots.slice(0, a.bot.currentWindow.inventoryStart).map((it, j) => it && !/pane/.test(it.name) && `${j}:${a.itemText(it).name}`).filter(Boolean).join(' | ')); a.bot.closeWindow(a.bot.currentWindow); }
  await a.sleep(1000); a.log('kc:', await R('minecraft:clear ThuCA minecraft:diamond 0'));
  await R('minecraft:clear ThuCA'); await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
