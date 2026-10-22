// x14: giữ ThuR3 online 70 giây (trang quản trị bấm Reset "Chơi lại" trong lúc này), in đồ + tiền trước/sau.
const h = require('./h');
(async () => {
  const t = await h.start('ThuR3'); const R = c => t.rc.cmd(c);
  await R('minecraft:clear ThuR3'); await R('minecraft:give ThuR3 minecraft:diamond 5'); await R('eco set ThuR3 9999'); await t.sleep(1500);
  t.log('trước:', await R('bal ThuR3'), '|', await R('minecraft:clear ThuR3 minecraft:diamond 0'));
  const n = t.chat.length; await t.sleep(70000);
  t.log('sau:', await R('bal ThuR3'), '|', await R('minecraft:clear ThuR3 minecraft:diamond 0'), '| online:', !!t.bot.entity);
  t.log('tin:', t.chat.slice(n).filter(x => /lại|reset|Chơi|quà/i.test(x)).join(' / ').slice(0, 300));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
