// 30/10: gói tài nguyên mặc định (ô Font chữ mềm tắt) phải là gói items (font pixel sửa dấu ngã).
const mf = require('mineflayer');
const seen = [];
const orig = mf.createBot;   // gắn nghe gói ngay khi tạo bot (server gửi gói 2 giây sau khi vào, trước khi h.start xong)
mf.createBot = (o) => { const b = orig(o); b._client.on('packet', (d, m) => { if (/resource|pack/i.test(m.name)) seen.push(m.name + ' ' + (d.url || '')); }); return b; };
const h = require('./h');
const N = process.env.BOTN || 'PackThu';
(async () => {
  const t = await h.start(N);
  await t.sleep(8000);
  t.log('## gói nhận:', seen.join(' | ') || '(không có)');
  await t.end();
})();
