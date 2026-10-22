// x6 Sứ Giả: bot gửi /sugia dangky <link> (bước 1) hoặc chỉ vào game đọc tin chờ + /sugia (bước 2). Duyệt / gia hạn / gỡ chạy qua hàm
// trang quản trị (panel media_admin) trong ops wrapper x6 (scratchpad), không gọi thẳng lệnh.
const { start } = require('./h');
const N = process.argv[2] || 'ThuSG', step = process.argv[3] || 'dangky';
(async () => {
  const t = await start(N);
  await t.sleep(3000);
  if (step === 'dangky') t.log('dangky:', (await t.say('/sugia dangky https://www.tiktok.com/@duckymomo_bot_thu', 1500)).join(' | ').slice(0, 250));
  t.log('tin lúc vào + /sugia:', t.chat.concat(await t.say('/sugia', 1500)).join(' | ').slice(0, 400));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
