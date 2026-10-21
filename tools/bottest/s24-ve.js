// s24 vé cổng Sáng Tạo dùng lại: A qua cổng (có vé 60 giây) -> vào sangtao-1 -> thoát -> vào lại ngay bằng vé cũ -> chờ hết hạn rồi vào lại.
const { start } = require('./h');
const A = 'ThuA';
const rejoin = async (t, name, host) => { await t.end(); await t.sleep(2500); const t2 = await start(name, undefined, host); await t2.sleep(1500); return t2; };
(async () => {
  let a = await start(A);
  await a.rc.cmd(`gamemode survival ${A}`);
  await a.say('/thaiap sangtao', 2500);
  a = await rejoin(a, A);
  const n = a.chat.length; a.bot.setControlState('forward', true); await a.sleep(1800); a.bot.setControlState('forward', false); await a.sleep(1500);
  a.log('qua cổng:', a.chat.slice(n).filter(l => /Sáng Tạo/.test(l)).join(' | ').slice(0, 120));
  await a.end(); await a.sleep(1500);
  const visit = async label => { const s = await start(A, undefined, 'sangtao-1'); await s.sleep(2500); const gm = (await s.rc.cmd(`data get entity ${A} playerGameType`)).replace(/.*data: /, ''); s.log(label, ': chế độ', gm, '(1 = sáng tạo, 2 = bị chặn) |', s.chat.filter(l => /Sáng Tạo|chỉ vào được/.test(l)).join(' | ').slice(0, 150)); await s.end(); await s.sleep(2000); };
  await visit('s24 lần 1 (vé mới)');
  await visit('s24 lần 2 ngay sau, vé cũ');
  await new Promise(r => setTimeout(r, 62000));
  await visit('s24 lần 3 sau 60 giây');
  process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
