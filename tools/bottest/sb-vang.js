// Skyblock: /vang mở cửa hàng; rank Đảo Chủ có /fly, tiền tố chat; hết rank thì mất.
const h = require('./h');
const N = process.env.BOTN || 'ThuSky';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const dump = (t, w) => w.slots.slice(0, 54).map((s, i) => s && !/pane/.test(s.name) ? `${i}:${t.itemText(s).name}` : null).filter(Boolean).join(' | ');
(async () => {
  const t = await h.start(N, undefined, 'skyblock-1'); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  t.bot.on('messagestr', m => msgs.push(m));
  await t.sleep(3500);
  let n0 = msgs.length; t.bot.chat('/fly'); await t.sleep(1200); t.log('/fly (có rank):', msgs.slice(n0).join(' / ').slice(0, 130));
  n0 = msgs.length; t.bot.chat('xin chao'); await t.sleep(1200); t.log('chat:', msgs.slice(n0).join(' / ').slice(0, 130));
  const w = await t.openMenu('/vang');
  t.log('/vang:', w ? dump(t, w).slice(0, 460) : '(không mở) ' + msgs.slice(-2).join(' / '));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  n0 = msgs.length; t.bot.chat('/vang nap'); await t.sleep(1200); t.log('/vang nap:', msgs.slice(n0).join(' / ').slice(0, 170));
  await R(`lp user ${N} parent removetemp daochu`); await t.sleep(1800);
  n0 = msgs.length; t.bot.chat('/fly'); await t.sleep(1200); t.log('/fly (gỡ rank):', msgs.slice(n0).join(' / ').slice(0, 130));
  await R(`eco take ${N} 2000`);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
