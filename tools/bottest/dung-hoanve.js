// q6 hoàn vé: mở cổng knights_castle_lair (trừ 1 vé), không chọn độ khó, chờ hết cửa sổ (tối thiểu 30 giây) -> hoàn vé.
const { start } = require('./h');
(async () => {
  const a = await start('ThuQ5', '10.9.2.1');
  await a.sleep(3000);
  const ve = async () => { const l = (await a.say('/dungeon', 2000)).find(x => /Lâu Đài Hiệp Sĩ/.test(x)); return l ? l.replace(/\s+/g, ' ').trim() : '?'; };
  a.log('trước:', await ve());
  a.log('mở cổng:', (await a.say('/dungeon vao knights_castle_lair', 2500)).join(' | ').slice(0, 200));
  if (a.bot.currentWindow) a.bot.closeWindow(a.bot.currentWindow);
  a.log('ngay sau khi mở:', await ve());
  const n0 = a.chat.length; await a.sleep(45000);
  a.log('sau 45 giây không vào:', a.chat.slice(n0).filter(l => /Cổng|vé|Vé/.test(l)).join(' | ').slice(0, 200), '||', await ve());
  await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
