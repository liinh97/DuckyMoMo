// s1/s2 phần AuthMe ở SẢNH (bot vào thẳng lobby qua forwarding vì Sonar ở proxy chặn bot từ trước — đã thử 22/10, s1s2-proxy.js).
// Chưa /login: thử đi lại, chat, /msg, /spawn, /menu, mở túi -> chỉ được /login /register. Không /register, không nhập mật khẩu.
// Chạy: ./tools/bottest/run.sh s1-lobby.js <tên>   (s2: tên bắt đầu bằng "." như tiền tố Bedrock)
const { start } = require('./h');
const name = process.argv[2] || 'ThuS1';
(async () => {
  const t = await start(name, '10.9.4.' + (name.startsWith('.') ? 2 : 1), 'lobby');
  await t.sleep(3000);
  t.log('tin đầu:', t.chat.join(' | ').replace(/\s+/g, ' ').slice(0, 400));
  const p0 = t.bot.entity.position.clone();
  t.bot.setControlState('forward', true); t.bot.setControlState('sprint', true); await t.sleep(2500); t.bot.clearControlStates(); await t.sleep(1500);
  const pos = (await t.rc.cmd(`data get entity ${name} Pos`)).replace(/.*data: /, '');
  t.log(`s1 đi 2,5 giây: bot tự thấy dịch ${p0.distanceTo(t.bot.entity.position).toFixed(2)} khối; server: ${pos} (lúc đầu ${p0.floored()})`);
  for (const c of ['xin chao moi nguoi', '/msg ThuX1 xin chao', '/spawn', '/menu', '/server banghoi-1', '/help']) {
    const n0 = t.chat.length; t.bot.chat(c); await t.sleep(1800);
    t.log(`s1 "${c}" ->`, t.chat.slice(n0).join(' | ').replace(/\s+/g, ' ').slice(0, 200) || '(không phản hồi)');
  }
  t.log('  tin chat công khai có lọt ra log sảnh không: xem wrapper');
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e.message); process.exit(1); });
