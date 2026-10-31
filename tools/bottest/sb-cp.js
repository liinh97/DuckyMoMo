// Skyblock: nạp bảng điều khiển, thử /is, /is upgrade, /is challenges, /huongdan bằng tiếng Việt.
const h = require('./h');
const N = process.env.BOTN || 'ThuSky';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const dump = (t, w) => w.slots.slice(0, w.inventoryStart ?? 54).map((s, i) => s && !/pane/.test(s.name) ? `${i}:${t.itemText(s).name}` : null).filter(Boolean).join(' | ');
(async () => {
  const t = await h.start(N, undefined, 'skyblock-1'); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await t.sleep(3000);
  await R(`lp user ${N} parent add admin`); await t.sleep(2000);
  t.bot.chat('/bsbadmin cp import'); await t.sleep(1500); t.bot.chat('/bsbadmin cp import'); await t.sleep(1500);
  t.log('import:', msgs.slice(-2).join(' / ').slice(0, 200));
  await R(`lp user ${N} parent remove admin`); await t.sleep(1500);
  for (const c of ['/is', '/is upgrade', '/is challenges', '/shop']) {
    const n0 = msgs.length;
    const w = await t.openMenu(c);
    t.log(`${c} -> ${w ? txt(w.title ?? '').slice(0, 40) : '(không mở)'} | ${w ? dump(t, w).slice(0, 430) : ''} | tin: ${msgs.slice(n0).join(' / ').slice(0, 160)}`);
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
    await t.sleep(500);
  }
  let n0 = msgs.length; t.bot.chat('/huongdan'); await t.sleep(1500);
  t.log('/huongdan:', msgs.slice(n0).join(' / ').slice(0, 300));
  n0 = msgs.length; t.bot.chat('/is go'); await t.sleep(1500); t.log('/is go:', msgs.slice(n0).join(' / ').slice(0, 120));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
