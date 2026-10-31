// Skyblock: nạp thử thách tiếng Việt: /bsbadmin challenges › Import Database › vietnam.json (bot được cấp admin tạm).
const h = require('./h');
const N = process.env.BOTN || 'ThuSky';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
const dump = (t, w) => w.slots.slice(0, w.inventoryStart ?? 54).map((s, i) => s ? `${i}:${s.name}:${t.itemText(s).name}` : null).filter(Boolean).filter(x => !/stained_glass_pane/.test(x)).join(' | ');
(async () => {
  const t = await h.start(N, undefined, 'skyblock-1'); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await R(`lp user ${N} parent add admin`); await t.sleep(2500);
  const w = await t.openMenu('/bsbadmin challenges');
  const p = new Promise(res => { t.bot.once('windowOpen', x => res(x)); setTimeout(() => res(null), 4000); });
  t.click(15).catch(() => {}); const w2 = await p; await t.sleep(800);
  t.log('## danh sách:', w2 ? dump(t, w2).slice(0, 500) : '(không mở)', '|', msgs.slice(-2).join(' / ').slice(0, 200));
  if (w2) {
    const s = w2.slots.findIndex((x, i) => x && i < (w2.inventoryStart ?? 54) && /vietnam/i.test(t.itemText(x).name + JSON.stringify(x.nbt ?? '')));
    t.log('ô vietnam', s);
    if (s >= 0) { t.click(s).catch(() => {}); await t.sleep(1500); t.bot.chat('confirm'); await t.sleep(3500); }
    t.log('tin:', msgs.slice(-3).join(' / ').slice(0, 300));
    if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  }
  await R(`lp user ${N} parent remove admin`); await t.sleep(800);
  const ws = await t.openMenu('/is challenges');
  t.log('## /is challenges:', ws ? dump(t, ws).slice(0, 600) : '(không mở) ' + msgs.slice(-2).join(' / '));
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
