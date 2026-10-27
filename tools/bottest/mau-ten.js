// Kiểm màu tên trong chat (27/10: tên ăn màu xám của tiền tố [Tị Nạn]): ThuMauA chat, ThuMauB đọc gói chat thô, in đoạn có tên kèm màu.
const { start } = require('./h');
(async () => {
  const a = await start('ThuMauA', '10.9.5.1'); const b = await start('ThuMauB', '10.9.5.2');
  const raw = [];
  for (const ev of ['player_chat', 'system_chat', 'profileless_chat']) b.bot._client.on(ev, p => raw.push(ev + ' ' + JSON.stringify(p).slice(0, 900)));
  await a.sleep(3000); a.bot.chat('thu mau ten'); await a.sleep(2500);
  for (const r of raw.filter(x => /ThuMauA/.test(x))) console.log(r.replace(/\\"/g, '"').slice(0, 900));
  await a.end(); await b.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e.message); process.exit(1); });
