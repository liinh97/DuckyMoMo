// Đơn đổi tuần: bot online nghe thông báo, admin đã sửa donbuon.yml sang tuần cũ rồi /dmm reload (chạy từ ngoài).
const { start } = require('./h');
(async () => {
  const t = await start('ThuNhaBuon');
  const n = t.chat.length;
  await t.rc.cmd('dmm reload'); await t.sleep(9000);   // roll() chạy sau 100 tick
  t.log('tin nhận:', t.since(n).filter(l => /Nhà Buôn|đơn/i.test(l)).join(' | ') || '(không có)');
  await t.end(); process.exit(0);
})();
