// Lấy mã đăng nhập web (/web) của bot, ghi ra _ma.txt (cd19: Playwright đăng nhập trang tài khoản). Xoá _ma.txt sau khi dùng.
const h = require('./h');
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const N = process.env.BOTN || 'ThuCD3';
(async () => {
  const t = await h.start(N); const m = [];
  t.bot._client.on('system_chat', p => m.push(txt(p.content ?? p)));
  t.bot.chat('/web'); await t.sleep(3000);
  const code = (m.join(' ').match(/\b(\d{6})\b/) || [])[1];
  t.log('mã:', code ? 'có' : 'không thấy', '|', m.filter(x => /web|mã/i.test(x)).join(' / ').replace(/\d{6}/, '******').slice(0, 200));
  if (code) require('fs').writeFileSync('/bot/_ma.txt', N + ' ' + code);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
