// Set Chuyên Cần: /thoren set chuyencan mở menu, /diemdanh xem hiện dòng phiếu + nút đổi.
const h = require('./h');
const N = process.env.BOTN || 'ThuChuyenCan';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await t.sleep(5000);
  const w = await t.openMenu('/thoren set chuyencan');
  t.log('menu:', w ? txt(w.title ?? '').slice(0, 40) + ' | ' + w.slots.slice(0, 54).map((s, i) => s && !/pane/.test(s.name) ? `${i}:${t.itemText(s).name}` : null).filter(Boolean).join(' | ').slice(0, 420) : '(không mở)');
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  const n0 = msgs.length; t.bot.chat('/diemdanh xem'); await t.sleep(1500);
  t.log('/diemdanh xem:', msgs.slice(n0).join(' / ').slice(0, 260));
  await R(`dmm laydo ${N} phieu_dd 3`); await t.sleep(1000);
  t.log('túi:', t.bot.inventory.items().map(i => `${t.itemText(i).name}×${i.count}`).filter(x => /Phiếu/.test(x)).join(', ') || '(không có phiếu)');
  await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
