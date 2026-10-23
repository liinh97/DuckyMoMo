// Vũ khí khác kiếm (23/10): menu set 9 món = 2 khối (giáp 4 + nút, vũ khí 5 + nút); rèn Rìu Chiến Binh +0 từ Rìu Tân Thủ +9.
const h = require('./h');
const N = process.env.BOTN || 'ThuVk';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await R(`minecraft:clear ${N}`); await R(`eco set ${N} 5000`);
  for (const [id, n] of [['tt_riu_9', 1], ['cb_an_binh', 2], ['hop_kim', 1], ['huyet_tinh', 1]]) await R(`dmm laydo ${id} ${n} ${N}`);
  await t.sleep(1500);
  const sl = (w, re) => w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
  let w = await t.openMenu('/thoren'); await t.click(sl(w, /^Chiến Binh/)); await t.sleep(1200); w = t.bot.currentWindow;
  t.log('## menu Chiến Binh: ' + w.inventoryStart / 9 + ' hàng');
  for (let r = 0; r < w.inventoryStart / 9; r++) t.log('   hàng', r, w.slots.slice(r * 9, r * 9 + 9).map(it => !it ? '·' : /pane$/.test(it.name) ? '-' : it.name.replace(/^(diamond|netherite|iron)_/, '')).join(' '));
  const b = sl(w, /Rèn Rìu Chiến Binh/); t.log('## nút Rèn Rìu:', t.itemText(w.slots[b]).lore.join(' ¦ '));
  const n = msgs.length; await t.click(b); await t.sleep(1500);
  t.log('## rèn ->', msgs.slice(n).join(' / '), '| túi:', t.bot.inventory.items().map(i => t.itemText(i).name).join(', '));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  // Tân Thủ: menu có đủ 9 món + nút
  await t.sleep(500); w = await t.openMenu('/thoren'); await t.click(sl(w, /^Tân Thủ/)); await t.sleep(1200); w = t.bot.currentWindow;
  t.log('## menu Tân Thủ: ' + w.slots.slice(0, w.inventoryStart).filter(it => it && /helmet|chestplate|leggings|boots|sword|axe|spear|bow|mace/.test(it.name)).map(it => t.itemText(it).name.replace(/&./g, '')).join(' | ').slice(0, 600));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
