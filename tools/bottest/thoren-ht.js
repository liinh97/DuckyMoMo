// Huyền Thoại (23/10): menu 2 hàng (món + nút Rèn), rèn Giáp Huyền Thoại +0 từ Giáp Cao Cấp +5 + Huyền Ấn + Long Hồn Châu.
const h = require('./h');
const N = process.env.BOTN || 'ThuHt';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await R(`minecraft:clear ${N}`); await R(`eco set ${N} 60000`);
  for (const [id, n] of [['cc_ao_5', 1], ['ht_an_than', 3], ['long_hon', 1]]) await R(`dmm laydo ${id} ${n} ${N}`);
  await R(`minecraft:give ${N} minecraft:netherite_ingot 2`); await t.sleep(2000);
  const sl = (w, re) => w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
  let w = await t.openMenu('/thoren');
  t.log('## menu chính:', w.slots.slice(0, w.inventoryStart).filter(it => it && !/pane|barrier|anvil/.test(it.name)).map(it => t.itemText(it).name).join(' | '));
  await t.click(sl(w, /^Huyền Thoại/)); await t.sleep(1200); w = t.bot.currentWindow;
  const cells = w.slots.slice(0, w.inventoryStart).map((it, i) => it && !/pane$/.test(it.name) ? `[${i}] ${it.name} ${t.itemText(it).name}` : null).filter(Boolean);
  t.log('## menu Huyền Thoại +0:', cells.join(' | ').slice(0, 700));
  const btn = sl(w, /Rèn Giáp Huyền Thoại/); t.log('## nút Rèn Giáp:', t.itemText(w.slots[btn]).lore.join(' ¦ '));
  const n = msgs.length; await t.click(btn); await t.sleep(1500);
  t.log('## rèn ->', msgs.slice(n).join(' / '), '| túi:', t.bot.inventory.items().map(i => t.itemText(i).name).filter(x => /Huyền|Cao Cấp/.test(x)).join(', '));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
