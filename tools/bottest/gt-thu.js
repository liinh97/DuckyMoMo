// Giới thiệu bạn bè (29/10): bot A vào (có IP) rồi thoát; bot B mới: nhập A -> bị chặn cùng IP, nhập chính mình -> chặn,
// nhập người không có -> báo, nhập Linh -> nhận quà; nhập lần 2 -> chặn; /gioithieu, /gioithieu top.
const h = require('./h');
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const a = await h.start('ThuGTa'); await a.sleep(3000); await a.end();
  const t = await h.start('ThuGTb');
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await t.sleep(9000);
  t.log('## lúc vào:', msgs.filter(m => /✉/.test(m)).join(' / '));
  for (const c of ['/gioithieu ThuGTa', '/gioithieu ThuGTb', '/gioithieu KhongCoAi123', '/gioithieu Linh', '/gioithieu Linh', '/gioithieu', '/gioithieu top']) {
    const n = msgs.length; t.bot.chat(c); await t.sleep(1500); t.log('##', c, '->', msgs.slice(n).join(' / ').slice(0, 500));
  }
  t.log('## xu:', await t.rc.cmd('balance ThuGTb'));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
