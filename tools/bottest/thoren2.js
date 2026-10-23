// Thợ Rèn nhiều set (23/10): menu chọn set; Tinh Anh Giáp +0 cần Giáp Tân Thủ +9 (bị lấy); Thợ Mỏ Cúp +0 từ nguyên liệu.
const h = require('./h');
const N = process.env.BOTN || 'ThuRen';
const txt = o => { if (o == null) return ''; if (typeof o === 'string') return o; if (Array.isArray(o)) return o.map(txt).join(''); if (o.type && 'value' in o) return o.type === 'list' ? txt(o.value.value) : txt(o.value); let r = ''; for (const k of ['text', '', 'extra', 'content']) if (k in o) r += txt(o[k]); return r; };
(async () => {
  const t = await h.start(N); const R = c => t.rc.cmd(c);
  const msgs = []; t.bot._client.on('system_chat', p => { const v = txt(p.content ?? p.text ?? p).trim(); if (v) msgs.push(v); });
  await R(`minecraft:clear ${N}`); await R(`eco set ${N} 100000`);
  for (const [id, n] of [['loiquai', 10], ['vang', 1], ['than', 1], ['xuquai', 10], ['giap_tan_thu_9', 1]]) await R(`dmm laydo ${id} ${n} ${N}`);
  for (const [m, n] of [['netherite_ingot', 2], ['iron_ingot', 32]]) await R(`minecraft:give ${N} minecraft:${m} ${n}`);
  await t.sleep(2000);
  const names = () => t.bot.inventory.items().map(i => t.itemText(i).name).filter(n => /tân thủ|tinh anh|thợ mỏ/i.test(n));
  const show = w => w.slots.slice(0, w.inventoryStart).forEach((it, i) => { if (!it || /pane$/.test(it.name)) return; const x = t.itemText(it); t.log(`  [${i}] ${it.name} | ${x.name} | ${x.lore.join(' ¦ ').slice(0, 200)}`); });
  const slotOf = (w, re) => w.slots.slice(0, w.inventoryStart).findIndex(it => it && re.test(t.itemText(it).name));
  let w = await t.openMenu('/thoren'); t.log('## menu chính'); show(w);
  await t.click(slotOf(w, /^Tinh Anh/)); await t.sleep(1200); w = t.bot.currentWindow; t.log('## menu Tinh Anh'); show(w);
  let n = msgs.length; await t.click(slotOf(w, /^Giáp Tinh Anh/)); await t.sleep(1500);
  t.log('## rèn Giáp Tinh Anh +0 ->', msgs.slice(n).join(' / '), '| túi:', names().join(', '));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow); await t.sleep(500);
  w = await t.openMenu('/thoren'); await t.click(slotOf(w, /^Thợ Mỏ/)); await t.sleep(1200); w = t.bot.currentWindow;
  n = msgs.length; await t.click(slotOf(w, /^Cúp Thợ Mỏ/)); await t.sleep(1500);
  t.log('## rèn Cúp Thợ Mỏ +0 ->', msgs.slice(n).join(' / '), '| túi:', names().join(', '));
  const ta = t.bot.inventory.items().find(i => /tinh anh/i.test(t.itemText(i).name)); if (ta) t.log('## lore Giáp tinh anh +0:', t.itemText(ta).name, '|', t.itemText(ta).lore.join(' ¦ '));
  if (t.bot.currentWindow) t.bot.closeWindow(t.bot.currentWindow);
  await R(`minecraft:clear ${N}`); await t.end(); process.exit(0);
})().catch(e => { console.log('FAIL', e); process.exit(1); });
