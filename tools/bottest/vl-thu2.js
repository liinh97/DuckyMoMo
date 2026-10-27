// Việc dân làng nhờ, lượt 2 (sau vl-thu.js, cùng bot): online đủ 5 phút -> /diemdanh (xong việc) -> Già Làng trả thưởng; đọc thanh chỉ số.
// Chạy: BOTN=ThuVL ./tools/bottest/run.sh vl-thu2.js   Dọn: /choilai admin ThuVL moi
const h = require('./h');
const N = process.env.BOTN || 'ThuVL';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N);
  const msgs = [], bars = [];
  t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) (p.isActionBar || p.overlay ? bars : msgs).push(v); });
  t.bot._client.on('action_bar', p => bars.push(txt(p.text)));
  t.bot._client.on('set_action_bar_text', p => bars.push(txt(p.text)));
  const step = async (name, fn, wait = 2500) => { msgs.length = 0; await fn(); await t.sleep(wait); t.log('## ' + name + ':\n  ' + msgs.join('\n  ').slice(0, 1500)); };
  await t.sleep(5000);
  t.log('## thanh chỉ số (thô):', JSON.stringify(bars.slice(-2)).slice(0, 600));
  for (let i = 0; i < 11; i++) { await t.sleep(30000); t.bot.swingArm('right'); }   // đứng ~5,5 phút
  await step('/diemdanh', () => t.bot.chat('/diemdanh'), 3000);
  if (t.bot.currentWindow) { t.log('## cửa sổ:', JSON.stringify(t.bot.currentWindow.title).slice(0, 100)); t.bot.closeWindow(t.bot.currentWindow); }
  await step('/nhiemvu', () => t.bot.chat('/nhiemvu'), 2000);
  await step('Già Làng (trả thưởng)', () => t.rc.cmd(`dmm viecnpc ${N} gialang`));
  t.log('## thanh chỉ số cuối:', JSON.stringify(bars.slice(-1)).slice(0, 600));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
