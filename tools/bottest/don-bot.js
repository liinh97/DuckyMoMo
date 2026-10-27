// Dọn bot test: từng bot vào Bang Hội Chiến (vào / ra im lặng), /choilai admin <tên> moi (như người mới), thoát. Danh sách: bot-test.txt (mỗi dòng một tên).
const fs = require('fs');
const { start } = require('./h');
(async () => {
  const names = fs.readFileSync('bot-test.txt', 'utf8').split('\n').map(s => s.trim()).filter(Boolean);
  let ok = 0;
  for (const n of names) {
    try {
      const t = await start(n);
      const r = await t.rc.cmd(`choilai admin ${n} moi`);
      const good = /RESET OK/.test(r); if (good) ok++;
      console.log(n, good ? 'đã dọn' : 'LỖI: ' + r.slice(0, 120));
      // "như người mới" xoá cả quyền lẻ -> cấp lại quyền vào/ra im lặng cho lúc thoát, thoát xong gỡ (tài khoản test sạch hẳn)
      await t.rc.cmd(`lp user ${n} permission set duckymomo.vaora.an true`); await t.sleep(1500);
      const rc = t.rc; t.bot.quit(); await t.sleep(1500);
      await rc.cmd(`lp user ${n} permission unset duckymomo.vaora.an`); rc.close();
    } catch (e) { console.log(n, 'không vào được:', e.message); }
  }
  console.log('XONG', ok, '/', names.length);
  process.exit(0);
})();
