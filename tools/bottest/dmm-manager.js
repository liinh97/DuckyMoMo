// Manager (quanly) dùng /dmm: được lệnh thường, bị chặn reload/decorate/structures (23/10).
const h = require('./h');
(async () => {
  const t = await h.start('ThuManager'); await t.sleep(1500);
  await t.rc.cmd('lp user ThuManager parent add quanly'); await t.sleep(3000);
  for (const c of ['/dmm thuvien', '/dmm reload', '/dmm decorate status', '/dmm structures status', '/dmm']) {
    const n = t.chat.length; t.bot.chat(c); await t.sleep(2000);
    t.log(`## ${c}: ${t.chat.slice(n).join(' / ').slice(0, 220)}`);
  }
  await t.rc.cmd('lp user ThuManager parent remove quanly');
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
