// Thời gian chờ dịch chuyển theo rank: gõ /spawn, đo bao lâu vị trí đổi (ThuA = danh phận, mong 5 giây; ThuX1 + nhóm namtuoc, mong 3 giây).
const h = require('./h');
(async () => {
  const name = process.argv[2]; const t = await h.start(name); const R = c => t.rc.cmd(c);
  await t.sleep(4000);
  const pos = async () => (await R(`data get entity ${name} Pos`)).replace(/.*data: /, '');
  const p0 = await pos(); const t0 = Date.now(); t.bot.chat('/spawn');
  let sec = null; for (let i = 0; i < 100; i++) { await t.sleep(100); if ((await pos()) !== p0) { sec = ((Date.now() - t0) / 1000).toFixed(1); break; } }
  t.log(name, ': /spawn sau', sec, 'giây | báo:', t.chat.slice(-3).join(' / ').slice(0, 160));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
