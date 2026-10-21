// s15 chèn mã màu / ký tự lạ / <script> vào tên: Thái Ấp (/thaiap ten), pet (/petname), Chợ đen (tên món đổi bằng đe không làm được ở bot).
// Xem trong game ra chữ gì (mã §k nhấp nháy, nút bấm giả) và dữ liệu lưu; phần web: kiểm riêng bằng /api.
const { start } = require('./h');
const A = 'ThuA';
// § gõ trong chat: server đá người chơi ra ngay (Minecraft cấm ký tự này) -> không thử ở đây
const BAD = ['&kNhapNhay&r', '<script>alert(1)</script>', 'A'.repeat(80), '{"text":"gia","clickEvent":{"action":"run_command","value":"/op ThuA"}}'];
(async () => {
  const a = await start(A);
  const R = x => a.rc.cmd(x);
  const raw = [];
  a.bot._client.on('system_chat', p => raw.push(JSON.stringify(p.content)));
  for (const b of BAD) {
    const r0 = raw.length;
    a.log(`/thaiap ten ${b.slice(0, 30)}:`, (await a.say(`/thaiap ten ${b}`, 1500)).join(' | ').slice(0, 160));
    const top = raw.length; await a.say('/thaiap top', 1500);
    const line = raw.slice(top).find(x => /ThuA/.test(x)) || '';
    a.log('   top (thô):', line.replace(/"type":"[a-z]+",|"value":/g, '').slice(0, 260), '| có obfuscated:', /obfuscated/.test(line), '| có click:', /click/i.test(line));
  }
  await a.say('/thaiap ten ThuA', 1200);
  await R(`petadmin create ${A} Pig`); await a.sleep(2000);
  for (const b of ['&k&lHack', '<script>x</script>', '&4Do']) {
    a.log(`/petname ${b}:`, (await a.say(`/petname ${b}`, 1500)).join(' | ').slice(0, 160));
    const r0 = raw.length; await a.say('/petinfo', 1500);
    a.log('   petinfo (thô):', (raw.slice(r0)[0] || '').replace(/"type":"[a-z]+",|"value":/g, '').slice(0, 200), '| obfuscated:', raw.slice(r0).some(x => /obfuscated/.test(x)));
  }
  await a.say('/petrelease ' + 'x', 1000);
  const info = (await a.say('/petinfo', 1500)).join(' ');
  const nm = (info.match(/^\s*([^:]+):/) || [])[1];
  if (nm) await a.say('/petrelease ' + nm.trim(), 1500);
  await a.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
