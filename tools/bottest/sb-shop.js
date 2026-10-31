// Skyblock: mua 1 kim cương ở /shop › Khoáng sản, rồi bán lại, so số dư.
const h = require('./h');
const N = process.env.BOTN || 'ThuSky';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N, undefined, 'skyblock-1'); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await t.sleep(4000);
  const bal = async () => (await R(`bal ${N}`)).replace(/\s+$/, '').slice(-14);
  t.log('## đầu:', await bal());
  const w = await t.openMenu('/shop');
  t.log('menu:', w.slots.slice(0, 54).map((s, i) => s && !/pane/.test(s.name) ? `${i}:${t.itemText(s).name}` : null).filter(Boolean).join(' | ').slice(0, 420));
  const p = new Promise(res => { t.bot.once('windowOpen', x => res(x)); setTimeout(() => res(null), 4000); });
  const m = w.slots.findIndex((s, i) => s && i < 54 && s.name === 'diamond');
  t.bot.clickWindow(m, 0, 0).catch(() => {}); const w2 = await p; await t.sleep(900);
  const cur = t.bot.currentWindow || w2;
  const d = cur.slots.findIndex((s, i) => s && i < 54 && s.name === 'diamond');
  t.log('shop:', txt(cur.title ?? '').slice(0, 40), '| ô kim cương', d, '| mô tả:', d >= 0 ? JSON.stringify(cur.slots[d].components?.find?.(c => /lore/.test(c.type))?.data ?? '').replace(/"(type|value|string|text|compound|list)":/g, '').slice(0, 260) : '');
  t.bot.clickWindow(d, 0, 0).catch(() => {}); await t.sleep(1800);
  t.log('## sau mua:', await bal(), '| tin:', msgs.slice(-2).join(' / ').slice(0, 170));
  t.bot.clickWindow(d, 1, 0).catch(() => {}); await t.sleep(1800);
  t.log('## sau bán:', await bal(), '| tin:', msgs.slice(-2).join(' / ').slice(0, 170));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
